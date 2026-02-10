package gov.nist.csd.pm.pdp.shared.eventstore;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.node.NodeType;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.store.*;
import gov.nist.csd.pm.pdp.proto.event.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyEventHandlerTest {

	@Mock
	private PAP pap;

	@Mock
	private PolicyStore policyStore;

	@Mock
	private GraphStore graph;

	@Mock
	private ProhibitionsStore prohibitions;

	@Mock
	private ObligationsStore obligations;

	@Mock
	private OperationsStore operations;

	private PolicyEventHandler handler;

	@BeforeEach
	void setUp() {
		handler = new PolicyEventHandler(pap);

		when(pap.policyStore()).thenReturn(policyStore);

		lenient().when(policyStore.graph()).thenReturn(graph);
		lenient().when(policyStore.prohibitions()).thenReturn(prohibitions);
		lenient().when(policyStore.obligations()).thenReturn(obligations);
		lenient().when(policyStore.operations()).thenReturn(operations);
	}

	@Test
	void handleEvents_beginTxAndCommitOnce() throws Exception {
		PMEvent e1 = PMEvent.newBuilder()
				.setNodeDeleted(NodeDeleted.newBuilder().setId(1).build())
				.build();

		PMEvent e2 = PMEvent.newBuilder()
				.setNodeDeleted(NodeDeleted.newBuilder().setId(2).build())
				.build();

		handler.handleEvents(List.of(e1, e2));

		verify(pap).beginTx();
		verify(pap).commit();

		verify(graph).deleteNode(1L);
		verify(graph).deleteNode(2L);
	}

	@Test
	void handleEvent_beginTxAndCommitOnce() throws Exception {
		PMEvent e = PMEvent.newBuilder()
				.setNodeDeleted(NodeDeleted.newBuilder().setId(1).build())
				.build();

		handler.handleEvent(e);

		verify(pap).beginTx();
		verify(pap).commit();

		verify(graph).deleteNode(1L);
	}

	@Test
	void assignmentCreated_createsAssignments() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setAssignmentCreated(
						AssignmentCreated.newBuilder()
								.setAscendant(1)
								.addAllDescendants(List.of(2L, 3L))
								.build()
				).build();

		handler.handleEvent(event);

		verify(graph).createAssignment(1L, 2L);
		verify(graph).createAssignment(1L, 3L);
	}

	@Test
	void associationCreated_createsAssociation() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setAssociationCreated(
						AssociationCreated.newBuilder()
								.setUa(1)
								.setTarget(2)
								.addAllArset(List.of("read", "write"))
								.build()
				).build();

		handler.handleEvent(event);

		verify(graph).createAssociation(
				eq(1L),
				eq(2L),
				argThat(arset -> arset.contains("read") && arset.contains("write"))
		);
	}

	@Test
	void policyClassCreated_createsNodePc() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setPolicyClassCreated(
						PolicyClassCreated.newBuilder()
								.setId(1)
								.setName("test")
								.build()
				).build();

		handler.handleEvent(event);

		verify(graph).createNode(1L, "test", NodeType.PC);
	}

	@Test
	void userAttributeCreated_createsNodeAndAssignments() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setUserAttributeCreated(
						UserAttributeCreated.newBuilder()
								.setId(1)
								.setName("test")
								.addAllDescendants(List.of(2L, 3L))
								.build()
				).build();

		handler.handleEvent(event);

		verify(graph).createNode(1L, "test", NodeType.UA);
		verify(graph).createAssignment(1L, 2L);
		verify(graph).createAssignment(1L, 3L);
	}

	@Test
	void objectAttributeCreated_createsNodeAndAssignments() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setObjectAttributeCreated(
						ObjectAttributeCreated.newBuilder()
								.setId(1)
								.setName("test")
								.addAllDescendants(List.of(2L))
								.build()
				).build();

		handler.handleEvent(event);

		verify(graph).createNode(1L, "test", NodeType.OA);
		verify(graph).createAssignment(1L, 2L);
	}

	@Test
	void userCreated_createsNodeAndAssignments() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setUserCreated(
						UserCreated.newBuilder()
								.setId(1)
								.setName("test")
								.addAllDescendants(List.of(2L))
								.build()
				).build();

		handler.handleEvent(event);

		verify(graph).createNode(1L, "test", NodeType.U);
		verify(graph).createAssignment(1L, 2L);
	}

	@Test
	void objectCreated_createsNodeAndAssignments() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setObjectCreated(
						ObjectCreated.newBuilder()
								.setId(1)
								.setName("test")
								.addAllDescendants(List.of(2L))
								.build()
				).build();

		handler.handleEvent(event);

		verify(graph).createNode(1L, "test", NodeType.O);
		verify(graph).createAssignment(1L, 2L);
	}

	@Test
	void assignmentDeleted_deletesAssignments() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setAssignmentDeleted(
						AssignmentDeleted.newBuilder()
								.setAscendant(1)
								.addAllDescendants(List.of(2L, 3L))
								.build()
				).build();

		handler.handleEvent(event);

		verify(graph).deleteAssignment(1L, 2L);
		verify(graph).deleteAssignment(1L, 3L);
	}

	@Test
	void associationDeleted_deletesAssociation() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setAssociationDeleted(
						AssociationDeleted.newBuilder()
								.setUa(1)
								.setTarget(2)
								.build()
				).build();

		handler.handleEvent(event);

		verify(graph).deleteAssociation(1L, 2L);
	}

	@Test
	void nodeDeleted_deletesNode() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setNodeDeleted(NodeDeleted.newBuilder().setId(1).build())
				.build();

		handler.handleEvent(event);

		verify(graph).deleteNode(1L);
	}

	@Test
	void nodePropertiesSet_setsProperties() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setNodePropertiesSet(
						NodePropertiesSet.newBuilder()
								.setId(1)
								.putAllProperties(Map.of("a", "test"))
								.build()
				).build();

		handler.handleEvent(event);

		verify(graph).setNodeProperties(1L, Map.of("a", "test"));
	}

	@Test
	void prohibitionCreated_subjectNode_createsProhibition() throws Exception {
		ProhibitionCreated created = ProhibitionCreated.newBuilder()
				.setName("test")
				.setNode(1)
				.addAllArset(List.of("read", "write"))
				.setIsConjunctive(true)
				.addAllInclusionSet(List.of(2L, 3L))
				.addAllExclusionSet(List.of(4L))
				.build();

		PMEvent event = PMEvent.newBuilder().setProhibitionCreated(created).build();

		handler.handleEvent(event);

		verify(prohibitions).createNodeProhibition(
				eq("test"),
				eq(1L),
				argThat(arset -> arset.contains("read") && arset.contains("write")),
				eq(Set.of(2L, 3L)),
				eq(Set.of(4L)),
				eq(true)
		);
	}

	@Test
	void prohibitionCreated_subjectProcess_createsProhibition() throws Exception {
		ProhibitionCreated created = ProhibitionCreated.newBuilder()
				.setName("test")
				.setNode(1)
				.setProcess("testProcess")
				.addAllArset(List.of("read"))
				.setIsConjunctive(false)
				.build();

		PMEvent event = PMEvent.newBuilder().setProhibitionCreated(created).build();

		handler.handleEvent(event);

		verify(prohibitions).createProcessProhibition(
				eq("test"),
				eq(1L),
				eq("testProcess"),
				argThat(arset -> arset.contains("read")),
				eq(Set.of()),
				eq(Set.of()),
				eq(false)
		);
	}

	@Test
	void prohibitionDeleted_deletesProhibition() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setProhibitionDeleted(ProhibitionDeleted.newBuilder().setName("test").build())
				.build();

		handler.handleEvent(event);

		verify(prohibitions).deleteProhibition("test");
	}

	@Test
	void obligationCreated_executesPmlAsAuthor() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setObligationCreated(
						ObligationCreated.newBuilder()
								.setAuthor(1)
								.setPml("test")
								.build()
				).build();

		handler.handleEvent(event);

		verify(pap).executePML(
				argThat(uc -> uc != null && uc.getUser() == 1L),
				eq("test")
		);
	}

	@Test
	void obligationDeleted_deletesObligation() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setObligationDeleted(
						ObligationDeleted.newBuilder().setName("test").build()
				).build();

		handler.handleEvent(event);

		verify(obligations).deleteObligation("test");
	}

	@Test
	void resourceAccessRightsSet_setsOperationsResourceRights() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setResourceAccessRightsSet(
						ResourceAccessRightsSet.newBuilder()
								.addAllOperations(List.of("read", "write"))
								.build()
				).build();

		handler.handleEvent(event);

		verify(operations).setResourceAccessRights(
				argThat(arset -> arset.contains("read") && arset.contains("write"))
		);
	}

	@Test
	void operationCreated_executesPmlAsUserZero() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setOperationCreated(OperationCreated.newBuilder().setPml("test").build())
				.build();

		handler.handleEvent(event);

		verify(pap).executePML(
				argThat(uc -> uc != null && uc.getUser() == 0L),
				eq("test")
		);
	}

	@Test
	void operationDeleted_deletesOperation() throws Exception {
		PMEvent event = PMEvent.newBuilder()
				.setOperationDeleted(OperationDeleted.newBuilder().setName("test").build())
				.build();

		handler.handleEvent(event);

		verify(operations).deleteOperation("test");
	}

	@Test
	void eventNotSet_doesNothingBeyondTx() throws Exception {
		PMEvent event = PMEvent.newBuilder().build();
		handler.handleEvent(event);

		verify(pap).beginTx();
		verify(pap).commit();

		verifyNoInteractions(graph, prohibitions, obligations, operations);
		verifyNoMoreInteractions(policyStore);
	}

	@Test
	void handleEvent_propagatesPMException() throws Exception {
		doThrow(new PMException("test exception"))
				.when(graph)
				.deleteNode(1L);

		PMEvent event = PMEvent.newBuilder()
				.setNodeDeleted(NodeDeleted.newBuilder().setId(1).build())
				.build();

		PMException ex = assertThrows(PMException.class, () -> handler.handleEvent(event));
		assertEquals("test exception", ex.getMessage());
	}
}