package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.function.AdminFunction;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.admin.pap.EventTrackingPAP;
import gov.nist.csd.pm.pdp.admin.pap.NoCommitNeo4jPolicyStore;

import java.util.List;

import gov.nist.csd.pm.pdp.shared.auth.UserContextFromHeader;
import org.springframework.stereotype.Component;

/**
 * Factory for creating NGACContext instances.
 */
@Component
public class ContextFactory {
    private final NoCommitNeo4jPolicyStore noCommitNeo4jPolicyStore;
    private final List<AdminFunction<?, ?>> loadedAdminFunctionPlugins;

    /**
     * Creates a new ContextFactory.
     *
     * @param noCommitNeo4jPolicyStore The policy store
     * @param loadedAdminFunctionPlugins The loaded admin function plugins
     */
    public ContextFactory(NoCommitNeo4jPolicyStore noCommitNeo4jPolicyStore,
                          List<AdminFunction<?, ?>> loadedAdminFunctionPlugins) {
        this.noCommitNeo4jPolicyStore = noCommitNeo4jPolicyStore;
        this.loadedAdminFunctionPlugins = loadedAdminFunctionPlugins;
    }

    /**
     * Creates a new NGACContext.
     *
     * @return The created context
     * @throws PMException If an error occurs during context creation
     */
    public NGACContext createContext() throws PMException {
        EventTrackingPAP pap = new EventTrackingPAP(noCommitNeo4jPolicyStore, loadedAdminFunctionPlugins);
        UserContext userCtx = UserContextFromHeader.get(pap);
        PDP pdp = new PDP(pap);

        return new NGACContext(userCtx, pdp, pap);
    }
}