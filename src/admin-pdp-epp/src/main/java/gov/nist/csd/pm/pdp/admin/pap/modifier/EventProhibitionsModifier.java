/*
 * This Software (Policy Machine) is being made available as a public service by the
 * National Institute of Standards and Technology (NIST), an Agency of the United
 * States Department of Commerce. This software was developed in part by employees of
 * NIST and in part by NIST contractors. Copyright in portions of this software that
 * were developed by NIST contractors has been licensed or assigned to NIST. Pursuant
 * to Title 17 United States Code Section 105, works of NIST employees are not
 * subject to copyright protection in the United States. However, NIST may hold
 * international copyright in software created by its employees and domestic
 * copyright (or licensing rights) in portions of software that were assigned or
 * licensed to NIST.
 *
 * This file is part of the admin-pdp-epp module, which compiles against
 * and embeds org.neo4j:neo4j (GPLv3, Community Edition). As a combined work, this
 * module is distributed under the GNU General Public License v3.0, not the CC BY 4.0
 * license used elsewhere in this repository. See admin-pdp-epp/LICENSE for the full text.
 */

package gov.nist.csd.pm.pdp.admin.pap.modifier;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.pap.modification.ProhibitionsModifier;
import gov.nist.ngac.pm.core.pap.operation.accessright.AccessRightSet;
import gov.nist.ngac.pm.core.pap.store.PolicyStore;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.proto.event.ProhibitionCreated;
import gov.nist.csd.pm.pdp.proto.event.ProhibitionDeleted;

import java.util.List;
import java.util.Set;

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