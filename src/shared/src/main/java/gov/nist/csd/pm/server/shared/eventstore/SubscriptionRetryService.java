package gov.nist.csd.pm.server.shared.eventstore;

import com.eventstore.dbclient.CreatePersistentSubscriptionToStreamOptions;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBPersistentSubscriptionsClient;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.server.shared.config.PDPConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.PostConstruct;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Aspect
@Service
public class SubscriptionRetryService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRetryService.class);

    private final PDPConfig pdpConfig;
    private final PolicyEventSubscriber policyEventSubscriber;
    private EventStoreDBPersistentSubscriptionsClient esdbSubClient;
    private Retry retry;
    private EventStoreDBClientSettings eventStoreDBClientSettings;

    public SubscriptionRetryService(PDPConfig pdpConfig,
                                    EventStoreDBPersistentSubscriptionsClient esdbSubClient,
                                    PAP pap,
                                    CurrentRevisionComponent currentRevision,
                                    EventStoreDBClientSettings eventStoreDBClientSettings,
                                    PolicyEventSubscriber policyEventSubscriber) {
        this.pdpConfig = pdpConfig;
        this.esdbSubClient = esdbSubClient;
        this.eventStoreDBClientSettings = eventStoreDBClientSettings;
        this.policyEventSubscriber = policyEventSubscriber;
        this.retry = Retry.of("subscriptionRetry", RetryConfig.custom()
            .maxAttempts(Integer.MAX_VALUE)
            .failAfterMaxAttempts(true)
            .intervalFunction(IntervalFunction.ofExponentialBackoff())
            .build());
    }

    @PostConstruct
    public void initSubscription() {
        logger.info("Initializing persistent subscription to event store");

        while (true) {
            if (esdbSubClient.isShutdown()) {
                esdbSubClient = EventStoreDBPersistentSubscriptionsClient.create(eventStoreDBClientSettings);
            }

            try {
                createConsumerGroup();

                esdbSubClient
                    .subscribeToStream(
                        pdpConfig.getEsdbStream(),
                        pdpConfig.getEsdbConsumerGroup(),
                        policyEventSubscriber
                    )
                    .get();
                break;
            } catch (InterruptedException | ExecutionException | RuntimeException e) {
                logger.error("Subscription failed, trying again in 5 seconds", e);
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        logger.info("Persistent subscription initialized");
    }

    public void subscribeWithRetry() {
        retry.executeRunnable(() -> {
            logger.info("Attempting to retry subscription...");

            try {
                createConsumerGroup();

                esdbSubClient
                    .subscribeToStream(
                        pdpConfig.getEsdbStream(),
                        pdpConfig.getEsdbConsumerGroup(),
                        policyEventSubscriber
                    )
                    .get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }

            logger.info("Persistent subscription re-initialized");
        });
    }

    @Pointcut("execution(* gov.nist.csd.pm.server.shared.eventstore.PolicyEventSubscriber.onCancelled(..))")
    public void onOnCancelled() {
    }

    @AfterReturning("onOnCancelled()")
    public void afterOnCancelled(JoinPoint joinPoint) {
        logger.info("afterOnCancelled()");
        subscribeWithRetry();
    }

    protected void createConsumerGroup() {
        String esdbConsumerGroup = pdpConfig.getEsdbConsumerGroup();
        logger.info("checking consumer group {} exists", esdbConsumerGroup);

        try {
            esdbSubClient.createToStream(
                pdpConfig.getEsdbStream(),
                esdbConsumerGroup,
                CreatePersistentSubscriptionToStreamOptions.get().fromStart()
            ).get(3, TimeUnit.SECONDS);

            logger.info("Consumer group '{}' created.", esdbConsumerGroup);
        } catch (ExecutionException | InterruptedException e) {
            Throwable cause = e.getCause();
            if (cause != null && cause.getMessage().contains("ALREADY_EXISTS")) {
                logger.info("consumer group '{}' already exists", esdbConsumerGroup);
            } else {
                logger.error("Unexpected error creating consumer group: {}",
                    e.getCause().getMessage());
                throw new RuntimeException(e.getCause());
            }
        } catch (TimeoutException e) {
            logger.error("Timeout creating consumer group", e);
            throw new RuntimeException(e);
        }
    }
} 