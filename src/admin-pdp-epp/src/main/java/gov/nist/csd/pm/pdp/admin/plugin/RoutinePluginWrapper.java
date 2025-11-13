package gov.nist.csd.pm.pdp.admin.plugin;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.function.arg.Args;
import gov.nist.csd.pm.core.pap.function.routine.Routine;

public class RoutinePluginWrapper<T> extends Routine<T> {

	private final Routine<T> routine;
	private final ClassLoader classLoader;

	public RoutinePluginWrapper(Routine<T> routine, ClassLoader classLoader) {
		super(routine.getName(), routine.getFormalParameters());
		this.routine = routine;
		this.classLoader = classLoader;
	}

	public Routine<T> getRoutine() {
		return routine;
	}

	public ClassLoader getClassLoader() {
		return classLoader;
	}

	@Override
	public T execute(PAP pap, Args args) throws PMException {
		return executeWithContext(() -> routine.execute(pap, args));
	}

	private <R> R executeWithContext(PluginCallable<R> callable) throws PMException {
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
	private interface PluginCallable<R> {
		R call() throws PMException;
	}
}
