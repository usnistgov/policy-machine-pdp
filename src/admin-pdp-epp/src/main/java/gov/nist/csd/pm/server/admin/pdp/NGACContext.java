package gov.nist.csd.pm.server.admin.pdp;

import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pdp.PDP;

public record NGACContext(UserContext userCtx, PDP pdp, PAP pap) {
}
