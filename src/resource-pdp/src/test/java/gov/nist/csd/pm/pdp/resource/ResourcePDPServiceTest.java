package gov.nist.csd.pm.pdp.resource;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.operation.AdminOperation;
import gov.nist.csd.pm.core.pap.operation.ResourceOperation;
import gov.nist.csd.pm.core.pap.query.OperationsQuery;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.core.pdp.UnauthorizedException;
import gov.nist.csd.pm.pdp.shared.auth.UserContextFromHeader;
import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;
import gov.nist.csd.pm.proto.v1.model.Value;
import gov.nist.csd.pm.proto.v1.model.ValueMap;
import gov.nist.csd.pm.proto.v1.pdp.adjudication.AdjudicateOperationResponse;
import gov.nist.csd.pm.proto.v1.pdp.adjudication.OperationRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
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
	@Mock(answer = Answers.RETURNS_DEEP_STUBS) private PAP pap;

	@Mock private StreamObserver<AdjudicateOperationResponse> responseObserver;

	private ResourcePDPService service;

	@BeforeEach
	void setUp() {
		service = new ResourcePDPService(pdp, pap);
	}

	@Test
	void adjudicateResourceOperation_success_callsPdp_andReturnsResponse() throws PMException {
		OperationRequest request = OperationRequest.newBuilder()
				.setOpName("op1")
				.setArgs(ValueMap.newBuilder().build())
				.build();

		UserContext userCtx = mock(UserContext.class);
		ResourceOperation<?> resourceOp = mock(ResourceOperation.class);

		Map<String, Object> argsObj = Map.of("a", "test");
		Object pdpResult = "test";
		Value resultValue = Value.newBuilder().setStringValue("test").build();

		try (MockedStatic<UserContextFromHeader> header = mockStatic(UserContextFromHeader.class);
		     MockedStatic<ProtoUtil> protoUtil = mockStatic(ProtoUtil.class)) {

			header.when(() -> UserContextFromHeader.get(pap)).thenReturn(userCtx);

			// IMPORTANT: do not inline pap.query().operations() inside when/doReturn
			var ops = pap.query().operations();
			doReturn(resourceOp)
					.when(ops)
					.getOperation("op1");

			protoUtil.when(() -> ProtoUtil.valueMapToObjectMap(any(ValueMap.class)))
					.thenReturn(argsObj);

			when(pdp.adjudicateOperation(eq(userCtx), eq("op1"), eq(argsObj)))
					.thenReturn(pdpResult);

			protoUtil.when(() -> ProtoUtil.objectToValue(pdpResult))
					.thenReturn(resultValue);

			service.adjudicateResourceOperation(request, responseObserver);

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
		OperationRequest request = OperationRequest.newBuilder()
				.setOpName("op1")
				.setArgs(ValueMap.newBuilder().build())
				.build();

		UserContext userCtx = mock(UserContext.class);
		ResourceOperation<?> resourceOp = mock(ResourceOperation.class);
		Map<String, Object> argsObj = Map.of("a", "test");

		UnauthorizedException unauth = mock(UnauthorizedException.class);
		when(unauth.getMessage()).thenReturn("test exception");

		try (MockedStatic<UserContextFromHeader> header = mockStatic(UserContextFromHeader.class);
		     MockedStatic<ProtoUtil> protoUtil = mockStatic(ProtoUtil.class)) {

			header.when(() -> UserContextFromHeader.get(pap)).thenReturn(userCtx);

			var ops = pap.query().operations();
			doReturn(resourceOp)
					.when(ops)
					.getOperation("op1");

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
		OperationRequest request = OperationRequest.newBuilder()
				.setOpName("op1")
				.setArgs(ValueMap.newBuilder().build())
				.build();

		UserContext userCtx = mock(UserContext.class);
		ResourceOperation<?> resourceOp = mock(ResourceOperation.class);
		Map<String, Object> argsObj = Map.of("a", "test");

		RuntimeException failure = new RuntimeException("test exception");

		try (MockedStatic<UserContextFromHeader> header = mockStatic(UserContextFromHeader.class);
		     MockedStatic<ProtoUtil> protoUtil = mockStatic(ProtoUtil.class)) {

			header.when(() -> UserContextFromHeader.get(pap)).thenReturn(userCtx);

			OperationsQuery ops = pap.query().operations();
			doReturn(resourceOp)
					.when(ops)
					.getOperation("op1");

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

	@Test
	void adjudicateResourceOperation_nonResourceOperation_returnsInternal() throws PMException {
		OperationRequest request = OperationRequest.newBuilder()
				.setOpName("op1")
				.setArgs(ValueMap.newBuilder().build())
				.build();

		UserContext userCtx = mock(UserContext.class);

		AdminOperation<?> nonResourceOp = mock(AdminOperation.class);

		try (MockedStatic<UserContextFromHeader> header = mockStatic(UserContextFromHeader.class)) {

			header.when(() -> UserContextFromHeader.get(pap)).thenReturn(userCtx);

			OperationsQuery ops = pap.query().operations();
			doReturn(nonResourceOp)
					.when(ops)
					.getOperation("op1");

			service.adjudicateResourceOperation(request, responseObserver);

			ArgumentCaptor<Throwable> errCaptor = ArgumentCaptor.forClass(Throwable.class);
			verify(responseObserver).onError(errCaptor.capture());

			Status status = Status.fromThrowable(errCaptor.getValue());
			assertEquals(Status.Code.INVALID_ARGUMENT, status.getCode());
			assertEquals(
					"only subclasses of ResourceOperation are allowed to be invoked in the resource-pdp",
					status.getDescription()
			);

			verify(responseObserver, never()).onNext(any());
			verify(responseObserver, never()).onCompleted();
		}
	}
}