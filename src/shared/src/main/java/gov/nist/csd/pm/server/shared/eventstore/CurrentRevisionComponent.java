package gov.nist.csd.pm.server.shared.eventstore;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class CurrentRevisionComponent {

    private AtomicLong currentRevision;

    public CurrentRevisionComponent() {
        currentRevision = new AtomicLong();
    }

    public long getCurrentRevision() {
        return currentRevision.get();
    }

    public void setCurrentRevision(long currentRevision) {
        this.currentRevision.set(currentRevision);
    }
}
