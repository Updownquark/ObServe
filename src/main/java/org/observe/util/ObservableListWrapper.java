package org.observe.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableList;
import org.observe.collect.OrderedObservableElement;

import prisms.lang.Type;

/**
 * Wraps an observable list
 *
 * @param <T> The type of the list
 */
public class ObservableListWrapper<T> implements ObservableList<T> {
	private final ObservableList<T> theWrapped;

	/** @param wrap The list to wrap */
	public ObservableListWrapper(ObservableList<T> wrap) {
		theWrapped = wrap;
	}

	/** @return The list that this wrapper wraps */
	protected ObservableList<T> getWrapped() {
		return theWrapped;
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
	public Subscription onOrderedElement(java.util.function.Consumer<? super OrderedObservableElement<T>> observer) {
		return theWrapped.onOrderedElement(observer);
	}

	@Override
	public Subscription onElementReverse(Consumer<? super OrderedObservableElement<T>> onElement) {
		return theWrapped.onElementReverse(onElement);
	}

	@Override
	public boolean addAll(int index, Collection<? extends T> c) {
		return theWrapped.addAll(c);
	}

	@Override
	public T get(int index) {
		return theWrapped.get(index);
	}

	@Override
	public T set(int index, T element) {
		return theWrapped.set(index, element);
	}

	@Override
	public void add(int index, T element) {
		theWrapped.add(index, element);
	}

	@Override
	public T remove(int index) {
		return theWrapped.remove(index);
	}

	@Override
	public int indexOf(Object o) {
		return theWrapped.indexOf(o);
	}

	@Override
	public int lastIndexOf(Object o) {
		return theWrapped.lastIndexOf(o);
	}

	@Override
	public ListIterator<T> listIterator() {
		return theWrapped.listIterator();
	}

	@Override
	public ListIterator<T> listIterator(int index) {
		return theWrapped.listIterator(index);
	}

	@Override
	public ObservableList<T> subList(int fromIndex, int toIndex) {
		return theWrapped.subList(fromIndex, toIndex);
	}

	@Override
	public String toString() {
		return theWrapped.toString();
	}
}
