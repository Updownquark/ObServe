package org.observe;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Lockable.CoreId;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterHashMap;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableMapEntryHandle;
import org.qommons.collect.ValueStoredCollection;
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

	/**
	 * @param <E2> The type of the instance to test
	 * @param instance The value to test
	 * @return An instance of this equivalence's set that corresponds to the given value
	 */
	<E2 extends E> E2 getSetMember(E2 instance);

	/**
	 * Creates an equivalence object based on a conceptual sorted sub-set of instances of this set
	 *
	 * @param <E2> The type of values that belong in the sub-set
	 * @param type The type of values that belong in the sub-set
	 * @param compare The comparator defining the sub-set
	 * @param nullable Whether null values are allowed in the sub-set
	 * @return The comparator-based equivalence
	 */
	default <E2 extends E> SortedEquivalence<E2> sorted(Class<E2> type, Comparator<? super E2> compare, boolean nullable) {
		if (type.isPrimitive())
			type = TypeTokens.get().wrap(type);
		return new ComparatorEquivalence<>(this, type, nullable, compare);
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
			return BetterHashSet.build().build();
		}

		@Override
		public <E2, V> BetterMap<E2, V> createMap() {
			return BetterHashMap.build().build();
		}

		@Override
		public <E2> E2 getSetMember(E2 instance) {
			return instance;
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
			return BetterHashSet.build().identity().build();
		}

		@Override
		public <E2, V> BetterMap<E2, V> createMap() {
			return BetterHashMap.build().identity().build();
		}

		@Override
		public <E2> E2 getSetMember(E2 instance) {
			return instance;
		}

		@Override
		public String toString() {
			return "Identity equivalence";
		}
	};

	/**
	 * Equivalence based on sorting of values
	 *
	 * @param <E> The type of values in the set
	 */
	interface SortedEquivalence<E> extends Equivalence<E> {
		/** @return The comparator defining the set */
		Comparator<? super E> comparator();

		/** @return The type of values that belong in the set */
		Class<E> getType();

		/** @return Whether null values are allowed in the set */
		boolean isNullable();

		/** @return An equivalence identical to this equivalence, but whose sorting is reversed */
		SortedEquivalence<E> reverse();

		@Override
		default <E2 extends E, T> SortedEquivalence<T> map(Class<T> type, Predicate<? super T> filter,
			Function<? super E2, ? extends T> map, Function<? super T, ? extends E2> reverse) {
			return new MappedComparatorEquivalence<>(this, type, filter, map, reverse);
		}
	}

	/**
	 * Implements {@link Equivalence#sorted(Class, Comparator, boolean)}
	 *
	 * @param <E> The type of values in the set
	 */
	class ComparatorEquivalence<E> implements SortedEquivalence<E> {
		private final Equivalence<? super E> theParentEquivalence;
		private final Class<E> type;
		private final boolean nullable;
		private final Comparator<? super E> compare;

		ComparatorEquivalence(Equivalence<? super E> parent, Class<E> type, boolean nullable, Comparator<? super E> compare) {
			if (compare == null)
				throw new NullPointerException();
			theParentEquivalence = parent;
			if (type.isPrimitive())
				type = TypeTokens.get().wrap(type);
			this.type = type;
			this.nullable = nullable;
			if (parent == DEFAULT || parent == ID)
				this.compare = compare;
			else
				this.compare = LambdaUtils.printableComparator((o1, o2) -> {
					o1 = theParentEquivalence.getSetMember(o1);
					o2 = theParentEquivalence.getSetMember(o2);
					return compare.compare(o1, o2);
				}, compare::toString, compare);
		}

		/** @return The equivalence that this sub-set is based on */
		public Equivalence<? super E> getParentEquivalence() {
			return theParentEquivalence;
		}

		@Override
		public Class<E> getType() {
			return type;
		}

		@Override
		public boolean isNullable() {
			return nullable;
		}

		@Override
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
			if (element == value)
				return true;
			else if (element == null || value == null)
				return false;
			else if (!isElement(value))
				return false;
			else
				return compare.compare(element, (E) value) == 0;
		}

		@Override
		public <E2 extends E> BetterSet<E2> createSet() {
			return BetterTreeSet.<E2> buildTreeSet(compare).build();
		}

		@Override
		public <E2 extends E, V> BetterMap<E2, V> createMap() {
			return BetterTreeMap.<E2> build(compare).buildMap();
		}

		@Override
		public <E2 extends E> E2 getSetMember(E2 instance) {
			return instance;
		}

		@Override
		public SortedEquivalence<E> reverse() {
			return new ComparatorEquivalence<>(theParentEquivalence, type, nullable, BetterSortedSet.ReversedSortedSet.reverse(compare));
		}

		@Override
		public String toString() {
			return "equivalence(" + compare + ")";
		}
	}

	/**
	 * Implements {@link Equivalence#map(Class, Predicate, Function, Function)}
	 *
	 * @param <E> The type of the source equivalence
	 * @param <E2> The sub type of the source equivalence's type that this equivalence understands
	 * @param <T> The type of this equivalence
	 */
	class MappedEquivalence<E, E2 extends E, T> implements Equivalence<T> {
		private final Equivalence<E> theWrapped;
		private final Class<T> theType;
		private final Predicate<? super T> theFilter;
		private final Function<? super E2, ? extends T> theMap;
		private final Function<? super T, ? extends E2> theReverse;

		public MappedEquivalence(Equivalence<E> wrapped, Class<T> type, Predicate<? super T> filter, Function<? super E2, ? extends T> map,
			Function<? super T, ? extends E2> reverse) {
			if (type.isPrimitive())
				type = TypeTokens.get().wrap(type);
			theWrapped = wrapped;
			theType = type;
			theFilter = filter;
			theMap = map;
			theReverse = reverse;
		}

		/** @return The type of values in the set */
		public Class<T> getType() {
			return theType;
		}

		protected Equivalence<E> getWrapped() {
			return theWrapped;
		}

		protected Predicate<? super T> getFilter() {
			return theFilter;
		}

		protected Function<? super E2, ? extends T> getMap() {
			return theMap;
		}

		protected Function<? super T, ? extends E2> getReverse() {
			return theReverse;
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
		public <T2 extends T> T2 getSetMember(T2 instance) {
			if (theFilter != null && !theFilter.test(instance))
				return null;
			E2 reversed = theReverse.apply(instance);
			return (T2) theMap.apply(reversed);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theWrapped, theType, theFilter, theMap, theReverse);
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof MappedEquivalence))
				return false;
			MappedEquivalence<?, ?, ?> equiv = (MappedEquivalence<?, ?, ?>) o;
			return equiv.theWrapped.equals(theWrapped) && equiv.theType.equals(theType) && Objects.equals(equiv.theFilter, theFilter)
				&& equiv.theReverse.equals(theReverse);
		}

		@Override
		public String toString() {
			return theWrapped + ".map(" + theMap + ")";
		}
	}

	/**
	 * Implements {@link Equivalence.SortedEquivalence#map(Class, Predicate, Function, Function)}
	 *
	 * @param <E> The type of the wrapped equivalence
	 * @param <E2> The sub-type of source values that can be mapped to values in this equivalence
	 * @param <T> The type of values in this equivalence
	 */
	class MappedComparatorEquivalence<E, E2 extends E, T> extends MappedEquivalence<E, E2, T> implements SortedEquivalence<T> {
		private final Comparator<T> theSorting;

		MappedComparatorEquivalence(SortedEquivalence<E> wrapped, Class<T> type, Predicate<? super T> filter,
			Function<? super E2, ? extends T> map, Function<? super T, ? extends E2> reverse) {
			super(wrapped, type, filter, map, reverse);
			theSorting = LambdaUtils.printableComparator((t1, t2) -> {
				E2 e1 = reverse.apply(t1);
				E2 e2 = reverse.apply(t2);
				return wrapped.comparator().compare(e1, e2);
			}, () -> wrapped.comparator() + ".map(" + map + ")", new BiTuple<>(wrapped.comparator(), map));
		}

		@Override
		protected SortedEquivalence<E> getWrapped() {
			return (org.observe.Equivalence.SortedEquivalence<E>) super.getWrapped();
		}

		@Override
		public Comparator<? super T> comparator() {
			return theSorting;
		}

		@Override
		public boolean isNullable() {
			return true;
		}

		@Override
		public SortedEquivalence<T> reverse() {
			return getWrapped().reverse().map(getType(), getFilter(), getMap(), getReverse());
		}
	}

	/**
	 * A set for a {@link Equivalence.MappedEquivalence}
	 *
	 * @param <E> The type of the source equivalence
	 * @param <E2> The sub type of the source equivalence's type that this set's equivalence understands
	 * @param <T> The type of this set's equivalence
	 * @param <T2> The type of the set
	 */
	class MappedSet<E, E2 extends E, T, T2 extends T> implements BetterSet<T2> {
		private final MappedEquivalence<E, E2, T> theEquivalence;
		private final BetterSet<E> theWrapped;
		private final Function<? super E2, ? extends T> theMap;
		private final Function<? super T, ? extends E2> theReverse;
		private Object theIdentity;

		public MappedSet(MappedEquivalence<E, E2, T> equiv, BetterSet<E> wrapped, Function<? super E2, ? extends T> map,
			Function<? super T, ? extends E2> reverse) {
			theEquivalence = equiv;
			theWrapped = wrapped;
			theMap = map;
			theReverse = reverse;
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(theWrapped.getIdentity(), "map", theMap);
			return theIdentity;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theWrapped.getThreadConstraint();
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
		public Transaction tryLock(boolean write, Object cause) {
			return theWrapped.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return theWrapped.getCoreId();
		}

		@Override
		public long getStamp() {
			return theWrapped.getStamp();
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
		public CollectionElement<T2> getOrAdd(T2 value, ElementId after, ElementId before, boolean first, Runnable preAdd,
			Runnable postAdd) {
			return handleFor(theWrapped.getOrAdd(theReverse.apply(value), after, before, first, preAdd, postAdd));
		}

		@Override
		public MutableCollectionElement<T2> mutableElement(ElementId id) {
			return mutableHandleFor(theWrapped.mutableElement(id));
		}

		@Override
		public BetterList<CollectionElement<T2>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return QommonsUtils.map2(theWrapped.getElementsBySource(sourceEl, sourceCollection), this::handleFor);
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return theWrapped.getSourceElements(localElement, theWrapped);
			return theWrapped.getSourceElements(localElement, sourceCollection);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			return theWrapped.getEquivalentElement(equivalentEl);
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
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return theWrapped.canMove(valueEl, after, before);
		}

		@Override
		public CollectionElement<T2> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			return handleFor(theWrapped.move(valueEl, after, before, first, afterRemove));
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
		public boolean isConsistent(ElementId element) {
			return theWrapped.isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			return theWrapped.checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, ValueStoredCollection.RepairListener<T2, X> listener) {
			return theWrapped.repair(element, listener == null ? null : new MappedRepairListener<>(listener));
		}

		@Override
		public <X> boolean repair(ValueStoredCollection.RepairListener<T2, X> listener) {
			return theWrapped.repair(listener == null ? null : new MappedRepairListener<>(listener));
		}

		@Override
		public void clear() {
			theWrapped.clear();
		}

		@Override
		public int hashCode() {
			return BetterSet.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterSet.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterSet.toString(this);
		}

		private class MappedRepairListener<X> implements ValueStoredCollection.RepairListener<E, X> {
			private final ValueStoredCollection.RepairListener<T2, X> theWrappedListener;

			MappedRepairListener(ValueStoredCollection.RepairListener<T2, X> wrapped) {
				theWrappedListener = wrapped;
			}

			@Override
			public X removed(CollectionElement<E> element) {
				return theWrappedListener.removed(handleFor(element));
			}

			@Override
			public void disposed(E value, X data) {
				theWrappedListener.disposed((T2) theMap.apply((E2) value), data);
			}

			@Override
			public void transferred(CollectionElement<E> element, X data) {
				theWrappedListener.transferred(handleFor(element), data);
			}
		}
	}

	/**
	 * A map for a {@link Equivalence.MappedEquivalence}
	 *
	 * @param <E> The type of the source equivalence
	 * @param <E2> The sub type of the source equivalence's type that this map's equivalence understands
	 * @param <T> The type of this map's equivalence
	 * @param <T2> The key type of this map
	 * @param <V> The value type of this map
	 */
	class MappedMap<E, E2 extends E, T, T2 extends T, V> implements BetterMap<T2, V> {
		private final MappedEquivalence<E, E2, T> theEquivalence;
		private final BetterMap<E, V> theWrapped;
		private final Function<? super E2, ? extends T> theMap;
		private final Function<? super T, ? extends E2> theReverse;
		private Object theIdentity;

		public MappedMap(MappedEquivalence<E, E2, T> equiv, BetterMap<E, V> wrapped, Function<? super E2, ? extends T> map,
			Function<? super T, ? extends E2> reverse) {
			theEquivalence = equiv;
			theWrapped = wrapped;
			theMap = map;
			theReverse = reverse;
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(theWrapped.getIdentity(), "map", theMap);
			return theIdentity;
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

		@Override
		public MapEntryHandle<T2, V> getOrPutEntry(T2 key, Function<? super T2, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable preAdd, Runnable postAdd) {
			return handleFor(theWrapped.getOrPutEntry(theReverse.apply(key), k -> value.apply(key), after, before, first, preAdd, postAdd));
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

		@Override
		public String canPut(T2 key, V value) {
			return theWrapped.canPut(//
				theReverse.apply(key), //
				value);
		}

		@Override
		public int hashCode() {
			return BetterMap.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterMap.equals(this, obj);
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}
	}
}
