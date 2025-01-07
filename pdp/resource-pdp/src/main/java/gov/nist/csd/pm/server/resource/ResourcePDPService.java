package gov.nist.csd.pm.server.resource;

import gov.nist.csd.pm.epp.EPP;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.exception.PMException;
import gov.nist.csd.pm.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pdp.PDP;
import gov.nist.csd.pm.pdp.proto.ResourceOperationRequest;
import gov.nist.csd.pm.pdp.proto.ResourceOperationResponse;
import gov.nist.csd.pm.pdp.proto.ResourcePDPGrpc;
import gov.nist.csd.pm.server.shared.ServerConfig;
import gov.nist.csd.pm.server.shared.UserContextInterceptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import com.eventstore.dbclient.*;

public class ResourcePDPService extends ResourcePDPGrpc.ResourcePDPImplBase {

	private PDP pdp;
	private EPPClient epp;

	public ResourcePDPService(PDP pdp, PAP pap, ServerConfig serverConfig) throws PMException {
		this.pdp = pdp;
		this.epp = new EPPClient(pdp, pap, serverConfig);
		this.pdp.addEventListener(this.epp);
	}

	@Override
	public void adjudicateResourceOperation(ResourceOperationRequest request, StreamObserver<ResourceOperationResponse> responseObserver) {
		System.out.println("Received operation: " + request);

		UserContext userCtx = new UserContext(
				UserContextInterceptor.getPmUserHeaderValue(),
				UserContextInterceptor.getPmProcessHeaderValue()
		);

		try {
			pdp.adjudicateResourceOperation(userCtx, request.getTarget(), request.getOperation());

			ResourceOperationResponse response = ResourceOperationResponse.newBuilder().build();

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (PMException e) {
			responseObserver.onError(
					Status.INTERNAL
							.withDescription(e.getMessage())
							.withCause(e)
							.asRuntimeException()
			);
		}
	}
}
