package gov.nist.csd.pm.pdp.resource;

import gov.nist.csd.pm.pdp.resource.config.ResourcePDPConfig;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

@Component
public final class RevisionCatchUpGate {

	private final AtomicLong requiredRevision = new AtomicLong(0);

	private final ReentrantLock lock = new ReentrantLock();
	private final Condition advanced = lock.newCondition();

	private final ResourcePDPConfig config;
	private final CurrentRevisionService currentRevisionService;

	public RevisionCatchUpGate(ResourcePDPConfig config, CurrentRevisionService currentRevisionService) {
		this.config = config;
		this.currentRevisionService = currentRevisionService;
	}

	public boolean isClosed() {
		return currentRevisionService.get() < requiredRevision.get();
	}

	/**
	 * Set the revision to wait for. If the given one is less than the required one, the required one will be returned.
	 * @param revision The revision to wait for.
	 * @return The revision to wait for.
	 */
	public long setWaitForRevision(long revision) {
		return requiredRevision.accumulateAndGet(revision, Math::max);
	}

	/**
	 * Wait up to timeout for currentRevision >= waitForRevision.
	 *
	 * @return true if caught up, false if timed out.
	 * @throws InterruptedException
	 */
	public boolean awaitCatchUp() throws InterruptedException {
		long timeoutMillis = config.getEppSyncCatchUpTimeout();
		long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

		long deadlineNanos = System.nanoTime() + timeoutNanos;
		long pollNanos = TimeUnit.MILLISECONDS.toNanos(10);

		while (true) {
			if (currentRevisionService.get() >= requiredRevision.get()) {
				return true;
			}

			long remainingNanos = deadlineNanos - System.nanoTime();
			if (remainingNanos <= 0) {
				return false;
			}

			LockSupport.parkNanos(Math.min(remainingNanos, pollNanos));

			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
		}
	}
}