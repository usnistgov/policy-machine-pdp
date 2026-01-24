package gov.nist.csd.pm.pdp.admin.epp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.epp.EPP;
import gov.nist.csd.pm.pdp.admin.pdp.Adjudicator;
import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;
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
					EPP epp = new EPP(ctx.pdp(), ctx.pap());

					// subscribe to PDP so the EPP will also process any operations executed during processEvent
					epp.subscribeTo(ctx.pdp());
					epp.processEvent(ProtoUtil.fromEventContextProto(request));
				} catch (PMException e) {
					throw new RuntimeException(e);
				}
			});

			if (lastRevision > 0) {
				responseObserver.onNext(
						ProcessEventResponse.newBuilder()
								.setResult(
										ValueMap.newBuilder()
												.putValues(
														"last_event_revision",
														Value.newBuilder().setInt64Value(lastRevision).build()
												)
								).build()
				);
			} else {
				responseObserver.onNext(ProcessEventResponse.newBuilder().build());
			}
		} catch (PMException e) {
			responseObserver.onError(Status.INTERNAL
					                         .withDescription(e.getMessage())
					                         .withCause(e)
					                         .asRuntimeException());
		}
	}
}
