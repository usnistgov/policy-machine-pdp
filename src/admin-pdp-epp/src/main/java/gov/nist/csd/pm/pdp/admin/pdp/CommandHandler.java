package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.core.common.prohibition.ContainerCondition;
import gov.nist.csd.pm.core.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pap.serialization.json.JSONDeserializer;
import gov.nist.csd.pm.core.pdp.PDPTx;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;
import gov.nist.csd.pm.proto.v1.cmd.*;
import gov.nist.csd.pm.proto.v1.model.SerializationFormat;
import gov.nist.csd.pm.proto.v1.model.Value;
import gov.nist.csd.pm.proto.v1.model.ValueList;
import gov.nist.csd.pm.proto.v1.model.ValueMap;
import org.springframework.stereotype.Component;

/**
 * Handles the execution of administrative commands.
 */
@Component
public class CommandHandler {

    /**
     * Handles an administrative command based on its type.
     *
     * @param ngacCtx The ngac transaction context.
     * @param pdpTx The pdp transaction context.
     * @param adminCommand The command to handle.
     * @throws PMException If an error occurs during command handling.
     */
    public Value handleCommand(NGACContext ngacCtx, PDPTx pdpTx, AdminOperationCommand adminCommand) throws PMException {
        return switch (adminCommand.getCmdCase()) {
            case CREATE_POLICY_CLASS_CMD     -> handleCreatePolicyClassCmd(pdpTx, adminCommand.getCreatePolicyClassCmd());
            case CREATE_USER_ATTRIBUTE_CMD   -> handleCreateUserAttributeCmd(pdpTx, ngacCtx.pap(), adminCommand.getCreateUserAttributeCmd());
            case CREATE_USER_CMD             -> handleCreateUserCmd(pdpTx, ngacCtx.pap(), adminCommand.getCreateUserCmd());
            case CREATE_OBJECT_ATTRIBUTE_CMD -> handleCreateObjectAttributeCmd(pdpTx, ngacCtx.pap(), adminCommand.getCreateObjectAttributeCmd());
            case CREATE_OBJECT_CMD           -> handleCreateObjectCmd(pdpTx, ngacCtx.pap(), adminCommand.getCreateObjectCmd());
            case SET_NODE_PROPERTIES_CMD     -> handleSetNodePropertiesCmd(pdpTx, ngacCtx.pap(), adminCommand.getSetNodePropertiesCmd());
            case DELETE_NODE_CMD             -> handleDeleteNodeCmd(pdpTx, ngacCtx.pap(), adminCommand.getDeleteNodeCmd());
            case ASSIGN_CMD                  -> handleAssignCmd(pdpTx, ngacCtx.pap(), adminCommand.getAssignCmd());
            case DEASSIGN_CMD                -> handleDeassignCmd(pdpTx, ngacCtx.pap(), adminCommand.getDeassignCmd());
            case ASSOCIATE_CMD               -> handleAssociateCmd(pdpTx, ngacCtx.pap(), adminCommand.getAssociateCmd());
            case DISSOCIATE_CMD              -> handleDissociateCmd(pdpTx, ngacCtx.pap(), adminCommand.getDissociateCmd());
            case CREATE_PROHIBITION_CMD      -> handleCreateProhibitionCmd(pdpTx, ngacCtx.pap(), adminCommand.getCreateProhibitionCmd());
            case DELETE_PROHIBITION_CMD      -> handleDeleteProhibitionCmd(pdpTx, adminCommand.getDeleteProhibitionCmd());
            case DELETE_OBLIGATION_CMD       -> handleDeleteObligationCmd(pdpTx, adminCommand.getDeleteObligationCmd());
            case DELETE_ADMIN_OPERATION_CMD  -> handleDeleteOperationCmd(pdpTx, adminCommand.getDeleteAdminOperationCmd());
            case SET_RESOURCE_OPERATIONS_CMD -> handleSetResourceOperationsCmd(pdpTx, adminCommand.getSetResourceOperationsCmd());
            case EXECUTE_PML_CMD             -> handleExecutePmlCmd(pdpTx, adminCommand.getExecutePmlCmd());
	        case DESERIALIZE_CMD             -> handleDeserializeCmd(pdpTx, adminCommand.getDeserializeCmd());
	        case CMD_NOT_SET                 -> throw new PMException("cmd not set");
        };
    }

