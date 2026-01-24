package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.pdp.UnauthorizedException;
import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;
import gov.nist.csd.pm.proto.v1.adjudication.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@GrpcService
public class AdjudicationService extends AdminAdjudicationServiceGrpc.AdminAdjudicationServiceImplBase {

	private static final Logger logger = LoggerFactory.getLogger(AdjudicationService.class);

	private final Adjudicator adjudicator;

	public AdjudicationService(Adjudicator adjudicator) {
		this.adjudicator = adjudicator;
	}

	@Override
	public void adjudicateOperation(OperationRequest request,
	                                StreamObserver<AdjudicateOperationResponse> responseObserver) {
		try {
			String opName = request.getOpName();
			Map<String, Object> args = ProtoUtil.valueMapToObjectMap(request.getArgs());

			Object result = adjudicator.adjudicateOperation(opName, args);

			responseObserver.onNext(AdjudicateOperationResponse.newBuilder()
					                        .setValue(ProtoUtil.objectToValue(result))
					                        .build());
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED
					                         .withDescription(e.getMessage())
					                         .withCause(e)
					                         .asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL
					                         .withDescription(e.getMessage())
					                         .withCause(e)
					                         .asRuntimeException());
		}
	}

	@Override
	public void adjudicateRoutine(RoutineRequest request, StreamObserver<AdjudicateRoutineResponse> responseObserver) {
		try {
			adjudicator.adjudicateRoutine(request.getCommandsList());
			responseObserver.onNext(AdjudicateRoutineResponse.newBuilder().build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED
					                         .withDescription(e.getMessage())
					                         .withCause(e)
					                         .asRuntimeException());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL
					                         .withDescription(e.getMessage())
					                         .withCause(e)
					                         .asRuntimeException());
		}
	}
}
