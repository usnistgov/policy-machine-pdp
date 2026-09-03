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

import com.eventstore.dbclient.*;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.serialization.json.JSONDeserializer;
import gov.nist.ngac.pm.core.pap.serialization.json.JSONSerializer;
import gov.nist.csd.pm.pdp.proto.event.PMSnapshot;
import gov.nist.csd.pm.pdp.shared.config.DefaultMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
@DefaultMode
public class SnapshotService {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotService.class);

    private final EventStoreDBConfig eventStoreDBConfig;
    private final PAP pap;
    private final EventStoreConnectionManager eventStoreConnectionManager;
    private final CurrentRevisionService currentRevision;

    public SnapshotService(EventStoreDBConfig eventStoreDBConfig,
                           EventStoreConnectionManager eventStoreConnectionManager,
                           PAP pap,
                           CurrentRevisionService currentRevision) {
        this.eventStoreDBConfig = eventStoreDBConfig;
        this.eventStoreConnectionManager = eventStoreConnectionManager;
        this.pap = pap;
        this.currentRevision = currentRevision;
    }

    public void snapshot() throws PMException, ExecutionException, InterruptedException {
        long revision;
        String json;

        synchronized (pap) {
            revision = currentRevision.get();
            json = pap.serialize(new JSONSerializer());
        }

        PMSnapshot pmSnapshot = PMSnapshot.newBuilder()
                .setJson(json)
                .setRevision(revision)
                .build();
        EventData eventData = EventData.builderAsBinary("PMSnapshot", pmSnapshot.toByteArray()).build();

        eventStoreConnectionManager.getOrInitClient()
                .appendToStream(eventStoreDBConfig.getSnapshotStream(), eventData)
                .get();
    }

    /**
     * Restore policy from snapshot stream and return the latest revision. If no events in stream exist, return -1.
     * @return The latest event revision or -1 if no snapshots.
     */
    public long restoreLatestSnapshot() throws PMException, ExecutionException, InterruptedException,
            InvalidProtocolBufferException {
        ReadStreamOptions options = ReadStreamOptions.get()
                .backwards()
                .maxCount(1)
                .fromEnd();

        ReadResult readResult = eventStoreConnectionManager.getOrInitClient()
                .readStream(eventStoreDBConfig.getSnapshotStream(), options)
                .get();

        List<ResolvedEvent> events = readResult.getEvents();
        if (events.isEmpty()) {
            // return -1 to signify there are no events -- 0 represents the first event
            currentRevision.set(-1);
            return -1;
        }

        ResolvedEvent first = events.getFirst();
        RecordedEvent originalEvent = first.getOriginalEvent();
        byte[] eventData = originalEvent.getEventData();
        PMSnapshot pmSnapshot = PMSnapshot.parseFrom(eventData);

        // restore policy
        synchronized (pap) {
            pap.reset();
            pap.deserialize(pmSnapshot.getJson(), new JSONDeserializer());
        }

        // set current revision to snapshot revision
        currentRevision.set(pmSnapshot.getRevision());

        return pmSnapshot.getRevision();
    }
}
