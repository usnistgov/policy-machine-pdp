package gov.nist.csd.pm.pdp.admin.pap;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.function.routine.Routine;
import gov.nist.csd.pm.core.pap.query.RoutinesQuerier;
import gov.nist.csd.pm.core.pap.store.PolicyStore;
import gov.nist.csd.pm.pdp.shared.plugin.PluginLoader;

import java.util.Collection;

public class Neo4jEmbeddedRoutinesQuerierWithPlugins extends RoutinesQuerier {

	private final PluginLoader pluginLoader;

	public Neo4jEmbeddedRoutinesQuerierWithPlugins(PolicyStore store, PluginLoader pluginLoader) {
		super(store);

		this.pluginLoader = pluginLoader;
	}

	@Override
	public Collection<String> getAdminRoutineNames() throws PMException {
		Collection<String> adminRoutineNames = super.getAdminRoutineNames();
		for (Routine<?, ?> routine : pluginLoader.routinePlugins()) {
			adminRoutineNames.add(routine.getName());
		}

		return adminRoutineNames;
	}

	@Override
	public Routine<?, ?> getAdminRoutine(String routineName) throws PMException {
		Routine<?, ?> routine = pluginLoader.getRoutine(routineName);
		if (routine != null) {
			return routine;
		}

		return super.getAdminRoutine(routineName);
	}
}
