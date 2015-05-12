package org.observe.util;

import java.util.Collection;
import java.util.Iterator;

import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableOrderedCollection;
import org.observe.collect.OrderedObservableElement;

import prisms.lang.Type;

/**
 * Wraps an observable ordered collection
 *
 * @param <E> The type of the ordered collection
 */
public class ObservableOrderedCollectionWrapper<E> implements ObservableOrderedCollection<E> {
	private final ObservableOrderedCollection<E> theWrapped;

	/** @param wrap The list to wrap */
	public ObservableOrderedCollectionWrapper(ObservableOrderedCollection<E> wrap) {
		theWrapped = wrap;
	}

	@Override
	public ObservableValue<CollectionSession> getSession() {
		return theWrapped.getSession();
	}

	@Override
	public Type getType() {
		return theWrapped.getType();
	}

	@Override
	public int size() {
		return theWrapped.size();
	}

	@Override
	public boolean isEmpty() {
		return theWrapped.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return theWrapped.contains(o);
	}

	@Override
	public Iterator<E> iterator() {
		return theWrapped.iterator();
	}

	@Override
	public E [] toArray() {
		return theWrapped.toArray();
	}

	@Override
	public <T2> T2 [] toArray(T2 [] a) {
		return theWrapped.toArray(a);
	}

	@Override
	public boolean add(E e) {
		return theWrapped.add(e);
	}

	@Override
	public boolean remove(Object o) {
		return theWrapped.remove(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return theWrapped.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		return theWrapped.addAll(c);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return theWrapped.removeAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return theWrapped.retainAll(c);
	}

	@Override
	public void clear() {
		theWrapped.clear();
	}

	@Override
	public Subscription onOrderedElement(java.util.function.Consumer<? super OrderedObservableElement<E>> observer) {
		return theWrapped.onOrderedElement(observer);
	}

	@Override
	public String toString() {
		return theWrapped.toString();
	}
}
