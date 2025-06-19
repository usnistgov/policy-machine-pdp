package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.Neo4jEmbeddedPAP;
import gov.nist.csd.pm.core.pdp.UnauthorizedException;
import gov.nist.csd.pm.proto.v1.adjudication.AdminCmdRequest;
import gov.nist.csd.pm.proto.v1.adjudication.AdminCmdResponse;
import gov.nist.csd.pm.proto.v1.cmd.AdminCommand;
import gov.nist.csd.pm.proto.v1.cmd.AssignCmd;
import gov.nist.csd.pm.proto.v1.cmd.GenericAdminCmd;
import gov.nist.csd.pm.proto.v1.model.Value;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
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

	@Nested
	class AdjudicateAdminCmdTest {

		@Test
		void shouldReturnCreatedNodeIdsAndComplete() throws PMException {
			AdminCmdRequest request = AdminCmdRequest.newBuilder().build();

			StreamObserver<AdminCmdResponse> observer = mock(StreamObserver.class);

			List<Value> values = new ArrayList<>();
			when(adjudicator.adjudicateAdminCommands(request.getCommandsList()))
					.thenReturn(values);

			service.adjudicateAdminCmd(request, observer);

			ArgumentCaptor<AdminCmdResponse> captor =
					ArgumentCaptor.forClass(AdminCmdResponse.class);
			verify(observer).onNext(captor.capture());
			AdminCmdResponse resp = captor.getValue();

			assertEquals(values, resp.getResultsList());

			verify(observer).onCompleted();
			verify(observer, never()).onError(any());
		}

		@Test
		void shouldOnErrorWhenAdjudicatorThrows() throws PMException {
			AdminCmdRequest request = AdminCmdRequest.newBuilder().build();
			StreamObserver<AdminCmdResponse> observer = mock(StreamObserver.class);

			doThrow(new RuntimeException("test exception"))
					.when(adjudicator)
					.adjudicateAdminCommands(request.getCommandsList());

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
			AdminCmdRequest request = AdminCmdRequest.newBuilder().build();
			StreamObserver<AdminCmdResponse> observer = mock(StreamObserver.class);

			UnauthorizedException unauthorizedException = mock(UnauthorizedException.class);
			when(unauthorizedException.getMessage()).thenReturn("unauthorized");

			doThrow(unauthorizedException)
					.when(adjudicator)
					.adjudicateAdminCommands(request.getCommandsList());

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
}