package gov.nist.csd.pm.pdp.shared.auth;

import io.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static gov.nist.csd.pm.pdp.shared.auth.UserContextInterceptor.*;
import static org.junit.jupiter.api.Assertions.*;

class UserContextInterceptorTest {

	private UserContextInterceptor interceptor;

	@BeforeEach
	void setUp() {
		interceptor = new UserContextInterceptor();
	}

	@Test
	void interceptCall_withAllHeaders_setsValues() {
		Metadata headers = new Metadata();

		headers.put(PM_USER_METADATA_KEY, "123");
		headers.put(PM_USER_ATTRS_METADATA_KEY, "[\"a\", \"b\"]");
		headers.put(PM_PROCESS_METADATA_KEY, "123");

		ServerCallHandler<String, String> handler = (call1, headers1) -> {
			assertEquals("123", UserContextInterceptor.getPmUserHeaderValue());
			assertEquals("123", UserContextInterceptor.getPmProcessHeaderValue());
			assertEquals(List.of("a", "b"), UserContextInterceptor.getPmUserAttrsHeaderValue());
			return new ServerCall.Listener<>() {};
		};

		interceptor.interceptCall(new NoopServerCall<>(), headers, handler);
	}

	@Test
	void interceptCall_withoutHeaders_setsToNull() {
		Metadata headers = new Metadata();

		ServerCallHandler<String, String> handler = (call1, headers1) -> {
			assertNull(UserContextInterceptor.getPmUserHeaderValue());
			assertNull(UserContextInterceptor.getPmUserAttrsHeaderValue());
			assertNull(UserContextInterceptor.getPmProcessHeaderValue());
			return new ServerCall.Listener<>() {};
		};

		interceptor.interceptCall(new NoopServerCall<>(), headers, handler);
	}

	@Test
	void interceptCall_invalidAttrsFormat_closesWithInvalidArgument() {
		Metadata headers = new Metadata();

		headers.put(PM_USER_ATTRS_METADATA_KEY, "\"test\"");

		boolean[] handlerInvoked = {false};
		ServerCallHandler<String, String> handler = (call1, headers1) -> {
			handlerInvoked[0] = true;
			return new ServerCall.Listener<>() {};
		};

		NoopServerCall<String, String> call = new NoopServerCall<>();
		interceptor.interceptCall(call, headers, handler);

		assertNotNull(call.closedStatus);
		assertEquals(Status.Code.INVALID_ARGUMENT, call.closedStatus.getCode());
		assertFalse(handlerInvoked[0], "downstream handler must not be invoked on bad header");
	}

	private static class NoopServerCall<ReqT, RespT> extends ServerCall<ReqT, RespT> {
		private Status closedStatus;
		@Override public void request(int numMessages) {}
		@Override public void sendHeaders(Metadata headers) {}
		@Override public void sendMessage(RespT message) {}
		@Override public void close(Status status, Metadata trailers) { this.closedStatus = status; }
		@Override public boolean isCancelled() { return false; }
		@Override public MethodDescriptor<ReqT, RespT> getMethodDescriptor() { return null; }
	}
}