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
