package org.observe.collect;

import org.observe.Subscription;

@FunctionalInterface
public interface CollectionSubscription extends Subscription {
	void unsubscribe(boolean removeAll);

	@Override
	default void unsubscribe() {
		unsubscribe(false);
	}

	default Subscription removeAll() {
		return () -> unsubscribe(true);
	}
}
