package gov.nist.csd.pm.server.shared.protobuf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.common.graph.node.Node;
import gov.nist.csd.pm.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.common.prohibition.Prohibition;
import gov.nist.csd.pm.pap.query.model.explain.*;
import gov.nist.csd.pm.pdp.adjudication.AdjudicationResponse;
import gov.nist.csd.pm.pdp.proto.model.*;
import gov.nist.csd.pm.pdp.proto.modify.AdjudicateDecision;
import gov.nist.csd.pm.pdp.proto.modify.AdjudicateGenericResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static gov.nist.csd.pm.server.shared.protobuf.ProtoUtil.buildExplainResponse;
import static gov.nist.csd.pm.server.shared.protobuf.ProtoUtil.toNodeProto;

public class AdjudicationResponseUtil {

    public static AdjudicateGenericResponse deny(AdjudicationResponse adjudicationResponse) {
        AdjudicateGenericResponse.Builder response = AdjudicateGenericResponse.newBuilder();
        response.setDecision(AdjudicateDecision.DENY);
        response.setExplain(buildExplainResponse(adjudicationResponse.getExplain()));

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
