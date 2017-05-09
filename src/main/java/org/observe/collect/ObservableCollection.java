package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.qommons.Ternian;
import org.qommons.Transaction;
import org.qommons.TriFunction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.Qollection;
import org.qommons.collect.TransactableCollection;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * An enhanced collection.
 *
 * The biggest differences between Qollection and Collection are:
 * <ul>
 * <li><b>Observability</b> The {@link #onElement(Consumer)} method provides {@link ObservableElement}s for each element in the collection
 * that allows subscribers to be notified of updates, additions, and deletions.</li>
 * <li><b>Dynamic Transformation</b> The stream api allows transforming of the content of one collection into another, but the
 * transformation is done once for all, creating a new collection independent of the source. Sometimes it is desirable to make a transformed
 * collection that does its transformation dynamically, keeping the same data source, so that when the source is modified, the transformed
 * collection is also updated accordingly. #map(Function), #filter(Function), #groupBy(Function), and others allow this. In addition, the
 * syntax of creating these dynamic transformations is much simpler and cleaner: e.g.<br />
 * &nbsp;&nbsp;&nbsp;&nbsp; <code>coll.{@link #map(Function) map}(Function)</code><br />
 * instead of<br />
 * &nbsp;&nbsp;&nbsp;&nbsp;<code>coll.stream().map(Function).collect(Collectors.toList())</code>.</li>
 * <li><b>Modification Control</b> The {@link #filterModification()} method creates a collection that forbids certain types of modifications
 * to it. Modification control can also be used to intercept and perform actions based on modifications to a collection.</li>
 * <li><b>ElementSpliterator</b> Qollections must implement {@link #spliterator()}, which returns a {@link ElementSpliterator}, which is an
 * enhanced {@link Spliterator}. This had potential for the improved performance associated with using {@link Spliterator} instead of
 * {@link Iterator} as well as the utility added by {@link ElementSpliterator}.</li>
 * <li><b>Transactionality</b> Qollections support the {@link org.qommons.Transactable} interface, allowing callers to reserve a collection
 * for write or to ensure that the collection is not written to during an operation (for implementations that support this. See
 * {@link org.qommons.Transactable#isLockSupported() isLockSupported()}).</li>
 * <li><b>Run-time type safety</b> Qollections have a {@link #getType() type} associated with them, allowing them to enforce type-safety at
 * run time. How strictly this type-safety is enforced is implementation-dependent.</li>
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
		static String GROUP_EXISTS = "Group already exists";
		static String WRONG_GROUP = "Item does not belong to this group";
		static String NOT_FOUND = "No such item found";
	}

	// Additional contract methods

	/** @return The type of elements in this collection */
	TypeToken<E> getType();

	@Override
	abstract ElementSpliterator<E> spliterator();

	// /**
	// * @param onElement The listener to be notified when new elements are added to the collection
	// * @return The function to call when the calling code is no longer interested in this collection
	// */
	// Subscription onElement(Consumer<? super ObservableElement<E>> onElement);

	Subscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer);

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
		ElementSpliterator<E> iter = spliterator();
		iter.forEachElement(el -> {
			if (filter.test(el.get())) {
				el.remove();
				removed[0] = true;
			}
		});
		return removed[0];
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
	default boolean replace(Function<? super E, ? extends E> map, boolean soft) {
		boolean[] replaced = new boolean[1];
		ElementSpliterator<E> iter = spliterator();
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
		return new ObservableValue<Integer>() {
			private final TypeToken<Integer> intType = TypeToken.of(Integer.TYPE);

			@Override
			public TypeToken<Integer> getType() {
				return intType;
			}

			@Override
			public Integer get() {
				return size();
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<Integer>> observer) {
				ObservableValue<Integer> sizeObs = this;
				boolean [] initialized = new boolean[1];
				Subscription sub = onElement(new Consumer<ObservableElement<E>>() {
					private AtomicInteger size = new AtomicInteger();

					@Override
					public void accept(ObservableElement<E> value) {
						int newSize = size.incrementAndGet();
						fire(newSize - 1, newSize, value);
						value.subscribe(new Observer<ObservableValueEvent<E>>() {
							@Override
							public <V2 extends ObservableValueEvent<E>> void onNext(V2 value2) {
							}

							@Override
							public <V2 extends ObservableValueEvent<E>> void onCompleted(V2 value2) {
								int newSize2 = size.decrementAndGet();
								fire(newSize2 + 1, newSize2, value2);
							}
						});
					}

					private void fire(int oldSize, int newSize, Object cause) {
						if(initialized[0])
							Observer.onNextAndFinish(observer, sizeObs.createChangeEvent(oldSize, newSize, cause));
					}
				});
				initialized[0] = true;
				Observer.onNextAndFinish(observer, sizeObs.createInitialEvent(size(), null));
				return sub;
			}

			@Override
			public boolean isSafe() {
				return true;
			}

			@Override
			public String toString() {
				return ObservableCollection.this + ".size()";
			}
		};
	}

	/**
	 * @return An observable that fires a change event whenever any elements in it are added, removed or changed. These changes are batched
	 *         by transaction when possible.
	 */
	default Observable<? extends CollectionChangeEvent<E>> changes() {
		return new CollectionChangesObservable<>(this);
	}

	/**
	 * @return An observable that fires a value (the cause event of the change) whenever anything in this collection changes. Unlike
	 *         {@link #changes()}, this observable will only fire 1 event per transaction.
	 */
	default Observable<Object> simpleChanges() {
		return new Observable<Object>() {
			@Override
			public Subscription subscribe(Observer<Object> observer) {
				Consumer<Object> action = root -> observer.onNext(root);
				boolean[] initialized = new boolean[1];
				Subscription sub = ObservableCollection.this.subscribe(evt -> {
					if (initialized[0])
						evt.onRootFinish(action);
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

	// Filter/mapping

	/**
	 * @param <T> The type of the new collection
	 * @param map The mapping function
	 * @return An observable collection of a new type backed by this collection and the mapping function
	 */
	default <T> ObservableCollection<T> map(Function<? super E, T> map) {
		return buildMap(MappedCollectionBuilder.returnType(map)).map(map, false).build(true);
	}

	/**
	 * @param filter The filter function
	 * @return A collection containing all non-null elements passing the given test
	 */
	default ObservableCollection<E> filter(Function<? super E, String> filter) {
		return this.<E> buildMap(getType()).filter(filter, false).build(true);
	}

	/**
	 * @param <T> The type for the new collection
	 * @param type The type to filter this collection by
	 * @return A collection backed by this collection, consisting only of elements in this collection whose values are instances of the
	 *         given class
	 */
	default <T> ObservableCollection<T> filter(Class<T> type) {
		return buildMap(TypeToken.of(type)).filter(value -> {
			if (type == null || type.isInstance(value))
				return null;
			else
				return StdMsg.BAD_TYPE;
		}, true).build(false);
	}

	/**
	 * Creates a builder that can be used to create a highly customized and efficient chain of filter-mappings. The
	 * {@link MappedCollectionBuilder#build(boolean) result} will be a collection backed by this collection's values but filtered/mapped
	 * according to the methods called on the builder.
	 *
	 * @param <T> The type of values to map to
	 * @param type The run-time type of values to map to
	 * @return A builder to customize the filter/mapped collection
	 */
	default <T> MappedCollectionBuilder<E, E, T> buildMap(TypeToken<T> type) {
		return new MappedCollectionBuilder<>(this, null, type);
	}

	/**
	 * Creates a collection using the results of a {@link MappedCollectionBuilder}
	 *
	 * @param <T> The type of values to map to
	 * @param filterMap The definition for the filter/mapping
	 * @param dynamic Whether the filtering on the collection (if <code>filterMap</code> is {@link FilterMapDef#isFiltered() filtered})
	 *        should be done dynamically or statically. Dynamic filtering allows for the possibility that changes to individual elements in
	 *        the collection may result in those elements passing or failing the filter. Static filtering uses the initial (on subscription)
	 *        value of the element to determine whether that element is included in the collection and this inclusion does not change.
	 *        Static filtering may offer potentially large performance improvements, particularly for filtering a small subset of a large
	 *        collection, but may cause incorrect results if the initial inclusion assumption is wrong.
	 * @return The filter/mapped collection
	 */
	default <T> ObservableCollection<T> filterMap(FilterMapDef<E, ?, T> filterMap, boolean dynamic) {
		return new ObservableCollectionImpl.FilterMappedObservableCollection<>(this, filterMap, dynamic);
	}

	/**
	 * Shorthand for {@link #flatten(ObservableCollection) flatten}({@link #map(Function) map}(Function))
	 *
	 * @param <T> The type of the values produced
	 * @param type The type of the values produced
	 * @param map The value producer
	 * @return A collection whose values are the accumulation of all those produced by applying the given function to all of this
	 *         collection's values
	 */
	default <T> ObservableCollection<T> flatMap(TypeToken<T> type, Function<? super E, ? extends ObservableCollection<? extends T>> map) {
		TypeToken<ObservableCollection<? extends T>> collectionType;
		if (type == null) {
			collectionType = (TypeToken<ObservableCollection<? extends T>>) TypeToken.of(map.getClass())
				.resolveType(Function.class.getTypeParameters()[1]);
			if (!collectionType.isAssignableFrom(new TypeToken<Qollection<T>>() {}))
				collectionType = new TypeToken<ObservableCollection<? extends T>>() {};
		} else
			collectionType = new TypeToken<ObservableCollection<? extends T>>() {}.where(new TypeParameter<T>() {}, type);
			return flatten(this.<ObservableCollection<? extends T>> buildMap(collectionType).map(map, false).build(true));
	}

	/** @return An observable value containing the only value in this collection while its size==1, otherwise null TODO TEST ME! */
	default ObservableValue<E> only() {
		return new ObservableValue<E>() {
			private final TypeToken<E> type = ObservableCollection.this.getType().wrap();

			@Override
			public TypeToken<E> getType() {
				return type;
			}

			@Override
			public E get() {
				return size() == 1 ? iterator().next() : null;
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
				boolean[] initialized = new boolean[1];
				final Object key = new Object();
				class OnlyElement implements Consumer<ObservableElement<E>> {
					private Collection<ObservableElement<E>> theElements = new LinkedHashSet<>();
					private E theValue;

					@Override
					public void accept(ObservableElement<E> element) {
						theElements.add(element);
						element.subscribe(new Observer<ObservableValueEvent<E>>() {
							@Override
							public <V3 extends ObservableValueEvent<E>> void onNext(V3 event) {
								if (event.isInitial()) {
									if (theElements.isEmpty())
										newValue(event.getValue(), event);
									else
										newValue(null, event);
								} else if (theElements.size() == 1)
									newValue(event.getValue(), event);
							}

							@Override
							public <V3 extends ObservableValueEvent<E>> void onCompleted(V3 event) {
								theElements.remove(element);
								if (theElements.isEmpty())
									newValue(null, event);
								else if (theElements.size() == 1)
									newValue(theElements.iterator().next().get(), event);
							}
						});
					}

					private void newValue(E value, ObservableValueEvent<E> cause) {
						E oldValue = theValue;
						theValue = value;
						if (initialized[0]) {
							CollectionSession session = getSession().get();
							if (session == null)
								Observer.onNextAndFinish(observer, createChangeEvent(oldValue, theValue, null));
							else {
								session.putIfAbsent(key, "oldValue", oldValue);
								session.put(key, "newValue", theValue);
							}
						}
					}
				}
				OnlyElement collOnEl = new OnlyElement();
				Subscription collSub = ObservableCollection.this.onElement(collOnEl);
				Subscription transSub = getSession().subscribe(new Observer<ObservableValueEvent<CollectionSession>>() {
					@Override
					public <V2 extends ObservableValueEvent<CollectionSession>> void onNext(V2 value) {
						CollectionSession completed = value.getOldValue();
						if (completed == null)
							return;
						E oldBest = (E) completed.get(key, "oldValue");
						E newBest = (E) completed.get(key, "newValue");
						if (oldBest == null && newBest == null)
							return;
						Observer.onNextAndFinish(observer, createChangeEvent(oldBest, newBest, value));
					}
				});
				Observer.onNextAndFinish(observer, createInitialEvent(collOnEl.theValue, null));
				return () -> {
					collSub.unsubscribe();
					transSub.unsubscribe();
				};
			}

			@Override
			public boolean isSafe() {
				return true;
			}
		};
	}

	/**
	 * @param arg The value to be combined
	 * @param targetType The type of elements in the resulting collection
	 * @return A builder to define a collection that is a combination of this collection's elements with the given observable value (and
	 *         possibly others)
	 */
	default <T, V> CombinedCollectionBuilder2<E, T, V> combineWith(ObservableValue<T> arg, TypeToken<V> targetType) {
		return new CombinedCollectionBuilder2<>(this, arg, targetType);
	}

	/**
	 * @param combination The collection definition
	 * @return The combined collection
	 * @see #combineWith(ObservableValue, TypeToken)
	 */
	default <V> ObservableCollection<V> combine(CombinedCollectionDef<E, V> combination) {
		return new ObservableCollectionImpl.CombinedObservableCollection<>(this, combination);
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
	 *            replacement of values will result in the entire collection being iterated over for each subscription. Null here will have
	 *            no consequence if the result is never observed. Must be associative.
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
	 *            replacement of values will result in the entire collection being iterated over for each subscription. Null here will have
	 *            no consequence if the result is never observed. Must be associative.
	 * @return The reduced value
	 */
	default <T> ObservableValue<T> reduce(TypeToken<T> type, T init, BiFunction<? super T, ? super E, T> add,
		BiFunction<? super T, ? super E, T> remove) {
		return new ObservableValue<T>() {
			@Override
			public TypeToken<T> getType() {
				return type;
			}

			@Override
			public T get() {
				T ret = init;
				for(E element : ObservableCollection.this)
					ret = add.apply(ret, element);
				return ret;
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
				final boolean [] initElements = new boolean[1];
				final Object key = new Object();
				class THolder {
					public T theValue = init;

					public boolean recalc = false;
				}
				THolder holder = new THolder();
				Subscription collSub = ObservableCollection.this.onElement(new Consumer<ObservableElement<E>>() {

					@Override
					public void accept(ObservableElement<E> element) {
						initElements[0] = true;
						element.subscribe(new Observer<ObservableValueEvent<E>>() {
							@Override
							public <V3 extends ObservableValueEvent<E>> void onNext(V3 value) {
								T newValue = holder.theValue;
								if(holder.recalc) {
								} else if(value.isInitial()) {
									newValue = add.apply(newValue, value.getValue());
									newValue(newValue);
								} else if(remove != null) {
									newValue = remove.apply(newValue, value.getOldValue());
									newValue = add.apply(newValue, value.getValue());
									newValue(newValue);
								} else
									recalc();
							}

							@Override
							public <V3 extends ObservableValueEvent<E>> void onCompleted(V3 value) {
								if(holder.recalc) {
								} else if(remove != null)
									newValue(remove.apply(holder.theValue, value.getOldValue()));
								else
									recalc();
							}
						});
					}

					void newValue(T value) {
						T oldValue = holder.theValue;
						holder.theValue = value;
						CollectionSession session = getSession().get();
						if(session == null)
							Observer.onNextAndFinish(observer, createChangeEvent(oldValue, holder.theValue, null));
						else {
							session.putIfAbsent(key, "oldValue", oldValue);
							session.put(key, "newValue", holder.theValue);
						}
					}

					private void recalc() {
						CollectionSession session = getSession().get();
						if(session == null)
							newValue(get());
						else {
							holder.recalc = true;
							session.putIfAbsent(key, "oldValue", holder.theValue);
							session.put(key, "recalc", true);
						}
					}
				});
				Subscription transSub = getSession().subscribe(new Observer<ObservableValueEvent<CollectionSession>>() {
					@Override
					public <V2 extends ObservableValueEvent<CollectionSession>> void onNext(V2 value) {
						CollectionSession completed = value.getOldValue();
						if(completed == null)
							return;
						T oldValue = (T) completed.get(key, "oldValue");
						T newValue;
						if(completed.get(key, "recalc") != null) {
							holder.theValue = newValue = get();
							holder.recalc = false;
						} else
							newValue = (T) completed.get(key, "newValue");
						if(oldValue == null && newValue == null)
							return;
						Observer.onNextAndFinish(observer, createChangeEvent(oldValue, newValue, value));
					}
				});
				if(!initElements[0])
					Observer.onNextAndFinish(observer, createInitialEvent(init, null));
				return () -> {
					collSub.unsubscribe();
					transSub.unsubscribe();
				};
			}

			@Override
			public boolean isSafe() {
				return true;
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
			if(v1 == null)
				return v2;
			else if(v2 == null)
				return v1;
			else if(compare.compare(v1, v2) <= 0)
				return v1;
			else
				return v2;
		} , null);
	}

	/**
	 * @param compare The comparator to use to compare this collection's values
	 * @return An observable value containing the maximum of the values, by the given comparator
	 */
	default ObservableValue<E> maxBy(Comparator<? super E> compare) {
		return reduce(getType(), null, (v1, v2) -> {
			if(v1 == null)
				return v2;
			else if(v2 == null)
				return v1;
			else if(compare.compare(v1, v2) >= 0)
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
	 * @param refresh The observable to re-fire events on
	 * @return A collection whose elements fire additional value events when the given observable fires
	 */
	default ObservableCollection<E> refresh(Observable<?> refresh) {
		return new ObservableCollectionImpl.RefreshingCollection<>(this, refresh);
	}

	/**
	 * @param refire A function that supplies a refresh observable as a function of element value
	 * @return A collection whose values individually refresh when the observable returned by the given function fires
	 */
	default ObservableCollection<E> refreshEach(Function<? super E, Observable<?>> refire) {
		return new ObservableCollectionImpl.ElementRefreshingCollection<>(this, refire);
	}

	/**
	 * Allows control over whether and how the collection may be modified
	 *
	 * @return A builder that can return a derived collection that can only be modified in certain ways
	 */
	default ModFilterBuilder<E> filterModification() {
		return new ModFilterBuilder<>(this);
	}

	/**
	 * @param filter The modification filter definition
	 * @return A collection with the same contents as this collection, but may only be modified as defined by the filter
	 */
	default ObservableCollection<E> filterModification(ModFilterDef<E> filter) {
		return new ObservableCollectionImpl.ModFilteredCollection<>(this, filter);
	}

	/**
	 * Creates a collection with the same elements as this collection, but cached, such that the
	 *
	 * @param until The observable to destroy the cache with. The cache will be maintained dynamically until the given observable fires,
	 *        after which all methods in the collection will throw {@link IllegalStateException}s.
	 * @return The cached collection
	 */
	default ObservableCollection<E> cached(Observable<?> until) {
		return new ObservableCollectionImpl.CachedObservableCollection<>(this, until);
	}

	/**
	 * @param until The observable to end the collection on
	 * @return A collection that mirrors this collection's values until the given observable fires a value, upon which the returned
	 *         collection's elements will be removed and collection subscriptions unsubscribed
	 */
	default ObservableCollection<E> takeUntil(Observable<?> until) {
		return new ObservableCollectionImpl.TakenUntilObservableCollection<>(this, until, true);
	}

	/**
	 * @param until The observable to unsubscribe the collection on
	 * @return A collection that mirrors this collection's values until the given observable fires a value, upon which the returned
	 *         collection's subscriptions will be removed. Unlike {@link #takeUntil(Observable)} however, the returned collection's elements
	 *         will not be removed when the observable fires.
	 */
	default ObservableCollection<E> unsubscribeOn(Observable<?> until) {
		return new ObservableCollectionImpl.TakenUntilObservableCollection<>(this, until, false);
	}

	/**
	 * @param type The type of the collection
	 * @param collection The collection
	 * @return An immutable collection with the same values as those in the given collection
	 */
	public static <E> ObservableCollection<E> constant(TypeToken<E> type, Collection<E> collection){
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
	 * Turns a collection of observable values into a collection composed of those holders' values
	 *
	 * @param <T> The type of elements held in the values
	 * @param collection The collection to flatten
	 * @return The flattened collection
	 */
	public static <T> ObservableCollection<T> flattenValues(ObservableCollection<? extends ObservableValue<T>> collection) {
		return new ObservableCollectionImpl.FlattenedValuesCollection<>(collection);
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
		return new ObservableCollectionImpl.FlattenedObservableCollection<>(coll);
	}

	/**
	 * @param <T> An observable collection that contains all elements the given collections
	 * @param colls The collections to flatten
	 * @return A collection containing all elements of the given collections
	 */
	public static <T> ObservableCollection<T> flattenCollections(ObservableCollection<? extends T>... colls) {
		return flatten(ObservableList.constant(new TypeToken<ObservableCollection<? extends T>>() {}, colls));
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
				Subscription ret = coll.onElement(element -> {
					element.subscribe(new Observer<ObservableValueEvent<? extends Observable<T>>>() {
						@Override
						public <V2 extends ObservableValueEvent<? extends Observable<T>>> void onNext(V2 value) {
							Observable<?> until = element.noInit().fireOnComplete();
							if (!value.isInitial())
								until = until.skip(1);
							value.getValue().takeUntil(until).subscribe(observer);
						}
					});
				});
				return ret;
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
		int hashCode = 1;
		for (Object e : coll)
			hashCode = 31 * hashCode + (e == null ? 0 : e.hashCode());
		return hashCode;
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

	/**
	 * Builds a filtered and/or mapped collection
	 *
	 * @param <E> The type of values in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of values in the mapped collection
	 */
	class MappedCollectionBuilder<E, I, T> {
		private final ObservableCollection<E> theWrapped;
		private final MappedCollectionBuilder<E, ?, I> theParent;
		private final TypeToken<T> theType;
		private Function<? super I, String> theFilter;
		private boolean areNullsFiltered;
		private Function<? super I, ? extends T> theMap;
		private boolean areNullsMapped;
		private Function<? super T, ? extends I> theReverse;
		private boolean areNullsReversed;

		protected MappedCollectionBuilder(ObservableCollection<E> wrapped, MappedCollectionBuilder<E, ?, I> parent, TypeToken<T> type) {
			theWrapped = wrapped;
			theParent = parent;
			theType = type;
		}

		protected ObservableCollection<E> getCollection() {
			return theWrapped;
		}

		protected MappedCollectionBuilder<E, ?, I> getParent() {
			return theParent;
		}

		protected TypeToken<T> getType() {
			return theType;
		}

		protected Function<? super I, String> getFilter() {
			return theFilter;
		}

		protected boolean areNullsFiltered() {
			return areNullsFiltered;
		}

		protected Function<? super I, ? extends T> getMap() {
			return theMap;
		}

		protected boolean areNullsMapped() {
			return areNullsMapped;
		}

		protected Function<? super T, ? extends I> getReverse() {
			return theReverse;
		}

		protected boolean areNullsReversed() {
			return areNullsReversed;
		}

		static <T> TypeToken<T> returnType(Function<?, ? extends T> fn) {
			return (TypeToken<T>) TypeToken.of(fn.getClass()).resolveType(Function.class.getTypeParameters()[1]);
		}

		public MappedCollectionBuilder<E, I, T> filter(Function<? super I, String> filter, boolean filterNulls) {
			theFilter = filter;
			areNullsFiltered = filterNulls;
			return this;
		}

		public MappedCollectionBuilder<E, I, T> map(Function<? super I, ? extends T> map, boolean mapNulls) {
			theMap = map;
			areNullsMapped = mapNulls;
			return this;
		}

		public MappedCollectionBuilder<E, I, T> withReverse(Function<? super T, ? extends I> reverse, boolean reverseNulls) {
			theReverse = reverse;
			areNullsReversed = reverseNulls;
			return this;
		}

		public FilterMapDef<E, I, T> toDef() {
			FilterMapDef<E, ?, I> parent = theParent == null ? null : theParent.toDef();
			TypeToken<I> intermediate = parent == null ? (TypeToken<I>) theWrapped.getType() : parent.destType;
			return new FilterMapDef<>(theWrapped.getType(), intermediate, theType, parent, theFilter, areNullsFiltered, theMap,
				areNullsMapped, theReverse, areNullsReversed);
		}

		public ObservableCollection<T> build(boolean dynamic) {
			if (theMap == null && !theWrapped.getType().equals(theType))
				throw new IllegalStateException("Building a type-mapped collection with no map defined");
			return theWrapped.filterMap(toDef(), dynamic);
		}

		public <X> MappedCollectionBuilder<E, T, X> andThen(TypeToken<X> nextType) {
			if (theMap == null && !theWrapped.getType().equals(theType))
				throw new IllegalStateException("Type-mapped collection builder with no map defined");
			return new MappedCollectionBuilder<>(theWrapped, this, nextType);
		}
	}

	/**
	 * A definition for a filter/mapped collection
	 *
	 * @param <E> The type of values in the source collection
	 * @param <I> Intermediate type, not exposed
	 * @param <T> The type of values for the mapped collection
	 */
	class FilterMapDef<E, I, T> {
		public final TypeToken<E> sourceType;
		private final TypeToken<I> intermediateType;
		public final TypeToken<T> destType;
		private final FilterMapDef<E, ?, I> parent;
		private final Function<? super I, String> filter;
		private final boolean filterNulls;
		private final Function<? super I, ? extends T> map;
		private final boolean mapNulls;
		private final Function<? super T, ? extends I> reverse;
		private final boolean reverseNulls;

		public FilterMapDef(TypeToken<E> sourceType, TypeToken<I> intermediateType, TypeToken<T> type, FilterMapDef<E, ?, I> parent,
			Function<? super I, String> filter, boolean filterNulls, Function<? super I, ? extends T> map, boolean mapNulls,
			Function<? super T, ? extends I> reverse, boolean reverseNulls) {
			this.sourceType = sourceType;
			this.intermediateType = intermediateType;
			this.destType = type;
			this.parent = parent;
			this.filter = filter;
			this.filterNulls = filterNulls;
			this.map = map;
			this.mapNulls = mapNulls;
			this.reverse = reverse;
			this.reverseNulls = reverseNulls;

			if (parent == null && !sourceType.equals(intermediateType))
				throw new IllegalArgumentException("A " + getClass().getName()
					+ " with no parent must have identical source and intermediate types: " + sourceType + ", " + intermediateType);
		}

		public boolean checkSourceType(Object value) {
			return value == null || sourceType.getRawType().isInstance(value);
		}

		private boolean checkIntermediateType(I value) {
			return value == null || intermediateType.getRawType().isInstance(value);
		}

		public boolean checkDestType(Object value) {
			return value == null || destType.getRawType().isInstance(value);
		}

		public FilterMapResult<E, ?> checkSourceValue(FilterMapResult<E, ?> result) {
			return internalCheckSourceValue((FilterMapResult<E, I>) result);
		}

		private FilterMapResult<E, I> internalCheckSourceValue(FilterMapResult<E, I> result) {
			result.error = null;

			// Get the starting point for this def
			I interm;
			if (parent != null) {
				interm = parent.map(result).result;
				result.result = null;
				if (result.error != null)
					return result;
				if (!checkIntermediateType(interm))
					throw new IllegalStateException(
						"Implementation error: intermediate value " + interm + " is not an instance of " + intermediateType);
			} else {
				interm = (I) result.source;
				if (!checkIntermediateType(interm))
					throw new IllegalStateException("Source value " + interm + " is not an instance of " + intermediateType);
			}
			if (result.error != null) {
				return result;
			}

			// Filter
			if (filter != null) {
				if (!filterNulls && interm == null)
					result.error = StdMsg.NULL_DISALLOWED;
				else
					result.error = filter.apply(interm);
			}

			return result;
		}

		public boolean isFiltered() {
			FilterMapDef<?, ?, ?> def = this;
			while (def != null) {
				if (def.filter != null)
					return true;
				def = def.parent;
			}
			return false;
		}

		public boolean isMapped() {
			FilterMapDef<?, ?, ?> def = this;
			while (def != null) {
				if (def.map != null)
					return true;
				def = def.parent;
			}
			return false;
		}

		public boolean isReversible() {
			FilterMapDef<?, ?, ?> def = this;
			while (def != null) {
				if (def.map != null && def.reverse == null)
					return false;
				def = def.parent;
			}
			return true;
		}

		public FilterMapResult<E, T> map(FilterMapResult<E, T> result) {
			internalCheckSourceValue((FilterMapResult<E, I>) result);
			I interm = ((FilterMapResult<E, I>) result).result;

			// Map
			if (map == null)
				result.result = (T) interm;
			else if (interm == null && !mapNulls)
				result.result = null;
			else
				result.result = map.apply(interm);

			if (result.result != null && !destType.getRawType().isInstance(result.result))
				throw new IllegalStateException("Result value " + result.result + " is not an instance of " + destType);

			return result;
		}

		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> result) {
			if (!isReversible())
				throw new IllegalStateException("This filter map is not reversible");

			result.error = null;

			if (!checkDestType(result.source))
				throw new IllegalStateException("Value to reverse " + result.source + " is not an instance of " + destType);
			// reverse map
			I interm;
			if (map == null)
				interm = (I) result.source;
			else if (result.source != null || reverseNulls)
				interm = reverse.apply(result.source);
			else
				interm = null;
			if (!checkIntermediateType(interm))
				throw new IllegalStateException("Reversed value " + interm + " is not an instance of " + intermediateType);

			// Filter
			if (filter != null) {
				if (!filterNulls && interm == null)
					result.error = StdMsg.NULL_DISALLOWED;
				else
					result.error = filter.apply(interm);
			}
			if (result.error != null)
				return result;

			if (parent != null) {
				((FilterMapResult<I, E>) result).source = interm;
				parent.reverse((FilterMapResult<I, E>) result);
			} else
				result.result = (E) interm;
			return result;
		}
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

	/**
	 * A structure that may be used to define a collection whose elements are those of a single source collection combined with one or more
	 * values
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <V> The type of elements in the resulting collection
	 * @see ObservableCollection#combineWith(ObservableValue, TypeToken)
	 * @see ObservableCollection.CombinedCollectionBuilder3#and(ObservableValue)
	 */
	interface CombinedCollectionBuilder<E, V> {
		<T> CombinedCollectionBuilder<E, V> and(ObservableValue<T> arg);

		<T> CombinedCollectionBuilder<E, V> and(ObservableValue<T> arg, boolean combineNulls);

		CombinedCollectionBuilder<E, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends E> reverse,
			boolean reverseNulls);

		ObservableCollection<V> build(Function<? super CombinedValues<? extends E>, ? extends V> combination);

		CombinedCollectionDef<E, V> toDef(Function<? super CombinedValues<? extends E>, ? extends V> combination);
	}

	/**
	 * A structure that is operated on to produce the elements of a combined collection
	 *
	 * @param <E> The type of the source element (or the value to be reversed)
	 * @see ObservableCollection#combineWith(ObservableValue, TypeToken)
	 */
	interface CombinedValues<E> {
		E getElement();

		<T> T get(ObservableValue<T> arg);
	}

	/**
	 * Defines a combination of a single source collection with one or more observable values
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <V> The type of elements in the resulting collection
	 */
	class CombinedCollectionDef<E, V> {
		public final TypeToken<V> targetType;
		private final Map<ObservableValue<?>, Boolean> theArgs;
		private final Function<? super CombinedValues<? extends E>, ? extends V> theCombination;
		private final boolean combineCollectionNulls;
		private final Function<? super CombinedValues<? extends V>, ? extends E> theReverse;
		private final boolean reverseNulls;

		public CombinedCollectionDef(TypeToken<V> type, Map<ObservableValue<?>, Boolean> args,
			Function<? super CombinedValues<? extends E>, ? extends V> combination, boolean combineCollectionNulls,
			Function<? super CombinedValues<? extends V>, ? extends E> reverse, boolean reverseNulls, boolean copyArgs) {
			targetType = type;
			if (copyArgs) {
				Map<ObservableValue<?>, Boolean> copy = new LinkedHashMap<>(args.size() * 2); // Pad for hashing
				copy.putAll(args);
				theArgs = Collections.unmodifiableMap(copy);
			} else
				theArgs = Collections.unmodifiableMap(args);
			theCombination = combination;
			this.combineCollectionNulls = combineCollectionNulls;
			this.reverseNulls = reverseNulls;
			theReverse = reverse;
		}

		public Set<ObservableValue<?>> getArgs() {
			return theArgs.keySet();
		}

		public Function<? super CombinedValues<? extends E>, ? extends V> getCombination() {
			return theCombination;
		}

		public boolean shouldCombine(CombinedValues<? extends E> values) {
			if (!combineCollectionNulls && values.getElement() == null)
				return false;
			return shouldCombineArgs(values);
		}

		public Function<? super CombinedValues<? extends V>, ? extends E> getReverse() {
			return theReverse;
		}

		public boolean areNullsReversed() {
			return reverseNulls;
		}

		public boolean shouldCombineReverse(CombinedValues<? extends V> values) {
			if (!reverseNulls && values.getElement() == null)
				return false;
			return shouldCombineArgs(values);
		}

		private boolean shouldCombineArgs(CombinedValues<?> values) {
			for (Map.Entry<ObservableValue<?>, Boolean> arg : theArgs.entrySet()) {
				if (!arg.getValue() && values.get(arg.getKey()) == null)
					return false;
			}
			return true;
		}
	}

	/**
	 * A {@link ObservableCollection.CombinedCollectionBuilder} for the combination of a collection with a single value. Use
	 * {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <T> The type of the combined value
	 * @param <V> The type of elements in the resulting collection
	 * @see ObservableCollection#combineWith(ObservableValue, TypeToken)
	 */
	class CombinedCollectionBuilder2<E, T, V> implements CombinedCollectionBuilder<E, V> {
		private final ObservableCollection<E> theCollection;
		private final TypeToken<V> theTargetType;
		private final ObservableValue<T> theArg2;
		private Function<? super CombinedValues<? extends V>, ? extends E> theReverse;
		private boolean defaultCombineNulls = false;
		private Ternian combineCollectionNulls = Ternian.NONE;
		private Ternian combineArg2Nulls = Ternian.NONE;
		private boolean isReverseNulls = false;

		public CombinedCollectionBuilder2(ObservableCollection<E> collection, ObservableValue<T> arg2, TypeToken<V> targetType) {
			theCollection = collection;
			theArg2 = arg2;
			theTargetType = targetType;
		}

		public ObservableCollection<E> getSource() {
			return theCollection;
		}

		public ObservableValue<T> getArg2() {
			return theArg2;
		}

		public TypeToken<V> getTargetType() {
			return theTargetType;
		}

		public CombinedCollectionBuilder2<E, T, V> combineNulls(boolean combineNulls) {
			defaultCombineNulls = combineNulls;
			return this;
		}

		public CombinedCollectionBuilder2<E, T, V> combineCollectionNulls(boolean combineNulls) {
			combineCollectionNulls = Ternian.of(combineNulls);
			return this;
		}

		public CombinedCollectionBuilder2<E, T, V> combineNullArg2(boolean combineNulls) {
			combineArg2Nulls = Ternian.of(combineNulls);
			return this;
		}

		public boolean defaultNullsCombined() {
			return defaultCombineNulls;
		}

		public boolean areCollectionNullsCombined() {
			if (combineCollectionNulls.value != null)
				return combineCollectionNulls.value;
			return defaultCombineNulls;
		}

		public CombinedCollectionBuilder2<E, T, V> withReverse(BiFunction<? super V, ? super T, ? extends E> reverse,
			boolean reverseNulls) {
			return withReverse(cv -> reverse.apply(cv.getElement(), cv.get(theArg2)), reverseNulls);
		}

		@Override
		public CombinedCollectionBuilder2<E, T, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends E> reverse,
			boolean reverseNulls) {
			theReverse = reverse;
			this.isReverseNulls = reverseNulls;
			return this;
		}

		public Function<? super CombinedValues<? extends V>, ? extends E> getReverse() {
			return theReverse;
		}

		public boolean areNullsReversed() {
			return isReverseNulls;
		}

		public ObservableCollection<V> build(BiFunction<? super E, ? super T, ? extends V> combination) {
			return build(cv -> combination.apply(cv.getElement(), cv.get(theArg2)));
		}

		@Override
		public ObservableCollection<V> build(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return theCollection.combine(toDef(combination));
		}

		@Override
		public <U> CombinedCollectionBuilder3<E, T, U, V> and(ObservableValue<U> arg3) {
			if (theReverse != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedCollectionBuilder3<>(this, arg3, Ternian.NONE);
		}

		@Override
		public <U> CombinedCollectionBuilder3<E, T, U, V> and(ObservableValue<U> arg3, boolean combineNulls) {
			if (theReverse != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedCollectionBuilder3<>(this, arg3, Ternian.of(combineNulls));
		}

		public Map<ObservableValue<?>, Boolean> addArgs(Map<ObservableValue<?>, Boolean> map) {
			map.put(theArg2, areArg2NullsCombined());
			return map;
		}

		private boolean areArg2NullsCombined() {
			if (combineArg2Nulls.value != null)
				return combineArg2Nulls.value;
			return defaultCombineNulls;
		}

		@Override
		public CombinedCollectionDef<E, V> toDef(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return new CombinedCollectionDef<>(theTargetType, addArgs(new LinkedHashMap<>(2)), combination, areCollectionNullsCombined(),
				theReverse, areNullsReversed(), false);
		}
	}

	/**
	 * A {@link ObservableCollection.CombinedCollectionBuilder} for the combination of a collection with 2 values. Use
	 * {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <T> The type of the first combined value
	 * @param <U> The type of the second combined value
	 * @param <V> The type of elements in the resulting collection
	 * @see ObservableCollection#combineWith(ObservableValue, TypeToken)
	 * @see ObservableCollection.CombinedCollectionBuilder2#and(ObservableValue)
	 */
	class CombinedCollectionBuilder3<E, T, U, V> implements CombinedCollectionBuilder<E, V> {
		private final CombinedCollectionBuilder2<E, T, V> theCombine2;
		private final ObservableValue<U> theArg3;
		private final Ternian combineArg3Nulls;

		public CombinedCollectionBuilder3(CombinedCollectionBuilder2<E, T, V> combine2, ObservableValue<U> arg3, Ternian combineNulls) {
			theCombine2 = combine2;
			theArg3 = arg3;
			combineArg3Nulls = combineNulls;
		}

		public ObservableCollection<E> getSource() {
			return theCombine2.getSource();
		}

		public TypeToken<V> getTargetType() {
			return theCombine2.getTargetType();
		}

		public boolean defaultNullsCombined() {
			return theCombine2.defaultNullsCombined();
		}

		public boolean areCollectionNullsCombined() {
			return theCombine2.areCollectionNullsCombined();
		}

		public CombinedCollectionBuilder3<E, T, U, V> withReverse(TriFunction<? super V, ? super T, ? super U, ? extends E> reverse,
			boolean reverseNulls) {
			return withReverse(cv -> reverse.apply(cv.getElement(), cv.get(theCombine2.getArg2()), cv.get(theArg3)), reverseNulls);
		}

		@Override
		public CombinedCollectionBuilder3<E, T, U, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends E> reverse,
			boolean reverseNulls) {
			theCombine2.withReverse(reverse, reverseNulls);
			return this;
		}

		public Function<? super CombinedValues<? extends V>, ? extends E> getReverse() {
			return theCombine2.getReverse();
		}

		public boolean areNullsReversed() {
			return theCombine2.areNullsReversed();
		}

		public ObservableCollection<V> build(TriFunction<? super E, ? super T, ? super U, ? extends V> combination) {
			return build(cv -> combination.apply(cv.getElement(), cv.get(theCombine2.getArg2()), cv.get(theArg3)));
		}

		@Override
		public ObservableCollection<V> build(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return theCombine2.getSource().combine(toDef(combination));
		}

		@Override
		public <T2> CombinedCollectionBuilderN<E, V> and(ObservableValue<T2> arg) {
			if (theCombine2.getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedCollectionBuilderN<>(this).and(arg);
		}

		@Override
		public <T2> CombinedCollectionBuilder<E, V> and(ObservableValue<T2> arg, boolean combineNulls) {
			if (theCombine2.getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedCollectionBuilderN<>(this).and(arg, combineNulls);
		}

		private boolean areArg3NullsCombined() {
			if (combineArg3Nulls.value != null)
				return combineArg3Nulls.value;
			return theCombine2.defaultNullsCombined();
		}

		public Map<ObservableValue<?>, Boolean> addArgs(Map<ObservableValue<?>, Boolean> map) {
			theCombine2.addArgs(map);
			map.put(theArg3, areArg3NullsCombined());
			return map;
		}

		@Override
		public CombinedCollectionDef<E, V> toDef(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return new CombinedCollectionDef<>(getTargetType(), addArgs(new LinkedHashMap<>(2)), combination, areCollectionNullsCombined(),
				getReverse(), areNullsReversed(), false);
		}
	}

	/**
	 * A {@link ObservableCollection.CombinedCollectionBuilder} for the combination of a collection with one or more (typically at least 3)
	 * values. Use {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <V> The type of elements in the resulting collection
	 * @see ObservableCollection#combineWith(ObservableValue, TypeToken)
	 * @see ObservableCollection.CombinedCollectionBuilder3#and(ObservableValue)
	 */
	class CombinedCollectionBuilderN<E, V> implements CombinedCollectionBuilder<E, V> {
		private final CombinedCollectionBuilder3<E, ?, ?, V> theCombine3;
		private final Map<ObservableValue<?>, Ternian> theOtherArgs;

		public CombinedCollectionBuilderN(CombinedCollectionBuilder3<E, ?, ?, V> combine3) {
			theCombine3 = combine3;
			theOtherArgs = new LinkedHashMap<>();
		}

		public TypeToken<V> getTargetType() {
			return theCombine3.getTargetType();
		}

		public boolean areCollectionNullsCombined() {
			return theCombine3.areCollectionNullsCombined();
		}

		@Override
		public CombinedCollectionBuilder<E, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends E> reverse,
			boolean reverseNulls) {
			theCombine3.withReverse(reverse, reverseNulls);
			return this;
		}

		public Function<? super CombinedValues<? extends V>, ? extends E> getReverse() {
			return theCombine3.getReverse();
		}

		public boolean areNullsReversed() {
			return theCombine3.areNullsReversed();
		}

		@Override
		public <T> CombinedCollectionBuilderN<E, V> and(ObservableValue<T> arg) {
			if (theCombine3.getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			theOtherArgs.put(arg, Ternian.NONE);
			return this;
		}

		@Override
		public <T> CombinedCollectionBuilderN<E, V> and(ObservableValue<T> arg, boolean combineNull) {
			if (theCombine3.getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			theOtherArgs.put(arg, Ternian.of(combineNull));
			return this;
		}

		public Map<ObservableValue<?>, Boolean> addArgs(Map<ObservableValue<?>, Boolean> map) {
			theCombine3.addArgs(map);
			for (Map.Entry<ObservableValue<?>, Ternian> arg : theOtherArgs.entrySet())
				map.put(arg.getKey(), areNullsCombined(arg.getValue()));
			return map;
		}

		private boolean areNullsCombined(Ternian combineNulls) {
			if (combineNulls.value != null)
				return combineNulls.value;
			else
				return theCombine3.defaultNullsCombined();
		}

		@Override
		public ObservableCollection<V> build(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return theCombine3.getSource().combine(toDef(combination));
		}

		@Override
		public CombinedCollectionDef<E, V> toDef(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return new CombinedCollectionDef<>(getTargetType(), addArgs(new LinkedHashMap<>(2)), combination, areCollectionNullsCombined(),
				getReverse(), areNullsReversed(), false);
		}
	}

	/**
	 * Builds a modification filter that may prevent certain kinds of modification to the collection
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ModFilterBuilder<E> {
		private final ObservableCollection<E> theCollection;
		private String theImmutableMsg;
		private String theAddMsg;
		private String theRemoveMsg;
		private Function<? super E, String> theAddMsgFn;
		private Function<? super E, String> theRemoveMsgFn;

		public ModFilterBuilder(ObservableCollection<E> collection) {
			theCollection = collection;
		}

		protected ObservableCollection<E> getSource() {
			return theCollection;
		}

		public ModFilterBuilder<E> immutable(String modMsg) {
			theImmutableMsg = modMsg;
			return this;
		}

		public ModFilterBuilder<E> noAdd(String modMsg) {
			theAddMsg = modMsg;
			return this;
		}

		public ModFilterBuilder<E> noRemove(String modMsg) {
			theRemoveMsg = modMsg;
			return this;
		}

		public ModFilterBuilder<E> filterAdd(Function<? super E, String> messageFn) {
			theAddMsgFn = messageFn;
			return this;
		}

		public ModFilterBuilder<E> filterRemove(Function<? super E, String> messageFn) {
			theRemoveMsgFn = messageFn;
			return this;
		}

		public ModFilterDef<E> toDef() {
			return new ModFilterDef<>(theCollection.getType(), theImmutableMsg, theAddMsg, theRemoveMsg, theAddMsgFn, theRemoveMsgFn);
		}

		public ObservableCollection<E> build() {
			return theCollection.filterModification(toDef());
		}
	}

	/**
	 * The definition of a modification-filtered collection (minus the source)
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ModFilterDef<E> {
		private final TypeToken<E> theType;
		private final String theImmutableMsg;
		private final String theAddMsg;
		private final String theRemoveMsg;
		private final Function<? super E, String> theAddMsgFn;
		private final Function<? super E, String> theRemoveMsgFn;

		public ModFilterDef(TypeToken<E> type, String immutableMsg, String addMsg, String removeMsg, Function<? super E, String> addMsgFn,
			Function<? super E, String> removeMsgFn) {
			theType = type;
			theImmutableMsg = immutableMsg;
			theAddMsg = addMsg;
			theRemoveMsg = removeMsg;
			theAddMsgFn = addMsgFn;
			theRemoveMsgFn = removeMsgFn;
		}

		public boolean isAddFiltered() {
			return theImmutableMsg != null || theAddMsg != null || theAddMsgFn != null;
		}

		public String attemptAdd(E value) {
			String msg = null;
			if (theAddMsgFn != null)
				msg = theAddMsgFn.apply(value);
			if (msg == null && theAddMsg != null)
				msg = theAddMsg;
			if (msg == null && theImmutableMsg != null)
				msg = theImmutableMsg;
			return msg;
		}

		public boolean isRemoveFiltered() {
			return theImmutableMsg != null || theRemoveMsg != null || theRemoveMsgFn != null;
		}

		public String attemptRemove(Object value) {
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
	}

	class GroupingBuilder<E, K> {
		private final ObservableCollection<E> theCollection;
		private final TypeToken<K> theKeyType;
		private final Function<? super E, ? extends K> theKeyMaker;
		private Equivalence<? super K> theEquivalence = Equivalence.DEFAULT;
		private boolean areNullsMapped;
		private boolean areKeysDynamic;
		private boolean isBuilt;

		public GroupingBuilder(ObservableCollection<E> collection, TypeToken<K> keyType, Function<? super E, ? extends K> keyMaker) {
			theCollection = collection;
			theKeyType = keyType;
			theKeyMaker = keyMaker;

			areKeysDynamic = true;
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

		public GroupingBuilder<E, K> dynamic(boolean dynamic) {
			areKeysDynamic = dynamic;
			return this;
		}

		public boolean areKeysDynamic() {
			return areKeysDynamic;
		}

		public SortedGroupingBuilder<E, K> sorted(Comparator<? super K> compare) {
			return new SortedGroupingBuilder<>(this, compare);
		}

		public ObservableMultiMap<K, E> build() {
			isBuilt = true;
			return theCollection.groupBy(this);
		}
	}

	class SortedGroupingBuilder<E, K> {
		private final GroupingBuilder<E, K> theBasicBuilder;
		private final Comparator<? super K> theCompare;

		public SortedGroupingBuilder(GroupingBuilder<E, K> basicBuilder, Comparator<? super K> compare) {
			theBasicBuilder = basicBuilder;
			theCompare = compare;
		}

		public ObservableSortedMultiMap<K, E> build() {
			return theBasicBuilder.getCollection().groupBy(this);
		}
	}
}
