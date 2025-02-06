package gov.nist.csd.pm.server.admin;

import gov.nist.csd.pm.proto.epp.OperandEntry;
import gov.nist.csd.pm.proto.epp.StringList;
import gov.nist.csd.pm.proto.pdp.*;
import gov.nist.csd.pm.server.shared.ServerConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;

import static gov.nist.csd.pm.server.shared.UserContextInterceptor.PM_USER_KEY;

public class Test {
	public static void main(String[] args) throws MalformedURLException, ClassNotFoundException {
		ServerConfig config = ServerConfig.load();

		ManagedChannel channel = ManagedChannelBuilder
				.forAddress("localhost", 8080)
				.usePlaintext()
				.build();

		Metadata metadata = new Metadata();
		Metadata.Key<String> userKey = Metadata.Key.of(PM_USER_KEY, Metadata.ASCII_STRING_MARSHALLER);
		metadata.put(userKey, "u1");

		ResourcePDPGrpc.ResourcePDPBlockingStub blockingStub = ResourcePDPGrpc.newBlockingStub(channel)
				.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));

		try {
			while (true) {
				try {
					AdjudicationResponse adjudicationResponse = blockingStub.adjudicateResourceOperation(
							ResourceOperationRequest
									.newBuilder()
									.setOperation("read")
									.setTarget("o1")
									.build()
					);

					System.out.println(adjudicationResponse);
				} catch (Exception e) {
					System.err.println(e.getMessage());
				}

				Thread.sleep(5000);
			}
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} finally {
			channel.shutdown();
		}
	}
}
