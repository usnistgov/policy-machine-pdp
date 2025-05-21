package gov.nist.csd.pm.server.admin.pdp;

import static org.junit.jupiter.api.Assertions.*;

import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.common.prohibition.ContainerCondition;
import gov.nist.csd.pm.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.pap.pml.PMLCompiler;
import gov.nist.csd.pm.pap.pml.expression.literal.StringLiteralExpression;
import gov.nist.csd.pm.pap.pml.statement.PMLStatement;
import gov.nist.csd.pm.pap.pml.statement.operation.CreateObligationStatement;
import gov.nist.csd.pm.pap.pml.statement.operation.CreatePolicyClassStatement;
import gov.nist.csd.pm.pap.pml.statement.operation.OperationDefinitionStatement;
import gov.nist.csd.pm.pap.pml.statement.operation.RoutineDefinitionStatement;
import gov.nist.csd.pm.pdp.PDPTx;
import gov.nist.csd.pm.pdp.modification.*;
import gov.nist.csd.pm.pdp.proto.model.AccessRightSetProto;
import gov.nist.csd.pm.pdp.proto.model.ProhibitionProto;
import gov.nist.csd.pm.pdp.proto.modify.*;
import gov.nist.csd.pm.server.admin.pap.EventTrackingPAP;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.neo4j.cypher.internal.ast.Create;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommandHandlerTest {

	@Mock PDPTx pdpTx;
	@Mock PolicyModificationAdjudicator policyModificationAdjudicator;
	@Mock GraphModificationAdjudicator graphModificationAdjudicator;
	@Mock ProhibitionsModificationAdjudicator prohibitionsModificationAdjudicator;
	@Mock ObligationsModificationAdjudicator obligationsModificationAdjudicator;
	@Mock OperationsModificationAdjudicator operationsModificationAdjudicator;
	@Mock RoutinesModificationAdjudicator routinesModificationAdjudicator;

	private CommandHandler commandHandler;

	@BeforeEach
	void setUp() {
		when(policyModificationAdjudicator.graph()).thenReturn(graphModificationAdjudicator);
		when(policyModificationAdjudicator.prohibitions()).thenReturn(prohibitionsModificationAdjudicator);
		when(policyModificationAdjudicator.obligations()).thenReturn(obligationsModificationAdjudicator);
		when(policyModificationAdjudicator.operations()).thenReturn(operationsModificationAdjudicator);
		when(policyModificationAdjudicator.routines()).thenReturn(routinesModificationAdjudicator);
		when(pdpTx.modify()).thenReturn(policyModificationAdjudicator);

		commandHandler = new CommandHandler();
	}

	@Test
	void handleCreatePolicyClassCmd() throws PMException {
		CreatePolicyClassCmd cmd = CreatePolicyClassCmd.newBuilder()
				.setName("pc1").build();
		AdminCommand ac = AdminCommand.newBuilder()
				.setCreatePolicyClassCmd(cmd).build();

		Map<String,Long> ids = new HashMap<>();
		when(graphModificationAdjudicator.createPolicyClass("pc1")).thenReturn(99L);

		commandHandler.handleCommand(pdpTx, ac, ids);

		assertEquals(1, ids.size());
		assertEquals(99L, ids.get("pc1"));
		verify(graphModificationAdjudicator).createPolicyClass("pc1");
	}

	@Test
	void handleSetNodePropertiesCmd() throws PMException {
		SetNodePropertiesCmd cmd = SetNodePropertiesCmd.newBuilder()
				.setId(42L)
				.putProperties("k","v")
				.build();
		AdminCommand ac = AdminCommand.newBuilder()
				.setSetNodePropertiesCmd(cmd).build();

		commandHandler.handleCommand(pdpTx, ac, new HashMap<>());

		verify(graphModificationAdjudicator).setNodeProperties(42L, Collections.singletonMap("k","v"));
	}

	@Test
	void handleAssociate() throws PMException {
		AssociateCmd a = AssociateCmd.newBuilder()
				.setUaId(1).setTargetId(2)
				.setArset(AccessRightSetProto.newBuilder().addSet("read").build())
				.build();
		
		commandHandler.handleCommand(pdpTx, AdminCommand.newBuilder().setAssociateCmd(a).build(), Map.of());

		verify(graphModificationAdjudicator).associate(1L,2L,new AccessRightSet(List.of("read")));
	}

	@Test
	void handleDissociateCmd() throws PMException {
		DissociateCmd d = DissociateCmd.newBuilder()
				.setUaId(1).setTargetId(2).build();

		commandHandler.handleCommand(pdpTx, AdminCommand.newBuilder().setDissociateCmd(d).build(), Map.of());

		verify(graphModificationAdjudicator).dissociate(1L,2L);
	}

	@Test
	void handleCreateProhibitionCmd_nodeSubject() throws PMException {
		ProhibitionProto.ContainerCondition cc = ProhibitionProto.ContainerCondition.newBuilder()
				.setContainerId(10).setComplement(true).build();
		ProhibitionProto p = ProhibitionProto.newBuilder()
				.setName("p1")
				.setNodeId(5)
				.setArset(AccessRightSetProto.newBuilder().addAllSet(List.of("r1", "r2")))
				.setIntersection(false)
				.addContainerConditions(cc)
				.build();
		AdminCommand ac = AdminCommand.newBuilder().setCreateProhibitionCmd(p).build();

		commandHandler.handleCommand(pdpTx, ac, Map.of());

		ArgumentCaptor<ProhibitionSubject> subjCap = ArgumentCaptor.forClass(ProhibitionSubject.class);
		ArgumentCaptor<List<ContainerCondition>> condsCap = ArgumentCaptor.forClass(List.class);
		verify(prohibitionsModificationAdjudicator).createProhibition(
				eq("p1"),
				subjCap.capture(),
				eq(new AccessRightSet(List.of("r1","r2"))),
				eq(false),
				condsCap.capture()
		);
		ProhibitionSubject subj = subjCap.getValue();
		assertEquals(5L, subj.getNodeId());
		List<ContainerCondition> conds = condsCap.getValue();
		assertEquals(1, conds.size());
		assertEquals(10L, conds.get(0).getId());
		assertTrue(conds.get(0).isComplement());
	}

	@Test
	void handleCreateProhibitionCmd_processSubject() throws PMException {
		ProhibitionProto p = ProhibitionProto.newBuilder()
				.setName("p2")
				.setProcess("proc")
				.setArset(AccessRightSetProto.newBuilder().addSet("op"))
				.setIntersection(true)
				.build();
		AdminCommand ac = AdminCommand.newBuilder().setCreateProhibitionCmd(p).build();

		commandHandler.handleCommand(pdpTx, ac, Map.of());

		verify(prohibitionsModificationAdjudicator).createProhibition(
				eq("p2"),
				eq(new ProhibitionSubject("proc")),
				eq(new AccessRightSet(List.of("op"))),
				eq(true),
				eq(Collections.emptyList())
		);
	}

	@Test
	void handleDeleteProhibitionCmd() throws PMException {
		DeleteProhibitionCmd cmd = DeleteProhibitionCmd.newBuilder().setName("x").build();
		commandHandler.handleCommand(pdpTx,
		                      AdminCommand.newBuilder().setDeleteProhibitionCmd(cmd).build(),
		                      Map.of()
		);
		verify(prohibitionsModificationAdjudicator).deleteProhibition("x");
	}

	@Test
	void handleCreateObligationCmd_valid() throws PMException {
		String pml = """
				create obligation "o" {}
				""";
		CreateObligationCmd cmd = CreateObligationCmd.newBuilder()
				.setPml(pml)
				.build();
		List<PMLStatement<?>> pmlStatements = new PMLCompiler().compilePML(pml);
		when(pdpTx.compilePML(pml)).thenReturn(pmlStatements);

		commandHandler.handleCommand(pdpTx,
		                      AdminCommand.newBuilder().setCreateObligationCmd(cmd).build(),
		                      Map.of()
		);

		verify(pdpTx).executePML(pml);
	}

	@Test
	void handleCreateObligationCmd_invalid() throws PMException {
		String pml = """
				create pc "pc1"
				""";
		CreateObligationCmd cmd = CreateObligationCmd.newBuilder()
				.setPml(pml)
				.build();
		when(pdpTx.compilePML(pml)).thenReturn(List.of(new CreatePolicyClassStatement(new StringLiteralExpression("pc1"))));

		PMException ex = assertThrows(
				PMException.class,
				() -> commandHandler.handleCommand(pdpTx,
				                            AdminCommand.newBuilder().setCreateObligationCmd(cmd).build(),
				                            Map.of()
				)
		);
		assertTrue(ex.getMessage().contains("only one create obligation"));
	}

	@Test
	void handleCreateAdminOperationCmd_valid() throws PMException {
		String pml = """
				operation test() {}
				""";
		CreateAdminOperationCmd cmd = CreateAdminOperationCmd.newBuilder()
				.setPml(pml)
				.build();
		List<PMLStatement<?>> pmlStatements = new PMLCompiler().compilePML(pml);
		when(pdpTx.compilePML(pml)).thenReturn(pmlStatements);

		commandHandler.handleCommand(pdpTx,
		                             AdminCommand.newBuilder().setCreateAdminOperationCmd(cmd).build(),
		                             Map.of()
		);

		verify(pdpTx).executePML(pml);
	}

	@Test
	void handleCreateAdminOperationCmd_invalid() throws PMException {
		String pml = """
				create pc "pc1"
				""";
		CreateAdminOperationCmd cmd = CreateAdminOperationCmd.newBuilder()
				.setPml(pml)
				.build();
		when(pdpTx.compilePML(pml)).thenReturn(List.of(new CreatePolicyClassStatement(new StringLiteralExpression("pc1"))));

		PMException ex = assertThrows(
				PMException.class,
				() -> commandHandler.handleCommand(pdpTx,
				                                   AdminCommand.newBuilder().setCreateAdminOperationCmd(cmd).build(),
				                                   Map.of()
				)
		);
		assertTrue(ex.getMessage().contains("only one operation"));
	}

	@Test
	void handleCreateAdminRoutineCmd_valid() throws PMException {
		String pml = """
				routine test() {}
				""";
		CreateAdminRoutineCmd cmd = CreateAdminRoutineCmd.newBuilder()
				.setPml(pml)
				.build();
		List<PMLStatement<?>> pmlStatements = new PMLCompiler().compilePML(pml);
		when(pdpTx.compilePML(pml)).thenReturn(pmlStatements);

		commandHandler.handleCommand(pdpTx,
		                             AdminCommand.newBuilder().setCreateAdminRoutineCmd(cmd).build(),
		                             Map.of()
		);

		verify(pdpTx).executePML(pml);
	}

	@Test
	void handleCreateAdminRoutineCmd_invalid() throws PMException {
		String pml = """
				create pc "pc1"
				""";
		CreateAdminRoutineCmd cmd = CreateAdminRoutineCmd.newBuilder()
				.setPml(pml)
				.build();
		when(pdpTx.compilePML(pml)).thenReturn(List.of(new CreatePolicyClassStatement(new StringLiteralExpression("pc1"))));

		PMException ex = assertThrows(
				PMException.class,
				() -> commandHandler.handleCommand(pdpTx,
				                                   AdminCommand.newBuilder().setCreateAdminRoutineCmd(cmd).build(),
				                                   Map.of()
				)
		);
		System.out.println(ex.getMessage());
		assertTrue(ex.getMessage().contains("only one routine"));
	}

	@Test
	void handleExecutePmlCmd() throws PMException {
		ExecutePMLCmd cmd = ExecutePMLCmd.newBuilder()
				.setPml("create pc \"pc1\"")
				.build();
		commandHandler.handleCommand(pdpTx,
		                      AdminCommand.newBuilder().setExecutePmlCmd(cmd).build(),
		                      Map.of()
		);
		verify(pdpTx).executePML("create pc \"pc1\"");
	}
}