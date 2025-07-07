package gov.nist.csd.pm.pdp.shared.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;

@ConfigurationProperties(prefix = "pm.pdp.plugins")
public class PluginLoaderConfig {

    /**
     * Directory path containing plugin JAR files for Operations and Routines
     */
    private String pluginDirectory;

    public PluginLoaderConfig() {
        this.pluginDirectory = "./plugins";
    }

    public PluginLoaderConfig(String pluginDirectory) {
        this.pluginDirectory = pluginDirectory;
    }

    @PostConstruct
    public void validate() {
        if (pluginDirectory == null || pluginDirectory.isEmpty()) {
            pluginDirectory = "./plugins";
        }
    }

    public String getPluginDirectory() {
        return pluginDirectory;
    }

    public void setPluginDirectory(String pluginDirectory) {
        this.pluginDirectory = pluginDirectory;
    }
} 