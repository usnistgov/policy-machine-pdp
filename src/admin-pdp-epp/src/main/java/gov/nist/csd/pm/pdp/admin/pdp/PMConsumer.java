package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;

@FunctionalInterface
public interface PMConsumer<T> {
	void accept(T t) throws PMException;
}
