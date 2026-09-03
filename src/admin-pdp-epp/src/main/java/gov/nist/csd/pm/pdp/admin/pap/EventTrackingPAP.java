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

package gov.nist.csd.pm.pdp.admin.pap;

import com.eventstore.dbclient.*;
import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.neo4j.embedded.pap.Neo4jEmbeddedPAP;
import gov.nist.ngac.pm.core.pap.id.RandomIdGenerator;
import gov.nist.ngac.pm.core.pap.operation.Operation;
import gov.nist.csd.pm.pdp.admin.pap.modifier.EventTrackingPolicyModifier;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class EventTrackingPAP extends Neo4jEmbeddedPAP {

    private static final Logger logger = LoggerFactory.getLogger(EventTrackingPAP.class);

    public EventTrackingPAP(NoCommitNeo4jPolicyStore policyStore, List<Operation<?>> plugins) throws PMException {
        super(policyStore);

        for (Operation<?> op : plugins) {
            javaOperations().register(op);
        }

        withPolicyModifier(EventTrackingPolicyModifier.createInstance(policyStore, new RandomIdGenerator(), javaOperations()));
    }

    @Override
    public EventTrackingPolicyModifier modify() {
        return (EventTrackingPolicyModifier) super.modify();
    }

    public long publishToEventStore(EventStoreDBClient esClient, String stream, long revision) {
        AppendToStreamOptions options = AppendToStreamOptions.get();

        if (revision == 0) {
            options.expectedRevision(ExpectedRevision.noStream());
        } else {
            options.expectedRevision(revision);
        }

        List<PMEvent> events = modify().getEvents();
        if (events.isEmpty()) {
            return -1;
        }

        List<EventData> eventDataList = pmEventsToEventDataList(events);

        logger.info("publishing {} events to event store at revision {}", events.size(), revision);
        try {
            esClient.appendToStream(stream, options, eventDataList.iterator())
                    .get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof WrongExpectedVersionException we) {
                logger.error(we.getMessage());
                throw we;
            } else if (cause != null) {
                throw new RuntimeException("Unexpected error", cause);
            }

            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Appending to event store was interrupted", e);
        }

        return events.size() + revision;
    }

    private List<EventData> pmEventsToEventDataList(List<PMEvent> events) {
        List<EventData> eventDataList = new ArrayList<>();
        for (PMEvent event : events) {
            EventData eventData = EventData.builderAsBinary(
                event.getDescriptorForType().getName(),
                event.toByteArray()
            ).build();

            eventDataList.add(eventData);
        }

        return eventDataList;
    }
}
