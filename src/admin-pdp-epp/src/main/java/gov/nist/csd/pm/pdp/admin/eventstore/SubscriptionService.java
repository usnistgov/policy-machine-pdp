/*
 * This Software (Policy Machine) is being made available as a public service by the
 * National Institute of Standards and Technology (NIST), an Agency of the United
 * States Department of Commerce. This software was developed in part by employees of
 * NIST and in part by NIST contractors. Copyright in portions of this software that
 * were developed by NIST contractors has been licensed or assigned to NIST. Pursuant
 * to Title 17 United States Code Section 105, works of NIST employees are not
 * subject to copyright protection in the United States. However, NIST may hold
 * international copyright in software created by its employees and domestic
 * copyright (or licensing rights) in portions of software that were assigned or
 * licensed to NIST.
 *
 * This file is part of the admin-pdp-epp module, which compiles against
 * and embeds org.neo4j:neo4j (GPLv3, Community Edition). As a combined work, this
 * module is distributed under the GNU General Public License v3.0, not the CC BY 4.0
 * license used elsewhere in this repository. See admin-pdp-epp/LICENSE for the full text.
 */

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
import gov.nist.csd.pm.pdp.shared.config.DefaultMode;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Aspect
@Service
@DefaultMode
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