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
import gov.nist.csd.pm.pdp.proto.model.*;
import gov.nist.csd.pm.pdp.proto.query.ObligationProto;
import gov.nist.csd.pm.pdp.proto.query.TargetContextProto;
import gov.nist.csd.pm.pdp.proto.query.UserContextProto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ProtoUtil {

	public static UserContext fromUserContextProto(UserContextProto userCtxProto) throws PMException {
		return switch (userCtxProto.getUserCase()) {
			case ID -> new UserContext(userCtxProto.getId());
			case ATTRIBUTES -> new UserContext(userCtxProto.getAttributes().getIdsList());
			case USER_NOT_SET -> throw new PMException("User not set");
		};
	}

	public static TargetContext fromTargetContextProto(TargetContextProto targetCtxProto) throws PMException {
		return switch (targetCtxProto.getTargetCase()) {
			case ID -> new TargetContext(targetCtxProto.getId());
			case ATTRIBUTES -> new TargetContext(targetCtxProto.getAttributes().getIdsList());
			case TARGET_NOT_SET -> throw new PMException("Target not set");
		};
	}

	public static NodeProto toNodeProto(Node node) {
		return NodeProto.newBuilder()
				.setId(node.getId())
				.setName(node.getName())
				.setType(NodeProto.NodeTypeProto.valueOf(node.getType().name()))
				.putAllProperties(node.getProperties())
				.build();
	}

	public static ProhibitionProto toProhibitionProto(Prohibition prohibition, PolicyQuery query) {
		ProhibitionProto.Builder builder = ProhibitionProto.newBuilder()
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

			List<ProhibitionProto.ContainerCondition> containerConditions = new ArrayList<>();
			for (ContainerCondition cc : prohibition.getContainers()) {
				containerConditions.add(
						ProhibitionProto.ContainerCondition.newBuilder()
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

	public static ObligationProto toObligationProto(Obligation obligation, PAP pap) throws PMException {
		ObligationProto.Builder builder = ObligationProto.newBuilder()
				.setName(obligation.getName())
				.setAuthor(ProtoUtil.toNodeProto(pap.query().graph().getNodeById(obligation.getAuthorId())))
				.setPml(obligation.toString());
		return builder.build();
	}

	public static ExplainProto buildExplainProto(Explain explain, PolicyQuery query) {
		if (explain == null) {
			return ExplainProto.newBuilder().build();
		}

		AccessRightSet privileges = explain.getPrivileges();
		Collection<PolicyClassExplain> policyClasses = explain.getPolicyClasses();
		Collection<Prohibition> prohibitions = explain.getProhibitions();
		AccessRightSet deniedPrivileges = explain.getDeniedPrivileges();

		List<PolicyClassExplainProto> policyClassProtos = new ArrayList<>();
		for (PolicyClassExplain pc : policyClasses) {
			Node pcNode = pc.pc();
			Collection<List<ExplainNode>> paths = pc.paths();
			List<ExplainNodePathProto> pathProtos = new ArrayList<>();
			for (List<ExplainNode> path : paths) {
				List<ExplainNodeProto> explainNodeProtos = new ArrayList<>();
				for (ExplainNode explainNode : path) {
					List<ExplainAssociationProto> explainAssociationProtos = new ArrayList<>();
					for (ExplainAssociation explainAssociation : explainNode.associations()) {
						List<PathProto> userPathProtos = new ArrayList<>();
						for (Path userPath : explainAssociation.userPaths()) {
							List<NodeProto> nodeProtos = new ArrayList<>();
							for (Node node : userPath) {
								nodeProtos.add(toNodeProto(node));
							}

							userPathProtos.add(PathProto.newBuilder()
									                   .addAllNodes(nodeProtos)
									                   .build());
						}

						explainAssociationProtos.add(ExplainAssociationProto.newBuilder()
								                             .setUa(toNodeProto(explainAssociation.ua()))
								                             .addAllArset(explainAssociation.arset())
								                             .addAllUserPaths(userPathProtos)
								                             .build());
					}

					explainNodeProtos.add(ExplainNodeProto.newBuilder()
							                      .setNode(toNodeProto(explainNode.node()))
							                      .addAllAssociations(explainAssociationProtos)
							                      .build());
				}

				pathProtos.add(ExplainNodePathProto.newBuilder()
						               .addAllNodes(explainNodeProtos)
						               .build());
			}

			policyClassProtos.add(PolicyClassExplainProto.newBuilder()
					                      .setPc(toNodeProto(pcNode))
					                      .addAllArset(pc.arset())
					                      .addAllPaths(pathProtos)
					                      .build());
		}

		List<ProhibitionProto> prohibitionProtos = new ArrayList<>();
		for (Prohibition p : prohibitions) {
			prohibitionProtos.add(toProhibitionProto(p, query));
		}

		return ExplainProto.newBuilder()
				.addAllPrivileges(privileges)
				.addAllDeniedPrivileges(deniedPrivileges)
				.addAllPolicyClasses(policyClassProtos)
				.addAllProhibitions(prohibitionProtos)
				.build();
	}

}
