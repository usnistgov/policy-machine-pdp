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

package gov.nist.csd.pm.pdp.admin.epp;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.grpc.util.FromProtoUtil;
import gov.nist.csd.pm.pdp.admin.pdp.AdminAdjudicator;
import gov.nist.ngac.pm.proto.v1.epp.EPPServiceGrpc;
import gov.nist.ngac.pm.proto.v1.epp.EventContext;
import gov.nist.ngac.pm.proto.v1.epp.ProcessEventResponse;
import gov.nist.ngac.pm.proto.v1.model.Value;
import gov.nist.ngac.pm.proto.v1.model.ValueMap;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GrpcService
public class EPPService extends EPPServiceGrpc.EPPServiceImplBase {

	private static final Logger logger = LoggerFactory.getLogger(EPPService.class);

	private final AdminAdjudicator adjudicator;

	public EPPService(AdminAdjudicator adjudicator) {
		this.adjudicator = adjudicator;
	}

	@Override
	public void processEvent(EventContext request, StreamObserver<ProcessEventResponse> responseObserver) {
		try {
			long lastRevision = adjudicator.adjudicateTransaction(ctx -> {
				ctx.epp().processEvent(FromProtoUtil.fromEventContextProto(request));
			});

			ProcessEventResponse.Builder resp = ProcessEventResponse.newBuilder();
			if (lastRevision > 0) {
				resp.setResult(
						ValueMap.newBuilder()
								.putValues(
										"last_event_revision",
										Value.newBuilder().setInt64Value(lastRevision).build()
								)
								.build()
				);
			}

			responseObserver.onNext(resp.build());
			responseObserver.onCompleted();
		} catch (RuntimeException | PMException e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL
					                         .withDescription(e.getMessage())
					                         .withCause(e)
					                         .asRuntimeException());
		}
	}
}
