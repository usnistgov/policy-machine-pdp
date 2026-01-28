package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.node.Node;
import gov.nist.csd.pm.core.common.graph.node.NodeType;
import gov.nist.csd.pm.core.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.core.common.graph.relationship.Association;
import gov.nist.csd.pm.core.common.prohibition.Prohibition;
import gov.nist.csd.pm.core.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.obligation.Obligation;
import gov.nist.csd.pm.core.pap.operation.*;
import gov.nist.csd.pm.core.pap.operation.arg.type.Type;
import gov.nist.csd.pm.core.pap.operation.param.FormalParameter;
import gov.nist.csd.pm.core.pap.operation.param.NodeFormalParameter;
import gov.nist.csd.pm.core.pap.query.model.explain.Explain;
import gov.nist.csd.pm.core.pap.query.model.subgraph.Subgraph;
import gov.nist.csd.pm.core.pap.query.model.subgraph.SubgraphPrivileges;
import gov.nist.csd.pm.core.pap.serialization.PolicySerializer;
import gov.nist.csd.pm.core.pap.serialization.json.JSONSerializer;
import gov.nist.csd.pm.core.pdp.UnauthorizedException;
import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;
import gov.nist.csd.pm.proto.v1.model.SerializationFormat;
import gov.nist.csd.pm.proto.v1.model.StringList;
import gov.nist.csd.pm.proto.v1.query.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@GrpcService
public class PolicyQueryService extends PolicyQueryServiceGrpc.PolicyQueryServiceImplBase {

	private static final Logger logger = LoggerFactory.getLogger(PolicyQueryService.class);

	private Adjudicator adjudicator;

	public PolicyQueryService(Adjudicator adjudicator) {
		this.adjudicator = adjudicator;
	}

