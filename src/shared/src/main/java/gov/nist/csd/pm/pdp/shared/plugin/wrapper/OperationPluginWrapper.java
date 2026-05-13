package gov.nist.csd.pm.pdp.shared.plugin.wrapper;

import gov.nist.csd.pm.core.common.exception.PMException;

public interface OperationPluginWrapper {

	default <R> R executeWithContext(ClassLoader classLoader, PluginCallable<R> callable) throws PMException {
		Thread currentThread = Thread.currentThread();
		ClassLoader previous = currentThread.getContextClassLoader();
		try {
			currentThread.setContextClassLoader(classLoader);
			return callable.call();
		} finally {
			currentThread.setContextClassLoader(previous);
		}
	}

	@FunctionalInterface
	interface PluginCallable<R> {
		R call() throws PMException;
	}
}
