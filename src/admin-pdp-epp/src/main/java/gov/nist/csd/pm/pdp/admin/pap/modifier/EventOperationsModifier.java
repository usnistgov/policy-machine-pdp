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
 * licensed to NIST.
 *
 * This file is part of the admin-pdp-epp module, which compiles against
 * and embeds org.neo4j:neo4j (GPLv3, Community Edition). As a combined work, this
 * module is distributed under the GNU General Public License v3.0, not the CC BY 4.0
 * license used elsewhere in this repository. See admin-pdp-epp/LICENSE for the full text.
 */

package gov.nist.csd.pm.pdp.admin.pap.modifier;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.pap.operation.JavaOperationRegistry;
import gov.nist.ngac.pm.core.pap.modification.OperationsModifier;
import gov.nist.ngac.pm.core.pap.operation.Operation;
import gov.nist.ngac.pm.core.pap.operation.accessright.AccessRightSet;
import gov.nist.ngac.pm.core.pap.pml.statement.PMLStatementSerializable;
import gov.nist.ngac.pm.core.pap.store.PolicyStore;
import gov.nist.csd.pm.pdp.proto.event.OperationCreated;
import gov.nist.csd.pm.pdp.proto.event.OperationDeleted;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.proto.event.ResourceAccessRightsSet;

import java.util.List;

public class EventOperationsModifier extends OperationsModifier {

    private final List<PMEvent> events;

    public EventOperationsModifier(List<PMEvent> events, PolicyStore store, JavaOperationRegistry pluginRegistry) {
        super(store, pluginRegistry);

        this.events = events;
    }

    @Override
    public void setResourceAccessRights(AccessRightSet resourceAccessRights) throws PMException {
        PMEvent event = PMEvent.newBuilder()
                .setResourceAccessRightsSet(
                        ResourceAccessRightsSet.newBuilder()
                                .addAllOperations(resourceAccessRights)
                )
                .build();
        events.add(event);

        super.setResourceAccessRights(resourceAccessRights);
    }

    @Override
    public void createOperation(Operation<?> operation) throws PMException {
        String pml;
        if (operation instanceof PMLStatementSerializable pmlStmtsOperation) {
            pml = pmlStmtsOperation.toFormattedString(0);
        } else {
            throw new PMException("only PML operations are supported");
        }

        PMEvent event = PMEvent.newBuilder()
                .setOperationCreated(
                        OperationCreated.newBuilder()
                                .setPml(pml)
                )
                .build();
        events.add(event);

        super.createOperation(operation);
    }

    @Override
    public void deleteOperation(String name) throws PMException {
        PMEvent event = PMEvent.newBuilder()
                .setOperationDeleted(
                        OperationDeleted.newBuilder()
                                .setName(name)
                )
                .build();
        events.add(event);

        super.deleteOperation(name);
    }
} 