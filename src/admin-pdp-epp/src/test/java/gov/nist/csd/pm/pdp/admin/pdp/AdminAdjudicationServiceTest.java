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
import gov.nist.ngac.pm.core.pdp.UnauthorizedException;
import gov.nist.ngac.pm.proto.v1.model.Value;
import gov.nist.ngac.pm.proto.v1.model.ValueMap;
import gov.nist.ngac.pm.proto.v1.pdp.adjudication.*;
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

class AdminAdjudicationServiceTest {

	private AdminAdjudicator adjudicator;
	private AdminAdjudicationService adminAdjudicationService;

	@BeforeEach
	void setUp() {
		adjudicator = mock(AdminAdjudicator.class);
		adminAdjudicationService = new AdminAdjudicationService(adjudicator);
	}

	@Nested
	class AdjudicateOperation {
		@Test
		void success() throws PMException {
			StreamObserver<AdjudicateOperationResponse> observer = mock(StreamObserver.class);
			Map<String, Value> args = new HashMap<>();
			args.put("key", Value.newBuilder().setStringValue("value").build());
			OperationRequest request = OperationRequest.newBuilder()
					.setName("op")
					.putAllArgs(args)
					.build();

			when(adjudicator.adjudicateOperation(anyString(), anyMap())).thenReturn("result");

			adminAdjudicationService.adjudicateOperation(request, observer);

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
					.setName("op")
					.build();

			UnauthorizedException ex = mock(UnauthorizedException.class);
			when(ex.getMessage()).thenReturn("unauthorized");
			doThrow(ex).when(adjudicator).adjudicateOperation(anyString(), anyMap());

			adminAdjudicationService.adjudicateOperation(request, observer);

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
					.setName("op")
					.build();

			doThrow(new RuntimeException("error"))
					.when(adjudicator).adjudicateOperation(anyString(), anyMap());

			adminAdjudicationService.adjudicateOperation(request, observer);

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
					.addOperations(OperationRequest.newBuilder().setName("op").build())
					.build();

			adminAdjudicationService.adjudicateRoutine(request, observer);

			verify(adjudicator).adjudicateRoutine(anyList());
			verify(observer).onNext(any(AdjudicateRoutineResponse.class));
			verify(observer).onCompleted();
		}

		@Test
		void unauthorized() throws PMException {
			StreamObserver<AdjudicateRoutineResponse> observer = mock(StreamObserver.class);
			RoutineRequest request = RoutineRequest.newBuilder()
					.addOperations(OperationRequest.newBuilder().setName("op").build())
					.build();

			UnauthorizedException ex = mock(UnauthorizedException.class);
			when(ex.getMessage()).thenReturn("unauthorized");
			doThrow(ex).when(adjudicator).adjudicateRoutine(anyList());

			adminAdjudicationService.adjudicateRoutine(request, observer);

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
					.addOperations(OperationRequest.newBuilder().setName("op").build())
					.build();

			doThrow(new RuntimeException("error"))
					.when(adjudicator).adjudicateRoutine(anyList());

			adminAdjudicationService.adjudicateRoutine(request, observer);

			ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
			verify(observer).onError(errorCaptor.capture());

			assertTrue(errorCaptor.getValue() instanceof StatusRuntimeException);
			StatusRuntimeException exception = (StatusRuntimeException) errorCaptor.getValue();
			assertEquals(Status.INTERNAL.getCode(), exception.getStatus().getCode());
		}
	}

	@Nested
	class ExecutePML {
		@Test
		void success() throws PMException {
			StreamObserver<ExecutePMLResponse> observer = mock(StreamObserver.class);
			ExecutePMLRequest request = ExecutePMLRequest.newBuilder()
					.setPml("pml code")
					.build();

			when(adjudicator.executePML(anyString())).thenReturn("result");

			adminAdjudicationService.executePML(request, observer);

			verify(adjudicator).executePML("pml code");

			ArgumentCaptor<ExecutePMLResponse> responseCaptor = ArgumentCaptor
					.forClass(ExecutePMLResponse.class);
			verify(observer).onNext(responseCaptor.capture());
			assertTrue(responseCaptor.getValue().getValue().hasStringValue());
			assertEquals("result", responseCaptor.getValue().getValue().getStringValue());
		}

		@Test
		void unauthorized() throws PMException {
			StreamObserver<ExecutePMLResponse> observer = mock(StreamObserver.class);
			ExecutePMLRequest request = ExecutePMLRequest.newBuilder()
					.setPml("pml code")
					.build();

			UnauthorizedException ex = mock(UnauthorizedException.class);
			when(ex.getMessage()).thenReturn("unauthorized");
			doThrow(ex).when(adjudicator).executePML(anyString());

			adminAdjudicationService.executePML(request, observer);

			ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
			verify(observer).onError(errorCaptor.capture());

			assertTrue(errorCaptor.getValue() instanceof StatusRuntimeException);
			StatusRuntimeException exception = (StatusRuntimeException) errorCaptor.getValue();
			assertEquals(Status.PERMISSION_DENIED.getCode(), exception.getStatus().getCode());
		}

		@Test
		void error() throws PMException {
			StreamObserver<ExecutePMLResponse> observer = mock(StreamObserver.class);
			ExecutePMLRequest request = ExecutePMLRequest.newBuilder()
					.setPml("pml code")
					.build();

			doThrow(new RuntimeException("error"))
					.when(adjudicator).executePML(anyString());

			adminAdjudicationService.executePML(request, observer);

			ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
			verify(observer).onError(errorCaptor.capture());

			assertTrue(errorCaptor.getValue() instanceof StatusRuntimeException);
			StatusRuntimeException exception = (StatusRuntimeException) errorCaptor.getValue();
			assertEquals(Status.INTERNAL.getCode(), exception.getStatus().getCode());
		}
	}

}