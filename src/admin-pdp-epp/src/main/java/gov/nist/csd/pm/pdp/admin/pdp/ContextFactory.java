package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.epp.EPP;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.operation.Operation;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.admin.pap.EventTrackingPAP;
import gov.nist.csd.pm.pdp.admin.pap.NoCommitNeo4jPolicyStore;
import gov.nist.csd.pm.pdp.shared.auth.UserContextFromHeader;
import org.neo4j.graphdb.GraphDatabaseService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Factory for creating NGACContext instances.
 */
@Component
public class ContextFactory {

    private final GraphDatabaseService graphDb;
    private final List<Operation<?>> plugins;

    public ContextFactory(GraphDatabaseService graphDb, List<Operation<?>> plugins) {
        this.graphDb = graphDb;
        this.plugins = plugins;
    }

    /**
     * Creates a new NGACContext.
     *
     * @return The created context.
     * @throws PMException If an error occurs during context creation
     */
    public NGACContext createContext() throws PMException {
        NoCommitNeo4jPolicyStore noCommitNeo4jPolicyStore = new NoCommitNeo4jPolicyStore(graphDb, getClass().getClassLoader());
        EventTrackingPAP pap = new EventTrackingPAP(noCommitNeo4jPolicyStore, plugins);
        PDP pdp = new PDP(pap);

        // set up EPP to process events in the PDP
        EPP epp = new EPP(pdp, pap);
        epp.subscribeTo(pdp);

        return new NGACContext(pdp, epp, pap);
    }

    public UserContext createUserContext(PAP pap) throws PMException {
        return UserContextFromHeader.get(pap);
    }
}