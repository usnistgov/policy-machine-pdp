package gov.nist.csd.pm.server.admin.pap;

import gov.nist.csd.pm.impl.neo4j.embedded.pap.store.Neo4jEmbeddedPolicyStore;
import gov.nist.csd.pm.pap.id.IdGenerator;
import gov.nist.csd.pm.pap.modification.PolicyModifier;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;

import java.util.ArrayList;
import java.util.List;

public class EventTrackingPolicyModifier extends PolicyModifier {

    private final List<PMEvent> events;

    private EventTrackingPolicyModifier(List<PMEvent> events,
                                        EventGraphModifier eventGraphModifier,
                                        EventProhibitionsModifier eventProhibitionsModifier,
                                        EventObligationsModifier eventObligationsModifier,
                                        EventOperationsModifier eventOperationsModifier,
                                        EventRoutinesModifier eventRoutinesModifier) {
        super(eventGraphModifier, eventProhibitionsModifier, eventObligationsModifier, eventOperationsModifier,
            eventRoutinesModifier);
        this.events = events;
    }

    public static EventTrackingPolicyModifier createInstance(Neo4jEmbeddedPolicyStore policyStore, IdGenerator idGenerator) {
        List<PMEvent> events = new ArrayList<>();

        EventGraphModifier graphModifier = new EventGraphModifier(events, policyStore, idGenerator);
        EventProhibitionsModifier prohibitionsModifier = new EventProhibitionsModifier(events, policyStore);
        EventObligationsModifier obligationsModifier = new EventObligationsModifier(events, policyStore);
        EventOperationsModifier operationsModifier = new EventOperationsModifier(events, policyStore);
        EventRoutinesModifier routinesModifier = new EventRoutinesModifier(events, policyStore);

        return new EventTrackingPolicyModifier(events, graphModifier, prohibitionsModifier, obligationsModifier,
            operationsModifier, routinesModifier);
    }

    public List<PMEvent> getEvents() {
        return events;
    }
}
