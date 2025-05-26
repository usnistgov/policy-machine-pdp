package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.core.common.prohibition.ContainerCondition;
import gov.nist.csd.pm.core.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.core.pap.pml.statement.PMLStatement;
import gov.nist.csd.pm.core.pap.pml.statement.operation.CreateObligationStatement;
import gov.nist.csd.pm.core.pap.pml.statement.operation.OperationDefinitionStatement;
import gov.nist.csd.pm.core.pap.pml.statement.operation.RoutineDefinitionStatement;
import gov.nist.csd.pm.core.pdp.PDPTx;
import gov.nist.csd.pm.pdp.proto.adjudication.*;
import gov.nist.csd.pm.pdp.proto.model.ProhibitionProto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Handles the execution of administrative commands.
 */
@Component
public class CommandHandler {

    /**
     * Handles an administrative command based on its type.
     *
     * @param pdpTx The transaction context
     * @param adminCommand The command to handle
     * @param createdNodeIds Map to store created node IDs
     * @throws PMException If an error occurs during command handling
     */
    public void handleCommand(PDPTx pdpTx, AdminCommand adminCommand, Map<String, Long> createdNodeIds) throws PMException {
        switch (adminCommand.getCmdCase()) {
            case CREATE_POLICY_CLASS_CMD     -> handleCreatePolicyClassCmd(pdpTx, adminCommand.getCreatePolicyClassCmd(), createdNodeIds);
            case CREATE_USER_ATTRIBUTE_CMD   -> handleCreateUserAttributeCmd(pdpTx, adminCommand.getCreateUserAttributeCmd(), createdNodeIds);
            case CREATE_USER_CMD             -> handleCreateUserCmd(pdpTx, adminCommand.getCreateUserCmd(), createdNodeIds);
            case CREATE_OBJECT_ATTRIBUTE_CMD -> handleCreateObjectAttributeCmd(pdpTx, adminCommand.getCreateObjectAttributeCmd(), createdNodeIds);
            case CREATE_OBJECT_CMD           -> handleCreateObjectCmd(pdpTx, adminCommand.getCreateObjectCmd(), createdNodeIds);
            case SET_NODE_PROPERTIES_CMD     -> handleSetNodePropertiesCmd(pdpTx, adminCommand.getSetNodePropertiesCmd());
            case DELETE_NODE_CMD             -> handleDeleteNodeCmd(pdpTx, adminCommand.getDeleteNodeCmd());
            case ASSIGN_CMD                  -> handleAssignCmd(pdpTx, adminCommand.getAssignCmd());
            case DEASSIGN_CMD                -> handleDeassignCmd(pdpTx, adminCommand.getDeassignCmd());
            case ASSOCIATE_CMD               -> handleAssociateCmd(pdpTx, adminCommand.getAssociateCmd());
            case DISSOCIATE_CMD              -> handleDissociateCmd(pdpTx, adminCommand.getDissociateCmd());
            case CREATE_PROHIBITION_CMD      -> handleCreateProhibitionCmd(pdpTx, adminCommand.getCreateProhibitionCmd());
            case DELETE_PROHIBITION_CMD      -> handleDeleteProhibitionCmd(pdpTx, adminCommand.getDeleteProhibitionCmd());
            case CREATE_OBLIGATION_CMD       -> handleCreateObligationCmd(pdpTx, adminCommand.getCreateObligationCmd());
            case DELETE_OBLIGATION_CMD       -> handleDeleteObligationCmd(pdpTx, adminCommand.getDeleteObligationCmd());
            case CREATE_ADMIN_OPERATION_CMD  -> handleCreateAdminOperationCmd(pdpTx, adminCommand.getCreateAdminOperationCmd());
            case DELETE_ADMIN_OPERATION_CMD  -> handleDeleteAdminOperationCmd(pdpTx, adminCommand.getDeleteAdminOperationCmd());
            case SET_RESOURCE_OPERATIONS_CMD -> handleSetResourceOperationsCmd(pdpTx, adminCommand.getSetResourceOperationsCmd());
            case CREATE_ADMIN_ROUTINE_CMD    -> handleCreateAdminRoutineCmd(pdpTx, adminCommand.getCreateAdminRoutineCmd());
            case DELETE_ADMIN_ROUTINE_CMD    -> handleDeleteAdminRoutineCmd(pdpTx, adminCommand.getDeleteAdminRoutineCmd());
            case EXECUTE_PML_CMD             -> handleExecutePmlCmd(pdpTx, adminCommand.getExecutePmlCmd());
            case CMD_NOT_SET                 -> throw new PMException("cmd not set");
        }
    }

    private void handleCreatePolicyClassCmd(PDPTx pdpTx, CreatePolicyClassCmd cmd, Map<String, Long> createdNodeIds) throws PMException {
        long id = pdpTx.modify().graph().createPolicyClass(cmd.getName());
        createdNodeIds.put(cmd.getName(), id);
    }

    private void handleCreateUserAttributeCmd(PDPTx pdpTx, CreateUserAttributeCmd cmd, Map<String, Long> createdNodeIds) throws PMException {
        long id = pdpTx.modify().graph().createUserAttribute(
                cmd.getName(),
                cmd.getDescendantsList()
        );
        createdNodeIds.put(cmd.getName(), id);
    }

    private void handleCreateUserCmd(PDPTx pdpTx, CreateUserCmd cmd, Map<String, Long> createdNodeIds) throws PMException {
        long id = pdpTx.modify().graph().createUser(
                cmd.getName(),
                cmd.getDescendantsList()
        );
        createdNodeIds.put(cmd.getName(), id);
    }

