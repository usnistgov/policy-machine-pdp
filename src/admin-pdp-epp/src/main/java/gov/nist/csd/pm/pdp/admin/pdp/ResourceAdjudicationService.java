/*
 * This Software (Policy Machine) is being made available as a public service by the
 * National Institute of Standards and Technology (NIST), an Agency of the United
 * States Department of Commerce. This software was developed in part by employees of
 * NIST and in part by NIST contractors. Copyright in portions of this software that
 * were developed by NIST contractors has been licensed or assigned to NIST. Pursuant
 * to Title 17 United States Code Section 105, works of NIST employees are not
 * subject to copyright protection in the United States. However, NIST may hold
 * international copyright in software created by its employees and domestic
 * copyright (or licensing rights) in portions of software that were assigned or
 * licensed to NIST.
 *
 * This file is part of the admin-pdp-epp module, which compiles against
 * and embeds org.neo4j:neo4j (GPLv3, Community Edition). As a combined work, this
 * module is distributed under the GNU General Public License v3.0, not the CC BY 4.0
 * license used elsewhere in this repository. See admin-pdp-epp/LICENSE for the full text.
 */

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
