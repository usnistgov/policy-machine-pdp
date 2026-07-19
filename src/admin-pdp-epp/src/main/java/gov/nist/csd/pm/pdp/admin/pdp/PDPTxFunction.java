package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;

@FunctionalInterface
public interface PDPTxFunction<R> {
	R apply(PAP pap, UserContext userCtx) throws PMException;
}
