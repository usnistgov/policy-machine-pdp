package gov.nist.csd.pm.pdp.shared.auth;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.model.context.AnonymousUserContext;
import gov.nist.csd.pm.core.pap.query.model.context.NodeUserContext;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the PDP {@link UserContext} from the JWT claims that {@link JwtUserContextInterceptor}
 * verified and stored in the gRPC context. Active only when {@code pm.pdp.auth.mode=jwt}; the
 * {@code none} mode uses {@link BasicUserContextResolver}.
 *
 * <p>One request maps to exactly one user. If the token carries the configured username claim it
 * becomes a {@link NodeUserContext}; otherwise the configured user-attribute claim values become an
 * attribute-only {@link AnonymousUserContext}. This mirrors how {@link UserContextFromHeader} maps
 * the {@code x-pm-user} / {@code x-pm-user-attrs} headers, but the identity here comes from the
 * cryptographically verified token rather than a client-supplied header.
 */
@Component
@ConditionalOnProperty(name = "pm.pdp.auth.mode", havingValue = "jwt")
public class JwtUserContextResolver implements UserContextResolver {

    private final String usernameClaim;
    private final String userAttrsClaim;

    public JwtUserContextResolver(AuthConfig authConfig) {
        this.usernameClaim = authConfig.getUsernameClaim();
        this.userAttrsClaim = authConfig.getUserAttrsClaim();
    }

    @Override
    public UserContext resolve(PAP pap) throws PMException {
        Map<String, Object> claims = JwtUserContextInterceptor.PM_JWT_CLAIMS_CONTEXT_KEY.get();
        if (claims == null || claims.isEmpty()) {
            throw new IllegalArgumentException("no verified JWT claims available to resolve user context");
        }

        String process = UserContextInterceptor.getPmProcessHeaderValue();
        if (process != null && process.isEmpty()) {
            process = null;
        }

        String user = asString(claims.get(usernameClaim));
        if (user != null) {
            return NodeUserContext.of(user, process);
        }

        List<String> attrs = asStringList(claims.get(userAttrsClaim));
        if (attrs == null || attrs.isEmpty()) {
            throw new IllegalArgumentException(
                    "JWT is missing username-claim '" + usernameClaim
                            + "' and user-attrs-claim '" + userAttrsClaim + "'");
        }

        Set<Long> attrIds = new HashSet<>();
        for (String attr : attrs) {
            attrIds.add(pap.query().graph().getNodeId(attr));
        }

        return AnonymousUserContext.ofIds(attrIds, process);
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    private static List<String> asStringList(Object value) {
        if (value instanceof String s) {
            return List.of(s);
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return null;
    }
}
