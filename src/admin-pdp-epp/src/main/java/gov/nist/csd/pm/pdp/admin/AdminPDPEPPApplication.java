package gov.nist.csd.pm.pdp.admin;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.Neo4jEmbeddedPAP;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.store.Neo4jEmbeddedPolicyStore;
import gov.nist.csd.pm.core.pap.function.op.Operation;
import gov.nist.csd.pm.core.pap.function.routine.Routine;
import gov.nist.csd.pm.pdp.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import java.io.File;
import java.util.List;

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

        return new Neo4jEmbeddedPolicyStore(graphDb);
    }

    @Bean
    public Neo4jEmbeddedPAP pap(Neo4jEmbeddedPolicyStore eventListenerPolicyStore, 
                                List<Operation<?, ?>> operationPlugins,
                                List<Routine<?, ?>> routinePlugins) throws PMException {
        Neo4jEmbeddedPAP pap = new Neo4jEmbeddedPAP(eventListenerPolicyStore);
        
        // Register operation plugins
        for (Operation<?, ?> operation : operationPlugins) {
            logger.info("Registering operation plugin: {}", operation.getName());
            pap.modify().operations().createAdminOperation(operation);
        }
        
        // Register routine plugins
        for (Routine<?, ?> routine : routinePlugins) {
            logger.info("Registering routine plugin: {}", routine.getName());
            pap.modify().routines().createAdminRoutine(routine);
        }
        
        return pap;
    }
}
