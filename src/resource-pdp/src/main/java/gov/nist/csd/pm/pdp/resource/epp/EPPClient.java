package gov.nist.csd.pm.pdp.resource.epp;

import gov.nist.csd.pm.core.common.event.EventContext;
import gov.nist.csd.pm.core.epp.EPP;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.resource.RevisionCatchUpGate;
import gov.nist.csd.pm.pdp.resource.config.EPPMode;
import gov.nist.csd.pm.pdp.resource.config.ResourcePDPConfig;
import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;
import gov.nist.csd.pm.proto.v1.epp.EPPServiceGrpc;
import gov.nist.csd.pm.proto.v1.epp.ProcessEventResponse;
import gov.nist.csd.pm.proto.v1.model.Value;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EPPClient extends EPP {

    private static final Logger logger = LoggerFactory.getLogger(EPPClient.class);

    private final PDP pdp;
    private final ResourcePDPConfig resourcePDPConfig;
    private EPPServiceGrpc.EPPServiceBlockingStub blockingStub;
    private EPPServiceGrpc.EPPServiceStub stub;
    private final RevisionCatchUpGate revisionCatchUpGate;

    public EPPClient(PDP pdp,
                     PAP pap,
                     ResourcePDPConfig resourcePDPConfig,
                     RevisionCatchUpGate revisionCatchUpGate) {
        super(pdp, pap);
        this.pdp = pdp;
        this.resourcePDPConfig = resourcePDPConfig;
        this.revisionCatchUpGate = revisionCatchUpGate;
    }

    @PostConstruct
    public void subscribeToPDP() {
        if (resourcePDPConfig.getEppMode() == EPPMode.DISABLED) {
            return;
        }

        // subscribe to the PDP bean
        this.pdp.addEventSubscriber(this);

        // init epp client to admin pdp epp service
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(resourcePDPConfig.getAdminHostname(), resourcePDPConfig.getAdminPort())
                .defaultServiceConfig(buildGrpcConfigMap())
                .enableRetry()
                .usePlaintext()
                .build();

        if (resourcePDPConfig.getEppMode() == EPPMode.ASYNC) {
            this.stub = EPPServiceGrpc.newStub(channel);
        } else {
            this.blockingStub = EPPServiceGrpc.newBlockingStub(channel);
        }
    }

    @Override
    public void processEvent(EventContext eventCtx) {
        if (resourcePDPConfig.getEppMode() == EPPMode.DISABLED) {
            return;
        }

        logger.info("sending to EPP {}", eventCtx);

        gov.nist.csd.pm.proto.v1.epp.EventContext eventCtxProto = ProtoUtil.toEventContextProto(eventCtx);

        if (stub != null) {
            processEventAsync(eventCtxProto);
        } else {
	        try {
		        processEventSync(eventCtxProto);
	        } catch (InterruptedException e) {
		        throw new RuntimeException(e);
	        }
        }
    }

    private void processEventAsync(gov.nist.csd.pm.proto.v1.epp.EventContext eventCtx) {
        stub.processEvent(eventCtx, new StreamObserver<>() {
            @Override
            public void onNext(ProcessEventResponse processEventResponse) {

            }

            @Override
            public void onError(Throwable throwable) {
                logger.error("EPP error", throwable);
            }

            @Override
            public void onCompleted() {

            }
        });
    }

    /**
     * Process the given even context synchronously in the EPP server. If no errors occur and the event context matched
     * an obligation event pattern, the EPP will respond with the revision of the last event generated. This method will
     * wait a configurable amount of time before returning for the local event subscription to catch up. If this process
     * times out, the method will return a success still, but subsequent calls to the PDP will fail until it's caught up.
     * @param eventCtx the EventContext proto to send to the EPP server.
     */
    private void processEventSync(gov.nist.csd.pm.proto.v1.epp.EventContext eventCtx) throws InterruptedException {
        ProcessEventResponse eppResponse = blockingStub.processEvent(eventCtx);

        // if there is no result than the EPP did generate any events
        if (!eppResponse.hasResult()) {
            return;
        }

        Value responseValue = eppResponse.getResult().getValuesMap().get("last_event_revision");
        long lastEventRevision = responseValue.getInt64Value();
        logger.debug("epp returned last_event_revision {}", lastEventRevision);

        // notify the revision catchup gate to wait for the last epp revision
        revisionCatchUpGate.setWaitForRevision(lastEventRevision);

        // wait for the service to catch up
        // if this times out without being caught up the current request will still return a success
        // subsequent calls will fail fast until caught up
        revisionCatchUpGate.awaitCatchUp();
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
