package gov.nist.csd.pm.pdp.shared.eventstore;

import com.eventstore.dbclient.*;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.serialization.json.JSONDeserializer;
import gov.nist.csd.pm.core.pap.serialization.json.JSONSerializer;
import gov.nist.csd.pm.pdp.proto.event.PMSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
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
