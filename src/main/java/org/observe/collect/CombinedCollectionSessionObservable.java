package org.observe.collect;

import org.observe.ObservableValue;
import org.observe.util.ObservableUtils;

import prisms.lang.Type;

public class CombinedCollectionSessionObservable implements ObservableValue<CollectionSession> {
	private static final Type SESSION_TYPE = new Type(CollectionSession.class);

	private final ObservableValue<Boolean> theWrappedSessionObservable;

	public CombinedCollectionSessionObservable(ObservableCollection<? extends ObservableCollection<?>> collection) {
		theWrappedSessionObservable = ObservableUtils.flattenValues(SESSION_TYPE, collection.mapC(collect -> collect.getSession()))
			.filterC(session -> session != null).observeSize().mapV(size -> size > 0);
	}

	@Override
	public Type getType() {
		return SESSION_TYPE;
	}

	@Override
	public CollectionSession get() {
		// TODO Auto-generated method stub
		return null;
	}
}
