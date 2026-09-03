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

package gov.nist.csd.pm.pdp.resource.eventstore;

import com.eventstore.dbclient.RecordedEvent;
import com.eventstore.dbclient.ResolvedEvent;
import com.eventstore.dbclient.Subscription;
import com.eventstore.dbclient.SubscriptionListener;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.pap.PAP;
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
		this.policyEventHandler = new PolicyEventHandler(pap, false);
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
