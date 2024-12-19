package gov.nist.csd.pm.server.resource;

import gov.nist.csd.pm.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.server.shared.PolicyEventSubscriber;
import gov.nist.csd.pm.server.shared.UserContextInterceptor;
import gov.nist.csd.pm.server.shared.ServerConfig;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pdp.PDP;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ResourcePDPServer {

	private static final Logger logger = LogManager.getLogger(ResourcePDPServer.class);

	public static void main(String[] args) throws Exception {
		ServerConfig config = ServerConfig.load();

		PAP pap = new MemoryPAP();
		PDP pdp = new PDP(pap);

		// create the PDP service
		ResourcePDPService resourcePDPService = new ResourcePDPService(pdp, pap, config);

		Server server = ServerBuilder
				.forPort(config.resourcePort())
				.addService(resourcePDPService)
				.intercept(new UserContextInterceptor())
				.build();

		logger.info("Starting resource PDP server on port " + config.resourcePort());

		// create the policy event subscriber
		PolicyEventSubscriber policyEventSubscriber = new PolicyEventSubscriber(pap, config);
		policyEventSubscriber.listenForEvents();

		// Start the server and keep it running
		server.start();
		server.awaitTermination();
	}
}
