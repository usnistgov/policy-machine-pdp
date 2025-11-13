package gov.nist.csd.pm.pdp.admin.pap;

import com.eventstore.dbclient.*;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.node.NodeType;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.store.Neo4jEmbeddedPolicyStore;
import gov.nist.csd.pm.core.pap.function.PluginRegistry;
import gov.nist.csd.pm.pdp.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
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
		policyStore = new NoCommitNeo4jPolicyStore(graphDb, Neo4jBootstrapperTest.class.getClassLoader());

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
}