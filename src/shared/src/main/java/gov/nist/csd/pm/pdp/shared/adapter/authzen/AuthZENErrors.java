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
import gov.nist.ngac.pm.core.common.exception.NodeDoesNotExistException;
import gov.nist.ngac.pm.core.common.exception.OperationDoesNotExistException;
import org.springframework.http.HttpStatus;

final class AuthZENErrors {

    private AuthZENErrors() {}

    /**
     * Client errors map to 400, everything else to 500. Causes are checked since adjudicators may wrap exceptions.
     */
    static HttpStatus statusOf(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (isClientError(t)) {
                return HttpStatus.BAD_REQUEST;
            }
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static boolean isClientError(Throwable t) {
        return t instanceof IllegalArgumentException
                || t instanceof OperationNotSupportedException
                || t instanceof OperationDoesNotExistException
                || t instanceof NodeDoesNotExistException;
    }
}
