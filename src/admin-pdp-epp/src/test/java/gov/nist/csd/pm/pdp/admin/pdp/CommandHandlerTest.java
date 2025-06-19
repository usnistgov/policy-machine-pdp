package gov.nist.csd.pm.pdp.admin.pdp;

import static org.junit.jupiter.api.Assertions.*;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.core.common.prohibition.ContainerCondition;
import gov.nist.csd.pm.core.common.prohibition.ProhibitionSubject;
import gov.nist.csd.pm.core.pdp.PDPTx;
import gov.nist.csd.pm.core.pdp.modification.*;
import gov.nist.csd.pm.proto.v1.cmd.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

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

		when(graphModificationAdjudicator.createPolicyClass("pc1")).thenReturn(99L);

		commandHandler.handleCommand(null, pdpTx, ac);

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
		
		commandHandler.handleCommand(null, pdpTx, ac);

		verify(graphModificationAdjudicator).setNodeProperties(42L, Collections.singletonMap("k","v"));
	}

	@Test
	void handleAssociate() throws PMException {
		AssociateCmd a = AssociateCmd.newBuilder()
				.setUaId(1).setTargetId(2)
				.addArset("read")
				.build();

		commandHandler.handleCommand(null, pdpTx, AdminCommand.newBuilder().setAssociateCmd(a).build());

		verify(graphModificationAdjudicator).associate(1L,2L,new AccessRightSet(List.of("read")));
	}

	@Test
	void handleDissociateCmd() throws PMException {
		DissociateCmd d = DissociateCmd.newBuilder()
				.setUaId(1).setTargetId(2).build();

		commandHandler.handleCommand(null, pdpTx, AdminCommand.newBuilder().setDissociateCmd(d).build());

		verify(graphModificationAdjudicator).dissociate(1L,2L);
	}

	@Test
	void handleCreateProhibitionCmd_nodeSubject() throws PMException {
		AdminCommand ac = AdminCommand.newBuilder().setCreateProhibitionCmd(
				CreateProhibitionCmd.newBuilder()
						.setName("p1")
						.setNodeId(5)
						.addAllArset(List.of("r1", "r2"))
						.setIntersection(false)
						.addContainerConditions(CreateProhibitionCmd.ContainerCondition
								                        .newBuilder()
								                        .setContainerId(10)
								                        .setComplement(true)
								                        .build())
						.build()
		).build();

		commandHandler.handleCommand(null, pdpTx, ac);

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
		CreateProhibitionCmd p = CreateProhibitionCmd.newBuilder()
				.setName("p2")
				.setProcess("proc")
				.addArset("op")
				.setIntersection(true)
				.build();
		AdminCommand ac = AdminCommand.newBuilder().setCreateProhibitionCmd(p).build();

		commandHandler.handleCommand(null, pdpTx, ac);

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
		commandHandler.handleCommand(
				null, pdpTx,
				AdminCommand.newBuilder().setDeleteProhibitionCmd(cmd).build()
		);
		verify(prohibitionsModificationAdjudicator).deleteProhibition("x");
	}

	@Test
	void handleExecutePmlCmd() throws PMException {
		ExecutePMLCmd cmd = ExecutePMLCmd.newBuilder()
				.setPml("create pc \"pc1\"")
				.build();
		commandHandler.handleCommand(
				null, pdpTx,
				AdminCommand.newBuilder().setExecutePmlCmd(cmd).build()
		);
		verify(pdpTx).executePML("create pc \"pc1\"");
	}
}