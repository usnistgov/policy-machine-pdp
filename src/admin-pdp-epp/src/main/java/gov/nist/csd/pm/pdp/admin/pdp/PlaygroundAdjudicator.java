package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.epp.EPP;
import gov.nist.csd.pm.core.epp.EventContext;
import gov.nist.csd.pm.core.impl.grpc.util.FromProtoUtil;
import gov.nist.csd.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.operation.AdminOperation;
import gov.nist.csd.pm.core.pap.operation.Operation;
import gov.nist.csd.pm.core.pap.operation.arg.Args;
import gov.nist.csd.pm.core.pap.pml.operation.PMLOperation;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.shared.auth.UserContextResolver;
import gov.nist.csd.pm.pdp.shared.config.PlaygroundMode;
import gov.nist.csd.pm.proto.v1.pdp.adjudication.OperationRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory adjudicator for playground mode. Mirrors {@link DefaultAdjudicator}'s PDP path over a single
 * shared {@link MemoryPAP} (no Neo4j, no EventStoreDB), but the {@link PDP} it uses is a {@link PlaygroundPDP}
 * that skips NGAC privilege checks so any user can freely exercise the policy. Obligations/EPP still fire:
 * {@link PlaygroundPDP} publishes the same admin-operation events that a real {@code PDPTx} would, it just
 * never calls {@code canExecute}.
 *
 * <p>Because the PDP/EPP and PAP are shared singletons across gRPC worker threads (unlike default mode,
 * which builds a fresh per-request context), mutating and querying entry points are serialized on
 * {@link #lock}.
 */
@Component
@PlaygroundMode
public class PlaygroundAdjudicator implements AdminAdjudicator {

    private final MemoryPAP pap;
    private final UserContextResolver userContextResolver;
    private final PlaygroundPDP pdp;
    private final EPP epp;
    private final Object lock = new Object();

    public PlaygroundAdjudicator(MemoryPAP pap, UserContextResolver userContextResolver) throws PMException {
        this.pap = pap;
        this.userContextResolver = userContextResolver;

        this.pdp = new PlaygroundPDP(pap);
        this.epp = new EPP(pdp, pap);
        pdp.setEpp(epp);
    }

    @Override
    public Object adjudicateOperation(String operation, Map<String, Object> args) throws PMException {
        synchronized (lock) {
            UserContext userCtx = userContextResolver.resolve(pap);
            return pdp.adjudicateOperation(userCtx, operation, args);
        }
    }

    @Override
    public void adjudicateRoutine(List<OperationRequest> adminCommands) throws PMException {
        synchronized (lock) {
            UserContext userCtx = userContextResolver.resolve(pap);
            for (OperationRequest cmd : adminCommands) {
                pdp.adjudicateOperation(userCtx, cmd.getName(), FromProtoUtil.fromValueMap(cmd.getArgs()));
            }
        }
    }

    @Override
    public <R> R adjudicateQuery(PDPTxFunction<R> consumer) throws PMException {
        synchronized (lock) {
            UserContext userCtx = userContextResolver.resolve(pap);
            return consumer.apply(pap, userCtx);
        }
    }

    @Override
    public Object executePML(String pml) throws PMException {
        synchronized (lock) {
            UserContext userCtx = userContextResolver.resolve(pap);
            return pdp.executePML(userCtx, pml);
        }
    }

    @Override
    public long adjudicateTransaction(PMConsumer<NGACContext> txConsumer) throws PMException {
        synchronized (lock) {
            txConsumer.accept(new NGACContext(pdp, epp, pap));
            // no EventStoreDB in playground mode, so there is no revision to report
            return -1;
        }
    }

    /**
     * A {@link PDP} that overrides {@link #adjudicateOperation} and {@link #executePML} to skip the
     * {@code canExecute} privilege gate and execute straight against the underlying {@link PAP}, while still
     * publishing admin-operation events to {@link #epp} so obligations keep firing. {@link #runTx} is left
     * untouched, so {@code EPP.processEvent}'s own use of it to run obligation responses still goes through
     * the real, privilege-checked {@code PDPTx} machinery.
     */
    private static final class PlaygroundPDP extends PDP {

        private final PAP playgroundPAP;
        private EPP epp;

        PlaygroundPDP(PAP pap) throws PMException {
            super(pap);
            this.playgroundPAP = new PlaygroundPAP(pap);
        }

        void setEpp(EPP epp) {
            this.epp = epp;
        }

        @Override
        public Object adjudicateOperation(UserContext user, String resourceOperation, Map<String, Object> rawArgs) throws PMException {
            Operation<?> op = pap.query().operations().getOperation(resourceOperation);
            Args args = op.validateArgs(rawArgs);

            AtomicReference<Object> result = new AtomicReference<>();
            playgroundPAP.runTx(tx -> result.set(tx.executeOperation(op, user, args)));
            return result.get();
        }

        @Override
        public Object executePML(UserContext userCtx, String pml) throws PMException {
            AtomicReference<Object> ret = new AtomicReference<>();
            playgroundPAP.runTx(tx -> {
                ret.set(tx.executePML(userCtx, pml));
            });
            return ret.get();
        }

        private final class PlaygroundPAP extends PAP {

            PlaygroundPAP(PAP pap) throws PMException {
                super(pap);
            }

            @Override
            public Object executeOperation(Operation<?> operation, UserContext userCtx, Args args) throws PMException {
                if (operation instanceof PMLOperation pmlOp) {
                    pmlOp.setCtx(buildExecutionContext(userCtx));
                }

                Object result = operation.execute(this, userCtx, args);

                if (operation instanceof AdminOperation<?>) {
                    epp.processEvent(EventContext.fromUserContext(this, userCtx, operation.getName(), args.toMap()));
                }

                return result;
            }
        }
    }
}
