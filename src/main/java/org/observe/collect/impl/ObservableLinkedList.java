package org.observe.collect.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableList;
import org.observe.collect.OrderedObservableElement;
import org.observe.util.DefaultTransactable;
import org.observe.util.Transactable;
import org.observe.util.Transaction;

import prisms.lang.Type;

public class ObservableLinkedList<E> implements ObservableList.PartialListImpl<E> {
	private final Type theType;

	private LinkedListInternals theInternals;

	private ObservableValue<CollectionSession> theSessionObservable;

	private Transactable theSessionController;

	private LinkedNode theFirst;

	private LinkedNode theLast;

	/**
	 * Creates the list
	 *
	 * @param type The type of elements for this list
	 */
	public ObservableLinkedList(Type type) {
		this(type, new ReentrantReadWriteLock(), null, null);

		theSessionController = new DefaultTransactable(theInternals.getLock());
		theSessionObservable = ((DefaultTransactable) theSessionController).getSession();
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
	public ObservableLinkedList(Type type, ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session,
		Transactable sessionController) {
		theType = type;
		theInternals = new LinkedListInternals(lock);
		theSessionObservable = session;
		theSessionController = sessionController;
	}

	@Override
	public ObservableValue<CollectionSession> getSession() {
		return theSessionObservable;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		if(theSessionController == null) {
			return () -> {
			};
		}
		return theSessionController.lock(write, cause);
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

	private LinkedNode createElement(E value) {
		return new LinkedNode(value);
	}

	@Override
	public int size() {
		LinkedNode last = theLast;
		return last == null ? 0 : last.getIndex();
	}

	@Override
	public E get(int index) {
		try (Transaction t = theInternals.lock(false)) {
			LinkedNode node = theFirst;
			for(int i = 0; i < index && node != null; i++)
				node = node.getNext();
			if(node == null)
				throw new IndexOutOfBoundsException();
			return node.get();
		}
	}

	@Override
	public boolean contains(Object o) {
		try (Transaction t = theInternals.lock(false)) {
			LinkedNode node = theFirst;
			while(node != null) {
				if(Objects.equals(node.get(), o))
					return true;
				node = node.getNext();
			}
		}
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> coll) {
		ArrayList<Object> copy = new ArrayList<>(coll);
		if(copy.isEmpty())
			return true;
		try (Transaction t = theInternals.lock(false)) {
			LinkedNode node = theFirst;
			while(node != null && !copy.isEmpty()) {
				copy.remove(node.get());
				node = node.getNext();
			}
		}
		return copy.isEmpty();
	}

	@Override
	public int indexOf(Object o) {
		try (Transaction t = theInternals.lock(false)) {
			int i = 0;
			LinkedNode node = theFirst;
			while(node != null) {
				if(Objects.equals(node.get(), o))
					return i;
				node = node.getNext();
				i++;
			}
		}
		return -1;
	}

	@Override
	public int lastIndexOf(Object o) {
		// TODO Auto-generated method stub
		return PartialListImpl.super.lastIndexOf(o);
	}

	@Override
	public Iterator<E> iterator() {
		return iterate(theFirst, LinkedNode::getNext);
	}

	@Override
	public Iterable<E> descending() {
		return () -> iterate(theLast, LinkedNode::getPrevious);
	}

	private Iterator<E> iterate(LinkedNode node, Function<LinkedNode, LinkedNode> next) {
		return new Iterator<E>() {
			private LinkedNode theNext;
			private LinkedNode thePassed;

			@Override
			public boolean hasNext() {
				return theNext != null;
			}

			@Override
			public E next() {
				thePassed = theNext;
				theNext = next.apply(theNext);
				return thePassed.get();
			}

			@Override
			public void remove() {
				thePassed.remove();
			}
		};
	}

	@Override
	public boolean add(E e) {
		try (Transaction t = theInternals.lock(true)) {
			LinkedNode newNode = createElement(e);
			newNode.setPrevious(theLast);
			theLast = newNode;
			theInternals.fireNewElement(newNode);
		}
		return true;
	}

	@Override
	public void add(int index, E element) {
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		// TODO Auto-generated method stub
		return PartialListImpl.super.addAll(c);
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		// TODO Auto-generated method stub
		return PartialListImpl.super.addAll(index, c);
	}

	@Override
	public boolean remove(Object o) {
		// TODO Auto-generated method stub
		return PartialListImpl.super.remove(o);
	}

	@Override
	public E remove(int index) {
		// TODO Auto-generated method stub
		return PartialListImpl.super.remove(index);
	}

	@Override
	public void removeRange(int fromIndex, int toIndex) {
		// TODO Auto-generated method stub
		PartialListImpl.super.removeRange(fromIndex, toIndex);
	}

	@Override
	public boolean removeAll(Collection<?> coll) {
		// TODO Auto-generated method stub
		return PartialListImpl.super.removeAll(coll);
	}

	@Override
	public boolean retainAll(Collection<?> coll) {
		boolean ret = false;
		try (Transaction t = lock(true, null)) {
			LinkedNode node = theFirst;
			while(node != null) {
				if(!coll.contains(node.get())) {
					ret = true;
					node.remove();
				}
				node = node.getNext();
			}
		}
		return ret;
	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub
		PartialListImpl.super.clear();
	}

	@Override
	public E set(int index, E element) {
		// TODO Auto-generated method stub
		return PartialListImpl.super.set(index, element);
	}

	private class LinkedNode extends InternalOrderedObservableElementImpl<E> {
		private LinkedNode thePrevious;
		private LinkedNode theNext;

		public LinkedNode(E value) {
			super(ObservableLinkedList.this.getType(), value);
		}

		LinkedNode getPrevious() {
			return thePrevious;
		}

		void setPrevious(LinkedNode previous) {
			thePrevious = previous;
			cacheIndex(thePrevious == null ? 0 : thePrevious.getCachedIndex(0), 0);
		}

		LinkedNode getNext() {
			return theNext;
		}

		void setNext(LinkedNode next) {
			theNext = next;
		}

		int getIndex() {
			// TODO
		}

		@Override
		void remove() {
			if(thePrevious != null)
				thePrevious.setNext(theNext);
			if(theNext != null)
				theNext.setPrevious(thePrevious);
			if(this == theFirst)
				theFirst = theNext;
			if(this == theLast)
				theLast = thePrevious;
			// TODO Set the removed index

			super.remove();
		}
	}

	private class LinkedListInternals extends DefaultCollectionInternals<E> {
		public LinkedListInternals(ReentrantReadWriteLock lock) {
			super(lock, null, null);
		}

		@Override
		Iterable<? extends InternalObservableElementImpl<E>> getElements(boolean forward) {
			return new Iterable<LinkedNode>() {
				@Override
				public Iterator<LinkedNode> iterator() {
					return new Iterator<LinkedNode>() {
						private LinkedNode next = forward ? theFirst : theLast;

						@Override
						public boolean hasNext() {
							return next != null;
						}

						@Override
						public LinkedNode next() {
							LinkedNode ret = next;
							next = forward ? next.getNext() : next.getPrevious();
							return ret;
						}
					};
				}
			};
		}

		@Override
		ObservableElement<E> createExposedElement(InternalObservableElementImpl<E> internal, Collection<Subscription> subscriptions) {
			LinkedNode orderedInternal = (LinkedNode) internal;
			class ExposedOrderedObservableElement extends ExposedObservableElement<E> implements OrderedObservableElement<E> {

				ExposedOrderedObservableElement() {
					super(internal, subscriptions);
				}

				@Override
				public int getIndex() {
					return orderedInternal.getIndex();
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
