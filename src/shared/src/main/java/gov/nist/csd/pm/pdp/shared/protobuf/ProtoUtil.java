package gov.nist.csd.pm.pdp.shared.protobuf;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.node.Node;
import gov.nist.csd.pm.core.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.core.common.prohibition.ContainerCondition;
import gov.nist.csd.pm.core.common.prohibition.Prohibition;
import gov.nist.csd.pm.core.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.obligation.Obligation;
import gov.nist.csd.pm.core.pap.query.PolicyQuery;
import gov.nist.csd.pm.core.pap.query.model.context.TargetContext;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pap.query.model.explain.*;
import gov.nist.csd.pm.proto.v1.model.NodeType;
import gov.nist.csd.pm.proto.v1.model.Value;
import gov.nist.csd.pm.proto.v1.model.ValueMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ProtoUtil {

	public static UserContext fromUserContextProto(gov.nist.csd.pm.proto.v1.query.UserContext userCtxProto) throws PMException {
		return switch (userCtxProto.getUserCase()) {
			case ID -> new UserContext(userCtxProto.getId());
			case ATTRIBUTES -> new UserContext(userCtxProto.getAttributes().getIdsList());
			case USER_NOT_SET -> throw new PMException("User not set");
		};
	}

	public static TargetContext fromTargetContextProto(gov.nist.csd.pm.proto.v1.query.TargetContext targetCtxProto) throws PMException {
		return switch (targetCtxProto.getTargetCase()) {
			case ID -> new TargetContext(targetCtxProto.getId());
			case ATTRIBUTES -> new TargetContext(targetCtxProto.getAttributes().getIdsList());
			case TARGET_NOT_SET -> throw new PMException("Target not set");
		};
	}

	public static gov.nist.csd.pm.proto.v1.model.Node toNodeProto(Node node) {
		ValueMap.Builder valueMap = ValueMap.newBuilder();
		
		for (var entry : node.getProperties().entrySet()) {
			valueMap.putValues(entry.getKey(), Value.newBuilder().setStringValue(entry.getValue()).build());
		}
		
		return gov.nist.csd.pm.proto.v1.model.Node.newBuilder()
				.setId(node.getId())
				.setName(node.getName())
				.setType(NodeType.valueOf(node.getType().name()))
				.putAllProperties(node.getProperties())
				.build();
	}

	public static gov.nist.csd.pm.proto.v1.model.Prohibition toProhibitionProto(Prohibition prohibition, PolicyQuery query) {
		gov.nist.csd.pm.proto.v1.model.Prohibition.Builder builder = gov.nist.csd.pm.proto.v1.model.Prohibition.newBuilder()
				.setName(prohibition.getName())
				.addAllArset(prohibition.getAccessRightSet())
				.setIntersection(prohibition.isIntersection());

		try {
			ProhibitionSubject subject = prohibition.getSubject();

			if (subject.isNode()) {
				builder.setNode(toNodeProto(query.graph().getNodeById(subject.getNodeId())));
			} else {
				builder.setProcess(subject.getProcess());
			}

			List<gov.nist.csd.pm.proto.v1.model.Prohibition.ContainerCondition> containerConditions = new ArrayList<>();
			for (ContainerCondition cc : prohibition.getContainers()) {
				containerConditions.add(
						gov.nist.csd.pm.proto.v1.model.Prohibition.ContainerCondition.newBuilder()
								.setContainer(toNodeProto(query.graph().getNodeById(cc.getId())))
								.setComplement(cc.isComplement())
								.build()
				);
			}

			return builder.addAllContainerConditions(containerConditions)
					.build();
		} catch (PMException e) {
			throw new RuntimeException(e);
		}
	}

	public static gov.nist.csd.pm.proto.v1.model.Obligation toObligationProto(Obligation obligation, PAP pap) throws PMException {
		gov.nist.csd.pm.proto.v1.model.Obligation.Builder builder = gov.nist.csd.pm.proto.v1.model.Obligation.newBuilder()
				.setName(obligation.getName())
				.setAuthor(ProtoUtil.toNodeProto(pap.query().graph().getNodeById(obligation.getAuthorId())))
				.setPml(obligation.toString());
		return builder.build();
	}

	public static gov.nist.csd.pm.proto.v1.query.ExplainResponse buildExplainProto(Explain explain, PolicyQuery query) {
		if (explain == null) {
			return gov.nist.csd.pm.proto.v1.query.ExplainResponse.newBuilder().build();
		}

		AccessRightSet privileges = explain.getPrivileges();
		Collection<PolicyClassExplain> policyClasses = explain.getPolicyClasses();
		Collection<Prohibition> prohibitions = explain.getProhibitions();
		AccessRightSet deniedPrivileges = explain.getDeniedPrivileges();

		List<gov.nist.csd.pm.proto.v1.query.PolicyClassExplain> policyClassProtos = new ArrayList<>();
		for (PolicyClassExplain pc : policyClasses) {
			Node pcNode = pc.pc();
			Collection<List<ExplainNode>> paths = pc.paths();
			List<gov.nist.csd.pm.proto.v1.query.ExplainNodePath> pathProtos = new ArrayList<>();
			for (List<ExplainNode> path : paths) {
				List<gov.nist.csd.pm.proto.v1.query.ExplainNode> explainNodeProtos = new ArrayList<>();
				for (ExplainNode explainNode : path) {
					List<gov.nist.csd.pm.proto.v1.query.ExplainAssociation> explainAssociationProtos = new ArrayList<>();
					for (ExplainAssociation explainAssociation : explainNode.associations()) {
						List<gov.nist.csd.pm.proto.v1.query.Path> userPathProtos = new ArrayList<>();
						for (Path userPath : explainAssociation.userPaths()) {
							List<gov.nist.csd.pm.proto.v1.model.Node> nodeProtos = new ArrayList<>();
							for (Node node : userPath) {
								nodeProtos.add(toNodeProto(node));
							}

							userPathProtos.add(gov.nist.csd.pm.proto.v1.query.Path.newBuilder()
									                   .addAllNodes(nodeProtos)
									                   .build());
						}

						explainAssociationProtos.add(gov.nist.csd.pm.proto.v1.query.ExplainAssociation.newBuilder()
								                             .setUa(toNodeProto(explainAssociation.ua()))
								                             .addAllArset(explainAssociation.arset())
								                             .addAllUserPaths(userPathProtos)
								                             .build());
					}

					explainNodeProtos.add(gov.nist.csd.pm.proto.v1.query.ExplainNode.newBuilder()
							                      .setNode(toNodeProto(explainNode.node()))
							                      .addAllAssociations(explainAssociationProtos)
							                      .build());
				}

				pathProtos.add(gov.nist.csd.pm.proto.v1.query.ExplainNodePath.newBuilder()
						               .addAllNodes(explainNodeProtos)
						               .build());
			}

			policyClassProtos.add(gov.nist.csd.pm.proto.v1.query.PolicyClassExplain.newBuilder()
					                      .setPc(toNodeProto(pcNode))
					                      .addAllArset(pc.arset())
					                      .addAllPaths(pathProtos)
					                      .build());
		}

		List<gov.nist.csd.pm.proto.v1.model.Prohibition> prohibitionProtos = new ArrayList<>();
		for (Prohibition p : prohibitions) {
			prohibitionProtos.add(toProhibitionProto(p, query));
		}

		return gov.nist.csd.pm.proto.v1.query.ExplainResponse.newBuilder()
				.addAllPrivileges(privileges)
				.addAllDeniedPrivileges(deniedPrivileges)
				.addAllPolicyClasses(policyClassProtos)
				.addAllProhibitions(prohibitionProtos)
				.build();
	}

}
