package gov.nist.csd.pm.pdp.admin.pap;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.pap.function.routine.Routine;
import gov.nist.csd.pm.core.pap.modification.RoutinesModifier;
import gov.nist.csd.pm.core.pap.pml.function.routine.PMLStmtsRoutine;
import gov.nist.csd.pm.core.pap.store.PolicyStore;
import gov.nist.csd.pm.pdp.proto.event.AdminRoutineCreated;
import gov.nist.csd.pm.pdp.proto.event.AdminRoutineDeleted;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;

import java.util.List;

public class EventRoutinesModifier extends RoutinesModifier {

    private final List<PMEvent> events;

    public EventRoutinesModifier(List<PMEvent> events, PolicyStore store) {
        super(store);

        this.events = events;
    }

    @Override
    public void createAdminRoutine(Routine<?, ?> routine) throws PMException {
        if (!(routine instanceof PMLStmtsRoutine pmlStmtsRoutine)) {
            throw new PMException("only PML routines are supported");
        }

        PMEvent event = PMEvent.newBuilder()
            .setAdminRoutineCreated(
                    AdminRoutineCreated.newBuilder()
                    .setPml(pmlStmtsRoutine.toFormattedString(0))
            )
            .build();
        events.add(event);

        super.createAdminRoutine(routine);
    }

    @Override
    public void deleteAdminRoutine(String name) throws PMException {
        PMEvent event = PMEvent.newBuilder()
            .setAdminRoutineDeleted(
                    AdminRoutineDeleted.newBuilder()
                    .setName(name)
            )
            .build();
        events.add(event);

        super.deleteAdminRoutine(name);
    }
} 