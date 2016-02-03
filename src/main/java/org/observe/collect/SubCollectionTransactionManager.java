package org.observe.collect;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.observe.DefaultObservableValue;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.qommons.ListenerSet;

import com.google.common.reflect.TypeToken;

/** Manages transactions for a derived collection in a thread-safe way */
class SubCollectionTransactionManager {
	private final ReentrantLock theLock;

	private CollectionSession theInternalSessionValue;
	private final DefaultObservableValue<CollectionSession> theInternalSession;
	private final ObservableValue<CollectionSession> theExposedSession;
	private final Observer<ObservableValueEvent<CollectionSession>> theSessionController;

	private final ListenerSet<Object> theListeners;

	private final Observable<?> theRefresh;

	/**
	 * @param collection The parent of the collection to manage the transactions for
	 * @param refresh The observable to refresh the collection when fired
	 */
	public SubCollectionTransactionManager(ObservableCollection<?> collection, Observable<?> refresh) {
		theLock = new ReentrantLock();
		theInternalSession = new DefaultObservableValue<CollectionSession>() {
			private final TypeToken<CollectionSession> TYPE = TypeToken.of(CollectionSession.class);

			@Override
			public TypeToken<CollectionSession> getType() {
				return TYPE;
			}

			@Override
			public CollectionSession get() {
				return theInternalSessionValue;
			}
		};
		theExposedSession = new org.observe.ObservableValue.ComposedObservableValue<>(
			sessions -> (CollectionSession) (sessions[0] != null ? sessions[0]
				: sessions[1]), true, theInternalSession, collection.getSession());
		theSessionController = theInternalSession.control(null);

		theListeners = new ListenerSet<>();
		theListeners.setUsedListener(new Consumer<Boolean>() {
			private Subscription sub;

			@Override
			public void accept(Boolean used) {
				// Here we're relying on observers being fired in the order they were subscribed
				// We're also relying on the ability to add an observer from an observer and have that observer invoked for the same
				// "firing"
				if(used) {
					sub = theRefresh.act(v -> {
						startTransaction(v);
						Subscription [] endSub = new Subscription[1];
						boolean [] fired = new boolean[1];
						endSub[0] = theRefresh.act(v2 -> {
							if(fired[0])
								return;
							endTransaction();
							fired[0] = true;
							if(endSub[0] != null) {
								endSub[0].unsubscribe();
								endSub[0] = null;
							}
						});
						if(fired[0] && endSub[0] != null)
							endSub[0].unsubscribe();
					});
				} else {
					sub.unsubscribe();
					sub = null;
				}
			}
		});
		theRefresh = refresh;
	}

	/** @return The session observable to use for the collection */
	public ObservableValue<CollectionSession> getSession() {
		return theExposedSession;
	}

	/**
	 * Installs an observer for a collection, wrapping refresh events in a transaction
	 *
	 * @param <E> The type of the elements in the collection
	 * @param collection The parent of the collection to observe
	 * @param refresh The observable to refresh the collection on
	 * @param onElement The code to deliver the elements to the observer
	 * @param forward Whether to iterate forward through initial elements, or backward (only for {@link ObservableReversibleCollection
	 *            reversible} collections)
	 * @return The runnable to execute to uninstall the observer
	 */
	public <E> Subscription onElement(ObservableCollection<E> collection, Consumer<? super ObservableElement<E>> onElement,
		boolean forward) {
		Consumer<ObservableElement<E>> elFn = el -> onElement.accept(el.refresh(theRefresh));
		Subscription collSub;
		theListeners.add(onElement);
		if(forward)
			collSub = collection.onElement(elFn);
		else
			collSub = ((ObservableReversibleCollection<E>) collection).onElementReverse(elFn);
		return () -> {
			theListeners.remove(onElement);
			collSub.unsubscribe();
		};
	}

	/**
	 * Starts a new transaction
	 *
	 * @param cause The cause of the new transaction
	 */
	public void startTransaction(Object cause) {
		CollectionSession newSession, oldSession;
		theLock.lock();
		try {
			oldSession = theInternalSessionValue;
			newSession = theInternalSessionValue = new DefaultCollectionSession(cause);
			theSessionController.onNext(theInternalSession.createChangeEvent(oldSession, newSession, cause));
		} finally {
			theLock.unlock();
		}
	}

	/** Ends a transaction */
	public void endTransaction() {
		CollectionSession session;
		theLock.lock();
		try {
			session = theInternalSessionValue;
			theInternalSessionValue = null;
			if(session != null)
				theSessionController.onNext(theInternalSession.createChangeEvent(session, null, session.getCause()));
		} finally {
			theLock.unlock();
		}
	}
}
