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
import org.observe.collect.ObservableSortedSet;
import org.observe.collect.OrderedObservableElement;
import org.observe.util.DefaultTransactable;
import org.observe.util.Transactable;
import org.observe.util.Transaction;
import org.observe.util.tree.CountedRedBlackNode.DefaultNode;
import org.observe.util.tree.CountedRedBlackNode.DefaultTreeMap;

import prisms.lang.Type;

/**
 * TODO This class has not been tested
 *
 * A sorted set whose content can be observed
 *
 * @param <E> The type of element in the set
 */
public class ObservableTreeSet<E> implements ObservableSortedSet<E>, ObservableFastFindCollection<E> {
	private final Type theType;

	private final Comparator<? super E> theCompare;

	private DefaultSortedSetInternals theInternals;

	private ObservableValue<CollectionSession> theSessionObservable;

	private Transactable theSessionController;

	private DefaultTreeMap<E, InternalElement> theValues;

	private volatile int theModCount;

	/**
	 * Creates the set
	 *
	 * @param type The type of elements for this set
	 * @param compare The comparator to sort this set's elements. Use {@link Comparable}::{@link Comparable#compareTo(Object) compareTo} for
	 *            natural ordering.
	 */
	public ObservableTreeSet(Type type, Comparator<? super E> compare) {
		this(type, new ReentrantReadWriteLock(), null, null, compare);

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
	 * @param compare The comparator to sort this set's elements. Use {@link Comparable}::{@link Comparable#compareTo(Object) compareTo} for
	 *            natural ordering.
	 */
	public ObservableTreeSet(Type type, ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session,
		Transactable sessionController, Comparator<? super E> compare) {
		theType = type;
		theInternals = new DefaultSortedSetInternals(lock, write -> {
			if(write)
				theModCount++;
		});
		theSessionObservable = session;
		theSessionController = sessionController;
		theCompare = compare;

		theValues = new DefaultTreeMap<>(theCompare);
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
		return theValues.size();
	}

	@Override
	public boolean contains(Object o) {
		try (Transaction t = theInternals.lock(false)) {
			return theValues.containsKey(o);
		}
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		try (Transaction t = theInternals.lock(false)) {
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
	public boolean add(E e) {
		try (Transaction t = theInternals.lock(true)) {
			if(theValues.containsKey(e))
				return false;
			InternalElement el = createElement(e);
			el.setNode(theValues.putGetNode(e, el));
			theInternals.fireNewElement(el);
			return true;
		}
	}

	@Override
	public boolean remove(Object o) {
		try (Transaction t = theInternals.lock(true)) {
			return removeNodeImpl(o);
		}
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		try (Transaction t = lock(true, null)) {
			try (Transaction trans = lock(true, null)) {
				boolean ret = false;
				for(Object o : c)
					ret |= removeNodeImpl(o);
				return ret;
			}
		}
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		boolean ret = false;
		try (Transaction t = lock(true, null)) {
			for(E add : c) {
				if(!theValues.containsKey(add))
					continue;
				ret = true;
				InternalElement el = createElement(add);
				el.setNode(theValues.putGetNode(add, el));
				theInternals.fireNewElement(el);
			}
		}
		return ret;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		boolean ret = false;
		try (Transaction t = lock(true, null)) {
			try (Transaction trans = lock(true, null)) {
				Iterator<DefaultNode<Map.Entry<E, InternalElement>>> iter = theValues.nodeIterator();
				while(iter.hasNext()) {
					DefaultNode<Map.Entry<E, InternalElement>> node = iter.next();
					if(c.contains(node.getValue().getKey()))
						continue;
					ret = true;
					removedNodeImpl(node);
					iter.remove();
				}
			}
		}
		return ret;
	}

	@Override
	public void clear() {
		try (Transaction t = lock(true, null)) {
			Iterator<DefaultNode<Map.Entry<E, InternalElement>>> iter = theValues.nodeIterator();
			while(iter.hasNext()) {
				DefaultNode<Map.Entry<E, InternalElement>> node = iter.next();
				removedNodeImpl(node);
			}
			theValues.clear();
		}
	}

	@Override
	public E pollFirst() {
		try (Transaction t = lock(true, null)) {
			Map.Entry<E, InternalElement> entry = theValues.pollFirstEntry();
			if(entry == null)
				return null;
			entry.getValue().remove();
			return entry.getKey();
		}
	}

	@Override
	public E pollLast() {
		try (Transaction t = lock(true, null)) {
			Map.Entry<E, InternalElement> entry = theValues.pollLastEntry();
			if(entry == null)
				return null;
			entry.getValue().remove();
			return entry.getKey();
		}
	}

	private boolean removeNodeImpl(Object o) {
		DefaultNode<Map.Entry<E, InternalElement>> node = theValues.getNode(o);
		if(node != null) {
			theValues.removeNode(node);
			removedNodeImpl(node);
			return true;
		} else
			return false;
	}

	private void removedNodeImpl(DefaultNode<Map.Entry<E, InternalElement>> node) {
		node.getValue().getValue().setRemovedIndex(node.getValue().getValue().getIndex());
		node.getValue().getValue().remove();
	}

	@Override
	public Comparator<? super E> comparator() {
		return theValues.comparator();
	}

	@Override
	protected ObservableTreeSet<E> clone() throws CloneNotSupportedException {
		ObservableTreeSet<E> ret = (ObservableTreeSet<E>) super.clone();
		ret.theValues = (DefaultTreeMap<E, InternalElement>) theValues.clone();
		for(Map.Entry<E, InternalElement> entry : theValues.entrySet())
			entry.setValue(ret.createElement(entry.getKey()));
		ret.theInternals = ret.new DefaultSortedSetInternals(new ReentrantReadWriteLock(), write -> {
			if(write)
				ret.theModCount++;
		});
		return ret;
	}

	private class SetIterator implements Iterator<E> {
		private final Iterator<Map.Entry<E, InternalElement>> backing;

		private InternalElement theLastElement;

		SetIterator(Iterator<Map.Entry<E, InternalElement>> back) {
			backing = back;
		}

		@Override
		public boolean hasNext() {
			try (Transaction t = theInternals.lock(false)) {
				return backing.hasNext();
			}
		}

		@Override
		public E next() {
			try (Transaction t = theInternals.lock(false)) {
				Map.Entry<E, InternalElement> entry = backing.next();
				theLastElement = entry.getValue();
				return entry.getKey();
			}
		}

		@Override
		public void remove() {
			try (Transaction t = theInternals.lock(true)) {
				backing.remove();
				theLastElement.remove();
				theLastElement = null;
			}
		}
	}

	private class InternalElement extends InternalOrderedObservableElementImpl<E> {
		private DefaultNode<Map.Entry<E, InternalElement>> theNode;

		InternalElement(Type type, E value) {
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

	private class DefaultSortedSetInternals extends DefaultCollectionInternals<E> {
		DefaultSortedSetInternals(ReentrantReadWriteLock lock, Consumer<? super Boolean> postAction) {
			super(lock, null, postAction);
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
					return getType() + " SortedSet[" + getIndex() + "]=" + get();
				}
			}
			return new ExposedOrderedObservableElement();
		}
	}
}
