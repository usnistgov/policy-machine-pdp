package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.ngac.pm.core.epp.EPP;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pdp.PDP;

public record NGACContext(PDP pdp, EPP epp, PAP pap) {
}
