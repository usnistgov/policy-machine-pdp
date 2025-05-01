package gov.nist.csd.pm.server.shared.eventstore;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.ServerVersion;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class EventStoreHealthChecker implements HealthIndicator {

    private final EventStoreDBClient eventStoreDBClient;

    public EventStoreHealthChecker(EventStoreDBClient eventStoreDBClient) {
        this.eventStoreDBClient = eventStoreDBClient;
    }

    @Override
    public Health health() {
        try {
            Optional<ServerVersion> version = eventStoreDBClient.getServerVersion()
                .get(2, TimeUnit.SECONDS); // Timeout after 2 seconds

            if (version.isPresent()) {
                return Health.up().build();
            } else {
                return Health.down().build();
            }
        } catch (Exception e) {
            return Health.down(e)
                .build();
        }
    }
}
