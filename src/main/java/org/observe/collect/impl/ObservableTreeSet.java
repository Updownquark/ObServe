package org.observe.collect.impl;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableFastFindCollection;
import org.observe.collect.ObservableOrderedElement;
import org.observe.collect.ObservableSortedSet;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.tree.CountedRedBlackNode.DefaultNode;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeMap;

import com.google.common.reflect.TypeToken;

/**
 * TODO This class has not been tested
 *
 * A sorted set whose content can be observed
 *
 * @param <E> The type of element in the set
 */
public class ObservableTreeSet<E> implements ObservableSortedSet<E>, ObservableFastFindCollection<E> {
	/**
	 * @param <T> The type of key for the set
	 * @param compare The comparator for the set
	 * @return The CollectionCreator to create tree sets
	 */
	public static <T> org.observe.assoc.impl.CollectionCreator<T, ObservableTreeSet<T>> creator(Comparator<? super T> compare) {
		return (type, lock, session, controller) -> new ObservableTreeSet<>(type, lock, session, controller, compare);
	};

	private final TypeToken<E> theType;

	private TreeSetInternals theInternals;

	private final Comparator<? super E> theCompare;
	private DefaultTreeMap<E, InternalElement> theValues;

	private volatile int theModCount;

	/**
	 * Creates the set
	 *
	 * @param type The type of elements for this set
	 * @param compare The comparator to sort this set's elements. Use {@link Comparable}::{@link Comparable#compareTo(Object) compareTo} for
	 *            natural ordering.
	 */
	public ObservableTreeSet(TypeToken<E> type, Comparator<? super E> compare) {
		this(type, new ReentrantReadWriteLock(), null, null, compare);
	}

	/**
	 * This constructor is for specifying some of the internals of the collection.
	 *
	 * @param type The type of elements for this collection
	 * @param lock The lock for this collection to use
	 * @param session The session for this collection to use (see {@link #getSession()})
	 * @param sessionController The controller for the session. May be null, in which case the transactional methods in this collection will
	 *            not actually create transactions.
	 * @param compare The comparator to sort this set's elements. Use {@link Comparable}::{@link Comparable#compareTo(Object) compareTo} for
	 *            natural ordering.
	 */
	public ObservableTreeSet(TypeToken<E> type, ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session,
		Transactable sessionController, Comparator<? super E> compare) {
		theType = type.wrap();
		theInternals = new TreeSetInternals(lock, session, sessionController, write -> {
			if(write)
				theModCount++;
		});
		theCompare = compare;

		theValues = new DefaultTreeMap<>(theCompare);
	}

	/**
	 * This method is for creating a tree set of comparable element while specifying some of the internals of the collection. This method
	 * matches the signature for
	 * {@link org.observe.assoc.impl.CollectionCreator#create(TypeToken, ReentrantReadWriteLock, ObservableValue, Transactable)} for easy
	 * use with the assoc implementation constructors.
	 *
	 * @param type The type of elements for this collection
	 * @param lock The lock for this collection to use
	 * @param session The session for this collection to use (see {@link #getSession()})
	 * @param sessionController The controller for the session. May be null, in which case the transactional methods in this collection will
	 *        not actually create transactions.
	 * @return The new tree set
	 */
	public static <E extends Comparable<E>> ObservableTreeSet<E> of(TypeToken<E> type, ReentrantReadWriteLock lock,
		ObservableValue<CollectionSession> session, Transactable sessionController) {
		return new ObservableTreeSet<>(type, lock, session, sessionController, (o1, o2) -> o1.compareTo(o2));
	}

