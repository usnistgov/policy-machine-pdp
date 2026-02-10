package gov.nist.csd.pm.pdp.resource;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.operation.Operation;
import gov.nist.csd.pm.core.pap.operation.ResourceOperation;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.core.pdp.UnauthorizedException;
import gov.nist.csd.pm.pdp.shared.auth.UserContextFromHeader;
import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;
import gov.nist.csd.pm.proto.v1.pdp.adjudication.AdjudicateOperationResponse;
import gov.nist.csd.pm.proto.v1.pdp.adjudication.OperationRequest;
import gov.nist.csd.pm.proto.v1.pdp.adjudication.ResourceAdjudicationServiceGrpc;
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

    public ResourcePDPService(PDP pdp, PAP pap) {
        this.pdp = pdp;
        this.pap = pap;
    }

    @Override
    public void adjudicateResourceOperation(OperationRequest request,
                                            StreamObserver<AdjudicateOperationResponse> responseObserver) {
        try {
            UserContext userCtx = UserContextFromHeader.get(pap);

            // only allow resource operations to be adjudicated
            Operation<?> operation = pap.query().operations().getOperation(request.getOpName());
            if (!(operation instanceof ResourceOperation<?>)) {
                throw new OperationIsNotResourceOperationException();
            }

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
        } catch (OperationIsNotResourceOperationException e) {
            logger.error("adjudication FAILED", e);
            responseObserver.onError(Status.INVALID_ARGUMENT
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

    static class OperationIsNotResourceOperationException extends Exception {
        public OperationIsNotResourceOperationException() {
            super("only subclasses of ResourceOperation are allowed to be invoked in the resource-pdp");
        }
    }
}
