package org.observe.collect;

import java.util.concurrent.atomic.AtomicBoolean;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.util.ListenerSet;
import org.observe.util.ObservableUtils;

import prisms.lang.Type;

/**
 * A collection session observable for collections that wrap multiple collections. This observable manages its own session which is present
 * whenever the containing collection or any of its elements have sessions.
 */
public class CombinedCollectionSessionObservable implements ObservableValue<CollectionSession> {
	private static final Type SESSION_TYPE = new Type(CollectionSession.class);

	private final ObservableValue<Boolean> theWrappedSessionObservable;

	private final ListenerSet<Observer<? super ObservableValueEvent<CollectionSession>>> theObservers;

	private final AtomicBoolean isInTransaction;

	private CollectionSession theSession;

	/**
	 * @param collection The collection of collections that this session observable is for
	 */
	public CombinedCollectionSessionObservable(ObservableCollection<? extends ObservableCollection<?>> collection) {
		theWrappedSessionObservable = ObservableUtils.flattenValues(SESSION_TYPE, collection.map(collect -> collect.getSession()))
			.filter(session -> session != null).observeSize().mapV(size -> size > 0)
			.combineV((Boolean value1, Boolean value2) -> value1 || value2, collection.getSession().mapV(session -> session != null));
		theObservers = new ListenerSet<>();
		isInTransaction = new AtomicBoolean();

		final Runnable [] wrappedSessionListener = new Runnable[1];
		theObservers.setUsedListener(used -> {
			if(used) {
				wrappedSessionListener[0] = theWrappedSessionObservable.internalSubscribe(new Observer<ObservableValueEvent<Boolean>>() {
					@Override
					public <V extends ObservableValueEvent<Boolean>> void onNext(V value) {
						if(isInTransaction.getAndSet(value.getValue()) == value.getValue())
							return; // No change
						if(value.getValue()) {
							theSession = createSession(value.getCause());
							fire(null, theSession, value.getCause());
						} else {
							CollectionSession session = theSession;
							theSession = null;
							fire(session, null, value.getCause());
						}
					}
				});
			} else {
				wrappedSessionListener[0].run();
				wrappedSessionListener[0] = null;
			}
		});
	}

	@Override
	public Type getType() {
		return SESSION_TYPE;
	}

	@Override
	public CollectionSession get() {
		return theSession;
	}

	@Override
	public Runnable internalSubscribe(Observer<? super ObservableValueEvent<CollectionSession>> observer) {
		theObservers.add(observer);
		return () -> theObservers.remove(observer);
	}

	private CollectionSession createSession(Object cause) {
		return new DefaultCollectionSession(cause);
	}

	private void fire(CollectionSession old, CollectionSession newSession, Object cause) {
		ObservableValueEvent<CollectionSession> evt = new ObservableValueEvent<>(this, old, newSession, cause);
		theObservers.forEach(listener -> listener.onNext(evt));
	}
}
