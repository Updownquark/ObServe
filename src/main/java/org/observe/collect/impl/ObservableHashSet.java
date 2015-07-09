package org.observe.collect.impl;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableFastFindCollection;
import org.observe.collect.ObservableSet;
import org.observe.util.DefaultTransactable;
import org.observe.util.Transactable;
import org.observe.util.Transaction;

import prisms.lang.Type;

/**
 * A set whose content can be observed.
 *
 * @param <E> The type of element in the set
 */
public class ObservableHashSet<E> implements ObservableSet.PartialSetImpl<E>, ObservableFastFindCollection<E> {
	private final Type theType;
	private LinkedHashMap<E, InternalObservableElementImpl<E>> theValues;

	private HashSetInternals theInternals;

	private ObservableValue<CollectionSession> theSessionObservable;
	private Transactable theSessionController;

	/**
	 * Creates the set
	 *
	 * @param type The type of elements for this set
	 */
	public ObservableHashSet(Type type) {
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
	public ObservableHashSet(Type type, ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session,
		Transactable sessionController) {
		theType = type;
		theInternals = new HashSetInternals(lock);
		theSessionObservable = session;
		theSessionController = sessionController;

		theValues = new LinkedHashMap<>();
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
	public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
		return theInternals.onElement(onElement, true);
	}

	private InternalObservableElementImpl<E> createElement(E value) {
		return new InternalObservableElementImpl<>(theType, value);
	}

	@Override
	public int size() {
		return theValues.size();
	}

	@Override
	public Iterator<E> iterator() {
		return new Iterator<E>() {
			private final Iterator<Map.Entry<E, InternalObservableElementImpl<E>>> backing = theValues.entrySet().iterator();
			private InternalObservableElementImpl<E> theLastElement;

			@Override
			public boolean hasNext() {
				try (Transaction t = theInternals.lock(false)) {
					return backing.hasNext();
				}
			}

			@Override
			public E next() {
				try (Transaction t = theInternals.lock(false)) {
					Map.Entry<E, InternalObservableElementImpl<E>> entry = backing.next();
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
		};
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
	public ObservableValue<E> equivalent(Object o) {
		return new ObservableSet.ObservableSetEquivalentFinder<E>(this, o){
			@Override
			public E get() {
				try (Transaction t = theInternals.lock(false)) {
					InternalObservableElementImpl<E> element = theValues.get(o);
					return element == null ? null : element.get();
				}
			}
		};
	}

	@Override
	public boolean add(E e) {
		try (Transaction t = theInternals.lock(true)) {
			if(theValues.containsKey(e))
				return false;
			InternalObservableElementImpl<E> el = createElement(e);
			theValues.put(e, el);
			theInternals.fireNewElement(el);
			return true;
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
				InternalObservableElementImpl<E> el = createElement(add);
				theValues.put(add, el);
				theInternals.fireNewElement(el);
			}
		}
		return ret;
	}

	@Override
	public boolean remove(Object o) {
		try (Transaction t = theInternals.lock(true)) {
			InternalObservableElementImpl<E> el = theValues.remove(o);
			if(el == null)
				return false;
			el.remove();
			return true;
		}
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		boolean ret = false;
		try (Transaction t = lock(true, null)) {
			for(Object o : c) {
				InternalObservableElementImpl<E> el = theValues.remove(o);
				if(el == null)
					continue;
				ret = true;
				el.remove();
			}
		}
		return ret;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		boolean ret = false;
		try (Transaction t = lock(true, null)) {
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
		}
		return ret;
	}

	@Override
	public void clear() {
		try (Transaction t = lock(true, null)) {
			Iterator<InternalObservableElementImpl<E>> iter = theValues.values().iterator();
			while(iter.hasNext()) {
				InternalObservableElementImpl<E> el = iter.next();
				iter.remove();
				el.remove();
			}
		}
	}

	@Override
	protected ObservableHashSet<E> clone() throws CloneNotSupportedException {
		ObservableHashSet<E> ret = (ObservableHashSet<E>) super.clone();
		ret.theValues = (LinkedHashMap<E, InternalObservableElementImpl<E>>) theValues.clone();
		for(Map.Entry<E, InternalObservableElementImpl<E>> entry : theValues.entrySet())
			entry.setValue(ret.createElement(entry.getKey()));
		ret.theInternals = ret.new HashSetInternals(new ReentrantReadWriteLock());
		return ret;
	}

	private class HashSetInternals extends DefaultCollectionInternals<E> {
		HashSetInternals(ReentrantReadWriteLock lock) {
			super(lock, null, null);
		}

		@Override
		Iterable<? extends InternalObservableElementImpl<E>> getElements(boolean forward) {
			return theValues.values();
		}

		@Override
		ObservableElement<E> createExposedElement(InternalObservableElementImpl<E> internal, Collection<Subscription> subscriptions) {
			return new ExposedObservableElement<>(internal, subscriptions);
		}
	}
}