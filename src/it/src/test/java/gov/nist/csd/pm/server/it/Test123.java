package gov.nist.csd.pm.server.it;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.MountableFile;

public class Test123 {

    // Define a network for containers to communicate
    private static final Network network = Network.newNetwork();

    // --- PLACEHOLDERS ---
    // Replace with the actual path to bootstrap.pml, ideally as a classpath resource
    // e.g., MountableFile.forClasspathResource("bootstrap.pml") if it's in src/test/resources
    private static final String BOOTSTRAP_PML_HOST_PATH = "docker/bootstrap.pml"; // Assumes relative to project root

    // Replace with the actual path to the directory containing the Dockerfile for admin-pdp-epp
    private static final String ADMIN_DOCKERFILE_PATH = "../admin-pdp-epp/"; // Adjust as needed

    // Replace with the actual path to the directory containing the Dockerfile for resource-pdp
    private static final String RESOURCE_DOCKERFILE_PATH = "../resource-pdp/"; // Adjust as needed
    // --- END PLACEHOLDERS ---

    @Container
    public static GenericContainer<?> eventstore = new GenericContainer<>("eventstore/eventstore:latest")
        .withImagePullPolicy(PullPolicy.alwaysPull()) // Corresponds to pull_policy: always
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
    // Testcontainers manages ephemeral volumes by default

    @Container
    public static GenericContainer<?> adminPdpEpp = new GenericContainer<>(
        new ImageFromDockerfile("pm.server.admin-pdp-epp-test", false) // Name the test image
            .withDockerfile(Paths.get(ADMIN_DOCKERFILE_PATH, "Dockerfile"))
        // Assumes Dockerfile is in the specified dir
    )
        .withNetwork(network)
        .withNetworkAliases("admin-pdp-epp") // Service name for discovery
        .withEnv("PM_ADMIN_PDP_HOST", "localhost")
        .withEnv("PM_ADMIN_PDP_PORT", "50052")
        .withEnv("PM_ESDB_STREAM", "pm-events-v1") // Use network alias
        .withEnv("PM_ESDB_HOST", "eventstore") // Use network alias
        .withEnv("PM_ESDB_PORT", "2113")
        .withEnv("PM_ESDB_HEALTH_CHECK_URL", "http://eventstore:2113/health/live")
        .withEnv("PM_HEALTH_CHECK_INTERVAL", "5")
        .withEnv("PM_BOOTSTRAP_FILE_PATH", "/config/bootstrap.pml")
        .withEnv("PM_BOOTSTRAP_USER", "u1")
        .withExposedPorts(50052)
        // Mount bootstrap.pml from host/classpath into the container
        .withCopyFileToContainer(MountableFile.forHostPath(BOOTSTRAP_PML_HOST_PATH), "/config/bootstrap.pml")
        .dependsOn(eventstore)
        // Wait until the gRPC port is listening
        .waitingFor(Wait.forLogMessage(".*gRPC server started on port 50052.*\\n", 1));

    public static GenericContainer<?> resourcePdp = new GenericContainer<>(
        new ImageFromDockerfile("pm.server.resource-pdp-test", false) // Name the test image
            .withDockerfile(Paths.get(RESOURCE_DOCKERFILE_PATH, "Dockerfile"))
        // Assumes Dockerfile is in the specified dir
    )
        .withNetwork(network)
        // .withNetworkAliases("resource-pdp") // Alias not strictly needed if nothing connects *to* it by name
        .withEnv("RESOURCE_GRPC_SERVER_PORT", "50051")
        .withEnv("ADMIN_GRPC_SERVER_HOST", "admin-pdp-epp") // Use network alias
        .withEnv("ADMIN_GRPC_SERVER_PORT", "50052")
        .withEnv("ESDB_HOST", "eventstore") // Use network alias
        .withEnv("ESDB_PORT", "2113")
        .withExposedPorts(50051)
        .dependsOn(eventstore, adminPdpEpp) // Depends on both other services
        // Wait until the gRPC port is listening
        .waitingFor(Wait.forListeningPort());

    @AfterEach
    void teardown() {
        eventstore.stop();
        adminPdpEpp.stop();
        resourcePdp.stop();
    }

    @Test
    void testServicesAreRunning() {
        //eventstore.start();
        adminPdpEpp.start();
        //resourcePdp.start();

        // assertTrue(eventstore.isRunning(), "EventStore container should be running");
        assertTrue(adminPdpEpp.isRunning(), "Admin PDP EPP container should be running");
        // assertTrue(resourcePdp.isRunning(), "Resource PDP container should be running");

        // TODO: Add actual integration tests here
        // Use the mapped ports and hostnames to connect to the services
        // String adminHost = adminPdpEpp.getHost();
        // Integer adminPort = adminPdpEpp.getMappedPort(50052);
        // String resourceHost = resourcePdp.getHost();
        // Integer resourcePort = resourcePdp.getMappedPort(50051);

        // Example gRPC connection test (requires grpc-netty-shaded dependency)
        // try (ManagedChannel channel = ManagedChannelBuilder.forAddress(adminHost, adminPort).usePlaintext().build()) {
        //     // YourGrpcServiceGrpc.YourGrpcServiceBlockingStub stub = YourGrpcServiceGrpc.newBlockingStub(channel);
        //     // YourRequest request = YourRequest.newBuilder().build();
        //     // YourResponse response = stub.yourMethod(request);
        //     // assertNotNull(response);
        // } catch (Exception e) {
        //     fail("Failed to connect or call admin service", e);
        // }
    }

}
