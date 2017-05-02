package org.observe.collect;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.qommons.collect.IdentityHashSet;

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
	 * Tests whether an element governed by this equivalence is equivalent to another value according to this collection.
	 *
	 * @param element The element to compare
	 * @param value The value to compare the element to
	 * @return Whether the given value is equivalent to the given element according to this collection
	 */
	boolean elementEquals(E element, Object value);

	/** @return A new, empty set whose exclusivity is governed by this equivalence */
	Set<E> createSet();

	/**
	 * @param <V> The type of values for the map
	 * @return A new, empty map whose key exclusivity is governed by this equivalence
	 */
	<V> Map<E, V> createMap();

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
		public Set<Object> createSet() {
			return new LinkedHashSet<>();
		}

		@Override
		public <V> Map<Object, V> createMap() {
			return new LinkedHashMap<>();
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
		public Set<Object> createSet() {
			return new IdentityHashSet<>();
		}

		@Override
		public <V> Map<Object, V> createMap() {
			return new IdentityHashMap<>();
		}
	};
}
