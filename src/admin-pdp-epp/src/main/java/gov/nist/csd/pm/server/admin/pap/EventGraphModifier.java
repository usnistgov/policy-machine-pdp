package gov.nist.csd.pm.server.admin.pap;

import com.eventstore.dbclient.EventData;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.pap.id.IdGenerator;
import gov.nist.csd.pm.pap.modification.GraphModifier;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class EventGraphModifier extends GraphModifier {

    private final List<EventData> events;

    public EventGraphModifier(List<EventData> events,
                              PolicyStore store,
                              IdGenerator idGenerator) {
        super(store, idGenerator);

        this.events = events;
    }

    @Override
    public long createPolicyClass(String name) throws PMException {
        long id = super.createPolicyClass(name);

        byte[] bytes = PMEvent.newBuilder()
            .setPolicyClassCreated(
                PolicyClassCreated.newBuilder()
                    .setId(id)
                    .setName(name)
            )
            .build()
            .toByteArray();
        events.add(
            EventData.builderAsBinary(PolicyClassCreated.getDescriptor().getName(), bytes)
                .build());

        return id;
    }

    @Override
    public long createUserAttribute(String name, Collection<Long> assignments) throws
                                                                               PMException {
        long id = super.createUserAttribute(name, assignments);

        byte[] bytes = PMEvent.newBuilder()
            .setUserAttributeCreated(
                UserAttributeCreated.newBuilder()
                    .setId(id)
                    .setName(name)
                    .addAllDescendants(assignments)
            )
            .build()
            .toByteArray();
        events.add(
            EventData.builderAsBinary(UserAttributeCreated.getDescriptor().getName(), bytes)
                .build());

        return id;
    }

    @Override
    public long createObjectAttribute(String name, Collection<Long> assignments) throws
                                                                                 PMException {
        long id = super.createObjectAttribute(name, assignments);

        byte[] bytes = PMEvent.newBuilder()
            .setObjectAttributeCreated(
                ObjectAttributeCreated.newBuilder()
                    .setId(id)
                    .setName(name)
                    .addAllDescendants(assignments)
            )
            .build()
            .toByteArray();
        events.add(
            EventData.builderAsBinary(ObjectAttributeCreated.getDescriptor().getName(), bytes)
                .build());

        return id;
    }

    @Override
    public long createObject(String name, Collection<Long> assignments) throws PMException {
        long id = super.createObject(name, assignments);

        byte[] bytes = PMEvent.newBuilder()
            .setObjectCreated(
                ObjectCreated.newBuilder()
                    .setId(id)
                    .setName(name)
                    .addAllDescendants(assignments)
            )
            .build()
            .toByteArray();
        events.add(
            EventData.builderAsBinary(ObjectCreated.getDescriptor().getName(), bytes).build());

        return id;
    }

    @Override
    public long createUser(String name, Collection<Long> assignments) throws PMException {
        long id = super.createUser(name, assignments);

        byte[] bytes = PMEvent.newBuilder()
            .setUserCreated(
                UserCreated.newBuilder()
                    .setId(id)
                    .setName(name)
                    .addAllDescendants(assignments)
            )
            .build()
            .toByteArray();
        events.add(
            EventData.builderAsBinary(UserCreated.getDescriptor().getName(), bytes).build());

        return id;
    }

    @Override
    public void setNodeProperties(long id, Map<String, String> properties) throws PMException {
        byte[] bytes = PMEvent.newBuilder()
            .setNodePropertiesSet(
                NodePropertiesSet.newBuilder()
                    .setId(id)
                    .putAllProperties(properties)
            )
            .build()
            .toByteArray();
        events.add(EventData.builderAsBinary(NodePropertiesSet.getDescriptor().getName(), bytes)
            .build());

        super.setNodeProperties(id, properties);
    }

    @Override
    public void deleteNode(long id) throws PMException {
        byte[] bytes = PMEvent.newBuilder()
            .setNodeDeleted(
                NodeDeleted.newBuilder()
                    .setId(id)
            )
            .build()
            .toByteArray();
        events.add(
            EventData.builderAsBinary(NodeDeleted.getDescriptor().getName(), bytes).build());

        super.deleteNode(id);
    }

    @Override
    public void assign(long ascendant, Collection<Long> descendants) throws PMException {
        byte[] bytes = PMEvent.newBuilder()
            .setAssignmentCreated(
                AssignmentCreated.newBuilder()
                    .setAscendant(ascendant)
                    .addAllDescendants(descendants)
            )
            .build()
            .toByteArray();
        events.add(EventData.builderAsBinary(AssignmentCreated.getDescriptor().getName(), bytes)
            .build());

        super.assign(ascendant, descendants);
    }

    @Override
    public void deassign(long ascendant, Collection<Long> descendants) throws PMException {
        byte[] bytes = PMEvent.newBuilder()
            .setAssignmentDeleted(
                AssignmentDeleted.newBuilder()
                    .setAscendant(ascendant)
                    .addAllDescendants(descendants)
            )
            .build()
            .toByteArray();
        events.add(EventData.builderAsBinary(AssignmentDeleted.getDescriptor().getName(), bytes)
            .build());

        super.deassign(ascendant, descendants);
    }

    @Override
    public void associate(long ua, long target, AccessRightSet accessRights) throws
                                                                             PMException {
        byte[] bytes = PMEvent.newBuilder()
            .setAssociationCreated(
                AssociationCreated.newBuilder()
                    .setUa(ua)
                    .setTarget(target)
                    .addAllArset(accessRights)
            )
            .build()
            .toByteArray();
        events.add(
            EventData.builderAsBinary(AssociationCreated.getDescriptor().getName(), bytes)
                .build());

        super.associate(ua, target, accessRights);
    }

    @Override
    public void dissociate(long ua, long target) throws PMException {
        byte[] bytes = PMEvent.newBuilder()
            .setAssociationDeleted(
                AssociationDeleted.newBuilder()
                    .setUa(ua)
                    .setTarget(target)
            )
            .build()
            .toByteArray();
        events.add(
            EventData.builderAsBinary(AssociationDeleted.getDescriptor().getName(), bytes)
                .build());

        super.dissociate(ua, target);
    }
} 