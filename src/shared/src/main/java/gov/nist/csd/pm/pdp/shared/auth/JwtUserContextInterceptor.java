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

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Verification;
import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@GrpcGlobalServerInterceptor
@ConditionalOnProperty(name = "pm.pdp.auth.mode", havingValue = "jwt")
public class JwtUserContextInterceptor implements ServerInterceptor {

    public static final String PM_TOKEN_KEY = "x-pm-token";

    public static final Metadata.Key<String> PM_TOKEN_METADATA_KEY =
            Metadata.Key.of(PM_TOKEN_KEY, Metadata.ASCII_STRING_MARSHALLER);

    // Holds the verified user's claims for this request: the extracted username-claim
    // and/or user-attrs-claim values. One request maps to exactly one user.
    public static final Context.Key<Map<String, Object>> PM_JWT_CLAIMS_CONTEXT_KEY =
            Context.key("x-pm-jwt-claims");

    private static final Logger logger = LoggerFactory.getLogger(JwtUserContextInterceptor.class);

    private final String usernameClaim;
    private final String userAttrsClaim;
    private final String issuer;
    private final String audience;
    private final JwkProvider jwkProvider;

    public JwtUserContextInterceptor(AuthConfig authConfig) {
        this(authConfig, buildJwkProvider(authConfig));
    }

    // Visible for testing: allows injecting a JwkProvider backed by a known key pair.
    JwtUserContextInterceptor(AuthConfig authConfig, JwkProvider jwkProvider) {
        this.usernameClaim = authConfig.getUsernameClaim();
        this.userAttrsClaim = authConfig.getUserAttrsClaim();
        this.issuer = authConfig.getIssuer();
        this.audience = authConfig.getAudience();
        this.jwkProvider = jwkProvider;
    }

    private static JwkProvider buildJwkProvider(AuthConfig authConfig) {
        String jwksUri = authConfig.getJwksUri();
        if (jwksUri == null || jwksUri.isBlank()) {
            throw new IllegalStateException(
                    "pm.pdp.auth.jwks-uri must be configured when pm.pdp.auth.mode=jwt");
        }
        try {
            return new JwkProviderBuilder(new URL(jwksUri)).cached(true).rateLimited(true).build();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("invalid pm.pdp.auth.jwks-uri: " + jwksUri, e);
        }
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

        DecodedJWT jwt;
        try {
            jwt = verify(tokenStr);
        } catch (Exception e) {
            // Unauthenticated callers control this path, so keep it quiet (no stack trace) and
            // avoid echoing internal verification details back to the client.
            logger.warn("failed to verify JWT: {}", e.getMessage());
            call.close(Status.UNAUTHENTICATED.withDescription("invalid JWT"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        Map<String, Object> claims;
        try {
            claims = extractRelevantClaims(jwt);
        } catch (UnauthenticatedException e) {
            logger.warn("JWT claim validation failed: {}", e.getMessage());
            call.close(Status.UNAUTHENTICATED.withDescription(e.getMessage()), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        Context context = Context.current().withValue(PM_JWT_CLAIMS_CONTEXT_KEY, claims);

        String process = headers.get(UserContextInterceptor.PM_PROCESS_METADATA_KEY);
        if (process != null) {
            context = context.withValue(UserContextInterceptor.PM_PROCESS_CONTEXT_KEY, process);
        }

        logger.debug("jwt auth: user claims extracted, process={}", process);

        return Contexts.interceptCall(context, call, headers, next);
    }

    /**
     * Verifies the token's RSA signature against the JWKS-published key matching its {@code kid},
     * and enforces the configured issuer/audience when set. Returns the decoded, verified token.
     */
    private DecodedJWT verify(String tokenStr) throws JwkException {
        DecodedJWT decoded = JWT.decode(tokenStr);
        Jwk jwk = jwkProvider.get(decoded.getKeyId());
        Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);

        Verification verification = JWT.require(algorithm);
        if (issuer != null && !issuer.isBlank()) {
            verification.withIssuer(issuer);
        }
        if (audience != null && !audience.isBlank()) {
            verification.withAudience(audience);
        }

        return verification.build().verify(tokenStr);
    }

    /**
     * Extracts the configured username and/or user-attribute claims from the verified token.
     * One request maps to exactly one user; any delegation/impersonation is the client's concern
     * and must be resolved into a single effective identity before the token reaches the PDP.
     */
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

        validateClaims(result);
        return result;
    }

    private void validateClaims(Map<String, Object> claims) throws UnauthenticatedException {
        if (usernameClaim != null && claims.containsKey(usernameClaim)) {
            return;
        }
        if (userAttrsClaim != null && claims.containsKey(userAttrsClaim)) {
            return;
        }
        throw new UnauthenticatedException(
                "JWT is missing required claim(s): username-claim=" + usernameClaim
                        + ", user-attrs-claim=" + userAttrsClaim);
    }

    static class UnauthenticatedException extends Exception {
        UnauthenticatedException(String message) {
            super(message);
        }
    }

}
