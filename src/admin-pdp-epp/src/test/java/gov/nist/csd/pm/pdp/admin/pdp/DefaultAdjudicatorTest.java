package gov.nist.csd.pm.pdp.admin.pdp;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.WrongExpectedVersionException;
import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.epp.EPP;
import gov.nist.ngac.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.admin.pap.EventTrackingPAP;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultAdjudicatorTest {

    private ContextFactory contextFactory;
    private CurrentRevisionService currentRevision;
    private EventStoreConnectionManager connectionManager;
    private EventTrackingPAP pap;
    private DefaultAdjudicator adjudicator;

    @BeforeEach
    void setUp() throws PMException {
        contextFactory = mock(ContextFactory.class);
        currentRevision = mock(CurrentRevisionService.class);
        connectionManager = mock(EventStoreConnectionManager.class);
        EventStoreDBConfig config = mock(EventStoreDBConfig.class);
        pap = mock(EventTrackingPAP.class);

        when(config.getEventStream()).thenReturn("pm-events");
        when(connectionManager.getOrInitClient()).thenReturn(mock(EventStoreDBClient.class));

        NGACContext ctx = new NGACContext(mock(PDP.class), mock(EPP.class), pap);
        when(contextFactory.createContext()).thenReturn(ctx);

        adjudicator = new DefaultAdjudicator(config, connectionManager, currentRevision, contextFactory);
    }

    @Test
    void adjudicateTransaction_success_publishesAndReturnsRevision() throws PMException {
        when(currentRevision.get()).thenReturn(3L);
        when(pap.publishToEventStore(any(), eq("pm-events"), eq(3L))).thenReturn(8L);

        long revision = adjudicator.adjudicateTransaction(ctx -> { /* no-op tx */ });

        assertEquals(8L, revision);
        verify(contextFactory, times(1)).createContext();
        verify(pap).publishToEventStore(any(), eq("pm-events"), eq(3L));
    }

    @Test
    void adjudicateTransaction_pmException_notRetriedAndUnwrapped() throws PMException {
        PMException thrown = assertThrows(PMException.class,
                () -> adjudicator.adjudicateTransaction(ctx -> {
                    throw new PMException("policy violation");
                }));

        assertEquals("policy violation", thrown.getMessage());
        // PMException is not a retryable exception -> context created exactly once
        verify(contextFactory, times(1)).createContext();
    }

    @Test
    void adjudicateTransaction_wrongExpectedVersion_retriesUpToMaxAttempts() throws PMException {
        WrongExpectedVersionException conflict = mock(WrongExpectedVersionException.class);

        assertThrows(WrongExpectedVersionException.class,
                () -> adjudicator.adjudicateTransaction(ctx -> {
                    throw conflict;
                }));

        // retry config is maxAttempts=3
        verify(contextFactory, times(3)).createContext();
    }

    @Test
    void adjudicateOperation_wrongExpectedVersion_isRetried() throws PMException {
        // a version conflict raised while publishing must not be wrapped before the retry sees it
        when(pap.publishToEventStore(any(), anyString(), anyLong()))
                .thenThrow(mock(WrongExpectedVersionException.class));

        assertThrows(WrongExpectedVersionException.class,
                () -> adjudicator.adjudicateOperation("op", java.util.Map.of()));

        verify(contextFactory, times(3)).createContext();
    }
}
