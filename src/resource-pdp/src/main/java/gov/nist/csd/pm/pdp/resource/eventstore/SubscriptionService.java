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
 * licensed to NIST. To the extent that NIST holds copyright in this software, it is
 * being made available under the Creative Commons Attribution 4.0 International
 * license (CC BY 4.0). The disclaimers of the CC BY 4.0 license apply to all parts
 * of the software developed or licensed by NIST.
 *
 * ACCESS THE FULL CC BY 4.0 LICENSE HERE:
 * https://creativecommons.org/licenses/by/4.0/legalcode
 */

package gov.nist.csd.pm.pdp.resource.eventstore;

import com.eventstore.dbclient.*;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import gov.nist.csd.pm.pdp.shared.eventstore.SnapshotService;
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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Aspect
@Service
public class SubscriptionService {

	private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

	private final PolicyEventSubscriptionListener policyEventSubscriptionListener;
	private final EventStoreConnectionManager eventStoreConnectionManager;
	private final EventStoreDBConfig eventStoreDBConfig;
	private final SnapshotService snapshotService;
	private final CurrentRevisionService currentRevisionService;
	private final Retry retry;

	public SubscriptionService(EventStoreConnectionManager eventStoreConnectionManager,
	                           PolicyEventSubscriptionListener policyEventSubscriptionListener,
	                           EventStoreDBConfig eventStoreDBConfig,
	                           SnapshotService snapshotService,
	                           CurrentRevisionService currentRevisionService) {
		this.eventStoreConnectionManager = eventStoreConnectionManager;
		this.policyEventSubscriptionListener = policyEventSubscriptionListener;
		this.eventStoreDBConfig = eventStoreDBConfig;
		this.snapshotService = snapshotService;
		this.currentRevisionService = currentRevisionService;

		this.retry = Retry.of("subscriptionRetry", RetryConfig.custom()
				.maxAttempts(Integer.MAX_VALUE)
				.intervalFunction(IntervalFunction.ofExponentialBackoff(
						1000,
						2,
						30000
				))
				.build());
	}

	@PostConstruct
	public void initSubscription() throws ExecutionException, InterruptedException, PMException, InvalidProtocolBufferException, TimeoutException {
		long snapshotRevision = restoreLatestSnapshot();
		catchUpEvents(snapshotRevision);
		startSubscription();
	}

	@Pointcut("execution(* gov.nist.csd.pm.pdp.resource.eventstore.PolicyEventSubscriptionListener.onCancelled(..))")
	public void onOnCancelled() {
	}

	@AfterReturning("onOnCancelled()")
	public void afterOnCancelled() {
		logger.info("onCancelled() called");
		startSubscriptionWithRetry();
	}

	private void startSubscriptionWithRetry() {
		retry.executeRunnable(() -> {
			try{
				startSubscription();
			} catch (InterruptedException e) {
				logger.error("Subscription interrupted", e);
				Thread.currentThread().interrupt();
				throw new RuntimeException("Subscription interrupted", e);
			} catch (ExecutionException | TimeoutException e) {
				logger.error("Subscription failed", e);
				throw new RuntimeException("Subscription failed", e);
			}
		});
	}

	private void startSubscription() throws ExecutionException, InterruptedException, TimeoutException {
		logger.info("Subscribing to event stream: {}", eventStoreDBConfig.getEventStream());

		SubscribeToStreamOptions options = SubscribeToStreamOptions.get()
				.fromRevision(currentRevisionService.get());
		String stream = eventStoreDBConfig.getEventStream();

		// create the subscription
		Subscription subscription = eventStoreConnectionManager.getOrInitClient()
				.subscribeToStream(
						stream,
						policyEventSubscriptionListener,
						options
				)
				.get(3, TimeUnit.SECONDS);
		logger.info("Subscribed to {} with id {}", stream, subscription.getSubscriptionId());
	}

	private long restoreLatestSnapshot() throws PMException, InvalidProtocolBufferException, ExecutionException, InterruptedException {
		try {
			return snapshotService.restoreLatestSnapshot();
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (!(cause instanceof StreamNotFoundException)) {
				throw e;
			}

			return 0;
		}
	}

	private void catchUpEvents(long snapshotRevision) throws ExecutionException, InterruptedException {
		String eventStream = eventStoreDBConfig.getEventStream();
		ReadStreamOptions readStreamOptions = ReadStreamOptions.get()
				.fromRevision(snapshotRevision)
				.forwards();

		ReadResult readResult = eventStoreConnectionManager.getOrInitClient()
				.readStream(eventStream, readStreamOptions)
				.get();

		List<ResolvedEvent> events = readResult.getEvents();
		for (ResolvedEvent event : events) {
			policyEventSubscriptionListener.onEvent(null, event);
		}
	}
}
