package gov.nist.csd.pm.pdp.shared.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@GrpcGlobalServerInterceptor
@ConditionalOnProperty(name = "pm.pdp.auth-mode", havingValue = "jwt")
public class JwtUserContextInterceptor implements ServerInterceptor {

    public static final String PM_TOKEN_KEY = "x-pm-token";

    public static final Metadata.Key<String> PM_TOKEN_METADATA_KEY =
            Metadata.Key.of(PM_TOKEN_KEY, Metadata.ASCII_STRING_MARSHALLER);

    // Stores one map per actor in the delegation chain, outermost first.
    // Each map contains the extracted username-claim and/or user-attrs-claim values.
    public static final Context.Key<List<Map<String, Object>>> PM_JWT_ACTORS_CONTEXT_KEY =
            Context.key("x-pm-jwt-actors");

    private static final Logger logger = LoggerFactory.getLogger(JwtUserContextInterceptor.class);

    private final String usernameClaim;
    private final String userAttrsClaim;

    public JwtUserContextInterceptor(AuthConfig authConfig) {
        this.usernameClaim = authConfig.getUsernameClaim();
        this.userAttrsClaim = authConfig.getUserAttrsClaim();
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String tokenStr = headers.get(PM_TOKEN_METADATA_KEY);
        if (tokenStr == null) {
            call.close(Status.UNAUTHENTICATED.withDescription("missing x-pm-token header"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        // NOTE: This decodes the JWT without verifying its signature.
        // Add signature verification (e.g. JWT.require(...).build().verify(tokenStr))
        // once the key/JWKS source is available.
        DecodedJWT jwt;
        try {
            jwt = JWT.decode(tokenStr);
        } catch (Exception e) {
            logger.error("failed to decode JWT", e);
            call.close(Status.UNAUTHENTICATED.withDescription("invalid JWT: " + e.getMessage()), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        List<Map<String, Object>> actors;
        try {
            actors = extractActors(jwt);
        } catch (UnauthenticatedException e) {
            logger.error("JWT actor validation failed: {}", e.getMessage());
            call.close(Status.UNAUTHENTICATED.withDescription(e.getMessage()), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        Context context = Context.current().withValue(PM_JWT_ACTORS_CONTEXT_KEY, actors);

        String process = headers.get(UserContextInterceptor.PM_PROCESS_METADATA_KEY);
        if (process != null) {
            context = context.withValue(UserContextInterceptor.PM_PROCESS_CONTEXT_KEY, process);
        }

        logger.debug("jwt auth: {} actor(s) extracted, process={}", actors.size(), process);

        return Contexts.interceptCall(context, call, headers, next);
    }

    /**
     * Walks the JWT's {@code act} claim chain and returns one claims map per actor,
     * starting with the outermost (primary) token.
     */
    private List<Map<String, Object>> extractActors(DecodedJWT jwt) throws UnauthenticatedException {
        List<Map<String, Object>> actors = new ArrayList<>();

        actors.add(extractRelevantClaims(jwt));

        // Walk the act chain (RFC 8693)
        Claim actClaim = jwt.getClaim("act");
        Map<String, Object> currentAct = actClaim.isNull() ? null : actClaim.asMap();
        while (currentAct != null) {
            actors.add(extractRelevantClaimsFromMap(currentAct));
            Object nestedAct = currentAct.get("act");
            if (nestedAct instanceof Map<?, ?> nestedMap) {
                //noinspection unchecked
                currentAct = (Map<String, Object>) nestedMap;
            } else {
                currentAct = null;
            }
        }

        return actors;
    }

    private Map<String, Object> extractRelevantClaims(DecodedJWT jwt) throws UnauthenticatedException {
        Map<String, Object> result = new HashMap<>();

        if (usernameClaim != null) {
            Claim c = jwt.getClaim(usernameClaim);
            if (!c.isNull()) {
                String val = c.asString();
                if (val != null) {
                    result.put(usernameClaim, val);
                }
            }
        }

        if (userAttrsClaim != null) {
            Claim c = jwt.getClaim(userAttrsClaim);
            if (!c.isNull()) {
                String strVal = c.asString();
                if (strVal != null) {
                    result.put(userAttrsClaim, strVal);
                } else {
                    List<String> listVal = c.asList(String.class);
                    if (listVal != null) {
                        result.put(userAttrsClaim, listVal);
                    }
                }
            }
        }

        validateActorClaims(result);
        return result;
    }

    private Map<String, Object> extractRelevantClaimsFromMap(Map<String, Object> claimMap)
            throws UnauthenticatedException {
        Map<String, Object> result = new HashMap<>();

        if (usernameClaim != null) {
            Object val = claimMap.get(usernameClaim);
            if (val instanceof String) {
                result.put(usernameClaim, val);
            }
        }

        if (userAttrsClaim != null) {
            Object val = claimMap.get(userAttrsClaim);
            if (val instanceof String) {
                result.put(userAttrsClaim, val);
            } else if (val instanceof List<?> list) {
                List<String> strList = new ArrayList<>();
                for (Object item : list) {
                    strList.add(String.valueOf(item));
                }
                result.put(userAttrsClaim, strList);
            }
        }

        validateActorClaims(result);
        return result;
    }

    private void validateActorClaims(Map<String, Object> actorClaims) throws UnauthenticatedException {
        if (usernameClaim != null && actorClaims.containsKey(usernameClaim)) {
            return;
        }
        if (userAttrsClaim != null && actorClaims.containsKey(userAttrsClaim)) {
            return;
        }
        throw new UnauthenticatedException(
                "JWT actor is missing required claim(s): username-claim=" + usernameClaim
                        + ", user-attrs-claim=" + userAttrsClaim);
    }

    static class UnauthenticatedException extends Exception {
        UnauthenticatedException(String message) {
            super(message);
        }
    }

}
