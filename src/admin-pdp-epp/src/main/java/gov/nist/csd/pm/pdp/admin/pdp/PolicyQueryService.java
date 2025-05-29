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
import gov.nist.csd.pm.core.pap.function.op.Operation;
import gov.nist.csd.pm.core.pap.function.routine.Routine;
import gov.nist.csd.pm.core.pap.obligation.Obligation;
import gov.nist.csd.pm.core.pap.pml.function.operation.PMLStmtsOperation;
import gov.nist.csd.pm.core.pap.pml.function.routine.PMLStmtsRoutine;
import gov.nist.csd.pm.core.pap.query.model.explain.Explain;
import gov.nist.csd.pm.core.pap.query.model.subgraph.Subgraph;
import gov.nist.csd.pm.core.pap.query.model.subgraph.SubgraphPrivileges;
import gov.nist.csd.pm.pdp.proto.model.ExplainResponse;
import gov.nist.csd.pm.pdp.proto.model.NodeProto;
import gov.nist.csd.pm.pdp.proto.model.ProhibitionProto;
import gov.nist.csd.pm.pdp.proto.model.StringList;
import gov.nist.csd.pm.pdp.proto.query.*;
import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.*;

@GrpcService
public class PolicyQueryService extends PolicyQueryServiceGrpc.PolicyQueryServiceImplBase {

	private final Neo4jEmbeddedPAP pap;
	private Adjudicator adjudicator;

	public PolicyQueryService(Adjudicator adjudicator, Neo4jEmbeddedPAP pap) {
		this.adjudicator = adjudicator;
		this.pap = pap;
	}

	@Override
	public void nodeExists(IdOrNameQuery request, StreamObserver<BooleanResponse> responseObserver) {
		boolean exists = adjudicator.adjudicateQuery(pdpTx -> pdpTx.query().graph().nodeExists(request.getId()));

		responseObserver.onNext(BooleanResponse.newBuilder().setResult(exists).build());
		responseObserver.onCompleted();
	}

	@Override
	public void getNode(IdOrNameQuery request, StreamObserver<NodeProto> responseObserver) {
		Node node =  adjudicator.adjudicateQuery(pdpTx -> switch (request.getIdOrNameCase()) {
			case ID -> pdpTx.query().graph().getNodeById(request.getId());
			case NAME -> pdpTx.query().graph().getNodeByName(request.getName());
			case IDORNAME_NOT_SET -> throw new PMException("IdOrName not set");
		});

		responseObserver.onNext(ProtoUtil.toNodeProto(node));
		responseObserver.onCompleted();
	}

	@Override
	public void getNodeId(IdOrNameQuery request, StreamObserver<NodeIdResponse> responseObserver) {
		long id = adjudicator.adjudicateQuery(pdpTx -> pdpTx.query().graph().getNodeId(request.getName()));

		responseObserver.onNext(NodeIdResponse.newBuilder().setId(id).build());
		responseObserver.onCompleted();

	}

	@Override
	public void searchNodes(SearchQuery request, StreamObserver<SearchResponse> responseObserver) {
		Collection<Node> nodes = adjudicator.adjudicateQuery(pdpTx -> pdpTx.query().graph().search(
				NodeType.toNodeType(request.getType().name()),
				request.getPropertiesMap()
		));

		List<NodeProto> nodeProtos = new ArrayList<>();
		for (Node node : nodes) {
			nodeProtos.add(ProtoUtil.toNodeProto(node));
		}

		responseObserver.onNext(SearchResponse.newBuilder().addAllNodes(nodeProtos).build());
		responseObserver.onCompleted();
	}

	@Override
	public void getPolicyClasses(Empty request, StreamObserver<PolicyClassesResponse> responseObserver) {
		Collection<Long> pcs = adjudicator.adjudicateQuery(pdpTx -> pdpTx.query().graph().getPolicyClasses());
		List<NodeProto> nodeProtos = nodeIdsToNodeProtoList(pcs);

		responseObserver.onNext(PolicyClassesResponse.newBuilder().addAllNodes(nodeProtos).build());
		responseObserver.onCompleted();

	}

