package gov.nist.csd.pm.pdp.admin.pap.modifier;

import gov.nist.ngac.pm.core.neo4j.embedded.pap.store.Neo4jEmbeddedPolicyStore;
import gov.nist.ngac.pm.core.pap.operation.JavaOperationRegistry;
import gov.nist.ngac.pm.core.pap.id.IdGenerator;
import gov.nist.ngac.pm.core.pap.modification.PolicyModifier;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;

import java.util.ArrayList;
import java.util.List;

public class EventTrackingPolicyModifier extends PolicyModifier {

    private final List<PMEvent> events;

    private EventTrackingPolicyModifier(List<PMEvent> events,
                                        EventGraphModifier eventGraphModifier,
                                        EventProhibitionsModifier eventProhibitionsModifier,
                                        EventObligationsModifier eventObligationsModifier,
                                        EventOperationsModifier eventOperationsModifier) {
        super(eventGraphModifier, eventProhibitionsModifier, eventObligationsModifier,
              eventOperationsModifier);
        this.events = events;
    }

    public static EventTrackingPolicyModifier createInstance(Neo4jEmbeddedPolicyStore policyStore, IdGenerator idGenerator, JavaOperationRegistry pluginRegistry) {
        List<PMEvent> events = new ArrayList<>();

        EventGraphModifier graphModifier = new EventGraphModifier(events, policyStore, idGenerator);
        EventProhibitionsModifier prohibitionsModifier = new EventProhibitionsModifier(events, policyStore);
        EventObligationsModifier obligationsModifier = new EventObligationsModifier(events, policyStore);
        EventOperationsModifier operationsModifier = new EventOperationsModifier(events, policyStore, pluginRegistry);

        return new EventTrackingPolicyModifier(events, graphModifier, prohibitionsModifier, obligationsModifier,
                                               operationsModifier);
    }

    public List<PMEvent> getEvents() {
        return events;
    }
}
