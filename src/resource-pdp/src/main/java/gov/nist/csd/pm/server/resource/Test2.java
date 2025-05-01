package gov.nist.csd.pm.server.resource;

import gov.nist.csd.pm.proto.pdp.PDPResponse;
import gov.nist.csd.pm.proto.pdp.ResourceOperationRequestByName;
import gov.nist.csd.pm.proto.pdp.ResourcePDPGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;

public class Test2 {

    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50051)
            .usePlaintext()
            .build();

        while (true) {
            ResourcePDPGrpc.ResourcePDPBlockingStub stub = ResourcePDPGrpc.newBlockingStub(channel);

            ResourceOperationRequestByName request = ResourceOperationRequestByName.newBuilder()
                .setTarget("o1")
                .setOperation("read")
                .build();

            try {
                PDPResponse response = stub.adjudicateResourceOperationByName(request);
                System.out.println("ResourcePDP Response: " + response);
            } catch (StatusRuntimeException e) {
                e.printStackTrace();
                System.err.println("ResourcePDP Error: " + e.getStatus());
            }

            TimeUnit.SECONDS.sleep(5);
        }
    }

	/*public static void main(String[] args) {
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
	}*/
}