package gov.nist.csd.pm.pdp.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.node.Node;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.core.pdp.adjudication.AdjudicationResponse;
import gov.nist.csd.pm.core.pdp.adjudication.Decision;
import gov.nist.csd.pm.pdp.proto.adjudication.AdjudicateGenericResponse;
import gov.nist.csd.pm.pdp.proto.adjudication.AdjudicateResourceOperationCmd;
import gov.nist.csd.pm.pdp.proto.adjudication.AdjudicateResourceOperationResponse;
import gov.nist.csd.pm.pdp.proto.adjudication.ResourcePDPServiceGrpc;
import gov.nist.csd.pm.pdp.shared.auth.UserContextFromHeader;
import gov.nist.csd.pm.pdp.shared.protobuf.AdjudicationResponseUtil;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil.toNodeProto;

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
                                            StreamObserver<AdjudicateResourceOperationResponse> responseObserver) {
        try {
            UserContext userCtx = UserContextFromHeader.get(pap);

            String operation = request.getOperation();
            long targetId = request.getTargetId();

            logger.info("adjudicating resource operation {} on {} by {}", operation, targetId, userCtx);
            AdjudicationResponse adjudicationResponse = pdp.adjudicateResourceOperation(userCtx, targetId, operation);
            if (adjudicationResponse.getDecision() == Decision.GRANT) {
                logger.debug("adjudication granted");
                responseObserver.onNext(grant(targetId, adjudicationResponse));
            } else {
                logger.debug("adjudication denied {}", adjudicationResponse.getExplain());
                responseObserver.onNext(deny(adjudicationResponse));
            }

            responseObserver.onCompleted();
        } catch (PMException | InvalidProtocolBufferException | JsonProcessingException e) {
            responseObserver.onError(e);
        }
    }

    private AdjudicateResourceOperationResponse grant(long targetId, AdjudicationResponse adjudicationResponse) throws PMException, InvalidProtocolBufferException, JsonProcessingException {
        AdjudicateGenericResponse grant = AdjudicationResponseUtil.grant(adjudicationResponse);
        Node node = pap.query().graph().getNodeById(targetId);

        return AdjudicateResourceOperationResponse.newBuilder()
                .setDecision(grant.getDecision())
                .setNode(toNodeProto(node))
                .build();

    }

    private AdjudicateResourceOperationResponse deny(AdjudicationResponse adjudicationResponse) throws PMException {
        AdjudicateGenericResponse deny = AdjudicationResponseUtil.deny(adjudicationResponse);
        return AdjudicateResourceOperationResponse.newBuilder()
                .setDecision(deny.getDecision())
                .setExplain(deny.getExplain())
                .build();
    }
}
