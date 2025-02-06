package gov.nist.csd.pm.server.resource;

import gov.nist.csd.pm.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.server.shared.PolicyEventSubscriber;
import gov.nist.csd.pm.server.shared.UserContextInterceptor;
import gov.nist.csd.pm.server.shared.ServerConfig;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pdp.PDP;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionService;

public class ResourcePDPServer {

	public static void main(String[] args) throws Exception {
		ServerConfig config = ServerConfig.load();

		PAP pap = new MemoryPAP();
		PDP pdp = new PDP(pap);

		// create the PDP service
		ResourcePDPService resourcePDPService = new ResourcePDPService(pdp, pap, config);

		// create health check
		HealthStatusManager healthStatusManager = new HealthStatusManager();
		healthStatusManager.setStatus("resource-pdp", HealthCheckResponse.ServingStatus.SERVING);

		Server server = ServerBuilder
				.forPort(config.resourcePort())
				.addService(resourcePDPService)
				.addService(ProtoReflectionService.newInstance())
				.addService(healthStatusManager.getHealthService())
				.intercept(new UserContextInterceptor())
				.build();

		System.out.println("Starting resource PDP server on port " + config.resourcePort());

		// create the policy event subscriber
		PolicyEventSubscriber policyEventSubscriber = new PolicyEventSubscriber(pap, config);
		policyEventSubscriber.listenForEvents();

		// Start the server and keep it running
		server.start();
		server.awaitTermination();
	}
}
