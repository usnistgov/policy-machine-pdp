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
