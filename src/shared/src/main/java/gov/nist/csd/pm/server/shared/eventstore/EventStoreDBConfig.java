package gov.nist.csd.pm.server.shared.eventstore;

import javax.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
