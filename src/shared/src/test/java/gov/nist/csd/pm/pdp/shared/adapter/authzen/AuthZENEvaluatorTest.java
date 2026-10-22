/*
 * This Software (Policy Machine) is being made available as a public service by the
 * National Institute of Standards and Technology (NIST), an Agency of the United
 * States Department of Commerce. This software was developed in part by employees of
 * NIST and in part by NIST contractors. Copyright in portions of this software that
 * were developed by NIST contractors has been licensed or assigned to NIST. Pursuant
 * to Title 17 United States Code Section 105, works of NIST employees are not
 * subject to copyright protection in the United States. However, NIST may hold
 * international copyright in software created by its employees and domestic
 * copyright (or licensing rights) in portions of software that were assigned or
 * licensed to NIST. To the extent that NIST holds copyright in this software, it is
 * being made available under the Creative Commons Attribution 4.0 International
 * license (CC BY 4.0). The disclaimers of the CC BY 4.0 license apply to all parts
 * of the software developed or licensed by NIST.
 *
 * ACCESS THE FULL CC BY 4.0 LICENSE HERE:
 * https://creativecommons.org/licenses/by/4.0/legalcode
 */

package gov.nist.csd.pm.pdp.shared.adapter.authzen;

import gov.nist.csd.pm.pdp.shared.adjudication.OperationNotSupportedException;
import gov.nist.ngac.pm.core.pap.query.model.context.AnonymousUserContext;
import gov.nist.ngac.pm.core.pap.query.model.context.NodeUserContext;
import gov.nist.ngac.pm.core.pap.query.model.context.UserContext;
import gov.nist.ngac.pm.core.pdp.UnauthorizedException;
import gov.nist.ngac.pm.core.pdp.adjudication.AccessAdjudication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthZENEvaluatorTest {

    private static final EvaluationRequest.Subject ALICE =
            new EvaluationRequest.Subject("user", "alice", Map.of("id_type", "name"));
    private static final EvaluationRequest.Resource DOC = new EvaluationRequest.Resource("object", "doc", null);

    private AccessAdjudication adjudicator;
    private AuthZENEvaluator evaluator;

    @BeforeEach
    void setUp() throws Exception {
        adjudicator = mock(AccessAdjudication.class);
        evaluator = new AuthZENEvaluator(adjudicator);

        // "read" is permitted, "write" is denied, "admin_op" is unsupported
        when(adjudicator.adjudicateOperation(any(), eq("write"), anyMap()))
                .thenThrow(mock(UnauthorizedException.class));
        when(adjudicator.adjudicateOperation(any(), eq("admin_op"), anyMap()))
                .thenThrow(new OperationNotSupportedException("not supported"));
    }

    @Test
    void evaluate_mapsAdjudicationToDecision() throws Exception {
        assertTrue(evaluator.evaluate(request("read")).decision());
        assertFalse(evaluator.evaluate(request("write")).decision());

        verify(adjudicator).adjudicateOperation(NodeUserContext.of("alice"), "read", Map.of());
    }

    @Test
    void evaluate_missingRequiredField_throwsBadRequest() {
        EvaluationRequest noResource = new EvaluationRequest(ALICE, null, action("read"), null);

        Exception e = assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(noResource));
        assertEquals(HttpStatus.BAD_REQUEST, AuthZENErrors.statusOf(e));
    }

    @Test
    void evaluateAll_appliesDefaultsAndReportsItemErrorsInContext() throws Exception {
        EvaluationsRequest request = new EvaluationsRequest(ALICE, DOC, null, null, List.of(
                new EvaluationRequest(null, null, action("read"), null),
                new EvaluationRequest(null, null, action("admin_op"), null),
                new EvaluationRequest(null, null, action("write"), null)
        ), null);

        List<EvaluationResponse> results = evaluator.evaluateAll(request).evaluations();

        assertEquals(3, results.size());
        assertTrue(results.get(0).decision());
        assertFalse(results.get(1).decision());
        assertEquals(Map.of("status", 400, "message", "not supported"), results.get(1).context().get("error"));
        assertFalse(results.get(2).decision());
    }

    @Test
    void evaluateAll_denyOnFirstDeny_skipsRemainingButKeepsCount() throws Exception {
        EvaluationsRequest request = new EvaluationsRequest(ALICE, DOC, null, null, List.of(
                new EvaluationRequest(null, null, action("read"), null),
                new EvaluationRequest(null, null, action("write"), null),
                new EvaluationRequest(null, null, action("read"), null)
        ), new EvaluationsRequest.Options(EvaluationsSemantic.DENY_ON_FIRST_DENY));

        List<EvaluationResponse> results = evaluator.evaluateAll(request).evaluations();

        assertEquals(List.of(true, false, false), decisions(results));
        assertNotNull(results.get(2).context().get("reason"));
        verify(adjudicator, times(1)).adjudicateOperation(any(), eq("read"), anyMap());
    }

    @Test
    void evaluateAll_permitOnFirstPermit_skipsRemainingButKeepsCount() throws Exception {
        EvaluationsRequest request = new EvaluationsRequest(ALICE, DOC, null, null, List.of(
                new EvaluationRequest(null, null, action("write"), null),
                new EvaluationRequest(null, null, action("read"), null),
                new EvaluationRequest(null, null, action("read"), null)
        ), new EvaluationsRequest.Options(EvaluationsSemantic.PERMIT_ON_FIRST_PERMIT));

        assertEquals(List.of(false, true, false), decisions(evaluator.evaluateAll(request).evaluations()));
        verify(adjudicator, times(1)).adjudicateOperation(any(), eq("read"), anyMap());
    }

    @Test
    void evaluateAll_rootActionAndResource_evaluationsCarrySubjectOnly() throws Exception {
        EvaluationRequest.Subject bob = new EvaluationRequest.Subject("agent", "bob", null);
        EvaluationsRequest request = new EvaluationsRequest(null, DOC, action("read"), null, List.of(
                new EvaluationRequest(ALICE, null, null, null),
                new EvaluationRequest(bob, null, null, null)
        ), null);

        assertEquals(List.of(true, true), decisions(evaluator.evaluateAll(request).evaluations()));
        verify(adjudicator).adjudicateOperation(NodeUserContext.of("alice"), "read", Map.of());
        verify(adjudicator).adjudicateOperation(NodeUserContext.of("bob"), "read", Map.of());
    }

    @Test
    void evaluateAll_fullTuplesWithEmptyRoot() throws Exception {
        EvaluationsRequest request = new EvaluationsRequest(null, null, null, null, List.of(
                request("read"), request("write")), null);

        assertEquals(List.of(true, false), decisions(evaluator.evaluateAll(request).evaluations()));
    }

    @Test
    void evaluateAll_evaluationActionOverridesRoot() throws Exception {
        EvaluationsRequest request = new EvaluationsRequest(ALICE, DOC, action("read"), null, List.of(
                new EvaluationRequest(null, null, null, null),
                new EvaluationRequest(null, null, action("write"), null)
        ), null);

        assertEquals(List.of(true, false), decisions(evaluator.evaluateAll(request).evaluations()));
    }

    @Test
    void evaluateAll_incompleteTuple_failsOnlyThatItem() throws Exception {
        EvaluationsRequest request = new EvaluationsRequest(ALICE, null, action("read"), null, List.of(
                new EvaluationRequest(null, DOC, null, null),
                new EvaluationRequest(null, null, null, null),
                new EvaluationRequest(null, DOC, null, null)
        ), null);

        List<EvaluationResponse> results = evaluator.evaluateAll(request).evaluations();

        assertEquals(List.of(true, false, true), decisions(results));
        assertTrue(results.get(1).context().toString().contains("resource"));
    }

    @Test
    void evaluateAll_noEvaluations_returnsSingleDecision() throws Exception {
        EvaluationsResponse response = evaluator.evaluateAll(
                new EvaluationsRequest(ALICE, DOC, action("read"), null, null, null));

        assertTrue(response.decision());
        assertNull(response.evaluations());
    }

    @Test
    void evaluate_attrsSubject_adjudicatesAsAnonymousUserWithProcess() throws Exception {
        EvaluationRequest.Subject agent = new EvaluationRequest.Subject("agent", "sbx",
                Map.of("attrs", List.of("@agent/claude-code", "@sandbox/sbx"), "process_id", "txn-1"));

        assertTrue(evaluator.evaluate(new EvaluationRequest(agent, DOC, action("read"), null)).decision());

        ArgumentCaptor<UserContext> captor = ArgumentCaptor.forClass(UserContext.class);
        verify(adjudicator).adjudicateOperation(captor.capture(), eq("read"), eq(Map.of()));

        AnonymousUserContext userCtx = assertInstanceOf(AnonymousUserContext.class, captor.getValue());
        assertEquals(Set.of("@agent/claude-code", "@sandbox/sbx"), userCtx.getAttributeNames());
        assertEquals("txn-1", userCtx.getProcess());
    }

    private static List<Boolean> decisions(List<EvaluationResponse> results) {
        return results.stream().map(EvaluationResponse::decision).toList();
    }

    private static EvaluationRequest request(String operation) {
        return new EvaluationRequest(ALICE, DOC, action(operation), null);
    }

    private static EvaluationRequest.Action action(String name) {
        return new EvaluationRequest.Action(name, null);
    }
}
