package gov.nist.csd.pm.pdp.admin;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.Neo4jEmbeddedPAP;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.store.Neo4jEmbeddedPolicyStore;
import gov.nist.csd.pm.core.pap.operation.Operation;
import gov.nist.csd.pm.pdp.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.pdp.admin.plugin.PluginLoader;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.io.File;
import java.util.List;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

@SpringBootApplication(exclude = {Neo4jAutoConfiguration.class})
@EnableAspectJAutoProxy
@ComponentScan(
    basePackages = {"gov.nist.csd.pm.pdp"}
)
@EnableConfigurationProperties({EventStoreDBConfig.class, AdminPDPConfig.class})
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
    public Neo4jEmbeddedPolicyStore eventListenerPolicyStore(GraphDatabaseService graphDb) throws PMException {
        Neo4jEmbeddedPolicyStore.createIndexes(graphDb);

        return new Neo4jEmbeddedPolicyStore(graphDb, getClass().getClassLoader());
    }

    @Bean
    public List<Operation<?>> loadPlugins(PluginLoader pluginLoader) {
        return pluginLoader.loadPlugins();
    }

    @Bean
    public Neo4jEmbeddedPAP neo4jEmbeddedPAP(Neo4jEmbeddedPolicyStore eventListenerPolicyStore, List<Operation<?>> pluginOps) throws PMException {
        Neo4jEmbeddedPAP neo4jEmbeddedPAP = new Neo4jEmbeddedPAP(eventListenerPolicyStore);

        for (Operation<?> op : pluginOps) {
            neo4jEmbeddedPAP.plugins().addOperation(op);
        }

        return neo4jEmbeddedPAP;
    }

}
