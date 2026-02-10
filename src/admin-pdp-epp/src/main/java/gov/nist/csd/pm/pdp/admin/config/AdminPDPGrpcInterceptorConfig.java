package gov.nist.csd.pm.pdp.admin.config;

import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import gov.nist.csd.pm.pdp.shared.interceptor.RevisionConsistencyInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class AdminPDPGrpcInterceptorConfig {

    @Bean
    @GrpcGlobalServerInterceptor
    public RevisionConsistencyInterceptor consistencyInterceptor(AdminPDPConfig adminPDPConfig,
                                                                 EventStoreDBConfig eventStoreDBConfig,
                                                                 CurrentRevisionService currentRevisionService,
                                                                 EventStoreConnectionManager connectionManager) {
        Set<String> excluded = new HashSet<>();
        excluded.add("gov.nist.csd.pm.proto.v1.epp.EPPService/processEvent");
        excluded.add("gov.nist.csd.pm.proto.v1.pdp.adjudication.AdminAdjudicationService/adjudicateOperation");
        excluded.add("gov.nist.csd.pm.proto.v1.pdp.adjudication.AdminAdjudicationService/adjudicateRoutine");

        return new RevisionConsistencyInterceptor(
                adminPDPConfig.getRevisionConsistencyTimeout(),
                excluded,
                eventStoreDBConfig,
                currentRevisionService,
                connectionManager
        );
    }
}
