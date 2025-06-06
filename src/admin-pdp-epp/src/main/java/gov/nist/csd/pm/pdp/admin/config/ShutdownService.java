package gov.nist.csd.pm.pdp.admin.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

@Service
@DependsOn(value = "Neo4jBootstrapper")
public class ShutdownService implements ApplicationListener<ApplicationReadyEvent> {

	private final AdminPDPConfig config;
	private final ConfigurableApplicationContext context;

	public ShutdownService(AdminPDPConfig config, ConfigurableApplicationContext context) {
		this.config = config;
		this.context = context;
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		if (config.isShutdownAfterBootstrap()) {
			context.close();
		}
	}
}
