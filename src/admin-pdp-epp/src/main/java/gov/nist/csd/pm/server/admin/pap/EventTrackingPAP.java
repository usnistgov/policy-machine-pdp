package gov.nist.csd.pm.server.admin.pap;

import com.eventstore.dbclient.AppendToStreamOptions;
import com.eventstore.dbclient.EventStoreDBClient;
import gov.nist.csd.pm.impl.neo4j.memory.pap.store.Neo4jMemoryPolicyStore;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.id.RandomIdGenerator;
import gov.nist.csd.pm.pap.query.AccessQuerier;
import gov.nist.csd.pm.pap.query.GraphQuerier;
import gov.nist.csd.pm.pap.query.ObligationsQuerier;
import gov.nist.csd.pm.pap.query.OperationsQuerier;
import gov.nist.csd.pm.pap.query.PolicyQuerier;
import gov.nist.csd.pm.pap.query.ProhibitionsQuerier;
import gov.nist.csd.pm.pap.query.RoutinesQuerier;

public class EventTrackingPAP extends PAP {

    public EventTrackingPAP(NoCommitNeo4jPolicyStore policyStore) {
        super(
            new PolicyQuerier(
                new GraphQuerier(policyStore),
                new ProhibitionsQuerier(policyStore),
                new ObligationsQuerier(policyStore),
                new OperationsQuerier(policyStore),
                new RoutinesQuerier(policyStore),
                new AccessQuerier(policyStore)
            ),
            EventTrackingPolicyModifier.createInstance(policyStore, new RandomIdGenerator()),
            policyStore
        );
    }

    @Override
    public EventTrackingPolicyModifier modify() {
        return (EventTrackingPolicyModifier) super.modify();
    }

    public void publishToEventStore(EventStoreDBClient esClient, String stream, long revision) {
        AppendToStreamOptions options = AppendToStreamOptions.get()
            .expectedRevision(revision);

        esClient.appendToStream(stream, options, modify().getEvents().iterator());
    }
}
