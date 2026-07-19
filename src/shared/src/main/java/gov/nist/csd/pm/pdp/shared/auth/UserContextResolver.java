package gov.nist.csd.pm.pdp.shared.auth;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;

public interface UserContextResolver {

    UserContext resolve(PAP pap) throws PMException;
}
