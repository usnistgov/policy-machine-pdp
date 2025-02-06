package gov.nist.csd.pm.server.resource;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import gov.nist.csd.pm.proto.pdp.AdminPDPGrpc;
import gov.nist.csd.pm.proto.pdp.AdjudicationResponse;
import gov.nist.csd.pm.proto.pdp.AdminOperationRequest;
import gov.nist.csd.pm.proto.epp.EPPGrpc;
import gov.nist.csd.pm.proto.epp.EventContext;
import gov.nist.csd.pm.proto.epp.EPPResponse;
import gov.nist.csd.pm.proto.pdp.ResourcePDPGrpc;
import gov.nist.csd.pm.proto.pdp.ResourceOperationRequest;

public class Test2 {
	public static void main(String[] args) {
		String ingressHost = "localhost"; // Replace with the Ingress Gateway IP if not running locally
		int ingressPort = 8080;          // Ingress Gateway port

		testResourcePDP(ingressHost, ingressPort);
	}

	private static void testResourcePDP(String host, int port) {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext()
				.build();

		ResourcePDPGrpc.ResourcePDPBlockingStub stub = ResourcePDPGrpc.newBlockingStub(channel);

		ResourceOperationRequest request = ResourceOperationRequest.newBuilder()
				.setTarget("o1")
				.setOperation("read")
				.build();

		try {
			AdjudicationResponse response = stub.adjudicateResourceOperation(request);
			System.out.println("ResourcePDP Response: " + response);
		} catch (StatusRuntimeException e) {
			System.err.println("ResourcePDP Error: " + e.getStatus());
		} finally {
			channel.shutdown();
		}
	}
}