package gov.nist.csd.pm.pdp.admin;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.store.Neo4jEmbeddedPolicyStore;
import gov.nist.csd.pm.pdp.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import gov.nist.csd.pm.pdp.shared.plugin.PluginLoader;
import gov.nist.csd.pm.pdp.shared.plugin.PluginLoaderConfig;
import java.io.File;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
@ComponentScan(
    basePackages = {"gov.nist.csd.pm.pdp"}
)
@EnableConfigurationProperties({EventStoreDBConfig.class, AdminPDPConfig.class, PluginLoaderConfig.class})
public class AdminPDPEPPApplication {

    private static final Logger logger = LoggerFactory.getLogger(AdminPDPEPPApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AdminPDPEPPApplication.class, args);
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
    public Neo4jEmbeddedPolicyStore eventListenerPolicyStore(PluginLoader pluginLoader, GraphDatabaseService graphDb) throws PMException {
        Neo4jEmbeddedPolicyStore.createIndexes(graphDb);

        return new Neo4jEmbeddedPolicyStore(graphDb, pluginLoader.getPluginClassLoader());
    }
}
