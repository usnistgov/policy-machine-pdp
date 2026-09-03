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

package gov.nist.csd.pm.pdp.resource.epp;

import gov.nist.ngac.pm.core.epp.EventContext;
import gov.nist.ngac.pm.core.grpc.util.ToProtoUtil;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.resource.config.ResourcePDPConfig;
import gov.nist.ngac.pm.proto.v1.epp.EPPServiceGrpc;
import gov.nist.ngac.pm.proto.v1.epp.ProcessEventResponse;
import gov.nist.ngac.pm.proto.v1.model.Value;
import gov.nist.ngac.pm.proto.v1.model.ValueMap;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EPPClientTest {

	@Mock private PDP pdp;
	@Mock private PAP pap;
	@Mock private ResourcePDPConfig resourcePDPConfig;

	@Mock private EPPServiceGrpc.EPPServiceBlockingStub blockingStub;

	private EPPClient client;

	@BeforeEach
	void setUp() {
		client = new EPPClient(pdp, pap, resourcePDPConfig);
	}

	@Test
	void subscribeToPDP_whenSync_setsBlockingStub_andRegistersSubscriber() throws Exception {
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

			assertNotNull(readField(client, "blockingStub"));
		}
	}

	@Test
	void processEvent_whenSync_andNoResult_doesNothingExtra() throws Exception {
		writeField(client, "blockingStub", blockingStub);

		EventContext eventCtx = mock(EventContext.class);
		gov.nist.ngac.pm.proto.v1.epp.EventContext protoCtx = mock(gov.nist.ngac.pm.proto.v1.epp.EventContext.class);

		ProcessEventResponse response = ProcessEventResponse.newBuilder().build();
		when(blockingStub.processEvent(protoCtx)).thenReturn(response);

		try (MockedStatic<ToProtoUtil> protoUtil = mockStatic(ToProtoUtil.class)) {
			protoUtil.when(() -> ToProtoUtil.toEventContextProto(eventCtx)).thenReturn(protoCtx);

			client.processEvent(eventCtx);

			verify(blockingStub).processEvent(protoCtx);
		}
	}

	@Test
	void processEvent_whenSync_andResultContainsLastRevision_waitsForCatchUp() throws Exception {
		writeField(client, "blockingStub", blockingStub);

		EventContext eventCtx = mock(EventContext.class);
		gov.nist.ngac.pm.proto.v1.epp.EventContext protoCtx = mock(gov.nist.ngac.pm.proto.v1.epp.EventContext.class);

		when(blockingStub.processEvent(protoCtx)).thenReturn(responseWithLastRevision(5));

		try (MockedStatic<ToProtoUtil> protoUtil = mockStatic(ToProtoUtil.class)) {
			protoUtil.when(() -> ToProtoUtil.toEventContextProto(eventCtx)).thenReturn(protoCtx);

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