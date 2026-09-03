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

package gov.nist.csd.pm.pdp.resource.epp;

import gov.nist.ngac.pm.core.epp.EPP;
import gov.nist.ngac.pm.core.epp.EventContext;
import gov.nist.ngac.pm.core.grpc.util.ToProtoUtil;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pdp.PDP;
import gov.nist.csd.pm.pdp.resource.config.ResourcePDPConfig;
import gov.nist.ngac.pm.proto.v1.epp.EPPServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EPPClient extends EPP {

    private static final Logger logger = LoggerFactory.getLogger(EPPClient.class);

    private final PDP pdp;
    private final ResourcePDPConfig resourcePDPConfig;
    private EPPServiceGrpc.EPPServiceBlockingStub blockingStub;

    public EPPClient(PDP pdp,
                     PAP pap,
                     ResourcePDPConfig resourcePDPConfig) {
        super(pdp, pap);
        this.pdp = pdp;
        this.resourcePDPConfig = resourcePDPConfig;
    }

    @PostConstruct
    public void subscribeToPDP() {
        // subscribe to the PDP bean
        this.pdp.addEventSubscriber(this);

        // init epp client to admin pdp epp service
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(resourcePDPConfig.getAdminHostname(), resourcePDPConfig.getAdminPort())
                .defaultServiceConfig(buildGrpcConfigMap())
                .enableRetry()
                .usePlaintext()
                .build();

        this.blockingStub = EPPServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public void processEvent(EventContext eventCtx) {
        logger.info("sending to EPP {}", eventCtx);

        gov.nist.ngac.pm.proto.v1.epp.EventContext eventCtxProto = ToProtoUtil.toEventContextProto(eventCtx);

        blockingStub.processEvent(eventCtxProto);
    }

    private Map<String, Object> buildGrpcConfigMap() {
        Map<String, Object> serviceConfig = new HashMap<>();

        // Load balancing configuration
        serviceConfig.put("loadBalancingConfig", List.of(Map.of("round_robin", new HashMap<>())));

        // Method configuration with retry policy
        Map<String, Object> retryPolicy = new HashMap<>();
        retryPolicy.put("maxAttempts", "3");
        retryPolicy.put("initialBackoff", "0.2s");
        retryPolicy.put("maxBackoff", "10s");
        retryPolicy.put("backoffMultiplier", 1.5);
        retryPolicy.put("retryableStatusCodes", List.of("UNAVAILABLE"));

        serviceConfig.put("methodConfig", List.of(
            Map.of(
                "name", List.of(Map.of("service", "resource-pdp")),
                "retryPolicy", retryPolicy
            )
        ));

        return serviceConfig;
    }
}
