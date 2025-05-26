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
		// Empty metadata
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
	void interceptCall_invalidAttrsFormat_throwsRuntimeException() {
		// Empty metadata
		Metadata headers = new Metadata();

		headers.put(PM_USER_ATTRS_METADATA_KEY, "\"test\"");

		ServerCallHandler<String, String> handler = (call1, headers1) -> {
			assertNull(UserContextInterceptor.getPmUserHeaderValue());
			assertNull(UserContextInterceptor.getPmUserAttrsHeaderValue());
			assertNull(UserContextInterceptor.getPmProcessHeaderValue());
			return new ServerCall.Listener<>() {};
		};

		assertThrows(RuntimeException.class, () -> interceptor.interceptCall(new NoopServerCall<>(), headers, handler));
	}

	private static class NoopServerCall<ReqT, RespT> extends ServerCall<ReqT, RespT> {
		@Override public void request(int numMessages) {}
		@Override public void sendHeaders(Metadata headers) {}
		@Override public void sendMessage(RespT message) {}
		@Override public void close(Status status, Metadata trailers) {}
		@Override public boolean isCancelled() { return false; }
		@Override public MethodDescriptor<ReqT, RespT> getMethodDescriptor() { return null; }
	}
}