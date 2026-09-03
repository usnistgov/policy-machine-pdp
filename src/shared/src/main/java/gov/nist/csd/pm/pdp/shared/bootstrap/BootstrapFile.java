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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reads and validates a policy bootstrap file, exposing its contents and detected format.
 *
 * <p>Shared by the default (Neo4j/EventStoreDB) and sandbox (in-memory) bootstrappers. They apply
 * the policy differently, but parse and validate the file the same way: the path must be configured,
 * the file must exist and be non-empty, and the extension must be a supported format.
 */
public final class BootstrapFile {

    public enum Format {
        PML,
        JSON
    }

    private final Format format;
    private final String data;

    private BootstrapFile(Format format, String data) {
        this.format = format;
        this.data = data;
    }

    public Format format() {
        return format;
    }

    public String data() {
        return data;
    }

    /**
     * Read the bootstrap file at {@code path}, validating that the path is configured, the file exists,
     * is non-empty, and has a supported extension (.pml or .json).
     *
     * @throws PMException if the path is missing, the file does not exist or is empty, or the extension
     *                     is not supported
     * @throws IOException if the file cannot be read
     */
    public static BootstrapFile load(String path) throws PMException, IOException {
        if (path == null || path.isEmpty()) {
            throw new PMException("No bootstrap file path configured");
        }

        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            throw new PMException("Bootstrap file not found: " + path);
        }

        String data = Files.readString(filePath);
        if (data.isEmpty()) {
            throw new PMException("Bootstrap file is empty: " + path);
        }

        if (path.endsWith(".pml")) {
            return new BootstrapFile(Format.PML, data);
        } else if (path.endsWith(".json")) {
            return new BootstrapFile(Format.JSON, data);
        }

        throw new PMException("unsupported bootstrap file type, expected .json or .pml");
    }
}
