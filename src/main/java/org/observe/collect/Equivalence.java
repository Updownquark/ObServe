package org.observe.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterHashMap;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableElementSpliterator;
import org.qommons.collect.MutableMapEntryHandle;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

/**
 * Defines an equality scheme for comparing values for equivalence
 *
 * @param <E> The type of values that this scheme governs
 */
public interface Equivalence<E> {
	/**
	 * Tests whether a value is governed by this equivalence. If it is, then it may be cast and passed to
	 * {@link #elementEquals(Object, Object)}.
	 *
	 * @param v The value to test
	 * @return Whether the given value is a member of this equivalence set
	 */
	boolean isElement(Object v);

	/**
	 * Tests whether an element governed by this equivalence is equivalent to another element according to this equivalence set. If the
	 * second argument is not an element in this equivalence set, this method should return false.
	 *
	 * @param element The element to compare
	 * @param value The value to compare the element to
	 * @return Whether the given value is equivalent to the given element according to this collection
	 */
	boolean elementEquals(E element, Object value);

	/**
	 * @param <E2> The type for the set
	 * @return A new, empty set whose exclusivity is governed by this equivalence
	 */
	<E2 extends E> BetterSet<E2> createSet();

	/**
	 * @param <E2> The key type for the map
	 * @param <V> The value type for the map
	 * @return A new, empty map whose key exclusivity is governed by this equivalence
	 */
	<E2 extends E, V> BetterMap<E2, V> createMap();

	/** The default {@link Object#equals(Object)} implementation of equivalence. Used by most java collections. */
	Equivalence<Object> DEFAULT=new Equivalence<Object>() {
		@Override
		public boolean isElement(Object v) {
			return true;
		}

		@Override
		public boolean elementEquals(Object element, Object value) {
			return Objects.equals(element, value);
		}

		@Override
		public <E2> BetterSet<E2> createSet() {
			return BetterHashSet.build().unsafe().buildSet();
		}

		@Override
		public <E2, V> BetterMap<E2, V> createMap() {
			return BetterHashMap.build().unsafe().buildMap();
		}

		@Override
		public String toString() {
			return "Default equivalence";
		}
	};

	/** The <code>==</code> implementation of equivalence. Objects are compared by identity. */
	Equivalence<Object> ID = new Equivalence<Object>() {
		@Override
		public boolean isElement(Object v) {
			return true;
		}

		@Override
		public boolean elementEquals(Object element, Object value) {
			return element == value;
		}

		@Override
		public <E2> BetterSet<E2> createSet() {
			return BetterHashSet.build().unsafe().identity().buildSet();
		}

		@Override
		public <E2, V> BetterMap<E2, V> createMap() {
			return BetterHashMap.build().unsafe().identity().buildMap();
		}

		@Override
		public String toString() {
			return "Identity equivalence";
		}
	};

	/**
	 * Creates an equivalence object based on a conceptual sorted set
	 *
	 * @param <E> The type of values that belong in the set
	 * @param type The type of values that belong in the set
	 * @param compare The comparator defining the set
	 * @param nullable Whether null values are allowed in the set
	 * @return The comparator-based equivalence
	 */
	static <E> ComparatorEquivalence<E> of(Class<E> type, Comparator<? super E> compare, boolean nullable) {
		if (type.isPrimitive())
			type = (Class<E>) TypeToken.of(type).wrap().getRawType(); // Better way to do this?
		return new ComparatorEquivalence<>(type, nullable, compare);
	}

	/**
	 * Implements {@link Equivalence#of(Class, Comparator, boolean)}
	 *
	 * @param <E> The type of values in the set
	 */
	class ComparatorEquivalence<E> implements Equivalence<E> {
		private final Class<E> type;
		private final boolean nullable;
		private final Comparator<? super E> compare;

		ComparatorEquivalence(Class<E> type, boolean nullable, Comparator<? super E> compare) {
			if (type.isPrimitive())
				type = (Class<E>) TypeToken.of(type).wrap().getRawType(); // Better way to do this?
			this.type = type;
			this.nullable = nullable;
			this.compare = compare;
		}

		/** @return The type of values that belong in the set */
		public Class<E> getType() {
			return type;
		}

		/** @return Whether null values are allowed in the set */
		public boolean isNullable() {
			return nullable;
		}

		/** @return The comparator defining the set */
		public Comparator<? super E> comparator() {
			return compare;
		}

