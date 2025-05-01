package gov.nist.csd.pm.server.admin.pdp;

import com.eventstore.dbclient.WrongExpectedVersionException;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.common.exception.PMRuntimeException;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pdp.PDP;
import gov.nist.csd.pm.server.admin.pap.EventTrackingPAP;
import gov.nist.csd.pm.server.admin.pap.NoCommitNeo4jPolicyStore;
import gov.nist.csd.pm.server.shared.auth.UserContextInterceptor;
import gov.nist.csd.pm.server.shared.resilience.PMRetry;
import io.github.resilience4j.retry.Retry;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import org.neo4j.graphdb.GraphDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Adjudicator<R> {

    private static final Logger logger = LoggerFactory.getLogger(Adjudicator.class);

    private final String eventStream;
    private final GraphDatabaseService graphDB;
    private final EventStoreConnectionManager esConnMgr;
    private final AtomicLong currentRevision;

    public Adjudicator(String eventStream,
                       GraphDatabaseService graphDB,
                       EventStoreConnectionManager esConnMgr,
                       AtomicLong currentRevision) {
        this.eventStream = eventStream;
        this.graphDB = graphDB;
        this.esConnMgr = esConnMgr;
        this.currentRevision = currentRevision;
    }

    public R adjudicate(Function<NGACContext, R> adjudicationConsumer) {
        Supplier<R> supplier = () -> {
            try {
                long revision = currentRevision.get();
                EventTrackingPAP pap = initEventPAP();
                PDP pdp = new PDP(pap);
                UserContext userCtx = getUserContext(pap);

                R result = adjudicationConsumer.apply(new NGACContext(userCtx, pdp, pap));

                pap.publishToEventStore(esConnMgr.getOrInitClient(), eventStream, revision);

                return result;
            } catch (PMException e) {
                throw new PMRuntimeException(e);
            }
        };

        Retry retry = new PMRetry<>("Adjudicator", WrongExpectedVersionException.class);

        return Retry.decorateSupplier(retry, supplier).get();
    }

    private EventTrackingPAP initEventPAP() throws PMException {
        NoCommitNeo4jPolicyStore neo4jMemoryPolicyStore = new NoCommitNeo4jPolicyStore(graphDB);
        return new EventTrackingPAP(neo4jMemoryPolicyStore);
    }

    private UserContext getUserContext(PAP pap) throws PMException {
        String pmUserHeaderValue = UserContextInterceptor.getPmUserHeaderValue();
        String pmProcessHeaderValue = UserContextInterceptor.getPmProcessHeaderValue();

        return new UserContext(
            pap.query().graph().getNodeByName(pmUserHeaderValue).getId(),
            pmProcessHeaderValue
        );
    }

}
