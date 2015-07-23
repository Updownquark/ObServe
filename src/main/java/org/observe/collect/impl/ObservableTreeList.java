package org.observe.collect.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableList.PartialListImpl;
import org.observe.collect.OrderedObservableElement;
import org.observe.util.Transactable;
import org.observe.util.Transaction;
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

	private final RedBlackTreeList<DefaultNode<InternalElement>, InternalElement> theElements;

	private volatile int theModCount;

	/**
	 * Creates the list
	 *
	 * @param type The type of elements for this set
	 */
	public ObservableTreeList(Type type) {
		this(type, new ReentrantReadWriteLock(), null, null);
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
		theInternals = new TreeListInternals(lock, session, sessionController, write -> {
			if(write)
				theModCount++;
		});

		theElements = new RedBlackTreeList<>(element -> new DefaultNode<>(element, null));
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
		try (Transaction t = theInternals.lock(false, false, null)) {
			return theElements.get(index).get();
		}
	}

	@Override
	public boolean contains(Object o) {
		try (Transaction t = theInternals.lock(false, false, null)) {
			for(InternalElement el : theElements) {
				if(Objects.equals(el.get(), o))
					return true;
			}
		}
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> coll) {
		ArrayList<Object> copy = new ArrayList<>(coll);
		if(copy.isEmpty())
			return true;
		try (Transaction t = theInternals.lock(false, false, null)) {
			for(InternalElement el : theElements)
				copy.remove(el.get());
		}
		return copy.isEmpty();
	}

	@Override
	public Iterator<E> iterator() {
		return iterate(true);
	}

	@Override
	public Iterable<E> descending() {
		return () -> iterate(false);
	}

	private Iterator<E> iterate(boolean forward) {
		return new Iterator<E>() {
			private Iterator<InternalElement> backing = theElements.iterator(forward);

			@Override
			public boolean hasNext() {
				return backing.hasNext();
			}

			@Override
			public E next() {
				return backing.next().get();
			}

			@Override
			public void remove() {
				backing.remove();
			}
		};
	}

	@Override
	public boolean add(E e) {
		try (Transaction t = theInternals.lock(true, false, null)) {
			addAfter(e, theElements.getLastNode());
		}
		return true;
	}

	private InternalElement addBefore(E value, DefaultNode<InternalElement> before) {
		InternalElement newNode = createElement(value);
		newNode.setNode(theElements.addBefore(newNode, before));
		theInternals.fireNewElement(newNode);
		return newNode;
	}

	private DefaultNode<InternalElement> addAfter(E value, DefaultNode<InternalElement> after) {
		InternalElement newNode = createElement(value);
		newNode.setNode(theElements.addAfter(newNode, after));
		theInternals.fireNewElement(newNode);
		return newNode.getNode();
	}

	@Override
	public void add(int index, E element) {
		try (Transaction t = theInternals.lock(true, false, null)) {
			InternalElement after = index == 0 ? null : theElements.get(index - 1);
			addAfter(element, after.getNode());
		}
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		if(c.isEmpty())
			return false;
		try (Transaction t = lock(true, null)) {
			for(E value : c)
				addAfter(value, theElements.getLastNode());
		}
		return true;
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		if(c.isEmpty())
			return false;
		try (Transaction t = lock(true, null)) {
			DefaultNode<InternalElement> after;
			if(index == 0)
				after = null;
			else
				after = theElements.getNodeAt(0);
			for(E value : c)
				after = addAfter(value, after);
		}
		return true;
	}

	@Override
	public E remove(int index) {
		try (Transaction t = theInternals.lock(true, false, null)) {
			InternalElement node = theElements.get(index);
			E ret = node.get();
			node.remove();
			return ret;
		}
	}

	@Override
	public E set(int index, E element) {
		try (Transaction t = theInternals.lock(true, false, null)) {
			InternalElement el = theElements.get(index);
			E ret = el.get();
			el.set(element);
			return ret;
		}
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		return new ListIterator<E>() {
			private ListIterator<InternalElement> backing = theElements.listIterator(index);

			private InternalElement theLast;

			private boolean lastWasNext;

			@Override
			public boolean hasNext() {
				return backing.hasNext();
			}

			@Override
			public E next() {
				theLast = backing.next();
				lastWasNext = true;
				return theLast.get();
			}

			@Override
			public boolean hasPrevious() {
				return backing.hasPrevious();
			}

			@Override
			public E previous() {
				theLast = backing.previous();
				lastWasNext = false;
				return theLast.get();
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
				try (Transaction t = theInternals.lock(true, false, null)) {
					backing.remove();
					theLast.remove();
					theLast = null;
				}
			}

			@Override
			public void set(E e) {
				theLast.set(e);
			}

			@Override
			public void add(E e) {
				try (Transaction t = theInternals.lock(true, false, null)) {
					if(lastWasNext)
						addBefore(e, theLast.getNode());
					else
						addAfter(e, theLast.getNode());
				}
			}
		};
	}

	private class InternalElement extends InternalOrderedObservableElementImpl<E> {
		private DefaultNode<InternalElement> theNode;

		InternalElement(Type type, E value) {
			super(type, value);
		}

		DefaultNode<InternalElement> getNode() {
			return theNode;
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
		TreeListInternals(ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session, Transactable sessionController,
			Consumer<? super Boolean> postAction) {
			super(lock, session, sessionController, null, postAction);
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
