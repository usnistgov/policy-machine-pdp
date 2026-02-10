package gov.nist.csd.pm.pdp.admin.config;

import com.eventstore.dbclient.*;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import gov.nist.csd.pm.pdp.shared.interceptor.RevisionConsistencyInterceptor;
import io.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AdminPDPGrpcInterceptorConfigTest {

    private AdminPDPGrpcInterceptorConfig configBean;
    private AdminPDPConfig adminPDPConfig;
    private EventStoreDBConfig eventStoreDBConfig;
    private CurrentRevisionService currentRevisionService;
    private EventStoreConnectionManager connectionManager;
    private EventStoreDBClient esClient;

    @BeforeEach
    void setUp() {
        configBean = new AdminPDPGrpcInterceptorConfig();
        adminPDPConfig = new AdminPDPConfig();
        adminPDPConfig.setRevisionConsistencyTimeout(1000);
        eventStoreDBConfig = new EventStoreDBConfig("test-stream", "test-snapshots", "localhost", 2113);
        currentRevisionService = new CurrentRevisionService();
        connectionManager = mock(EventStoreConnectionManager.class);
        esClient = mock(EventStoreDBClient.class);
        when(connectionManager.getOrInitClient()).thenReturn(esClient);
    }

    @Test
    void consistencyInterceptor_createsInterceptor() {
        RevisionConsistencyInterceptor interceptor = configBean.consistencyInterceptor(
                adminPDPConfig,
                eventStoreDBConfig,
                currentRevisionService,
                connectionManager
        );

        assertNotNull(interceptor, "Interceptor should not be null");
    }

    @Test
    void consistencyInterceptor_excludesEPPServiceProcessEvent() throws Exception {
        RevisionConsistencyInterceptor interceptor = configBean.consistencyInterceptor(
                adminPDPConfig,
                eventStoreDBConfig,
                currentRevisionService,
                connectionManager
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>(
                "gov.nist.csd.pm.proto.v1.epp.EPPService/processEvent"
        );
        interceptor.interceptCall(call, new Metadata(), handler);

        assertTrue(handlerCalled.get(), "Handler should be called for excluded EPPService/processEvent");
        assertNull(call.closedStatus, "Call should not be closed for excluded method");
        verifyNoInteractions(esClient);
    }

    @Test
    void consistencyInterceptor_excludesAdjudicateOperation() throws Exception {
        RevisionConsistencyInterceptor interceptor = configBean.consistencyInterceptor(
                adminPDPConfig,
                eventStoreDBConfig,
                currentRevisionService,
                connectionManager
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>(
                "gov.nist.csd.pm.proto.v1.pdp.adjudication.AdminAdjudicationService/adjudicateOperation"
        );
        interceptor.interceptCall(call, new Metadata(), handler);

        assertTrue(handlerCalled.get(), "Handler should be called for excluded adjudicateOperation");
        assertNull(call.closedStatus, "Call should not be closed for excluded method");
        verifyNoInteractions(esClient);
    }

    @Test
    void consistencyInterceptor_excludesAdjudicateRoutine() throws Exception {
        RevisionConsistencyInterceptor interceptor = configBean.consistencyInterceptor(
                adminPDPConfig,
                eventStoreDBConfig,
                currentRevisionService,
                connectionManager
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>(
                "gov.nist.csd.pm.proto.v1.pdp.adjudication.AdminAdjudicationService/adjudicateRoutine"
        );
        interceptor.interceptCall(call, new Metadata(), handler);

        assertTrue(handlerCalled.get(), "Handler should be called for excluded adjudicateRoutine");
        assertNull(call.closedStatus, "Call should not be closed for excluded method");
        verifyNoInteractions(esClient);
    }

    @Test
    void consistencyInterceptor_nonExcludedMethod_performsCheck() throws Exception {
        currentRevisionService.set(10);
        mockLatestRevision(10);

        RevisionConsistencyInterceptor interceptor = configBean.consistencyInterceptor(
                adminPDPConfig,
                eventStoreDBConfig,
                currentRevisionService,
                connectionManager
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>(
                "gov.nist.csd.pm.proto.v1.pdp.query.PolicyQueryService/someMethod"
        );
        interceptor.interceptCall(call, new Metadata(), handler);

        assertTrue(handlerCalled.get(), "Handler should be called for non-excluded method when caught up");
        verify(esClient).readStream(eq("test-stream"), any(ReadStreamOptions.class));
    }

    @Test
    void consistencyInterceptor_nonExcludedMethod_blocksWhenNotCaughtUp() throws Exception {
        currentRevisionService.set(5);
        mockLatestRevision(100);
        adminPDPConfig.setRevisionConsistencyTimeout(50);

        RevisionConsistencyInterceptor interceptor = configBean.consistencyInterceptor(
                adminPDPConfig,
                eventStoreDBConfig,
                currentRevisionService,
                connectionManager
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>(
                "gov.nist.csd.pm.proto.v1.pdp.query.PolicyQueryService/someMethod"
        );
        interceptor.interceptCall(call, new Metadata(), handler);

        assertFalse(handlerCalled.get(), "Handler should not be called when not caught up");
        assertNotNull(call.closedStatus, "Call should be closed with error");
        assertEquals(Status.Code.UNAVAILABLE, call.closedStatus.getCode());
    }

    @Test
    void consistencyInterceptor_usesConfiguredTimeout() throws Exception {
        adminPDPConfig.setRevisionConsistencyTimeout(100);
        currentRevisionService.set(5);
        mockLatestRevision(10);

        RevisionConsistencyInterceptor interceptor = configBean.consistencyInterceptor(
                adminPDPConfig,
                eventStoreDBConfig,
                currentRevisionService,
                connectionManager
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>("test.Service/method");

        Thread catchUpThread = new Thread(() -> {
            try {
                Thread.sleep(30);
                currentRevisionService.set(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        catchUpThread.start();

        interceptor.interceptCall(call, new Metadata(), handler);
        catchUpThread.join();

        assertTrue(handlerCalled.get(), "Handler should be called after catching up within timeout");
    }

    private void mockLatestRevision(long revision) throws Exception {
        RecordedEvent recordedEvent = mock(RecordedEvent.class);
        when(recordedEvent.getRevision()).thenReturn(revision);

        ResolvedEvent resolvedEvent = mock(ResolvedEvent.class);
        when(resolvedEvent.getEvent()).thenReturn(recordedEvent);

        ReadResult readResult = mock(ReadResult.class);
        when(readResult.getEvents()).thenReturn(List.of(resolvedEvent));

        CompletableFuture<ReadResult> future = CompletableFuture.completedFuture(readResult);
        when(esClient.readStream(eq("test-stream"), any(ReadStreamOptions.class)))
                .thenReturn(future);
    }

    private static class TestServerCall<ReqT, RespT> extends ServerCall<ReqT, RespT> {
        private final String fullMethodName;
        Status closedStatus;

        TestServerCall(String fullMethodName) {
            this.fullMethodName = fullMethodName;
        }

        @Override
        public void request(int numMessages) {}

        @Override
        public void sendHeaders(Metadata headers) {}

        @Override
        public void sendMessage(RespT message) {}

        @Override
        public void close(Status status, Metadata trailers) {
            this.closedStatus = status;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public MethodDescriptor<ReqT, RespT> getMethodDescriptor() {
            return (MethodDescriptor<ReqT, RespT>) MethodDescriptor.newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(fullMethodName)
                    .setRequestMarshaller(mock(MethodDescriptor.Marshaller.class))
                    .setResponseMarshaller(mock(MethodDescriptor.Marshaller.class))
                    .build();
        }
    }
}
