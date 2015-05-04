package org.observe.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import org.observe.DefaultObservableValue;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.collect.CollectionSession;
import org.observe.collect.DefaultCollectionSession;

import prisms.lang.Type;

/** A simple transactable that manages a reentrant session for an observable collection or data structure */
public class DefaultTransactable implements Transactable {
	private final Lock theLock;

	private final AtomicInteger theDepth;

	private final DefaultObservableValue<CollectionSession> theObservableSession;

	private final Observer<ObservableValueEvent<CollectionSession>> theSessionController;

	private CollectionSession theInternalSessionValue;

	/** @param lock The lock to use. Must support reentrancy. */
	public DefaultTransactable(Lock lock) {
		theLock = lock;
		theDepth = new AtomicInteger();
		theObservableSession = new DefaultObservableValue<CollectionSession>() {
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
		theSessionController = theObservableSession.control(null);
	}

	/** @return The session observable to use for the collection */
	public ObservableValue<CollectionSession> getSession() {
		return theObservableSession;
	}

	@Override
	public Transaction startTransaction(Object cause) {
		theLock.lock();
		boolean success = false;
		try {
			int depth = theDepth.getAndIncrement();
			if(depth != 0) {
				success = true;
				return new EndTransaction();
			}

			theInternalSessionValue = createSession(cause);
			theSessionController.onNext(theObservableSession.createEvent(null, theInternalSessionValue, cause));
			success = true;
			return new EndTransaction();
		} finally {
			if(!success) // If we don't successfully return the transaction that allows the unlock, we need to do it here
				theLock.unlock();
		}
	}

	/**
	 * @param cause The cause of the transaction, may be null
	 * @return The collection session to use for this transaction
	 */
	protected CollectionSession createSession(Object cause) {
		return new DefaultCollectionSession(cause);
	}

	private void endTransaction() {
		try {
			int depth = theDepth.decrementAndGet();
			if(depth != 0)
				return;

			CollectionSession old = theInternalSessionValue;
			theInternalSessionValue = null;
			theSessionController.onNext(theObservableSession.createEvent(old, null, old.getCause()));
		} finally {
			theLock.unlock();
		}
	}

	private class EndTransaction implements Transaction {
		private volatile boolean hasRun;

		@Override
		public void close() {
			if(hasRun)
				return;
			hasRun = true;
			endTransaction();
		}
	}
}
