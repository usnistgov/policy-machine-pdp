package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.ngac.pm.core.grpc.util.FromProtoUtil;
import gov.nist.ngac.pm.core.grpc.util.ToProtoUtil;
import gov.nist.ngac.pm.core.pap.operation.Operation;
import gov.nist.ngac.pm.core.pap.operation.ResourceOperation;
import gov.nist.ngac.pm.core.pdp.UnauthorizedException;
import gov.nist.ngac.pm.proto.v1.pdp.adjudication.AdjudicateOperationResponse;
import gov.nist.ngac.pm.proto.v1.pdp.adjudication.OperationRequest;
import gov.nist.ngac.pm.proto.v1.pdp.adjudication.ResourceAdjudicationServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@GrpcService
public class ResourceAdjudicationService extends ResourceAdjudicationServiceGrpc.ResourceAdjudicationServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(ResourceAdjudicationService.class);

    private final AdminAdjudicator adjudicator;

    public ResourceAdjudicationService(AdminAdjudicator adjudicator) {
        this.adjudicator = adjudicator;
    }

    @Override
    public void adjudicateResourceOperation(OperationRequest request,
                                            StreamObserver<AdjudicateOperationResponse> responseObserver) {
        try {
            String opName = request.getName();

            // only allow resource operations to be adjudicated through this endpoint
            Operation<?> operation = adjudicator.adjudicateQuery((pap, userCtx) -> pap.query().operations().getOperation(opName));
            if (!(operation instanceof ResourceOperation<?>)) {
                throw new OperationIsNotResourceOperationException();
            }

            Map<String, Object> args = FromProtoUtil.fromValueMap(request.getArgsMap());
            Object result = adjudicator.adjudicateOperation(opName, args);

            AdjudicateOperationResponse.Builder b = AdjudicateOperationResponse.newBuilder();
            if (result != null) {
                b.setValue(ToProtoUtil.toValueProto(result));
            }
            responseObserver.onNext(b.build());
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
