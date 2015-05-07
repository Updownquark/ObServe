package org.observe.collect;

import java.util.Collection;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;

import prisms.lang.Type;

class ExposedObservableElement<E> implements ObservableElement<E> {
	private final InternalObservableElementImpl<E> theInternalElement;

	private final Collection<Runnable> theSubscriptions;

	ExposedObservableElement(InternalObservableElementImpl<E> internal, Collection<Runnable> subscriptions) {
		theInternalElement = internal;
		theSubscriptions = subscriptions;
	}

	@Override
	public Type getType() {
		return theInternalElement.getType();
	}

	@Override
	public E get() {
		return theInternalElement.get();
	}

	@Override
	public Runnable observe(Observer<? super ObservableValueEvent<E>> observer) {
		Runnable ret = theInternalElement.observe(new Observer<ObservableValueEvent<E>>() {
			@Override
			public <V extends ObservableValueEvent<E>> void onNext(V event) {
				ObservableValueEvent<E> event2 = createEvent(event.getOldValue(), event.getValue(), event.getCause());
				observer.onNext(event2);
			}

			@Override
			public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
				ObservableValueEvent<E> event2 = createEvent(event.getOldValue(), event.getValue(), event.getCause());
				observer.onCompleted(event2);
			}

			@Override
			public void onError(Throwable e) {
				observer.onError(e);
			}
		});
		theSubscriptions.add(ret);
		return ret;
	}

	@Override
	public ObservableValue<E> persistent() {
		return theInternalElement;
	}

	@Override
	public String toString() {
		return getType() + " set element (" + get() + ")";
	}
}