package org.observe.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterHashMap;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterSet;
import org.qommons.collect.ElementHandle;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableElementHandle;
import org.qommons.collect.MutableElementSpliterator;
import org.qommons.collect.MutableMapEntryHandle;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;

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
	};

	static <E> Equivalence<E> of(Class<E> type, Comparator<? super E> compare, boolean nullable) {
		return new ComparatorEquivalence<>(type, nullable, compare);
	}

	class ComparatorEquivalence<E> implements Equivalence<E> {
		private final Class<E> type;
		private final boolean nullable;
		private final Comparator<? super E> compare;

		public ComparatorEquivalence(Class<E> type, boolean nullable, Comparator<? super E> compare) {
			this.type = type;
			this.nullable = nullable;
			this.compare = compare;
		}

		public Class<E> getType() {
			return type;
		}

		public boolean isNullable() {
			return nullable;
		}

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
	}

	default <T> Equivalence<T> map(Class<T> type, Predicate<? super T> filter, Function<? super E, ? extends T> map,
		Function<? super T, ? extends E> reverse) {
		return new MappedEquivalence<>(this, type, filter, map, reverse);
	}

	class MappedEquivalence<E, T> implements Equivalence<T> {
		private final Equivalence<E> theWrapped;
		private final Class<T> theType;
		private final Predicate<? super T> theFilter;
		private final Function<? super E, ? extends T> theMap;
		private final Function<? super T, ? extends E> theReverse;

		public MappedEquivalence(Equivalence<E> wrapped, Class<T> type, Predicate<? super T> filter, Function<? super E, ? extends T> map,
			Function<? super T, ? extends E> reverse) {
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
		public <E2 extends T> BetterSet<E2> createSet() {
			return new MappedSet<>(this, theWrapped.createSet(), theMap, theReverse);
		}

		@Override
		public <E2 extends T, V> BetterMap<E2, V> createMap() {
			return new MappedMap<>(this, theWrapped.createMap(), theMap, theReverse);
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof MappedEquivalence))
				return false;
			MappedEquivalence<?, ?> equiv = (MappedEquivalence<?, ?>) o;
			return equiv.theWrapped.equals(theWrapped) && equiv.theType.equals(theType) && Objects.equals(equiv.theFilter, theFilter)
				&& equiv.theReverse.equals(theReverse);
		}
	}

	class MappedSet<E, T, T2 extends T> implements BetterSet<T2> {
		private final MappedEquivalence<E, T> theEquivalence;
		private final BetterSet<E> theWrapped;
		private final Function<? super E, ? extends T> theMap;
		private final Function<? super T, ? extends E> theReverse;

		public MappedSet(MappedEquivalence<E, T> equiv, BetterSet<E> wrapped, Function<? super E, ? extends T> map,
			Function<? super T, ? extends E> reverse) {
			theEquivalence = equiv;
			theWrapped = wrapped;
			theMap = map;
			theReverse = reverse;
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
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public boolean forElement(T2 value, Consumer<? super ElementHandle<? extends T2>> onElement, boolean first) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean forMutableElement(T2 value, Consumer<? super MutableElementHandle<? extends T2>> onElement, boolean first) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public <X> X ofElementAt(ElementId elementId, Function<? super ElementHandle<? extends T2>, X> onElement) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public <X> X ofMutableElementAt(ElementId elementId, Function<? super MutableElementHandle<? extends T2>, X> onElement) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public MutableElementSpliterator<T2> mutableSpliterator(boolean fromStart) {
			return new MappedMutableSpliterator(theWrapped.mutableSpliterator(fromStart));
		}

		@Override
		public String canAdd(T2 value) {
			return theWrapped.canAdd(theReverse.apply(value));
		}

		@Override
		public ElementId addElement(T2 value) {
			return theWrapped.addElement(theReverse.apply(value));
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

		private class MappedMutableSpliterator implements MutableElementSpliterator<T2> {
			private final MutableElementSpliterator<E> theWrapped;

			public MappedMutableSpliterator(MutableElementSpliterator<E> wrap) {
				theWrapped = wrap;
			}
		}
	}

	class MappedMap<E, T, T2 extends T, V> implements BetterMap<T2, V> {
		private final MappedEquivalence<E, T> theEquivalence;
		private final BetterMap<E, V> theWrapped;
		private final Function<? super E, ? extends T> theMap;
		private final Function<? super T, ? extends E> theReverse;

		public MappedMap(MappedEquivalence<E, T> equiv, BetterMap<E, V> wrapped, Function<? super E, ? extends T> map,
			Function<? super T, ? extends E> reverse) {
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
		public ElementId putEntry(T2 key, V value) {
			return theWrapped.putEntry(theReverse.apply(key), value);
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
					return (T2) theMap.apply(entry.getKey());
				}
			};
		}

		private MutableMapEntryHandle<T2, V> mutableHandleFor(MutableMapEntryHandle<E, V> entry) {
			return new MutableMapEntryHandle<T2, V>() {
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
					return (T2) theMap.apply(entry.getKey());
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
		public boolean forEntry(T2 key, Consumer<? super MapEntryHandle<T2, V>> onEntry) {
			return theWrapped.forEntry(theReverse.apply(key), entry -> onEntry.accept(handleFor(entry)));
		}

		@Override
		public boolean forMutableEntry(T2 key, Consumer<? super MutableMapEntryHandle<T2, V>> onEntry) {
			return theWrapped.forMutableEntry(theReverse.apply(key), entry -> onEntry.accept(mutableHandleFor(entry)));
		}

		@Override
		public <X> X ofEntry(ElementId entryId, Function<? super MapEntryHandle<T2, V>, X> onEntry) {
			return theWrapped.ofEntry(entryId, entry -> onEntry.apply(handleFor(entry)));
		}

		@Override
		public <X> X ofMutableEntry(ElementId entryId, Function<? super MutableMapEntryHandle<T2, V>, X> onEntry) {
			return theWrapped.ofMutableEntry(entryId, entry -> onEntry.apply(mutableHandleFor(entry)));
		}
	}
}
