package gov.nist.csd.pm.pdp.admin.pap;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.function.op.Operation;
import gov.nist.csd.pm.core.pap.query.OperationsQuerier;
import gov.nist.csd.pm.core.pap.store.PolicyStore;
import gov.nist.csd.pm.pdp.shared.plugin.PluginLoader;

import java.util.Collection;

public class Neo4jEmbeddedOperationsQuerierWithPlugins extends OperationsQuerier {

	private final PluginLoader pluginLoader;

	public Neo4jEmbeddedOperationsQuerierWithPlugins(PolicyStore store, PluginLoader pluginLoader) {
		super(store);

		this.pluginLoader = pluginLoader;
	}

	@Override
	public Collection<String> getAdminOperationNames() throws PMException {
		Collection<String> adminOperationNames = super.getAdminOperationNames();
		for (Operation<?, ?> operation : pluginLoader.operationPlugins()) {
			adminOperationNames.add(operation.getName());
		}

		return adminOperationNames;
	}

	@Override
	public Operation<?, ?> getAdminOperation(String operationName) throws PMException {
		Operation<?, ?> operation = pluginLoader.getOperation(operationName);
		if (operation != null) {
			return operation;
		}

		return super.getAdminOperation(operationName);
	}
}
