package gov.nist.csd.pm.pdp.resource.config;

import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.LatestRevisionTracker;
import gov.nist.csd.pm.pdp.shared.interceptor.RevisionConsistencyInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResourcePDPGrpcInterceptorConfig {

    @Bean
    @GrpcGlobalServerInterceptor
    public RevisionConsistencyInterceptor consistencyInterceptor(ResourcePDPConfig resourcePDPConfig,
                                                                 CurrentRevisionService currentRevisionService,
                                                                 LatestRevisionTracker latestRevisionTracker) {
        return new RevisionConsistencyInterceptor(
                resourcePDPConfig.getRevisionConsistencyTimeout(),
                currentRevisionService,
                latestRevisionTracker
        );
    }
}
