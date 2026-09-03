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
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtUserContextInterceptorTest {

    private static final String ISSUER = "https://issuer.example.com";

    private Algorithm signingAlgorithm;
    private JwtUserContextInterceptor interceptor;

    @BeforeEach
    void setUp() throws Exception {
        KeyPair kp = generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) kp.getPublic();
        signingAlgorithm = Algorithm.RSA256(publicKey, (RSAPrivateKey) kp.getPrivate());

        Jwk jwk = mock(Jwk.class);
        when(jwk.getPublicKey()).thenReturn(publicKey);
        JwkProvider jwkProvider = mock(JwkProvider.class);
        when(jwkProvider.get(anyString())).thenReturn(jwk);

        AuthConfig config = new AuthConfig();
        config.setMode("jwt");
        config.setUsernameClaim("username");
        config.setUserAttrsClaim("user_attrs");
        config.setIssuer(ISSUER);

        interceptor = new JwtUserContextInterceptor(config, jwkProvider);
    }

    @Test
    void validToken_invokesHandlerWithClaims() {
        String token = JWT.create()
                .withKeyId("test-key")
                .withIssuer(ISSUER)
                .withClaim("username", "alice")
                .withExpiresAt(future())
                .sign(signingAlgorithm);

        boolean[] handlerInvoked = {false};
        ServerCallHandler<String, String> handler = (call1, headers1) -> {
            handlerInvoked[0] = true;
            Map<String, Object> claims = JwtUserContextInterceptor.PM_JWT_CLAIMS_CONTEXT_KEY.get();
            assertNotNull(claims);
            assertEquals("alice", claims.get("username"));
            return new ServerCall.Listener<>() {};
        };

        NoopServerCall<String, String> call = new NoopServerCall<>();
        interceptor.interceptCall(call, tokenHeaders(token), handler);

        assertTrue(handlerInvoked[0], "valid token should reach the downstream handler");
        assertNull(call.closedStatus);
    }

    @Test
    void missingToken_closesUnauthenticated() {
        NoopServerCall<String, String> call = new NoopServerCall<>();
        interceptor.interceptCall(call, new Metadata(), rejectingHandler());

        assertNotNull(call.closedStatus);
        assertEquals(Status.Code.UNAUTHENTICATED, call.closedStatus.getCode());
    }

    @Test
    void badSignature_closesUnauthenticated() throws Exception {
        KeyPair attacker = generateKeyPair();
        Algorithm attackerAlg = Algorithm.RSA256(
                (RSAPublicKey) attacker.getPublic(), (RSAPrivateKey) attacker.getPrivate());

        String token = JWT.create()
                .withKeyId("test-key")
                .withIssuer(ISSUER)
                .withClaim("username", "alice")
                .withExpiresAt(future())
                .sign(attackerAlg);

        NoopServerCall<String, String> call = new NoopServerCall<>();
        interceptor.interceptCall(call, tokenHeaders(token), rejectingHandler());

        assertNotNull(call.closedStatus);
        assertEquals(Status.Code.UNAUTHENTICATED, call.closedStatus.getCode());
    }

    @Test
    void expiredToken_closesUnauthenticated() {
        String token = JWT.create()
                .withKeyId("test-key")
                .withIssuer(ISSUER)
                .withClaim("username", "alice")
                .withExpiresAt(new Date(System.currentTimeMillis() - 60_000))
                .sign(signingAlgorithm);

        NoopServerCall<String, String> call = new NoopServerCall<>();
        interceptor.interceptCall(call, tokenHeaders(token), rejectingHandler());

        assertNotNull(call.closedStatus);
        assertEquals(Status.Code.UNAUTHENTICATED, call.closedStatus.getCode());
    }

    @Test
    void wrongIssuer_closesUnauthenticated() {
        String token = JWT.create()
                .withKeyId("test-key")
                .withIssuer("https://evil.example.com")
                .withClaim("username", "alice")
                .withExpiresAt(future())
                .sign(signingAlgorithm);

        NoopServerCall<String, String> call = new NoopServerCall<>();
        interceptor.interceptCall(call, tokenHeaders(token), rejectingHandler());

        assertNotNull(call.closedStatus);
        assertEquals(Status.Code.UNAUTHENTICATED, call.closedStatus.getCode());
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    private static Date future() {
        return new Date(System.currentTimeMillis() + 60_000);
    }

    private static Metadata tokenHeaders(String token) {
        Metadata headers = new Metadata();
        headers.put(JwtUserContextInterceptor.PM_TOKEN_METADATA_KEY, token);
        return headers;
    }

    private static ServerCallHandler<String, String> rejectingHandler() {
        return (call, headers) -> {
            fail("downstream handler must not be invoked for an invalid token");
            return new ServerCall.Listener<>() {};
        };
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
