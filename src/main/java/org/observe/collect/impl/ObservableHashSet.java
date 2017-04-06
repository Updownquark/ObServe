package org.observe.collect.impl;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.assoc.impl.CollectionCreator;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableFastFindCollection;
import org.observe.collect.ObservableSet;
import org.qommons.Equalizer;
import org.qommons.Transactable;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

/**
 * A set whose content can be observed.
 *
 * @param <E> The type of element in the set
 */
public class ObservableHashSet<E> implements ObservableSet.PartialSetImpl<E>, ObservableFastFindCollection<E> {
	private final TypeToken<E> theType;
	private LinkedHashMap<E, InternalObservableElementImpl<E>> theValues;

	private HashSetInternals theInternals;

	/**
	 * Creates the set
	 *
	 * @param type The type of elements for this set
	 */
	public ObservableHashSet(TypeToken<E> type) {
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
	public ObservableHashSet(TypeToken<E> type, ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session,
		Transactable sessionController) {
		theType = type.wrap();
		theInternals = new HashSetInternals(lock, session, sessionController);

		theValues = new LinkedHashMap<>();
	}

	/** @return A {@link CollectionCreator} that creates hash sets of this type to back an associative data structure */
	public static <E> CollectionCreator<E, ObservableHashSet<E>> creator() {
		return (type, lock, session, controller) -> new ObservableHashSet<>(type, lock, session, controller);
	}

	@Override
	public Equalizer getEqualizer() {
		return Objects::equals;
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
				try (Transaction t = theInternals.lock(false, false, null)) {
					return backing.hasNext();
				}
			}

			@Override
			public E next() {
				try (Transaction t = theInternals.lock(false, false, null)) {
					Map.Entry<E, InternalObservableElementImpl<E>> entry = backing.next();
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
		};
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
	public ObservableValue<E> equivalent(Object o) {
		return new ObservableSet.ObservableSetEquivalentFinder<E>(this, o){
			@Override
			public E get() {
				try (Transaction t = theInternals.lock(false, false, null)) {
					InternalObservableElementImpl<E> element = theValues.get(o);
					return element == null ? null : element.get();
				}
			}
		};
	}

	@Override
	public boolean add(E e) {
		try (Transaction t = theInternals.lock(true, false, null)) {
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
		try (Transaction t = theInternals.lock(true, false, null)) {
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
		return ObservableSet.toString(this);
	}

	private class HashSetInternals extends DefaultCollectionInternals<E> {
		HashSetInternals(ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session, Transactable sessionController) {
			super(lock, session, sessionController, null);
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
