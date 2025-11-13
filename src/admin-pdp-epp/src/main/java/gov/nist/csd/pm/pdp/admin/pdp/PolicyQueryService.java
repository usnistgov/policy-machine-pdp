package gov.nist.csd.pm.pdp.admin.pdp;

import com.google.protobuf.BoolValue;
import com.google.protobuf.Empty;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.node.Node;
import gov.nist.csd.pm.core.common.graph.node.NodeType;
import gov.nist.csd.pm.core.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.core.common.graph.relationship.Association;
import gov.nist.csd.pm.core.common.prohibition.Prohibition;
import gov.nist.csd.pm.core.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.function.arg.FormalParameter;
import gov.nist.csd.pm.core.pap.function.arg.type.Type;
import gov.nist.csd.pm.core.pap.function.op.Operation;
import gov.nist.csd.pm.core.pap.function.routine.Routine;
import gov.nist.csd.pm.core.pap.obligation.Obligation;
import gov.nist.csd.pm.core.pap.query.model.explain.Explain;
import gov.nist.csd.pm.core.pap.query.model.subgraph.Subgraph;
import gov.nist.csd.pm.core.pap.query.model.subgraph.SubgraphPrivileges;
import gov.nist.csd.pm.core.pap.serialization.PolicySerializer;
import gov.nist.csd.pm.core.pap.serialization.json.JSONSerializer;
import gov.nist.csd.pm.core.pdp.UnauthorizedException;
import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;
import gov.nist.csd.pm.proto.v1.model.*;
import gov.nist.csd.pm.proto.v1.query.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@GrpcService
public class PolicyQueryService extends PolicyQueryServiceGrpc.PolicyQueryServiceImplBase {

	private static final Logger logger = LoggerFactory.getLogger(PolicyQueryService.class);

	private Adjudicator adjudicator;

	public PolicyQueryService(Adjudicator adjudicator) {
		this.adjudicator = adjudicator;
	}

