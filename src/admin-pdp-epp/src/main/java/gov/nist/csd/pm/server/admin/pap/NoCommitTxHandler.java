package gov.nist.csd.pm.server.admin.pap;

import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.impl.neo4j.memory.pap.store.TxHandler;
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
	public void commit() throws PMException {
		if (txCounter-1 != 0) {
			txCounter--;
			return;
		}

		txCounter = 0;
		tx.close();
		tx = null;
	}
}
