package org.observe.collect;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.observe.DefaultObservableValue;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;

import prisms.lang.Type;

/** Manages transactions for a derived collection in a thread-safe way */
class SubCollectionTransactionManager {
	private final ReentrantLock theLock;

	private CollectionSession theInternalSessionValue;
	private final DefaultObservableValue<CollectionSession> theInternalSession;
	private final ObservableValue<CollectionSession> theExposedSession;
	private final Observer<ObservableValueEvent<CollectionSession>> theSessionController;

	/** @param collection The parent of the collection to manage the transactions for */
	public SubCollectionTransactionManager(ObservableCollection<?> collection) {
		theLock = new ReentrantLock();
		theInternalSession = new DefaultObservableValue<CollectionSession>() {
			private final Type TYPE = new Type(CollectionSession.class);

			@Override
			public Type getType() {
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
	public <E> Subscription onElement(ObservableCollection<E> collection, Observable<?> refresh,
		Consumer<? super ObservableElement<E>> onElement, boolean forward) {
		// Here we're relying on observers being fired in the order they were subscribed
		Subscription refreshStartSub = refresh == null ? null : refresh.subscribe(new Observer<Object>() {
			@Override
			public <V> void onNext(V value) {
				startTransaction(value);
			}

			@Override
			public <V> void onCompleted(V value) {
				startTransaction(value);
			}
		});
		Observer<Object> refreshEnd = new Observer<Object>() {
			@Override
			public <V> void onNext(V value) {
				endTransaction();
			}

			@Override
			public <V> void onCompleted(V value) {
				endTransaction();
			}
		};
		Subscription [] refreshEndSub = new Subscription[] {refresh.subscribe(refreshEnd)};
		Consumer<ObservableElement<E>> elFn = element -> {
			onElement.accept(element);
			// The refresh end always needs to be after the elements
			Subscription oldRefreshEnd = refreshEndSub[0];
			refreshEndSub[0] = refresh.subscribe(refreshEnd);
			oldRefreshEnd.unsubscribe();
		};
		Subscription collSub;
		if(forward)
			collSub = collection.onElement(elFn);
		else
			collSub = ((ObservableReversibleCollection<E>) collection).onElementReverse(onElement);
		return () -> {
			refreshStartSub.unsubscribe();
			refreshEndSub[0].unsubscribe();
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
