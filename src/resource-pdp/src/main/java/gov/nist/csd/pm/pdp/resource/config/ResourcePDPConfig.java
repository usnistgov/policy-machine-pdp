package gov.nist.csd.pm.pdp.resource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;

@ConfigurationProperties(prefix = "pm.pdp.resource")
public class ResourcePDPConfig {

    /**
     * Admin PDP host name
     */
    private String adminHostname;

    /**
     * Admin PDP port
     */
    private int adminPort;

    /**
     * The mode of the EPP client: ASYNC, SYNC, or DISABLED. Default is ASYNC.
     */
    private EPPMode eppMode;

    /**
     * The amount of time, in milliseconds, that the service will wait to ensure revision consistency with event store.
     */
    private int revisionConsistencyTimeout;

    public ResourcePDPConfig() {
    }

    public ResourcePDPConfig(String adminHostname, int adminPort, EPPMode eppMode, int revisionConsistencyTimeout) {
        this.adminHostname = adminHostname;
        this.adminPort = adminPort;
        this.eppMode = eppMode;
        this.revisionConsistencyTimeout = revisionConsistencyTimeout;
    }

    @PostConstruct
    public void validate() {
        if (adminHostname == null || adminHostname.isEmpty() || adminHostname.equals("null")) {
            throw new IllegalArgumentException("adminHostname is null or empty");
        }

        if (adminPort == 0) {
            throw new IllegalArgumentException("adminPort is 0");
        }

        if (eppMode == null) {
            this.eppMode = EPPMode.ASYNC;
        }

        if (revisionConsistencyTimeout <= 0) {
            setRevisionConsistencyTimeout(1000);
        }
    }

    public String getAdminHostname() {
        return adminHostname;
    }

    public void setAdminHostname(String adminHostname) {
        this.adminHostname = adminHostname;
    }

    public int getAdminPort() {
        return adminPort;
    }

    public void setAdminPort(int adminPort) {
        this.adminPort = adminPort;
    }

    public EPPMode getEppMode() {
        return eppMode;
    }

    public void setEppMode(EPPMode eppMode) {
        this.eppMode = eppMode;
    }

    public int getRevisionConsistencyTimeout() {
        return revisionConsistencyTimeout;
    }

    public void setRevisionConsistencyTimeout(int revisionConsistencyTimeout) {
        this.revisionConsistencyTimeout = revisionConsistencyTimeout;
    }
}
