package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pdp.PDPTx;

@FunctionalInterface
public interface PDPTxFunction<R> {
	R apply(PDPTx pdpTx) throws PMException;
}
