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
