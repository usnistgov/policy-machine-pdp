package gov.nist.csd.pm.server.admin.epp;

import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.epp.EPP;
import gov.nist.csd.pm.pdp.adjudication.AdjudicationResponse;
import gov.nist.csd.pm.proto.epp.EPPGrpc;
import gov.nist.csd.pm.proto.epp.EPPResponse;
import gov.nist.csd.pm.proto.epp.EventContext;
import gov.nist.csd.pm.server.admin.pdp.Adjudicator;
import gov.nist.csd.pm.server.shared.protobuf.EventContextUtil;
import io.grpc.stub.StreamObserver;

public class EPPService extends EPPGrpc.EPPImplBase {

    private final Adjudicator<AdjudicationResponse> adjudicator;

    public EPPService(Adjudicator<AdjudicationResponse> adjudicator) {
        this.adjudicator = adjudicator;
    }

    @Override
    public void processEvent(EventContext request, StreamObserver<EPPResponse> responseObserver) {
        try {
            adjudicator.adjudicate((ctx) -> {
                try {
                    EPP epp = new EPP(ctx.pdp(), ctx.pap());

                    // subscribe to PDP so any operations executed during processEvent will also be processed by the EPP
                    epp.subscribeTo(ctx.pdp());
                    epp.processEvent(EventContextUtil.fromProto(request));

                    return null;
                } catch (PMException e) {
                    throw new RuntimeException(e);
                }
            });

            responseObserver.onNext(EPPResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }
}
