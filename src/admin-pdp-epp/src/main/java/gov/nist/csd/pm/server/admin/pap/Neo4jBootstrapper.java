package gov.nist.csd.pm.server.admin.pap;

import com.eventstore.dbclient.*;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.impl.neo4j.embedded.pap.store.Neo4jEmbeddedPolicyStore;
import gov.nist.csd.pm.pap.function.AdminFunction;
import gov.nist.csd.pm.pdp.PDP;
import gov.nist.csd.pm.pdp.bootstrap.JSONBootstrapper;
import gov.nist.csd.pm.pdp.bootstrap.PMLBootstrapper;
import gov.nist.csd.pm.pdp.bootstrap.PolicyBootstrapper;
import gov.nist.csd.pm.server.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.server.admin.config.ShutdownService;
import gov.nist.csd.pm.server.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.server.shared.eventstore.EventStoreDBConfig;
import gov.nist.csd.pm.server.shared.eventstore.EventStoreConnectionManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import org.neo4j.graphdb.GraphDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component(value = "Neo4jBootstrapper")
public class Neo4jBootstrapper {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jBootstrapper.class);

    private final AdminPDPConfig adminPDPConfig;
    private final EventStoreDBConfig eventStoreDBConfig;
    private final EventStoreConnectionManager eventStoreConnectionManager;
    private final NoCommitNeo4jPolicyStore policyStore;
    private final List<AdminFunction<?, ?>> adminFunctions;

    public Neo4jBootstrapper(AdminPDPConfig adminPDPConfig,
                             EventStoreDBConfig eventStoreDBConfig,
                             EventStoreConnectionManager eventStoreConnectionManager,
                             NoCommitNeo4jPolicyStore policyStore,
                             List<AdminFunction<?, ?>> adminFunctions) {
        this.adminPDPConfig = adminPDPConfig;
        this.eventStoreDBConfig = eventStoreDBConfig;
        this.eventStoreConnectionManager = eventStoreConnectionManager;
        this.policyStore = policyStore;
        this.adminFunctions = adminFunctions;
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
        if (bootstrapFilePath == null) {
            logger.info("No bootstrap file path configured");
            throw new PMException("No bootstrap file path configured");
        }

        logger.info("bootstrapping from file {}", bootstrapFilePath);
        Path path = Paths.get(bootstrapFilePath);
        if (!Files.exists(path)) {
            logger.error("Bootstrap file not found: {}", bootstrapFilePath);
            throw new PMException("Bootstrap file not found: " + bootstrapFilePath);
        }

        String data = Files.readString(path);
        if (data.isEmpty()) {
            throw new PMException("Bootstrap file is empty: " + bootstrapFilePath);
        }

        // bootstrap file in PDP, tracking policy events
        PolicyBootstrapper policyBootstrapper = getPolicyBootstrapper(bootstrapFilePath, data);
        EventTrackingPAP eventTrackingPAP = new EventTrackingPAP(policyStore, adminFunctions);
        PDP pdp = new PDP(eventTrackingPAP);
        pdp.bootstrap(policyBootstrapper);

        // send policy updates to event store
        logger.debug("sending events from bootstrapping to event store");
        eventTrackingPAP.publishToEventStore(
            eventStoreConnectionManager.getOrInitClient(),
            eventStoreDBConfig.getEventStream(),
            0
        );
    }

    private PolicyBootstrapper getPolicyBootstrapper(String bootstrapFilePath, String data) throws PMException {
        PolicyBootstrapper policyBootstrapper;
        if (bootstrapFilePath.endsWith(".pml")) {
            String bootstrapUser = adminPDPConfig.getBootstrapUser();
            if (bootstrapUser == null) {
                throw new PMException("bootstrap user is null but expected for PML bootstrapping");
            }

            policyBootstrapper = new PMLBootstrapper(new ArrayList<>(), new ArrayList<>(), bootstrapUser, data);
        } else if (bootstrapFilePath.endsWith(".json")) {
            policyBootstrapper = new JSONBootstrapper(new ArrayList<>(), new ArrayList<>(), data);
        } else {
            throw new PMException("unsupported bootstrap file type, expected .json or .pml");
        }

        return policyBootstrapper;
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
