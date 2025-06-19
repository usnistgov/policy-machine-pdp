package gov.nist.csd.pm.pdp.admin.epp;

import com.google.protobuf.Empty;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.epp.EPP;
import gov.nist.csd.pm.pdp.admin.pdp.Adjudicator;
import gov.nist.csd.pm.pdp.shared.protobuf.EventContextUtil;
import gov.nist.csd.pm.proto.v1.epp.EPPServiceGrpc;
import gov.nist.csd.pm.proto.v1.epp.GenericEventContext;
import gov.nist.csd.pm.proto.v1.epp.PolicyEventContext;
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
	public void processPolicyEvent(PolicyEventContext request, StreamObserver<Empty> responseObserver) {
		try {
			adjudicator.adjudicateTransaction(ctx -> {
				try {
					EPP epp = new EPP(ctx.pdp(), ctx.pap());

					// subscribe to PDP so the EPP will also process any operations executed during processEvent
					epp.subscribeTo(ctx.pdp());
					epp.processEvent(EventContextUtil.fromProto(request));
				} catch (PMException e) {
					throw new RuntimeException(e);
				}
			});
		} catch (PMException e) {
			responseObserver.onError(Status.INTERNAL
					                         .withDescription(e.getMessage())
					                         .withCause(e)
					                         .asRuntimeException());
		}
	}

	@Override
	public void processGenericEvent(GenericEventContext request, StreamObserver<Empty> responseObserver) {
		// TODO
		super.processGenericEvent(request, responseObserver);
	}
}
