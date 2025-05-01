package gov.nist.csd.pm.server.resource;

import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pdp.PDP;
import gov.nist.csd.pm.server.shared.config.PDPConfig;
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
@EnableConfigurationProperties(PDPConfig.class)
public class ResourcePDPApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResourcePDPApplication.class, args);
    }

    @Bean
    public PAP pap() throws PMException {
        MemoryPAP memoryPAP = new MemoryPAP();
        memoryPAP.executePML(new UserContext(1), """
            create pc "pc1"
            create ua "ua1" in ["pc1"]
            create oa "oa1" in ["pc1"]
            create u "u1" in ["ua1"]
            create o "o1" in ["oa1"]
            
            set resource operations ["read"]
            
            associate "ua1" and "oa1" with ["read"]
            """);
        return memoryPAP;
    }

    @Bean
    public PDP pdp() throws PMException {
        return new PDP(pap());
    }
}
