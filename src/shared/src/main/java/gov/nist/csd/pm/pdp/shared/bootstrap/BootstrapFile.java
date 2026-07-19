package gov.nist.csd.pm.pdp.shared.bootstrap;

import gov.nist.csd.pm.core.common.exception.PMException;

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
