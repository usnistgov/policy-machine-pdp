package gov.nist.csd.pm.server.admin;

import com.eventstore.dbclient.EventStoreDBClient;
import gov.nist.csd.pm.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.impl.neo4j.memory.pap.Neo4jMemoryPAP;
import gov.nist.csd.pm.impl.neo4j.memory.pap.store.Neo4jMemoryPolicyStore;
import gov.nist.csd.pm.server.admin.EPPService;
import gov.nist.csd.pm.server.admin.EventStoreClient;
import gov.nist.csd.pm.server.admin.PDPService;
import gov.nist.csd.pm.server.shared.PolicyEventSubscriber;
import gov.nist.csd.pm.server.shared.UserContextInterceptor;
import gov.nist.csd.pm.server.shared.ServerConfig;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pdp.PDP;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;

import java.io.File;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

public class AdminPDPEPPServer {

	private static final Logger logger = LogManager.getLogger(AdminPDPEPPServer.class);

	public static void main(String[] args) throws Exception {
		ServerConfig config = ServerConfig.load();

		// init event store client
		EventStoreDBClient eventStoreDBClient = EventStoreClient.get(config);

		// start neo4j embedded
		DatabaseManagementService managementService = new DatabaseManagementServiceBuilder(new File("/tmp/admin-pdp-epp").toPath()).build();
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
		PolicyEventSubscriber policyEventSubscriber = new PolicyEventSubscriber(new Neo4jMemoryPAP(new Neo4jMemoryPolicyStore(graphDb)), config);
		policyEventSubscriber.listenForEvents();

		// Start the server and keep it running
		server.start();
		server.awaitTermination();
	}
}
