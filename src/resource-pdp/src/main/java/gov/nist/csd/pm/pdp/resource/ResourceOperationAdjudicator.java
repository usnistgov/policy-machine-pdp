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

package gov.nist.csd.pm.pdp.resource;

import gov.nist.csd.pm.pdp.shared.adjudication.OperationNotSupportedException;
import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.operation.Operation;
import gov.nist.ngac.pm.core.pap.operation.ResourceOperation;
import gov.nist.ngac.pm.core.pap.query.model.context.UserContext;
import gov.nist.ngac.pm.core.pdp.PDP;
import gov.nist.ngac.pm.core.pdp.adjudication.AccessAdjudication;
import gov.nist.ngac.pm.core.pdp.adjudication.OperationRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Adjudicates operations against the in-memory PDP. Only resource operations are supported since
 * the resource-pdp cannot persist admin changes. Primary over the raw PDP bean, which also
 * implements AccessAdjudication.
 */
@Component
@Primary
public class ResourceOperationAdjudicator implements AccessAdjudication {

    private final PDP pdp;
    private final PAP pap;

    public ResourceOperationAdjudicator(PDP pdp, PAP pap) {
        this.pdp = pdp;
        this.pap = pap;
    }

    @Override
    public Object adjudicateOperation(UserContext userCtx, String operation, Map<String, Object> args) throws PMException {
        checkIsResourceOperation(operation);
        return pdp.adjudicateOperation(userCtx, operation, args);
    }

    @Override
    public void adjudicateRoutine(UserContext userCtx, List<OperationRequest> operations) throws PMException {
        for (OperationRequest op : operations) {
            checkIsResourceOperation(op.op());
        }

        pdp.adjudicateRoutine(userCtx, operations);
    }

    private void checkIsResourceOperation(String operation) throws PMException {
        Operation<?> op = pap.query().operations().getOperation(operation);
        if (!(op instanceof ResourceOperation<?>)) {
            throw new OperationNotSupportedException(
                    "only subclasses of ResourceOperation are allowed to be invoked in the resource-pdp");
        }
    }
}
