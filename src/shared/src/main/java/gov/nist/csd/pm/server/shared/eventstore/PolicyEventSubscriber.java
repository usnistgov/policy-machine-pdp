package gov.nist.csd.pm.server.shared.eventstore;

import com.eventstore.dbclient.EventStoreDBPersistentSubscriptionsClient;
import com.eventstore.dbclient.NackAction;
import com.eventstore.dbclient.PersistentSubscription;
import com.eventstore.dbclient.PersistentSubscriptionListener;
import com.eventstore.dbclient.RecordedEvent;
import com.eventstore.dbclient.ResolvedEvent;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.proto.event.PMEvent;
import gov.nist.csd.pm.server.shared.config.PDPConfig;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class PolicyEventSubscriber extends PersistentSubscriptionListener {

    private static final Logger logger = LoggerFactory.getLogger(PolicyEventSubscriber.class);

    private CurrentRevisionComponent currentRevision;
    private final AtomicBoolean isLockdown;
    private final PolicyEventHandler policyEventHandler;

    public PolicyEventSubscriber(PAP pap,
                                 CurrentRevisionComponent currentRevision) {
        this.currentRevision = currentRevision;
        this.isLockdown = new AtomicBoolean(false);
        this.policyEventHandler = new PolicyEventHandler(pap);
    }

    protected void lockdown() {
        isLockdown.set(true);
    }

    public AtomicBoolean isLockdown() {
        return isLockdown;
    }

    @Override
    public void onEvent(PersistentSubscription subscription, int retryCount, ResolvedEvent event) {
        if (isLockdown.get()) {
            logger.warn(
                "PolicyEventHandler is locked down due to corrupted event at revision {}. "
                    + "Will not handle events until action is taken.", currentRevision.getCurrentRevision());
            return;
        }

        RecordedEvent originalEvent = event.getOriginalEvent();
        String eventType = originalEvent.getEventType();
        if (eventType.equals("StreamCreated")) {
            subscription.ack(event);
            logger.info("skipping StreamCreated event");
            return;
        }

        // synchronize the listener so in the case of a retry, subsequent events
        // aren't handled out of order
        synchronized (this) {
            try {
                PMEvent pmEvent = PMEvent.parseFrom(originalEvent.getEventData());
                policyEventHandler.handleEvent(pmEvent);

                currentRevision.setCurrentRevision(originalEvent.getRevision());

                subscription.ack(event);
            } catch (PMException | InvalidProtocolBufferException e) {
                // retry 3 times, if an exception is still thrown, lockdown the subscription
                // so no more events are processed until the error is resolved
                // if the server is restarted but the errors is with the event,
                // the listener will attempt 3 more times to handle it before locking
                // down again
                if (retryCount < 3) {
                    logger.error(
                        "unexpected error handling event revision {}, retrying {}/3",
                        event.getPosition(), retryCount + 1, e);

                    subscription.nack(NackAction.Retry, e.getMessage(), event);
                } else {
                    logger.error(
                        "unexpected error handling event {}, retry limit reached",
                        e.getMessage(), e);
                    logger.warn(
                        "Policy Machine will only serve stale data until error is resolved");
                    subscription.nack(NackAction.Park, e.getMessage(), event);
                    lockdown();
                }
            }
        }
    }

    @Override
    public void onCancelled(PersistentSubscription subscription, Throwable exception) {
        logger.error("subscription cancelled", exception);
    }

}