package gov.nist.csd.pm.pdp.resource.eventstore;

import com.eventstore.dbclient.RecordedEvent;
import com.eventstore.dbclient.ResolvedEvent;
import com.eventstore.dbclient.Subscription;
import com.eventstore.dbclient.SubscriptionListener;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.PolicyEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PolicyEventSubscriptionListener extends SubscriptionListener {

	private static final Logger logger = LoggerFactory.getLogger(PolicyEventSubscriptionListener.class);

	private final PolicyEventHandler policyEventHandler;
	private final CurrentRevisionService currentRevision;

	public PolicyEventSubscriptionListener(PAP pap,
	                                       CurrentRevisionService currentRevision) {
		this.policyEventHandler = new PolicyEventHandler(pap);
		this.currentRevision = currentRevision;
	}

	@Override
	public void onEvent(Subscription subscription, ResolvedEvent event) {
		RecordedEvent recordedEvent = event.getEvent();
		long eventRevision = recordedEvent.getRevision();
		long curRev = currentRevision.get();
		logger.info("onEvent: eventRevision={} type={}", eventRevision, recordedEvent.getEventType());

		if (eventRevision <= curRev) {
			logger.info("already committed revision {}, local copy at revision {}", eventRevision, curRev);
			return;
		}

		handleEvent(eventRevision, recordedEvent.getEventData());
	}

	@Override
	public void onCancelled(Subscription subscription, Throwable exception) {
		logger.error("Subscription cancelled", exception);
	}

	private void handleEvent(long revision, byte[] eventData) {
		try {
			PMEvent pmEvent = PMEvent.parseFrom(eventData);
			policyEventHandler.handleEvent(pmEvent);
			currentRevision.set(revision);
		} catch (PMException | InvalidProtocolBufferException e) {
			logger.error("unexpected error handling event", e);
		}
	}
}
