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