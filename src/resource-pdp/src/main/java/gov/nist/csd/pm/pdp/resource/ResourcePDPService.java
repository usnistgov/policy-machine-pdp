package gov.nist.csd.pm.pdp.resource;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.node.Node;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.proto.adjudication.AdjudicateResourceOperationCmd;
import gov.nist.csd.pm.pdp.proto.adjudication.ResourcePDPServiceGrpc;
import gov.nist.csd.pm.pdp.proto.model.NodeProto;
import gov.nist.csd.pm.pdp.shared.auth.UserContextFromHeader;
import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GrpcService
public class ResourcePDPService extends ResourcePDPServiceGrpc.ResourcePDPServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(ResourcePDPService.class);

    private final PDP pdp;
    private final PAP pap;

    public ResourcePDPService(PDP pdp, PAP pap) {
        this.pdp = pdp;
        this.pap = pap;
    }

    @Override
    public void adjudicateResourceOperation(AdjudicateResourceOperationCmd request,
                                            StreamObserver<NodeProto> responseObserver) {
        try {
            UserContext userCtx = UserContextFromHeader.get(pap);
            String operation = request.getOperation();
            long targetId = request.getTargetId();

            logger.info("adjudicating resource operation {} on {} by {}", operation, targetId, userCtx);
            Node node = pdp.adjudicateResourceOperation(userCtx, targetId, operation);
            responseObserver.onNext(ProtoUtil.toNodeProto(node));
            responseObserver.onCompleted();
        } catch (PMException e) {
            responseObserver.onError(e);
        }
    }
}
