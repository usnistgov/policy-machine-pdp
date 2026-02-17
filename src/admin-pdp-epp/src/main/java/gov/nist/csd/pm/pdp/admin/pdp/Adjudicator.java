package gov.nist.csd.pm.pdp.admin.pdp;

import com.eventstore.dbclient.WrongExpectedVersionException;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.exception.PMRuntimeException;
import gov.nist.csd.pm.core.impl.grpc.util.FromProtoUtil;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pdp.admin.pap.EventTrackingPAP;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import gov.nist.csd.pm.proto.v1.pdp.adjudication.OperationRequest;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class Adjudicator {

    private static final Logger logger = LoggerFactory.getLogger(Adjudicator.class);

    private final CurrentRevisionService currentRevision;
    private final Retry retry;
    private final EventStoreDBConfig eventStoreDBConfig;
    private final EventStoreConnectionManager eventStoreConnectionManager;
    private final ContextFactory contextFactory;

    public Adjudicator(EventStoreDBConfig eventStoreDBConfig,
                       EventStoreConnectionManager eventStoreConnectionManager,
                       CurrentRevisionService currentRevision,
                       ContextFactory contextFactory) {
        this.eventStoreDBConfig = eventStoreDBConfig;
        this.eventStoreConnectionManager = eventStoreConnectionManager;
        this.currentRevision = currentRevision;
        this.contextFactory = contextFactory;
        this.retry = Retry.of("Adjudicator", RetryConfig.custom()
                .retryExceptions(WrongExpectedVersionException.class)
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .build());
    }

    public Object adjudicateOperation(String operation, Map<String, Object> args) throws PMException {
        Supplier<Object> supplier = () -> {
            try {
                NGACContext ctx = contextFactory.createContext();
                UserContext userContext = contextFactory.createUserContext(ctx.pap());

                Object result = ctx.pdp().adjudicateOperation(userContext, operation, args);
                publishEvents(ctx.pap());

                return result;
            } catch (Exception e) {
                throw new PMRuntimeException(e);
            }
        };

        return executeWithRetry(supplier);
    }

    public void adjudicateRoutine(List<OperationRequest> adminCommands) throws PMException {
        adjudicateTransaction(ctx -> {
            UserContext userContext = contextFactory.createUserContext(ctx.pap());

            ctx.pdp().runTx(userContext, pdpTx -> {
                for (OperationRequest operationRequest : adminCommands) {
                    try {
                        Object result = ctx.pdp().adjudicateOperation(userContext,
                                                                      operationRequest.getName(),
                                                                      FromProtoUtil.fromValueMap(operationRequest.getArgs()));
                        publishEvents(ctx.pap());

                        return result;
                    } catch (Exception e) {
                        throw new PMRuntimeException(e);
                    }
                }

                return null;
            });
        });
    }

    public <R> R adjudicateQuery(PDPTxFunction<R> consumer) throws PMException {
        NGACContext ctx = contextFactory.createContext();

        return ctx.pdp().runTx(contextFactory.createUserContext(ctx.pap()), pdpTx -> consumer.apply(ctx.pap(), pdpTx));
    }

    public Object executePML(String pml) throws PMException {
        NGACContext ctx = contextFactory.createContext();

        return ctx.pdp().runTx(contextFactory.createUserContext(ctx.pap()), pdpTx -> pdpTx.executePML(pml));
    }

    /**
     * Executes a transaction consumer and returns the last event's revision.
     *
     * @param txConsumer The transaction consumer to execute
     * @return The revision of the last event in the transaction.
     */
    public long adjudicateTransaction(PMConsumer<NGACContext> txConsumer) throws PMException {
        Supplier<Long> supplier = () -> {
            try {
                NGACContext ctx = contextFactory.createContext();
                txConsumer.accept(ctx);
                return publishEvents(ctx.pap());
            } catch (PMException e) {
                throw new PMRuntimeException(e);
            }
        };

        return executeWithRetry(supplier);
    }

    private <T> T executeWithRetry(Supplier<T> supplier) throws PMException {
        try {
            return Retry.decorateSupplier(retry, supplier).get();
        } catch (PMRuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PMException p) {
                throw p;
            } else if (cause instanceof WrongExpectedVersionException w) {
                throw new PMException(w.getMessage());
            }

            throw e;
        }
    }

    private long publishEvents(EventTrackingPAP pap) throws PMException {
        long revision = currentRevision.get();
        return pap.publishToEventStore(
                eventStoreConnectionManager.getOrInitClient(),
                eventStoreDBConfig.getEventStream(),
                revision
        );
    }
}