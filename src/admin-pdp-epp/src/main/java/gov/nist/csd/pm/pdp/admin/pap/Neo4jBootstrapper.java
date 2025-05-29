package gov.nist.csd.pm.pdp.admin.pap;

import com.eventstore.dbclient.*;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.node.Node;
import gov.nist.csd.pm.core.common.graph.node.NodeType;
import gov.nist.csd.pm.core.pap.function.AdminFunction;
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
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component(value = "Neo4jBootstrapper")
public class Neo4jBootstrapper {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jBootstrapper.class);

    private final AdminPDPConfig adminPDPConfig;
    private final EventStoreDBConfig eventStoreDBConfig;
    private final EventStoreConnectionManager eventStoreConnectionManager;
    private final List<AdminFunction<?, ?>> adminFunctions;
    private final GraphDatabaseService graphDb;

    public Neo4jBootstrapper(AdminPDPConfig adminPDPConfig,
                             EventStoreDBConfig eventStoreDBConfig,
                             EventStoreConnectionManager eventStoreConnectionManager,
                             GraphDatabaseService graphDb,
                             List<AdminFunction<?, ?>> adminFunctions) {
        this.adminPDPConfig = adminPDPConfig;
        this.eventStoreDBConfig = eventStoreDBConfig;
        this.eventStoreConnectionManager = eventStoreConnectionManager;
        this.graphDb = graphDb;
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

        PolicyBootstrapper policyBootstrapper = getPolicyBootstrapper(bootstrapFilePath, data);
        EventTrackingPAP eventTrackingPAP = new EventTrackingPAP(new NoCommitNeo4jPolicyStore(graphDb), adminFunctions);
        eventTrackingPAP.beginTx();
        eventTrackingPAP.bootstrap(policyBootstrapper);
        publishToEventStore(eventTrackingPAP.query().graph(), policyBootstrapper, data);
        eventTrackingPAP.commit();
    }

    private void publishToEventStore(GraphQuery graphQuery, PolicyBootstrapper policyBootstrapper, String input) throws PMException, ExecutionException, InterruptedException {
        logger.debug("sending events from bootstrapping to event store");

        Collection<Node> search = graphQuery.search(NodeType.ANY, new HashMap<>());
        Map<String, Long> nodeIds = new HashMap<>();
        for (Node node : search) {
            nodeIds.put(node.getName(), node.getId());
        }

        String type;
        if (policyBootstrapper instanceof PMLBootstrapper) {
            type = "pml";
        } else {
            type = "json";
        }

        PMEvent pmEvent = PMEvent.newBuilder()
                .setBootstrapped(
                        Bootstrapped.newBuilder()
                                .setType(type)
                                .setValue(input)
                                .putAllCreatedNodes(nodeIds)
                )
                .build();
        EventData eventData = EventData.builderAsBinary(pmEvent.getDescriptorForType().getName(),
                                                        pmEvent.toByteArray()).build();

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
