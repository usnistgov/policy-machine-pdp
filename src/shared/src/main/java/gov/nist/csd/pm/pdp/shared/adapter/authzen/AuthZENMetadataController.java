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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * AuthZEN PDP metadata discovery. The PDP identifier is the base URL the metadata was requested from.
 */
@RestController
public class AuthZENMetadataController {

    @GetMapping("/.well-known/authzen-configuration")
    public Metadata metadata() {
        String pdp = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        String base = pdp + AuthZENController.BASE_PATH;

        return new Metadata(
                pdp,
                base + AuthZENController.EVALUATION_PATH,
                base + AuthZENController.EVALUATIONS_PATH
        );
    }

    public record Metadata(@JsonProperty("policy_decision_point") String policyDecisionPoint,
                           @JsonProperty("access_evaluation_endpoint") String accessEvaluationEndpoint,
                           @JsonProperty("access_evaluations_endpoint") String accessEvaluationsEndpoint) {}
}
