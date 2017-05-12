package org.observe.collect.impl;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.observe.collect.CollectionSubscription;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableElementSpliterator;
import org.observe.collect.ObservableSet;
import org.qommons.Transaction;
import org.qommons.collect.BetterHashSet;

import com.google.common.reflect.TypeToken;

/**
 * A simple linked hash set implementation of {@link ObservableSet}
 *
 * @param <E> The type of values in the set
 */
public class ObservableHashSet2<E> implements ObservableSet<E> {
	private class Element {
		private Long theElementId;
		private final E theValue;

		Element(E value) {
			theValue = value;
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theValue);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ObservableHashSet2.Element && Objects.equals(((Element) obj).theValue, theValue);
		}

		@Override
		public String toString() {
			return String.valueOf(theValue);
		}
	}
	private final TypeToken<E> theType;
	private final BetterHashSet<Element> theHashSet;
	private final AtomicLong theElementIdGen;

	/** @param type The type for the set */
	public ObservableHashSet2(TypeToken<E> type) {
		theType = type;
		theHashSet = new BetterHashSet<>();
		theElementIdGen = new AtomicLong(Long.MIN_VALUE);
		int todo = todo; // TODO Need to extend BetterHashSet or something to get notifications when elements are added, removed, changed
		// TODO When generating element IDs, need to have a check in place to reset the generator and all elements currently in the set
		// when the long value wraps around
	}

	@Override
	public TypeToken<E> getType() {
		return theType;
	}

	@Override
	public boolean order(Object elementId1, Object elementId2) {
		return ((Long) elementId1).longValue() < ((Long) elementId2).longValue();
	}

	@Override
	public Equivalence<? super E> equivalence() {
		return Equivalence.DEFAULT;
	}

	@Override
	public boolean isLockSupported() {
		return theHashSet.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theHashSet.lock(write, cause);
	}

	@Override
	public int size() {
		return theHashSet.size();
	}

	@Override
	public boolean isEmpty() {
		return theHashSet.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		if (o != null && !theType.getRawType().isInstance(o))
			return false;
		return theHashSet.contains(new Element((E) o));
	}

	@Override
	public boolean containsAny(Collection<?> c) {
		return theHashSet.containsAny(c.stream().filter(o -> o == null || theType.getRawType().isInstance(o)).map(o -> new Element((E) o))
			.collect(Collectors.toSet()));
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return theHashSet.containsAll(c.stream().filter(o -> o == null || theType.getRawType().isInstance(o)).map(o -> new Element((E) o))
			.collect(Collectors.toSet()));
	}

	@Override
	public String canAdd(E value) {
		if (value != null && !theType.getRawType().isInstance(value))
			return StdMsg.BAD_TYPE;
		if (contains(value))
			return StdMsg.ELEMENT_EXISTS;
		return null;
	}

	@Override
	public boolean add(E value) {
		if (value != null && !theType.getRawType().isInstance(value))
			throw new IllegalArgumentException(StdMsg.BAD_TYPE);
		return theHashSet.add(new Element(value));
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		for (E v : c)
			if (v != null && !theType.getRawType().isInstance(v))
				throw new IllegalStateException(StdMsg.BAD_TYPE);
		return theHashSet.addAll(c.stream().map(o -> new Element(o)).collect(Collectors.toSet()));
	}

	@Override
	public String canRemove(Object value) {
		if (value != null && !theType.getRawType().isInstance(value))
			return StdMsg.BAD_TYPE;
		if (!contains(value))
			return StdMsg.NOT_FOUND;
		return null;
	}

	@Override
	public boolean remove(Object o) {
		return theHashSet.remove(new Element((E) o));
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return theHashSet.removeAll(c.stream().filter(o -> o == null || theType.getRawType().isInstance(o)).map(o -> new Element((E) o))
			.collect(Collectors.toSet()));
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return theHashSet.retainAll(c.stream().filter(o -> o == null || theType.getRawType().isInstance(o)).map(o -> new Element((E) o))
			.collect(Collectors.toSet()));
	}

	@Override
	public void clear() {
		theHashSet.clear();
	}

	@Override
	public ObservableElementSpliterator<E> spliterator() {
		// TODO Auto-generated method stub
	}

	@Override
	public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
		// TODO Auto-generated method stub
	}
}
