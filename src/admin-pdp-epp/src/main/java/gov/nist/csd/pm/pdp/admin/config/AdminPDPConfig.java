package gov.nist.csd.pm.pdp.admin.config;

import javax.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pm.pdp.admin")
public class AdminPDPConfig {

    /**
     * Path to store neo4j policy locally
     */
    private String neo4jDbPath;

    /**
     * The user bootstrapping the policy
     */
    private String bootstrapUser;

    /**
     * The file to the policy file to bootstrap the PDP with
     */
    private String bootstrapFilePath;

    /**
     * Name of the event store consumer group
     */
    private String esdbConsumerGroup;

    /**
     * Name of the event store stream for snapshots
     */
    private int snapshotInterval;

    /**
     * Shutdown the server once the bootstrap process is complete
     */
    private boolean shutdownAfterBootstrap;

    /**
     * Directory path containing plugin JAR files for Operations and Routines
     */
    private String pluginsDir;

    @PostConstruct
    public void validate() {
        if (neo4jDbPath == null || neo4jDbPath.isEmpty() || neo4jDbPath.equals("null")) {
            setNeo4jDbPath("/neo4j");
        }

        if (bootstrapFilePath == null || bootstrapFilePath.isEmpty() || bootstrapFilePath.equals("null")) {
            throw new IllegalArgumentException("bootstrapFilePath is null or empty");
        }

        if (esdbConsumerGroup == null || esdbConsumerGroup.isEmpty() || esdbConsumerGroup.equals("null")) {
            throw new IllegalStateException("esdbConsumerGroup cannot be null or empty");
        }

        if (snapshotInterval <= 0) {
            setSnapshotInterval(1000);
        }
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

    public String getEsdbConsumerGroup() {
        return esdbConsumerGroup;
    }

    public void setEsdbConsumerGroup(String esdbConsumerGroup) {
        this.esdbConsumerGroup = esdbConsumerGroup;
    }

    public int getSnapshotInterval() {
        return snapshotInterval;
    }

    public void setSnapshotInterval(int snapshotInterval) {
        this.snapshotInterval = snapshotInterval;
    }

    public boolean isShutdownAfterBootstrap() {
        return shutdownAfterBootstrap;
    }

    public void setShutdownAfterBootstrap(boolean shutdownAfterBootstrap) {
        this.shutdownAfterBootstrap = shutdownAfterBootstrap;
    }

    public String getPluginsDir() {
        return pluginsDir;
    }

    public void setPluginsDir(String pluginsDir) {
        this.pluginsDir = pluginsDir;
    }
}