package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.admin.pap.EventTrackingPAP;
import gov.nist.csd.pm.pdp.admin.pap.NoCommitNeo4jPolicyStore;

import gov.nist.csd.pm.pdp.shared.auth.UserContextFromHeader;
import gov.nist.csd.pm.pdp.shared.plugin.PluginLoader;
import org.neo4j.graphdb.GraphDatabaseService;
import org.springframework.stereotype.Component;

/**
 * Factory for creating NGACContext instances.
 */
@Component
public class ContextFactory {

    private final PluginLoader pluginLoader;
    private final GraphDatabaseService graphDb;

    public ContextFactory(GraphDatabaseService graphDb, PluginLoader pluginLoader) {
        this.graphDb = graphDb;
        this.pluginLoader = pluginLoader;
    }

    /**
     * Creates a new NGACContext.
     *
     * @return The created context
     * @throws PMException If an error occurs during context creation
     */
    public NGACContext createContext() throws PMException {
        NoCommitNeo4jPolicyStore noCommitNeo4jPolicyStore = new NoCommitNeo4jPolicyStore(graphDb, pluginLoader.getPluginClassLoader());
        EventTrackingPAP pap = new EventTrackingPAP(noCommitNeo4jPolicyStore, pluginLoader);
        PDP pdp = new PDP(pap);

        return new NGACContext(pdp, pap);
    }

    public UserContext createUserContext(PAP pap) throws PMException {
        return UserContextFromHeader.get(pap);
    }
}