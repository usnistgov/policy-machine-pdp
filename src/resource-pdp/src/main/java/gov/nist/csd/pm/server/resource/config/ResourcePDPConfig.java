package gov.nist.csd.pm.server.resource.config;

import javax.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pm.pdp.resource")
public class ResourcePDPConfig {

    /**
     * Resource PDP hostname
     */
    private String hostname;

    /**
     * Resource PDP port
     */
    private int port;

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

    public ResourcePDPConfig() {
    }

    public ResourcePDPConfig(String hostname,
                             int port,
                             String adminHostname,
                             int adminPort,
                             boolean eppAsync,
                             int eppSideEffectTimeout) {
        this.hostname = hostname;
        this.port = port;
        this.adminHostname = adminHostname;
        this.adminPort = adminPort;
        this.eppAsync = eppAsync;
        this.eppSideEffectTimeout = eppSideEffectTimeout;
    }

    @PostConstruct
    public void validate() {
        if (hostname == null || hostname.isEmpty() || hostname.equals("null")) {
            setHostname("localhost");
        }

        if (port == 0) {
            setPort(50051);
        }

        if (adminHostname == null || adminHostname.isEmpty() || adminHostname.equals("null")) {
            setAdminHostname("localhost");
        }

        if (adminPort == 0) {
            setAdminPort(50052);
        }
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
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
}
