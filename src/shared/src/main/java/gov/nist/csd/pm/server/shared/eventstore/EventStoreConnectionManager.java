/*
package gov.nist.csd.pm.server.shared.eventstore;

import com.eventstore.dbclient.AppendToStreamOptions;
import com.eventstore.dbclient.EventData;
import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBPersistentSubscriptionsClient;
import com.eventstore.dbclient.ExpectedRevision;
import com.eventstore.dbclient.WriteResult;
import com.eventstore.dbclient.WrongExpectedVersionException;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.server.shared.config.BaseConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventStoreConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(EventStoreConnectionManager.class);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(2);

    private BaseConfig config;
    private CircuitBreaker circuitBreaker;
    private EventStoreDBPersistentSubscriptionsClient subClient;
    private EventStoreDBClient client;
    private AtomicBoolean circuitBreakerOpen = new AtomicBoolean(false);

    public EventStoreConnectionManager(BaseConfig config) {
        this.config = config;
        this.circuitBreaker = CircuitBreaker.of(
            "eventStoreHealth",
            CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build()
        );
    }

    public EventStoreConnectionManager(BaseConfig config, CircuitBreaker circuitBreaker) {
        this.config = config;
        this.circuitBreaker = circuitBreaker;
    }

    public BaseConfig getConfig() {
        return config;
    }

    public void setConfig(BaseConfig config) {
        this.config = config;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public boolean isCircuitBreakerOpen() {
        return circuitBreakerOpen.get();
    }

    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public EventStoreDBPersistentSubscriptionsClient getSubClient() {
        return subClient;
    }

    public void setSubClient(EventStoreDBPersistentSubscriptionsClient subClient) {
        this.subClient = subClient;
    }

    public EventStoreDBPersistentSubscriptionsClient getOrInitSubClient() {
        if (subClient == null || subClient.isShutdown()) {
            subClient = EventStoreDBPersistentSubscriptionsClient.create(
                EventStoreDBClientConnector.settings(config));
        }

        return subClient;
    }

    public EventStoreDBClient getOrInitClient() {
        if (client == null || client.isShutdown()) {
            client = EventStoreDBClientConnector.connect(config);
        }

        return client;
    }

    public void blockUntilHealthy() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                circuitBreaker.executeRunnable(() -> {
                    try {
                        this.healthCheck();
                    } catch (IOException | PMException e) {
                        throw new RuntimeException(e);
                    }
                });

                logger.info("EventStore is healthy. Unblocking.");
                break;
            } catch (Exception e) {
                logger.error("error performing health check: ", e);
            }

            if (circuitBreaker.getState() == State.OPEN) {
                logger.warn("CircuitBreaker is OPEN");
                circuitBreakerOpen.set(true);
            }

            try {
                TimeUnit.SECONDS.sleep(config.getHealthCheckInterval());
            } catch (InterruptedException e) {
                logger.error("error waiting for health check interval: ", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    protected void healthCheck() throws IOException, PMException {
        
    }

    public void shutdown() {
        if (subClient != null) {
            subClient.shutdown();
        }
    }

    public void createEventStreamIfNotExists(String esdbStream) {
        EventStoreDBClient client = EventStoreDBClientConnector.connect(config);

        AppendToStreamOptions options = AppendToStreamOptions.get()
            .expectedRevision(ExpectedRevision.noStream()); // CRITICAL: Only succeed if stream doesn't exist

        try {
            EventData streamCreatedEvent = EventData.builderAsBinary(
                "StreamCreated",
                esdbStream.getBytes(StandardCharsets.UTF_8)
            ).build();

            // Attempt to append the first event, creating the stream if it doesn't exist
            WriteResult writeResult = client.appendToStream(esdbStream, options, streamCreatedEvent)
                .get(); // Blocking call for simplicity

            logger.info("Stream '" + esdbStream + "' created successfully (or first event appended). Revision: "
                + writeResult.getNextExpectedRevision());

        } catch (ExecutionException e) {
            // if the stream already exists, continue without error
            if (e.getCause() instanceof WrongExpectedVersionException w) {
                logger.info("stream {} exists with expected revision {}", esdbStream, w.getActualVersion());
            }

            throw new RuntimeException("error creating event stream", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
*/
