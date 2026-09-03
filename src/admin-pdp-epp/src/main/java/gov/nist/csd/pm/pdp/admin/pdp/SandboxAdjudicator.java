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

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.epp.EPP;
import gov.nist.ngac.pm.core.epp.EventContext;
import gov.nist.ngac.pm.core.grpc.util.FromProtoUtil;
import gov.nist.ngac.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.operation.AdminOperation;
import gov.nist.ngac.pm.core.pap.operation.Operation;
import gov.nist.ngac.pm.core.pap.operation.arg.Args;
import gov.nist.ngac.pm.core.pap.pml.operation.PMLOperation;
import gov.nist.ngac.pm.core.pap.query.model.context.UserContext;
import gov.nist.ngac.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.shared.auth.UserContextResolver;
import gov.nist.csd.pm.pdp.shared.config.SandboxMode;
import gov.nist.ngac.pm.proto.v1.pdp.adjudication.OperationRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
@SandboxMode
public class SandboxAdjudicator implements AdminAdjudicator {

    private final MemoryPAP pap;
    private final UserContextResolver userContextResolver;
    private final SandboxPDP pdp;
    private final EPP epp;
    private final Object lock = new Object();

    public SandboxAdjudicator(MemoryPAP pap, UserContextResolver userContextResolver) throws PMException {
        this.pap = pap;
        this.userContextResolver = userContextResolver;

        // The PDP and EPP form a cycle: the PDP fires events to the EPP, and the EPP runs obligation
        // responses back through the PDP. Construct the PDP first, then the EPP, then wire the EPP into
        // the PDP. All three share the single injected PAP.
        this.pdp = new SandboxPDP(pap);
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
            // no EventStoreDB in sandbox mode, so there is no revision to report
            return -1;
        }
    }

    /**
     * A PDP that overrides adjudicateOperation and executePML to skip the
     * canExecute privilege gate and execute straight against the underlying PAP, while still
     * publishing admin-operation events to epp so obligations keep firing. runTx is left
     * untouched, so EPP.processEvent's own use of it to run obligation responses still goes through
     * the real, privilege-checked PDPTx machinery.
     */
    private static final class SandboxPDP extends PDP {

        // SandboxPAP wraps the same underlying policy store as the PDP's base PAP (super(pap)); it only
        // overrides executeOperation to bypass the privilege gate while still firing admin events to the EPP.
        private final PAP sandboxPAP;
        private EPP epp;

        SandboxPDP(PAP pap) throws PMException {
            super(pap);
            this.sandboxPAP = new SandboxPAP(pap);
        }

        void setEpp(EPP epp) {
            this.epp = epp;
        }

        @Override
        public Object adjudicateOperation(UserContext user, String resourceOperation, Map<String, Object> rawArgs) throws PMException {
            Operation<?> op = pap.query().operations().getOperation(resourceOperation);
            Args args = op.validateArgs(rawArgs);

            AtomicReference<Object> result = new AtomicReference<>();
            sandboxPAP.runTx(tx -> result.set(tx.executeOperation(op, user, args)));
            return result.get();
        }

        @Override
        public Object executePML(UserContext userCtx, String pml) throws PMException {
            AtomicReference<Object> ret = new AtomicReference<>();
            sandboxPAP.runTx(tx -> {
                ret.set(tx.executePML(userCtx, pml));
            });
            return ret.get();
        }

        private final class SandboxPAP extends PAP {

            SandboxPAP(PAP pap) throws PMException {
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
