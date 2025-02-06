package gov.nist.csd.pm.server.shared;

import com.eventstore.dbclient.*;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.common.exception.PMException;
import gov.nist.csd.pm.common.exception.PMRuntimeException;
import gov.nist.csd.pm.pap.PAP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class PolicyEventSubscriber {

	private PAP pap;
	private ServerConfig config;

	public PolicyEventSubscriber(PAP pap, ServerConfig config) {
		this.pap = pap;
		this.config = config;
	}

	public void listenForEvents() {
		EventStoreDBClient client = createConnection();

		// TODO check health of event store

		client.subscribeToStream("policy-machine-v1", new SubscriptionListener() {
			@Override
			public void onEvent(Subscription subscription, ResolvedEvent event) {
				try {
					System.out.println("handling event " + event);
					PolicyEventHandler.handleEvents(pap, List.of(event));
				} catch (PMException | InvalidProtocolBufferException e) {
					throw new PMRuntimeException(e);
				}
			}
		}).thenAccept(subscription -> {
			System.out.println("Subscription to stream 'policy-machine-v1' is active.");
		}).exceptionally(ex -> {
			ex.printStackTrace();
			return null;
		});
	}

	private EventStoreDBClient createConnection() {
		try {
			EventStoreDBClientSettings settings = EventStoreDBConnectionString.parseOrThrow(
					String.format("esdb://%s:%d?tls=false", config.esdbHost(), config.esdbPort())
			);
			EventStoreDBClient client = EventStoreDBClient.create(settings);
			System.out.println("Connected to EventStoreDB");
			return client;
		} catch (Exception e) {
			throw new RuntimeException("Failed to connect to EventStoreDB", e);
		}
	}
}
