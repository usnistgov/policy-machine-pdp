package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pdp.adjudication.AdjudicationResponse;
import gov.nist.csd.pm.core.pdp.adjudication.Decision;
import gov.nist.csd.pm.pdp.proto.adjudication.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdjudicationServiceTest {

	private AdjudicationService service;
	private Adjudicator adjudicator;
	@BeforeEach
	void setUp() {
		adjudicator     = mock(Adjudicator.class);

		service = new AdjudicationService(adjudicator);
	}

	@Test
	void shouldReturnCreatedNodeIdsAndComplete() {
		AdjudicateAdminCmdRequest request = AdjudicateAdminCmdRequest.newBuilder().build();
		@SuppressWarnings("unchecked")
		StreamObserver<CreatedNodeIdsResponse> observer = mock(StreamObserver.class);

		Map<String, Long> resultMap = Map.of(
				"node1", 1L,
				"node2", 2L
		);
		when(adjudicator.adjudicateAdminCommands(request.getCommandsList()))
				.thenReturn(resultMap);

		service.adjudicateAdminCmd(request, observer);

		ArgumentCaptor<CreatedNodeIdsResponse> captor =
				ArgumentCaptor.forClass(CreatedNodeIdsResponse.class);
		verify(observer).onNext(captor.capture());
		CreatedNodeIdsResponse resp = captor.getValue();

		assertEquals(1L, resp.getNodeIdsMap().get("node1"));
		assertEquals(2L, resp.getNodeIdsMap().get("node2"));

		verify(observer).onCompleted();
		verify(observer, never()).onError(any());
	}

	@Test
	void shouldOnErrorWhenAdjudicatorThrows() {
		AdjudicateAdminCmdRequest request = AdjudicateAdminCmdRequest.newBuilder().build();
		@SuppressWarnings("unchecked")
		StreamObserver<CreatedNodeIdsResponse> observer = mock(StreamObserver.class);

		when(adjudicator.adjudicateAdminCommands(request.getCommandsList()))
				.thenThrow(new RuntimeException("test exception"));

		service.adjudicateAdminCmd(request, observer);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Throwable> errorCaptor =
				ArgumentCaptor.forClass(Throwable.class);
		verify(observer).onError(errorCaptor.capture());

		assertEquals("test exception", errorCaptor.getValue().getMessage());

		verify(observer, never()).onNext(any());
		verify(observer, never()).onCompleted();
	}

	@Nested
	class AdjudicateGenericOperationTest {

		@Test
		void shouldGrantWhenDecisionIsGrant() {
			GenericAdminCmd request = GenericAdminCmd.newBuilder()
					.setOpName("myOp")
					.putArgs("count", Arg.newBuilder().setInt64Value(42L).build())
					.build();
			@SuppressWarnings("unchecked")
			StreamObserver<AdjudicateGenericResponse> observer = mock(StreamObserver.class);

			AdjudicationResponse pdpResponse = mock(AdjudicationResponse.class);
			when(pdpResponse.getDecision()).thenReturn(Decision.GRANT);

			when(adjudicator.adjudicateAdminOperation(eq("myOp"), anyMap()))
					.thenReturn(pdpResponse);

			service.adjudicateGenericOperation(request, observer);

			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<String, Object>> argsCaptor =
					ArgumentCaptor.forClass((Class) Map.class);
			verify(adjudicator).adjudicateAdminOperation(eq("myOp"), argsCaptor.capture());
			Map<String, Object> captured = argsCaptor.getValue();
			assertThat(captured).containsEntry("count", 42L);

			ArgumentCaptor<AdjudicateGenericResponse> respCaptor =
					ArgumentCaptor.forClass(AdjudicateGenericResponse.class);
			verify(observer).onNext(respCaptor.capture());
			AdjudicateGenericResponse protoResp = respCaptor.getValue();
			assertThat(protoResp.getDecision())
					.isEqualTo(AdjudicateDecision.GRANT);

			verify(observer).onCompleted();
			verify(observer, never()).onError(any());
		}

		@Test
		void shouldDenyWhenDecisionIsDeny() throws PMException {
			GenericAdminCmd request = GenericAdminCmd.newBuilder()
					.setOpName("otherOp")
					.putArgs("flag", Arg.newBuilder().setInt64Value(0L).build())
					.build();
			@SuppressWarnings("unchecked")
			StreamObserver<AdjudicateGenericResponse> observer = mock(StreamObserver.class);

			AdjudicationResponse pdpResponse = mock(AdjudicationResponse.class);
			when(pdpResponse.getDecision()).thenReturn(Decision.DENY);

			when(adjudicator.adjudicateAdminOperation(eq("otherOp"), anyMap()))
					.thenReturn(pdpResponse);

			service.adjudicateGenericOperation(request, observer);

			ArgumentCaptor<AdjudicateGenericResponse> respCaptor =
					ArgumentCaptor.forClass(AdjudicateGenericResponse.class);
			verify(observer).onNext(respCaptor.capture());
			AdjudicateGenericResponse protoResp = respCaptor.getValue();
			assertThat(protoResp.getDecision())
					.isEqualTo(AdjudicateDecision.DENY);

			verify(observer).onCompleted();
			verify(observer, never()).onError(any());
		}

		@Test
		void shouldOnErrorWhenAdjudicatorThrows() throws PMException {
			GenericAdminCmd request = GenericAdminCmd.newBuilder()
					.setOpName("failOp")
					.build();
			@SuppressWarnings("unchecked")
			StreamObserver<AdjudicateGenericResponse> observer = mock(StreamObserver.class);

			when(adjudicator.adjudicateAdminOperation(eq("failOp"), anyMap()))
					.thenThrow(new IllegalStateException("boom"));

			service.adjudicateGenericOperation(request, observer);

			@SuppressWarnings("unchecked")
			ArgumentCaptor<Throwable> errorCaptor =
					ArgumentCaptor.forClass(Throwable.class);
			verify(observer).onError(errorCaptor.capture());
			assertThat(errorCaptor.getValue())
					.hasMessage("boom");

			verify(observer, never()).onNext(any());
			verify(observer, never()).onCompleted();
		}
	}

	@Nested
	class AdjudicateGenericRoutineTest {

		@Test
		void shouldGrantWhenDecisionIsGrant() throws PMException {
			GenericAdminCmd request = GenericAdminCmd.newBuilder()
					.setOpName("routineOp")
					.putArgs("flag", Arg.newBuilder().setBoolValue(true).build())
					.build();
			@SuppressWarnings("unchecked")
			StreamObserver<AdjudicateGenericResponse> observer = mock(StreamObserver.class);

			AdjudicationResponse pdpResponse = mock(AdjudicationResponse.class);
			when(pdpResponse.getDecision()).thenReturn(Decision.GRANT);

			when(adjudicator.adjudicateAdminRoutine(eq("routineOp"), anyMap()))
					.thenReturn(pdpResponse);

			service.adjudicateGenericRoutine(request, observer);

			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<String, Object>> argsCaptor =
					ArgumentCaptor.forClass((Class) Map.class);
			verify(adjudicator).adjudicateAdminRoutine(eq("routineOp"), argsCaptor.capture());
			Map<String, Object> captured = argsCaptor.getValue();
			assertThat(captured).containsEntry("flag", true);

			ArgumentCaptor<AdjudicateGenericResponse> respCaptor =
					ArgumentCaptor.forClass(AdjudicateGenericResponse.class);
			verify(observer).onNext(respCaptor.capture());
			AdjudicateGenericResponse protoResp = respCaptor.getValue();
			assertThat(protoResp.getDecision())
					.isEqualTo(AdjudicateDecision.GRANT);

			verify(observer).onCompleted();
			verify(observer, never()).onError(any());
		}

		@Test
		void shouldDenyWhenDecisionIsDeny() throws PMException {
			GenericAdminCmd request = GenericAdminCmd.newBuilder()
					.setOpName("routineOp2")
					.putArgs("count", Arg.newBuilder().setInt64Value(0L).build())
					.build();
			@SuppressWarnings("unchecked")
			StreamObserver<AdjudicateGenericResponse> observer = mock(StreamObserver.class);

			AdjudicationResponse pdpResponse = mock(AdjudicationResponse.class);
			when(pdpResponse.getDecision()).thenReturn(Decision.DENY);

			when(adjudicator.adjudicateAdminRoutine(eq("routineOp2"), anyMap()))
					.thenReturn(pdpResponse);

			service.adjudicateGenericRoutine(request, observer);

			ArgumentCaptor<AdjudicateGenericResponse> respCaptor =
					ArgumentCaptor.forClass(AdjudicateGenericResponse.class);
			verify(observer).onNext(respCaptor.capture());
			AdjudicateGenericResponse protoResp = respCaptor.getValue();
			assertThat(protoResp.getDecision())
					.isEqualTo(AdjudicateDecision.DENY);

			verify(observer).onCompleted();
			verify(observer, never()).onError(any());
		}

		@Test
		void shouldOnErrorWhenAdjudicatorThrows() throws PMException {
			GenericAdminCmd request = GenericAdminCmd.newBuilder()
					.setOpName("failRoutine")
					.build();
			@SuppressWarnings("unchecked")
			StreamObserver<AdjudicateGenericResponse> observer = mock(StreamObserver.class);

			when(adjudicator.adjudicateAdminRoutine(eq("failRoutine"), anyMap()))
					.thenThrow(new IllegalStateException("boomRoutine"));

			service.adjudicateGenericRoutine(request, observer);

			@SuppressWarnings("unchecked")
			ArgumentCaptor<Throwable> errorCaptor =
					ArgumentCaptor.forClass(Throwable.class);
			verify(observer).onError(errorCaptor.capture());
			assertThat(errorCaptor.getValue())
					.hasMessage("boomRoutine");

			verify(observer, never()).onNext(any());
			verify(observer, never()).onCompleted();
		}
	}

}