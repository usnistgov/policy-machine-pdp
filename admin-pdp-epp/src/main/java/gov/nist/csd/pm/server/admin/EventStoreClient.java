package gov.nist.csd.pm.server.admin;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import gov.nist.csd.pm.server.shared.ServerConfig;

public class EventStoreClient {

	public static EventStoreDBClient get(ServerConfig serverConfig) {
		EventStoreDBClientSettings settings = EventStoreDBClientSettings.builder()
				.addHost(serverConfig.esdbHost(), serverConfig.esdbPort())
				.tls(false)
				.keepAliveTimeout(10000)
				.keepAliveInterval(10000)
				.defaultDeadline(30000)
				.buildConnectionSettings();

		return EventStoreDBClient.create(settings);
	}
}
