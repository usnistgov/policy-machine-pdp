package gov.nist.csd.pm.server.admin.pap;

import com.eventstore.dbclient.EventData;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.common.prohibition.ContainerCondition;
import gov.nist.csd.pm.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.pap.modification.ProhibitionsModifier;
import gov.nist.csd.pm.pap.store.PolicyStore;
import gov.nist.csd.pm.proto.event.PMEvent;
import gov.nist.csd.pm.proto.prohibition.ProhibitionCreated;
import gov.nist.csd.pm.proto.prohibition.ProhibitionDeleted;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class EventProhibitionsModifier extends ProhibitionsModifier {

    private final List<EventData> events;

    public EventProhibitionsModifier(List<EventData> events, PolicyStore store) {
        super(store);

        this.events = events;
    }

    @Override
    public void createProhibition(String name,
                                  ProhibitionSubject subject,
                                  AccessRightSet accessRightSet,
                                  boolean intersection,
                                  Collection<ContainerCondition> containerConditions) throws
                                                                                      PMException {
        ProhibitionCreated.Builder builder = ProhibitionCreated.newBuilder()
            .setName(name)
            .addAllArset(accessRightSet)
            .setIntersection(intersection)
            .putAllContainerConditions(
                containerConditions
                    .stream()
                    .collect(Collectors.toMap(ContainerCondition::getId,
                        ContainerCondition::isComplement))
            );

        if (subject.isNode()) {
            builder.setNode(subject.getNodeId());
        } else {
            builder.setProcess(subject.getProcess());
        }

        byte[] bytes = PMEvent.newBuilder().setProhibitionCreated(builder).build()
            .toByteArray();
        events.add(
            EventData.builderAsBinary(ProhibitionCreated.getDescriptor().getName(), bytes)
                .build());

        super.createProhibition(name, subject, accessRightSet, intersection,
            containerConditions);
    }

    @Override
    public void deleteProhibition(String name) throws PMException {
        byte[] bytes = PMEvent.newBuilder()
            .setProhibitionDeleted(
                ProhibitionDeleted.newBuilder()
                    .setName(name)
            )
            .build()
            .toByteArray();
        events.add(
            EventData.builderAsBinary(ProhibitionDeleted.getDescriptor().getName(), bytes)
                .build());

        super.deleteProhibition(name);
    }
} 