    private Value handleDeserializeCmd(PDPTx pdpTx, DeserializeCmd deserializeCmd) throws PMException {
        SerializationFormat format = deserializeCmd.getFormat();
        if (format == SerializationFormat.JSON) {
            pdpTx.deserialize(deserializeCmd.getSerialized(), new JSONDeserializer());
        } else {
            throw new PMException("unsupported serialize format " + format);
        }

        return Value.newBuilder().build();
    }

    private Value handleCreatePolicyClassCmd(PDPTx pdpTx, CreatePolicyClassCmd cmd) throws PMException {
        long id = pdpTx.modify().graph().createPolicyClass(cmd.getName());
        return Value.newBuilder().setInt64Value(id).build();
    }

    private Value handleCreateUserAttributeCmd(PDPTx pdpTx, PAP pap, CreateUserAttributeCmd cmd) throws PMException {
        long id = pdpTx.modify().graph().createUserAttribute(
                cmd.getName(),
                ProtoUtil.resolveNodeRefIdList(pap, cmd.getDescendantsList())
        );
        return Value.newBuilder().setInt64Value(id).build();
    }

    private Value handleCreateUserCmd(PDPTx pdpTx, PAP pap, CreateUserCmd cmd) throws PMException {
        long id = pdpTx.modify().graph().createUser(
                cmd.getName(),
                ProtoUtil.resolveNodeRefIdList(pap, cmd.getDescendantsList())
        );
        return Value.newBuilder().setInt64Value(id).build();
    }

    private Value handleCreateObjectAttributeCmd(PDPTx pdpTx, PAP pap, CreateObjectAttributeCmd cmd) throws PMException {
        long id = pdpTx.modify().graph().createObjectAttribute(
                cmd.getName(),
                ProtoUtil.resolveNodeRefIdList(pap, cmd.getDescendantsList())
        );
        return Value.newBuilder().setInt64Value(id).build();
    }

    private Value handleCreateObjectCmd(PDPTx pdpTx, PAP pap, CreateObjectCmd cmd) throws PMException {
        long id = pdpTx.modify().graph().createObject(
                cmd.getName(),
                ProtoUtil.resolveNodeRefIdList(pap, cmd.getDescendantsList())
        );
        return Value.newBuilder().setInt64Value(id).build();
    }

    private Value handleSetNodePropertiesCmd(PDPTx pdpTx, PAP pap, SetNodePropertiesCmd cmd) throws PMException {
        pdpTx.modify().graph().setNodeProperties(ProtoUtil.resolveNodeRefId(pap, cmd.getNode()), cmd.getPropertiesMap());
        return Value.newBuilder().build();
    }

    private Value handleDeleteNodeCmd(PDPTx pdpTx, PAP pap, DeleteNodeCmd cmd) throws PMException {
        pdpTx.modify().graph().deleteNode(ProtoUtil.resolveNodeRefId(pap, cmd.getNode()));
        return Value.newBuilder().build();
    }

    private Value handleAssignCmd(PDPTx pdpTx, PAP pap, AssignCmd cmd) throws PMException {
        pdpTx.modify().graph().assign(
                ProtoUtil.resolveNodeRefId(pap, cmd.getAscendant()),
                ProtoUtil.resolveNodeRefIdList(pap, cmd.getDescendantsList())
        );
        return Value.newBuilder().build();
    }

    private Value handleDeassignCmd(PDPTx pdpTx, PAP pap, DeassignCmd cmd) throws PMException {
        pdpTx.modify().graph().deassign(
                ProtoUtil.resolveNodeRefId(pap, cmd.getAscendant()),
                ProtoUtil.resolveNodeRefIdList(pap, cmd.getDescendantsList())
        );
        return Value.newBuilder().build();
    }

    private Value handleAssociateCmd(PDPTx pdpTx, PAP pap, AssociateCmd cmd) throws PMException {
        pdpTx.modify().graph().associate(
                ProtoUtil.resolveNodeRefId(pap, cmd.getUa()),
                ProtoUtil.resolveNodeRefId(pap, cmd.getTarget()),
                new AccessRightSet(cmd.getArsetList())
        );
        return Value.newBuilder().build();
    }

