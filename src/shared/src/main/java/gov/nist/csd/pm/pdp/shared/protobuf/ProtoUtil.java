package gov.nist.csd.pm.pdp.shared.protobuf;

import gov.nist.csd.pm.core.common.event.EventContext;
import gov.nist.csd.pm.core.common.event.EventContextUser;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.node.Node;
import gov.nist.csd.pm.core.common.prohibition.NodeProhibition;
import gov.nist.csd.pm.core.common.prohibition.ProcessProhibition;
import gov.nist.csd.pm.core.common.prohibition.Prohibition;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.obligation.Obligation;
import gov.nist.csd.pm.core.pap.operation.accessright.AccessRightSet;
import gov.nist.csd.pm.core.pap.query.PolicyQuery;
import gov.nist.csd.pm.core.pap.query.model.context.TargetContext;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pap.query.model.explain.*;
import gov.nist.csd.pm.proto.v1.model.*;

import java.util.*;

public class ProtoUtil {

	public static Map<String, Object> valueMapToObjectMap(ValueMap valueMap) {
		Map<String, Object> converted = new HashMap<>();

		Map<String, Value> values = valueMap.getValuesMap();
		for (Map.Entry<String, Value> entry : values.entrySet()) {
			converted.put(entry.getKey(), valueToObject(entry.getValue()));
		}

		return converted;
	}

	public static ValueMap objectMapToValueMap(Map<String, Object> objectMap) {
		Map<String, Value> converted = new HashMap<>();

		for (Map.Entry<String, Object> entry : objectMap.entrySet()) {
			converted.put(entry.getKey(), objectToValue(entry.getValue()));
		}

		return ValueMap.newBuilder().putAllValues(converted).build();
	}

	public static Object valueToObject(Value value) {
		return switch (value.getDataCase()) {
			case INT64_VALUE -> value.getInt64Value();
			case STRING_VALUE -> value.getStringValue();
			case BOOL_VALUE -> value.getBoolValue();
			case LIST_VALUE -> convertListValue(value.getListValue());
			case MAP_VALUE -> convertMapValue(value.getMapValue());
			case DATA_NOT_SET -> throw new IllegalArgumentException("value data field is not set");
		};
	}

	public static Value objectToValue(Object o) {
		Value.Builder builder = Value.newBuilder();
		if (o instanceof Long l) {
			return builder.setInt64Value(l).build();
		} else if (o instanceof Boolean b) {
			return builder.setBoolValue(b).build();
		} else if (o instanceof List<?> list) {
			List<Value> values = new ArrayList<>();
			for (Object obj : list) {
				values.add(objectToValue(obj));
			}

			return builder.setListValue(ValueList.newBuilder().addAllValues(values)).build();
		} else if (o instanceof Map<?, ?> map) {
			Map<String, Value> values = new HashMap<>();
			for (var e : map.entrySet()) {
				Object key = e.getKey();
				if (!(key instanceof String)) {
					// only supports string keys in maps
					key = key.toString();
				}

				Object value = e.getValue();

				values.put((String) key, objectToValue(value));
			}

			return builder.setMapValue(ValueMap.newBuilder().putAllValues(values)).build();
		} else if (o instanceof String str) {
			return builder.setStringValue(str).build();
		}

		return Value.newBuilder().build();
	}

	public static UserContext fromUserContextProto(PAP pap, gov.nist.csd.pm.proto.v1.pdp.query.UserContext userCtxProto) throws PMException {
		String process = userCtxProto.getProcess();

		return switch (userCtxProto.getUserCase()) {
			case USER_NODE ->
					new UserContext(resolveNodeRefId(pap, userCtxProto.getUserNode()), process);
			case USER_ATTRIBUTES ->
					new UserContext(resolveNodeRefIdList(pap, userCtxProto.getUserAttributes().getNodesList()), process);
			case USER_NOT_SET ->
					throw new IllegalArgumentException("user context not set");
		};
	}

	public static TargetContext fromTargetContextProto(PAP pap, gov.nist.csd.pm.proto.v1.pdp.query.TargetContext targetCtxProto) throws PMException {
		return switch (targetCtxProto.getTargetCase()) {
			case TARGET_NODE ->
					new TargetContext(resolveNodeRefId(pap, targetCtxProto.getTargetNode()));
			case TARGET_ATTRIBUTES ->
					new TargetContext(resolveNodeRefIdList(pap, targetCtxProto.getTargetAttributes().getNodesList()));
			case TARGET_NOT_SET ->
					throw new IllegalArgumentException("target context not set");
		};
	}

