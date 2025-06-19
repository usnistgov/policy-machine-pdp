package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.core.common.prohibition.ContainerCondition;
import gov.nist.csd.pm.core.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.function.AdminFunction;
import gov.nist.csd.pm.core.pap.serialization.json.JSONDeserializer;
import gov.nist.csd.pm.core.pdp.PDPTx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gov.nist.csd.pm.pdp.admin.pap.EventTrackingPAP;
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
     * @param pap
     * @param pdpTx          The transaction context
     * @param adminCommand   The command to handle
     * @throws PMException If an error occurs during command handling
     */
    public Value handleCommand(PAP pap, PDPTx pdpTx, AdminCommand adminCommand) throws PMException {
        return switch (adminCommand.getCmdCase()) {
            case CREATE_POLICY_CLASS_CMD     -> handleCreatePolicyClassCmd(pdpTx, adminCommand.getCreatePolicyClassCmd());
            case CREATE_USER_ATTRIBUTE_CMD   -> handleCreateUserAttributeCmd(pdpTx, adminCommand.getCreateUserAttributeCmd());
            case CREATE_USER_CMD             -> handleCreateUserCmd(pdpTx, adminCommand.getCreateUserCmd());
            case CREATE_OBJECT_ATTRIBUTE_CMD -> handleCreateObjectAttributeCmd(pdpTx, adminCommand.getCreateObjectAttributeCmd());
            case CREATE_OBJECT_CMD           -> handleCreateObjectCmd(pdpTx, adminCommand.getCreateObjectCmd());
            case SET_NODE_PROPERTIES_CMD     -> handleSetNodePropertiesCmd(pdpTx, adminCommand.getSetNodePropertiesCmd());
            case DELETE_NODE_CMD             -> handleDeleteNodeCmd(pdpTx, adminCommand.getDeleteNodeCmd());
            case ASSIGN_CMD                  -> handleAssignCmd(pdpTx, adminCommand.getAssignCmd());
            case DEASSIGN_CMD                -> handleDeassignCmd(pdpTx, adminCommand.getDeassignCmd());
            case ASSOCIATE_CMD               -> handleAssociateCmd(pdpTx, adminCommand.getAssociateCmd());
            case DISSOCIATE_CMD              -> handleDissociateCmd(pdpTx, adminCommand.getDissociateCmd());
            case CREATE_PROHIBITION_CMD      -> handleCreateProhibitionCmd(pdpTx, adminCommand.getCreateProhibitionCmd());
            case DELETE_PROHIBITION_CMD      -> handleDeleteProhibitionCmd(pdpTx, adminCommand.getDeleteProhibitionCmd());
            case DELETE_OBLIGATION_CMD       -> handleDeleteObligationCmd(pdpTx, adminCommand.getDeleteObligationCmd());
            case DELETE_ADMIN_OPERATION_CMD  -> handleDeleteAdminOperationCmd(pdpTx, adminCommand.getDeleteAdminOperationCmd());
            case SET_RESOURCE_OPERATIONS_CMD -> handleSetResourceOperationsCmd(pdpTx, adminCommand.getSetResourceOperationsCmd());
            case DELETE_ADMIN_ROUTINE_CMD    -> handleDeleteAdminRoutineCmd(pdpTx, adminCommand.getDeleteAdminRoutineCmd());
            case EXECUTE_PML_CMD             -> handleExecutePmlCmd(pdpTx, adminCommand.getExecutePmlCmd());
	        case DESERIALIZE_CMD             -> handleDeserializeCmd(pdpTx, adminCommand.getDeserializeCmd());
	        case GENERIC_ADMIN_CMD           -> handleGenericCmd(pap, pdpTx, adminCommand.getGenericAdminCmd());
	        case CMD_NOT_SET                 -> throw new PMException("cmd not set");
        };
    }

    private Value handleGenericCmd(PAP pap, PDPTx pdpTx, GenericAdminCmd genericAdminCmd) throws PMException {
        String opName = genericAdminCmd.getOpName();
        ValueMap args = genericAdminCmd.getArgs();

        AdminFunction<?, ?> function;
        if (pap.query().operations().getAdminOperationNames().contains(opName)) {
            function = pap.query().operations().getAdminOperation(opName);
        } else if (pap.query().routines().getAdminRoutineNames().contains(opName)) {
            function = pap.query().routines().getAdminRoutine(opName);
        } else {
            throw new PMException("unknown AdminFunction " + opName);
        }

        Object o = pdpTx.executeAdminFunction(function, toArgsMap(args));

        return objToValue(o);
    }

    private Value objToValue(Object o) {
        Value.Builder builder = Value.newBuilder();
        if (o instanceof Long l) {
            return builder.setInt64Value(l).build();
        } else if (o instanceof Boolean b) {
            return builder.setBoolValue(b).build();
        } else if (o instanceof List<?> list) {
            List<Value> values = new ArrayList<>();
            for (Object obj : list) {
                values.add(objToValue(obj));
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

                values.put((String) key, objToValue(value));
            }

            return builder.setMapValue(ValueMap.newBuilder().putAllValues(values)).build();
        }

        // set string for default case, this will also handle the string case
        return builder.setStringValue(o.toString()).build();
    }

    private Map<String, Object> toArgsMap(ValueMap args) throws PMException {
        Map<String, Object> argMap = new HashMap<>();

        for (var entry : args.getValuesMap().entrySet()) {
            String key = entry.getKey();
            Value value = entry.getValue();
            Object o = valueToObject(value);

            argMap.put(key, o);
        }

        return argMap;
    }

    private Object valueToObject(Value value) throws PMException {
        return switch (value.getDataCase()) {
            case INT64_VALUE -> value.getInt64Value();
            case STRING_VALUE -> value.getStringValue();
            case BOOL_VALUE -> value.getBoolValue();
            case LIST_VALUE -> {
                List<Object> list = new ArrayList<>();
                for (Value v : value.getListValue().getValuesList()) {
                    list.add(valueToObject(v));
                }

                yield list;
            }
            case MAP_VALUE -> {
                Map<String, Object> map = new HashMap<>();

                for (var entry : value.getMapValue().getValuesMap().entrySet()) {
                    map.put(entry.getKey(), valueToObject(entry.getValue()));
                }

                yield  map;
            }
            case DATA_NOT_SET -> throw new PMException("value data not set");
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

    private Value handleCreateUserAttributeCmd(PDPTx pdpTx, CreateUserAttributeCmd cmd) throws PMException {
        long id = pdpTx.modify().graph().createUserAttribute(
                cmd.getName(),
                cmd.getDescendantsList()
        );
        return Value.newBuilder().setInt64Value(id).build();
    }

    private Value handleCreateUserCmd(PDPTx pdpTx, CreateUserCmd cmd) throws PMException {
        long id = pdpTx.modify().graph().createUser(
                cmd.getName(),
                cmd.getDescendantsList()
        );
        return Value.newBuilder().setInt64Value(id).build();
    }

    private Value handleCreateObjectAttributeCmd(PDPTx pdpTx, CreateObjectAttributeCmd cmd) throws PMException {
        long id = pdpTx.modify().graph().createObjectAttribute(
                cmd.getName(),
                cmd.getDescendantsList()
        );
        return Value.newBuilder().setInt64Value(id).build();
    }

    private Value handleCreateObjectCmd(PDPTx pdpTx, CreateObjectCmd cmd) throws PMException {
        long id = pdpTx.modify().graph().createObject(
                cmd.getName(),
                cmd.getDescendantsList()
        );
        return Value.newBuilder().setInt64Value(id).build();
    }

    private Value handleSetNodePropertiesCmd(PDPTx pdpTx, SetNodePropertiesCmd cmd) throws PMException {
        pdpTx.modify().graph().setNodeProperties(cmd.getId(), cmd.getPropertiesMap());
        return Value.newBuilder().build();
    }

    private Value handleDeleteNodeCmd(PDPTx pdpTx, DeleteNodeCmd cmd) throws PMException {
        pdpTx.modify().graph().deleteNode(cmd.getId());
        return Value.newBuilder().build();
    }

    private Value handleAssignCmd(PDPTx pdpTx, AssignCmd cmd) throws PMException {
        pdpTx.modify().graph().assign(cmd.getAscendantId(), cmd.getDescendantIdsList());
        return Value.newBuilder().build();
    }

    private Value handleDeassignCmd(PDPTx pdpTx, DeassignCmd cmd) throws PMException {
        pdpTx.modify().graph().deassign(cmd.getAscendantId(), cmd.getDescendantIdsList());
        return Value.newBuilder().build();
    }

    private Value handleAssociateCmd(PDPTx pdpTx, AssociateCmd cmd) throws PMException {
        pdpTx.modify().graph().associate(
                cmd.getUaId(),
                cmd.getTargetId(),
                new AccessRightSet(cmd.getArsetList())
        );
        return Value.newBuilder().build();
    }

    private Value handleDissociateCmd(PDPTx pdpTx, DissociateCmd cmd) throws PMException {
        pdpTx.modify().graph().dissociate(
                cmd.getUaId(),
                cmd.getTargetId()
        );
        return Value.newBuilder().build();
    }

    private Value handleCreateProhibitionCmd(PDPTx pdpTx, CreateProhibitionCmd cmd) throws PMException {
        ProhibitionSubject subject = switch (cmd.getSubjectCase()) {
            case NODE_ID -> new ProhibitionSubject(cmd.getNodeId());
            case PROCESS -> new ProhibitionSubject(cmd.getProcess());
            case SUBJECT_NOT_SET -> throw new PMException("subject not set");
        };

        List<ContainerCondition> containerConditions = new ArrayList<>();
        for (CreateProhibitionCmd.ContainerCondition ccProto : cmd.getContainerConditionsList()) {
            containerConditions.add(new ContainerCondition(
                    ccProto.getContainerId(),
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

    private Value handleDeleteAdminOperationCmd(PDPTx pdpTx, DeleteAdminOperationCmd cmd) throws PMException {
        pdpTx.modify().operations().deleteAdminOperation(cmd.getName());
        return Value.newBuilder().build();
    }

    private Value handleSetResourceOperationsCmd(PDPTx pdpTx, SetResourceOperationsCmd cmd) throws PMException {
        pdpTx.modify().operations().setResourceOperations(
                new AccessRightSet(cmd.getOperationsList())
        );
        return Value.newBuilder().build();
    }

    private Value handleDeleteAdminRoutineCmd(PDPTx pdpTx, DeleteAdminRoutineCmd cmd) throws PMException {
        pdpTx.modify().routines().deleteAdminRoutine(cmd.getName());
        return Value.newBuilder().build();
    }

    private Value handleExecutePmlCmd(PDPTx pdpTx, ExecutePMLCmd cmd) throws PMException {
        pdpTx.executePML(cmd.getPml());
        return Value.newBuilder().build();
    }
}
