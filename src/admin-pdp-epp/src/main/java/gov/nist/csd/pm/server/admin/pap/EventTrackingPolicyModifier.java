package gov.nist.csd.pm.server.admin.pap;

import com.eventstore.dbclient.AppendToStreamOptions;
import com.eventstore.dbclient.EventData;
import com.eventstore.dbclient.EventStoreDBClient;
import gov.nist.csd.pm.pap.id.IdGenerator;
import gov.nist.csd.pm.pap.modification.PolicyModifier;
import gov.nist.csd.pm.pap.store.PolicyStore;
import java.util.ArrayList;
import java.util.List;

public class EventTrackingPolicyModifier extends PolicyModifier {

    private final List<EventData> events;

    private EventTrackingPolicyModifier(List<EventData> events,
                                        EventGraphModifier eventGraphModifier,
                                        EventProhibitionsModifier eventProhibitionsModifier,
                                        EventObligationsModifier eventObligationsModifier,
                                        EventOperationsModifier eventOperationsModifier,
                                        EventRoutinesModifier eventRoutinesModifier) {
        super(eventGraphModifier, eventProhibitionsModifier, eventObligationsModifier, eventOperationsModifier,
            eventRoutinesModifier);
        this.events = events;
    }

    public static EventTrackingPolicyModifier createInstance(PolicyStore policyStore, IdGenerator idGenerator) {
        List<EventData> events = new ArrayList<>();

        EventGraphModifier graphModifier = new EventGraphModifier(events, policyStore, idGenerator);
        EventProhibitionsModifier prohibitionsModifier = new EventProhibitionsModifier(events, policyStore);
        EventObligationsModifier obligationsModifier = new EventObligationsModifier(events, policyStore);
        EventOperationsModifier operationsModifier = new EventOperationsModifier(events, policyStore);
        EventRoutinesModifier routinesModifier = new EventRoutinesModifier(events, policyStore);

        return new EventTrackingPolicyModifier(events, graphModifier, prohibitionsModifier, obligationsModifier,
            operationsModifier, routinesModifier);
    }

    public List<EventData> getEvents() {
        return events;
    }
}
