package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.Neo4jEmbeddedPAP;
import gov.nist.csd.pm.core.pdp.UnauthorizedException;
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

		service = new AdjudicationService(adjudicator, mock(Neo4jEmbeddedPAP.class));
	}

	@Nested
	class AdjudicateAdminCmdTest {

		@Test
		void shouldReturnCreatedNodeIdsAndComplete() throws PMException {
			AdjudicateAdminCmdRequest request = AdjudicateAdminCmdRequest.newBuilder().build();

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
		void shouldOnErrorWhenAdjudicatorThrows() throws PMException {
			AdjudicateAdminCmdRequest request = AdjudicateAdminCmdRequest.newBuilder().build();
			StreamObserver<CreatedNodeIdsResponse> observer = mock(StreamObserver.class);

			when(adjudicator.adjudicateAdminCommands(request.getCommandsList()))
					.thenThrow(new RuntimeException("test exception"));

			service.adjudicateAdminCmd(request, observer);

			ArgumentCaptor<Throwable> errorCaptor =
					ArgumentCaptor.forClass(Throwable.class);
			verify(observer).onError(errorCaptor.capture());

			assertEquals("INTERNAL: test exception", errorCaptor.getValue().getMessage());

			verify(observer, never()).onNext(any());
			verify(observer, never()).onCompleted();
		}

		@Test
		void shouldOnErrorWithPermissionDeniedWhenUnauthorized() throws PMException {
			AdjudicateAdminCmdRequest request = AdjudicateAdminCmdRequest.newBuilder().build();
			StreamObserver<CreatedNodeIdsResponse> observer = mock(StreamObserver.class);

			UnauthorizedException unauthorizedException = mock(UnauthorizedException.class);
			when(unauthorizedException.getMessage()).thenReturn("unauthorized");
			when(adjudicator.adjudicateAdminCommands(request.getCommandsList()))
					.thenThrow(unauthorizedException);

			service.adjudicateAdminCmd(request, observer);

			ArgumentCaptor<Throwable> errorCaptor =
					ArgumentCaptor.forClass(Throwable.class);
			verify(observer).onError(errorCaptor.capture());

			assertThat(errorCaptor.getValue())
					.hasMessage("PERMISSION_DENIED: unauthorized");

			verify(observer, never()).onNext(any());
			verify(observer, never()).onCompleted();
		}
	}

	@Nested
	class AdjudicateGenericOperationTest {

		@Test
		void shouldReturnResponseAndComplete() throws PMException {
			GenericAdminCmd request = GenericAdminCmd.newBuilder()
					.setOpName("testOp")
					.putArgs("param1", Arg.newBuilder().setStringValue("value1").build())
					.putArgs("param2", Arg.newBuilder().setInt64Value(1L).build())
					.build();

			StreamObserver<AdjudicateGenericResponse> observer = mock(StreamObserver.class);

			Map<String, Object> expectedResult = Map.of("result", "success");
			when(adjudicator.adjudicateAdminOperation(eq("testOp"), anyMap()))
					.thenReturn(expectedResult);

			service.adjudicateGenericOperation(request, observer);

			ArgumentCaptor<AdjudicateGenericResponse> captor =
					ArgumentCaptor.forClass(AdjudicateGenericResponse.class);
			verify(observer).onNext(captor.capture());
			AdjudicateGenericResponse response = captor.getValue();

			assertNotNull(response.getValue());
			verify(observer).onCompleted();
			verify(observer, never()).onError(any());

			// Verify the adjudicator was called with correct parameters
			ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
			verify(adjudicator).adjudicateAdminOperation(eq("testOp"), argsCaptor.capture());
			Map<String, Object> capturedArgs = argsCaptor.getValue();
			assertEquals("value1", capturedArgs.get("param1"));
			assertEquals(1L, capturedArgs.get("param2"));
		}

		@Test
		void shouldOnErrorWhenAdjudicatorThrows() throws PMException {
			GenericAdminCmd request = GenericAdminCmd.newBuilder()
					.setOpName("errorOp")
					.build();

			StreamObserver<AdjudicateGenericResponse> observer = mock(StreamObserver.class);

			when(adjudicator.adjudicateAdminOperation(eq("errorOp"), anyMap()))
					.thenThrow(new IllegalStateException("error"));

			service.adjudicateGenericOperation(request, observer);

			ArgumentCaptor<Throwable> errorCaptor =
					ArgumentCaptor.forClass(Throwable.class);
			verify(observer).onError(errorCaptor.capture());
			assertThat(errorCaptor.getValue())
					.hasMessage("INTERNAL: error");

			verify(observer, never()).onNext(any());
			verify(observer, never()).onCompleted();
		}

		@Test
		void shouldOnErrorWithPermissionDeniedWhenUnauthorized() throws PMException {
			GenericAdminCmd request = GenericAdminCmd.newBuilder()
					.setOpName("unauthorizedOp")
					.build();

			StreamObserver<AdjudicateGenericResponse> observer = mock(StreamObserver.class);

			UnauthorizedException unauthorizedException = mock(UnauthorizedException.class);
			when(unauthorizedException.getMessage()).thenReturn("unauthorized");
			when(adjudicator.adjudicateAdminOperation(eq("unauthorizedOp"), anyMap()))
					.thenThrow(unauthorizedException);

			service.adjudicateGenericOperation(request, observer);

			ArgumentCaptor<Throwable> errorCaptor =
					ArgumentCaptor.forClass(Throwable.class);
			verify(observer).onError(errorCaptor.capture());
			assertThat(errorCaptor.getValue())
					.hasMessage("PERMISSION_DENIED: unauthorized");

			verify(observer, never()).onNext(any());
			verify(observer, never()).onCompleted();
		}
	}

	@Nested
	class AdjudicateGenericRoutineTest {

		@Test
		void shouldReturnResponseAndComplete() throws PMException {
			GenericAdminCmd request = GenericAdminCmd.newBuilder()
					.setOpName("testRoutine")
					.putArgs("a", Arg.newBuilder().setBoolValue(true).build())
					.putArgs("b", Arg.newBuilder()
							.setListValue(ArgList.newBuilder()
									.addArgs(Arg.newBuilder().setStringValue("item1"))
									.addArgs(Arg.newBuilder().setStringValue("item2"))
									.build())
							.build())
					.build();

			StreamObserver<AdjudicateGenericResponse> observer = mock(StreamObserver.class);

			Map<String, Object> expectedResult = Map.of("status", "completed");
			when(adjudicator.adjudicateAdminRoutine(eq("testRoutine"), anyMap()))
					.thenReturn(expectedResult);

			service.adjudicateGenericRoutine(request, observer);

			ArgumentCaptor<AdjudicateGenericResponse> captor =
					ArgumentCaptor.forClass(AdjudicateGenericResponse.class);
			verify(observer).onNext(captor.capture());
			AdjudicateGenericResponse response = captor.getValue();

			assertNotNull(response.getValue());
			verify(observer).onCompleted();
			verify(observer, never()).onError(any());

			// Verify the adjudicator was called with correct parameters
			ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
			verify(adjudicator).adjudicateAdminRoutine(eq("testRoutine"), argsCaptor.capture());
			Map<String, Object> capturedArgs = argsCaptor.getValue();
			assertEquals(true, capturedArgs.get("a"));
			assertTrue(capturedArgs.get("b") instanceof java.util.List);
		}

		@Test
		void shouldOnErrorWhenAdjudicatorThrows() throws PMException {
			GenericAdminCmd request = GenericAdminCmd.newBuilder()
					.setOpName("errorOp")
					.build();

			StreamObserver<AdjudicateGenericResponse> observer = mock(StreamObserver.class);

			when(adjudicator.adjudicateAdminRoutine(eq("errorOp"), anyMap()))
					.thenThrow(new IllegalStateException("error"));

			service.adjudicateGenericRoutine(request, observer);

			ArgumentCaptor<Throwable> errorCaptor =
					ArgumentCaptor.forClass(Throwable.class);
			verify(observer).onError(errorCaptor.capture());
			assertThat(errorCaptor.getValue())
					.hasMessage("INTERNAL: error");

			verify(observer, never()).onNext(any());
			verify(observer, never()).onCompleted();
		}

		@Test
		void shouldOnErrorWithPermissionDeniedWhenUnauthorized() throws PMException {
			GenericAdminCmd request = GenericAdminCmd.newBuilder()
					.setOpName("unauthorizedRoutine")
					.build();

			StreamObserver<AdjudicateGenericResponse> observer = mock(StreamObserver.class);

			UnauthorizedException unauthorizedException = mock(UnauthorizedException.class);
			when(unauthorizedException.getMessage()).thenReturn("unauthorized");
			when(adjudicator.adjudicateAdminRoutine(eq("unauthorizedRoutine"), anyMap()))
					.thenThrow(unauthorizedException);

			service.adjudicateGenericRoutine(request, observer);

			ArgumentCaptor<Throwable> errorCaptor =
					ArgumentCaptor.forClass(Throwable.class);
			verify(observer).onError(errorCaptor.capture());
			assertThat(errorCaptor.getValue())
					.hasMessage("PERMISSION_DENIED: unauthorized");

			verify(observer, never()).onNext(any());
			verify(observer, never()).onCompleted();
		}
	}
}