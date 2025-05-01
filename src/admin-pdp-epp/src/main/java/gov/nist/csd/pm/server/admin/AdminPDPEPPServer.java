package gov.nist.csd.pm.server.admin;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.ReadResult;
import com.eventstore.dbclient.ReadStreamOptions;
import com.eventstore.dbclient.ResolvedEvent;
import com.eventstore.dbclient.StreamNotFoundException;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.impl.neo4j.memory.pap.Neo4jMemoryPAP;
import gov.nist.csd.pm.impl.neo4j.memory.pap.store.Neo4jMemoryPolicyStore;
import gov.nist.csd.pm.pdp.PDP;
import gov.nist.csd.pm.pdp.adjudication.AdjudicationResponse;
import gov.nist.csd.pm.pdp.bootstrap.JSONBootstrapper;
import gov.nist.csd.pm.pdp.bootstrap.PMLBootstrapper;
import gov.nist.csd.pm.pdp.bootstrap.PolicyBootstrapper;
import gov.nist.csd.pm.server.admin.config.AdminPDPEPPConfig;
import gov.nist.csd.pm.server.admin.epp.EPPService;
import gov.nist.csd.pm.server.admin.pap.EventTrackingPAP;
import gov.nist.csd.pm.server.admin.pap.NoCommitNeo4jPolicyStore;
import gov.nist.csd.pm.server.admin.pdp.Adjudicator;
import gov.nist.csd.pm.server.admin.pdp.PDPService;
import gov.nist.csd.pm.server.shared.auth.UserContextInterceptor;
import gov.nist.csd.pm.server.shared.eventstore.PolicyEventSubscriber;
import gov.nist.csd.pm.server.shared.resilience.PMRetry;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class previously managed the gRPC server lifecycle and bootstrapping. It has been refactored to use Spring Boot
 * and beans. Remaining methods (bootstrapPolicy, eventsInStream, adjudicate) have been moved to dedicated components
 * (e.g., BootstrapRunner, Adjudicator). This class might be further refactored or removed.
 */
public class AdminPDPEPPServer {

    private static final Logger logger = LoggerFactory.getLogger(AdminPDPEPPServer.class);

    private AdminPDPEPPConfig config;
    private Server server;
    private GraphDatabaseService graphDb;
    private PolicyEventSubscriber policyEventSubscriber;
    private EventStoreConnectionManager esConnMgr;
    private boolean isRunning = false;
    private PDPService pdpService;
    private EPPService eppService;
    private HealthStatusManager healthStatusManager;

    public AdminPDPEPPServer() {
        this.config = new AdminPDPEPPConfig();
    }

    public AdminPDPEPPServer(AdminPDPEPPConfig config) {
        this.config = config;
    }

    public AdminPDPEPPConfig getConfig() {
        return config;
    }

    Server getServer() {
        return server;
    }

    GraphDatabaseService getGraphDb() {
        return graphDb;
    }

    PolicyEventSubscriber getPolicyEventSubscriber() {
        return policyEventSubscriber;
    }

    EventStoreConnectionManager getEsConnMgr() {
        return esConnMgr;
    }

    PDPService getPdpService() {
        return pdpService;
    }

    EPPService getEppService() {
        return eppService;
    }

    HealthStatusManager getHealthStatusManager() {
        return healthStatusManager;
    }

