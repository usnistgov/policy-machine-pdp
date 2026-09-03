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

package gov.nist.csd.pm.pdp.admin;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.neo4j.embedded.pap.Neo4jEmbeddedPAP;
import gov.nist.ngac.pm.core.neo4j.embedded.pap.store.Neo4jEmbeddedPolicyStore;
import gov.nist.ngac.pm.core.pap.operation.Operation;
import gov.nist.csd.pm.pdp.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.pdp.shared.auth.AuthConfig;
import gov.nist.csd.pm.pdp.shared.plugin.PluginLoader;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import gov.nist.csd.pm.pdp.shared.config.DefaultMode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.io.File;
import java.util.List;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

@SpringBootApplication
@EnableAspectJAutoProxy
@ComponentScan(
    basePackages = {"gov.nist.csd.pm.pdp"}
)
@EnableConfigurationProperties({EventStoreDBConfig.class, AdminPDPConfig.class, AuthConfig.class})
public class AdminPDPEPPApplication {

    private static final Logger logger = LoggerFactory.getLogger(AdminPDPEPPApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AdminPDPEPPApplication.class, args);
    }

    @Bean
    @DefaultMode
    public GraphDatabaseService graphDb(AdminPDPConfig adminPDPConfig) {
        logger.info("Creating Neo4j embedded database instance");

        DatabaseManagementService managementService = new DatabaseManagementServiceBuilder(
            new File(adminPDPConfig.getNeo4jDbPath()).toPath())
            .setConfig(GraphDatabaseSettings.strict_config_validation, false)
            .build();

        return managementService.database(DEFAULT_DATABASE_NAME);
    }

    @Bean
    @DefaultMode
    public Neo4jEmbeddedPolicyStore eventListenerPolicyStore(GraphDatabaseService graphDb) throws PMException {
        Neo4jEmbeddedPolicyStore.createIndexes(graphDb);

        return new Neo4jEmbeddedPolicyStore(graphDb);
    }

    @Bean
    public PluginLoader pluginLoader(AdminPDPConfig config) {
        return new PluginLoader(config.getPluginsDir());
    }

    @Bean
    public List<Operation<?>> loadPlugins(PluginLoader pluginLoader) {
        return pluginLoader.loadPlugins();
    }

    @Bean
    @DefaultMode
    public Neo4jEmbeddedPAP neo4jEmbeddedPAP(Neo4jEmbeddedPolicyStore eventListenerPolicyStore, List<Operation<?>> pluginOps) throws PMException {
        Neo4jEmbeddedPAP neo4jEmbeddedPAP = new Neo4jEmbeddedPAP(eventListenerPolicyStore);

        for (Operation<?> op : pluginOps) {
            neo4jEmbeddedPAP.javaOperations().register(op);
        }

        return neo4jEmbeddedPAP;
    }

}
