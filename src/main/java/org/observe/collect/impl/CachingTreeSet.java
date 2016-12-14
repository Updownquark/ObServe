package org.observe.collect.impl;

import java.util.Comparator;
import java.util.function.Consumer;

import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableOrderedElement;
import org.observe.collect.ObservableSortedSet;

/**
 * A caching set that stores its values in a tree set
 *
 * @param <E> The type of element in the set
 */
public class CachingTreeSet<E> extends CachingObservableCollection<E> implements ObservableSortedSet<E> {

	/** @param wrap The set to keep a cache of */
	public CachingTreeSet(ObservableSortedSet<E> wrap) {
		super(wrap);
	}

	@Override
	protected ObservableCollection<E> createCacheCollection(ObservableCollection<E> wrap) {
		return new ObservableTreeSet<>(wrap.getType(), ((ObservableSortedSet<E>) wrap).comparator());
	}

	@Override
	protected Subscription sync(ObservableCollection<E> wrapped, ObservableCollection<E> cached) {
		return ((ObservableSortedSet<E>) wrapped).onElement(el -> {
			el.subscribe(new Observer<ObservableValueEvent<E>>() {
				@Override
				public <V extends ObservableValueEvent<E>> void onNext(V event) {
					if (event.isInitial())
						cached.add(event.getValue());
					else if (comparator().compare(event.getOldValue(), event.getValue()) != 0) {
						cached.remove(event.getOldValue());
						cached.add(event.getValue());
					}
				}

				@Override
				public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
					cached.remove(event.getValue());
				}
			});
		});
	}

	@Override
	protected ObservableSortedSet<E> getWrapped() {
		return (ObservableSortedSet<E>) super.getWrapped();
	}

	@Override
	protected ObservableSortedSet<E> getCache() {
		return (ObservableSortedSet<E>) super.getCache();
	}

	@Override
	public Comparator<? super E> comparator() {
		return getCache().comparator();
	}

	@Override
	public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
		return getCache().onOrderedElement(onElement);
	}

	@Override
	public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
		return getCache().onElementReverse(onElement);
	}

	@Override
	public Iterable<E> descending() {
		// Not supporting remove for now
		return getCache().descending();
	}

	@Override
	public Iterable<E> iterateFrom(E element, boolean included, boolean reversed) {
		// Not supporting remove for now
		return getCache().iterateFrom(element, included, reversed);
	}

	@Override
	public E pollFirst() {
		return getWrapped().pollFirst();
	}

	@Override
	public E pollLast() {
		return getWrapped().pollLast();
	}
}
