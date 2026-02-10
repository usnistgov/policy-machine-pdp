package gov.nist.csd.pm.pdp.shared.interceptor;

import com.eventstore.dbclient.*;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import io.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RevisionConsistencyInterceptorTest {

    private CurrentRevisionService currentRevisionService;
    private EventStoreConnectionManager connectionManager;
    private EventStoreDBConfig config;
    private EventStoreDBClient esClient;

    @BeforeEach
    void setUp() {
        currentRevisionService = new CurrentRevisionService();
        connectionManager = mock(EventStoreConnectionManager.class);
        config = new EventStoreDBConfig("test-stream", "test-snapshots", "localhost", 2113);
        esClient = mock(EventStoreDBClient.class);
        when(connectionManager.getOrInitClient()).thenReturn(esClient);
    }

    @Test
    void interceptCall_excludedMethod_bypassesConsistencyCheck() {
        Set<String> excluded = new HashSet<>();
        excluded.add("test.Service/excludedMethod");

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, excluded, config, currentRevisionService, connectionManager
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>("test.Service/excludedMethod");
        interceptor.interceptCall(call, new Metadata(), handler);

        assertTrue(handlerCalled.get(), "Handler should be called for excluded method");
        assertNull(call.closedStatus, "Call should not be closed for excluded method");
        verifyNoInteractions(esClient);
    }

    @Test
    void interceptCall_nonExcludedMethod_performsConsistencyCheck() throws Exception {
        Set<String> excluded = new HashSet<>();
        excluded.add("test.Service/excludedMethod");

        currentRevisionService.set(10);
        mockLatestRevision(10);

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, excluded, config, currentRevisionService, connectionManager
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>("test.Service/nonExcludedMethod");
        interceptor.interceptCall(call, new Metadata(), handler);

        assertTrue(handlerCalled.get(), "Handler should be called when caught up");
        assertNull(call.closedStatus, "Call should not be closed when caught up");
    }

    @Test
    void interceptCall_localRevisionCaughtUp_proceedsWithCall() throws Exception {
        currentRevisionService.set(15);
        mockLatestRevision(10);

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, config, currentRevisionService, connectionManager
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>("test.Service/method");
        interceptor.interceptCall(call, new Metadata(), handler);

        assertTrue(handlerCalled.get(), "Handler should be called when local revision >= latest");
        assertNull(call.closedStatus, "Call should not be closed when caught up");
    }

    @Test
    void interceptCall_localRevisionBehindButCatchesUp_proceedsWithCall() throws Exception {
        currentRevisionService.set(5);
        mockLatestRevision(10);

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, config, currentRevisionService, connectionManager
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>("test.Service/method");

        Thread catchUpThread = new Thread(() -> {
            try {
                Thread.sleep(50);
                currentRevisionService.set(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        catchUpThread.start();

        interceptor.interceptCall(call, new Metadata(), handler);

        catchUpThread.join();

        assertTrue(handlerCalled.get(), "Handler should be called after catching up");
        assertNull(call.closedStatus, "Call should not be closed after catching up");
    }

    @Test
    void interceptCall_timeoutWaitingForCatchUp_closesCallWithUnavailable() throws Exception {
        currentRevisionService.set(5);
        mockLatestRevision(100);

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                50, config, currentRevisionService, connectionManager
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>("test.Service/method");
        interceptor.interceptCall(call, new Metadata(), handler);

        assertFalse(handlerCalled.get(), "Handler should not be called on timeout");
        assertNotNull(call.closedStatus, "Call should be closed on timeout");
        assertEquals(Status.Code.UNAVAILABLE, call.closedStatus.getCode());
        assertTrue(call.closedStatus.getDescription().contains("stale"));
    }

    @Test
    void interceptCall_eventStoreException_closesCallWithUnavailable() throws Exception {
        currentRevisionService.set(5);

        CompletableFuture<ReadResult> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new TimeoutException("Connection timeout"));
        when(esClient.readStream(eq("test-stream"), any(ReadStreamOptions.class)))
                .thenReturn(failedFuture);

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, config, currentRevisionService, connectionManager
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>("test.Service/method");
        interceptor.interceptCall(call, new Metadata(), handler);

        assertFalse(handlerCalled.get(), "Handler should not be called on exception");
        assertNotNull(call.closedStatus, "Call should be closed on exception");
        assertEquals(Status.Code.UNAVAILABLE, call.closedStatus.getCode());
        assertTrue(call.closedStatus.getDescription().contains("errored"));
    }

    @Test
    void interceptCall_emptyEventStream_proceedsWithCall() throws Exception {
        currentRevisionService.set(-1);
        mockEmptyEventStream();

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, config, currentRevisionService, connectionManager
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>("test.Service/method");
        interceptor.interceptCall(call, new Metadata(), handler);

        assertTrue(handlerCalled.get(), "Handler should be called for empty event stream");
        assertNull(call.closedStatus, "Call should not be closed for empty event stream");
    }

    @Test
    void constructor_withoutExcludedMethods_createsEmptySet() throws Exception {
        currentRevisionService.set(10);
        mockLatestRevision(10);

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, config, currentRevisionService, connectionManager
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>("any.Service/anyMethod");
        interceptor.interceptCall(call, new Metadata(), handler);

        assertTrue(handlerCalled.get(), "Handler should be called when no exclusions and caught up");
    }

    @Test
    void interceptCall_multipleExcludedMethods_allBypassed() {
        Set<String> excluded = new HashSet<>();
        excluded.add("test.Service/method1");
        excluded.add("test.Service/method2");
        excluded.add("other.Service/method3");

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, excluded, config, currentRevisionService, connectionManager
        );

        for (String methodName : excluded) {
            AtomicBoolean handlerCalled = new AtomicBoolean(false);
            ServerCallHandler<String, String> handler = (call, headers) -> {
                handlerCalled.set(true);
                return new ServerCall.Listener<>() {};
            };

            TestServerCall<String, String> call = new TestServerCall<>(methodName);
            interceptor.interceptCall(call, new Metadata(), handler);

            assertTrue(handlerCalled.get(), "Handler should be called for excluded method: " + methodName);
        }

        verifyNoInteractions(esClient);
    }

    @Test
    void interceptCall_localRevisionExactlyMatchesLatest_proceedsWithCall() throws Exception {
        currentRevisionService.set(42);
        mockLatestRevision(42);

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, config, currentRevisionService, connectionManager
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>("test.Service/method");
        interceptor.interceptCall(call, new Metadata(), handler);

        assertTrue(handlerCalled.get(), "Handler should be called when revisions match exactly");
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

    private void mockEmptyEventStream() throws Exception {
        ReadResult readResult = mock(ReadResult.class);
        when(readResult.getEvents()).thenReturn(Collections.emptyList());

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
