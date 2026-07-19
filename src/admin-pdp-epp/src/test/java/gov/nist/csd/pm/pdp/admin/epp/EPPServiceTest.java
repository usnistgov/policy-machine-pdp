package gov.nist.csd.pm.pdp.admin.epp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.pdp.admin.pdp.AdminAdjudicator;
import gov.nist.csd.pm.proto.v1.epp.EventContext;
import gov.nist.csd.pm.proto.v1.epp.ProcessEventResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EPPServiceTest {

    private AdminAdjudicator adjudicator;
    private EPPService eppService;

    @BeforeEach
    void setUp() {
        adjudicator = mock(AdminAdjudicator.class);
        eppService = new EPPService(adjudicator);
    }

    @Test
    @SuppressWarnings("unchecked")
    void processEvent_positiveRevision_returnsLastEventRevision() throws PMException {
        when(adjudicator.adjudicateTransaction(any())).thenReturn(42L);
        StreamObserver<ProcessEventResponse> observer = mock(StreamObserver.class);

        eppService.processEvent(EventContext.newBuilder().build(), observer);

        ArgumentCaptor<ProcessEventResponse> captor = ArgumentCaptor.forClass(ProcessEventResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        assertEquals(42L, captor.getValue().getResult().getValuesOrThrow("last_event_revision").getInt64Value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void processEvent_nonPositiveRevision_returnsEmptyResult() throws PMException {
        when(adjudicator.adjudicateTransaction(any())).thenReturn(0L);
        StreamObserver<ProcessEventResponse> observer = mock(StreamObserver.class);

        eppService.processEvent(EventContext.newBuilder().build(), observer);

        ArgumentCaptor<ProcessEventResponse> captor = ArgumentCaptor.forClass(ProcessEventResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        assertFalse(captor.getValue().hasResult());
    }

    @Test
    @SuppressWarnings("unchecked")
    void processEvent_error_returnsInternal() throws PMException {
        when(adjudicator.adjudicateTransaction(any())).thenThrow(new PMException("boom"));
        StreamObserver<ProcessEventResponse> observer = mock(StreamObserver.class);

        eppService.processEvent(EventContext.newBuilder().build(), observer);

        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(errorCaptor.capture());
        verify(observer, never()).onCompleted();

        assertInstanceOf(StatusRuntimeException.class, errorCaptor.getValue());
        assertEquals(Status.INTERNAL.getCode(),
                ((StatusRuntimeException) errorCaptor.getValue()).getStatus().getCode());
    }
}
