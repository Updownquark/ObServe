package org.observe.collect;

import java.util.Comparator;
import java.util.Objects;

import org.qommons.collect.IdentityHashSet;
import org.qommons.collect.UpdatableHashMap;
import org.qommons.collect.UpdatableHashSet;
import org.qommons.collect.UpdatableIdentityHashMap;
import org.qommons.collect.UpdatableMap;
import org.qommons.collect.UpdatableSet;
import org.qommons.tree.CountedRedBlackNode;

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
	<E2 extends E> UpdatableSet<E2> createSet();

	/**
	 * @param <E2> The key type for the map
	 * @param <V> The value type for the map
	 * @return A new, empty map whose key exclusivity is governed by this equivalence
	 */
	<E2 extends E, V> UpdatableMap<E2, V> createMap();

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
		public <E2> UpdatableSet<E2> createSet() {
			return new UpdatableHashSet<>();
		}

		@Override
		public <E2, V> UpdatableMap<E2, V> createMap() {
			return new UpdatableHashMap<>();
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

	static <E> Equivalence<E> of(Class<E> type, boolean nullable, Comparator<? super E> compare) {
		return new ComparatorEquivalence<>(type, nullable, compare);
	}

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
		public <E2 extends E> UpdatableSet<E2> createSet() {
			return new CountedRedBlackNode.DefaultTreeSet<>(compare);
		}

		@Override
		public <E2 extends E, V> UpdatableMap<E2, V> createMap() {
			return new CountedRedBlackNode.DefaultTreeMap<>(compare);
		}
	}
}
