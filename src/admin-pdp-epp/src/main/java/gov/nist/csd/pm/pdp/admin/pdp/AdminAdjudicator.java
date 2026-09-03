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
