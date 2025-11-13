package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.function.PluginRegistry;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.admin.pap.EventTrackingPAP;
import gov.nist.csd.pm.pdp.admin.pap.NoCommitNeo4jPolicyStore;

import gov.nist.csd.pm.pdp.shared.auth.UserContextFromHeader;
import org.neo4j.graphdb.GraphDatabaseService;
import org.springframework.stereotype.Component;

/**
 * Factory for creating NGACContext instances.
 */
@Component
public class ContextFactory {

    private final GraphDatabaseService graphDb;
    private final PluginRegistry pluginRegistry;

    public ContextFactory(GraphDatabaseService graphDb, PluginRegistry pluginRegistry) {
        this.graphDb = graphDb;
        this.pluginRegistry = pluginRegistry;
    }

    /**
     * Creates a new NGACContext.
     *
     * @return The created context
     * @throws PMException If an error occurs during context creation
     */
    public NGACContext createContext() throws PMException {
        NoCommitNeo4jPolicyStore noCommitNeo4jPolicyStore = new NoCommitNeo4jPolicyStore(graphDb, getClass().getClassLoader());
        EventTrackingPAP pap = new EventTrackingPAP(noCommitNeo4jPolicyStore, pluginRegistry);
        PDP pdp = new PDP(pap);

        return new NGACContext(pdp, pap);
    }

    public UserContext createUserContext(PAP pap) throws PMException {
        return UserContextFromHeader.get(pap);
    }
}