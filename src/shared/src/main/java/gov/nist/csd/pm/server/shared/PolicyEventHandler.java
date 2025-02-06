package gov.nist.csd.pm.server.shared;

import com.eventstore.dbclient.RecordedEvent;
import com.eventstore.dbclient.ResolvedEvent;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.common.prohibition.ContainerCondition;
import gov.nist.csd.pm.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.query.model.context.UserContext;
import gov.nist.csd.pm.proto.graph.*;
import gov.nist.csd.pm.proto.obligation.CreateObligationOp;
import gov.nist.csd.pm.proto.obligation.DeleteObligationOp;
import gov.nist.csd.pm.proto.operation.CreateAdminOperationOp;
import gov.nist.csd.pm.proto.operation.DeleteAdminOperationOp;
import gov.nist.csd.pm.proto.operation.SetResourceOperationsOp;
import gov.nist.csd.pm.proto.prohibition.CreateProhibitionOp;
import gov.nist.csd.pm.proto.prohibition.DeleteProhibitionOp;
import gov.nist.csd.pm.proto.routine.CreateAdminRoutineOp;
import gov.nist.csd.pm.proto.routine.DeleteAdminRoutineOp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class PolicyEventHandler {

	public static void handleEvents(PAP pap, Collection<ResolvedEvent> recordedEvents) throws PMException, InvalidProtocolBufferException {
		for (ResolvedEvent event : recordedEvents) {
			handleEvent(pap, event.getOriginalEvent());
		}
	}

	private static void handleEvent(PAP pap, RecordedEvent event) throws InvalidProtocolBufferException, PMException {
		String eventType = event.getEventType();
		byte[] eventData = event.getEventData();

		switch (eventType) {
			// Graph events
			case "AssignOp" -> handleAssignOp(pap, AssignOp.parseFrom(eventData));
			case "AssociateOp" -> handleAssociateOp(pap, AssociateOp.parseFrom(eventData));
			case "CreatePolicyClassOp" -> handleCreatePolicyClassOp(pap, CreatePolicyClassOp.parseFrom(eventData));
			case "CreateUserAttributeOp" -> handleCreateUserAttributeOp(pap, CreateUserAttributeOp.parseFrom(eventData));
			case "CreateObjectAttributeOp" -> handleCreateObjectAttributeOp(pap, CreateObjectAttributeOp.parseFrom(eventData));
			case "CreateUserOp" -> handleCreateUserOp(pap, CreateUserOp.parseFrom(eventData));
			case "CreateObjectOp" -> handleCreateObjectOp(pap, CreateObjectOp.parseFrom(eventData));
			case "DeassignOp" -> handleDeassignOp(pap, DeassignOp.parseFrom(eventData));
			case "DeleteNodeOp" -> handleDeleteNodeOp(pap, DeleteNodeOp.parseFrom(eventData));
			case "SetNodePropertiesOp" -> handleSetNodePropertiesOp(pap, SetNodePropertiesOp.parseFrom(eventData));

			// Obligation events
			case "CreateObligationOp" -> handleCreateObligationOp(pap, CreateObligationOp.parseFrom(eventData));
			case "DeleteObligationOp" -> handleDeleteObligationOp(pap, DeleteObligationOp.parseFrom(eventData));

			// Prohibition events
			case "SetResourceOperationsOp" -> handleSetResourceOperationsOp(pap, SetResourceOperationsOp.parseFrom(eventData));
			case "CreateProhibitionOp" -> handleCreateProhibitionOp(pap, CreateProhibitionOp.parseFrom(eventData));
			case "DeleteProhibitionOp" -> handleDeleteProhibitionOp(pap, DeleteProhibitionOp.parseFrom(eventData));

			// Operation events
			case "CreateAdminOperationOp" -> handleCreateAdminOperationOp(pap, CreateAdminOperationOp.parseFrom(eventData));
			case "DeleteAdminOperationOp" -> handleDeleteAdminOperationOp(pap, DeleteAdminOperationOp.parseFrom(eventData));

			// Routine events
			case "CreateAdminRoutineOp" -> handleCreateAdminRoutineOp(pap, CreateAdminRoutineOp.parseFrom(eventData));
			case "DeleteAdminRoutineOp" -> handleDeleteAdminRoutineOp(pap, DeleteAdminRoutineOp.parseFrom(eventData));

			default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
		}
	}

	// Graph events
	private static void handleAssignOp(PAP pap, AssignOp assignOp) throws PMException {
		pap.modify().graph().assign(assignOp.getAscendant(), assignOp.getDescendantsList());
	}

	private static void handleAssociateOp(PAP pap, AssociateOp associateOp) throws PMException {
		pap.modify().graph().associate(associateOp.getUa(), associateOp.getTarget(), new AccessRightSet(associateOp.getArsetList()));
	}

	private static void handleCreatePolicyClassOp(PAP pap, CreatePolicyClassOp createPolicyClassOp) throws PMException {
		pap.modify().graph().createPolicyClass(createPolicyClassOp.getName());
	}

	private static void handleCreateUserAttributeOp(PAP pap, CreateUserAttributeOp createUserAttributeOp) throws PMException {
		pap.modify().graph().createUserAttribute(createUserAttributeOp.getName(), createUserAttributeOp.getDescendantsList());
	}

	private static void handleCreateObjectAttributeOp(PAP pap, CreateObjectAttributeOp createObjectAttributeOp) throws PMException {
		pap.modify().graph().createObjectAttribute(createObjectAttributeOp.getName(), createObjectAttributeOp.getDescendantsList());
	}

	private static void handleCreateUserOp(PAP pap, CreateUserOp createUserOp) throws PMException {
		pap.modify().graph().createUser(createUserOp.getName(), createUserOp.getDescendantsList());
	}

	private static void handleCreateObjectOp(PAP pap, CreateObjectOp createObjectOp) throws PMException {
		pap.modify().graph().createObject(createObjectOp.getName(), createObjectOp.getDescendantsList());
	}

	private static void handleDeassignOp(PAP pap, DeassignOp deassignOp) throws PMException {
		pap.modify().graph().deassign(deassignOp.getAscendant(), deassignOp.getDescendantsList());
	}

	private static void handleDeleteNodeOp(PAP pap, DeleteNodeOp deleteNodeOp) throws PMException {
		pap.modify().graph().deleteNode(deleteNodeOp.getName());
	}

	private static void handleSetNodePropertiesOp(PAP pap, SetNodePropertiesOp setNodePropertiesOp) throws PMException {
		pap.modify().graph().setNodeProperties(setNodePropertiesOp.getName(), setNodePropertiesOp.getPropertiesMap());
	}

	// Obligation events
	private static void handleCreateObligationOp(PAP pap, CreateObligationOp createObligationOp) throws PMException {
		// create the obligation by executing the obligation PML
		// TODO add check that the pml is only a create obligation statement
		pap.executePML(new UserContext(createObligationOp.getAuthor()), createObligationOp.getPml());
	}

	private static void handleDeleteObligationOp(PAP pap, DeleteObligationOp deleteObligationOp) throws PMException {
		pap.modify().obligations().deleteObligation(deleteObligationOp.getName());
	}

	// Prohibition events
	private static void handleSetResourceOperationsOp(PAP pap, SetResourceOperationsOp setResourceOperationsOp) throws PMException {
		pap.modify().operations().setResourceOperations(new AccessRightSet(setResourceOperationsOp.getOperationsList()));
	}

	private static void handleCreateProhibitionOp(PAP pap, CreateProhibitionOp createProhibitionOp) throws PMException {
		Map<String, Boolean> containerConditionsMap = createProhibitionOp.getContainerConditionsMap();
		List<ContainerCondition> containerConditions = new ArrayList<>();
		for (Map.Entry<String, Boolean> entry : containerConditionsMap.entrySet()) {
			containerConditions.add(new ContainerCondition(entry.getKey(), entry.getValue()));
		}

		pap.modify().prohibitions().createProhibition(
				createProhibitionOp.getName(),
				new ProhibitionSubject(
						createProhibitionOp.getName(),
						createProhibitionOp.getSubjectType()
				),
				new AccessRightSet(createProhibitionOp.getArsetList()),
				createProhibitionOp.getIntersection(),
				containerConditions
		);
	}

	private static void handleDeleteProhibitionOp(PAP pap, DeleteProhibitionOp deleteProhibitionOp) throws PMException {
		pap.modify().prohibitions().deleteProhibition(deleteProhibitionOp.getName());
	}

	// Operation events
	private static void handleCreateAdminOperationOp(PAP pap, CreateAdminOperationOp createAdminOperationOp) throws PMException {
		// TODO add check that the pml is only a create operation statement
		pap.executePML(new UserContext(""), createAdminOperationOp.getPml());
	}

	private static void handleDeleteAdminOperationOp(PAP pap, DeleteAdminOperationOp deleteAdminOperationOp) throws PMException {
		pap.modify().operations().deleteAdminOperation(deleteAdminOperationOp.getName());
	}

	// Routine events
	private static void handleCreateAdminRoutineOp(PAP pap, CreateAdminRoutineOp createAdminRoutineOp) throws PMException {
		// TODO add check that the pml is only a create routine statement
		pap.executePML(new UserContext(""), createAdminRoutineOp.getPml());
	}

	private static void handleDeleteAdminRoutineOp(PAP pap, DeleteAdminRoutineOp deleteAdminRoutineOp) throws PMException {
		pap.modify().routines().deleteAdminRoutine(deleteAdminRoutineOp.getName());
	}
}
