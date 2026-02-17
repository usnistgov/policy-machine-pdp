package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.impl.grpc.util.FromProtoUtil;
import gov.nist.csd.pm.core.impl.grpc.util.ToProtoUtil;
import gov.nist.csd.pm.core.pdp.UnauthorizedException;
import gov.nist.csd.pm.proto.v1.pdp.adjudication.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@GrpcService
public class AdminAdjudicationService extends AdminAdjudicationServiceGrpc.AdminAdjudicationServiceImplBase {

	private static final Logger logger = LoggerFactory.getLogger(AdminAdjudicationService.class);

	private final Adjudicator adjudicator;

	public AdminAdjudicationService(Adjudicator adjudicator) {
		this.adjudicator = adjudicator;
	}

	@Override
	public void adjudicateOperation(OperationRequest request,
	                                StreamObserver<AdjudicateOperationResponse> responseObserver) {
		try {
			String opName = request.getName();
			Map<String, Object> args = FromProtoUtil.fromValueMap(request.getArgs());
			logger.info("adjudicating operation {} with args {}", opName, args);

			Object result = adjudicator.adjudicateOperation(opName, args);

			AdjudicateOperationResponse.Builder builder = AdjudicateOperationResponse.newBuilder();
			if (result != null) {
				builder.setValue(ToProtoUtil.toValueProto(result));
			}

			responseObserver.onNext(builder.build());
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

	@Override
	public void adjudicateRoutine(RoutineRequest request, StreamObserver<AdjudicateRoutineResponse> responseObserver) {
		try {
			adjudicator.adjudicateRoutine(request.getOperationsList());
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

	@Override
	public void executePML(ExecutePMLRequest request, StreamObserver<ExecutePMLResponse> responseObserver) {
		try {
			Object result = adjudicator.executePML(request.getPml());

			ExecutePMLResponse.Builder builder = ExecutePMLResponse.newBuilder();
			if (result != null) {
				builder.setValue(ToProtoUtil.toValueProto(result));
			}

			responseObserver.onNext(builder.build());
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
