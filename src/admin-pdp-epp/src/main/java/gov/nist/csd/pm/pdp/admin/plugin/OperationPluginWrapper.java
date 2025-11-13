package gov.nist.csd.pm.pdp.admin.plugin;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.function.arg.Args;
import gov.nist.csd.pm.core.pap.function.op.Operation;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;

public class OperationPluginWrapper<T> extends Operation<T> {

	private final Operation<T> operation;
	private final ClassLoader classLoader;

	public OperationPluginWrapper(Operation<T> operation, ClassLoader classLoader) {
		super(operation.getName(), operation.getFormalParameters());
		this.operation = operation;
		this.classLoader = classLoader;
	}

	public Operation<T> getOperation() {
		return operation;
	}

	public ClassLoader getClassLoader() {
		return classLoader;
	}

	@Override
	public void canExecute(PAP pap, UserContext userContext, Args args) throws PMException {
		executeWithContext(() -> {
			operation.canExecute(pap, userContext, args);
			return null;
		});
	}

	@Override
	public T execute(PAP pap, Args args) throws PMException {
		return executeWithContext(() -> operation.execute(pap, args));
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
