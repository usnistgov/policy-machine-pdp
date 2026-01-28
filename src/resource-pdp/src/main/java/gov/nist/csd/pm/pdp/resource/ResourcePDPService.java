package gov.nist.csd.pm.pdp.resource;

import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.core.pdp.UnauthorizedException;
import gov.nist.csd.pm.pdp.shared.auth.UserContextFromHeader;
import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;
import gov.nist.csd.pm.proto.v1.adjudication.AdjudicateOperationResponse;
import gov.nist.csd.pm.proto.v1.adjudication.OperationRequest;
import gov.nist.csd.pm.proto.v1.adjudication.ResourceAdjudicationServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GrpcService
public class ResourcePDPService extends ResourceAdjudicationServiceGrpc.ResourceAdjudicationServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(ResourcePDPService.class);

    private final PDP pdp;
    private final PAP pap;
    private final RevisionCatchUpGate revisionCatchUpGate;

    public ResourcePDPService(PDP pdp, PAP pap, RevisionCatchUpGate revisionCatchUpGate) {
        this.pdp = pdp;
        this.pap = pap;
        this.revisionCatchUpGate = revisionCatchUpGate;
    }

    @Override
    public void adjudicateResourceOperation(OperationRequest request,
                                            StreamObserver<AdjudicateOperationResponse> responseObserver) {
        if (revisionCatchUpGate.isClosed()) {
            responseObserver.onError(Status.UNAVAILABLE
                                             .withDescription("the resource PDP timed out waiting for the last EPP revision")
                                             .asRuntimeException());
            return;
        }

        try {
            UserContext userCtx = UserContextFromHeader.get(pap);

            Object result = pdp.adjudicateOperation(
                    userCtx,
                    request.getOpName(),
                    ProtoUtil.valueMapToObjectMap(request.getArgs())
            );

            responseObserver.onNext(AdjudicateOperationResponse.newBuilder().setValue(ProtoUtil.objectToValue(result)).build());
            responseObserver.onCompleted();
        } catch (UnauthorizedException e) {
            logger.error("adjudication UNAUTHORIZED: {}", e.getMessage());
            responseObserver.onError(Status.PERMISSION_DENIED
                                             .withDescription(e.getMessage())
                                             .withCause(e)
                                             .asRuntimeException());
        } catch (Exception e) {
            logger.error("adjudication FAILED", e);
            responseObserver.onError(Status.INTERNAL
                                             .withDescription(e.getMessage())
                                             .withCause(e)
                                             .asRuntimeException());
        }
    }
}
