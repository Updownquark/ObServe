package org.observe;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

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
	private boolean isAlive = true;
	private final ListenerList<Observer<? super T>> theListeners;
	private final boolean isInternalState;
	private final ReentrantReadWriteLock theLock;

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
		this(onSubscribe, internalState, safe ? new ReentrantReadWriteLock() : null, null);
	}

	public SimpleObservable(Consumer<? super Observer<? super T>> onSubscribe, boolean internalState, ReentrantReadWriteLock lock,
		Consumer<ListenerList.Builder> listeningOptions) {
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
		ListenerList.Builder listeningBuilder = ListenerList.build();
		if (listeningOptions != null)
			listeningOptions.accept(listeningBuilder);
		theListeners = listeningBuilder.build();
		theOnSubscribe = onSubscribe;
		isInternalState = internalState;
		theLock = lock;
	}

	protected ReentrantReadWriteLock getLock() {
		return theLock;
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
		if (theLock != null)
			theLock.writeLock().lock();
		try {
			if (!isAlive)
				throw new IllegalStateException("Firing a value on a completed observable");
			theListeners.forEach(//
				observer -> observer.onNext(value));
		} finally {
			if (theLock != null)
				theLock.writeLock().unlock();
		}
	}

	@Override
	public <V extends T> void onCompleted(V value) {
		if (theLock != null)
			theLock.writeLock().lock();
		try {
			if (!isAlive)
				return;
			theListeners.forEach(//
				observer -> observer.onCompleted(value));
			theListeners.clear();
		} finally {
			if (theLock != null)
				theLock.writeLock().unlock();
		}
	}

	@Override
	public boolean isSafe() {
		return theLock != null;
	}

	@Override
	public Transaction lock() {
		return Transactable.lock(theLock, false);
	}

	@Override
	public Transaction tryLock() {
		return Transactable.tryLock(theLock, false);
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
