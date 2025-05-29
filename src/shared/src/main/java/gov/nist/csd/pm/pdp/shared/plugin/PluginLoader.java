package gov.nist.csd.pm.pdp.shared.plugin;

import gov.nist.csd.pm.core.pap.function.op.Operation;
import gov.nist.csd.pm.core.pap.function.routine.Routine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class PluginLoader {

	@Bean
	public List<Operation<?, ?>> loadOperationPlugins() {
		return new ArrayList<>();
	}

	@Bean
	public List<Routine<?, ?>> loadRoutinePlugins() {
		return new ArrayList<>();
	}
}
