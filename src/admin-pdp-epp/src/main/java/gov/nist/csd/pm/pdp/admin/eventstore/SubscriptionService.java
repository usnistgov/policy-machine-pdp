package gov.nist.csd.pm.pdp.admin.eventstore;

import com.eventstore.dbclient.CreatePersistentSubscriptionToStreamOptions;
import com.eventstore.dbclient.PersistentSubscriptionToStreamInfo;
import gov.nist.csd.pm.pdp.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.pdp.admin.pap.Neo4jBootstrapper;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Aspect
@Service
public class SubscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    private final PolicyEventPersistentSubscriptionListener policyEventPersistentSubscriptionListener;
    private final EventStoreConnectionManager eventStoreConnectionManager;
    private final EventStoreDBConfig eventStoreDBConfig;
    private final AdminPDPConfig adminPDPConfig;
    private final Retry retry;
    private final CurrentRevisionService currentRevisionService;

    public SubscriptionService(EventStoreConnectionManager eventStoreConnectionManager,
                               PolicyEventPersistentSubscriptionListener policyEventPersistentSubscriptionListener,
                               EventStoreDBConfig eventStoreDBConfig,
                               AdminPDPConfig adminPDPConfig,
                               CurrentRevisionService currentRevisionService,
                               Neo4jBootstrapper neo4jBootstrapper) {
        this.eventStoreConnectionManager = eventStoreConnectionManager;
        this.policyEventPersistentSubscriptionListener = policyEventPersistentSubscriptionListener;
        this.eventStoreDBConfig = eventStoreDBConfig;
        this.adminPDPConfig = adminPDPConfig;
        this.retry = Retry.of("subscriptionRetry", RetryConfig.custom()
                .maxAttempts(Integer.MAX_VALUE)
                .failAfterMaxAttempts(true)
                .intervalFunction(IntervalFunction.ofExponentialBackoff())
                .build());
        this.currentRevisionService = currentRevisionService;
    }

    @PostConstruct
    public void initSubscription() {
        // start subscription attempt in new thread to avoid blocking the spring thread
        Thread t = new Thread(this::startSubscriptionWithRetry);
        t.start();
    }

    @Pointcut("execution(* gov.nist.csd.pm.pdp.admin.eventstore.PolicyEventPersistentSubscriptionListener.onCancelled(..))")
    public void onOnCancelled() {
    }

    @AfterReturning("onOnCancelled()")
    public void afterOnCancelled() {
        logger.info("afterOnCancelled()");
        startSubscriptionWithRetry();
    }

    public void startSubscriptionWithRetry() {
        retry.executeRunnable(() -> {
            logger.info("Starting persistent subscription...");
            try {
                startSubscription();
                logger.info("Persistent subscription up");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Retry thread interrupted", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new RuntimeException("Subscription retry failed", e);
            }
        });
    }

    private void startSubscription() throws InterruptedException, ExecutionException, TimeoutException {
        // ensure the consumer group exists
        createConsumerGroup();

        String eventStream = eventStoreDBConfig.getEventStream();
        String group = adminPDPConfig.getEsdbConsumerGroup();

        // create the persistent subscription
        eventStoreConnectionManager.getOrInitPersistentSubClient()
                .subscribeToStream(eventStream, group, policyEventPersistentSubscriptionListener)
                .get(5, TimeUnit.SECONDS);

        setCurrentRevision(eventStream, group);
    }

    private void createConsumerGroup() {
        String esdbConsumerGroup = adminPDPConfig.getEsdbConsumerGroup();
        logger.info("checking consumer group {} exists", esdbConsumerGroup);

        try {
            eventStoreConnectionManager.getOrInitPersistentSubClient()
                    .createToStream(
                            eventStoreDBConfig.getEventStream(),
                            esdbConsumerGroup,
                            CreatePersistentSubscriptionToStreamOptions.get().fromStart()
                    ).get(5, TimeUnit.SECONDS);

            logger.info("Consumer group '{}' created.", esdbConsumerGroup);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Throwable cause = e.getCause();
            if (cause != null && cause.getMessage().contains("ALREADY_EXISTS")) {
                logger.info("consumer group '{}' already exists", esdbConsumerGroup);
            } else {
                logger.error("Unexpected error creating consumer group", e);
                throw new RuntimeException(e.getCause());
            }
        }
    }

    private void setCurrentRevision(String eventStream, String group) throws ExecutionException, InterruptedException {
        // get the current revision
        Optional<PersistentSubscriptionToStreamInfo> info = eventStoreConnectionManager
                .getOrInitPersistentSubClient()
                .getInfoToStream(eventStream, group)
                .get();

        if(info.isPresent()) {
            Optional<Long> lastKnownEventRevision = info.get().getStats().getLastKnownEventRevision();
            if (lastKnownEventRevision.isPresent()) {
                long lastCheckpointedRevisionValue = lastKnownEventRevision.get();
                logger.info("lastKnownEventRevision is {}", lastCheckpointedRevisionValue);
                currentRevisionService.set(lastCheckpointedRevisionValue);
            } else {
                logger.info("lastKnownEventRevision not found for stream={} and group={}", eventStream, group);
            }
        } else {
            logger.error("Getting info on stream={} and group={} returned null, indicating an error with the subscription.",
                         eventStream, group);
            throw new RuntimeException("No info available on stream " + eventStream + " for group " + group);
        }
    }
} 