package gov.nist.csd.pm.server.admin;

import com.eventstore.dbclient.EventData;
import com.eventstore.dbclient.EventStoreDBClient;
import gov.nist.csd.pm.epp.EPP;
import gov.nist.csd.pm.impl.neo4j.memory.pap.Neo4jMemoryPAP;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.exception.PMException;
import gov.nist.csd.pm.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pdp.OperationRequest;
import gov.nist.csd.pm.pdp.PDP;
import gov.nist.csd.pm.pdp.proto.*;
import gov.nist.csd.pm.server.admin.event.EventPAP;
import gov.nist.csd.pm.server.shared.UserContextInterceptor;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.graphdb.GraphDatabaseService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static gov.nist.csd.pm.server.shared.EventContextUtil.fromProtoOperands;

public class PDPService extends AdminPDPGrpc.AdminPDPImplBase {

	private static final Logger logger = LogManager.getLogger(PDPService.class);

	private GraphDatabaseService graphDB;
	private EventStoreDBClient eventStoreDBClient;

	public PDPService(GraphDatabaseService graphDB, EventStoreDBClient eventStoreDBClient) throws PMException {
		// create indexes
		NoCommitNeo4jPolicyStore.createIndexes(graphDB);
		this.graphDB = graphDB;
		this.eventStoreDBClient = eventStoreDBClient;
	}

	@Override
	public void adjudicateAdminOperation(AdminOperationRequest request, StreamObserver<AdminOperationResponse> responseObserver) {
		try {
			NoCommitNeo4jPolicyStore neo4jMemoryPolicyStore = new NoCommitNeo4jPolicyStore(graphDB);
			EventPAP pap = new EventPAP(new Neo4jMemoryPAP(neo4jMemoryPolicyStore));
			PDP pdp = new PDP(pap);
			EPP epp = new EPP(pdp, pap);

			UserContext userCtx = new UserContext(
					UserContextInterceptor.getPmUserHeaderValue(),
					UserContextInterceptor.getPmProcessHeaderValue()
			);

			Map<String, Object> operands = fromProtoOperands(request.getOperandsList());

			logger.info("adjudicating operation {}({})", request.getOpName(), operands);

			// adjudicate operation - changes not committed
			pdp.adjudicateAdminOperation(
					userCtx,
					request.getOpName(),
					operands
			);

			// send events
			eventStoreDBClient.appendToStream("policy-machine-v1", pap.modify().getEvents().toArray(EventData[]::new));
		} catch (PMException e) {
			responseObserver.onError(e);
		}
	}

	@Override
	public void adjudicateAdminRoutine(AdminRoutineRequest request, StreamObserver<AdminRoutineResponse> responseObserver) {
		try {
			NoCommitNeo4jPolicyStore neo4jMemoryPolicyStore = new NoCommitNeo4jPolicyStore(graphDB);
			EventPAP pap = new EventPAP(new Neo4jMemoryPAP(neo4jMemoryPolicyStore));
			PDP pdp = new PDP(pap);
			EPP epp = new EPP(pdp, pap);

			UserContext userCtx = new UserContext(
					UserContextInterceptor.getPmUserHeaderValue(),
					UserContextInterceptor.getPmProcessHeaderValue()
			);

			List<AdminOperationRequest> opsList = request.getOpsList();
			List<OperationRequest> requests = new ArrayList<>();
			for (AdminOperationRequest opRequest : opsList) {
				String opName = opRequest.getOpName();
				Map<String, Object> operands = fromProtoOperands(opRequest.getOperandsList());

				requests.add(new OperationRequest(opName, operands));
			}

			pdp.adjudicateAdminRoutine(
					userCtx,
					requests
			);

			// send events
			eventStoreDBClient.appendToStream("policy-machine-v1", pap.modify().getEvents().toArray(EventData[]::new));
		} catch (PMException e) {
			responseObserver.onError(e);
		}
	}

	@Override
	public void adjudicateNamedAdminRoutine(NamedAdminRoutineRequest request, StreamObserver<AdminRoutineResponse> responseObserver) {
		try {
			NoCommitNeo4jPolicyStore neo4jMemoryPolicyStore = new NoCommitNeo4jPolicyStore(graphDB);
			EventPAP pap = new EventPAP(new Neo4jMemoryPAP(neo4jMemoryPolicyStore));
			PDP pdp = new PDP(pap);
			EPP epp = new EPP(pdp, pap);

			UserContext userCtx = new UserContext(
					UserContextInterceptor.getPmUserHeaderValue(),
					UserContextInterceptor.getPmProcessHeaderValue()
			);

			Map<String, Object> operands = fromProtoOperands(request.getOperandsList());

			pdp.adjudicateAdminRoutine(
					userCtx,
					request.getName(),
					operands
			);

			// send events
			eventStoreDBClient.appendToStream("policy-machine-v1", pap.modify().getEvents().toArray(EventData[]::new));
		} catch (PMException e) {
			responseObserver.onError(e);
		}
	}
}
