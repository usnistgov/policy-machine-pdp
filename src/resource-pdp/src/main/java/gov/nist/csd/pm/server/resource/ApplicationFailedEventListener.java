package gov.nist.csd.pm.server.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationFailedEventListener {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationFailedEventListener.class);

    @EventListener
    public void handleContextFailedEvent(ApplicationFailedEvent event) {
        logger.error("Application failed to start, forcing shutdown", event.getException());
        System.exit(1);
    }
}
