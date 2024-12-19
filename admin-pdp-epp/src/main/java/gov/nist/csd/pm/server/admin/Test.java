package gov.nist.csd.pm.server.admin;

import gov.nist.csd.pm.epp.proto.EPPGrpc;
import gov.nist.csd.pm.epp.proto.OperandEntry;
import gov.nist.csd.pm.epp.proto.StringList;
import gov.nist.csd.pm.pdp.proto.AdminOperationRequest;
import gov.nist.csd.pm.pdp.proto.AdminPDPGrpc;
import gov.nist.csd.pm.server.shared.ServerConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class Test {
	public static void main(String[] args) {
		ServerConfig config = ServerConfig.load();

		ManagedChannel channel = ManagedChannelBuilder
				.forAddress(config.adminHost(), config.adminPort())
				.usePlaintext()
				.build();

		AdminPDPGrpc.AdminPDPBlockingStub blockingStub = AdminPDPGrpc.newBlockingStub(channel);
		blockingStub.adjudicateAdminOperation(
				AdminOperationRequest
						.newBuilder()
						.setOpName("create_object")
						.addOperands(OperandEntry.newBuilder().setName("name").setStringValue("obj " + System.nanoTime()).build())
						.addOperands(OperandEntry.newBuilder().setName("descendants").setListValue(StringList.newBuilder().addValues("oa1").build()).build())
						.build()
		);
	}
}
