package gov.nist.csd.pm.pdp.admin.pap.modifier;

import com.google.protobuf.ByteString;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.modification.ObligationsModifier;
import gov.nist.csd.pm.core.pap.obligation.Obligation;
import gov.nist.csd.pm.core.pap.obligation.event.EventPattern;
import gov.nist.csd.pm.core.pap.obligation.response.ObligationResponse;
import gov.nist.csd.pm.core.pap.store.PolicyStore;
import gov.nist.csd.pm.pdp.proto.event.ObligationCreated;
import gov.nist.csd.pm.pdp.proto.event.ObligationDeleted;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;

public class EventObligationsModifier extends ObligationsModifier {

    private final List<PMEvent> events;

    public EventObligationsModifier(List<PMEvent> events, PolicyStore store) {
        super(store);

        this.events = events;
    }

    @Override
    public void createObligation(long authorId, String name, EventPattern eventPattern,
                                 ObligationResponse response) throws PMException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(new Obligation(authorId, name, eventPattern, response));
            }

            PMEvent event = PMEvent.newBuilder()
                    .setObligationCreated(
                            ObligationCreated.newBuilder()
                                    .setAuthor(authorId)
                                    .setData(ByteString.copyFrom(baos.toByteArray()))
                    )
                    .build();
            events.add(event);
        } catch (IOException e) {
            throw new PMException("failed to serialize obligation: " + e.getMessage(), e);
        }

        super.createObligation(authorId, name, eventPattern, response);
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