package gov.nist.csd.pm.pdp.shared.eventstore;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBPersistentSubscriptionsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;

@Service
public class EventStoreConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(EventStoreConnectionManager.class);

    private final EventStoreDBConfig eventStoreDBConfig;
    private EventStoreDBPersistentSubscriptionsClient subClient;
    private EventStoreDBClient client;

    public EventStoreConnectionManager(EventStoreDBConfig eventStoreDBConfig) {
        this.eventStoreDBConfig = eventStoreDBConfig;
    }

    @PreDestroy
    public void shutdown() {
        if (subClient != null) {
            subClient.shutdown();
        }

        if (client != null) {
            client.shutdown();
        }
    }

    public EventStoreDBClientSettings settings() {
        return EventStoreDBClientSettings.builder()
            .addHost(eventStoreDBConfig.getHostname(), eventStoreDBConfig.getPort())
            .tls(false)
            .keepAliveTimeout(10000)
            .keepAliveInterval(10000)
            .defaultDeadline(30000)
            .maxDiscoverAttempts(3)
            .buildConnectionSettings();
    }

    public EventStoreDBPersistentSubscriptionsClient getOrInitPersistentSubClient() {
        if (subClient == null || subClient.isShutdown()) {
            logger.info("Creating EventStore persistent subscriptions client");
            subClient = EventStoreDBPersistentSubscriptionsClient.create(settings());
        }

        return subClient;
    }

    public EventStoreDBClient getOrInitClient() {
        if (client == null || client.isShutdown()) {
            logger.info("Creating EventStore client");
            client = EventStoreDBClient.create(settings());
        }

        return client;
    }
}
