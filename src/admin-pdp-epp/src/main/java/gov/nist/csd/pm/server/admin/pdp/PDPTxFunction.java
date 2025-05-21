package gov.nist.csd.pm.server.admin.pdp;

import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.pdp.PDPTx;

@FunctionalInterface
public interface PDPTxFunction<R> {
	R apply(PDPTx pdpTx) throws PMException;
}
