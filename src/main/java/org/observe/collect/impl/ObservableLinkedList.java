package org.observe.collect.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
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

/**
 * A list whose content can be observed. This list is a classic linked-type list with the following performance characteristics:
 * <ul>
 * <li><b>Access by index</b> Linear</li>
 * <li><b>Addition and removal</b> Constant at the beginning or the end, linear for the middle</li>
 * </ul>
 *
 * @param <E> The type of element in the list
 */
public class ObservableLinkedList<E> implements ObservableList.PartialListImpl<E> {
	private final Type theType;

	private LinkedListInternals theInternals;
	private ObservableValue<CollectionSession> theSessionObservable;
	private Transactable theSessionController;

	private LinkedNode theFirst;
	private LinkedNode theLast;

	private int theSize;

	LinkedNode theHighestIndexedFromFirst;
	LinkedNode theLowestIndexedFromLast;

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
		return theSize;
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
			addImpl(e, theLast);
		}
		return true;
	}

	private LinkedNode addImpl(E value, LinkedNode after) {
		LinkedNode newNode = createElement(value);
		newNode.added(after);
		return newNode;
	}

	@Override
	public void add(int index, E element) {
		try (Transaction t = theInternals.lock(true)) {
			LinkedNode after = index == 0 ? null : getNodeAt(index - 1);
			addImpl(element, after);
		}
	}

	private LinkedNode getNodeAt(int index) {
		if(index < 0 || index >= theSize)
			throw new IndexOutOfBoundsException(index + " of " + theSize);
		int i;
		LinkedNode node;
		Function<LinkedNode, LinkedNode> next;
		int delta;
		if(index <= theSize / 2) {
			i = 0;
			node = theFirst;
			next = LinkedNode::getNext;
			delta = 1;
		} else {
			i = theSize - 1;
			node = theLast;
			next = LinkedNode::getPrevious;
			delta = -1;
		}
		while(i != index) {
			node = next.apply(node);
			i += delta;
		}
		return node;
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		if(c.isEmpty())
			return false;
		try (Transaction t = lock(true, null)) {
			for(E value : c)
				addImpl(value, theLast);
		}
		return true;
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		if(c.isEmpty())
			return false;
		try (Transaction t = lock(true, null)) {
			LinkedNode after = theLast;
			for(E value : c)
				after = addImpl(value, after);
		}
		return true;
	}

	@Override
	public boolean remove(Object o) {
		try (Transaction t = theInternals.lock(true)) {
			LinkedNode node = theFirst;
			while(node != null && !Objects.equals(node.get(), o))
				node = node.getNext();
			if(node != null)
				node.remove();
			return node != null;
		}
	}

	@Override
	public E remove(int index) {
		try (Transaction t = theInternals.lock(true)) {
			LinkedNode node = getNodeAt(index);
			E ret = node.get();
			node.remove();
			return ret;
		}
	}

	@Override
	public boolean removeAll(Collection<?> coll) {
		boolean ret = false;
		try (Transaction t = lock(true, null)) {
			LinkedNode node = theFirst;
			while(node != null) {
				if(coll.contains(node.get())) {
					ret = true;
					node.remove();
				}
				node = node.getNext();
			}
		}
		return ret;
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
		try (Transaction t = lock(true, null)) {
			LinkedNode node = theLast;
			while(node != null) {
				node.remove();
				node = node.getPrevious();
			}
		}
	}

	@Override
	public E set(int index, E element) {
		try (Transaction t = theInternals.lock(true)) {
			LinkedNode node = getNodeAt(index);
			E ret = node.get();
			node.set(element);
			return ret;
		}
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		LinkedNode indexed;
		try (Transaction t = theInternals.lock(false)) {
			indexed = getNodeAt(index);
		}
		return new ListIterator<E>() {
			private LinkedNode theNext = indexed;

			private boolean isCursorBefore;

			private boolean hasRemoved = true;

			@Override
			public boolean hasNext() {
				if(theNext == null)
					return false;
				if(isCursorBefore && !hasRemoved)
					return true;
				else
					return theNext.getNext() != null;
			}

			@Override
			public E next() {
				if(!hasNext())
					throw new NoSuchElementException();
				if(!isCursorBefore || hasRemoved)
					theNext = theNext.getNext();
				E ret = theNext.get();
				isCursorBefore = false;
				hasRemoved = false;
				return ret;
			}

			@Override
			public boolean hasPrevious() {
				if(theNext == null)
					return false;
				if(!isCursorBefore && !hasRemoved)
					return true;
				else
					return theNext.getPrevious() != null;
			}

			@Override
			public E previous() {
				if(!hasPrevious())
					throw new NoSuchElementException();
				if(isCursorBefore || hasRemoved)
					theNext = theNext.getPrevious();
				E ret = theNext.get();
				isCursorBefore = true;
				hasRemoved = false;
				return ret;
			}

			@Override
			public int nextIndex() {
				int nextIndex = theNext.getIndex();
				if(!isCursorBefore)
					nextIndex++;
				return nextIndex;
			}

			@Override
			public int previousIndex() {
				int prevIndex = theNext.getIndex();
				if(isCursorBefore)
					prevIndex--;
				return prevIndex;
			}

			@Override
			public void remove() {
				if(hasRemoved)
					throw new IllegalStateException("remove() may only be called (once) after next() or previous()");
				hasRemoved = true;
				try (Transaction t = theInternals.lock(true)) {
					theNext.remove();
				}
			}

			@Override
			public void set(E e) {
				if(hasRemoved)
					throw new IllegalStateException("set() may only be called after next() or previous() and not after remove()");
				theNext.set(e);
			}

			@Override
			public void add(E e) {
				if(hasRemoved)
					throw new IllegalStateException("add() may only be called after next() or previous() and not after remove()");
				try (Transaction t = theInternals.lock(true)) {
					addImpl(e, theNext);
				}
			}
		};
	}

	private class LinkedNode extends InternalObservableElementImpl<E> {
		private LinkedNode thePrevious;
		private LinkedNode theNext;

		private int theIndexFromFirst;
		private int theIndexFromLast;

		private boolean isRemoved;

		public LinkedNode(E value) {
			super(ObservableLinkedList.this.getType(), value);
		}

		LinkedNode getPrevious() {
			return thePrevious;
		}

		LinkedNode getNext() {
			return theNext;
		}

		int getIndex() {
			if(isRemoved)
				return theIndexFromFirst;
			if(theIndexFromFirst < theHighestIndexedFromFirst.theIndexFromFirst || this == theHighestIndexedFromFirst)
				return theIndexFromFirst;
			else if(theIndexFromLast < theLowestIndexedFromLast.theIndexFromLast || this == theLowestIndexedFromLast)
				return theSize - theIndexFromLast - 1;
			// Don't know our index. Find it.
			return cacheIndex();
		}

		private int cacheIndex() {
			int lowDiff = theIndexFromFirst - theHighestIndexedFromFirst.theIndexFromFirst;
			int highDiff = theIndexFromLast - theLowestIndexedFromLast.theIndexFromLast;
			while(true) {
				// This loop *should* be safe, since we know we're an element in the collection and we're between the highest and lowest
				// indexed elements. If this assumption does not hold (due to implementation errors in this list), this loop will just
				// generate an NPE
				if(lowDiff < highDiff) {
					theHighestIndexedFromFirst.theNext.theIndexFromFirst = theHighestIndexedFromFirst.theIndexFromFirst + 1;
					theHighestIndexedFromFirst = theHighestIndexedFromFirst.theNext;
					if(this == theHighestIndexedFromFirst)
						return theIndexFromFirst;
					lowDiff++;
				} else {
					theLowestIndexedFromLast.thePrevious.theIndexFromLast = theLowestIndexedFromLast.theIndexFromLast + 1;
					theLowestIndexedFromLast = theLowestIndexedFromLast.thePrevious;
					if(this == theLowestIndexedFromLast)
						return theIndexFromLast;
					highDiff++;
				}
			}
		}

		void added(LinkedNode after) {
			thePrevious = after;
			if(theLast == after)
				theLast = this;
			if(after == null) {
				theNext = theFirst;
				theFirst = this;
			} else {
				theNext = after.getNext();
				after.theNext = this;
			}
			theSize++;
			theInternals.fireNewElement(this);

			// Maintain cached indexes where possible
			if(after != null) {
				// For starters, assume we know where after is in relation to first and last. Adjust indexes accordingly.
				theIndexFromFirst = after.theIndexFromFirst + 1;
				theIndexFromLast = after.theIndexFromLast;
				after.theIndexFromLast++;

				if(theHighestIndexedFromFirst == after)
					theHighestIndexedFromFirst = this;
				else if(theIndexFromFirst < theHighestIndexedFromFirst.theIndexFromFirst)
					theHighestIndexedFromFirst = this;

				if(after.theIndexFromLast < theLowestIndexedFromLast.theIndexFromLast)
					theLowestIndexedFromLast = after;
			} else {
				// Inserting at beginning
				theIndexFromFirst = 0;
				theIndexFromLast = theSize - 1;
				theHighestIndexedFromFirst = this;
				if(theLowestIndexedFromLast == theNext)
					theLowestIndexedFromLast = this;
			}
		}

		@Override
		void remove() {
			if(thePrevious != null)
				thePrevious.theNext = theNext;
			if(theNext != null)
				theNext.thePrevious = thePrevious;
			if(this == theFirst)
				theFirst = theNext;
			if(this == theLast)
				theLast = thePrevious;
			theSize--;
			theIndexFromFirst = theIndexFromLast = getIndex();
			isRemoved = true;

			// Maintain cached indexes where possible
			if(theIndexFromFirst <= theHighestIndexedFromFirst.theIndexFromFirst) {
				if(thePrevious != null)
					theHighestIndexedFromFirst = thePrevious;
				else
					theHighestIndexedFromFirst = theNext;
			}
			if(theIndexFromLast <= theLowestIndexedFromLast.theIndexFromLast) {
				if(theNext != null)
					theLowestIndexedFromLast = theNext;
				else
					theLowestIndexedFromLast = thePrevious;
			}

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
