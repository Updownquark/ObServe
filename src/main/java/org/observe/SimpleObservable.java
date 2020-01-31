package org.observe;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.qommons.Identifiable;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;

/**
 * A simple observable that can be controlled directly
 *
 * @param <T> The type of values from this observable
 */
public class SimpleObservable<T> implements Observable<T>, Observer<T> {
	private final Consumer<? super Observer<? super T>> theOnSubscribe;
	private final Object theIdentity;
	private boolean isAlive = true;
	private final ListenerList<Observer<? super T>> theListeners;
	private final boolean isInternalState;
	private final Transactable theLock;

	/** Creates a simple observable */
	public SimpleObservable() {
		this(false, false);
	}

	/**
	 * @param internalState Whether this observable is firing changes for some valued state
	 * @param safe Whether this observable is externally thread-safed
	 */
	protected SimpleObservable(boolean internalState, boolean safe) {
		this(null, internalState, safe);
	}

	/**
	 * @param onSubscribe The function to notify when a subscription is added to this observable
	 * @param internalState Whether this observable is firing changes for some valued state
	 * @param safe Whether this observable is externally thread-safed
	 */
	public SimpleObservable(Consumer<? super Observer<? super T>> onSubscribe, boolean internalState, boolean safe) {
		this(onSubscribe, null, internalState, safe ? new ReentrantReadWriteLock() : null, null);
	}

	/**
	 * @param onSubscribe The function to notify when a subscription is added to this observable
	 * @param identity The identity for this observable
	 * @param internalState Whether this observable is firing changes for some valued state
	 * @param lock The lock for this observable
	 * @param listening Listening options for this observable
	 */
	public SimpleObservable(Consumer<? super Observer<? super T>> onSubscribe, Object identity, boolean internalState,
		ReentrantReadWriteLock lock, ListenerList.Builder listening) {
		this(onSubscribe, identity, internalState, Transactable.transactable(lock), listening);
	}

	/**
	 * @param onSubscribe The function to notify when a subscription is added to this observable
	 * @param identity The identity for this observable
	 * @param internalState Whether this observable is firing changes for some valued state
	 * @param lock The lock for this observable
	 * @param listening Listening options for this observable
	 */
	public SimpleObservable(Consumer<? super Observer<? super T>> onSubscribe, Object identity, boolean internalState, Transactable lock,
		ListenerList.Builder listening) {
		theIdentity = identity != null ? identity : Identifiable.baseId("observable", this);
		/* Java's ConcurrentLinkedQueue has a problem (for me) that makes the class unusable here.  As documented in fireNext() below, the
		 * behavior of observables is advertised such that if a listener is added by a listener, the new listener will be added at the end
		 * of the listeners and will be notified for the currently firing value.  ConcurrentLinkedQueue allows for this except when the
		 * listener adding the new listener was previously the last listener in the queue.  ConcurrentLinkedQueue's iterator looks ahead in
		 * the next() method, not hasNext(); so if a listener returned by that iterator adds another value to the queue, that iterator will
		 * not see it.
		 * That's why the following line is commented out and replaced with a possibly less efficient but more predictable implementation of
		 * mine.
		 */
		// theListeners = new ConcurrentLinkedQueue<>();
		if (listening == null)
			listening = ListenerList.build();
		theListeners = listening.build();
		theOnSubscribe = onSubscribe;
		isInternalState = internalState;
		theLock = lock;
	}

	/** @return This observable's lock */
	protected Transactable getLock() {
		return theLock;
	}

	@Override
	public Object getIdentity() {
		return theIdentity;
	}

	@Override
	public Subscription subscribe(Observer<? super T> observer) {
		if (!isAlive) {
			observer.onCompleted(null);
			return () -> {};
		} else {
			Runnable unsub = theListeners.add(observer, isInternalState);
			if (theOnSubscribe != null)
				theOnSubscribe.accept(observer);
			return unsub::run;
		}
	}

	@Override
	public <V extends T> void onNext(V value) {
		try (Transaction lock = theLock == null ? Transaction.NONE : theLock.lock(true, value)) {
			if (!isAlive)
				throw new IllegalStateException("Firing a value on a completed observable");
			theListeners.forEach(//
				observer -> observer.onNext(value));
		}
	}

	@Override
	public <V extends T> void onCompleted(V value) {
		try (Transaction lock = theLock == null ? Transaction.NONE : theLock.lock(true, value)) {
			if (!isAlive)
				return;
			isAlive = false;
			theListeners.forEach(//
				observer -> observer.onCompleted(value));
			theListeners.clear();
		}
	}

	@Override
	public boolean isSafe() {
		return theLock != null;
	}

	/**
	 * Locks this observable exclusively, allowing {@link #onNext(Object)} or {@link #onCompleted(Object)} to be called on the current
	 * thread while the lock is held.
	 *
	 * @return The transaction to close to release the lock
	 */
	public Transaction lockWrite() {
		return theLock == null ? Transaction.NONE : theLock.lock(true, null);
	}

	@Override
	public Transaction lock() {
		return theLock == null ? Transaction.NONE : theLock.lock(false, null);
	}

	@Override
	public Transaction tryLock() {
		return theLock == null ? Transaction.NONE : theLock.tryLock(false, null);
	}

	/** @return An observable that fires events from this SimpleObservable but cannot be used to initiate events */
	public Observable<T> readOnly() {
		return new ReadOnlyObservable<>(this);
	}

	static class ReadOnlyObservable<T> implements Observable<T> {
		private final SimpleObservable<T> theWrapped;

		ReadOnlyObservable(SimpleObservable<T> wrap) {
			theWrapped = wrap;
		}

		@Override
		public Object getIdentity() {
			return theWrapped.getIdentity();
		}

		@Override
		public Subscription subscribe(Observer<? super T> observer) {
			return theWrapped.subscribe(observer);
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}

		@Override
		public Transaction lock() {
			return theWrapped.lock();
		}

		@Override
		public Transaction tryLock() {
			return theWrapped.tryLock();
		}
	}
}
