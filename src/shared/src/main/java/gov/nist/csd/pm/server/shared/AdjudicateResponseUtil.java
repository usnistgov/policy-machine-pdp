package gov.nist.csd.pm.server.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import gov.nist.csd.pm.proto.pdp.AdjudicationResponse;
import gov.nist.csd.pm.proto.pdp.Decision;

public class AdjudicateResponseUtil {

	public static AdjudicationResponse deny(gov.nist.csd.pm.pdp.adjudication.AdjudicationResponse adjudicationResponse) throws InvalidProtocolBufferException, JsonProcessingException {
		AdjudicationResponse.Builder response = AdjudicationResponse.newBuilder();
		response.setDecision(Decision.DENY);
		response.setExplain(ObjectToStruct.convert(adjudicationResponse.getExplain()));
		response.setValue(ObjectToStruct.convert(adjudicationResponse.getValue()));

		return response.build();
	}

	public static AdjudicationResponse grant(gov.nist.csd.pm.pdp.adjudication.AdjudicationResponse adjudicationResponse) throws InvalidProtocolBufferException, JsonProcessingException {
		AdjudicationResponse.Builder response = AdjudicationResponse.newBuilder();
		response.setDecision(Decision.GRANT);
		response.setExplain(ObjectToStruct.convert(adjudicationResponse.getExplain()));
		response.setValue(ObjectToStruct.convert(adjudicationResponse.getValue()));

		return response.build();
	}
}
