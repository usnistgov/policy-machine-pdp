package resource;

import gov.nist.csd.pm.proto.v1.adjudication.ResourceAdjudicationServiceGrpc;
import gov.nist.csd.pm.proto.v1.adjudication.ResourceOperationCmd;
import gov.nist.csd.pm.proto.v1.model.Node;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

public class ResourcePDPClientExample {

	public static void main(String[] args) {
		ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50051)
				.usePlaintext()
				.build();

		Metadata metadata = new io.grpc.Metadata();
		Metadata.Key<String> userKey = Metadata.Key.of("x-pm-user", Metadata.ASCII_STRING_MARSHALLER);
		metadata.put(userKey, "u1");

		ResourceAdjudicationServiceGrpc.ResourceAdjudicationServiceBlockingStub blockingStub = ResourceAdjudicationServiceGrpc.newBlockingStub(channel)
				.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));

		ResourceOperationCmd request = ResourceOperationCmd.newBuilder()
				.setId(6)
				.setOperation("read")
				.build();

		Node node = blockingStub.adjudicateResourceOperation(request);
		System.out.println(node);
	}

}
