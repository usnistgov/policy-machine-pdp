package gov.nist.csd.pm.pdp.shared.eventstore;

import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class CurrentRevisionService {

	private final AtomicLong currentRevision;
	private final ReentrantLock lock;
	private final Condition revisionUpdated;

	public CurrentRevisionService() {
		currentRevision = new AtomicLong(-1);
		lock = new ReentrantLock();
		revisionUpdated = lock.newCondition();
	}

	public void set(long revision) {
		this.currentRevision.set(revision);
		lock.lock();
		try {
			revisionUpdated.signalAll();
		} finally {
			lock.unlock();
		}
	}

	public long get() {
		return currentRevision.get();
	}

	/**
	 * Blocks until the current revision is at least targetRevision, or the timeout is reached.
	 *
	 * @return true if the target revision was reached, false if timed out.
	 */
	public boolean awaitRevision(long targetRevision, long timeoutMs) throws InterruptedException {
		if (currentRevision.get() >= targetRevision) {
			return true;
		}

		long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
		lock.lock();
		try {
			while (currentRevision.get() < targetRevision) {
				long remainingNanos = deadlineNanos - System.nanoTime();
				if (remainingNanos <= 0) {
					return false;
				}
				revisionUpdated.await(remainingNanos, TimeUnit.NANOSECONDS);
			}
			return true;
		} finally {
			lock.unlock();
		}
	}
}
