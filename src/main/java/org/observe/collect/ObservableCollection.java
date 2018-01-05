package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.Combination.CombinationPrecursor;
import org.observe.collect.Combination.CombinedFlowDef;
import org.observe.collect.FlowOptions.MapOptions;
import org.observe.collect.FlowOptions.UniqueOptions;
import org.observe.collect.ObservableCollectionDataFlowImpl.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.PassiveCollectionManager;
import org.qommons.Causable;
import org.qommons.Ternian;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.SimpleCause;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * An enhanced collection.
 *
 * The biggest differences between ObservableCollection and Collection are:
 * <ul>
 * <li><b>Observability</b> ObservableCollections may be listened to via {@link #onChange(Consumer)}, {@link #subscribe(Consumer, boolean)},
 * {@link #changes()}, and {@link #simpleChanges()}, as well as various other listenable derived information, such as
 * {@link #observeSize()}.</li>
 * <li><b>Dynamic Transformation</b> The stream API allows transforming of the content of one collection into another, but the
 * transformation is done once for all, creating a new collection independent of the source. Sometimes it is desirable to make a transformed
 * collection that does its transformation dynamically, keeping the same data source, so that when the source is modified, the transformed
 * collection is also updated accordingly. The {@link #flow() flow} API allows the creation of collections that are the result of
 * {@link CollectionDataFlow#map(TypeToken, Function, Consumer) map}, {@link CollectionDataFlow#filter(Function) filter},
 * {@link CollectionDataFlow#distinct(Consumer) unique}, {@link CollectionDataFlow#sorted(Comparator) sort},
 * {@link CollectionDataFlow#combine(TypeToken, Function) combination} or other operations on the elements of the source. Collections so
 * derived from a source collection are themselves observable and reflect changes to the source. The derived collection may also be mutable,
 * with modifications to the derived collection affecting the source.</li>
 * <li><b>Modification Control</b> The {@link #flow() flow} API also supports constraints on how or whether a derived collection may be
 * {@link CollectionDataFlow#filterMod(Consumer) modified}.</li>
 * <li><b>Enhanced {@link Spliterator}s</b> ObservableCollections must implement {@link #spliterator(boolean)}, which returns a
 * {@link org.qommons.collect.MutableElementSpliterator}, which is an enhanced {@link Spliterator}. This has potential for the improved
 * performance associated with using {@link Spliterator} instead of {@link Iterator} as well as the reversibility and ability to
 * {@link MutableCollectionElement#add(Object, boolean) add}, {@link MutableCollectionElement#remove() remove}, or
 * {@link MutableCollectionElement#set(Object) replace} elements during iteration.</li>
 * <li><b>Transactionality</b> ObservableCollections support the {@link org.qommons.Transactable} interface, allowing callers to reserve a
 * collection for write or to ensure that the collection is not written to during an operation (for implementations that support this. See
 * {@link org.qommons.Transactable#isLockSupported() isLockSupported()}).</li>
 * <li><b>Run-time type safety</b> ObservableCollections have a {@link #getType() type} associated with them, allowing them to enforce
 * type-safety at run time. How strictly this type-safety is enforced is implementation-dependent.</li>
 * <li><b>Custom {@link #equivalence() equivalence}</b> Instead of being a slave to each element's own {@link Object#equals(Object) equals}
 * scheme, collections can be defined with custom schemes which will affect any operations involving element comparison, such as
 * {@link #contains(Object)} and {@link #remove()}.</li>
 * <li><b>Enhanced element access</b> The {@link #forElement(Object, Consumer, boolean) forObservableElement} and
 * {@link #forMutableElement(Object, Consumer, boolean) forMutableElement} methods, along with several others, allow access to elements in
 * the array without the need and potentially without the performance cost of iterating.</li>
 * </ul>
 *
 * @param <E> The type of element in the collection
 */
public interface ObservableCollection<E> extends BetterList<E> {
	/**
	 * The {@link ObservableCollectionEvent#getCause() cause} for events fired for extant elements in the collection upon
	 * {@link #subscribe(Consumer, boolean) subscription}
	 */
	public static class SubscriptionCause extends SimpleCause {}

	// Additional contract methods

	/** @return The type of elements in this collection */
	TypeToken<E> getType();

	@Override
	default boolean belongs(Object value) {
		if (!equivalence().isElement(value))
			return false;
		TypeToken<E> type = getType();
		if (value == null)
			return !type.isPrimitive();
		else if (!getType().wrap().getRawType().isInstance(value))
			return false;
		return true;
	}

	@Override
	abstract boolean isLockSupported();

	/**
	 * Registers a listener for changes to this collection
	 *
	 * @param observer The listener to be notified of each element change in the collection
	 * @return The subscription to use to terminate listening
	 */
	Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer);

	@Override
	default boolean containsAny(Collection<?> c) {
		try (Transaction ct = Transactable.lock(c, false, null)) {
			if (c.isEmpty())
				return true;
			try (Transaction t = lock(false, null)) {
				if (c.size() < size()) {
					for (Object o : c)
						if (contains(o))
							return true;
					return false;
				} else {
					if (c.isEmpty())
						return false;
					Set<E> cSet = ObservableCollectionImpl.toSet(this, equivalence(), c);
					Spliterator<E> iter = spliterator();
					boolean[] found = new boolean[1];
					while (iter.tryAdvance(next -> {
						found[0] = cSet.contains(next);
					}) && !found[0]) {
					}
					return found[0];
				}
			}
		}
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		try (Transaction ct = Transactable.lock(c, false, null)) {
			if (c.isEmpty())
				return true;
			try (Transaction t = lock(false, null)) {
				if (c.size() < size()) {
					for (Object o : c)
						if (!contains(o))
							return false;
					return true;
				} else {
					if (c.isEmpty())
						return false;
					Set<E> cSet = ObservableCollectionImpl.toSet(this, equivalence(), c);
					cSet.removeAll(this);
					return cSet.isEmpty();
				}
			}
		}
	}

	@Override
	void clear();

	/**
	 * Like {@link #onChange(Consumer)}, but also fires initial {@link CollectionChangeType#add add} events for each element currently in
	 * the collection, and optionally fires final {@link CollectionChangeType#remove remove} events for each element in the collection on
	 * unsubscription.
	 *
	 * @param observer The listener to be notified of each element change in the collection
	 * @param forward Whether to fire events for initial values (and possibly terminal removes) in forward or reverse order
	 * @param reverse Whether to fire add events for the initial elements in reverse
	 * @return The subscription to use to terminate listening
	 */
	default CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer, boolean forward) {
		Subscription changeSub;
		try (Transaction t = lock(false, null)) {
			// Initial events
			int[] index = new int[] { forward ? 0 : size() - 1 };
			SubscriptionCause cause = new SubscriptionCause();
			try (Transaction ct = SubscriptionCause.use(cause)) {
				spliterator(forward).forEachElement(el -> {
					ObservableCollectionEvent<E> event = new ObservableCollectionEvent<>(el.getElementId(), getType(), index[0],
						CollectionChangeType.add, null, el.get(), cause);
					observer.accept(event);
					if (forward)
						index[0]++;
					else
						index[0]--;
				}, forward);
			}
			// Subscribe changes
			changeSub = onChange(observer);
		}
		return removeAll -> {
			try (Transaction t = lock(false, null)) {
				// Unsubscribe changes
				changeSub.unsubscribe();
				if (removeAll) {
					// Remove events
					int[] index = new int[] { forward ? 0 : size() - 1 };
					SubscriptionCause cause = new SubscriptionCause();
					try (Transaction ct = SubscriptionCause.use(cause)) {
						spliterator(forward).forEachElement(el -> {
							E value = el.get();
							ObservableCollectionEvent<E> event = new ObservableCollectionEvent<>(el.getElementId(), getType(), index[0],
								CollectionChangeType.remove, value, value, cause);
							observer.accept(event);
							if (!forward)
								index[0]--;
						}, forward);
					}
				}
			}
		};
	}

	/** @return A collection that is identical to this one, but with its elements reversed */
	@Override
	default ObservableCollection<E> reverse() {
		return new ObservableCollectionImpl.ReversedObservableCollection<>(this);
	}

	/**
	 * Gets access to the equivalence scheme that this collection uses. {@link ObservableCollection}s are permitted to compare their
	 * elements using any consistent scheme. This scheme is exposed here and should affect any operations on the collection that require
	 * element comparison, e.g.:
	 * <ul>
	 * <li>{@link #contains(Object)}, which will return true for an argument <code>arg</code> if and only if there is an
	 * <code>element</code> in the collection for which this <code>elementEquals(element, arg)</code> returns true</li>
	 * <li>{@link #remove(Object)}, which will only remove elements that match according to this method</li>
	 * </ul>
	 *
	 * The equivalence's {@link Equivalence#isElement(Object)} method must return true for any element in this collection or any element for
	 * which {@link #canAdd(Object)} returns null.
	 *
	 * For {@link ObservableSet}s, this method exposes a test of the set's exclusiveness. I.e. a set may not contain any 2 elements for
	 * which this method returns true.
	 *
	 * @return The equivalence that governs this collection
	 */
	Equivalence<? super E> equivalence();

	// Default implementations of redundant Collection methods

	@Override
	default E[] toArray() {
		E[] array;
		try (Transaction t = lock(false, null)) {
			array = (E[]) java.lang.reflect.Array.newInstance(getType().wrap().getRawType(), size());
			int[] i = new int[1];
			spliterator().forEachRemaining(v -> array[i[0]++] = v);
		}
		return array;
	}

	@Override
	default <T> T[] toArray(T[] a) {
		ArrayList<E> ret;
		try (Transaction t = lock(false, null)) {
			ret = new ArrayList<>();
			spliterator().forEachRemaining(v -> ret.add(v));
		}
		return ret.toArray(a);
	}

	// Simple utility methods

	@Override
	default ObservableCollection<E> with(E... values) {
		addAll(java.util.Arrays.asList(values));
		return this;
	}

	/**
	 * @param elements The IDs of the elements to modify
	 * @param value The value to set for each specified element
	 */
	void setValue(Collection<ElementId> elements, E value);

	// Derived observable changes

	/** @return An observable value for the size of this collection */
	default ObservableValue<Integer> observeSize() {
		return reduce(TypeToken.of(Integer.TYPE), 0, (s, v) -> s + 1, (s, v) -> s - 1);
	}

	/**
	 * @return An observable that fires a change event whenever any elements in it are added, removed or changed. These changes are batched
	 *         by transaction when possible.
	 */
	default Observable<? extends CollectionChangeEvent<E>> changes() {
		return new ObservableCollectionImpl.CollectionChangesObservable<>(this);
	}

	/**
	 * @return An observable that fires a value (the {@link Causable#getRootCause() root cause} event of the change) whenever anything in
	 *         this collection changes. Unlike {@link #changes()}, this observable will only fire 1 event per transaction.
	 */
	default Observable<Object> simpleChanges() {
		return new Observable<Object>() {
			@Override
			public Subscription subscribe(Observer<Object> observer) {
				Object key = new Object();
				Subscription sub = ObservableCollection.this
					.onChange(evt -> evt.getRootCausable().onFinish(key, (root, values) -> observer.onNext(root)));
				return sub;
			}

			@Override
			public boolean isSafe() {
				return true;
			}
		};
	}

	// Observable containment

	/**
	 * @param value The value to observe in the collection
	 * @param defaultValue The default value for the result when the value is not found in the collection (typically <code>()->null</code>
	 * @param first Whether to observe the first or the last equivalent value in the collection
	 * @return An observable value whose content is the first or last value in the collection that is {@link #equivalence() equivalent} to
	 *         the given value
	 * @throws IllegalArgumentException If the given value may not be an element of this collection
	 */
	default ObservableValue<E> observeEquivalent(E value, Supplier<? extends E> defaultValue, boolean first) {
		return observeElement(value, first).map(getType(), el -> el != null ? el.get() : defaultValue.get());
	}

	/**
	 * @param value The value to observe in the collection
	 * @param defaultValue The default value for the result when the value is not found in the collection (typically <code>()->null</code>
	 * @param first Whether to observe the first or the last equivalent value in the collection
	 * @return An observable value whose content is the first or last value in the collection that is {@link #equivalence() equivalent} to
	 *         the given value
	 * @throws IllegalArgumentException If the given value may not be an element of this collection
	 */
	default ObservableValue<CollectionElement<? extends E>> observeElement(E value, boolean first) {
		return new ObservableCollectionImpl.ObservableEquivalentFinder<>(this, value, first);
	}

	/**
	 * @param test The test to find passing elements for
	 * @param def Supplies a default value for the observable result when no elements in this collection pass the test
	 * @param first true to always use the first element passing the test, false to always use the last element
	 * @return An observable value containing a value in this collection passing the given test
	 */
	default ObservableValue<E> observeFind(Predicate<? super E> test, Supplier<? extends E> def, boolean first) {
		return observeFind(test, def, Ternian.of(first));
	}

	/**
	 * @param test The test to find passing elements for
	 * @param def Supplies a default value for the observable result when no elements in this collection pass the test
	 * @param first {@link Ternian#TRUE} to always use the first element passing the test, {@link Ternian#FALSE} to always use the last
	 *        element, or {@link Ternian#NONE NONE} to use any passing element
	 * @return An observable value containing a value in this collection passing the given test
	 */
	default ObservableValue<E> observeFind(Predicate<? super E> test, Supplier<? extends E> def, Ternian first) {
		return new ObservableCollectionImpl.ObservableCollectionFinder<>(this, test, first).map(getType(),
			el -> el != null ? el.get() : def.get());
	}

	/**
	 * @param <X> The type of the value to test
	 * @param value The value to test
	 * @return An observable boolean whose value is whether this collection contains the given value, according to
	 *         {@link #equivalence()}.{@link Equivalence#elementEquals(Object, Object) elementEquals()}
	 */
	default <X> ObservableValue<Boolean> observeContains(ObservableValue<X> value) {
		return new ObservableCollectionImpl.ContainsValue<>(this, value);
	}

	/**
	 * @param <X> The type of the collection to test
	 * @param collection The collection to test
	 * @return An observable boolean whose value is whether this collection contains every element of the given collection, according to
	 *         {@link #equivalence()}.{@link Equivalence#elementEquals(Object, Object) elementEquals()}
	 */
	default <X> ObservableValue<Boolean> observeContainsAll(ObservableCollection<X> collection) {
		return new ObservableCollectionImpl.ContainsAllValue<>(this, collection);
	}

	/**
	 * @param <X> The type of the collection to test
	 * @param collection The collection to test
	 * @return An observable boolean whose value is whether this collection contains any element of the given collection, according to
	 *         {@link #equivalence()}.{@link Equivalence#elementEquals(Object, Object) elementEquals()}
	 */
	default <X> ObservableValue<Boolean> observeContainsAny(ObservableCollection<X> collection) {
		return new ObservableCollectionImpl.ContainsAnyValue<>(this, collection);
	}

	/** @return A builder that facilitates filtering, mapping, and other derivations of this collection's source data */
	default <T> CollectionDataFlow<E, E, E> flow() {
		return new ObservableCollectionDataFlowImpl.BaseCollectionDataFlow<>(this);
	}

	/** @return An observable value containing the only value in this collection while its size==1, otherwise null TODO TEST ME! */
	default ObservableValue<E> only() {
		return reduce(new int[1], (sz, v) -> {
			sz[0]++;
			return sz;
		}, (sz, v) -> {
			sz[0]--;
			return sz;
		}).map(getType(), sz -> sz[0] == 1 ? getFirst() : null);
	}

	/**
	 * Equivalent to {@link #reduce(Object, BiFunction, BiFunction)} with null for the remove function
	 *
	 * @param <T> The type of the reduced value
	 * @param init The seed value before the reduction
	 * @param reducer The reducer function to accumulate the values. Must be associative.
	 * @return The reduced value
	 */
	default <T> ObservableValue<T> reduce(T init, BiFunction<? super T, ? super E, T> reducer) {
		return reduce(init, reducer, null);
	}

	/**
	 * Equivalent to {@link #reduce(TypeToken, Object, BiFunction, BiFunction)} using the type derived from the reducer's return type
	 *
	 * @param <T> The type of the reduced value
	 * @param init The seed value before the reduction
	 * @param add The reducer function to accumulate the values. Must be associative.
	 * @param remove The de-reducer function to handle removal or replacement of values. This may be null, in which case removal or
	 *        replacement of values will result in the entire collection being iterated over for each subscription. Null here will have no
	 *        consequence if the result is never observed. Must be associative.
	 * @return The reduced value
	 */
	default <T> ObservableValue<T> reduce(T init, BiFunction<? super T, ? super E, T> add, BiFunction<? super T, ? super E, T> remove) {
		return reduce((TypeToken<T>) TypeToken.of(add.getClass()).resolveType(BiFunction.class.getTypeParameters()[2]), init, add, remove);
	}

	/**
	 * Reduces all values in this collection to a single value
	 *
	 * @param <T> The compile-time type of the reduced value
	 * @param type The run-time type of the reduced value
	 * @param init The seed value before the reduction
	 * @param add The reducer function to accumulate the values. Must be associative.
	 * @param remove The de-reducer function to handle removal or replacement of values. This may be null, in which case removal or
	 *        replacement of values will result in the entire collection being iterated over for each subscription. Null here will have no
	 *        consequence if the result is never observed. Must be associative.
	 * @return The reduced value
	 */
	default <T> ObservableValue<T> reduce(TypeToken<T> type, T init, BiFunction<? super T, ? super E, T> add,
		BiFunction<? super T, ? super E, T> remove) {
		return new ObservableCollectionImpl.ReducedValue<E, T, T>(this, type) {
			private final T RECALC = (T) new Object(); // Placeholder indicating that the value must be recalculated from scratch

			@Override
			public T get() {
				T ret = init;
				for (E element : ObservableCollection.this)
					ret = add.apply(ret, element);
				return ret;
			}

			@Override
			protected T init() {
				return init;
			}

			@Override
			protected T update(T oldValue, ObservableCollectionEvent<? extends E> change) {
				if (oldValue == RECALC)
					return oldValue;
				switch (change.getType()) {
				case add:
					oldValue = add.apply(oldValue, change.getNewValue());
					break;
				case remove:
					if (remove != null)
						oldValue = remove.apply(oldValue, change.getOldValue());
					else
						oldValue = RECALC;
					break;
				case set:
					if (remove != null) {
						oldValue = remove.apply(oldValue, change.getOldValue());
						oldValue = add.apply(oldValue, change.getNewValue());
					} else
						oldValue = RECALC;
				}
				return oldValue;
			}

			@Override
			protected T getValue(T updated) {
				if (updated == RECALC)
					return get();
				else
					return updated;
			}

			@Override
			public String toString() {
				return "reduce " + ObservableCollection.this;
			}
		};
	}

	/**
	 * @param compare The comparator to use to compare this collection's values
	 * @return An observable value containing the minimum of the values, by the given comparator
	 */
	default ObservableValue<E> minBy(Comparator<? super E> compare) {
		return reduce(getType(), null, (v1, v2) -> {
			if (v1 == null)
				return v2;
			else if (v2 == null)
				return v1;
			else if (compare.compare(v1, v2) <= 0)
				return v1;
			else
				return v2;
		}, null);
	}

	/**
	 * @param compare The comparator to use to compare this collection's values
	 * @return An observable value containing the maximum of the values, by the given comparator
	 */
	default ObservableValue<E> maxBy(Comparator<? super E> compare) {
		return reduce(getType(), null, (v1, v2) -> {
			if (v1 == null)
				return v2;
			else if (v2 == null)
				return v1;
			else if (compare.compare(v1, v2) >= 0)
				return v1;
			else
				return v2;
		}, null);
	}

	/**
	 * @param <E> The type for the collection
	 * @param type The type for the collection
	 * @param values The values to be in the immutable collection
	 * @return An immutable collection with the given values
	 */
	static <E> ObservableCollection<E> of(TypeToken<E> type, E... values) {
		return of(type, BetterList.of(values));
	}

	/**
	 * @param <E> The type for the root collection
	 * @param type The type for the root collection
	 * @param values The values to be in the immutable collection
	 * @return An immutable collection with the given values
	 */
	static <E> ObservableCollection<E> of(TypeToken<E> type, Collection<? extends E> values) {
		return of(type, BetterList.of(values));
	}

	/**
	 * @param <E> The type for the collection
	 * @param type The type for the collection
	 * @param values The values to be in the collection
	 * @return An immutable observable collection with the given contents
	 */
	static <E> ObservableCollection<E> of(TypeToken<E> type, BetterList<? extends E> values) {
		return new ObservableCollectionImpl.ConstantCollection<>(type, values);
	}

	/**
	 * @param <E> The type for the collection
	 * @param type The type for the collection
	 * @return A new, empty, mutable observable collection
	 */
	static <E> ObservableCollection<E> create(TypeToken<E> type) {
		return create(type, createDefaultBacking());
	}

	/**
	 * @param <E> The type for the collection
	 * @return A new list to back a collection created by {@link #create(TypeToken)}
	 */
	static <E> BetterList<E> createDefaultBacking() {
		return new BetterTreeList<>(true);
	}

	/**
	 * @param <E> The type for the collection
	 * @param type The type for the collection
	 * @param backing The list to hold the collection's data
	 * @return A new, empty, mutable observable collection whose performance and storage characteristics are determined by
	 *         <code> backing</code>
	 */
	static <E> ObservableCollection<E> create(TypeToken<E> type, BetterList<E> backing) {
		return new DefaultObservableCollection<>(type, backing);
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	static <E> ObservableCollection<E> flattenValue(ObservableValue<? extends ObservableCollection<E>> collectionObservable) {
		return new ObservableCollectionImpl.FlattenedValueCollection<>(collectionObservable);
	}

	/**
	 * @param <E> The super type of element in the collections
	 * @param innerType The type of elements in the result
	 * @param colls The collections to flatten
	 * @return A collection containing all elements of the given collections
	 */
	static <E> CollectionDataFlow<?, ?, E> flattenCollections(TypeToken<E> innerType, ObservableCollection<? extends E>... colls) {
		return of(new TypeToken<ObservableCollection<? extends E>>() {}, colls).flow().flatMap(innerType, c -> c.flow());
	}

	/**
	 * @param <T> The type of the folded observable
	 * @param coll The collection to fold
	 * @return An observable that is notified for every event on any observable in the collection
	 */
	static <T> Observable<T> fold(ObservableCollection<? extends Observable<T>> coll) {
		return new Observable<T>() {
			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				HashMap<ElementId, Subscription> subscriptions = new HashMap<>();
				return coll.subscribe(evt -> {
					switch (evt.getType()) {
					case add:
						subscriptions.put(evt.getElementId(), evt.getNewValue().subscribe(observer));
						break;
					case remove:
						subscriptions.remove(evt.getElementId()).unsubscribe();
						break;
					case set:
						if (evt.getOldValue() != evt.getNewValue()) {
							subscriptions.remove(evt.getElementId()).unsubscribe();
							subscriptions.put(evt.getElementId(), evt.getNewValue().subscribe(observer));
						}
						break;
					}
				}, true).removeAll();
			}

			@Override
			public boolean isSafe() {
				return false;
			}

			@Override
			public String toString() {
				return "fold(" + coll + ")";
			}
		};
	}

	/**
	 * A typical hashCode implementation for collections
	 *
	 * @param coll The collection to hash
	 * @return The hash code of the collection's contents
	 */
	static int hashCode(ObservableCollection<?> coll) {
		try (Transaction t = coll.lock(false, null)) {
			int hashCode = 1;
			for (Object e : coll)
				hashCode += e.hashCode();
			return hashCode;
		}
	}

	/**
	 * A typical equals implementation for collections
	 *
	 * @param coll The collection to test
	 * @param o The object to test the collection against
	 * @return Whether the two objects are equal
	 */
	static <E> boolean equals(ObservableCollection<E> coll, Object o) {
		if (!(o instanceof Collection))
			return false;
		Collection<?> c = (Collection<?>) o;

		try (Transaction t1 = coll.lock(false, null); Transaction t2 = Transactable.lock(c, false, null)) {
			Iterator<E> e1 = coll.iterator();
			Iterator<?> e2 = c.iterator();
			while (e1.hasNext() && e2.hasNext()) {
				E o1 = e1.next();
				Object o2 = e2.next();
				if (!coll.equivalence().elementEquals(o1, o2))
					return false;
			}
			return !(e1.hasNext() || e2.hasNext());
		}
	}

	/**
	 * A simple toString implementation for collections
	 *
	 * @param coll The collection to print
	 * @return The string representation of the collection's contents
	 */
	static String toString(ObservableCollection<?> coll) {
		StringBuilder ret = new StringBuilder("[");
		boolean first = true;
		try (Transaction t = coll.lock(false, null)) {
			for (Object value : coll) {
				if (!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
		}
		ret.append(']');
		return ret.toString();
	}

	/**
	 * Allows creation of a collection that uses a collection's data as its source, but filters, maps, or otherwise transforms the data
	 *
	 * @param <E> The type of the source collection
	 * @param <I> An intermediate type
	 * @param <T> The type of collection this flow may build
	 */
	interface CollectionDataFlow<E, I, T> {
		/** @return The type of collection this flow may build */
		TypeToken<T> getTargetType();

		Equivalence<? super T> equivalence();

		// Flow operations

		/** @return a {@link #supportsPassive() passive} flow consisting of this flow's elements, reversed */
		CollectionDataFlow<E, T, T> reverse();

		/**
		 * Filters some elements from the collection by value. The filtering is done dynamically, such that a change to an element may cause
		 * the element to be excluded or included in the collection.
		 *
		 * @param filter A filter function that returns null for elements to maintain in the collection, or a message indicating why the
		 *        element is excluded
		 * @return A {@link #supportsPassive() active} data flow capable of producing a collection that excludes certain elements from the input
		 */
		CollectionDataFlow<E, T, T> filter(Function<? super T, String> filter);

		/**
		 * Filters elements from this flow by type
		 *
		 * @param <X> The type for the new collection
		 * @param type The type to filter this collection by
		 * @return A {@link #supportsPassive() active} collection consisting only of elements in the source whose values are instances of
		 *         the given class
		 */
		default <X> CollectionDataFlow<E, ?, X> filter(Class<X> type) {
			return filter(value -> {
				if (type == null || type.isInstance(value))
					return null;
				else
					return MutableCollectionElement.StdMsg.BAD_TYPE;
			}).map(TypeToken.of(type), v -> (X) v);
		}

		/**
		 * Performs an intersection or exclusion operation. The result is {@link #supportsPassive() active}.
		 *
		 * @param <X> The type of the collection to filter with
		 * @param other The other collection to use to filter this flow's elements
		 * @param include If true, the resulting flow will be an intersection of this flow's elements with those of the other collection,
		 *        according to this flow's {@link ObservableCollection#equivalence()}. Otherwise the resulting flow will be all elements of
		 *        this flow that are <b>NOT</b> included in the other collection.
		 * @return The filtered data flow
		 */
		<X> CollectionDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include);

		/**
		 * @param equivalence The new {@link ObservableCollection#equivalence() equivalence} scheme for the derived collection to use
		 * @return A {@link #supportsPassive() passive} data flow capable of producing a collection that uses a different
		 *         {@link ObservableCollection#equivalence() equivalence} scheme to determine containment and perform other by-value
		 *         operations.
		 */
		CollectionDataFlow<E, T, T> withEquivalence(Equivalence<? super T> equivalence);

		/**
		 * @param refresh The observable to use to refresh the collection's values
		 * @return A {@link #supportsPassive() passive} data flow capable of producing a collection that fires updates on the source's values
		 *         whenever the given refresh observable fires
		 */
		CollectionDataFlow<E, T, T> refresh(Observable<?> refresh);

		/**
		 * Like {@link #refresh(Observable)}, but each element may use a different observable to refresh itself
		 *
		 * @param refresh The function to get observable to use to refresh individual values
		 * @return A {@link #supportsPassive() active} data flow capable of producing a collection that fires updates on the source's values
		 *         whenever each element's refresh observable fires
		 */
		CollectionDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh);

		/**
		 * @param <X> The type to map to
		 * @param target The type to map to
		 * @param map The mapping function to apply to each element
		 * @return The mapped flow
		 */
		default <X> CollectionDataFlow<E, T, X> map(TypeToken<X> target, Function<? super T, ? extends X> map) {
			return map(target, map, options -> {});
		}

		/**
		 * Allows elements to be transformed via a function. This operation may produce an {@link #supportsPassive() active or passive} flow
		 * depending on the options selected.
		 *
		 * @param <X> The type to map to
		 * @param target The type to map to
		 * @param map The mapping function to apply to each element
		 * @param options Allows various options to be selected that determine the behavior of the mapped set
		 * @return The mapped flow
		 */
		<X> CollectionDataFlow<E, T, X> map(TypeToken<X> target, Function<? super T, ? extends X> map, Consumer<MapOptions<T, X>> options);

		/**
		 * Combines each element of this flow the the value of one or more observable values. This operation may produce an
		 * {@link #supportsPassive() active or passive} flow depending on the options selected on the builder.
		 *
		 * @param <X> The type of the combined values
		 * @param targetType The type of the combined values
		 * @param combination The function to create the combination definition
		 * @return A data flow capable of producing a collection whose elements are each some combination of the source element and the
		 *         dynamic value of the observable
		 */
		<X> CollectionDataFlow<E, T, X> combine(TypeToken<X> targetType,
			Function<CombinationPrecursor<T, X>, CombinedFlowDef<T, X>> combination);

		/**
		 * @param target The target type
		 * @param map A function that produces observable values from each element of the source
		 * @return A {@link #supportsPassive() active} flow capable of producing a collection that is the value of the observable values mapped to
		 *         each element of the source.
		 */
		default <X> CollectionDataFlow<E, ?, X> flattenValues(TypeToken<X> target,
			Function<? super T, ? extends ObservableValue<? extends X>> map) {
			TypeToken<ObservableValue<? extends X>> valueType = new TypeToken<ObservableValue<? extends X>>() {}
			.where(new TypeParameter<X>() {}, target.wrap());
			return map(valueType, map).refreshEach(v -> v.changes().noInit()).map(target, obs -> obs == null ? null : obs.get(),
				options -> options//
				.withElementSetting((ov, newValue, doSet) -> {
					// Allow setting elements via the wrapped settable value
					if (!(ov instanceof SettableValue))
						return MutableCollectionElement.StdMsg.UNSUPPORTED_OPERATION;
					else if (newValue != null && !ov.getType().wrap().getRawType().isInstance(newValue))
						return MutableCollectionElement.StdMsg.BAD_TYPE;
					String msg = ((SettableValue<X>) ov).isAcceptable(newValue);
					if (msg != null)
						return msg;
					if (doSet)
						((SettableValue<X>) ov).set(newValue, null);
					return null;
				}));
		}

		/**
		 * @param target The type of values in the flattened result
		 * @param map The function to produce {@link ObservableCollection.CollectionDataFlow data flows} from each element in this flow
		 * @return A flow containing each element in the data flow produced by the map of each element in this flow
		 */
		<X> CollectionDataFlow<E, ?, X> flatMap(TypeToken<X> target,
			Function<? super T, ? extends CollectionDataFlow<?, ?, ? extends X>> map);

		/**
		 * @param compare The comparator to use to sort the source elements
		 * @return A {@link #supportsPassive() active} flow capable of producing a collection whose elements are sorted by the given comparison
		 *         scheme.
		 */
		CollectionDataFlow<E, T, T> sorted(Comparator<? super T> compare);

		/**
		 * @return A {@link #supportsPassive() active} flow capable of producing a set that excludes duplicate elements according to its
		 *         {@link ObservableCollection#equivalence() equivalence} scheme.
		 * @see #withEquivalence(Equivalence)
		 */
		default UniqueDataFlow<E, T, T> distinct() {
			return distinct(options -> {});
		}

		/**
		 * @param options Allows some customization of the behavior of collections collected from the unique flow
		 * @return A {@link #supportsPassive() active} flow capable of producing a set that excludes duplicate elements according to its
		 *         {@link ObservableCollection#equivalence() equivalence} scheme.
		 * @see #withEquivalence(Equivalence)
		 */
		UniqueDataFlow<E, T, T> distinct(Consumer<UniqueOptions> options);

		/**
		 * @param compare The comparator to use to sort the source elements
		 * @param alwaysUseFirst Whether to always use the first element in the collection to represent other equivalent values. If this is
		 *        false, the produced collection may be able to fire fewer events because elements that are added earlier in the collection
		 *        can be ignored if they are already represented.
		 * @return A {@link #supportsPassive() active} flow capable of producing a sorted set ordered by the given comparator that excludes
		 *         duplicate elements according to the comparator's {@link Equivalence#of(Class, Comparator, boolean) equivalence}.
		 */
		UniqueSortedDataFlow<E, T, T> distinctSorted(Comparator<? super T> compare, boolean alwaysUseFirst);

		/** @return A flow with the same data and properties as this flow, but whose collected results cannot be modified externally */
		default CollectionDataFlow<E, T, T> immutable() {
			return filterMod(options -> options.immutable(StdMsg.UNSUPPORTED_OPERATION, true));
		}

		/**
		 * Allows control of whether and how the produced collection may be modified. The produced collection will still reflect
		 * modifications made to the source collection.
		 *
		 * @param options A builder that determines what modifications may be performed on the resulting flow
		 * @return The mod-filtered flow
		 */
		CollectionDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options);

		/**
		 * @param <K> The key type for the map
		 * @param keyType The key type for the map
		 * @param keyMap The function to produce keys from this flow's values
		 * @return A multi-map flow that may be used to produce a multi-map of this flow's values, categorized by the given key mapping
		 */
		default <K> ObservableMultiMap.MultiMapFlow<K, T> groupBy(TypeToken<K> keyType, Function<? super T, ? extends K> keyMap) {
			return groupBy(keyType, keyMap, Equivalence.DEFAULT);
		}

		/**
		 * @param <K> The key type for the map
		 * @param keyType The key type for the map
		 * @param keyMap The function to produce keys from this flow's values
		 * @param keyEquivalence The equivalence set to be used for the key set's uniqueness
		 * @return A multi-map flow that may be used to produce a multi-map of this flow's values, categorized by the given key mapping
		 */
		<K> ObservableMultiMap.MultiMapFlow<K, T> groupBy(TypeToken<K> keyType, Function<? super T, ? extends K> keyMap,
			Equivalence<? super K> keyEquivalence);

		/**
		 * @param <K> The key type for the map
		 * @param keyType The key type for the map
		 * @param keyMap The function to produce keys from this flow's values
		 * @param keyCompare The comparator to sort the keys
		 * @return A sorted multi-map flow that may be used to produce a sorted multi-map of this flow's values, categorized by the given
		 *         key mapping
		 */
		<K> ObservableSortedMultiMap.SortedMultiMapFlow<K, T> groupBy(TypeToken<K> keyType, Function<? super T, ? extends K> keyMap,
			Comparator<? super K> keyCompare);

		// Terminal operations

		/**
		 * <p>
		 * Determines if this flow supports building passive collections via {@link #collect()}.
		 * </p>
		 *
		 * <p>
		 * A passive collection does not need to keep track of its own data, but rather performs per-access and per-operation
		 * transformations that delegate to the base collection. Because a passive collection maintains fewer resources, it may be more
		 * suitable for collections of unknown size that derived by light-weight operations, where the building of the derived collection of
		 * elements would be largely wasted.
		 * </p>
		 * <p>
		 * On the other hand, because active collections maintain all their elements at the ready, access is generally cheaper. And because
		 * the elements are maintained dynamically regardless of the number of subscriptions on the derived collection, multiple
		 * subscriptions may be cheaper.
		 * </p>
		 * <p>
		 * Many flow operations are active by nature, in that the operation is not stateless and requires extra book-keeping by the derived
		 * collection. Each method on {@link ObservableCollection.CollectionDataFlow} documents whether it is an active or passive
		 * operation.
		 * </p>
		 *
		 * @return Whether this data flow is capable of producing a passive collection
		 */
		boolean supportsPassive();

		/**
		 * @return Whether this data flow not only {@link #supportsPassive() supports passive} collection building, but will default to this
		 *         if collected using {@link #collect()}
		 */
		default boolean prefersPassive() {
			return supportsPassive();
		}

		/** @return A collection manager to be used by the active derived collection produced by {@link #collectActive(Observable)} */
		ActiveCollectionManager<E, ?, T> manageActive();

		/**
		 * @return A collection manager to be used by the passive derived collection produced by {@link #collectPassive()}. Will be null if
		 *         this collection is not {@link #supportsPassive() passive}.
		 */
		PassiveCollectionManager<E, ?, T> managePassive();

		/**
		 * @return A heavy-weight collection derived via this flow from the source collection
		 * @see #supportsPassive()
		 */
		default ObservableCollection<T> collect() {
			if (prefersPassive())
				return collectPassive();
			else
				return collectActive(Observable.empty);
		}

		/**
		 * @return A {@link #supportsPassive() passively-managed} collection derived via this flow from the source collection
		 * @throws UnsupportedOperationException If this flow does not support passive collection
		 */
		ObservableCollection<T> collectPassive() throws UnsupportedOperationException;

		/**
		 * @param until An observable that will kill the collection when it fires. May be used to control the release of unneeded resources
		 *        instead of relying on the garbage collector to dispose of them in its own time.
		 * @return An {@link #supportsPassive() actively-managed} collection derived via this flow from the source collection.
		 */
		ObservableCollection<T> collectActive(Observable<?> until);
	}

	/**
	 * A data flow that produces a set
	 *
	 * @param <E> The type of the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of collection this flow may build
	 */
	interface UniqueDataFlow<E, I, T> extends CollectionDataFlow<E, I, T> {
		@Override
		UniqueDataFlow<E, T, T> reverse();

		@Override
		UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter);

		@Override
		<X> UniqueDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include);

		@Override
		UniqueDataFlow<E, T, T> refresh(Observable<?> refresh);

		@Override
		UniqueDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh);

		/**
		 * @param <X> The type for the mapped flow
		 * @param target The type for the mapped flow
		 * @param map The mapping function for each source element's value
		 * @param reverse The mapping function to recover source values from mapped values--required for equivalence
		 * @return The mapped flow
		 * @see #mapEquivalent(TypeToken, Function, Function, Consumer)
		 */
		default <X> UniqueDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, Function<? super T, ? extends X> map,
			Function<? super X, ? extends T> reverse) {
			return mapEquivalent(target, map, reverse, options -> {});
		}

		/**
		 * <p>
		 * Same as {@link #map(TypeToken, Function, Consumer)}, but with the additional assertion that the produced mapped data will be
		 * one-to-one with the source data, such that the produced collection is unique in a similar way, without a need for an additional
		 * {@link #distinct(Consumer) uniqueness} check.
		 * </p>
		 * <p>
		 * This assertion cannot be checked (at compile time or run time), and if the assertion is incorrect such that multiple source
		 * values map to equivalent target values, <b>the resulting set will not be unique and data errors, including internal
		 * ObservableCollection errors, are possible</b>. Therefore caution should be used when considering whether to invoke this method.
		 * When in doubt, use {@link #map(TypeToken, Function, Consumer)} and {@link #distinct(Consumer)}.
		 * </p>
		 *
		 * @param <X> The type of the mapped values
		 * @param target The type of the mapped values
		 * @param map The function to produce result values from source values
		 * @param reverse The function to produce source values from result values--required to facilitate equivalence with the source flow
		 * @param options Allows customization of the behavior of the mapped set
		 * @return The mapped flow
		 */
		<X> UniqueDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, Function<? super T, ? extends X> map,
			Function<? super X, ? extends T> reverse, Consumer<MapOptions<T, X>> options);

		@Override
		default UniqueDataFlow<E, T, T> immutable() {
			return filterMod(options -> options.immutable(StdMsg.UNSUPPORTED_OPERATION, true));
		}

		@Override
		UniqueDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options);

		@Override
		default ObservableSet<T> collect() {
			return (ObservableSet<T>) CollectionDataFlow.super.collect();
		}

		@Override
		ObservableSet<T> collectPassive();

		@Override
		ObservableSet<T> collectActive(Observable<?> until);
	}

	/**
	 * A data flow that produces a sorted set
	 *
	 * @param <E> The type of the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of collection this flow may build
	 */
	interface UniqueSortedDataFlow<E, I, T> extends UniqueDataFlow<E, I, T> {
		Comparator<? super T> comparator();

		@Override
		UniqueSortedDataFlow<E, T, T> reverse();

		@Override
		UniqueSortedDataFlow<E, T, T> filter(Function<? super T, String> filter);

		@Override
		<X> UniqueSortedDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include);

		@Override
		UniqueSortedDataFlow<E, T, T> refresh(Observable<?> refresh);

		@Override
		UniqueSortedDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh);

		@Override
		default <X> UniqueSortedDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, Function<? super T, ? extends X> map,
			Function<? super X, ? extends T> reverse) {
			return mapEquivalent(target, map, reverse, options -> {});
		}

		/**
		 * @param <X> The type for the mapped flow
		 * @param target The type for the mapped flow
		 * @param map The mapping function to produce values from each source element
		 * @param compare The comparator to source the mapped values in the same order as the corresponding source values
		 * @return The mapped flow
		 */
		default <X> UniqueSortedDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, Function<? super T, ? extends X> map,
			Comparator<? super X> compare) {
			return mapEquivalent(target, map, compare, options -> {});
		}

		/**
		 * <p>
		 * Same as {@link #map(TypeToken, Function, Consumer)}, but with the additional assertion that the produced mapped data will be
		 * one-to-one with the source data, such that the produced collection is unique in a similar way, without a need for an additional
		 * {@link #distinctSorted(Comparator, boolean) uniqueness} check.
		 * </p>
		 * <p>
		 * This assertion cannot be checked (at compile time or run time), and if the assertion is incorrect such that multiple source
		 * values map to equivalent target values, <b>the resulting set will not be unique and data errors, including internal
		 * ObservableCollection errors, are possible</b>. Therefore caution should be used when considering whether to invoke this method.
		 * When in doubt, use {@link #map(TypeToken, Function, Consumer)} and {@link #distinctSorted(Comparator, boolean)}.
		 * </p>
		 *
		 * @param <X> The type of the mapped values
		 * @param target The type of the mapped values
		 * @return The mapped flow
		 */
		@Override
		<X> UniqueSortedDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, Function<? super T, ? extends X> map,
			Function<? super X, ? extends T> reverse, Consumer<MapOptions<T, X>> options);

		/**
		 * @param <X> The type for the mapped flow
		 * @param target The type for the mapped flow
		 * @param map The mapping function to produce values from each source element
		 * @param compare The comparator to source the mapped values in the same order as the corresponding source values
		 * @param options Allows customization for the behavior of the mapped flow
		 * @return The mapped flow
		 */
		<X> UniqueSortedDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, Function<? super T, ? extends X> map,
			Comparator<? super X> compare, Consumer<MapOptions<T, X>> options);

		@Override
		default UniqueSortedDataFlow<E, T, T> immutable() {
			return filterMod(options -> options.immutable(StdMsg.UNSUPPORTED_OPERATION, true));
		}

		@Override
		UniqueSortedDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options);

		@Override
		default ObservableSortedSet<T> collect() {
			return (ObservableSortedSet<T>) UniqueDataFlow.super.collect();
		}

		@Override
		ObservableSortedSet<T> collectPassive();

		@Override
		ObservableSortedSet<T> collectActive(Observable<?> until);
	}

	/**
	 * Supports modifying the values of elements in a collection via modification operations from a derived collection
	 *
	 * @param <I> The type of the source element
	 * @param <T> The type of the derived element
	 */
	@FunctionalInterface
	interface ElementSetter<I, T> {
		/**
		 * @param element The source value
		 * @param newValue The derived value
		 * @param replace Whether to actually do the replacement, as opposed to just testing whether it is possible/allowed
		 * @return Null if the replacement is possible/allowed/done; otherwise a string saying why it is not
		 */
		String setElement(I element, T newValue, boolean replace);
	}

	/**
	 * Allows creation of a collection that reflects a source collection's data, but may limit the operations the user can perform on the
	 * data or when the user can observe the data
	 *
	 * @param <T> The type of the collection to filter modification on
	 */
	class ModFilterBuilder<T> {
		private String theImmutableMsg;
		private boolean areUpdatesAllowed;
		private String theAddMsg;
		private String theRemoveMsg;
		private Function<? super T, String> theAddMsgFn;
		private Function<? super T, String> theRemoveMsgFn;

		public ModFilterBuilder() {
		}

		public String getImmutableMsg() {
			return theImmutableMsg;
		}

		public boolean areUpdatesAllowed() {
			return areUpdatesAllowed;
		}

		public String getAddMsg() {
			return theAddMsg;
		}

		public String getRemoveMsg() {
			return theRemoveMsg;
		}

		public Function<? super T, String> getAddMsgFn() {
			return theAddMsgFn;
		}

		public Function<? super T, String> getRemoveMsgFn() {
			return theRemoveMsgFn;
		}

		public ModFilterBuilder<T> immutable(String modMsg, boolean allowUpdates) {
			theImmutableMsg = modMsg;
			areUpdatesAllowed = allowUpdates;
			return this;
		}

		public ModFilterBuilder<T> noAdd(String modMsg) {
			theAddMsg = modMsg;
			return this;
		}

		public ModFilterBuilder<T> noRemove(String modMsg) {
			theRemoveMsg = modMsg;
			return this;
		}

		public ModFilterBuilder<T> filterAdd(Function<? super T, String> messageFn) {
			theAddMsgFn = messageFn;
			return this;
		}

		public ModFilterBuilder<T> filterRemove(Function<? super T, String> messageFn) {
			theRemoveMsgFn = messageFn;
			return this;
		}
	}
}
