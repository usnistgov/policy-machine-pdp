package gov.nist.csd.pm.server.admin.pdp;

import static gov.nist.csd.pm.pdp.adjudication.Decision.GRANT;
import static gov.nist.csd.pm.server.shared.protobuf.EventContextUtil.fromProtoOperands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.common.exception.PMRuntimeException;
import gov.nist.csd.pm.pdp.adjudication.AdjudicationResponse;
import gov.nist.csd.pm.pdp.adjudication.OperationRequest;
import gov.nist.csd.pm.proto.pdp.AdminOperationRequest;
import gov.nist.csd.pm.proto.pdp.AdminPDPGrpc;
import gov.nist.csd.pm.proto.pdp.AdminRoutineRequest;
import gov.nist.csd.pm.proto.pdp.ExecutePmlRequest;
import gov.nist.csd.pm.proto.pdp.NamedAdminRoutineRequest;
import gov.nist.csd.pm.proto.pdp.PDPResponse;
import gov.nist.csd.pm.server.shared.protobuf.PDPResponseUtil;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class PDPService extends AdminPDPGrpc.AdminPDPImplBase {

    private final Adjudicator<AdjudicationResponse> adjudicator;

    public PDPService(Adjudicator<AdjudicationResponse> adjudicator) {
        this.adjudicator = adjudicator;
    }

    @Override
    public void adjudicateAdminOperation(AdminOperationRequest request,
                                         StreamObserver<PDPResponse> responseObserver) {
        try {
            AdjudicationResponse response = adjudicator.adjudicate(
                (ctx) -> {
                    try {
                        return ctx.pdp().adjudicateAdminOperation(
                            ctx.userCtx(),
                            request.getOpName(),
                            fromProtoOperands(request.getOperandsList())
                        );
                    } catch (PMException e) {
                        throw new PMRuntimeException(e);
                    }
                });

            success(responseObserver, response);
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void adjudicateAdminRoutine(AdminRoutineRequest request,
                                       StreamObserver<PDPResponse> responseObserver) {
        try {
            AdjudicationResponse response = adjudicator.adjudicate(
                (ctx) -> {
                    List<OperationRequest> requests = new ArrayList<>();
                    for (AdminOperationRequest opRequest : request.getOpsList()) {
                        requests.add(new OperationRequest(opRequest.getOpName(),
                            fromProtoOperands(opRequest.getOperandsList())));
                    }

                    try {
                        return ctx.pdp().adjudicateAdminRoutine(ctx.userCtx(), requests);
                    } catch (PMException e) {
                        throw new PMRuntimeException(e);
                    }
                });

            success(responseObserver, response);
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void adjudicateNamedAdminRoutine(NamedAdminRoutineRequest request,
                                            StreamObserver<PDPResponse> responseObserver) {
        try {
            AdjudicationResponse response = adjudicator.adjudicate(
                (ctx) -> {
                    try {
                        return ctx.pdp().adjudicateAdminRoutine(
                            ctx.userCtx(),
                            request.getName(),
                            fromProtoOperands(request.getOperandsList())
                        );
                    } catch (PMException e) {
                        throw new PMRuntimeException(e);
                    }
                });

            success(responseObserver, response);
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void executePml(ExecutePmlRequest request,
                           StreamObserver<PDPResponse> responseObserver) {
        try {
            AdjudicationResponse response = adjudicator.adjudicate(
                (ctx) -> {
                    try {
                        ctx.pdp().executePML(ctx.userCtx(), request.getPml());

                        return new AdjudicationResponse(GRANT);
                    } catch (PMException e) {
                        throw new PMRuntimeException(e);
                    }
                });

            success(responseObserver, response);
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    private void success(StreamObserver<PDPResponse> responseObserver,
                         AdjudicationResponse response) throws InvalidProtocolBufferException,
                                                               JsonProcessingException {
        PDPResponse grant = PDPResponseUtil.grant(response);
        responseObserver.onNext(grant);
        responseObserver.onCompleted();
    }
}