	@Override
	public void nodeExists(NodeExistsRequest request, StreamObserver<NodeExistsResponse> responseObserver) {
		try {
			boolean exists = adjudicator.adjudicateQuery((pap, pdpTx) -> pdpTx.query().graph().nodeExists(ProtoUtil.resolveNodeRefId(pap, request.getNode())));

			responseObserver.onNext(NodeExistsResponse.newBuilder().setExists(exists).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getNode(GetNodeRequest request, StreamObserver<GetNodeResponse> responseObserver) {
		try {
			Node node =  adjudicator.adjudicateQuery((pap, pdpTx) -> pdpTx.query().graph().getNodeById(ProtoUtil.resolveNodeRefId(pap, request.getNode())));

			responseObserver.onNext(GetNodeResponse.newBuilder().setNode(ProtoUtil.toNodeProto(node)).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getNodeId(GetNodeIdRequest request, StreamObserver<GetNodeIdResponse> responseObserver) {
		try {
			long id = adjudicator.adjudicateQuery((pap, pdpTx) -> pdpTx.query().graph().getNodeId(request.getName()));

			responseObserver.onNext(GetNodeIdResponse.newBuilder().setId(id).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void searchNodes(SearchNodesRequest request, StreamObserver<SearchNodesResponse> responseObserver) {
		try {
			Collection<Node> nodes = adjudicator.adjudicateQuery((pap, pdpTx) -> pdpTx.query().graph().search(
					NodeType.toNodeType(request.getType().name()),
					request.getPropertiesMap()
			));

			List<gov.nist.csd.pm.proto.v1.model.Node> nodeProtos = new ArrayList<>();
			for (Node node : nodes) {
				nodeProtos.add(ProtoUtil.toNodeProto(node));
			}

			responseObserver.onNext(SearchNodesResponse.newBuilder().addAllNodes(nodeProtos).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getPolicyClasses(GetPolicyClassesRequest request, StreamObserver<GetPolicyClassesResponse> responseObserver) {
		try {
			List<gov.nist.csd.pm.proto.v1.model.Node> nodeProtos = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Long> policyClasses = pdpTx.query().graph().getPolicyClasses();
				return nodeIdsToNodeProtoList(pap, policyClasses);
			});

			responseObserver.onNext(GetPolicyClassesResponse.newBuilder().addAllPolicyClasses(nodeProtos).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAdjacentDescendants(GetAdjacentDescendantsRequest request,
	                                   StreamObserver<GetAdjacentDescendantsResponse> responseObserver) {
		try {
			Collection<gov.nist.csd.pm.proto.v1.model.Node> descs = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Long> adjacentDescendants = pdpTx.query().graph().getAdjacentDescendants(ProtoUtil.resolveNodeRefId(pap, request.getNode()));
				return nodeIdsToNodeProtoList(pap, adjacentDescendants);
			});

			responseObserver.onNext(GetAdjacentDescendantsResponse.newBuilder().addAllNodes(descs).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAdjacentAscendants(GetAdjacentAscendantsRequest request,
	                                  StreamObserver<GetAdjacentAscendantsResponse> responseObserver) {
		try {
			Collection<gov.nist.csd.pm.proto.v1.model.Node> ascs = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Long> adjacentAscendants = pdpTx.query().graph().getAdjacentAscendants(ProtoUtil.resolveNodeRefId(pap, request.getNode()));
				return nodeIdsToNodeProtoList(pap, adjacentAscendants);
			});

			responseObserver.onNext(GetAdjacentAscendantsResponse.newBuilder().addAllNodes(ascs).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAssociationsWithSource(GetAssociationsWithSourceRequest request,
	                                      StreamObserver<GetAssociationsWithSourceResponse> responseObserver) {
		try {
			List<gov.nist.csd.pm.proto.v1.model.Association> associations = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Association> associationsWithSource = pdpTx.query().graph().getAssociationsWithSource(ProtoUtil.resolveNodeRefId(pap, request.getNode()));
				return toAssociationProtoList(pap, associationsWithSource);
			});

			responseObserver.onNext(GetAssociationsWithSourceResponse.newBuilder().addAllAssociations(associations).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAssociationsWithTarget(GetAssociationsWithTargetRequest request,
	                                      StreamObserver<GetAssociationsWithTargetResponse> responseObserver) {
		try {
			List<gov.nist.csd.pm.proto.v1.model.Association> associations = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Association> associationsWithTarget = pdpTx.query().graph().getAssociationsWithTarget(ProtoUtil.resolveNodeRefId(pap, request.getNode()));
				return toAssociationProtoList(pap, associationsWithTarget);
			});

			responseObserver.onNext(GetAssociationsWithTargetResponse.newBuilder().addAllAssociations(associations).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAscendantSubgraph(GetAscendantSubgraphRequest request, StreamObserver<GetAscendantSubgraphResponse> responseObserver) {
		try {
			Subgraph subgraph = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().graph().getAscendantSubgraph(ProtoUtil.resolveNodeRefId(pap, request.getNode()));
			});

			responseObserver.onNext(GetAscendantSubgraphResponse.newBuilder().setSubgraph(toSubgraph(subgraph)).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getDescendantSubgraph(GetDescendantSubgraphRequest request, StreamObserver<GetDescendantSubgraphResponse> responseObserver) {
		try {
			Subgraph subgraph = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().graph().getDescendantSubgraph(ProtoUtil.resolveNodeRefId(pap, request.getNode()));
			});

			responseObserver.onNext(GetDescendantSubgraphResponse.newBuilder().setSubgraph(toSubgraph(subgraph)).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAttributeDescendants(GetAttributeDescendantsRequest request,
	                                    StreamObserver<GetAttributeDescendantsResponse> responseObserver) {
		try {
			List<gov.nist.csd.pm.proto.v1.model.Node> nodes = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Long> descs = pdpTx.query().graph().getAttributeDescendants(ProtoUtil.resolveNodeRefId(pap, request.getNode()));
				return nodeIdsToNodeProtoList(pap, descs);
			});

			responseObserver.onNext(GetAttributeDescendantsResponse.newBuilder().addAllNodes(nodes).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getPolicyClassDescendants(GetPolicyClassDescendantsRequest request,
	                                      StreamObserver<GetPolicyClassDescendantsResponse> responseObserver) {
		try {
			Collection<gov.nist.csd.pm.proto.v1.model.Node> descs = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Long> policyClassDescendants = pdpTx.query().graph().getPolicyClassDescendants(ProtoUtil.resolveNodeRefId(pap, request.getNode()));
				return nodeIdsToNodeProtoList(pap, policyClassDescendants);
			});

			responseObserver.onNext(GetPolicyClassDescendantsResponse.newBuilder().addAllNodes(descs).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void isAscendant(IsAscendantRequest request, StreamObserver<IsAscendantResponse> responseObserver) {
		try {
			boolean isAscendant = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().graph().isAscendant(
						ProtoUtil.resolveNodeRefId(pap, request.getAscendant()),
						ProtoUtil.resolveNodeRefId(pap, request.getDescendant())
				);
			});

			responseObserver.onNext(IsAscendantResponse.newBuilder().setResult(isAscendant).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void isDescendant(IsDescendantRequest request, StreamObserver<IsDescendantResponse> responseObserver) {
		try {
			boolean isDescendant = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().graph().isDescendant(
						ProtoUtil.resolveNodeRefId(pap, request.getAscendant()),
						ProtoUtil.resolveNodeRefId(pap, request.getDescendant())
				);
			});

			responseObserver.onNext(IsDescendantResponse.newBuilder().setResult(isDescendant).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getProhibitions(GetProhibitionsRequest request, StreamObserver<GetProhibitionsResponse> responseObserver) {
		try {
			Collection<gov.nist.csd.pm.proto.v1.model.Prohibition> prohibitions = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				List<gov.nist.csd.pm.proto.v1.model.Prohibition> prohibitionProtos = new ArrayList<>();
				for (Prohibition prohibition : pdpTx.query().prohibitions().getProhibitions()) {
					prohibitionProtos.add(ProtoUtil.toProhibitionProto(prohibition, pap.query()));
				}

				return prohibitionProtos;
			});

			responseObserver.onNext(GetProhibitionsResponse.newBuilder().addAllProhibitions(prohibitions).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getProhibitionsBySubject(GetProhibitionsBySubjectRequest request,
	                                     StreamObserver<GetProhibitionsBySubjectResponse> responseObserver) {
		try {
			Collection<gov.nist.csd.pm.proto.v1.model.Prohibition> prohibitions = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				ProhibitionSubject subject = switch (request.getSubjectCase()) {
					case NODE -> new ProhibitionSubject(ProtoUtil.resolveNodeRefId(pap, request.getNode()));
					case PROCESS -> new ProhibitionSubject(request.getProcess());
					case SUBJECT_NOT_SET -> throw new PMException("subject not set");
				};

				Collection<Prohibition> prohibitionsWithSubject = pdpTx.query().prohibitions().getProhibitionsWithSubject(subject);
				List<gov.nist.csd.pm.proto.v1.model.Prohibition> prohibitionProtos = new ArrayList<>();
				for (Prohibition prohibition : prohibitionsWithSubject) {
					prohibitionProtos.add(ProtoUtil.toProhibitionProto(prohibition, pap.query()));
				}

				return prohibitionProtos;
			});

			responseObserver.onNext(GetProhibitionsBySubjectResponse.newBuilder().addAllProhibitions(prohibitions).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getProhibition(GetProhibitionRequest request, StreamObserver<GetProhibitionResponse> responseObserver) {
		try {
			gov.nist.csd.pm.proto.v1.model.Prohibition prohibition = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Prohibition p = pdpTx.query().prohibitions().getProhibition(request.getName());
				return ProtoUtil.toProhibitionProto(p, pap.query());
			});

			responseObserver.onNext(GetProhibitionResponse.newBuilder().setProhibition(prohibition).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getInheritedProhibitions(GetInheritedProhibitionsRequest request,
	                                     StreamObserver<GetInheritedProhibitionsResponse> responseObserver) {
		try {
			Collection<gov.nist.csd.pm.proto.v1.model.Prohibition> prohibitionProtos = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Prohibition> inheritedProhibitionsFor = pdpTx.query().prohibitions().getInheritedProhibitionsFor(
						ProtoUtil.resolveNodeRefId(pap, request.getSubject())
				);

				List<gov.nist.csd.pm.proto.v1.model.Prohibition> protos = new ArrayList<>();
				for (Prohibition prohibition : inheritedProhibitionsFor) {
					protos.add(ProtoUtil.toProhibitionProto(prohibition, pap.query()));
				}

				return protos;
			});

			responseObserver.onNext(GetInheritedProhibitionsResponse.newBuilder().addAllProhibitions(prohibitionProtos).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getProhibitionsWithContainer(GetProhibitionsWithContainerRequest request,
	                                         StreamObserver<GetProhibitionsWithContainerResponse> responseObserver) {
		try {
			Collection<gov.nist.csd.pm.proto.v1.model.Prohibition> prohibitionProtos = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Prohibition> prohibitionsWithContainer = pdpTx.query().prohibitions().getProhibitionsWithContainer(
						ProtoUtil.resolveNodeRefId(pap, request.getContainer())
				);

				List<gov.nist.csd.pm.proto.v1.model.Prohibition> protos = new ArrayList<>();
				for (Prohibition prohibition : prohibitionsWithContainer) {
					protos.add(ProtoUtil.toProhibitionProto(prohibition, pap.query()));
				}

				return protos;
			});

			responseObserver.onNext(GetProhibitionsWithContainerResponse.newBuilder().addAllProhibitions(prohibitionProtos).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getObligations(GetObligationsRequest request, StreamObserver<GetObligationsResponse> responseObserver) {
		long s = System.nanoTime();
		try {
			List<gov.nist.csd.pm.proto.v1.model.Obligation> obligationProtos = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Obligation> obligations = pdpTx.query().obligations().getObligations();
				return toObligationProtoList(pap, obligations);
			});

			responseObserver.onNext(GetObligationsResponse.newBuilder().addAllObligations(obligationProtos).build());
			responseObserver.onCompleted();
			System.out.println(System.nanoTime() - s);
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getObligation(GetObligationRequest request, StreamObserver<GetObligationResponse> responseObserver) {
		try {
			gov.nist.csd.pm.proto.v1.model.Obligation obligation = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Obligation o = pdpTx.query().obligations().getObligation(request.getName());
				return ProtoUtil.toObligationProto(o, pap);
			});

			responseObserver.onNext(GetObligationResponse.newBuilder().setObligation(obligation).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getObligationsByAuthor(GetObligationsByAuthorRequest request,
	                                   StreamObserver<GetObligationsByAuthorResponse> responseObserver) {
		try {
			Collection<gov.nist.csd.pm.proto.v1.model.Obligation> obligations = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Obligation> obligationsWithAuthor = pdpTx.query().obligations().getObligationsWithAuthor(
						ProtoUtil.resolveNodeRefId(pap, request.getAuthor())
				);
				return toObligationProtoList(pap, obligationsWithAuthor);
			});

			responseObserver.onNext(GetObligationsByAuthorResponse.newBuilder().addAllObligations(obligations).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getResourceAccessRights(GetResourceAccessRightsRequest request, StreamObserver<GetResourceAccessRightsResponse> responseObserver) {
		try {
			AccessRightSet resourceOps = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().operations().getResourceAccessRights();
			});

			responseObserver.onNext(GetResourceAccessRightsResponse.newBuilder().addAllAccessRights(resourceOps).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getResourceOperationSignatures(GetResourceOperationSignaturesRequest request,
	                                           StreamObserver<GetResourceOperationSignaturesResponse> responseObserver) {
		try {
			responseObserver.onNext(GetResourceOperationSignaturesResponse.newBuilder().addAllSignatures(getSignatures(
					ResourceOperation.class)).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getResourceOperationSignature(GetResourceOperationSignatureRequest request,
	                                          StreamObserver<GetResourceOperationSignatureResponse> responseObserver) {
		try {
			Operation<?> op = adjudicator.adjudicateQuery((pap, pdpTx) -> pdpTx.query().operations().getOperation(request.getName()));
			responseObserver.onNext(GetResourceOperationSignatureResponse.newBuilder().setSignature(buildSignature(op)).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAdminOperationSignatures(GetAdminOperationSignaturesRequest request,
	                                        StreamObserver<GetAdminOperationSignaturesResponse> responseObserver) {
		try {
			responseObserver.onNext(GetAdminOperationSignaturesResponse.newBuilder().addAllSignatures(getSignatures(AdminOperation.class)).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAdminOperationSignature(GetAdminOperationSignatureRequest request,
	                                       StreamObserver<GetAdminOperationSignatureResponse> responseObserver) {
		try {
			Operation<?> op = adjudicator.adjudicateQuery((pap, pdpTx) -> pdpTx.query().operations().getOperation(request.getName()));
			responseObserver.onNext(GetAdminOperationSignatureResponse.newBuilder().setSignature(buildSignature(op)).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getRoutineSignatures(GetRoutineSignaturesRequest request,
	                                 StreamObserver<GetRoutineSignaturesResponse> responseObserver) {
		try {
			responseObserver.onNext(GetRoutineSignaturesResponse.newBuilder().addAllSignatures(getSignatures(Routine.class)).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getRoutineSignature(GetRoutineSignatureRequest request, StreamObserver<GetRoutineSignatureResponse> responseObserver) {
		try {
			Operation<?> op = adjudicator.adjudicateQuery((pap, pdpTx) -> pdpTx.query().operations().getOperation(request.getName()));
			responseObserver.onNext(GetRoutineSignatureResponse.newBuilder().setSignature(buildSignature(op)).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getQuerySignatures(GetQuerySignaturesRequest request,
	                               StreamObserver<GetQuerySignaturesResponse> responseObserver) {
		try {
			responseObserver.onNext(GetQuerySignaturesResponse.newBuilder().addAllSignatures(getSignatures(
					QueryOperation.class)).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getQuerySignature(GetQuerySignatureRequest request,
	                              StreamObserver<GetQuerySignatureResponse> responseObserver) {
		try {
			Operation<?> op = adjudicator.adjudicateQuery((pap, pdpTx) -> pdpTx.query().operations().getOperation(request.getName()));
			responseObserver.onNext(GetQuerySignatureResponse.newBuilder().setSignature(buildSignature(op)).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getFunctionSignatures(GetFunctionSignaturesRequest request,
	                                  StreamObserver<GetFunctionSignaturesResponse> responseObserver) {
		try {
			responseObserver.onNext(GetFunctionSignaturesResponse.newBuilder().addAllSignatures(getSignatures(
					Function.class)).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getFunctionSignature(GetFunctionSignatureRequest request,
	                                 StreamObserver<GetFunctionSignatureResponse> responseObserver) {
		try {
			Operation<?> op = adjudicator.adjudicateQuery((pap, pdpTx) -> pdpTx.query().operations().getOperation(request.getName()));
			responseObserver.onNext(GetFunctionSignatureResponse.newBuilder().setSignature(buildSignature(op)).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computePrivileges(ComputePrivilegesRequest request,
	                              StreamObserver<ComputePrivilegesResponse> responseObserver) {
		try {
			AccessRightSet privs = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().access().computePrivileges(
						ProtoUtil.fromUserContextProto(pap, request.getUserCtx()),
						ProtoUtil.fromTargetContextProto(pap, request.getTargetCtx())
				);
			});

			responseObserver.onNext(ComputePrivilegesResponse.newBuilder().addAllPrivileges(privs).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computeDeniedPrivileges(ComputeDeniedPrivilegesRequest request,
	                                    StreamObserver<ComputeDeniedPrivilegesResponse> responseObserver) {
		try {
			AccessRightSet denied = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().access().computeDeniedPrivileges(
						ProtoUtil.fromUserContextProto(pap, request.getUserCtx()),
						ProtoUtil.fromTargetContextProto(pap, request.getTargetCtx())
				);
			});

			responseObserver.onNext(ComputeDeniedPrivilegesResponse.newBuilder().addAllPrivileges(denied).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computeCapabilityList(ComputeCapabilityListRequest request,
	                                  StreamObserver<ComputeCapabilityListResponse> responseObserver) {
		try {
			List<NodePrivileges> nodePrivilegesList = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Map<Long, AccessRightSet> map = pdpTx.query().access().computeCapabilityList(
						ProtoUtil.fromUserContextProto(pap, request.getUserCtx())
				);

				return toNodePrivilegesList(pap, map);
			});

			responseObserver.onNext(ComputeCapabilityListResponse.newBuilder().addAllNodePrivileges(nodePrivilegesList).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computeACL(ComputeACLRequest request, StreamObserver<ComputeACLResponse> responseObserver) {
		try {
			List<NodePrivileges> nodePrivileges = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Map<Long, AccessRightSet> acl = pdpTx.query().access().computeACL(
						ProtoUtil.fromTargetContextProto(pap, request.getTargetCtx())
				);

				return toNodePrivilegesList(pap, acl);
			});

			responseObserver.onNext(ComputeACLResponse.newBuilder().addAllNodePrivileges(nodePrivileges).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computeDestinationAttributes(ComputeDestinationAttributesRequest request,
	                                         StreamObserver<ComputeDestinationAttributesResponse> responseObserver) {
		try {
			List<NodePrivileges> nodePrivilegesList = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Map<Long, AccessRightSet> destAttrs = pdpTx.query().access().computeDestinationAttributes(
						ProtoUtil.fromUserContextProto(pap, request.getUserCtx())
				);

				return toNodePrivilegesList(pap, destAttrs);
			});

			responseObserver.onNext(ComputeDestinationAttributesResponse.newBuilder().addAllNodePrivileges(nodePrivilegesList).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computeSubgraphPrivileges(ComputeSubgraphPrivilegesRequest request,
	                                      StreamObserver<ComputeSubgraphPrivilegesResponse> responseObserver) {
		try {
			SubgraphPrivileges subgraphPrivileges = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().access().computeSubgraphPrivileges(
						ProtoUtil.fromUserContextProto(pap, request.getUserCtx()),
						ProtoUtil.resolveNodeRefId(pap, request.getRoot())
				);
			});

			responseObserver.onNext(ComputeSubgraphPrivilegesResponse.newBuilder().setSubgraphPrivileges(toSubgraphPrivilegesProto(subgraphPrivileges)).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computeAdjacentAscendantPrivileges(ComputeAdjacentAscendantPrivilegesRequest request,
	                                               StreamObserver<ComputeAdjacentAscendantPrivilegesResponse> responseObserver) {
		try {
			Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().access().computeAdjacentAscendantPrivileges(
						ProtoUtil.fromUserContextProto(pap, request.getUserCtx()),
						ProtoUtil.resolveNodeRefId(pap, request.getRoot())
				);
			});

			nodePrivilegesResponse(map);

		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computeAdjacentDescendantPrivileges(ComputeAdjacentDescendantPrivilegesRequest request,
	                                                StreamObserver<ComputeAdjacentDescendantPrivilegesResponse> responseObserver) {
		try {
			Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().access().computeAdjacentDescendantPrivileges(
						ProtoUtil.fromUserContextProto(pap, request.getUserCtx()),
						ProtoUtil.resolveNodeRefId(pap, request.getRoot())
				);
			});

			List<NodePrivileges> nodePrivileges = nodePrivilegesResponse(map);
			responseObserver.onNext(ComputeAdjacentDescendantPrivilegesResponse.newBuilder().addAllNodePrivileges(nodePrivileges).build());
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void explain(ExplainRequest request, StreamObserver<ExplainResponse> responseObserver) {
		try {
			ExplainResponse explainProto = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Explain explain = pdpTx.query().access().explain(
						ProtoUtil.fromUserContextProto(pap, request.getUserCtx()),
						ProtoUtil.fromTargetContextProto(pap, request.getTargetCtx())
				);

				return ProtoUtil.buildExplainProto(explain, pap.query());
			});

			responseObserver.onNext(explainProto);
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computePersonalObjectSystem(ComputePersonalObjectSystemRequest request,
	                                        StreamObserver<ComputePersonalObjectSystemResponse> responseObserver) {
		try {
			Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().access().computePersonalObjectSystem(
						ProtoUtil.fromUserContextProto(pap, request.getUserCtx())
				);
			});

			List<NodePrivileges> nodePrivileges = nodePrivilegesResponse(map);
			responseObserver.onNext(ComputePersonalObjectSystemResponse.newBuilder().addAllNodePrivileges(nodePrivileges).build());
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void selfComputePrivileges(SelfComputePrivilegesRequest request,
	                                  StreamObserver<SelfComputePrivilegesResponse> responseObserver) {
		try {
			AccessRightSet privs = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().selfAccess().computePrivileges(
						ProtoUtil.fromTargetContextProto(pap, request.getTargetCtx())
				);
			});

			responseObserver.onNext(SelfComputePrivilegesResponse.newBuilder().addAllPrivileges(privs).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void selfComputeSubgraphPrivileges(SelfComputeSubgraphPrivilegesRequest request,
	                                          StreamObserver<SelfComputeSubgraphPrivilegesResponse> responseObserver) {
		try {
			SubgraphPrivileges subgraphPrivileges = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().selfAccess().computeSubgraphPrivileges(ProtoUtil.resolveNodeRefId(pap, request.getRoot()));
			});

			responseObserver.onNext(SelfComputeSubgraphPrivilegesResponse.newBuilder().setSubgraphPrivileges(toSubgraphPrivilegesProto(subgraphPrivileges)).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void selfComputeAdjacentAscendantPrivileges(SelfComputeAdjacentAscendantPrivilegesRequest request,
	                                                   StreamObserver<SelfComputeAdjacentAscendantPrivilegesResponse> responseObserver) {
		try {
			Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().selfAccess().computeAdjacentAscendantPrivileges(ProtoUtil.resolveNodeRefId(pap, request.getRoot()));
			});

			List<NodePrivileges> nodePrivileges = nodePrivilegesResponse(map);
			responseObserver.onNext(SelfComputeAdjacentAscendantPrivilegesResponse.newBuilder().addAllNodePrivileges(nodePrivileges).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void selfComputeAdjacentDescendantPrivileges(SelfComputeAdjacentDescendantPrivilegesRequest request,
	                                                    StreamObserver<SelfComputeAdjacentDescendantPrivilegesResponse> responseObserver) {
		try {
			Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().selfAccess().computeAdjacentDescendantPrivileges(ProtoUtil.resolveNodeRefId(pap, request.getRoot()));
			});

			List<NodePrivileges> nodePrivileges = nodePrivilegesResponse(map);
			responseObserver.onNext(SelfComputeAdjacentDescendantPrivilegesResponse.newBuilder().addAllNodePrivileges(nodePrivileges).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void selfComputePersonalObjectSystem(SelfComputePersonalObjectSystemRequest request, StreamObserver<SelfComputePersonalObjectSystemResponse> responseObserver) {
		try {
			Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().selfAccess().computePersonalObjectSystem();
			});

			List<NodePrivileges> nodePrivileges = nodePrivilegesResponse(map);
			responseObserver.onNext(SelfComputePersonalObjectSystemResponse.newBuilder().addAllNodePrivileges(nodePrivileges).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void serialize(SerializeRequest request, StreamObserver<SerializeResponse> responseObserver) {
		PolicySerializer serializer;
		SerializationFormat format = request.getFormat();
		switch (format) {
			case JSON -> serializer = new JSONSerializer();
			default -> throw new IllegalArgumentException("Unrecognized format: " + format);
		}

		try {
			String serialized = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.serialize(serializer);
			});

			responseObserver.onNext(SerializeResponse.newBuilder().setSerialized(serialized).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	private gov.nist.csd.pm.proto.v1.query.Subgraph toSubgraph(Subgraph subgraph) {
		List<gov.nist.csd.pm.proto.v1.query.Subgraph> subgraphs = new ArrayList<>();
		for (Subgraph childSubgraph : subgraph.subgraphs()) {
			subgraphs.add(toSubgraph(childSubgraph));
		}

		return gov.nist.csd.pm.proto.v1.query.Subgraph.newBuilder()
				.setNode(ProtoUtil.toNodeProto(subgraph.node()))
				.addAllSubgraphs(subgraphs)
				.build();
	}

	private gov.nist.csd.pm.proto.v1.query.SubgraphPrivileges toSubgraphPrivilegesProto(SubgraphPrivileges subgraphPrivileges) {
		// prune any subgraphs in which all nodes are inaccessible
		pruneInaccessibleSubgraphs(subgraphPrivileges);

		List<gov.nist.csd.pm.proto.v1.query.SubgraphPrivileges> subgraphs = new ArrayList<>();
		for (SubgraphPrivileges childSubgraph : subgraphPrivileges.ascendants()) {
			subgraphs.add(toSubgraphPrivilegesProto(childSubgraph));
		}

		return gov.nist.csd.pm.proto.v1.query.SubgraphPrivileges.newBuilder()
				.setNode(ProtoUtil.toNodeProto(subgraphPrivileges.node()))
				.addAllArset(subgraphPrivileges.privileges())
				.addAllAscendants(subgraphs)
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

	private List<NodePrivileges> toNodePrivilegesList(PAP pap, Map<Long, AccessRightSet> map) {
		List<NodePrivileges> entriesProto = new ArrayList<>();
		for (var entry : map.entrySet()) {
			AccessRightSet arset = entry.getValue();

			// ignore entries that are empty or null
			if (arset == null || arset.isEmpty()) {
				continue;
			}

			try {
				entriesProto.add(NodePrivileges.newBuilder()
						.setNode(ProtoUtil.toNodeProto(pap.query().graph().getNodeById(entry.getKey())))
						.addAllArset(arset)
						.build());
			} catch (PMException e) {
				throw new RuntimeException(e);
			}
		}

		return entriesProto;
	}

	private List<NodePrivileges> nodePrivilegesResponse(Map<Node, AccessRightSet> map) {
		List<NodePrivileges> nodePrivileges = new ArrayList<>();
		for (var entry : map.entrySet()) {
			AccessRightSet arset = entry.getValue();

			// ignore entries that are empty or null
			if (arset == null || arset.isEmpty()) {
				continue;
			}

			NodePrivileges nodePrivilege = NodePrivileges.newBuilder()
					.setNode(ProtoUtil.toNodeProto(entry.getKey()))
					.addAllArset(arset)
					.build();
			nodePrivileges.add(nodePrivilege);
		}

		return nodePrivileges;
	}

	private List<gov.nist.csd.pm.proto.v1.model.Node> nodeIdsToNodeProtoList(PAP pap, Collection<Long> descs) {
		List<gov.nist.csd.pm.proto.v1.model.Node> nodeProtos = new ArrayList<>();
		for (Long desc : descs) {
			try {
				nodeProtos.add(ProtoUtil.toNodeProto(pap.query().graph().getNodeById(desc)));
			} catch (PMException e) {
				throw new RuntimeException(e);
			}
		}

		return nodeProtos;
	}

	private List<gov.nist.csd.pm.proto.v1.model.Association> toAssociationProtoList(PAP pap, Collection<Association> associations) {
		List<gov.nist.csd.pm.proto.v1.model.Association> associationProtos = new ArrayList<>();
		for (Association association : associations) {
			try {
				associationProtos.add(
						gov.nist.csd.pm.proto.v1.model.Association.newBuilder()
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

	private List<gov.nist.csd.pm.proto.v1.model.Obligation> toObligationProtoList(PAP pap, Collection<Obligation> obligations) {
		List<gov.nist.csd.pm.proto.v1.model.Obligation> obligationProtos = new ArrayList<>();
		for (Obligation obligation : obligations) {
			try {
				obligationProtos.add(ProtoUtil.toObligationProto(obligation, pap));
			} catch (PMException e) {
				throw new RuntimeException(e);
			}
		}

		return obligationProtos;
	}

	private List<Param> convertParamsToProtoParams(List<FormalParameter<?>> formalParameters) {
		List<Param> params = new ArrayList<>();

		for (FormalParameter<?> formalParameter : formalParameters) {
			Param.Builder builder = Param.newBuilder()
					.setName(formalParameter.getName())
					.setType(typeToParamType(formalParameter.getType()));

			if (formalParameter instanceof NodeFormalParameter<?> nodeFormalParameter) {
				builder.setReqCaps(StringList.newBuilder().addAllValues(nodeFormalParameter.getRequiredCapabilities()).build());
			}

			params.add(builder.build());
		}

		return params;
	}

	private ParamType typeToParamType(Type<?> type) {
		switch (type) {
			case gov.nist.csd.pm.core.pap.operation.arg.type.StringType stringType -> {
				return ParamType.newBuilder()
						.setStringType(StringType.newBuilder().build())
						.build();
			}
			case gov.nist.csd.pm.core.pap.operation.arg.type.LongType longType -> {
				return ParamType.newBuilder()
						.setLongType(LongType.newBuilder().build())
						.build();
			}
			case gov.nist.csd.pm.core.pap.operation.arg.type.BooleanType booleanType -> {
				return ParamType.newBuilder()
						.setBooleanType(BooleanType.newBuilder().build())
						.build();
			}
			case gov.nist.csd.pm.core.pap.operation.arg.type.ListType<?> listType -> {
				Type<?> elementType = listType.getElementType();
				return ParamType.newBuilder()
						.setListType(ListType.newBuilder()
								             .setElementType(typeToParamType(elementType))
								             .build())
						.build();

			}
			case gov.nist.csd.pm.core.pap.operation.arg.type.MapType<?, ?> mapType -> {
				Type<?> keyType = mapType.getKeyType();
				Type<?> valueType = mapType.getValueType();
				return ParamType.newBuilder()
						.setMapType(MapType.newBuilder()
								            .setKeyType(typeToParamType(keyType))
								            .setValueType(typeToParamType(valueType))
								            .build())
						.build();
			}
			case null, default -> {
				return ParamType.newBuilder()
						.setAnyType(AnyType.newBuilder().build())
						.build();
			}
		}
	}

	private <T extends Operation<?>> List<Signature> getSignatures(Class<T> clazz) throws PMException {
		Collection<T> adminOperations = adjudicator.adjudicateQuery((pap, pdpTx) -> {
			Collection<Operation<?>> ops = new ArrayList<>(pdpTx.query().operations().getOperations());
			return ops.stream()
					.filter(clazz::isInstance)
					.map(clazz::cast)
					.toList();
		});

		List<Signature> signatures = new ArrayList<>();
		for (Operation<?> op : adminOperations) {
			signatures.add(buildSignature(op));
		}

		return signatures;
	}

	private Signature buildSignature(Operation<?> op) {
		return Signature.newBuilder()
				.setName(op.getName())
				.addAllParams(convertParamsToProtoParams(op.getFormalParameters()))
				.build();
	}

}
