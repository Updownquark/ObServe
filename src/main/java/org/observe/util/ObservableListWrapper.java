package org.observe.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.function.Consumer;

import org.observe.Subscription;
import org.observe.collect.ObservableList;
import org.observe.collect.ObservableOrderedElement;

/**
 * Wraps an observable list
 *
 * @param <E> The type of the list
 */
public class ObservableListWrapper<E> extends ObservableOrderedCollectionWrapper<E> implements ObservableList<E> {
	/** @param wrap The list to wrap */
	public ObservableListWrapper(ObservableList<E> wrap) {
		super(wrap, true);
	}

	/**
	 * @param wrap The list to wrap
	 * @param modifiable Whether this list can propagate modifications to the wrapped list. If false, this list will be immutable.
	 */
	public ObservableListWrapper(ObservableList<E> wrap, boolean modifiable) {
		super(wrap, modifiable);
	}

	/** @return The list that this wrapper wraps */
	@Override
	protected ObservableList<E> getWrapped() {
		return (ObservableList<E>) super.getWrapped();
	}

	@Override
	public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
		return getWrapped().onElementReverse(onElement);
	}

	@Override
	public Iterator<E> iterator() {
		return super.iterator();
	}

	@Override
	public E get(int index) {
		return getWrapped().get(index);
	}

	@Override
	public int indexOf(Object o) {
		return getWrapped().indexOf(o);
	}

	@Override
	public int lastIndexOf(Object o) {
		return getWrapped().lastIndexOf(o);
	}

	@Override
	public void add(int index, E element) {
		assertModifiable();
		getWrapped().add(index, element);
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		assertModifiable();
		return getWrapped().addAll(c);
	}

	@Override
	public E remove(int index) {
		assertModifiable();
		return getWrapped().remove(index);
	}

	@Override
	public E set(int index, E element) {
		assertModifiable();
		return getWrapped().set(index, element);
	}

	@Override
	public ListIterator<E> listIterator() {
		return listIterator(0);
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		return new ListIterator<E>() {
			private final ListIterator<E> backing = getWrapped().listIterator(index);

			@Override
			public boolean hasNext() {
				return backing.hasNext();
			}

			@Override
			public E next() {
				return backing.next();
			}

			@Override
			public boolean hasPrevious() {
				return backing.hasPrevious();
			}

			@Override
			public E previous() {
				return backing.previous();
			}

			@Override
			public int nextIndex() {
				return backing.nextIndex();
			}

			@Override
			public int previousIndex() {
				return backing.previousIndex();
			}

			@Override
			public void remove() {
				assertModifiable();
				backing.remove();
			}

			@Override
			public void set(E e) {
				assertModifiable();
				backing.set(e);
			}

			@Override
			public void add(E e) {
				assertModifiable();
				backing.add(e);
			}
		};
	}

	@Override
	public String toString() {
		return getWrapped().toString();
	}
}
