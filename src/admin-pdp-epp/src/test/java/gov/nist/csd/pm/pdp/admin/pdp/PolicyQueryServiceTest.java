package gov.nist.csd.pm.pdp.admin.pdp;

import com.google.protobuf.Empty;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.exception.PMRuntimeException;
import gov.nist.csd.pm.core.common.graph.node.Node;
import gov.nist.csd.pm.core.common.graph.node.NodeType;
import gov.nist.csd.pm.core.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.core.common.graph.relationship.Association;
import gov.nist.csd.pm.core.common.prohibition.Prohibition;
import gov.nist.csd.pm.core.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.Neo4jEmbeddedPAP;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.function.op.Operation;
import gov.nist.csd.pm.core.pap.function.routine.Routine;
import gov.nist.csd.pm.core.pap.obligation.Obligation;
import gov.nist.csd.pm.core.pap.pml.function.operation.PMLStmtsOperation;
import gov.nist.csd.pm.core.pap.pml.function.routine.PMLStmtsRoutine;
import gov.nist.csd.pm.core.pap.query.GraphQuery;
import gov.nist.csd.pm.core.pap.query.PolicyQuery;
import gov.nist.csd.pm.core.pap.query.model.context.TargetContext;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pap.query.model.explain.Explain;
import gov.nist.csd.pm.core.pap.query.model.subgraph.Subgraph;
import gov.nist.csd.pm.core.pap.query.model.subgraph.SubgraphPrivileges;
import gov.nist.csd.pm.core.pdp.PDPTx;
import gov.nist.csd.pm.pdp.proto.model.*;
import gov.nist.csd.pm.pdp.proto.query.*;
import gov.nist.csd.pm.core.pdp.query.*;
import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static gov.nist.csd.pm.pdp.admin.util.TestProtoUtil.testNode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PolicyQueryServiceTest {

	@Mock
	private Adjudicator adjudicator;
	@Mock
	private PDPTx pdpTx;
	@Mock
	private PolicyQueryAdjudicator policyQueryAdjudicator;
	@Mock
	private GraphQueryAdjudicator graphQueryAdjudicator;
	@Mock
	private ProhibitionsQueryAdjudicator prohibitionsQueryAdjudicator;
	@Mock
	private ObligationsQueryAdjudicator obligationsQueryAdjudicator;
	@Mock
	private OperationsQueryAdjudicator operationsQueryAdjudicator;
	@Mock
	private RoutinesQueryAdjudicator routinesQueryAdjudicator;
	@Mock
	private AccessQueryAdjudicator accessQueryAdjudicator;
	@Mock
	private SelfAccessQueryAdjudicator selfAccessQueryAdjudicator;
	@Mock
	private Neo4jEmbeddedPAP pap;
	@Mock
	private PolicyQuery policyQuery;
	@Mock
	private GraphQuery graphQuery;

	private PolicyQueryService service;

	@BeforeEach
	void setUp() throws PMException {
		when(pdpTx.query()).thenReturn(policyQueryAdjudicator);
		when(policyQueryAdjudicator.graph()).thenReturn(graphQueryAdjudicator);
		when(policyQueryAdjudicator.prohibitions()).thenReturn(prohibitionsQueryAdjudicator);
		when(policyQueryAdjudicator.obligations()).thenReturn(obligationsQueryAdjudicator);
		when(policyQueryAdjudicator.operations()).thenReturn(operationsQueryAdjudicator);
		when(policyQueryAdjudicator.routines()).thenReturn(routinesQueryAdjudicator);
		when(policyQueryAdjudicator.access()).thenReturn(accessQueryAdjudicator);
		when(policyQueryAdjudicator.selfAccess()).thenReturn(selfAccessQueryAdjudicator);

		when(pap.query()).thenReturn(policyQuery);
		when(policyQuery.graph()).thenReturn(graphQuery);
		when(graphQuery.getNodeById(1)).thenReturn(new Node(1L, "testNode1", NodeType.OA));
		when(graphQuery.getNodeById(2)).thenReturn(new Node(2L, "testNode2", NodeType.OA));
		when(graphQuery.getNodeById(3)).thenReturn(new Node(3L, "testNode3", NodeType.OA));

		service = new PolicyQueryService(adjudicator, pap);
	}

	@FunctionalInterface
	private interface TxFunction {
		void apply(PolicyQueryAdjudicator adjudicator) throws PMException;
	}

	private void stubAdjudicatorTx(TxFunction txFn) {
		when(adjudicator.adjudicateQuery(any())).thenAnswer(invocation -> {
			PDPTxFunction<?> fn = invocation.getArgument(0);
			when(pdpTx.query()).thenReturn(policyQueryAdjudicator);
			return fn.apply(pdpTx);
		});

		try {
			txFn.apply(policyQueryAdjudicator);
		} catch (PMException e) {
			fail(e.getMessage());
		}
	}

	@Nested
	class NodeExistsTests {

		@Mock
		private StreamObserver<BooleanResponse> observer;

		@Test
		void shouldReturnTrueWhenNodeExists() throws PMException {
			IdOrNameQuery request = IdOrNameQuery.newBuilder().setId(123).build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().nodeExists(123)).thenReturn(true);
			});

			service.nodeExists(request, observer);

			verify(graphQueryAdjudicator).nodeExists(123);
			verify(observer).onNext(BooleanResponse.newBuilder().setResult(true).build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnFalseWhenNodeDoesNotExist() {
			IdOrNameQuery request = IdOrNameQuery.newBuilder().setId(123).build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().nodeExists(123)).thenReturn(false);
			});

			service.nodeExists(request, observer);

			verify(observer).onNext(BooleanResponse.newBuilder().setResult(false).build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateRuntimeExceptionAndNotCallObserver() {
			IdOrNameQuery request = IdOrNameQuery.newBuilder()
					.setId(123)
					.build();
			RuntimeException ex = new RuntimeException("test exception");
			when(adjudicator.adjudicateQuery(any())).thenThrow(ex);

			assertThrows(RuntimeException.class, () -> service.nodeExists(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetNodeTests {
		@Mock
		private StreamObserver<NodeProto> observer;

		@Test
		void shouldReturnNodeProtoWhenIdProvided() {
			IdOrNameQuery req = IdOrNameQuery.newBuilder()
					.setId(123)
					.build();
			Node node = mock(Node.class);
			NodeProto nodeProto = NodeProto.newBuilder().setName("test").build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getNodeById(123)).thenReturn(node);
			});

			try (MockedStatic<ProtoUtil> protoUtil = mockStatic(ProtoUtil.class)) {
				protoUtil.when(() -> ProtoUtil.toNodeProto(node)).thenReturn(nodeProto);

				service.getNode(req, observer);

				verify(observer).onNext(nodeProto);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldReturnNodeProtoWhenNameProvided() {
			IdOrNameQuery req = IdOrNameQuery.newBuilder()
					.setName("test")
					.build();
			Node node = mock(Node.class);
			NodeProto nodeProto = NodeProto.newBuilder().setName("test").build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getNodeByName("test")).thenReturn(node);
			});

			try (MockedStatic<ProtoUtil> protoUtil = mockStatic(ProtoUtil.class)) {
				protoUtil.when(() -> ProtoUtil.toNodeProto(node)).thenReturn(nodeProto);

				service.getNode(req, observer);

				verify(observer).onNext(nodeProto);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldThrowWhenIdOrNameNotSet() {
			IdOrNameQuery request = IdOrNameQuery.getDefaultInstance();

			stubAdjudicatorTx(adjudicator -> { });

			assertThrows(PMException.class,
			             () -> service.getNode(request, observer));
			verifyNoInteractions(observer);
		}

		@Test
		void shouldPropagateRuntimeExceptionAndNotCallObserver() {
			IdOrNameQuery req = IdOrNameQuery.newBuilder()
					.setId(999L)
					.build();
			RuntimeException e = new RuntimeException("test exception");

			when(adjudicator.adjudicateQuery(any())).thenThrow(e);

			assertThrows(RuntimeException.class,
			             () -> service.getNode(req, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetNodeIdTests {
		@Mock
		private StreamObserver<NodeIdResponse> observer;

		@Test
		void shouldReturnIdGivenName() throws PMException {
			IdOrNameQuery request = IdOrNameQuery.newBuilder()
					.setName("test")
					.build();

			long expectedId = 123;

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getNodeId("test")).thenReturn(expectedId);
			});

			service.getNodeId(request, observer);

			verify(graphQueryAdjudicator).getNodeId("test");
			verify(observer).onNext(NodeIdResponse.newBuilder()
					                        .setId(expectedId)
					                        .build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateRuntimeExceptionAndNotCallObserver() {
			IdOrNameQuery request = IdOrNameQuery.newBuilder()
					.setName("test")
					.build();

			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getNodeId(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class SearchNodesTests {

		@Mock
		private StreamObserver<SearchResponse> observer;

		@Test
		void shouldReturnProtoListForNodes() throws PMException {
			SearchQuery request = SearchQuery.getDefaultInstance();
			Node node1 = mock(Node.class);
			Node node2 = mock(Node.class);
			NodeProto proto1 = NodeProto.newBuilder().setName("test").setType(NodeProto.NodeTypeProto.PC).build();
			NodeProto proto2 = NodeProto.newBuilder().setName("test").setType(NodeProto.NodeTypeProto.PC).build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().search(NodeType.PC, new HashMap<>()))
						.thenReturn(List.of(node1, node2));
			});

			try (MockedStatic<ProtoUtil> protoUtil = mockStatic(ProtoUtil.class)) {
				protoUtil.when(() -> ProtoUtil.toNodeProto(node1)).thenReturn(proto1);
				protoUtil.when(() -> ProtoUtil.toNodeProto(node2)).thenReturn(proto2);

				service.searchNodes(request, observer);

				verify(graphQueryAdjudicator).search(NodeType.PC, new HashMap<>());

				SearchResponse expected = SearchResponse.newBuilder()
						.addNodes(proto1)
						.addNodes(proto2)
						.build();
				verify(observer).onNext(expected);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldReturnEmptyListWhenNoNodes() throws PMException {
			SearchQuery request = SearchQuery.getDefaultInstance();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().search(NodeType.PC, new HashMap<>())).thenReturn(Collections.emptyList());
			});

			service.searchNodes(request, observer);

			verify(graphQueryAdjudicator).search(NodeType.PC, new HashMap<>());

			verify(observer).onNext(SearchResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateRuntimeExceptionAndNotCallObserver() {
			SearchQuery request = SearchQuery.getDefaultInstance();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.searchNodes(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetPolicyClassesTests {

		@Mock
		private StreamObserver<PolicyClassesResponse> observer;

		@Test
		void shouldReturnIdsList() throws PMException {
			Empty request = Empty.getDefaultInstance();
			List<Long> expectedIds = List.of(1L, 2L, 3L);

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getPolicyClasses()).thenReturn(expectedIds);
			});

			service.getPolicyClasses(request, observer);

			verify(graphQueryAdjudicator).getPolicyClasses();
			verify(observer).onNext(
					PolicyClassesResponse.newBuilder()
							.addAllNodes(List.of(testNode(1L), testNode(2L), testNode(3L)))
							.build()
			);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyListWhenNoPolicyClasses() throws PMException {
			Empty request = Empty.getDefaultInstance();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getPolicyClasses()).thenReturn(List.of());
			});

			service.getPolicyClasses(request, observer);

			verify(graphQueryAdjudicator).getPolicyClasses();
			verify(observer).onNext(
					PolicyClassesResponse.newBuilder().build()
			);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateRuntimeExceptionAndNotCallObserver() {
			Empty request = Empty.getDefaultInstance();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getPolicyClasses(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetAdjacentDescendantsTests {

		@Mock
		private StreamObserver<NodeListResponse> observer;

		@Test
		void shouldReturnDescendantIds() throws PMException {
			GetAdjacentAssignmentsQuery request = GetAdjacentAssignmentsQuery.newBuilder()
					.setNodeId(1)
					.build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getAdjacentDescendants(request.getNodeId()))
						.thenReturn(List.of(1L, 2L, 3L));
			});

			service.getAdjacentDescendants(request, observer);

			verify(graphQueryAdjudicator).getAdjacentDescendants(request.getNodeId());
			
			verify(observer).onNext(
					NodeListResponse.newBuilder()
							.addAllNodes(List.of(testNode(1L), testNode(2L), testNode(3L)))
							.build()
			);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyWhenNone() throws PMException {
			GetAdjacentAssignmentsQuery request = GetAdjacentAssignmentsQuery.newBuilder()
					.setNodeId(2)
					.build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getAdjacentDescendants(request.getNodeId()))
						.thenReturn(Collections.emptyList());
			});

			service.getAdjacentDescendants(request, observer);

			verify(graphQueryAdjudicator).getAdjacentDescendants(request.getNodeId());
			verify(observer).onNext(NodeListResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			GetAdjacentAssignmentsQuery request = GetAdjacentAssignmentsQuery.newBuilder()
					.setNodeId(3)
					.build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getAdjacentDescendants(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetAdjacentAscendantsTests {

		@Mock
		private StreamObserver<NodeListResponse> observer;

		@Test
		void shouldReturnAscendantIds() throws PMException {
			GetAdjacentAssignmentsQuery request = GetAdjacentAssignmentsQuery.newBuilder()
					.setNodeId(5)
					.build();
			List<Long> ids = List.of(1L, 2L, 3L);

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getAdjacentAscendants(request.getNodeId()))
						.thenReturn(ids);
			});

			service.getAdjacentAscendants(request, observer);

			verify(graphQueryAdjudicator).getAdjacentAscendants(request.getNodeId());
			verify(observer).onNext(
					NodeListResponse.newBuilder()
							.addAllNodes(List.of(testNode(1L), testNode(2L), testNode(3L)))
							.build()
			);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyWhenNone() throws PMException {
			GetAdjacentAssignmentsQuery request = GetAdjacentAssignmentsQuery.newBuilder()
					.setNodeId(6)
					.build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getAdjacentAscendants(request.getNodeId()))
						.thenReturn(Collections.emptyList());
			});

			service.getAdjacentAscendants(request, observer);

			verify(graphQueryAdjudicator).getAdjacentAscendants(request.getNodeId());
			verify(observer).onNext(NodeListResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			GetAdjacentAssignmentsQuery request = GetAdjacentAssignmentsQuery.newBuilder()
					.setNodeId(7)
					.build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getAdjacentAscendants(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetAssociationsWithSourceTests {

		@Mock
		private StreamObserver<AssociationListResponse> observer;

		@Test
		void shouldReturnAssociationProtos() throws PMException {
			GetAssociationsQuery request = GetAssociationsQuery.newBuilder()
					.setNodeId(1L)
					.build();

			Association assoc1 = mock(Association.class);
			when(assoc1.getSource()).thenReturn(1L);
			when(assoc1.getTarget()).thenReturn(2L);
			when(assoc1.getAccessRightSet()).thenReturn(new AccessRightSet("read"));

			Association assoc2 = mock(Association.class);
			when(assoc2.getSource()).thenReturn(1L);
			when(assoc2.getTarget()).thenReturn(3L);
			when(assoc2.getAccessRightSet()).thenReturn(new AccessRightSet("write"));

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getAssociationsWithSource(request.getNodeId()))
						.thenReturn(List.of(assoc1, assoc2));
			});

			AssociationProto proto1 = AssociationProto.newBuilder()
					.setUa(testNode(1L))
					.setTarget(testNode(2L))
					.addAllArset(List.of("read"))
					.build();
			AssociationProto proto2 = AssociationProto.newBuilder()
					.setUa(testNode(1L))
					.setTarget(testNode(3L))
					.addAllArset(List.of("write"))
					.build();

			service.getAssociationsWithSource(request, observer);

			verify(graphQueryAdjudicator).getAssociationsWithSource(request.getNodeId());
			AssociationListResponse expected = AssociationListResponse.newBuilder()
					.addAssociations(proto1)
					.addAssociations(proto2)
					.build();
			verify(observer).onNext(expected);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyListWhenNoAssociations() throws PMException {
			GetAssociationsQuery request = GetAssociationsQuery.newBuilder()
					.setNodeId(5L)
					.build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getAssociationsWithSource(request.getNodeId()))
						.thenReturn(Collections.emptyList());
			});

			service.getAssociationsWithSource(request, observer);

			verify(graphQueryAdjudicator).getAssociationsWithSource(request.getNodeId());
			verify(observer).onNext(AssociationListResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			GetAssociationsQuery request = GetAssociationsQuery.newBuilder()
					.setNodeId(6L)
					.build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getAssociationsWithSource(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetAssociationsWithTargetTests {

		@Mock
		private StreamObserver<AssociationListResponse> observer;

		@Test
		void shouldReturnAssociationProtos() throws PMException {
			GetAssociationsQuery request = GetAssociationsQuery.newBuilder()
					.setNodeId(3L)
					.build();

			Association assoc1 = mock(Association.class);
			when(assoc1.getSource()).thenReturn(1L);
			when(assoc1.getTarget()).thenReturn(3L);
			when(assoc1.getAccessRightSet()).thenReturn(new AccessRightSet("read"));

			Association assoc2 = mock(Association.class);
			when(assoc2.getSource()).thenReturn(2L);
			when(assoc2.getTarget()).thenReturn(3L);
			when(assoc2.getAccessRightSet()).thenReturn(new AccessRightSet("write"));

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getAssociationsWithTarget(request.getNodeId()))
						.thenReturn(List.of(assoc1, assoc2));
			});

			AssociationProto proto1 = AssociationProto.newBuilder()
					.setUa(testNode(1))
					.setTarget(testNode(3))
					.addAllArset(List.of("read"))
					.build();
			AssociationProto proto2 = AssociationProto.newBuilder()
					.setUa(testNode(2))
					.setTarget(testNode(3))
					.addAllArset(List.of("write"))
					.build();

			service.getAssociationsWithTarget(request, observer);

			verify(graphQueryAdjudicator).getAssociationsWithTarget(request.getNodeId());
			AssociationListResponse expected = AssociationListResponse.newBuilder()
					.addAssociations(proto1)
					.addAssociations(proto2)
					.build();
			verify(observer).onNext(expected);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyListWhenNoAssociations() throws PMException {
			GetAssociationsQuery request = GetAssociationsQuery.newBuilder()
					.setNodeId(1L)
					.build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getAssociationsWithTarget(request.getNodeId()))
						.thenReturn(Collections.emptyList());
			});

			service.getAssociationsWithTarget(request, observer);

			verify(graphQueryAdjudicator).getAssociationsWithTarget(request.getNodeId());
			verify(observer).onNext(AssociationListResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			GetAssociationsQuery request = GetAssociationsQuery.newBuilder()
					.setNodeId(1L)
					.build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getAssociationsWithTarget(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetAscendantSubgraphTests {
		@Mock
		StreamObserver<SubgraphProto> observer;

		@Test
		void shouldReturnSingleNodeSubgraph() throws PMException {
			GetSubgraphQuery request = GetSubgraphQuery.newBuilder()
					.setNodeId(1)
					.build();
			var node = mock(Node.class);
			Subgraph model = new Subgraph(node, Collections.emptyList());

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getAscendantSubgraph(request.getNodeId()))
						.thenReturn(model);
			});

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				var nodeProto = gov.nist.csd.pm.pdp.proto.model.NodeProto.newBuilder()
						.setName("test")
						.build();
				pu.when(() -> ProtoUtil.toNodeProto(node)).thenReturn(nodeProto);

				service.getAscendantSubgraph(request, observer);

				verify(graphQueryAdjudicator).getAscendantSubgraph(request.getNodeId());
				SubgraphProto expected = SubgraphProto.newBuilder()
						.setNode(nodeProto)
						.build();
				verify(observer).onNext(expected);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldReturnNestedSubgraph() throws PMException {
			GetSubgraphQuery request = GetSubgraphQuery.newBuilder()
					.setNodeId(1)
					.build();
			var parentNode = mock(Node.class);
			var childNode = mock(Node.class);
			Subgraph child = new Subgraph(childNode, Collections.emptyList());
			Subgraph parent = new Subgraph(parentNode, List.of(child));

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getAscendantSubgraph(request.getNodeId()))
						.thenReturn(parent);
			});

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				var parentProto = gov.nist.csd.pm.pdp.proto.model.NodeProto.newBuilder()
						.setId(1)
						.setName("test1")
						.build();
				var childProto = gov.nist.csd.pm.pdp.proto.model.NodeProto.newBuilder()
						.setId(2)
						.setName("test2")
						.build();
				pu.when(() -> ProtoUtil.toNodeProto(parentNode)).thenReturn(parentProto);
				pu.when(() -> ProtoUtil.toNodeProto(childNode)).thenReturn(childProto);

				service.getAscendantSubgraph(request, observer);

				verify(graphQueryAdjudicator).getAscendantSubgraph(request.getNodeId());
				SubgraphProto expectedChild = SubgraphProto.newBuilder()
						.setNode(childProto)
						.build();
				SubgraphProto expected = SubgraphProto.newBuilder()
						.setNode(parentProto)
						.addSubgraph(expectedChild)
						.build();
				verify(observer).onNext(expected);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			GetSubgraphQuery request = GetSubgraphQuery.newBuilder()
					.setNodeId(1)
					.build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getAscendantSubgraph(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetDescendantSubgraphTests {
		@Mock
		StreamObserver<SubgraphProto> observer;

		@Test
		void shouldReturnSingleNodeSubgraph() throws PMException {
			GetSubgraphQuery request = GetSubgraphQuery.newBuilder()
					.setNodeId(1)
					.build();
			var node = mock(Node.class);
			Subgraph model = new Subgraph(node, Collections.emptyList());

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getDescendantSubgraph(request.getNodeId()))
						.thenReturn(model);
			});

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				var nodeProto = gov.nist.csd.pm.pdp.proto.model.NodeProto.newBuilder()
						.setId(1)
						.setName("test")
						.build();
				pu.when(() -> ProtoUtil.toNodeProto(node)).thenReturn(nodeProto);

				service.getDescendantSubgraph(request, observer);

				verify(graphQueryAdjudicator).getDescendantSubgraph(request.getNodeId());
				SubgraphProto expected = SubgraphProto.newBuilder()
						.setNode(nodeProto)
						.build();
				verify(observer).onNext(expected);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldReturnNestedSubgraph() throws PMException {
			GetSubgraphQuery request = GetSubgraphQuery.newBuilder()
					.setNodeId(1)
					.build();
			var parentNode = mock(Node.class);
			var childNode = mock(Node.class);
			Subgraph child = new Subgraph(childNode, Collections.emptyList());
			Subgraph parent = new Subgraph(parentNode, List.of(child));

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getDescendantSubgraph(request.getNodeId()))
						.thenReturn(parent);
			});

			var parentProto = gov.nist.csd.pm.pdp.proto.model.NodeProto.newBuilder()
					.setId(1)
					.setName("test1")
					.build();
			var childProto = gov.nist.csd.pm.pdp.proto.model.NodeProto.newBuilder()
					.setId(2)
					.setName("test2")
					.build();

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.toNodeProto(parentNode)).thenReturn(parentProto);
				pu.when(() -> ProtoUtil.toNodeProto(childNode)).thenReturn(childProto);

				service.getDescendantSubgraph(request, observer);

				verify(graphQueryAdjudicator).getDescendantSubgraph(request.getNodeId());
				SubgraphProto expectedChild = SubgraphProto.newBuilder()
						.setNode(childProto)
						.build();
				SubgraphProto expected = SubgraphProto.newBuilder()
						.setNode(parentProto)
						.addSubgraph(expectedChild)
						.build();
				verify(observer).onNext(expected);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			GetSubgraphQuery request = GetSubgraphQuery.newBuilder()
					.setNodeId(3)
					.build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getDescendantSubgraph(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetAttributeDescendantsTests {

		@Mock
		private StreamObserver<NodeListResponse> observer;

		@Test
		void shouldReturnAttributeDescendantIds() throws PMException {
			GetDescendantsQuery request = GetDescendantsQuery.newBuilder()
					.setNodeId(1)
					.build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getAttributeDescendants(request.getNodeId()))
						.thenReturn(List.of(1L, 2L, 3L));
			});

			service.getAttributeDescendants(request, observer);

			verify(graphQueryAdjudicator).getAttributeDescendants(request.getNodeId());
			verify(observer).onNext(
					NodeListResponse.newBuilder()
							.addAllNodes(List.of(testNode(1L), testNode(2L), testNode(3L)))
							.build()
			);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyWhenNoAttributes() throws PMException {
			GetDescendantsQuery request = GetDescendantsQuery.newBuilder()
					.setNodeId(1)
					.build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getAttributeDescendants(request.getNodeId()))
						.thenReturn(Collections.emptyList());
			});

			service.getAttributeDescendants(request, observer);

			verify(graphQueryAdjudicator).getAttributeDescendants(request.getNodeId());
			verify(observer).onNext(NodeListResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			GetDescendantsQuery request = GetDescendantsQuery.newBuilder()
					.setNodeId(1)
					.build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getAttributeDescendants(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetPolicyClassDescendantsTests {

		@Mock
		private StreamObserver<NodeListResponse> observer;

		@Test
		void shouldReturnPolicyClassDescendantIds() throws PMException {
			GetDescendantsQuery request = GetDescendantsQuery.newBuilder()
					.setNodeId(1)
					.build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getPolicyClassDescendants(request.getNodeId()))
						.thenReturn(List.of(1L, 2L, 3L));
			});

			service.getPolicyClassDescendants(request, observer);

			verify(graphQueryAdjudicator).getPolicyClassDescendants(request.getNodeId());
			verify(observer).onNext(
					NodeListResponse.newBuilder()
							.addAllNodes(List.of(testNode(1L), testNode(2L), testNode(3L)))
							.build()
			);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyWhenNone() throws PMException {
			GetDescendantsQuery request = GetDescendantsQuery.newBuilder()
					.setNodeId(1)
					.build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().getPolicyClassDescendants(request.getNodeId()))
						.thenReturn(Collections.emptyList());
			});

			service.getPolicyClassDescendants(request, observer);

			verify(graphQueryAdjudicator).getPolicyClassDescendants(request.getNodeId());
			verify(observer).onNext(NodeListResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			GetDescendantsQuery request = GetDescendantsQuery.newBuilder()
					.setNodeId(1)
					.build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getPolicyClassDescendants(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class IsAscendantTests {

		@Mock
		private StreamObserver<BooleanResponse> observer;

		private final IsAncestorQuery request = IsAncestorQuery.newBuilder()
				.setAscendantId(1)
				.setDescendantId(2)
				.build();

		@Test
		void shouldReturnTrueWhenAscendant() throws PMException {
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().isAscendant(
						request.getAscendantId(),
						request.getDescendantId()))
						.thenReturn(true);
			});

			service.isAscendant(request, observer);

			verify(graphQueryAdjudicator).isAscendant(
					request.getAscendantId(),
					request.getDescendantId());
			verify(observer).onNext(BooleanResponse.newBuilder().setResult(true).build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnFalseWhenNotAscendant() throws PMException {
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().isAscendant(
						request.getAscendantId(),
						request.getDescendantId()))
						.thenReturn(false);
			});

			service.isAscendant(request, observer);

			verify(graphQueryAdjudicator).isAscendant(
					request.getAscendantId(),
					request.getDescendantId());
			verify(observer).onNext(BooleanResponse.newBuilder().setResult(false).build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.isAscendant(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class IsDescendantTests {

		@Mock
		private StreamObserver<BooleanResponse> observer;

		private final IsAncestorQuery request = IsAncestorQuery.newBuilder()
				.setAscendantId(1)
				.setDescendantId(2)
				.build();

		@Test
		void shouldReturnTrueWhenDescendant() throws PMException {
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().isDescendant(
						request.getAscendantId(),
						request.getDescendantId()))
						.thenReturn(true);
			});

			service.isDescendant(request, observer);

			verify(graphQueryAdjudicator).isDescendant(
					request.getAscendantId(),
					request.getDescendantId());
			verify(observer).onNext(BooleanResponse.newBuilder().setResult(true).build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnFalseWhenNotDescendant() throws PMException {
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.graph().isDescendant(
						request.getAscendantId(),
						request.getDescendantId()))
						.thenReturn(false);
			});

			service.isDescendant(request, observer);

			verify(graphQueryAdjudicator).isDescendant(
					request.getAscendantId(),
					request.getDescendantId());
			verify(observer).onNext(BooleanResponse.newBuilder().setResult(false).build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.isDescendant(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetProhibitionsTests {

		@Mock
		private StreamObserver<ProhibitionListResponse> observer;

		@Test
		void shouldReturnProhibitionProtos() throws PMException {
			Prohibition p1 = mock(Prohibition.class);
			Prohibition p2 = mock(Prohibition.class);
			ProhibitionProto proto1 = ProhibitionProto.newBuilder().setName("test").build();
			ProhibitionProto proto2 = ProhibitionProto.newBuilder().setName("test").build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.prohibitions().getProhibitions()).thenReturn(List.of(p1, p2));
			});

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.toProhibitionProto(p1, pap.query())).thenReturn(proto1);
				pu.when(() -> ProtoUtil.toProhibitionProto(p2, pap.query())).thenReturn(proto2);

				service.getProhibitions(Empty.getDefaultInstance(), observer);

				verify(prohibitionsQueryAdjudicator).getProhibitions();

				ProhibitionListResponse expected = ProhibitionListResponse.newBuilder()
						.addAllProhibitions(List.of(proto1, proto2))
						.build();
				verify(observer).onNext(expected);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldReturnEmptyWhenNoProhibitions() throws PMException {
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.prohibitions().getProhibitions())
						.thenReturn(Collections.emptyList());
			});

			service.getProhibitions(Empty.getDefaultInstance(), observer);

			verify(prohibitionsQueryAdjudicator).getProhibitions();
			verify(observer).onNext(ProhibitionListResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getProhibitions(Empty.getDefaultInstance(), observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetProhibitionsBySubjectTests {

		@Mock
		private StreamObserver<ProhibitionListResponse> observer;

		@Test
		void shouldReturnProhibitionsForNodeSubject() throws PMException {
			GetProhibitionBySubjectQuery request = GetProhibitionBySubjectQuery.newBuilder()
					.setNodeId(1)
					.build();
			Prohibition p = mock(Prohibition.class);
			ProhibitionProto proto = ProhibitionProto.newBuilder().setName("test").build();

			ProhibitionSubject prohibitionSubject = new ProhibitionSubject(request.getNodeId());
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.prohibitions().getProhibitionsWithSubject(prohibitionSubject))
						.thenReturn(List.of(p));
			});

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.toProhibitionProto(p, pap.query())).thenReturn(proto);

				service.getProhibitionsBySubject(request, observer);

				verify(prohibitionsQueryAdjudicator).getProhibitionsWithSubject(prohibitionSubject);

				ProhibitionListResponse expected = ProhibitionListResponse.newBuilder()
						.addProhibitions(proto)
						.build();
				verify(observer).onNext(expected);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldReturnProhibitionsForProcessSubject() throws PMException {
			GetProhibitionBySubjectQuery request = GetProhibitionBySubjectQuery.newBuilder()
					.setProcess("test")
					.build();
			Prohibition p = mock(Prohibition.class);
			ProhibitionProto proto = ProhibitionProto.newBuilder().setName("test").build();

			ProhibitionSubject prohibitionSubject = new ProhibitionSubject(request.getProcess());
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.prohibitions().getProhibitionsWithSubject(prohibitionSubject))
						.thenReturn(List.of(p));
			});

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.toProhibitionProto(p, pap.query())).thenReturn(proto);

				service.getProhibitionsBySubject(request, observer);

				verify(prohibitionsQueryAdjudicator).getProhibitionsWithSubject(prohibitionSubject);

				ProhibitionListResponse expected = ProhibitionListResponse.newBuilder()
						.addProhibitions(proto)
						.build();
				verify(observer).onNext(expected);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldPropagateExceptionWhenSubjectNotSet() {
			GetProhibitionBySubjectQuery request = GetProhibitionBySubjectQuery.getDefaultInstance();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new PMRuntimeException("test exception"));

			assertThrows(PMRuntimeException.class,
			             () -> service.getProhibitionsBySubject(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetProhibitionTests {

		@Mock
		private StreamObserver<ProhibitionProto> observer;

		@Test
		void shouldReturnProhibitionProto() throws PMException {
			GetByNameQuery request = GetByNameQuery.newBuilder()
					.setName("test")
					.build();
			Prohibition p = mock(Prohibition.class);
			ProhibitionProto proto = ProhibitionProto.newBuilder().setName("test").build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.prohibitions().getProhibition(request.getName()))
						.thenReturn(p);
			});

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.toProhibitionProto(p, pap.query())).thenReturn(proto);

				service.getProhibition(request, observer);

				verify(prohibitionsQueryAdjudicator).getProhibition(request.getName());

				verify(observer).onNext(proto);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			GetByNameQuery request = GetByNameQuery.newBuilder()
					.setName("test")
					.build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getProhibition(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetInheritedProhibitionsTests {

		@Mock
		private StreamObserver<ProhibitionListResponse> observer;

		@Test
		void shouldReturnInheritedProhibitions() throws PMException {
			GetInheritedProhibitionsQuery request = GetInheritedProhibitionsQuery.newBuilder()
					.setSubjectId(1)
					.build();
			Prohibition p = mock(Prohibition.class);
			ProhibitionProto proto = ProhibitionProto.newBuilder().setName("test").build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.prohibitions().getInheritedProhibitionsFor(request.getSubjectId()))
						.thenReturn(List.of(p));
			});

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.toProhibitionProto(p, pap.query())).thenReturn(proto);

				service.getInheritedProhibitions(request, observer);

				verify(prohibitionsQueryAdjudicator).getInheritedProhibitionsFor(request.getSubjectId());

				ProhibitionListResponse expected = ProhibitionListResponse.newBuilder()
						.addProhibitions(proto)
						.build();
				verify(observer).onNext(expected);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldReturnEmptyWhenNoneInherited() throws PMException {
			GetInheritedProhibitionsQuery request = GetInheritedProhibitionsQuery.newBuilder()
					.setSubjectId(1)
					.build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.prohibitions().getInheritedProhibitionsFor(request.getSubjectId()))
						.thenReturn(Collections.emptyList());
			});

			service.getInheritedProhibitions(request, observer);

			verify(prohibitionsQueryAdjudicator).getInheritedProhibitionsFor(request.getSubjectId());
			verify(observer).onNext(ProhibitionListResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			GetInheritedProhibitionsQuery request = GetInheritedProhibitionsQuery.newBuilder()
					.setSubjectId(1)
					.build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new PMRuntimeException("test exception"));

			assertThrows(PMRuntimeException.class,
			             () -> service.getInheritedProhibitions(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetProhibitionsWithContainerTests {

		@Mock
		private StreamObserver<ProhibitionListResponse> observer;

		@Test
		void shouldReturnProhibitionsWithContainer() throws PMException {
			GetProhibitionsWithContainerQuery request = GetProhibitionsWithContainerQuery.newBuilder()
					.setContainerId(1)
					.build();
			Prohibition p = mock(Prohibition.class);
			ProhibitionProto proto = ProhibitionProto.newBuilder()
					.setName("test")
					.build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.prohibitions().getProhibitionsWithContainer(request.getContainerId()))
						.thenReturn(List.of(p));
			});

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.toProhibitionProto(p, pap.query())).thenReturn(proto);

				service.getProhibitionsWithContainer(request, observer);

				verify(prohibitionsQueryAdjudicator).getProhibitionsWithContainer(request.getContainerId());

				ProhibitionListResponse expected = ProhibitionListResponse.newBuilder()
						.addProhibitions(proto)
						.build();
				verify(observer).onNext(expected);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldReturnEmptyWhenNoContainerProhibitions() throws PMException {
			GetProhibitionsWithContainerQuery request = GetProhibitionsWithContainerQuery.newBuilder()
					.setContainerId(1)
					.build();

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.prohibitions().getProhibitionsWithContainer(request.getContainerId()))
						.thenReturn(Collections.emptyList());
			});

			service.getProhibitionsWithContainer(request, observer);

			verify(prohibitionsQueryAdjudicator).getProhibitionsWithContainer(request.getContainerId());
			verify(observer).onNext(ProhibitionListResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			GetProhibitionsWithContainerQuery request = GetProhibitionsWithContainerQuery.newBuilder()
					.setContainerId(1)
					.build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getProhibitionsWithContainer(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetObligationsTests {
		@Mock StreamObserver<ObligationListResponse> observer;

		@Test
		void shouldReturnListOfObligations() throws PMException {
			Empty request = Empty.getDefaultInstance();
			Obligation o1 = mock(Obligation.class);
			when(o1.getName()).thenReturn("test");
			when(o1.getAuthorId()).thenReturn(1L);
			when(o1.toString()).thenReturn("pml1");
			Obligation o2 = mock(Obligation.class);
			when(o2.getName()).thenReturn("test");
			when(o2.getAuthorId()).thenReturn(2L);
			when(o2.toString()).thenReturn("pml2");

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.obligations().getObligations())
						.thenReturn(List.of(o1, o2));
			});

			service.getObligations(request, observer);

			verify(obligationsQueryAdjudicator).getObligations();

			ObligationProto proto1 = ObligationProto.newBuilder()
					.setName("test").setAuthor(testNode(1L)).setPml("pml1").build();
			ObligationProto proto2 = ObligationProto.newBuilder()
					.setName("test").setAuthor(testNode(2L)).setPml("pml2").build();
			ObligationListResponse expected = ObligationListResponse.newBuilder()
					.addObligations(proto1)
					.addObligations(proto2)
					.build();

			verify(observer).onNext(expected);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyWhenNoObligations() throws PMException {
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.obligations().getObligations())
						.thenReturn(Collections.emptyList());
			});

			service.getObligations(Empty.getDefaultInstance(), observer);

			verify(obligationsQueryAdjudicator).getObligations();
			verify(observer).onNext(ObligationListResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getObligations(Empty.getDefaultInstance(), observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetObligationTests {
		@Mock StreamObserver<ObligationProto> observer;

		@Test
		void shouldReturnObligationProto() throws PMException {
			GetByNameQuery request = GetByNameQuery.newBuilder()
					.setName("test")
					.build();
			Obligation o = mock(Obligation.class);
			when(o.getName()).thenReturn("test");
			when(o.getAuthorId()).thenReturn(1L);
			when(o.toString()).thenReturn("pml");

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.obligations().getObligation(request.getName()))
						.thenReturn(o);
			});

			service.getObligation(request, observer);

			verify(obligationsQueryAdjudicator).getObligation(request.getName());

			ObligationProto expected = ObligationProto.newBuilder()
					.setName("test").setAuthor(testNode(1L)).setPml("pml").build();
			verify(observer).onNext(expected);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			GetByNameQuery request = GetByNameQuery.newBuilder()
					.setName("test")
					.build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getObligation(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class GetObligationsByAuthorTests {
		@Mock StreamObserver<ObligationListResponse> observer;

		@Test
		void shouldReturnObligationsForAuthor() throws PMException {
			GetObligationByAuthorQuery request = GetObligationByAuthorQuery.newBuilder()
					.setAuthorId(1L)
					.build();
			Obligation o = mock(Obligation.class);
			when(o.getName()).thenReturn("test");
			when(o.getAuthorId()).thenReturn(1L);
			when(o.toString()).thenReturn("pml");

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.obligations().getObligationsWithAuthor(request.getAuthorId()))
						.thenReturn(List.of(o));
			});

			service.getObligationsByAuthor(request, observer);

			verify(obligationsQueryAdjudicator).getObligationsWithAuthor(request.getAuthorId());

			ObligationProto proto = ObligationProto.newBuilder()
					.setName("test").setAuthor(testNode(1L)).setPml("pml").build();
			ObligationListResponse expected = ObligationListResponse.newBuilder()
					.addObligations(proto)
					.build();
			verify(observer).onNext(expected);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyWhenNoObligationsForAuthor() throws PMException {
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.obligations().getObligationsWithAuthor(1L))
						.thenReturn(Collections.emptyList());
			});

			service.getObligationsByAuthor(
					GetObligationByAuthorQuery.newBuilder().setAuthorId(1L).build(),
					observer
			);

			verify(obligationsQueryAdjudicator).getObligationsWithAuthor(1L);
			verify(observer).onNext(ObligationListResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getObligationsByAuthor(
					             GetObligationByAuthorQuery.newBuilder().setAuthorId(1L).build(),
					             observer
			             ));
			verifyNoMoreInteractions(observer);
		}
	}

	@Nested
	class GetResourceOperationsTests {
		@Mock
		StreamObserver<StringList> observer;

		@Test
		void shouldReturnResourceOperations() throws PMException {
			AccessRightSet arset = new AccessRightSet("read", "write");

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.operations().getResourceOperations()).thenReturn(arset);
			});

			service.getResourceOperations(Empty.getDefaultInstance(), observer);

			verify(operationsQueryAdjudicator).getResourceOperations();
			StringList expected = StringList.newBuilder()
					.addAllValues(arset)
					.build();
			verify(observer).onNext(expected);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyWhenNoOperations() throws PMException {
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.operations().getResourceOperations())
						.thenReturn(new AccessRightSet());
			});

			service.getResourceOperations(Empty.getDefaultInstance(), observer);

			verify(operationsQueryAdjudicator).getResourceOperations();
			verify(observer).onNext(StringList.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getResourceOperations(Empty.getDefaultInstance(), observer));
			verifyNoMoreInteractions(observer);
		}
	}

	@Nested
	class GetAdminOperationNamesTests {
		@Mock
		StreamObserver<GetAdminOperationNamesResponse> observer;

		@Test
		void shouldReturnOperationNames() throws PMException {
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.operations().getAdminOperationNames())
						.thenReturn(List.of("op1", "op2"));
			});

			service.getAdminOperationNames(Empty.getDefaultInstance(), observer);

			verify(operationsQueryAdjudicator).getAdminOperationNames();
			GetAdminOperationNamesResponse expected = GetAdminOperationNamesResponse.newBuilder()
					.addAllNames(List.of("op1", "op2"))
					.build();
			verify(observer).onNext(expected);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyWhenNoOperationNames() throws PMException {
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.operations().getAdminOperationNames())
						.thenReturn(Collections.emptyList());
			});

			service.getAdminOperationNames(Empty.getDefaultInstance(), observer);

			verify(operationsQueryAdjudicator).getAdminOperationNames();
			verify(observer).onNext(GetAdminOperationNamesResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getAdminOperationNames(Empty.getDefaultInstance(), observer));
			verifyNoMoreInteractions(observer);
		}
	}

	@Nested
	class GetAdminOperationTests {
		@Mock
		StreamObserver<OperationResponse> observer;

		@Test
		void shouldReturnPmlWhenOperationIsPML() throws PMException {
			GetAdminOperationQuery request = GetAdminOperationQuery.newBuilder()
					.setOperationName("test")
					.build();
			Operation operation = mock(PMLStmtsOperation.class);
			when(operation.toString()).thenReturn("test");

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.operations().getAdminOperation(request.getOperationName())).thenReturn(operation);
			});

			service.getAdminOperation(request, observer);

			verify(operationsQueryAdjudicator).getAdminOperation(request.getOperationName());
			OperationResponse expected = OperationResponse.newBuilder()
					.setPml("test")
					.build();
			verify(observer).onNext(expected);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldThrowWhenNotPMLOperation() {
			GetAdminOperationQuery request = GetAdminOperationQuery.newBuilder()
					.setOperationName("test")
					.build();
			Operation notPmlOp = mock(Operation.class);

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.operations().getAdminOperation(request.getOperationName()))
						.thenReturn(notPmlOp);
			});

			assertThrows(PMRuntimeException.class,
			             () -> service.getAdminOperation(request, observer));
			verifyNoMoreInteractions(observer);
		}
	}

	@Nested
	class GetAdminRoutineNamesTests {
		@Mock
		StreamObserver<GetAdminRoutineNamesResponse> observer;

		@Test
		void shouldReturnRoutineNames() throws PMException {
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.routines().getAdminRoutineNames())
						.thenReturn(List.of("r1", "r2"));
			});

			service.getAdminRoutineNames(Empty.getDefaultInstance(), observer);

			verify(routinesQueryAdjudicator).getAdminRoutineNames();
			GetAdminRoutineNamesResponse expected = GetAdminRoutineNamesResponse.newBuilder()
					.addAllNames(List.of("r1", "r2"))
					.build();
			verify(observer).onNext(expected);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyWhenNoRoutineNames() throws PMException {
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.routines().getAdminRoutineNames())
						.thenReturn(Collections.emptyList());
			});

			service.getAdminRoutineNames(Empty.getDefaultInstance(), observer);

			verify(routinesQueryAdjudicator).getAdminRoutineNames();
			verify(observer).onNext(GetAdminRoutineNamesResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.getAdminRoutineNames(Empty.getDefaultInstance(), observer));
			verifyNoMoreInteractions(observer);
		}
	}

	@Nested
	class GetAdminRoutineTests {
		@Mock
		StreamObserver<RoutineResponse> observer;

		@Test
		void shouldReturnPmlWhenRoutineIsPML() throws PMException {
			GetAdminRoutineQuery request = GetAdminRoutineQuery.newBuilder()
					.setRoutineName("test")
					.build();
			Routine routine = mock(PMLStmtsRoutine.class);
			when(routine.toString()).thenReturn("test");

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.routines().getAdminRoutine(request.getRoutineName()))
						.thenReturn(routine);
			});

			service.getAdminRoutine(request, observer);

			verify(routinesQueryAdjudicator).getAdminRoutine(request.getRoutineName());
			RoutineResponse expected = RoutineResponse.newBuilder()
					.setPml("test")
					.build();
			verify(observer).onNext(expected);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldThrowWhenNotPMLRoutine() {
			GetAdminRoutineQuery request = GetAdminRoutineQuery.newBuilder()
					.setRoutineName("test")
					.build();
			Routine notPml = mock(Routine.class);

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.routines().getAdminRoutine(request.getRoutineName()))
						.thenReturn(notPml);
			});

			assertThrows(PMRuntimeException.class,
			             () -> service.getAdminRoutine(request, observer));
			verifyNoMoreInteractions(observer);
		}
	}

	@Nested
	class ComputePrivilegesTests {

		@Mock
		private StreamObserver<StringList> observer;

		@Test
		void shouldReturnPrivilegesSet() throws PMException {
			ComputePrivilegesQuery request = ComputePrivilegesQuery.newBuilder()
					.setUserCtx(UserContextProto.newBuilder().setId(1).build())
					.setTargetCtx(TargetContextProto.newBuilder().setId(2).build())
					.build();
			AccessRightSet privs = new AccessRightSet("read", "write");

			UserContext userContext = ProtoUtil.fromUserContextProto(request.getUserCtx());
			TargetContext targetContext = ProtoUtil.fromTargetContextProto(request.getTargetCtx());

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.access().computePrivileges(
						userContext,
						targetContext
				)).thenReturn(privs);
			});

			service.computePrivileges(request, observer);

			verify(accessQueryAdjudicator).computePrivileges(userContext, targetContext);
			verify(observer).onNext(
					StringList.newBuilder()
							.addAllValues(privs)
							.build()
			);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyWhenNoPrivileges() throws PMException {
			ComputePrivilegesQuery request = ComputePrivilegesQuery.newBuilder()
					.setUserCtx(UserContextProto.newBuilder().setId(1).build())
					.setTargetCtx(TargetContextProto.newBuilder().setId(2).build())
					.build();
			AccessRightSet empty = mock(AccessRightSet.class);
			when(empty.iterator()).thenReturn(new AccessRightSet().iterator());

			UserContext userContext = ProtoUtil.fromUserContextProto(request.getUserCtx());
			TargetContext targetContext = ProtoUtil.fromTargetContextProto(request.getTargetCtx());

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.access().computePrivileges(userContext, targetContext)).thenReturn(empty);
			});

			service.computePrivileges(request, observer);

			verify(accessQueryAdjudicator).computePrivileges(userContext, targetContext);
			verify(observer).onNext(StringList.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			ComputePrivilegesQuery request = ComputePrivilegesQuery.newBuilder().build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.computePrivileges(request, observer));
			verifyNoMoreInteractions(observer);
		}
	}

	@Nested
	class ComputeDeniedPrivilegesTests {

		@Mock
		private StreamObserver<StringList> observer;

		@Test
		void shouldReturnDeniedPrivileges() throws PMException {
			ComputeDeniedPrivilegesQuery request = ComputeDeniedPrivilegesQuery.newBuilder()
					.setUserCtx(UserContextProto.newBuilder().setId(1).build())
					.setTargetCtx(TargetContextProto.newBuilder().setId(2).build())
					.build();
			AccessRightSet denied = mock(AccessRightSet.class);
			when(denied.iterator()).thenReturn(new AccessRightSet("test").iterator());

			UserContext userContext = ProtoUtil.fromUserContextProto(request.getUserCtx());
			TargetContext targetContext = ProtoUtil.fromTargetContextProto(request.getTargetCtx());

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.access().computeDeniedPrivileges(userContext, targetContext))
						.thenReturn(denied);
			});

			service.computeDeniedPrivileges(request, observer);

			verify(accessQueryAdjudicator).computeDeniedPrivileges(userContext, targetContext);
			verify(observer).onNext(
					StringList.newBuilder()
							.addAllValues(List.of("test"))
							.build()
			);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyWhenNoDeniedPrivileges() throws PMException {
			ComputeDeniedPrivilegesQuery request = ComputeDeniedPrivilegesQuery.newBuilder()
					.setUserCtx(UserContextProto.newBuilder().setId(1).build())
					.setTargetCtx(TargetContextProto.newBuilder().setId(2).build())
					.build();
			AccessRightSet empty = mock(AccessRightSet.class);
			when(empty.iterator()).thenReturn(new AccessRightSet().iterator());

			UserContext userContext = ProtoUtil.fromUserContextProto(request.getUserCtx());
			TargetContext targetContext = ProtoUtil.fromTargetContextProto(request.getTargetCtx());

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.access().computeDeniedPrivileges(userContext, targetContext)).thenReturn(empty);
			});

			service.computeDeniedPrivileges(request, observer);

			verify(accessQueryAdjudicator).computeDeniedPrivileges(userContext, targetContext);
			verify(observer).onNext(StringList.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			ComputeDeniedPrivilegesQuery request = ComputeDeniedPrivilegesQuery.newBuilder().build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.computeDeniedPrivileges(request, observer));
			verifyNoMoreInteractions(observer);
		}
	}

	@Nested
	class ComputeCapabilityListTests {

		@Mock
		private StreamObserver<AccessQueryMappingResponse> observer;

		@Test
		void shouldReturnCapabilityList() throws PMException {
			ComputeCapabilityListQuery request = ComputeCapabilityListQuery.newBuilder()
					.setUserCtx(UserContextProto.newBuilder().setId(1)).build();
			AccessRightSet set = mock(AccessRightSet.class);
			when(set.iterator()).thenReturn(new AccessRightSet("test").iterator());
			Map<Long, AccessRightSet> map = Map.of(1L, set);

			UserContext userContext = ProtoUtil.fromUserContextProto(request.getUserCtx());

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.access().computeCapabilityList(userContext))
						.thenReturn(map);
			});

			service.computeCapabilityList(request, observer);

			verify(accessQueryAdjudicator).computeCapabilityList(userContext);
			AccessQueryMappingResponse expected = AccessQueryMappingResponse.newBuilder()
					.putAllMap(Map.of(1L, AccessQueryMappingEntry.newBuilder().addArset("test").setNode(testNode(1)).build()))
					.build();
			verify(observer).onNext(expected);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyWhenNoCapabilities() throws PMException {
			ComputeCapabilityListQuery request = ComputeCapabilityListQuery.newBuilder()
					.setUserCtx(UserContextProto.newBuilder().setId(1).build()).build();
			UserContext userContext = ProtoUtil.fromUserContextProto(request.getUserCtx());

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.access().computeCapabilityList(userContext))
						.thenReturn(Collections.emptyMap());
			});

			service.computeCapabilityList(request, observer);

			verify(accessQueryAdjudicator).computeCapabilityList(userContext);
			verify(observer).onNext(AccessQueryMappingResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			ComputeCapabilityListQuery request = ComputeCapabilityListQuery.newBuilder().build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.computeCapabilityList(request, observer));
			verifyNoMoreInteractions(observer);
		}
	}

	@Nested
	class ComputeACLTests {

		@Mock
		private StreamObserver<AccessQueryMappingResponse> observer;

		@Test
		void shouldReturnAclMap() throws PMException {
			ComputeACLQuery request = ComputeACLQuery.newBuilder()
					.setTargetCtx(TargetContextProto.newBuilder().setId(1))
					.build();
			AccessRightSet set = mock(AccessRightSet.class);
			when(set.iterator()).thenReturn(new AccessRightSet("test").iterator());
			Map<Long, AccessRightSet> map = Map.of(1L, set);

			TargetContext targetContext = ProtoUtil.fromTargetContextProto(request.getTargetCtx());

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.access().computeACL(targetContext))
						.thenReturn(map);
			});

			service.computeACL(request, observer);

			verify(accessQueryAdjudicator).computeACL(targetContext);
			AccessQueryMappingResponse expected = AccessQueryMappingResponse.newBuilder()
					.putAllMap(Map.of(1L, AccessQueryMappingEntry.newBuilder().addArset("test").setNode(testNode(1)).build()))
					.build();
			verify(observer).onNext(expected);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyWhenNoAclEntries() throws PMException {
			ComputeACLQuery request = ComputeACLQuery.newBuilder()
					.setTargetCtx(TargetContextProto.newBuilder().setId(1))
					.build();
			TargetContext targetContext = ProtoUtil.fromTargetContextProto(request.getTargetCtx());

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.access().computeACL(targetContext))
						.thenReturn(Collections.emptyMap());
			});

			service.computeACL(request, observer);

			verify(accessQueryAdjudicator).computeACL(targetContext);
			verify(observer).onNext(AccessQueryMappingResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			ComputeACLQuery request = ComputeACLQuery.newBuilder().build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.computeACL(request, observer));
			verifyNoMoreInteractions(observer);
		}
	}

	@Nested
	class ComputeDestinationAttributesTests {

		@Mock
		private StreamObserver<AccessQueryMappingResponse> observer;

		@Test
		void shouldReturnDestinationAttributes() throws PMException {
			ComputeDestinationAttributesQuery request =
					ComputeDestinationAttributesQuery.newBuilder()
							.setUserCtx(UserContextProto.newBuilder().setId(1))
							.build();
			AccessRightSet set = mock(AccessRightSet.class);
			when(set.iterator()).thenReturn(new AccessRightSet("test").iterator());
			Map<Long, AccessRightSet> map = Map.of(1L, set);

			UserContext userContext = ProtoUtil.fromUserContextProto(request.getUserCtx());
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.access().computeDestinationAttributes(userContext))
						.thenReturn(map);
			});

			service.computeDestinationAttributes(request, observer);

			verify(accessQueryAdjudicator).computeDestinationAttributes(userContext);
			AccessQueryMappingResponse expected = AccessQueryMappingResponse.newBuilder()
					.putAllMap(Map.of(1L, AccessQueryMappingEntry.newBuilder().addArset("test").setNode(testNode(1)).build()))
					.build();
			verify(observer).onNext(expected);
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldReturnEmptyWhenNoDestinationAttributes() throws PMException {
			ComputeDestinationAttributesQuery request =
					ComputeDestinationAttributesQuery.newBuilder()
							.setUserCtx(UserContextProto.newBuilder().setId(1))
							.build();

			UserContext userContext = ProtoUtil.fromUserContextProto(request.getUserCtx());
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.access().computeDestinationAttributes(userContext))
						.thenReturn(Collections.emptyMap());
			});

			service.computeDestinationAttributes(request, observer);

			verify(accessQueryAdjudicator).computeDestinationAttributes(userContext);
			verify(observer).onNext(AccessQueryMappingResponse.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			ComputeDestinationAttributesQuery request =
					ComputeDestinationAttributesQuery.newBuilder().build();
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.computeDestinationAttributes(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class ComputeSubgraphPrivilegesTests {

		@Mock
		private StreamObserver<SubgraphPrivilegesProto> observer;

		private final AccessWithRootQuery request = AccessWithRootQuery.newBuilder()
				.setUserCtx(UserContextProto.getDefaultInstance())
				.setRoot(1)
				.build();

		@Test
		void shouldReturnSingleNodeSubgraphPrivileges() throws PMException {
			Node node = mock(Node.class);
			SubgraphPrivileges model = new SubgraphPrivileges(node,
			                                                  new AccessRightSet("read"),
			                                                  Collections.emptyList()
			);

			UserContext userCtx = mock(UserContext.class);

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.fromUserContextProto(request.getUserCtx()))
						.thenReturn(userCtx);

				stubAdjudicatorTx(adjudicator -> {
					when(adjudicator.access()
							     .computeSubgraphPrivileges(userCtx, request.getRoot()))
							.thenReturn(model);
				});

				var nodeProto = gov.nist.csd.pm.pdp.proto.model.NodeProto.newBuilder()
						.setName("test")
						.build();
				pu.when(() -> ProtoUtil.toNodeProto(node)).thenReturn(nodeProto);

				service.computeSubgraphPrivileges(request, observer);

				verify(accessQueryAdjudicator)
						.computeSubgraphPrivileges(userCtx, request.getRoot());

				SubgraphPrivilegesProto expected = SubgraphPrivilegesProto.newBuilder()
						.setNode(nodeProto)
						.addAllArset(List.of("read"))
						.build();
				verify(observer).onNext(expected);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldReturnNestedSubgraphPrivileges() throws PMException {
			Node parentNode = mock(Node.class);
			Node childNode  = mock(Node.class);
			var childModel = new SubgraphPrivileges(childNode,
			                                        new AccessRightSet("read"),
			                                        new ArrayList<>()
			);
			var parentModel = new SubgraphPrivileges(parentNode,
			                                         new AccessRightSet("read"),
			                                         new ArrayList<>(List.of(childModel))
			);

			UserContext userCtx = mock(UserContext.class);

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.fromUserContextProto(request.getUserCtx()))
						.thenReturn(userCtx);

				stubAdjudicatorTx(adjudicator -> {
					when(adjudicator.access().computeSubgraphPrivileges(userCtx, request.getRoot()))
							.thenReturn(parentModel);
				});

				var parentProto = gov.nist.csd.pm.pdp.proto.model.NodeProto.newBuilder()
						.setName("parent")
						.build();
				var childProto = gov.nist.csd.pm.pdp.proto.model.NodeProto.newBuilder()
						.setName("child")
						.build();
				pu.when(() -> ProtoUtil.toNodeProto(parentNode)).thenReturn(parentProto);
				pu.when(() -> ProtoUtil.toNodeProto(childNode)).thenReturn(childProto);

				service.computeSubgraphPrivileges(request, observer);

				verify(accessQueryAdjudicator)
						.computeSubgraphPrivileges(userCtx, request.getRoot());

				SubgraphPrivilegesProto expectedChild = SubgraphPrivilegesProto.newBuilder()
						.setNode(childProto)
						.addAllArset(List.of("read"))
						.build();
				SubgraphPrivilegesProto expected = SubgraphPrivilegesProto.newBuilder()
						.setNode(parentProto)
						.addAllArset(List.of("read"))
						.addAscendants(expectedChild)
						.build();
				verify(observer).onNext(expected);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.computeSubgraphPrivileges(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class ComputeAdjacentAscendantPrivilegesTests {

		@Mock
		private StreamObserver<NodePrivilegeList> observer;

		private final AccessWithRootQuery request = AccessWithRootQuery.newBuilder()
				.setUserCtx(UserContextProto.getDefaultInstance())
				.setRoot(1)
				.build();

		@Test
		void shouldReturnNodePrivilegesInAnyOrder() throws PMException {
			Node n1 = mock(Node.class), n2 = mock(Node.class);
			AccessRightSet s1 = new AccessRightSet("read"), s2 = new AccessRightSet("write");
			Map<Node, AccessRightSet> map = Map.of(n1, s1, n2, s2);

			NodeProto p1 = NodeProto.newBuilder().setName("n1").build();
			NodeProto p2 = NodeProto.newBuilder().setName("n2").build();
			NodePrivilege np1 = NodePrivilege.newBuilder()
					.setNode(p1)
					.addAllArset(List.of("read"))
					.build();
			NodePrivilege np2 = NodePrivilege.newBuilder()
					.setNode(p2)
					.addAllArset(List.of("write"))
					.build();

			UserContext userCtx = mock(UserContext.class);

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.fromUserContextProto(request.getUserCtx()))
						.thenReturn(userCtx);
				pu.when(() -> ProtoUtil.toNodeProto(n1)).thenReturn(p1);
				pu.when(() -> ProtoUtil.toNodeProto(n2)).thenReturn(p2);

				stubAdjudicatorTx(adjudicator -> {
					when(adjudicator.access()
							     .computeAdjacentAscendantPrivileges(userCtx, request.getRoot()))
							.thenReturn(map);
				});

				service.computeAdjacentAscendantPrivileges(request, observer);

				verify(accessQueryAdjudicator)
						.computeAdjacentAscendantPrivileges(userCtx, request.getRoot());

				ArgumentCaptor<NodePrivilegeList> captor =
						ArgumentCaptor.forClass(NodePrivilegeList.class);
				verify(observer).onNext(captor.capture());
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);

				List<NodePrivilege> actual = captor.getValue().getPrivilegesList();
				assertThat(actual, containsInAnyOrder(np1, np2));
			}
		}

		@Test
		void shouldReturnEmptyWhenNoPrivileges() throws PMException {
			UserContext userCtx = mock(UserContext.class);
			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.fromUserContextProto(request.getUserCtx()))
						.thenReturn(userCtx);

				stubAdjudicatorTx(adjudicator -> {
					when(adjudicator.access()
							     .computeAdjacentAscendantPrivileges(userCtx, request.getRoot()))
							.thenReturn(Collections.emptyMap());
				});

				service.computeAdjacentAscendantPrivileges(request, observer);

					verify(accessQueryAdjudicator)
						.computeAdjacentAscendantPrivileges(userCtx, request.getRoot());
				verify(observer).onNext(NodePrivilegeList.newBuilder().build());
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.computeAdjacentAscendantPrivileges(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class ComputeAdjacentDescendantPrivilegesTests {

		@Mock
		private StreamObserver<NodePrivilegeList> observer;

		private final AccessWithRootQuery request = AccessWithRootQuery.newBuilder()
				.setUserCtx(UserContextProto.getDefaultInstance())
				.setRoot(1)
				.build();

		@Test
		void shouldReturnNodePrivilegesInAnyOrder() throws PMException {
			Node n1 = mock(Node.class), n2 = mock(Node.class);
			AccessRightSet s1 = new AccessRightSet("create"), s2 = new AccessRightSet("delete");
			Map<Node, AccessRightSet> map = Map.of(n1, s1, n2, s2);

			UserContext userCtx = mock(UserContext.class);

			NodeProto p1 = NodeProto.newBuilder().setName("n1").build();
			NodeProto p2 = NodeProto.newBuilder().setName("n2").build();
			NodePrivilege np1 = NodePrivilege.newBuilder()
					.setNode(p1)
					.addAllArset(List.of("create"))
					.build();
			NodePrivilege np2 = NodePrivilege.newBuilder()
					.setNode(p2)
					.addAllArset(List.of("delete"))
					.build();

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.fromUserContextProto(request.getUserCtx()))
						.thenReturn(userCtx);
				pu.when(() -> ProtoUtil.toNodeProto(n1)).thenReturn(p1);
				pu.when(() -> ProtoUtil.toNodeProto(n2)).thenReturn(p2);

				stubAdjudicatorTx(adjudicator -> {
					when(adjudicator.access()
							     .computeAdjacentDescendantPrivileges(userCtx, request.getRoot()))
							.thenReturn(map);
				});

				service.computeAdjacentDescendantPrivileges(request, observer);

				verify(accessQueryAdjudicator)
						.computeAdjacentDescendantPrivileges(userCtx, request.getRoot());

				ArgumentCaptor<NodePrivilegeList> cap =
						ArgumentCaptor.forClass(NodePrivilegeList.class);
				verify(observer).onNext(cap.capture());
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);

				List<NodePrivilege> actual = cap.getValue().getPrivilegesList();
				assertThat(actual, containsInAnyOrder(np1, np2));
			}
		}

		@Test
		void shouldReturnEmptyWhenNoPrivileges() throws PMException {
			UserContext userCtx = mock(UserContext.class);

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.fromUserContextProto(request.getUserCtx()))
						.thenReturn(userCtx);

				stubAdjudicatorTx(adjudicator -> {
					when(adjudicator.access()
							     .computeAdjacentDescendantPrivileges(userCtx, request.getRoot()))
							.thenReturn(Collections.emptyMap());
				});

				service.computeAdjacentDescendantPrivileges(request, observer);

					verify(accessQueryAdjudicator)
						.computeAdjacentDescendantPrivileges(userCtx, request.getRoot());
				verify(observer).onNext(NodePrivilegeList.newBuilder().build());
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.computeAdjacentDescendantPrivileges(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class ExplainTests {
		@Mock
		private StreamObserver<ExplainResponse> observer;

		private final ExplainQuery request = ExplainQuery.newBuilder()
				.setUserCtx(UserContextProto.getDefaultInstance())
				.setTargetCtx(TargetContextProto.getDefaultInstance())
				.build();

		@Test
		void shouldReturnExplainResponse() throws PMException {
			Explain explainModel = mock(Explain.class);
			ExplainResponse expectedResponse = ExplainResponse.newBuilder()
					.addPrivileges("read")
					.build();

			UserContext userCtx = mock(UserContext.class);
			TargetContext targetCtx = mock(TargetContext.class);

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.fromUserContextProto(request.getUserCtx()))
						.thenReturn(userCtx);
				pu.when(() -> ProtoUtil.fromTargetContextProto(request.getTargetCtx()))
						.thenReturn(targetCtx);

				stubAdjudicatorTx(adjudicator -> {
					when(adjudicator.access().explain(userCtx, targetCtx))
							.thenReturn(explainModel);
				});

				pu.when(() -> ProtoUtil.buildExplainResponse(explainModel, pap.query()))
						.thenReturn(expectedResponse);

				service.explain(request, observer);

				verify(accessQueryAdjudicator).explain(userCtx, targetCtx);

				verify(observer).onNext(expectedResponse);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldPropagateExceptionAndNotCallObserver() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.explain(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class ComputePersonalObjectSystemTests {

		@Mock
		private StreamObserver<NodePrivilegeList> observer;

		private final ComputePOSQuery request = ComputePOSQuery.newBuilder()
				.setUserCtx(UserContextProto.getDefaultInstance())
				.build();

		@Test
		void shouldReturnNodePrivilegesInAnyOrder() throws PMException {
			Node n1 = mock(Node.class), n2 = mock(Node.class);
			AccessRightSet s1 = new AccessRightSet("read"), s2 = new AccessRightSet("write");
			Map<Node, AccessRightSet> map = Map.of(n1, s1, n2, s2);

			UserContext userCtx = mock(UserContext.class);
			NodeProto p1 = NodeProto.newBuilder().setName("n1").build();
			NodeProto p2 = NodeProto.newBuilder().setName("n2").build();
			NodePrivilege np1 = NodePrivilege.newBuilder()
					.setNode(p1)
					.addAllArset(List.of("read"))
					.build();
			NodePrivilege np2 = NodePrivilege.newBuilder()
					.setNode(p2)
					.addAllArset(List.of("write"))
					.build();

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.fromUserContextProto(request.getUserCtx()))
						.thenReturn(userCtx);
				pu.when(() -> ProtoUtil.toNodeProto(n1)).thenReturn(p1);
				pu.when(() -> ProtoUtil.toNodeProto(n2)).thenReturn(p2);

				stubAdjudicatorTx(adjudicator -> {
					when(adjudicator.access().computePersonalObjectSystem(userCtx))
							.thenReturn(map);
				});

				service.computePersonalObjectSystem(request, observer);

				verify(accessQueryAdjudicator).computePersonalObjectSystem(userCtx);

				ArgumentCaptor<NodePrivilegeList> cap =
						ArgumentCaptor.forClass(NodePrivilegeList.class);
				verify(observer).onNext(cap.capture());
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);

				List<NodePrivilege> actual = cap.getValue().getPrivilegesList();
				assertThat(actual, containsInAnyOrder(np1, np2));
			}
		}

		@Test
		void shouldReturnEmptyWhenNoEntries() throws PMException {
			UserContext userCtx = mock(UserContext.class);

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.fromUserContextProto(request.getUserCtx()))
						.thenReturn(userCtx);

				stubAdjudicatorTx(adjudicator -> {
					when(adjudicator.access().computePersonalObjectSystem(userCtx))
							.thenReturn(Collections.emptyMap());
				});

				service.computePersonalObjectSystem(request, observer);

					verify(accessQueryAdjudicator).computePersonalObjectSystem(userCtx);
				verify(observer).onNext(NodePrivilegeList.newBuilder().build());
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldPropagateException() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.computePersonalObjectSystem(request, observer));
			verifyNoInteractions(observer);
		}
	}

	@Nested
	class SelfComputePrivilegesTests {
		@Mock
		private StreamObserver<StringList> observer;

		private final TargetContextProto request =
				TargetContextProto.newBuilder().setId(1).build();

		@Test
		void shouldReturnPrivileges() throws PMException {
			AccessRightSet privs = new AccessRightSet("read", "write");
			TargetContext targetCtx = mock(TargetContext.class);

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.fromTargetContextProto(request))
						.thenReturn(targetCtx);

				stubAdjudicatorTx(adjudicator -> {
					when(adjudicator.selfAccess().computePrivileges(targetCtx))
							.thenReturn(privs);
				});

				service.selfComputePrivileges(request, observer);

					verify(selfAccessQueryAdjudicator).computePrivileges(targetCtx);
				StringList expected = StringList.newBuilder()
						.addAllValues(privs)
						.build();
				verify(observer).onNext(expected);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldReturnEmptyWhenNoPrivileges() throws PMException {
			AccessRightSet empty = mock(AccessRightSet.class);
			when(empty.iterator()).thenReturn(new AccessRightSet().iterator());
			TargetContext targetCtx = mock(TargetContext.class);

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.fromTargetContextProto(request))
						.thenReturn(targetCtx);

				stubAdjudicatorTx(adjudicator -> {
					when(adjudicator.selfAccess().computePrivileges(targetCtx))
							.thenReturn(empty);
				});

				service.selfComputePrivileges(request, observer);

					verify(selfAccessQueryAdjudicator).computePrivileges(targetCtx);
				verify(observer).onNext(StringList.newBuilder().build());
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldPropagateException() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.selfComputePrivileges(request, observer));
			verifyNoMoreInteractions(observer);
		}
	}

	@Nested
	class SelfComputeSubgraphPrivilegesTests {
		@Mock
		private StreamObserver<SubgraphPrivilegesProto> observer;

		private final SelfAccessWithRootQuery request =
				SelfAccessWithRootQuery.newBuilder().setRoot(1).build();

		@Test
		void shouldReturnSingleNode() throws PMException {
			Node node = mock(Node.class);
			SubgraphPrivileges model = new SubgraphPrivileges(node,
			                                                  new AccessRightSet("read"), Collections.emptyList()
			);

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.selfAccess().computeSubgraphPrivileges(request.getRoot()))
						.thenReturn(model);
			});

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				var nodeProto = gov.nist.csd.pm.pdp.proto.model.NodeProto.newBuilder()
						.setName("test").build();
				pu.when(() -> ProtoUtil.toNodeProto(node)).thenReturn(nodeProto);

				service.selfComputeSubgraphPrivileges(request, observer);

					verify(selfAccessQueryAdjudicator)
						.computeSubgraphPrivileges(request.getRoot());
				SubgraphPrivilegesProto expected = SubgraphPrivilegesProto.newBuilder()
						.setNode(nodeProto)
						.addAllArset(List.of("read"))
						.build();
				verify(observer).onNext(expected);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldReturnNestedNodes() throws PMException {
			Node parent = mock(Node.class), child = mock(Node.class);
			var childModel = new SubgraphPrivileges(child,
			                                        new AccessRightSet("read"), new ArrayList<>()
			);
			var parentModel = new SubgraphPrivileges(parent,
			                                         new AccessRightSet("read"), new ArrayList<>(List.of(childModel))
			);

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.selfAccess().computeSubgraphPrivileges(request.getRoot()))
						.thenReturn(parentModel);
			});

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				var parentProto = gov.nist.csd.pm.pdp.proto.model.NodeProto.newBuilder()
						.setName("p").build();
				var childProto  = gov.nist.csd.pm.pdp.proto.model.NodeProto.newBuilder()
						.setName("c").build();
				pu.when(() -> ProtoUtil.toNodeProto(parent)).thenReturn(parentProto);
				pu.when(() -> ProtoUtil.toNodeProto(child)).thenReturn(childProto);

				service.selfComputeSubgraphPrivileges(request, observer);

				verify(selfAccessQueryAdjudicator)
						.computeSubgraphPrivileges(request.getRoot());
				SubgraphPrivilegesProto expectedChild = SubgraphPrivilegesProto.newBuilder()
						.setNode(childProto)
						.addAllArset(List.of("read"))
						.build();
				SubgraphPrivilegesProto expected = SubgraphPrivilegesProto.newBuilder()
						.setNode(parentProto)
						.addAllArset(List.of("read"))
						.addAscendants(expectedChild)
						.build();
				verify(observer).onNext(expected);
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);
			}
		}

		@Test
		void shouldPropagateException() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.selfComputeSubgraphPrivileges(request, observer));
			verifyNoMoreInteractions(observer);
		}
	}

	@Nested
	class SelfComputeAdjacentAscendantPrivilegesTests {
		@Mock
		private StreamObserver<NodePrivilegeList> observer;

		private final SelfAccessWithRootQuery request =
				SelfAccessWithRootQuery.newBuilder().setRoot(1).build();

		@Test
		void shouldReturnNodePrivilegesOrderAgnostic() throws PMException {
			Node n1 = mock(Node.class), n2 = mock(Node.class);
			AccessRightSet s1 = new AccessRightSet("create"), s2 = new AccessRightSet("delete");
			Map<Node, AccessRightSet> map = Map.of(n1, s1, n2, s2);

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.selfAccess()
						     .computeAdjacentAscendantPrivileges(request.getRoot()))
						.thenReturn(map);
			});

			NodeProto p1 = NodeProto.newBuilder().setName("t1").build();
			NodeProto p2 = NodeProto.newBuilder().setName("t2").build();
			NodePrivilege np1 = NodePrivilege.newBuilder()
					.setNode(p1)
					.addAllArset(List.of("create"))
					.build();
			NodePrivilege np2 = NodePrivilege.newBuilder()
					.setNode(p2)
					.addAllArset(List.of("delete"))
					.build();

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.toNodeProto(n1)).thenReturn(p1);
				pu.when(() -> ProtoUtil.toNodeProto(n2)).thenReturn(p2);

				service.selfComputeAdjacentAscendantPrivileges(request, observer);

				verify(selfAccessQueryAdjudicator)
						.computeAdjacentAscendantPrivileges(request.getRoot());

				ArgumentCaptor<NodePrivilegeList> cap =
						ArgumentCaptor.forClass(NodePrivilegeList.class);
				verify(observer).onNext(cap.capture());
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);

				List<NodePrivilege> actual = cap.getValue().getPrivilegesList();
				assertThat(actual, containsInAnyOrder(np1, np2));
			}
		}

		@Test
		void shouldReturnEmptyWhenNone() throws PMException {
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.selfAccess()
						     .computeAdjacentAscendantPrivileges(request.getRoot()))
						.thenReturn(Collections.emptyMap());
			});

			service.selfComputeAdjacentAscendantPrivileges(request, observer);

			verify(selfAccessQueryAdjudicator)
					.computeAdjacentAscendantPrivileges(request.getRoot());
			verify(observer).onNext(NodePrivilegeList.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateException() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.selfComputeAdjacentAscendantPrivileges(request, observer));
			verifyNoMoreInteractions(observer);
		}
	}

	@Nested
	class SelfComputeAdjacentDescendantPrivilegesTests {
		@Mock
		private StreamObserver<NodePrivilegeList> observer;

		private final SelfAccessWithRootQuery request =
				SelfAccessWithRootQuery.newBuilder().setRoot(1).build();

		@Test
		void shouldReturnNodePrivilegesOrderAgnostic() throws PMException {
			Node n1 = mock(Node.class), n2 = mock(Node.class);
			AccessRightSet s1 = new AccessRightSet("create"), s2 = new AccessRightSet("delete");
			Map<Node, AccessRightSet> map = Map.of(n1, s1, n2, s2);

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.selfAccess()
						     .computeAdjacentDescendantPrivileges(request.getRoot()))
						.thenReturn(map);
			});

			NodeProto p1 = NodeProto.newBuilder().setName("t1").build();
			NodeProto p2 = NodeProto.newBuilder().setName("t2").build();
			NodePrivilege np1 = NodePrivilege.newBuilder()
					.setNode(p1)
					.addAllArset(List.of("create"))
					.build();
			NodePrivilege np2 = NodePrivilege.newBuilder()
					.setNode(p2)
					.addAllArset(List.of("delete"))
					.build();

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.toNodeProto(n1)).thenReturn(p1);
				pu.when(() -> ProtoUtil.toNodeProto(n2)).thenReturn(p2);

				service.selfComputeAdjacentDescendantPrivileges(request, observer);

				verify(selfAccessQueryAdjudicator)
						.computeAdjacentDescendantPrivileges(request.getRoot());

				ArgumentCaptor<NodePrivilegeList> cap =
						ArgumentCaptor.forClass(NodePrivilegeList.class);
				verify(observer).onNext(cap.capture());
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);

				List<NodePrivilege> actual = cap.getValue().getPrivilegesList();
				assertThat(actual, containsInAnyOrder(np1, np2));
			}
		}

		@Test
		void shouldReturnEmptyWhenNone() throws PMException {
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.selfAccess()
						     .computeAdjacentDescendantPrivileges(request.getRoot()))
						.thenReturn(Collections.emptyMap());
			});

			service.selfComputeAdjacentDescendantPrivileges(request, observer);

			verify(selfAccessQueryAdjudicator)
					.computeAdjacentDescendantPrivileges(request.getRoot());
			verify(observer).onNext(NodePrivilegeList.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateException() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.selfComputeAdjacentDescendantPrivileges(request, observer));
			verifyNoMoreInteractions(observer);
		}
	}

	@Nested
	class SelfComputePersonalObjectSystemTests {
		@Mock
		private StreamObserver<NodePrivilegeList> observer;

		private final Empty request = Empty.getDefaultInstance();

		@Test
		void shouldReturnNodePrivilegesOrderAgnostic() throws PMException {
			Node n1 = mock(Node.class), n2 = mock(Node.class);
			AccessRightSet s1 = new AccessRightSet("read"), s2 = new AccessRightSet("write");
			Map<Node, AccessRightSet> map = Map.of(n1, s1, n2, s2);

			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.selfAccess().computePersonalObjectSystem())
						.thenReturn(map);
			});

			NodeProto p1 = NodeProto.newBuilder().setName("A").build();
			NodeProto p2 = NodeProto.newBuilder().setName("B").build();
			NodePrivilege np1 = NodePrivilege.newBuilder()
					.setNode(p1)
					.addAllArset(List.of("read"))
					.build();
			NodePrivilege np2 = NodePrivilege.newBuilder()
					.setNode(p2)
					.addAllArset(List.of("write"))
					.build();

			try (MockedStatic<ProtoUtil> pu = mockStatic(ProtoUtil.class)) {
				pu.when(() -> ProtoUtil.toNodeProto(n1)).thenReturn(p1);
				pu.when(() -> ProtoUtil.toNodeProto(n2)).thenReturn(p2);

				service.selfComputePersonalObjectSystem(request, observer);

				verify(selfAccessQueryAdjudicator)
						.computePersonalObjectSystem();

				ArgumentCaptor<NodePrivilegeList> cap =
						ArgumentCaptor.forClass(NodePrivilegeList.class);
				verify(observer).onNext(cap.capture());
				verify(observer).onCompleted();
				verifyNoMoreInteractions(observer);

				Set<NodePrivilege> got = new HashSet<>(cap.getValue().getPrivilegesList());
				assertEquals(Set.of(np1, np2), got);
			}
		}

		@Test
		void shouldReturnEmptyWhenNone() throws PMException {
			stubAdjudicatorTx(adjudicator -> {
				when(adjudicator.selfAccess().computePersonalObjectSystem())
						.thenReturn(Collections.emptyMap());
			});

			service.selfComputePersonalObjectSystem(request, observer);

			verify(selfAccessQueryAdjudicator)
					.computePersonalObjectSystem();
			verify(observer).onNext(NodePrivilegeList.newBuilder().build());
			verify(observer).onCompleted();
			verifyNoMoreInteractions(observer);
		}

		@Test
		void shouldPropagateException() {
			when(adjudicator.adjudicateQuery(any()))
					.thenThrow(new RuntimeException("test exception"));

			assertThrows(RuntimeException.class,
			             () -> service.selfComputePersonalObjectSystem(request, observer));
			verifyNoMoreInteractions(observer);
		}
	}
}
