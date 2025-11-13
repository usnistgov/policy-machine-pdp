package gov.nist.csd.pm.pdp.admin;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.Neo4jEmbeddedPAP;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.store.Neo4jEmbeddedPolicyStore;
import gov.nist.csd.pm.core.pap.function.PluginRegistry;
import gov.nist.csd.pm.core.pap.function.arg.Args;
import gov.nist.csd.pm.core.pap.function.arg.FormalParameter;
import gov.nist.csd.pm.core.pap.function.arg.type.ListType;
import gov.nist.csd.pm.core.pap.function.arg.type.MapType;
import gov.nist.csd.pm.core.pap.function.arg.type.StringType;
import gov.nist.csd.pm.core.pap.function.arg.type.Type;
import gov.nist.csd.pm.core.pap.function.op.Operation;
import gov.nist.csd.pm.core.pap.function.routine.Routine;
import gov.nist.csd.pm.pdp.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import gov.nist.csd.pm.pdp.admin.plugin.PluginLoader;

import java.io.File;
import java.util.List;
import java.util.Map;

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

        return new Neo4jEmbeddedPolicyStore(graphDb, getClass().getClassLoader());
    }

    @Bean
    public Neo4jEmbeddedPAP neo4jEmbeddedPAP(Neo4jEmbeddedPolicyStore eventListenerPolicyStore) throws PMException {
        return new Neo4jEmbeddedPAP(eventListenerPolicyStore);
    }

    public static FormalParameter<String> TARGET_DB_URL_PARAM = new FormalParameter<>("target_db_url", Type.STRING_TYPE);
    private static final FormalParameter<String> JDBC_CATALOG_NAME_PARAM = new FormalParameter<>("jdbc_catalog", new StringType());
    private static final FormalParameter<String> JDBC_SCHEMA_PARAM = new FormalParameter<>("jdbc_schema", new StringType());
    private static final FormalParameter<Map<String, List<String>>> EXCLUSIONS_PARAM =
            new FormalParameter<>("exclusions", new MapType<>(new StringType(), new ListType<>(new StringType())));

    @Bean
    public PluginRegistry pluginRegistry(PluginLoader pluginLoader) throws PMException {
        PluginRegistry pluginRegistry = new PluginRegistry();

        List<Operation<?>> operations = pluginLoader.getOperationPlugins();
        for (Operation<?> operation : operations) {
            if (operation.getName().equals("import_schema")) {
                operation.execute(new MemoryPAP(), new Args(Map.of(
                        TARGET_DB_URL_PARAM, "jdbc:mysql://root:rootroot@localhost:3306/airline_demo",
                        JDBC_CATALOG_NAME_PARAM, "airline_demo",
                        JDBC_SCHEMA_PARAM, "airline_demo",
                        EXCLUSIONS_PARAM, Map.of())));
            }
            pluginRegistry.registerOperation(operation);
        }

        List<Routine<?>> routines = pluginLoader.getRoutinePlugins();
        for (Routine<?> routine : routines) {
            pluginRegistry.registerRoutine(routine);
        }

        return pluginRegistry;
    }
}
