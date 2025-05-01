package gov.nist.csd.pm.server.sharedtest;

import com.eventstore.dbclient.EventStoreDBClientSettings;

public class NoopTestEventStoreContainer extends TestEventStoreContainer {

	public NoopTestEventStoreContainer() {

	}

	@Override
	protected EventStoreDBClientSettings settings() {
		return EventStoreDBClientSettings.builder()
				.addHost("localhost", 0)
				.tls(false)
				.keepAliveTimeout(10000)
				.keepAliveInterval(10000)
				.defaultDeadline(30000)
				.maxDiscoverAttempts(3)
				.buildConnectionSettings();
	}
}
