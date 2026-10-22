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
 * licensed to NIST. To the extent that NIST holds copyright in this software, it is
 * being made available under the Creative Commons Attribution 4.0 International
 * license (CC BY 4.0). The disclaimers of the CC BY 4.0 license apply to all parts
 * of the software developed or licensed by NIST.
 *
 * ACCESS THE FULL CC BY 4.0 LICENSE HERE:
 * https://creativecommons.org/licenses/by/4.0/legalcode
 */

package gov.nist.csd.pm.pdp.resource;

import gov.nist.ngac.pm.core.grpc.util.FromProtoUtil;
import gov.nist.ngac.pm.core.grpc.util.ToProtoUtil;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.query.model.context.UserContext;
import gov.nist.ngac.pm.core.pdp.UnauthorizedException;
import gov.nist.csd.pm.pdp.shared.adjudication.OperationNotSupportedException;
import gov.nist.csd.pm.pdp.shared.auth.UserContextResolver;
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
public class ResourcePDPService extends ResourceAdjudicationServiceGrpc.ResourceAdjudicationServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(ResourcePDPService.class);

    private final ResourceOperationAdjudicator adjudicator;
    private final PAP pap;
    private final UserContextResolver userContextResolver;

    public ResourcePDPService(ResourceOperationAdjudicator adjudicator, PAP pap, UserContextResolver userContextResolver) {
        this.adjudicator = adjudicator;
        this.pap = pap;
        this.userContextResolver = userContextResolver;
    }

    @Override
    public void adjudicateResourceOperation(OperationRequest request,
                                            StreamObserver<AdjudicateOperationResponse> responseObserver) {
        try {
            UserContext userCtx = userContextResolver.resolve(pap);
            Map<String, Object> args = FromProtoUtil.fromValueMap(request.getArgs());
            Object result = adjudicator.adjudicateOperation(userCtx, request.getName(), args);

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
        } catch (OperationNotSupportedException e) {
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
}