	@Override
	public void nodeExists(IdOrNameQuery request, StreamObserver<BoolValue> responseObserver) {
		try {
			boolean exists = adjudicator.adjudicateQuery((pap, pdpTx) -> pdpTx.query().graph().nodeExists(request.getId()));

			responseObserver.onNext(BoolValue.newBuilder().setValue(exists).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getNode(IdOrNameQuery request, StreamObserver<gov.nist.csd.pm.proto.v1.model.Node> responseObserver) {
		try {
			Node node =  adjudicator.adjudicateQuery((pap, pdpTx) -> switch (request.getIdOrNameCase()) {
				case ID -> pdpTx.query().graph().getNodeById(request.getId());
				case NAME -> pdpTx.query().graph().getNodeByName(request.getName());
				case IDORNAME_NOT_SET -> throw new PMException("IdOrName not set");
			});

			responseObserver.onNext(ProtoUtil.toNodeProto(node));
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getNodeId(IdOrNameQuery request, StreamObserver<Int64Value> responseObserver) {
		try {
			long id = adjudicator.adjudicateQuery((pap, pdpTx) -> pdpTx.query().graph().getNodeId(request.getName()));

			responseObserver.onNext(Int64Value.newBuilder().setValue(id).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void searchNodes(SearchQuery request, StreamObserver<gov.nist.csd.pm.proto.v1.model.NodeList> responseObserver) {
		try {
			Collection<Node> nodes = adjudicator.adjudicateQuery((pap, pdpTx) -> pdpTx.query().graph().search(
					NodeType.toNodeType(request.getType().name()),
					request.getPropertiesMap()
			));

			List<gov.nist.csd.pm.proto.v1.model.Node> nodeProtos = new ArrayList<>();
			for (Node node : nodes) {
				nodeProtos.add(ProtoUtil.toNodeProto(node));
			}

			responseObserver.onNext(gov.nist.csd.pm.proto.v1.model.NodeList.newBuilder().addAllNodes(nodeProtos).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getPolicyClasses(Empty request, StreamObserver<NodeList> responseObserver) {
		try {
			List<gov.nist.csd.pm.proto.v1.model.Node> nodeProtos = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Long> policyClasses = pdpTx.query().graph().getPolicyClasses();
				return nodeIdsToNodeProtoList(pap, policyClasses);
			});

			responseObserver.onNext(NodeList.newBuilder().addAllNodes(nodeProtos).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAdjacentDescendants(GetAdjacentAssignmentsQuery request,
	                                   StreamObserver<NodeList> responseObserver) {
		try {
			Collection<gov.nist.csd.pm.proto.v1.model.Node> descs = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Long> adjacentDescendants = pdpTx.query().graph().getAdjacentDescendants(request.getNodeId());
				return nodeIdsToNodeProtoList(pap, adjacentDescendants);
			});

			responseObserver.onNext(NodeList.newBuilder().addAllNodes(descs).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAdjacentAscendants(GetAdjacentAssignmentsQuery request,
	                                  StreamObserver<NodeList> responseObserver) {
		try {
			Collection<gov.nist.csd.pm.proto.v1.model.Node> ascs = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Long> adjacentAscendants = pdpTx.query().graph().getAdjacentAscendants(request.getNodeId());
				return nodeIdsToNodeProtoList(pap, adjacentAscendants);
			});

			responseObserver.onNext(NodeList.newBuilder().addAllNodes(ascs).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAssociationsWithSource(GetAssociationsQuery request,
	                                      StreamObserver<AssociationList> responseObserver) {
		try {
			List<gov.nist.csd.pm.proto.v1.model.Association> associations = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Association> associationsWithSource = pdpTx.query().graph().getAssociationsWithSource(request.getNodeId());
				return toAssociationProtoList(pap, associationsWithSource);
			});

			responseObserver.onNext(AssociationList.newBuilder().addAllAssociations(associations).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAssociationsWithTarget(GetAssociationsQuery request,
	                                      StreamObserver<AssociationList> responseObserver) {
		try {
			List<gov.nist.csd.pm.proto.v1.model.Association> associations = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Association> associationsWithTarget = pdpTx.query().graph().getAssociationsWithTarget(request.getNodeId());
				return toAssociationProtoList(pap, associationsWithTarget);
			});

			responseObserver.onNext(AssociationList.newBuilder().addAllAssociations(associations).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAscendantSubgraph(GetSubgraphQuery request, StreamObserver<gov.nist.csd.pm.proto.v1.query.Subgraph> responseObserver) {
		try {
			Subgraph subgraph = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().graph().getAscendantSubgraph(request.getNodeId());
			});

			responseObserver.onNext(toSubgraph(subgraph));
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getDescendantSubgraph(GetSubgraphQuery request, StreamObserver<gov.nist.csd.pm.proto.v1.query.Subgraph> responseObserver) {
		try {
			Subgraph subgraph = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().graph().getDescendantSubgraph(request.getNodeId());
			});

			responseObserver.onNext(toSubgraph(subgraph));
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAttributeDescendants(GetDescendantsQuery request,
	                                    StreamObserver<NodeList> responseObserver) {
		try {
			List<gov.nist.csd.pm.proto.v1.model.Node> nodes = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Long> descs = pdpTx.query().graph().getAttributeDescendants(request.getNodeId());
				return nodeIdsToNodeProtoList(pap, descs);
			});

			responseObserver.onNext(NodeList.newBuilder().addAllNodes(nodes).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getPolicyClassDescendants(GetDescendantsQuery request,
	                                      StreamObserver<NodeList> responseObserver) {
		try {
			Collection<gov.nist.csd.pm.proto.v1.model.Node> descs = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Long> policyClassDescendants = pdpTx.query().graph().getPolicyClassDescendants(request.getNodeId());
				return nodeIdsToNodeProtoList(pap, policyClassDescendants);
			});

			responseObserver.onNext(NodeList.newBuilder().addAllNodes(descs).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void isAscendant(ContainmentQuery request, StreamObserver<BoolValue> responseObserver) {
		try {
			boolean isAscendant = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().graph().isAscendant(request.getAscendantId(), request.getDescendantId());
			});

			responseObserver.onNext(BoolValue.newBuilder().setValue(isAscendant).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void isDescendant(ContainmentQuery request, StreamObserver<BoolValue> responseObserver) {
		try {
			boolean isDescendant = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().graph().isDescendant(request.getAscendantId(), request.getDescendantId());
			});

			responseObserver.onNext(BoolValue.newBuilder().setValue(isDescendant).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getProhibitions(Empty request, StreamObserver<ProhibitionList> responseObserver) {
		try {
			Collection<gov.nist.csd.pm.proto.v1.model.Prohibition> prohibitions = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				List<gov.nist.csd.pm.proto.v1.model.Prohibition> prohibitionProtos = new ArrayList<>();
				for (Prohibition prohibition : pdpTx.query().prohibitions().getProhibitions()) {
					prohibitionProtos.add(ProtoUtil.toProhibitionProto(prohibition, pap.query()));
				}
				
				return prohibitionProtos;
			});

			responseObserver.onNext(ProhibitionList.newBuilder().addAllProhibitions(prohibitions).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getProhibitionsBySubject(GetProhibitionBySubjectQuery request,
	                                     StreamObserver<ProhibitionList> responseObserver) {
		try {
			Collection<gov.nist.csd.pm.proto.v1.model.Prohibition> prohibitions = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				ProhibitionSubject subject = switch (request.getSubjectCase()) {
					case NODE_ID -> new ProhibitionSubject(request.getNodeId());
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

			responseObserver.onNext(ProhibitionList.newBuilder().addAllProhibitions(prohibitions).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getProhibition(GetByNameQuery request, StreamObserver<gov.nist.csd.pm.proto.v1.model.Prohibition> responseObserver) {
		try {
			gov.nist.csd.pm.proto.v1.model.Prohibition prohibition = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Prohibition p = pdpTx.query().prohibitions().getProhibition(request.getName());
				return ProtoUtil.toProhibitionProto(p, pap.query());
			});

			responseObserver.onNext(prohibition);
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getInheritedProhibitions(GetInheritedProhibitionsQuery request,
	                                     StreamObserver<ProhibitionList> responseObserver) {
		try {
			Collection<gov.nist.csd.pm.proto.v1.model.Prohibition> prohibitionProtos = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Prohibition> inheritedProhibitionsFor = pdpTx.query().prohibitions().getInheritedProhibitionsFor(
						request.getSubjectId()
				);

				List<gov.nist.csd.pm.proto.v1.model.Prohibition> protos = new ArrayList<>();
				for (Prohibition prohibition : inheritedProhibitionsFor) {
					protos.add(ProtoUtil.toProhibitionProto(prohibition, pap.query()));
				}

				return protos;
			});

			responseObserver.onNext(ProhibitionList.newBuilder().addAllProhibitions(prohibitionProtos).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getProhibitionsWithContainer(GetProhibitionsWithContainerQuery request,
	                                         StreamObserver<ProhibitionList> responseObserver) {
		try {
			Collection<gov.nist.csd.pm.proto.v1.model.Prohibition> prohibitionProtos = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Prohibition> prohibitionsWithContainer = pdpTx.query().prohibitions().getProhibitionsWithContainer(
						request.getContainerId()
				);

				List<gov.nist.csd.pm.proto.v1.model.Prohibition> protos = new ArrayList<>();
				for (Prohibition prohibition : prohibitionsWithContainer) {
					protos.add(ProtoUtil.toProhibitionProto(prohibition, pap.query()));
				}

				return protos;
			});

			responseObserver.onNext(ProhibitionList.newBuilder().addAllProhibitions(prohibitionProtos).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getObligations(Empty request, StreamObserver<ObligationList> responseObserver) {
		long s = System.nanoTime();
		try {
			List<gov.nist.csd.pm.proto.v1.model.Obligation> obligationProtos = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Obligation> obligations = pdpTx.query().obligations().getObligations();
				return toObligationProtoList(pap, obligations);
			});

			responseObserver.onNext(ObligationList.newBuilder().addAllObligations(obligationProtos).build());
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
	public void getObligation(GetByNameQuery request, StreamObserver<gov.nist.csd.pm.proto.v1.model.Obligation> responseObserver) {
		try {
			gov.nist.csd.pm.proto.v1.model.Obligation obligation = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Obligation o = pdpTx.query().obligations().getObligation(request.getName());
				return ProtoUtil.toObligationProto(o, pap);
			});

			responseObserver.onNext(obligation);
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getObligationsByAuthor(GetObligationByAuthorQuery request,
	                                   StreamObserver<ObligationList> responseObserver) {
		try {
			Collection<gov.nist.csd.pm.proto.v1.model.Obligation> obligations = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<Obligation> obligationsWithAuthor = pdpTx.query().obligations().getObligationsWithAuthor(
						request.getAuthorId()
				);
				return toObligationProtoList(pap, obligationsWithAuthor);
			});

			responseObserver.onNext(ObligationList.newBuilder().addAllObligations(obligations).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getResourceOperations(Empty request, StreamObserver<gov.nist.csd.pm.proto.v1.model.StringList> responseObserver) {
		try {
			AccessRightSet resourceOps = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().operations().getResourceOperations();
			});

			responseObserver.onNext(gov.nist.csd.pm.proto.v1.model.StringList.newBuilder().addAllValues(resourceOps).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAdminOperationSignatures(Empty request,
	                                        StreamObserver<SignatureList> responseObserver) {
		try {
			Collection<Operation<?>> adminOperations = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<String> names = pdpTx.query().operations().getAdminOperationNames();
				List<Operation<?>> operations = new ArrayList<>();
				for (String name : names) {
					Operation<?> op = pap.query().operations().getAdminOperation(name);
					operations.add(op);
				}
				return operations;
			});

			List<Signature> signatures = new ArrayList<>();
			for (Operation<?> op : adminOperations) {
				signatures.add(Signature.newBuilder()
						               .setName(op.getName())
						               .addAllParams(convertParamsToProtoParams(op.getFormalParameters()))
						               .build());
			}

			SignatureList signatureList = SignatureList.newBuilder()
					.addAllSignatures(signatures)
					.build();
			responseObserver.onNext(signatureList);
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAdminOperationSignature(GetByNameQuery request,
	                                       StreamObserver<gov.nist.csd.pm.proto.v1.query.Signature> responseObserver) {
		try {
			Operation<?> operation = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().operations().getAdminOperation(request.getName());
			});

			Signature signature = Signature.newBuilder()
					.setName(operation.getName())
					.addAllParams(convertParamsToProtoParams(operation.getFormalParameters()))
					.build();

			responseObserver.onNext(signature);
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAdminRoutineSignatures(Empty request,
	                                      StreamObserver<SignatureList> responseObserver) {
		try {
			Collection<Routine<?>> adminRoutines = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Collection<String> names = pdpTx.query().routines().getAdminRoutineNames();
				List<Routine<?>> routines = new ArrayList<>();
				for (String name : names) {
					Routine<?> op = pap.query().routines().getAdminRoutine(name);
					routines.add(op);
				}
				return routines;
			});

			List<Signature> signatures = new ArrayList<>();
			for (Routine<?> routine : adminRoutines) {
				signatures.add(Signature.newBuilder()
						               .setName(routine.getName())
						               .addAllParams(convertParamsToProtoParams(routine.getFormalParameters()))
						               .build());
			}

			SignatureList signatureList = SignatureList.newBuilder()
					.addAllSignatures(signatures)
					.build();
			responseObserver.onNext(signatureList);
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void getAdminRoutineSignature(GetByNameQuery request, StreamObserver<Signature> responseObserver) {
		try {
			Routine<?> routine = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().routines().getAdminRoutine(request.getName());
			});

			Signature signature = Signature.newBuilder()
					.setName(routine.getName())
					.addAllParams(convertParamsToProtoParams(routine.getFormalParameters()))
					.build();

			responseObserver.onNext(signature);
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computePrivileges(ComputePrivilegesQuery request,
	                              StreamObserver<StringList> responseObserver) {
		try {
			AccessRightSet privs = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().access().computePrivileges(
						ProtoUtil.fromUserContextProto(request.getUserCtx()),
						ProtoUtil.fromTargetContextProto(request.getTargetCtx())
				);
			});

			responseObserver.onNext(StringList.newBuilder().addAllValues(privs).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computeDeniedPrivileges(ComputeDeniedPrivilegesQuery request,
	                                    StreamObserver<StringList> responseObserver) {
		try {
			AccessRightSet denied = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().access().computeDeniedPrivileges(
						ProtoUtil.fromUserContextProto(request.getUserCtx()),
						ProtoUtil.fromTargetContextProto(request.getTargetCtx())
				);
			});

			responseObserver.onNext(StringList.newBuilder().addAllValues(denied).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computeCapabilityList(ComputeCapabilityListQuery request,
	                                  StreamObserver<AccessQueryMapping> responseObserver) {
		try {
			Map<Long, AccessQueryMappingEntry> capList = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Map<Long, AccessRightSet> map = pdpTx.query().access().computeCapabilityList(
						ProtoUtil.fromUserContextProto(request.getUserCtx())
				);

				return toArsetProtoMap(pap, map);
			});

			responseObserver.onNext(AccessQueryMapping.newBuilder().putAllMap(capList).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computeACL(ComputeACLQuery request, StreamObserver<AccessQueryMapping> responseObserver) {
		try {
			Map<Long, AccessQueryMappingEntry> map = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Map<Long, AccessRightSet> acl = pdpTx.query().access().computeACL(
						ProtoUtil.fromTargetContextProto(request.getTargetCtx())
				);

				return toArsetProtoMap(pap, acl);
			});

			responseObserver.onNext(AccessQueryMapping.newBuilder().putAllMap(map).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computeDestinationAttributes(ComputeDestinationAttributesQuery request,
	                                         StreamObserver<AccessQueryMapping> responseObserver) {
		try {
			Map<Long, AccessQueryMappingEntry> map = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Map<Long, AccessRightSet> destAttrs = pdpTx.query().access().computeDestinationAttributes(
						ProtoUtil.fromUserContextProto(request.getUserCtx())
				);

				return toArsetProtoMap(pap, destAttrs);
			});

			responseObserver.onNext(AccessQueryMapping.newBuilder().putAllMap(map).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computeSubgraphPrivileges(AccessWithRootQuery request,
	                                      StreamObserver<gov.nist.csd.pm.proto.v1.query.SubgraphPrivileges> responseObserver) {
		try {
			SubgraphPrivileges subgraphPrivileges = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().access().computeSubgraphPrivileges(
						ProtoUtil.fromUserContextProto(request.getUserCtx()),
						request.getRoot()
				);
			});

			responseObserver.onNext(toSubgraphPrivilegesProto(subgraphPrivileges));
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computeAdjacentAscendantPrivileges(AccessWithRootQuery request,
	                                               StreamObserver<NodePrivilegeList> responseObserver) {
		try {
			Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().access().computeAdjacentAscendantPrivileges(
						ProtoUtil.fromUserContextProto(request.getUserCtx()),
						request.getRoot()
				);
			});

			nodePrivilegeResponse(responseObserver, map);
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void computeAdjacentDescendantPrivileges(AccessWithRootQuery request,
	                                                StreamObserver<NodePrivilegeList> responseObserver) {
		try {
			Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().access().computeAdjacentDescendantPrivileges(
						ProtoUtil.fromUserContextProto(request.getUserCtx()),
						request.getRoot()
				);
			});

			nodePrivilegeResponse(responseObserver, map);
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void explain(ExplainQuery request, StreamObserver<ExplainResponse> responseObserver) {
		try {
			ExplainResponse explainProto = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				Explain explain = pdpTx.query().access().explain(
						ProtoUtil.fromUserContextProto(request.getUserCtx()),
						ProtoUtil.fromTargetContextProto(request.getTargetCtx())
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
	public void computePersonalObjectSystem(ComputePOSQuery request,
	                                        StreamObserver<NodePrivilegeList> responseObserver) {
		try {
			Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().access().computePersonalObjectSystem(
						ProtoUtil.fromUserContextProto(request.getUserCtx())
				);
			});

			nodePrivilegeResponse(responseObserver, map);
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void selfComputePrivileges(gov.nist.csd.pm.proto.v1.query.TargetContext request,
	                                  StreamObserver<StringList> responseObserver) {
		try {
			AccessRightSet privs = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().selfAccess().computePrivileges(
						ProtoUtil.fromTargetContextProto(request)
				);
			});

			responseObserver.onNext(StringList.newBuilder().addAllValues(privs).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void selfComputeSubgraphPrivileges(SelfAccessWithRootQuery request,
	                                          StreamObserver<gov.nist.csd.pm.proto.v1.query.SubgraphPrivileges> responseObserver) {
		try {
			SubgraphPrivileges subgraphPrivileges = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().selfAccess().computeSubgraphPrivileges(request.getRoot());
			});

			responseObserver.onNext(toSubgraphPrivilegesProto(subgraphPrivileges));
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void selfComputeAdjacentAscendantPrivileges(SelfAccessWithRootQuery request,
	                                                   StreamObserver<NodePrivilegeList> responseObserver) {
		try {
			Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().selfAccess().computeAdjacentAscendantPrivileges(
						request.getRoot());
			});

			nodePrivilegeResponse(responseObserver, map);
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void selfComputeAdjacentDescendantPrivileges(SelfAccessWithRootQuery request,
	                                                    StreamObserver<NodePrivilegeList> responseObserver) {
		try {
			Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().selfAccess().computeAdjacentDescendantPrivileges(request.getRoot());
			});

			nodePrivilegeResponse(responseObserver, map);
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void selfComputePersonalObjectSystem(Empty request, StreamObserver<NodePrivilegeList> responseObserver) {
		try {
			Map<Node, AccessRightSet> map = adjudicator.adjudicateQuery((pap, pdpTx) -> {
				return pdpTx.query().selfAccess().computePersonalObjectSystem();
			});

			nodePrivilegeResponse(responseObserver, map);
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
		}
	}

	@Override
	public void serialize(SerializeQuery request, StreamObserver<StringValue> responseObserver) {
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

			responseObserver.onNext(StringValue.newBuilder().setValue(serialized).build());
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
				.addAllSubgraph(subgraphs)
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

	private Map<Long, AccessQueryMappingEntry> toArsetProtoMap(PAP pap, Map<Long, AccessRightSet> map) {
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
			params.add(Param.newBuilder()
					           .setName(formalParameter.getName())
					           .setType(typeToParamType(formalParameter.getType()))
					           .build());
		}

		return params;
	}

	private ParamType typeToParamType(Type<?> type) {
		switch (type) {
			case gov.nist.csd.pm.core.pap.function.arg.type.StringType stringType -> {
				return ParamType.newBuilder()
						.setStringType(StringType.newBuilder().build())
						.build();
			}
			case gov.nist.csd.pm.core.pap.function.arg.type.LongType longType -> {
				return ParamType.newBuilder()
						.setLongType(LongType.newBuilder().build())
						.build();
			}
			case gov.nist.csd.pm.core.pap.function.arg.type.BooleanType booleanType -> {
				return ParamType.newBuilder()
						.setBooleanType(BooleanType.newBuilder().build())
						.build();
			}
			case gov.nist.csd.pm.core.pap.function.arg.type.ListType<?> listType -> {
				Type<?> elementType = listType.getElementType();
				return ParamType.newBuilder()
						.setListType(ListType.newBuilder()
								             .setElementType(typeToParamType(elementType))
								             .build())
						.build();

			}
			case gov.nist.csd.pm.core.pap.function.arg.type.MapType<?, ?> mapType -> {
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

}
