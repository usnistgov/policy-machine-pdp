package gov.nist.csd.pm.pdp.admin.plugin.wrapper;

import gov.nist.csd.pm.core.common.exception.PMException;

public interface OperationPluginWrapper {

	/**
	 * 	Allows plugins to be executed by the PM server components using their own internal JARs that the PM server
	 * 	doesn't know about.
	 * @param classLoader the class loader used to load the plugin.
	 * @param callable the implementation of the PluginCallable interface to execute the operation being wrapped.
	 * @return The value returned by the wrapped operation.
	 * @param <R> The generic representing the return type of the wrapped operation.
	 * @throws PMException if there is an error executing the wrapped operation.
	 */
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
