package org.observe;

import org.qommons.Causable;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;

/**
 * A simple observable that can be controlled directly
 *
 * @param <T> The type of values from this observable
 */
public class LightWeightObservable<T> implements Observable<T>, Observer<T> {
	private boolean isAlive = true;
	private final ListenerList<Observer<? super T>> theListeners;

	/** Creates a simple observable */
	public LightWeightObservable() {
		this(ListenerList.build().build());
	}

	public LightWeightObservable(ListenerList<Observer<? super T>> listeners) {
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
		theListeners = listeners;
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return ThreadConstraint.ANY;
	}

	@Override
	public CoreId getCoreId() {
		return CoreId.EMPTY;
	}

	@Override
	public Transaction lock() {
		return Transaction.NONE;
	}

	@Override
	public Transaction tryLock() {
		return Transaction.NONE;
	}

	@Override
	public boolean isEventing() {
		return theListeners.isFiring();
	}

	@Override
	public Object getIdentity() {
		return null;
	}

	public boolean isAlive() {
		return isAlive;
	}

	protected boolean isInternalState() {
		return false;
	}

	@Override
	public Subscription subscribe(Observer<? super T> observer) {
		if (!isAlive) {
			observer.onCompleted(null);
			return () -> {};
		} else {
			Runnable unsub = theListeners.add(observer, isInternalState());
			return unsub::run;
		}
	}

	@Override
	public <V extends T> void onNext(V value) {
		if (!isAlive)
			throw new IllegalStateException("Firing a value on a completed observable");
		theListeners.forEach(//
			observer -> observer.onNext(value));
	}

	@Override
	public void onCompleted(Causable cause) {
		if (!isAlive)
			return;
		isAlive = false;
		theListeners.forEach(//
			observer -> observer.onCompleted(cause));
		theListeners.clear();
	}

	@Override
	public boolean isSafe() {
		return false;
	}

	/** @return An observable that fires events from this SimpleObservable but cannot be used to initiate events */
	public Observable<T> readOnly() {
		return new ReadOnlyObservable<>(this);
	}

	static class ReadOnlyObservable<T> implements Observable<T> {
		private final Observable<T> theWrapped;

		ReadOnlyObservable(Observable<T> wrap) {
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
		public ThreadConstraint getThreadConstraint() {
			return theWrapped.getThreadConstraint();
		}

		@Override
		public boolean isEventing() {
			return theWrapped.isEventing();
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

		@Override
		public CoreId getCoreId() {
			return theWrapped.getCoreId();
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!(obj instanceof ReadOnlyObservable))
				return false;
			return theWrapped.equals(((ReadOnlyObservable<?>) obj).theWrapped);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}
}