	@Override
	public void getAdjacentDescendants(GetAdjacentAssignmentsQuery request,
	                                   StreamObserver<NodeListResponse> responseObserver) {
		Collection<Long> descs = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().graph().getAdjacentDescendants(request.getNodeId());
		});
		List<NodeProto> nodeProtos = nodeIdsToNodeProtoList(descs);

		responseObserver.onNext(NodeListResponse.newBuilder().addAllNodes(nodeProtos).build());
		responseObserver.onCompleted();

	}

	@Override
	public void getAdjacentAscendants(GetAdjacentAssignmentsQuery request,
	                                  StreamObserver<NodeListResponse> responseObserver) {
		Collection<Long> ascs = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().graph().getAdjacentAscendants(request.getNodeId());
		});
		List<NodeProto> nodeProtos = nodeIdsToNodeProtoList(ascs);

		responseObserver.onNext(NodeListResponse.newBuilder().addAllNodes(nodeProtos).build());
		responseObserver.onCompleted();
	}

	@Override
	public void getAssociationsWithSource(GetAssociationsQuery request,
	                                      StreamObserver<AssociationListResponse> responseObserver) {
		Collection<Association> associations = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().graph().getAssociationsWithSource(request.getNodeId());
		});

		List<AssociationProto> associationProtoList = toAssociationProtoList(associations);

		responseObserver.onNext(AssociationListResponse.newBuilder().addAllAssociations(associationProtoList).build());
		responseObserver.onCompleted();
	}

	@Override
	public void getAssociationsWithTarget(GetAssociationsQuery request,
	                                      StreamObserver<AssociationListResponse> responseObserver) {
		Collection<Association> associations = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().graph().getAssociationsWithTarget(request.getNodeId());
		});

		List<AssociationProto> associationProtoList = toAssociationProtoList(associations);

		responseObserver.onNext(AssociationListResponse.newBuilder().addAllAssociations(associationProtoList).build());
		responseObserver.onCompleted();
	}

	@Override
	public void getAscendantSubgraph(GetSubgraphQuery request, StreamObserver<SubgraphProto> responseObserver) {
		Subgraph subgraph = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().graph().getAscendantSubgraph(request.getNodeId());
		});

		responseObserver.onNext(toSubgraphProto(subgraph));
		responseObserver.onCompleted();
	}

	@Override
	public void getDescendantSubgraph(GetSubgraphQuery request, StreamObserver<SubgraphProto> responseObserver) {
		Subgraph subgraph = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().graph().getDescendantSubgraph(request.getNodeId());
		});

		responseObserver.onNext(toSubgraphProto(subgraph));
		responseObserver.onCompleted();
	}

	@Override
	public void getAttributeDescendants(GetDescendantsQuery request,
	                                    StreamObserver<NodeListResponse> responseObserver) {
		Collection<Long> descs = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().graph().getAttributeDescendants(request.getNodeId());
		});

		responseObserver.onNext(NodeListResponse.newBuilder().addAllNodes(nodeIdsToNodeProtoList(descs)).build());
		responseObserver.onCompleted();

	}

	@Override
	public void getPolicyClassDescendants(GetDescendantsQuery request,
	                                      StreamObserver<NodeListResponse> responseObserver) {
		Collection<Long> descs = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().graph().getPolicyClassDescendants(request.getNodeId());
		});

		responseObserver.onNext(NodeListResponse.newBuilder().addAllNodes(nodeIdsToNodeProtoList(descs)).build());
		responseObserver.onCompleted();

	}

	@Override
	public void isAscendant(IsAncestorQuery request, StreamObserver<BooleanResponse> responseObserver) {
		boolean isAscendant = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().graph().isAscendant(request.getAscendantId(), request.getDescendantId());
		});

		responseObserver.onNext(BooleanResponse.newBuilder().setResult(isAscendant).build());
		responseObserver.onCompleted();

	}

	@Override
	public void isDescendant(IsAncestorQuery request, StreamObserver<BooleanResponse> responseObserver) {
		boolean isDescendant = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().graph().isDescendant(request.getAscendantId(), request.getDescendantId());
		});

		responseObserver.onNext(BooleanResponse.newBuilder().setResult(isDescendant).build());
		responseObserver.onCompleted();
	}

	@Override
	public void getProhibitions(Empty request, StreamObserver<ProhibitionListResponse> responseObserver) {
		Collection<Prohibition> prohibitions = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().prohibitions().getProhibitions();
		});

		List<ProhibitionProto> prohibitionProtos = new ArrayList<>();
		for (Prohibition prohibition : prohibitions) {
			prohibitionProtos.add(ProtoUtil.toProhibitionProto(prohibition, pap.query()));
		}

		responseObserver.onNext(ProhibitionListResponse.newBuilder().addAllProhibitions(prohibitionProtos).build());
		responseObserver.onCompleted();

	}

	@Override
	public void getProhibitionsBySubject(GetProhibitionBySubjectQuery request,
	                                     StreamObserver<ProhibitionListResponse> responseObserver) {
		Collection<Prohibition> prohibitions = adjudicator.adjudicateQuery(pdpTx -> {
			ProhibitionSubject subject = switch (request.getSubjectCase()) {
				case NODE_ID -> new ProhibitionSubject(request.getNodeId());
				case PROCESS -> new ProhibitionSubject(request.getProcess());
				case SUBJECT_NOT_SET -> throw new PMException("subject not set");
			};

			return pdpTx.query().prohibitions().getProhibitionsWithSubject(subject);
		});

		List<ProhibitionProto> prohibitionProtos = new ArrayList<>();
		for (Prohibition prohibition : prohibitions) {
			prohibitionProtos.add(ProtoUtil.toProhibitionProto(prohibition, pap.query()));
		}

		responseObserver.onNext(ProhibitionListResponse.newBuilder().addAllProhibitions(prohibitionProtos).build());
		responseObserver.onCompleted();

	}

	@Override
	public void getProhibition(GetByNameQuery request, StreamObserver<ProhibitionProto> responseObserver) {
		Prohibition prohibition = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().prohibitions().getProhibition(request.getName());
		});

		responseObserver.onNext(ProtoUtil.toProhibitionProto(prohibition, pap.query()));
		responseObserver.onCompleted();

	}

	@Override
	public void getInheritedProhibitions(GetInheritedProhibitionsQuery request,
	                                     StreamObserver<ProhibitionListResponse> responseObserver) {
		Collection<Prohibition> prohibitions = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().prohibitions().getInheritedProhibitionsFor(request.getSubjectId());
		});


		List<ProhibitionProto> prohibitionProtos = new ArrayList<>();
		for (Prohibition prohibition : prohibitions) {
			prohibitionProtos.add(ProtoUtil.toProhibitionProto(prohibition, pap.query()));
		}

		responseObserver.onNext(ProhibitionListResponse.newBuilder().addAllProhibitions(prohibitionProtos).build());
		responseObserver.onCompleted();

	}

	@Override
	public void getProhibitionsWithContainer(GetProhibitionsWithContainerQuery request,
	                                         StreamObserver<ProhibitionListResponse> responseObserver) {
		Collection<Prohibition> prohibitions = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().prohibitions().getProhibitionsWithContainer(request.getContainerId());
		});

		List<ProhibitionProto> prohibitionProtos = new ArrayList<>();
		for (Prohibition prohibition : prohibitions) {
			prohibitionProtos.add(ProtoUtil.toProhibitionProto(prohibition, pap.query()));
		}

		responseObserver.onNext(ProhibitionListResponse.newBuilder().addAllProhibitions(prohibitionProtos).build());
		responseObserver.onCompleted();

	}

	@Override
	public void getObligations(Empty request, StreamObserver<ObligationListResponse> responseObserver) {
		Collection<Obligation> obligations = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().obligations().getObligations();
		});

		responseObserver.onNext(ObligationListResponse.newBuilder().addAllObligations(toObligationProtoList(obligations)).build());
		responseObserver.onCompleted();
	}

	@Override
	public void getObligation(GetByNameQuery request, StreamObserver<ObligationProto> responseObserver) {
		Obligation obligation = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().obligations().getObligation(request.getName());
		});

		try {
			responseObserver.onNext(ProtoUtil.toObligationProto(obligation, pap));
			responseObserver.onCompleted();
		} catch (PMException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void getObligationsByAuthor(GetObligationByAuthorQuery request,
	                                   StreamObserver<ObligationListResponse> responseObserver) {
		Collection<Obligation> obligations = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().obligations().getObligationsWithAuthor(request.getAuthorId());
		});

		responseObserver.onNext(ObligationListResponse.newBuilder().addAllObligations(toObligationProtoList(obligations)).build());
		responseObserver.onCompleted();
	}

	@Override
	public void getResourceOperations(Empty request, StreamObserver<StringList> responseObserver) {
		AccessRightSet resourceOps = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().operations().getResourceOperations();
		});

		responseObserver.onNext(StringList.newBuilder().addAllValues(resourceOps).build());
		responseObserver.onCompleted();

	}

	@Override
	public void getAdminOperationNames(Empty request, StreamObserver<GetAdminOperationNamesResponse> responseObserver) {
		Collection<String> adminOps = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().operations().getAdminOperationNames();
		});

		responseObserver.onNext(GetAdminOperationNamesResponse.newBuilder().addAllNames(adminOps).build());
		responseObserver.onCompleted();

	}

	@Override
	public void getAdminOperation(GetAdminOperationQuery request, StreamObserver<OperationResponse> responseObserver) {
		Operation<?, ?> operation = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().operations().getAdminOperation(request.getOperationName());
		});

		if (!(operation instanceof PMLStmtsOperation pmlStmtsOperation)) {
			throw new PMRuntimeException("operation " + request.getOperationName() + " is not a PML operation and cannot be serialized");
		}

		responseObserver.onNext(OperationResponse.newBuilder().setPml(pmlStmtsOperation.toString()).build());
		responseObserver.onCompleted();
	}

	@Override
	public void getAdminRoutineNames(Empty request, StreamObserver<GetAdminRoutineNamesResponse> responseObserver) {
		Collection<String> adminRoutines = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().routines().getAdminRoutineNames();
		});

		responseObserver.onNext(GetAdminRoutineNamesResponse.newBuilder().addAllNames(adminRoutines).build());
		responseObserver.onCompleted();
	}

	@Override
	public void getAdminRoutine(GetAdminRoutineQuery request, StreamObserver<RoutineResponse> responseObserver) {
		Routine<?, ?> routine = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().routines().getAdminRoutine(request.getRoutineName());
		});

		if (!(routine instanceof PMLStmtsRoutine pmlStmtsOperation)) {
			throw new PMRuntimeException("routine " + request.getRoutineName() + " is not a PML operation and cannot be serialized");
		}

		responseObserver.onNext(RoutineResponse.newBuilder().setPml(pmlStmtsOperation.toString()).build());
		responseObserver.onCompleted();
	}

	@Override
	public void computePrivileges(ComputePrivilegesQuery request,
	                              StreamObserver<StringList> responseObserver) {
		AccessRightSet privs = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().access().computePrivileges(
					ProtoUtil.fromUserContextProto(request.getUserCtx()),
					ProtoUtil.fromTargetContextProto(request.getTargetCtx())
			);
		});

		responseObserver.onNext(StringList.newBuilder().addAllValues(privs).build());
		responseObserver.onCompleted();

	}

	@Override
	public void computeDeniedPrivileges(ComputeDeniedPrivilegesQuery request,
	                                    StreamObserver<StringList> responseObserver) {
		AccessRightSet denied = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().access().computeDeniedPrivileges(
					ProtoUtil.fromUserContextProto(request.getUserCtx()),
					ProtoUtil.fromTargetContextProto(request.getTargetCtx())
			);
		});

		responseObserver.onNext(StringList.newBuilder().addAllValues(denied).build());
		responseObserver.onCompleted();

	}

	@Override
	public void computeCapabilityList(ComputeCapabilityListQuery request,
	                                  StreamObserver<AccessQueryMappingResponse> responseObserver) {
		Map<Long, AccessRightSet> capList = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().access().computeCapabilityList(
					ProtoUtil.fromUserContextProto(request.getUserCtx())
			);
		});

		Map<Long, AccessQueryMappingEntry> arsetProtoMap = toArsetProtoMap(capList);

		responseObserver.onNext(AccessQueryMappingResponse.newBuilder().putAllMap(arsetProtoMap).build());
		responseObserver.onCompleted();
	}

	@Override
	public void computeACL(ComputeACLQuery request, StreamObserver<AccessQueryMappingResponse> responseObserver) {
		Map<Long, AccessRightSet> map = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().access().computeACL(
					ProtoUtil.fromTargetContextProto(request.getTargetCtx())
			);
		});

		Map<Long, AccessQueryMappingEntry> arsetProtoMap = toArsetProtoMap(map);

		responseObserver.onNext(AccessQueryMappingResponse.newBuilder().putAllMap(arsetProtoMap).build());
		responseObserver.onCompleted();
	}

	@Override
	public void computeDestinationAttributes(ComputeDestinationAttributesQuery request,
	                                         StreamObserver<AccessQueryMappingResponse> responseObserver) {
		Map<Long, AccessRightSet> map = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().access().computeDestinationAttributes(
					ProtoUtil.fromUserContextProto(request.getUserCtx())
			);
		});

		Map<Long, AccessQueryMappingEntry> arsetProtoMap = toArsetProtoMap(map);

		responseObserver.onNext(AccessQueryMappingResponse.newBuilder().putAllMap(arsetProtoMap).build());
		responseObserver.onCompleted();
	}

	@Override
	public void computeSubgraphPrivileges(AccessWithRootQuery request,
	                                      StreamObserver<SubgraphPrivilegesProto> responseObserver) {
		SubgraphPrivileges subgraphPrivileges = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().access().computeSubgraphPrivileges(
					ProtoUtil.fromUserContextProto(request.getUserCtx()),
					request.getRoot()
			);
		});

		responseObserver.onNext(toSubgraphPrivilegesProto(subgraphPrivileges));
		responseObserver.onCompleted();

	}

	@Override
	public void computeAdjacentAscendantPrivileges(AccessWithRootQuery request,
	                                               StreamObserver<NodePrivilegeList> responseObserver) {
		Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().access().computeAdjacentAscendantPrivileges(
					ProtoUtil.fromUserContextProto(request.getUserCtx()),
					request.getRoot()
			);
		});

		nodePrivilegeResponse(responseObserver, map);
	}

	@Override
	public void computeAdjacentDescendantPrivileges(AccessWithRootQuery request,
	                                                StreamObserver<NodePrivilegeList> responseObserver) {
		Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().access().computeAdjacentDescendantPrivileges(
					ProtoUtil.fromUserContextProto(request.getUserCtx()),
					request.getRoot()
			);
		});

		nodePrivilegeResponse(responseObserver, map);
	}

	@Override
	public void explain(ExplainQuery request, StreamObserver<ExplainResponse> responseObserver) {
		Explain explain = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().access().explain(
					ProtoUtil.fromUserContextProto(request.getUserCtx()),
					ProtoUtil.fromTargetContextProto(request.getTargetCtx())
			);
		});

		responseObserver.onNext(ProtoUtil.buildExplainResponse(explain, pap.query()));
		responseObserver.onCompleted();

	}

	@Override
	public void computePersonalObjectSystem(ComputePOSQuery request,
	                                        StreamObserver<NodePrivilegeList> responseObserver) {
		Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().access().computePersonalObjectSystem(
					ProtoUtil.fromUserContextProto(request.getUserCtx())
			);
		});

		nodePrivilegeResponse(responseObserver, map);
	}

	@Override
	public void selfComputePrivileges(TargetContextProto request,
	                                  StreamObserver<StringList> responseObserver) {
		AccessRightSet privs = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().selfAccess().computePrivileges(
					ProtoUtil.fromTargetContextProto(request)
			);
		});

		responseObserver.onNext(StringList.newBuilder().addAllValues(privs).build());
		responseObserver.onCompleted();

	}

	@Override
	public void selfComputeSubgraphPrivileges(SelfAccessWithRootQuery request,
	                                          StreamObserver<SubgraphPrivilegesProto> responseObserver) {
		SubgraphPrivileges subgraphPrivileges = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().selfAccess().computeSubgraphPrivileges(request.getRoot());
		});

		responseObserver.onNext(toSubgraphPrivilegesProto(subgraphPrivileges));
		responseObserver.onCompleted();

	}

	@Override
	public void selfComputeAdjacentAscendantPrivileges(SelfAccessWithRootQuery request,
	                                                   StreamObserver<NodePrivilegeList> responseObserver) {
		Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().selfAccess().computeAdjacentAscendantPrivileges(request.getRoot());
		});

		nodePrivilegeResponse(responseObserver, map);
	}

	@Override
	public void selfComputeAdjacentDescendantPrivileges(SelfAccessWithRootQuery request,
	                                                    StreamObserver<NodePrivilegeList> responseObserver) {
		Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().selfAccess().computeAdjacentDescendantPrivileges(request.getRoot());
		});

		nodePrivilegeResponse(responseObserver, map);
	}

	@Override
	public void selfComputePersonalObjectSystem(Empty request, StreamObserver<NodePrivilegeList> responseObserver) {
		Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery(pdpTx -> {
			return pdpTx.query().selfAccess().computePersonalObjectSystem();
		});

		nodePrivilegeResponse(responseObserver, map);
	}

	private SubgraphProto toSubgraphProto(Subgraph subgraph) {
		List<SubgraphProto> subgraphProtos = new ArrayList<>();
		for (Subgraph childSubgraph : subgraph.subgraphs()) {
			subgraphProtos.add(toSubgraphProto(childSubgraph));
		}

		return SubgraphProto.newBuilder()
				.setNode(ProtoUtil.toNodeProto(subgraph.node()))
				.addAllSubgraph(subgraphProtos)
				.build();
	}

	private SubgraphPrivilegesProto toSubgraphPrivilegesProto(SubgraphPrivileges subgraphPrivileges) {
		// prune any subgraphs in which all nodes are inaccessible
		pruneInaccessibleSubgraphs(subgraphPrivileges);

		List<SubgraphPrivilegesProto> subgraphProtos = new ArrayList<>();
		for (SubgraphPrivileges childSubgraph : subgraphPrivileges.ascendants()) {
			subgraphProtos.add(toSubgraphPrivilegesProto(childSubgraph));
		}

		return SubgraphPrivilegesProto.newBuilder()
				.setNode(ProtoUtil.toNodeProto(subgraphPrivileges.node()))
				.addAllArset(subgraphPrivileges.privileges())
				.addAllAscendants(subgraphProtos)
				.build();
	}

	private void pruneInaccessibleSubgraphs(SubgraphPrivileges subgraphPrivileges) {
		if (subgraphPrivileges.ascendants() == null || subgraphPrivileges.ascendants().isEmpty()) {
			return;
		}

		for (SubgraphPrivileges subPrivs : subgraphPrivileges.ascendants()) {
			pruneInaccessibleSubgraphs(subPrivs);
		}

		subgraphPrivileges.ascendants().removeIf(s -> s.privileges() == null || s.privileges().isEmpty());
	}

	private Map<Long, AccessQueryMappingEntry> toArsetProtoMap(Map<Long, AccessRightSet> map) {
		Map<Long, AccessQueryMappingEntry> mapProto = new HashMap<>();
		for (var entry : map.entrySet()) {
			AccessRightSet arset = entry.getValue();

			// ignore entries that are empty or null
			if (arset == null || arset.isEmpty()) {
				continue;
			}

			try {
				mapProto.put(entry.getKey(), AccessQueryMappingEntry.newBuilder()
						.setNode(ProtoUtil.toNodeProto(pap.query().graph().getNodeById(entry.getKey())))
						.addAllArset(arset)
						.build());
			} catch (PMException e) {
				throw new RuntimeException(e);
			}
		}

		return mapProto;
	}

	private void nodePrivilegeResponse(StreamObserver<NodePrivilegeList> responseObserver,
	                                   Map<Node, AccessRightSet> map) {
		List<NodePrivilege> nodePrivileges = new ArrayList<>();
		for (var entry : map.entrySet()) {
			AccessRightSet arset = entry.getValue();

			// ignore entries that are empty or null
			if (arset == null || arset.isEmpty()) {
				continue;
			}

			NodePrivilege nodePrivilege = NodePrivilege.newBuilder()
					.setNode(ProtoUtil.toNodeProto(entry.getKey()))
					.addAllArset(arset)
					.build();
			nodePrivileges.add(nodePrivilege);
		}

		responseObserver.onNext(NodePrivilegeList.newBuilder()
				                        .addAllPrivileges(nodePrivileges)
				                        .build());
		responseObserver.onCompleted();
	}

	private List<NodeProto> nodeIdsToNodeProtoList(Collection<Long> descs) {
		List<NodeProto> nodeProtos = new ArrayList<>();
		for (Long desc : descs) {
			try {
				nodeProtos.add(ProtoUtil.toNodeProto(pap.query().graph().getNodeById(desc)));
			} catch (PMException e) {
				throw new RuntimeException(e);
			}
		}

		return nodeProtos;
	}

	private List<AssociationProto> toAssociationProtoList(Collection<Association> associations) {
		List<AssociationProto> associationProtos = new ArrayList<>();
		for (Association association : associations) {
			try {
				associationProtos.add(
						AssociationProto.newBuilder()
								.setUa(ProtoUtil.toNodeProto(pap.query().graph().getNodeById(association.getSource())))
								.setTarget(ProtoUtil.toNodeProto(pap.query().graph().getNodeById(association.getTarget())))
								.addAllArset(association.getAccessRightSet())
								.build()
				);
			} catch (PMException e) {
				throw new RuntimeException(e);
			}
		}
		return associationProtos;
	}

	private List<ObligationProto> toObligationProtoList(Collection<Obligation> obligations) {
		List<ObligationProto> obligationProtos = new ArrayList<>();
		for (Obligation obligation : obligations) {
			try {
				obligationProtos.add(ProtoUtil.toObligationProto(obligation, pap));
			} catch (PMException e) {
				throw new RuntimeException(e);
			}
		}

		return obligationProtos;
	}

}
