package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.core.pap.query.model.context.NodeUserContext;
import gov.nist.csd.pm.core.pdp.bootstrap.PMLBootstrapperWithSuper;
import gov.nist.csd.pm.pdp.shared.auth.UserContextResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaygroundAdjudicatorTest {

    /**
     * Bootstraps a super user, then a policy class with an unprivileged user "u1" that holds no
     * associations and therefore no access rights. In default mode "u1" could not run any admin
     * operation; the playground adjudicator skips the privilege gate so it can.
     */
    private static final String BASE_PML =
            "create pc \"pc1\"\n" +
            "create ua \"ua1\" in [\"pc1\"]\n" +
            "create u \"u1\" in [\"ua1\"]\n";

    private PlaygroundAdjudicator newAdjudicator(String pml) throws PMException {
        MemoryPAP pap = new MemoryPAP();
        pap.bootstrap(new PMLBootstrapperWithSuper(pml));

        // Resolve every request to the unprivileged user "u1".
        UserContextResolver resolver = p -> NodeUserContext.of("u1");

        return new PlaygroundAdjudicator(pap, resolver);
    }

    @Test
    void executePML_unprivilegedUser_skipsPrivilegeGate() throws PMException {
        PlaygroundAdjudicator adjudicator = newAdjudicator(BASE_PML);

        adjudicator.executePML("create pc \"pc2\"");

        boolean exists = adjudicator.adjudicateQuery((pap, userCtx) -> pap.query().graph().nodeExists("pc2"));
        assertTrue(exists, "unprivileged user should be able to create a policy class in playground mode");
    }

    @Test
    void adjudicateOperation_unprivilegedUser_skipsPrivilegeGate() throws PMException {
        PlaygroundAdjudicator adjudicator = newAdjudicator(BASE_PML);

        adjudicator.adjudicateOperation("create_policy_class", Map.of("name", "pc2"));

        boolean exists = adjudicator.adjudicateQuery((pap, userCtx) -> pap.query().graph().nodeExists("pc2"));
        assertTrue(exists, "unprivileged user should be able to adjudicate an admin operation in playground mode");
    }
}
