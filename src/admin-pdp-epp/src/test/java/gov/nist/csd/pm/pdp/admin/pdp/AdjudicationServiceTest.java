package gov.nist.csd.pm.pdp.admin.pdp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.Neo4jEmbeddedPAP;
import gov.nist.csd.pm.pdp.proto.adjudication.*;
import gov.nist.csd.pm.pdp.shared.protobuf.ObjectToStruct;
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