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
import org.observe.collect.ObservableList.PartialListImpl;
import org.observe.collect.OrderedObservableElement;
import org.observe.util.DefaultTransactable;
import org.observe.util.Transactable;
import org.observe.util.Transaction;
import org.observe.util.tree.CountedRedBlackNode;
import org.observe.util.tree.CountedRedBlackNode.DefaultNode;
import org.observe.util.tree.RedBlackTreeList;

import prisms.lang.Type;

/**
 * A list whose content can be observed. This list is backed by a tree structure and has the following performance characteristics:
 * <ul>
 * <li><b>Access by index</b> logarithmic</li>
 * <li><b>Addition and removal</b> logarithmic</li>
 * </ul>
 *
 * @param <E> The type of element in the list
 */
public class ObservableTreeList<E> implements PartialListImpl<E> {
	private final Type theType;

	private TreeListInternals theInternals;
	private ObservableValue<CollectionSession> theSessionObservable;
	private Transactable theSessionController;

	private final RedBlackTreeList<CountedRedBlackNode<InternalElement>, InternalElement> theElements;

	private volatile int theModCount;

	/**
	 * Creates the list
	 *
	 * @param type The type of elements for this set
	 */
	public ObservableTreeList(Type type) {
		this(type, new ReentrantReadWriteLock(), null, null);

		theSessionController = new DefaultTransactable(theInternals.getLock());
		theSessionObservable = ((DefaultTransactable) theSessionController).getSession();
	}

	/**
	 * This constructor is for specifying some of the internals of the collection.
	 *
	 * @param type The type of elements for this collection
	 * @param lock The lock for this collection to use
	 * @param session The session for this collection to use (see {@link #getSession()})
	 * @param sessionController The controller for the session. May be null, in which case the transactional methods in this collection will
	 *            not actually create transactions.
	 */
	public ObservableTreeList(Type type, ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session,
		Transactable sessionController) {
		theType = type;
		theInternals = new TreeListInternals(lock, write -> {
			if(write)
				theModCount++;
		});
		theSessionObservable = session;
		theSessionController = sessionController;

		theElements = new RedBlackTreeList<>(element -> new DefaultNode<>(element, null));
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
	public Subscription onElement(Consumer<? super ObservableElement<E>> observer) {
		return theInternals.onElement(observer, true);
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

	private InternalElement createElement(E value) {
		return new InternalElement(theType, value);
	}

	@Override
	public int size() {
		return theElements.size();
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

	private InternalElement getNodeAt(int index) {
		if(index < 0 || index >= theElements.size())
			throw new IndexOutOfBoundsException(index + " of " + theElements.size());
		int i;
		InternalElement node;
		Function<InternalElement, InternalElement> next;
		int delta;
		int size = theElements.size();
		if(index <= size / 2) {
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
			Iterator<InternalElement> iter = theElements.iterator(false);
			while(iter.hasNext()) {
				InternalElement el = iter.next();
				iter.remove();
				el.remove();
			}
		}
	}

	@Override
	public E set(int index, E element) {
		try (Transaction t = theInternals.lock(true)) {
			InternalElement el = getNodeAt(index);
			E ret = el.get();
			el.set(element);
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

	private class InternalElement extends InternalOrderedObservableElementImpl<E> {
		private DefaultNode<InternalElement> theNode;

		InternalElement(Type type, E value) {
			super(type, value);
		}

		void setNode(DefaultNode<InternalElement> node) {
			theNode = node;
		}

		int getIndex() {
			int ret = getCachedIndex(theModCount);
			if(ret < 0)
				ret = cacheIndex(theNode);
			return ret;
		}

		private int cacheIndex(DefaultNode<InternalElement> node) {
			int ret = node.getValue().getCachedIndex(theModCount);
			if(ret < 0) {
				ret = 0;
				DefaultNode<InternalElement> left = (DefaultNode<InternalElement>) node.getLeft();
				if(left != null)
					ret += left.getSize();
				DefaultNode<InternalElement> parent = (DefaultNode<InternalElement>) node.getParent();
				DefaultNode<InternalElement> child = node;
				while(parent != null) {
					if(parent.getRight() == child) {
						ret += cacheIndex(parent) + 1;
						break;
					}
					child = parent;
					parent = (DefaultNode<InternalElement>) parent.getParent();
				}
				node.getValue().cacheIndex(ret, theModCount);
			}
			return ret;
		}
	}

	private class TreeListInternals extends DefaultCollectionInternals<E> {
		TreeListInternals(ReentrantReadWriteLock lock, Consumer<? super Boolean> postAction) {
			super(lock, null, postAction);
		}

		@Override
		Iterable<? extends InternalElement> getElements(boolean forward) {
			return () -> theElements.iterator(forward);
		}

		@Override
		ObservableElement<E> createExposedElement(InternalObservableElementImpl<E> internal, Collection<Subscription> subscriptions) {
			class ExposedOrderedObservableElement extends ExposedObservableElement<E> implements OrderedObservableElement<E> {
				ExposedOrderedObservableElement() {
					super(internal, subscriptions);
				}

				@Override
				protected InternalElement getInternalElement() {
					return (InternalElement) super.getInternalElement();
				}

				@Override
				public int getIndex() {
					return ((InternalElement) internal).getIndex();
				}

				@Override
				public String toString() {
					return getType() + " TreeList[" + getIndex() + "]=" + get();
				}
			}
			return new ExposedOrderedObservableElement();
		}
	}
}
