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

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluationResponse(boolean decision, Map<String, Object> context) {

    static EvaluationResponse of(boolean decision) {
        return new EvaluationResponse(decision, null);
    }

    /**
     * A failed evaluation within a batch. AuthZEN reports these as a deny with the error in the context.
     */
    static EvaluationResponse error(HttpStatus status, String message) {
        Map<String, Object> error = Map.of(
                "status", status.value(),
                "message", Objects.requireNonNullElse(message, status.getReasonPhrase())
        );
        return new EvaluationResponse(false, Map.of("error", error));
    }

    /**
     * An evaluation not run because an earlier result satisfied the evaluations semantic.
     */
    static EvaluationResponse skipped(EvaluationsSemantic semantic) {
        return new EvaluationResponse(false, Map.of("reason", "skipped by evaluations_semantic " + semantic.wireName()));
    }
}
