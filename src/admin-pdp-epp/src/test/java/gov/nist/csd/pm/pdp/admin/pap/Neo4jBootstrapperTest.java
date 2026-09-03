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
 * licensed to NIST.
 *
 * This file is part of the admin-pdp-epp module, which compiles against
 * and embeds org.neo4j:neo4j (GPLv3, Community Edition). As a combined work, this
 * module is distributed under the GNU General Public License v3.0, not the CC BY 4.0
 * license used elsewhere in this repository. See admin-pdp-epp/LICENSE for the full text.
 */

package gov.nist.csd.pm.pdp.admin.pap;

import com.eventstore.dbclient.ReadStreamOptions;
import com.eventstore.dbclient.ResolvedEvent;
import com.eventstore.dbclient.StreamNotFoundException;
import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.neo4j.embedded.pap.store.Neo4jEmbeddedPolicyStore;
import gov.nist.csd.pm.pdp.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import gov.nist.csd.pm.pdp.sharedtest.EventStoreTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

class Neo4jBootstrapperTest {

    private EventStoreTestContainer eventStoreTestContainer;
    private DatabaseManagementService managementService;
    private GraphDatabaseService graphDb;
    private EventStoreDBConfig eventStoreDBConfig;
    private EventStoreConnectionManager eventStoreConnectionManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws PMException, IOException {
        eventStoreTestContainer = new EventStoreTestContainer();
        eventStoreTestContainer.start();

        managementService = new DatabaseManagementServiceBuilder(tempDir.resolve("neo4j"))
                .setConfig(GraphDatabaseSettings.strict_config_validation, false)
                .build();
        graphDb = managementService.database(DEFAULT_DATABASE_NAME);
        Neo4jEmbeddedPolicyStore.createIndexes(graphDb);

        eventStoreDBConfig = new EventStoreDBConfig(
                "test-events",
                "test-snapshots",
                eventStoreTestContainer.getHost(),
                eventStoreTestContainer.getPort()
        );
        eventStoreConnectionManager = new EventStoreConnectionManager(eventStoreDBConfig);
    }

    @AfterEach
    void tearDown() {
        if (managementService != null) {
            managementService.shutdown();
        }
        if (eventStoreTestContainer != null) {
            eventStoreTestContainer.stop();
        }
    }

    private Neo4jBootstrapper bootstrapper(String bootstrapFilePath) {
        AdminPDPConfig config = new AdminPDPConfig();
        config.setMode("default");
        config.setBootstrapFilePath(bootstrapFilePath);

        return new Neo4jBootstrapper(
                config,
                eventStoreDBConfig,
                eventStoreConnectionManager,
                graphDb,
                List.of()
        );
    }

    private int countEvents() throws ExecutionException, InterruptedException {
        try {
            List<ResolvedEvent> events = eventStoreConnectionManager.getOrInitClient()
                    .readStream(eventStoreDBConfig.getEventStream(), ReadStreamOptions.get().fromStart())
                    .get()
                    .getEvents();
            return events.size();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof StreamNotFoundException) {
                return 0;
            }
            throw e;
        }
    }

    @Test
    void eventsInStream_emptyStream_returnsFalse() throws ExecutionException, InterruptedException {
        assertFalse(bootstrapper("unused.pml").eventsInStream());
    }

    @Test
    void bootstrap_jsonFile_publishesEvent() throws Exception {
        Path jsonFile = tempDir.resolve("bootstrap.json");
        Files.writeString(jsonFile, "{\"graph\":{}}");

        Neo4jBootstrapper bootstrapper = bootstrapper(jsonFile.toString());
        bootstrapper.bootstrap();

        assertTrue(bootstrapper.eventsInStream());
        assertEquals(1, countEvents());
    }

    @Test
    void bootstrap_pmlFile_publishesEvents() throws Exception {
        Neo4jBootstrapper bootstrapper = bootstrapper("src/main/resources/bootstrap.pml");
        bootstrapper.bootstrap();

        assertTrue(bootstrapper.eventsInStream());
        assertTrue(countEvents() > 0, "PML bootstrap should publish at least one event");
    }

    @Test
    void bootstrap_existingEvents_skips() throws Exception {
        Path jsonFile = tempDir.resolve("bootstrap.json");
        Files.writeString(jsonFile, "{\"graph\":{}}");

        bootstrapper(jsonFile.toString()).bootstrap();
        int afterFirst = countEvents();

        // a second bootstrap against a non-empty stream must be a no-op
        bootstrapper(jsonFile.toString()).bootstrap();

        assertEquals(afterFirst, countEvents());
    }
}
