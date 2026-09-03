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
 * licensed to NIST. To the extent that NIST holds copyright in this software, it is
 * being made available under the Creative Commons Attribution 4.0 International
 * license (CC BY 4.0). The disclaimers of the CC BY 4.0 license apply to all parts
 * of the software developed or licensed by NIST.
 *
 * ACCESS THE FULL CC BY 4.0 LICENSE HERE:
 * https://creativecommons.org/licenses/by/4.0/legalcode
 */

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
