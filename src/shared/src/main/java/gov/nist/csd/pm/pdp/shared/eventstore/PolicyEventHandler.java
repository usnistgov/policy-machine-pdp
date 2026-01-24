package gov.nist.csd.pm.pdp.shared.eventstore;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.node.NodeType;
import gov.nist.csd.pm.core.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.core.common.prohibition.ContainerCondition;
import gov.nist.csd.pm.core.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pap.store.PolicyStore;

import java.util.*;

import gov.nist.csd.pm.pdp.proto.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolicyEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(PolicyEventHandler.class);

    private final PAP pap;

    public PolicyEventHandler(PAP pap) {
        this.pap = pap;
    }

    public synchronized void handleEvents(Iterable<PMEvent> events) throws PMException {
        pap.beginTx();

        PolicyStore policyStore = pap.policyStore();
        for (PMEvent e : events) {
            handleEvent(e, policyStore);
        }

        pap.commit();
    }

    public synchronized void handleEvent(PMEvent event) throws PMException {
        pap.beginTx();

        PolicyStore policyStore = pap.policyStore();
        handleEvent(event, policyStore);

        pap.commit();
    }

    private void handleEvent(PMEvent pmEvent, PolicyStore policyStore) throws PMException {
        logger.info("Handling event {}", pmEvent);
        switch (pmEvent.getEventCase()) {
            case ASSIGNMENT_CREATED -> handleAssignmentCreated(pmEvent.getAssignmentCreated(), policyStore);
            case ASSOCIATION_CREATED -> handleAssociationCreated(pmEvent.getAssociationCreated(), policyStore);
            case POLICY_CLASS_CREATED -> handlePolicyClassCreated(pmEvent.getPolicyClassCreated(), policyStore);
            case USER_ATTRIBUTE_CREATED -> handleUserAttributeCreated(pmEvent.getUserAttributeCreated(), policyStore);
            case OBJECT_ATTRIBUTE_CREATED -> handleObjectAttributeCreated(pmEvent.getObjectAttributeCreated(), policyStore);
            case USER_CREATED -> handleUserCreated(pmEvent.getUserCreated(), policyStore);
            case OBJECT_CREATED -> handleObjectCreated(pmEvent.getObjectCreated(), policyStore);
            case ASSIGNMENT_DELETED -> handleAssignmentDeleted(pmEvent.getAssignmentDeleted(), policyStore);
            case ASSOCIATION_DELETED -> handleAssociationDeleted(pmEvent.getAssociationDeleted(), policyStore);
            case NODE_DELETED -> handleNodeDeleted(pmEvent.getNodeDeleted(), policyStore);
            case NODE_PROPERTIES_SET -> handleNodePropertiesSet(pmEvent.getNodePropertiesSet(), policyStore);
            case PROHIBITION_CREATED -> handleProhibitionCreated(pmEvent.getProhibitionCreated(), policyStore);
            case PROHIBITION_DELETED -> handleProhibitionDeleted(pmEvent.getProhibitionDeleted(), policyStore);
            case OBLIGATION_CREATED -> handleObligationCreated(pmEvent.getObligationCreated());
            case OBLIGATION_DELETED -> handleObligationDeleted(pmEvent.getObligationDeleted(), policyStore);
            case OPERATION_CREATED -> handleOperationCreated(pmEvent.getOperationCreated());
            case OPERATION_DELETED -> handleOperationDeleted(pmEvent.getOperationDeleted(), policyStore);
            case RESOURCE_ACCESS_RIGHTS_SET -> handleResourceAccessRightsSet(pmEvent.getResourceAccessRightsSet(), policyStore);
            case EVENT_NOT_SET -> logger.debug("event not set for {}", pmEvent);
        }
    }

    private void handleAssignmentCreated(AssignmentCreated assignmentCreated, PolicyStore policyStore) throws PMException {
        createAssignments(assignmentCreated.getAscendant(), assignmentCreated.getDescendantsList(), policyStore);
    }

    private void createAssignments(long ascendant, List<Long> descendants, PolicyStore policyStore) throws PMException {
        for (long descendant : descendants) {
            policyStore.graph().createAssignment(ascendant, descendant);
        }
    }

    private void handleAssociationCreated(AssociationCreated associationCreated, PolicyStore policyStore) throws PMException {
        policyStore.graph().createAssociation(associationCreated.getUa(), associationCreated.getTarget(),
                                              new AccessRightSet(associationCreated.getArsetList()));
    }

    private void handlePolicyClassCreated(PolicyClassCreated policyClassCreated, PolicyStore policyStore) throws PMException {
        policyStore.graph().createNode(policyClassCreated.getId(), policyClassCreated.getName(), NodeType.PC);
    }

    private void handleUserAttributeCreated(UserAttributeCreated userAttributeCreated, PolicyStore policyStore) throws PMException {
        policyStore.graph().createNode(userAttributeCreated.getId(), userAttributeCreated.getName(), NodeType.UA);
        createAssignments(userAttributeCreated.getId(), userAttributeCreated.getDescendantsList(), policyStore);
    }

    private void handleObjectAttributeCreated(ObjectAttributeCreated objectAttributeCreated, PolicyStore policyStore) throws PMException {
        policyStore.graph().createNode(objectAttributeCreated.getId(), objectAttributeCreated.getName(), NodeType.OA);
        createAssignments(objectAttributeCreated.getId(), objectAttributeCreated.getDescendantsList(), policyStore);
    }

    private void handleUserCreated(UserCreated userCreated, PolicyStore policyStore) throws PMException {
        policyStore.graph().createNode(userCreated.getId(), userCreated.getName(), NodeType.U);
        createAssignments(userCreated.getId(), userCreated.getDescendantsList(), policyStore);
    }

    private void handleObjectCreated(ObjectCreated objectCreated, PolicyStore policyStore) throws PMException {
        policyStore.graph().createNode(objectCreated.getId(), objectCreated.getName(), NodeType.O);
        createAssignments(objectCreated.getId(), objectCreated.getDescendantsList(), policyStore);
    }

    private void handleAssignmentDeleted(AssignmentDeleted assignmentDeleted, PolicyStore policyStore) throws PMException {
        for (long descendant : assignmentDeleted.getDescendantsList()) {
            policyStore.graph().deleteAssignment(assignmentDeleted.getAscendant(), descendant);
        }
    }

    private void handleAssociationDeleted(AssociationDeleted associationDeleted, PolicyStore policyStore) throws PMException {
        policyStore.graph().deleteAssociation(associationDeleted.getUa(), associationDeleted.getTarget());
    }

    private void handleNodeDeleted(NodeDeleted nodeDeleted, PolicyStore policyStore) throws PMException {
        policyStore.graph().deleteNode(nodeDeleted.getId());
    }

    private void handleNodePropertiesSet(NodePropertiesSet nodePropertiesSet, PolicyStore policyStore) throws PMException {
        policyStore.graph().setNodeProperties(nodePropertiesSet.getId(), nodePropertiesSet.getPropertiesMap());
    }

    private void handleObligationCreated(ObligationCreated obligationCreated) throws PMException {
        pap.executePML(new UserContext(obligationCreated.getAuthor()), obligationCreated.getPml());
    }

    private void handleObligationDeleted(ObligationDeleted obligationDeleted, PolicyStore policyStore) throws PMException {
        policyStore.obligations().deleteObligation(obligationDeleted.getName());
    }

    private void handleProhibitionCreated(ProhibitionCreated prohibitionCreated, PolicyStore policyStore) throws PMException {
        Map<Long, Boolean> containerConditionsMap = prohibitionCreated.getContainerConditionsMap();
        List<ContainerCondition> containerConditions = new ArrayList<>();
        for (Map.Entry<Long, Boolean> entry : containerConditionsMap.entrySet()) {
            containerConditions.add(new ContainerCondition(entry.getKey(), entry.getValue()));
        }

        ProhibitionSubject prohibitionSubject = (prohibitionCreated.getSubjectCase() == ProhibitionCreated.SubjectCase.NODE)
                ? new ProhibitionSubject(prohibitionCreated.getNode())
                : new ProhibitionSubject(prohibitionCreated.getProcess());

        policyStore.prohibitions().createProhibition(
                prohibitionCreated.getName(),
                prohibitionSubject,
                new AccessRightSet(prohibitionCreated.getArsetList()),
                prohibitionCreated.getIntersection(),
                containerConditions
        );
    }

    private void handleProhibitionDeleted(ProhibitionDeleted prohibitionDeleted, PolicyStore policyStore) throws PMException {
        policyStore.prohibitions().deleteProhibition(prohibitionDeleted.getName());
    }

    private void handleResourceAccessRightsSet(ResourceAccessRightsSet resourceAccessRightsSet, PolicyStore policyStore) throws PMException {
        policyStore.operations().setResourceAccessRights(new AccessRightSet(resourceAccessRightsSet.getOperationsList()));
    }

    private void handleOperationCreated(OperationCreated operationCreated) throws PMException {
        pap.executePML(new UserContext(0), operationCreated.getPml());
    }

    private void handleOperationDeleted(OperationDeleted operationDeleted, PolicyStore policyStore) throws PMException {
        policyStore.operations().deleteOperation(operationDeleted.getName());
    }
}
