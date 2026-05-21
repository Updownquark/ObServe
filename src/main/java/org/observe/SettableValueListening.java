package org.observe;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Subscription;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.ListenerQueue;

public class SettableValueListening<T> implements Observable<T> {
	private final Object theIdentity;
	private final Transactable theLock;
	private final ListenerQueue<Observer<? super T>> theListeners;
	private volatile int theLockDepth;

	public SettableValueListening(Object identity, Transactable lock, ListenerQueue<Observer<? super T>> listeners) {
		theIdentity = identity;
		theLock = lock;
		theListeners = listeners;
	}

	public boolean isAnyoneListening() {
		return !theListeners.isEmpty();
	}

	public boolean isEmpty() {
		return theListeners.isEmpty();
	}

	@Override
	public boolean isEventing() {
		return theListeners.isFiring();
	}

	@Override
	public Object getIdentity() {
		return theIdentity;
	}

	@Override
	public Identifiable alias(String alias) {
		return this; // Don't support aliases on the changes observable
	}

	@Override
	public Set<String> getAliases() {
		return Collections.emptySet(); // Don't support aliases on the changes observable
	}

	@Override
	public CoreId getCoreId() {
		return theLock == null ? CoreId.EMPTY : theLock.getCoreId();
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theLock.getThreadConstraint();
	}

	@Override
	public long getStamp() {
		return theListeners.getStamp();
	}

	public void fire(T value) {
		theListeners.forEach(//
			observer -> observer.onNext(value));
	}

	public void fireCompleted(Supplier<Causable> cause) {
		theListeners.forEach(//
			observer -> observer.onCompleted(cause));
	}

	@Override
	public Subscription subscribe(Observer<? super T> observer) {
		Subscription sub = theListeners.addNew(observer);
		if (theLockDepth != 0) {
			// If the listeners are currently locked, try to lock the new one as well.
			// We can only do this safely within a lock (otherwise there's a potential the observer would not be unlocked).
			// If this subscription is happening off of the event thread, the observer will just have to obtain its lock for every change.
			Transaction writeLock = innerWriteLock(true, null);
			try {
				if (writeLock != null && theLockDepth != 0) {
					// Try to lock the observer. If this fails, we can't go back in time and forbid the lock,
					// so the new observer will just have to obtain its lock for every event.
					// There's a possibility for deadlock here, but I feel this is the best I can do.
					observer.tryLock();
				}
			} finally {
				if (writeLock != null)
					writeLock.close();
			}
		}
		return sub;
	}

	@Override
	public Transaction lock(boolean tryOnly) {
		return theLock == null ? Transaction.NONE : theLock.lock(tryOnly);
	}

	private Transaction theWriteLock;

	/**
	 * This class does not implement Transactable so it can more safely be returned as-is from {@link SimpleSettableValue#noInitChanges()}
	 *
	 * @param tryOnly Whether to only attempt the transaction
	 * @param cause The cause for any changes that the lock is to be obtained for
	 * @return The transaction to close the lock, or null if <code>tryOnly</code> is true and the lock attempt was unsuccessful
	 * @see org.qommons.Transactable#lockWrite(boolean, java.lang.Object)
	 */
	public Transaction lockWrite(boolean tryOnly, Object cause) {
		Transaction lock = innerWriteLock(tryOnly, cause);
		if (lock == null)
			return null;
		else if (0 == theLockDepth++) { // Initial lock
			if (!doLock(tryOnly, cause, lock))
				return null;
		} else
			lock.close();
		return this::unlock;
	}

	private Transaction innerWriteLock(boolean tryOnly, Object cause) {
		return theLock == null ? Transaction.NONE : theLock.lockWrite(tryOnly, cause);
	}

	boolean isLockSuccess;

	private boolean doLock(boolean tryOnly, Object cause, Transaction currentLock) {
		isLockSuccess = true;
		do {
			if (!isLockSuccess)
				currentLock = innerWriteLock(false, cause);
			isLockSuccess = true;
			boolean complete = false;
			try {
				theListeners.visitEach(l -> {
					if (isLockSuccess)
						isLockSuccess = l.tryLock();
				});
				complete = true;
			} finally {
				if (!complete || !isLockSuccess) {
					isLockSuccess = false;
					unlock();
					currentLock.close();
				}
			}
		} while (!isLockSuccess && !tryOnly);
		if (isLockSuccess) {
			theWriteLock = currentLock;
			return true;
		} else {
			theLockDepth = 0;
			return false;
		}
	}

	void unlock() {
		if (0 == --theLockDepth) { // Final lock
			theListeners.visitEach(Observer::unlock);
			Transaction wl = theWriteLock;
			theWriteLock = null;
			wl.close();
		}
	}

	public void incrementStamp() {
		theListeners.incrementStamp();
	}

	@Override
	public CoreChangeSources getChangeSources() {
		return CoreChangeSources.core(this);
	}
}
