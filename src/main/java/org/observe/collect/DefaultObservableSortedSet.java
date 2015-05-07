package org.observe.collect;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.util.DefaultTransactable;
import org.observe.util.Transactable;
import org.observe.util.Transaction;

import prisms.lang.Type;

/**
 * TODO This class has not been fully implemented or tested
 *
 * A sorted set whose content can be observed
 *
 * @param <E> The type of element in the set
 */
public class DefaultObservableSortedSet<E> extends AbstractSet<E> implements ObservableSortedSet<E>, TransactableCollection<E> {
	private final Type theType;
	private AtomicBoolean hasIssuedController;
	private DefaultSortedSetInternals theInternals;

	private ObservableValue<CollectionSession> theSessionObservable;
	private Transactable theSessionController;

	private TreeMap<E, InternalObservableElementImpl<E>> theValues;

	/**
	 * Creates the set
	 *
	 * @param type The type of elements for this set
	 */
	public DefaultObservableSortedSet(Type type) {
		this(type, new ReentrantReadWriteLock(), null, null);

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
	 */
	public DefaultObservableSortedSet(Type type, ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session,
		Transactable sessionController) {
		theType = type;
		hasIssuedController = new AtomicBoolean(false);
		theInternals = new DefaultSortedSetInternals(lock, hasIssuedController);
		theSessionObservable = session;
		theSessionController = sessionController;

		theValues = new TreeMap<>();
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
	public TransactableSet<E> control(Consumer<? super Consumer<? super ObservableElement<E>>> onSubscribe) {
		if(hasIssuedController.getAndSet(true))
			throw new IllegalStateException("This observable set is already controlled");
		theInternals.setOnSubscribe(onSubscribe);
		return new ObservableSortedSetController();
	}

	@Override
	public Runnable onElement(Consumer<? super ObservableElement<E>> observer) {
		return theInternals.onElement(observer, true);
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
	public Object [] toArray() {
		Object [][] ret = new Object[1][];
		theInternals.doLocked(() -> {
			ret[0] = theValues.keySet().toArray();
		}, false, false);
		return ret[0];
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
		return new Iterator<E>() {
			private final Iterator<E> backing = theValues.keySet().iterator();

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
					ret[0] = backing.next();
				}, false, false);
				return (E) ret[0];
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException("Not Implemented");
			}
		};
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

	private boolean addImpl(E e) {
		if(theValues.containsKey(e))
			return false;
		InternalObservableElementImpl<E> el = new InternalObservableElementImpl<>(theType, e);
		theValues.put(e, el);
		theInternals.fireNewElement(el);
		return true;
	}

	private boolean removeImpl(Object o) {
		InternalObservableElementImpl<E> el = theValues.remove(o);
		if(el == null)
			return false;
		el.remove();
		return true;
	}

	private boolean addAllImpl(Collection<? extends E> c) {
		boolean ret = false;
		for(E add : c) {
			if(!theValues.containsKey(add))
				continue;
			ret = true;
			InternalObservableElementImpl<E> el = new InternalObservableElementImpl<>(theType, add);
			theValues.put(add, el);
			theInternals.fireNewElement(el);
		}
		return ret;
	}

	private boolean removeAllImpl(Collection<?> c) {
		boolean ret = false;
		for(Object o : c) {
			InternalObservableElementImpl<E> el = theValues.remove(o);
			if(el == null)
				continue;
			ret = true;
			el.remove();
		}
		return ret;
	}

	private boolean retainAllImpl(Collection<?> c) {
		boolean ret = false;
		Iterator<Map.Entry<E, InternalObservableElementImpl<E>>> iter = theValues.entrySet().iterator();
		while(iter.hasNext()) {
			Map.Entry<E, InternalObservableElementImpl<E>> entry = iter.next();
			if(c.contains(entry.getKey()))
				continue;
			ret = true;
			InternalObservableElementImpl<E> el = entry.getValue();
			iter.remove();
			el.remove();
		}
		return ret;
	}

	private void clearImpl() {
		Iterator<InternalObservableElementImpl<E>> iter = theValues.values().iterator();
		while(iter.hasNext()) {
			InternalObservableElementImpl<E> el = iter.next();
			iter.remove();
			el.remove();
		}
	}

	@Override
	public E lower(E e) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E floor(E e) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E ceiling(E e) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E higher(E e) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E pollFirst() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E pollLast() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public NavigableSet<E> descendingSet() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterator<E> descendingIterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public NavigableSet<E> headSet(E toElement, boolean inclusive) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SortedSet<E> subSet(E fromElement, E toElement) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SortedSet<E> headSet(E toElement) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SortedSet<E> tailSet(E fromElement) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Comparator<? super E> comparator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E first() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E last() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected DefaultObservableSortedSet<E> clone() throws CloneNotSupportedException {
		DefaultObservableSortedSet<E> ret = (DefaultObservableSortedSet<E>) super.clone();
		ret.theValues = (TreeMap<E, InternalObservableElementImpl<E>>) theValues.clone();
		for(Map.Entry<E, InternalObservableElementImpl<E>> entry : theValues.entrySet())
			entry.setValue(new InternalObservableElementImpl<>(theType, entry.getKey()));
		ret.hasIssuedController = new AtomicBoolean(false);
		ret.theInternals = ret.new DefaultSortedSetInternals(new ReentrantReadWriteLock(), hasIssuedController);
		return ret;
	}

	private class ObservableSortedSetController extends AbstractSet<E> implements NavigableSet<E>, TransactableSet<E> {
		@Override
		public Transaction startTransaction(Object cause) {
			return startTransactionImpl(cause);
		}

		@Override
		public Iterator<E> iterator() {
			return new Iterator<E>() {
				private Iterator<Map.Entry<E, InternalObservableElementImpl<E>>> backing = theValues.entrySet().iterator();

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
						ret[0] = backing.next();
					}, false, false);
					return (E) ret[0];
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException("Not Implemented");
				}
			};
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
		public Object [] toArray() {
			return DefaultObservableSortedSet.this.toArray();
		}

		@Override
		public <T> T [] toArray(T [] a) {
			return DefaultObservableSortedSet.this.toArray(a);
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

		@Override
		public NavigableSet<E> descendingSet() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Iterator<E> descendingIterator() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public NavigableSet<E> headSet(E toElement, boolean inclusive) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public SortedSet<E> subSet(E fromElement, E toElement) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public SortedSet<E> headSet(E toElement) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public SortedSet<E> tailSet(E fromElement) {
			// TODO Auto-generated method stub
			return null;
		}
	}

	private class DefaultSortedSetInternals extends DefaultCollectionInternals<E> {
		DefaultSortedSetInternals(ReentrantReadWriteLock lock, AtomicBoolean issuedController) {
			super(lock, issuedController, null, null);
		}

		@Override
		Iterable<? extends InternalObservableElementImpl<E>> getElements(boolean forward) {
			if(forward)
				return theValues.values();
			else
				return new Iterable<InternalObservableElementImpl<E>>() {
					@Override
					public Iterator<InternalObservableElementImpl<E>> iterator() {
						return theValues.descendingMap().values().iterator();
					}
				};
		}

		@Override
		ObservableElement<E> createExposedElement(InternalObservableElementImpl<E> internal, Collection<Runnable> subscriptions) {
			int todo; // TODO This should be an ordered element
			return new ExposedObservableElement<>(internal, subscriptions);
		}
	}
}
