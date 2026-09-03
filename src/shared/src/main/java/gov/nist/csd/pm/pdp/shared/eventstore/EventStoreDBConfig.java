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

package gov.nist.csd.pm.pdp.shared.eventstore;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;

@ConfigurationProperties(prefix = "pm.pdp.esdb")
public class EventStoreDBConfig {

    /**
     * Name of the event store stream
     */
    private String eventStream;

    /**
     * Name of the event store stream for snapshots
     */
    private String snapshotStream;

    /**
     * Event store hostname
     */
    private String hostname;

    /**
     * Event store port
     */
    private int port;

    public EventStoreDBConfig() {
    }

    public EventStoreDBConfig(String eventStream,
                              String snapshotStream,
                              String hostname,
                              int port) {
        this.eventStream = eventStream;
        this.snapshotStream = snapshotStream;
        this.hostname = hostname;
        this.port = port;
    }

    @PostConstruct
    public void validate() {
        if (eventStream == null || eventStream.isEmpty() || eventStream.equals("null")) {
            setEventStream("pm-events");
        }

        if (snapshotStream == null || snapshotStream.isEmpty() || snapshotStream.equals("null")) {
            setSnapshotStream("pm-snapshots");
        }

        if (hostname == null || hostname.isEmpty() || hostname.equals("null")) {
            setHostname("localhost");
        }

        if (port == 0) {
            setPort(2113);
        }
    }

    public String getEventStream() {
        return eventStream;
    }

    public void setEventStream(String eventStream) {
        this.eventStream = eventStream;
    }

    public String getSnapshotStream() {
        return snapshotStream;
    }

    public void setSnapshotStream(String snapshotStream) {
        this.snapshotStream = snapshotStream;
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
}
