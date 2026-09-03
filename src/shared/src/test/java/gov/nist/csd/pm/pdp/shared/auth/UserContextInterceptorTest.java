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