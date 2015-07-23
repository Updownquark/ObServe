package org.observe.collect.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableList;
import org.observe.collect.ObservableRandomAccessList;
import org.observe.collect.OrderedObservableElement;
import org.observe.util.Transactable;
import org.observe.util.Transaction;

import prisms.lang.Type;

/**
 * A list whose content can be observed. This list is a classic array-type list with the following performance characteristics:
 * <ul>
 * <li><b>Access by index</b> Constant</li>
 * <li><b>Addition and removal</b> Linear</li>
 * </ul>
 *
 * @param <E> The type of element in the list
 */
public class ObservableArrayList<E> implements ObservableRandomAccessList<E>, ObservableList.PartialListImpl<E> {
	private final Type theType;

	private ArrayListInternals theInternals;

	private ArrayList<InternalOrderedObservableElementImpl<E>> theElements;
	private ArrayList<E> theValues;

	private volatile int theModCount;

	/**
	 * Creates the list
	 *
	 * @param type The type of elements for this list
	 */
	public ObservableArrayList(Type type) {
		this(type, new ReentrantReadWriteLock(), null, null);
	}

	/**
	 * This constructor is for specifying some of the internals of the list.
	 *
	 * @param type The type of elements for this list
	 * @param lock The lock for this list to use
	 * @param session The session for this list to use (see {@link #getSession()})
	 * @param sessionController The controller for the session. May be null, in which case the transactional methods in this collection will
	 *            not actually create transactions.
	 */
	public ObservableArrayList(Type type, ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session,
		Transactable sessionController) {
		theType = type;
		theInternals = new ArrayListInternals(lock, session, sessionController, write -> {
			if(write)
				theModCount++;
		});

		theValues = new ArrayList<>();
		theElements = new ArrayList<>();
	}

	@Override
	public ObservableValue<CollectionSession> getSession() {
		return theInternals.getSession();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theInternals.lock(write, true, cause);
	}

	@Override
	public Type getType() {
		return theType;
	}

	@Override
	public Subscription onOrderedElement(Consumer<? super OrderedObservableElement<E>> onElement) {
		// Cast is safe because the internals of this set will only create ordered elements
		return theInternals.onElement((Consumer<ObservableElement<E>>) onElement, true);
	}

	@Override
	public Subscription onElementReverse(Consumer<? super OrderedObservableElement<E>> onElement) {
		// Cast is safe because the internals of this set will only create ordered elements
		return theInternals.onElement((Consumer<ObservableElement<E>>) onElement, false);
	}

	private InternalOrderedObservableElementImpl<E> createElement(E value) {
		return new InternalOrderedObservableElementImpl<>(theType, value);
	}

	@Override
	public E get(int index) {
		Object [] ret = new Object[1];
		try (Transaction t = theInternals.lock(false, false, null)) {
			ret[0] = theValues.get(index);
		}
		return (E) ret[0];
	}

	@Override
	public int size() {
		return theValues.size();
	}

	@Override
	public boolean contains(Object o) {
		try (Transaction t = theInternals.lock(false, false, null)) {
			return theValues.contains(o);
		}
	}

	@Override
	public int indexOf(Object o) {
		try (Transaction t = theInternals.lock(false, false, null)) {
			return theValues.indexOf(o);
		}
	}

	@Override
	public int lastIndexOf(Object o) {
		try (Transaction t = theInternals.lock(false, false, null)) {
			return theValues.lastIndexOf(o);
		}
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		try (Transaction t = theInternals.lock(false, false, null)) {
			return theValues.containsAll(c);
		}
	}

	@Override
	public boolean add(E e) {
		try (Transaction t = theInternals.lock(true, false, null)) {
			E val = (E) theType.cast(e);
			theValues.add(val);
			InternalOrderedObservableElementImpl<E> add = createElement(val);
			add.cacheIndex(theElements.size(), theModCount + 1); // +1 because the mod count will be incremented when the transaction ends
			theElements.add(add);
			theInternals.fireNewElement(add);
		}
		return true;
	}

	@Override
	public void add(int index, E element) {
		try (Transaction t = theInternals.lock(true, false, null)) {
			E val = (E) theType.cast(element);
			theValues.add(index, val);
			InternalOrderedObservableElementImpl<E> newWrapper = createElement(val);
			newWrapper.cacheIndex(index, theModCount + 1);
			theElements.add(index, newWrapper);
			theInternals.fireNewElement(newWrapper);
		}
	}

	@Override
	public boolean remove(Object o) {
		try (Transaction t = theInternals.lock(true, false, null)) {
			int idx = theValues.indexOf(o);
			if(idx < 0) {
				return false;
			}
			theValues.remove(idx);
			InternalOrderedObservableElementImpl<E> removed = theElements.remove(idx);
			removed.setRemovedIndex(idx);
			removed.remove();
			return true;
		}
	}

	@Override
	public E remove(int index) {
		try (Transaction t = theInternals.lock(true, false, null)) {
			E ret = theValues.remove(index);
			InternalOrderedObservableElementImpl<E> removed = theElements.remove(index);
			removed.setRemovedIndex(index);
			removed.remove();
			return ret;
		}
	}

