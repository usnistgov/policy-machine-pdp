package gov.nist.csd.pm.pdp.shared.interceptor;

import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.LatestRevisionTracker;
import io.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RevisionConsistencyInterceptorTest {

    private CurrentRevisionService currentRevisionService;
    private LatestRevisionTracker latestRevisionTracker;

    @BeforeEach
    void setUp() throws InterruptedException, TimeoutException {
        currentRevisionService = new CurrentRevisionService();
        latestRevisionTracker = mock(LatestRevisionTracker.class);
        // Default: tracker is initialized and returns -1
        when(latestRevisionTracker.get(anyLong())).thenReturn(-1L);
    }

    @Test
    void interceptCall_excludedMethod_bypassesConsistencyCheck() {
        Set<String> excluded = new HashSet<>();
        excluded.add("test.Service/excludedMethod");

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, excluded, currentRevisionService, latestRevisionTracker
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
    }

    @Test
    void interceptCall_nonExcludedMethod_performsConsistencyCheck() throws InterruptedException, TimeoutException {
        Set<String> excluded = new HashSet<>();
        excluded.add("test.Service/excludedMethod");

        currentRevisionService.set(10);
        when(latestRevisionTracker.get(anyLong())).thenReturn(10L);

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, excluded, currentRevisionService, latestRevisionTracker
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
    void interceptCall_localRevisionCaughtUp_proceedsWithCall() throws InterruptedException, TimeoutException {
        currentRevisionService.set(15);
        when(latestRevisionTracker.get(anyLong())).thenReturn(10L);

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, currentRevisionService, latestRevisionTracker
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
    void interceptCall_localRevisionBehindButCatchesUp_proceedsWithCall() throws InterruptedException, TimeoutException {
        currentRevisionService.set(5);
        when(latestRevisionTracker.get(anyLong())).thenReturn(10L);

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, currentRevisionService, latestRevisionTracker
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

        try {
            catchUpThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertTrue(handlerCalled.get(), "Handler should be called after catching up");
        assertNull(call.closedStatus, "Call should not be closed after catching up");
    }

    @Test
    void interceptCall_timeoutWaitingForCatchUp_closesCallWithUnavailable() throws InterruptedException, TimeoutException {
        currentRevisionService.set(5);
        when(latestRevisionTracker.get(anyLong())).thenReturn(100L);

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                50, currentRevisionService, latestRevisionTracker
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
    void interceptCall_trackerNotInitialized_closesCallWithUnavailable() throws InterruptedException, TimeoutException {
        // tracker.get(timeout) throws TimeoutException when not initialized within timeout
        when(latestRevisionTracker.get(anyLong())).thenThrow(new TimeoutException("Latest revision tracker not initialized within timeout"));

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                50, currentRevisionService, latestRevisionTracker
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>("test.Service/method");
        interceptor.interceptCall(call, new Metadata(), handler);

        assertFalse(handlerCalled.get(), "Handler should not be called when tracker not initialized");
        assertNotNull(call.closedStatus, "Call should be closed when tracker not initialized");
        assertEquals(Status.Code.UNAVAILABLE, call.closedStatus.getCode());
    }

    @Test
    void interceptCall_trackerBecomesInitialized_proceedsWithCall() throws InterruptedException, TimeoutException {
        when(latestRevisionTracker.get(anyLong())).thenReturn(10L);
        currentRevisionService.set(10);

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, currentRevisionService, latestRevisionTracker
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>("test.Service/method");
        interceptor.interceptCall(call, new Metadata(), handler);

        assertTrue(handlerCalled.get(), "Handler should be called after tracker initializes");
        assertNull(call.closedStatus, "Call should not be closed after tracker initializes");
    }

    @Test
    void interceptCall_emptyEventStream_proceedsWithCall() throws InterruptedException, TimeoutException {
        currentRevisionService.set(-1);
        when(latestRevisionTracker.get(anyLong())).thenReturn(-1L);

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, currentRevisionService, latestRevisionTracker
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
    void constructor_withoutExcludedMethods_createsEmptySet() throws InterruptedException, TimeoutException {
        currentRevisionService.set(10);
        when(latestRevisionTracker.get(anyLong())).thenReturn(10L);

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, currentRevisionService, latestRevisionTracker
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
                1000, excluded, currentRevisionService, latestRevisionTracker
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
    }

    @Test
    void interceptCall_localRevisionExactlyMatchesLatest_proceedsWithCall() throws InterruptedException, TimeoutException {
        currentRevisionService.set(42);
        when(latestRevisionTracker.get(anyLong())).thenReturn(42L);

        RevisionConsistencyInterceptor interceptor = new RevisionConsistencyInterceptor(
                1000, currentRevisionService, latestRevisionTracker
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
