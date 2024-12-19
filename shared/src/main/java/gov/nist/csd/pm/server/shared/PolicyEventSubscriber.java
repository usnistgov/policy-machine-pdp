package gov.nist.csd.pm.server.shared;

import com.eventstore.dbclient.*;
import com.google.protobuf.InvalidProtocolBufferException;
import gov.nist.csd.pm.pap.PAP;
import gov.nist.csd.pm.pap.exception.PMException;
import gov.nist.csd.pm.pap.exception.PMRuntimeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class PolicyEventSubscriber {

	private static final Logger logger = LogManager.getLogger(PolicyEventSubscriber.class);

	private PAP pap;
	private ServerConfig config;

	public PolicyEventSubscriber(PAP pap, ServerConfig config) {
		this.pap = pap;
		this.config = config;
	}

	public void listenForEvents() {
		EventStoreDBClient client = createConnection();

		client.subscribeToStream("policy-machine-v1", new SubscriptionListener() {
			@Override
			public void onEvent(Subscription subscription, ResolvedEvent event) {
				try {
					logger.info("handling event " + event);
					PolicyEventHandler.handleEvents(pap, List.of(event));
				} catch (PMException | InvalidProtocolBufferException e) {
					throw new PMRuntimeException(e);
				}
			}
		}).thenAccept(subscription -> {
			System.out.println("Subscription to stream 'policy-machine-v1' is active.");
		}).exceptionally(ex -> {
			System.err.println("Error subscribing to stream: " + ex.getMessage());
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
