package gov.nist.csd.pm.server.admin.config;

import gov.nist.csd.pm.server.shared.config.PDPConfig;
import java.util.Objects;

public class AdminPDPEPPConfig extends PDPConfig {

    private String host;
    private int port;
    private String neo4jDbPath;
    private String bootstrapUser;
    private String bootstrapFilePath;

    public AdminPDPEPPConfig() {
        super();

        this.host = Objects.requireNonNull(System.getenv("PM_ADMIN_PDP_HOST"));
        this.port = Integer.parseInt(Objects.requireNonNull(System.getenv("PM_ADMIN_PDP_PORT")));
        this.neo4jDbPath = Objects.requireNonNull(System.getenv("NEO4J_DB_PATH"));
        this.bootstrapUser = Objects.requireNonNull(System.getenv("PM_BOOTSTRAP_USER"));
        this.bootstrapFilePath = Objects.requireNonNull(System.getenv("PM_BOOTSTRAP_FILE_PATH"));
    }

    public AdminPDPEPPConfig(String esdbStream,
                             String esdbHost,
                             int esdbPort,
                             String esdbHealthCheckUrl,
                             int healthCheckInterval,
                             String host,
                             int port,
                             String neo4jDbPath,
                             String bootstrapUser,
                             String bootstrapFilePath) {
        super(esdbStream, esdbHost, esdbPort, esdbHealthCheckUrl, healthCheckInterval);
        this.host = host;
        this.port = port;
        this.neo4jDbPath = neo4jDbPath;
        this.bootstrapUser = bootstrapUser;
        this.bootstrapFilePath = bootstrapFilePath;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getNeo4jDbPath() {
        return neo4jDbPath;
    }

    public void setNeo4jDbPath(String neo4jDbPath) {
        this.neo4jDbPath = neo4jDbPath;
    }

    public String getBootstrapUser() {
        return bootstrapUser;
    }

    public void setBootstrapUser(String bootstrapUser) {
        this.bootstrapUser = bootstrapUser;
    }

    public String getBootstrapFilePath() {
        return bootstrapFilePath;
    }

    public void setBootstrapFilePath(String bootstrapFilePath) {
        this.bootstrapFilePath = bootstrapFilePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AdminPDPEPPConfig that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return port == that.port && Objects.equals(host, that.host)
            && Objects.equals(bootstrapUser, that.bootstrapUser) && Objects.equals(
            bootstrapFilePath, that.bootstrapFilePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), host, port, bootstrapUser, bootstrapFilePath);
    }
}
