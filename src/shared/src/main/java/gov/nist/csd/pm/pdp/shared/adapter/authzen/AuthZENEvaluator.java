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

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.pap.query.model.context.AnonymousUserContext;
import gov.nist.ngac.pm.core.pap.query.model.context.NodeUserContext;
import gov.nist.ngac.pm.core.pap.query.model.context.UserContext;
import gov.nist.ngac.pm.core.pdp.UnauthorizedException;
import gov.nist.ngac.pm.core.pdp.adjudication.AccessAdjudication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps AuthZEN evaluations onto operation adjudication. The action name is the operation and the
 * action properties are its args. Which operations are supported depends on the server's
 * {@link AccessAdjudication}.
 */
@Component
public class AuthZENEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(AuthZENEvaluator.class);

    private final AccessAdjudication adjudicator;

    public AuthZENEvaluator(AccessAdjudication adjudicator) {
        this.adjudicator = adjudicator;
    }

    public EvaluationResponse evaluate(EvaluationRequest request) throws PMException {
        request.validate();

        UserContext userCtx = toUserContext(request.subject());
        String operation = request.action().name();
        Map<String, Object> args = Objects.requireNonNullElse(request.action().properties(), Map.of());

        try {
            logger.info("userctx {}, operation {}, args {}", userCtx, operation, args);
            adjudicator.adjudicateOperation(userCtx, operation, args);
            return EvaluationResponse.of(true);
        } catch (UnauthorizedException e) {
            return EvaluationResponse.of(false);
        }
    }

    /**
     * Returns exactly one result per evaluation, in request order. Evaluations skipped by a short-circuiting
     * semantic are reported as denies so the result count always matches the request.
     */
    public EvaluationsResponse evaluateAll(EvaluationsRequest request) throws PMException {
        EvaluationRequest defaults = request.defaults();
        if (request.evaluations() == null || request.evaluations().isEmpty()) {
            return EvaluationsResponse.single(evaluateLogged(defaults).decision());
        }

        EvaluationsSemantic semantic = request.semantic();
        List<EvaluationResponse> results = new ArrayList<>();
        boolean stopped = false;
        for (EvaluationRequest evaluation : request.evaluations()) {
            if (stopped) {
                results.add(EvaluationResponse.skipped(semantic));
                continue;
            }

            EvaluationRequest effective = (evaluation != null ? evaluation : EvaluationRequest.EMPTY)
                    .withDefaults(defaults);
            EvaluationResponse result = evaluateCapturingErrors(effective);
            results.add(result);
            stopped = semantic.stopsAfter(result.decision());
        }

        return EvaluationsResponse.batch(results);
    }

    private EvaluationResponse evaluateCapturingErrors(EvaluationRequest request) {
        try {
            return evaluateLogged(request);
        } catch (Exception e) {
            logger.warn("authzen effective tuple: subject={}, action={}, resource={} -> error: {}",
                    request.subject(), request.action(), request.resource(), e.getMessage());
            return EvaluationResponse.error(AuthZENErrors.statusOf(e), e.getMessage());
        }
    }

    private EvaluationResponse evaluateLogged(EvaluationRequest effective) throws PMException {
        EvaluationResponse response = evaluate(effective);
        logger.info("authzen effective tuple: subject={}, action={}, resource={} -> {}",
                effective.subject(), effective.action(), effective.resource(), response.decision());
        return response;
    }

    /**
     * A subject with properties.attrs is an anonymous user made of those attributes. Otherwise the id is a
     * user node name, or a node id when properties.id_type is "id". properties.process_id is the process.
     */
    private static UserContext toUserContext(EvaluationRequest.Subject subject) {
        Map<String, Object> properties = Objects.requireNonNullElse(subject.properties(), Map.of());
        String process = properties.get("process_id") instanceof String p && !p.isBlank() ? p : null;

        if (properties.get("attrs") instanceof Collection<?> attrs && !attrs.isEmpty()) {
            Set<String> names = attrs.stream().map(String::valueOf).collect(Collectors.toSet());
            return AnonymousUserContext.ofNames(names, process);
        }

        if ("id".equals(properties.get("id_type"))) {
            return NodeUserContext.of(Long.parseLong(subject.id()), process);
        }

        return NodeUserContext.of(subject.id(), process);
    }
}
