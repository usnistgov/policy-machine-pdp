package gov.nist.csd.pm.pdp.resource.eventstore;

import com.eventstore.dbclient.*;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.PolicyEventHandler;
import gov.nist.csd.pm.pdp.shared.plugin.PluginLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class PolicyEventSubscriptionListener extends SubscriptionListener {

	private static final Logger logger = LoggerFactory.getLogger(PolicyEventSubscriptionListener.class);

	private final PolicyEventHandler policyEventHandler;
	private final CurrentRevisionService currentRevision;
	private final Map<Long, List<PMEvent>> queuedTxs;
	private final Map<Long, CompletableFuture<Void>> pendingTxs;

	public PolicyEventSubscriptionListener(PAP pap,
	                                       CurrentRevisionService currentRevision) {
		this.policyEventHandler = new PolicyEventHandler(pap);
		this.currentRevision = currentRevision;
		this.queuedTxs = new HashMap<>();
		this.pendingTxs = new HashMap<>();
	}

	public CompletableFuture<Void> processOrQueue(long startRevision, List<PMEvent> events) {
		CompletableFuture<Void> txProcessed = new CompletableFuture<>();

		// synchronize this to avoid processes steeping on each other to add to the queue
		// as well as preventing events from being handled in onEvent while a tx is being processed here
		synchronized (this) {
			long curRev = currentRevision.get();

			// if this tx can be applied now, apply and do not add to the queue
			// update the current startRevision to startRevision + events.size()
			// eventstoredb guarantees the events are added in sequential order
			if (curRev == startRevision-1) {
				logger.info("Processing {} events from revision {}", events.size(), curRev);
				handleTxEvents(startRevision, events);
				txProcessed.complete(null);
			} else {
				logger.info("Queued {} events from revision {}", events.size(), curRev);
				pendingTxs.put(startRevision, txProcessed);
				queuedTxs.put(startRevision, events);
			}
		}

		return txProcessed;
	}

	@Override
	public void onEvent(Subscription subscription, ResolvedEvent event) {
		RecordedEvent recordedEvent = event.getEvent();
		long eventRevision = recordedEvent.getRevision();
		long curRev = currentRevision.get();
		logger.info("onEvent: eventRevision={} type={}", eventRevision, recordedEvent.getEventType());

		// ignore revisions that have already been applied via processOrQueue
		if (eventRevision <= curRev) {
			logger.info("already committed revision {}, local copy at revision {}", eventRevision, curRev);
			return;
		}

		synchronized (this) {
			List<PMEvent> pmEvents = queuedTxs.get(eventRevision);

			// if there exists a queued transaction that starts at the event's revision number
			// handle the queued tx instead of passed event.
			if (pmEvents == null) {
				handleEvent(eventRevision, recordedEvent.getEventData());

				// check that this event is not the trigger for a queued tx
				pmEvents = queuedTxs.get(eventRevision+1);
				if (pmEvents == null) {
					return;
				}
			}

			handleTxEvents(eventRevision+1, pmEvents);
		}
	}

	@Override
	public void onCancelled(Subscription subscription, Throwable exception) {
		logger.error("Subscription cancelled", exception);
	}

	private void handleTxEvents(long revision, List<PMEvent> events) {
		try {
			policyEventHandler.handleEvents(events);
			currentRevision.set(revision + (events.size()-1));

			CompletableFuture<Void> txProcessed = pendingTxs.remove(revision);
			if (txProcessed != null) {
				txProcessed.complete(null);
			}
		} catch (PMException e) {
			logger.error("error handling queued tx events at revision {}", revision, e);
		}
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
