package gov.nist.csd.pm.pdp.shared.eventstore;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.ReadResult;
import com.eventstore.dbclient.ReadStreamOptions;
import com.eventstore.dbclient.ResolvedEvent;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.node.NodeType;
import gov.nist.csd.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.core.pap.query.GraphQuery;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pap.serialization.json.JSONDeserializer;
import gov.nist.csd.pm.pdp.proto.event.PMSnapshot;
import gov.nist.csd.pm.pdp.sharedtest.EventStoreTestContainer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotServiceTest {

	@Test
	void snapshot_Success() throws PMException, ExecutionException, InterruptedException, InvalidProtocolBufferException {
		// create test event store container
		try (EventStoreTestContainer eventStoreTestContainer = new EventStoreTestContainer()) {
			eventStoreTestContainer.start();

			MemoryPAP pap = new MemoryPAP();
			pap.executePML(new UserContext(0), """
					create pc "pc1"
					create ua "ua1" in ["pc1"]
					create oa "oa1" in ["pc1"]
					create u "u1" in ["ua1"]
					create o "o1" in ["oa1"]
					""");

			// create Snapshot Service
			EventStoreDBConfig config = new EventStoreDBConfig(
					"test-events",
					"test-snapshots",
					eventStoreTestContainer.getHost(),
					eventStoreTestContainer.getPort()
			);

			EventStoreConnectionManager eventStoreConnectionManager = new EventStoreConnectionManager(config);

			SnapshotService snapshotService = new SnapshotService(
					config,
					eventStoreConnectionManager,
					pap,
					new CurrentRevisionService()
			);

			snapshotService.snapshot();

			// get from snapshot stream and deserialize
			EventStoreDBClient client = eventStoreConnectionManager.getOrInitClient();
			ReadResult readResult = client.readStream(
					config.getSnapshotStream(),
					ReadStreamOptions.get().fromStart()
			).get();
			List<ResolvedEvent> events = readResult.getEvents();
			assertEquals(1, events.size());

			PMSnapshot pmSnapshot = PMSnapshot.parseFrom(events.getFirst().getEvent().getEventData());
			MemoryPAP snapshotPAP = new MemoryPAP();
			snapshotPAP.deserialize(pmSnapshot.getJson(), new JSONDeserializer());

			GraphQuery graph = pap.query().graph();
			GraphQuery snapshotGraph = snapshotPAP.query().graph();
			assertEquals(graph.search(NodeType.ANY, new HashMap<>()), snapshotGraph.search(NodeType.ANY, new HashMap<>()));
		}
	}

	@Test
	void  snapshot_whenEventStoreIsUnavailable_exceptionThrown() throws PMException, ExecutionException, InterruptedException, InvalidProtocolBufferException {
		// create test event store container
		try (EventStoreTestContainer eventStoreTestContainer = new EventStoreTestContainer()) {
			MemoryPAP pap = new MemoryPAP();
			pap.executePML(new UserContext(0), """
					create pc "pc1"
					create ua "ua1" in ["pc1"]
					create oa "oa1" in ["pc1"]
					create u "u1" in ["ua1"]
					create o "o1" in ["oa1"]
					""");

			// create Snapshot Service
			EventStoreDBConfig config = new EventStoreDBConfig(
					"test-events",
					"test-snapshots",
					"localhost",
					0
			);

			EventStoreConnectionManager eventStoreConnectionManager = new EventStoreConnectionManager(config);

			SnapshotService snapshotService = new SnapshotService(
					config,
					eventStoreConnectionManager,
					pap,
					new CurrentRevisionService()
			);

			ExecutionException e = assertThrows(
					ExecutionException.class,
					() -> snapshotService.snapshot()
			);
			assertTrue(e.getMessage().contains("connection is closed"));
		}
	}

	@Test
	void restoreLatestSnapshot_Success() throws PMException, ExecutionException, InterruptedException, InvalidProtocolBufferException {
		try (EventStoreTestContainer eventStoreTestContainer = new EventStoreTestContainer()) {
			eventStoreTestContainer.start();

			MemoryPAP pap = new MemoryPAP();
			pap.executePML(new UserContext(0), """
					create pc "pc1"
					create ua "ua1" in ["pc1"]
					create oa "oa1" in ["pc1"]
					create u "u1" in ["ua1"]
					create o "o1" in ["oa1"]
					""");

			EventStoreDBConfig config = new EventStoreDBConfig(
					"test-events",
					"test-snapshots",
					eventStoreTestContainer.getHost(),
					eventStoreTestContainer.getPort()
			);
			EventStoreConnectionManager eventStoreConnectionManager = new EventStoreConnectionManager(config);
			CurrentRevisionService currentRevisionService = new CurrentRevisionService();
			SnapshotService snapshotService = new SnapshotService(
					config,
					eventStoreConnectionManager,
					pap,
					currentRevisionService
			);
			currentRevisionService.set(1);

			snapshotService.snapshot();
			pap.modify().graph().deleteNode(pap.query().graph().getNodeId("o1"));
			currentRevisionService.set(2);
			snapshotService.snapshot();
			pap.modify().graph().createObject("o1", List.of(pap.query().graph().getNodeId("oa1")));

			snapshotService.restoreLatestSnapshot();

			assertTrue(pap.query().graph().nodeExists("pc1"));
			assertFalse(pap.query().graph().nodeExists("o1"));
			assertEquals(2, currentRevisionService.get());
		}
	}

}