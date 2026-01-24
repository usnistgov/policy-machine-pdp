package gov.nist.csd.pm.pdp.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import gov.nist.csd.pm.pdp.resource.config.ResourcePDPConfig;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevisionCatchUpGateTest {

	@Mock
	private ResourcePDPConfig config;

	@Mock
	private CurrentRevisionService currentRevisionService;

	private AtomicLong currentRevision;
	private RevisionCatchUpGate gate;

	@BeforeEach
	void setUp() {
		currentRevision = new AtomicLong(0);

		// currentRevisionService.get() reads from a real AtomicLong so we can advance it during the test
		when(currentRevisionService.get()).thenAnswer(inv -> currentRevision.get());

		gate = new RevisionCatchUpGate(config, currentRevisionService);
	}

	@Test
	void isClosed_falseWhenCaughtUp() {
		gate.setWaitForRevision(0);
		currentRevision.set(0);

		assertFalse(gate.isClosed());
	}

	@Test
	void isClosed_trueWhenBehind() {
		gate.setWaitForRevision(5);
		currentRevision.set(4);

		assertTrue(gate.isClosed());
	}

	@Test
	void setWaitForRevision_usesMaxAndNeverDecreases() {
		assertEquals(5, gate.setWaitForRevision(5));
		assertEquals(5, gate.setWaitForRevision(3));
		assertEquals(7, gate.setWaitForRevision(7));
		assertEquals(7, gate.setWaitForRevision(6));

		currentRevision.set(6);
		assertTrue(gate.isClosed());

		currentRevision.set(7);
		assertFalse(gate.isClosed());
	}

	@Test
	void awaitCatchUp_returnsTrueImmediatelyWhenAlreadyCaughtUp() throws Exception {
		when(config.getEppSyncCatchUpTimeout()).thenReturn(200);

		gate.setWaitForRevision(5);
		currentRevision.set(5);

		assertTrue(gate.awaitCatchUp());
	}

	@Test
	void awaitCatchUp_returnsTrueWhenRevisionAdvancesBeforeTimeout() throws Exception {
		when(config.getEppSyncCatchUpTimeout()).thenReturn(500);

		gate.setWaitForRevision(5);
		currentRevision.set(0);

		Thread advancer = new Thread(() -> {
			try {
				Thread.sleep(50);
			} catch (InterruptedException ignored) {
			}
			currentRevision.set(5);
		});
		advancer.start();

		assertTrue(gate.awaitCatchUp());

		advancer.join();
	}

	@Test
	void awaitCatchUp_returnsFalseOnTimeout() throws Exception {
		when(config.getEppSyncCatchUpTimeout()).thenReturn(100);

		gate.setWaitForRevision(5);
		currentRevision.set(0);

		assertFalse(gate.awaitCatchUp());
	}

	@Test
	void awaitCatchUp_throwsInterruptedExceptionWhenInterrupted() throws Exception {
		when(config.getEppSyncCatchUpTimeout()).thenReturn(1000);

		gate.setWaitForRevision(5);
		currentRevision.set(0);

		CountDownLatch started = new CountDownLatch(1);
		AtomicLong resultMarker = new AtomicLong(-1);

		Thread waiter = new Thread(() -> {
			started.countDown();
			try {
				gate.awaitCatchUp();
				resultMarker.set(1); // should not happen
			} catch (InterruptedException e) {
				resultMarker.set(2); // expected
			}
		});

		waiter.start();

		assertTrue(started.await(200, TimeUnit.MILLISECONDS));

		// interrupt while it's waiting
		waiter.interrupt();
		waiter.join(500);

		assertEquals(2, resultMarker.get(), "Expected awaitCatchUp to throw InterruptedException");
	}
}