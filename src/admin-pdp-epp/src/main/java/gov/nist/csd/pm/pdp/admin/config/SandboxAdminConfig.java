package gov.nist.csd.pm.pdp.admin.config;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.core.pap.operation.Operation;
import gov.nist.csd.pm.pdp.shared.config.SandboxMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@SandboxMode
public class SandboxAdminConfig {

    @Bean
    public MemoryPAP memoryPAP(List<Operation<?>> plugins) throws PMException {
        MemoryPAP pap = new MemoryPAP();
        for (Operation<?> op : plugins) {
            pap.plugins().addOperation(op);
        }
        return pap;
    }
}
