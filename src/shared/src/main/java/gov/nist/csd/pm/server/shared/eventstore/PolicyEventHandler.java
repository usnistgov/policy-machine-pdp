package gov.nist.csd.pm.server.shared.eventstore;

import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.common.graph.node.NodeType;
import gov.nist.csd.pm.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.common.prohibition.ContainerCondition;
import gov.nist.csd.pm.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pap.store.PolicyStore;
import gov.nist.csd.pm.proto.event.PMEvent;
import gov.nist.csd.pm.proto.graph.AssignmentCreated;
import gov.nist.csd.pm.proto.graph.AssignmentDeleted;
import gov.nist.csd.pm.proto.graph.AssociationCreated;
import gov.nist.csd.pm.proto.graph.AssociationDeleted;
import gov.nist.csd.pm.proto.graph.NodeDeleted;
import gov.nist.csd.pm.proto.graph.NodePropertiesSet;
import gov.nist.csd.pm.proto.graph.ObjectAttributeCreated;
import gov.nist.csd.pm.proto.graph.ObjectCreated;
import gov.nist.csd.pm.proto.graph.PolicyClassCreated;
import gov.nist.csd.pm.proto.graph.UserAttributeCreated;
import gov.nist.csd.pm.proto.graph.UserCreated;
import gov.nist.csd.pm.proto.obligation.ObligationCreated;
import gov.nist.csd.pm.proto.obligation.ObligationDeleted;
import gov.nist.csd.pm.proto.operation.AdminOperationCreated;
import gov.nist.csd.pm.proto.operation.AdminOperationDeleted;
import gov.nist.csd.pm.proto.operation.ResourceOperationsSet;
import gov.nist.csd.pm.proto.prohibition.ProhibitionCreated;
import gov.nist.csd.pm.proto.prohibition.ProhibitionDeleted;
import gov.nist.csd.pm.proto.routine.AdminRoutineCreated;
import gov.nist.csd.pm.proto.routine.AdminRoutineDeleted;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolicyEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(PolicyEventHandler.class);

    private final PAP pap;
    private final PolicyStore policyStore;

    public PolicyEventHandler(PAP pap) {
        this.pap = pap;
        this.policyStore = pap.policyStore();
    }

    public void handleEvent(PMEvent pmEvent) throws PMException {
        logger.info("Handling event {}", pmEvent);
        synchronized (policyStore) {
            switch (pmEvent.getEventCase()) {
                // Graph events
                case ASSIGNMENTCREATED -> handleAssignmentCreated(pmEvent.getAssignmentCreated());
                case ASSOCIATIONCREATED -> handleAssociationCreated(pmEvent.getAssociationCreated());
                case POLICYCLASSCREATED -> handlePolicyClassCreated(pmEvent.getPolicyClassCreated());
                case USERATTRIBUTECREATED -> handleUserAttributeCreated(pmEvent.getUserAttributeCreated());
                case OBJECTATTRIBUTECREATED -> handleObjectAttributeCreated(pmEvent.getObjectAttributeCreated());
                case USERCREATED -> handleUserCreated(pmEvent.getUserCreated());
                case OBJECTCREATED -> handleObjectCreated(pmEvent.getObjectCreated());
                case ASSIGNMENTDELETED -> handleAssignmentDeleted(pmEvent.getAssignmentDeleted());
                case ASSOCIATIONDELETED -> handleAssociationDeleted(pmEvent.getAssociationDeleted());
                case NODEDELETED -> handleNodeDeleted(pmEvent.getNodeDeleted());
                case NODEPROPERTIESSET -> handleNodePropertiesSet(pmEvent.getNodePropertiesSet());

                // Prohibition events
                case PROHIBITIONCREATED -> handleProhibitionCreated(pmEvent.getProhibitionCreated());
                case PROHIBITIONDELETED -> handleProhibitionDeleted(pmEvent.getProhibitionDeleted());

                // Obligation events
                case OBLIGATIONCREATED -> handleObligationCreated(pmEvent.getObligationCreated());
                case OBLIGATIONDELETED -> handleObligationDeleted(pmEvent.getObligationDeleted());

                // Operation events
                case ADMINOPERATIONCREATED -> handleAdminOperationCreated(pmEvent.getAdminOperationCreated());
                case ADMINOPERATIONDELETED -> handleAdminOperationDeleted(pmEvent.getAdminOperationDeleted());
                case RESOURCEOPERATIONSSET -> handleResourceOperationsSet(pmEvent.getResourceOperationsSet());

                // Routine events
                case ADMINROUTINECREATED -> handleAdminRoutineCreated(pmEvent.getAdminRoutineCreated());
                case ADMINROUTINEDELETED -> handleAdminRoutineDeleted(pmEvent.getAdminRoutineDeleted());

                // log and ignore if event is not set
                case EVENT_NOT_SET -> {
                    logger.debug("event not set for {}", pmEvent);
                }
            }
        }
    }

    // Graph events
    private void handleAssignmentCreated(AssignmentCreated assignmentCreated) throws
                                                                              PMException {
        createAssignments(assignmentCreated.getAscendant(),
            assignmentCreated.getDescendantsList());
    }

    private void createAssignments(long ascendant, List<Long> descendants) throws
                                                                           PMException {
        System.out.println(policyStore.graph().search(NodeType.ANY, new HashMap<>()));
        for (long descendant : descendants) {
            policyStore.graph().createAssignment(ascendant, descendant);
        }
    }

    private void handleAssociationCreated(AssociationCreated associationCreated) throws
                                                                                 PMException {
        policyStore.graph()
            .createAssociation(associationCreated.getUa(), associationCreated.getTarget(),
                new AccessRightSet(associationCreated.getArsetList()));
    }

    private void handlePolicyClassCreated(PolicyClassCreated policyClassCreated) throws
                                                                                 PMException {
        policyStore.graph()
            .createNode(policyClassCreated.getId(), policyClassCreated.getName(), NodeType.PC);
    }

    private void handleUserAttributeCreated(UserAttributeCreated userAttributeCreated) throws
                                                                                       PMException {
        policyStore.graph()
            .createNode(userAttributeCreated.getId(), userAttributeCreated.getName(), NodeType.UA);

        createAssignments(userAttributeCreated.getId(),
            userAttributeCreated.getDescendantsList());
    }

    private void handleObjectAttributeCreated(ObjectAttributeCreated objectAttributeCreated) throws
                                                                                             PMException {
        policyStore.graph().createNode(objectAttributeCreated.getId(), objectAttributeCreated.getName(), NodeType.OA);

        createAssignments(objectAttributeCreated.getId(), objectAttributeCreated.getDescendantsList());
    }

    private void handleUserCreated(UserCreated userCreated) throws PMException {
        policyStore.graph()
            .createNode(userCreated.getId(), userCreated.getName(), NodeType.U);

        createAssignments(userCreated.getId(), userCreated.getDescendantsList());
    }

    private void handleObjectCreated(ObjectCreated objectCreated) throws PMException {
        policyStore.graph()
            .createNode(objectCreated.getId(), objectCreated.getName(), NodeType.O);

        createAssignments(objectCreated.getId(), objectCreated.getDescendantsList());
    }

    private void handleAssignmentDeleted(AssignmentDeleted assignmentDeleted) throws
                                                                              PMException {
        for (long descendant : assignmentDeleted.getDescendantsList()) {
            policyStore.graph()
                .createAssignment(assignmentDeleted.getAscendant(), descendant);
        }
    }

    private void handleAssociationDeleted(AssociationDeleted associationDeleted) throws
                                                                                 PMException {
        policyStore.graph()
            .deleteAssociation(associationDeleted.getUa(), associationDeleted.getTarget());
    }

    private void handleNodeDeleted(NodeDeleted nodeDeleted) throws PMException {
        policyStore.graph().deleteNode(nodeDeleted.getId());
    }

    private void handleNodePropertiesSet(NodePropertiesSet nodePropertiesSet) throws
                                                                              PMException {
        policyStore.graph()
            .setNodeProperties(nodePropertiesSet.getId(), nodePropertiesSet.getPropertiesMap());
    }

    // Obligation events
    private void handleObligationCreated(ObligationCreated obligationCreated) throws
                                                                              PMException {
        pap.executePML(new UserContext(obligationCreated.getAuthor()), obligationCreated.getPml());
    }

    private void handleObligationDeleted(ObligationDeleted obligationDeleted) throws
                                                                              PMException {
        policyStore.obligations().deleteObligation(obligationDeleted.getName());
    }

    // Prohibition events
    private void handleProhibitionCreated(ProhibitionCreated prohibitionCreated) throws
                                                                                 PMException {
        Map<Long, Boolean> containerConditionsMap = prohibitionCreated.getContainerConditionsMap();
        List<ContainerCondition> containerConditions = new ArrayList<>();
        for (Map.Entry<Long, Boolean> entry : containerConditionsMap.entrySet()) {
            containerConditions.add(new ContainerCondition(entry.getKey(), entry.getValue()));
        }

        ProhibitionSubject prohibitionSubject;
        if (prohibitionCreated.getSubjectCase() == ProhibitionCreated.SubjectCase.NODE) {
            prohibitionSubject = new ProhibitionSubject(prohibitionCreated.getNode());
        } else {
            prohibitionSubject = new ProhibitionSubject(prohibitionCreated.getProcess());
        }

        policyStore.prohibitions().createProhibition(
            prohibitionCreated.getName(),
            prohibitionSubject,
            new AccessRightSet(prohibitionCreated.getArsetList()),
            prohibitionCreated.getIntersection(),
            containerConditions
        );
    }

    private void handleProhibitionDeleted(ProhibitionDeleted prohibitionDeleted) throws
                                                                                 PMException {
        policyStore.prohibitions().deleteProhibition(prohibitionDeleted.getName());
    }

    // Operation events
    private void handleResourceOperationsSet(ResourceOperationsSet resourceOperationsSet) throws
                                                                                          PMException {
        policyStore.operations()
            .setResourceOperations(new AccessRightSet(resourceOperationsSet.getOperationsList()));
    }

    private void handleAdminOperationCreated(AdminOperationCreated adminOperationCreated) throws
                                                                                          PMException {
        pap.executePML(new UserContext(0), adminOperationCreated.getPml());
    }

    private void handleAdminOperationDeleted(AdminOperationDeleted adminOperationDeleted) throws
                                                                                          PMException {
        pap.modify().operations().deleteAdminOperation(adminOperationDeleted.getName());
    }

    // Routine events
    private void handleAdminRoutineCreated(AdminRoutineCreated adminRoutineCreated) throws
                                                                                    PMException {
        pap.executePML(new UserContext(0), adminRoutineCreated.getPml());
    }

    private void handleAdminRoutineDeleted(AdminRoutineDeleted adminRoutineDeleted) throws
                                                                                    PMException {
        pap.modify().routines().deleteAdminRoutine(adminRoutineDeleted.getName());
    }

}
