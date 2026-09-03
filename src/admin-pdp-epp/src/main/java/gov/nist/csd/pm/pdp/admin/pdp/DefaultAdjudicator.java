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

package gov.nist.csd.pm.pdp.admin.pdp;

import com.eventstore.dbclient.WrongExpectedVersionException;
import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.common.exception.PMRuntimeException;
import gov.nist.ngac.pm.core.grpc.util.FromProtoUtil;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.pdp.admin.pap.EventTrackingPAP;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import gov.nist.ngac.pm.proto.v1.pdp.adjudication.OperationRequest;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import gov.nist.csd.pm.pdp.shared.config.DefaultMode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
@DefaultMode
public class DefaultAdjudicator implements AdminAdjudicator {

    private static final Logger logger = LoggerFactory.getLogger(DefaultAdjudicator.class);

    private final CurrentRevisionService currentRevision;
    private final Retry retry;
    private final EventStoreDBConfig eventStoreDBConfig;
    private final EventStoreConnectionManager eventStoreConnectionManager;
    private final ContextFactory contextFactory;

    public DefaultAdjudicator(EventStoreDBConfig eventStoreDBConfig,
                              EventStoreConnectionManager eventStoreConnectionManager,
                              CurrentRevisionService currentRevision,
                              ContextFactory contextFactory) {
        this.eventStoreDBConfig = eventStoreDBConfig;
        this.eventStoreConnectionManager = eventStoreConnectionManager;
        this.currentRevision = currentRevision;
        this.contextFactory = contextFactory;
        this.retry = Retry.of("DefaultAdjudicator", RetryConfig.custom()
                .retryExceptions(WrongExpectedVersionException.class)
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .build());
    }

    @Override
    public Object adjudicateOperation(String operation, Map<String, Object> args) throws PMException {
        Supplier<Object> supplier = () -> {
            try {
                NGACContext ctx = contextFactory.createContext();
                UserContext userContext = contextFactory.createUserContext(ctx.pap());

                Object result = ctx.pdp().adjudicateOperation(userContext, operation, args);
                publishEvents(ctx.pap());

                return result;
            } catch (WrongExpectedVersionException e) {
                // let the retry observe version conflicts so they can be retried
                throw e;
            } catch (Exception e) {
                throw new PMRuntimeException(e);
            }
        };

        return executeWithRetry(supplier);
    }

    @Override
    public void adjudicateRoutine(List<OperationRequest> adminCommands) throws PMException {
        adjudicateTransaction(ctx -> {
            UserContext userContext = contextFactory.createUserContext(ctx.pap());

            // Execute every command in a single transaction. adjudicateTransaction publishes the
            // accumulated events to the event store once, after the transaction completes.
            ctx.pdp().runTx(userContext, pdpTx -> {
                for (OperationRequest operationRequest : adminCommands) {
                    ctx.pdp().adjudicateOperation(userContext,
                                                  operationRequest.getName(),
                                                  FromProtoUtil.fromValueMap(operationRequest.getArgs()));
                }

                return null;
            });
        });
    }

    @Override
    public <R> R adjudicateQuery(PDPTxFunction<R> consumer) throws PMException {
        NGACContext ctx = contextFactory.createContext();
        UserContext userCtx = contextFactory.createUserContext(ctx.pap());
        return consumer.apply(ctx.pap(), userCtx);
    }

    @Override
    public Object executePML(String pml) throws PMException {
        Supplier<Object> supplier = () -> {
            try {
                NGACContext ctx = contextFactory.createContext();
                UserContext userContext = contextFactory.createUserContext(ctx.pap());

                ctx.pdp().executePML(userContext, pml);

                publishEvents(ctx.pap());

                return null;
            } catch (WrongExpectedVersionException e) {
                // let the retry observe version conflicts so they can be retried
                throw e;
            } catch (Exception e) {
                throw new PMRuntimeException(e);
            }
        };

        return executeWithRetry(supplier);
    }

    @Override
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

    private long publishEvents(PAP papBase) throws PMException {
        EventTrackingPAP pap = (EventTrackingPAP) papBase;
        long revision = currentRevision.get();
        return pap.publishToEventStore(
                eventStoreConnectionManager.getOrInitClient(),
                eventStoreDBConfig.getEventStream(),
                revision
        );
    }
}
