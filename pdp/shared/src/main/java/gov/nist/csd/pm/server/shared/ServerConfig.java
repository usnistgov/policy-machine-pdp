package gov.nist.csd.pm.server.shared;

import java.util.Map;

public record ServerConfig(int resourcePort,
                           String resourceHost,
                           int adminPort,
                           String adminHost,
                           String esdbHost,
                           int esdbPort) {

	private static final String RESOURCE_GRPC_SERVER_HOST = "RESOURCE_GRPC_SERVER_HOST";
	private static final String RESOURCE_GRPC_SERVER_PORT = "RESOURCE_GRPC_SERVER_PORT";

	private static final String ADMIN_GRPC_SERVER_HOST = "ADMIN_GRPC_SERVER_HOST";
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
		String resourceHost = env.get(RESOURCE_GRPC_SERVER_HOST);
		String adminHost = env.get(ADMIN_GRPC_SERVER_HOST);

		// grpc server ports
		String resPort = env.get(RESOURCE_GRPC_SERVER_PORT);
		String adminPort = env.get(ADMIN_GRPC_SERVER_PORT);

		// esdb connection params
		String esdbHost = env.get(ESDB_HOST);
		String esdbPort = env.get(ESDB_PORT);

		System.out.println(env);

		return new ServerConfig(
				resPort != null ? Integer.parseInt(resPort) : -1,
				resourceHost,
				adminPort != null ? Integer.parseInt(adminPort) : -1,
				adminHost,
				esdbHost,
				esdbPort != null ? Integer.parseInt(esdbPort) : -1
		);
	}

}
