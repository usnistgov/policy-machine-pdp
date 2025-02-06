package gov.nist.csd.pm.server.admin.event;

import com.eventstore.dbclient.EventData;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.common.obligation.Obligation;
import gov.nist.csd.pm.common.obligation.Rule;
import gov.nist.csd.pm.common.op.Operation;
import gov.nist.csd.pm.common.prohibition.ContainerCondition;
import gov.nist.csd.pm.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.common.routine.Routine;
import gov.nist.csd.pm.pap.*;
import gov.nist.csd.pm.pap.pml.executable.operation.PMLStmtsOperation;
import gov.nist.csd.pm.pap.pml.executable.routine.PMLRoutine;
import gov.nist.csd.pm.pap.pml.executable.routine.PMLStmtsRoutine;
import gov.nist.csd.pm.pap.store.PolicyStore;
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
import java.util.stream.Collectors;

public class EventPolicyModifier extends PolicyModifier {

	private EventGraphModifier eventGraphModifier;
	private EventProhibitionsModifier eventProhibitionsModifier;
	private EventObligationsModifier eventObligationsModifier;
	private EventOperationsModifier eventOperationsModifier;
	private EventRoutinesModifier eventRoutinesModifier;

	private List<EventData> events;

	public EventPolicyModifier(PolicyStore store) throws PMException {
		super(store);

		this.events = new ArrayList<>();
		this.eventGraphModifier = new EventGraphModifier(events, store);
		this.eventProhibitionsModifier = new EventProhibitionsModifier(events, store);
		this.eventObligationsModifier = new EventObligationsModifier(events, store);
		this.eventOperationsModifier = new EventOperationsModifier(events, store);
		this.eventRoutinesModifier = new EventRoutinesModifier(events, store);
	}

	public List<EventData> getEvents() {
		return events;
	}

	@Override
	public GraphModifier graph() {
		return eventGraphModifier;
	}

	@Override
	public ProhibitionsModifier prohibitions() {
		return eventProhibitionsModifier;
	}

	@Override
	public ObligationsModifier obligations() {
		return eventObligationsModifier;
	}

	@Override
	public OperationsModifier operations() {
		return eventOperationsModifier;
	}

	@Override
	public RoutinesModifier routines() {
		return eventRoutinesModifier;
	}

	static class EventGraphModifier extends GraphModifier {

		private List<EventData> events;

		public EventGraphModifier(List<EventData> events, PolicyStore store) {
			super(store);

			this.events = events;
		}

		@Override
		public String createPolicyClass(String name) throws PMException {
			byte[] bytes = CreatePolicyClassOp.newBuilder()
					.setName(name)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("CreatePolicyClassOp", bytes).build());

			return super.createPolicyClass(name);
		}

		@Override
		public String createUserAttribute(String name, Collection<String> assignments) throws PMException {
			byte[] bytes = CreateUserAttributeOp.newBuilder()
					.setName(name)
					.addAllDescendants(assignments)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("CreateUserAttributeOp", bytes).build());

