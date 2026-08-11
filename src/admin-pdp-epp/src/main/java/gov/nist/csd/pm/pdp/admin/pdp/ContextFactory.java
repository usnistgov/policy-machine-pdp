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