package org.observe;

import java.util.concurrent.ConcurrentLinkedQueue;
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
	private ConcurrentLinkedQueue<Observer<? super T>> theListeners;

	/** Creates the observable */
	public DefaultObservable() {
		theListeners = new ConcurrentLinkedQueue<>();
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
	public Runnable observe(Observer<? super T> observer) {
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
		for(Observer<? super T> observer : theListeners) {
			try {
				observer.onNext(value);
			} catch(Throwable e) {
				observer.onError(e);
			}
		}
	}

	private void fireCompleted(T value) {
		if(isAlive.getAndSet(false)) {
			Observer<? super T> [] observers = theListeners.toArray(new Observer[theListeners.size()]);
			theListeners.clear();
			for(Observer<? super T> observer : observers) {
				try {
					observer.onCompleted(value);
				} catch(Throwable e) {
					observer.onError(e);
				}
			}
		}
	}

	private void fireError(Throwable e) {
		if(!isAlive.get())
			throw new IllegalStateException("Firing a value on a completed observable");
		for(Observer<? super T> observer : theListeners)
			observer.onError(e);
	}

	/**
	 * Clones this value into a new observable that is NOT yet controlled. Note that this class does not implement {@link Cloneable}, so
	 * subclasses will need to implement Cloneable for this method to not throw the {@link CloneNotSupportedException}
	 */
	@Override
	protected DefaultObservable<T> clone() throws CloneNotSupportedException {
		DefaultObservable<T> ret = (DefaultObservable<T>) super.clone();
		ret.theListeners = new ConcurrentLinkedQueue<>();
		ret.isAlive = new AtomicBoolean(true);
		ret.hasIssuedController = new AtomicBoolean(false);
		return ret;
	}
}
