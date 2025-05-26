package gov.nist.csd.pm.pdp.admin.pap;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.store.Neo4jEmbeddedPolicyStore;
import org.neo4j.graphdb.GraphDatabaseService;

public class NoCommitNeo4jPolicyStore extends Neo4jEmbeddedPolicyStore {
	public NoCommitNeo4jPolicyStore(GraphDatabaseService graphDb) throws PMException {
		super(graphDb);

		setTxHandler(new NoCommitTxHandler(graphDb));
	}
}
