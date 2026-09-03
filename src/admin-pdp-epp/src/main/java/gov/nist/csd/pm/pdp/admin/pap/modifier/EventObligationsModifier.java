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
import gov.nist.ngac.pm.core.pap.modification.ObligationsModifier;
import gov.nist.ngac.pm.core.pap.obligation.Obligation;
import gov.nist.ngac.pm.core.pap.pml.statement.operation.CreateObligationStatement;
import gov.nist.ngac.pm.core.pap.query.model.context.NodeUserContext;
import gov.nist.ngac.pm.core.pap.store.PolicyStore;
import gov.nist.csd.pm.pdp.proto.event.ObligationCreated;
import gov.nist.csd.pm.pdp.proto.event.ObligationDeleted;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;

import java.util.List;

public class EventObligationsModifier extends ObligationsModifier {

    private final List<PMEvent> events;

    public EventObligationsModifier(List<PMEvent> events, PolicyStore store) {
        super(store);

        this.events = events;
    }

    @Override
    public void createObligation(Obligation obligation) throws PMException {
        NodeUserContext author = obligation.getAuthor();
        author.resolveNodeIds(policyStore.graph());

        String pml = CreateObligationStatement.fromObligation(obligation)
                .toFormattedString(0);

        ObligationCreated.Builder builder = ObligationCreated.newBuilder()
                .setPml(pml);
        if (author.getName() != null) {
            builder.setAuthorName(author.getName());
        } else {
            builder.setAuthorId(author.getId());
        }

        PMEvent event = PMEvent.newBuilder()
                .setObligationCreated(builder)
                .build();
        events.add(event);

        super.createObligation(obligation);
    }

    @Override
    public void deleteObligation(String name) throws PMException {
        PMEvent event = PMEvent.newBuilder()
            .setObligationDeleted(
                    ObligationDeleted.newBuilder()
                    .setName(name)
            )
            .build();
        events.add(event);

        super.deleteObligation(name);
    }
} 