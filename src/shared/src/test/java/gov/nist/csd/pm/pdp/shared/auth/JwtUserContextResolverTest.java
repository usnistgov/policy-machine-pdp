package gov.nist.csd.pm.pdp.shared.auth;

import gov.nist.ngac.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.ngac.pm.core.pap.query.model.context.AnonymousUserContext;
import gov.nist.ngac.pm.core.pap.query.model.context.NodeUserContext;
import gov.nist.ngac.pm.core.pap.query.model.context.UserContext;
import gov.nist.ngac.pm.core.pdp.bootstrap.PMLBootstrapperWithSuper;
import io.grpc.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtUserContextResolverTest {

    private JwtUserContextResolver resolver;

    @BeforeEach
    void setUp() {
        AuthConfig config = new AuthConfig();
        config.setMode("jwt");
        config.setUsernameClaim("username");
        config.setUserAttrsClaim("user_attrs");
        resolver = new JwtUserContextResolver(config);
    }

    @Test
    void usernameClaim_buildsNodeUserContext() throws Exception {
        Map<String, Object> claims = Map.of("username", "alice");

        UserContext ctx = resolveWithContext(claims, "proc-1", new MemoryPAP());

        assertInstanceOf(NodeUserContext.class, ctx);
        NodeUserContext nodeCtx = (NodeUserContext) ctx;
        assertEquals("alice", nodeCtx.getName());
        assertEquals("proc-1", nodeCtx.getProcess());
    }

    @Test
    void attrsClaim_buildsAnonymousUserContextFromResolvedIds() throws Exception {
        MemoryPAP pap = new MemoryPAP();
        pap.bootstrap(new PMLBootstrapperWithSuper(
                "create pc \"pc1\"\n" +
                "create ua \"ua1\" in [\"pc1\"]\n" +
                "create ua \"ua2\" in [\"pc1\"]\n"));
        long ua1 = pap.query().graph().getNodeId("ua1");
        long ua2 = pap.query().graph().getNodeId("ua2");

        Map<String, Object> claims = Map.of("user_attrs", List.of("ua1", "ua2"));

        UserContext ctx = resolveWithContext(claims, null, pap);

        assertInstanceOf(AnonymousUserContext.class, ctx);
        assertEquals(Set.of(ua1, ua2), ((AnonymousUserContext) ctx).getAttributeIds());
    }

    @Test
    void missingClaims_throws() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(new MemoryPAP()));
    }

    private UserContext resolveWithContext(Map<String, Object> claims, String process, MemoryPAP pap)
            throws Exception {
        Context context = Context.current()
                .withValue(JwtUserContextInterceptor.PM_JWT_CLAIMS_CONTEXT_KEY, claims);
        if (process != null) {
            context = context.withValue(UserContextInterceptor.PM_PROCESS_CONTEXT_KEY, process);
        }
        return context.call(() -> resolver.resolve(pap));
    }
}
