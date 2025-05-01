package gov.nist.csd.pm.server.shared.auth;

import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.query.model.context.UserContext;

public class UserContextFromHeader extends UserContext {

    public UserContextFromHeader(PAP pap) throws PMException {
        super(
            pap.query().graph().getNodeByName(UserContextInterceptor.getPmUserHeaderValue()).getId(),
            UserContextInterceptor.getPmProcessHeaderValue()
        );
    }

}
