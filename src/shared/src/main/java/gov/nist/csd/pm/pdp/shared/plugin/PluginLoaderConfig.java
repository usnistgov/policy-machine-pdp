package gov.nist.csd.pm.pdp.shared.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;

@ConfigurationProperties(prefix = "pm.pdp.plugins")
public class PluginLoaderConfig {

    /**
     * Directory path containing plugin JAR files for Operations and Routines
     */
    private String dir;

    public PluginLoaderConfig() {
    }

    public PluginLoaderConfig(String dir) {
        this.dir = dir;
    }

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }
} 