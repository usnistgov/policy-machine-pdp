package gov.nist.csd.pm.pdp.admin.pap;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.core.pap.function.op.Operation;
import gov.nist.csd.pm.core.pap.modification.OperationsModifier;
import gov.nist.csd.pm.core.pap.pml.function.operation.PMLStmtsOperation;
import gov.nist.csd.pm.core.pap.store.PolicyStore;
import gov.nist.csd.pm.pdp.proto.event.AdminOperationCreated;
import gov.nist.csd.pm.pdp.proto.event.AdminOperationDeleted;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.proto.event.ResourceOperationsSet;

import java.util.List;

public class EventOperationsModifier extends OperationsModifier {

    private final List<PMEvent> events;

    public EventOperationsModifier(List<PMEvent> events, PolicyStore store) {
        super(store);

        this.events = events;
    }

    @Override
    public void setResourceOperations(AccessRightSet resourceOperations) throws PMException {
        PMEvent event = PMEvent.newBuilder()
            .setResourceOperationsSet(
                    ResourceOperationsSet.newBuilder()
                    .addAllOperations(resourceOperations)
            )
            .build();
        events.add(event);

        super.setResourceOperations(resourceOperations);
    }

    @Override
    public void createAdminOperation(Operation<?, ?> operation) throws PMException {
        if (!(operation instanceof PMLStmtsOperation pmlStmtsOperation)) {
            throw new PMException("only PML operations are supported");
        }

        PMEvent event = PMEvent.newBuilder()
            .setAdminOperationCreated(
                    AdminOperationCreated.newBuilder()
                    .setPml(pmlStmtsOperation.toFormattedString(0))
            )
            .build();
        events.add(event);

        super.createAdminOperation(operation);
    }

    @Override
    public void deleteAdminOperation(String operation) throws PMException {
        PMEvent event = PMEvent.newBuilder()
            .setAdminOperationDeleted(
                    AdminOperationDeleted.newBuilder()
                    .setName(operation)
            )
            .build();
        events.add(event);

        super.deleteAdminOperation(operation);
    }
} 