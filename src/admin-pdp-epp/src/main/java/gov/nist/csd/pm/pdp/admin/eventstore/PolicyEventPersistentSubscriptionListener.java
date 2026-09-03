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
 * licensed to NIST.
 *
 * This file is part of the admin-pdp-epp module, which compiles against
 * and embeds org.neo4j:neo4j (GPLv3, Community Edition). As a combined work, this
 * module is distributed under the GNU General Public License v3.0, not the CC BY 4.0
 * license used elsewhere in this repository. See admin-pdp-epp/LICENSE for the full text.
 */

package gov.nist.csd.pm.pdp.admin.eventstore;

import com.eventstore.dbclient.*;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.neo4j.embedded.pap.Neo4jEmbeddedPAP;
import gov.nist.csd.pm.pdp.admin.config.AdminPDPConfig;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.PolicyEventHandler;
import gov.nist.csd.pm.pdp.shared.eventstore.SnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import gov.nist.csd.pm.pdp.shared.config.DefaultMode;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@DefaultMode
public class PolicyEventPersistentSubscriptionListener extends PersistentSubscriptionListener {

    private static final Logger logger = LoggerFactory.getLogger(PolicyEventPersistentSubscriptionListener.class);

    private final PolicyEventHandler policyEventHandler;
    private final SnapshotService snapshotService;
    private final CurrentRevisionService currentRevision;
    private final AdminPDPConfig adminPDPConfig;

    public PolicyEventPersistentSubscriptionListener(Neo4jEmbeddedPAP pap,
                                                     CurrentRevisionService currentRevision,
                                                     SnapshotService snapshotService,
                                                     AdminPDPConfig adminPDPConfig) {
        this.policyEventHandler = new PolicyEventHandler(pap, true);
        this.currentRevision = currentRevision;
        this.snapshotService = snapshotService;
        this.adminPDPConfig = adminPDPConfig;
    }

    @Override
    public void onEvent(PersistentSubscription subscription, int retryCount, ResolvedEvent event) {
        RecordedEvent originalEvent = event.getEvent();
        long revision = originalEvent.getRevision();

        try {
            PMEvent pmEvent = PMEvent.parseFrom(originalEvent.getEventData());
            policyEventHandler.handleEvent(pmEvent);
            currentRevision.set(revision);

            subscription.ack(event);
            snapshot(currentRevision.get());
        } catch (PMException | InvalidProtocolBufferException e) {
            logger.error("unexpected error handling event", e);
            subscription.nack(NackAction.Park, e.getMessage(), event);
            // we have parked the event that has an error
            // we need to still advance the revision or else the server will be stuck until the next
            // successful event is handled
            currentRevision.set(revision);
        }
    }

    @Override
    public void onCancelled(PersistentSubscription subscription, Throwable exception) {
        logger.error("subscription cancelled", exception);
    }

    private void snapshot(long revision) {
        if (revision % adminPDPConfig.getSnapshotInterval() == 0) {
            try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> {
                    try {
                        snapshotService.snapshot();
                    } catch (PMException | ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException e) {
                logger.error("snapshot could not be completed", e);
            }
        }
    }
}