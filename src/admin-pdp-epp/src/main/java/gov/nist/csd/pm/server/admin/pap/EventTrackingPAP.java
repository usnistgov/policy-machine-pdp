package gov.nist.csd.pm.server.admin.pap;

import com.eventstore.dbclient.*;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.impl.neo4j.embedded.pap.store.Neo4jEmbeddedPolicyStore;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.function.AdminFunction;
import gov.nist.csd.pm.pap.function.op.Operation;
import gov.nist.csd.pm.pap.function.routine.Routine;
import gov.nist.csd.pm.pap.id.RandomIdGenerator;
import gov.nist.csd.pm.pap.query.AccessQuerier;
import gov.nist.csd.pm.pap.query.GraphQuerier;
import gov.nist.csd.pm.pap.query.ObligationsQuerier;
import gov.nist.csd.pm.pap.query.OperationsQuerier;
import gov.nist.csd.pm.pap.query.PolicyQuerier;
import gov.nist.csd.pm.pap.query.ProhibitionsQuerier;
import gov.nist.csd.pm.pap.query.RoutinesQuerier;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Int;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class EventTrackingPAP extends PAP {

    private static final Logger logger = LoggerFactory.getLogger(EventTrackingPAP.class);

    public EventTrackingPAP(NoCommitNeo4jPolicyStore policyStore, List<AdminFunction<?, ?>> adminFunctions) throws PMException {
        super(
            new PolicyQuerier(
                new GraphQuerier(policyStore),
                new ProhibitionsQuerier(policyStore),
                new ObligationsQuerier(policyStore),
                new OperationsQuerier(policyStore),
                new RoutinesQuerier(policyStore),
                new AccessQuerier(policyStore)
            ),
            EventTrackingPolicyModifier.createInstance(policyStore, new RandomIdGenerator()),
            policyStore
        );

        // add plugin admin functions
        for (AdminFunction<?, ?> adminFunction : adminFunctions) {
            if (adminFunction instanceof Operation<?, ?> operation) {
                modify().operations().createAdminOperation(operation);
            } else if (adminFunction instanceof Routine<?, ?> routine) {
                modify().routines().createAdminRoutine(routine);
            }
        }
    }

    @Override
    public EventTrackingPolicyModifier modify() {
        return (EventTrackingPolicyModifier) super.modify();
    }

    public List<PMEvent> publishToEventStore(EventStoreDBClient esClient, String stream, long revision) {
        AppendToStreamOptions options;
        if (revision == 0) {
            options = AppendToStreamOptions.get()
                    .expectedRevision(ExpectedRevision.noStream());
        } else {
            options = AppendToStreamOptions.get()
                    .expectedRevision(revision);
        }

        List<PMEvent> events = modify().getEvents();
        List<EventData> eventDataList = pmEventsToEventDataList(events);

        logger.info("publishing {} events to event store ar revision {}", events.size(), revision);
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

        return events;
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