		@Override
		public boolean isElement(Object v) {
			if (v == null)
				return nullable;
			else
				return type.isInstance(v);
		}

		@Override
		public boolean elementEquals(E element, Object value) {
			return isElement(value) && compare.compare(element, (E) value) == 0;
		}

		@Override
		public <E2 extends E> BetterSet<E2> createSet() {
			return new BetterTreeSet<>(false, compare);
		}

		@Override
		public <E2 extends E, V> BetterMap<E2, V> createMap() {
			return new BetterTreeMap<>(false, compare);
		}

		public ComparatorEquivalence<E> reverse() {
			return new ComparatorEquivalence<>(type, nullable, compare.reversed());
		}
	}

	/**
	 * @param <T> The type of values in the mapped set
	 * @param type The type of values in the mapped set
	 * @param filter A filter that determines what values of the mapped type are allowed in the mapped set
	 * @param map The mapping function of this equivalence set's values to the mapped set value
	 * @param reverse The mapping function of the mapped set's values to this set's value
	 * @return An equivalence set of objects that are the result of a mapping function applied to values in this equivalence set
	 */
	default <E2 extends E, T> Equivalence<T> map(Class<T> type, Predicate<? super T> filter, Function<? super E2, ? extends T> map,
		Function<? super T, ? extends E2> reverse) {
		return new MappedEquivalence<>(this, type, filter, map, reverse);
	}

	class MappedEquivalence<E, E2 extends E, T> implements Equivalence<T> {
		private final Equivalence<E> theWrapped;
		private final Class<T> theType;
		private final Predicate<? super T> theFilter;
		private final Function<? super E2, ? extends T> theMap;
		private final Function<? super T, ? extends E2> theReverse;

		public MappedEquivalence(Equivalence<E> wrapped, Class<T> type, Predicate<? super T> filter, Function<? super E2, ? extends T> map,
			Function<? super T, ? extends E2> reverse) {
			if (type.isPrimitive())
				type = (Class<T>) TypeToken.of(type).wrap().getRawType(); // Better way to do this?
			theWrapped = wrapped;
			theType = type;
			theFilter = filter;
			theMap = map;
			theReverse = reverse;
		}

		@Override
		public boolean isElement(Object v) {
			if (v != null && !theType.isInstance(v))
				return false;
			if (theFilter != null && !theFilter.test((T) v))
				return false;
			return theWrapped.isElement(theReverse.apply((T) v));
		}

		@Override
		public boolean elementEquals(T element, Object value) {
			if (value != null && !theType.isInstance(value))
				return false;
			if (theFilter != null && !theFilter.test((T) value))
				return false;
			return theWrapped.elementEquals(theReverse.apply(element), theReverse.apply((T) value));
		}

		@Override
		public <E3 extends T> BetterSet<E3> createSet() {
			return new MappedSet<>(this, theWrapped.createSet(), theMap, theReverse);
		}

