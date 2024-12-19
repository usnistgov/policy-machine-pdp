package gov.nist.csd.pm.server.shared;

import java.util.Map;

public record ServerConfig(int resourcePort,
                           String resourceHost,
                           int adminPort,
                           String adminHost,
                           String esdbHost,
                           int esdbPort) {

	private static final String RESOURCE_GRPC_SERVER_HOST = "RESOURCE_GRPC_SERVER_HOST";
	private static final String ADMIN_GRPC_SERVER_HOST = "ADMIN_GRPC_SERVER_HOST";
	private static final String RESOURCE_GRPC_SERVER_PORT = "RESOURCE_GRPC_SERVER_PORT";
	private static final String ADMIN_GRPC_SERVER_PORT = "ADMIN_GRPC_SERVER_PORT";
	private static final String ESDB_HOST = "ESDB_HOST";
	private static final String ESDB_PORT = "ESDB_PORT";

	/**
	 * Load config from environment variables.
	 * @return The loaded Config object.
	 */
	public static ServerConfig load() {
		Map<String, String> env = System.getenv();

		// grpc server host names
		String resourceHost = env.getOrDefault(RESOURCE_GRPC_SERVER_HOST, "localhost");
		String adminHost = env.getOrDefault(ADMIN_GRPC_SERVER_HOST, "localhost");

		// grpc server ports
		String resPort = env.getOrDefault(RESOURCE_GRPC_SERVER_PORT, "50051");
		String adminPort = env.getOrDefault(ADMIN_GRPC_SERVER_PORT, "50052");

		// esdb connection params
		String esdbHost = env.getOrDefault(ESDB_HOST, "localhost");
		String esdbPort = env.getOrDefault(ESDB_PORT, "2113");

		return new ServerConfig(
				Integer.parseInt(resPort),
				resourceHost,
				Integer.parseInt(adminPort),
				adminHost,
				esdbHost,
				Integer.parseInt(esdbPort)
		);
	}

}
