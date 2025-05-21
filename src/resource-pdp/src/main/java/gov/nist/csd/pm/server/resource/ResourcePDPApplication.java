package gov.nist.csd.pm.server.resource;

import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pdp.PDP;
import gov.nist.csd.pm.server.resource.config.ResourcePDPConfig;
import gov.nist.csd.pm.server.shared.eventstore.EventStoreDBConfig;
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
@EnableConfigurationProperties({EventStoreDBConfig.class, ResourcePDPConfig.class})
public class ResourcePDPApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResourcePDPApplication.class, args);
    }

    @Bean
    public PAP pap() throws PMException {
	    return new MemoryPAP();
    }

    @Bean
    public PDP pdp(PAP pap) {
        return new PDP(pap);
    }
}
