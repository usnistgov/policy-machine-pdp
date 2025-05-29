package gov.nist.csd.pm.pdp.resource.eventstore;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pdp.proto.event.ObjectCreated;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.plugin.PluginLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class PolicyEventSubscriptionListenerTest {

	@Test
	void processOrQueue_whenCanProcessTxEvents_eventsAreProcessed() throws PMException, ExecutionException, InterruptedException {
		MemoryPAP pap = new MemoryPAP();
		pap.withIdGenerator((node, type) -> node.hashCode());
		pap.executePML(new UserContext(0), """
					create pc "pc1"
					create ua "ua1" in ["pc1"]
					create oa "oa1" in ["pc1"]
					create u "u1" in ["ua1"]
					create o "o1" in ["oa1"]
					""");

		CurrentRevisionService currentRevisionService = new CurrentRevisionService();
		currentRevisionService.set(5);

		PolicyEventSubscriptionListener listener = new PolicyEventSubscriptionListener(
				pap,
				currentRevisionService,
				new PluginLoader()
		);

		listener.processOrQueue(6, List.of(
				PMEvent.newBuilder()
						.setObjectCreated(
								ObjectCreated.newBuilder()
										.setId(6)
										.setName("o2")
										.addAllDescendants(List.of((long) "oa1".hashCode()))
										.build()
						)
						.build(),
				PMEvent.newBuilder().setObjectCreated(
								ObjectCreated.newBuilder()
										.setId(7)
										.setName("o3")
										.addAllDescendants(List.of((long) "oa1".hashCode()))
										.build()
						)
						.build()
		)).get();

		assertEquals(7, currentRevisionService.get());
		assertTrue(pap.query().graph().nodeExists(6));
		assertTrue(pap.query().graph().nodeExists(7));
	}

	@Test
	void processOrQueue_whenCannotProcessTxEvents_eventsAreQueuedAndThenProcessed() throws PMException, ExecutionException, InterruptedException {
		MemoryPAP pap = new MemoryPAP();
		pap.withIdGenerator((node, type) -> node.hashCode());
		pap.executePML(new UserContext(0), """
					create pc "pc1"
					create ua "ua1" in ["pc1"]
					create oa "oa1" in ["pc1"]
					create u "u1" in ["ua1"]
					create o "o1" in ["oa1"]
					""");

		CurrentRevisionService currentRevisionService = new CurrentRevisionService();
		currentRevisionService.set(5);

		PolicyEventSubscriptionListener listener = new PolicyEventSubscriptionListener(
				pap,
				currentRevisionService,
				new PluginLoader()
		);

		new Thread(() -> {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

			listener.onEvent(null, ResolvedEventMock.of(6, PMEvent.newBuilder()
					.setObjectCreated(
							ObjectCreated.newBuilder()
									.setId(6)
									.setName("o2")
									.addAllDescendants(List.of((long) "oa1".hashCode()))
									.build()
					)
					.build()));
		}).start();

		listener.processOrQueue(7, List.of(
				PMEvent.newBuilder()
						.setObjectCreated(
								ObjectCreated.newBuilder()
										.setId(7)
										.setName("o3")
										.addAllDescendants(List.of((long) "oa1".hashCode()))
										.build()
						)
						.build(),
				PMEvent.newBuilder().setObjectCreated(
								ObjectCreated.newBuilder()
										.setId(8)
										.setName("o4")
										.addAllDescendants(List.of((long) "oa1".hashCode()))
										.build()
						)
						.build()
		)).get();

		assertEquals(8, currentRevisionService.get());
		assertTrue(pap.query().graph().nodeExists(6));
		assertTrue(pap.query().graph().nodeExists(7));
		assertTrue(pap.query().graph().nodeExists(8));
	}

	@Test
	void onEvent_whenNoWaitingTxs_SingleEventIsHandled() throws PMException, ExecutionException, InterruptedException {
		MemoryPAP pap = new MemoryPAP();
		pap.withIdGenerator((node, type) -> node.hashCode());
		pap.executePML(new UserContext(0), """
					create pc "pc1"
					create ua "ua1" in ["pc1"]
					create oa "oa1" in ["pc1"]
					create u "u1" in ["ua1"]
					create o "o1" in ["oa1"]
					""");

		CurrentRevisionService currentRevisionService = new CurrentRevisionService();
		currentRevisionService.set(5);

		PolicyEventSubscriptionListener listener = new PolicyEventSubscriptionListener(
				pap,
				currentRevisionService,
				new PluginLoader()
		);

		listener.onEvent(null, ResolvedEventMock.of(6, PMEvent.newBuilder()
				.setObjectCreated(
						ObjectCreated.newBuilder()
								.setId(6)
								.setName("o2")
								.addAllDescendants(List.of((long) "oa1".hashCode()))
								.build()
				)
				.build()));

		assertEquals(6, currentRevisionService.get());
		assertTrue(pap.query().graph().nodeExists(6));
	}

}