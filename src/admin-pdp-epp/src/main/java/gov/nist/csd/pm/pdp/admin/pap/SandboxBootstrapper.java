package gov.nist.csd.pm.pdp.admin.pap;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.core.pdp.bootstrap.PMLBootstrapperWithSuper;
import gov.nist.csd.pm.core.pap.serialization.json.JSONDeserializer;
import gov.nist.csd.pm.pdp.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.pdp.shared.bootstrap.BootstrapFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import gov.nist.csd.pm.pdp.shared.config.SandboxMode;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Component("policyBootstrapper")
@SandboxMode
public class SandboxBootstrapper {

    private static final Logger logger = LoggerFactory.getLogger(SandboxBootstrapper.class);

    private final AdminPDPConfig adminPDPConfig;
    private final MemoryPAP pap;

    public SandboxBootstrapper(AdminPDPConfig adminPDPConfig, MemoryPAP pap) {
        this.adminPDPConfig = adminPDPConfig;
        this.pap = pap;
    }

    @PostConstruct
    public void bootstrap() throws PMException, IOException {
        String bootstrapFilePath = adminPDPConfig.getBootstrapFilePath();
        logger.info("sandbox mode: bootstrapping from file {}", bootstrapFilePath);

        BootstrapFile bootstrapFile = BootstrapFile.load(bootstrapFilePath);
        switch (bootstrapFile.format()) {
            case PML -> pap.bootstrap(new PMLBootstrapperWithSuper(bootstrapFile.data()));
            case JSON -> pap.deserialize(bootstrapFile.data(), new JSONDeserializer());
        }

        logger.info("sandbox mode: bootstrap complete");
    }
}
