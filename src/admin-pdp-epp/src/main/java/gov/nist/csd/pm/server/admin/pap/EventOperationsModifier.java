package gov.nist.csd.pm.server.admin.pap;

import com.eventstore.dbclient.EventData;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.pap.function.op.Operation;
import gov.nist.csd.pm.pap.modification.OperationsModifier;
import gov.nist.csd.pm.pap.pml.function.operation.PMLStmtsOperation;
import gov.nist.csd.pm.pap.store.PolicyStore;
import gov.nist.csd.pm.proto.event.PMEvent;
import gov.nist.csd.pm.proto.operation.AdminOperationCreated;
import gov.nist.csd.pm.proto.operation.AdminOperationDeleted;
import gov.nist.csd.pm.proto.operation.ResourceOperationsSet;
import java.util.List;

public class EventOperationsModifier extends OperationsModifier {

    private final List<EventData> events;

    public EventOperationsModifier(List<EventData> events, PolicyStore store) {
        super(store);

        this.events = events;
    }

    @Override
    public void setResourceOperations(AccessRightSet resourceOperations) throws PMException {
        byte[] bytes = PMEvent.newBuilder()
            .setResourceOperationsSet(
                ResourceOperationsSet.newBuilder()
                    .addAllOperations(resourceOperations)
            )
            .build()
            .toByteArray();
        events.add(
            EventData.builderAsBinary(ResourceOperationsSet.getDescriptor().getName(), bytes)
                .build());

        super.setResourceOperations(resourceOperations);
    }

    @Override
    public void createAdminOperation(Operation<?, ?> operation) throws PMException {
        if (!(operation instanceof PMLStmtsOperation pmlStmtsOperation)) {
            throw new PMException("only PML operations are supported");
        }

        byte[] bytes = PMEvent.newBuilder()
            .setAdminOperationCreated(
                AdminOperationCreated.newBuilder()
                    .setPml(pmlStmtsOperation.toFormattedString(0))
            )
            .build()
            .toByteArray();
        events.add(
            EventData.builderAsBinary(AdminOperationCreated.getDescriptor().getName(), bytes)
                .build());

        super.createAdminOperation(operation);
    }

    @Override
    public void deleteAdminOperation(String operation) throws PMException {
        byte[] bytes = PMEvent.newBuilder()
            .setAdminOperationDeleted(
                AdminOperationDeleted.newBuilder()
                    .setName(operation)
            )
            .build()
            .toByteArray();
        events.add(
            EventData.builderAsBinary(AdminOperationDeleted.getDescriptor().getName(), bytes)
                .build());

        super.deleteAdminOperation(operation);
    }
} 