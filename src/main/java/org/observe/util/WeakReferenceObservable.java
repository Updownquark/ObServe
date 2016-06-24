package org.observe.util;

import java.lang.ref.WeakReference;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;

import com.google.common.reflect.TypeToken;

/**
 * An observable holding a {@link WeakReference weak reference} to a value. When the {@link #check()} method is called, this class checks
 * the weak reference. If the reference has been garbage-collected, this observable will fire its {@link Observer#onCompleted(Object)
 * completed} events. External code is responsible for calling {@link #check()}.
 *
 * @param <T> The type of value in this observable
 */
public class WeakReferenceObservable<T> implements ObservableValue<T> {
	private final TypeToken<T> theType;
	private final WeakReference<T> theRef;
	private final boolean isSafe;
	private final java.util.concurrent.ConcurrentLinkedQueue<Observer<? super ObservableValueEvent<T>>> theObservers;

	/**
	 * @param type The type of value in this observable
	 * @param value The value for this observable
	 * @param safe Whether the {@link #check()} method will only be called from a single thread at a time
	 */
	public WeakReferenceObservable(TypeToken<T> type, T value, boolean safe) {
		theType = type;
		theType.getRawType().cast(value);
		theRef = new WeakReference<>(value);
		isSafe = safe;
		theObservers = new java.util.concurrent.ConcurrentLinkedQueue<>();
	}

	@Override
	public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
		T ref = theRef.get();
		if(ref == null) {
			observer.onCompleted(createInitialEvent(null));
			return () -> {
			};
		}
		theObservers.add(observer);
		observer.onNext(createInitialEvent(ref));
		return () -> {
			theObservers.remove(observer);
		};
	}

	@Override
	public TypeToken<T> getType() {
		return theType;
	}

	@Override
	public T get() {
		return theRef.get();
	}

	@Override
	public boolean isSafe() {
		return isSafe;
	}

	/**
	 * Checks the reference stored in this observable. If the reference has been garbage-collected, this observable will fire its
	 * {@link Observer#onCompleted(Object) completed} events.
	 */
	public void check() {
		if(theRef.get() == null) {
			Observer<? super ObservableValueEvent<T>> [] observers = theObservers.toArray(new Observer[0]);
			theObservers.clear();
			for(Observer<? super ObservableValueEvent<T>> observer : observers)
				observer.onCompleted(createChangeEvent(null, null, null));
		}
	}
}
