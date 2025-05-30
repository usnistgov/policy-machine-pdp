package gov.nist.csd.pm.pdp.shared.protobuf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;

public class ObjectToStruct {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	public static Struct convert(Object o) throws InvalidProtocolBufferException, JsonProcessingException {
		if (o == null) {
			return Struct.newBuilder().build();
		}

		String jsonString = objectMapper.writeValueAsString(o);

		// Convert the JSON string to a Protobuf Struct
		Struct.Builder structBuilder = Struct.newBuilder();
		JsonFormat.parser().merge(jsonString, structBuilder);

		return structBuilder.build();
	}
}
