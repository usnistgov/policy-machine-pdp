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
