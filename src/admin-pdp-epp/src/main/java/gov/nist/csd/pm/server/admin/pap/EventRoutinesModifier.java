package gov.nist.csd.pm.server.admin.pap;

import com.eventstore.dbclient.EventData;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.pap.function.routine.Routine;
import gov.nist.csd.pm.pap.modification.RoutinesModifier;
import gov.nist.csd.pm.pap.pml.function.routine.PMLStmtsRoutine;
import gov.nist.csd.pm.pap.store.PolicyStore;
import gov.nist.csd.pm.proto.event.PMEvent;
import gov.nist.csd.pm.proto.routine.AdminRoutineCreated;
import gov.nist.csd.pm.proto.routine.AdminRoutineDeleted;
import java.util.List;

public class EventRoutinesModifier extends RoutinesModifier {

    private final List<EventData> events;

    public EventRoutinesModifier(List<EventData> events, PolicyStore store) {
        super(store);

        this.events = events;
    }

    @Override
    public void createAdminRoutine(Routine<?, ?> routine) throws PMException {
        if (!(routine instanceof PMLStmtsRoutine pmlStmtsRoutine)) {
            throw new PMException("only PML routines are supported");
        }

        byte[] bytes = PMEvent.newBuilder()
            .setAdminRoutineCreated(
                AdminRoutineCreated.newBuilder()
                    .setPml(pmlStmtsRoutine.toFormattedString(0))
            )
            .build()
            .toByteArray();
        events.add(
            EventData.builderAsBinary(AdminRoutineCreated.getDescriptor().getName(), bytes)
                .build());

        super.createAdminRoutine(routine);
    }

    @Override
    public void deleteAdminRoutine(String name) throws PMException {
        byte[] bytes = PMEvent.newBuilder()
            .setAdminRoutineDeleted(
                AdminRoutineDeleted.newBuilder()
                    .setName(name)
            )
            .build()
            .toByteArray();
        events.add(
            EventData.builderAsBinary(AdminRoutineDeleted.getDescriptor().getName(), bytes)
                .build());

        super.deleteAdminRoutine(name);
    }
} 