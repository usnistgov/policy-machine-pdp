package gov.nist.csd.pm.server.resource;

import static gov.nist.csd.pm.server.shared.protobuf.PDPResponseUtil.deny;
import static gov.nist.csd.pm.server.shared.protobuf.PDPResponseUtil.grant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.common.graph.node.Node;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pdp.PDP;
import gov.nist.csd.pm.proto.pdp.PDPResponse;
import gov.nist.csd.pm.proto.pdp.ResourceOperationRequestById;
import gov.nist.csd.pm.proto.pdp.ResourceOperationRequestByName;
import gov.nist.csd.pm.proto.pdp.ResourcePDPGrpc;
import gov.nist.csd.pm.server.resource.epp.EPPClient;
import gov.nist.csd.pm.server.shared.auth.UserContextInterceptor;
import gov.nist.csd.pm.server.shared.config.PDPConfig;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class ResourcePDPService extends ResourcePDPGrpc.ResourcePDPImplBase {

    private PDP pdp;
    private PAP pap;
    private EPPClient epp;

    public ResourcePDPService(PDP pdp, PAP pap, PDPConfig config) throws PMException {
        this.pdp = pdp;
        this.pap = pap;
        this.epp = new EPPClient(pdp, pap, config);
        this.pdp.addEventSubscriber(this.epp);
    }

    @Override
    public void adjudicateResourceOperationById(ResourceOperationRequestById request,
                                                StreamObserver<PDPResponse> responseObserver) {
        try {
            adjudicateResourceOperation(request.getOperation(), request.getTarget(), responseObserver);
        } catch (PMException | InvalidProtocolBufferException | JsonProcessingException e) {
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription(e.getMessage())
                    .withCause(e)
                    .asRuntimeException()
            );
        }
    }

    @Override
    public void adjudicateResourceOperationByName(ResourceOperationRequestByName request,
                                                  StreamObserver<PDPResponse> responseObserver) {
        System.out.println("resource operation " + request);
        try {
            Node node = pap.query().graph().getNodeByName(request.getTarget());

            adjudicateResourceOperation(request.getOperation(), node.getId(), responseObserver);
        } catch (PMException | InvalidProtocolBufferException | JsonProcessingException e) {
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription(e.getMessage())
                    .withCause(e)
                    .asRuntimeException()
            );
        }
    }

    private void adjudicateResourceOperation(String operation,
                                             long targetId,
                                             StreamObserver<PDPResponse> responseObserver) throws PMException,
                                                                                                  InvalidProtocolBufferException,
                                                                                                  JsonProcessingException {
        String username = UserContextInterceptor.getPmUserHeaderValue();
        Node node = pap.query().graph().getNodeByName(username);

        UserContext userCtx = new UserContext(
            node.getId(),
            UserContextInterceptor.getPmProcessHeaderValue()
        );

        gov.nist.csd.pm.pdp.adjudication.AdjudicationResponse adjudicationResponse =
            pdp.adjudicateResourceOperation(userCtx, targetId, operation);

        if (adjudicationResponse.getDecision() == gov.nist.csd.pm.pdp.adjudication.Decision.GRANT) {
            responseObserver.onNext(grant(adjudicationResponse));
        } else {
            responseObserver.onNext(deny(adjudicationResponse));
        }

        responseObserver.onCompleted();
    }
}
