package gov.nist.csd.pm.pdp.shared.bootstrap;

import gov.nist.ngac.pm.core.common.exception.PMException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BootstrapFileTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsPmlFile() throws PMException, IOException {
        Path file = tempDir.resolve("bootstrap.pml");
        Files.writeString(file, "create pc \"pc1\"");

        BootstrapFile bootstrapFile = BootstrapFile.load(file.toString());

        assertEquals(BootstrapFile.Format.PML, bootstrapFile.format());
        assertEquals("create pc \"pc1\"", bootstrapFile.data());
    }

    @Test
    void loadsJsonFile() throws PMException, IOException {
        Path file = tempDir.resolve("bootstrap.json");
        Files.writeString(file, "{\"graph\":{}}");

        BootstrapFile bootstrapFile = BootstrapFile.load(file.toString());

        assertEquals(BootstrapFile.Format.JSON, bootstrapFile.format());
        assertEquals("{\"graph\":{}}", bootstrapFile.data());
    }

    @Test
    void nullPathThrows() {
        PMException e = assertThrows(PMException.class, () -> BootstrapFile.load(null));
        assertEquals("No bootstrap file path configured", e.getMessage());
    }

    @Test
    void emptyPathThrows() {
        PMException e = assertThrows(PMException.class, () -> BootstrapFile.load(""));
        assertEquals("No bootstrap file path configured", e.getMessage());
    }

    @Test
    void missingFileThrows() {
        String path = tempDir.resolve("does-not-exist.pml").toString();
        PMException e = assertThrows(PMException.class, () -> BootstrapFile.load(path));
        assertEquals("Bootstrap file not found: " + path, e.getMessage());
    }

    @Test
    void emptyFileThrows() throws IOException {
        Path file = tempDir.resolve("empty.pml");
        Files.writeString(file, "");

        PMException e = assertThrows(PMException.class, () -> BootstrapFile.load(file.toString()));
        assertEquals("Bootstrap file is empty: " + file, e.getMessage());
    }

    @Test
    void unsupportedExtensionThrows() throws IOException {
        Path file = tempDir.resolve("bootstrap.txt");
        Files.writeString(file, "some content");

        PMException e = assertThrows(PMException.class, () -> BootstrapFile.load(file.toString()));
        assertEquals("unsupported bootstrap file type, expected .json or .pml", e.getMessage());
    }
}
