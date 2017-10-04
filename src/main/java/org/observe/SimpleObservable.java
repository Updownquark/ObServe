package org.observe;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A simple observable that can be controlled directly
 *
 * @param <T> The type of values from this observable
 */
public class SimpleObservable<T> implements Observable<T>, Observer<T> {
	private final Consumer<? super Observer<? super T>> theOnSubscribe;
	private final AtomicBoolean isAlive = new AtomicBoolean(true);
	private final org.qommons.collect.ListenerList<Observer<? super T>> theListeners;

	/** Creates a simple observable */
	public SimpleObservable() {
		this(null);
	}

	/** @param onSubscribe The function to notify when a subscription is added to this observable */
	public SimpleObservable(Consumer<? super Observer<? super T>> onSubscribe) {
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
		theListeners = new org.qommons.collect.ListenerList<>();
		theOnSubscribe = onSubscribe;
	}

	@Override
	public Subscription subscribe(Observer<? super T> observer) {
		if (!isAlive.get()) {
			observer.onCompleted(null);
			return () -> {};
		} else {
			Runnable unsub = theListeners.add(observer);
			if (theOnSubscribe != null)
				theOnSubscribe.accept(observer);
			return unsub::run;
		}
	}

	@Override
	public <V extends T> void onNext(V value) {
		if (!isAlive.get())
			throw new IllegalStateException("Firing a value on a completed observable");
		theListeners.forEach(//
			observer -> observer.onNext(value));
	}

	@Override
	public <V extends T> void onCompleted(V value) {
		if (isAlive.getAndSet(false)) {
			theListeners.forEach(//
				observer -> observer.onCompleted(value));
			theListeners.clear();
		}
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
	}
}
