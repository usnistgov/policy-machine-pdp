package gov.nist.csd.pm.pdp.admin.plugin.wrapper;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.operation.Function;
import gov.nist.csd.pm.core.pap.operation.arg.Args;

public class FunctionPluginWrapper<T> extends Function<T> implements OperationPluginWrapper {

	private final Function<T> operation;
	private final ClassLoader classLoader;

	public FunctionPluginWrapper(Function<T> operation, ClassLoader classLoader) {
		super(operation.getName(), operation.getReturnType(), operation.getFormalParameters());
		this.operation = operation;
		this.classLoader = classLoader;
	}

	@Override
	public T execute(Args args) throws PMException {
		return executeWithContext(classLoader, () -> operation.execute(args));
	}
}
