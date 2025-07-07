package gov.nist.csd.pm.pdp.admin.pap;

import com.eventstore.dbclient.*;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.node.NodeType;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.store.Neo4jEmbeddedPolicyStore;
import gov.nist.csd.pm.pdp.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.pdp.proto.event.Bootstrapped;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import gov.nist.csd.pm.pdp.shared.plugin.PluginLoader;
import gov.nist.csd.pm.pdp.shared.plugin.PluginLoaderConfig;
import gov.nist.csd.pm.pdp.sharedtest.EventStoreTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

class Neo4jBootstrapperTest {

	private EventStoreTestContainer eventStoreTestContainer;
	private DatabaseManagementService managementService;
	private GraphDatabaseService graphDb;
	private NoCommitNeo4jPolicyStore policyStore;
	private EventStoreDBConfig eventStoreDBConfig;
	private EventStoreConnectionManager eventStoreConnectionManager;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() throws PMException, IOException {
		eventStoreTestContainer = new EventStoreTestContainer();
		eventStoreTestContainer.start();

		managementService = new DatabaseManagementServiceBuilder(tempDir.resolve("neo4j"))
				.setConfig(GraphDatabaseSettings.strict_config_validation, false)
				.build();
		graphDb = managementService.database(DEFAULT_DATABASE_NAME);
		Neo4jEmbeddedPolicyStore.createIndexes(graphDb);
		policyStore = new NoCommitNeo4jPolicyStore(graphDb);

		eventStoreDBConfig = new EventStoreDBConfig(
				"test-events",
				"test-snapshots",
				eventStoreTestContainer.getHost(),
				eventStoreTestContainer.getPort()
		);
		eventStoreConnectionManager = new EventStoreConnectionManager(eventStoreDBConfig);
	}

	@AfterEach
	void tearDown() {
		if (managementService != null) {
			managementService.shutdown();
		}
		if (eventStoreTestContainer != null) {
			eventStoreTestContainer.stop();
		}
	}

	@Test
	void test_OnSuccessWithPML_EventsArePublishedWithCorrectIds() throws PMException, ExecutionException, InterruptedException, IOException, InvalidProtocolBufferException {
		Path bootstrapFile = tempDir.resolve("bootstrap.pml");
		String bootstrapContent = """
				set resource operations ["read", "write"]
				
				create pc "pc1"
				create ua "ua1" in ["pc1"]
				create oa "oa1" in ["pc1"]
				assign "u1" to ["ua1"]
				create o "o1" in ["oa1"]
				
				associate "ua1" and "oa1" with ["read"]
				""";
		Files.writeString(bootstrapFile, bootstrapContent);

		AdminPDPConfig adminPDPConfig = new AdminPDPConfig();
		adminPDPConfig.setBootstrapFilePath(bootstrapFile.toString());
		adminPDPConfig.setBootstrapUser("u1");
		adminPDPConfig.setNeo4jDbPath(tempDir.resolve("neo4j").toString());
		adminPDPConfig.setEsdbConsumerGroup("test-cg");
		adminPDPConfig.setSnapshotInterval(1000);

		Neo4jBootstrapper bootstrapper = new Neo4jBootstrapper(
				adminPDPConfig,
				eventStoreDBConfig,
				eventStoreConnectionManager,
				graphDb,
				new PluginLoader(new PluginLoaderConfig())
		);

		bootstrapper.bootstrap();

		EventStoreDBClient client = eventStoreConnectionManager.getOrInitClient();
		ReadResult readResult = client.readStream(
				eventStoreDBConfig.getEventStream(),
				ReadStreamOptions.get().fromStart()
		).get();

		List<ResolvedEvent> events = readResult.getEvents();
		assertEquals(1, events.size());

		ResolvedEvent resolvedEvent = events.get(0);
		PMEvent pmEvent = PMEvent.parseFrom(resolvedEvent.getEvent().getEventData());

		assertTrue(pmEvent.hasBootstrapped());

		Bootstrapped bootstrapped = pmEvent.getBootstrapped();

		assertEquals("pml", bootstrapped.getType());
		assertEquals(Files.readString(bootstrapFile), bootstrapped.getValue());

		Map<String, Long> createdNodes = bootstrapped.getCreatedNodesMap();
		assertFalse(createdNodes.isEmpty());
		assertTrue(createdNodes.containsKey("pc1"));
		assertTrue(createdNodes.containsKey("ua1"));
		assertTrue(createdNodes.containsKey("oa1"));
		assertTrue(createdNodes.containsKey("u1"));
		assertTrue(createdNodes.containsKey("o1"));

		assertTrue(policyStore.graph().search(NodeType.ANY, new HashMap<>()).isEmpty());
	}

	@Test
	void test_OnSuccessWithJSON_EventsArePublishedWithCorrectIds() throws PMException, ExecutionException, InterruptedException, IOException, InvalidProtocolBufferException {
		Path bootstrapFile = tempDir.resolve("bootstrap.json");
		String bootstrapContent = """
				{
					"graph": {
						"pcs": [{"id": 1, "name": "pc1"}],
						"uas": [{"id": 2, "name": "ua1", "assignments": [1]}],
						"oas": [{"id": 3, "name": "oa1", "assignments": [1]}],
						"users": [{"id": 4, "name": "u1", "assignments": [2]}],
						"objects": [{"id": 5, "name": "o1", "assignments": [3]}]
					}
				}
				""";
		Files.writeString(bootstrapFile, bootstrapContent);

		AdminPDPConfig adminPDPConfig = new AdminPDPConfig();
		adminPDPConfig.setBootstrapFilePath(bootstrapFile.toString());
		adminPDPConfig.setNeo4jDbPath(tempDir.resolve("neo4j").toString());
		adminPDPConfig.setEsdbConsumerGroup("test-cg");
		adminPDPConfig.setSnapshotInterval(1000);

		Neo4jBootstrapper bootstrapper = new Neo4jBootstrapper(
				adminPDPConfig,
				eventStoreDBConfig,
				eventStoreConnectionManager,
				graphDb,
				new PluginLoader(new PluginLoaderConfig())
		);

		bootstrapper.bootstrap();

		EventStoreDBClient client = eventStoreConnectionManager.getOrInitClient();
		ReadResult readResult = client.readStream(
				eventStoreDBConfig.getEventStream(),
				ReadStreamOptions.get().fromStart()
		).get();

		List<ResolvedEvent> events = readResult.getEvents();
		assertEquals(1, events.size());

		ResolvedEvent resolvedEvent = events.get(0);
		PMEvent pmEvent = PMEvent.parseFrom(resolvedEvent.getEvent().getEventData());

		assertTrue(pmEvent.hasBootstrapped());

		Bootstrapped bootstrapped = pmEvent.getBootstrapped();

		assertEquals("json", bootstrapped.getType());
		assertEquals(Files.readString(bootstrapFile), bootstrapped.getValue());

		Map<String, Long> createdNodes = bootstrapped.getCreatedNodesMap();
		assertFalse(createdNodes.isEmpty());
		assertEquals(1, createdNodes.get("pc1"));
		assertEquals(2, createdNodes.get("ua1"));
		assertEquals(3, createdNodes.get("oa1"));
		assertEquals(4, createdNodes.get("u1"));
		assertEquals(5, createdNodes.get("o1"));

		assertTrue(policyStore.graph().search(NodeType.ANY, new HashMap<>()).isEmpty());
	}
}