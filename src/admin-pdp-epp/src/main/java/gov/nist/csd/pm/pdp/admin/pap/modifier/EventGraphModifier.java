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
import gov.nist.ngac.pm.core.pap.id.IdGenerator;
import gov.nist.ngac.pm.core.pap.modification.GraphModifier;
import gov.nist.ngac.pm.core.pap.operation.accessright.AccessRightSet;
import gov.nist.ngac.pm.core.pap.store.PolicyStore;
import gov.nist.csd.pm.pdp.proto.event.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class EventGraphModifier extends GraphModifier {

    private final List<PMEvent> events;

    public EventGraphModifier(List<PMEvent> events,
                              PolicyStore store,
                              IdGenerator idGenerator) {
        super(store, idGenerator);

        this.events = events;
    }

    @Override
    public long createPolicyClass(String name) throws PMException {
        long id = super.createPolicyClass(name);

        PMEvent event = PMEvent.newBuilder()
            .setPolicyClassCreated(
                    PolicyClassCreated.newBuilder()
                    .setId(id)
                    .setName(name)
            )
            .build();
        events.add(event);

        return id;
    }

    @Override
    public long createUserAttribute(String name, Collection<Long> assignments) throws
                                                                               PMException {
        long id = super.createUserAttribute(name, assignments);

        PMEvent event = PMEvent.newBuilder()
            .setUserAttributeCreated(
                    UserAttributeCreated.newBuilder()
                    .setId(id)
                    .setName(name)
                    .addAllDescendants(assignments)
            )
            .build();
        events.add(event);

        return id;
    }

    @Override
    public long createObjectAttribute(String name, Collection<Long> assignments) throws
                                                                                 PMException {
        long id = super.createObjectAttribute(name, assignments);

        PMEvent event = PMEvent.newBuilder()
            .setObjectAttributeCreated(
                    ObjectAttributeCreated.newBuilder()
                    .setId(id)
                    .setName(name)
                    .addAllDescendants(assignments)
            )
            .build();
        events.add(event);

        return id;
    }

    @Override
    public long createObject(String name, Collection<Long> assignments) throws PMException {
        long id = super.createObject(name, assignments);

        PMEvent event = PMEvent.newBuilder()
            .setObjectCreated(
                    ObjectCreated.newBuilder()
                    .setId(id)
                    .setName(name)
                    .addAllDescendants(assignments)
            )
            .build();
        events.add(event);

        return id;
    }

    @Override
    public long createUser(String name, Collection<Long> assignments) throws PMException {
        long id = super.createUser(name, assignments);

        PMEvent event = PMEvent.newBuilder()
            .setUserCreated(
                UserCreated.newBuilder()
                    .setId(id)
                    .setName(name)
                    .addAllDescendants(assignments)
            )
            .build();
        events.add(event);

        return id;
    }

    @Override
    public void setNodeProperties(long id, Map<String, String> properties) throws PMException {
        PMEvent event = PMEvent.newBuilder()
            .setNodePropertiesSet(
                NodePropertiesSet.newBuilder()
                    .setId(id)
                    .putAllProperties(properties)
            )
            .build();
        events.add(event);

        super.setNodeProperties(id, properties);
    }

    @Override
    public void deleteNode(long id) throws PMException {
        PMEvent event = PMEvent.newBuilder()
            .setNodeDeleted(
                NodeDeleted.newBuilder()
                    .setId(id)
            )
            .build();
        events.add(event);

        super.deleteNode(id);
    }

    @Override
    public void assign(long ascendant, Collection<Long> descendants) throws PMException {
        PMEvent event = PMEvent.newBuilder()
            .setAssignmentCreated(
                AssignmentCreated.newBuilder()
                    .setAscendant(ascendant)
                    .addAllDescendants(descendants)
            )
            .build();
        events.add(event);

        super.assign(ascendant, descendants);
    }

    @Override
    public void deassign(long ascendant, Collection<Long> descendants) throws PMException {
        PMEvent event = PMEvent.newBuilder()
            .setAssignmentDeleted(
                AssignmentDeleted.newBuilder()
                    .setAscendant(ascendant)
                    .addAllDescendants(descendants)
            )
            .build();
        events.add(event);

        super.deassign(ascendant, descendants);
    }

    @Override
    public void associate(long ua, long target, AccessRightSet accessRights) throws PMException {
        PMEvent event = PMEvent.newBuilder()
            .setAssociationCreated(
                AssociationCreated.newBuilder()
                    .setUa(ua)
                    .setTarget(target)
                    .addAllArset(accessRights)
            )
            .build();
        events.add(event);

        super.associate(ua, target, accessRights);
    }

    @Override
    public void dissociate(long ua, long target) throws PMException {
        PMEvent event = PMEvent.newBuilder()
            .setAssociationDeleted(
                AssociationDeleted.newBuilder()
                    .setUa(ua)
                    .setTarget(target)
            )
            .build();
        events.add(event);

        super.dissociate(ua, target);
    }
} 