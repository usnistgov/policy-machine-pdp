package gov.nist.csd.pm.server.sharedtest;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBPersistentSubscriptionsClient;
import com.github.dockerjava.api.model.HealthCheck;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Map;

public class TestEventStoreContainer extends GenericContainer<TestEventStoreContainer> {

	public TestEventStoreContainer() {
		super("docker.eventstore.com/eventstore-ce/eventstoredb-ce:latest");

		addExposedPorts(2113, 1113);
		withEnv(Map.of(
				"EVENTSTORE_RUN_PROJECTIONS", "All",
				"EVENTSTORE_START_STANDARD_PROJECTIONS", "true",
				"EVENTSTORE_INSECURE", "true",
				"EVENTSTORE_ENABLE_ATOM_PUB_OVER_HTTP", "true"
		));
		withCreateContainerCmdModifier(cmd -> cmd.withHealthcheck(
				new HealthCheck()
						.withInterval(1000000000L)
						.withTimeout(1000000000L)
						.withRetries(10))
		);
		waitingFor(Wait.forHealthcheck());
	}

	protected EventStoreDBClientSettings settings() {
		return EventStoreDBClientSettings.builder()
				.addHost(this.getHost(), this.getMappedPort(2113))
				.tls(false)
				.keepAliveTimeout(10000)
				.keepAliveInterval(10000)
				.defaultDeadline(30000)
				.maxDiscoverAttempts(3)
				.buildConnectionSettings();
	}

	public EventStoreDBClient createEventStoreDBClient() {
		return EventStoreDBClient.create(settings());
	}

	public EventStoreDBPersistentSubscriptionsClient createEventStoreDBSubscriptionClient() {
		return EventStoreDBPersistentSubscriptionsClient.create(settings());
	}
}
