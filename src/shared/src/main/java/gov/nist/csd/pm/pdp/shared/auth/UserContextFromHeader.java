package gov.nist.csd.pm.pdp.shared.auth;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;

import java.util.ArrayList;
import java.util.List;

public class UserContextFromHeader {

    public static UserContext get(PAP pap) throws PMException {
        String user = UserContextInterceptor.getPmUserHeaderValue();
        String process = UserContextInterceptor.getPmProcessHeaderValue();
        List<String> attrs = UserContextInterceptor.getPmUserAttrsHeaderValue();

        if (user == null && attrs == null) {
            throw new IllegalArgumentException("user and attrs cannot both be null in request header");
        }

        if (process == null || process.isEmpty()) {
            process = null;
        }

        if (user != null) {
            return new UserContext(pap.query().graph().getNodeId(user), process);
        }

        List<Long> attrIds = new ArrayList<>();
        for (String attr : attrs) {
            attrIds.add(pap.query().graph().getNodeId(attr));
        }

        return new UserContext(attrIds, process);
    }
}
