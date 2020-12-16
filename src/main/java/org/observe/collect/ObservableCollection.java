package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.Transformation;
import org.observe.Transformation.ReversibleTransformation;
import org.observe.Transformation.ReversibleTransformationPrecursor;
import org.observe.TypedValueContainer;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.FlowOptions.UniqueOptions;
import org.observe.collect.ObservableCollectionActiveManagers.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionActiveManagers.ActiveValueStoredManager;
import org.observe.collect.ObservableCollectionPassiveManagers.PassiveCollectionManager;
import org.observe.util.ObservableUtils;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Lockable;
import org.qommons.Ternian;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeList;

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
 * {@link CollectionDataFlow#transform(TypeToken, Function) transform}, {@link CollectionDataFlow#filter(Function) filter},
 * {@link CollectionDataFlow#distinct(Consumer) unique}, {@link CollectionDataFlow#sorted(Comparator) sort},
 * {@link CollectionDataFlow#combine(TypeToken, Function) combination} or other operations on the elements of the source. Collections so
 * derived from a source collection are themselves observable and reflect changes to the source. The derived collection may also be mutable,
 * with modifications to the derived collection affecting the source.</li>
 * <li><b>Modification Control</b> The {@link #flow() flow} API also supports constraints on how or whether a derived collection may be
 * {@link CollectionDataFlow#filterMod(Consumer) modified}.</li>
 * <li><b>Transactionality</b> ObservableCollections support the {@link org.qommons.Transactable} interface, allowing callers to reserve a
 * collection for write or to ensure that the collection is not written to during an operation (for implementations that support this. See
 * {@link org.qommons.Transactable#isLockSupported() isLockSupported()}).</li>
 * <li><b>Run-time type safety</b> ObservableCollections have a {@link #getType() type} associated with them, allowing them to enforce
 * type-safety at run time. How strictly this type-safety is enforced is implementation-dependent.</li>
 * <li><b>Custom {@link #equivalence() equivalence}</b> Instead of being a slave to each element's own {@link Object#equals(Object) equals}
 * scheme, collections can be defined with custom schemes which will affect any operations involving element comparison, such as
 * {@link #contains(Object)} and {@link #remove()}.</li>
 * <li><b>Enhanced element access</b> The {@link #getElement(Object, boolean) getElement} and {@link #mutableElement(ElementId)
 * mutableElement} methods, along with several others, allow access to elements in the array without the need and potentially without the
 * performance cost of iterating.</li>
 * </ul>
 *
 * @param <E> The type of element in the collection
 */
public interface ObservableCollection<E> extends BetterList<E>, TypedValueContainer<E> {
	/** This class's wildcard {@link TypeToken} */
	static TypeToken<ObservableCollection<?>> TYPE = TypeTokens.get().keyFor(ObservableCollection.class).wildCard();

	/**
	 * It is illegal to attempt to modify a collection (or even fire an update event on it) as a result of a currently executing
	 * modification (or update) to the collection. If such an attempt is made (and the implementation is able detect it), an
	 * {@link IllegalStateException} will be thrown with this {@link IllegalStateException#getMessage() message}.
	 */
	String REENTRANT_EVENT_ERROR = "A collection may not be modified as a result of a change event";

	// Additional contract methods

	/** @return The type of elements in this collection */
	@Override
	TypeToken<E> getType();

	@Override
	default boolean belongs(Object value) {
		if (!equivalence().isElement(value))
			return false;
		TypeToken<E> type = getType();
		if (value == null)
			return !type.isPrimitive();
		else if (!TypeTokens.get().isInstance(getType(), value))
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
				return false;
			try (Transaction t = lock(false, null)) {
				if (c.size() < size()) {
					for (Object o : c)
						if (contains(o))
							return true;
					return false;
				} else {
					if (c.isEmpty())
						return false;
					Set<E> cSet = ObservableCollectionImpl.toSet(this, equivalence(), c, null);
					CollectionElement<E> el = getTerminalElement(true);
					while (el != null) {
						if (cSet.contains(el.get()))
							return true;
						el = getAdjacentElement(el.getElementId(), true);
					}
					return false;
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
					boolean[] excluded = new boolean[1];
					Set<E> cSet = ObservableCollectionImpl.toSet(this, equivalence(), c, excluded);
					if (excluded[0])
						return false;
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
			ObservableUtils.populateValues(this, observer, forward);
			// Subscribe changes
			changeSub = onChange(observer);
		}
		return removeAll -> {
			try (Transaction t = lock(false, null)) {
				// Unsubscribe changes
				changeSub.unsubscribe();
				if (removeAll) {
					// Remove events
					// Remove elements in reverse order from how they were subscribed
					ObservableUtils.depopulateValues(this, observer, !forward);
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
			array = (E[]) java.lang.reflect.Array.newInstance(TypeTokens.get().wrap(TypeTokens.getRawType(getType())), size());
			return toArray(array);
		}
	}

	@Override
	default void removeRange(int fromIndex, int toIndex) {
		// Because of the possibility of cascading operations (e.g. with flattened collections that re-use multiple component collections),
		// this operation has to be done a little differently
		if (fromIndex == toIndex)
			return;
		else if (toIndex < fromIndex)
			throw new IndexOutOfBoundsException(fromIndex + " to " + toIndex);
		try (Transaction t = lock(true, null)) {
			if (fromIndex == size())
				return;
			ElementId[] next = new ElementId[] { getElement(fromIndex).getElementId() };
			ElementId[] end = new ElementId[] { toIndex < size() ? getElement(toIndex).getElementId() : null };
			try (Subscription sub = onChange(evt -> {
				if (evt.getType() == CollectionChangeType.remove) {
					if (evt.getElementId().equals(next[0]))
						next[0] = CollectionElement.getElementId(getAdjacentElement(next[0], true));
					if (evt.getElementId().equals(end[0]))
						end[0] = CollectionElement.getElementId(getAdjacentElement(end[0], true));
				}
			})) {
				while (next[0] != null && (end[0] == null || next[0].compareTo(end[0]) < 0)) {
					MutableCollectionElement<E> mutableEl = mutableElement(next[0]);
					next[0] = CollectionElement.getElementId(getAdjacentElement(next[0], true));
					if (mutableEl.canRemove() == null)
						mutableEl.remove();
				}
			}
		}
	}

	@Override
	default <T> T[] toArray(T[] a) {
		ArrayList<E> ret;
		try (Transaction t = lock(false, null)) {
			if (size() > a.length) { // Don't want to mess with this--let ArrayList do it
				ret = new ArrayList<>(size());
				ret.addAll(this);
				return ret.toArray(a);
			} else {
				CollectionElement<E> el = getTerminalElement(true);
				for (int index = 0; el != null; index++, el = getAdjacentElement(el.getElementId(), true))
					a[index] = (T) el.get();
				return a;
			}
		}
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
		return new ObservableCollectionImpl.ReducedValue<E, Integer, Integer>(this, TypeToken.of(Integer.TYPE)) {
			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(getCollection().getIdentity(), "size");
			}

			@Override
			public long getStamp() {
				return getCollection().getStamp();
			}

			@Override
			public Integer get() {
				return getCollection().size();
			}

			@Override
			protected Integer init() {
				return getCollection().size();
			}

			@Override
			protected Integer update(Integer oldValue, ObservableCollectionEvent<? extends E> change) {
				switch (change.getType()) {
				case add:
					return oldValue + 1;
				case remove:
					return oldValue - 1;
				case set:
					break;
				}
				return oldValue;
			}

			@Override
			protected Integer getValue(Integer updated) {
				return updated;
			}
		};
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
		class SimpleChanges extends AbstractIdentifiable implements Observable<Object> {
			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(ObservableCollection.this.getIdentity(), "simpleChanges");
			}

			@Override
			public Subscription subscribe(Observer<Object> observer) {
				Causable.CausableKey key = Causable.key((root, values) -> {
					observer.onNext(root);
				});
				Subscription sub = ObservableCollection.this.onChange(evt -> {
					evt.getRootCausable().onFinish(key);
				});
				return sub;
			}

			@Override
			public boolean isSafe() {
				return ObservableCollection.this.isLockSupported();
			}

			@Override
			public Transaction lock() {
				return ObservableCollection.this.lock(false, null);
			}

			@Override
			public Transaction tryLock() {
				return ObservableCollection.this.tryLock(false, null);
			}
		}
		return new SimpleChanges();
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
	default ObservableElement<E> observeElement(E value, boolean first) {
		if (!belongs(value))
			return ObservableElement.empty(getType());
		return new ObservableCollectionImpl.ObservableEquivalentFinder<>(this, value, first);
	}

	/**
	 * @param test The test to find passing elements for
	 * @param def Supplies a default value for the observable result when no elements in this collection pass the test
	 * @param first true to always use the first element passing the test, false to always use the last element
	 * @return An observable value containing a value in this collection passing the given test
	 */
	default ObservableFinderBuilder<E> observeFind(Predicate<? super E> test) {
		return new ObservableFinderBuilder<>(this, test);
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

	/** @return An observable value containing the only value in this collection while its size==1, otherwise null */
	default SettableElement<E> only() {
		return new ObservableCollectionImpl.OnlyElement<>(this);
	}

	/**
	 * Equivalent to {@link #reduce(Object, BiFunction, BiFunction)} with null for the remove function
	 *
	 * @param <T> The type of the reduced value
	 * @param seed The seed value before the reduction
	 * @param reducer The reducer function to accumulate the values. Must be associative.
	 * @return The reduced value
	 */
	default <T> ObservableValue<T> reduce(T seed, BiFunction<? super T, ? super E, T> reducer) {
		return reduce(seed, reducer, null);
	}

	/**
	 * Equivalent to {@link #reduce(TypeToken, Object, BiFunction, BiFunction)} using the type derived from the reducer's return type
	 *
	 * @param <T> The type of the reduced value
	 * @param seed The seed value before the reduction
	 * @param add The reducer function to accumulate the values. Must be associative.
	 * @param remove The de-reducer function to handle removal or replacement of values. This may be null, in which case removal or
	 *        replacement of values will result in the entire collection being iterated over for each subscription. Null here will have no
	 *        consequence if the result is never observed. Must be associative.
	 * @return The reduced value
	 */
	default <T> ObservableValue<T> reduce(T seed, BiFunction<? super T, ? super E, T> add, BiFunction<? super T, ? super E, T> remove) {
		return reduce((TypeToken<T>) TypeToken.of(add.getClass()).resolveType(BiFunction.class.getTypeParameters()[2]), seed, add, remove);
	}

	/**
	 * Reduces all values in this collection to a single value
	 *
	 * @param <T> The compile-time type of the reduced value
	 * @param type The run-time type of the reduced value
	 * @param seed The seed value before the reduction
	 * @param add The reducer function to accumulate the values. Must be associative.
	 * @param remove The de-reducer function to handle removal or replacement of values. This may be null, in which case removal or
	 *        replacement of values will result in the entire collection being iterated over for each subscription. Null here will have no
	 *        consequence if the result is never observed. Must be associative.
	 * @return The reduced value
	 */
	default <T> ObservableValue<T> reduce(TypeToken<T> type, T seed, BiFunction<? super T, ? super E, T> add,
		BiFunction<? super T, ? super E, T> remove) {
		return new ObservableCollectionImpl.ReducedValue<E, T, T>(this, type) {
			private final T RECALC = (T) new Object(); // Placeholder indicating that the value must be recalculated from scratch

			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(getCollection().getIdentity(), "reduce", seed, add, remove);
			}

			@Override
			public T get() {
				T ret = seed;
				for (E element : ObservableCollection.this)
					ret = add.apply(ret, element);
				return ret;
			}

			@Override
			protected T init() {
				T value = seed;
				for (E v : getCollection())
					value = add.apply(value, v);
				return value;
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
		};
	}

	/**
	 * @param compare The comparator to use to compare this collection's values
	 * @param def The default value to use with an empty collection
	 * @param first Whether to choose the first (Ternian#TRUE TRUE), last (Ternian#FALSE FALSE), or any (Ternian#NONE NONE) element in a tie
	 * @return An observable value containing the minimum of the values, by the given comparator
	 */
	default ObservableElement<E> minBy(Comparator<? super E> compare, Supplier<? extends E> def, Ternian first) {
		return new ObservableCollectionImpl.BestCollectionElement<>(this, compare, def, first, null);
	}

	/**
	 * @param compare The comparator to use to compare this collection's values
	 * @param def The default value to use with an empty collection
	 * @param first Whether to choose the first (Ternian#TRUE TRUE), last (Ternian#FALSE FALSE), or any (Ternian#NONE NONE) element in a tie
	 * @return An observable value containing the maximum of the values, by the given comparator
	 */
	default ObservableElement<E> maxBy(Comparator<? super E> compare, Supplier<? extends E> def, Ternian first) {
		return minBy(compare.reversed(), def, first);
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
	 * @param <E> The type for the collection
	 * @param type The type for the collection
	 * @param values The values to be in the immutable collection
	 * @return An immutable collection with the given values
	 */
	static <E> ObservableCollection<E> of(Class<E> type, E... values) {
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
	 * @param <E> The type for the root collection
	 * @param type The type for the root collection
	 * @param values The values to be in the immutable collection
	 * @return An immutable collection with the given values
	 */
	static <E> ObservableCollection<E> of(Class<E> type, Collection<? extends E> values) {
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
	 * @param values The values to be in the collection
	 * @return An immutable observable collection with the given contents
	 */
	static <E> ObservableCollection<E> of(Class<E> type, BetterList<? extends E> values) {
		return of(TypeTokens.get().of(type), values);
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
	 * @param type The type for the collection
	 * @return A builder for a new, empty, mutable, observable collection
	 */
	static <E> ObservableCollectionBuilder<E, ?> build(TypeToken<E> type) {
		return DefaultObservableCollection.build(type);
	}

	/**
	 * @param <E> The type for the collection
	 * @param type The type for the collection
	 * @return A builder for a new, empty, mutable, observable collection
	 */
	static <E> ObservableCollectionBuilder<E, ?> build(Class<E> type) {
		return build(TypeTokens.get().of(type));
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
	static <E> ObservableCollection<E> flattenValue(ObservableValue<? extends ObservableCollection<? extends E>> collectionObservable) {
		return flattenValue(collectionObservable, Equivalence.DEFAULT);
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @param equivalence The equivalence for the collection
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	static <E> ObservableCollection<E> flattenValue(ObservableValue<? extends ObservableCollection<? extends E>> collectionObservable,
		Equivalence<Object> equivalence) {
		return new ObservableCollectionImpl.FlattenedValueCollection<>(collectionObservable, equivalence);
	}

	/**
	 * @param <E> The super type of element in the collections
	 * @param innerType The type of elements in the result
	 * @param colls The collections to flatten
	 * @return A collection containing all elements of the given collections
	 */
	static <E> CollectionDataFlow<?, ?, E> flattenCollections(TypeToken<E> innerType, ObservableCollection<? extends E>... colls) {
		return of(TypeTokens.get().keyFor(ObservableCollection.class).parameterized(innerType), colls).flow().flatMap(innerType,
			LambdaUtils.printableFn(ObservableCollection::flow, "flow", "flow"));
	}

	/**
	 * @param <T> The type of the folded observable
	 * @param coll The collection to fold
	 * @return An observable that is notified for every event on any observable in the collection
	 */
	static <T> Observable<T> fold(ObservableCollection<? extends Observable<? extends T>> coll) {
		class FoldedCollectionObservable extends AbstractIdentifiable implements Observable<T> {
			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(coll.getIdentity(), "fold");
			}

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
			public Transaction lock() {
				return Lockable.lockAll(Lockable.lockable(coll), coll);
			}

			@Override
			public Transaction tryLock() {
				return Lockable.tryLockAll(Lockable.lockable(coll), coll);
			}
		}
		return new FoldedCollectionObservable();
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
	 * Allows creation of a collection that uses a collection's data as its source, but filters, maps, or otherwise transforms the data
	 *
	 * @param <E> The type of the source collection
	 * @param <I> An intermediate type
	 * @param <T> The type of collection this flow may build
	 */
	interface CollectionDataFlow<E, I, T> extends Identifiable {
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
			return filter(LambdaUtils.printableFn(value -> {
				if (type == null || type.isInstance(value))
					return null;
				else
					return MutableCollectionElement.StdMsg.BAD_TYPE;
			}, type.getName(), type)).transform(TypeTokens.get().of(type), tx -> tx.cache(false).map(v -> (X) v).withReverse(v -> (T) v));
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
		 * Transforms each value in this flow to a new value by some function, possibly including other values. This operation may produce
		 * an {@link #supportsPassive() active or passive} flow depending on the options selected on the builder.
		 *
		 * @param <X> The target type of the transformed flow
		 * @param target The target type of the transformed flow
		 * @param transform Configures the transformation
		 * @return The transformed flow
		 * @see Transformation for help using the API
		 */
		<X> CollectionDataFlow<E, T, X> transform(TypeToken<X> target, //
			Function<ReversibleTransformationPrecursor<T, X, ?>, Transformation<T, X>> transform);

		/**
		 * Transforms each value in this flow to a new value by some function, possibly including other values. This operation may produce
		 * an {@link #supportsPassive() active or passive} flow depending on the options selected on the builder.
		 *
		 * @param <X> The target type of the transformed flow
		 * @param target The target type of the transformed flow
		 * @param transform Configures the transformation
		 * @return The transformed flow
		 * @see Transformation for help using the API
		 */
		default <X> CollectionDataFlow<E, T, X> transform(Class<X> target, //
			Function<ReversibleTransformationPrecursor<T, X, ?>, Transformation<T, X>> transform) {
			return transform(TypeTokens.get().of(target), transform);
		}

		/**
		 * @param <X> The type to map to
		 * @param target The type to map to
		 * @param map The mapping function to apply to each element
		 * @return The mapped flow
		 */
		default <X> CollectionDataFlow<E, T, X> map(TypeToken<X> target, Function<? super T, ? extends X> map) {
			return transform(target, tx -> tx.map(map));
		}

		/**
		 * @param <X> The type to map to
		 * @param target The type to map to
		 * @param map The mapping function to apply to each element
		 * @return The mapped flow
		 */
		default <X> CollectionDataFlow<E, T, X> map(Class<X> target, Function<? super T, ? extends X> map) {
			return transform(target, tx -> tx.map(map));
		}

		/**
		 * Combines each element of this flow the the value of one or more observable values. This operation may produce an
		 * {@link #supportsPassive() active or passive} flow depending on the options selected on the builder.
		 *
		 * @param <X> The type of the combined values
		 * @param targetType The type of the combined values
		 * @param combination The function to create the combination definition
		 * @return A data flow capable of producing a collection whose elements are each some combination of the source element and the
		 *         dynamic value of the observable
		 * @see Transformation for help using the API
		 */
		default <X> CollectionDataFlow<E, T, X> combine(TypeToken<X> targetType,
			Function<ReversibleTransformationPrecursor<T, X, ?>, Transformation<T, X>> combination) {
			return transform(targetType, combination);
		}

		/**
		 * @param target The target type
		 * @param map A function that produces observable values from each element of the source
		 * @return A {@link #supportsPassive() active} flow capable of producing a collection that is the value of the observable values
		 *         mapped to each element of the source.
		 */
		<X> CollectionDataFlow<E, ?, X> flattenValues(TypeToken<X> target, Function<? super T, ? extends ObservableValue<? extends X>> map);

		/**
		 * @param target The target type
		 * @param map A function that produces observable values from each element of the source
		 * @return A {@link #supportsPassive() active} flow capable of producing a collection that is the value of the observable values
		 *         mapped to each element of the source.
		 */
		default <X> CollectionDataFlow<E, ?, X> flattenValues(Class<X> target,
			Function<? super T, ? extends ObservableValue<? extends X>> map) {
			return flattenValues(TypeTokens.get().of(target), map);
		}

		/**
		 * @param target The type of values in the flattened result
		 * @param map The function to produce {@link ObservableCollection.CollectionDataFlow data flows} from each element in this flow
		 * @return A flow containing each element in the data flow produced by the map of each element in this flow
		 */
		<X> CollectionDataFlow<E, ?, X> flatMap(TypeToken<X> target,
			Function<? super T, ? extends CollectionDataFlow<?, ?, ? extends X>> map);

		/**
		 *
		 * @param target The type of values in the flattened result
		 * @param map The function to produce {@link ObservableCollection.CollectionDataFlow data flows} from each element in this flow
		 * @return A flow containing each element in the data flow produced by the map of each element in this flow
		 */
		default <X> CollectionDataFlow<E, ?, X> flatMap(Class<X> target,
			Function<? super T, ? extends CollectionDataFlow<?, ?, ? extends X>> map) {
			return flatMap(TypeTokens.get().of(target), map);
		}

		/**
		 * @param target The type of values in the flattened result
		 * @param map The function to produce {@link ObservableCollection.CollectionDataFlow data flows} from each element in this flow
		 * @param options The options to use to combine the source element with each element in the mapped flow to produce the target values
		 * @return A flow containing each element in the data flow produced by the map of each element in this flow
		 */
		<V, X> CollectionDataFlow<E, ?, X> flatMap(TypeToken<X> target,
			Function<? super T, ? extends CollectionDataFlow<?, ?, ? extends V>> map,
				Function<FlatMapOptions<T, V, X>, FlatMapOptions.FlatMapDef<T, V, X>> options);

		/**
		 * @param target The type of values in the flattened result
		 * @param other The flow to combine with this one
		 * @param options The options to use to combine the elements of this flow and the other to produce the target values
		 * @return A flow containing one element for each combination of an element from this flow and an element from the other flow. The
		 *         resulting flow will have a number of elements equal to that of this flow times that of the other
		 */
		default <V, X> CollectionDataFlow<E, ?, X> cross(TypeToken<X> target, CollectionDataFlow<?, ?, ? extends V> other,
			Function<FlatMapOptions<T, V, X>, FlatMapOptions.FlatMapDef<T, V, X>> options) {
			// Don't allow structural modifications to crossed collections, as they have lots of side effects
			String noModMsg = "Crossed collections cannot be structurally modified";
			return flatMap(target, v -> other, options).filterMod(f -> f.noAdd(noModMsg).noRemove(noModMsg).noMove(noModMsg));
		}

		/**
		 * @param compare The comparator to use to sort the source elements
		 * @return A {@link #supportsPassive() active} flow capable of producing a collection whose elements are sorted by the given comparison
		 *         scheme.
		 */
		SortedDataFlow<E, T, T> sorted(Comparator<? super T> compare);

		/**
		 * @return A {@link #supportsPassive() active} flow capable of producing a set that excludes duplicate elements according to its
		 *         {@link ObservableCollection#equivalence() equivalence} scheme.
		 * @see #withEquivalence(Equivalence)
		 */
		default DistinctDataFlow<E, T, T> distinct() {
			return distinct(options -> {});
		}

		/**
		 * @param options Allows some customization of the behavior of collections collected from the unique flow
		 * @return A {@link #supportsPassive() active} flow capable of producing a set that excludes duplicate elements according to its
		 *         {@link ObservableCollection#equivalence() equivalence} scheme.
		 * @see #withEquivalence(Equivalence)
		 */
		DistinctDataFlow<E, T, T> distinct(Consumer<UniqueOptions> options);

		/**
		 * @param compare The comparator to use to sort the source elements
		 * @param alwaysUseFirst Whether to always use the first element in the collection to represent other equivalent values. If this is
		 *        false, the produced collection may be able to fire fewer events because elements that are added earlier in the collection
		 *        can be ignored if they are already represented.
		 * @return A {@link #supportsPassive() active} flow capable of producing a sorted set ordered by the given comparator that excludes
		 *         duplicate elements according to the comparator's {@link Equivalence#sorted(Class, Comparator, boolean) equivalence}.
		 */
		DistinctSortedDataFlow<E, T, T> distinctSorted(Comparator<? super T> compare, boolean alwaysUseFirst);

		/**
		 * @return A flow with the same data and properties as this flow, but whose collected results cannot be modified externally (with
		 *         the exception of updates, which are allowed)
		 */
		default CollectionDataFlow<E, T, T> unmodifiable() {
			return filterMod(options -> options.unmodifiable(StdMsg.UNSUPPORTED_OPERATION, true));
		}

		/**
		 * @return A flow with the same data and properties as this flow, but whose collected results cannot be modified externally
		 * @param allowUpdates Whether the collected results should allow updates
		 */
		default CollectionDataFlow<E, T, T> unmodifiable(boolean allowUpdates) {
			return filterMod(options -> options.unmodifiable(StdMsg.UNSUPPORTED_OPERATION, allowUpdates));
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
		 * @param reverse A function to produce a value for this flow from a given key and value. Supplying this function allows additions
		 *        into the gathered multi-map.
		 * @return A multi-map flow that may be used to produce a multi-map of this flow's values, categorized by the given key mapping
		 */
		default <K> ObservableMultiMap.MultiMapFlow<K, T> groupBy(TypeToken<K> keyType, Function<? super T, ? extends K> keyMap,
			BiFunction<K, T, T> reverse) {
			return groupBy(flow -> flow.map(keyType, keyMap).distinct(), reverse);
		}

		/**
		 * @param <K> The key type for the map
		 * @param keyType The key type for the map
		 * @param keyMap The function to produce keys from this flow's values
		 * @param reverse A function to produce a value for this flow from a given key and value. Supplying this function allows additions
		 *        into the gathered multi-map.
		 * @return A multi-map flow that may be used to produce a multi-map of this flow's values, categorized by the given key mapping
		 */
		default <K> ObservableMultiMap.MultiMapFlow<K, T> groupBy(Class<K> keyType, Function<? super T, ? extends K> keyMap,
			BiFunction<K, T, T> reverse) {
			return groupBy(TypeTokens.get().of(keyType), keyMap, reverse);
		}

		/**
		 * @param <K> The key type for the map
		 * @param keyType The key type for the map
		 * @param keyMap The function to produce keys from this flow's values
		 * @param keySorting The ordering for the key set
		 * @param reverse A function to produce a value for this flow from a given key and value. Supplying this function allows additions
		 *        into the gathered multi-map.
		 * @return A sorted multi-map flow that may be used to produce a multi-map of this flow's values, categorized by the given key
		 *         mapping
		 */
		default <K> ObservableSortedMultiMap.SortedMultiMapFlow<K, T> groupBy(TypeToken<K> keyType, Function<? super T, ? extends K> keyMap,
			Comparator<? super K> keySorting, BiFunction<K, T, T> reverse) {
			return groupSorted(flow -> flow.map(keyType, keyMap).distinctSorted(keySorting, false), reverse);
		}

		/**
		 * @param <K> The key type for the map
		 * @param keyType The key type for the map
		 * @param keyMap The function to produce keys from this flow's values
		 * @param keySorting The ordering for the key set
		 * @param reverse A function to produce a value for this flow from a given key and value. Supplying this function allows additions
		 *        into the gathered multi-map.
		 * @return A sorted multi-map flow that may be used to produce a multi-map of this flow's values, categorized by the given key
		 *         mapping
		 */
		default <K> ObservableSortedMultiMap.SortedMultiMapFlow<K, T> groupBy(Class<K> keyType, Function<? super T, ? extends K> keyMap,
			Comparator<? super K> keySorting, BiFunction<K, T, T> reverse) {
			return groupBy(TypeTokens.get().of(keyType), keyMap, keySorting, reverse);
		}

		<K> ObservableMultiMap.MultiMapFlow<K, T> groupBy(Function<? super CollectionDataFlow<E, I, T>, DistinctDataFlow<E, ?, K>> keyFlow,
			BiFunction<K, T, T> reverse);

		<K> ObservableSortedMultiMap.SortedMultiMapFlow<K, T> groupSorted(
			Function<? super CollectionDataFlow<E, I, T>, DistinctSortedDataFlow<E, ?, K>> keyFlow, BiFunction<K, T, T> reverse);

		// Terminal operations

		/**
		 * <p>
		 * Determines if this flow supports building passive collections via {@link #collectPassive()}.
		 * </p>
		 *
		 * <p>
		 * A passive collection does not need to keep close track of its own data, but rather performs per-access and per-operation
		 * transformations that delegate to the base collection. Because a passive collection maintains fewer resources, it may be more
		 * suitable for collections of large or unknown size derived by light-weight operations, where building the derived collection of
		 * elements would be largely wasted. Passive collections also keep no subscriptions to either their source collection or to any
		 * sources of change in the flow except when the passive collection itself is subscribed to. This allows them to be created and
		 * handed off without any accountability.
		 * </p>
		 * <p>
		 * On the other hand, because active collections maintain all their elements at the ready, access is generally cheaper. And because
		 * the elements are maintained dynamically regardless of the number of subscriptions on the derived collection, multiple
		 * subscriptions may be cheaper. Because they maintain subscriptions to the source and flow all the time, actively-derived
		 * collections are best created using the {@link #collectActive(Observable)} method with an observable to tell the collection it is
		 * not needed anymore. Actively-derived collections are created in such a way that they may be garbage collected if no strong
		 * references to them exist and no subscriptions to them remain. If this happens, the subscriptions to the source and flow will be
		 * released.
		 * </p>
		 * <p>
		 * Many flow operations are active by nature, in that the operation is not stateless and requires extra book-keeping by the derived
		 * collection. Each method on {@link ObservableCollection.CollectionDataFlow} documents whether it is an active or passive
		 * operation.
		 * </p>
		 * <p>
		 * In particular, passively-derived collections always have elements that are one-to-one with the elements in the source collection.
		 * Elements also cannot be arbitrarily reordered, though they can be ordered in {@link PassiveCollectionManager#isReversed()
		 * reverse} from the source. In general, operations which satisfy these requirements can be passive. Any flow can be collected
		 * actively.
		 * </p>
		 *
		 * @return Whether this data flow is capable of producing a passive collection
		 */
		boolean supportsPassive();

		/**
		 * @return Whether this data flow not only {@link #supportsPassive() supports passive} collection building, but will default to this
		 *         if collected using the general {@link #collect()} method.
		 */
		default boolean prefersPassive() {
			return supportsPassive();
		}

		/**
		 * This is called internally by the API and will not typically be used externally
		 *
		 * @return A collection manager to be used by the active derived collection produced by {@link #collectActive(Observable)}
		 */
		ActiveCollectionManager<E, ?, T> manageActive();

		/**
		 * This is called internally by the API and will not typically be used externally
		 *
		 * @return A collection manager to be used by the passive derived collection produced by {@link #collectPassive()}. Will be null if
		 *         this collection is not {@link #supportsPassive() passive}
		 */
		PassiveCollectionManager<E, ?, T> managePassive();

		/**
		 * @return A collection derived via this flow from the source collection
		 * @see #supportsPassive()
		 * @see #prefersPassive()
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
		 * @param until An observable that will kill the collection and release all its subscriptions and resources when it fires. May be
		 *        used to control the release of unneeded resources instead of relying on the garbage collector to dispose of them in its
		 *        own time.
		 * @return An {@link #supportsPassive() actively-managed} collection derived via this flow from the source collection
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
	interface DistinctDataFlow<E, I, T> extends CollectionDataFlow<E, I, T> {
		@Override
		DistinctDataFlow<E, T, T> reverse();

		@Override
		default <X> DistinctDataFlow<E, ?, X> filter(Class<X> type) {
			return filter(value -> {
				if (type == null || type.isInstance(value))
					return null;
				else
					return MutableCollectionElement.StdMsg.BAD_TYPE;
			}).transformEquivalent(TypeTokens.get().of(type), tx -> tx.cache(false).map(v -> (X) v).withReverse(x -> (T) x));
		}

		@Override
		DistinctDataFlow<E, T, T> filter(Function<? super T, String> filter);

		@Override
		<X> DistinctDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include);

		@Override
		DistinctDataFlow<E, T, T> refresh(Observable<?> refresh);

		@Override
		DistinctDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh);

		/**
		 * @param <X> The type for the mapped flow
		 * @param target The type for the mapped flow
		 * @param map The mapping function for each source element's value
		 * @param reverse The mapping function to recover source values from mapped values--required for equivalence
		 * @return The mapped flow
		 * @see #transformEquivalent(TypeToken, Function)
		 */
		default <X> DistinctDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, Function<? super T, ? extends X> map,
			Function<? super X, ? extends T> reverse) {
			return transformEquivalent(target, tx -> tx.map(map).withReverse(reverse));
		}

		/**
		 * @param <X> The type for the mapped flow
		 * @param target The type for the mapped flow
		 * @param map The mapping function for each source element's value
		 * @param reverse The mapping function to recover source values from mapped values--required for equivalence
		 * @return The mapped flow
		 * @see #transformEquivalent(TypeToken, Function)
		 */
		default <X> DistinctDataFlow<E, T, X> mapEquivalent(Class<X> target, Function<? super T, ? extends X> map,
			Function<? super X, ? extends T> reverse) {
			return mapEquivalent(TypeTokens.get().of(target), map, reverse);
		}

		/**
		 * <p>
		 * Same as {@link #transform(TypeToken, Function)}, but with the additional assertion that the produced transformed values will be
		 * one-to-one with the source values, such that the produced collection is unique in a similar way, without a need for an additional
		 * {@link #distinct(Consumer) uniqueness} check.
		 * </p>
		 * <p>
		 * This assertion cannot be checked (at compile time or run time), and if the assertion is incorrect such that multiple source
		 * values map to equivalent target values, <b>the resulting set will not be unique and data errors, including internal
		 * ObservableCollection errors, are possible</b>. Therefore caution should be used when considering whether to invoke this method.
		 * When in doubt, use {@link #transform(TypeToken, Function)} and {@link #distinct(Consumer)}.
		 * </p>
		 * <p>
		 * There may be performance concerns with this method, as the resulting flows equivalence must perform reverse operations, which is
		 * needed by {@link ObservableCollection#getElement(Object, boolean)}, {@link ObservableCollection#contains(Object)}, and other
		 * methods.
		 * </p>
		 *
		 * @param <X> The type of the transformed values
		 * @param target The type of the transformed values
		 * @param transform Defines the transformation from source to target values and the reverse
		 * @return The transformed flow
		 * @see Transformation for help using the API
		 */
		<X> DistinctDataFlow<E, T, X> transformEquivalent(TypeToken<X> target, //
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, ReversibleTransformation<T, X>> transform);

		/**
		 * @param <X> The type of the transformed values
		 * @param target The type of the transformed values
		 * @param transform Defines the transformation from source to target values and the reverse
		 * @return The transformed flow
		 * @see #transformEquivalent(TypeToken, Function)
		 */
		default <X> DistinctDataFlow<E, T, X> transformEquivalent(Class<X> target, //
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, ReversibleTransformation<T, X>> transform) {
			return transformEquivalent(TypeTokens.get().of(target), transform);
		}

		@Override
		default DistinctDataFlow<E, T, T> unmodifiable() {
			return filterMod(options -> options.unmodifiable(StdMsg.UNSUPPORTED_OPERATION, true));
		}

		@Override
		default DistinctDataFlow<E, T, T> unmodifiable(boolean allowUpdates) {
			return filterMod(options -> options.unmodifiable(StdMsg.UNSUPPORTED_OPERATION, allowUpdates));
		}

		@Override
		DistinctDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options);

		@Override
		default ObservableSet<T> collect() {
			return (ObservableSet<T>) CollectionDataFlow.super.collect();
		}

		@Override
		ActiveValueStoredManager<E, ?, T> manageActive();

		@Override
		ObservableSet<T> collectPassive();

		@Override
		ObservableSet<T> collectActive(Observable<?> until);
	}

	/**
	 * A data flow that produces a sorted collection
	 *
	 * @param <E> The type of the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of collection this flow may build
	 */
	interface SortedDataFlow<E, I, T> extends CollectionDataFlow<E, I, T>{
		Comparator<? super T> comparator();

		@Override
		Equivalence.SortedEquivalence<? super T> equivalence();

		@Override
		SortedDataFlow<E, T, T> reverse();

		@Override
		default <X> SortedDataFlow<E, ?, X> filter(Class<X> type) {
			return (SortedDataFlow<E, ?, X>) CollectionDataFlow.super.filter(type);
		}

		@Override
		SortedDataFlow<E, T, T> filter(Function<? super T, String> filter);

		@Override
		<X> SortedDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include);

		@Override
		SortedDataFlow<E, T, T> refresh(Observable<?> refresh);

		@Override
		SortedDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh);

		@Override
		DistinctSortedDataFlow<E, T, T> distinct(Consumer<UniqueOptions> options);

		default <X> SortedDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, Function<? super T, ? extends X> map,
			Function<? super X, ? extends T> reverse) {
			return transformEquivalent(target, tx -> tx.map(map).withReverse(reverse));
		}

		default <X> SortedDataFlow<E, T, X> mapEquivalent(Class<X> target, Function<? super T, ? extends X> map,
			Function<? super X, ? extends T> reverse) {
			return mapEquivalent(TypeTokens.get().of(target), map, reverse);
		}
		/**
		 * @param <X> The type for the mapped flow
		 * @param target The type for the mapped flow
		 * @param map The mapping function to produce values from each source element
		 * @param compare The comparator to source the mapped values in the same order as the corresponding source values
		 * @return The mapped flow
		 */
		default <X> SortedDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, Function<? super T, ? extends X> map,
			Comparator<? super X> compare) {
			return transformEquivalent(target, tx -> tx.map(map), compare);
		}

		default <X> SortedDataFlow<E, T, X> mapEquivalent(Class<X> target, Function<? super T, ? extends X> map,
			Comparator<? super X> compare) {
			return mapEquivalent(TypeTokens.get().of(target), map, compare);
		}

		<X> SortedDataFlow<E, T, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, ReversibleTransformation<T, X>> transform);

		default <X> SortedDataFlow<E, T, X> transformEquivalent(Class<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, ReversibleTransformation<T, X>> transform) {
			return transformEquivalent(TypeTokens.get().of(target), transform);
		}

		/**
		 * @param <X> The compile-time transformed type
		 * @param target The runtime transformed type
		 * @param transform A function to transform elements in this flow to transformed elements
		 * @param compare The comparator to compare the target values with--should reflect the order of this flow's comparator
		 * @return The transformed flow
		 * @see Transformation for help using the API
		 */
		<X> SortedDataFlow<E, T, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, Transformation<T, X>> transform, Comparator<? super X> compare);

		default <X> SortedDataFlow<E, T, X> transformEquivalent(Class<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, Transformation<T, X>> transform, Comparator<? super X> compare) {
			return transformEquivalent(TypeTokens.get().of(target), transform, compare);
		}

		@Override
		default SortedDataFlow<E, T, T> unmodifiable() {
			return filterMod(options -> options.unmodifiable(StdMsg.UNSUPPORTED_OPERATION, true));
		}

		@Override
		default SortedDataFlow<E, T, T> unmodifiable(boolean allowUpdates) {
			return filterMod(options -> options.unmodifiable(StdMsg.UNSUPPORTED_OPERATION, allowUpdates));
		}

		@Override
		SortedDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options);

		@Override
		default ObservableSortedCollection<T> collect() {
			return (ObservableSortedCollection<T>) CollectionDataFlow.super.collect();
		}

		@Override
		ObservableSortedCollection<T> collectPassive();

		@Override
		ObservableSortedCollection<T> collectActive(Observable<?> until);
	}

	/**
	 * A data flow that produces a sorted set
	 *
	 * @param <E> The type of the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of collection this flow may build
	 */
	interface DistinctSortedDataFlow<E, I, T> extends DistinctDataFlow<E, I, T>, SortedDataFlow<E, I, T> {
		@Override
		DistinctSortedDataFlow<E, T, T> reverse();

		@Override
		default <X> DistinctSortedDataFlow<E, ?, X> filter(Class<X> type) {
			return (DistinctSortedDataFlow<E, ?, X>) DistinctDataFlow.super.filter(type);
		}

		@Override
		DistinctSortedDataFlow<E, T, T> filter(Function<? super T, String> filter);

		@Override
		<X> DistinctSortedDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include);

		@Override
		DistinctSortedDataFlow<E, T, T> refresh(Observable<?> refresh);

		@Override
		DistinctSortedDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh);

		@Override
		default <X> DistinctSortedDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, Function<? super T, ? extends X> map,
			Function<? super X, ? extends T> reverse) {
			return (DistinctSortedDataFlow<E, T, X>) DistinctDataFlow.super.mapEquivalent(target, map, reverse);
		}

		@Override
		default <X> DistinctSortedDataFlow<E, T, X> mapEquivalent(Class<X> target, Function<? super T, ? extends X> map,
			Function<? super X, ? extends T> reverse) {
			return mapEquivalent(TypeTokens.get().of(target), map, reverse);
		}

		@Override
		default <X> DistinctSortedDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, Function<? super T, ? extends X> map,
			Comparator<? super X> compare) {
			return transformEquivalent(target, tx -> tx.map(map), compare);
		}

		@Override
		default <X> DistinctSortedDataFlow<E, T, X> mapEquivalent(Class<X> target, Function<? super T, ? extends X> map,
			Comparator<? super X> compare) {
			return mapEquivalent(TypeTokens.get().of(target), map, compare);
		}

		@Override
		<X> DistinctSortedDataFlow<E, T, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, ReversibleTransformation<T, X>> transform);

		@Override
		default <X> DistinctSortedDataFlow<E, T, X> transformEquivalent(Class<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, ReversibleTransformation<T, X>> transform) {
			return transformEquivalent(TypeTokens.get().of(target), transform);
		}

		@Override
		<X> DistinctSortedDataFlow<E, T, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, Transformation<T, X>> transform, Comparator<? super X> compare);

		@Override
		default <X> DistinctSortedDataFlow<E, T, X> transformEquivalent(Class<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, Transformation<T, X>> transform, Comparator<? super X> compare) {
			return transformEquivalent(TypeTokens.get().of(target), transform, compare);
		}

		@Override
		default DistinctSortedDataFlow<E, T, T> unmodifiable() {
			return filterMod(options -> options.unmodifiable(StdMsg.UNSUPPORTED_OPERATION, true));
		}

		@Override
		default DistinctSortedDataFlow<E, T, T> unmodifiable(boolean allowUpdates) {
			return filterMod(options -> options.unmodifiable(StdMsg.UNSUPPORTED_OPERATION, allowUpdates));
		}

		@Override
		DistinctSortedDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options);

		@Override
		default ObservableSortedSet<T> collect() {
			return (ObservableSortedSet<T>) DistinctDataFlow.super.collect();
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
		private String theUnmodifiableMsg;
		private boolean areUpdatesAllowed;
		private String theAddMsg;
		private String theRemoveMsg;
		private String theMoveMsg;
		private Function<? super T, String> theAddMsgFn;
		private Function<? super T, String> theRemoveMsgFn;

		public ModFilterBuilder() {
			areUpdatesAllowed = true;
		}

		public String getUnmodifiableMsg() {
			return theUnmodifiableMsg;
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

		public String getMoveMsg() {
			return theMoveMsg;
		}

		public ModFilterBuilder<T> unmodifiable(String modMsg, boolean allowUpdates) {
			theUnmodifiableMsg = modMsg;
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

		public ModFilterBuilder<T> noMove(String modMsg) {
			theMoveMsg = modMsg;
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

	/**
	 * Builds an observable element that corresponds to an element in an {@link ObservableCollection} whose value matches a condition
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ObservableFinderBuilder<E> {
		private final ObservableCollection<E> theCollection;
		private final Predicate<? super E> theCondition;
		private Ternian theLocation;
		private Supplier<? extends E> theDefault;
		private Observable<?> theRefresh;

		public ObservableFinderBuilder(ObservableCollection<E> collection, Predicate<? super E> condition) {
			theCollection = collection;
			theCondition = condition;
			theLocation = Ternian.TRUE;
			theDefault = () -> null;
		}

		/**
		 * Determines the location of the element that this finder will find when multiple elements match the condition
		 *
		 * @param location
		 *        <ul>
		 *        <li>{@link Ternian#TRUE TRUE} to find the matching element closest to the beginning of the collection</li>
		 *        <li>{@link Ternian#FALSE FALSE} to find the matching element closest to the end of the collection</li>
		 *        <li>{@link Ternian#NONE NONE} to find any matching element in the collection regardless of location. This option may have
		 *        performance advantages</li>
		 *        </ul>
		 * @return This builder
		 */
		public ObservableFinderBuilder<E> at(Ternian location) {
			theLocation = location;
			return this;
		}

		/**
		 * Determines the location of the element that this finder will find when multiple elements match the condition
		 *
		 * @param first Whether to find the matching element closest to the beginning or the end of the collection
		 * @return this builder
		 */
		public ObservableFinderBuilder<E> at(boolean first) {
			return at(Ternian.ofBoolean(first));
		}

		/**
		 * Use this to find the matching element closest to the beginning of the collection
		 *
		 * @return This builder
		 */
		public ObservableFinderBuilder<E> first() {
			return at(Ternian.TRUE);
		}

		/**
		 * Use this to find the matching element closest to the end of the collection
		 *
		 * @return This builder
		 */
		public ObservableFinderBuilder<E> last() {
			return at(Ternian.FALSE);
		}

		/**
		 * Use this to find any matching element in the collection regardless of location. This option may have performance advantages
		 *
		 * @return This builder
		 */
		public ObservableFinderBuilder<E> anywhere() {
			return at(Ternian.NONE);
		}

		/**
		 * @param refresh An observable which, when fired, will cause the finder to re-check its results
		 * @return This builder
		 */
		public ObservableFinderBuilder<E> refresh(Observable<?> refresh) {
			if (refresh == null) {//
			} else if (theRefresh != null)
				theRefresh = Observable.or(theRefresh, refresh);
			else
				theRefresh = refresh;
			return this;
		}

		/**
		 * @param def A supplier of a default value for the finder when no collection elements match the condition
		 * @return This builder
		 */
		public ObservableFinderBuilder<E> withDefault(Supplier<? extends E> def) {
			theDefault = def;
			return this;
		}

		/** @return The finder */
		public SettableElement<E> find() {
			return new ObservableCollectionImpl.ObservableCollectionFinder<>(theCollection, theCondition, theDefault, theLocation,
				theRefresh);
		}
	}
}
