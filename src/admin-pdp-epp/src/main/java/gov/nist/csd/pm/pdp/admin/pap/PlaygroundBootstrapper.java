package gov.nist.csd.pm.pdp.admin.pap;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.core.pdp.bootstrap.PMLBootstrapperWithSuper;
import gov.nist.csd.pm.core.pap.serialization.json.JSONDeserializer;
import gov.nist.csd.pm.pdp.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.pdp.shared.bootstrap.BootstrapFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import gov.nist.csd.pm.pdp.shared.config.PlaygroundMode;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Component("policyBootstrapper")
@PlaygroundMode
public class PlaygroundBootstrapper {

    private static final Logger logger = LoggerFactory.getLogger(PlaygroundBootstrapper.class);

    private final AdminPDPConfig adminPDPConfig;
    private final MemoryPAP pap;

    public PlaygroundBootstrapper(AdminPDPConfig adminPDPConfig, MemoryPAP pap) {
        this.adminPDPConfig = adminPDPConfig;
        this.pap = pap;
    }

    @PostConstruct
    public void bootstrap() throws PMException, IOException {
        String bootstrapFilePath = adminPDPConfig.getBootstrapFilePath();
        logger.info("playground mode: bootstrapping from file {}", bootstrapFilePath);

        BootstrapFile bootstrapFile = BootstrapFile.load(bootstrapFilePath);
        switch (bootstrapFile.format()) {
            case PML -> pap.bootstrap(new PMLBootstrapperWithSuper(bootstrapFile.data()));
            case JSON -> pap.deserialize(bootstrapFile.data(), new JSONDeserializer());
        }

        logger.info("playground mode: bootstrap complete");
    }
}
