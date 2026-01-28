package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pdp.UnauthorizedException;
import gov.nist.csd.pm.proto.v1.adjudication.AdjudicateOperationResponse;
import gov.nist.csd.pm.proto.v1.adjudication.AdjudicateRoutineResponse;
import gov.nist.csd.pm.proto.v1.adjudication.OperationRequest;
import gov.nist.csd.pm.proto.v1.adjudication.RoutineRequest;
import gov.nist.csd.pm.proto.v1.cmd.AdminOperationCommand;
import gov.nist.csd.pm.proto.v1.model.Value;
import gov.nist.csd.pm.proto.v1.model.ValueMap;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AdjudicationServiceTest {

	private Adjudicator adjudicator;
	private AdjudicationService adjudicationService;

	@BeforeEach
	void setUp() {
		adjudicator = mock(Adjudicator.class);
		adjudicationService = new AdjudicationService(adjudicator);
	}

	@Nested
	class AdjudicateOperation {
		@Test
		void success() throws PMException {
			StreamObserver<AdjudicateOperationResponse> observer = mock(StreamObserver.class);
			Map<String, Value> args = new HashMap<>();
			args.put("key", Value.newBuilder().setStringValue("value").build());
			OperationRequest request = OperationRequest.newBuilder()
					.setOpName("op")
					.setArgs(ValueMap.newBuilder().putAllValues(args).build())
					.build();

			when(adjudicator.adjudicateOperation(anyString(), anyMap())).thenReturn("result");

			adjudicationService.adjudicateOperation(request, observer);

			ArgumentCaptor<String> opNameCaptor = ArgumentCaptor.forClass(String.class);
			ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);
			verify(adjudicator).adjudicateOperation(opNameCaptor.capture(), mapCaptor.capture());

			assertEquals("op", opNameCaptor.getValue());
			assertEquals("value", mapCaptor.getValue().get("key"));

			ArgumentCaptor<AdjudicateOperationResponse> responseCaptor = ArgumentCaptor
					.forClass(AdjudicateOperationResponse.class);
			verify(observer).onNext(responseCaptor.capture());
			assertTrue(responseCaptor.getValue().getValue().hasStringValue());
			assertEquals("result", responseCaptor.getValue().getValue().getStringValue());
		}

		@Test
		void unauthorized() throws PMException {
			StreamObserver<AdjudicateOperationResponse> observer = mock(StreamObserver.class);
			OperationRequest request = OperationRequest.newBuilder()
					.setOpName("op")
					.build();

			UnauthorizedException ex = mock(UnauthorizedException.class);
			when(ex.getMessage()).thenReturn("unauthorized");
			doThrow(ex).when(adjudicator).adjudicateOperation(anyString(), anyMap());

			adjudicationService.adjudicateOperation(request, observer);

			ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
			verify(observer).onError(errorCaptor.capture());

			assertTrue(errorCaptor.getValue() instanceof StatusRuntimeException);
			StatusRuntimeException exception = (StatusRuntimeException) errorCaptor.getValue();
			assertEquals(Status.PERMISSION_DENIED.getCode(), exception.getStatus().getCode());
		}

		@Test
		void error() throws PMException {
			StreamObserver<AdjudicateOperationResponse> observer = mock(StreamObserver.class);
			OperationRequest request = OperationRequest.newBuilder()
					.setOpName("op")
					.build();

			doThrow(new RuntimeException("error"))
					.when(adjudicator).adjudicateOperation(anyString(), anyMap());

			adjudicationService.adjudicateOperation(request, observer);

			ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
			verify(observer).onError(errorCaptor.capture());

			assertTrue(errorCaptor.getValue() instanceof StatusRuntimeException);
			StatusRuntimeException exception = (StatusRuntimeException) errorCaptor.getValue();
			assertEquals(Status.INTERNAL.getCode(), exception.getStatus().getCode());
		}
	}

	@Nested
	class AdjudicateRoutine {
		@Test
		void success() throws PMException {
			StreamObserver<AdjudicateRoutineResponse> observer = mock(StreamObserver.class);
			RoutineRequest request = RoutineRequest.newBuilder()
					.addCommands(AdminOperationCommand.newBuilder().build())
					.build();

			adjudicationService.adjudicateRoutine(request, observer);

			verify(adjudicator).adjudicateRoutine(anyList());
			verify(observer).onNext(any(AdjudicateRoutineResponse.class));
			verify(observer).onCompleted();
		}

		@Test
		void unauthorized() throws PMException {
			StreamObserver<AdjudicateRoutineResponse> observer = mock(StreamObserver.class);
			RoutineRequest request = RoutineRequest.newBuilder()
					.addCommands(AdminOperationCommand.newBuilder().build())
					.build();

			UnauthorizedException ex = mock(UnauthorizedException.class);
			when(ex.getMessage()).thenReturn("unauthorized");
			doThrow(ex).when(adjudicator).adjudicateRoutine(anyList());

			adjudicationService.adjudicateRoutine(request, observer);

			ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
			verify(observer).onError(errorCaptor.capture());

			assertTrue(errorCaptor.getValue() instanceof StatusRuntimeException);
			StatusRuntimeException exception = (StatusRuntimeException) errorCaptor.getValue();
			assertEquals(Status.PERMISSION_DENIED.getCode(), exception.getStatus().getCode());
		}

		@Test
		void error() throws PMException {
			StreamObserver<AdjudicateRoutineResponse> observer = mock(StreamObserver.class);
			RoutineRequest request = RoutineRequest.newBuilder()
					.addCommands(AdminOperationCommand.newBuilder().build())
					.build();

			doThrow(new RuntimeException("error"))
					.when(adjudicator).adjudicateRoutine(anyList());

			adjudicationService.adjudicateRoutine(request, observer);

			ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
			verify(observer).onError(errorCaptor.capture());

			assertTrue(errorCaptor.getValue() instanceof StatusRuntimeException);
			StatusRuntimeException exception = (StatusRuntimeException) errorCaptor.getValue();
			assertEquals(Status.INTERNAL.getCode(), exception.getStatus().getCode());
		}
	}

}