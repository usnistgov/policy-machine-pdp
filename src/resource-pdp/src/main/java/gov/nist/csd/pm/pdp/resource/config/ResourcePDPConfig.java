package gov.nist.csd.pm.pdp.resource.config;

import javax.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
     * The timeout that the EPPClient will use when waiting for the current revision to catch up
     * to the side effect revision returned by the EPP. This value will be ignored if eppMode is ASYNC.
     */
    private int eppSideEffectTimeout;

    public ResourcePDPConfig() {
    }

    public ResourcePDPConfig(String adminHostname, int adminPort, EPPMode eppMode, int eppSideEffectTimeout) {
        this.adminHostname = adminHostname;
        this.adminPort = adminPort;
        this.eppMode = eppMode;
        this.eppSideEffectTimeout = eppSideEffectTimeout;
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

    public int getEppSideEffectTimeout() {
        return eppSideEffectTimeout;
    }

    public void setEppSideEffectTimeout(int eppSideEffectTimeout) {
        this.eppSideEffectTimeout = eppSideEffectTimeout;
    }

}
