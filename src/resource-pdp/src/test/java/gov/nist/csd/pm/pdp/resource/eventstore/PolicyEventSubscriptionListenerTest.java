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

package gov.nist.csd.pm.pdp.resource.eventstore;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.ngac.pm.core.pap.query.model.context.NodeUserContext;
import gov.nist.csd.pm.pdp.proto.event.ObjectCreated;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyEventSubscriptionListenerTest {

	@Test
	void onEvent_SingleEventIsHandled() throws PMException {
		MemoryPAP pap = new MemoryPAP();
		pap.withIdGenerator((node, type) -> node.hashCode());
		pap.executePML(
                NodeUserContext.of(0), """
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