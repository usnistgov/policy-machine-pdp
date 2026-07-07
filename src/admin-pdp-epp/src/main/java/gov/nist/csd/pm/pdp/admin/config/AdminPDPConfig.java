package gov.nist.csd.pm.pdp.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;

@ConfigurationProperties(prefix = "pm.pdp.admin")
public class AdminPDPConfig {

    /**
     * Server mode: "default" (Neo4j + EventStoreDB) or "sandbox" (in-memory PAP, no Neo4j/EventStoreDB and
     * no access checks on admin operations).
     */
    private String mode = "default";

    /**
     * Path to store neo4j policy locally
     */
    private String neo4jDbPath;

    /**
     * The file to the policy file to bootstrap the PDP with
     */
    private String bootstrapFilePath;

    /**
     * Name of the event store consumer group
     */
    private String esdbConsumerGroup;

    /**
     * Snapshot event revision interval (e.g. snapshot every 1000 events)
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

    /**
     * The amount of time, in milliseconds, that the service will wait to ensure revision consistency with event store.
     */
    private int revisionConsistencyTimeout;

    @PostConstruct
    public void validate() {
        if (!"default".equals(mode) && !"sandbox".equals(mode)) {
            throw new IllegalArgumentException(
                    "pm.pdp.admin.mode must be 'default' or 'sandbox', got: " + mode);
        }

        if (bootstrapFilePath == null || bootstrapFilePath.isEmpty() || bootstrapFilePath.equals("null")) {
            throw new IllegalArgumentException("bootstrapFilePath is null or empty");
        }

        if ("sandbox".equals(mode)) {
            return;
        }

        if (neo4jDbPath == null || neo4jDbPath.isEmpty() || neo4jDbPath.equals("null")) {
            setNeo4jDbPath("/neo4j");
        }

        if (esdbConsumerGroup == null || esdbConsumerGroup.isEmpty() || esdbConsumerGroup.equals("null")) {
            throw new IllegalStateException("esdbConsumerGroup cannot be null or empty");
        }

        if (snapshotInterval <= 0) {
            setSnapshotInterval(1000);
        }

        if (revisionConsistencyTimeout <= 0) {
            setRevisionConsistencyTimeout(1000);
        }
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getNeo4jDbPath() {
        return neo4jDbPath;
    }

    public void setNeo4jDbPath(String neo4jDbPath) {
        this.neo4jDbPath = neo4jDbPath;
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

    public int getRevisionConsistencyTimeout() {
        return revisionConsistencyTimeout;
    }

    public void setRevisionConsistencyTimeout(int revisionConsistencyTimeout) {
        this.revisionConsistencyTimeout = revisionConsistencyTimeout;
    }
}