		@Override
		public <E3 extends T, V> BetterMap<E3, V> createMap() {
			return new MappedMap<>(this, theWrapped.createMap(), theMap, theReverse);
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof MappedEquivalence))
				return false;
			MappedEquivalence<?, ?, ?> equiv = (MappedEquivalence<?, ?, ?>) o;
			return equiv.theWrapped.equals(theWrapped) && equiv.theType.equals(theType) && Objects.equals(equiv.theFilter, theFilter)
				&& equiv.theReverse.equals(theReverse);
		}
	}

	class MappedSet<E, E2 extends E, T, T2 extends T> implements BetterSet<T2> {
		private final MappedEquivalence<E, E2, T> theEquivalence;
		private final BetterSet<E> theWrapped;
		private final Function<? super E2, ? extends T> theMap;
		private final Function<? super T, ? extends E2> theReverse;

		public MappedSet(MappedEquivalence<E, E2, T> equiv, BetterSet<E> wrapped, Function<? super E2, ? extends T> map,
			Function<? super T, ? extends E2> reverse) {
			theEquivalence = equiv;
			theWrapped = wrapped;
			theMap = map;
			theReverse = reverse;
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theWrapped.lock(write, structural, cause);
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theWrapped.getStamp(structuralOnly);
		}

		@Override
		public boolean belongs(Object o) {
			return theEquivalence.isElement(o);
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public <X> X[] toArray(X[] a) {
			return BetterSet.super.toArray(a);
		}

		@Override
		public Object[] toArray() {
			return BetterSet.super.toArray();
		}

		private CollectionElement<T2> handleFor(CollectionElement<? extends E> el) {
			return new CollectionElement<T2>() {
				@Override
				public ElementId getElementId() {
					return el.getElementId();
				}

				@Override
				public T2 get() {
					return (T2) theMap.apply((E2) el.get());
				}
			};
		}

		private MutableCollectionElement<T2> mutableHandleFor(MutableCollectionElement<? extends E> el) {
			return new MutableCollectionElement<T2>() {
				@Override
				public BetterCollection<T2> getCollection() {
					return MappedSet.this;
				}

				@Override
				public ElementId getElementId() {
					return el.getElementId();
				}

				@Override
				public T2 get() {
					return (T2) theMap.apply((E2) el.get());
				}

				@Override
				public String isEnabled() {
					return el.isEnabled();
				}

				@Override
				public String isAcceptable(T2 value) {
					return ((MutableCollectionElement<E>) el).isAcceptable(theReverse.apply(value));
				}

				@Override
				public void set(T2 value) throws UnsupportedOperationException, IllegalArgumentException {
					((MutableCollectionElement<E>) el).set(theReverse.apply(value));
				}

				@Override
				public String canRemove() {
					return el.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					el.remove();
				}

				@Override
				public String canAdd(T2 value, boolean before) {
					return ((MutableCollectionElement<E>) el).canAdd(theReverse.apply(value), before);
				}

				@Override
				public ElementId add(T2 value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
					return ((MutableCollectionElement<E>) el).add(theReverse.apply(value), before);
				}
			};
		}

		@Override
		public CollectionElement<T2> getTerminalElement(boolean first) {
			CollectionElement<E> wrapEl = theWrapped.getTerminalElement(first);
			return wrapEl == null ? null : handleFor(wrapEl);
		}

		@Override
		public CollectionElement<T2> getAdjacentElement(ElementId elementId, boolean next) {
			CollectionElement<E> wrapEl = theWrapped.getAdjacentElement(elementId, next);
			return wrapEl == null ? null : handleFor(wrapEl);
		}

		@Override
		public CollectionElement<T2> getElement(T2 value, boolean first) {
			CollectionElement<E> wrapEl = theWrapped.getElement(theReverse.apply(value), first);
			return wrapEl == null ? null : handleFor(wrapEl);
		}

		@Override
		public CollectionElement<T2> getElement(ElementId id) {
			return handleFor(theWrapped.getElement(id));
		}

		@Override
		public MutableCollectionElement<T2> mutableElement(ElementId id) {
			return mutableHandleFor(theWrapped.mutableElement(id));
		}

		@Override
		public MutableElementSpliterator<T2> spliterator(ElementId element, boolean asNext) {
			return new MappedMutableSpliterator(theWrapped.spliterator(element, asNext));
		}

		@Override
		public MutableElementSpliterator<T2> spliterator(boolean fromStart) {
			return new MappedMutableSpliterator(theWrapped.spliterator(fromStart));
		}

		@Override
		public String canAdd(T2 value) {
			return theWrapped.canAdd(theReverse.apply(value));
		}

		@Override
		public CollectionElement<T2> addElement(T2 value, boolean first) {
			return handleFor(theWrapped.addElement(theReverse.apply(value), first));
		}

		@Override
		public String canAdd(T2 value, ElementId after, ElementId before) {
			return theWrapped.canAdd(theReverse.apply(value), after, before);
		}

		@Override
		public CollectionElement<T2> addElement(T2 value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return handleFor(theWrapped.addElement(theReverse.apply(value), after, before, first));
		}

		@Override
		public boolean addAll(Collection<? extends T2> c) {
			try (Transaction t = lock(true, null); Transaction ct = Transactable.lock(c, false, null)) {
				for (T2 e : c)
					add(e);
				return !c.isEmpty();
			}
		}

		@Override
		public void clear() {
			theWrapped.clear();
		}

		private class MappedMutableSpliterator extends MutableElementSpliterator.SimpleMutableSpliterator<T2> {
			private final MutableElementSpliterator<E> theWrappedSpliter;

			public MappedMutableSpliterator(MutableElementSpliterator<E> wrap) {
				super(MappedSet.this);
				theWrappedSpliter = wrap;
			}

			@Override
			public long estimateSize() {
				return theWrappedSpliter.estimateSize();
			}

			@Override
			public long getExactSizeIfKnown() {
				return theWrappedSpliter.getExactSizeIfKnown();
			}

			@Override
			public int characteristics() {
				return theWrappedSpliter.characteristics();
			}

			@Override
			public Comparator<? super T2> getComparator() {
				Comparator<? super E> wrapCompare = theWrappedSpliter.getComparator();
				if (wrapCompare == null)
					return null;
				return (t1, t2) -> wrapCompare.compare(theReverse.apply(t1), theReverse.apply(t2));
			}

			@Override
			protected boolean internalForElement(Consumer<? super CollectionElement<T2>> action, boolean forward) {
				return theWrappedSpliter.forElement(el -> action.accept(handleFor(el)), forward);
			}

			@Override
			protected boolean internalForElementM(Consumer<? super MutableCollectionElement<T2>> action, boolean forward) {
				return theWrappedSpliter.forElementM(el -> action.accept(mutableHandleFor(el)), forward);
			}

			@Override
			public MutableElementSpliterator<T2> trySplit() {
				MutableElementSpliterator<E> wrapSplit = theWrappedSpliter.trySplit();
				return wrapSplit == null ? null : new MappedMutableSpliterator(wrapSplit);
			}
		}
	}

	class MappedMap<E, E2 extends E, T, T2 extends T, V> implements BetterMap<T2, V> {
		private final MappedEquivalence<E, E2, T> theEquivalence;
		private final BetterMap<E, V> theWrapped;
		private final Function<? super E2, ? extends T> theMap;
		private final Function<? super T, ? extends E2> theReverse;

		public MappedMap(MappedEquivalence<E, E2, T> equiv, BetterMap<E, V> wrapped, Function<? super E2, ? extends T> map,
			Function<? super T, ? extends E2> reverse) {
			theEquivalence = equiv;
			theWrapped = wrapped;
			theMap = map;
			theReverse = reverse;
		}

		@Override
		public BetterSet<T2> keySet() {
			return new MappedSet<>(theEquivalence, theWrapped.keySet(), theMap, theReverse);
		}

		@Override
		public MapEntryHandle<T2, V> putEntry(T2 key, V value, boolean first) {
			return handleFor(theWrapped.putEntry(theReverse.apply(key), value, first));
		}

		@Override
		public MapEntryHandle<T2, V> putEntry(T2 key, V value, ElementId after, ElementId before, boolean first) {
			return handleFor(theWrapped.putEntry(theReverse.apply(key), value, after, before, first));
		}

		private MapEntryHandle<T2, V> handleFor(MapEntryHandle<E, V> entry) {
			return new MapEntryHandle<T2, V>() {
				@Override
				public ElementId getElementId() {
					return entry.getElementId();
				}

				@Override
				public V get() {
					return entry.get();
				}

				@Override
				public T2 getKey() {
					return (T2) theMap.apply((E2) entry.getKey());
				}
			};
		}

		private MutableMapEntryHandle<T2, V> mutableHandleFor(MutableMapEntryHandle<E, V> entry) {
			return new MutableMapEntryHandle<T2, V>() {
				@Override
				public BetterCollection<V> getCollection() {
					return MappedMap.this.values();
				}

				@Override
				public ElementId getElementId() {
					return entry.getElementId();
				}

				@Override
				public V get() {
					return entry.get();
				}

				@Override
				public T2 getKey() {
					return (T2) theMap.apply((E2) entry.getKey());
				}

				@Override
				public String isEnabled() {
					return entry.isEnabled();
				}

				@Override
				public String isAcceptable(V value) {
					return entry.isAcceptable(value);
				}

				@Override
				public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
					entry.set(value);
				}

				@Override
				public String canRemove() {
					return entry.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					entry.remove();
				}

				@Override
				public String canAdd(V value, boolean before) {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public ElementId add(V value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}
			};
		}

		@Override
		public MapEntryHandle<T2, V> getEntry(T2 key) {
			MapEntryHandle<E, V> wrapEntry = theWrapped.getEntry(theReverse.apply(key));
			return wrapEntry == null ? null : handleFor(wrapEntry);
		}

		@Override
		public MapEntryHandle<T2, V> getEntryById(ElementId entryId) {
			return handleFor(theWrapped.getEntryById(entryId));
		}

		@Override
		public MutableMapEntryHandle<T2, V> mutableEntry(ElementId entryId) {
			return mutableHandleFor(theWrapped.mutableEntry(entryId));
		}
	}
}
