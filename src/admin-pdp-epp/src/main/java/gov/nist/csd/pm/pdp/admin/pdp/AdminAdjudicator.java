package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.proto.v1.pdp.adjudication.OperationRequest;

import java.util.List;
import java.util.Map;

public interface AdminAdjudicator {

    Object adjudicateOperation(String operation, Map<String, Object> args) throws PMException;

    void adjudicateRoutine(List<OperationRequest> adminCommands) throws PMException;

    <R> R adjudicateQuery(PDPTxFunction<R> consumer) throws PMException;

    Object executePML(String pml) throws PMException;

    long adjudicateTransaction(PMConsumer<NGACContext> txConsumer) throws PMException;
}
