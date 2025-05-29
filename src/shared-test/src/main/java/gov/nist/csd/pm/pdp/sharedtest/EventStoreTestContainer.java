package gov.nist.csd.pm.pdp.sharedtest;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class EventStoreTestContainer extends GenericContainer<EventStoreTestContainer> {

	public EventStoreTestContainer() {
		super("eventstore/eventstore:24.10");

		withEnv("EVENTSTORE_HTTP_PORT", "2113")
				.withEnv("EVENTSTORE_INT_TCP_PORT", "1113")
				.withEnv("EVENTSTORE_CLUSTER_SIZE", "1")
				.withEnv("EVENTSTORE_RUN_PROJECTIONS", "All")
				.withEnv("EVENTSTORE_START_STANDARD_PROJECTIONS", "true")
				.withEnv("EVENTSTORE_INSECURE", "true")
				.withEnv("EVENTSTORE_ENABLE_ATOM_PUB_OVER_HTTP", "true")
				.withExposedPorts(2113, 1113)
				.waitingFor(Wait.forHttp("/stats").forPort(2113).forStatusCode(200));
	}

	public int getPort() {
		return getMappedPort(2113);
	}
}
