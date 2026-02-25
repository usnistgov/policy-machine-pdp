package gov.nist.csd.pm.pdp.admin.epp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.impl.grpc.util.FromProtoUtil;
import gov.nist.csd.pm.pdp.admin.pdp.Adjudicator;
import gov.nist.csd.pm.proto.v1.epp.EPPServiceGrpc;
import gov.nist.csd.pm.proto.v1.epp.EventContext;
import gov.nist.csd.pm.proto.v1.epp.ProcessEventResponse;
import gov.nist.csd.pm.proto.v1.model.Value;
import gov.nist.csd.pm.proto.v1.model.ValueMap;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GrpcService
public class EPPService extends EPPServiceGrpc.EPPServiceImplBase {

	private static final Logger logger = LoggerFactory.getLogger(EPPService.class);

	private final Adjudicator adjudicator;

	public EPPService(Adjudicator adjudicator) {
		this.adjudicator = adjudicator;
	}

	@Override
	public void processEvent(EventContext request, StreamObserver<ProcessEventResponse> responseObserver) {
		try {
			long lastRevision = adjudicator.adjudicateTransaction(ctx -> {
				try {
					ctx.epp().processEvent(FromProtoUtil.fromEventContextProto(request));
				} catch (PMException e) {
					// Can't throw checked from the lambda; wrap it.
					throw new RuntimeException(e);
				}
			});

			ProcessEventResponse.Builder resp = ProcessEventResponse.newBuilder();
			if (lastRevision > 0) {
				resp.setResult(
						ValueMap.newBuilder()
								.putValues(
										"last_event_revision",
										Value.newBuilder().setInt64Value(lastRevision).build()
								)
								.build()
				);
			}

			responseObserver.onNext(resp.build());
			responseObserver.onCompleted();
		} catch (RuntimeException | PMException e) {
			responseObserver.onError(Status.INTERNAL
					                         .withDescription(e.getMessage())
					                         .withCause(e)
					                         .asRuntimeException());
		}
	}
}
