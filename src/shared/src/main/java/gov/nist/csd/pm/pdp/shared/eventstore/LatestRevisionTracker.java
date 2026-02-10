package gov.nist.csd.pm.pdp.shared.eventstore;

import com.eventstore.dbclient.*;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Subscribes to the EventStoreDB stream (from end) and tracks the latest known revision.
 * Does not process events — only records revision numbers for consistency checks.
 */
@Service
public class LatestRevisionTracker {

	private static final Logger logger = LoggerFactory.getLogger(LatestRevisionTracker.class);

	private final EventStoreConnectionManager eventStoreConnectionManager;
	private final EventStoreDBConfig eventStoreDBConfig;
	private final AtomicLong latestRevision;
	private final Retry retry;

	private volatile boolean initialized;
	private final ReentrantLock lock;
	private final Condition initializedCondition;

	public LatestRevisionTracker(EventStoreConnectionManager eventStoreConnectionManager,
	                             EventStoreDBConfig eventStoreDBConfig) {
		this.eventStoreConnectionManager = eventStoreConnectionManager;
		this.eventStoreDBConfig = eventStoreDBConfig;
		this.latestRevision = new AtomicLong(-1);
		this.initialized = false;
		this.lock = new ReentrantLock();
		this.initializedCondition = lock.newCondition();

		this.retry = Retry.of("latestRevisionTrackerRetry", RetryConfig.custom()
				.maxAttempts(Integer.MAX_VALUE)
				.intervalFunction(IntervalFunction.ofExponentialBackoff(
						1000,
						2,
						30000
				))
				.build());
	}

	@PostConstruct
	public void init() {
		Thread daemonThread = new Thread(this::startSubscriptionWithRetry, "latest-revision-tracker");
		daemonThread.setDaemon(true);
		daemonThread.start();
	}

	/**
	 * Returns the latest known revision. If the tracker is not yet initialized,
	 * blocks up to {@code timeoutMs} milliseconds waiting for the subscription to connect.
	 *
	 * @return the latest revision
	 * @throws InterruptedException if the calling thread is interrupted while waiting
	 * @throws TimeoutException if the tracker is not initialized within the timeout
	 */
	public long get(long timeoutMs) throws InterruptedException, TimeoutException {
		if (!initialized) {
			long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
			lock.lock();
			try {
				while (!initialized) {
					long remainingNanos = deadlineNanos - System.nanoTime();
					if (remainingNanos <= 0) {
						throw new TimeoutException("Latest revision tracker not initialized within timeout");
					}
					initializedCondition.await(remainingNanos, TimeUnit.NANOSECONDS);
				}
			} finally {
				lock.unlock();
			}
		}
		return latestRevision.get();
	}

	private void startSubscriptionWithRetry() {
		retry.executeRunnable(() -> {
			try {
				startSubscription();
			} catch (Exception e) {
				logger.error("Failed to start latest revision tracker subscription", e);
				throw new RuntimeException("Subscription failed", e);
			}
		});
	}

	private void startSubscription() throws Exception {
		String stream = eventStoreDBConfig.getEventStream();
		logger.info("Starting latest revision tracker subscription on stream: {}", stream);

		SubscribeToStreamOptions options = SubscribeToStreamOptions.get()
				.fromEnd();

		eventStoreConnectionManager.getOrInitClient()
				.subscribeToStream(stream, new SubscriptionListener() {
					@Override
					public void onEvent(Subscription subscription, ResolvedEvent event) {
						long revision = event.getEvent().getRevision();
						latestRevision.accumulateAndGet(revision, Math::max);
						logger.debug("Latest revision tracker updated to {}", revision);
					}

					@Override
					public void onCancelled(Subscription subscription, Throwable exception) {
						logger.warn("Latest revision tracker subscription cancelled", exception);
						initialized = false;
						startSubscriptionWithRetry();
					}
				}, options)
				.get(5, TimeUnit.SECONDS);

		// Read the current latest revision so we have an accurate starting point
		readCurrentLatestRevision(stream);

		initialized = true;
		lock.lock();
		try {
			initializedCondition.signalAll();
		} finally {
			lock.unlock();
		}

		logger.info("Latest revision tracker initialized, current latest revision: {}", latestRevision.get());
	}

	private void readCurrentLatestRevision(String stream) throws Exception {
		ReadStreamOptions readOptions = ReadStreamOptions.get()
				.fromEnd()
				.backwards()
				.maxCount(1);

		ReadResult result = eventStoreConnectionManager.getOrInitClient()
				.readStream(stream, readOptions)
				.get(5, TimeUnit.SECONDS);

		if (!result.getEvents().isEmpty()) {
			long revision = result.getEvents().getFirst().getEvent().getRevision();
			latestRevision.accumulateAndGet(revision, Math::max);
		}
	}
}