	public static long resolveNodeRefId(PAP pap, NodeRef nodeRef) throws PMException {
		return switch (nodeRef.getRefCase()) {
			case ID -> nodeRef.getId();
			case NAME -> pap.query().graph().getNodeByName(nodeRef.getName()).getId();
			case REF_NOT_SET -> throw new IllegalArgumentException("node reference not set");
		};
	}

	public static List<Long> resolveNodeRefIdList(PAP pap, List<NodeRef> nodeRefs) throws PMException {
		List<Long> nodeRefIds = new ArrayList<>();
		for (NodeRef nr : nodeRefs) {
			nodeRefIds.add(resolveNodeRefId(pap, nr));
		}

		return nodeRefIds;
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

	public static gov.nist.csd.pm.proto.v1.model.Prohibition toProhibitionProto(Prohibition prohibition, PolicyQuery query) throws PMException {
		List<gov.nist.csd.pm.proto.v1.model.Node> inclusionNodes = new ArrayList<>();
		for (long node : prohibition.getInclusionSet()) {
			inclusionNodes.add(toNodeProto(query.graph().getNodeById(node)));
		}

		List<gov.nist.csd.pm.proto.v1.model.Node> exclusionNodes = new ArrayList<>();
		for (long node : prohibition.getExclusionSet()) {
			exclusionNodes.add(toNodeProto(query.graph().getNodeById(node)));
		}

		gov.nist.csd.pm.proto.v1.model.Prohibition.Builder builder = gov.nist.csd.pm.proto.v1.model.Prohibition.newBuilder()
				.setName(prohibition.getName())
				.addAllArset(prohibition.getAccessRightSet())
				.addAllInclusionSet(inclusionNodes)
				.addAllInclusionSet(exclusionNodes)
				.setIsConjunctive(prohibition.isConjunctive());

		if (prohibition instanceof NodeProhibition nodeProhibition) {
			builder.setNode(toNodeProto(query.graph().getNodeById(nodeProhibition.getNodeId())));
		} else {
			builder.setProcess(((ProcessProhibition) prohibition).getProcess());
		}

		return builder.build();
	}

	public static gov.nist.csd.pm.proto.v1.model.Obligation toObligationProto(Obligation obligation, PAP pap) throws PMException {
		gov.nist.csd.pm.proto.v1.model.Obligation.Builder builder = gov.nist.csd.pm.proto.v1.model.Obligation.newBuilder()
				.setName(obligation.getName())
				.setAuthor(ProtoUtil.toNodeProto(pap.query().graph().getNodeById(obligation.getAuthorId())))
				.setPml(obligation.toString());
		return builder.build();
	}

	public static gov.nist.csd.pm.proto.v1.pdp.query.ExplainResponse buildExplainProto(Explain explain, PolicyQuery query) throws PMException {
		if (explain == null) {
			return gov.nist.csd.pm.proto.v1.pdp.query.ExplainResponse.newBuilder().build();
		}

		AccessRightSet privileges = explain.getPrivileges();
		Collection<PolicyClassExplain> policyClasses = explain.getPolicyClasses();
		Collection<Prohibition> prohibitions = explain.getProhibitions();
		AccessRightSet deniedPrivileges = explain.getDeniedPrivileges();

		List<gov.nist.csd.pm.proto.v1.pdp.query.PolicyClassExplain> policyClassProtos = new ArrayList<>();
		for (PolicyClassExplain pc : policyClasses) {
			Node pcNode = pc.pc();
			Collection<List<ExplainNode>> paths = pc.paths();
			List<gov.nist.csd.pm.proto.v1.pdp.query.ExplainNodePath> pathProtos = new ArrayList<>();
			for (List<ExplainNode> path : paths) {
				List<gov.nist.csd.pm.proto.v1.pdp.query.ExplainNode> explainNodeProtos = new ArrayList<>();
				for (ExplainNode explainNode : path) {
					List<gov.nist.csd.pm.proto.v1.pdp.query.ExplainAssociation> explainAssociationProtos = new ArrayList<>();
					for (ExplainAssociation explainAssociation : explainNode.associations()) {
						List<gov.nist.csd.pm.proto.v1.pdp.query.Path> userPathProtos = new ArrayList<>();
						for (Path userPath : explainAssociation.userPaths()) {
							List<gov.nist.csd.pm.proto.v1.model.Node> nodeProtos = new ArrayList<>();
							for (Node node : userPath) {
								nodeProtos.add(toNodeProto(node));
							}

							userPathProtos.add(gov.nist.csd.pm.proto.v1.pdp.query.Path.newBuilder()
									                   .addAllNodes(nodeProtos)
									                   .build());
						}

						explainAssociationProtos.add(gov.nist.csd.pm.proto.v1.pdp.query.ExplainAssociation.newBuilder()
								                             .setUa(toNodeProto(explainAssociation.ua()))
								                             .addAllArset(explainAssociation.arset())
								                             .addAllUserPaths(userPathProtos)
								                             .build());
					}

					explainNodeProtos.add(gov.nist.csd.pm.proto.v1.pdp.query.ExplainNode.newBuilder()
							                      .setNode(toNodeProto(explainNode.node()))
							                      .addAllAssociations(explainAssociationProtos)
							                      .build());
				}

				pathProtos.add(gov.nist.csd.pm.proto.v1.pdp.query.ExplainNodePath.newBuilder()
						               .addAllNodes(explainNodeProtos)
						               .build());
			}

			policyClassProtos.add(gov.nist.csd.pm.proto.v1.pdp.query.PolicyClassExplain.newBuilder()
					                      .setPc(toNodeProto(pcNode))
					                      .addAllArset(pc.arset())
					                      .addAllPaths(pathProtos)
					                      .build());
		}

		List<gov.nist.csd.pm.proto.v1.model.Prohibition> prohibitionProtos = new ArrayList<>();
		for (Prohibition p : prohibitions) {
			prohibitionProtos.add(toProhibitionProto(p, query));
		}

		return gov.nist.csd.pm.proto.v1.pdp.query.ExplainResponse.newBuilder()
				.addAllPrivileges(privileges)
				.addAllDeniedPrivileges(deniedPrivileges)
				.addAllPolicyClasses(policyClassProtos)
				.addAllProhibitions(prohibitionProtos)
				.build();
	}

	public static gov.nist.csd.pm.proto.v1.epp.EventContext toEventContextProto(EventContext eventContext) {
		gov.nist.csd.pm.proto.v1.epp.EventContext.Builder builder = gov.nist.csd.pm.proto.v1.epp.EventContext.newBuilder();

		// user
		EventContextUser user = eventContext.user();
		if (user.isUser()) {
			builder.setUserName(user.getName());
		} else {
			builder.setUserAttrs(StringList.newBuilder().addAllValues(user.getAttrs()).build());
		}

		// process
		builder.setProcess(eventContext.user().getProcess());

		// op
		builder.setOpName(eventContext.opName());

		// args
		builder.setArgs(objectMapToValueMap(eventContext.args()));

		return builder.build();
	}

	public static EventContext fromEventContextProto(gov.nist.csd.pm.proto.v1.epp.EventContext proto) {
		String process = proto.getProcess();

		EventContextUser user = switch (proto.getUserCase()) {
			case USER_NAME -> new EventContextUser(proto.getUserName(), process);
			case USER_ATTRS -> new EventContextUser(proto.getUserAttrs().getValuesList(), process);
			case USER_NOT_SET -> throw new IllegalStateException("User not set");
		};

		Map<String, Object> args = new HashMap<>();
		ValueMap protoArgs = proto.getArgs();

		for (Map.Entry<String, Value> e : protoArgs.getValuesMap().entrySet()) {
			String name = e.getKey();
			Value value = e.getValue();

			args.put(name, valueToObject(value));
		}

		return new EventContext(user, proto.getOpName(), args);
	}

	private static List<Object> convertListValue(ValueList valueList) {
		List<Object> result = new ArrayList<>();
		for (Value v : valueList.getValuesList()) {
			result.add(valueToObject(v));
		}

		return result;
	}

	private static Map<Object, Object> convertMapValue(ValueMap valueMap) {
		Map<Object, Object> result = new HashMap<>();
		for(Map.Entry<String, Value> e : valueMap.getValuesMap().entrySet()) {
			result.put(e.getKey(), valueToObject(e.getValue()));
		}

		return result;
	}

}
