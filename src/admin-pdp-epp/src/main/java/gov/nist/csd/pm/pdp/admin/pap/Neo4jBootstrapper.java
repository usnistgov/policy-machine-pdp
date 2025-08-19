package gov.nist.csd.pm.pdp.admin.pap;

import com.eventstore.dbclient.*;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.node.Node;
import gov.nist.csd.pm.core.common.graph.node.NodeType;
import gov.nist.csd.pm.core.pap.query.GraphQuery;
import gov.nist.csd.pm.pdp.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.core.pdp.bootstrap.JSONBootstrapper;
import gov.nist.csd.pm.core.pdp.bootstrap.PMLBootstrapper;
import gov.nist.csd.pm.core.pdp.bootstrap.PolicyBootstrapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;

import gov.nist.csd.pm.pdp.proto.event.Bootstrapped;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import gov.nist.csd.pm.pdp.shared.plugin.PluginLoader;
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
    private final PluginLoader pluginLoader;
    private final GraphDatabaseService graphDb;

    public Neo4jBootstrapper(AdminPDPConfig adminPDPConfig,
                             EventStoreDBConfig eventStoreDBConfig,
                             EventStoreConnectionManager eventStoreConnectionManager,
                             GraphDatabaseService graphDb,
                             PluginLoader pluginLoader) {
        this.adminPDPConfig = adminPDPConfig;
        this.eventStoreDBConfig = eventStoreDBConfig;
        this.eventStoreConnectionManager = eventStoreConnectionManager;
        this.graphDb = graphDb;
        this.pluginLoader = pluginLoader;
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

        bootstrap(bootstrapFilePath, data);
    }

    private void bootstrap(String bootstrapFilePath, String data) throws PMException, ExecutionException, InterruptedException {
        NoCommitNeo4jPolicyStore noCommitNeo4jPolicyStore = new NoCommitNeo4jPolicyStore(graphDb);

        // need to start a transaction so the initial policy admin verification succeeds
        noCommitNeo4jPolicyStore.beginTx();
        EventTrackingPAP eventTrackingPAP = new EventTrackingPAP(noCommitNeo4jPolicyStore, pluginLoader);
        noCommitNeo4jPolicyStore.commit();

        PolicyBootstrapper policyBootstrapper;
        String type;
        String bootstrapUser = null;
        if (bootstrapFilePath.endsWith(".pml")) {
            bootstrapUser = adminPDPConfig.getBootstrapUser();
            if (bootstrapUser == null) {
                throw new PMException("bootstrap user is null but expected for PML bootstrapping");
            }

            policyBootstrapper = new PMLBootstrapper(pluginLoader.operationPlugins(), pluginLoader.routinePlugins(), bootstrapUser, data);
            type = "pml";
        } else if (bootstrapFilePath.endsWith(".json")) {
            policyBootstrapper = new JSONBootstrapper(data);
            type = "json";
        } else {
            throw new PMException("unsupported bootstrap file type, expected .json or .pml");
        }

        eventTrackingPAP.beginTx();
        eventTrackingPAP.bootstrap(policyBootstrapper);
        publishBootstrappedEvent(eventTrackingPAP.query().graph(), data, type, bootstrapUser);
        eventTrackingPAP.commit();
    }

    private void publishBootstrappedEvent(GraphQuery graphQuery, String input, String type, String bootstrapUser) throws PMException, ExecutionException, InterruptedException {
        logger.debug("sending bootstrapped event to event store");

        // get the ids of the nodes created during bootstrapping
        Collection<Node> search = graphQuery.search(NodeType.ANY, new HashMap<>());
        Map<String, Long> nodeIds = new HashMap<>();
        for (Node node : search) {
            nodeIds.put(node.getName(), node.getId());
        }

        // create bootstrapped event
        Bootstrapped.Builder builder = Bootstrapped.newBuilder()
                .setType(type)
                .setValue(input)
                .putAllCreatedNodes(nodeIds);
        // set bootstrap username if it is provided
        if (bootstrapUser != null) {
            builder.setBootstrapUserName(bootstrapUser);
        }

        PMEvent pmEvent = PMEvent.newBuilder()
                .setBootstrapped(builder.build())
                .build();
        EventData eventData = EventData.builderAsBinary(
                pmEvent.getDescriptorForType().getName(),
                pmEvent.toByteArray()
        ).build();

        // append event to stream with expectation of no existing stream since this is bootstrapping
        AppendToStreamOptions options = AppendToStreamOptions.get()
                .expectedRevision(ExpectedRevision.noStream());
        eventStoreConnectionManager.getOrInitClient()
                .appendToStream(
                        eventStoreDBConfig.getEventStream(),
                        options,
                        eventData
                )
                .get();
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
