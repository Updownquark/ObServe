package org.observe.util;

import java.util.Collection;
import java.util.Iterator;

import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableSet;

import prisms.lang.Type;

/**
 * Wraps an observable set
 *
 * @param <T> The type of the set
 */
public class ObservableSetWrapper<T> implements ObservableSet<T> {
	private final ObservableSet<T> theWrapped;

	/** @param wrap The set to wrap */
	public ObservableSetWrapper(ObservableSet<T> wrap) {
		theWrapped = wrap;
	}

	@Override
	public ObservableValue<CollectionSession> getSession() {
		return theWrapped.getSession();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theWrapped.lock(write, cause);
	}

	@Override
	public Type getType() {
		return theWrapped.getType();
	}

	@Override
	public Subscription onElement(java.util.function.Consumer<? super ObservableElement<T>> observer) {
		return theWrapped.onElement(observer);
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
	public Iterator<T> iterator() {
		return theWrapped.iterator();
	}

	@Override
	public T [] toArray() {
		return theWrapped.toArray();
	}

	@Override
	public <T2> T2 [] toArray(T2 [] a) {
		return theWrapped.toArray(a);
	}

	@Override
	public boolean add(T e) {
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
	public boolean addAll(Collection<? extends T> c) {
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
	public String toString() {
		return theWrapped.toString();
	}
}
