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
