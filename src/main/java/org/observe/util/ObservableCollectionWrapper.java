package org.observe.util;

import java.util.Collection;
import java.util.Iterator;

import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableElement;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

/**
 * Wraps an observable set
 *
 * @param <E> The type of the set
 */
public class ObservableCollectionWrapper<E> implements ObservableCollection<E> {
	private final ObservableCollection<E> theWrapped;

	private final boolean isModifiable;

	/** @param wrap The collection to wrap */
	public ObservableCollectionWrapper(ObservableCollection<E> wrap) {
		this(wrap, true);
	}

	/**
	 * @param wrap The collection to wrap
	 * @param modifiable Whether this collection can propagate modifications to the wrapped collection. If false, this collection will be
	 *            immutable.
	 */
	public ObservableCollectionWrapper(ObservableCollection<E> wrap, boolean modifiable) {
		theWrapped = wrap;
		isModifiable = modifiable;
	}

	/** @return The collection that this wrapper wraps */
	protected ObservableCollection<E> getWrapped() {
		return theWrapped;
	}

	/** @return Whether this collection can be modified directly */
	protected boolean isModifiable() {
		return isModifiable;
	}

	@Override
	public TypeToken<E> getType() {
		return theWrapped.getType();
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
	public boolean isSafe() {
		return theWrapped.isSafe();
	}

	@Override
	public Subscription onElement(java.util.function.Consumer<? super ObservableElement<E>> observer) {
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
	public boolean containsAll(Collection<?> c) {
		return theWrapped.containsAll(c);
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
	public Iterator<E> iterator() {
		return new Iterator<E>() {
			private final Iterator<E> backing = getWrapped().iterator();

			@Override
			public boolean hasNext() {
				return backing.hasNext();
			}

			@Override
			public E next() {
				return backing.next();
			}

			@Override
			public void remove() {
				assertModifiable();
				backing.remove();
			}
		};
	}

	/** Throws an {@link UnsupportedOperationException} if this collection is not {@link #isModifiable() modifiable} */
	protected void assertModifiable() {
		if(!isModifiable)
			throw new UnsupportedOperationException("This collection is not modifiable");
	}

	@Override
	public boolean add(E e) {
		assertModifiable();
		return theWrapped.add(e);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		assertModifiable();
		return theWrapped.addAll(c);
	}

	@Override
	public boolean remove(Object o) {
		assertModifiable();
		return theWrapped.remove(o);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		assertModifiable();
		return theWrapped.removeAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		assertModifiable();
		return theWrapped.retainAll(c);
	}

	@Override
	public void clear() {
		assertModifiable();
		theWrapped.clear();
	}

	@Override
	public boolean canRemove(E value) {
		return isModifiable && theWrapped.canRemove(value);
	}

	@Override
	public boolean canAdd(E value) {
		return isModifiable && theWrapped.canAdd(value);
	}

	@Override
	public String toString() {
		return theWrapped.toString();
	}
}
