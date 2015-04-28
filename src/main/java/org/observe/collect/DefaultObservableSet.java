package org.observe.collect;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.observe.DefaultObservableValue;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.util.Transaction;

import prisms.lang.Type;

/**
 * A set whose content can be observed. This set is immutable in that none of its methods, including {@link Set} methods, can modify its
 * content (Set modification methods will throw {@link UnsupportedOperationException}). To modify the list content, use
 * {@link #control(Consumer)} to obtain a set controller. This controller can be modified and these modifications will be reflected in this
 * set and will be propagated to subscribers.
 *
 * @param <E> The type of element in the set
 */
public class DefaultObservableSet<E> extends AbstractSet<E> implements ObservableSet<E>, TransactableSet<E> {
	private final Type theType;
	private LinkedHashMap<E, ObservableElementImpl<E>> theValues;

	private ReentrantReadWriteLock theLock;
	private AtomicBoolean hasIssuedController = new AtomicBoolean(false);

	private Consumer<? super Consumer<? super ObservableElement<E>>> theOnSubscribe;

	private java.util.concurrent.ConcurrentHashMap<Consumer<? super ObservableElement<E>>, ConcurrentLinkedQueue<Runnable>> theObservers;

	private CollectionSession theSession;
	private ObservableValue<CollectionSession> theSessionObservable;
	private org.observe.Observer<ObservableValueEvent<CollectionSession>> theSessionController;

	/**
	 * Creates the set
	 *
	 * @param type The type of elements for this set
	 */
	public DefaultObservableSet(Type type) {
		this(type, new ReentrantReadWriteLock(), null);

		theSessionObservable = new DefaultObservableValue<CollectionSession>() {
			private final Type theSessionType = new Type(CollectionSession.class);

			@Override
			public Type getType() {
				return theSessionType;
			}

			@Override
			public CollectionSession get() {
				return theSession;
			}
		};
		theSessionController = ((DefaultObservableValue<CollectionSession>) theSessionObservable).control(null);
	}

	/**
	 * This constructor is for specifying some of the internals of the list.
	 *
	 * @param type The type of elements for this list
	 * @param lock The lock for this list to use
	 * @param session The session for this list to use (see {@link #getSession()})
	 */
	protected DefaultObservableSet(Type type, ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session) {
		theType = type;
		theLock = lock;
		theSessionObservable = session;

		theValues = new LinkedHashMap<>();
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
		Lock lock = theLock.writeLock();
		lock.lock();
		theSession = new DefaultCollectionSession(cause);
		theSessionController.onNext(new ObservableValueEvent<>(theSessionObservable, null, theSession, cause));
		return new org.observe.util.Transaction() {
			@Override
			public void close() {
				if(theLock.getWriteHoldCount() != 1) {
					lock.unlock();
					return;
				}
				CollectionSession session = theSession;
				theSession = null;
				theSessionController.onNext(new ObservableValueEvent<>(theSessionObservable, session, null, cause));
				lock.unlock();
			}
		};
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
	 * Obtains the controller for this list. Once this is called, the observable set cannot be modified directly, but only through the
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
		return new ObservableSetController();
	}

	@Override
	public Runnable onElement(Consumer<? super ObservableElement<E>> onElement) {
		ConcurrentLinkedQueue<Runnable> subSubscriptions = new ConcurrentLinkedQueue<>();
		theObservers.put(onElement, subSubscriptions);
		doLocked(() -> {
			for(ObservableElementImpl<E> el : theValues.values())
				onElement.accept(newValue(el, subSubscriptions));
		}, false, false);
		if(theOnSubscribe != null)
			theOnSubscribe.accept(onElement);
		return () -> {
			ConcurrentLinkedQueue<Runnable> subs = theObservers.remove(onElement);
			if(subs == null)
				return;
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
			public Runnable observe(Observer<? super ObservableValueEvent<E>> observer) {
				ObservableValue<E> element = this;
				Runnable ret = el.observe(new Observer<ObservableValueEvent<E>>() {
					@Override
					public <V extends ObservableValueEvent<E>> void onNext(V event) {
						ObservableValueEvent<E> event2 = new ObservableValueEvent<>(element, event.getOldValue(), event.getValue(), event
							.getCause());
						observer.onNext(event2);
					}

					@Override
					public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
						ObservableValueEvent<E> event2 = new ObservableValueEvent<>(element, event.getOldValue(), event.getValue(), event
							.getCause());
						observer.onCompleted(event2);
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
		for(Map.Entry<Consumer<? super ObservableElement<E>>, ConcurrentLinkedQueue<Runnable>> observer : theObservers.entrySet()) {
			observer.getKey().accept(newValue(el, observer.getValue()));
		}
	}

	@Override
	public int size() {
		return theValues.size();
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
	public boolean contains(Object o) {
		boolean [] ret = new boolean[1];
		doLocked(() -> {
			ret[0] = theValues.containsKey(o);
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
	public boolean containsAll(Collection<?> c) {
		boolean [] ret = new boolean[1];
		doLocked(() -> {
			ret[0] = theValues.keySet().containsAll(c);
		}, false, false);
		return ret[0];
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

	private boolean addImpl(E e) {
		if(theValues.containsKey(e))
			return false;
		ObservableElementImpl<E> el = new ObservableElementImpl<>(theType, e);
		theValues.put(e, el);
		fireNewElement(el);
		return true;
	}

	@Override
	public void clear() {
		doLocked(() -> {
			clearImpl();
		}, true, true);
	}

	private boolean removeImpl(Object o) {
		ObservableElementImpl<E> el = theValues.remove(o);
		if(el == null)
			return false;
		el.remove();
		return true;
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

	private boolean addAllImpl(Collection<? extends E> c) {
		boolean ret = false;
		for(E add : c) {
			if(theValues.containsKey(add))
				continue;
			ret = true;
			ObservableElementImpl<E> el = new ObservableElementImpl<>(theType, add);
			theValues.put(add, el);
			fireNewElement(el);
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
	protected DefaultObservableSet<E> clone() throws CloneNotSupportedException {
		DefaultObservableSet<E> ret = (DefaultObservableSet<E>) super.clone();
		ret.theValues = (LinkedHashMap<E, ObservableElementImpl<E>>) theValues.clone();
		for(Map.Entry<E, ObservableElementImpl<E>> entry : theValues.entrySet())
			entry.setValue(new ObservableElementImpl<>(theType, entry.getKey()));
		ret.theLock = new ReentrantReadWriteLock();
		ret.hasIssuedController = new AtomicBoolean(false);
		return ret;
	}

	private class ObservableSetController extends AbstractSet<E> implements TransactableSet<E> {
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
			return DefaultObservableSet.this.size();
		}

		@Override
		public boolean contains(Object o) {
			return DefaultObservableSet.this.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return DefaultObservableSet.this.containsAll(c);
		}

		@Override
		public Object [] toArray() {
			return DefaultObservableSet.this.toArray();
		}

		@Override
		public <T> T [] toArray(T [] a) {
			return DefaultObservableSet.this.toArray(a);
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
	}
}
