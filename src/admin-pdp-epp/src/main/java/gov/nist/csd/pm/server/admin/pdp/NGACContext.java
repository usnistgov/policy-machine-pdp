package gov.nist.csd.pm.server.admin.pdp;

import gov.nist.csd.pm.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pdp.PDP;
import gov.nist.csd.pm.server.admin.pap.EventTrackingPAP;

public record NGACContext(UserContext userCtx, PDP pdp, EventTrackingPAP pap) {
}
