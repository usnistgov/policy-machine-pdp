package gov.nist.csd.pm.server.shared.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;

@Component
@GrpcGlobalServerInterceptor
public class UserContextInterceptor implements ServerInterceptor {

    public static final String PM_USER_KEY = "x-pm-user-id";
    public static final String PM_USER_ATTRS_KEY = "x-pm-user-attrs";
    public static final String PM_PROCESS_KEY = "x-pm-process";

    public static final Metadata.Key<String> PM_USER_METADATA_KEY = Metadata.Key.of(PM_USER_KEY, Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> PM_USER_ATTRS_METADATA_KEY = Metadata.Key.of(PM_USER_ATTRS_KEY, Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> PM_PROCESS_METADATA_KEY = Metadata.Key.of(PM_PROCESS_KEY, Metadata.ASCII_STRING_MARSHALLER);

    public static final Context.Key<Long> PM_USER_CONTEXT_KEY = Context.key(PM_USER_KEY);
    public static final Context.Key<List<String>> PM_USER_ATTRS_CONTEXT_KEY = Context.key(PM_USER_ATTRS_KEY);
    public static final Context.Key<String> PM_PROCESS_CONTEXT_KEY = Context.key(PM_PROCESS_KEY);

    private Logger logger = LoggerFactory.getLogger(UserContextInterceptor.class);
    private static ObjectMapper userAttrsMapper = new ObjectMapper();

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call,
        Metadata headers,
        ServerCallHandler<ReqT, RespT> next) {

        String pmUserHeaderValue = headers.get(PM_USER_METADATA_KEY);
        String pmProcessHeaderValue = headers.get(PM_PROCESS_METADATA_KEY);
        String attrsStr = headers.get(PM_USER_ATTRS_METADATA_KEY);
        List<String> pmUserAttrsHeaderValue = null;
        if (attrsStr != null) {
            try {
                pmUserAttrsHeaderValue = userAttrsMapper.readValue(attrsStr, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                logger.error("error parsing user attributes in header", e);
                throw new RuntimeException(e);
            }
        }

        logger.debug("user header values user={} attributes={} process={}", pmUserHeaderValue, pmUserAttrsHeaderValue, pmProcessHeaderValue);

        Context context = Context.current();
        if (pmUserHeaderValue != null) {
            context = context.withValue(PM_USER_CONTEXT_KEY, Long.parseLong(pmUserHeaderValue));
        }

        if (pmUserAttrsHeaderValue != null) {
            context = context.withValue(PM_USER_ATTRS_CONTEXT_KEY, pmUserAttrsHeaderValue);
        }

        if (pmProcessHeaderValue != null) {
            context = context.withValue(PM_PROCESS_CONTEXT_KEY, pmProcessHeaderValue);
        }

        return Contexts.interceptCall(context, call, headers, next);
    }

    public static Long getPmUserHeaderValue() {
        return PM_USER_CONTEXT_KEY.get();
    }

    public static List<String> getPmUserAttrsHeaderValue() {
        return PM_USER_ATTRS_CONTEXT_KEY.get();
    }

    public static String getPmProcessHeaderValue() {
        return PM_PROCESS_CONTEXT_KEY.get();
    }
}
