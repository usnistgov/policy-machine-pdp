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

import java.util.Map;

public record EvaluationRequest(Subject subject, Resource resource, Action action, Map<String, Object> context) {

    static final EvaluationRequest EMPTY = new EvaluationRequest(null, null, null, null);

    public record Subject(String type, String id, Map<String, Object> properties) {}

    public record Resource(String type, String id, Map<String, Object> properties) {}

    public record Action(String name, Map<String, Object> properties) {}

    /**
     * Returns a copy with any omitted field taken whole from the given defaults.
     */
    EvaluationRequest withDefaults(EvaluationRequest defaults) {
        return new EvaluationRequest(
                subject != null ? subject : defaults.subject,
                resource != null ? resource : defaults.resource,
                action != null ? action : defaults.action,
                context != null ? context : defaults.context
        );
    }

    /**
     * @throws IllegalArgumentException if a field required by AuthZEN is missing.
     */
    void validate() {
        if (subject == null || isBlank(subject.type()) || isBlank(subject.id())) {
            throw new IllegalArgumentException("incomplete evaluation: subject.type and subject.id are required");
        }
        if (resource == null || isBlank(resource.type()) || isBlank(resource.id())) {
            throw new IllegalArgumentException("incomplete evaluation: resource.type and resource.id are required");
        }
        if (action == null || isBlank(action.name())) {
            throw new IllegalArgumentException("incomplete evaluation: action.name is required");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
