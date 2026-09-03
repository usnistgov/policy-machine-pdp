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
