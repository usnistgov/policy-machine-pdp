package gov.nist.csd.pm.pdp.admin.plugin;

import gov.nist.csd.pm.core.pap.function.op.Operation;
import gov.nist.csd.pm.core.pap.function.routine.Routine;
import gov.nist.csd.pm.pdp.admin.config.AdminPDPConfig;
import org.pf4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Configuration
public class PluginLoader {

	private static final Logger logger = LoggerFactory.getLogger(PluginLoader.class);

	private final PluginManager pluginManager;
	private final Path pluginsRoot;
	private final Object lifecycleMonitor = new Object();
	private volatile boolean initialized;

	public PluginLoader(AdminPDPConfig config) {
		this.pluginsRoot = resolvePluginsRoot(config.getPluginsDir());
		this.pluginManager = (this.pluginsRoot == null)
				? null
				: new DefaultPluginManager(this.pluginsRoot);
	}

	public List<Operation<?>> getOperationPlugins() {
		if (!ensurePluginManagerStarted()) {
			return Collections.emptyList();
		}

		List<Operation<?>> wrapped = new ArrayList<>();

		// PF4J will return *instances* of all @Extension classes across all plugins
		List<Operation> providers = pluginManager.getExtensions(Operation.class);

		for (Operation op : providers) {
			PluginWrapper wrapper = pluginManager.whichPlugin(op.getClass());
			ClassLoader pluginCl = wrapper.getPluginClassLoader();

			wrapped.add(new OperationPluginWrapper<>(op, pluginCl));
		}

		logger.info("Loaded {} Operation plugins via PF4J extensions", wrapped.size());
		return wrapped;
	}

	public List<Routine<?>> getRoutinePlugins() {
		if (!ensurePluginManagerStarted()) {
			return Collections.emptyList();
		}

		List<Routine<?>> wrapped = new ArrayList<>();

		// PF4J will return *instances* of all @Extension classes across all plugins
		List<Routine> providers = pluginManager.getExtensions(Routine.class);

		for (Routine routine : providers) {
			PluginWrapper wrapper = pluginManager.whichPlugin(routine.getClass());
			ClassLoader pluginCl = wrapper.getPluginClassLoader();

			wrapped.add(new RoutinePluginWrapper<>(routine, pluginCl));
		}

		logger.info("Loaded {} Operation plugins via PF4J extensions", wrapped.size());
		return wrapped;
	}

	private Path resolvePluginsRoot(String configuredDir) {
		String dir = Optional.ofNullable(configuredDir)
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.orElse(null);

		if (dir == null) {
			logger.info("Plugin directory is not configured; plugin loading is disabled.");
			return null;
		}

		Path path = Paths.get(dir).toAbsolutePath().normalize();
		logger.info("Using plugin directory: {}", path);
		return path;
	}

	private boolean ensurePluginManagerStarted() {
		if (pluginsRoot == null) {
			return false;
		}

		if (initialized) {
			return true;
		}

		synchronized (lifecycleMonitor) {
			if (initialized) {
				return true;
			}

			pluginManager.loadPlugins();
			pluginManager.startPlugins();
			initialized = true;
			logger.info("PF4J plugin manager started with {} plugins", pluginManager.getStartedPlugins().size());
			return true;
		}
	}

	@PreDestroy
	public void destroy() {
		synchronized (lifecycleMonitor) {
			if (initialized) {
				try {
					pluginManager.stopPlugins();
					pluginManager.unloadPlugins();
					logger.info("PF4J plugin manager stopped");
				} catch (RuntimeException e) {
					logger.warn("Error while stopping PF4J plugin manager", e);
				}
			}
		}
	}
}
