package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.admin.pap.EventTrackingPAP;

public record NGACContext(UserContext userCtx, PDP pdp, EventTrackingPAP pap) {
}
