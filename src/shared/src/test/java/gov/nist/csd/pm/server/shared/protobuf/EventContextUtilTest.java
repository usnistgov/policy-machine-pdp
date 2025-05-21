package gov.nist.csd.pm.server.shared.protobuf;

import gov.nist.csd.pm.common.event.EventContext;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.epp.proto.EventContextArg;
import gov.nist.csd.pm.epp.proto.EventContextProto;
import gov.nist.csd.pm.pdp.proto.model.StringList;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventContextUtilTest {

	@Test
	void testToProtoWithAllFieldsSet() throws PMException {
		Map<String, Object> args = new HashMap<>();
		args.put("key1", "value1");
		args.put("key2", Arrays.asList("item1", "item2"));

		EventContext eventContext = mock(EventContext.class);
		when(eventContext.getUser()).thenReturn("testUser");
		when(eventContext.getOpName()).thenReturn("testOpName");
		when(eventContext.getProcess()).thenReturn("testProcess");
		when(eventContext.getArgs()).thenReturn(args);

		EventContextProto result = EventContextUtil.toProto(eventContext);

		assertEquals("testUser", result.getUser());
		assertEquals("testOpName", result.getOpName());
		assertEquals("testProcess", result.getProcess());
		assertEquals(2, result.getArgsCount());
	}

	@Test
	void testToProtoWithNullProcess() throws PMException {
		Map<String, Object> args = new HashMap<>();
		args.put("key", "value");

		EventContext eventContext = mock(EventContext.class);
		when(eventContext.getUser()).thenReturn("testUser");
		when(eventContext.getOpName()).thenReturn("testOpName");
		when(eventContext.getProcess()).thenReturn(null);
		when(eventContext.getArgs()).thenReturn(args);

		EventContextProto result = EventContextUtil.toProto(eventContext);

		assertEquals("testUser", result.getUser());
		assertEquals("testOpName", result.getOpName());
		assertEquals("", result.getProcess());
		assertEquals(1, result.getArgsCount());
	}

	@Test
	void testFromProtoWithAllFieldsSet() throws PMException {
		EventContextArg arg1 = EventContextArg.newBuilder()
				.setName("key1")
				.setStringValue("value1")
				.build();
		EventContextArg arg2 = EventContextArg.newBuilder()
				.setName("key2")
				.setListValue(StringList.newBuilder().addAllValues(Arrays.asList("item1", "item2")).build())
				.build();

		EventContextProto proto = EventContextProto.newBuilder()
				.setUser("testUser")
				.setProcess("testProcess")
				.setOpName("testOpName")
				.addArgs(arg1)
				.addArgs(arg2)
				.build();

		EventContext result = EventContextUtil.fromProto(proto);

		assertEquals("testUser", result.getUser());
		assertEquals("testProcess", result.getProcess());
		assertEquals("testOpName", result.getOpName());
		assertEquals("value1", result.getArgs().get("key1"));
		assertEquals(Arrays.asList("item1", "item2"), result.getArgs().get("key2"));
	}

	@Test
	void testFromProtoWithEmptyArguments() throws PMException {
		EventContextProto proto = EventContextProto.newBuilder()
				.setUser("testUser")
				.setProcess("testProcess")
				.setOpName("testOpName")
				.build();

		EventContext result = EventContextUtil.fromProto(proto);

		assertEquals("testUser", result.getUser());
		assertEquals("testProcess", result.getProcess());
		assertEquals("testOpName", result.getOpName());
		assertTrue(result.getArgs().isEmpty());
	}

	@Test
	void testToProtoEventContextArgsWithSingleValue() throws PMException {
		Map<String, Object> args = new HashMap<>();
		args.put("key1", "value1");

		List<EventContextArg> result = EventContextUtil.toProtoEventContextArgs(args);

		assertEquals(1, result.size());
		assertEquals("key1", result.get(0).getName());
		assertEquals("value1", result.get(0).getStringValue());
	}

	@Test
	void testToProtoEventContextArgsWithListValue() throws PMException {
		Map<String, Object> args = new HashMap<>();
		args.put("key1", Arrays.asList("item1", "item2"));

		List<EventContextArg> result = EventContextUtil.toProtoEventContextArgs(args);

		assertEquals(1, result.size());
		assertEquals("key1", result.get(0).getName());
		assertEquals(Arrays.asList("item1", "item2"), result.get(0).getListValue().getValuesList());
	}

	@Test
	void testToProtoEventContextArgsWithEmptyMap() throws PMException {
		Map<String, Object> args = new HashMap<>();

		List<EventContextArg> result = EventContextUtil.toProtoEventContextArgs(args);

		assertTrue(result.isEmpty());
	}

	@Test
	void testFromProtoEventContextArgsWithSingleValue() {
		EventContextArg arg = EventContextArg.newBuilder()
				.setName("key1")
				.setStringValue("value1")
				.build();

		List<EventContextArg> protoArgs = Collections.singletonList(arg);

		Map<String, Object> result = EventContextUtil.fromProtoEventContextArgs(protoArgs);

		assertEquals(1, result.size());
		assertEquals("value1", result.get("key1"));
	}

	@Test
	void testFromProtoEventContextArgsWithListValue() {
		EventContextArg arg = EventContextArg.newBuilder()
				.setName("key1")
				.setListValue(StringList.newBuilder().addAllValues(Arrays.asList("item1", "item2")).build())
				.build();

		List<EventContextArg> protoArgs = Collections.singletonList(arg);

		Map<String, Object> result = EventContextUtil.fromProtoEventContextArgs(protoArgs);

		assertEquals(1, result.size());
		assertEquals(Arrays.asList("item1", "item2"), result.get("key1"));
	}

	@Test
	void testFromProtoEventContextArgsWithEmptyList() {
		List<EventContextArg> protoArgs = Collections.emptyList();

		Map<String, Object> result = EventContextUtil.fromProtoEventContextArgs(protoArgs);

		assertTrue(result.isEmpty());
	}
}