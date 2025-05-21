package gov.nist.csd.pm.server.admin.eventstore;

import com.eventstore.dbclient.*;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.impl.neo4j.embedded.pap.Neo4jEmbeddedPAP;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.server.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.server.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.server.shared.eventstore.PolicyEventHandler;
import gov.nist.csd.pm.server.shared.eventstore.SnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Component
public class PolicyEventPersistentSubscriptionListener extends PersistentSubscriptionListener {

    private static final Logger logger = LoggerFactory.getLogger(PolicyEventPersistentSubscriptionListener.class);

    private final Neo4jEmbeddedPAP pap;
    private final SnapshotService snapshotService;
    private final CurrentRevisionService currentRevision;
    private final AdminPDPConfig adminPDPConfig;

    public PolicyEventPersistentSubscriptionListener(Neo4jEmbeddedPAP pap,
                                                     CurrentRevisionService currentRevision,
                                                     SnapshotService snapshotService,
                                                     AdminPDPConfig adminPDPConfig) {
        this.pap = pap;
        this.currentRevision = currentRevision;
        this.snapshotService = snapshotService;
        this.adminPDPConfig = adminPDPConfig;
    }

    @Override
    public void onEvent(PersistentSubscription subscription, int retryCount, ResolvedEvent event) {
        RecordedEvent originalEvent = event.getEvent();
        long revision = originalEvent.getRevision();
        PolicyEventHandler policyEventHandler = new PolicyEventHandler(pap);

        try {
            PMEvent pmEvent = PMEvent.parseFrom(originalEvent.getEventData());
            policyEventHandler.handleEvent(pmEvent);
            currentRevision.set(revision);

            subscription.ack(event);
            snapshot(currentRevision.get());
        } catch (PMException | InvalidProtocolBufferException e) {
            logger.error("unexpected error handling event", e);
            subscription.nack(NackAction.Park, e.getMessage(), event);
        }
    }

    @Override
    public void onCancelled(PersistentSubscription subscription, Throwable exception) {
        logger.error("subscription cancelled", exception);
    }

    private void snapshot(long revision) {
        if (revision % adminPDPConfig.getSnapshotInterval() == 0) {
            try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> {
                    try {
                        snapshotService.snapshot();
                    } catch (PMException | ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException e) {
                logger.error("snapshot could not be completed", e);
            }
        }
    }
}