package gov.nist.csd.pm.server.resource.epp;

import gov.nist.csd.pm.common.event.EventContext;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.epp.EPP;
import gov.nist.csd.pm.epp.proto.EPPGrpc;
import gov.nist.csd.pm.epp.proto.EPPResponse;
import gov.nist.csd.pm.epp.proto.EventContextProto;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pdp.PDP;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.server.resource.config.ResourcePDPConfig;
import gov.nist.csd.pm.server.resource.eventstore.PolicyEventSubscriptionListener;
import gov.nist.csd.pm.server.shared.protobuf.EventContextUtil;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EPPClient extends EPP {

    private static final Logger logger = LoggerFactory.getLogger(EPPClient.class);

    private final PDP pdp;
    private final PolicyEventSubscriptionListener listener;
    private final ResourcePDPConfig resourcePDPConfig;
    private EPPGrpc.EPPBlockingStub blockingStub;
    private EPPGrpc.EPPStub stub;

    public EPPClient(PDP pdp,
                     PAP pap,
                     ResourcePDPConfig config,
                     PolicyEventSubscriptionListener listener,
                     ResourcePDPConfig resourcePDPConfig) {
        super(pdp, pap);
        this.pdp = pdp;
        this.listener = listener;
        this.resourcePDPConfig = resourcePDPConfig;

        ManagedChannel channel = ManagedChannelBuilder
            .forAddress(config.getAdminHostname(), config.getAdminPort())
            .defaultServiceConfig(buildGrpcConfigMap())
            .enableRetry()
            .usePlaintext()
            .build();

        if (config.isEppAsync()) {
            this.stub = EPPGrpc.newStub(channel);
        } else {
            this.blockingStub = EPPGrpc.newBlockingStub(channel);
        }
    }

    @PostConstruct
    public void subscribeToPDP() {
        this.pdp.addEventSubscriber(this);
    }

    @Override
    public void processEvent(EventContext eventCtx) throws PMException {
        logger.info("sending event {}", eventCtx);

        EventContextProto eventCtxProto = EventContextUtil.toProto(eventCtx);

        if (stub != null) {
            processEventAsync(eventCtxProto);
        } else {
            processEventSync(eventCtxProto);
        }
    }

    private void processEventAsync(EventContextProto eventCtx) {
        stub.processEvent(eventCtx, new StreamObserver<>() {
            @Override
            public void onNext(EPPResponse eppResponse) {
                logger.info("EPP returned {} side effect events for event {}", eppResponse.getEventsCount(), eventCtx);
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
     * an obligation event pattern, the EPP will respond with a list of events and the revision number of the first event.
     */
    private void processEventSync(EventContextProto eventCtx) {
        EPPResponse eppResponse = blockingStub.processEvent(eventCtx);
        List<PMEvent> eventsList = eppResponse.getEventsList();
        long startRevision = eppResponse.getStartRevision();
        logger.debug("epp returned start revision {} and {} events", startRevision, eventsList.size());

        if (startRevision == 0 || eventsList.isEmpty()) {
            return;
        }

        // Send the epp side effect events to the listener. The listener will return a CompletableFuture that
        // completes when the given tx has been applied. If it times out based on the value set in the configuration,
        // there is no error. Instead, the current thread returns and the tx will be processed at a later time by the listener.
        CompletableFuture<Void> eppSideEffectEventsProcessed = listener.processOrQueue(startRevision, eventsList);

        try {
            eppSideEffectEventsProcessed.get(resourcePDPConfig.getEppSideEffectTimeout(), TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            logger.warn("error processing epp side effect events", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("thread interrupted processing epp side effect events", e);
        }
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
