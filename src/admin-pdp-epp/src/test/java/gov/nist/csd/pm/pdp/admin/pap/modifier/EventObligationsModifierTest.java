package gov.nist.csd.pm.pdp.admin.pap.modifier;

import gov.nist.csd.pm.core.common.graph.node.Node;
import gov.nist.csd.pm.core.pap.obligation.event.EventPattern;
import gov.nist.csd.pm.core.pap.obligation.event.operation.AnyOperationPattern;
import gov.nist.csd.pm.core.pap.obligation.event.subject.SubjectPattern;
import gov.nist.csd.pm.core.pap.obligation.response.ObligationResponse;
import gov.nist.csd.pm.core.pap.pml.expression.literal.StringLiteralExpression;
import gov.nist.csd.pm.core.pap.pml.statement.operation.CreatePolicyClassStatement;
import gov.nist.csd.pm.core.pap.query.model.context.NodeUserContext;
import gov.nist.csd.pm.core.pap.store.GraphStore;
import gov.nist.csd.pm.core.pap.store.ObligationsStore;
import gov.nist.csd.pm.core.pap.store.PolicyStore;
import gov.nist.csd.pm.pdp.proto.event.ObligationCreated;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventObligationsModifierTest {

    @Mock
    private PolicyStore policyStore;

    @Mock
    private ObligationsStore obligations;

    @Mock
    private GraphStore graph;

    private List<PMEvent> events;
    private EventObligationsModifier modifier;

    @BeforeEach
    void setUp() {
        events = new ArrayList<>();
        modifier = new EventObligationsModifier(events, policyStore);

        lenient().when(policyStore.obligations()).thenReturn(obligations);
        lenient().when(policyStore.graph()).thenReturn(graph);
    }

    @Test
    void createObligation_authorById_recordsPmlEventAndDelegatesToStore() throws Exception {
        NodeUserContext author = NodeUserContext.of(1L);
        EventPattern eventPattern = new EventPattern(new SubjectPattern(), new AnyOperationPattern());
        ObligationResponse response = new ObligationResponse(
                "ctx",
                List.of(new CreatePolicyClassStatement(new StringLiteralExpression("pc1")))
        );

        modifier.createObligation(author, "test", eventPattern, response);

        assertEquals(1, events.size());
        ObligationCreated created = events.get(0).getObligationCreated();
        assertEquals(ObligationCreated.AuthorCase.AUTHOR_ID, created.getAuthorCase());
        assertEquals(1L, created.getAuthorId());
        assertTrue(created.getPml().contains("create obligation \"test\""));
        assertTrue(created.getPml().contains("pc1"));

        verify(obligations).createObligation(author, "test", eventPattern, response);
        verifyNoInteractions(graph);
    }

    @Test
    void createObligation_authorByName_recordsPmlEventWithAuthorNameAndResolvesNode() throws Exception {
        NodeUserContext author = NodeUserContext.of("u1");
        EventPattern eventPattern = new EventPattern(new SubjectPattern(), new AnyOperationPattern());
        ObligationResponse response = new ObligationResponse(
                "ctx",
                List.of(new CreatePolicyClassStatement(new StringLiteralExpression("pc1")))
        );

        Node node = mock(Node.class);
        when(node.getId()).thenReturn(5L);
        when(graph.getNodeByName("u1")).thenReturn(node);

        modifier.createObligation(author, "test", eventPattern, response);

        ObligationCreated created = events.get(0).getObligationCreated();
        assertEquals(ObligationCreated.AuthorCase.AUTHOR_NAME, created.getAuthorCase());
        assertEquals("u1", created.getAuthorName());

        verify(obligations).createObligation(author, "test", eventPattern, response);
    }
}
