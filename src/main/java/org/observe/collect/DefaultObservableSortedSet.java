package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.util.DefaultTransactable;
import org.observe.util.Transactable;
import org.observe.util.Transaction;
import org.observe.util.tree.CountedRedBlackNode.DefaultNode;
import org.observe.util.tree.CountedRedBlackNode.DefaultTreeMap;
import org.observe.util.tree.RedBlackTreeSet.NodeSet;

import prisms.lang.Type;

/**
 * TODO This class has not been tested
 *
 * A sorted set whose content can be observed
 *
 * @param <E> The type of element in the set
 */
public class DefaultObservableSortedSet<E> implements ObservableSortedSet<E>, TransactableCollection<E> {
	private final Type theType;

	private final Comparator<? super E> theCompare;

	private AtomicBoolean hasIssuedController;

	private DefaultSortedSetInternals theInternals;

	private ObservableValue<CollectionSession> theSessionObservable;

	private Transactable theSessionController;

	private DefaultTreeMap<E, InternalElement> theValues;

	private volatile InternalElement theRemovedElement;

	private volatile int theRemovedElementIndex;

	private volatile int theModCount;

	/**
	 * Creates the set
	 *
	 * @param type The type of elements for this set
	 * @param compare The comparator to sort this set's elements. Use {@link Comparable}::{@link Comparable#compareTo(Object) compareTo} for
	 *            natural ordering.
	 */
	public DefaultObservableSortedSet(Type type, Comparator<? super E> compare) {
		this(type, new ReentrantReadWriteLock(), null, null, compare);

		theSessionController = new DefaultTransactable(theInternals.getLock().writeLock());
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
	public DefaultObservableSortedSet(Type type, ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session,
		Transactable sessionController, Comparator<? super E> compare) {
		theType = type;
		hasIssuedController = new AtomicBoolean(false);
		theInternals = new DefaultSortedSetInternals(lock, hasIssuedController, write -> {
			if(write) {
				theRemovedElement = null;
				theRemovedElementIndex = -1;
				theModCount++;
			}
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
	public Transaction startTransaction(Object cause) {
		if(hasIssuedController.get())
			throw new IllegalStateException("Controlled default observable collections cannot be modified directly");
		return startTransactionImpl(cause);
	}

	private Transaction startTransactionImpl(Object cause) {
		if(theSessionController == null) {
			return () -> {
			};
		}
		return theSessionController.startTransaction(cause);
	}

	@Override
	public Type getType() {
		return theType;
	}

	/**
	 * Obtains the controller for this set. Once this is called, the observable set cannot be modified directly, but only through the
	 * controller. Modification methods to this set after this call will throw an {@link IllegalStateException}. Only one call can be made
	 * to this method. All calls after the first will throw an {@link IllegalStateException}.
	 *
	 * @param onSubscribe The listener to be notified when new subscriptions to this collection are made
	 * @return The list to control this list's data.
	 */
	public TransactableSortedSet<E> control(Consumer<? super Consumer<? super ObservableElement<E>>> onSubscribe) {
		if(hasIssuedController.getAndSet(true))
			throw new IllegalStateException("This observable set is already controlled");
		theInternals.setOnSubscribe(onSubscribe);
		return new ObservableSortedSetController();
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
		boolean [] ret = new boolean[1];
		theInternals.doLocked(() -> {
			ret[0] = theValues.containsKey(o);
		}, false, false);
		return ret[0];
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		boolean [] ret = new boolean[1];
		theInternals.doLocked(() -> {
			ret[0] = theValues.keySet().containsAll(c);
		}, false, false);
		return ret[0];
	}

	@Override
	public E [] toArray() {
		Object [][] ret = new Object[1][];
		theInternals.doLocked(() -> {
			Class<?> base = getType().toClass();
			if(base.isPrimitive())
				base = Type.getWrapperType(base);
			ret[0] = theValues.keySet().toArray((E []) java.lang.reflect.Array.newInstance(base, theValues.size()));
		}, false, false);
		return (E []) ret[0];
	}

	@Override
	public <T> T [] toArray(T [] a) {
		Object [][] ret = new Object[1][];
		theInternals.doLocked(() -> {
			ret[0] = theValues.keySet().toArray(a);
		}, false, false);
		return (T []) ret[0];
	}

	@Override
	public Iterator<E> iterator() {
		return new SetIterator(theValues.entrySet().iterator(), false);
	}

	@Override
	public Iterable<E> descending() {
		return () -> new SetIterator(theValues.descendingMap().entrySet().iterator(), false);
	}

	@Override
	public boolean add(E e) {
		boolean [] ret = new boolean[1];
		theInternals.doLocked(() -> {
			ret[0] = addImpl(e);
		}, true, true);
		return ret[0];
	}

	@Override
	public boolean remove(Object o) {
		boolean [] ret = new boolean[1];
		theInternals.doLocked(() -> {
			ret[0] = removeImpl(o);
		}, true, true);
		return ret[0];
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		boolean [] ret = new boolean[1];
		theInternals.doLocked(() -> {
			ret[0] = removeAllImpl(c);
		}, true, true);
		return ret[0];
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		boolean [] ret = new boolean[1];
		theInternals.doLocked(() -> {
			ret[0] = addAllImpl(c);
		}, true, true);
		return ret[0];
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		boolean [] ret = new boolean[1];
		theInternals.doLocked(() -> {
			ret[0] = retainAllImpl(c);
		}, true, true);
		return ret[0];
	}

	@Override
	public void clear() {
		theInternals.doLocked(() -> {
			clearImpl();
		}, true, true);
	}

	@Override
	public E pollFirst() {
		Object [] ret = new Object[1];
		theInternals.doLocked(() -> {
			ret[0] = pollFirstImpl();
		}, true, true);
		return (E) ret[0];
	}

	@Override
	public E pollLast() {
		Object [] ret = new Object[1];
		theInternals.doLocked(() -> {
			ret[0] = pollLastImpl();
		}, true, true);
		return (E) ret[0];
	}

	private boolean addImpl(E e) {
		if(theValues.containsKey(e))
			return false;
		InternalElement el = createElement(e);
		el.setNode(theValues.putGetNode(e, el));
		theInternals.fireNewElement(el);
		return true;
	}

	private boolean removeImpl(Object o) {
		return removeNodeImpl(o);
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
		theRemovedElementIndex = node.getIndex();
		theRemovedElement = node.getValue().getValue();
		theRemovedElement.remove();
	}

	private boolean addAllImpl(Collection<? extends E> c) {
		boolean ret = false;
		for(E add : c) {
			if(!theValues.containsKey(add))
				continue;
			ret = true;
			InternalElement el = createElement(add);
			el.setNode(theValues.putGetNode(add, el));
			theInternals.fireNewElement(el);
		}
		return ret;
	}

	private boolean removeAllImpl(Collection<?> c) {
		try (Transaction trans = startTransactionImpl(null)) {
			boolean ret = false;
			for(Object o : c) {
				ret |= removeNodeImpl(o);
			}
			return ret;
		}
	}

	private boolean retainAllImpl(Collection<?> c) {
		boolean ret = false;
		Iterator<DefaultNode<Map.Entry<E, InternalElement>>> iter = theValues.nodeIterator();
		while(iter.hasNext()) {
			DefaultNode<Map.Entry<E, InternalElement>> node = iter.next();
			if(c.contains(node.getValue().getKey()))
				continue;
			ret = true;
			theRemovedElementIndex = node.getIndex();
			theRemovedElement = node.getValue().getValue();
			theRemovedElement.remove();
			iter.remove();
		}
		return ret;
	}

	private void clearImpl() {
		try (Transaction trans = startTransactionImpl(null)) {
			theValues.clear();
			ArrayList<InternalElement> remove = new ArrayList<>();
			remove.addAll(theValues.values());
			theValues.clear();
			for(int i = remove.size() - 1; i >= 0; i--) {
				theRemovedElement = remove.get(i);
				theRemovedElementIndex = i;
				theRemovedElement.remove();
			}
		}
	}

	private E pollFirstImpl() {
		Map.Entry<E, InternalElement> entry = theValues.pollFirstEntry();
		if(entry == null)
			return null;
		entry.getValue().remove();
		return entry.getKey();
	}

	private E pollLastImpl() {
		Map.Entry<E, InternalElement> entry = theValues.pollLastEntry();
		if(entry == null)
			return null;
		entry.getValue().remove();
		return entry.getKey();
	}

	@Override
	public Comparator<? super E> comparator() {
		return theValues.comparator();
	}

	@Override
	protected DefaultObservableSortedSet<E> clone() throws CloneNotSupportedException {
		DefaultObservableSortedSet<E> ret = (DefaultObservableSortedSet<E>) super.clone();
		ret.theValues = (DefaultTreeMap<E, InternalElement>) theValues.clone();
		for(Map.Entry<E, InternalElement> entry : theValues.entrySet())
			entry.setValue(ret.createElement(entry.getKey()));
		ret.hasIssuedController = new AtomicBoolean(false);
		ret.theInternals = ret.new DefaultSortedSetInternals(new ReentrantReadWriteLock(), ret.hasIssuedController, write -> {
			if(write) {
				theRemovedElement = null;
				theRemovedElementIndex = -1;
			}
		});
		return ret;
	}

	private class SetIterator implements Iterator<E> {
		private final Iterator<Map.Entry<E, InternalElement>> backing;

		private final boolean isController;

		private InternalElement theLastElement;

		SetIterator(Iterator<Map.Entry<E, InternalElement>> back, boolean controller) {
			backing = back;
			isController = controller;
		}

		@Override
		public boolean hasNext() {
			boolean [] ret = new boolean[1];
			theInternals.doLocked(() -> {
				ret[0] = backing.hasNext();
			}, false, false);
			return ret[0];
		}

		@Override
		public E next() {
			Object [] ret = new Object[1];
			theInternals.doLocked(() -> {
				Map.Entry<E, InternalElement> entry = backing.next();
				ret[0] = entry.getKey();
				theLastElement = entry.getValue();
			}, false, false);
			return (E) ret[0];
		}

		@Override
		public void remove() {
			theInternals.doLocked(() -> {
				backing.remove();
				theLastElement.remove();
				theLastElement = null;
			}, true, !isController);
		}
	}

	private class ControllerSubSet implements NavigableSet<E> {
		private final NodeSet<Map.Entry<E, InternalElement>, DefaultNode<Map.Entry<E, InternalElement>>> backing;

		ControllerSubSet(NodeSet<Map.Entry<E, InternalElement>, DefaultNode<Map.Entry<E, InternalElement>>> back) {
			backing = back;
		}

		@Override
		public Comparator<? super E> comparator() {
			return theCompare;
		}

		@Override
		public E first() {
			Object [] ret = new Object[1];
			theInternals.doLocked(() -> {
				DefaultNode<Entry<E, InternalElement>> entry = backing.first();
				if(entry == null)
					throw new java.util.NoSuchElementException();
				ret[0] = entry.getValue().getKey();
			}, false, false);
			return (E) ret[0];
		}

		@Override
		public E last() {
			Object [] ret = new Object[1];
			theInternals.doLocked(() -> {
				DefaultNode<Entry<E, InternalElement>> entry = backing.last();
				if(entry == null)
					throw new java.util.NoSuchElementException();
				ret[0] = entry.getValue().getKey();
			}, false, false);
			return (E) ret[0];
		}

		@Override
		public E lower(E e) {
			Object [] ret = new Object[1];
			theInternals.doLocked(() -> {
				DefaultNode<Entry<E, InternalElement>> entry = backing.lower(theValues.keyEntry(e));
				if(entry == null)
					throw new java.util.NoSuchElementException();
				ret[0] = entry.getValue().getKey();
			}, false, false);
			return (E) ret[0];
		}

		@Override
		public E floor(E e) {
			Object [] ret = new Object[1];
			theInternals.doLocked(() -> {
				DefaultNode<Entry<E, InternalElement>> entry = backing.floor(theValues.keyEntry(e));
				if(entry == null)
					throw new java.util.NoSuchElementException();
				ret[0] = entry.getValue().getKey();
			}, false, false);
			return (E) ret[0];
		}

		@Override
		public E ceiling(E e) {
			Object [] ret = new Object[1];
			theInternals.doLocked(() -> {
				DefaultNode<Entry<E, InternalElement>> entry = backing.ceiling(theValues.keyEntry(e));
				if(entry == null)
					throw new java.util.NoSuchElementException();
				ret[0] = entry.getValue().getKey();
			}, false, false);
			return (E) ret[0];
		}

		@Override
		public E higher(E e) {
			Object [] ret = new Object[1];
			theInternals.doLocked(() -> {
				DefaultNode<Entry<E, InternalElement>> entry = backing.higher(theValues.keyEntry(e));
				if(entry == null)
					throw new java.util.NoSuchElementException();
				ret[0] = entry.getValue().getKey();
			}, false, false);
			return (E) ret[0];
		}

		@Override
		public int size() {
			int [] ret = new int[1];
			theInternals.doLocked(() -> {
				ret[0] = backing.size();
			}, false, false);
			return ret[0];
		}

		@Override
		public boolean isEmpty() {
			return size() == 0;
		}

		@Override
		public boolean contains(Object o) {
			boolean [] ret = new boolean[1];
			theInternals.doLocked(() -> {
				ret[0] = backing.contains(theValues.keyEntry((E) o));
			}, false, false);
			return ret[0];
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			boolean [] ret = new boolean[] {true};
			theInternals.doLocked(() -> {
				for(Object o : c) {
					if(!backing.contains(theValues.keyEntry((E) o)))
						ret[0] = false;
				}
			}, false, false);
			return ret[0];
		}

		@Override
		public Object [] toArray() {
			Object [][] ret = new Object[1][];
			theInternals.doLocked(() -> {
				ret[0] = backing.toArray();
				for(int i = 0; i < ret[0].length; i++)
					ret[0][i] = ((DefaultNode<Entry<E, InternalElement>>) ret[0][i]).getValue().getKey();
			}, false, false);
			return ret[0];
		}

		@Override
		public <T> T [] toArray(T [] a) {
			Object [][] ret = new Object[][] {a};
			theInternals.doLocked(() -> {
				Object [] backed = backing.toArray();
				if(backed.length <= a.length)
					ret[0] = a;
				else
					ret[0] = (T []) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), backed.length);
				for(int i = 0; i < backed.length; i++)
					ret[0][i] = ((DefaultNode<Entry<E, InternalElement>>) ret[0][i]).getValue().getKey();
			}, false, false);
			return (T []) ret[0];
		}

		@Override
		public Iterator<E> iterator() {
			return wrap(backing.iterator());
		}

		@Override
		public Iterator<E> descendingIterator() {
			return wrap(backing.descendingIterator());
		}

		private Iterator<E> wrap(Iterator<DefaultNode<Entry<E, InternalElement>>> entryIter) {
			return new Iterator<E>() {
				private DefaultNode<Entry<E, InternalElement>> theLast;

				@Override
				public boolean hasNext() {
					return entryIter.hasNext();
				}

				@Override
				public E next() {
					theLast = entryIter.next();
					return theLast.getValue().getKey();
				}

				@Override
				public void remove() {
					theInternals.doLocked(() -> {
						entryIter.remove();
						removedNodeImpl(theLast);
						theLast = null;
					}, true, false);
				}
			};
		}

		@Override
		public E pollFirst() {
			Object [] ret = new Object[1];
			theInternals.doLocked(() -> {
				DefaultNode<Entry<E, InternalElement>> node = backing.pollFirst();
				ret[0] = node.getValue().getKey();
				removedNodeImpl(node);
			}, true, false);
			return (E) ret[0];
		}

		@Override
		public E pollLast() {
			Object [] ret = new Object[1];
			theInternals.doLocked(() -> {
				DefaultNode<Entry<E, InternalElement>> node = backing.pollLast();
				ret[0] = node.getValue().getKey();
				removedNodeImpl(node);
			}, true, false);
			return (E) ret[0];
		}

		@Override
		public boolean add(E e) {
			backing.checkRange(theValues.keyEntry(e), true, true);
			boolean [] ret = new boolean[1];
			theInternals.doLocked(() -> {
				ret[0] = addImpl(e);
			}, true, false);
			return ret[0];
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			for(E e : c)
				backing.checkRange(theValues.keyEntry(e), true, true);
			boolean [] ret = new boolean[1];
			theInternals.doLocked(() -> {
				ret[0] = addAllImpl(c);
			}, true, false);
			return ret[0];
		}

		@Override
		public boolean remove(Object o) {
			if(!backing.checkRange(theValues.keyEntry((E) o)))
				return false;
			boolean [] ret = new boolean[1];
			theInternals.doLocked(() -> {
				ret[0] = removeImpl(o);
			}, true, false);
			return ret[0];
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			ArrayList<Object> copy = new ArrayList<>(c);
			Iterator<Object> iter = copy.iterator();
			while(iter.hasNext())
				if(!backing.checkRange(theValues.keyEntry((E) iter.next())))
					iter.remove();
			boolean [] ret = new boolean[1];
			theInternals.doLocked(() -> {
				ret[0] = removeAllImpl(copy);
			}, true, false);
			return ret[0];
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean [] ret = new boolean[1];
			theInternals.doLocked(() -> {
				Iterator<DefaultNode<Entry<E, InternalElement>>> entryIter = backing.iterator();
				while(entryIter.hasNext()) {
					DefaultNode<Entry<E, InternalElement>> node = entryIter.next();
					if(!c.contains(node.getValue().getKey())) {
						ret[0] = true;
						entryIter.remove();
						removedNodeImpl(node);
					}
				}
			}, true, false);
			return ret[0];
		}

		@Override
		public void clear() {
			theInternals.doLocked(() -> {
				Iterator<DefaultNode<Entry<E, InternalElement>>> entryIter = backing.iterator();
				while(entryIter.hasNext()) {
					removedNodeImpl(entryIter.next());
					entryIter.remove();
				}
			}, true, false);
		}

		public NavigableSet<E> getSubSet(boolean reversed, E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			return new ControllerSubSet(backing.getSubSet(reversed, theValues.keyEntry(fromElement), fromInclusive,
				theValues.keyEntry(toElement), toInclusive));
		}

		@Override
		public NavigableSet<E> descendingSet() {
			return getSubSet(true, null, true, null, true);
		}

		@Override
		public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			return getSubSet(false, fromElement, fromInclusive, toElement, toInclusive);
		}

		@Override
		public NavigableSet<E> headSet(E toElement, boolean inclusive) {
			return getSubSet(false, null, true, toElement, inclusive);
		}

		@Override
		public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
			return getSubSet(false, fromElement, inclusive, null, true);
		}

		@Override
		public SortedSet<E> subSet(E fromElement, E toElement) {
			return getSubSet(false, fromElement, true, toElement, false);
		}

		@Override
		public SortedSet<E> headSet(E toElement) {
			return getSubSet(false, null, true, toElement, false);
		}

		@Override
		public SortedSet<E> tailSet(E fromElement) {
			return getSubSet(false, fromElement, true, null, true);
		}
	}

