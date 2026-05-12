package gov.nist.csd.pm.pdp.shared.auth;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pm.pdp.auth-mode", havingValue = "none", matchIfMissing = true)
public class BasicUserContextResolver implements UserContextResolver {

    @Override
    public UserContext resolve(PAP pap) throws PMException {
        return UserContextFromHeader.get(pap);
    }
}
