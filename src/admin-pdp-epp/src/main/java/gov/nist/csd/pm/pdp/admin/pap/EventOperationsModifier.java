package gov.nist.csd.pm.pdp.admin.pap;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.graph.relationship.AccessRightSet;
import gov.nist.csd.pm.core.pap.function.op.Operation;
import gov.nist.csd.pm.core.pap.modification.OperationsModifier;
import gov.nist.csd.pm.core.pap.pml.function.basic.PMLStmtsBasicFunction;
import gov.nist.csd.pm.core.pap.pml.function.operation.PMLStmtsOperation;
import gov.nist.csd.pm.core.pap.store.PolicyStore;
import gov.nist.csd.pm.pdp.proto.event.AdminOperationCreated;
import gov.nist.csd.pm.pdp.proto.event.AdminOperationDeleted;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.proto.event.ResourceOperationsSet;
import gov.nist.csd.pm.pdp.shared.plugin.PluginLoader;

import java.util.List;

public class EventOperationsModifier extends OperationsModifier {

    private final List<PMEvent> events;
    private final PluginLoader pluginLoader;

    public EventOperationsModifier(List<PMEvent> events, PolicyStore store, PluginLoader pluginLoader) {
        super(store);

        this.events = events;
        this.pluginLoader = pluginLoader;
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
        String pml;
        if (operation instanceof PMLStmtsOperation pmlStmtsOperation) {
            pml = pmlStmtsOperation.toFormattedString(0);
        } else if (operation instanceof PMLStmtsBasicFunction pmlStmtsBasicFunction) {
            pml = pmlStmtsBasicFunction.toFormattedString(0);
        } else if (pluginLoader.pluginExists(operation.getName())) {
            return;
        } else {
            throw new PMException("only PML operations are supported");
        }

        PMEvent event = PMEvent.newBuilder()
                .setAdminOperationCreated(
                        AdminOperationCreated.newBuilder()
                                .setPml(pml)
                )
                .build();
        events.add(event);

        super.createAdminOperation(operation);
    }

    @Override
    public void deleteAdminOperation(String operation) throws PMException {
        if (pluginLoader.pluginExists(operation)) {
            throw new PMException("cannot delete plugin operations");
        }

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