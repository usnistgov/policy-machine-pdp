package gov.nist.csd.pm.pdp.shared.protobuf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.query.PolicyQuery;
import gov.nist.csd.pm.core.pdp.adjudication.AdjudicationResponse;
import gov.nist.csd.pm.pdp.proto.adjudication.AdjudicateDecision;
import gov.nist.csd.pm.pdp.proto.adjudication.AdjudicateGenericResponse;

import static gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil.buildExplainResponse;

public class AdjudicationResponseUtil {

    public static AdjudicateGenericResponse deny(AdjudicationResponse adjudicationResponse, PolicyQuery query) {
        AdjudicateGenericResponse.Builder response = AdjudicateGenericResponse.newBuilder();
        response.setDecision(AdjudicateDecision.DENY);
        response.setExplain(buildExplainResponse(adjudicationResponse.getExplain(), query));

        return response.build();
    }

    public static AdjudicateGenericResponse grant(AdjudicationResponse adjudicationResponse)
		    throws InvalidProtocolBufferException, JsonProcessingException {
        AdjudicateGenericResponse.Builder response = AdjudicateGenericResponse.newBuilder();
        response.setDecision(AdjudicateDecision.GRANT);
        response.setValue(ObjectToStruct.convert(adjudicationResponse.getValue()));

        return response.build();
    }
}
