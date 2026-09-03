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

package gov.nist.csd.pm.pdp.admin.pap;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.ngac.pm.core.pdp.bootstrap.PMLBootstrapperWithSuper;
import gov.nist.ngac.pm.core.pap.serialization.json.JSONDeserializer;
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
