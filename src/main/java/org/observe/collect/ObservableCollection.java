package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection.AbstractCombinedCollectionBuilder;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.CombinedValues;
import org.observe.collect.ObservableCollection.StdMsg;
import org.qommons.AbstractCausable;
import org.qommons.Causable;
import org.qommons.Ternian;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.TriFunction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.TransactableCollection;
import org.qommons.collect.TreeList;
import org.qommons.tree.CountedRedBlackNode.DefaultNode;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeSet;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * An enhanced collection.
 *
 * The biggest differences between ObservableCollection and Collection are:
 * <ul>
 * <li><b>Observability</b> The {@link #subscribe(Consumer)} method and the {@link #changes()} and {@link #simpleChanges()} observables
 * allow subscribers to be notified of changes in the collection.</li>
 * <li><b>Dynamic Transformation</b> The stream API allows transforming of the content of one collection into another, but the
 * transformation is done once for all, creating a new collection independent of the source. Sometimes it is desirable to make a transformed
 * collection that does its transformation dynamically, keeping the same data source, so that when the source is modified, the transformed
 * collection is also updated accordingly. #map(Function), #filter(Function), #groupBy(Function), and others allow this. In addition, the
 * syntax of creating these dynamic transformations is much simpler and cleaner: e.g.<br />
 * &nbsp;&nbsp;&nbsp;&nbsp; <code>coll.{@link #map(Function) map}(Function)</code><br />
 * instead of<br />
 * &nbsp;&nbsp;&nbsp;&nbsp;<code>coll.stream().map(Function).collect(Collectors.toList())</code>.</li>
 * <li><b>Modification Control</b> The {@link #filterModification()} method creates a collection that forbids certain types of modifications
 * to it. Modification control can also be used to intercept and perform actions based on modifications to a collection.</li>
 * <li><b>ElementSpliterator</b> ObservableCollections must implement {@link #spliterator()}, which returns a {@link ElementSpliterator},
 * which is an enhanced {@link Spliterator}. This had potential for the improved performance associated with using {@link Spliterator}
 * instead of {@link Iterator} as well as the utility added by {@link ElementSpliterator}.</li>
 * <li><b>Transactionality</b> ObservableCollections support the {@link org.qommons.Transactable} interface, allowing callers to reserve a
 * collection for write or to ensure that the collection is not written to during an operation (for implementations that support this. See
 * {@link org.qommons.Transactable#isLockSupported() isLockSupported()}).</li>
 * <li><b>Run-time type safety</b> ObservableCollections have a {@link #getType() type} associated with them, allowing them to enforce
 * type-safety at run time. How strictly this type-safety is enforced is implementation-dependent.</li>
 * </ul>
 *
 * @param <E> The type of element in the collection
 */
public interface ObservableCollection<E> extends TransactableCollection<E>, BetterCollection<E> {
	/** Standard messages returned by this class */
	interface StdMsg {
		static String BAD_TYPE = "Object is the wrong type for this collection";
		static String UNSUPPORTED_OPERATION = "Unsupported Operation";
		static String NULL_DISALLOWED = "Null is not allowed";
		static String ELEMENT_EXISTS = "Element already exists";
		static String GROUP_EXISTS = "Group already exists";
		static String WRONG_GROUP = "Item does not belong to this group";
		static String NOT_FOUND = "No such item found";
		static String ILLEGAL_ELEMENT = "Element is not allowed";
	}

	public static class SubscriptionCause extends AbstractCausable {
		public SubscriptionCause() {
			super(null);
		}
	}

	// Additional contract methods

	/** @return The type of elements in this collection */
	TypeToken<E> getType();

	@Override
	abstract boolean isLockSupported();

	@Override
	MutableObservableSpliterator<E> mutableSpliterator();

	// /**
	// * @param onElement The listener to be notified when new elements are added to the collection
	// * @return The function to call when the calling code is no longer interested in this collection
	// */
	// Subscription onElement(Consumer<? super ObservableElement<E>> onElement);

	Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer);

	@Override
	default ObservableElementSpliterator<E> spliterator() {
		return mutableSpliterator().immutable();
	}

	/**
	 * Like {@link #onChange(Consumer)}, but also fires initial {@link CollectionChangeType#add add} events for each element currently in
	 * the collection, and optionally fires final {@link CollectionChangeType#remove remove} events for each element in the collection on
	 * unsubscription.
	 *
	 * @param observer The listener to be notified of each element change in the collection
	 * @return The subscription to use to terminate listening
	 */
	default CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
		Subscription changeSub;
		try (Transaction t = lock(false, null)) {
			// Initial events
			SubscriptionCause.doWith(new SubscriptionCause(), c -> spliterator().forEachObservableElement(
				el -> observer.accept(new ObservableCollectionEvent<>(el.getElementId(), CollectionChangeType.add, null, el.get(), c))));
			// Subscribe changes
			changeSub = onChange(observer);
		}
		return removeAll -> {
			try (Transaction t = lock(false, null)) {
				// Unsubscribe changes
				changeSub.unsubscribe();
				if (removeAll) {
					// Remove events
					SubscriptionCause.doWith(new SubscriptionCause(), c -> spliterator().forEachObservableElement(el -> observer
						.accept(new ObservableCollectionEvent<>(el.getElementId(), CollectionChangeType.remove, null, el.get(), c))));
				}
			}
		};
	}

	default CollectionSubscription subscribeIndexed(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
		DefaultTreeSet<ElementId> ids = new DefaultTreeSet<>(Comparable::compareTo);
		return subscribe(evt -> {
			ElementId id = evt.getElementId();
			int index = -1;
			switch (evt.getType()) {
			case add:
				index = ids.addGetNode(id).getIndex();
				break;
			case remove:
				DefaultNode<ElementId> node = ids.getNode(id);
				index = node.getIndex();
				ids.removeNode(node);
				break;
			case set:
				index = ids.getNode(id).getIndex();
				break;
			}
			observer.accept(new IndexedCollectionEvent<>(id, index, evt.getType(), evt.getOldValue(), evt.getNewValue(), evt));
		});
	}

	// /**
	// * <p>
	// * The session allows listeners to retain state for the duration of a unit of work (controlled by implementation-specific means),
	// * batching events where possible. Not all events on a collection will have a session (the value may be null). In addition, the
	// presence
	// * or absence of a session need not imply anything about the threaded interactions with a session. A transaction may encompass events
	// * fired and received on multiple threads. In short, the only thing guaranteed about sessions is that they will end. Therefore, if a
	// * session is present, observers may assume that they can delay expensive results of collection events until the session completes.
	// * </p>
	// * <p>
	// * In order to use the session for a listening operation, 2 observers must be installed: one for the collection, and one for the
	// * session. If an event that the observer is interested in occurs in the collection, the session value must be checked. If there is
	// * currently a session, then the session must be tagged with information that will allow later reconstruction of the interesting
	// * particulars of the event. When a session event occurs, the observer should check to see if the
	// * {@link ObservableValueEvent#getOldValue() old value} of the event is non null and whether that old session (the one that is now
	// * ending) has any information installed by the collection observer. If it does, the interesting information should be reconstructed
	// and
	// * dealt with at that time.
	// * </p>
	// *
	// * @return The observable value for the current session of this collection
	// */
	// ObservableValue<CollectionSession> getSession();

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

	/**
	 * Tests the compatibility of an object with this collection. This method exposes a "best guess" on whether an element could be added to
	 * the collection , but does not provide any guarantee. This method should return null for any object for which {@link #add(Object)} is
	 * successful, but the fact that an object passes this test does not guarantee that it would be removed successfully. E.g. the position
	 * of the element in the collection may be a factor, but is tested for here.
	 *
	 * @param value The value to test compatibility for
	 * @return Null if given value could possibly be added to this collection, or a message why it can't
	 */
	String canAdd(E value);

	/**
	 * Tests the removability of an element from this collection. This method exposes a "best guess" on whether an element in the collection
	 * could be removed, but does not provide any guarantee. This method should return null for any object for which {@link #remove(Object)}
	 * is successful, but the fact that an object passes this test does not guarantee that it would be removed successfully. E.g. the
	 * position of the element in the collection may be a factor, but may not be tested for here.
	 *
	 * @param value The value to test removability for
	 * @return Null if given value could possibly be removed from this collection, or a message why it can't
	 */
	String canRemove(Object value);

	/**
	 * @param c collection to be checked for containment in this collection
	 * @return Whether this collection contains at least one of the elements in the specified collection
	 * @see #containsAll(Collection)
	 */
	@Override
	boolean containsAny(Collection<?> c);

	@Override
	default boolean removeIf(Predicate<? super E> filter) {
		boolean[] removed = new boolean[1];
		MutableObservableSpliterator<E> iter = mutableSpliterator();
		iter.forEachElement(el -> {
			if (filter.test(el.get())) {
				el.remove();
				removed[0] = true;
			}
		});
		return removed[0];
	}

	/**
	 * Overridden to return an {@link ObservableCollectionElement}. Also with the additional contract that this method must return the
	 * <b>first</b> matching element.
	 *
	 * @see org.qommons.collect.BetterCollection#elementFor(Object)
	 */
	@Override
	default MutableObservableElement<E> elementFor(Object value) {
		return (MutableObservableElement<E>) BetterCollection.super.elementFor(value);
	}

	/**
	 * @param search The test to search for elements that pass
	 * @param onElement The action to take on the first passing element in the collection
	 * @return Whether an element was found that passed the test
	 * @see #find(Predicate, Consumer)
	 */
	default boolean findObservableElement(Predicate<? super E> search, Consumer<? super MutableObservableElement<? extends E>> onElement) {
		return BetterCollection.super.find(search, el -> onElement.accept((MutableObservableElement<E>) el));
	}

	/**
	 * @param search The test to search for elements that pass
	 * @param onElement The action to take on all passing elements in the collection
	 * @return The number of elements found that passed the test
	 * @see #findAll(Predicate, Consumer)
	 */
	default int findAllObservableElements(Predicate<? super E> search, Consumer<? super MutableObservableElement<? extends E>> onElement) {
		return BetterCollection.super.findAll(search, el -> onElement.accept((MutableObservableElement<E>) el));
	}

	/**
	 * Optionally replaces each value in this collection with a mapped value. For every element, the map will be applied. If the result is
	 * identically (==) different from the existing value, that element will be replaced with the mapped value.
	 *
	 * @param map The map to apply to each value in this collection
	 * @param soft If true, this method will attempt to determine whether each differing mapped value is acceptable as a replacement. This
	 *        may, but is not guaranteed to, prevent {@link IllegalArgumentException}s
	 * @return Whether any elements were replaced
	 * @throws IllegalArgumentException If a mapped value is not acceptable as a replacement
	 */
	default boolean replaceAll(Function<? super E, ? extends E> map, boolean soft) {
		boolean[] replaced = new boolean[1];
		MutableObservableSpliterator<E> iter = mutableSpliterator();
		iter.forEachElement(el -> {
			E value = el.get();
			E newValue = map.apply(value);
			if (value != newValue && (!soft || el.isAcceptable(newValue) == null)) {
				el.set(newValue, null);
				replaced[0] = true;
			}
		});
		return replaced[0];
	}

	/**
	 * Replaces each value in this collection with a mapped value. For every element, the operation will be applied. If the result is
	 * identically (==) different from the existing value, that element will be replaced with the mapped value.
	 *
	 * @param op The operation to apply to each value in this collection
	 */
	default void replaceAll(UnaryOperator<E> op) {
		replaceAll(v -> op.apply(v), false);
	}

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

	/**
	 * @param values The values to add to the collection
	 * @return This collection
	 */
	default ObservableCollection<E> addValues(E... values) {
		addAll(java.util.Arrays.asList(values));
		return this;
	}

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
				boolean[] initialized = new boolean[1];
				Subscription sub = ObservableCollection.this.subscribe(evt -> {
					if (initialized[0])
						evt.getRootCausable().onFinish(key, (root, values) -> observer.onNext(root));
				});
				initialized[0] = true;
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
	 * @param test The test to find passing elements for
	 * @param def Supplies a default value for the observable result when no elements in this collection pass the test
	 * @param first true to always use the first element passing the test, false to always use the last element
	 * @return An observable value containing a value in this collection passing the given test
	 */
	default ObservableValue<E> find(Predicate<? super E> test, Supplier<? extends E> def, boolean first) {
		return new ObservableCollectionImpl.ObservableCollectionFinder<>(this, test, def, first);
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

	/** @return A builder that facilitates filtering, mapping, and other operations against this collection */
	default <T> CollectionDataFlow<E, E, E> flow() {
		return new DefaultCollectionDataFlow<>(this);
	}

	/** @return An observable value containing the only value in this collection while its size==1, otherwise null TODO TEST ME! */
	default ObservableValue<E> only() {
		return new ObservableCollectionImpl.ReducedValue<E, TreeList<E>, E>(this, getType().wrap()) {
			@Override
			public E get() {
				return size() == 1 ? iterator().next() : null;
			}

			@Override
			protected TreeList<E> init() {
				return new TreeList<>();
			}

			@Override
			protected TreeList<E> update(TreeList<E> oldValue, ObservableCollectionEvent<? extends E> change) {
				switch (change.getType()) {
				case add:
					oldValue.add(change.getNewValue());
					break;
				case remove:
					oldValue.remove(change.getOldValue());
					break;
				case set:
					oldValue.findAndReplace(v -> v == change.getOldValue(), v -> change.getNewValue());
					break;
				}
				return oldValue;
			}

			@Override
			protected E getValue(TreeList<E> updated) {
				return updated.size() == 1 ? updated.get(0) : null;
			}
		};
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
	 * @param <K> The compile-time key type for the multi-map
	 * @param keyType The run-time key type for the multi-map
	 * @param keyMaker The mapping function to group this collection's values by
	 * @return A builder to create a multi-map containing each of this collection's elements, each in the collection of the value mapped by
	 *         the given function applied to the element
	 */
	default <K> GroupingBuilder<E, K> groupBy(TypeToken<K> keyType, Function<? super E, ? extends K> keyMaker) {
		return new GroupingBuilder<>(this, keyType, keyMaker);
	}

	/**
	 * @param grouping The grouping builder containing the information needed to create the map
	 * @return A sorted multi-map whose keys are key-mapped elements of this collection and whose values are this collection's elements,
	 *         grouped by their mapped keys
	 */
	default <K> ObservableMultiMap<K, E> groupBy(GroupingBuilder<E, K> grouping) {
		return new ObservableCollectionImpl.GroupedMultiMap<>(this, grouping);
	}

	/**
	 * TODO TEST ME!
	 *
	 * @param grouping The grouping builder containing the information needed to create the map
	 * @return A sorted multi-map whose keys are key-mapped elements of this collection and whose values are this collection's elements,
	 *         grouped by their mapped keys
	 */
	default <K> ObservableSortedMultiMap<K, E> groupBy(SortedGroupingBuilder<E, K> grouping) {
		return new ObservableCollectionImpl.GroupedSortedMultiMap<>(this, grouping);
	}

	/**
	 * @param compare The comparator to use to sort this collection's elements
	 * @param nullable Whether null values may belong in the collection. If this is false and this collection contains null values,
	 *        {@link NullPointerException}s may be thrown from methods of the resulting collection
	 * @param until The observable to destroy the collection with. The collection's ordering will be maintained dynamically until the given
	 *        observable fires, after which the collection's methods will throw {@link IllegalStateException}.
	 * @return A new collection containing all the same elements as this collection, but ordered according to the given comparator
	 */
	default ObservableCollection<E> sorted(Comparator<? super E> compare, boolean nullable, Observable<?> until) {}

	/**
	 * @return An ObservableSet that contains this collection's values with no duplicates, according to this collection's
	 *         {@link #equivalence()} scheme. Values in this collection must maintain their equivalence properties. If a collection
	 *         element's value's properties change such that the set of values it is equivalent to change, the resulting set may become
	 *         corrupt.
	 */
	default ObservableSet<E> unique() {
		return new ObservableSetImpl.CollectionWrappingSet<>(this);
	}

	/**
	 * @param compare The comparator to use to sort this collection's elements
	 * @param nullable Whether null values may belong in the collection. If this is false and this collection contains null values,
	 *        {@link NullPointerException}s may be thrown from methods of the resulting collection
	 * @param until The observable to destroy the collection with. The collection's ordering will be maintained dynamically until the given
	 *        observable fires, after which the collection's methods will throw {@link IllegalStateException}.
	 * @return An ObservableSortedSet that contains this collection's values with no duplicates, according to the comparator. Values in this
	 *         collection must maintain their equivalence properties. If a collection element's value's properties change such that the set
	 *         of values it is equivalent to change, the resulting set may become corrupt.
	 */
	default ObservableSortedSet<E> unique(Comparator<? super E> compare, boolean nullable, Observable<?> until) {
		return new ObservableSortedSetImpl.CollectionWrappingSortedSet<>(this, compare, nullable, until);
	}

	/**
	 * Creates an indexed collection backed by this collection's content. Index operations (e.g.
	 * {@link ObservableIndexedCollection#get(int)}) may be linear-time.
	 *
	 * @return An indexed collection backed by this collection's content
	 */
	default ObservableIndexedCollection<E> indexify() {
		return new ObservableIndexedCollectionImpl.IndexifiedCollection<>(this);
	}

	/** @return A builder that allows creation of a view backed by this collection's data but with different capabilities */
	default ViewBuilder<E> view() {
		return new ViewBuilder<>(this);
	}

	/**
	 * @param type The type of the collection
	 * @param collection The collection
	 * @return An immutable collection with the same values as those in the given collection
	 */
	public static <E> ObservableCollection<E> constant(TypeToken<E> type, Collection<? extends E> collection) {
		return new ObservableCollectionImpl.ConstantObservableCollection<>(type, collection);
	}

	/**
	 * @param type The type of the collection
	 * @param values The values for the new collection
	 * @return An immutable collection with the same values as those in the given collection
	 */
	public static <E> ObservableCollection<E> constant(TypeToken<E> type, E... values) {
		return constant(type, java.util.Arrays.asList(values));
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> ObservableCollection<E> flattenValue(ObservableValue<? extends ObservableCollection<E>> collectionObservable) {
		return new ObservableCollectionImpl.FlattenedValueCollection<>(collectionObservable);
	}

	/**
	 * @param <E> The super-type of elements in the inner collections
	 * @param coll The collection to flatten
	 * @return A collection containing all elements of all collections in the outer collection
	 */
	public static <E> ObservableCollection<E> flatten(ObservableCollection<? extends ObservableCollection<? extends E>> coll) {
		return interleave(coll, list -> 0, false);
	}

	/**
	 * @param <T> An observable collection that contains all elements the given collections
	 * @param colls The collections to flatten
	 * @return A collection containing all elements of the given collections
	 */
	public static <T> ObservableCollection<T> flattenCollections(ObservableCollection<? extends T>... colls) {
		return flatten(ObservableList.constant(new TypeToken<ObservableCollection<? extends T>>() {}, colls));
	}

	public static <T> ObservableCollection<T> interleave(ObservableCollection<? extends ObservableCollection<? extends T>> coll,
		Function<? super List<? extends T>, Integer> discriminator, boolean withRemove) {
		return new ObservableCollectionImpl.FlattenedObservableCollection<>(coll, discriminator, withRemove);
	}

	/**
	 * @param <T> The type of the folded observable
	 * @param coll The collection to fold
	 * @return An observable that is notified for every event on any observable in the collection
	 */
	public static <T> Observable<T> fold(ObservableCollection<? extends Observable<T>> coll) {
		return new Observable<T>() {
			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				HashMap<Object, Subscription> subscriptions = new HashMap<>();
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
				}).removeAll();
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
	public static int hashCode(ObservableCollection<?> coll) {
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
	public static <E> boolean equals(ObservableCollection<E> coll, Object o) {
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
	public static String toString(ObservableCollection<?> coll) {
		StringBuilder ret = new StringBuilder("(");
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
		ret.append(')');
		return ret.toString();
	}

	abstract class DerivedCollectionDef<E, I, T> {
		private final DerivedCollectionDef<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;

		public DerivedCollectionDef(DerivedCollectionDef<E, ?, I> parent, TypeToken<T> target) {
			theParent = parent;
			theTargetType = target;
		}

		protected DerivedCollectionDef<E, ?, I> getParent() {
			return theParent;
		}

		protected TypeToken<T> getTargetType() {
			return theTargetType;
		}

		public abstract CollectionManager<E, T> manageCollection();
	}

	interface CollectionManager<E, T> extends Transactable {
		TypeToken<T> getTargetType();

		boolean isDynamicallyFiltered();
		boolean isMapped();

		boolean isReversible();

		void begin(Consumer<CollectionUpdate> onUpdate);

		FilterMapResult<E, T> map(FilterMapResult<E, T> source);
		default FilterMapResult<E, T> map(E source) {
			return map(new FilterMapResult<>(source));
		}

		FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest);
		default FilterMapResult<T, E> reverse(T dest) {
			return reverse(new FilterMapResult<>(dest));
		}

		CollectionElementManager<E, T> createElement(E init);
	}

	/**
	 * Implements {@link ObservableCollection#flattenValues(ObservableCollection)}
	 *
	 * @param <E> The source type
	 * @param <T> The destination type
	 */
	class FilterMapResult<E, T> {
		public E source;
		public T result;
		public String error;

		public FilterMapResult() {}

		public FilterMapResult(E src) {
			source = src;
		}
	}

	interface CollectionElementManager<E, T> {
		void remove();

		boolean isPresent();

		T get();

		void set(E value);

		boolean update(CollectionUpdate update);
	}

	interface CollectionUpdate {
		Object getCause();
	}

	abstract class CollectionDataFlow<E, I, T> extends DerivedCollectionDef<E, I, T> {
		private final ObservableCollection<E> theCollection;

		protected CollectionDataFlow(ObservableCollection<E> collection, DerivedCollectionDef<E, ?, I> parent, TypeToken<T> target) {
			super(parent, target);
			theCollection = collection;
		}

		protected ObservableCollection<E> getCollection() {
			return theCollection;
		}

		// Flow operations

		public CollectionDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return filter(filter, false);
		}

		public CollectionDataFlow<E, T, T> filter(Function<? super T, String> filter, boolean filterNulls) {
			return new FilterOp<>(theCollection, this, filter, filterNulls);
		}

		public CollectionDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return filterStatic(filter, false);
		}

		public CollectionDataFlow<E, T, T> filterStatic(Function<? super T, String> filter, boolean filterNulls) {
			return new StaticFilterOp<>(theCollection, this, filter, filterNulls);
		}

		/**
		 * @param <X> The type for the new collection
		 * @param type The type to filter this collection by
		 * @return A collection backed by this collection, consisting only of elements in this collection whose values are instances of the
		 *         given class
		 */
		public <X> CollectionDataFlow<E, T, X> filter(Class<X> type) {
			return filterStatic(value -> {
				if (type == null || type.isInstance(value))
					return null;
				else
					return StdMsg.BAD_TYPE;
			}, true).map(TypeToken.of(type)).map(v -> (X) v, true);
		}

		public CollectionDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new RefreshOp<>(theCollection, this, refresh);
		}

		public CollectionDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new ElementRefreshOp<>(theCollection, this, refresh);
		}

		public <X> MappedCollectionBuilder<E, T, X> map(TypeToken<X> target) {
			return new MappedCollectionBuilder<>(theCollection, this, target);
		}

		public <X> CollectionDataFlow<E, ?, X> flatMap(TypeToken<X> target,
			Function<? super T, ? extends ObservableValue<? extends X>> map) {
			TypeToken<ObservableValue<? extends X>> valueType = new TypeToken<ObservableValue<? extends X>>() {}
			.where(new TypeParameter<X>() {}, target);
			return map(valueType).map(map, false).refreshEach(v -> v.noInit()).map(target).map(ObservableValue::get, false);
		}

		public <V, X> CombinedCollectionBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, TypeToken<X> target) {
			return new CombinedCollectionBuilder2<>(theCollection, this, target, value, Ternian.NONE);
		}

		public <V, X> CombinedCollectionBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, boolean combineNulls,
			TypeToken<X> target) {
			return new CombinedCollectionBuilder2<>(theCollection, this, target, value, Ternian.of(combineNulls));
		}

		// Build method

		public ObservableCollection<T> build() {
			return new ObservableCollectionImpl.DerivedCollection<>(theCollection, manageCollection());
		}
	}

	class DefaultCollectionDataFlow<E> extends CollectionDataFlow<E, E, E> {
		protected DefaultCollectionDataFlow(ObservableCollection<E> collection) {
			super(collection, null, collection.getType());
		}

		@Override
		public CollectionManager<E, E> manageCollection() {
			return new ObservableCollectionImpl.DefaultCollectionManager<>(getCollection().getType(), getCollection().isLockSupported());
		}

		@Override
		public ObservableCollection<E> build() {
			return getCollection();
		}
	}

	class FilterOp<E, T> extends CollectionDataFlow<E, T, T> {
		private final Function<? super T, String> theFilter;
		private final boolean areNullsFiltered;

		protected FilterOp(ObservableCollection<E> collection, DerivedCollectionDef<E, ?, T> parent, Function<? super T, String> filter,
			boolean filterNulls) {
			super(collection, parent, parent.getTargetType());
			theFilter = filter;
			areNullsFiltered = filterNulls;
		}

		@Override
		public CollectionManager<E, T> manageCollection() {
			return ObservableCollectionImpl.FilteredCollectionManager<>(getParent().manageCollection(), theFilter, areNullsFiltered);
		}
	}

	class StaticFilterOp<E, T> extends CollectionDataFlow<E, T, T> {
		private final Function<? super T, String> theFilter;
		private final boolean areNullsFiltered;

		protected StaticFilterOp(ObservableCollection<E> collection, DerivedCollectionDef<E, ?, T> parent,
			Function<? super T, String> filter, boolean filterNulls) {
			super(collection, parent, parent.getTargetType());
			theFilter = filter;
			areNullsFiltered = filterNulls;
		}

		@Override
		public CollectionManager<E, T> manageCollection() {
			return ObservableCollectionImpl.StaticFilteredCollectionManager<>(getParent().manageCollection(), theFilter, areNullsFiltered);
		}
	}

	class RefreshOp<E, T> extends CollectionDataFlow<E, T, T> {
		private final Observable<?> theRefresh;

		protected RefreshOp(ObservableCollection<E> collection, DerivedCollectionDef<E, ?, T> parent, Observable<?> refresh) {
			super(collection, parent, parent.getTargetType());
			theRefresh = refresh;
		}

		@Override
		public CollectionManager<E, T> manageCollection() {
			return new ObservableCollectionImpl.RefreshingCollectionManager<>(getParent().manageCollection(), theRefresh);
		}
	}

	class ElementRefreshOp<E, T> extends CollectionDataFlow<E, T, T> {
		private final Function<? super T, ? extends Observable<?>> theElementRefresh;

		protected ElementRefreshOp(ObservableCollection<E> collection, DerivedCollectionDef<E, ?, T> parent,
			Function<? super T, ? extends Observable<?>> elementRefresh) {
			super(collection, parent, parent.getTargetType());
			theElementRefresh = elementRefresh;
		}

		@Override
		public CollectionManager<E, T> manageCollection() {
			return new ObservableCollectionImpl.ElementRefreshingCollectionManager<>(getParent().manageCollection(), theElementRefresh);
		}
	}

	/**
	 * Builds a filtered and/or mapped collection
	 *
	 * @param <E> The type of values in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of values in the mapped collection
	 */
	class MappedCollectionBuilder<E, I, T> {
		private final ObservableCollection<E> theCollection;
		private final CollectionDataFlow<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private Function<? super T, ? extends I> theReverse;
		private boolean areNullsReversed;

		protected MappedCollectionBuilder(ObservableCollection<E> collection, CollectionDataFlow<E, ?, I> parent, TypeToken<T> type) {
			theCollection = collection;
			theParent = parent;
			theTargetType = type;
		}

		public MappedCollectionBuilder<E, I, T> withReverse(Function<? super T, ? extends I> reverse, boolean reverseNulls) {
			theReverse = reverse;
			areNullsReversed = reverseNulls;
			return this;
		}

		public CollectionDataFlow<E, I, T> map(Function<? super I, ? extends T> map, boolean mapNulls) {
			return new MapOp<>(theCollection, theParent, theTargetType, map, mapNulls, theReverse, areNullsReversed);
		}
	}

	class MapOp<E, I, T> extends CollectionDataFlow<E, I, T> {
		private final Function<? super I, ? extends T> theMap;
		private final boolean areNullsMapped;
		private final Function<? super T, ? extends I> theReverse;
		private final boolean areNullsReversed;

		protected MapOp(ObservableCollection<E> collection, DerivedCollectionDef<E, ?, I> parent, TypeToken<T> target,
			Function<? super I, ? extends T> map, boolean mapNulls, Function<? super T, ? extends I> reverse, boolean reverseNulls) {
			super(collection, parent, target);
			theMap = map;
			areNullsMapped = mapNulls;
			theReverse = reverse;
			areNullsReversed = reverseNulls;
		}

		@Override
		public CollectionManager<E, T> manageCollection() {
			return new ObservableCollectionImpl.MappedCollectionManager<>(getParent().manageCollection(), theMap, areNullsMapped,
				theReverse, areNullsReversed);
		}
	}

	/**
	 * A structure that may be used to define a collection whose elements are those of a single source collection combined with one or more
	 * values
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <I> Intermediate type
	 * @param <R> The type of elements in the resulting collection
	 * @see ObservableCollection.CollectionDataFlow#combineWith(ObservableValue, TypeToken)
	 * @see ObservableCollection.CombinedCollectionBuilder3#and(ObservableValue)
	 */
	interface CombinedCollectionBuilder<E, I, R> {
		<T> CombinedCollectionBuilder<E, I, R> and(ObservableValue<T> arg);

		<T> CombinedCollectionBuilder<E, I, R> and(ObservableValue<T> arg, boolean combineNulls);

		CombinedCollectionBuilder<E, I, R> withReverse(Function<? super CombinedValues<? extends R>, ? extends I> reverse,
			boolean reverseNulls);

		default CollectionDataFlow<E, I, R> build(Function<? super CombinedValues<? extends I>, ? extends R> combination) {
			return build(combination, false);
		}

		CollectionDataFlow<E, I, R> build(Function<? super CombinedValues<? extends I>, ? extends R> combination, boolean combineNulls);
	}

	/**
	 * A structure that is operated on to produce the elements of a combined collection
	 *
	 * @param <E> The type of the source element (or the value to be reversed)
	 * @see ObservableCollection.CollectionDataFlow#combineWith(ObservableValue, TypeToken)
	 */
	interface CombinedValues<E> {
		E getElement();

		<T> T get(ObservableValue<T> arg);
	}

	/**
	 * Defines a combination of a single source collection with one or more observable values
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of elements in the resulting collection
	 */
	class CombinedCollectionDef<E, I, T> extends CollectionDataFlow<E, I, T> {
		private final Map<ObservableValue<?>, Boolean> theArgs;
		private final Function<? super CombinedValues<? extends I>, ? extends T> theCombination;
		private final boolean combineNulls;
		private final Function<? super CombinedValues<? extends T>, ? extends I> theReverse;
		private final boolean reverseNulls;

		protected CombinedCollectionDef(ObservableCollection<E> collection, DerivedCollectionDef<E, ?, I> parent, TypeToken<T> target,
			Map<ObservableValue<?>, Boolean> args, Function<? super CombinedValues<? extends I>, ? extends T> combination,
			boolean combineNulls,
			Function<? super CombinedValues<? extends T>, ? extends I> reverse, boolean reverseNulls) {
			super(collection, parent, target);
			theArgs = Collections.unmodifiableMap(args);
			theCombination = combination;
			this.combineNulls = combineNulls;
			theReverse = reverse;
			this.reverseNulls = reverseNulls;
		}

		@Override
		public CollectionManager<E, T> manageCollection() {
			return new ObservableCollectionImpl.CombinedCollectionManager<>(getParent().manageCollection(), getTargetType(), theArgs,
				theCombination, combineNulls, theReverse, reverseNulls);
		}
	}

	abstract class AbstractCombinedCollectionBuilder<E, I, R> implements CombinedCollectionBuilder<E, I, R> {
		private final ObservableCollection<E> theCollection;
		private final CollectionDataFlow<E, ?, I> theParent;
		private final TypeToken<R> theTargetType;
		private final LinkedHashMap<ObservableValue<?>, Ternian> theArgs;
		private Function<? super CombinedValues<? extends R>, ? extends I> theReverse;
		private boolean defaultCombineNulls = false;
		private boolean isReverseNulls = false;

		protected AbstractCombinedCollectionBuilder(ObservableCollection<E> collection, CollectionDataFlow<E, ?, I> parent,
			TypeToken<R> targetType) {
			theCollection = collection;
			theParent = parent;
			theTargetType = targetType;
			theArgs = new LinkedHashMap<>();
		}

		protected void addArg(ObservableValue<?> arg, Ternian combineNulls) {
			if (theArgs.containsKey(arg))
				throw new IllegalArgumentException("Argument " + arg + " is already combined");
			theArgs.put(arg, combineNulls);
		}

		protected Ternian combineNulls(ObservableValue<?> arg) {
			return theArgs.get(arg);
		}

		protected ObservableCollection<E> getCollection() {
			return theCollection;
		}

		protected CollectionDataFlow<E, ?, I> getParent() {
			return theParent;
		}

		protected TypeToken<R> getTargetType() {
			return theTargetType;
		}

		protected Function<? super CombinedValues<? extends R>, ? extends I> getReverse() {
			return theReverse;
		}

		protected boolean isDefaultCombineNulls() {
			return defaultCombineNulls;
		}

		protected boolean isReverseNulls() {
			return isReverseNulls;
		}

		public AbstractCombinedCollectionBuilder<E, I, R> combineNullsByDefault() {
			defaultCombineNulls = true;
			return this;
		}

		@Override
		public AbstractCombinedCollectionBuilder<E, I, R> withReverse(Function<? super CombinedValues<? extends R>, ? extends I> reverse,
			boolean reverseNulls) {
			theReverse = reverse;
			this.isReverseNulls = reverseNulls;
			return this;
		}

		@Override
		public CollectionDataFlow<E, I, R> build(Function<? super CombinedValues<? extends I>, ? extends R> combination,
			boolean combineNulls) {
			return new CombinedCollectionDef<>(theCollection, theParent, theTargetType, getResultArgs(), combination, combineNulls,
				theReverse, isReverseNulls);
		}

		private Map<ObservableValue<?>, Boolean> getResultArgs() {
			Map<ObservableValue<?>, Boolean> result = new LinkedHashMap<>(theArgs.size() * 3 / 2);
			for (Map.Entry<ObservableValue<?>, Ternian> arg : theArgs.entrySet())
				result.put(arg.getKey(), arg.getValue().withDefault(defaultCombineNulls));
			return result;
		}
	}

	/**
	 * A {@link ObservableCollection.CombinedCollectionBuilder} for the combination of a collection with a single value. Use
	 * {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <I> Intermediate type
	 * @param <V> The type of the combined value
	 * @param <R> The type of elements in the resulting collection
	 * @see ObservableCollection.CollectionDataFlow#combineWith(ObservableValue, TypeToken)
	 */
	class CombinedCollectionBuilder2<E, I, V, R> extends AbstractCombinedCollectionBuilder<E, I, R> {
		private final ObservableValue<V> theArg2;

		protected CombinedCollectionBuilder2(ObservableCollection<E> collection, CollectionDataFlow<E, ?, I> parent,
			TypeToken<R> targetType, ObservableValue<V> arg2, Ternian combineArg2Nulls) {
			super(collection, parent, targetType);
			theArg2 = arg2;
			addArg(arg2, combineArg2Nulls);
		}

		@Override
		public CombinedCollectionBuilder2<E, I, V, R> combineNullsByDefault() {
			return (CombinedCollectionBuilder2<E, I, V, R>) super.combineNullsByDefault();
		}

		public CombinedCollectionBuilder2<E, I, V, R> withReverse(BiFunction<? super R, ? super V, ? extends I> reverse,
			boolean reverseNulls) {
			return withReverse(cv -> reverse.apply(cv.getElement(), cv.get(theArg2)), reverseNulls);
		}

		@Override
		public CombinedCollectionBuilder2<E, I, V, R> withReverse(Function<? super CombinedValues<? extends R>, ? extends I> reverse,
			boolean reverseNulls) {
			return (CombinedCollectionBuilder2<E, I, V, R>) super.withReverse(reverse, reverseNulls);
		}

		public CollectionDataFlow<E, I, R> build(BiFunction<? super I, ? super V, ? extends R> combination) {
			return build(cv -> combination.apply(cv.getElement(), cv.get(theArg2)));
		}

		@Override
		public <U> CombinedCollectionBuilder3<E, I, V, U, R> and(ObservableValue<U> arg3) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedCollectionBuilder3<>(getCollection(), getParent(), getTargetType(), theArg2, combineNulls(theArg2), arg3,
				Ternian.NONE);
		}

		@Override
		public <U> CombinedCollectionBuilder3<E, I, V, U, R> and(ObservableValue<U> arg3, boolean combineNulls) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedCollectionBuilder3<>(getCollection(), getParent(), getTargetType(), theArg2, combineNulls(theArg2), arg3,
				Ternian.of(combineNulls));
		}
	}

	/**
	 * A {@link ObservableCollection.CombinedCollectionBuilder} for the combination of a collection with 2 values. Use
	 * {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <I> Intermediate type
	 * @param <V1> The type of the first combined value
	 * @param <V2> The type of the second combined value
	 * @param <R> The type of elements in the resulting collection
	 * @see ObservableCollection.CollectionDataFlow#combineWith(ObservableValue, TypeToken)
	 * @see ObservableCollection.CombinedCollectionBuilder2#and(ObservableValue)
	 */
	class CombinedCollectionBuilder3<E, I, V1, V2, R> extends AbstractCombinedCollectionBuilder<E, I, R> {
		private final ObservableValue<V1> theArg2;
		private final ObservableValue<V2> theArg3;

		protected CombinedCollectionBuilder3(ObservableCollection<E> collection, CollectionDataFlow<E, ?, I> parent,
			TypeToken<R> targetType, ObservableValue<V1> arg2, Ternian combineArg2Nulls, ObservableValue<V2> arg3,
			Ternian combineArg3Nulls) {
			super(collection, parent, targetType);
			theArg2 = arg2;
			theArg3 = arg3;
			addArg(arg2, combineArg2Nulls);
			addArg(arg3, combineArg3Nulls);
		}

		@Override
		public CombinedCollectionBuilder3<E, I, V1, V2, R> combineNullsByDefault() {
			return (CombinedCollectionBuilder3<E, I, V1, V2, R>) super.combineNullsByDefault();
		}

		public CombinedCollectionBuilder3<E, I, V1, V2, R> withReverse(TriFunction<? super R, ? super V1, ? super V2, ? extends I> reverse,
			boolean reverseNulls) {
			return withReverse(cv -> reverse.apply(cv.getElement(), cv.get(theArg2), cv.get(theArg3)), reverseNulls);
		}

		@Override
		public CombinedCollectionBuilder3<E, I, V1, V2, R> withReverse(Function<? super CombinedValues<? extends R>, ? extends I> reverse,
			boolean reverseNulls) {
			return (CombinedCollectionBuilder3<E, I, V1, V2, R>) super.withReverse(reverse, reverseNulls);
		}

		public CollectionDataFlow<E, I, R> build(TriFunction<? super I, ? super V1, ? super V2, ? extends R> combination) {
			return build(cv -> combination.apply(cv.getElement(), cv.get(theArg2), cv.get(theArg3)));
		}

		@Override
		public <T2> CombinedCollectionBuilderN<E, I, R> and(ObservableValue<T2> arg) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedCollectionBuilderN<>(getCollection(), getParent(), getTargetType(), theArg2, combineNulls(theArg2), theArg3,
				combineNulls(theArg3), arg, Ternian.NONE);
		}

		@Override
		public <T2> CombinedCollectionBuilderN<E, I, R> and(ObservableValue<T2> arg, boolean combineNulls) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedCollectionBuilderN<>(getCollection(), getParent(), getTargetType(), theArg2, combineNulls(theArg2), theArg3,
				combineNulls(theArg3), arg, Ternian.of(combineNulls));
		}
	}

	/**
	 * A {@link ObservableCollection.CombinedCollectionBuilder} for the combination of a collection with one or more (typically at least 3)
	 * values. Use {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <I> Intermediate type
	 * @param <R> The type of elements in the resulting collection
	 * @see ObservableCollection.CollectionDataFlow#combineWith(ObservableValue, TypeToken)
	 * @see ObservableCollection.CombinedCollectionBuilder3#and(ObservableValue)
	 */
	class CombinedCollectionBuilderN<E, I, R> extends AbstractCombinedCollectionBuilder<E, I, R> {
		protected CombinedCollectionBuilderN(ObservableCollection<E> collection, CollectionDataFlow<E, ?, I> parent,
			TypeToken<R> targetType, ObservableValue<?> arg2, Ternian combineArg2Nulls, ObservableValue<?> arg3, Ternian combineArg3Nulls,
			ObservableValue<?> arg4, Ternian combineArg4Nulls) {
			super(collection, parent, targetType);
			addArg(arg2, combineArg2Nulls);
			addArg(arg3, combineArg3Nulls);
			addArg(arg4, combineArg4Nulls);
		}

		@Override
		public CombinedCollectionBuilderN<E, I, R> combineNullsByDefault() {
			return (CombinedCollectionBuilderN<E, I, R>) super.combineNullsByDefault();
		}

		@Override
		public CombinedCollectionBuilderN<E, I, R> withReverse(Function<? super CombinedValues<? extends R>, ? extends I> reverse,
			boolean reverseNulls) {
			return (CombinedCollectionBuilderN<E, I, R>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public <T> CombinedCollectionBuilderN<E, I, R> and(ObservableValue<T> arg) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			addArg(arg, Ternian.NONE);
			return this;
		}

		@Override
		public <T> CombinedCollectionBuilderN<E, I, R> and(ObservableValue<T> arg, boolean combineNull) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			addArg(arg, Ternian.of(combineNull));
			return this;
		}
	}

	class ViewDef<E> {
		private final TypeToken<E> theType;
		private final String theImmutableMsg;
		private final String theAddMsg;
		private final String theRemoveMsg;
		private final Function<? super E, String> theAddMsgFn;
		private final Function<? super E, String> theRemoveMsgFn;

		private final Equivalence<? super E> theEquivalence;

		private final Observable<?> theUntil;
		private final boolean untilRemoves;

		public ViewDef(TypeToken<E> type, String immutableMsg, String addMsg, String removeMsg, Function<? super E, String> addMsgFn,
			Function<? super E, String> removeMsgFn, Equivalence<? super E> equivalence, Observable<?> until, boolean untilRemoves) {
			theType = type;
			theImmutableMsg = immutableMsg;
			theAddMsg = addMsg;
			theRemoveMsg = removeMsg;
			theAddMsgFn = addMsgFn;
			theRemoveMsgFn = removeMsgFn;

			theEquivalence = equivalence;

			theUntil = until;
			this.untilRemoves = untilRemoves;
		}

		public boolean isAddFiltered() {
			return theImmutableMsg != null || theAddMsg != null || theAddMsgFn != null;
		}

		public String checkAdd(E value) {
			String msg = null;
			if (theAddMsgFn != null)
				msg = theAddMsgFn.apply(value);
			if (msg == null && theAddMsg != null)
				msg = theAddMsg;
			if (msg == null && theImmutableMsg != null)
				msg = theImmutableMsg;
			return msg;
		}

		public E tryAdd(E value) {
			String msg = null;
			if (theAddMsgFn != null)
				msg = theAddMsgFn.apply(value);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			if (theAddMsg != null)
				throw new UnsupportedOperationException(theAddMsg);
			if (theImmutableMsg != null)
				throw new UnsupportedOperationException(theImmutableMsg);
			return value;
		}

		public boolean isRemoveFiltered() {
			return theImmutableMsg != null || theRemoveMsg != null || theRemoveMsgFn != null;
		}

		public String checkRemove(Object value) {
			String msg = null;
			if (theRemoveMsgFn != null) {
				if (value != null && !theType.getRawType().isInstance(value))
					msg = StdMsg.BAD_TYPE;
				msg = theRemoveMsgFn.apply((E) value);
			}
			if (msg == null && theRemoveMsg != null)
				msg = theRemoveMsg;
			if (msg == null && theImmutableMsg != null)
				msg = theImmutableMsg;
			return msg;
		}

		public E tryRemove(E value) {
			String msg = null;
			if (theRemoveMsgFn != null)
				msg = theRemoveMsgFn.apply(value);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			if (theRemoveMsg != null)
				throw new UnsupportedOperationException(theRemoveMsg);
			if (theImmutableMsg != null)
				throw new UnsupportedOperationException(theImmutableMsg);
			return value;
		}

		public Equivalence<? super E> getEquivalence() {
			return theEquivalence;
		}

		public Observable<?> getUntil() {
			return theUntil;
		}

		public boolean isUntilRemoves() {
			return untilRemoves;
		}
	}

	class ViewBuilder<E> {
		private final ObservableCollection<E> theCollection;
		private String theImmutableMsg;
		private String theAddMsg;
		private String theRemoveMsg;
		private Function<? super E, String> theAddMsgFn;
		private Function<? super E, String> theRemoveMsgFn;

		private Equivalence<? super E> theEquivalence;

		private Observable<?> theUntil;
		private boolean untilRemoves;

		public ViewBuilder(ObservableCollection<E> collection) {
			theCollection = collection;
		}

		protected ObservableCollection<E> getSource() {
			return theCollection;
		}

		public ViewBuilder<E> immutable(String modMsg) {
			theImmutableMsg = modMsg;
			return this;
		}

		public ViewBuilder<E> noAdd(String modMsg) {
			theAddMsg = modMsg;
			return this;
		}

		public ViewBuilder<E> noRemove(String modMsg) {
			theRemoveMsg = modMsg;
			return this;
		}

		public ViewBuilder<E> filterAdd(Function<? super E, String> messageFn) {
			theAddMsgFn = messageFn;
			return this;
		}

		public ViewBuilder<E> filterRemove(Function<? super E, String> messageFn) {
			theRemoveMsgFn = messageFn;
			return this;
		}

		public ViewBuilder<E> withEquivalence(Equivalence<? super E> equivalence) {
			theEquivalence = equivalence;
			return this;
		}

		public ViewBuilder<E> takeUntil(Observable<?> until) {
			if (theUntil != null)
				throw new IllegalStateException("takeUntil already called");
			theUntil = until;
			return this;
		}

		public ViewBuilder<E> unsubscribeOn(Observable<?> until) {
			if (theUntil != null)
				throw new IllegalStateException("takeUntil already called");
			theUntil = until;
			untilRemoves = true;
			return this;
		}

		public ViewDef<E> toDef() {
			return new ViewDef<>(theCollection.getType(), theImmutableMsg, theAddMsg, theRemoveMsg, theAddMsgFn, theRemoveMsgFn,
				theEquivalence, theUntil, untilRemoves);
		}

		public ObservableCollection<E> build() {
			return new ObservableCollectionImpl.CollectionView<>(theCollection, toDef());
		}
	}

	/**
	 * Builds a grouping definition that can be used to create a collection-backed multi-map
	 *
	 * @param <E> The type of values in the collection, which will be the type of values in the multi-map
	 * @param <K> The key type for the multi-map
	 * @see ObservableCollection#groupBy(TypeToken, Function)
	 * @see ObservableCollection#groupBy(GroupingBuilder)
	 */
	class GroupingBuilder<E, K> {
		private final ObservableCollection<E> theCollection;
		private final TypeToken<K> theKeyType;
		private final Function<? super E, ? extends K> theKeyMaker;
		private Equivalence<? super K> theEquivalence = Equivalence.DEFAULT;
		private boolean areNullsMapped;
		private boolean isBuilt;

		public GroupingBuilder(ObservableCollection<E> collection, TypeToken<K> keyType, Function<? super E, ? extends K> keyMaker) {
			theCollection = collection;
			theKeyType = keyType;
			theKeyMaker = keyMaker;
		}

		public ObservableCollection<E> getCollection() {
			return theCollection;
		}

		public TypeToken<K> getKeyType() {
			return theKeyType;
		}

		public Function<? super E, ? extends K> getKeyMaker() {
			return theKeyMaker;
		}

		public GroupingBuilder<E, K> withEquivalence(Equivalence<? super K> equivalence) {
			if (isBuilt)
				throw new IllegalStateException("Cannot change the grouping builder's properties after building");
			theEquivalence = equivalence;
			return this;
		}

		public Equivalence<? super K> getEquivalence() {
			return theEquivalence;
		}

		public GroupingBuilder<E, K> withNullsMapped(boolean mapNulls) {
			areNullsMapped = mapNulls;
			return this;
		}

		public boolean areNullsMapped() {
			return areNullsMapped;
		}

		public SortedGroupingBuilder<E, K> sorted(Comparator<? super K> compare) {
			return new SortedGroupingBuilder<>(this, compare);
		}

		public ObservableMultiMap<K, E> build() {
			isBuilt = true;
			return theCollection.groupBy(this);
		}
	}

	/**
	 * Builds a sorted grouping definition that can be used to create a collection-backed sorted multi-map
	 *
	 * @param <E> The type of values in the collection, which will be the type of values in the multi-map
	 * @param <K> The key type for the multi-map
	 * @see ObservableCollection#groupBy(TypeToken, Function)
	 * @see ObservableCollection.GroupingBuilder#sorted(Comparator)
	 * @see ObservableCollection#groupBy(SortedGroupingBuilder)
	 */
	class SortedGroupingBuilder<E, K> extends GroupingBuilder<E, K> {
		private final Comparator<? super K> theCompare;

		public SortedGroupingBuilder(GroupingBuilder<E, K> basicBuilder, Comparator<? super K> compare) {
			super(basicBuilder.getCollection(), basicBuilder.getKeyType(), basicBuilder.getKeyMaker());
			withNullsMapped(basicBuilder.areNullsMapped());
			theCompare = compare;
		}

		@Override
		public ObservableSortedMultiMap<K, E> build() {
			return getCollection().groupBy(this);
		}

		public Comparator<? super K> getCompare() {
			return theCompare;
		}
	}
}
