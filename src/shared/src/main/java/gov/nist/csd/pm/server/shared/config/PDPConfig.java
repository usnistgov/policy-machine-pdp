package gov.nist.csd.pm.server.shared.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pm.pdp")
public class PDPConfig {

    private String esdbStream;
    private String esdbConsumerGroup;
    private String esdbHost;
    private Integer esdbPort;
    private String resourceHost;
    private Integer resourcePort;
    private String adminHost;
    private Integer adminPort;

    public PDPConfig() {
    }

    public String getEsdbStream() {
        if (esdbStream == null || esdbStream.isEmpty() || esdbStream.equals("null")) {
            throw new IllegalStateException("esdbStream cannot be null or empty");
        }

        return esdbStream;
    }

    public void setEsdbStream(String esdbStream) {
        this.esdbStream = esdbStream;
    }

    public String getEsdbConsumerGroup() {
        if (esdbConsumerGroup == null || esdbConsumerGroup.isEmpty() || esdbConsumerGroup.equals("null")) {
            throw new IllegalStateException("esdbConsumerGroup cannot be null or empty");
        }

        return esdbConsumerGroup;
    }

    public void setEsdbConsumerGroup(String esdbConsumerGroup) {
        this.esdbConsumerGroup = esdbConsumerGroup;
    }

    public String getEsdbHost() {
        if (esdbHost == null || esdbHost.isEmpty() || esdbHost.equals("null")) {
            throw new IllegalStateException("esdbHost cannot be null or empty");
        }

        return esdbHost;
    }

    public void setEsdbHost(String esdbHost) {
        this.esdbHost = esdbHost;
    }

    public Integer getEsdbPort() {
        if (esdbPort == null || esdbPort == 0) {
            throw new IllegalStateException("esdbPort cannot be null or 0");
        }

        return esdbPort;
    }

    public void setEsdbPort(Integer esdbPort) {
        this.esdbPort = esdbPort;
    }

    public String getResourceHost() {
        if (resourceHost == null || resourceHost.isEmpty() || resourceHost.equals("null")) {
            throw new IllegalStateException("resourceHost cannot be null or empty");
        }

        return resourceHost;
    }

    public void setResourceHost(String resourceHost) {
        this.resourceHost = resourceHost;
    }

    public Integer getResourcePort() {
        if (resourcePort == null || resourcePort == 0) {
            throw new IllegalStateException("resourcePort cannot be null or empty");
        }

        return resourcePort;
    }

    public void setResourcePort(Integer resourcePort) {
        this.resourcePort = resourcePort;
    }

    public String getAdminHost() {
        if (adminHost == null || adminHost.isEmpty() || adminHost.equals("null")) {
            throw new IllegalStateException("adminHost cannot be null or empty");
        }

        return adminHost;
    }

    public void setAdminHost(String adminHost) {
        this.adminHost = adminHost;
    }

    public Integer getAdminPort() {
        if (adminPort == null || adminPort == 0) {
            throw new IllegalStateException("adminPort cannot be null or empty");
        }

        return adminPort;
    }

    public void setAdminPort(Integer adminPort) {
        this.adminPort = adminPort;
    }

    @Override
    public String toString() {
        return "BaseConfig{" +
            "esdbStream='" + esdbStream + '\'' +
            ", esdbConsumerGroup='" + esdbConsumerGroup + '\'' +
            ", esdbHost='" + esdbHost + '\'' +
            ", esdbPort=" + esdbPort +
            ", resourceHost='" + resourceHost + '\'' +
            ", resourcePort=" + resourcePort +
            ", adminHost='" + adminHost + '\'' +
            ", adminPort=" + adminPort +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PDPConfig that)) {
            return false;
        }
        return esdbPort == that.esdbPort && resourcePort == that.resourcePort && adminPort == that.adminPort
            && Objects.equals(esdbStream, that.esdbStream) && Objects.equals(esdbConsumerGroup,
            that.esdbConsumerGroup) && Objects.equals(esdbHost, that.esdbHost) && Objects.equals(
            resourceHost, that.resourceHost) && Objects.equals(adminHost, that.adminHost);
    }

    @Override
    public int hashCode() {
        return Objects.hash(esdbStream, esdbConsumerGroup, esdbHost, esdbPort, resourceHost, resourcePort, adminHost,
            adminPort);
    }
}
