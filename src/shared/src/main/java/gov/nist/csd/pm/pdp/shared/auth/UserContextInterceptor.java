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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@GrpcGlobalServerInterceptor
@ConditionalOnProperty(name = "pm.pdp.auth.mode", havingValue = "none", matchIfMissing = true)
public class UserContextInterceptor implements ServerInterceptor {

    public static final String PM_USER_KEY = "x-pm-user";
    public static final String PM_USER_ATTRS_KEY = "x-pm-user-attrs";
    public static final String PM_PROCESS_KEY = "x-pm-process";

    public static final Metadata.Key<String> PM_USER_METADATA_KEY = Metadata.Key.of(PM_USER_KEY, Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> PM_USER_ATTRS_METADATA_KEY = Metadata.Key.of(PM_USER_ATTRS_KEY, Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> PM_PROCESS_METADATA_KEY = Metadata.Key.of(PM_PROCESS_KEY, Metadata.ASCII_STRING_MARSHALLER);

    public static final Context.Key<String> PM_USER_CONTEXT_KEY = Context.key(PM_USER_KEY);
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
                logger.warn("error parsing user attributes in header: {}", e.getMessage());
                call.close(Status.INVALID_ARGUMENT
                        .withDescription("invalid " + PM_USER_ATTRS_KEY + " header: " + e.getMessage())
                        .withCause(e), new Metadata());
                return new ServerCall.Listener<>() {};
            }
        }

        logger.debug("user header values user={} attributes={} process={}",
                pmUserHeaderValue, pmUserAttrsHeaderValue, pmProcessHeaderValue);

        Context context = Context.current();
        if (pmUserHeaderValue != null) {
            context = context.withValue(PM_USER_CONTEXT_KEY, pmUserHeaderValue);
        }

        if (pmUserAttrsHeaderValue != null) {
            context = context.withValue(PM_USER_ATTRS_CONTEXT_KEY, pmUserAttrsHeaderValue);
        }

        if (pmProcessHeaderValue != null) {
            context = context.withValue(PM_PROCESS_CONTEXT_KEY, pmProcessHeaderValue);
        }

        return Contexts.interceptCall(context, call, headers, next);
    }

    public static String getPmUserHeaderValue() {
        return PM_USER_CONTEXT_KEY.get();
    }

    public static List<String> getPmUserAttrsHeaderValue() {
        return PM_USER_ATTRS_CONTEXT_KEY.get();
    }

    public static String getPmProcessHeaderValue() {
        return PM_PROCESS_CONTEXT_KEY.get();
    }
}
