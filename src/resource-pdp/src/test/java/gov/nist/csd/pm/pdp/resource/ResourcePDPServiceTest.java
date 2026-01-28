package gov.nist.csd.pm.pdp.resource;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.core.pdp.UnauthorizedException;
import gov.nist.csd.pm.pdp.shared.auth.UserContextFromHeader;
import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;
import gov.nist.csd.pm.proto.v1.adjudication.AdjudicateOperationResponse;
import gov.nist.csd.pm.proto.v1.adjudication.OperationRequest;
import gov.nist.csd.pm.proto.v1.model.Value;
import gov.nist.csd.pm.proto.v1.model.ValueMap;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourcePDPServiceTest {

	@Mock private PDP pdp;
	@Mock private PAP pap;
	@Mock private RevisionCatchUpGate revisionCatchUpGate;

	@Mock private StreamObserver<AdjudicateOperationResponse> responseObserver;

	private ResourcePDPService service;

	@BeforeEach
	void setUp() {
		service = new ResourcePDPService(pdp, pap, revisionCatchUpGate);
	}

	@Test
	void adjudicateResourceOperation_gateClosed_returnsUnavailable() {
		when(revisionCatchUpGate.isClosed()).thenReturn(true);

		OperationRequest request = OperationRequest.newBuilder()
				.setOpName("op")
				.setArgs(ValueMap.newBuilder().build())
				.build();

		service.adjudicateResourceOperation(request, responseObserver);

		ArgumentCaptor<Throwable> errCaptor = ArgumentCaptor.forClass(Throwable.class);
		verify(responseObserver).onError(errCaptor.capture());

		Status status = Status.fromThrowable(errCaptor.getValue());
		assertEquals(Status.Code.UNAVAILABLE, status.getCode());
		assertEquals(
				"the resource PDP timed out waiting for the last EPP revision",
				status.getDescription()
		);

		verify(responseObserver, never()).onNext(any());
		verify(responseObserver, never()).onCompleted();
		verifyNoInteractions(pdp);
	}

	@Test
	void adjudicateResourceOperation_success_callsPdp_andReturnsResponse() throws PMException {
		when(revisionCatchUpGate.isClosed()).thenReturn(false);

		OperationRequest request = OperationRequest.newBuilder()
				.setOpName("op1")
				.setArgs(ValueMap.newBuilder().build())
				.build();

		UserContext userCtx = mock(UserContext.class);

		Map<String, Object> argsObj = Map.of("a", "test");
		Object pdpResult = "test";

		Value resultValue = Value.newBuilder().setStringValue("test").build();

		try (MockedStatic<UserContextFromHeader> header = mockStatic(UserContextFromHeader.class);
		     MockedStatic<ProtoUtil> protoUtil = mockStatic(ProtoUtil.class)) {

			header.when(() -> UserContextFromHeader.get(pap)).thenReturn(userCtx);

			protoUtil.when(() -> ProtoUtil.valueMapToObjectMap(any(ValueMap.class)))
					.thenReturn(argsObj);

			when(pdp.adjudicateOperation(eq(userCtx), eq("op1"), eq(argsObj)))
					.thenReturn(pdpResult);

			protoUtil.when(() -> ProtoUtil.objectToValue(pdpResult))
					.thenReturn(resultValue);

			service.adjudicateResourceOperation(request, responseObserver);

			// verify response
			ArgumentCaptor<AdjudicateOperationResponse> respCaptor =
					ArgumentCaptor.forClass(AdjudicateOperationResponse.class);

			verify(responseObserver).onNext(respCaptor.capture());
			verify(responseObserver).onCompleted();
			verify(responseObserver, never()).onError(any());

			AdjudicateOperationResponse resp = respCaptor.getValue();
			assertEquals(resultValue, resp.getValue());

			verify(pdp).adjudicateOperation(userCtx, "op1", argsObj);
		}
	}

	@Test
	void adjudicateResourceOperation_unauthorized_returnsPermissionDenied() throws PMException {
		when(revisionCatchUpGate.isClosed()).thenReturn(false);

		OperationRequest request = OperationRequest.newBuilder()
				.setOpName("op1")
				.setArgs(ValueMap.newBuilder().build())
				.build();

		UserContext userCtx = mock(UserContext.class);
		Map<String, Object> argsObj = Map.of("a", "test");

		UnauthorizedException unauth = mock(UnauthorizedException.class);
		when(unauth.getMessage()).thenReturn("test exception");

		try (MockedStatic<UserContextFromHeader> header = mockStatic(UserContextFromHeader.class);
		     MockedStatic<ProtoUtil> protoUtil = mockStatic(ProtoUtil.class)) {

			header.when(() -> UserContextFromHeader.get(pap)).thenReturn(userCtx);

			protoUtil.when(() -> ProtoUtil.valueMapToObjectMap(any(ValueMap.class)))
					.thenReturn(argsObj);

			when(pdp.adjudicateOperation(eq(userCtx), eq("op1"), eq(argsObj)))
					.thenThrow(unauth);

			service.adjudicateResourceOperation(request, responseObserver);

			ArgumentCaptor<Throwable> errCaptor = ArgumentCaptor.forClass(Throwable.class);
			verify(responseObserver).onError(errCaptor.capture());

			Throwable t = errCaptor.getValue();
			assertTrue(t instanceof StatusRuntimeException);

			Status status = Status.fromThrowable(t);
			assertEquals(Status.Code.PERMISSION_DENIED, status.getCode());
			assertEquals("test exception", status.getDescription());

			verify(responseObserver, never()).onNext(any());
			verify(responseObserver, never()).onCompleted();
		}
	}

	@Test
	void adjudicateResourceOperation_genericException_returnsInternal() throws PMException {
		when(revisionCatchUpGate.isClosed()).thenReturn(false);

		OperationRequest request = OperationRequest.newBuilder()
				.setOpName("op1")
				.setArgs(ValueMap.newBuilder().build())
				.build();

		UserContext userCtx = mock(UserContext.class);
		Map<String, Object> argsObj = Map.of("a", "test");

		RuntimeException failure = new RuntimeException("test exception");

		try (MockedStatic<UserContextFromHeader> header = mockStatic(UserContextFromHeader.class);
		     MockedStatic<ProtoUtil> protoUtil = mockStatic(ProtoUtil.class)) {

			header.when(() -> UserContextFromHeader.get(pap)).thenReturn(userCtx);

			protoUtil.when(() -> ProtoUtil.valueMapToObjectMap(any(ValueMap.class)))
					.thenReturn(argsObj);

			when(pdp.adjudicateOperation(eq(userCtx), eq("op1"), eq(argsObj)))
					.thenThrow(failure);

			service.adjudicateResourceOperation(request, responseObserver);

			ArgumentCaptor<Throwable> errCaptor = ArgumentCaptor.forClass(Throwable.class);
			verify(responseObserver).onError(errCaptor.capture());

			Status status = Status.fromThrowable(errCaptor.getValue());
			assertEquals(Status.Code.INTERNAL, status.getCode());
			assertEquals("test exception", status.getDescription());

			verify(responseObserver, never()).onNext(any());
			verify(responseObserver, never()).onCompleted();
		}
	}
}