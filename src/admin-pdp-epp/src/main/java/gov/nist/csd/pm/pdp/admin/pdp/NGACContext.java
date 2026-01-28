package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.epp.EPP;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.admin.pap.EventTrackingPAP;

public record NGACContext(PDP pdp, EPP epp, EventTrackingPAP pap) {
}
