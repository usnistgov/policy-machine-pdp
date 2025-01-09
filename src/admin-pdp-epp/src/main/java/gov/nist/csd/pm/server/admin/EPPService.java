package gov.nist.csd.pm.server.admin;

import com.eventstore.dbclient.EventData;
import com.eventstore.dbclient.EventStoreDBClient;
import gov.nist.csd.pm.epp.EPP;
import gov.nist.csd.pm.epp.proto.EPPGrpc;
import gov.nist.csd.pm.epp.proto.EPPResponse;
import gov.nist.csd.pm.epp.proto.EventContext;
import gov.nist.csd.pm.impl.neo4j.memory.pap.Neo4jMemoryPAP;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.exception.PMException;
import gov.nist.csd.pm.pdp.PDP;
import gov.nist.csd.pm.server.admin.event.EventPAP;
import gov.nist.csd.pm.server.shared.EventContextUtil;
import io.grpc.stub.StreamObserver;
import org.neo4j.graphdb.GraphDatabaseService;

public class EPPService extends EPPGrpc.EPPImplBase {

	private GraphDatabaseService graphDB;
	private EventStoreDBClient eventStoreDBClient;

	public EPPService(GraphDatabaseService graphDB, EventStoreDBClient eventStoreDBClient) throws PMException {
		this.graphDB = graphDB;
		this.eventStoreDBClient = eventStoreDBClient;
	}

	@Override
	public void processEvent(EventContext request, StreamObserver<EPPResponse> responseObserver) {
		try {
			NoCommitNeo4jPolicyStore neo4jMemoryPolicyStore = new NoCommitNeo4jPolicyStore(graphDB);
			EventPAP pap = new EventPAP(new Neo4jMemoryPAP(neo4jMemoryPolicyStore));
			PDP pdp = new PDP(pap);
			EPP epp = new EPP(pdp, pap);

			// create event listener to listen to all events from PDP
			epp.getEventProcessor().processEvent(EventContextUtil.fromProto(request));

			// send events to event store
			eventStoreDBClient.appendToStream("policy-machine-v1", pap.modify().getEvents().toArray(EventData[]::new));
		} catch (PMException e) {
			responseObserver.onError(e);
		}
	}
}
