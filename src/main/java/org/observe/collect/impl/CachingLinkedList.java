package org.observe.collect.impl;

import java.util.List;
import java.util.function.Consumer;

import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableList;
import org.observe.collect.ObservableOrderedCollection;
import org.observe.collect.ObservableOrderedElement;

/**
 * Caches the contents of an {@link ObservableOrderedCollection}
 *
 * @param <E> The type of elements in the list
 */
public class CachingLinkedList<E> extends CachingObservableCollection<E> implements ObservableOrderedCollection<E> {
	/** @param wrap The list to cache */
	public CachingLinkedList(ObservableOrderedCollection<E> wrap) {
		super(wrap);
	}

	@Override
	protected ObservableOrderedCollection<E> getWrapped() {
		return (ObservableOrderedCollection<E>) super.getWrapped();
	}

	@Override
	protected ObservableLinkedList<E> getCache() {
		return (ObservableLinkedList<E>) super.getCache();
	}

	@Override
	protected ObservableCollection<E> createCacheCollection(ObservableCollection<E> wrap) {
		return new ObservableLinkedList<>(getWrapped().getType());
	}

	@Override
	protected Subscription sync(ObservableCollection<E> wrapped, ObservableCollection<E> cached) {
		return ((ObservableList<E>) wrapped).onOrderedElement(el -> {
			el.subscribe(new Observer<ObservableValueEvent<E>>() {
				@Override
				public <V extends ObservableValueEvent<E>> void onNext(V event) {
					if (event.isInitial())
						cached.add(event.getValue());
					else if (event.getOldValue() != event.getValue())
						((List<E>) cached).set(el.getIndex(), event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
					cached.remove(el.getIndex());
				}
			});
		});
	}

	@Override
	public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
		return getCache().onOrderedElement(onElement);
	}
}