	private class ObservableSortedSetController implements TransactableSortedSet<E> {
		@Override
		public Transaction startTransaction(Object cause) {
			return startTransactionImpl(cause);
		}

		@Override
		public Iterator<E> iterator() {
			return new SetIterator(theValues.entrySet().iterator(), true);
		}

		@Override
		public boolean isEmpty() {
			return DefaultObservableSortedSet.this.isEmpty();
		}

		@Override
		public int size() {
			return DefaultObservableSortedSet.this.size();
		}

		@Override
		public Comparator<? super E> comparator() {
			return DefaultObservableSortedSet.this.comparator();
		}

		@Override
		public E first() {
			return DefaultObservableSortedSet.this.first();
		}

		@Override
		public E last() {
			return DefaultObservableSortedSet.this.last();
		}

		@Override
		public E lower(E e) {
			return DefaultObservableSortedSet.this.lower(e);
		}

		@Override
		public E floor(E e) {
			return DefaultObservableSortedSet.this.floor(e);
		}

		@Override
		public E ceiling(E e) {
			return DefaultObservableSortedSet.this.ceiling(e);
		}

		@Override
		public E higher(E e) {
			return DefaultObservableSortedSet.this.higher(e);
		}

		@Override
		public E pollFirst() {
			return DefaultObservableSortedSet.this.pollFirst();
		}

