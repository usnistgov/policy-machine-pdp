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
