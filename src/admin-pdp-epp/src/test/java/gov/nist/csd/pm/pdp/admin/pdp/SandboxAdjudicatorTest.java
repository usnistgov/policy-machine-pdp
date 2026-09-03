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
 * licensed to NIST.
 *
 * This file is part of the admin-pdp-epp module, which compiles against
 * and embeds org.neo4j:neo4j (GPLv3, Community Edition). As a combined work, this
 * module is distributed under the GNU General Public License v3.0, not the CC BY 4.0
 * license used elsewhere in this repository. See admin-pdp-epp/LICENSE for the full text.
 */

package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.ngac.pm.core.pap.query.model.context.NodeUserContext;
import gov.nist.ngac.pm.core.pdp.bootstrap.PMLBootstrapperWithSuper;
import gov.nist.csd.pm.pdp.shared.auth.UserContextResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxAdjudicatorTest {

    /**
     * Bootstraps a super user, then a policy class with an unprivileged user "u1" that holds no
     * associations and therefore no access rights. In default mode "u1" could not run any admin
     * operation; the sandbox adjudicator skips the privilege gate so it can.
     */
    private static final String BASE_PML =
            "create pc \"pc1\"\n" +
            "create ua \"ua1\" in [\"pc1\"]\n" +
            "create u \"u1\" in [\"ua1\"]\n";

    private SandboxAdjudicator newAdjudicator(String pml) throws PMException {
        MemoryPAP pap = new MemoryPAP();
        pap.bootstrap(new PMLBootstrapperWithSuper(pml));

        // Resolve every request to the unprivileged user "u1".
        UserContextResolver resolver = p -> NodeUserContext.of("u1");

        return new SandboxAdjudicator(pap, resolver);
    }

    @Test
    void executePML_unprivilegedUser_skipsPrivilegeGate() throws PMException {
        SandboxAdjudicator adjudicator = newAdjudicator(BASE_PML);

        adjudicator.executePML("create pc \"pc2\"");

        boolean exists = adjudicator.adjudicateQuery((pap, userCtx) -> pap.query().graph().nodeExists("pc2"));
        assertTrue(exists, "unprivileged user should be able to create a policy class in sandbox mode");
    }

    @Test
    void adjudicateOperation_unprivilegedUser_skipsPrivilegeGate() throws PMException {
        SandboxAdjudicator adjudicator = newAdjudicator(BASE_PML);

        adjudicator.adjudicateOperation("create_policy_class", Map.of("name", "pc2"));

        boolean exists = adjudicator.adjudicateQuery((pap, userCtx) -> pap.query().graph().nodeExists("pc2"));
        assertTrue(exists, "unprivileged user should be able to adjudicate an admin operation in sandbox mode");
    }
}
