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

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class LatestRevisionTrackerTest {

    private LatestRevisionTracker createTracker() {
        EventStoreConnectionManager connectionManager = mock(EventStoreConnectionManager.class);
        EventStoreDBConfig config = new EventStoreDBConfig("test-stream", "test-snapshots", "localhost", 2113);
        return new LatestRevisionTracker(connectionManager, config);
    }

    @Test
    void get_throwsWhenNotInitialized() {
        LatestRevisionTracker tracker = createTracker();
        assertThrows(TimeoutException.class, () -> tracker.get(50));
    }

    @Test
    void revisionUpdates_afterManualSet() throws Exception {
        LatestRevisionTracker tracker = createTracker();
        setInitialized(tracker, true);

        Field latestRevisionField = LatestRevisionTracker.class.getDeclaredField("latestRevision");
        latestRevisionField.setAccessible(true);
        java.util.concurrent.atomic.AtomicLong latestRevision =
                (java.util.concurrent.atomic.AtomicLong) latestRevisionField.get(tracker);

        latestRevision.accumulateAndGet(5, Math::max);
        assertEquals(5, tracker.get(50));

        latestRevision.accumulateAndGet(10, Math::max);
        assertEquals(10, tracker.get(50));
    }

    @Test
    void revisionNeverDecreases() throws Exception {
        LatestRevisionTracker tracker = createTracker();
        setInitialized(tracker, true);

        Field latestRevisionField = LatestRevisionTracker.class.getDeclaredField("latestRevision");
        latestRevisionField.setAccessible(true);
        java.util.concurrent.atomic.AtomicLong latestRevision =
                (java.util.concurrent.atomic.AtomicLong) latestRevisionField.get(tracker);

        latestRevision.accumulateAndGet(10, Math::max);
        assertEquals(10, tracker.get(50));

        // Attempt to set a lower revision — should not decrease
        latestRevision.accumulateAndGet(5, Math::max);
        assertEquals(10, tracker.get(50));
    }

    @Test
    void get_timesOut_whenNotInitialized() {
        LatestRevisionTracker tracker = createTracker();
        assertThrows(TimeoutException.class, () -> tracker.get(50));
    }

    @Test
    void get_returnsRevision_whenAlreadyInitialized() throws Exception {
        LatestRevisionTracker tracker = createTracker();
        setInitialized(tracker, true);
        assertEquals(-1, tracker.get(50));
    }

    @Test
    void get_wakesUp_whenInitializedMidWait() throws Exception {
        LatestRevisionTracker tracker = createTracker();

        Thread initializer = new Thread(() -> {
            try {
                Thread.sleep(50);
                setInitialized(tracker, true);
                signalInitialized(tracker);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        initializer.start();

        long result = tracker.get(5000);
        assertEquals(-1, result); // -1 is the default latestRevision
        initializer.join();
    }

    private void setInitialized(LatestRevisionTracker tracker, boolean value) throws Exception {
        Field initializedField = LatestRevisionTracker.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        initializedField.set(tracker, value);
    }

    private void signalInitialized(LatestRevisionTracker tracker) throws Exception {
        Field lockField = LatestRevisionTracker.class.getDeclaredField("lock");
        lockField.setAccessible(true);
        ReentrantLock lock = (ReentrantLock) lockField.get(tracker);

        Field condField = LatestRevisionTracker.class.getDeclaredField("initializedCondition");
        condField.setAccessible(true);
        Condition condition = (Condition) condField.get(tracker);

        lock.lock();
        try {
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
