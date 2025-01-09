package gov.nist.csd.pm.server.admin;

import com.eventstore.dbclient.EventStoreDBClient;
import gov.nist.csd.pm.impl.neo4j.memory.pap.Neo4jMemoryPAP;
import gov.nist.csd.pm.impl.neo4j.memory.pap.store.Neo4jMemoryPolicyStore;
import gov.nist.csd.pm.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pap.serialization.pml.PMLDeserializer;
import gov.nist.csd.pm.server.shared.PolicyEventSubscriber;
import gov.nist.csd.pm.server.shared.UserContextInterceptor;
import gov.nist.csd.pm.server.shared.ServerConfig;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

public class AdminPDPEPPServer {

	private static final String BOOTSTRAP_PML_PATH = "BOOTSTRAP_PML_PATH";
	private static final Logger logger = LogManager.getLogger(AdminPDPEPPServer.class);

	public static void main(String[] args) throws Exception {
		ServerConfig config = ServerConfig.load();

		System.out.println(config);

		// init event store client
		EventStoreDBClient eventStoreDBClient = EventStoreClient.get(config);

		// start neo4j embedded
		DatabaseManagementService managementService = new DatabaseManagementServiceBuilder(new File("/tmp/admin-pdp-epp").toPath())
				.setConfig(GraphDatabaseSettings.strict_config_validation, false)
				.build();
		GraphDatabaseService graphDb = managementService.database(DEFAULT_DATABASE_NAME);

		// create the PDP and EPP services
		PDPService pdpService = new PDPService(graphDb, eventStoreDBClient);
		EPPService eppService = new EPPService(graphDb, eventStoreDBClient);

		Server server = ServerBuilder
				.forPort(config.adminPort())
				.addService(pdpService)
				.addService(eppService)
				.intercept(new UserContextInterceptor())
				.build();

		logger.info("Starting admin PDP and EPP servers on port " + config.adminPort());

		// create the policy event subscriber
		// use the regular neo4j memory store so changes are committed from event store
		Neo4jMemoryPAP neo4jMemoryPAP = new Neo4jMemoryPAP(new Neo4jMemoryPolicyStore(graphDb));
		PolicyEventSubscriber policyEventSubscriber = new PolicyEventSubscriber(neo4jMemoryPAP, config);
		policyEventSubscriber.listenForEvents();

		// load bootstrap policy from resources
		bootstrapPolicy(neo4jMemoryPAP);

		// Start the server and keep it running
		server.start();
		server.awaitTermination();
	}

	private static void bootstrapPolicy(Neo4jMemoryPAP neo4jMemoryPAP) throws IOException {
		String path = System.getenv(BOOTSTRAP_POLICY_PATH);
		String format = System.getenv(BOOTSTRAP_POLICY_FORMAT);
		String pml = Files.readString(Path.of(path));
		neo4jMemoryPAP.deserialize(new UserContext("TODO"), pml, new PMLDeserializer());

		/*
		bootstrap user and format (json, pml , etc)

		maybe scan classpath for annotated classes to add as functions, etc for pml?
		 */

	}
}
