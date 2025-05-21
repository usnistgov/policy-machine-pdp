package gov.nist.csd.pm.server.resource.eventstore;

import static org.mockito.Mockito.*;

import com.eventstore.dbclient.*;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.proto.event.PolicyClassCreated;
import gov.nist.csd.pm.server.shared.eventstore.*;
import gov.nist.csd.pm.server.sharedtest.TestEventStoreContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ExecutionException;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

	@Nested
	class InitSubscription {

		private TestEventStoreContainer testEventStoreContainer;
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
			testEventStoreContainer = new TestEventStoreContainer();
			testEventStoreContainer.start();

			config = new EventStoreDBConfig(
					"events",
					"snapshots",
					"localhost",
					testEventStoreContainer.getPort()
			);

			eventStoreConnectionManager = new EventStoreConnectionManager(config);
			currentRevisionService = new CurrentRevisionService();
			pap = new MemoryPAP();
			pap.withIdGenerator((name, type) -> name.hashCode());
			snapshotService = new SnapshotService(config, eventStoreConnectionManager, pap, currentRevisionService);
			subscriptionService = new SubscriptionService(
					eventStoreConnectionManager,
					mockListener,
					//new PolicyEventSubscriptionListener(pap, currentRevisionService),
					config,
					snapshotService,
					currentRevisionService
			);
		}

		@AfterEach
		public void teardown() {
			testEventStoreContainer.stop();
		}

		@Test
		void whenNoSnapshot_catchesUpFrom0() throws ExecutionException, InterruptedException {
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
		void whenSnapshotExists_catchesUpFromSnapshotNumber() throws PMException, ExecutionException, InterruptedException {
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
		void whenCaughtUp_subscriptionReceivesNewEventsAfterLastRevision() throws ExecutionException, InterruptedException {
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