    private Value handleDissociateCmd(PDPTx pdpTx, PAP pap, DissociateCmd cmd) throws PMException {
        pdpTx.modify().graph().dissociate(
                ProtoUtil.resolveNodeRefId(pap, cmd.getUa()),
                ProtoUtil.resolveNodeRefId(pap, cmd.getTarget())
        );
        return Value.newBuilder().build();
    }

    private Value handleCreateProhibitionCmd(PDPTx pdpTx, PAP pap, CreateProhibitionCmd cmd) throws PMException {
        ProhibitionSubject subject = switch (cmd.getSubjectCase()) {
            case NODE -> new ProhibitionSubject(ProtoUtil.resolveNodeRefId(pap, cmd.getNode()));
            case PROCESS -> new ProhibitionSubject(cmd.getProcess());
            case SUBJECT_NOT_SET -> throw new PMException("subject not set");
        };

        List<ContainerCondition> containerConditions = new ArrayList<>();
        for (CreateProhibitionCmd.ContainerCondition ccProto : cmd.getContainerConditionsList()) {
            containerConditions.add(new ContainerCondition(
                    ProtoUtil.resolveNodeRefId(pap, ccProto.getContainer()),
                    ccProto.getComplement()
            ));
        }

        pdpTx.modify().prohibitions().createProhibition(
                cmd.getName(),
                subject,
                new AccessRightSet(cmd.getArsetList()),
                cmd.getIntersection(),
                containerConditions
        );

        return Value.newBuilder().build();
    }

    private Value handleDeleteProhibitionCmd(PDPTx pdpTx, DeleteProhibitionCmd cmd) throws PMException {
        pdpTx.modify().prohibitions().deleteProhibition(cmd.getName());
        return Value.newBuilder().build();
    }

    private Value handleDeleteObligationCmd(PDPTx pdpTx, DeleteObligationCmd cmd) throws PMException {
        pdpTx.modify().obligations().deleteObligation(cmd.getName());
        return Value.newBuilder().build();
    }

    private Value handleDeleteOperationCmd(PDPTx pdpTx, DeleteOperationCmd cmd) throws PMException {
        pdpTx.modify().operations().deleteOperation(cmd.getName());
        return Value.newBuilder().build();
    }

    private Value handleSetResourceOperationsCmd(PDPTx pdpTx, SetResourceAccessRightsCmd cmd) throws PMException {
        pdpTx.modify().operations().setResourceAccessRights(
                new AccessRightSet(cmd.getAccessRightsList())
        );
        return Value.newBuilder().build();
    }

    private Value handleExecutePmlCmd(PDPTx pdpTx, ExecutePMLCmd cmd) throws PMException {
        Object ret = pdpTx.executePML(cmd.getPml());
        return objectToValue(ret);
    }

    /**
     * Converts a generic Java Object into a Protobuf Value message.
     * Supported types: Integer, Long, Boolean, String, Iterable (List/Set), Map<String, Object>.
     *
     * @param o The object to convert.
     * @return The constructed Protobuf Value.
     * @throws IllegalArgumentException if the type is not supported.
     */
    public static Value objectToValue(Object o) {
        Value.Builder builder = Value.newBuilder();

	    switch (o) {
		    case null -> {
			    return builder.build();
		    }
		    case Boolean b -> {
			    return builder.setBoolValue(b).build();
		    }
		    case Long l -> {
			    return builder.setInt64Value(l).build();
		    }
		    case String s -> {
			    return builder.setStringValue(s).build();
		    }
            case List<?> objects -> {
			    ValueList.Builder listBuilder = ValueList.newBuilder();
			    for (Object item : objects) {
				    listBuilder.addValues(objectToValue(item));
			    }
			    return builder.setListValue(listBuilder).build();
		    }
		    case Map<?, ?> map -> {
			    ValueMap.Builder mapBuilder = ValueMap.newBuilder();
			    for (Map.Entry<?, ?> entry : map.entrySet()) {
				    String key = String.valueOf(entry.getKey());
				    mapBuilder.putValues(key, objectToValue(entry.getValue()));
			    }
			    return builder.setMapValue(mapBuilder).build();
		    }
		    default -> {
		    }
	    }

	    throw new IllegalArgumentException("Unsupported type for Value conversion: " + o.getClass().getName());
    }
}
