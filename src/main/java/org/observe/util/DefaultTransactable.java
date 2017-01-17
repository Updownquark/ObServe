package org.observe.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

import org.observe.DefaultObservableValue;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.collect.CollectionSession;
import org.observe.collect.DefaultCollectionSession;
import org.qommons.Transactable;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

/** A simple transactable that manages a reentrant session for an observable collection or data structure */
public class DefaultTransactable implements Transactable {
	private final ReadWriteLock theLock;

	private final AtomicInteger theDepth;

	private final DefaultObservableValue<CollectionSession> theObservableSession;

	private final Observer<ObservableValueEvent<CollectionSession>> theSessionController;

	private CollectionSession theInternalSessionValue;

	/** @param lock The lock to use. Must support reentrancy. */
	public DefaultTransactable(ReadWriteLock lock) {
		theLock = lock;
		theDepth = new AtomicInteger();
		theObservableSession = new DefaultObservableValue<CollectionSession>() {
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
		theSessionController = theObservableSession.control(null);
	}

	/** @return The session observable to use for the collection */
	public ObservableValue<CollectionSession> getSession() {
		return theObservableSession;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		Lock lock = write ? theLock.writeLock() : theLock.readLock();
		lock.lock();
		boolean success = false;
		try {
			if(write) {
				int depth = theDepth.getAndIncrement();
				if(depth != 0) {
					success = true;
					return new EndTransaction(lock, true);
				}

				theInternalSessionValue = createSession(cause);
				Observer.onNextAndFinish(theSessionController,
					theObservableSession.createChangeEvent(null, theInternalSessionValue, cause));
			}
			success = true;
			return new EndTransaction(lock, write);
		} finally {
			if(!success) // If we don't successfully return the transaction that allows the unlock, we need to do it here
				lock.unlock();
		}
	}

	/**
	 * @param cause The cause of the transaction, may be null
	 * @return The collection session to use for this transaction
	 */
	protected CollectionSession createSession(Object cause) {
		return new DefaultCollectionSession(cause);
	}

	private void endTransaction(Lock lock, boolean write) {
		try {
			if(write) {
				int depth = theDepth.decrementAndGet();
				if(depth != 0)
					return;

				CollectionSession old = theInternalSessionValue;
				theInternalSessionValue = null;
				Observer.onNextAndFinish(theSessionController, theObservableSession.createChangeEvent(old, null, old.getCause()));
				old.finish();
			}
		} finally {
			lock.unlock();
		}
	}

	private class EndTransaction implements Transaction {
		private final Lock theTransactionLock;
		private final boolean isWrite;
		private volatile boolean hasRun;

		EndTransaction(Lock lock, boolean write) {
			theTransactionLock = lock;
			isWrite = write;
		}

		@Override
		public void close() {
			if(hasRun)
				return;
			hasRun = true;
			endTransaction(theTransactionLock, isWrite);
		}

		@Override
		protected void finalize() {
			if(!hasRun)
				close();
		}
	}
}
