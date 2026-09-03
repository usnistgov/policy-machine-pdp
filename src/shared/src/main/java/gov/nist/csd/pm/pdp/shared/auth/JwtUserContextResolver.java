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
 * licensed to NIST. To the extent that NIST holds copyright in this software, it is
 * being made available under the Creative Commons Attribution 4.0 International
 * license (CC BY 4.0). The disclaimers of the CC BY 4.0 license apply to all parts
 * of the software developed or licensed by NIST.
 *
 * ACCESS THE FULL CC BY 4.0 LICENSE HERE:
 * https://creativecommons.org/licenses/by/4.0/legalcode
 */

package gov.nist.csd.pm.pdp.shared.auth;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.query.model.context.AnonymousUserContext;
import gov.nist.ngac.pm.core.pap.query.model.context.NodeUserContext;
import gov.nist.ngac.pm.core.pap.query.model.context.UserContext;
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
