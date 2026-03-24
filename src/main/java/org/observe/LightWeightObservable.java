package org.observe;

import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.Subscription;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;
import org.qommons.collect.ListenerQueue;

/**
 * A simple observable that can be controlled directly
 *
 * @param <T> The type of values from this observable
 */
public class LightWeightObservable<T> extends AbstractIdentifiable implements Observable<T>, Observer<T> {
	private boolean isAlive = true;
	private final ListenerQueue<Observer<? super T>> theListeners;

	/** Creates a simple observable */
	public LightWeightObservable() {
		this(ListenerList.build().withFastSize(false).build());
	}

	/** @param listeners The listeners for this observable */
	public LightWeightObservable(ListenerQueue<Observer<? super T>> listeners) {
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

	/** @param listening Produces the listener list for this observable */
	public LightWeightObservable(Function<? super LightWeightObservable<T>, ? extends ListenerQueue<Observer<? super T>>> listening) {
		theListeners = listening.apply(this);
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
	public long getStamp() {
		return theListeners.getStamp();
	}

	@Override
	protected Object createIdentity() {
		return Identifiable.baseId("lightWeightObservable", this);
	}

	/** @return Whether this observable is still alive */
	public boolean isAlive() {
		return isAlive;
	}

	@Override
	public Subscription subscribe(Observer<? super T> observer) {
		if (!isAlive) {
			observer.onCompleted(null);
			return Subscription.NONE;
		} else {
			return theListeners.addNew(observer);
		}
	}

	@Override
	public void onNext(T value) {
		if (!isAlive)
			throw new IllegalStateException("Firing a value on a completed observable");
		theListeners.forEach(//
			observer -> observer.onNext(value));
	}

	@Override
	public void onCompleted(Supplier<Causable> cause) {
		if (!isAlive)
			return;
		isAlive = false;
		theListeners.forEach(//
			observer -> observer.onCompleted(cause));
		theListeners.clear();
	}

	/** Resets this observable so that it can be used again after {@link #onCompleted(Supplier)} is called */
	public void reUse() {
		isAlive = true;
	}

	@Override
	public boolean isSafe() {
		return false;
	}

	/** @return Whether anyone is listening to this observable */
	public boolean isAnyoneListening() {
		return !theListeners.isEmpty();
	}

	/** Increments this observable's {@link #getStamp()} without invoking any listeners */
	public void incrementStamp() {
		theListeners.incrementStamp();
	}

	/** @return An observable that fires events from this SimpleObservable but cannot be used to initiate events */
	public Observable<T> readOnly() {
		return new ReadOnlyObservable<>(this);
	}

	@Override
	public CoreChangeSources getChangeSources() {
		return CoreChangeSources.core(this);
	}

	static class ReadOnlyObservable<T> extends AbstractIdentifiable implements Observable<T> {
		private final Observable<T> theWrapped;

		ReadOnlyObservable(Observable<T> wrap) {
			theWrapped = wrap;
		}

		@Override
		protected Object createIdentity() {
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
		public long getStamp() {
			return theWrapped.getStamp();
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return theWrapped.getChangeSources();
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
