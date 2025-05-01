package gov.nist.csd.pm.server.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gov.nist.csd.pm.impl.neo4j.memory.pap.store.Neo4jMemoryGraphStore;
import gov.nist.csd.pm.impl.neo4j.memory.pap.store.TxHandler;
import gov.nist.csd.pm.server.admin.config.AdminPDPEPPConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.health.v1.HealthGrpc;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;

public class EventStoreIT {

    private static final Network network = Network.newNetwork();
    private static final String NEO4J_DB_PATH = "/tmp/neo4j";

    public static GenericContainer<?> eventstore = new GenericContainer<>("eventstore/eventstore:latest")
        .withImagePullPolicy(PullPolicy.defaultPolicy())
        .withNetwork(network)
        .withNetworkAliases("eventstore") // Service name for discovery
        .withEnv("EVENTSTORE_HTTP_PORT", "2113")
        .withEnv("EVENTSTORE_INT_TCP_PORT", "1113")
        .withEnv("EVENTSTORE_CLUSTER_SIZE", "1")
        .withEnv("EVENTSTORE_RUN_PROJECTIONS", "All")
        .withEnv("EVENTSTORE_START_STANDARD_PROJECTIONS", "true")
        .withEnv("EVENTSTORE_INSECURE", "true")
        .withEnv("EVENTSTORE_ENABLE_ATOM_PUB_OVER_HTTP", "true")
        .withExposedPorts(2113, 1113)
        // Wait until EventStore health check endpoint is available
        .waitingFor(Wait.forHttp("/stats").forPort(2113).forStatusCode(200));

    public AdminPDPEPPConfig testConfig;
    private Thread serverThread;
    private AdminPDPEPPServer server;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        //eventstore.start();

        // clear data from neo4j
        FileUtils.deleteDirectory(new File(NEO4J_DB_PATH));

        testConfig = new AdminPDPEPPConfig(
            "pm-events-v" + UUID.randomUUID(),
            "localhost",
            2113, // eventstore.getMappedPort(2113) TODO uncomment when using testcontainers, same for below port
            "http://localhost:" + 2113 + "/health/live",
            5,
            "localhost",
            50052,
            NEO4J_DB_PATH,
            "u1",
            "./src/test/resources/bootstrap_it.pml"
        );

        server = new AdminPDPEPPServer(testConfig);
        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.start();

        while (!server.isRunning()) {
            Thread.sleep(1000);
        }
    }

    @AfterEach
    void tearDown() {
        if (serverThread != null) {
            serverThread.interrupt();
            server.shutdown();
        }

        eventstore.stop();
    }

    @Test
    void test_WhenStreamIsEmpty_Bootstrap() throws Exception {
        GraphDatabaseService graphDb = server.getGraphDb();
        Neo4jMemoryGraphStore neo4jMemoryGraphStore = new Neo4jMemoryGraphStore(new TxHandler(graphDb));
        assertTrue(neo4jMemoryGraphStore.nodeExists("pc1"));
        assertTrue(neo4jMemoryGraphStore.nodeExists("ua1"));
        assertTrue(neo4jMemoryGraphStore.nodeExists("u1"));
    }

    @Test
    void test_WhenHealthy_HealthStatusReturnsServing() {
        ManagedChannel channel = ManagedChannelBuilder
            .forAddress("localhost", testConfig.getPort())
            .usePlaintext()
            .build();

        try {
            HealthGrpc.HealthBlockingStub healthStub = HealthGrpc.newBlockingStub(channel);
            HealthCheckRequest request = HealthCheckRequest.newBuilder()
                .setService("admin-pdp-epp")
                .build();
            HealthCheckResponse response = healthStub
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .check(request);
            assertEquals(ServingStatus.SERVING, response.getStatus());

            server.getHealthStatusManager().setStatus("admin-pdp-epp", ServingStatus.NOT_SERVING);
            response = healthStub
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .check(request);
            assertEquals(ServingStatus.NOT_SERVING, response.getStatus());
        } finally {
            // Clean up the channel
            channel.shutdown();
        }
    }
}
