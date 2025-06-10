package gov.nist.csd.pm.pdp.shared.eventstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

public abstract class SubscriptionManager<S> {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionManager.class);

    private ConnectionManager connectionManager;

    protected final EventStoreDBConfig eventStoreDBConfig;
    protected S subscription;

    public SubscriptionManager(EventStoreDBConfig eventStoreDBConfig) {
        this.eventStoreDBConfig = eventStoreDBConfig;
    }

    protected abstract S initSubscription() throws ExecutionException, InterruptedException;
    public abstract boolean isSubscribed();
    public abstract void closeSubscription();

    public void subscribe() throws ExecutionException, InterruptedException {
        logger.info("Subscribing to EventStoreDB");
        // reset subscription just in case it wasn't closed before
        // to avoid creating double subscriptions on the same listener
        unsubscribe();

        // initialize the subscription
        subscription = initSubscription();
    }

    public void unsubscribe() {
        closeSubscription();
    }
}
