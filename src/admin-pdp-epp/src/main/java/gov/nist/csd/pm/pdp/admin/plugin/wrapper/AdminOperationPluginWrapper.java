package gov.nist.csd.pm.pdp.admin.plugin.wrapper;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.operation.AdminOperation;
import gov.nist.csd.pm.core.pap.operation.arg.Args;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;

public class AdminOperationPluginWrapper<T> extends AdminOperation<T> implements OperationPluginWrapper {

	private final AdminOperation<T> operation;
	private final ClassLoader classLoader;

	public AdminOperationPluginWrapper(AdminOperation<T> operation, ClassLoader classLoader) {
		super(operation.getName(), operation.getReturnType(), operation.getFormalParameters(), operation.getRequiredCapabilities());
		this.operation = operation;
		this.classLoader = classLoader;
	}

	@Override
	public void canExecute(PAP pap, UserContext userContext, Args args) throws PMException {
		executeWithContext(classLoader, () -> {
			operation.canExecute(pap, userContext, args);
			return null;
		});
	}

	@Override
	public T execute(PAP pap, Args args) throws PMException {
		return executeWithContext(classLoader, () -> operation.execute(pap, args));
	}

}
