package gov.nist.csd.pm.pdp.shared.protobuf;

import gov.nist.csd.pm.core.common.event.EventContext;
import gov.nist.csd.pm.core.common.event.EventContextUser;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.epp.proto.ResourceEventContext;
import gov.nist.csd.pm.epp.proto.StringList;
import gov.nist.csd.pm.proto.v1.epp.PolicyEventContext;
import gov.nist.csd.pm.proto.v1.epp.PolicyEventContextArg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventContextUtil {

    public static ResourceEventContext toProto(EventContext eventContext) {
        ResourceEventContext.Builder builder = ResourceEventContext.newBuilder()
                .setOpName(eventContext.opName())
                .setTarget((String) eventContext.args().get("target"));

        EventContextUser user = eventContext.user();
        if (user.isUser()) {
            builder.setUserName(user.getName());
        } else {
            builder.setUserAttrs(StringList.newBuilder().addAllValues(eventContext.user().getAttrs()).build());
        }

        if (user.getProcess() != null && !user.getProcess().isEmpty()) {
            builder.setProcess(user.getProcess());
        }

        return builder.build();
    }

    public static EventContext fromProto(ResourceEventContext protoCtx) throws PMException {
        EventContextUser user;
        if (protoCtx.getUserCase() == ResourceEventContext.UserCase.USER_NAME) {
            user = new EventContextUser(protoCtx.getUserName(), protoCtx.getProcess());
        } else {
            user = new EventContextUser(protoCtx.getUserAttrs().getValuesList(), protoCtx.getProcess());
        }

        return new EventContext(
                user,
                protoCtx.getOpName(),
                Map.of("target", protoCtx.getTarget())
        );
    }

    public static EventContext fromProto(PolicyEventContext proto) {
        EventContextUser user;
        if (proto.getUserCase() == PolicyEventContext.UserCase.USER_NAME) {
            user = new EventContextUser(proto.getUserName(), proto.getProcess());
        } else {
            user = new EventContextUser(proto.getUserAttrs().getValuesList(), proto.getProcess());
        }

        Map<String, Object> args = new HashMap<>();
        List<PolicyEventContextArg> argsList = proto.getArgsList();
        for (PolicyEventContextArg argEntry : argsList) {
            String name = argEntry.getName();
            Object value = switch (argEntry.getValueCase()) {
                case NODE_NAME -> argEntry.getNodeName();
                case NODE_NAME_LIST -> new ArrayList<>(argEntry.getNodeNameList().getValuesList());
                case VALUE_NOT_SET -> throw new IllegalArgumentException("PolicyEventContext arg not set");
            };

            args.put(name, value);
        }

        return new EventContext(user, proto.getOpName(), args);
    }
}