			return super.createUserAttribute(name, assignments);
		}

		@Override
		public String createObjectAttribute(String name, Collection<String> assignments) throws PMException {
			byte[] bytes = CreateObjectAttributeOp.newBuilder()
					.setName(name)
					.addAllDescendants(assignments)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("CreateObjectAttributeOp", bytes).build());

			return super.createObjectAttribute(name, assignments);
		}

		@Override
		public String createObject(String name, Collection<String> assignments) throws PMException {
			byte[] bytes = CreateObjectOp.newBuilder()
					.setName(name)
					.addAllDescendants(assignments)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("CreateObjectOp", bytes).build());

			return super.createObject(name, assignments);
		}

		@Override
		public String createUser(String name, Collection<String> assignments) throws PMException {
			byte[] bytes = CreateUserOp.newBuilder()
					.setName(name)
					.addAllDescendants(assignments)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("CreateUserOp", bytes).build());

			return super.createUser(name, assignments);
		}

		@Override
		public void setNodeProperties(String name, Map<String, String> properties) throws PMException {
			byte[] bytes = SetNodePropertiesOp.newBuilder()
					.setName(name)
					.putAllProperties(properties)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("SetNodePropertiesOp", bytes).build());

			super.setNodeProperties(name, properties);
		}

		@Override
		public void deleteNode(String name) throws PMException {
			byte[] bytes = DeleteNodeOp.newBuilder()
					.setName(name)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("DeleteNodeOp", bytes).build());

			super.deleteNode(name);
		}

		@Override
		public void assign(String ascendant, Collection<String> descendants) throws PMException {
			byte[] bytes = AssignOp.newBuilder()
					.setAscendant(ascendant)
					.addAllDescendants(descendants)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("AssignOp", bytes).build());

			super.assign(ascendant, descendants);
		}

		@Override
		public void deassign(String ascendant, Collection<String> descendants) throws PMException {
			byte[] bytes = DeassignOp.newBuilder()
					.setAscendant(ascendant)
					.addAllDescendants(descendants)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("DeassignOp", bytes).build());

			super.deassign(ascendant, descendants);
		}

		@Override
		public void associate(String ua, String target, AccessRightSet accessRights) throws PMException {
			byte[] bytes = AssociateOp.newBuilder()
					.setUa(ua)
					.setTarget(target)
					.addAllArset(accessRights)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("AssociateOp", bytes).build());

			super.associate(ua, target, accessRights);
		}

		@Override
		public void dissociate(String ua, String target) throws PMException {
			byte[] bytes = DissociateOp.newBuilder()
					.setUa(ua)
					.setTarget(target)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("DissociateOp", bytes).build());

			super.dissociate(ua, target);
		}
	}

	static class EventProhibitionsModifier extends ProhibitionsModifier {

		private List<EventData> events;

		public EventProhibitionsModifier(List<EventData> events, PolicyStore store) {
			super(store);

			this.events = events;
		}

		@Override
		public void createProhibition(String name, ProhibitionSubject subject, AccessRightSet accessRightSet, boolean intersection, Collection<ContainerCondition> containerConditions) throws PMException {
			byte[] bytes = CreateProhibitionOp.newBuilder()
					.setName(name)
					.setSubject(subject.getName())
					.setSubjectType(subject.getType().toString())
					.addAllArset(accessRightSet)
					.setIntersection(intersection)
					.putAllContainerConditions(
							containerConditions
									.stream()
									.collect(Collectors.toMap(ContainerCondition::getName, ContainerCondition::isComplement))
					)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("CreateProhibitionOp", bytes).build());

			super.createProhibition(name, subject, accessRightSet, intersection, containerConditions);
		}

		@Override
		public void deleteProhibition(String name) throws PMException {
			byte[] bytes = DeleteProhibitionOp.newBuilder()
					.setName(name)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("DeleteProhibitionOp", bytes).build());

			super.deleteProhibition(name);
		}
	}

	static class EventObligationsModifier extends ObligationsModifier {

		private List<EventData> events;

		public EventObligationsModifier(List<EventData> events, PolicyStore store) {
			super(store);

			this.events = events;
		}

		@Override
		public void createObligation(String author, String name, List<Rule> rules) throws PMException {
			byte[] bytes = CreateObligationOp.newBuilder()
					.setAuthor(author)
					.setName(name)
					.setPml(new Obligation(author, name, rules).toString())
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("CreateObligationOp", bytes).build());

			super.createObligation(author, name, rules);
		}

		@Override
		public void deleteObligation(String name) throws PMException {
			byte[] bytes = DeleteObligationOp.newBuilder()
					.setName(name)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("DeleteObligationOp", bytes).build());

			super.deleteObligation(name);
		}
	}

	static class EventOperationsModifier extends OperationsModifier {

		private List<EventData> events;

		public EventOperationsModifier(List<EventData> events, PolicyStore store) throws PMException {
			super(store);

			this.events = events;
		}

		@Override
		public void setResourceOperations(AccessRightSet resourceOperations) throws PMException {
			byte[] bytes = SetResourceOperationsOp.newBuilder()
					.addAllOperations(resourceOperations)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("SetResourceOperationsOp", bytes).build());

			super.setResourceOperations(resourceOperations);
		}

		@Override
		public void createAdminOperation(Operation<?> operation) throws PMException {
			if (!(operation instanceof PMLStmtsOperation pmlStmtsRoutine)) {
				throw new PMException("only PML operations are supported");
			}

			byte[] bytes = CreateAdminOperationOp.newBuilder()
					.setPml(pmlStmtsRoutine.toFormattedString(0))
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("CreateAdminOperationOp", bytes).build());

			super.createAdminOperation(operation);
		}

		@Override
		public void deleteAdminOperation(String operation) throws PMException {
			byte[] bytes = DeleteAdminOperationOp.newBuilder()
					.setName(operation)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("DeleteAdminOperationOp", bytes).build());

			super.deleteAdminOperation(operation);
		}
	}

	static class EventRoutinesModifier extends RoutinesModifier {

		private List<EventData> events;

		public EventRoutinesModifier(List<EventData> events, PolicyStore store) {
			super(store);

			this.events = events;
		}

		@Override
		public void createAdminRoutine(Routine<?> routine) throws PMException {
			if (!(routine instanceof PMLStmtsRoutine pmlStmtsRoutine)) {
				throw new PMException("only PML routines are supported");
			}

			byte[] bytes = CreateAdminRoutineOp.newBuilder()
					.setPml(pmlStmtsRoutine.toFormattedString(0))
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("CreateAdminRoutineOp", bytes).build());

			super.createAdminRoutine(routine);
		}

		@Override
		public void deleteAdminRoutine(String name) throws PMException {
			byte[] bytes = DeleteAdminRoutineOp.newBuilder()
					.setName(name)
					.build()
					.toByteArray();
			events.add(EventData.builderAsBinary("DeleteAdminRoutineOp", bytes).build());

			super.deleteAdminRoutine(name);
		}
	}
}
