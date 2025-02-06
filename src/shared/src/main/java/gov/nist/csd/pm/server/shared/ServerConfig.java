package gov.nist.csd.pm.server.shared;

import java.util.Map;

public record ServerConfig(String resourceHost, int resourcePort,
                           String adminHost, int adminPort,
                           String esdbHost, int esdbPort,
                           String bootstrapFilePath,
                           String bootstrapUser) {

	public enum Key {
		RESOURCE_GRPC_SERVER_HOST,
		RESOURCE_GRPC_SERVER_PORT,
		ADMIN_GRPC_SERVER_HOST,
		ADMIN_GRPC_SERVER_PORT,
		ESDB_HOST,
		ESDB_PORT,
		BOOTSTRAP_FILE_PATH,
		BOOTSTRAP_USER
	}

	/**
	 * Load config from environment variables.
	 * @return The loaded Config object.
	 */
	public static ServerConfig load() {
		Map<String, String> env = System.getenv();

		// grpc server host names
		String resourceHost = env.getOrDefault(Key.RESOURCE_GRPC_SERVER_HOST.name(), "localhost");
		String adminHost = env.getOrDefault(Key.ADMIN_GRPC_SERVER_HOST.name(), "localhost");

		// grpc server ports
		String resPort = env.getOrDefault(Key.RESOURCE_GRPC_SERVER_PORT.name(), "50051");
		String adminPort = env.getOrDefault(Key.ADMIN_GRPC_SERVER_PORT.name(), "50052");

		// esdb connection params
		String esdbHost = env.getOrDefault(Key.ESDB_HOST.name(), "localhost");
		String esdbPort = env.getOrDefault(Key.ESDB_PORT.name(), "2113");

		// bootstrap params
		String bootstrapFilePath = env.get(Key.BOOTSTRAP_FILE_PATH.name());
		String bootstrapUser = env.get(Key.BOOTSTRAP_USER.name());

		return new ServerConfig(
				resourceHost, Integer.parseInt(resPort),
				adminHost, Integer.parseInt(adminPort),
				esdbHost, Integer.parseInt(esdbPort),
				bootstrapFilePath,
				bootstrapUser
		);
	}
}
