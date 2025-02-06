package gov.nist.csd.pm.server.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pdp.PDP;
import gov.nist.csd.pm.proto.pdp.AdjudicationResponse;
import gov.nist.csd.pm.proto.pdp.Decision;
import gov.nist.csd.pm.proto.pdp.ResourceOperationRequest;
import gov.nist.csd.pm.proto.pdp.ResourcePDPGrpc;
import gov.nist.csd.pm.server.shared.ObjectToStruct;
import gov.nist.csd.pm.server.shared.ServerConfig;
import gov.nist.csd.pm.server.shared.UserContextInterceptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import static gov.nist.csd.pm.server.shared.AdjudicateResponseUtil.deny;
import static gov.nist.csd.pm.server.shared.AdjudicateResponseUtil.grant;

public class ResourcePDPService extends ResourcePDPGrpc.ResourcePDPImplBase {

	private PDP pdp;
	private EPPClient epp;

	public ResourcePDPService(PDP pdp, PAP pap, ServerConfig serverConfig) throws PMException {
		this.pdp = pdp;
		this.epp = new EPPClient(pdp, pap, serverConfig);
		this.pdp.addEventSubscriber(this.epp);
	}

	@Override
	public void adjudicateResourceOperation(ResourceOperationRequest request, StreamObserver<AdjudicationResponse> responseObserver) {
		System.out.println("Received operation: " + request);

		UserContext userCtx = new UserContext(
				UserContextInterceptor.getPmUserHeaderValue(),
				UserContextInterceptor.getPmProcessHeaderValue()
		);

		try {
			gov.nist.csd.pm.pdp.adjudication.AdjudicationResponse adjudicationResponse =
					pdp.adjudicateResourceOperation(userCtx, request.getTarget(), request.getOperation());

			if (adjudicationResponse.getDecision() == gov.nist.csd.pm.pdp.adjudication.Decision.GRANT)
				responseObserver.onNext(grant(adjudicationResponse));
			else {
				responseObserver.onNext(deny(adjudicationResponse));
			}

			responseObserver.onCompleted();
		} catch (Exception e) {
			responseObserver.onError(
					Status.INTERNAL
							.withDescription(e.getMessage())
							.withCause(e)
							.asRuntimeException()
			);
		}
	}
}
