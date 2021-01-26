package org.observe.remote;

import java.util.Collection;
import java.util.function.Consumer;

public class CollectionServerClient<E, VP, CP> implements ObservableCollectionServer.Client<CP> {
	private final ObservableCollectionServer<E, VP, CP> theServer;
	private ObservableCollectionServer.ClientInterface<CP> theClient;
	private final Consumer<? super CP> thePusher;
	private final Consumer<? super Collection<VP>> theResync;

	public CollectionServerClient(ObservableCollectionServer<E, VP, CP> server, Consumer<? super CP> pusher,
		Consumer<? super Collection<VP>> resync) {
		theServer = server;
		thePusher = pusher;
		theResync = resync;
	}

	public ObservableCollectionServer<E, VP, CP> getServer() {
		return theServer;
	}

	public synchronized boolean poll(Consumer<? super CP> onChange, Consumer<? super Collection<VP>> onResync) {
		if (theClient != null && theClient.poll(onChange))
			return true;
		else {
			theClient = theServer.addClient(this, onResync);
			return false;
		}
	}

	@Override
	public void eventOccurred(CP event) {
		if (thePusher != null)
			poll(thePusher, theResync);
	}
}
