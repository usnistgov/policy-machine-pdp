/*
package gov.nist.csd.pm.server.shared.eventstore;

import com.eventstore.dbclient.CreatePersistentSubscriptionToStreamOptions;
import com.eventstore.dbclient.NackAction;
import com.eventstore.dbclient.PersistentSubscription;
import com.eventstore.dbclient.PersistentSubscriptionListener;
import com.eventstore.dbclient.RecordedEvent;
import com.eventstore.dbclient.ResolvedEvent;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.proto.event.PMEvent;
import gov.nist.csd.pm.server.shared.config.BaseConfig;
import gov.nist.csd.pm.server.shared.eventstore.handler.PolicyEventHandler;
import io.github.resilience4j.retry.Retry;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolicyEventSubscriber extends PersistentSubscriptionListener {

    private static final Logger logger = LoggerFactory.getLogger(PolicyEventSubscriber.class);

    private final BaseConfig config;
    private final AtomicLong currentRevision;
    private final PAP pap;
    private final String consumerGroup;
    private final EventStoreConnectionManager eventStoreConnectionManager;
    private final Retry subscribeRetry;
    private final AtomicBoolean isLockdown;
    private final PolicyEventHandler policyEventHandler;

    public PolicyEventSubscriber(BaseConfig config,
                                 AtomicLong currentRevision,
                                 PAP pap,
                                 String consumerGroup,
                                 EventStoreConnectionManager eventStoreConnectionManager,
                                 Retry subscribeRetry) {
        this.config = config;
        this.currentRevision = currentRevision;
        this.pap = pap;
        this.consumerGroup = consumerGroup;
        this.eventStoreConnectionManager = eventStoreConnectionManager;
        this.subscribeRetry = subscribeRetry;
        this.isLockdown = new AtomicBoolean(false);
        this.policyEventHandler = new PolicyEventHandler(pap);
    }

    public BaseConfig getConfig() {
        return config;
    }

    public AtomicLong getCurrentRevision() {
        return currentRevision;
    }

    public PAP getPap() {
        return pap;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public EventStoreConnectionManager getEventStoreConnectionManager() {
        return eventStoreConnectionManager;
    }

    public Retry getSubscribeRetry() {
        return subscribeRetry;
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
                "PolicyEventHandler is locked down due to corrupted event. "
                    + "Will not handle events until action is taken.");
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

                currentRevision.set(originalEvent.getRevision());

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

        retrySubscription();
    }

    public void startPersistentSubscription() {
        // check the consumer group exists for starting the persistent subscription
        createConsumerGroup();

        logger.info("starting persistent subscription");

        try {
            eventStoreConnectionManager.getOrInitSubClient()
                .subscribeToStream(
                    config.getEsdbStream(),
                    consumerGroup,
                    this
                )
                .thenAccept(e -> logger.info("persistent subscription created"))
                .join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            logger.error("error creating persistent subscription: {}", cause.getMessage());

            retrySubscription();
        }
    }

    public void shutdown() {
        eventStoreConnectionManager.shutdown();
    }

    protected void createConsumerGroup() {
        logger.info("checking consumer group {} exists", consumerGroup);

        try {
            eventStoreConnectionManager.getOrInitSubClient().createToStream(
                config.getEsdbStream(),
                consumerGroup,
                CreatePersistentSubscriptionToStreamOptions.get().fromStart()
            ).join();

            logger.info("Consumer group '{}' created.", consumerGroup);
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause.getMessage().contains("ALREADY_EXISTS")) {
                logger.info("consumer group '{}' already exists", consumerGroup);
            } else {
                logger.error("unexpected error creating consumer group {}",
                    e.getCause().getMessage());
                throw new RuntimeException(e.getCause());
            }
        }
    }

    protected void retrySubscription() {
        logger.info("retrying subscription");

        subscribeRetry.executeRunnable(() -> {
            // block the thread until the connection to eventstoredb is healthy
            eventStoreConnectionManager.blockUntilHealthy();

            // start the subscription again
            startPersistentSubscription();
        });
    }


}*/
