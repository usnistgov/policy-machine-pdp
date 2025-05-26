package gov.nist.csd.pm.pdp.admin.pap;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.store.TxHandler;
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
