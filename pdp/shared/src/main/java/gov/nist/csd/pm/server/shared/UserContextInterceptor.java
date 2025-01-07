package gov.nist.csd.pm.server.shared;

import io.grpc.*;

public class UserContextInterceptor implements ServerInterceptor {

	public static final String PM_USER_KEY = "x-pm-user";
	public static final String PM_PROCESS_KEY = "x-pm-process";

	// Define a Context Key to store the header value
	private static final Context.Key<String> PM_USER_HEADER_KEY = Context.key(PM_USER_KEY);
	private static final Context.Key<String> PM_PROCESS_HEADER_KEY = Context.key(PM_PROCESS_KEY);

	@Override
	public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
			ServerCall<ReqT, RespT> call,
			Metadata headers,
			ServerCallHandler<ReqT, RespT> next) {

		Metadata.Key<String> pmUserHeaderKey = Metadata.Key.of(PM_USER_KEY, Metadata.ASCII_STRING_MARSHALLER);
		String pmUserHeaderValue = headers.get(pmUserHeaderKey);

		Metadata.Key<String> pmProcessHeaderKey = Metadata.Key.of(PM_PROCESS_KEY, Metadata.ASCII_STRING_MARSHALLER);
		String pmProcessHeaderValue = headers.get(pmProcessHeaderKey);

		// Print or log the header value (optional)
		System.out.println(PM_USER_KEY + ": " + pmUserHeaderValue);
		System.out.println(PM_PROCESS_KEY + ": " + pmProcessHeaderValue);

		// Store the header value in the gRPC Context
		Context context = Context.current()
				.withValue(PM_USER_HEADER_KEY, pmUserHeaderValue != null ? pmUserHeaderValue : "")
				.withValue(PM_PROCESS_HEADER_KEY, pmProcessHeaderValue != null ? pmProcessHeaderValue : "");

		// Continue the call with the updated Context
		return Contexts.interceptCall(context, call, headers, next);
	}

	// Utility method to retrieve the header value in the service
	public static String getPmUserHeaderValue() {
		return PM_USER_HEADER_KEY.get();
	}

	public static String getPmProcessHeaderValue() {
		return PM_PROCESS_HEADER_KEY.get();
	}
}
