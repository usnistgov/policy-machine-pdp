package admin;

import gov.nist.csd.pm.proto.v1.adjudication.*;
import gov.nist.csd.pm.proto.v1.cmd.AdminCommand;
import gov.nist.csd.pm.proto.v1.cmd.AssignCmd;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

public class AdminPDPClientExample {

	public static void main(String[] args) {
		ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50052)
				.usePlaintext()
				.build();

		Metadata metadata = new io.grpc.Metadata();
		Metadata.Key<String> userKey = Metadata.Key.of("x-pm-user", Metadata.ASCII_STRING_MARSHALLER);
		metadata.put(userKey, "u1");

		AdminAdjudicationServiceGrpc.AdminAdjudicationServiceBlockingStub blockingStub = AdminAdjudicationServiceGrpc.newBlockingStub(channel)
				.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));

		// assign o1 -> oa1
		AdminCmdRequest request = AdminCmdRequest.newBuilder()
				.addCommands(
						AdminCommand.newBuilder()
								.setAssignCmd(
										AssignCmd.newBuilder()
												.setAscendantId(6)
												.addDescendantIds(4)
												.build()
								)
								.build()
				)
				.build();

		AdminCmdResponse response = blockingStub.adjudicateAdminCmd(request);
		System.out.println(response);
	}
}
