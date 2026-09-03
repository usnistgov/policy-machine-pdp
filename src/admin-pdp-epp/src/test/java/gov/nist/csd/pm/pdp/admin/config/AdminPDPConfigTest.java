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

package gov.nist.csd.pm.pdp.admin.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminPDPConfigTest {

    private AdminPDPConfig newConfig(String mode) {
        AdminPDPConfig config = new AdminPDPConfig();
        config.setMode(mode);
        config.setBootstrapFilePath("bootstrap.pml");
        return config;
    }

    @Test
    void validate_invalidMode_throws() {
        AdminPDPConfig config = newConfig("playground");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, config::validate);
        assertTrue(e.getMessage().contains("playground"));
    }

    @Test
    void validate_missingBootstrapFile_throws() {
        AdminPDPConfig config = newConfig("default");
        config.setBootstrapFilePath(null);

        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    void validate_sandboxMode_skipsEventStoreValidation() {
        AdminPDPConfig config = newConfig("sandbox");
        // no esdbConsumerGroup / neo4jDbPath set

        assertDoesNotThrow(config::validate);
    }

    @Test
    void validate_defaultModeMissingConsumerGroup_throws() {
        AdminPDPConfig config = newConfig("default");

        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    void validate_defaultMode_appliesDefaults() {
        AdminPDPConfig config = newConfig("default");
        config.setEsdbConsumerGroup("cg");

        assertDoesNotThrow(config::validate);
        assertEquals("/neo4j", config.getNeo4jDbPath());
        assertEquals(1000, config.getSnapshotInterval());
        assertEquals(1000, config.getRevisionConsistencyTimeout());
    }
}
