/*
 * This Software (Policy Machine) is being made available as a public service by the
 * National Institute of Standards and Technology (NIST), an Agency of the United
 * States Department of Commerce. This software was developed in part by employees of
 * NIST and in part by NIST contractors. Copyright in portions of this software that
 * were developed by NIST contractors has been licensed or assigned to NIST. Pursuant
 * to Title 17 United States Code Section 105, works of NIST employees are not
 * subject to copyright protection in the United States. However, NIST may hold
 * international copyright in software created by its employees and domestic
 * copyright (or licensing rights) in portions of software that were assigned or
 * licensed to NIST. To the extent that NIST holds copyright in this software, it is
 * being made available under the Creative Commons Attribution 4.0 International
 * license (CC BY 4.0). The disclaimers of the CC BY 4.0 license apply to all parts
 * of the software developed or licensed by NIST.
 *
 * ACCESS THE FULL CC BY 4.0 LICENSE HERE:
 * https://creativecommons.org/licenses/by/4.0/legalcode
 */

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
