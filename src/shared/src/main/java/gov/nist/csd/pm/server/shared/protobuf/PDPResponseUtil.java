package gov.nist.csd.pm.server.shared.protobuf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.pdp.adjudication.AdjudicationResponse;
import gov.nist.csd.pm.proto.pdp.Decision;
import gov.nist.csd.pm.proto.pdp.PDPResponse;

public class PDPResponseUtil {

    public static PDPResponse deny(AdjudicationResponse adjudicationResponse)
        throws InvalidProtocolBufferException, JsonProcessingException {
        PDPResponse.Builder response = PDPResponse.newBuilder();
        response.setDecision(Decision.DENY);
        response.setExplain(ObjectToStruct.convert(adjudicationResponse.getExplain()));
        response.setValue(ObjectToStruct.convert(adjudicationResponse.getValue()));

        return response.build();
    }

    public static PDPResponse grant(AdjudicationResponse adjudicationResponse)
        throws InvalidProtocolBufferException, JsonProcessingException {
        PDPResponse.Builder response = PDPResponse.newBuilder();
        response.setDecision(Decision.GRANT);
        response.setExplain(ObjectToStruct.convert(adjudicationResponse.getExplain()));
        response.setValue(ObjectToStruct.convert(adjudicationResponse.getValue()));

        return response.build();
    }
}
