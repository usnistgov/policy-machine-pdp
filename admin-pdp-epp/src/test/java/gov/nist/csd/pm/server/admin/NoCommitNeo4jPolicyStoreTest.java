package gov.nist.csd.pm.server.admin;

import gov.nist.csd.pm.pap.exception.PMException;
import gov.nist.csd.pm.pap.graph.node.NodeType;
import org.junit.jupiter.api.Test;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;

import java.io.File;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

class NoCommitNeo4jPolicyStoreTest {

	@Test
	void testNoCommit() throws PMException {
		DatabaseManagementService managementService = new DatabaseManagementServiceBuilder(new File("/tmp/no-commit-test").toPath()).build();
		GraphDatabaseService graphDb = managementService.database(DEFAULT_DATABASE_NAME);
		try(Transaction tx = graphDb.beginTx()) {
			tx.execute("match (n) detach delete n");
			tx.commit();
		}

		NoCommitNeo4jPolicyStore policyStore = new NoCommitNeo4jPolicyStore(graphDb);
		NoCommitNeo4jPolicyStore actual = new NoCommitNeo4jPolicyStore(graphDb);

		policyStore.beginTx();
		policyStore.graph().createNode("pc1", NodeType.PC);
		policyStore.commit();

		assertFalse(actual.graph().nodeExists("pc1"));

		managementService.shutdown();
	}


}