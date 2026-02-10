package gov.nist.csd.pm.pdp.shared.eventstore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CurrentRevisionServiceTest {

    private CurrentRevisionService service;

    @BeforeEach
    void setUp() {
        service = new CurrentRevisionService();
    }

    @Test
    void awaitRevision_alreadyCaughtUp_returnsImmediately() throws InterruptedException {
        service.set(10);
        assertTrue(service.awaitRevision(10, 1000));
    }

    @Test
    void awaitRevision_aheadOfTarget_returnsImmediately() throws InterruptedException {
        service.set(15);
        assertTrue(service.awaitRevision(10, 1000));
    }

    @Test
    void awaitRevision_catchesUpMidWait_returnsTrue() throws InterruptedException {
        service.set(5);

        Thread updater = new Thread(() -> {
            try {
                Thread.sleep(50);
                service.set(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        updater.start();

        assertTrue(service.awaitRevision(10, 2000));
        updater.join();
    }

    @Test
    void awaitRevision_timeout_returnsFalse() throws InterruptedException {
        service.set(5);
        assertFalse(service.awaitRevision(100, 50));
    }

    @Test
    void awaitRevision_immediateWakeOnSignal() throws InterruptedException {
        service.set(5);

        AtomicBoolean result = new AtomicBoolean(false);
        Thread waiter = new Thread(() -> {
            try {
                result.set(service.awaitRevision(10, 5000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waiter.start();

        // small delay to ensure waiter is blocked
        Thread.sleep(50);
        long before = System.currentTimeMillis();
        service.set(10);
        waiter.join(2000);

        long elapsed = System.currentTimeMillis() - before;
        assertTrue(result.get(), "Should have returned true after signal");
        assertTrue(elapsed < 1000, "Should wake up quickly after signal, took " + elapsed + "ms");
    }

    @Test
    void set_and_get_workCorrectly() {
        assertEquals(-1, service.get());
        service.set(42);
        assertEquals(42, service.get());
    }

    @Test
    void awaitRevision_negativeTarget_returnsImmediately() throws InterruptedException {
        // default is -1, so target of -1 should match
        assertTrue(service.awaitRevision(-1, 1000));
    }
}
