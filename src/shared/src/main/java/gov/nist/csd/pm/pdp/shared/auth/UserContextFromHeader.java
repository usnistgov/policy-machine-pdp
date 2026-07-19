package gov.nist.csd.pm.pdp.shared.auth;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.model.context.*;
import gov.nist.csd.pm.proto.v1.pdp.query.ConjunctiveUserContext;

import java.util.*;

public class UserContextFromHeader {

    public static UserContext get(PAP pap) throws PMException {
        String process = UserContextInterceptor.getPmProcessHeaderValue();

        if (process != null && process.isEmpty()) {
            process = null;
        }

        String user = UserContextInterceptor.getPmUserHeaderValue();
        List<String> attrs = UserContextInterceptor.getPmUserAttrsHeaderValue();

        if (user == null && attrs == null) {
            throw new IllegalArgumentException("user and attrs cannot both be null in request header");
        }

        if (user != null) {
            return NodeUserContext.of(user, process);
        }

        Set<Long> attrIds = new HashSet<>();
        for (String attr : attrs) {
            attrIds.add(pap.query().graph().getNodeId(attr));
        }

        return AnonymousUserContext.ofIds(attrIds, process);
    }
}
