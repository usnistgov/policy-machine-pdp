package gov.nist.csd.pm.pdp.shared.plugin;

import gov.nist.csd.pm.core.pap.function.op.Operation;
import gov.nist.csd.pm.core.pap.function.routine.Routine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

@Configuration
public class PluginLoader {

	private static final Logger logger = LoggerFactory.getLogger(PluginLoader.class);

	private final PluginLoaderConfig config;
	private URLClassLoader pluginClassLoader;

	public PluginLoader(PluginLoaderConfig config) {
		this.config = config;
		this.pluginClassLoader = createPluginClassLoader();
	}

	public URLClassLoader getPluginClassLoader() {
		return pluginClassLoader;
	}

	@Bean
	public List<Operation<?, ?>> operationPlugins() {
		try {
			return (List<Operation<?, ?>>)(List<?>) loadPlugins(Operation.class);
		} catch (IOException e) {
			logger.warn("Failed to load operation plugins: {}", e.getMessage());
			return new ArrayList<>();
		}
	}

	@Bean
	public List<Routine<?, ?>> routinePlugins() {
		try {
			return (List<Routine<?, ?>>)(List<?>) loadPlugins(Routine.class);
		} catch (IOException e) {
			logger.warn("Failed to routine plugins: {}", e.getMessage());
			return new ArrayList<>();
		}
	}

	public Operation<?, ?> getOperation(String name) {
		for (Operation<?, ?> op : operationPlugins()) {
			if (op.getName().equals(name)) {
				return op;
			}
		}

		return null;
	}

	public Routine<?, ?> getRoutine(String name) {
		for (Routine<?, ?> routine : routinePlugins()) {
			if (routine.getName().equals(name)) {
				return routine;
			}
		}

		return null;
	}

	public boolean pluginExists(String name) {
		for (Operation<?, ?> op : operationPlugins()) {
			if (op.getName().equals(name)) {
				return true;
			}
		}

		for (Routine<?, ?> r : routinePlugins()) {
			if (r.getName().equals(name)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Creates a URLClassLoader that can load classes from JAR files in the plugin directory
	 */
	private URLClassLoader createPluginClassLoader() {
		List<URL> urls = new ArrayList<>();
		Path root = Paths.get(config.getDir()).toAbsolutePath().normalize();
		
		if (!Files.exists(root)) {
			logger.warn("Plugin directory does not exist: {}", root);
			return URLClassLoader.newInstance(new URL[0], this.getClass().getClassLoader());
		}

		try {
			// Add all JAR files in the plugin directory and subdirectories
			try (Stream<Path> jarFiles = Files.walk(root)) {
				jarFiles.filter(p -> p.toString().endsWith(".jar"))
					.forEach(jarPath -> {
						try {
							urls.add(jarPath.toUri().toURL());
							logger.debug("Added JAR to classpath: {}", jarPath);
						} catch (MalformedURLException e) {
							logger.warn("Failed to add JAR to classpath: {}", jarPath, e);
						}
					});
			}
			
		} catch (IOException e) {
			logger.error("Failed to create plugin class loader URLs", e);
		}

		URL[] urlArray = urls.toArray(new URL[0]);
		logger.info("Created plugin class loader with {} JAR URLs", urlArray.length);
		
		return URLClassLoader.newInstance(urlArray, this.getClass().getClassLoader());
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> loadPlugins(Class<T> type) throws IOException {
		logger.info("loading plugins from {} of type {}", config.getDir(), type.getName());

		Set<String> loadedPlugins = new HashSet<>();
		List<T> result = new ArrayList<>();
		Path root = Paths.get(config.getDir()).toAbsolutePath().normalize();

		if (!Files.exists(root)) {
			logger.warn("Plugin directory does not exist: {}", root);
			return result;
		}

		// Scan for JARs
		try (Stream<Path> jarFiles = Files.walk(root)) {
			jarFiles.filter(p -> p.toString().endsWith(".jar"))
				.forEach(jarPath -> {
					try (JarFile jar = new JarFile(jarPath.toFile())) {
						Enumeration<JarEntry> entries = jar.entries();
						while (entries.hasMoreElements()) {
							JarEntry entry = entries.nextElement();
							if (entry.getName().endsWith(".class")) {
								String className = entry.getName()
										.replace('/', '.')
										.replaceAll("\\.class$", "");
								addIfTypeMatches(className, type, result, loadedPlugins);
							}
						}
					} catch (IOException e) {
						logger.warn("Failed to read JAR file: {}", jarPath, e);
					}
				});
		}

		logger.info("loaded {} {} plugins", result.size(), type.getSimpleName());

		return result;
	}

	private <T> void addIfTypeMatches(String className, Class<T> parentType, List<T> plugins, Set<String> loadedPlugins) {
		if (loadedPlugins.contains(className)) {
			logger.debug("already loaded plugin {}", className);
			return;
		}

		try {
			// Use the plugin class loader instead of Class.forName() to load classes not in current classpath
			Class<?> cls = pluginClassLoader.loadClass(className);
			if (parentType.isAssignableFrom(cls) && !cls.equals(parentType)) {
				Constructor<?> constructor = cls.getDeclaredConstructor();
				constructor.setAccessible(true); // Allow access to non-public constructors
				plugins.add((T) constructor.newInstance());

				loadedPlugins.add(className);
				logger.debug("Successfully loaded plugin: {}", className);
			}
		} catch (ClassNotFoundException | NoClassDefFoundError | NoSuchMethodException | InstantiationException |
		         IllegalAccessException | InvocationTargetException e) {
			logger.debug("Cannot load plugin class " + className + ", error: " + e.getMessage());
		}
	}

	@PreDestroy
	public void destroy() {
		if (pluginClassLoader != null) {
			try {
				pluginClassLoader.close();
			} catch (IOException e) {
				logger.warn("Failed to close plugin class loader", e);
			}
		}
	}
}
