package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.Neo4jEmbeddedPAP;
import gov.nist.csd.pm.core.pdp.UnauthorizedException;
import gov.nist.csd.pm.proto.v1.adjudication.AdminAdjudicationServiceGrpc;
import gov.nist.csd.pm.proto.v1.adjudication.AdminCmdRequest;
import gov.nist.csd.pm.proto.v1.adjudication.AdminCmdResponse;
import gov.nist.csd.pm.proto.v1.model.Value;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;

@GrpcService
public class AdjudicationService extends AdminAdjudicationServiceGrpc.AdminAdjudicationServiceImplBase {

	private final Adjudicator adjudicator;

	public AdjudicationService(Adjudicator adjudicator) {
		this.adjudicator = adjudicator;
	}

	@Override
	public void adjudicateAdminCmd(AdminCmdRequest request, StreamObserver<AdminCmdResponse> responseObserver) {
		try {
			List<Value> values = adjudicator.adjudicateAdminCommands(request.getCommandsList());
			responseObserver.onNext(AdminCmdResponse.newBuilder().addAllResults(values).build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED
					                         .withDescription(e.getMessage())
					                         .withCause(e)
					                         .asRuntimeException());
		} catch (Exception e) {
			responseObserver.onError(Status.INTERNAL
					                         .withDescription(e.getMessage())
					                         .withCause(e)
					                         .asRuntimeException());
		}
	}
}
