package gov.nist.csd.pm.pdp.shared.auth;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.model.context.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@ConditionalOnProperty(name = "pm.pdp.auth-mode", havingValue = "jwt")
public class JwtUserContextResolver implements UserContextResolver {

    private final String usernameClaim;
    private final String userAttrsClaim;

    public JwtUserContextResolver(AuthConfig authConfig) {
        this.usernameClaim = authConfig.getUsernameClaim();
        this.userAttrsClaim = authConfig.getUserAttrsClaim();
    }

    @Override
    public UserContext resolve(PAP pap) throws PMException {
        List<Map<String, Object>> actors = JwtUserContextInterceptor.PM_JWT_ACTORS_CONTEXT_KEY.get();
        if (actors == null || actors.isEmpty()) {
            throw new IllegalStateException("no JWT actor context found");
        }

        String process = UserContextInterceptor.getPmProcessHeaderValue();
        if (process != null && process.isEmpty()) {
            process = null;
        }

        if (actors.size() == 1) {
            return buildUserContext(actors.getFirst(), process);
        }

        List<UserContext> contexts = new ArrayList<>();
        for (Map<String, Object> actor : actors) {
            contexts.add(buildUserContext(actor, process));
        }
        return new ConjunctiveUserContext(contexts);
    }

    private UserContext buildUserContext(Map<String, Object> actorClaims, String process) {
        // usernameClaim takes priority: its presence always means a named user
        if (usernameClaim != null) {
            Object val = actorClaims.get(usernameClaim);
            if (val instanceof String username) {
                return new NameUserContext(username, process);
            }
        }

        // Fall back to userAttrsClaim: value may be a single string or a list
        if (userAttrsClaim != null) {
            Object val = actorClaims.get(userAttrsClaim);
            if (val instanceof String single) {
                return new AttributeNamesUserContext(Set.of(single), process);
            } else if (val instanceof List<?> attrNames) {
                Set<String> attrIds = new HashSet<>();
                for (Object attr : attrNames) {
                    attrIds.add(String.valueOf(attr));
                }
                return new AttributeNamesUserContext(attrIds, process);
            }
        }

        throw new IllegalStateException(
                "actor claims contain neither username-claim (" + usernameClaim
                        + ") nor user-attrs-claim (" + userAttrsClaim + ")");
    }
}