	@Override
	public void removeRange(int fromIndex, int toIndex) {
		try (Transaction t = theInternals.lock(true, false, null)) {
			for(int i = toIndex - 1; i >= fromIndex; i--) {
				theValues.remove(i);
				InternalOrderedObservableElementImpl<E> removed = theElements.remove(i);
				removed.setRemovedIndex(i);
				removed.remove();
			}
		}
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		if(c.isEmpty())
			return false;
		try (Transaction t = lock(true, null)) {
			for(E e : c) {
				E val = (E) theType.cast(e);
				theValues.add(val);
				InternalOrderedObservableElementImpl<E> newWrapper = createElement(val);
				newWrapper.cacheIndex(theElements.size(), theModCount + 1);
				theElements.add(newWrapper);
				theInternals.fireNewElement(newWrapper);
			}
		}
		return true;
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		if(c.isEmpty())
			return false;
		try (Transaction t = lock(true, null)) {
			int idx = index;
			for(E e : c) {
				E val = (E) theType.cast(e);
				theValues.add(idx, val);
				InternalOrderedObservableElementImpl<E> newWrapper = createElement(val);
				newWrapper.cacheIndex(idx, theModCount + 1);
				theElements.add(idx, newWrapper);
				theInternals.fireNewElement(newWrapper);
				idx++;
			}
		}
		return true;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		if(c.isEmpty() || isEmpty())
			return false;
		boolean ret = false;
		try (Transaction t = lock(true, null)) {
			for(int i = theValues.size() - 1; i >= 0; i--) {
				if(c.contains(theValues.get(i))) {
					ret = true;
					theValues.remove(i);
					InternalOrderedObservableElementImpl<E> element = theElements.remove(i);
					element.setRemovedIndex(i);
					element.remove();
				}
			}
		}
		return ret;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		if(c.isEmpty()) {
			if(isEmpty())
				return false;
			clear();
			return true;
		}
		boolean ret = false;
		try (Transaction t = lock(true, null)) {
			for(int i = theValues.size() - 1; i >= 0; i--) {
				if(!c.contains(theValues.get(i))) {
					ret = true;
					theValues.remove(i);
					InternalOrderedObservableElementImpl<E> element = theElements.remove(i);
					element.setRemovedIndex(i);
					element.remove();
				}
			}
		}
		return ret;
	}

	@Override
	public void clear() {
		if(isEmpty())
			return;
		try (Transaction t = lock(true, null)) {
			theValues.clear();
			ArrayList<InternalOrderedObservableElementImpl<E>> remove = new ArrayList<>(theElements);
			theElements.clear();
			for(int i = remove.size() - 1; i >= 0; i--) {
				InternalOrderedObservableElementImpl<E> removed = remove.get(i);
				removed.setRemovedIndex(i);
				removed.remove();
			}
		}
	}

	@Override
	public E set(int index, E element) {
		try (Transaction t = theInternals.lock(true, false, null)) {
			E val = (E) theType.cast(element);
			E ret = theValues.set(index, val);
			theElements.get(index).set(val);
			return ret;
		}
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder("[");
		boolean first = true;
		try (Transaction t = theInternals.lock(false, false, null)) {
			for(E value : theValues) {
				if(!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
		}
		ret.append(']');
		return ret.toString();
	}

	private class ArrayListInternals extends DefaultCollectionInternals<E> {
		ArrayListInternals(ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session, Transactable sessionController,
			Consumer<? super Boolean> postAction) {
			super(lock, session, sessionController, null, postAction);
		}

		@Override
		Iterable<? extends InternalObservableElementImpl<E>> getElements(boolean forward) {
			if(forward)
				return theElements;
			else
				return new Iterable<InternalObservableElementImpl<E>>() {
				@Override
				public Iterator<InternalObservableElementImpl<E>> iterator() {
					return new Iterator<InternalObservableElementImpl<E>>() {
						private final ListIterator<InternalOrderedObservableElementImpl<E>> backing;

						{
							backing = theElements.listIterator(theElements.size());
						}

						@Override
						public boolean hasNext() {
							return backing.hasPrevious();
						}

						@Override
						public InternalObservableElementImpl<E> next() {
							return backing.previous();
						}
					};
				}
			};
		}

		private void cacheIndexes() {
			for(int i = 0; i < theElements.size(); i++)
				theElements.get(i).cacheIndex(i, theModCount);
		}

		@Override
		ObservableElement<E> createExposedElement(InternalObservableElementImpl<E> internal, Collection<Subscription> subscriptions) {
			InternalOrderedObservableElementImpl<E> orderedInternal = (InternalOrderedObservableElementImpl<E>) internal;
			class ExposedOrderedObservableElement extends ExposedObservableElement<E> implements OrderedObservableElement<E> {

				ExposedOrderedObservableElement() {
					super(internal, subscriptions);
				}

				@Override
				public int getIndex() {
					int index = orderedInternal.getCachedIndex(theModCount);
					if(index < 0) {
						cacheIndexes();
						index = orderedInternal.getCachedIndex(theModCount);
					}
					return index;
				}

				@Override
				public String toString() {
					return getType() + " list[" + getIndex() + "]=" + get();
				}
			}
			return new ExposedOrderedObservableElement();
		}
	}
}
