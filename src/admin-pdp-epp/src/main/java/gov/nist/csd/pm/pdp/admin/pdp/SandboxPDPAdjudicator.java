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

import gov.nist.csd.pm.pdp.shared.auth.UserContextResolver;
import gov.nist.csd.pm.pdp.shared.config.SandboxPDPMode;
import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.epp.EPP;
import gov.nist.ngac.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.ngac.pm.core.pdp.PDP;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Sandbox that enforces NGAC privileges against the in-memory policy, like default mode but without
 * Neo4j or EventStoreDB.
 */
@Component
@SandboxPDPMode
public class SandboxPDPAdjudicator extends InMemoryAdjudicator {

    @Autowired
    public SandboxPDPAdjudicator(MemoryPAP pap, UserContextResolver userContextResolver) throws PMException {
        this(pap, userContextResolver, new PDP(pap));
    }

    private SandboxPDPAdjudicator(MemoryPAP pap, UserContextResolver userContextResolver, PDP pdp)
            throws PMException {
        super(pap, userContextResolver, pdp, wireEpp(pap, pdp));
    }

    private static EPP wireEpp(MemoryPAP pap, PDP pdp) throws PMException {
        EPP epp = new EPP(pdp, pap);
        epp.subscribeTo(pdp);
        return epp;
    }
}
