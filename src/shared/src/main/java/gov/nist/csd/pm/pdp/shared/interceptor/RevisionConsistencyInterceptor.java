package gov.nist.csd.pm.pdp.shared.interceptor;

import com.eventstore.dbclient.ReadResult;
import com.eventstore.dbclient.ReadStreamOptions;
import com.eventstore.dbclient.ResolvedEvent;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

/**
 * gRPC interceptor that ensures strong consistency by checking that the local
 * policy state is caught up with EventStoreDB before processing requests.
 *
 * <p>This interceptor calls the provided consistency check before each request.
 * If the check fails (returns false), the request is rejected with UNAVAILABLE status.</p>
 *
 * <p>Certain services can be excluded from the consistency check (e.g., EPPService
 * which uses optimistic concurrency control instead).</p>
 */
public class RevisionConsistencyInterceptor implements ServerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RevisionConsistencyInterceptor.class);

    private final long timeout;
    private final Set<String> excludedMethods;
    private final EventStoreDBConfig config;
    private final CurrentRevisionService currentRevisionService;
    private final EventStoreConnectionManager eventStoreConnectionManager;

    public RevisionConsistencyInterceptor(long timeout,
                                          Set<String> excludedMethods,
                                          EventStoreDBConfig config,
                                          CurrentRevisionService currentRevisionService,
                                          EventStoreConnectionManager connectionManager) {
        this.timeout = timeout;
        this.excludedMethods = excludedMethods;
        this.config = config;
        this.currentRevisionService = currentRevisionService;
        this.eventStoreConnectionManager = connectionManager;
    }

    public RevisionConsistencyInterceptor(long timeout,
                                          EventStoreDBConfig config,
                                          CurrentRevisionService currentRevisionService,
                                          EventStoreConnectionManager connectionManager) {
        this.timeout = timeout;
        this.excludedMethods = new HashSet<>();
        this.config = config;
        this.currentRevisionService = currentRevisionService;
        this.eventStoreConnectionManager = connectionManager;
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
		        logger.warn("Revision onsistency check failed for {}", fullMethodName);
		        call.close(
		                Status.UNAVAILABLE.withDescription("current revision is stale, and server timed out catching up"),
		                new Metadata()
		        );
		        return new ServerCall.Listener<>() {};
		    }
	    } catch (ExecutionException | InterruptedException | TimeoutException e) {
            logger.warn("Error ensuring revision caught up {}", fullMethodName);
            call.close(
                    Status.UNAVAILABLE.withDescription("current revision is stale, and server errored catching up"),
                    new Metadata()
            );
            return new ServerCall.Listener<>() {};
	    }

	    return next.startCall(call, headers);
    }

    private boolean ensureCaughtUp() throws ExecutionException, InterruptedException, TimeoutException {
        long latestRevision = getLatestRevision();
        long localRevision = currentRevisionService.get();

        if (localRevision >= latestRevision) {
            // caught up, proceed
            return true;
        }

        // wait for latest revision locally
        logger.debug("Local revision {} < latest {}, waiting up to {}ms", localRevision, latestRevision, timeout);
        return awaitCatchUp(latestRevision);
    }

    private boolean awaitCatchUp(long requiredRev) {
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeout);
        long deadlineNanos = System.nanoTime() + timeoutNanos;
        long pollNanos = TimeUnit.MILLISECONDS.toNanos(10);

        while (true) {
            long localRev = currentRevisionService.get();

            if (localRev >= requiredRev) {
                return true;
            }

            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                logger.warn("Timeout waiting for catch up: local {} < required {}", localRev, requiredRev);
                return false;
            }

            LockSupport.parkNanos(Math.min(remainingNanos, pollNanos));

            if (Thread.interrupted()) {
                logger.warn("Interrupted while waiting for catch up");
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private long getLatestRevision() throws ExecutionException, InterruptedException, TimeoutException {
        ReadStreamOptions options = ReadStreamOptions.get()
                .fromEnd()
                .backwards()
                .maxCount(1);

        ReadResult result = eventStoreConnectionManager.getOrInitClient()
                .readStream(config.getEventStream(), options)
                .get(5, TimeUnit.SECONDS);

        List<ResolvedEvent> events = result.getEvents();
        if (events.isEmpty()) {
            logger.debug("Event stream is empty");
            return -1;
        }

        long revision = events.getFirst().getEvent().getRevision();
        logger.debug("Latest EventStore revision: {}", revision);
        return revision;
    }
}
