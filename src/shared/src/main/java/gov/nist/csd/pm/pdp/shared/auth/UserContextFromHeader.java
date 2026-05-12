package gov.nist.csd.pm.pdp.shared.auth;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.model.context.*;

import java.util.*;

public class UserContextFromHeader {

    public static UserContext get(PAP pap) throws PMException {
        String chain = UserContextInterceptor.getPmUserChainHeaderValue();
        String process = UserContextInterceptor.getPmProcessHeaderValue();

        if (process != null && process.isEmpty()) {
            process = null;
        }

        if (chain != null) {
            return buildFromChain(chain, process);
        }

        String user = UserContextInterceptor.getPmUserHeaderValue();
        List<String> attrs = UserContextInterceptor.getPmUserAttrsHeaderValue();

        if (user == null && attrs == null) {
            throw new IllegalArgumentException("user and attrs cannot both be null in request header");
        }

        if (user != null) {
            return new NameUserContext(user, process);
        }

        Set<Long> attrIds = new HashSet<>();
        for (String attr : attrs) {
            attrIds.add(pap.query().graph().getNodeId(attr));
        }

        return new AttributeIdsUserContext(attrIds, process);
    }

    /**
     * Parses x-pm-user-chain into a ConjunctiveUserContext
     *
     * Format: semicolon-separated entries where each entry is either a single username
     * or a comma-separated list of user-attribute names.
     * Example: bob;[a,b,c];alice → 3 contexts: user bob, attrs [a,b,c], user alice.
     */
    private static UserContext buildFromChain(String chain, String process) {
        String[] entries = chain.split(";");
        List<UserContext> contexts = new ArrayList<>();

        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.startsWith("[")) {
                Set<String> attrNames = Set.of(trimmed.split(","));
                contexts.add(new AttributeNamesUserContext(attrNames, process));
            } else {
                contexts.add(new NameUserContext(trimmed, process));
            }
        }

        if (contexts.isEmpty()) {
            throw new IllegalArgumentException("x-pm-user-chain header is empty or contains no valid entries");
        }

        if (contexts.size() == 1) {
            return contexts.getFirst();
        }

        return new ConjunctiveUserContext(contexts);
    }
}
