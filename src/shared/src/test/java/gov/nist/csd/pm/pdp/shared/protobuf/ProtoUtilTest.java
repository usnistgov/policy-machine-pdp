package gov.nist.csd.pm.pdp.shared.protobuf;

import gov.nist.csd.pm.core.common.event.EventContext;
import gov.nist.csd.pm.core.common.event.EventContextUser;
import gov.nist.csd.pm.core.common.graph.node.Node;
import gov.nist.csd.pm.core.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.core.common.prohibition.ContainerCondition;
import gov.nist.csd.pm.core.common.prohibition.Prohibition;
import gov.nist.csd.pm.core.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.obligation.Obligation;
import gov.nist.csd.pm.core.pap.query.PolicyQuery;
import gov.nist.csd.pm.core.pap.query.model.context.TargetContext;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pap.query.model.explain.Explain;
import gov.nist.csd.pm.proto.v1.model.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProtoUtilTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    PAP pap;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    PolicyQuery query;

    @Test
    void valueToObject_int64() {
        Value v = Value.newBuilder().setInt64Value(1).build();
        assertEquals(1L, ProtoUtil.valueToObject(v));
    }

    @Test
    void valueToObject_string() {
        Value v = Value.newBuilder().setStringValue("test").build();
        assertEquals("test", ProtoUtil.valueToObject(v));
    }

    @Test
    void valueToObject_bool() {
        Value v = Value.newBuilder().setBoolValue(true).build();
        assertEquals(true, ProtoUtil.valueToObject(v));
    }

    @Test
    void valueToObject_list() {
        ValueList list = ValueList.newBuilder()
                .addValues(Value.newBuilder().setInt64Value(1).build())
                .addValues(Value.newBuilder().setStringValue("test").build())
                .build();

        Value v = Value.newBuilder().setListValue(list).build();

        Object obj = ProtoUtil.valueToObject(v);
        assertTrue(obj instanceof List<?>);

        List<?> out = (List<?>) obj;
        assertEquals(2, out.size());
        assertEquals(1L, out.get(0));
        assertEquals("test", out.get(1));
    }

    @Test
    void valueToObject_map() {
        ValueMap map = ValueMap.newBuilder()
                .putValues("a", Value.newBuilder().setInt64Value(1).build())
                .putValues("b", Value.newBuilder().setStringValue("test").build())
                .build();

        Value v = Value.newBuilder().setMapValue(map).build();

        Object obj = ProtoUtil.valueToObject(v);
        assertTrue(obj instanceof Map<?, ?>);

        Map<?, ?> out = (Map<?, ?>) obj;
        assertEquals(1L, out.get("a"));
        assertEquals("test", out.get("b"));
    }

    @Test
    void valueToObject_notSet_throws() {
        Value v = Value.newBuilder().build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProtoUtil.valueToObject(v));
        assertEquals("value data field is not set", ex.getMessage());
    }

    @Test
    void objectToValue_long() {
        Value v = ProtoUtil.objectToValue(1L);
        assertEquals(Value.DataCase.INT64_VALUE, v.getDataCase());
        assertEquals(1L, v.getInt64Value());
    }

    @Test
    void objectToValue_boolean() {
        Value v = ProtoUtil.objectToValue(true);
        assertEquals(Value.DataCase.BOOL_VALUE, v.getDataCase());
        assertTrue(v.getBoolValue());
    }

    @Test
    void objectToValue_string() {
        Value v = ProtoUtil.objectToValue("test");
        assertEquals(Value.DataCase.STRING_VALUE, v.getDataCase());
        assertEquals("test", v.getStringValue());
    }

    @Test
    void objectToValue_list() {
        List<Object> list = List.of(1L, "test", true);

        Value v = ProtoUtil.objectToValue(list);

        assertEquals(Value.DataCase.LIST_VALUE, v.getDataCase());
        assertEquals(3, v.getListValue().getValuesCount());
        assertEquals(1L, v.getListValue().getValues(0).getInt64Value());
        assertEquals("test", v.getListValue().getValues(1).getStringValue());
        assertTrue(v.getListValue().getValues(2).getBoolValue());
    }

    @Test
    void objectToValue_map_stringKeys() {
        Map<String, Object> map = new HashMap<>();
        map.put("a", 1L);
        map.put("b", "test");

        Value v = ProtoUtil.objectToValue(map);

        assertEquals(Value.DataCase.MAP_VALUE, v.getDataCase());
        assertEquals(1L, v.getMapValue().getValuesMap().get("a").getInt64Value());
        assertEquals("test", v.getMapValue().getValuesMap().get("b").getStringValue());
    }

    @Test
    void objectToValue_map_nonStringKeys_coercesToString() {
        Map<Object, Object> map = new HashMap<>();
        map.put(1, "test");

        Value v = ProtoUtil.objectToValue(map);

        assertEquals(Value.DataCase.MAP_VALUE, v.getDataCase());
        assertEquals("test", v.getMapValue().getValuesMap().get("1").getStringValue());
    }

    @Test
    void objectToValue_unknownType_returnsEmptyValue() {
        Value v = ProtoUtil.objectToValue(new Object());
        assertEquals(Value.DataCase.DATA_NOT_SET, v.getDataCase());
    }

    @Test
    void valueMapToObjectMap_roundTrip() {
        ValueMap vm = ValueMap.newBuilder()
                .putValues("a", Value.newBuilder().setInt64Value(1).build())
                .putValues("b", Value.newBuilder().setStringValue("test").build())
                .build();

        Map<String, Object> obj = ProtoUtil.valueMapToObjectMap(vm);
        assertEquals(1L, obj.get("a"));
        assertEquals("test", obj.get("b"));

        ValueMap back = ProtoUtil.objectMapToValueMap(obj);
        assertEquals(1L, back.getValuesMap().get("a").getInt64Value());
        assertEquals("test", back.getValuesMap().get("b").getStringValue());
    }

    @Test
    void resolveNodeRefId_idCase() throws Exception {
        NodeRef ref = NodeRef.newBuilder().setId(1).build();
        assertEquals(1L, ProtoUtil.resolveNodeRefId(pap, ref));

        verify(pap, never()).query();
    }

    @Test
    void resolveNodeRefId_nameCase() throws Exception {
        Node node = mock(Node.class);
        when(node.getId()).thenReturn(1L);

        when(pap.query().graph().getNodeByName("test")).thenReturn(node);

        NodeRef ref = NodeRef.newBuilder().setName("test").build();
        assertEquals(1L, ProtoUtil.resolveNodeRefId(pap, ref));

        verify(pap.query().graph()).getNodeByName("test");
    }

    @Test
    void resolveNodeRefId_notSet_throws() {
        NodeRef ref = NodeRef.newBuilder().build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProtoUtil.resolveNodeRefId(pap, ref));
        assertEquals("node reference not set", ex.getMessage());
    }

    @Test
    void resolveNodeRefIdList_mixed() throws Exception {
        Node node = mock(Node.class);
        when(node.getId()).thenReturn(2L);

        when(pap.query().graph().getNodeByName("test")).thenReturn(node);

        List<NodeRef> refs = List.of(
                NodeRef.newBuilder().setId(1).build(),
                NodeRef.newBuilder().setName("test").build()
        );

        List<Long> ids = ProtoUtil.resolveNodeRefIdList(pap, refs);
        assertEquals(List.of(1L, 2L), ids);
    }

    @Test
    void fromUserContextProto_userNode() throws Exception {
        gov.nist.csd.pm.proto.v1.query.UserContext proto =
                gov.nist.csd.pm.proto.v1.query.UserContext.newBuilder()
                        .setProcess("test")
                        .setUserNode(NodeRef.newBuilder().setId(1).build())
                        .build();

        UserContext ctx = ProtoUtil.fromUserContextProto(pap, proto);
        assertNotNull(ctx);
    }

    @Test
    void fromUserContextProto_userAttributes() throws Exception {
        NodeRefList attrs = NodeRefList.newBuilder()
                .addNodes(NodeRef.newBuilder().setId(1).build())
                .addNodes(NodeRef.newBuilder().setId(2).build())
                .build();

        gov.nist.csd.pm.proto.v1.query.UserContext proto =
                gov.nist.csd.pm.proto.v1.query.UserContext.newBuilder()
                        .setProcess("test")
                        .setUserAttributes(attrs)
                        .build();

        UserContext ctx = ProtoUtil.fromUserContextProto(pap, proto);
        assertNotNull(ctx);
    }

    @Test
    void fromUserContextProto_notSet_throws() {
        gov.nist.csd.pm.proto.v1.query.UserContext proto =
                gov.nist.csd.pm.proto.v1.query.UserContext.newBuilder()
                        .setProcess("test")
                        .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProtoUtil.fromUserContextProto(pap, proto));
        assertEquals("user context not set", ex.getMessage());
    }

    @Test
    void fromTargetContextProto_targetNode() throws Exception {
        gov.nist.csd.pm.proto.v1.query.TargetContext proto =
                gov.nist.csd.pm.proto.v1.query.TargetContext.newBuilder()
                        .setTargetNode(NodeRef.newBuilder().setId(1).build())
                        .build();

        TargetContext ctx = ProtoUtil.fromTargetContextProto(pap, proto);
        assertNotNull(ctx);
    }

    @Test
    void fromTargetContextProto_targetAttributes() throws Exception {
        NodeRefList attrs = NodeRefList.newBuilder()
                .addNodes(NodeRef.newBuilder().setId(1).build())
                .addNodes(NodeRef.newBuilder().setId(2).build())
                .build();

        gov.nist.csd.pm.proto.v1.query.TargetContext proto =
                gov.nist.csd.pm.proto.v1.query.TargetContext.newBuilder()
                        .setTargetAttributes(attrs)
                        .build();

        TargetContext ctx = ProtoUtil.fromTargetContextProto(pap, proto);
        assertNotNull(ctx);
    }

    @Test
    void fromTargetContextProto_notSet_throws() {
        gov.nist.csd.pm.proto.v1.query.TargetContext proto =
                gov.nist.csd.pm.proto.v1.query.TargetContext.newBuilder().build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProtoUtil.fromTargetContextProto(pap, proto));
        assertEquals("target context not set", ex.getMessage());
    }

    @Test
    void toNodeProto_setsFields() {
        Node node = mock(Node.class);

        when(node.getId()).thenReturn(1L);
        when(node.getName()).thenReturn("test");
        when(node.getType()).thenReturn(gov.nist.csd.pm.core.common.graph.node.NodeType.UA);

        Map<String, String> props = new HashMap<>();
        props.put("a", "test");
        when(node.getProperties()).thenReturn(props);

        gov.nist.csd.pm.proto.v1.model.Node proto = ProtoUtil.toNodeProto(node);

        assertEquals(1L, proto.getId());
        assertEquals("test", proto.getName());
        assertEquals(NodeType.UA, proto.getType());
        assertEquals("test", proto.getPropertiesMap().get("a"));
    }

    @Test
    void toProhibitionProto_subjectNode_andContainers() throws Exception {
        Prohibition prohibition = mock(Prohibition.class);
        ProhibitionSubject subject = mock(ProhibitionSubject.class);

        when(prohibition.getName()).thenReturn("test");
        when(prohibition.getAccessRightSet()).thenReturn(new AccessRightSet("read", "write"));
        when(prohibition.isIntersection()).thenReturn(false);

        when(prohibition.getSubject()).thenReturn(subject);
        when(subject.isNode()).thenReturn(true);
        when(subject.getNodeId()).thenReturn(1L);

        ContainerCondition cc = mock(ContainerCondition.class);
        when(cc.getId()).thenReturn(2L);
        when(cc.isComplement()).thenReturn(true);
        when(prohibition.getContainers()).thenReturn(List.of(cc));

        Node node1 = mock(Node.class);
        when(node1.getId()).thenReturn(1L);
        when(node1.getName()).thenReturn("test");
        when(node1.getType()).thenReturn(gov.nist.csd.pm.core.common.graph.node.NodeType.UA);
        when(node1.getProperties()).thenReturn(Map.of());

        Node node2 = mock(Node.class);
        when(node2.getId()).thenReturn(2L);
        when(node2.getName()).thenReturn("test2");
        when(node2.getType()).thenReturn(gov.nist.csd.pm.core.common.graph.node.NodeType.UA);
        when(node2.getProperties()).thenReturn(Map.of());

        when(query.graph().getNodeById(1L)).thenReturn(node1);
        when(query.graph().getNodeById(2L)).thenReturn(node2);

        gov.nist.csd.pm.proto.v1.model.Prohibition proto = ProtoUtil.toProhibitionProto(prohibition, query);

        assertEquals("test", proto.getName());
        assertEquals(List.of("read", "write"), proto.getArsetList());
        assertFalse(proto.getIntersection());
        assertTrue(proto.hasNode());

        assertEquals(1L, proto.getNode().getId());
        assertEquals(1, proto.getContainerConditionsCount());
        assertEquals(2L, proto.getContainerConditions(0).getContainer().getId());
        assertTrue(proto.getContainerConditions(0).getComplement());
    }

    @Test
    void toProhibitionProto_subjectProcess() throws Exception {
        Prohibition prohibition = mock(Prohibition.class);
        ProhibitionSubject subject = mock(ProhibitionSubject.class);

        when(prohibition.getName()).thenReturn("test");
        when(prohibition.getAccessRightSet()).thenReturn(new AccessRightSet(List.of("read")));
        when(prohibition.isIntersection()).thenReturn(true);

        when(prohibition.getSubject()).thenReturn(subject);
        when(subject.isNode()).thenReturn(false);
        when(subject.getProcess()).thenReturn("test");

        when(prohibition.getContainers()).thenReturn(List.of());

        gov.nist.csd.pm.proto.v1.model.Prohibition proto = ProtoUtil.toProhibitionProto(prohibition, query);

        assertEquals("test", proto.getName());
        assertEquals(List.of("read"), proto.getArsetList());
        assertTrue(proto.getIntersection());
        assertEquals("test", proto.getProcess());
    }

    @Test
    void toObligationProto_mapsFields() throws Exception {
        Obligation obligation = mock(Obligation.class);

        when(obligation.getName()).thenReturn("test");
        when(obligation.getAuthorId()).thenReturn(1L);
        when(obligation.toString()).thenReturn("test");

        Node author = mock(Node.class);
        when(author.getId()).thenReturn(1L);
        when(author.getName()).thenReturn("test");
        when(author.getType()).thenReturn(gov.nist.csd.pm.core.common.graph.node.NodeType.UA);
        when(author.getProperties()).thenReturn(Map.of());

        when(pap.query().graph().getNodeById(1L)).thenReturn(author);

        gov.nist.csd.pm.proto.v1.model.Obligation proto = ProtoUtil.toObligationProto(obligation, pap);

        assertEquals("test", proto.getName());
        assertEquals(1L, proto.getAuthor().getId());
        assertEquals("test", proto.getPml());
    }

    @Test
    void buildExplainProto_nullExplain_returnsEmpty() {
        gov.nist.csd.pm.proto.v1.query.ExplainResponse resp = ProtoUtil.buildExplainProto(null, query);
        assertNotNull(resp);
        assertEquals(0, resp.getPrivilegesCount());
        assertEquals(0, resp.getDeniedPrivilegesCount());
        assertEquals(0, resp.getPolicyClassesCount());
        assertEquals(0, resp.getProhibitionsCount());
    }

    @Test
    void buildExplainProto_minimalExplain_noPolicyClasses_noProhibitions() {
        Explain explain = mock(Explain.class);

        AccessRightSet allowed = new AccessRightSet();
        allowed.add("read");

        AccessRightSet denied = new AccessRightSet();
        denied.add("write");

        when(explain.getPrivileges()).thenReturn(allowed);
        when(explain.getDeniedPrivileges()).thenReturn(denied);
        when(explain.getPolicyClasses()).thenReturn(List.of());
        when(explain.getProhibitions()).thenReturn(List.of());

        gov.nist.csd.pm.proto.v1.query.ExplainResponse resp = ProtoUtil.buildExplainProto(explain, query);

        assertEquals(List.of("read"), resp.getPrivilegesList());
        assertEquals(List.of("write"), resp.getDeniedPrivilegesList());
        assertEquals(0, resp.getPolicyClassesCount());
        assertEquals(0, resp.getProhibitionsCount());
    }

    @Nested
    class EventContextTests {

        @Test
        void toEventContextProto_userName() {
            EventContextUser user = new EventContextUser("test", "test");
            Map<String, Object> args = new HashMap<>();
            args.put("a", 1L);
            args.put("b", "test");

            EventContext ctx = new EventContext(user, "test", args);

            gov.nist.csd.pm.proto.v1.epp.EventContext proto = ProtoUtil.toEventContextProto(ctx);

            assertEquals("test", proto.getUserName());
            assertEquals("test", proto.getProcess());
            assertEquals("test", proto.getOpName());
            assertEquals(1L, proto.getArgs().getValuesMap().get("a").getInt64Value());
            assertEquals("test", proto.getArgs().getValuesMap().get("b").getStringValue());
        }

        @Test
        void toEventContextProto_userAttrs() {
            EventContextUser user = new EventContextUser(List.of("test", "test2"), "test");
            Map<String, Object> args = new HashMap<>();
            args.put("a", true);

            EventContext ctx = new EventContext(user, "test", args);

            gov.nist.csd.pm.proto.v1.epp.EventContext proto = ProtoUtil.toEventContextProto(ctx);

            assertEquals(List.of("test", "test2"), proto.getUserAttrs().getValuesList());
            assertEquals("test", proto.getProcess());
            assertEquals("test", proto.getOpName());
            assertTrue(proto.getArgs().getValuesMap().get("a").getBoolValue());
        }

        @Test
        void fromEventContextProto_userName_roundTrip() {
            ValueMap args = ValueMap.newBuilder()
                    .putValues("a", Value.newBuilder().setInt64Value(1).build())
                    .build();

            gov.nist.csd.pm.proto.v1.epp.EventContext proto = gov.nist.csd.pm.proto.v1.epp.EventContext.newBuilder()
                    .setUserName("test")
                    .setProcess("test")
                    .setOpName("test")
                    .setArgs(args)
                    .build();

            EventContext ctx = ProtoUtil.fromEventContextProto(proto);

            assertEquals("test", ctx.user().getName());
            assertEquals("test", ctx.user().getProcess());
            assertEquals("test", ctx.opName());
            assertEquals(1L, ctx.args().get("a"));
        }

        @Test
        void fromEventContextProto_userAttrs_roundTrip() {
            ValueMap args = ValueMap.newBuilder()
                    .putValues("a", Value.newBuilder().setStringValue("test").build())
                    .build();

            gov.nist.csd.pm.proto.v1.epp.EventContext proto = gov.nist.csd.pm.proto.v1.epp.EventContext.newBuilder()
                    .setUserAttrs(StringList.newBuilder().addAllValues(List.of("test", "test2")).build())
                    .setProcess("test")
                    .setOpName("test")
                    .setArgs(args)
                    .build();

            EventContext ctx = ProtoUtil.fromEventContextProto(proto);

            assertEquals(List.of("test", "test2"), ctx.user().getAttrs());
            assertEquals("test", ctx.user().getProcess());
            assertEquals("test", ctx.opName());
            assertEquals("test", ctx.args().get("a"));
        }

        @Test
        void fromEventContextProto_userNotSet_throws() {
            gov.nist.csd.pm.proto.v1.epp.EventContext proto = gov.nist.csd.pm.proto.v1.epp.EventContext.newBuilder()
                    .setProcess("test")
                    .setOpName("test")
                    .setArgs(ValueMap.newBuilder().build())
                    .build();

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> ProtoUtil.fromEventContextProto(proto));
            assertEquals("User not set", ex.getMessage());
        }
    }
}