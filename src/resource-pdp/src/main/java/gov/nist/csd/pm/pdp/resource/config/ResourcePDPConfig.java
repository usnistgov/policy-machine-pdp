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
     * If true, configure the EPP to process events asynchronously. If true, EPP side effect events will be processed
     * once received from the event store.
     */
    private boolean eppAsync;

    /**
     * The timeout that the EPPClient will use when waiting for the current revision to catch up
     * to the side effect revision returned by the EPP. This value will be ignored if eppAsync is true.
     */
    private int eppSideEffectTimeout;

    /**
     * If true, the server will not send event contexts to the EPP.
     */
    private boolean disableEpp;

    public ResourcePDPConfig() {
    }

    public ResourcePDPConfig(String adminHostname, int adminPort, boolean eppAsync, int eppSideEffectTimeout,
                             boolean disableEpp) {
        this.adminHostname = adminHostname;
        this.adminPort = adminPort;
        this.eppAsync = eppAsync;
        this.eppSideEffectTimeout = eppSideEffectTimeout;
        this.disableEpp = disableEpp;
    }

    @PostConstruct
    public void validate() {
        if (adminHostname == null || adminHostname.isEmpty() || adminHostname.equals("null")) {
            throw new IllegalArgumentException("adminHostname is null or empty");
        }

        if (adminPort == 0) {
            throw new IllegalArgumentException("adminPort is 0");
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

    public boolean isEppAsync() {
        return eppAsync;
    }

    public void setEppAsync(boolean eppAsync) {
        this.eppAsync = eppAsync;
    }

    public int getEppSideEffectTimeout() {
        return eppSideEffectTimeout;
    }

    public void setEppSideEffectTimeout(int eppSideEffectTimeout) {
        this.eppSideEffectTimeout = eppSideEffectTimeout;
    }

    public boolean isDisableEpp() {
        return disableEpp;
    }

    public void setDisableEpp(boolean disableEpp) {
        this.disableEpp = disableEpp;
    }
}
