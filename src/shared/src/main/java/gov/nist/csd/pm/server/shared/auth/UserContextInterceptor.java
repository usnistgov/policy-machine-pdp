package gov.nist.csd.pm.server.shared.auth;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@GrpcGlobalServerInterceptor
public class UserContextInterceptor implements ServerInterceptor {

    public static final String PM_USER_KEY = "x-pm-user";
    public static final String PM_PROCESS_KEY = "x-pm-process";

    // Define a Context Key to store the header value
    Logger logger = LoggerFactory.getLogger(UserContextInterceptor.class);
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

        logger.debug("user header value {}|{} = {}|{}", PM_USER_KEY, PM_PROCESS_KEY,
            pmUserHeaderValue, pmProcessHeaderValue);

        Context context = Context.current()
            .withValue(PM_USER_HEADER_KEY, pmUserHeaderValue != null ? pmUserHeaderValue : "")
            .withValue(PM_PROCESS_HEADER_KEY, pmProcessHeaderValue != null ? pmProcessHeaderValue : "");

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
