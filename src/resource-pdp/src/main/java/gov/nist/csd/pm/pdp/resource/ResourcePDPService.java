package gov.nist.csd.pm.pdp.resource;

import gov.nist.csd.pm.core.common.graph.node.Node;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.core.pdp.UnauthorizedException;
import gov.nist.csd.pm.pdp.shared.auth.UserContextFromHeader;
import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;
import gov.nist.csd.pm.proto.v1.adjudication.ResourceAdjudicationServiceGrpc;
import gov.nist.csd.pm.proto.v1.adjudication.ResourceOperationCmd;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.grpc.Status;

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
    public void adjudicateResourceOperation(ResourceOperationCmd request,
                                            StreamObserver<gov.nist.csd.pm.proto.v1.model.Node> responseObserver) {
        try {
            UserContext userCtx = UserContextFromHeader.get(pap);
            String operation = request.getOperation();
            long targetId = 0;
            if (request.getTargetCase() == ResourceOperationCmd.TargetCase.ID) {
                targetId = request.getId();
            } else if (request.getTargetCase() == ResourceOperationCmd.TargetCase.NAME) {
                targetId = pap.query().graph().getNodeId(request.getName());
            } else {
                responseObserver.onError(Status.INVALID_ARGUMENT
                                                 .withDescription("ID or name not set")
                                                 .asRuntimeException());
            }

            logger.info("adjudicating resource operation {} on {} by {}", operation, targetId, userCtx);
            Node node = pdp.adjudicateResourceOperation(userCtx, targetId, operation);
            responseObserver.onNext(ProtoUtil.toNodeProto(node));
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
