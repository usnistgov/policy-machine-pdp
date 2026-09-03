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

package gov.nist.csd.pm.pdp.resource;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.operation.Operation;
import gov.nist.ngac.pm.core.pap.operation.ResourceOperation;
import gov.nist.ngac.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.resource.config.ResourcePDPConfig;
import gov.nist.csd.pm.pdp.shared.auth.AuthConfig;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import gov.nist.csd.pm.pdp.shared.plugin.PluginLoader;

import java.util.List;
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
@EnableConfigurationProperties({EventStoreDBConfig.class, ResourcePDPConfig.class, AuthConfig.class})
public class ResourcePDPApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResourcePDPApplication.class, args);
    }

    @Bean
    public PluginLoader pluginLoader(ResourcePDPConfig config) {
        return new PluginLoader(config.getPluginsDir());
    }

    @Bean
    public List<Operation<?>> pluginOps(PluginLoader pluginLoader) {
        return pluginLoader.loadPlugins().stream()
                .filter(op -> op instanceof ResourceOperation)
                .toList();
    }

    @Bean
    public PAP pap(List<Operation<?>> pluginOps) throws PMException {
        MemoryPAP pap = new MemoryPAP();
        for (Operation<?> op : pluginOps) {
            pap.javaOperations().register(op);
        }
        return pap;
    }

    @Bean
    public PDP pdp(PAP pap) {
        return new PDP(pap);
    }
}
