package gov.nist.csd.pm.pdp.resource.eventstore;

import com.eventstore.dbclient.EventData;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.proto.event.PolicyClassCreated;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import gov.nist.csd.pm.pdp.shared.eventstore.SnapshotService;
import gov.nist.csd.pm.pdp.sharedtest.EventStoreTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

	@Nested
	class InitSubscription {

		private EventStoreTestContainer eventStoreTestContainer;
		private CurrentRevisionService currentRevisionService;
		private PAP pap;
		private SnapshotService snapshotService;
		private SubscriptionService subscriptionService;
		private EventStoreConnectionManager eventStoreConnectionManager;
		@Mock
		private PolicyEventSubscriptionListener mockListener;
		private EventStoreDBConfig config;

		@BeforeEach
		void setUp() throws PMException {
			eventStoreTestContainer = new EventStoreTestContainer();
			eventStoreTestContainer.start();

			config = new EventStoreDBConfig(
					"events",
					"snapshots",
					"localhost",
					eventStoreTestContainer.getPort()
			);

			eventStoreConnectionManager = new EventStoreConnectionManager(config);
			currentRevisionService = new CurrentRevisionService();
			pap = new MemoryPAP();
			pap.withIdGenerator((name, type) -> name.hashCode());
			snapshotService = new SnapshotService(config, eventStoreConnectionManager, pap, currentRevisionService);
			subscriptionService = new SubscriptionService(
					eventStoreConnectionManager,
					mockListener,
					config,
					snapshotService,
					currentRevisionService
			);
		}

		@AfterEach
		public void teardown() {
			eventStoreTestContainer.stop();
		}

		@Test
		void whenNoSnapshot_catchesUpFrom0() throws ExecutionException, InterruptedException, PMException, InvalidProtocolBufferException, TimeoutException {
			eventStoreConnectionManager.getOrInitClient()
					.appendToStream(
							config.getEventStream(),
							List.of(
									EventData.builderAsBinary(
											PolicyClassCreated.getDescriptor().getName(),
											PolicyClassCreated.newBuilder()
													.setId("pc1".hashCode())
													.setName("pc1")
													.build()
													.toByteArray()
									).build()
							).iterator()
					)
					.get();

			subscriptionService.initSubscription();
			verify(mockListener, times(1)).onEvent(any(), any());
		}

		@Test
		void whenSnapshotExists_catchesUpFromSnapshotNumber() throws PMException, ExecutionException, InterruptedException, InvalidProtocolBufferException, TimeoutException {
			currentRevisionService.set(1);
			pap.modify().graph().createPolicyClass("pc1");
			eventStoreConnectionManager.getOrInitClient()
					.appendToStream(
							config.getEventStream(),
							List.of(
									EventData.builderAsBinary(
											PolicyClassCreated.getDescriptor().getName(),
											PolicyClassCreated.newBuilder()
													.setId("pc1".hashCode())
													.setName("pc1")
													.build()
													.toByteArray()
									).build()
							).iterator()
					)
					.get();

			snapshotService.snapshot();
			eventStoreConnectionManager.getOrInitClient()
					.appendToStream(
							config.getEventStream(),
							List.of(
									EventData.builderAsBinary(
											PolicyClassCreated.getDescriptor().getName(),
											PolicyClassCreated.newBuilder()
													.setId(2)
													.setName("pc2")
													.build()
													.toByteArray()
									).build()
							).iterator()
					)
					.get();

			subscriptionService.initSubscription();
			verify(mockListener, times(1)).onEvent(any(), any());
		}

		@Test
		void whenCaughtUp_subscriptionReceivesNewEventsAfterLastRevision() throws ExecutionException, InterruptedException, PMException, InvalidProtocolBufferException, TimeoutException {
			PolicyClassCreated pc2 = PolicyClassCreated.newBuilder()
					.setId("pc2".hashCode())
					.setName("pc2")
					.build();
			PMEvent pmEvent = PMEvent.newBuilder()
					.setPolicyClassCreated(pc2)
					.build();
			eventStoreConnectionManager.getOrInitClient()
					.appendToStream(
							config.getEventStream(),
							List.of(
									EventData.builderAsBinary(
											pmEvent.getDescriptorForType().getName(),
											pmEvent.toByteArray()
									).build()
							).iterator()
					)
					.get();

			Thread.sleep(2000);

			subscriptionService.initSubscription();

			Thread.sleep(2000);

					PolicyClassCreated pc3 = PolicyClassCreated.newBuilder()
							.setId("pc3".hashCode())
							.setName("pc3")
							.build();
					PMEvent pmEvent3 = PMEvent.newBuilder()
							.setPolicyClassCreated(pc3)
							.build();

					eventStoreConnectionManager.getOrInitClient()
							.appendToStream(
									config.getEventStream(),
									EventData.builderAsBinary(
											pmEvent3.getDescriptorForType().getName(),
											pmEvent3.toByteArray()
									).build()
							)
							.get();

			Thread.sleep(30000);
			verify(mockListener, times(2)).onEvent(any(), any());
		}
	}
}