package org.observe.collect;

import java.util.concurrent.locks.ReentrantLock;

import org.observe.DefaultObservableValue;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;

import prisms.lang.Type;

/** Manages transactions for a collection in a thread-safe way */
public class DefaultTransactionManager {
	private final ReentrantLock theLock;

	private CollectionSession theInternalSessionValue;
	private final DefaultObservableValue<CollectionSession> theInternalSession;
	private final ObservableValue<CollectionSession> theExposedSession;
	private final Observer<ObservableValueEvent<CollectionSession>> theSessionController;

	/** @param collection The parent of the collection to manage the transactions for */
	public DefaultTransactionManager(ObservableCollection<?> collection) {
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
		theExposedSession = new org.observe.ComposedObservableValue<>(sessions -> (CollectionSession) (sessions[0] != null ? sessions[0]
			: sessions[1]), true, theInternalSession, collection.getSession());
		theSessionController = theInternalSession.control(null);
	}

	/** @return The session observable to use for the collection */
	public ObservableValue<CollectionSession> getSession() {
		return theExposedSession;
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
			theSessionController.onNext(new org.observe.ObservableValueEvent<>(theInternalSession, oldSession, newSession, cause));
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
				theSessionController.onNext(new org.observe.ObservableValueEvent<>(theInternalSession, session, null, session.getCause()));
		} finally {
			theLock.unlock();
		}
	}
}
