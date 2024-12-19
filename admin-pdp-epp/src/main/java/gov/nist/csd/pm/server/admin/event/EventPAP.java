package gov.nist.csd.pm.server.admin.event;

import gov.nist.csd.pm.impl.neo4j.memory.pap.Neo4jMemoryPAP;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.exception.PMException;
import gov.nist.csd.pm.pap.modification.PolicyModification;
import gov.nist.csd.pm.pap.query.PolicyQuery;

public class EventPAP extends PAP {

	private Neo4jMemoryPAP pap;
	private EventPolicyModifier policyModifier;

	public EventPAP(Neo4jMemoryPAP pap) throws PMException {
		super(pap.policyStore());

		this.pap = pap;
		this.policyModifier = new EventPolicyModifier(pap.policyStore());
	}

	@Override
	public PolicyQuery query() {
		return pap.query();
	}

	@Override
	public EventPolicyModifier modify() {
		return policyModifier;
	}

	@Override
	public void beginTx() throws PMException {
		pap.beginTx();
	}

	@Override
	public void commit() throws PMException {
		pap.commit();
	}

	@Override
	public void rollback() throws PMException {
		pap.rollback();
	}
}
