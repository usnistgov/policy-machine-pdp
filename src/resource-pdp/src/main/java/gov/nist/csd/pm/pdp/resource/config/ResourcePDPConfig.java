/*
 * This Software (Policy Machine) is being made available as a public service by the
 * National Institute of Standards and Technology (NIST), an Agency of the United
 * States Department of Commerce. This software was developed in part by employees of
 * NIST and in part by NIST contractors. Copyright in portions of this software that
 * were developed by NIST contractors has been licensed or assigned to NIST. Pursuant
 * to Title 17 United States Code Section 105, works of NIST employees are not
 * subject to copyright protection in the United States. However, NIST may hold
 * international copyright in software created by its employees and domestic
 * copyright (or licensing rights) in portions of software that were assigned or
 * licensed to NIST. To the extent that NIST holds copyright in this software, it is
 * being made available under the Creative Commons Attribution 4.0 International
 * license (CC BY 4.0). The disclaimers of the CC BY 4.0 license apply to all parts
 * of the software developed or licensed by NIST.
 *
 * ACCESS THE FULL CC BY 4.0 LICENSE HERE:
 * https://creativecommons.org/licenses/by/4.0/legalcode
 */

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
     * The amount of time, in milliseconds, that the service will wait to ensure revision consistency with event store.
     */
    private int revisionConsistencyTimeout;

    /**
     * Directory path containing plugin JAR files for Operations and Routines
     */
    private String pluginsDir;

    public ResourcePDPConfig() {
    }

    public ResourcePDPConfig(String adminHostname, int adminPort, int revisionConsistencyTimeout) {
        this.adminHostname = adminHostname;
        this.adminPort = adminPort;
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

    public int getRevisionConsistencyTimeout() {
        return revisionConsistencyTimeout;
    }

    public void setRevisionConsistencyTimeout(int revisionConsistencyTimeout) {
        this.revisionConsistencyTimeout = revisionConsistencyTimeout;
    }

    public String getPluginsDir() {
        return pluginsDir;
    }

    public void setPluginsDir(String pluginsDir) {
        this.pluginsDir = pluginsDir;
    }
}
