package gov.nist.csd.pm.server.shared.auth;

import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.query.model.context.UserContext;

public class UserContextFromHeader extends UserContext {

    public UserContextFromHeader() throws PMException {
        super(validateUser(), validateProcess());
    }

    private static String validateProcess() {
        String process = UserContextInterceptor.getPmProcessHeaderValue();
        return process == null || process.isEmpty() ? null : process;
    }

    private static long validateUser() throws PMException {
        long userId = UserContextInterceptor.getPmUserHeaderValue();
        if (userId == 0) {
            throw new PMException("user id in request header is null or empty");
        }

        return userId;
    }

}
