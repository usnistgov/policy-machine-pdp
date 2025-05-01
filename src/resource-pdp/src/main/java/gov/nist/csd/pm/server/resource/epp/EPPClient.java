package gov.nist.csd.pm.server.resource.epp;

import gov.nist.csd.pm.common.event.EventContext;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.epp.EPP;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pdp.PDP;
import gov.nist.csd.pm.proto.epp.EPPGrpc;
import gov.nist.csd.pm.server.shared.config.PDPConfig;
import gov.nist.csd.pm.server.shared.protobuf.EventContextUtil;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EPPClient extends EPP {

    private EPPGrpc.EPPBlockingStub blockingStub;

    public EPPClient(PDP pdp, PAP pap, PDPConfig config) {
        super(pdp, pap);

        ManagedChannel channel = ManagedChannelBuilder
            .forAddress(config.getAdminHost(), config.getAdminPort())
            .defaultServiceConfig(buildGrpcConfigMap())
            .enableRetry()
            .usePlaintext()
            .build();

        this.blockingStub = EPPGrpc.newBlockingStub(channel);
    }

    @Override
    public void processEvent(EventContext eventCtx) throws PMException {
        // send to EPP service (in the admin-pdp-epp)
        System.out.println("sending event " + eventCtx);

        blockingStub.processEvent(EventContextUtil.toProto(eventCtx));
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
                "name", List.of(Map.of("service", "resource-pdp")),
                "retryPolicy", retryPolicy
            )
        ));

        return serviceConfig;
    }
}
