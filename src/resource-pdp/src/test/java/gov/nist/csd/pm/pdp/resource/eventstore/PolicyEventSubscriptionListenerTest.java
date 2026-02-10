package gov.nist.csd.pm.pdp.resource.eventstore;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pdp.proto.event.ObjectCreated;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyEventSubscriptionListenerTest {

	@Test
	void onEvent_SingleEventIsHandled() throws PMException, ExecutionException, InterruptedException {
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
				currentRevisionService
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