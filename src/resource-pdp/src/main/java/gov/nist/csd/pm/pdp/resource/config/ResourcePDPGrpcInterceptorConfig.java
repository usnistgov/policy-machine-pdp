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
