package org.observe.collect;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.qommons.IterableUtils;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterSet;
import org.qommons.collect.IdentityHashSet;
import org.qommons.collect.TreeSet;
import org.qommons.collect.UpdatableIdentityHashMap;
import org.qommons.collect.UpdatableMap;
import org.qommons.collect.UpdatableSet;

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
			return new BetterHashSet<>();
		}

		@Override
		public <E2, V> BetterMap<E2, V> createMap() {
			return new BetterHashMap<>();
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
		public <E2> UpdatableSet<E2> createSet() {
			return new IdentityHashSet<>();
		}

		@Override
		public <E2, V> UpdatableMap<E2, V> createMap() {
			return new UpdatableIdentityHashMap<>();
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
			return new TreeSet<>(false, compare);
		}

		@Override
		public <E2 extends E, V> BetterMap<E2, V> createMap() {
			return new org.qommons.collect.TreeMap<>(compare);
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

	class MappedSet<E, T, T2 extends T> extends AbstractSet<T2> implements BetterSet<T2> {
		private final MappedEquivalence<E, T> theEquivalence;
		private final UpdatableSet<E> theWrapped;
		private final Function<? super E, ? extends T> theMap;
		private final Function<? super T, ? extends E> theReverse;

		public MappedSet(MappedEquivalence<E, T> equiv, UpdatableSet<E> wrapped, Function<? super E, ? extends T> map,
			Function<? super T, ? extends E> reverse) {
			theEquivalence = equiv;
			theWrapped = wrapped;
			theMap = map;
			theReverse = reverse;
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
		public boolean contains(Object o) {
			return theEquivalence.isElement(o) && theWrapped.contains(theReverse.apply((T) o));
		}

		@Override
		public UpdatableSet.ElementUpdateResult update(T2 value) {
			return theWrapped.update(theReverse.apply(value));
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theWrapped
				.containsAll(c.stream().filter(theEquivalence::isElement).map(o -> theReverse.apply((T) o)).collect(Collectors.toList()));
		}

		@Override
		public Iterator<T2> iterator() {
			return IterableUtils.map(theWrapped, v -> (T2) theMap.apply(v)).iterator();
		}

		@Override
		public boolean add(T2 e) {
			if (!theEquivalence.isElement(e))
				throw new IllegalArgumentException("Illegal value");
			return theWrapped.add(theReverse.apply(e));
		}

		@Override
		public boolean addAll(Collection<? extends T2> c) {
			for (Object o : c)
				if (!theEquivalence.isElement(o))
					throw new IllegalArgumentException("Illegal value");
			return theWrapped.addAll(c.stream().map(theReverse).collect(Collectors.toList()));
		}

		@Override
		public boolean remove(Object o) {
			if (!theEquivalence.isElement(o))
				return false;
			return theWrapped.remove(theReverse.apply((T) o));
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theWrapped
				.retainAll(c.stream().filter(theEquivalence::isElement).map(v -> theReverse.apply((T) v)).collect(Collectors.toList()));
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theWrapped
				.removeAll(c.stream().filter(theEquivalence::isElement).map(v -> theReverse.apply((T) v)).collect(Collectors.toList()));
		}

		@Override
		public void clear() {
			theWrapped.clear();
		}
	}

	class MappedMap<E, T, T2 extends T, V> extends AbstractMap<T2, V> implements BetterMap<T2, V> {
		private final MappedEquivalence<E, T> theEquivalence;
		private final UpdatableMap<E, V> theWrapped;
		private final Function<? super E, ? extends T> theMap;
		private final Function<? super T, ? extends E> theReverse;

		public MappedMap(MappedEquivalence<E, T> equiv, UpdatableMap<E, V> wrapped, Function<? super E, ? extends T> map,
			Function<? super T, ? extends E> reverse) {
			theEquivalence = equiv;
			theWrapped = wrapped;
			theMap = map;
			theReverse = reverse;
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
		public boolean containsValue(Object value) {
			return theWrapped.containsValue(value);
		}

		@Override
		public boolean containsKey(Object key) {
			return theEquivalence.isElement(key) && theWrapped.containsKey(theReverse.apply((T) key));
		}

		@Override
		public UpdatableSet.ElementUpdateResult update(T2 key) {
			return theWrapped.update(theReverse.apply(key));
		}

		@Override
		public V get(Object key) {
			if (!theEquivalence.isElement(key))
				return null;
			return theWrapped.get(theReverse.apply((T) key));
		}

		@Override
		public V put(T2 key, V value) {
			if (!theEquivalence.isElement(key))
				throw new IllegalArgumentException("Invalid key");
			return theWrapped.put(theReverse.apply(key), value);
		}

		@Override
		public V remove(Object key) {
			if (!theEquivalence.isElement(key))
				return null;
			return theWrapped.remove(theReverse.apply((T) key));
		}

		@Override
		public void clear() {
			theWrapped.clear();
		}

		@Override
		public Collection<V> values() {
			return theWrapped.values();
		}

		@Override
		public Set<Entry<T2, V>> entrySet() {
			return new AbstractSet<Entry<T2, V>>() {
				@Override
				public Iterator<java.util.Map.Entry<T2, V>> iterator() {
					Function<Entry<E, V>, Entry<T2, V>> map = entry -> new Entry<T2, V>() {
						@Override
						public T2 getKey() {
							return (T2) theMap.apply(entry.getKey());
						}

						@Override
						public V getValue() {
							return entry.getValue();
						}

						@Override
						public V setValue(V value) {
							return entry.setValue(value);
						}
					};
					return IterableUtils.map(theWrapped.entrySet(), map).iterator();
				}

				@Override
				public int size() {
					return theWrapped.size();
				}

				@Override
				public void clear() {
					theWrapped.clear();
				}
			};
		}

		@Override
		public UpdatableSet<T2> keySet() {
			return super.keySet();
		}
	}
}
