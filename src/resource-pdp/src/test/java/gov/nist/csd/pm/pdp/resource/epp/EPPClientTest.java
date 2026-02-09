package gov.nist.csd.pm.pdp.resource.epp;

import gov.nist.csd.pm.core.common.event.EventContext;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.resource.config.EPPMode;
import gov.nist.csd.pm.pdp.resource.config.ResourcePDPConfig;
import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;
import gov.nist.csd.pm.proto.v1.epp.EPPServiceGrpc;
import gov.nist.csd.pm.proto.v1.epp.ProcessEventResponse;
import gov.nist.csd.pm.proto.v1.model.Value;
import gov.nist.csd.pm.proto.v1.model.ValueMap;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EPPClientTest {

	@Mock private PDP pdp;
	@Mock private PAP pap;
	@Mock private ResourcePDPConfig resourcePDPConfig;

	@Mock private EPPServiceGrpc.EPPServiceBlockingStub blockingStub;
	@Mock private EPPServiceGrpc.EPPServiceStub asyncStub;

	private EPPClient client;

	@BeforeEach
	void setUp() {
		client = new EPPClient(pdp, pap, resourcePDPConfig);
	}

	@Test
	void subscribeToPDP_whenDisabled_doesNotRegisterSubscriber() {
		when(resourcePDPConfig.getEppMode()).thenReturn(EPPMode.DISABLED);

		client.subscribeToPDP();

		verify(pdp, never()).addEventSubscriber(any());
	}

	@Test
	void subscribeToPDP_whenAsync_setsAsyncStub_andRegistersSubscriber() throws Exception {
		when(resourcePDPConfig.getEppMode()).thenReturn(EPPMode.ASYNC);
		when(resourcePDPConfig.getAdminHostname()).thenReturn("localhost");
		when(resourcePDPConfig.getAdminPort()).thenReturn(50051);

		ManagedChannelBuilder builder = mock(ManagedChannelBuilder.class);
		ManagedChannel channel = mock(ManagedChannel.class);

		when(builder.defaultServiceConfig(anyMap())).thenReturn(builder);
		when(builder.enableRetry()).thenReturn(builder);
		when(builder.usePlaintext()).thenReturn(builder);
		when(builder.build()).thenReturn(channel);

		try (MockedStatic<ManagedChannelBuilder> mcb = mockStatic(ManagedChannelBuilder.class);
		     MockedStatic<EPPServiceGrpc> grpc = mockStatic(EPPServiceGrpc.class)) {

			mcb.when(() -> ManagedChannelBuilder.forAddress("localhost", 50051)).thenReturn((ManagedChannelBuilder) builder);
			grpc.when(() -> EPPServiceGrpc.newStub(channel)).thenReturn(asyncStub);

			client.subscribeToPDP();

			verify(pdp).addEventSubscriber(client);
			grpc.verify(() -> EPPServiceGrpc.newStub(channel));

			assertNotNull(readField(client, "stub"));
			assertNull(readField(client, "blockingStub"));
		}
	}

	@Test
	void subscribeToPDP_whenSync_setsBlockingStub_andRegistersSubscriber() throws Exception {
		when(resourcePDPConfig.getEppMode()).thenReturn(EPPMode.SYNC);
		when(resourcePDPConfig.getAdminHostname()).thenReturn("localhost");
		when(resourcePDPConfig.getAdminPort()).thenReturn(50051);

		ManagedChannelBuilder builder = mock(ManagedChannelBuilder.class);
		ManagedChannel channel = mock(ManagedChannel.class);

		when(builder.defaultServiceConfig(anyMap())).thenReturn(builder);
		when(builder.enableRetry()).thenReturn(builder);
		when(builder.usePlaintext()).thenReturn(builder);
		when(builder.build()).thenReturn(channel);

		try (MockedStatic<ManagedChannelBuilder> mcb = mockStatic(ManagedChannelBuilder.class);
		     MockedStatic<EPPServiceGrpc> grpc = mockStatic(EPPServiceGrpc.class)) {

			mcb.when(() -> ManagedChannelBuilder.forAddress("localhost", 50051)).thenReturn((ManagedChannelBuilder) builder);
			grpc.when(() -> EPPServiceGrpc.newBlockingStub(channel)).thenReturn(blockingStub);

			client.subscribeToPDP();

			verify(pdp).addEventSubscriber(client);
			grpc.verify(() -> EPPServiceGrpc.newBlockingStub(channel));

			assertNull(readField(client, "stub"));
			assertNotNull(readField(client, "blockingStub"));
		}
	}

	@Test
	void processEvent_whenDisabled_shortCircuits() throws Exception {
		when(resourcePDPConfig.getEppMode()).thenReturn(EPPMode.DISABLED);

		writeField(client, "stub", asyncStub);
		writeField(client, "blockingStub", blockingStub);

		client.processEvent(mock(EventContext.class));

		verifyNoInteractions(asyncStub, blockingStub);
	}

	@Test
	void processEvent_whenAsync_forwardsEventToAsyncStub() throws Exception {
		when(resourcePDPConfig.getEppMode()).thenReturn(EPPMode.ASYNC);
		writeField(client, "stub", asyncStub);
		writeField(client, "blockingStub", null);

		EventContext eventCtx = mock(EventContext.class);
		gov.nist.csd.pm.proto.v1.epp.EventContext protoCtx = mock(gov.nist.csd.pm.proto.v1.epp.EventContext.class);

		try (MockedStatic<ProtoUtil> protoUtil = mockStatic(ProtoUtil.class)) {
			protoUtil.when(() -> ProtoUtil.toEventContextProto(eventCtx)).thenReturn(protoCtx);

			client.processEvent(eventCtx);

			verify(asyncStub).processEvent(eq(protoCtx), any(StreamObserver.class));
		}
	}

	@Test
	void processEvent_whenSync_andNoResult_doesNothingExtra() throws Exception {
		when(resourcePDPConfig.getEppMode()).thenReturn(EPPMode.SYNC);
		writeField(client, "stub", null);
		writeField(client, "blockingStub", blockingStub);

		EventContext eventCtx = mock(EventContext.class);
		gov.nist.csd.pm.proto.v1.epp.EventContext protoCtx = mock(gov.nist.csd.pm.proto.v1.epp.EventContext.class);

		ProcessEventResponse response = ProcessEventResponse.newBuilder().build();
		when(blockingStub.processEvent(protoCtx)).thenReturn(response);

		try (MockedStatic<ProtoUtil> protoUtil = mockStatic(ProtoUtil.class)) {
			protoUtil.when(() -> ProtoUtil.toEventContextProto(eventCtx)).thenReturn(protoCtx);

			client.processEvent(eventCtx);

			verify(blockingStub).processEvent(protoCtx);
		}
	}

	@Test
	void processEvent_whenSync_andResultContainsLastRevision_waitsForCatchUp() throws Exception {
		when(resourcePDPConfig.getEppMode()).thenReturn(EPPMode.SYNC);
		writeField(client, "stub", null);
		writeField(client, "blockingStub", blockingStub);

		EventContext eventCtx = mock(EventContext.class);
		gov.nist.csd.pm.proto.v1.epp.EventContext protoCtx = mock(gov.nist.csd.pm.proto.v1.epp.EventContext.class);

		when(blockingStub.processEvent(protoCtx)).thenReturn(responseWithLastRevision(5));

		try (MockedStatic<ProtoUtil> protoUtil = mockStatic(ProtoUtil.class)) {
			protoUtil.when(() -> ProtoUtil.toEventContextProto(eventCtx)).thenReturn(protoCtx);

			client.processEvent(eventCtx);

			verify(blockingStub).processEvent(protoCtx);
		}
	}

	@Test
	void grpcConfigMap_containsRetryPolicyBasics() throws Exception {
		Map<String, Object> cfg = callBuildGrpcConfigMap(client);

		assertTrue(cfg.containsKey("loadBalancingConfig"));
		assertTrue(cfg.containsKey("methodConfig"));

		assertTrue(cfg.get("loadBalancingConfig") instanceof List);
		assertTrue(cfg.get("methodConfig") instanceof List);

		@SuppressWarnings("unchecked")
		Map<String, Object> methodConfigEntry = (Map<String, Object>) ((List<?>) cfg.get("methodConfig")).get(0);

		@SuppressWarnings("unchecked")
		Map<String, Object> retryPolicy = (Map<String, Object>) methodConfigEntry.get("retryPolicy");

		assertEquals("3", retryPolicy.get("maxAttempts"));
		assertEquals("0.2s", retryPolicy.get("initialBackoff"));
		assertEquals("10s", retryPolicy.get("maxBackoff"));
		assertEquals(1.5, retryPolicy.get("backoffMultiplier"));
		assertEquals(List.of("UNAVAILABLE"), retryPolicy.get("retryableStatusCodes"));
	}

	private static ProcessEventResponse responseWithLastRevision(long revision) {
		ValueMap resultMap = ValueMap.newBuilder()
				.putValues("last_event_revision", Value.newBuilder().setInt64Value(revision).build())
				.build();

		return ProcessEventResponse.newBuilder()
				.setResult(resultMap)
				.build();
	}

	private static Object readField(Object target, String fieldName) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.get(target);
	}

	private static void writeField(Object target, String fieldName, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> callBuildGrpcConfigMap(EPPClient target) throws Exception {
		Method method = EPPClient.class.getDeclaredMethod("buildGrpcConfigMap");
		method.setAccessible(true);
		return (Map<String, Object>) method.invoke(target);
	}
}