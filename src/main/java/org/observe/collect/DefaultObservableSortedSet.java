package org.observe.collect;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
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
	private TreeMap<E, ObservableElementImpl<E>> theValues;

	private ReentrantReadWriteLock theLock;
	private AtomicBoolean hasIssuedController = new AtomicBoolean(false);

	private Consumer<? super Consumer<? super ObservableElement<E>>> theOnSubscribe;

	private java.util.concurrent.ConcurrentHashMap<Consumer<? super ObservableElement<E>>, ConcurrentLinkedQueue<Runnable>> theObservers;

	private ObservableValue<CollectionSession> theSessionObservable;

	private Transactable theSessionController;

	/**
	 * Creates the set
	 *
	 * @param type The type of elements for this set
	 */
	public DefaultObservableSortedSet(Type type) {
		this(type, new ReentrantReadWriteLock(), null, null);

		theSessionController = new DefaultTransactable(theLock.writeLock());
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
		theLock = lock;
		theSessionObservable = session;
		theSessionController = sessionController;

		theValues = new TreeMap<>();
		theObservers = new java.util.concurrent.ConcurrentHashMap<>();
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
	 * @param action The action to perform under a lock
	 * @param write Whether to perform the action under a write lock or a read lock
	 * @param errIfControlled Whether to throw an exception if this list is controlled
	 */
	protected void doLocked(Runnable action, boolean write, boolean errIfControlled) {
		if(errIfControlled && hasIssuedController.get())
			throw new IllegalStateException("Controlled default observable collections cannot be modified directly");
		Lock lock = write ? theLock.writeLock() : theLock.readLock();
		lock.lock();
		try {
			action.run();
		} finally {
			lock.unlock();
		}
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
		theOnSubscribe = onSubscribe;
		return new ObservableSortedSetController();
	}

	@Override
	public Runnable onElement(Consumer<? super ObservableElement<E>> observer) {
		ConcurrentLinkedQueue<Runnable> subSubscriptions = new ConcurrentLinkedQueue<>();
		theObservers.put(observer, subSubscriptions);
		doLocked(() -> {
			for(ObservableElementImpl<E> el : theValues.values())
				observer.accept(newValue(el, subSubscriptions));
		}, false, false);
		if(theOnSubscribe != null)
			theOnSubscribe.accept(observer);
		return () -> {
			ConcurrentLinkedQueue<Runnable> subs = theObservers.remove(observer);
			for(Runnable sub : subs)
				sub.run();
		};
	}

	private ObservableElement<E> newValue(ObservableElementImpl<E> el, ConcurrentLinkedQueue<Runnable> observers) {
		return new ObservableElement<E>() {
			@Override
			public Type getType() {
				return el.getType();
			}

			@Override
			public E get() {
				return el.get();
			}

			@Override
			public Runnable observe(org.observe.Observer<? super ObservableValueEvent<E>> observer) {
				ObservableValue<E> element = this;
				Runnable ret = el.observe(new org.observe.Observer<ObservableValueEvent<E>>() {
					@Override
					public <V extends ObservableValueEvent<E>> void onNext(V event) {
						observer.onNext(new ObservableValueEvent<>(element, event.getOldValue(), event.getValue(), event.getCause()));
					}

					@Override
					public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
						observer.onCompleted(new ObservableValueEvent<>(element, event.getOldValue(), event.getValue(), event.getCause()));
					}

					@Override
					public void onError(Throwable e) {
						observer.onError(e);
					}
				});
				observers.add(ret);
				return ret;
			}

			@Override
			public ObservableValue<E> persistent() {
				return el;
			}

			@Override
			public String toString() {
				return getType() + " set element (" + get() + ")";
			}
		};
	}

	private void fireNewElement(ObservableElementImpl<E> el) {
		for(Map.Entry<Consumer<? super ObservableElement<E>>, ConcurrentLinkedQueue<Runnable>> observer : theObservers
			.entrySet()) {
			observer.getKey().accept(newValue(el, observer.getValue()));
		}
	}

	@Override
	public int size() {
		return theValues.size();
	}

	@Override
	public boolean contains(Object o) {
		boolean [] ret = new boolean[1];
		doLocked(() -> {
			ret[0] = theValues.containsKey(o);
		}, false, false);
		return ret[0];
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		boolean [] ret = new boolean[1];
		doLocked(() -> {
			ret[0] = theValues.keySet().containsAll(c);
		}, false, false);
		return ret[0];
	}

	@Override
	public Object [] toArray() {
		Object [][] ret = new Object[1][];
		doLocked(() -> {
			ret[0] = theValues.keySet().toArray();
		}, false, false);
		return ret[0];
	}

	@Override
	public <T> T [] toArray(T [] a) {
		Object [][] ret = new Object[1][];
		doLocked(() -> {
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
				doLocked(() -> {
					ret[0] = backing.hasNext();
				}, false, false);
				return ret[0];
			}

			@Override
			public E next() {
				Object [] ret = new Object[1];
				doLocked(() -> {
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
		doLocked(() -> {
			ret[0] = addImpl(e);
		}, true, true);
		return ret[0];
	}

	@Override
	public boolean remove(Object o) {
		boolean [] ret = new boolean[1];
		doLocked(() -> {
			ret[0] = removeImpl(o);
		}, true, true);
		return ret[0];
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		boolean [] ret = new boolean[1];
		doLocked(() -> {
			ret[0] = removeAllImpl(c);
		}, true, true);
		return ret[0];
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		boolean [] ret = new boolean[1];
		doLocked(() -> {
			ret[0] = addAllImpl(c);
		}, true, true);
		return ret[0];
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		boolean [] ret = new boolean[1];
		doLocked(() -> {
			ret[0] = retainAllImpl(c);
		}, true, true);
		return ret[0];
	}

	@Override
	public void clear() {
		doLocked(() -> {
			clearImpl();
		}, true, true);
	}

	private boolean addImpl(E e) {
		if(theValues.containsKey(e))
			return false;
		ObservableElementImpl<E> el = new ObservableElementImpl<>(theType, e);
		theValues.put(e, el);
		fireNewElement(el);
		return true;
	}

	private boolean removeImpl(Object o) {
		ObservableElementImpl<E> el = theValues.remove(o);
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
			ObservableElementImpl<E> el = new ObservableElementImpl<>(theType, add);
			theValues.put(add, el);
			fireNewElement(el);
		}
		return ret;
	}

	private boolean removeAllImpl(Collection<?> c) {
		boolean ret = false;
		for(Object o : c) {
			ObservableElementImpl<E> el = theValues.remove(o);
			if(el == null)
				continue;
			ret = true;
			el.remove();
		}
		return ret;
	}

	private boolean retainAllImpl(Collection<?> c) {
		boolean ret = false;
		Iterator<Map.Entry<E, ObservableElementImpl<E>>> iter = theValues.entrySet().iterator();
		while(iter.hasNext()) {
			Map.Entry<E, ObservableElementImpl<E>> entry = iter.next();
			if(c.contains(entry.getKey()))
				continue;
			ret = true;
			ObservableElementImpl<E> el = entry.getValue();
			iter.remove();
			el.remove();
		}
		return ret;
	}

	private void clearImpl() {
		Iterator<ObservableElementImpl<E>> iter = theValues.values().iterator();
		while(iter.hasNext()) {
			ObservableElementImpl<E> el = iter.next();
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
		ret.theValues = (TreeMap<E, ObservableElementImpl<E>>) theValues.clone();
		for(Map.Entry<E, ObservableElementImpl<E>> entry : theValues.entrySet())
			entry.setValue(new ObservableElementImpl<>(theType, entry.getKey()));
		ret.theLock = new ReentrantReadWriteLock();
		ret.hasIssuedController = new AtomicBoolean(false);
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
				private Iterator<Map.Entry<E, ObservableElementImpl<E>>> backing = theValues.entrySet().iterator();

				@Override
				public boolean hasNext() {
					boolean [] ret = new boolean[1];
					doLocked(() -> {
						ret[0] = backing.hasNext();
					}, false, false);
					return ret[0];
				}

				@Override
				public E next() {
					Object [] ret = new Object[1];
					doLocked(() -> {
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
			doLocked(() -> {
				ret[0] = addImpl(e);
			}, true, false);
			return ret[0];
		}

		@Override
		public boolean remove(Object o) {
			boolean [] ret = new boolean[1];
			doLocked(() -> {
				ret[0] = removeImpl(o);
			}, true, false);
			return ret[0];
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean [] ret = new boolean[1];
			doLocked(() -> {
				ret[0] = removeAllImpl(c);
			}, true, false);
			return ret[0];
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			boolean [] ret = new boolean[1];
			doLocked(() -> {
				ret[0] = addAllImpl(c);
			}, true, false);
			return ret[0];
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean [] ret = new boolean[1];
			doLocked(() -> {
				ret[0] = retainAllImpl(c);
			}, true, false);
			return ret[0];
		}

		@Override
		public void clear() {
			doLocked(() -> {
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
}
