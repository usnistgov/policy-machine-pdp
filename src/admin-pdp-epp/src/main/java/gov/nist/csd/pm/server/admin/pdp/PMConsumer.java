package gov.nist.csd.pm.server.admin.pdp;

import gov.nist.csd.pm.common.exception.PMException;

@FunctionalInterface
public interface PMConsumer<T> {
	void accept(T t) throws PMException;
}
