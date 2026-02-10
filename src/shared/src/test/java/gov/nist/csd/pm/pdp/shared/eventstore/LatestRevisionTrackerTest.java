package gov.nist.csd.pm.pdp.shared.eventstore;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
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
