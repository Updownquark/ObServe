package org.observe.collect.impl;

import java.util.Collection;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.ObservableElement;
import org.observe.util.ObservableUtils;

import prisms.lang.Type;

class ExposedObservableElement<E> implements ObservableElement<E> {
	private final InternalObservableElementImpl<E> theInternalElement;

	private final Collection<Subscription> theSubscriptions;

	ExposedObservableElement(InternalObservableElementImpl<E> internal, Collection<Subscription> subscriptions) {
		theInternalElement = internal;
		theSubscriptions = subscriptions;
	}

	protected InternalObservableElementImpl<E> getInternalElement() {
		return theInternalElement;
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
	public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
		Subscription ret = theInternalElement.subscribe(new Observer<ObservableValueEvent<E>>() {
			@Override
			public <V extends ObservableValueEvent<E>> void onNext(V event) {
				ObservableValueEvent<E> event2 = ObservableUtils.wrap(event, ExposedObservableElement.this);
				observer.onNext(event2);
			}

			@Override
			public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
				ObservableValueEvent<E> event2 = createChangeEvent(event.getOldValue(), event.getValue(), event.getCause());
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