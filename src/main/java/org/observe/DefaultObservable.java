package org.observe;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A default implementation of observable
 *
 * @param <T> The type of values this observable provides
 */
public class DefaultObservable<T> implements Observable<T> {
	/**
	 * Listens for subscriptions to an observable
	 *
	 * @param <T> The type of observable to listen for subscriptions to
	 */
	public static interface OnSubscribe<T> {
		/** @param observer The new observer on the observable */
		void onsubscribe(Observer<? super T> observer);
	}

	private OnSubscribe<T> theOnSubscribe;
	private AtomicBoolean isAlive = new AtomicBoolean(true);
	private AtomicBoolean hasIssuedController = new AtomicBoolean(false);
	private AtomicBoolean isFiringEvent = new AtomicBoolean(false);
	private Queue<Observer<? super T>> theListeners;

	/** Creates the observable */
	public DefaultObservable() {
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
		theListeners = new org.qommons.LinkedQueue<>();
	}

	/**
	 * Obtains control of the observable. This method may only be called once.
	 *
	 * @param onSubscribe The listener to call for each new subscription to the observable
	 * @return An observer whose methods control this observable
	 * @throws IllegalStateException If this observer is already controlled
	 */
	public Observer<T> control(OnSubscribe<T> onSubscribe) throws IllegalStateException {
		if(hasIssuedController.getAndSet(true))
			throw new IllegalStateException("This observable is already controlled");
		theOnSubscribe = onSubscribe;
		return new Observer<T>(){
			@Override
			public <V extends T> void onNext(V value) {
				fireNext(value);
			}

			@Override
			public <V extends T> void onCompleted(V value) {
				fireCompleted(value);
			}

			@Override
			public void onError(Throwable e) {
				fireError(e);
			}
		};
	}

	@Override
	public Subscription subscribe(Observer<? super T> observer) {
		if(!isAlive.get()) {
			observer.onCompleted(null);
			return () -> {
			};
		} else {
			theListeners.add(observer);
			if(theOnSubscribe != null)
				theOnSubscribe.onsubscribe(observer);
			return () -> {
				theListeners.remove(observer);
			};
		}
	}

	private void fireNext(T value) {
		if(!isAlive.get())
			throw new IllegalStateException("Firing a value on a completed observable");
		boolean preFiring = isFiringEvent.getAndSet(true);
		try {
			if (preFiring)
				throw new IllegalStateException(
					"A new value cannot be fired when not all listeners have been notified of the current value");
			// This allows listeners to be added by listeners. Those new listeners will be fired last.
			for (Observer<? super T> observer : theListeners) {
				try {
					observer.onNext(value);
				} catch (Throwable e) {
					observer.onError(e);
				}
			}
		} finally {
			isFiringEvent.set(false);
		}
	}

	private void fireCompleted(T value) {
		if(isAlive.getAndSet(false)) {
			boolean preFiring = isFiringEvent.getAndSet(true);
			try {
				if (preFiring)
					throw new IllegalStateException(
						"A value cannot be completed when not all listeners have been notified of the current value");
				Observer<? super T>[] observers = theListeners.toArray(new Observer[theListeners.size()]);
				theListeners.clear();
				for (Observer<? super T> observer : observers) {
					try {
						observer.onCompleted(value);
					} catch (Throwable e) {
						observer.onError(e);
					}
				}
			} finally {
				isFiringEvent.set(false);
			}
		}
	}

	private void fireError(Throwable e) {
		if(!isAlive.get())
			throw new IllegalStateException("Firing a value on a completed observable");
		for(Observer<? super T> observer : theListeners)
			observer.onError(e);
	}

	@Override
	public boolean isSafe() {
		return false;
	}

	/**
	 * Clones this value into a new observable that is NOT yet controlled. Note that this class does not implement {@link Cloneable}, so
	 * subclasses will need to implement Cloneable for this method to not throw the {@link CloneNotSupportedException}
	 */
	@Override
	protected DefaultObservable<T> clone() throws CloneNotSupportedException {
		DefaultObservable<T> ret = (DefaultObservable<T>) super.clone();
		// ret.theListeners = new ConcurrentLinkedQueue<>();
		ret.theListeners = new org.qommons.LinkedQueue<>();
		ret.isAlive = new AtomicBoolean(true);
		ret.hasIssuedController = new AtomicBoolean(false);
		return ret;
	}
}
