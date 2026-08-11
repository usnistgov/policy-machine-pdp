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