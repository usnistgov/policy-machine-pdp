package gov.nist.csd.pm.pdp.admin.pap;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.modification.ObligationsModifier;
import gov.nist.csd.pm.core.pap.obligation.Obligation;
import gov.nist.csd.pm.core.pap.obligation.Rule;
import gov.nist.csd.pm.core.pap.store.PolicyStore;
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
    public void createObligation(long author, String name, List<Rule> rules) throws
                                                                             PMException {
        PMEvent event = PMEvent.newBuilder()
            .setObligationCreated(
                    ObligationCreated.newBuilder()
                    .setAuthor(author)
                    .setPml(new Obligation(author, name, rules).toString())
            )
            .build();
        events.add(event);

        super.createObligation(author, name, rules);
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