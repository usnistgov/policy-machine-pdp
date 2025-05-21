package gov.nist.csd.pm.server.shared.protobuf;

import gov.nist.csd.pm.common.event.EventContext;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.epp.proto.EventContextArg;
import gov.nist.csd.pm.epp.proto.EventContextProto;
import gov.nist.csd.pm.pdp.proto.model.StringList;
import gov.nist.csd.pm.epp.proto.EventContextArg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventContextUtil {

    public static EventContextProto toProto(EventContext eventContext) throws PMException {
        EventContextProto.Builder builder = EventContextProto.newBuilder()
            .setUser(eventContext.getUser())
            .setOpName(eventContext.getOpName())
            .addAllArgs(toProtoEventContextArgs(eventContext.getArgs()));

        if (eventContext.getProcess() != null && !eventContext.getProcess().isEmpty()) {
            builder.setProcess(eventContext.getProcess());
        }

        return builder.build();
    }

    public static EventContext fromProto(EventContextProto protoCtx) throws PMException {
        return new EventContext(
                protoCtx.getUser(),
                protoCtx.getProcess(),
                protoCtx.getOpName(),
                fromProtoEventContextArgs(protoCtx.getArgsList())
        );
    }

    public static Map<String, Object> fromProtoEventContextArgs(List<EventContextArg> operandsList) {
        Map<String, Object> operandsMap = new HashMap<>();
        for (EventContextArg operandEntry : operandsList) {
            Object operandObj;

            if (operandEntry.getValueCase() == EventContextArg.ValueCase.LISTVALUE) {
                operandObj = operandEntry.getListValue().getValuesList();
            } else {
                operandObj = operandEntry.getStringValue();
            }

            operandsMap.put(operandEntry.getName(), operandObj);
        }

        return operandsMap;
    }

    public static List<EventContextArg> toProtoEventContextArgs(Map<String, Object> operands) throws
                                                                                   PMException {
        List<EventContextArg> operandEntries = new ArrayList<>();
        for (Map.Entry<String, Object> entry : operands.entrySet()) {
            // serialize the value of the operand to a hex string byte array defined in Neo4j package
            EventContextArg.Builder operandEntryBuilder = EventContextArg.newBuilder()
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
