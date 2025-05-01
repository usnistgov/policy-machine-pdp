package gov.nist.csd.pm.server.shared.eventstore;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBPersistentSubscriptionsClient;
import gov.nist.csd.pm.server.shared.config.PDPConfig;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventStoreClientConfiguration {

    private final PDPConfig pdpConfig;

    public EventStoreClientConfiguration(PDPConfig pdpConfig) {
        this.pdpConfig = pdpConfig;
    }

    @Bean
    public EventStoreDBClientSettings eventStoreDBClientSettings() {
        return EventStoreDBClientSettings.builder()
            .addHost(pdpConfig.getEsdbHost(), pdpConfig.getEsdbPort())
            .tls(false)
            .keepAliveTimeout(10000)
            .keepAliveInterval(10000)
            .defaultDeadline(30000)
            .maxDiscoverAttempts(3)
            .buildConnectionSettings();
    }

    @Bean()
    public EventStoreDBClient eventStoreDBClient() {
        return EventStoreDBClient.create(eventStoreDBClientSettings());
    }

    @Bean()
    public EventStoreDBPersistentSubscriptionsClient eventStoreDBPersistentSubscriptionsClient() {
        return EventStoreDBPersistentSubscriptionsClient.create(eventStoreDBClientSettings());
    }

}
