package org.observe.collect;

import java.util.concurrent.atomic.AtomicBoolean;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.qommons.ListenerSet;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

/**
 * A collection session observable for collections that wrap multiple collections. This observable manages its own session which is present
 * whenever the containing collection or any of its elements have sessions.
 */
public class CombinedCollectionSessionObservable implements ObservableValue<CollectionSession> {
	private static final TypeToken<CollectionSession> SESSION_TYPE = TypeToken.of(CollectionSession.class);

	private final ObservableValue<Boolean> theWrappedSessionObservable;

	private final ListenerSet<Observer<? super ObservableValueEvent<CollectionSession>>> theObservers;

	private final AtomicBoolean isInTransaction;

	private CollectionSession theSession;

	/** @param collection The collection of collections that this session observable is for */
	public CombinedCollectionSessionObservable(ObservableCollection<? extends ObservableCollection<?>> collection) {
		theWrappedSessionObservable = ObservableCollection.flattenValues(collection.map(collect -> collect.getSession()))
			.filterMap(null, session -> session, false).observeSize().mapV(size -> size > 0)
			.combineV((Boolean value1, Boolean value2) -> value1 || value2,
				collection.getSession().mapV(null, session -> session != null, true));
		theObservers = new ListenerSet<>();
		isInTransaction = new AtomicBoolean();

		final Subscription [] wrappedSessionListener = new Subscription[1];
		theObservers.setUsedListener(used -> {
			if(used) {
				wrappedSessionListener[0] = theWrappedSessionObservable.subscribe(new Observer<ObservableValueEvent<Boolean>>() {
					@Override
					public <V extends ObservableValueEvent<Boolean>> void onNext(V value) {
						if (isInTransaction.getAndSet(value.getValue()) == value.getValue()) {
							if (value.isInitial())
								fire(null, theSession, value);
							return; // No change
						}
						if(value.getValue()) {
							theSession = createSession(value.getCause());
							fire(null, theSession, value);
						} else {
							CollectionSession session = theSession;
							theSession = null;
							fire(session, null, value);
						}
					}
				});
			} else {
				wrappedSessionListener[0].unsubscribe();
				wrappedSessionListener[0] = null;
			}
		});
	}

	@Override
	public TypeToken<CollectionSession> getType() {
		return SESSION_TYPE;
	}

	@Override
	public CollectionSession get() {
		return theSession;
	}

	@Override
	public Subscription subscribe(Observer<? super ObservableValueEvent<CollectionSession>> observer) {
		theObservers.add(observer);
		return () -> theObservers.remove(observer);
	}

	private CollectionSession createSession(Object cause) {
		return new DefaultCollectionSession(cause);
	}

	private void fire(CollectionSession old, CollectionSession newSession, ObservableValueEvent<?> cause) {
		ObservableValueEvent<CollectionSession> evt;
		if (cause.isInitial())
			evt = createInitialEvent(newSession);
		else
			evt = createChangeEvent(old, newSession, cause);
		theObservers.forEach(listener -> listener.onNext(evt));
	}

	@Override
	public boolean isSafe() {
		return true;
	}

	/**
	 * An implementation of {@link ObservableCollection#lock(boolean, Object)} for a collection implementation that uses a collection of
	 * collections
	 * 
	 * @param collection The collection of collections
	 * @param write Whether to lock for write
	 * @param cause The cause of the change
	 * @return The transaction to close when the operation is finished
	 */
	public static Transaction lock(ObservableCollection<? extends ObservableCollection<?>> collection, boolean write, Object cause) {
		Transaction outerLock = collection.lock(write, cause);
		Transaction[] innerLocks = new Transaction[collection.size()];
		int i = 0;
		for (ObservableCollection<?> c : collection) {
			innerLocks[i++] = c.lock(write, cause);
		}
		return new Transaction() {
			private volatile boolean hasRun;

			@Override
			public void close() {
				if (hasRun)
					return;
				hasRun = true;
				for (int j = innerLocks.length - 1; j >= 0; j--)
					innerLocks[j].close();
				outerLock.close();
			}

			@Override
			protected void finalize() {
				if (!hasRun)
					close();
			}
		};
	}
}
