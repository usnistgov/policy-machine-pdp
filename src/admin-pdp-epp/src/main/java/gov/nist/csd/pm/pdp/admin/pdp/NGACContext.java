package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.epp.EPP;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pdp.PDP;

public record NGACContext(PDP pdp, EPP epp, PAP pap) {
}
