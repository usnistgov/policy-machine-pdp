package gov.nist.csd.pm.pdp.admin.pap;

import com.eventstore.dbclient.*;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.operation.Operation;
import gov.nist.csd.pm.core.pdp.bootstrap.PMLBootstrapperWithSuper;
import gov.nist.csd.pm.pdp.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.pdp.shared.bootstrap.BootstrapFile;
import gov.nist.csd.pm.pdp.proto.event.JsonDeserializedEvent;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import org.neo4j.graphdb.GraphDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import gov.nist.csd.pm.pdp.shared.config.DefaultMode;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Component("policyBootstrapper")
@DefaultMode
public class Neo4jBootstrapper {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jBootstrapper.class);

    private final AdminPDPConfig adminPDPConfig;
    private final EventStoreDBConfig eventStoreDBConfig;
    private final EventStoreConnectionManager eventStoreConnectionManager;
    private final GraphDatabaseService graphDb;
    private final List<Operation<?>> pluginOps;

    public Neo4jBootstrapper(AdminPDPConfig adminPDPConfig,
                             EventStoreDBConfig eventStoreDBConfig,
                             EventStoreConnectionManager eventStoreConnectionManager,
                             GraphDatabaseService graphDb,
                             List<Operation<?>> pluginOps) {
        this.adminPDPConfig = adminPDPConfig;
        this.eventStoreDBConfig = eventStoreDBConfig;
        this.eventStoreConnectionManager = eventStoreConnectionManager;
        this.graphDb = graphDb;
        this.pluginOps = pluginOps;
    }

    @PostConstruct
    public void bootstrap() throws PMException, ExecutionException, InterruptedException, IOException {
        // check the event store stream is empty before bootstrapping
        // if events already exist - do not bootstrap
        if (eventsInStream()) {
            logger.info("events in stream, skipping bootstrapping");
            return;
        }

        String bootstrapFilePath = adminPDPConfig.getBootstrapFilePath();
        logger.info("bootstrapping from file {}", bootstrapFilePath);

        BootstrapFile bootstrapFile = BootstrapFile.load(bootstrapFilePath);
        bootstrap(bootstrapFile);
    }

    private void bootstrap(BootstrapFile bootstrapFile) throws PMException {
        NoCommitNeo4jPolicyStore noCommitNeo4jPolicyStore = new NoCommitNeo4jPolicyStore(graphDb, getClass().getClassLoader());

        // need to start a transaction so the initial policy admin verification succeeds
        noCommitNeo4jPolicyStore.beginTx();
        EventTrackingPAP eventTrackingPAP = new EventTrackingPAP(noCommitNeo4jPolicyStore, pluginOps);
        noCommitNeo4jPolicyStore.commit();

        switch (bootstrapFile.format()) {
            case PML -> {
                eventTrackingPAP.beginTx();
                eventTrackingPAP.bootstrap(new PMLBootstrapperWithSuper(bootstrapFile.data()));
                eventTrackingPAP.publishToEventStore(eventStoreConnectionManager.getOrInitClient(), eventStoreDBConfig.getEventStream(), 0);
                eventTrackingPAP.commit();
            }
            case JSON -> publishJsonBootstrapEvent(bootstrapFile.data());
        }
    }

    private void publishJsonBootstrapEvent(String data) {
        logger.info("publishing JsonDeserializedEvent to event store at revision 0");

        AppendToStreamOptions options = AppendToStreamOptions.get();
        options.expectedRevision(ExpectedRevision.noStream());

        PMEvent pmEvent = PMEvent.newBuilder()
                .setJsonDeserializedEvent(JsonDeserializedEvent.newBuilder()
                        .setJson(data)
                        .build())
                .build();
        EventData eventData = EventData.builderAsBinary(
                pmEvent.getDescriptorForType().getName(),
                pmEvent.toByteArray()
        ).build();

        try {
            eventStoreConnectionManager.getOrInitClient()
                    .appendToStream(eventStoreDBConfig.getEventStream(), options, eventData)
                    .get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof WrongExpectedVersionException we) {
                logger.error(we.getMessage());
                throw we;
            } else if (cause != null) {
                throw new RuntimeException("Unexpected error bootstrapping json", cause);
            }

            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Appending JsonDeserializedEvent to event store was interrupted", e);
        }
    }

    protected boolean eventsInStream() throws ExecutionException, InterruptedException {
        String eventStream = eventStoreDBConfig.getEventStream();
        ReadStreamOptions options = ReadStreamOptions.get()
                .maxCount(1)
                .fromStart();

        try {
            ReadResult result = eventStoreConnectionManager.getOrInitClient()
                    .readStream(eventStream, options).get();
            List<ResolvedEvent> events = result.getEvents();

            if (events.isEmpty()) {
                logger.debug("Event stream {} is empty", eventStream);
                return false;
            }

            logger.debug("Found {} events in stream {}", events.size(), eventStream);
            return true;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof StreamNotFoundException) {
                logger.debug("Event stream {} not found", eventStream);
                return false;
            }

            logger.error("Error reading event stream {}: {}", eventStream, e.getMessage(), e);
            throw e;
        }
    }
}
