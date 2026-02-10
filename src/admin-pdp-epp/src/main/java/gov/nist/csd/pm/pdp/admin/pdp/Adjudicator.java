package gov.nist.csd.pm.pdp.admin.pdp;

import com.eventstore.dbclient.WrongExpectedVersionException;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.exception.PMRuntimeException;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pdp.admin.pap.EventTrackingPAP;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import gov.nist.csd.pm.proto.v1.pdp.cmd.AdminOperationCommand;
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

    private final CommandHandler commandHandler;
    private final CurrentRevisionService currentRevision;
    private final Retry retry;
    private final EventStoreDBConfig eventStoreDBConfig;
    private final EventStoreConnectionManager eventStoreConnectionManager;
    private final ContextFactory contextFactory;

    public Adjudicator(CommandHandler commandHandler,
                       EventStoreDBConfig eventStoreDBConfig,
                       EventStoreConnectionManager eventStoreConnectionManager,
                       CurrentRevisionService currentRevision,
                       ContextFactory contextFactory) {
        this.commandHandler = commandHandler;
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

    /**
     * Adjudicates a transaction function and returns the result.
     *
     * @param <R> The return type
     * @param consumer The transaction function to execute
     * @return The result of the transaction
     */
    public <R> R adjudicateQuery(PDPTxFunction<R> consumer) throws PMException {
        NGACContext ctx = contextFactory.createContext();

        return ctx.pdp().runTx(contextFactory.createUserContext(ctx.pap()), pdpTx -> consumer.apply(ctx.pap(), pdpTx));
    }

    /**
     * Adjudicates a list of administrative commands.
     *
     * @param adminCommands The commands to adjudicate
     */
    public void adjudicateRoutine(List<AdminOperationCommand> adminCommands) throws PMException {
        adjudicateTransaction(ctx -> {
            UserContext userContext = contextFactory.createUserContext(ctx.pap());

            ctx.pdp().runTx(userContext, pdpTx -> {
                for (AdminOperationCommand adminCommand : adminCommands) {
                    commandHandler.handleCommand(ctx, pdpTx, adminCommand);
                }

                return null;
            });
        });
    }

    public Object adjudicateOperation(String operation, Map<String, Object> args) throws PMException {
        Supplier<Object> supplier = () -> {
            try {
                NGACContext ctx = contextFactory.createContext();
                UserContext userContext = contextFactory.createUserContext(ctx.pap());

                Object result = ctx.pdp().adjudicateOperation(userContext, operation, args);
                publishEvents(ctx.pap());

                return result;
            } catch (PMException e) {
                throw new PMRuntimeException(e);
            }
        };

        return executeWithRetry(supplier);
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