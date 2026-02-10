package gov.nist.csd.pm.pdp.resource.epp;

import gov.nist.csd.pm.core.common.event.EventContext;
import gov.nist.csd.pm.core.epp.EPP;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.resource.config.EPPMode;
import gov.nist.csd.pm.pdp.resource.config.ResourcePDPConfig;
import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;
import gov.nist.csd.pm.proto.v1.epp.EPPServiceGrpc;
import gov.nist.csd.pm.proto.v1.epp.ProcessEventResponse;
import gov.nist.csd.pm.proto.v1.model.Value;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EPPClient extends EPP {

    private static final Logger logger = LoggerFactory.getLogger(EPPClient.class);

    private final PDP pdp;
    private final ResourcePDPConfig resourcePDPConfig;
    private EPPServiceGrpc.EPPServiceBlockingStub blockingStub;

    public EPPClient(PDP pdp,
                     PAP pap,
                     ResourcePDPConfig resourcePDPConfig) {
        super(pdp, pap);
        this.pdp = pdp;
        this.resourcePDPConfig = resourcePDPConfig;
    }

    @PostConstruct
    public void subscribeToPDP() {
        // subscribe to the PDP bean
        this.pdp.addEventSubscriber(this);

        // init epp client to admin pdp epp service
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(resourcePDPConfig.getAdminHostname(), resourcePDPConfig.getAdminPort())
                .defaultServiceConfig(buildGrpcConfigMap())
                .enableRetry()
                .usePlaintext()
                .build();

        this.blockingStub = EPPServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public void processEvent(EventContext eventCtx) {
        logger.info("sending to EPP {}", eventCtx);

        gov.nist.csd.pm.proto.v1.epp.EventContext eventCtxProto = ProtoUtil.toEventContextProto(eventCtx);

        blockingStub.processEvent(eventCtxProto);
    }

    private Map<String, Object> buildGrpcConfigMap() {
        Map<String, Object> serviceConfig = new HashMap<>();

        // Load balancing configuration
        serviceConfig.put("loadBalancingConfig", List.of(Map.of("round_robin", new HashMap<>())));

        // Method configuration with retry policy
        Map<String, Object> retryPolicy = new HashMap<>();
        retryPolicy.put("maxAttempts", "3");
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
