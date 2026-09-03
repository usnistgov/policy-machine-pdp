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
import gov.nist.ngac.pm.core.epp.EPP;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.operation.Operation;
import gov.nist.ngac.pm.core.pap.query.model.context.UserContext;
import gov.nist.ngac.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.admin.pap.EventTrackingPAP;
import gov.nist.csd.pm.pdp.admin.pap.NoCommitNeo4jPolicyStore;
import gov.nist.csd.pm.pdp.shared.auth.UserContextResolver;
import org.neo4j.graphdb.GraphDatabaseService;
import gov.nist.csd.pm.pdp.shared.config.DefaultMode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Factory for creating NGACContext instances.
 */
@Component
@DefaultMode
public class ContextFactory {

    private final GraphDatabaseService graphDb;
    private final List<Operation<?>> plugins;
    private final UserContextResolver userContextResolver;

    public ContextFactory(GraphDatabaseService graphDb, List<Operation<?>> plugins,
                          UserContextResolver userContextResolver) {
        this.graphDb = graphDb;
        this.plugins = plugins;
        this.userContextResolver = userContextResolver;
    }

    /**
     * Creates a new NGACContext.
     *
     * @return The created context.
     * @throws PMException If an error occurs during context creation
     */
    public NGACContext createContext() throws PMException {
        NoCommitNeo4jPolicyStore noCommitNeo4jPolicyStore = new NoCommitNeo4jPolicyStore(graphDb);
        EventTrackingPAP pap = new EventTrackingPAP(noCommitNeo4jPolicyStore, plugins);
        PDP pdp = new PDP(pap);

        // set up EPP to process events in the PDP
        EPP epp = new EPP(pdp, pap);
        epp.subscribeTo(pdp);

        return new NGACContext(pdp, epp, pap);
    }

    public UserContext createUserContext(PAP pap) throws PMException {
        return userContextResolver.resolve(pap);
    }
}