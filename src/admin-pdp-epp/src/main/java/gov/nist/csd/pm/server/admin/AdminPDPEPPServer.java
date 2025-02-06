package gov.nist.csd.pm.server.admin;

import com.eventstore.dbclient.EventStoreDBClient;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.impl.neo4j.memory.pap.Neo4jMemoryPAP;
import gov.nist.csd.pm.impl.neo4j.memory.pap.store.Neo4jMemoryPolicyStore;
import gov.nist.csd.pm.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pap.serialization.PolicyDeserializer;
import gov.nist.csd.pm.pap.serialization.json.JSONDeserializer;
import gov.nist.csd.pm.pap.serialization.pml.PMLDeserializer;
import gov.nist.csd.pm.server.shared.PolicyEventSubscriber;
import gov.nist.csd.pm.server.shared.UserContextInterceptor;
import gov.nist.csd.pm.server.shared.ServerConfig;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static gov.nist.csd.pm.impl.neo4j.memory.pap.store.Neo4jUtil.NAME_PROPERTY;
import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

public class AdminPDPEPPServer {

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

		// create health check
		HealthStatusManager healthStatusManager = new HealthStatusManager();
		healthStatusManager.setStatus("admin-pdp-epp", HealthCheckResponse.ServingStatus.SERVING);

		Server server = ServerBuilder
				.forPort(config.adminPort())
				.addService(pdpService)
				.addService(eppService)
				.addService(ProtoReflectionService.newInstance())
				.addService(healthStatusManager.getHealthService())
				.intercept(new UserContextInterceptor())
				.build();

		System.out.println("Starting admin PDP and EPP servers on port " + config.adminPort());

		// create the policy event subscriber
		// use the regular neo4j memory store so changes are committed from event store
		Neo4jMemoryPAP neo4jMemoryPAP = new Neo4jMemoryPAP(new Neo4jMemoryPolicyStore(graphDb));

		PolicyEventSubscriber policyEventSubscriber = new PolicyEventSubscriber(neo4jMemoryPAP, config);
		policyEventSubscriber.listenForEvents();

		// bootstrap policy
		bootstrapPolicy(config, neo4jMemoryPAP);

		// Start the server and keep it running
		server.start();
		server.awaitTermination();
	}

	private static void bootstrapPolicy(ServerConfig config, Neo4jMemoryPAP neo4jMemoryPAP) throws IOException, PMException {
		String bootstrapFilePath = config.bootstrapFilePath();
		if (bootstrapFilePath == null) {
			return;
		}

		String bootstrapUser = config.bootstrapUser();
		if (bootstrapUser == null) {
			throw new BootstrapException("bootstrap user is undefined");
		}

		UserContext bootstrapUserCtx = new UserContext(bootstrapUser);
		Path path = Paths.get(bootstrapFilePath);
		String data = Files.readString(path);

		PolicyDeserializer policyDeserializer;
		if (bootstrapFilePath.endsWith(".pml")) {
			policyDeserializer = new PMLDeserializer();
		} else if (bootstrapFilePath.endsWith(".json")) {
			policyDeserializer = new JSONDeserializer();
		} else {
			throw new BootstrapException("unsupported bootstrap file type, expected .json or .pml");
		}

		System.out.println("bootstrapping PML policy " + data);
		neo4jMemoryPAP.deserialize(bootstrapUserCtx, data, policyDeserializer);

		// TODO support multiple bootstrap files?
	}
}
