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
 * licensed to NIST. To the extent that NIST holds copyright in this software, it is
 * being made available under the Creative Commons Attribution 4.0 International
 * license (CC BY 4.0). The disclaimers of the CC BY 4.0 license apply to all parts
 * of the software developed or licensed by NIST.
 *
 * ACCESS THE FULL CC BY 4.0 LICENSE HERE:
 * https://creativecommons.org/licenses/by/4.0/legalcode
 */

package gov.nist.csd.pm.pdp.resource.config;

import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.LatestRevisionTracker;
import gov.nist.csd.pm.pdp.shared.interceptor.RevisionConsistencyInterceptor;
import io.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class ResourcePDPGrpcInterceptorConfigTest {

    private ResourcePDPGrpcInterceptorConfig configBean;
    private ResourcePDPConfig resourcePDPConfig;
    private CurrentRevisionService currentRevisionService;
    private LatestRevisionTracker latestRevisionTracker;

    @BeforeEach
    void setUp() throws InterruptedException, TimeoutException {
        configBean = new ResourcePDPGrpcInterceptorConfig();
        resourcePDPConfig = new ResourcePDPConfig("localhost", 9090, 1000);
        currentRevisionService = new CurrentRevisionService();
        latestRevisionTracker = mock(LatestRevisionTracker.class);
        when(latestRevisionTracker.get(anyLong())).thenReturn(-1L);
    }

    @Test
    void consistencyInterceptor_createsInterceptor() {
        RevisionConsistencyInterceptor interceptor = configBean.consistencyInterceptor(
                resourcePDPConfig,
                currentRevisionService,
                latestRevisionTracker
        );

        assertNotNull(interceptor, "Interceptor should not be null");
    }

    @Test
    void consistencyInterceptor_hasNoExcludedMethods() throws InterruptedException, TimeoutException {
        currentRevisionService.set(10);
        when(latestRevisionTracker.get(anyLong())).thenReturn(10L);

        RevisionConsistencyInterceptor interceptor = configBean.consistencyInterceptor(
                resourcePDPConfig,
                currentRevisionService,
                latestRevisionTracker
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>(
                "gov.nist.ngac.pm.proto.v1.epp.EPPService/processEvent"
        );
        interceptor.interceptCall(call, new Metadata(), handler);

        assertTrue(handlerCalled.get(), "Handler should be called");
        verify(latestRevisionTracker).get(anyLong());
    }

    @Test
    void consistencyInterceptor_checksConsistencyForAllMethods() throws InterruptedException, TimeoutException {
        currentRevisionService.set(10);
        when(latestRevisionTracker.get(anyLong())).thenReturn(10L);

        RevisionConsistencyInterceptor interceptor = configBean.consistencyInterceptor(
                resourcePDPConfig,
                currentRevisionService,
                latestRevisionTracker
        );

        String[] methodNames = {
                "gov.nist.ngac.pm.proto.v1.pdp.query.PolicyQueryService/query",
                "gov.nist.ngac.pm.proto.v1.pdp.access.AccessService/check",
                "any.Service/anyMethod"
        };

        for (String methodName : methodNames) {
            reset(latestRevisionTracker);
            when(latestRevisionTracker.get(anyLong())).thenReturn(10L);

            AtomicBoolean handlerCalled = new AtomicBoolean(false);
            ServerCallHandler<String, String> handler = (call, headers) -> {
                handlerCalled.set(true);
                return new ServerCall.Listener<>() {};
            };

            TestServerCall<String, String> call = new TestServerCall<>(methodName);
            interceptor.interceptCall(call, new Metadata(), handler);

            assertTrue(handlerCalled.get(), "Handler should be called for: " + methodName);
            verify(latestRevisionTracker).get(anyLong());
        }
    }

    @Test
    void consistencyInterceptor_blocksWhenNotCaughtUp() throws InterruptedException, TimeoutException {
        currentRevisionService.set(5);
        when(latestRevisionTracker.get(anyLong())).thenReturn(100L);
        resourcePDPConfig = new ResourcePDPConfig("localhost", 9090, 50);

        RevisionConsistencyInterceptor interceptor = configBean.consistencyInterceptor(
                resourcePDPConfig,
                currentRevisionService,
                latestRevisionTracker
        );

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        ServerCallHandler<String, String> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() {};
        };

        TestServerCall<String, String> call = new TestServerCall<>("test.Service/method");
        interceptor.interceptCall(call, new Metadata(), handler);

        assertFalse(handlerCalled.get(), "Handler should not be called when not caught up");
        assertNotNull(call.closedStatus, "Call should be closed with error");
        assertEquals(Status.Code.UNAVAILABLE, call.closedStatus.getCode());
    }

    @Test
    void consistencyInterceptor_proceedsWhenCaughtUp() throws InterruptedException, TimeoutException {
        currentRevisionService.set(15);
        when(latestRevisionTracker.get(anyLong())).thenReturn(10L);

        RevisionConsistencyInterceptor interceptor = configBean.consistencyInterceptor(
                resourcePDPConfig,
                currentRevisionService,
                latestRevisionTracker
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
    void consistencyInterceptor_usesConfiguredTimeout() throws InterruptedException, TimeoutException {
        resourcePDPConfig = new ResourcePDPConfig("localhost", 9090, 200);
        currentRevisionService.set(5);
        when(latestRevisionTracker.get(anyLong())).thenReturn(10L);

        RevisionConsistencyInterceptor interceptor = configBean.consistencyInterceptor(
                resourcePDPConfig,
                currentRevisionService,
                latestRevisionTracker
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

        assertTrue(handlerCalled.get(), "Handler should be called after catching up within timeout");
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
