package gov.nist.csd.pm.pdp.admin.pap.modifier;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.core.common.prohibition.ContainerCondition;
import gov.nist.csd.pm.core.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.core.pap.modification.ProhibitionsModifier;
import gov.nist.csd.pm.core.pap.store.PolicyStore;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.proto.event.ProhibitionCreated;
import gov.nist.csd.pm.pdp.proto.event.ProhibitionDeleted;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class EventProhibitionsModifier extends ProhibitionsModifier {

    private final List<PMEvent> events;

    public EventProhibitionsModifier(List<PMEvent> events, PolicyStore store) {
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

        PMEvent event = PMEvent.newBuilder().setProhibitionCreated(builder).build();
        events.add(event);

        super.createProhibition(name, subject, accessRightSet, intersection,
            containerConditions);
    }

    @Override
    public void deleteProhibition(String name) throws PMException {
        PMEvent event = PMEvent.newBuilder()
            .setProhibitionDeleted(
                    ProhibitionDeleted.newBuilder()
                    .setName(name)
            )
            .build();
        events.add(event);

        super.deleteProhibition(name);
    }
} 