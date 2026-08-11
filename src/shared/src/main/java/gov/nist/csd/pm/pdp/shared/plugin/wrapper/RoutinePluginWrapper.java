package gov.nist.csd.pm.pdp.shared.plugin.wrapper;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.operation.Routine;
import gov.nist.ngac.pm.core.pap.operation.arg.Args;
import gov.nist.ngac.pm.core.pap.query.model.context.UserContext;

public class RoutinePluginWrapper<T> extends Routine<T> implements OperationPluginWrapper {

	private final Routine<T> operation;
	private final ClassLoader classLoader;

	public RoutinePluginWrapper(Routine<T> operation, ClassLoader classLoader) {
		super(operation.getName(), operation.getReturnType(), operation.getFormalParameters());
		this.operation = operation;
		this.classLoader = classLoader;
	}

	@Override
	public T execute(PAP pap, UserContext userContext, Args args) throws PMException {
		return executeWithContext(classLoader, () -> operation.execute(pap, userContext, args));
	}
}
