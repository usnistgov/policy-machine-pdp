package gov.nist.csd.pm.pdp.admin.pap;

import static org.junit.Assert.assertFalse;
import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.node.NodeType;
import java.io.File;

import org.junit.jupiter.api.Test;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;

class NoCommitNeo4jPolicyStoreTest {

    @Test
    void testNoCommit() throws PMException {
        DatabaseManagementService managementService = new DatabaseManagementServiceBuilder(
            new File("/tmp/no-commit-test").toPath()).build();
        GraphDatabaseService graphDb = managementService.database(DEFAULT_DATABASE_NAME);
        try (Transaction tx = graphDb.beginTx()) {
            tx.execute("match (n) detach delete n");
            tx.commit();
        }

        NoCommitNeo4jPolicyStore policyStore = new NoCommitNeo4jPolicyStore(graphDb, NoCommitNeo4jPolicyStoreTest.class.getClassLoader());
        NoCommitNeo4jPolicyStore actual = new NoCommitNeo4jPolicyStore(graphDb, getClass().getClassLoader());

        policyStore.beginTx();
        policyStore.graph().createNode(1, "pc1", NodeType.PC);
        policyStore.commit();

        assertFalse(actual.graph().nodeExists("pc1"));

        managementService.shutdown();
    }
}