	/** Checks the internal structure of this set for debugging. TODO Remove this when it's solid. */
	public void checkValid() {
		DefaultNode<?> root = theValues.getRoot();
		if(root != null)
			root.checkValid();
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
	public Subscription onElement(Consumer<? super ObservableElement<E>> observer) {
		return theInternals.onElement(observer, true);
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

	private InternalElement createElement(E value) {
		theModCount++;
		return new InternalElement(theType, value);
	}

	@Override
	public int size() {
		return theValues.size();
	}

	@Override
	public boolean contains(Object o) {
		try (Transaction t = theInternals.lock(false, false, null)) {
			return theValues.containsKey(o);
		}
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		try (Transaction t = theInternals.lock(false, false, null)) {
			return theValues.keySet().containsAll(c);
		}
	}

	@Override
	public Iterator<E> iterator() {
		return new SetIterator(theValues.entrySet().iterator());
	}

	@Override
	public Iterable<E> descending() {
		return () -> new SetIterator(theValues.descendingMap().entrySet().iterator());
	}

	@Override
	public Iterable<E> iterateFrom(E start, boolean up, boolean withStart) {
		return () -> new Iterator<E>() {
			private final Iterator<Entry<E, InternalElement>> backing = theValues.entrySet().iterator(up, theValues.keyEntry(start),
				withStart, null, true);

			@Override
			public boolean hasNext() {
				return backing.hasNext();
			}

			@Override
			public E next() {
				return backing.next().getKey();
			}

			@Override
			public void remove() {
				try (Transaction t = theInternals.lock(true, false, null)) {
					backing.remove();
				}
			}
		};
	}

	@Override
	public boolean add(E e) {
		try (Transaction t = theInternals.lock(true, false, null)) {
			if(theValues.containsKey(e))
				return false;
			InternalElement el = createElement((E) theType.getRawType().cast(e));
			el.setNode(theValues.putGetNode(e, el));
			theInternals.fireNewElement(el);
			return true;
		}
	}

	@Override
	public boolean remove(Object o) {
		try (Transaction t = theInternals.lock(true, false, null)) {
			return removeNodeImpl(o);
		}
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		try (Transaction t = lock(true, null)) {
			boolean ret = false;
			for(Object o : c) {
				if(removeNodeImpl(o)) {
					ret = true;
					theModCount++;
				}
			}
			return ret;
		}
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		boolean ret = false;
		try (Transaction t = lock(true, null)) {
			for(E add : c) {
				if(theValues.containsKey(add))
					continue;
				ret = true;
				InternalElement el = createElement((E) theType.getRawType().cast(add));
				el.setNode(theValues.putGetNode(add, el));
				theModCount++;
				theInternals.fireNewElement(el);
			}
		}
		return ret;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		boolean ret = false;
		try (Transaction t = lock(true, null)) {
			Iterator<DefaultNode<Map.Entry<E, InternalElement>>> iter = theValues.nodeIterator(false, null, true, null, true);
			while(iter.hasNext()) {
				DefaultNode<Map.Entry<E, InternalElement>> node = iter.next();
				if(c.contains(node.getValue().getKey()))
					continue;
				removedNodeImpl(node, () -> iter.remove());
				ret = true;
				theModCount++;
			}
		}
		return ret;
	}

	@Override
	public void clear() {
		try (Transaction t = lock(true, null)) {
			Iterator<DefaultNode<Map.Entry<E, InternalElement>>> iter = theValues.nodeIterator(false, null, true, null, true);
			while(iter.hasNext()) {
				DefaultNode<Map.Entry<E, InternalElement>> node = iter.next();
				removedNodeImpl(node, null);
			}
			theValues.clear();
		}
	}

	@Override
	public E pollFirst() {
		try (Transaction t = lock(true, null)) {
			DefaultNode<Map.Entry<E, InternalElement>> node = theValues.getEndNode(true);
			if(node == null)
				return null;
			removedNodeImpl(node, () -> theValues.removeNode(node));
			return node.getValue().getKey();
		}
	}

	@Override
	public E pollLast() {
		try (Transaction t = lock(true, null)) {
			DefaultNode<Map.Entry<E, InternalElement>> node = theValues.getEndNode(false);
			if(node == null)
				return null;
			removedNodeImpl(node, () -> theValues.removeNode(node));
			return node.getValue().getKey();
		}
	}

	@Override
	public E get(int index) {
		return theValues.get(index).getKey();
	}

	@Override
	public int indexOf(Object o){
		return theValues.indexOfKey((E) o);
	}

	private boolean removeNodeImpl(Object o) {
		DefaultNode<Map.Entry<E, InternalElement>> node = theValues.getNode(o);
		if(node != null) {
			removedNodeImpl(node, () -> theValues.removeNode(node));
			return true;
		} else
			return false;
	}

	private void removedNodeImpl(DefaultNode<Map.Entry<E, InternalElement>> node, Runnable removeAction) {
		theModCount++;
		node.getValue().getValue().setRemovedIndex(node.getValue().getValue().getIndex());
		if(removeAction != null)
			removeAction.run();
		node.getValue().getValue().remove();
	}

	@Override
	public Comparator<? super E> comparator() {
		return theValues.comparator();
	}

	@Override
	public boolean canRemove(Object value) {
		return value == null || theType.getRawType().isInstance(value);
	}

	@Override
	public boolean canAdd(E value) {
		if (value != null && !theType.getRawType().isInstance(value))
			return false;
		try (Transaction t = lock(false, null)) {
			if (theValues.containsKey(value))
				return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return org.observe.collect.ObservableSet.toString(this);
	}

	private class SetIterator implements Iterator<E> {
		private final Iterator<Map.Entry<E, InternalElement>> backing;

		private InternalElement theLastElement;

		SetIterator(Iterator<Map.Entry<E, InternalElement>> back) {
			backing = back;
		}

		@Override
		public boolean hasNext() {
			try (Transaction t = theInternals.lock(false, false, null)) {
				return backing.hasNext();
			}
		}

		@Override
		public E next() {
			try (Transaction t = theInternals.lock(false, false, null)) {
				Map.Entry<E, InternalElement> entry = backing.next();
				theLastElement = entry.getValue();
				return entry.getKey();
			}
		}

		@Override
		public void remove() {
			try (Transaction t = theInternals.lock(true, false, null)) {
				backing.remove();
				theLastElement.remove();
				theLastElement = null;
			}
		}
	}

	private class InternalElement extends InternalOrderedObservableElementImpl<E> {
		private DefaultNode<Map.Entry<E, InternalElement>> theNode;

		InternalElement(TypeToken<E> type, E value) {
			super(type, value);
		}

		void setNode(DefaultNode<Map.Entry<E, InternalElement>> node) {
			theNode = node;
		}

		int getIndex() {
			int ret = getCachedIndex(theModCount);
			if(ret < 0)
				ret = cacheIndex(theNode);
			return ret;
		}

		private int cacheIndex(DefaultNode<Map.Entry<E, InternalElement>> node) {
			int ret = node.getValue().getValue().getCachedIndex(theModCount);
			if(ret < 0) {
				ret = 0;
				DefaultNode<Map.Entry<E, InternalElement>> left = (DefaultNode<Entry<E, InternalElement>>) node.getLeft();
				if(left != null)
					ret += left.getSize();
				DefaultNode<Map.Entry<E, InternalElement>> parent = (DefaultNode<Entry<E, InternalElement>>) node.getParent();
				DefaultNode<Map.Entry<E, InternalElement>> child = node;
				while(parent != null) {
					if(parent.getRight() == child) {
						ret += cacheIndex(parent) + 1;
						break;
					}
					child = parent;
					parent = (DefaultNode<Entry<E, InternalElement>>) parent.getParent();
				}
				node.getValue().getValue().cacheIndex(ret, theModCount);
			}
			return ret;
		}
	}

	private class TreeSetInternals extends DefaultCollectionInternals<E> {
		TreeSetInternals(ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session, Transactable sessionController,
			Consumer<? super Boolean> postAction) {
			super(lock, session, sessionController, null, postAction);
		}

		@Override
		Iterable<? extends InternalElement> getElements(boolean forward) {
			if(forward)
				return theValues.values();
			else
				return new Iterable<InternalElement>() {
				@Override
				public Iterator<InternalElement> iterator() {
					return theValues.descendingMap().values().iterator();
				}
			};
		}

		@Override
		ObservableElement<E> createExposedElement(InternalObservableElementImpl<E> internal, Collection<Subscription> subscriptions) {
			class ExposedOrderedObservableElement extends ExposedObservableElement<E> implements ObservableOrderedElement<E> {
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
					return getType() + " SortedSet[" + getIndex() + "]=" + get();
				}
			}
			return new ExposedOrderedObservableElement();
		}
	}
}
