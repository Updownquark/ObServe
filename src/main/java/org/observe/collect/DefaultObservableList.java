package org.observe.collect;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.util.DefaultTransactable;
import org.observe.util.Transactable;
import org.observe.util.Transaction;

import prisms.lang.Type;

/**
 * A list whose content can be observed. This list is immutable in that none of its methods, including {@link List} methods, can modify its
 * content (List modification methods will throw {@link UnsupportedOperationException}). To modify the list content, use
 * {@link #control(Consumer)} to obtain a list controller. This controller can be modified and these modifications will be reflected in this
 * list and will be propagated to subscribers.
 *
 * @param <E> The type of element in the list
 */
public class DefaultObservableList<E> extends AbstractList<E> implements ObservableRandomAccessList<E>, TransactableList<E>, RandomAccess {
	private final Type theType;

	private DefaultListInternals theInternals;
	private AtomicBoolean hasIssuedController;

	private ObservableValue<CollectionSession> theSessionObservable;

	private Transactable theSessionController;

	private ArrayList<InternalObservableElementImpl<E>> theElements;

	private ArrayList<E> theValues;

	private volatile InternalObservableElementImpl<E> theRemovedElement;
	private volatile int theRemovedElementIndex;

	/**
	 * Creates the list
	 *
	 * @param type The type of elements for this list
	 */
	public DefaultObservableList(Type type) {
		this(type, new ReentrantReadWriteLock(), null, null);

		theSessionController = new DefaultTransactable(theInternals.getLock().writeLock());
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
	public DefaultObservableList(Type type, ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session,
		Transactable sessionController) {
		theType = type;
		hasIssuedController = new AtomicBoolean(false);
		theInternals = new DefaultListInternals(lock, hasIssuedController, write -> {
			if(write) {
				theRemovedElement = null;
				theRemovedElementIndex = -1;
			}
		});
		theSessionObservable = session;
		theSessionController = sessionController;

		theValues = new ArrayList<>();
		theElements = new ArrayList<>();
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
	 * Obtains the controller for this list. Once this is called, the observable list cannot be modified directly, but only through the
	 * controller. Modification methods to this list after this call will throw an {@link IllegalStateException}. Only one call can be made
	 * to this method. All calls after the first will throw an {@link IllegalStateException}.
	 *
	 * @param onSubscribe The listener to be notified when new subscriptions to this collection are made
	 * @return The list to control this list's data.
	 */
	public TransactableList<E> control(Consumer<? super Consumer<? super ObservableElement<E>>> onSubscribe) {
		if(hasIssuedController.getAndSet(true))
			throw new IllegalStateException("This observable list is already controlled");
		theInternals.setOnSubscribe(onSubscribe);
		return new ObservableListController();
	}

	@Override
	public Runnable onOrderedElement(Consumer<? super OrderedObservableElement<E>> onElement) {
		// Cast is safe because the internals of this set will only create ordered elements
		return theInternals.onElement((Consumer<ObservableElement<E>>) onElement, true);
	}

	@Override
	public Runnable onElementReverse(Consumer<? super OrderedObservableElement<E>> onElement) {
		// Cast is safe because the internals of this set will only create ordered elements
		return theInternals.onElement((Consumer<ObservableElement<E>>) onElement, false);
	}

	@Override
	public E get(int index) {
		Object [] ret = new Object[1];
		theInternals.doLocked(() -> {
			ret[0] = theValues.get(index);
		}, false, false);
		return (E) ret[0];
	}

	@Override
	public int size() {
		return theValues.size();
	}

	@Override
	public boolean contains(Object o) {
		boolean [] ret = new boolean[1];
		theInternals.doLocked(() -> {
			ret[0] = theValues.contains(o);
		}, false, false);
		return ret[0];
	}

	@Override
	public Object [] toArray() {
		Object [][] ret = new Object[1][];
		theInternals.doLocked(() -> {
			ret[0] = theValues.toArray();
		}, false, false);
		return ret[0];
	}

	@Override
	public <T> T [] toArray(T [] a) {
		Object [][] ret = new Object[1][];
		theInternals.doLocked(() -> {
			ret[0] = theValues.toArray(a);
		}, false, false);
		return (T []) ret[0];
	}

	@Override
	public int indexOf(Object o) {
		int [] ret = new int[1];
		theInternals.doLocked(() -> {
			ret[0] = theValues.indexOf(o);
		}, false, false);
		return ret[0];
	}

	@Override
	public int lastIndexOf(Object o) {
		int [] ret = new int[1];
		theInternals.doLocked(() -> {
			ret[0] = theValues.lastIndexOf(o);
		}, false, false);
		return ret[0];
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		boolean [] ret = new boolean[1];
		theInternals.doLocked(() -> {
			ret[0] = theValues.containsAll(c);
		}, false, false);
		return ret[0];
	}

	@Override
	public boolean add(E e) {
		theInternals.doLocked(() -> {
			addImpl(e);
		}, true, true);
		return true;
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
	public boolean addAll(Collection<? extends E> c) {
		if(c.isEmpty())
			return false;
		theInternals.doLocked(() -> {
			addAllImpl(c);
		}, true, false);
		return true;
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		if(c.isEmpty())
			return false;
		theInternals.doLocked(() -> {
			addAllImpl(index, c);
		}, true, true);
		return true;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		if(c.isEmpty())
			return false;
		boolean [] ret = new boolean[] {false};
		theInternals.doLocked(() -> {
			ret[0] = removeAllImpl(c);
		}, true, true);
		return ret[0];
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		if(c.isEmpty()) {
			boolean ret = !isEmpty();
			clear();
			return ret;
		}
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
	public E set(int index, E element) {
		Object [] ret = new Object[1];
		theInternals.doLocked(() -> {
			ret[0] = setImpl(index, element);
		}, true, true);
		return (E) ret[0];
	}

	@Override
	public void add(int index, E element) {
		theInternals.doLocked(() -> {
			addImpl(index, element);
		}, true, true);
	}

	@Override
	public E remove(int index) {
		Object [] ret = new Object[1];
		theInternals.doLocked(() -> {
			ret[0] = removeImpl(index);
		}, true, true);
		return (E) ret[0];
	}

	private void addImpl(Object e) {
		E val = (E) theType.cast(e);
		theValues.add(val);
		InternalObservableElementImpl<E> add = new InternalObservableElementImpl<>(theType, val);
		theElements.add(add);
		theInternals.fireNewElement(add);
	}

	private boolean removeImpl(Object o) {
		int idx = theValues.indexOf(o);
		if(idx < 0) {
			return false;
		}
		theValues.remove(idx);
		theRemovedElement = theElements.remove(idx);
		theRemovedElementIndex = idx;
		theRemovedElement.remove();
		return true;
	}

	private void addAllImpl(Collection<? extends E> c) {
		try (Transaction trans = startTransactionImpl(null)) {
			for(E e : c) {
				E val = (E) theType.cast(e);
				theValues.add(val);
				InternalObservableElementImpl<E> newWrapper = new InternalObservableElementImpl<>(theType, val);
				theElements.add(newWrapper);
				theInternals.fireNewElement(newWrapper);
			}
		}
	}

	private void addAllImpl(int index, Collection<? extends E> c) {
		try (Transaction trans = startTransactionImpl(null)) {
			int idx = index;
			for(E e : c) {
				E val = (E) theType.cast(e);
				theValues.add(idx, val);
				InternalObservableElementImpl<E> newWrapper = new InternalObservableElementImpl<>(theType, val);
				theElements.add(idx, newWrapper);
				theInternals.fireNewElement(newWrapper);
				idx++;
			}
		}
	}

	private boolean removeAllImpl(Collection<?> c) {
		try (Transaction trans = startTransactionImpl(null)) {
			boolean ret = false;
			for(Object o : c) {
				int idx = theValues.indexOf(o);
				if(idx >= 0) {
					ret = true;
					theValues.remove(idx);
					theRemovedElement = theElements.remove(idx);
					theRemovedElementIndex = idx;
					theRemovedElement.remove();
				}
			}
			return ret;
		}
	}

	private boolean retainAllImpl(Collection<?> c) {
		try (Transaction trans = startTransactionImpl(null)) {
			boolean ret = false;
			BitSet keep = new BitSet();
			for(Object o : c) {
				int idx = theValues.indexOf(o);
				if(idx >= 0)
					keep.set(idx);
			}
			ret = keep.nextClearBit(0) < theValues.size();
			if(ret) {
				for(int i = theValues.size() - 1; i >= 0; i--)
					if(!keep.get(i)) {
						theValues.remove(i);
						theRemovedElement = theElements.remove(i);
						theRemovedElementIndex = i;
						theRemovedElement.remove();
					}
			}
			return ret;
		}
	}

	private void clearImpl() {
		try (Transaction trans = startTransactionImpl(null)) {
			theValues.clear();
			ArrayList<InternalObservableElementImpl<E>> remove = new ArrayList<>();
			remove.addAll(theElements);
			theElements.clear();
			for(int i = remove.size() - 1; i >= 0; i--) {
				theRemovedElement = remove.get(i);
				theRemovedElementIndex = i;
				theRemovedElement.remove();
			}
		}
	}

	private E setImpl(int index, E element) {
		E val = (E) theType.cast(element);
		E ret = theValues.set(index, val);
		theElements.get(index).set(val);
		return ret;
	}

	private void addImpl(int index, E element) {
		E val = (E) theType.cast(element);
		theValues.add(index, val);
		InternalObservableElementImpl<E> newWrapper = new InternalObservableElementImpl<>(theType, val);
		theElements.add(index, newWrapper);
		theInternals.fireNewElement(newWrapper);
	}

	private E removeImpl(int index) {
		E ret = theValues.remove(index);
		theRemovedElement = theElements.remove(index);
		theRemovedElementIndex = index;
		theRemovedElement.remove();
		return ret;
	}

	@Override
	protected DefaultObservableList<E> clone() throws CloneNotSupportedException {
		DefaultObservableList<E> ret = (DefaultObservableList<E>) super.clone();
		ret.theValues = (ArrayList<E>) theValues.clone();
		ret.theElements = new ArrayList<>();
		for(E el : ret.theValues)
			ret.theElements.add(new InternalObservableElementImpl<>(theType, el));
		ret.hasIssuedController = new AtomicBoolean(false);
		ret.theInternals = ret.new DefaultListInternals(new ReentrantReadWriteLock(), ret.hasIssuedController, write -> {
			if(write) {
				theRemovedElement = null;
				theRemovedElementIndex = -1;
			}
		});
		return ret;
	}

	private class ObservableListController extends AbstractList<E> implements TransactableList<E> {
		@Override
		public Transaction startTransaction(Object cause) {
			return startTransactionImpl(cause);
		}

		@Override
		public int size() {
			return DefaultObservableList.this.size();
		}

		@Override
		public boolean contains(Object o) {
			return DefaultObservableList.this.contains(o);
		}

		@Override
		public Object [] toArray() {
			return DefaultObservableList.this.toArray();
		}

		@Override
		public <T> T [] toArray(T [] a) {
			return DefaultObservableList.this.toArray(a);
		}

		@Override
		public E get(int index) {
			return DefaultObservableList.this.get(index);
		}

		@Override
		public int indexOf(Object o) {
			return DefaultObservableList.this.indexOf(o);
		}

		@Override
		public int lastIndexOf(Object o) {
			return DefaultObservableList.this.lastIndexOf(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return DefaultObservableList.this.containsAll(c);
		}

		@Override
		public boolean add(E e) {
			theInternals.doLocked(() -> {
				addImpl(e);
			}, true, false);
			return true;
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
		public boolean addAll(Collection<? extends E> c) {
			if(c.isEmpty())
				return false;
			theInternals.doLocked(() -> {
				addAllImpl(c);
			}, true, false);
			return true;
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			if(c.isEmpty())
				return false;
			theInternals.doLocked(() -> {
				addAllImpl(index, c);
			}, true, false);
			return true;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			if(c.isEmpty())
				return false;
			boolean [] ret = new boolean[] {false};
			theInternals.doLocked(() -> {
				ret[0] = removeAllImpl(c);
			}, true, false);
			return ret[0];
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			if(c.isEmpty()) {
				boolean ret = !isEmpty();
				clear();
				return ret;
			}
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
		public E set(int index, E element) {
			Object [] ret = new Object[1];
			theInternals.doLocked(() -> {
				ret[0] = setImpl(index, element);
			}, true, false);
			return (E) ret[0];
		}

		@Override
		public void add(int index, E element) {
			theInternals.doLocked(() -> {
				addImpl(index, element);
			}, true, false);
		}

		@Override
		public E remove(int index) {
			Object [] ret = new Object[1];
			theInternals.doLocked(() -> {
				ret[0] = removeImpl(index);
			}, true, false);
			return (E) ret[0];
		}
	}

	private class DefaultListInternals extends DefaultCollectionInternals<E> {
		DefaultListInternals(ReentrantReadWriteLock lock, AtomicBoolean issuedController, Consumer<? super Boolean> postAction) {
			super(lock, issuedController, null, postAction);
		}

		@Override
		Iterable<? extends InternalObservableElementImpl<E>> getElements(boolean forward) {
			if(forward)
				return theElements;
			else
				return new Iterable<InternalObservableElementImpl<E>>() {
				@Override
				public Iterator<InternalObservableElementImpl<E>> iterator() {
					return new Iterator<InternalObservableElementImpl<E>>() {
						private final ListIterator<InternalObservableElementImpl<E>> backing;

						{
							backing = theElements.listIterator(theElements.size());
						}

						@Override
						public boolean hasNext() {
							return backing.hasPrevious();
						}

						@Override
						public InternalObservableElementImpl<E> next() {
							return backing.previous();
						}
					};
				}
			};
		}

		@Override
		ObservableElement<E> createExposedElement(InternalObservableElementImpl<E> internal, Collection<Runnable> subscriptions) {
			class ExposedOrderedObservableElement extends ExposedObservableElement<E> implements OrderedObservableElement<E> {
				private int theRemovedIndex = -1;

				ExposedOrderedObservableElement() {
					super(internal, subscriptions);
				}

				@Override
				public int getIndex() {
					int ret = theElements.indexOf(internal);
					if(ret < 0)
						ret = theRemovedIndex;
					if(ret < 0 && theRemovedElement == internal)
						ret = theRemovedIndex = theRemovedElementIndex;
					return ret;
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
