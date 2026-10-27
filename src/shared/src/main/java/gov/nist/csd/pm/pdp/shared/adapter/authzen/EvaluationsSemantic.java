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

import java.util.Locale;

public enum EvaluationsSemantic {

    @JsonProperty("execute_all")
    EXECUTE_ALL,

    @JsonProperty("deny_on_first_deny")
    DENY_ON_FIRST_DENY,

    @JsonProperty("permit_on_first_permit")
    PERMIT_ON_FIRST_PERMIT;

    String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    boolean stopsAfter(boolean decision) {
        return switch (this) {
            case EXECUTE_ALL -> false;
            case DENY_ON_FIRST_DENY -> !decision;
            case PERMIT_ON_FIRST_PERMIT -> decision;
        };
    }
}
