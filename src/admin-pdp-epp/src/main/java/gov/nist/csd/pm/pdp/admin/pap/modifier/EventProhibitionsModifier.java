package gov.nist.csd.pm.pdp.admin.pap.modifier;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.modification.ProhibitionsModifier;
import gov.nist.csd.pm.core.pap.operation.accessright.AccessRightSet;
import gov.nist.csd.pm.core.pap.store.PolicyStore;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.proto.event.ProhibitionCreated;
import gov.nist.csd.pm.pdp.proto.event.ProhibitionDeleted;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class EventProhibitionsModifier extends ProhibitionsModifier {

    private final List<PMEvent> events;

    public EventProhibitionsModifier(List<PMEvent> events, PolicyStore store) {
        super(store);

        this.events = events;
    }

    @Override
    public void createProcessProhibition(String name, long userId, String process, AccessRightSet accessRightSet,
                                         Set<Long> inclusionSet, Set<Long> exclusionSet,
                                         boolean isConjunctive) throws PMException {
        ProhibitionCreated.Builder builder = ProhibitionCreated.newBuilder()
                .setName(name)
                .setNode(userId)
                .setProcess(process)
                .addAllArset(accessRightSet)
                .setIsConjunctive(isConjunctive)
                .addAllInclusionSet(inclusionSet)
                .addAllExclusionSet(exclusionSet);

        PMEvent event = PMEvent.newBuilder().setProhibitionCreated(builder).build();
        events.add(event);

        super.createProcessProhibition(
                name,
                userId,
                process,
                accessRightSet,
                inclusionSet,
                exclusionSet,
                isConjunctive
        );
    }

    @Override
    public void createNodeProhibition(String name, long nodeId, AccessRightSet accessRightSet, Set<Long> inclusionSet,
                                      Set<Long> exclusionSet, boolean isConjunctive) throws PMException {
        ProhibitionCreated.Builder builder = ProhibitionCreated.newBuilder()
                .setName(name)
                .setNode(nodeId)
                .addAllArset(accessRightSet)
                .setIsConjunctive(isConjunctive)
                .addAllInclusionSet(inclusionSet)
                .addAllExclusionSet(exclusionSet);

        PMEvent event = PMEvent.newBuilder().setProhibitionCreated(builder).build();
        events.add(event);

        super.createNodeProhibition(name, nodeId, accessRightSet, inclusionSet, exclusionSet, isConjunctive);
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