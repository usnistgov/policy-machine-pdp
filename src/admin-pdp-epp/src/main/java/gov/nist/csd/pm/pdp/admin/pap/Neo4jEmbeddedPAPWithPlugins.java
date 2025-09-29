package gov.nist.csd.pm.pdp.admin.pap;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.Neo4jEmbeddedPAP;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.store.Neo4jEmbeddedPolicyStore;
import gov.nist.csd.pm.core.pap.id.RandomIdGenerator;
import gov.nist.csd.pm.core.pap.modification.*;
import gov.nist.csd.pm.core.pap.query.*;
import gov.nist.csd.pm.pdp.shared.plugin.PluginLoader;
import org.springframework.stereotype.Component;

@Component
public class Neo4jEmbeddedPAPWithPlugins extends Neo4jEmbeddedPAP {

	public Neo4jEmbeddedPAPWithPlugins(Neo4jEmbeddedPolicyStore neo4jEmbeddedPolicyStore, PluginLoader pluginLoader) throws PMException {
		super(
				initQuerier(neo4jEmbeddedPolicyStore, pluginLoader),
				initModifier(neo4jEmbeddedPolicyStore),
				neo4jEmbeddedPolicyStore
		);
	}

	private static PolicyModifier initModifier(Neo4jEmbeddedPolicyStore policyStore) {
		return new PolicyModifier(
				new GraphModifier(policyStore, new RandomIdGenerator()),
				new ProhibitionsModifier(policyStore),
				new ObligationsModifier(policyStore),
				new OperationsModifier(policyStore),
				new RoutinesModifier(policyStore)
		);
	}

	private static PolicyQuerier initQuerier(Neo4jEmbeddedPolicyStore policyStore, PluginLoader pluginLoader) {
		return new PolicyQuerier(
				new GraphQuerier(policyStore),
				new ProhibitionsQuerier(policyStore),
				new ObligationsQuerier(policyStore),
				new OperationsQuerier(policyStore),
				new RoutinesQuerier(policyStore),
				new AccessQuerier(policyStore)
		);
	}
}
