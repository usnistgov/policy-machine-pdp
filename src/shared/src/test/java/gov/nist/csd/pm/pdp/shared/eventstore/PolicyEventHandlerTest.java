package gov.nist.csd.pm.pdp.shared.eventstore;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.core.pap.query.GraphQuery;
import gov.nist.csd.pm.pdp.proto.event.Bootstrapped;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.shared.plugin.PluginLoader;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PolicyEventHandlerTest {

	@Test
	void testBootstrap() throws PMException {
		MemoryPAP pap = new MemoryPAP();
		PluginLoader pluginLoader = new PluginLoader();

		PolicyEventHandler policyEventHandler = new PolicyEventHandler(pap, pluginLoader);

		PMEvent pmEvent = PMEvent.newBuilder()
				.setBootstrapped(
						Bootstrapped.newBuilder()
								.setType("json")
								.setValue(
										"""
												{
													"graph": {
														"pcs": [{"id": 1, "name": "pc1"}],
														"uas": [{"id": 2, "name": "ua1", "assignments": [1]}],
														"oas": [{"id": 3, "name": "oa1", "assignments": [1]}],
														"users": [{"id": 4, "name": "u1", "assignments": [2]}],
														"objects": [{"id": 5, "name": "o1", "assignments": [3]}]
													}
												}
												"""
								)
								.putAllCreatedNodes(Map.of(
										"pc1", 1L,
										"oa1", 2L,
										"ua1", 3L,
										"u1", 4L,
										"o1", 5L
								))
				)
				.build();
		policyEventHandler.handleEvent(pmEvent);

		GraphQuery graph = pap.query().graph();
		assertTrue(graph.nodeExists(1));
		assertTrue(graph.nodeExists(2));
		assertTrue(graph.nodeExists(3));
		assertTrue(graph.nodeExists(4));
		assertTrue(graph.nodeExists(5));
	}

}