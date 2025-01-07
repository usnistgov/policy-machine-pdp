package gov.nist.csd.pm.server.resource;

import com.eventstore.dbclient.*;
import gov.nist.csd.pm.pap.op.graph.proto.CreatePolicyClassOp;
import gov.nist.csd.pm.pap.op.graph.proto.CreatePolicyClassOpOrBuilder;
import gov.nist.csd.pm.pdp.proto.ResourceOperationRequest;
import gov.nist.csd.pm.pdp.proto.ResourceOperationResponse;
import gov.nist.csd.pm.pdp.proto.ResourcePDPGrpc;
import gov.nist.csd.pm.server.shared.UserContextInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Test {

	public static void main(String[] args) throws ExecutionException, InterruptedException {
		// Create a channel to connect to the server
		ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50051)
				.usePlaintext() // Disable TLS for simplicity
				.build();

		Metadata metadata = new Metadata();
		Metadata.Key<String> userKey = Metadata.Key.of(UserContextInterceptor.PM_USER_KEY, Metadata.ASCII_STRING_MARSHALLER);
		metadata.put(userKey, "u1");

		// Create a blocking stub (synchronous)
		ResourcePDPGrpc.ResourcePDPBlockingStub blockingStub = ResourcePDPGrpc.newBlockingStub(channel)
				.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));;

		// Create a request
		ResourceOperationRequest request = ResourceOperationRequest.newBuilder()
				.setOperation("read")
				.setTarget("o1")
				.build();

		// Call the gRPC method on the server and get the response
		ResourceOperationResponse response = blockingStub.adjudicateResourceOperation(request);

		// Print the response from the server
		System.out.println("Received response: " + response.getNode());

		// Close the channel
		channel.shutdown();

		/*
		EventData event = EventData.builderAsJson(eventType, eventData)
				.eventId(eventId)
				.build();

		// Append the event to the stream and handle response properly
		CompletableFuture<WriteResult> future = client.appendToStream(streamName, event);

		// Wait for completion (join will propagate any exception)
		future.thenAccept(result ->
				System.out.println("Event successfully appended. Position: " + result.getLogPosition())
		).exceptionally(ex -> {
			System.err.println("Failed to append event: " + ex.getMessage());
			ex.printStackTrace();
			return null; // Returning null since exceptionally requires a return value
		}).join(); // Wait for completion, blocking here









		// Parse the connection string to create client settings
		EventStoreDBClientSettings settings = EventStoreDBClientSettings.builder()
				.addHost("127.0.0.1", 2113)
				.tls(false)
				.keepAliveTimeout(10000) // 10-second keep-alive
				.keepAliveInterval(10000) // 10-second interval
				.defaultDeadline(30000) // 30-second timeout for calls
				.buildConnectionSettings();

		// Create the EventStoreDB client (try-with-resources will ensure it closes properly)
		EventStoreDBClient client = EventStoreDBClient.create(settings);

		// Stream name and event data
		String streamName = "policy-machine-v1";

		try {
			// Read events from the stream
			ReadStreamOptions options = ReadStreamOptions.get()
					.fromStart();

			client.readStream(streamName, options)
					.thenAccept(event -> {
								try {
									List<ResolvedEvent> events = event.getEvents();
									for (ResolvedEvent e : events) {
										System.out.println(e.getOriginalEvent().getEventType());
									}
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
					).exceptionally(ex -> {
						System.err.println("Failed to append event: " + ex.getMessage());
						ex.printStackTrace();
						return null; // Returning null since exceptionally requires a return value
					}).join();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			client.shutdown();
		}*/
	}
}