    private void handleCreateObjectAttributeCmd(PDPTx pdpTx, CreateObjectAttributeCmd cmd, Map<String, Long> createdNodeIds) throws PMException {
        long id = pdpTx.modify().graph().createObjectAttribute(
                cmd.getName(),
                cmd.getDescendantsList()
        );
        createdNodeIds.put(cmd.getName(), id);
    }

    private void handleCreateObjectCmd(PDPTx pdpTx, CreateObjectCmd cmd, Map<String, Long> createdNodeIds) throws PMException {
        long id = pdpTx.modify().graph().createObject(
                cmd.getName(),
                cmd.getDescendantsList()
        );
        createdNodeIds.put(cmd.getName(), id);
    }

    private void handleSetNodePropertiesCmd(PDPTx pdpTx, SetNodePropertiesCmd cmd) throws PMException {
        pdpTx.modify().graph().setNodeProperties(cmd.getId(), cmd.getPropertiesMap());
    }

    private void handleDeleteNodeCmd(PDPTx pdpTx, DeleteNodeCmd cmd) throws PMException {
        pdpTx.modify().graph().deleteNode(cmd.getId());
    }

    private void handleAssignCmd(PDPTx pdpTx, AssignCmd cmd) throws PMException {
        pdpTx.modify().graph().assign(cmd.getAscendantId(), cmd.getDescendantIdsList());
    }

    private void handleDeassignCmd(PDPTx pdpTx, DeassignCmd cmd) throws PMException {
        pdpTx.modify().graph().deassign(cmd.getAscendantId(), cmd.getDescendantIdsList());
    }

    private void handleAssociateCmd(PDPTx pdpTx, AssociateCmd cmd) throws PMException {
        pdpTx.modify().graph().associate(
                cmd.getUaId(),
                cmd.getTargetId(),
                new AccessRightSet(cmd.getArset().getSetList())
        );
    }

    private void handleDissociateCmd(PDPTx pdpTx, DissociateCmd cmd) throws PMException {
        pdpTx.modify().graph().dissociate(
                cmd.getUaId(),
                cmd.getTargetId()
        );
    }

    private void handleCreateProhibitionCmd(PDPTx pdpTx, ProhibitionProto cmd) throws PMException {
        ProhibitionSubject subject = switch (cmd.getSubjectCase()) {
            case NODE_ID -> new ProhibitionSubject(cmd.getNodeId());
            case PROCESS -> new ProhibitionSubject(cmd.getProcess());
            case SUBJECT_NOT_SET -> throw new PMException("subject not set");
        };

        List<ContainerCondition> containerConditions = new ArrayList<>();
        for (ProhibitionProto.ContainerCondition ccProto : cmd.getContainerConditionsList()) {
            containerConditions.add(new ContainerCondition(
                    ccProto.getContainerId(),
                    ccProto.getComplement()
            ));
        }

        pdpTx.modify().prohibitions().createProhibition(
                cmd.getName(),
                subject,
                new AccessRightSet(cmd.getArset().getSetList()),
                cmd.getIntersection(),
                containerConditions
        );
    }

    private void handleDeleteProhibitionCmd(PDPTx pdpTx, DeleteProhibitionCmd cmd) throws PMException {
        pdpTx.modify().prohibitions().deleteProhibition(cmd.getName());
    }

    private void handleCreateObligationCmd(PDPTx pdpTx, CreateObligationCmd cmd) throws PMException {
        List<PMLStatement<?>> pmlStatements = pdpTx.compilePML(cmd.getPml());
        if (pmlStatements.size() != 1 || !(pmlStatements.getFirst() instanceof CreateObligationStatement)) {
            throw new PMException("only one create obligation statement allowed");
        }

        pdpTx.executePML(cmd.getPml());
    }

    private void handleDeleteObligationCmd(PDPTx pdpTx, DeleteObligationCmd cmd) throws PMException {
        pdpTx.modify().obligations().deleteObligation(cmd.getName());
    }

    private void handleCreateAdminOperationCmd(PDPTx pdpTx, CreateAdminOperationCmd cmd) throws PMException {
        List<PMLStatement<?>> pmlStatements = pdpTx.compilePML(cmd.getPml());
        if (pmlStatements.size() != 1 || !(pmlStatements.getFirst() instanceof OperationDefinitionStatement)) {
            throw new PMException("only one operation definition statement allowed");
        }

        pdpTx.executePML(cmd.getPml());
    }

    private void handleDeleteAdminOperationCmd(PDPTx pdpTx, DeleteAdminOperationCmd cmd) throws PMException {
        pdpTx.modify().operations().deleteAdminOperation(cmd.getName());
    }

    private void handleSetResourceOperationsCmd(PDPTx pdpTx, SetResourceOperationsCmd cmd) throws PMException {
        pdpTx.modify().operations().setResourceOperations(
                new AccessRightSet(cmd.getOperationsList())
        );
    }

    private void handleCreateAdminRoutineCmd(PDPTx pdpTx, CreateAdminRoutineCmd cmd) throws PMException {
        List<PMLStatement<?>> pmlStatements = pdpTx.compilePML(cmd.getPml());
        if (pmlStatements.size() != 1 || !(pmlStatements.getFirst() instanceof RoutineDefinitionStatement)) {
            throw new PMException("only one routine definition statement allowed");
        }

        pdpTx.executePML(cmd.getPml());
    }

    private void handleDeleteAdminRoutineCmd(PDPTx pdpTx, DeleteAdminRoutineCmd cmd) throws PMException {
        pdpTx.modify().routines().deleteAdminRoutine(cmd.getName());
    }

    private void handleExecutePmlCmd(PDPTx pdpTx, ExecutePMLCmd cmd) throws PMException {
        pdpTx.executePML(cmd.getPml());
    }
}
