package gov.nist.csd.pm.server.shared;

import com.google.protobuf.ByteString;
import gov.nist.csd.pm.epp.proto.OperandEntry;
import gov.nist.csd.pm.epp.proto.StringList;
import gov.nist.csd.pm.impl.neo4j.memory.pap.store.Neo4jUtil;
import gov.nist.csd.pm.pap.exception.PMException;
import gov.nist.csd.pm.pap.obligation.EventContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventContextUtil {

	public static gov.nist.csd.pm.epp.proto.EventContext toProto(EventContext eventContext) throws PMException {
		return gov.nist.csd.pm.epp.proto.EventContext.newBuilder()
				.setUser(eventContext.user())
				.setProcess(eventContext.process())
				.setOpName(eventContext.opName())
				.addAllOperands(toProtoOperands(eventContext.operands()))
				.build();
	}

	public static EventContext fromProto(gov.nist.csd.pm.epp.proto.EventContext protoCtx) throws PMException {
		return new EventContext(
				protoCtx.getUser(),
				protoCtx.getProcess(),
				protoCtx.getOpName(),
				fromProtoOperands(protoCtx.getOperandsList())
		);
	}

	public static Map<String, Object> fromProtoOperands(List<OperandEntry> operandsList) {
		Map<String, Object> operandsMap = new HashMap<>();
		for (OperandEntry operandEntry : operandsList) {
			Object operandObj;

			if (operandEntry.getValueCase() == OperandEntry.ValueCase.LISTVALUE) {
				operandObj = operandEntry.getListValue();
			} else {
				operandObj = operandEntry.getStringValue();
			}

			operandsMap.put(operandEntry.getName(), operandObj);
		}

		return operandsMap;
	}

	public static List<OperandEntry> toProtoOperands(Map<String, Object> operands) throws PMException {
		List<OperandEntry> operandEntries = new ArrayList<>();
		for (Map.Entry<String, Object> entry : operands.entrySet()) {
			// serialize the value of the operand to a hex string byte array defined in Neo4j package
			OperandEntry.Builder operandEntryBuilder = OperandEntry.newBuilder()
					.setName(entry.getKey());

			if (entry.getValue() instanceof String) {
				operandEntryBuilder.setStringValue((String) entry.getValue());
			} else if (entry.getValue() instanceof Iterable<?>) {
				// build StringList
				StringList stringList = StringList.newBuilder()
						.addAllValues((Iterable<String>) entry.getValue())
						.build();

				operandEntryBuilder.setListValue(stringList);
			}

			operandEntries.add(operandEntryBuilder.build());
		}

		return operandEntries;
	}

}
