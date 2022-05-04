package org.observe.remote;

import org.observe.Subscription;

public interface CollectionEventStore<P> {
	public interface EventStoreListener {
		void eventsPurged(long oldestRemaining);
	}

	void store(long eventId, P event);

	P retrieve(long eventId);

	void release(long oldestNeededEventId);

	Subscription addListener(EventStoreListener listener);
}
