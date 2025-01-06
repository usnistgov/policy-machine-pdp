package gov.nist.csd.pm.server.resource;

import com.google.protobuf.ByteString;
import gov.nist.csd.pm.epp.EPP;
import gov.nist.csd.pm.epp.EventProcessor;
import gov.nist.csd.pm.epp.proto.EPPGrpc;
import gov.nist.csd.pm.epp.proto.EPPResponse;
import gov.nist.csd.pm.impl.neo4j.memory.pap.store.Neo4jUtil;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.exception.PMException;
import gov.nist.csd.pm.pap.obligation.EventContext;
import gov.nist.csd.pm.pdp.PDP;
import gov.nist.csd.pm.server.shared.EventContextUtil;
import gov.nist.csd.pm.server.shared.ServerConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EPPClient extends EPP implements EventProcessor {

	private static final Logger logger = LogManager.getLogger(EPPClient.class);

	private EPPClientProcessor processor;

	public EPPClient(PDP pdp, PAP pap, ServerConfig serverConfig) throws PMException {
		super(pdp, pap);

		this.processor = new EPPClientProcessor(pdp, pap, serverConfig);
	}

	@Override
	public EPPClientProcessor getEventProcessor() {
		return processor;
	}

	@Override
	public void processEvent(EventContext eventCtx) throws PMException {
		processor.processEvent(eventCtx);
	}

	public static class EPPClientProcessor extends EPPEventProcessor {

		private EPPGrpc.EPPBlockingStub blockingStub;

		public EPPClientProcessor(PDP pdp, PAP pap, ServerConfig config) {
			super(pdp, pap);

			ManagedChannel channel = ManagedChannelBuilder
					.forAddress(config.adminHost(), config.adminPort())
					.defaultServiceConfig(buildGrpcConfigMap())
					.enableRetry()
					.usePlaintext()
					.build();

			this.blockingStub = EPPGrpc.newBlockingStub(channel);
		}

		@Override
		public void processEvent(EventContext eventContext) throws PMException {
			// send to EPP service (in the admin-pdp-epp)
			logger.info("sending event {}", eventContext);

			blockingStub.processEvent(EventContextUtil.toProto(eventContext));
		}

		private Map<String, Object> buildGrpcConfigMap() {
			Map<String, Object> serviceConfig = new HashMap<>();

			// Load balancing configuration
			serviceConfig.put("loadBalancingConfig", List.of(Map.of("round_robin", new HashMap<>())));

			// Method configuration with retry policy
			Map<String, Object> retryPolicy = new HashMap<>();
			retryPolicy.put("maxAttempts", "4");
			retryPolicy.put("initialBackoff", "0.2s");
			retryPolicy.put("maxBackoff", "10s");
			retryPolicy.put("backoffMultiplier", 1.5);
			retryPolicy.put("retryableStatusCodes", List.of("UNAVAILABLE"));

			serviceConfig.put("methodConfig", List.of(
					Map.of(
							"name", List.of(Map.of("service", "your.service.Name")),
							"retryPolicy", retryPolicy
					)
			));

			return serviceConfig;
		}
	}
}
