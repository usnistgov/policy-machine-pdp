package gov.nist.csd.pm.server.admin.epp;

import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.epp.EPP;
import gov.nist.csd.pm.epp.proto.EPPGrpc;
import gov.nist.csd.pm.epp.proto.EPPResponse;
import gov.nist.csd.pm.epp.proto.EventContextProto;
import gov.nist.csd.pm.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.server.admin.pdp.Adjudicator;
import gov.nist.csd.pm.server.shared.protobuf.EventContextUtil;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@GrpcService
public class EPPService extends EPPGrpc.EPPImplBase {

    private static final Logger logger = LoggerFactory.getLogger(EPPService.class);

    private final Adjudicator adjudicator;

    public EPPService(Adjudicator adjudicator) {
        this.adjudicator = adjudicator;
    }

    @Override
    public void processEvent(EventContextProto request, StreamObserver<EPPResponse> responseObserver) {
	    logger.info("Processing event {}", request);

        try {
            // since this adjudication function only processes an event, we can ignore the user
            // if the PDP is used at all, the user will be the author of any matched obligations
            // not the user that invoked processEvent
            List<PMEvent> sideEffectEvents = adjudicator.adjudicateTransaction(ctx -> {
                try {
                    EPP epp = new EPP(ctx.pdp(), ctx.pap());

                    // subscribe to PDP so the EPP will also process any operations executed during processEvent
                    epp.subscribeTo(ctx.pdp());
                    epp.processEvent(EventContextUtil.fromProto(request));
                } catch (PMException e) {
                    throw new RuntimeException(e);
                }
            });

            responseObserver.onNext(EPPResponse.newBuilder().addAllEvents(sideEffectEvents).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            responseObserver.onError(e);
        }
    }
}
