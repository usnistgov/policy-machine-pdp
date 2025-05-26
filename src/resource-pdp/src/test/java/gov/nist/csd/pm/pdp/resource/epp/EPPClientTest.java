package gov.nist.csd.pm.pdp.resource.epp;

import gov.nist.csd.pm.core.common.event.EventContext;
import gov.nist.csd.pm.epp.proto.EPPGrpc;
import gov.nist.csd.pm.epp.proto.EPPResponse;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.resource.config.ResourcePDPConfig;
import gov.nist.csd.pm.pdp.resource.eventstore.PolicyEventSubscriptionListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EPPClientTest {

	@Mock
	private PDP pdp;
	@Mock private PAP pap;
	@Mock private ResourcePDPConfig config;
	@Mock private PolicyEventSubscriptionListener listener;
	@Mock private EPPGrpc.EPPBlockingStub blockingStub;
	@Mock private EPPResponse response;

	private EPPClient eppClient;

	@BeforeEach
	void setup() {
		when(config.getAdminHostname()).thenReturn("localhost");
		when(config.getAdminPort()).thenReturn(50051);
		when(config.isEppAsync()).thenReturn(false);

		eppClient = new EPPClient(pdp, pap, config, listener, config);

		ReflectionTestUtils.setField(eppClient, "blockingStub", blockingStub);
	}

	@Test
	void testProcessEventSync_WithSideEffects() throws Exception {
		when(config.getEppSideEffectTimeout()).thenReturn(2);

		EventContext eventCtx = mock(EventContext.class);
		when(eventCtx.getUser()).thenReturn("u1");
		when(eventCtx.getOpName()).thenReturn("op1");
		when(eventCtx.getArgs()).thenReturn(Map.of());

		PMEvent pmEvent = mock(PMEvent.class);
		List<PMEvent> sideEffects = List.of(pmEvent);
		CompletableFuture<Void> future = CompletableFuture.completedFuture(null);

		when(blockingStub.processEvent(any())).thenReturn(response);
		when(response.getStartRevision()).thenReturn(100L);
		when(response.getEventsList()).thenReturn(sideEffects);
		when(listener.processOrQueue(eq(100L), eq(sideEffects))).thenReturn(future);

		eppClient.processEvent(eventCtx);

		verify(listener).processOrQueue(100L, sideEffects);
	}

	@Test
	void testProcessEventSync_WithNoSideEffects() throws Exception {
		EventContext eventCtx = mock(EventContext.class);
		when(eventCtx.getUser()).thenReturn("u1");
		when(eventCtx.getOpName()).thenReturn("op1");
		when(eventCtx.getArgs()).thenReturn(Map.of());

		PMEvent pmEvent = mock(PMEvent.class);
		List<PMEvent> sideEffects = List.of(pmEvent);
		CompletableFuture<Void> future = CompletableFuture.completedFuture(null);

		when(blockingStub.processEvent(any())).thenReturn(response);
		when(response.getStartRevision()).thenReturn(0L);
		when(response.getEventsList()).thenReturn(sideEffects);

		eppClient.processEvent(eventCtx);

		verify(listener, never()).processOrQueue(0L, sideEffects);
	}
}