		@Override
		public E pollLast() {
			return DefaultObservableSortedSet.this.pollLast();
		}

		@Override
		public boolean contains(Object o) {
			return DefaultObservableSortedSet.this.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return DefaultObservableSortedSet.this.containsAll(c);
		}

		@Override
		public E [] toArray() {
			return DefaultObservableSortedSet.this.toArray();
		}

		@Override
		public <T> T [] toArray(T [] a) {
			return DefaultObservableSortedSet.this.toArray(a);
		}

		@Override
		public Iterator<E> descendingIterator() {
			return new SetIterator(theValues.entrySet().descendingIterator(), true);
		}

		public NavigableSet<E> getSubSet(boolean reversed, E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			return new ControllerSubSet(theValues.entrySet().nodes(reversed, theValues.keyEntry(fromElement), fromInclusive,
				theValues.keyEntry(toElement), toInclusive));
		}

		@Override
		public NavigableSet<E> descendingSet() {
			return getSubSet(true, null, true, null, true);
		}

		@Override
		public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			return getSubSet(false, fromElement, fromInclusive, toElement, toInclusive);
		}

		@Override
		public NavigableSet<E> headSet(E toElement, boolean inclusive) {
			return getSubSet(false, null, true, toElement, inclusive);
		}

		@Override
		public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
			return getSubSet(false, fromElement, inclusive, null, true);
		}

		@Override
		public SortedSet<E> subSet(E fromElement, E toElement) {
			return getSubSet(false, fromElement, true, toElement, false);
		}

		@Override
		public SortedSet<E> headSet(E toElement) {
			return getSubSet(false, null, true, toElement, false);
		}

		@Override
		public SortedSet<E> tailSet(E fromElement) {
			return getSubSet(false, fromElement, true, null, true);
		}

		@Override
		public boolean add(E e) {
			boolean [] ret = new boolean[1];
			theInternals.doLocked(() -> {
				ret[0] = addImpl(e);
			}, true, false);
			return ret[0];
		}

		@Override
		public boolean remove(Object o) {
			boolean [] ret = new boolean[1];
			theInternals.doLocked(() -> {
				ret[0] = removeImpl(o);
			}, true, false);
			return ret[0];
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean [] ret = new boolean[1];
			theInternals.doLocked(() -> {
				ret[0] = removeAllImpl(c);
			}, true, false);
			return ret[0];
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			boolean [] ret = new boolean[1];
			theInternals.doLocked(() -> {
				ret[0] = addAllImpl(c);
			}, true, false);
			return ret[0];
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean [] ret = new boolean[1];
			theInternals.doLocked(() -> {
				ret[0] = retainAllImpl(c);
			}, true, false);
			return ret[0];
		}

		@Override
		public void clear() {
			theInternals.doLocked(() -> {
				clearImpl();
			}, true, false);
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
				if(theRemovedElement == node.getValue().getValue()) {
					ret = theRemovedElementIndex;
					node.getValue().getValue().setRemovedIndex(ret);
				} else {
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
			}
			return ret;
		}
	}

	private class DefaultSortedSetInternals extends DefaultCollectionInternals<E> {
		DefaultSortedSetInternals(ReentrantReadWriteLock lock, AtomicBoolean issuedController, Consumer<? super Boolean> postAction) {
			super(lock, issuedController, null, postAction);
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
