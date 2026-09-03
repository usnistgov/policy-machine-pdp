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
import gov.nist.ngac.pm.core.neo4j.embedded.pap.store.TxHandler;
import org.neo4j.graphdb.GraphDatabaseService;

public class NoCommitTxHandler extends TxHandler {

    public NoCommitTxHandler(GraphDatabaseService graphDb) {
        super(graphDb);
    }

    @Override
    public void beginTx() throws PMException {
        super.beginTx();
    }

    @Override
    public void commit() {
        if (txCounter - 1 != 0) {
            txCounter--;
            return;
        }

        rollback();
    }

    @Override
    public void rollback() {
        txCounter = 0;

        // tx is null if a nested tx called rollback()
        if (tx == null) {
            return;
        }

        tx.rollback();
        tx.close();
        tx = null;
    }
}
