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
 * licensed to NIST.
 *
 * This file is part of the admin-pdp-epp module, which compiles against
 * and embeds org.neo4j:neo4j (GPLv3, Community Edition). As a combined work, this
 * module is distributed under the GNU General Public License v3.0, not the CC BY 4.0
 * license used elsewhere in this repository. See admin-pdp-epp/LICENSE for the full text.
 */

package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.pap.operation.AdminOperation;
import gov.nist.ngac.pm.core.pap.operation.ResourceOperation;
import gov.nist.ngac.pm.core.pdp.UnauthorizedException;
import gov.nist.ngac.pm.proto.v1.model.Value;
import gov.nist.ngac.pm.proto.v1.pdp.adjudication.AdjudicateOperationResponse;
import gov.nist.ngac.pm.proto.v1.pdp.adjudication.OperationRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ResourceAdjudicationServiceTest {

    private AdminAdjudicator adjudicator;
    private ResourceAdjudicationService service;

    @BeforeEach
    void setUp() {
        adjudicator = mock(AdminAdjudicator.class);
        service = new ResourceAdjudicationService(adjudicator);
    }

    @Test
    void success_delegatesToAdjudicator_andReturnsResponse() throws PMException {
        StreamObserver<AdjudicateOperationResponse> observer = mock(StreamObserver.class);
        Map<String, Value> args = new HashMap<>();
        args.put("key", Value.newBuilder().setStringValue("value").build());
        OperationRequest request = OperationRequest.newBuilder()
                .setName("resourceOp")
                .putAllArgs(args)
                .build();

        ResourceOperation<?> resourceOp = mock(ResourceOperation.class);
        doReturn(resourceOp).when(adjudicator).adjudicateQuery(any());
        when(adjudicator.adjudicateOperation(anyString(), anyMap())).thenReturn("result");

        service.adjudicateResourceOperation(request, observer);

        ArgumentCaptor<String> opNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(adjudicator).adjudicateOperation(opNameCaptor.capture(), mapCaptor.capture());

        assertEquals("resourceOp", opNameCaptor.getValue());
        assertEquals("value", mapCaptor.getValue().get("key"));

        ArgumentCaptor<AdjudicateOperationResponse> responseCaptor = ArgumentCaptor
                .forClass(AdjudicateOperationResponse.class);
        verify(observer).onNext(responseCaptor.capture());
        verify(observer).onCompleted();
        verify(observer, never()).onError(any());
        assertTrue(responseCaptor.getValue().getValue().hasStringValue());
        assertEquals("result", responseCaptor.getValue().getValue().getStringValue());
    }

    @Test
    void nonResourceOperation_returnsInvalidArgument_andDoesNotAdjudicate() throws PMException {
        StreamObserver<AdjudicateOperationResponse> observer = mock(StreamObserver.class);
        OperationRequest request = OperationRequest.newBuilder()
                .setName("adminOp")
                .build();

        AdminOperation<?> adminOp = mock(AdminOperation.class);
        doReturn(adminOp).when(adjudicator).adjudicateQuery(any());

        service.adjudicateResourceOperation(request, observer);

        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(errorCaptor.capture());

        assertTrue(errorCaptor.getValue() instanceof StatusRuntimeException);
        Status status = Status.fromThrowable(errorCaptor.getValue());
        assertEquals(Status.Code.INVALID_ARGUMENT, status.getCode());
        assertEquals(
                "only subclasses of ResourceOperation are allowed to be invoked in the resource-pdp",
                status.getDescription()
        );

        verify(adjudicator, never()).adjudicateOperation(anyString(), anyMap());
        verify(observer, never()).onNext(any());
        verify(observer, never()).onCompleted();
    }

    @Test
    void unauthorized_returnsPermissionDenied() throws PMException {
        StreamObserver<AdjudicateOperationResponse> observer = mock(StreamObserver.class);
        OperationRequest request = OperationRequest.newBuilder()
                .setName("resourceOp")
                .build();

        ResourceOperation<?> resourceOp = mock(ResourceOperation.class);
        doReturn(resourceOp).when(adjudicator).adjudicateQuery(any());

        UnauthorizedException ex = mock(UnauthorizedException.class);
        when(ex.getMessage()).thenReturn("unauthorized");
        doThrow(ex).when(adjudicator).adjudicateOperation(anyString(), anyMap());

        service.adjudicateResourceOperation(request, observer);

        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(errorCaptor.capture());

        assertTrue(errorCaptor.getValue() instanceof StatusRuntimeException);
        Status status = Status.fromThrowable(errorCaptor.getValue());
        assertEquals(Status.Code.PERMISSION_DENIED, status.getCode());
    }

    @Test
    void genericException_returnsInternal() throws PMException {
        StreamObserver<AdjudicateOperationResponse> observer = mock(StreamObserver.class);
        OperationRequest request = OperationRequest.newBuilder()
                .setName("resourceOp")
                .build();

        ResourceOperation<?> resourceOp = mock(ResourceOperation.class);
        doReturn(resourceOp).when(adjudicator).adjudicateQuery(any());
        doThrow(new RuntimeException("error")).when(adjudicator).adjudicateOperation(anyString(), anyMap());

        service.adjudicateResourceOperation(request, observer);

        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(errorCaptor.capture());

        assertTrue(errorCaptor.getValue() instanceof StatusRuntimeException);
        Status status = Status.fromThrowable(errorCaptor.getValue());
        assertEquals(Status.Code.INTERNAL, status.getCode());
    }
}
