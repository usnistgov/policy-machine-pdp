package gov.nist.csd.pm.server.admin;

import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.impl.neo4j.memory.pap.store.Neo4jMemoryPolicyStore;
import org.neo4j.graphdb.GraphDatabaseService;

public class NoCommitNeo4jPolicyStore extends Neo4jMemoryPolicyStore {
	public NoCommitNeo4jPolicyStore(GraphDatabaseService graphDb) throws PMException {
		super(graphDb);

		setTxHandler(new NoCommitTxHandler(graphDb));
	}
}
