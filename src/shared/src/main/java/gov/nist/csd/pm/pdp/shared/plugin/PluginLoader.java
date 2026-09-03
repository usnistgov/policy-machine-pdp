/*
 * This Software (Policy Machine) is being made available as a public service by the
 * National Institute of Standards and Technology (NIST), an Agency of the United
 * States Department of Commerce. This software was developed in part by employees of
 * NIST and in part by NIST contractors. Copyright in portions of this software that
 * were developed by NIST contractors has been licensed or assigned to NIST. Pursuant
 * to Title 17 United States Code Section 105, works of NIST employees are not
 * subject to copyright protection in the United States. However, NIST may hold
 * international copyright in software created by its employees and domestic
 * copyright (or licensing rights) in portions of software that were assigned or
 * licensed to NIST. To the extent that NIST holds copyright in this software, it is
 * being made available under the Creative Commons Attribution 4.0 International
 * license (CC BY 4.0). The disclaimers of the CC BY 4.0 license apply to all parts
 * of the software developed or licensed by NIST.
 *
 * ACCESS THE FULL CC BY 4.0 LICENSE HERE:
 * https://creativecommons.org/licenses/by/4.0/legalcode
 */

package gov.nist.csd.pm.pdp.shared.plugin;

import gov.nist.ngac.pm.core.pap.operation.*;
import gov.nist.csd.pm.pdp.shared.plugin.wrapper.*;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class PluginLoader {

	private static final Logger logger = LoggerFactory.getLogger(PluginLoader.class);

	private final PluginManager pluginManager;
	private final Path pluginsRoot;
	private final Object lifecycleMonitor = new Object();
	private volatile boolean initialized;

	public PluginLoader(String pluginsDir) {
		this.pluginsRoot = resolvePluginsRoot(pluginsDir);
		this.pluginManager = (this.pluginsRoot == null)
				? null
				: new DefaultPluginManager(this.pluginsRoot);
	}

	public List<Operation<?>> loadPlugins() {
		if (!ensurePluginManagerStarted()) {
			return Collections.emptyList();
		}

		List<Operation<?>> wrapped = new ArrayList<>();

		List<Operation> providers = pluginManager.getExtensions(Operation.class);

		for (Operation op : providers) {
			PluginWrapper wrapper = pluginManager.whichPlugin(op.getClass());
			ClassLoader pluginCl = wrapper.getPluginClassLoader();

			Operation wrappedOp = switch (op) {
				case AdminOperation adminOperation -> new AdminOperationPluginWrapper<>(adminOperation, pluginCl);
				case Function function -> new FunctionPluginWrapper(function, pluginCl);
				case QueryOperation queryOperation -> new QueryOperationPluginWrapper(queryOperation, pluginCl);
				case ResourceOperation resourceOperation -> new ResourceOperationPluginWrapper(resourceOperation, pluginCl);
				case Routine routine -> new RoutinePluginWrapper(routine, pluginCl);
			};

			wrapped.add(wrappedOp);
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
