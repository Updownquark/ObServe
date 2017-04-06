package org.observe.collect.impl;

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
import org.observe.collect.ObservableOrderedElement;
import org.qommons.RollingBuffer;
import org.qommons.Transactable;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

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
	/**
	 * <p>
	 * The number of actions against this list to be recorded too-small and too-large numbers may hurt performance. Currently this value is
	 * a guess at optimum.
	 * </p>
	 *
	 * <p>
	 * Too-small values will make it necessary to traverse the entire list frequently when the index of a particular observable element is
	 * requested.
	 * </p>
	 * <p>
	 * Too-large values will make computing the index expensive, even without traversal.
	 * </p>
	 */
	private static final int ACTION_CAPACITY = 10;

	private final TypeToken<E> theType;

	private LinkedListInternals theInternals;

	private LinkedNode theFirst;
	private LinkedNode theLast;

	private int theSize;

	private RollingBuffer<ListAction> theActions;

	/**
	 * Creates the list
	 *
	 * @param type The type of elements for this list
	 */
	public ObservableLinkedList(TypeToken<E> type) {
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
	public ObservableLinkedList(TypeToken<E> type, ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session,
		Transactable sessionController) {
		theType = type.wrap();
		theInternals = new LinkedListInternals(lock, session, sessionController);
		theActions = new RollingBuffer<>(ACTION_CAPACITY);
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
	public boolean isSafe() {
		return true;
	}

	@Override
	public TypeToken<E> getType() {
		return theType;
	}

	@Override
	public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
		// Cast is safe because the internals of this set will only create ordered elements
		return theInternals.onElement((Consumer<ObservableElement<E>>) onElement, true);
	}

	@Override
	public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<E>> onElement) {
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

	/** Internal validation method */
	public void validate() {
		if(theFirst != null && theFirst.thePrevious != null)
			throw new IllegalStateException("First's previous!=null");
		if(theLast != null && theLast.theNext != null)
			throw new IllegalStateException("Last's next!=null");
		if((theFirst != null) != (theLast != null))
			throw new IllegalStateException("First XOR Last !=null");
		LinkedNode node = theFirst;
		while(node != null && node.theNext != null) {
			if(node.theNext.thePrevious != node)
				throw new IllegalStateException("Bad links at " + node.getIndex() + "->" + (node.getIndex() + 1));
			node = node.theNext;
		}
	}

	@Override
	public E get(int index) {
		try (Transaction t = theInternals.lock(false, false, null)) {
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
		try (Transaction t = theInternals.lock(false, false, null)) {
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
	public Iterator<E> iterator() {
		return iterate(theFirst, LinkedNode::getNext);
	}

	@Override
	public Iterable<E> descending() {
		return () -> iterate(theLast, LinkedNode::getPrevious);
	}

	private Iterator<E> iterate(LinkedNode node, Function<LinkedNode, LinkedNode> next) {
		return new Iterator<E>() {
			private LinkedNode theNext = node;
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
		try (Transaction t = theInternals.lock(true, false, null)) {
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
		try (Transaction t = theInternals.lock(true, false, null)) {
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
			LinkedNode after = getNodeAt(index);
			after = after.getPrevious();
			for(E value : c)
				after = addImpl(value, after);
		}
		return true;
	}

	@Override
	public boolean remove(Object o) {
		try (Transaction t = theInternals.lock(true, false, null)) {
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
		try (Transaction t = theInternals.lock(true, false, null)) {
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
			theActions.clear();
		}
	}

	@Override
	public E set(int index, E element) {
		try (Transaction t = theInternals.lock(true, false, null)) {
			LinkedNode node = getNodeAt(index);
			E ret = node.get();
			node.set(element);
			return ret;
		}
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		LinkedNode indexed;
		try (Transaction t = theInternals.lock(false, false, null)) {
			indexed = getNodeAt(index);
		}
		return new ListIterator<E>() {
			private LinkedNode theAnchor = indexed;

			private boolean isCursorBefore = true;

			private boolean hasRemoved = false;

			@Override
			public boolean hasNext() {
				if(theAnchor == null)
					return false;
				if(isCursorBefore)
					return true;
				else
					return theAnchor.getNext() != null;
			}

			@Override
			public E next() {
				if(!hasNext())
					throw new NoSuchElementException();
				if(!isCursorBefore) {
					theAnchor = theAnchor.getNext();
					isCursorBefore = true;
				}

				E ret = theAnchor.get();
				isCursorBefore = false;
				hasRemoved = false;
				return ret;
			}

			@Override
			public boolean hasPrevious() {
				if(theAnchor == null)
					return false;
				if(!isCursorBefore && !hasRemoved)
					return true;
				else
					return theAnchor.getPrevious() != null;
			}

			@Override
			public E previous() {
				if(!hasPrevious())
					throw new NoSuchElementException();
				if(isCursorBefore) {
					theAnchor = theAnchor.getPrevious();
					isCursorBefore = false;
				}

				E ret = theAnchor.get();
				isCursorBefore = true;
				hasRemoved = false;
				return ret;
			}

			@Override
			public int nextIndex() {
				int nextIndex = theAnchor.getIndex();
				if(!isCursorBefore)
					nextIndex++;
				return nextIndex;
			}

			@Override
			public int previousIndex() {
				int prevIndex = theAnchor.getIndex();
				if(isCursorBefore)
					prevIndex--;
				return prevIndex;
			}

			@Override
			public void remove() {
				if(hasRemoved)
					throw new IllegalStateException("remove() may only be called (once) after next() or previous()");
				hasRemoved = true;
				try (Transaction t = theInternals.lock(true, false, null)) {
					theAnchor.remove();
					theAnchor = theAnchor.getNext();
					isCursorBefore = true;
				}
			}

			@Override
			public void set(E e) {
				if(hasRemoved)
					throw new IllegalStateException("set() may only be called after next() or previous() and not after remove()");
				theAnchor.set(e);
			}

			@Override
			public void add(E e) {
				if(hasRemoved)
					throw new IllegalStateException("add() may only be called after next() or previous() and not after remove()");
				try (Transaction t = theInternals.lock(true, false, null)) {
					if(!isCursorBefore)
						theAnchor = addImpl(e, theAnchor);
					else
						addImpl(e, theAnchor.getPrevious());
				}
			}
		};
	}

	@Override
	public boolean canRemove(Object value) {
		return value == null || theType.getRawType().isInstance(value);
	}

	@Override
	public boolean canAdd(E value) {
		return value == null || theType.getRawType().isInstance(value);
	}

	@Override
	public String toString() {
		return ObservableList.toString(this);
	}

	private class LinkedNode extends InternalObservableElementImpl<E> {
		private LinkedNode thePrevious;
		private LinkedNode theNext;

		private int theIndex;
		private int theModTracker;
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
				return theIndex;
			if(theModTracker == theActions.getTotal())
				return theIndex;
			if(theModTracker < theActions.getTotal() - theActions.getCapacity()) {
				// Can't get the index from the actions. Gotta expand outward along the list.
				LinkedNode pre = thePrevious;
				LinkedNode next = theNext;
				while(pre != null && next != null && pre.theModTracker < theActions.getTotal() - theActions.getCapacity()
					&& next.theModTracker < theActions.getTotal() - theActions.getCapacity()) {
					pre = pre.thePrevious;
					next = next.theNext;
				}
				int index;
				boolean forward;
				if(pre == null) {
					pre = theFirst;
					index = 0;
					forward = true;
				} else if(next == null) {
					next = theLast;
					index = theSize - 1;
					forward = false;
				} else if(pre.theModTracker >= theActions.getTotal() - theActions.getCapacity()) {
					index = adjustIndex(pre.theIndex, pre.theModTracker);
					forward = true;
				} else {
					index = adjustIndex(next.theIndex, next.theModTracker);
					forward = false;
				}

				if(forward) {
					while(pre != this) {
						pre.theIndex = index;
						pre.theModTracker = theActions.getTotal();
						pre = pre.theNext;
						index++;
					}
				} else {
					while(next != this) {
						next.theIndex = index;
						next.theModTracker = theActions.getTotal();
						next = next.thePrevious;
						index--;
					}
				}
				theIndex = index;
				theModTracker = theActions.getTotal();
			} else {
				theIndex = adjustIndex(theIndex, theModTracker);
				theModTracker = theActions.getTotal();
			}
			return theIndex;
		}

		int adjustIndex(int index, int mods) {
			if(mods == theActions.getTotal())
				return index;
			Iterator<ListAction> iter = theActions.iterator();
			int skip = theActions.size() - (theActions.getTotal() - mods);
			for(int i = 0; i < skip; i++)
				iter.next();
			while(iter.hasNext()) {
				ListAction action = iter.next();
				if(action.theIndex > index)
					continue;
				else if(action.isRemove)
					index--;
				else
					index++;
			}
			return index;
		}

		void added(LinkedNode after) {
			theIndex = after == null ? 0 : after.getIndex() + 1;
			theActions.add(new ListAction(theIndex, false));
			theModTracker = theActions.getTotal();

			thePrevious = after;
			if(theLast == after)
				theLast = this;
			if(after == null) {
				theNext = theFirst;
				theFirst = this;
			} else {
				theNext = after.getNext();
				if(theNext != null)
					theNext.thePrevious = this;
				after.theNext = this;
			}
			theSize++;

			theInternals.fireNewElement(this);
		}

		@Override
		void remove() {
			getIndex(); // Make sure we have the right index cached before we mark ourselves as removed
			isRemoved = true;
			theActions.add(new ListAction(theIndex, true));
			theModTracker = theActions.getTotal();

			if(thePrevious != null)
				thePrevious.theNext = theNext;
			if(theNext != null)
				theNext.thePrevious = thePrevious;
			if(this == theFirst)
				theFirst = theNext;
			if(this == theLast)
				theLast = thePrevious;
			theSize--;

			super.remove();
		}
	}

	private static class ListAction {
		final int theIndex;

		private boolean isRemove;

		ListAction(int index, boolean remove) {
			theIndex = index;
			isRemove = remove;
		}
	}

	private class LinkedListInternals extends DefaultCollectionInternals<E> {
		public LinkedListInternals(ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session, Transactable sessionController) {
			super(lock, session, sessionController, null);
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
			class ExposedOrderedObservableElement extends ExposedObservableElement<E> implements ObservableOrderedElement<E> {

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
