/*
 * This Software (Policy Machine) is being made available as a public service by the
 * National Institute of Standards and Technology (NIST), an Agency of the United
 * States Department of Commerce. This software was developed in part by employees of
 * NIST and in part by NIST contractors. Copyright in portions of this software that
 * were developed by NIST contractors has been licensed or assigned to NIST. Pursuant
 * to Title 17 United States Code Section 105, works of NIST employees are not
 * subject to copyright protection in the United States. However, NIST may hold
 * international copyright in software created by its employees and domestic
 * copyright (or licensing rights) in portions of software that were assigned or
 * licensed to NIST.
 *
 * This file is part of the admin-pdp-epp module, which compiles against
 * and embeds org.neo4j:neo4j (GPLv3, Community Edition). As a combined work, this
 * module is distributed under the GNU General Public License v3.0, not the CC BY 4.0
 * license used elsewhere in this repository. See admin-pdp-epp/LICENSE for the full text.
 */

package gov.nist.csd.pm.pdp.admin.pap;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.common.graph.node.NodeType;
import org.junit.jupiter.api.Test;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

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

        NoCommitNeo4jPolicyStore policyStore = new NoCommitNeo4jPolicyStore(graphDb);
        NoCommitNeo4jPolicyStore actual = new NoCommitNeo4jPolicyStore(graphDb);

        policyStore.beginTx();
        policyStore.graph().createNode(1, "pc1", NodeType.PC);
        policyStore.commit();

        assertFalse(actual.graph().nodeExists("pc1"));

        managementService.shutdown();
    }
}