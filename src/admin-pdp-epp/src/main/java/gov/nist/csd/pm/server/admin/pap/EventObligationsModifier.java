package gov.nist.csd.pm.server.admin.pap;

import com.eventstore.dbclient.EventData;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.pap.modification.ObligationsModifier;
import gov.nist.csd.pm.pap.obligation.Obligation;
import gov.nist.csd.pm.pap.obligation.Rule;
import gov.nist.csd.pm.pap.store.PolicyStore;
import gov.nist.csd.pm.proto.event.PMEvent;
import gov.nist.csd.pm.proto.obligation.ObligationCreated;
import gov.nist.csd.pm.proto.obligation.ObligationDeleted;
import java.util.List;

public class EventObligationsModifier extends ObligationsModifier {

    private final List<EventData> events;

    public EventObligationsModifier(List<EventData> events, PolicyStore store) {
        super(store);

        this.events = events;
    }

    @Override
    public void createObligation(long author, String name, List<Rule> rules) throws
                                                                             PMException {
        byte[] bytes = PMEvent.newBuilder()
            .setObligationCreated(
                ObligationCreated.newBuilder()
                    .setAuthor(author)
                    .setPml(new Obligation(author, name, rules).toString())
            )
            .build()
            .toByteArray();
        events.add(EventData.builderAsBinary(ObligationCreated.getDescriptor().getName(), bytes)
            .build());

        super.createObligation(author, name, rules);
    }

    @Override
    public void deleteObligation(String name) throws PMException {
        byte[] bytes = PMEvent.newBuilder()
            .setObligationDeleted(
                ObligationDeleted.newBuilder()
                    .setName(name)
            )
            .build()
            .toByteArray();
        events.add(EventData.builderAsBinary(ObligationDeleted.getDescriptor().getName(), bytes)
            .build());

        super.deleteObligation(name);
    }
} 