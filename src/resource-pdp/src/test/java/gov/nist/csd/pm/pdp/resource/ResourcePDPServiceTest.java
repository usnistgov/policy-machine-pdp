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

package gov.nist.csd.pm.pdp.resource;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.grpc.util.FromProtoUtil;
import gov.nist.ngac.pm.core.grpc.util.ToProtoUtil;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.operation.AdminOperation;
import gov.nist.ngac.pm.core.pap.operation.ResourceOperation;
import gov.nist.ngac.pm.core.pap.query.OperationsQuery;
import gov.nist.ngac.pm.core.pap.query.model.context.UserContext;
import gov.nist.ngac.pm.core.pdp.PDP;
import gov.nist.ngac.pm.core.pdp.UnauthorizedException;
import gov.nist.csd.pm.pdp.shared.auth.BasicUserContextResolver;
import gov.nist.csd.pm.pdp.shared.auth.UserContextFromHeader;
import gov.nist.ngac.pm.proto.v1.model.Value;
import gov.nist.ngac.pm.proto.v1.pdp.adjudication.AdjudicateOperationResponse;
import gov.nist.ngac.pm.proto.v1.pdp.adjudication.OperationRequest;
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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
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
        service = new ResourcePDPService(pdp, pap, new BasicUserContextResolver());
    }

    @Test
    void adjudicateResourceOperation_success_callsPdp_andReturnsResponse() throws PMException {
        OperationRequest request = OperationRequest.newBuilder()
                .setName("op1")
                .putAllArgs(new HashMap<>())
                .build();

        UserContext userCtx = mock(UserContext.class);
        ResourceOperation<?> resourceOp = mock(ResourceOperation.class);

        Map<String, Object> argsObj = Map.of("a", "test");
        Object pdpResult = "test";
        Value resultValue = Value.newBuilder().setStringValue("test").build();

        try (MockedStatic<UserContextFromHeader> header = mockStatic(UserContextFromHeader.class);
             MockedStatic<FromProtoUtil> fromUtil = mockStatic(FromProtoUtil.class);
             MockedStatic<ToProtoUtil> toUtil = mockStatic(ToProtoUtil.class)) {

            header.when(() -> UserContextFromHeader.get(pap)).thenReturn(userCtx);

            var ops = pap.query().operations();
            doReturn(resourceOp)
                    .when(ops)
                    .getOperation("op1");

            fromUtil.when(() -> FromProtoUtil.fromValueMap(anyMap()))
                    .thenReturn(argsObj);

            when(pdp.adjudicateOperation(eq(userCtx), eq("op1"), eq(argsObj)))
                    .thenReturn(pdpResult);

            toUtil.when(() -> ToProtoUtil.toValueProto(pdpResult))
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
                .setName("op1")
                .putAllArgs(new HashMap<>())
                .build();

        UserContext userCtx = mock(UserContext.class);
        ResourceOperation<?> resourceOp = mock(ResourceOperation.class);
        Map<String, Object> argsObj = Map.of("a", "test");

        UnauthorizedException unauth = mock(UnauthorizedException.class);
        when(unauth.getMessage()).thenReturn("test exception");

        try (MockedStatic<UserContextFromHeader> header = mockStatic(UserContextFromHeader.class);
             MockedStatic<FromProtoUtil> protoUtil = mockStatic(FromProtoUtil.class)) {

            header.when(() -> UserContextFromHeader.get(pap)).thenReturn(userCtx);

            var ops = pap.query().operations();
            doReturn(resourceOp)
                    .when(ops)
                    .getOperation("op1");

            protoUtil.when(() -> FromProtoUtil.fromValueMap(anyMap()))
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
                .setName("op1")
                .putAllArgs(new HashMap<>())
                .build();

        UserContext userCtx = mock(UserContext.class);
        ResourceOperation<?> resourceOp = mock(ResourceOperation.class);
        Map<String, Object> argsObj = Map.of("a", "test");

        RuntimeException failure = new RuntimeException("test exception");

        try (MockedStatic<UserContextFromHeader> header = mockStatic(UserContextFromHeader.class);
             MockedStatic<FromProtoUtil> protoUtil = mockStatic(FromProtoUtil.class)) {

            header.when(() -> UserContextFromHeader.get(pap)).thenReturn(userCtx);

            OperationsQuery ops = pap.query().operations();
            doReturn(resourceOp)
                    .when(ops)
                    .getOperation("op1");

            protoUtil.when(() -> FromProtoUtil.fromValueMap(anyMap()))
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
                .setName("op1")
                .putAllArgs(new HashMap<>())
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
