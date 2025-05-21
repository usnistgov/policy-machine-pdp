package gov.nist.csd.pm.server.admin;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.impl.neo4j.embedded.pap.Neo4jEmbeddedPAP;
import gov.nist.csd.pm.impl.neo4j.embedded.pap.store.Neo4jEmbeddedGraphStore;
import gov.nist.csd.pm.impl.neo4j.embedded.pap.store.Neo4jEmbeddedPolicyStore;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.function.AdminFunction;
import gov.nist.csd.pm.pap.function.op.Operation;
import gov.nist.csd.pm.server.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.server.admin.pap.EventTrackingPAP;
import gov.nist.csd.pm.server.admin.pap.NoCommitNeo4jPolicyStore;
import gov.nist.csd.pm.server.shared.eventstore.EventStoreDBConfig;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
@ComponentScan(
    basePackages = {"gov.nist.csd.pm.server"}
)
@EnableConfigurationProperties({EventStoreDBConfig.class, AdminPDPConfig.class})
public class AdminPDPEPPApplication {

    private static final Logger logger = LoggerFactory.getLogger(AdminPDPEPPApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AdminPDPEPPApplication.class, args);
    }

    @Bean
    public List<AdminFunction<?, ?>> adminFunctionPlugins() {
        // TODO get from plugin dir using pf4j
        return new ArrayList<>();
    }

    @Bean
    public GraphDatabaseService graphDb(AdminPDPConfig adminPDPConfig) {
        logger.info("Creating Neo4j embedded database instance");

        DatabaseManagementService managementService = new DatabaseManagementServiceBuilder(
            new File(adminPDPConfig.getNeo4jDbPath()).toPath())
            .setConfig(GraphDatabaseSettings.strict_config_validation, false)
            .build();

        return managementService.database(DEFAULT_DATABASE_NAME);
    }

    @Bean
    @Qualifier("txSupportPolicyStore")
    public NoCommitNeo4jPolicyStore txSupportPolicyStore(GraphDatabaseService graphDb) throws PMException {
        Neo4jEmbeddedPolicyStore.createIndexes(graphDb);

        return new NoCommitNeo4jPolicyStore(graphDb);
    }

    @Bean
    @Qualifier("eventListenerPolicyStore")
    public Neo4jEmbeddedPolicyStore eventListenerPolicyStore(GraphDatabaseService graphDb) throws PMException {
        Neo4jEmbeddedPolicyStore.createIndexes(graphDb);

        return new Neo4jEmbeddedPolicyStore(graphDb);
    }

    @Bean
    public Neo4jEmbeddedPAP pap(@Qualifier("eventListenerPolicyStore") Neo4jEmbeddedPolicyStore eventListenerPolicyStore) throws PMException {
        return new Neo4jEmbeddedPAP(eventListenerPolicyStore);
    }
}
