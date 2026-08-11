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