    public void start() throws Exception {
        initNeo4j();
        Neo4jMemoryPolicyStore neo4jMemoryPolicyStore = new Neo4jMemoryPolicyStore(graphDb);
        // TODO need more complete snapshotting approach -- resetting from scratch for now
        neo4jMemoryPolicyStore.reset();

        esConnMgr = new EventStoreConnectionManager(config);
        esConnMgr.createEventStreamIfNotExists(config.getEsdbStream());

        AtomicLong currentRevision = new AtomicLong();
        policyEventSubscriber = new PolicyEventSubscriber(
            config,
            currentRevision,
            new Neo4jMemoryPAP(neo4jMemoryPolicyStore),
            "AdminPDPEPPServer-cg",
            esConnMgr,
            new PMRetry<>("PolicyEventSubscriber")
        );
        policyEventSubscriber.startPersistentSubscription();

        bootstrapPolicy(config, currentRevision, esConnMgr.getOrInitClient(), graphDb);

        Adjudicator<AdjudicationResponse> adjudicator = new Adjudicator<>(
            config.getEsdbStream(), graphDb, esConnMgr, currentRevision);
        pdpService = new PDPService(adjudicator);
        eppService = new EPPService(adjudicator);
        healthStatusManager = initHealthStatusService();

        server = startGRPCServer(config, pdpService, eppService, healthStatusManager);
        isRunning = true;

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    public void awaitTermination() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public void shutdown() {
        if (!isRunning) {
            return;
        }

        logger.info("Shutting down server");

        if (policyEventSubscriber != null) {
            logger.info("Shutting down subscription to event store");
            policyEventSubscriber.shutdown();
        }

        if (server != null) {
            server.shutdown();
            try {
                server.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                server.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        isRunning = false;
        logger.info("Server shutdown completed");
    }

    public boolean isRunning() {
        return isRunning && server != null && !server.isShutdown();
    }

    private HealthStatusManager initHealthStatusService() {
        HealthStatusManager healthStatusManager = new HealthStatusManager();
        healthStatusManager.setStatus("admin-pdp-epp", HealthCheckResponse.ServingStatus.SERVING);
        return healthStatusManager;
    }

    private Server startGRPCServer(AdminPDPEPPConfig config,
                                   PDPService pdpService,
                                   EPPService eppService,
                                   HealthStatusManager healthStatusManager) throws
                                                                            Exception {
        Server server = ServerBuilder
            .forPort(config.getPort())
            .addService(pdpService)
            .addService(eppService)
            .addService(ProtoReflectionService.newInstance())
            .addService(healthStatusManager.getHealthService())
            .intercept(new UserContextInterceptor())
            .build();

        logger.info("Starting admin PDP/EPP server on port {}", config.getPort());
        server.start();

        return server;
    }

    private void initNeo4j() throws PMException {
        DatabaseManagementService managementService = new DatabaseManagementServiceBuilder(
            new File(config.getNeo4jDbPath()).toPath())
            .setConfig(GraphDatabaseSettings.strict_config_validation, false)
            .build();
        graphDb = managementService.database(DEFAULT_DATABASE_NAME);

        // create indexes
        Neo4jMemoryPolicyStore.createIndexes(graphDb);
    }

    private void bootstrapPolicy(AdminPDPEPPConfig config,
                                 AtomicLong currentRevision,
                                 EventStoreDBClient eventStoreDBClient,
                                 GraphDatabaseService graphdb) throws IOException,
                                                                      PMException,
                                                                      ExecutionException,
                                                                      InterruptedException {
        // check the event store stream is empty before bootstrapping
        // if events already exist - do not bootstrap
        if (eventsInStream(config, eventStoreDBClient)) {
            logger.info("events in stream, skipping bootstrapping");
            return;
        }

        String bootstrapFilePath = config.getBootstrapFilePath();
        if (bootstrapFilePath == null) {
            logger.info("No bootstrap file path configured, skipping bootstrapping.");
            return;
        }

        String bootstrapUser = config.getBootstrapUser();
        if (bootstrapUser == null) {
            throw new PMException("bootstrap user is undefined");
        }

        Path path = Paths.get(bootstrapFilePath);
        if (!Files.exists(path)) {
            logger.error("Bootstrap file not found: {}", bootstrapFilePath);
            throw new PMException("Bootstrap file not found: " + bootstrapFilePath);
        }
        String data = Files.readString(path);

        PolicyBootstrapper policyBootstrapper;
        if (bootstrapFilePath.endsWith(".pml")) {
            policyBootstrapper = new PMLBootstrapper(new ArrayList<>(), new ArrayList<>(), data);
        } else if (bootstrapFilePath.endsWith(".json")) {
            policyBootstrapper = new JSONBootstrapper(new ArrayList<>(), new ArrayList<>(), data);
        } else {
            throw new PMException("unsupported bootstrap file type, expected .json or .pml");
        }

        // This needs a PAP instance. Should use the injected bean or create one consistently.
        // Using NoCommitNeo4jPolicyStore directly might be problematic.
        // Consider creating/injecting a dedicated PAP bean for bootstrapping.
        EventTrackingPAP eventTrackingPAP = new EventTrackingPAP(new NoCommitNeo4jPolicyStore(graphdb));
        PDP pdp = new PDP(eventTrackingPAP);
        long expectedRevision = currentRevision.get(); // Use the injected AtomicLong bean

        logger.info("bootstrapping as user {} from file {}", bootstrapUser, bootstrapFilePath);
        pdp.bootstrap(bootstrapUser, policyBootstrapper);

        // send policy updates to event store
        logger.debug("sending events from bootstrapping to event store");
        eventTrackingPAP.publishToEventStore(eventStoreDBClient, config.getEsdbStream(), expectedRevision);

        // TODO support multiple bootstrap files?
    }

    protected boolean eventsInStream(AdminPDPEPPConfig config,
                                     EventStoreDBClient eventStoreDBClient) throws ExecutionException,
                                                                                   InterruptedException {
        ReadStreamOptions options = ReadStreamOptions.get()
            .maxCount(2) // use 2 since the first event could be the stream created event
            .fromStart();

        try {
            ReadResult result = eventStoreDBClient.readStream(config.getEsdbStream(), options).get();
            List<ResolvedEvent> events = result.getEvents();

            if (events.isEmpty()
                || (events.size() == 1 && events.getFirst().getOriginalEvent().getEventType()
                .equals("$_stream_created")) // Check specific event type
            ) {
                logger.debug("Event stream {} is empty or only contains creation event.", config.getEsdbStream());
                return false;
            }

            logger.debug("Found {} events in stream {}. Assuming not empty.", events.size(), config.getEsdbStream());
            return true; // Found more than just the potential creation event
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof StreamNotFoundException) {
                logger.debug("Event stream {} not found.", config.getEsdbStream());
                return false;
            }
            logger.error("Error reading event stream {}: {}", config.getEsdbStream(), e.getMessage(), e);
            throw e;
        }
    }

    public static void main(String[] args) throws Exception {
        AdminPDPEPPConfig config = new AdminPDPEPPConfig();
        AdminPDPEPPServer server = new AdminPDPEPPServer(config);
        server.start();
        server.awaitTermination();
    }
}
