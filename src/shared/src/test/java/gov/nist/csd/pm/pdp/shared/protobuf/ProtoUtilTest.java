package gov.nist.csd.pm.pdp.shared.protobuf;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.node.Node;
import gov.nist.csd.pm.core.common.graph.node.NodeType;
import gov.nist.csd.pm.core.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.core.common.prohibition.ContainerCondition;
import gov.nist.csd.pm.core.common.prohibition.Prohibition;
import gov.nist.csd.pm.core.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.core.pap.query.GraphQuery;
import gov.nist.csd.pm.core.pap.query.PolicyQuery;
import gov.nist.csd.pm.core.pap.query.model.context.TargetContext;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pap.query.model.explain.Explain;
import gov.nist.csd.pm.core.pap.query.model.explain.ExplainNode;
import gov.nist.csd.pm.core.pap.query.model.explain.PolicyClassExplain;
import gov.nist.csd.pm.pdp.proto.model.ExplainProto;
import gov.nist.csd.pm.pdp.proto.model.NodeProto;
import gov.nist.csd.pm.pdp.proto.model.PolicyClassExplainProto;
import gov.nist.csd.pm.pdp.proto.model.ProhibitionProto;
import gov.nist.csd.pm.pdp.proto.query.IdList;
import gov.nist.csd.pm.pdp.proto.query.TargetContextProto;
import gov.nist.csd.pm.pdp.proto.query.UserContextProto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static gov.nist.csd.pm.core.common.graph.node.NodeType.OA;
import static gov.nist.csd.pm.core.common.graph.node.NodeType.U;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProtoUtilTest {

	@Test
	void testBuildExplainProto_NullExplain() {
		// Test when the input `explain` object is null
		ExplainProto result = ProtoUtil.buildExplainProto(null, mock(PolicyQuery.class));
		Assertions.assertNotNull(result);
		Assertions.assertTrue(result.getPrivilegesList().isEmpty());
		Assertions.assertTrue(result.getDeniedPrivilegesList().isEmpty());
		Assertions.assertTrue(result.getPolicyClassesList().isEmpty());
		Assertions.assertTrue(result.getProhibitionsList().isEmpty());
	}

	@Test
	void testBuildExplainProto_WithPrivilegesOnly() {
		// Mock privileges present in the Explain object
		AccessRightSet mockPrivileges = new AccessRightSet(List.of("read", "write"));
		Explain explain = mock(Explain.class);
		when(explain.getPrivileges()).thenReturn(mockPrivileges);
		when(explain.getPolicyClasses()).thenReturn(List.of());
		when(explain.getProhibitions()).thenReturn(List.of());
		when(explain.getDeniedPrivileges()).thenReturn(new AccessRightSet());

		// Run the method and validate results
		ExplainProto result = ProtoUtil.buildExplainProto(explain, mock(PolicyQuery.class));
		assertFalse(result.getPrivilegesList().isEmpty());
		assertEquals(List.of("read", "write"), result.getPrivilegesList());
		assertTrue(result.getPolicyClassesList().isEmpty());
		assertTrue(result.getProhibitionsList().isEmpty());
	}

	@Test
	void testBuildExplainProto_WithPolicyClasses() {
		// Mock data for a single policy class explain
		Node mockNode = new Node(1L, "PolicyClass1", NodeType.PC);
		ExplainNode mockExplainNode = mock(ExplainNode.class);
		when(mockExplainNode.node()).thenReturn(mockNode);
		when(mockExplainNode.associations()).thenReturn(List.of());
		PolicyClassExplain policyClassExplain = mock(PolicyClassExplain.class);
		when(policyClassExplain.pc()).thenReturn(mockNode);
		when(policyClassExplain.paths()).thenReturn(List.of(List.of(mockExplainNode)));
		when(policyClassExplain.arset()).thenReturn(new AccessRightSet(List.of("read")));

		Explain explain = mock(Explain.class);
		when(explain.getPrivileges()).thenReturn(new AccessRightSet());
		when(explain.getPolicyClasses()).thenReturn(List.of(policyClassExplain));
		when(explain.getProhibitions()).thenReturn(List.of());
		when(explain.getDeniedPrivileges()).thenReturn(new AccessRightSet());

		// Run the method and validate constructed paths
		ExplainProto result = ProtoUtil.buildExplainProto(explain, mock(PolicyQuery.class));
		assertEquals(1, result.getPolicyClassesCount());
		PolicyClassExplainProto pcProto = result.getPolicyClasses(0);
		assertEquals(1L, pcProto.getPc().getId());
		assertEquals("PolicyClass1", pcProto.getPc().getName());
		assertEquals(List.of("read"), pcProto.getArsetList());
		assertEquals(1, pcProto.getPathsCount());
		assertEquals(1L, pcProto.getPaths(0).getNodes(0).getNode().getId());
	}

	@Test
	void testBuildExplainProto_WithProhibitions() throws PMException {
		// Mock a prohibition
		Prohibition prohibition = new Prohibition(
				"Prohibition1", new ProhibitionSubject(1L),
				new AccessRightSet(List.of("execute")), false, List.of()
		);

		Explain explain = mock(Explain.class);
		when(explain.getPrivileges()).thenReturn(new AccessRightSet());
		when(explain.getPolicyClasses()).thenReturn(List.of());
		when(explain.getProhibitions()).thenReturn(List.of(prohibition));
		when(explain.getDeniedPrivileges()).thenReturn(new AccessRightSet());

		PolicyQuery policyQuery = mock(PolicyQuery.class);
		when(policyQuery.graph()).thenReturn(mock(GraphQuery.class));
		when(policyQuery.graph().getNodeById(1L)).thenReturn(new Node(1L, "test", U));

		// Run the method and validate prohibition results
		ExplainProto result = ProtoUtil.buildExplainProto(explain, policyQuery);
		assertEquals(1, result.getProhibitionsCount());
		ProhibitionProto prohibitionProto = result.getProhibitions(0);
		assertEquals("Prohibition1", prohibitionProto.getName());
		assertEquals(1L, prohibitionProto.getNode().getId());
		assertEquals(List.of("execute"), prohibitionProto.getArsetList());
		assertFalse(prohibitionProto.getIntersection());
	}

	@Test
	void testBuildExplainProto_WithDeniedPrivileges() {
		// Mock denied privileges
		AccessRightSet mockDeniedPrivileges = new AccessRightSet(List.of("delete", "update"));
		Explain explain = mock(Explain.class);
		when(explain.getPrivileges()).thenReturn(new AccessRightSet());
		when(explain.getPolicyClasses()).thenReturn(List.of());
		when(explain.getProhibitions()).thenReturn(List.of());
		when(explain.getDeniedPrivileges()).thenReturn(mockDeniedPrivileges);

		// Run the method and validate denied privileges
		ExplainProto result = ProtoUtil.buildExplainProto(explain, mock(PolicyQuery.class));
		assertFalse(result.getDeniedPrivilegesList().isEmpty());
		assertEquals(List.of("delete", "update"), result.getDeniedPrivilegesList());
	}

	@Test
	void testFromTargetContextProto_WithId() throws PMException {
		// Mocking the proto object for the ID case
		TargetContextProto protoMock = mock(TargetContextProto.class);
		when(protoMock.getTargetCase()).thenReturn(TargetContextProto.TargetCase.ID);
		when(protoMock.getId()).thenReturn(456L);

		// Invoking the method and verifying the result
		TargetContext result = ProtoUtil.fromTargetContextProto(protoMock);
		assertEquals(new TargetContext(456L), result);
	}

	@Test
	void testFromTargetContextProto_WithAttributes() throws PMException {
		// Mocking the proto object for the ATTRIBUTES case
		TargetContextProto protoMock = mock(TargetContextProto.class);
		IdList idListMock = mock(IdList.class);
		when(protoMock.getTargetCase()).thenReturn(TargetContextProto.TargetCase.ATTRIBUTES);
		when(protoMock.getAttributes()).thenReturn(idListMock);
		when(idListMock.getIdsList()).thenReturn(List.of(7L, 8L, 9L));

		// Invoking the method and verifying the result
		TargetContext result = ProtoUtil.fromTargetContextProto(protoMock);
		assertEquals(new TargetContext(List.of(7L, 8L, 9L)), result);
	}

	@Test
	void testFromTargetContextProto_TargetNotSet() {
		// Mocking the proto object for the TARGET_NOT_SET case
		TargetContextProto protoMock = mock(TargetContextProto.class);
		when(protoMock.getTargetCase()).thenReturn(TargetContextProto.TargetCase.TARGET_NOT_SET);

		// Expecting an exception to be thrown
		assertThrows(PMException.class, () -> ProtoUtil.fromTargetContextProto(protoMock));
	}

	@Test
	void testFromUserContextProto_WithId() throws PMException {
		// Mocking the proto object for the ID case
		UserContextProto protoMock = mock(UserContextProto.class);
		when(protoMock.getUserCase()).thenReturn(UserContextProto.UserCase.ID);
		when(protoMock.getId()).thenReturn(123L);

		// Invoking the method and verifying the result
		UserContext result = ProtoUtil.fromUserContextProto(protoMock);
		assertEquals(new UserContext(123L), result);
	}

	@Test
	void testFromUserContextProto_WithAttributes() throws PMException {
		// Mocking the proto object for the ATTRIBUTES case
		UserContextProto protoMock = mock(UserContextProto.class);
		IdList idListMock = mock(IdList.class);
		when(protoMock.getUserCase()).thenReturn(UserContextProto.UserCase.ATTRIBUTES);
		when(protoMock.getAttributes()).thenReturn(idListMock);
		when(idListMock.getIdsList()).thenReturn(List.of(1L, 2L, 3L));

		// Invoking the method and verifying the result
		UserContext result = ProtoUtil.fromUserContextProto(protoMock);
		assertEquals(new UserContext(List.of(1L, 2L, 3L)), result);
	}

	@Test
	void testFromUserContextProto_UserNotSet() {
		// Mocking the proto object for the USER_NOT_SET case
		UserContextProto protoMock = mock(UserContextProto.class);
		when(protoMock.getUserCase()).thenReturn(UserContextProto.UserCase.USER_NOT_SET);

		// Expecting an exception to be thrown
		assertThrows(PMException.class, () -> ProtoUtil.fromUserContextProto(protoMock));
	}

	@Test
	void testToNodeProto_BasicData() {
		// Creating a Node object with basic data (id, name, type)
		Node node = new Node(123L, "TestNode", NodeType.UA);

		// Calling the method to be tested
		NodeProto result = ProtoUtil.toNodeProto(node);

		// Verifying the result
		assertEquals(123L, result.getId());
		assertEquals("TestNode", result.getName());
		assertEquals(NodeProto.NodeTypeProto.UA, result.getType());
		assertEquals(0, result.getPropertiesCount());
	}

	@Test
	void testToNodeProto_WithProperties() {
		// Creating a Node object with properties
		Node node = new Node(456L, "NodeWithProperties", NodeType.OA);
		node.getProperties().put("key1", "value1");
		node.getProperties().put("key2", "value2");

		// Calling the method to be tested
		NodeProto result = ProtoUtil.toNodeProto(node);

		// Verifying the result
		assertEquals(456L, result.getId());
		assertEquals("NodeWithProperties", result.getName());
		assertEquals(NodeProto.NodeTypeProto.OA, result.getType());
		assertEquals(2, result.getPropertiesCount());
		assertEquals("value1", result.getPropertiesOrDefault("key1", null));
		assertEquals("value2", result.getPropertiesOrDefault("key2", null));
	}

	@Test
	void testToNodeProto_CorrectNodeTypeMapping() {
		// Testing all NodeType to NodeTypeProto mappings
		for (NodeType nodeType : NodeType.values()) {
			Node node = new Node(1L, "NodeTypeTest", nodeType);

			NodeProto result = ProtoUtil.toNodeProto(node);

			// Verify correct mapping
			assertEquals(NodeProto.NodeTypeProto.valueOf(nodeType.name()), result.getType());
		}
	}

	@Test
	void testToNodeProto_VerifyNoUnexpectedFields() {
		// Creating a mocked Node object with expectations
		Node node = mock(Node.class);
		when(node.getId()).thenReturn(789L);
		when(node.getName()).thenReturn("MockedNode");
		when(node.getType()).thenReturn(U);
		when(node.getProperties()).thenReturn(Map.of("key", "value"));

		// Calling the method to be tested
		NodeProto result = ProtoUtil.toNodeProto(node);

		// Verify the protobuf object matches the mock expectations
		assertEquals(789L, result.getId());
		assertEquals("MockedNode", result.getName());
		assertEquals(NodeProto.NodeTypeProto.U, result.getType());
		assertEquals(1, result.getPropertiesCount());
		assertEquals("value", result.getPropertiesOrDefault("key", null));
	}

	@Test
	void testToProhibitionProto_NodeSubject() throws PMException {
		// Creating a prohibition with a node subject
		Prohibition prohibition = new Prohibition(
				"testProhibition", new ProhibitionSubject(1L),
				new AccessRightSet(List.of("read", "write")), true, List.of()
		);

		PolicyQuery policyQuery = mock(PolicyQuery.class);
		when(policyQuery.graph()).thenReturn(mock(GraphQuery.class));
		when(policyQuery.graph().getNodeById(1L)).thenReturn(new Node(1L, "test", U));

		// Calling the method to be tested
		ProhibitionProto result = ProtoUtil.toProhibitionProto(prohibition, policyQuery);

		// Asserting values from the result
		assertEquals("testProhibition", result.getName());
		assertEquals(1L, result.getNode().getId());
		assertFalse(result.hasProcess());
		assertEquals(List.of("read", "write"), result.getArsetList());
		assertTrue(result.getIntersection());
		assertEquals(0, result.getContainerConditionsCount());
	}

	@Test
	void testToProhibitionProto_ProcessSubject() {
		// Creating a prohibition with a process subject
		Prohibition prohibition = new Prohibition(
				"testProhibition", new ProhibitionSubject("testProcess"),
				new AccessRightSet(List.of("execute")), false, List.of()
		);

		// Calling the method to be tested
		ProhibitionProto result = ProtoUtil.toProhibitionProto(prohibition, mock(PolicyQuery.class));

		// Asserting values from the result
		assertEquals("testProhibition", result.getName());
		assertEquals("testProcess", result.getProcess());
		assertFalse(result.hasNode());
		assertEquals(List.of("execute"), result.getArsetList());
		assertFalse(result.getIntersection());
		assertEquals(0, result.getContainerConditionsCount());
	}

	@Test
	void testToProhibitionProto_EmptyContainerConditions() throws PMException {
		// Creating a prohibition with no container conditions
		Prohibition prohibition = new Prohibition(
				"testProhibition", new ProhibitionSubject(1L),
				new AccessRightSet(List.of("read")), false, List.of()
		);

		PolicyQuery policyQuery = mock(PolicyQuery.class);
		when(policyQuery.graph()).thenReturn(mock(GraphQuery.class));
		when(policyQuery.graph().getNodeById(1L)).thenReturn(new Node(1L, "test", U));

		// Calling the method to be tested
		ProhibitionProto result = ProtoUtil.toProhibitionProto(prohibition, policyQuery);

		// Asserting values from the result
		assertEquals("testProhibition", result.getName());
		assertEquals(1L, result.getNode().getId());
		assertFalse(result.hasProcess());
		assertEquals(List.of("read"), result.getArsetList());
		assertFalse(result.getIntersection());
		assertEquals(0, result.getContainerConditionsCount());
	}

	@Test
	void testToProhibitionProto_WithContainerConditions() throws PMException {
		// Creating container conditions
		ContainerCondition cc1 = new ContainerCondition(1L, true);
		ContainerCondition cc2 = new ContainerCondition(2L, false);

		// Creating a prohibition with container conditions
		Prohibition prohibition = new Prohibition(
				"testProhibition", new ProhibitionSubject(3L),
				new AccessRightSet(List.of("write")), true, List.of(cc1, cc2)
		);

		PolicyQuery policyQuery = mock(PolicyQuery.class);
		when(policyQuery.graph()).thenReturn(mock(GraphQuery.class));
		when(policyQuery.graph().getNodeById(1L)).thenReturn(new Node(1L, "test", OA));
		when(policyQuery.graph().getNodeById(2L)).thenReturn(new Node(2L, "test", OA));
		when(policyQuery.graph().getNodeById(3L)).thenReturn(new Node(3L, "test", U));

		// Calling the method to be tested
		ProhibitionProto result = ProtoUtil.toProhibitionProto(prohibition, policyQuery);

		// Asserting values from the result
		assertEquals("testProhibition", result.getName());
		assertEquals(3L, result.getNode().getId());
		assertFalse(result.hasProcess());
		assertEquals(List.of("write"), result.getArsetList());
		assertTrue(result.getIntersection());
		assertEquals(2, result.getContainerConditionsCount());
		assertEquals(1L, result.getContainerConditions(0).getContainer().getId());
		assertTrue(result.getContainerConditions(0).getComplement());
		assertEquals(2L, result.getContainerConditions(1).getContainer().getId());
		assertFalse(result.getContainerConditions(1).getComplement());
	}
}