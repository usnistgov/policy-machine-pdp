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
 * licensed to NIST. To the extent that NIST holds copyright in this software, it is
 * being made available under the Creative Commons Attribution 4.0 International
 * license (CC BY 4.0). The disclaimers of the CC BY 4.0 license apply to all parts
 * of the software developed or licensed by NIST.
 *
 * ACCESS THE FULL CC BY 4.0 LICENSE HERE:
 * https://creativecommons.org/licenses/by/4.0/legalcode
 */

package gov.nist.csd.pm.pdp.shared.interceptor;

import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.LatestRevisionTracker;
import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * gRPC interceptor that ensures strong consistency by checking that the local
 * policy state is caught up with EventStoreDB before processing requests.
 */
public class RevisionConsistencyInterceptor implements ServerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RevisionConsistencyInterceptor.class);

    private final long timeout;
    private final Set<String> excludedMethods;
    private final CurrentRevisionService currentRevisionService;
    private final LatestRevisionTracker latestRevisionTracker;

    public RevisionConsistencyInterceptor(long timeout,
                                          Set<String> excludedMethods,
                                          CurrentRevisionService currentRevisionService,
                                          LatestRevisionTracker latestRevisionTracker) {
        this.timeout = timeout;
        this.excludedMethods = excludedMethods;
        this.currentRevisionService = currentRevisionService;
        this.latestRevisionTracker = latestRevisionTracker;
    }

    public RevisionConsistencyInterceptor(long timeout,
                                          CurrentRevisionService currentRevisionService,
                                          LatestRevisionTracker latestRevisionTracker) {
        this.timeout = timeout;
        this.excludedMethods = new HashSet<>();
        this.currentRevisionService = currentRevisionService;
        this.latestRevisionTracker = latestRevisionTracker;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        String fullMethodName = call.getMethodDescriptor().getFullMethodName();

        if (excludedMethods.contains(fullMethodName)) {
            return next.startCall(call, headers);
        }

	    try {
		    if (!ensureCaughtUp()) {
		        logger.warn("revision consistency check failed for {}", fullMethodName);
		        call.close(
		                Status.UNAVAILABLE.withDescription("current revision is stale, and server timed out catching up"),
		                new Metadata()
		        );
		        return new ServerCall.Listener<>() {};
		    }
	    } catch (InterruptedException e) {
            logger.warn("interrupted ensuring revision caught up for {}", fullMethodName);
            Thread.currentThread().interrupt();
            call.close(
                    Status.UNAVAILABLE.withDescription("current revision is stale, and server errored catching up"),
                    new Metadata()
            );
            return new ServerCall.Listener<>() {};
	    }

	    return next.startCall(call, headers);
    }

    private boolean ensureCaughtUp() throws InterruptedException {
        long latestRevision;
        try {
            latestRevision = latestRevisionTracker.get(timeout);
        } catch (TimeoutException e) {
            logger.warn("Latest revision tracker not initialized within timeout");
            return false;
        }

        long localRevision = currentRevisionService.get();

        if (localRevision >= latestRevision) {
            return true;
        }

        logger.debug("Local revision {} < eventstore latest {}, waiting up to {}ms", localRevision, latestRevision, timeout);
        return currentRevisionService.awaitRevision(latestRevision, timeout);
    }
}
