package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pdp.PDPTx;

@FunctionalInterface
public interface PDPTxFunction<R> {
	R apply(PAP pap, PDPTx pdpTx) throws PMException;
}
