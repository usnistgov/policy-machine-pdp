package gov.nist.csd.pm.pdp.resource.epp;

import gov.nist.csd.pm.core.common.event.EventContext;
import gov.nist.csd.pm.core.common.event.EventContextUser;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.epp.proto.ResourceEPPServiceGrpc;
import gov.nist.csd.pm.epp.proto.SideEffectEvents;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.resource.config.EPPMode;
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
	@Mock private ResourceEPPServiceGrpc.ResourceEPPServiceBlockingStub blockingStub;
	@Mock private SideEffectEvents response;

	private EPPClient eppClient;

	@BeforeEach
	void setup() {
		when(config.getEppMode()).thenReturn(EPPMode.SYNC);

		eppClient = new EPPClient(pdp, pap, listener, config);

		ReflectionTestUtils.setField(eppClient, "blockingStub", blockingStub);
	}

	@Test
	void testProcessEventSync_WithSideEffects() throws Exception {
		when(config.getEppSideEffectTimeout()).thenReturn(2);

		EventContext eventCtx = mock(EventContext.class);
		when(eventCtx.user()).thenReturn(new EventContextUser("u1"));
		when(eventCtx.opName()).thenReturn("op1");
		when(eventCtx.args()).thenReturn(Map.of("target", "o1"));

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
		when(eventCtx.user()).thenReturn(new EventContextUser("u1"));
		when(eventCtx.opName()).thenReturn("op1");
		when(eventCtx.args()).thenReturn(Map.of("target", "o1"));

		PMEvent pmEvent = mock(PMEvent.class);
		List<PMEvent> sideEffects = List.of(pmEvent);

		when(blockingStub.processEvent(any())).thenReturn(response);
		when(response.getStartRevision()).thenReturn(0L);
		when(response.getEventsList()).thenReturn(sideEffects);

		eppClient.processEvent(eventCtx);

		verify(listener, never()).processOrQueue(0L, sideEffects);
	}
}