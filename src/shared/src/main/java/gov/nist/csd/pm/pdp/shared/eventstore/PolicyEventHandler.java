package gov.nist.csd.pm.pdp.shared.eventstore;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.node.NodeType;
import gov.nist.csd.pm.core.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.core.common.prohibition.ContainerCondition;
import gov.nist.csd.pm.core.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.id.RandomIdGenerator;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pap.store.PolicyStore;

import java.util.*;

import gov.nist.csd.pm.core.pdp.bootstrap.JSONBootstrapper;
import gov.nist.csd.pm.core.pdp.bootstrap.PMLBootstrapper;
import gov.nist.csd.pm.core.pdp.bootstrap.PolicyBootstrapper;
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
            case ASSIGNMENTCREATED -> handleAssignmentCreated(pmEvent.getAssignmentCreated(), policyStore);
            case ASSOCIATIONCREATED -> handleAssociationCreated(pmEvent.getAssociationCreated(), policyStore);
            case POLICYCLASSCREATED -> handlePolicyClassCreated(pmEvent.getPolicyClassCreated(), policyStore);
            case USERATTRIBUTECREATED -> handleUserAttributeCreated(pmEvent.getUserAttributeCreated(), policyStore);
            case OBJECTATTRIBUTECREATED -> handleObjectAttributeCreated(pmEvent.getObjectAttributeCreated(), policyStore);
            case USERCREATED -> handleUserCreated(pmEvent.getUserCreated(), policyStore);
            case OBJECTCREATED -> handleObjectCreated(pmEvent.getObjectCreated(), policyStore);
            case ASSIGNMENTDELETED -> handleAssignmentDeleted(pmEvent.getAssignmentDeleted(), policyStore);
            case ASSOCIATIONDELETED -> handleAssociationDeleted(pmEvent.getAssociationDeleted(), policyStore);
            case NODEDELETED -> handleNodeDeleted(pmEvent.getNodeDeleted(), policyStore);
            case NODEPROPERTIESSET -> handleNodePropertiesSet(pmEvent.getNodePropertiesSet(), policyStore);
            case PROHIBITIONCREATED -> handleProhibitionCreated(pmEvent.getProhibitionCreated(), policyStore);
            case PROHIBITIONDELETED -> handleProhibitionDeleted(pmEvent.getProhibitionDeleted(), policyStore);
            case OBLIGATIONCREATED -> handleObligationCreated(pmEvent.getObligationCreated());
            case OBLIGATIONDELETED -> handleObligationDeleted(pmEvent.getObligationDeleted(), policyStore);
            case ADMINOPERATIONCREATED -> handleAdminOperationCreated(pmEvent.getAdminOperationCreated());
            case ADMINOPERATIONDELETED -> handleAdminOperationDeleted(pmEvent.getAdminOperationDeleted());
            case RESOURCEOPERATIONSSET -> handleResourceOperationsSet(pmEvent.getResourceOperationsSet(), policyStore);
            case ADMINROUTINECREATED -> handleAdminRoutineCreated(pmEvent.getAdminRoutineCreated());
            case ADMINROUTINEDELETED -> handleAdminRoutineDeleted(pmEvent.getAdminRoutineDeleted());
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

    private void handleResourceOperationsSet(ResourceOperationsSet resourceOperationsSet, PolicyStore policyStore) throws PMException {
        policyStore.operations().setResourceOperations(new AccessRightSet(resourceOperationsSet.getOperationsList()));
    }

    private void handleAdminOperationCreated(AdminOperationCreated adminOperationCreated) throws PMException {
        pap.executePML(new UserContext(0), adminOperationCreated.getPml());
    }

    private void handleAdminOperationDeleted(AdminOperationDeleted adminOperationDeleted) throws PMException {
        pap.modify().operations().deleteAdminOperation(adminOperationDeleted.getName());
    }

    private void handleAdminRoutineCreated(AdminRoutineCreated adminRoutineCreated) throws PMException {
        pap.executePML(new UserContext(0), adminRoutineCreated.getPml());
    }

    private void handleAdminRoutineDeleted(AdminRoutineDeleted adminRoutineDeleted) throws PMException {
        pap.modify().routines().deleteAdminRoutine(adminRoutineDeleted.getName());
    }

    static class BootstrappedRandomIdGenerator extends RandomIdGenerator {

        private final Map<String, Long> createdNodes;

        public BootstrappedRandomIdGenerator(Map<String, Long> createdNodes) {
            this.createdNodes = createdNodes;
        }

        @Override
        public long generateId(String name, NodeType type) {
            if (createdNodes.containsKey(name)) {
                return createdNodes.get(name);
            }

            return super.generateId(name, type);
        }
    }
}
