package org.observe.collect;

import static org.observe.ObservableDebug.d;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.observe.DefaultObservable;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ElementSpliterator.CollectionElement;
import org.observe.collect.ObservableCollection.FilterMapResult;
import org.observe.util.ObservableCollectionWrapper;
import org.observe.util.ObservableUtils;
import org.qommons.Equalizer;
import org.qommons.IterableUtils;
import org.qommons.ListenerSet;
import org.qommons.Transaction;
import org.qommons.collect.MultiMap.MultiEntry;
import org.qommons.collect.Qollection;
import org.qommons.collect.Quiterator;
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
 * <li><b>Modification Control</b> The {@link #filterAdd(Function)} and {@link #filterRemove(Function)} methods create collections that
 * forbid certain types of modifications to a collection. The {@link #immutable(String)} prevents any API modification at all. Modification
 * control can also be used to intercept and perform actions based on modifications to a collection.</li>
 * <li><b>Quiterator</b> Qollections must implement {@link #spliterator()}, which returns a {@link Quiterator}, which is an enhanced
 * {@link Spliterator}. This had potential for the improved performance associated with using {@link Spliterator} instead of
 * {@link Iterator} as well as the utility added by {@link Quiterator}.</li>
 * <li><b>Transactionality</b> Qollections support the {@link org.qommons.Transactable} interface, allowing callers to reserve a collection
 * for write or to ensure that the collection is not written to during an operation (for implementations that support this. See
 * {@link org.qommons.Transactable#isLockSupported() isLockSupported()}).</li>
 * <li><b>Run-time type safety</b> Qollections have a {@link #getType() type} associated with them, allowing them to enforce type-safety at
 * run time. How strictly this type-safety is enforced is implementation-dependent.</li>
 * </ul>
 *
 * @param <E> The type of element in the collection
 */
public interface ObservableCollection<E> extends TransactableCollection<E> {
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

	/**
	 * @param onElement The listener to be notified when new elements are added to the collection
	 * @return The function to call when the calling code is no longer interested in this collection
	 */
	Subscription onElement(Consumer<? super ObservableElement<E>> onElement);

	/**
	 * <p>
	 * The session allows listeners to retain state for the duration of a unit of work (controlled by implementation-specific means),
	 * batching events where possible. Not all events on a collection will have a session (the value may be null). In addition, the presence
	 * or absence of a session need not imply anything about the threaded interactions with a session. A transaction may encompass events
	 * fired and received on multiple threads. In short, the only thing guaranteed about sessions is that they will end. Therefore, if a
	 * session is present, observers may assume that they can delay expensive results of collection events until the session completes.
	 * </p>
	 * <p>
	 * In order to use the session for a listening operation, 2 observers must be installed: one for the collection, and one for the
	 * session. If an event that the observer is interested in occurs in the collection, the session value must be checked. If there is
	 * currently a session, then the session must be tagged with information that will allow later reconstruction of the interesting
	 * particulars of the event. When a session event occurs, the observer should check to see if the
	 * {@link ObservableValueEvent#getOldValue() old value} of the event is non null and whether that old session (the one that is now
	 * ending) has any information installed by the collection observer. If it does, the interesting information should be reconstructed and
	 * dealt with at that time.
	 * </p>
	 *
	 * @return The observable value for the current session of this collection
	 */
	ObservableValue<CollectionSession> getSession();

	/** @return Whether this collection is thread-safe, meaning it is constrained to only fire events on a single thread at a time */
	boolean isSafe();

	/** @return An observable collection with the same values but that only fires events on a single thread at a time */
	default ObservableCollection<E> safe() {
		if (isSafe())
			return this;
		else
			return d().debug(new SafeObservableCollection<>(this)).from("safe", this).get();
	}

	/**
	 * Tests the removability of an element from this collection. This method exposes a "best guess" on whether an element in the collection
	 * could be removed, but does not provide any guarantee. This method should return true for any object for which {@link #remove(Object)}
	 * is successful, but the fact that an object passes this test does not guarantee that it would be removed successfully. E.g. the
	 * position of the element in the collection may be a factor, but may not be tested for here.
	 *
	 * @param value The value to test removability for
	 * @return Null if given value could possibly be removed from this collection, or a message why it can't
	 */
	String canRemove(Object value);

	/**
	 * Tests the compatibility of an object with this collection. This method exposes a "best guess" on whether an element could be added to
	 * the collection , but does not provide any guarantee. This method should return true for any object for which {@link #add(Object)} is
	 * successful, but the fact that an object passes this test does not guarantee that it would be removed successfully. E.g. the position
	 * of the element in the collection may be a factor, but is tested for here.
	 *
	 * @param value The value to test compatibility for
	 * @return Null if given value could possibly be added to this collection, or a message why it can't
	 */
	String canAdd(E value);

	/**
	 * @param c collection to be checked for containment in this collection
	 * @return Whether this collection contains at least one of the elements in the specified collection
	 * @see #containsAll(Collection)
	 */
	boolean containsAny(Collection<?> c);

	// Default implementations of redundant Collection methods

	@Override
	default Iterator<E> iterator() {
		return new SpliteratorIterator<>(spliterator());
	}

	@Override
	default E[] toArray() {
		ArrayList<E> ret;
		try (Transaction t = lock(false, null)) {
			ret = new ArrayList<>(size());
			spliterator().forEachRemaining(v -> ret.add(v));
		}

		return ret.toArray((E[]) java.lang.reflect.Array.newInstance(getType().wrap().getRawType(), ret.size()));
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

	/**
	 * Searches in this collection for an element.
	 *
	 * @param filter The filter function
	 * @return A value in this list passing the filter, or empty if none of this collection's elements pass.
	 */
	default Optional<E> find(Predicate<? super E> filter) {
		try (Transaction t = lock(false, null)) {
			for (E element : this) {
				if (filter.test(element))
					return Optional.of(element);
			}
			return Optional.empty();
		}
	}

	// Derived observable changes

	/** @return An observable value for the size of this collection */
	default ObservableValue<Integer> observeSize() {
		return d().debug(new ObservableValue<Integer>() {
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
				return ObservableCollection.this.isSafe();
			}

			@Override
			public String toString() {
				return ObservableCollection.this + ".size()";
			}
		}).from("size", this).get();
	}

	/**
	 * @return An observable that fires a change event whenever any elements in it are added, removed or changed. These changes are batched
	 *         by transaction when possible.
	 */
	default Observable<? extends CollectionChangeEvent<E>> changes() {
		return d().debug(new CollectionChangesObservable<>(this)).from("changes", this).get();
	}

	/**
	 * @return An observable that fires a value (the cause event of the change) whenever anything in this collection changes. Unlike
	 *         {@link #changes()}, this observable will only fire 1 event per transaction.
	 */
	default Observable<ObservableValueEvent<?>> simpleChanges() {
		return new Observable<ObservableValueEvent<?>>() {
			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<?>> observer) {
				boolean[] initialized = new boolean[1];
				Object key = new Object();
				Subscription collSub = onElement(element -> {
					element.subscribe(new Observer<ObservableValueEvent<? extends E>>() {
						@Override
						public <V extends ObservableValueEvent<? extends E>> void onNext(V event) {
							if (!initialized[0])
								return;
							CollectionSession session = getSession().get();
							if (session == null)
								observer.onNext(event);
							else
								session.put(key, "changed", true);
						}

						@Override
						public <V extends ObservableValueEvent<? extends E>> void onCompleted(V event) {
							if (!initialized[0])
								return;
							CollectionSession session = getSession().get();
							if (session == null)
								observer.onNext(event);
							else
								session.put(key, "changed", true);
						}
					});
				});
				Subscription transSub = getSession().act(event -> {
					if (!initialized[0])
						return;
					if (event.getOldValue() != null && event.getOldValue().put(key, "changed", null) != null) {
						observer.onNext(event);
					}
				});
				initialized[0] = true;
				return () -> {
					collSub.unsubscribe();
					transSub.unsubscribe();
				};
			}

			@Override
			public boolean isSafe() {
				return ObservableCollection.this.isSafe();
			}
		};
	}

	/** @return An observable that passes along only events for removal of elements from the collection */
	default Observable<ObservableValueEvent<E>> removes() {
		ObservableCollection<E> coll = this;
		return d().debug(new Observable<ObservableValueEvent<E>>() {
			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
				return coll.onElement(element -> element.completed().act(value -> observer.onNext(value)));
			}

			@Override
			public boolean isSafe() {
				return ObservableCollection.this.isSafe();
			}

			@Override
			public String toString() {
				return "removes(" + coll + ")";
			}
		}).from("removes", this).get();
	}

	/** @return This collection, as an observable value containing an immutable collection */
	default ObservableValue<Collection<E>> asValue() {
		ObservableCollection<E> outer = this;
		return new ObservableValue<Collection<E>>() {
			final TypeToken<Collection<E>> theType = new TypeToken<Collection<E>>() {}.where(new TypeParameter<E>() {}, outer.getType());

			@Override
			public TypeToken<Collection<E>> getType() {
				return theType;
			}

			@Override
			public Collection<E> get() {
				return Collections.unmodifiableCollection(new ArrayList<>(outer));
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<Collection<E>>> observer) {
				Collection<E> [] value = new Collection[] {get()};
				Observer.onNextAndFinish(observer, createInitialEvent(value[0], null));
				return outer.simpleChanges().act(v -> {
					Collection<E> old = value[0];
					value[0] = get();
					Observer.onNextAndFinish(observer, createChangeEvent(old, value[0], null));
				});
			}

			@Override
			public boolean isSafe() {
				return outer.isSafe();
			}
		};
	}

	// Observable containment

	default <X> ObservableValue<Boolean> observeContains(ObservableValue<X> value) {}

	/**
	 * @param <X> The type of the collection to test
	 * @param collection The collection to test
	 * @return An observable boolean whose value is whether this collection contains every element of the given collection, according to
	 *         {@link Object#equals(Object)}
	 */
	default <X> ObservableValue<Boolean> observeContainsAll(ObservableCollection<X> collection) {
		return new ObservableValue<Boolean>() {
			@Override
			public TypeToken<Boolean> getType() {
				return TypeToken.of(Boolean.TYPE);
			}

			@Override
			public boolean isSafe() {
				return true;
			}

			@Override
			public Boolean get() {
				return containsAll(collection);
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<Boolean>> observer) {
				class ValueCount {
					final Object value;
					int left;
					int right;
					boolean satisfied = true;

					ValueCount(Object val) {
						value = val;
					}

					int modify(boolean add, boolean lft, int unsatisfied) {
						if (add) {
							if (lft)
								left++;
							else
								right++;
						} else {
							if (lft)
								left--;
							else
								right--;
						}
						if (satisfied) {
							if (!checkSatisfied()) {
								satisfied = false;
								return unsatisfied + 1;
							}
						} else if (unsatisfied > 0) {
							if (checkSatisfied()) {
								satisfied = true;
								return unsatisfied - 1;
							}
						}
						return unsatisfied;
					}

					private boolean checkSatisfied() {
						return left > 0 || right == 0;
					}

					boolean isEmpty() {
						return left == 0 && right == 0;
					}

					@Override
					public String toString() {
						return value + " (" + left + "/" + right + ")";
					}
				}
				Map<Object, ValueCount> allValueCounts = new HashMap<>();
				final int[] unsatisfied = new int[1];
				final int[] transUnsatisfied = new int[1];
				final boolean[] init = new boolean[] { true };
				final ReentrantLock lock = new ReentrantLock();
				abstract class ValueCountModifier {
					final void doNotify(Object cause) {
						if (init[0] || (transUnsatisfied[0] > 0) == (unsatisfied[0] > 0))
							return; // Still (un)satisfied, no change
						if (ObservableCollection.this.getSession().get() == null && collection.getSession().get() == null) {
							Observer.onNextAndFinish(observer, createChangeEvent(unsatisfied[0] == 0, transUnsatisfied[0] == 0, cause));
							unsatisfied[0] = transUnsatisfied[0];
						}
					}
				}
				class ValueCountElModifier extends ValueCountModifier implements Observer<ObservableValueEvent<?>>{
					final boolean left;
					ValueCountElModifier(boolean lft){
						left = lft;
					}

					@Override
					public <V extends ObservableValueEvent<?>> void onNext(V event) {
						if (event.isInitial() || !Objects.equals(event.getOldValue(), event.getValue())) {
							lock.lock();
							try {
								if (event.isInitial())
									modify(event.getValue(), true);
								else if (!Objects.equals(event.getOldValue(), event.getValue())) {
									modify(event.getOldValue(), false);
									modify(event.getValue(), true);
								}
								doNotify(event);
							} finally {
								lock.unlock();
							}
						}
					}

					@Override
					public <V extends ObservableValueEvent<?>> void onCompleted(V event) {
						lock.lock();
						try {
							modify(event.getValue(), false);
							doNotify(event);
						} finally {
							lock.unlock();
						}
					}

					private void modify(Object value, boolean add) {
						ValueCount count;
						if (add)
							count = allValueCounts.computeIfAbsent(value, v -> new ValueCount(v));
						else {
							count = allValueCounts.get(value);
							if (count == null)
								return;
						}
						transUnsatisfied[0] = count.modify(add, left, transUnsatisfied[0]);
						if (!add && count.isEmpty())
							allValueCounts.remove(value);
					}
				}
				class ValueCountSessModifier extends ValueCountModifier implements Observer<ObservableValueEvent<? extends CollectionSession>> {
					@Override
					public <V extends ObservableValueEvent<? extends CollectionSession>> void onNext(V event) {
						if (event.getOldValue() != null) {
							lock.lock();
							try {
								doNotify(event);
							} finally {
								lock.unlock();
							}
						}
					}
				}
				Subscription thisElSub = onElement(el -> {
					el.subscribe(new ValueCountElModifier(true));
				});
				Subscription collElSub = collection.onElement(el -> {
					el.subscribe(new ValueCountElModifier(false));
				});
				Subscription thisSessSub = getSession().subscribe(new ValueCountSessModifier());
				Subscription collSessSub = collection.getSession().subscribe(new ValueCountSessModifier());
				// Fire initial event
				lock.lock();
				try {
					unsatisfied[0] = transUnsatisfied[0];
					Observer.onNextAndFinish(observer, createInitialEvent(unsatisfied[0] == 0, null));
					init[0] = false;
				} finally {
					lock.unlock();
				}
				return Subscription.forAll(thisElSub, collElSub, thisSessSub, collSessSub);
			}
		};
	}

	default <X> ObservableValue<Boolean> observeContainsAny(ObservableCollection<X> collection) {}

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
	 * {@link MappedCollectionBuilder#build() result} will be a collection backed by this collection's values but filtered/mapped according
	 * to the methods called on the builder.
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
	 * @return The filter/mapped collection
	 */
	default <T> ObservableCollection<T> filterMap(FilterMapDef<E, ?, T> filterMap, boolean dynamic) {
		return new FilterMappedObservableCollection<>(this, filterMap, dynamic);
	}

	/**
	 * Shorthand for {@link #flatten(Qollection) flatten}({@link #map(Function) map}(Function))
	 *
	 * @param <T> The type of the values produced
	 * @param type The type of the values produced
	 * @param map The value producer
	 * @return A collection whose values are the accumulation of all those produced by applying the given function to all of this
	 *         collection's values
	 */
	default <T> ObservableCollection<T> flatMap(TypeToken<T> type, Function<? super E, ? extends ObservableCollection<? extends T>> map) {
		TypeToken<Qollection<? extends T>> collectionType;
		if (type == null) {
			collectionType = (TypeToken<Qollection<? extends T>>) TypeToken.of(map.getClass())
				.resolveType(Function.class.getTypeParameters()[1]);
			if (!collectionType.isAssignableFrom(new TypeToken<Qollection<T>>() {}))
				collectionType = new TypeToken<Qollection<? extends T>>() {};
		} else
			collectionType = new TypeToken<Qollection<? extends T>>() {}.where(new TypeParameter<T>() {}, type);
			return flatten(this.<ObservableCollection<? extends T>> buildMap(collectionType).map(map, false).build());
	}

	/**
	 * A shortcut for {@link #filterDynamic(Predicate)}
	 *
	 * @param filter The filter function
	 * @return A collection containing all non-null elements passing the given test
	 */
	default ObservableCollection<E> filter(Predicate<? super E> filter) {
		return filter(filter, false);
	}

	/**
	 * <p>
	 * Creates a collection containing the non-null elements of this collection that pass a given filter.
	 * </p>
	 *
	 * <p>
	 * The filtering is dynamic, such that if an element in the collection changes in a way that changes whether it passes the filter, the
	 * element will be added or removed from the filtered collection accordingly.
	 * </p>
	 * <p>
	 * This method is safe in that no matter what changes with the collection and their elements, the filtered collection will be updated
	 * correctly each time a relevant event is fired. However, it may perform more poorly than static filtering when many elements are
	 * filtered out.
	 * </p>
	 *
	 * @param filter The filter function
	 * @return A collection containing all non-null elements passing the given test
	 */
	default ObservableCollection<E> filterDynamic(Predicate<? super E> filter) {
		return filter(filter, false);
	}

	/**
	 * <p>
	 * Creates a collection containing the non-null elements of this collection that pass a given filter.
	 * </p>
	 *
	 * <p>
	 * The filtering is static; it is assumed that whether an element passes the test will never change. If an element in the collection
	 * changes in a way that changes whether it passes the filter, the element's presence in the filtered collection will not change.
	 * </p>
	 * <p>
	 * Also, methods that affect the contents of a single element position (e.g. {@link ObservableList#set(int, Object)} may cause problems.
	 * For example, if an element that does not match the given filter is replaced in the same position by an element that matches the
	 * filter, the new element may not show up in the filtered collection.
	 * </p>
	 * <p>
	 * This behavior allows significant performance improvements for filtered collections that exclude many elements, but it must only be
	 * used in cases where the data being filtered on is known to be constant.
	 * </p>
	 *
	 * @param filter The filter function
	 * @return A collection containing all non-null elements passing the given test
	 */
	default ObservableCollection<E> filterStatic(Predicate<? super E> filter) {
		return filter(filter, true);
	}

	/**
	 * @param filter The filter function
	 * @param staticFilter Whether the filtering on the filtered collection is to be {@link #filterStatic(Predicate) static} or
	 *            {@link #filterDynamic(Predicate) dynamic}.
	 * @return A collection containing all non-null elements passing the given test
	 */
	default ObservableCollection<E> filter(Predicate<? super E> filter, boolean staticFilter) {
		return d().label(filterMap2(getType(), value -> {
			boolean pass = filter.test(value);
			return new FilterMapResult<>(pass ? value : null, pass);
		} , value -> value, staticFilter)).tag("filter", filter).get();
	}

	/**
	 * @param <T> The type for the new collection
	 * @param type The type to filter this collection by
	 * @return A collection backed by this collection, consisting only of elements in this collection whose values are instances of the
	 *         given class
	 */
	default <T> ObservableCollection<T> filter(Class<T> type) {
		return d().label(filterMap2(TypeToken.of(type), value -> {
			if (type.isInstance(value))
				return new FilterMapResult<>(type.cast(value), true);
			else
				return new FilterMapResult<>(null, false);
		} , value -> (E) value, true)).tag("filterType", type).get();
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param filterMap The mapping function
	 * @return An observable collection of a new type backed by this collection and the mapping function
	 */
	default <T> ObservableCollection<T> filterMap(Function<? super E, T> filterMap) {
		return filterMap((TypeToken<T>) TypeToken.of(filterMap.getClass()).resolveType(Function.class.getTypeParameters()[1]), filterMap,
			null, false);
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type of the mapped collection
	 * @param map The mapping function
	 * @param staticFilter Whether the filtering on the filtered collection is to be {@link #filterStatic(Predicate) static} or
	 *            {@link #filterDynamic(Predicate) dynamic}.
	 * @return A collection containing every element in this collection for which the mapping function returns a non-null value
	 */
	default <T> ObservableCollection<T> filterMap(TypeToken<T> type, Function<? super E, T> map, boolean staticFilter) {
		return filterMap(type, map, null, staticFilter);
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type of the mapped collection
	 * @param map The mapping function
	 * @param reverse The reverse function if addition support is desired for the mapped collection
	 * @param staticFilter Whether the filtering on the filtered collection is to be {@link #filterStatic(Predicate) static} or
	 *        {@link #filterDynamic(Predicate) dynamic}.
	 * @return A collection containing every element in this collection for which the mapping function returns a non-null value
	 */
	default <T> ObservableCollection<T> filterMap(TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse,
		boolean staticFilter) {
		return filterMap2(type, value -> {
			T mapped = map.apply(value);
			return new FilterMapResult<>(mapped, mapped != null);
		} , reverse, staticFilter);
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type of the mapped collection
	 * @param map The filter/mapping function
	 * @param reverse The reverse function if addition support is desired for the mapped collection
	 * @param staticFilter Whether the filtering on the filtered collection is to be {@link #filterStatic(Predicate) static} or
	 *        {@link #filterDynamic(Predicate) dynamic}.
	 * @return A collection containing every element in this collection for which the mapping function returns a passing value
	 */
	default <T> ObservableCollection<T> filterMap2(TypeToken<T> type, Function<? super E, FilterMapResult<T>> map,
		Function<? super T, E> reverse, boolean staticFilter) {
		if(type == null)
			type = (TypeToken<T>) TypeToken.of(map.getClass()).resolveType(Function.class.getTypeParameters()[1]);
		if(staticFilter)
			return d().debug(new StaticFilteredCollection<>(this, type, map, reverse)).from("filterMap", this).using("map", map)
				.using("reverse", reverse).get();
		else
			return d().debug(new DynamicFilteredCollection<>(this, type, map, reverse)).from("filterMap", this).using("map", map)
				.using("reverse", reverse).get();
	}

	/**
	 * Searches in this collection for an element. Since an ObservableCollection's order may or may not be significant, the element
	 * reflected in the value may not be the first element in the collection (by {@link #iterator()}) to match the filter. As an
	 * optimization, subscribers to this method will not be called if an element matching the filter is inserted in this collection when a
	 * match is already present, regardless of the relative positions of the two matches. A side effect of this is that the latest value
	 * passed to the subscription and the value returned from the {@link ObservableValue#get()} method may not be the same, since the get
	 * method always returns the first match.
	 *
	 * @param filter The filter function
	 * @return A value in this list passing the filter, or null if none of this collection's elements pass.
	 */
	default ObservableValue<E> find(Predicate<E> filter) {
		return d().debug(new ObservableValue<E>() {
			private final TypeToken<E> type = ObservableCollection.this.getType().wrap();

			@Override
			public TypeToken<E> getType() {
				return type;
			}

			@Override
			public E get() {
				for(E element : ObservableCollection.this) {
					if(filter.test(element))
						return element;
				}
				return null;
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
				final Object key = new Object();
				class FindOnElement implements Consumer<ObservableElement<E>> {
					private Collection<ObservableElement<E>> theMatching = new LinkedHashSet<>();
					ObservableElement<E> theMatchingElement;
					private E theValue;

					@Override
					public void accept(ObservableElement<E> element) {
						element.subscribe(new Observer<ObservableValueEvent<E>>() {
							@Override
							public <V3 extends ObservableValueEvent<E>> void onNext(V3 value) {
								boolean hasMatch = !theMatching.isEmpty();
								boolean preMatches = hasMatch && !value.isInitial() && theMatching.contains(element);
								boolean matches = filter.test(value.getValue());
								if(matches && !preMatches)
									theMatching.add(element);
								if(!hasMatch && matches)
									newBest(element, value.getValue());
								else if(preMatches && !matches) {
									theMatching.remove(element);
									if(theMatchingElement == element)
										findNextBest();
								}
							}

							@Override
							public <V3 extends ObservableValueEvent<E>> void onCompleted(V3 value) {
								theMatching.remove(element);
								if(theMatchingElement == element)
									findNextBest();
							}

							private void findNextBest() {
								if(theMatching.isEmpty())
									newBest(null, null);
								else {
									theMatchingElement = theMatching.iterator().next();
									newBest(theMatchingElement, theMatchingElement.get());
								}
							}
						});
					}

					void newBest(ObservableElement<E> element, E value) {
						theMatchingElement = element;
						E oldValue = theValue;
						theValue = value;
						CollectionSession session = getSession().get();
						if(session == null)
							Observer.onNextAndFinish(observer, createChangeEvent(oldValue, theValue, null));
						else {
							session.putIfAbsent(key, "oldBest", oldValue);
							session.put(key, "newBest", theValue);
						}
					}
				}
				FindOnElement collOnEl = new FindOnElement();
				Subscription collSub = ObservableCollection.this.onElement(collOnEl);
				Subscription transSub = getSession().subscribe(new Observer<ObservableValueEvent<CollectionSession>>() {
					@Override
					public <V2 extends ObservableValueEvent<CollectionSession>> void onNext(V2 value) {
						CollectionSession completed = value.getOldValue();
						if(completed == null)
							return;
						E oldBest = (E) completed.get(key, "oldBest");
						E newBest = (E) completed.get(key, "newBest");
						if(oldBest == null && newBest == null)
							return;
						Observer.onNextAndFinish(observer, createChangeEvent(oldBest, newBest, value));
					}
				});
				if(collOnEl.theMatchingElement == null) // If no initial match, fire an initial null
					Observer.onNextAndFinish(observer, createInitialEvent(null, null));
				return () -> {
					collSub.unsubscribe();
					transSub.unsubscribe();
				};
			}

			@Override
			public boolean isSafe() {
				return ObservableCollection.this.isSafe();
			}

			@Override
			public String toString() {
				return "find in " + ObservableCollection.this;
			}
		}).from("find", this).using("filter", filter).get();
	}

	/** @return An observable value containing the only value in this collection while its size==1, otherwise null TODO TEST ME! */
	default ObservableValue<E> only() {
		return d().debug(new ObservableValue<E>() {
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
				return ObservableCollection.this.isSafe();
			}
		}).from("only", this).get();
	}

	/**
	 * @param <T> The type of the argument value
	 * @param <V> The type of the new observable collection
	 * @param arg The value to combine with each of this collection's elements
	 * @param func The combination function to apply to this collection's elements and the given value
	 * @return An observable collection containing this collection's elements combined with the given argument
	 */
	default <T, V> ObservableCollection<V> combine(ObservableValue<T> arg, BiFunction<? super E, ? super T, V> func) {
		return combine(arg, (TypeToken<V>) TypeToken.of(func.getClass()).resolveType(BiFunction.class.getTypeParameters()[2]), func);
	}

	/**
	 * @param <T> The type of the argument value
	 * @param <V> The type of the new observable collection
	 * @param arg The value to combine with each of this collection's elements
	 * @param type The type for the new collection
	 * @param func The combination function to apply to this collection's elements and the given value
	 * @return An observable collection containing this collection's elements combined with the given argument
	 */
	default <T, V> ObservableCollection<V> combine(ObservableValue<T> arg, TypeToken<V> type, BiFunction<? super E, ? super T, V> func) {
		return combine(arg, type, func, null);
	}

	/**
	 * @param <T> The type of the argument value
	 * @param <V> The type of the new observable collection
	 * @param arg The value to combine with each of this collection's elements
	 * @param type The type for the new collection
	 * @param func The combination function to apply to this collection's elements and the given value
	 * @param reverse The reverse function if addition support is desired for the combined collection
	 * @return An observable collection containing this collection's elements combined with the given argument
	 */
	default <T, V> ObservableCollection<V> combine(ObservableValue<T> arg, TypeToken<V> type, BiFunction<? super E, ? super T, V> func,
		BiFunction<? super V, ? super T, E> reverse) {
		return d().debug(new CombinedObservableCollection<>(this, type, arg, func, reverse)).from("combine", this).from("with", arg)
			.using("combination", func).using("reverse", reverse).get();
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
		return d().debug(new ObservableValue<T>() {
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
				return ObservableCollection.this.isSafe();
			}

			@Override
			public String toString() {
				return "reduce " + ObservableCollection.this;
			}
		}).from("reduce", this).using("add", add).using("remove", remove).get();
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
		} , null);
	}

	/**
	 * @param <K> The type of the key
	 * @param keyMap The mapping function to group this collection's values by
	 * @return A multi-map containing each of this collection's elements, each in the collection of the value mapped by the given function
	 *         applied to the element
	 */
	default <K> ObservableMultiMap<K, E> groupBy(Function<E, K> keyMap) {
		return groupBy(keyMap, (org.qommons.Equalizer)Objects::equals);
	}

	/**
	 * @param <K> The type of the key
	 * @param keyMap The mapping function to group this collection's values by
	 * @param equalizer The equalizer to use to group the keys
	 * @return A multi-map containing each of this collection's elements, each in the collection of the value mapped by the given function
	 *         applied to the element
	 */
	default <K> ObservableMultiMap<K, E> groupBy(Function<E, K> keyMap, Equalizer equalizer) {
		return groupBy(null, keyMap, equalizer);
	}

	/**
	 * @param equalizer The equalizer to group the values by
	 * @return A multi-map containing each of this collection's elements, each in the collection of one value that it matches according to
	 *         the equalizer
	 */
	default ObservableMultiMap<E, E> groupBy(Equalizer equalizer) {
		return groupBy(getType(), null, equalizer);
	}

	/**
	 * @param <K> The type of the key
	 * @param keyType The type of the key
	 * @param keyMap The mapping function to group this collection's values by
	 * @param equalizer The equalizer to use to group the keys
	 * @return A multi-map containing each of this collection's elements, each in the collection of the value mapped by the given function
	 *         applied to the element
	 */
	default <K> ObservableMultiMap<K, E> groupBy(TypeToken<K> keyType, Function<E, K> keyMap, Equalizer equalizer) {
		return d().debug(new GroupedMultiMap<>(this, keyMap, keyType, equalizer)).from("grouped", this).using("keyMap", keyMap)
			.using("equalizer", equalizer).get();
	}

	/**
	 * @param <K> The type of the key
	 * @param keyMap The mapping function to group this collection's values by
	 * @param compare The comparator to use to sort the keys
	 * @return A sorted multi-map containing each of this collection's elements, each in the collection of the value mapped by the given
	 *         function applied to the element
	 */
	default <K> ObservableSortedMultiMap<K, E> groupBy(Function<E, K> keyMap, Comparator<? super K> compare) {
		return groupBy(null, keyMap, compare);
	}

	/**
	 * @param compare The comparator to use to group the value
	 * @return A sorted multi-map containing each of this collection's elements, each in the collection of one value that it matches
	 *         according to the comparator
	 */
	default ObservableSortedMultiMap<E, E> groupBy(Comparator<? super E> compare) {
		return groupBy(getType(), null, compare);
	}

	/**
	 * TODO TEST ME!
	 *
	 * @param <K> The type of the key
	 * @param keyType The type of the key
	 * @param keyMap The mapping function to group this collection's values by
	 * @param compare The comparator to use to sort the keys
	 * @return A sorted multi-map containing each of this collection's elements, each in the collection of the value mapped by the given
	 *         function applied to the element
	 */
	default <K> ObservableSortedMultiMap<K, E> groupBy(TypeToken<K> keyType, Function<E, K> keyMap, Comparator<? super K> compare) {
		return d().debug(new GroupedSortedMultiMap<>(this, keyMap, keyType, compare)).from("grouped", this).using("keyMap", keyMap)
			.using("compare", compare).get();
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A collection whose elements fire additional value events when the given observable fires
	 */
	default ObservableCollection<E> refresh(Observable<?> refresh) {
		return d().debug(new RefreshingCollection<>(this, refresh)).from("refresh", this).from("on", refresh).get();
	}

	/**
	 * @param refire A function that supplies a refresh observable as a function of element value
	 * @return A collection whose values individually refresh when the observable returned by the given function fires
	 */
	default ObservableCollection<E> refreshEach(Function<? super E, Observable<?>> refire) {
		return d().debug(new ElementRefreshingCollection<>(this, refire)).from("refreshEach", this).using("on", refire).get();
	}

	/** @return An observable collection that cannot be modified directly but reflects the value of this collection as it changes */
	default ObservableCollection<E> immutable() {
		return d().debug(new ImmutableObservableCollection<>(this)).from("immutable", this).get();
	}

	/**
	 * Creates a wrapper collection for which removals are filtered
	 *
	 * @param filter The filter to check removals with
	 * @return The removal-filtered collection
	 */
	default ObservableCollection<E> filterRemove(Predicate<? super E> filter) {
		return filterModification(filter, null);
	}

	/**
	 * Creates a wrapper collection for which removals are rejected with an {@link IllegalStateException}
	 *
	 * @return The removal-disabled collection
	 */
	default ObservableCollection<E> noRemove() {
		return filterModification(value -> {
			throw new IllegalStateException("This collection does not allow removal");
		}, null);
	}

	/**
	 * Creates a wrapper collection for which additions are filtered
	 *
	 * @param filter The filter to check additions with
	 * @return The addition-filtered collection
	 */
	default ObservableCollection<E> filterAdd(Predicate<? super E> filter) {
		return filterModification(null, filter);
	}

	/**
	 * Creates a wrapper collection for which additions are rejected with an {@link IllegalStateException}
	 *
	 * @return The addition-disabled collection
	 */
	default ObservableCollection<E> noAdd() {
		return filterModification(null, value -> {
			throw new IllegalStateException("This collection does not allow addition");
		});
	}

	/**
	 * Creates a wrapper around this collection that can filter items that are attempted to be added or removed from it. If the filter
	 * returns true, the addition/removal is allowed. If it returns false, the addition/removal is silently rejected. The filter is also
	 * allowed to throw an exception, in which case the operation as a whole will fail. In the case of batch operations like
	 * {@link #addAll(Collection) addAll} or {@link #removeAll(Collection) removeAll}, if the filter throws an exception on any item, the
	 * collection will not be changed. Note that for filters that can return false, silently failing to add or remove items may break the
	 * contract for the collection type.
	 *
	 * @param removeFilter The filter to test items being removed from the collection. If null, removals will not be filtered and will all
	 *            pass.
	 * @param addFilter The filter to test items being added to the collection. If null, additions will not be filtered and will all pass
	 * @return The controlled collection
	 */
	default ObservableCollection<E> filterModification(Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
		return new ModFilteredCollection<>(this, removeFilter, addFilter);
	}

	/**
	 * Creates a collection with the same elements as this collection, but cached, such that the
	 *
	 * @return The cached collection
	 */
	default ObservableCollection<E> cached() {
		return d().debug(new SafeCachedObservableCollection<>(this)).from("cached", this).get();
	}

	/**
	 * @param until The observable to end the collection on
	 * @return A collection that mirrors this collection's values until the given observable fires a value, upon which the returned
	 *         collection's elements will be removed and collection subscriptions unsubscribed
	 */
	default ObservableCollection<E> takeUntil(Observable<?> until) {
		return d().debug(new TakenUntilObservableCollection<>(this, until, true)).from("take", this).from("until", until).get();
	}

	/**
	 * @param until The observable to unsubscribe the collection on
	 * @return A collection that mirrors this collection's values until the given observable fires a value, upon which the returned
	 *         collection's subscriptions will be removed. Unlike {@link #takeUntil(Observable)} however, the returned collection's elements
	 *         will not be removed when the observable fires.
	 */
	default ObservableCollection<E> unsubscribeOn(Observable<?> until) {
		return d().debug(new TakenUntilObservableCollection<>(this, until, true)).from("take", this).from("until", until).get();
	}

	/**
	 * @param type The type of the collection
	 * @param collection The collection
	 * @return An immutable collection with the same values as those in the given collection
	 */
	public static <E> ObservableCollection<E> constant(TypeToken<E> type, Collection<E> collection){
		return d().debug(new ConstantObservableCollection<>(type, collection)).get();
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
		return d().debug(new FlattenedValuesCollection<>(collection)).from("flatten", collection).get();
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> ObservableCollection<E> flattenValue(ObservableValue<? extends ObservableCollection<E>> collectionObservable) {
		return d().debug(new FlattenedValueCollection<>(collectionObservable)).from("flatten", collectionObservable).get();
	}

	/**
	 * @param <E> The super-type of elements in the inner collections
	 * @param coll The collection to flatten
	 * @return A collection containing all elements of all collections in the outer collection
	 */
	public static <E> ObservableCollection<E> flatten(ObservableCollection<? extends ObservableCollection<? extends E>> coll) {
		return d().debug(new FlattenedObservableCollection<E>(coll)).from("flatten", coll).get();
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
		return d().debug(new Observable<T>() {
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
		}).from("fold", coll).get();
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
	public static boolean equals(ObservableCollection<?> coll, Object o) {
		if (!(o instanceof Collection))
			return false;
		Collection<?> c = (Collection<?>) o;

		Iterator<?> e1 = coll.iterator();
		Iterator<?> e2 = c.iterator();
		while (e1.hasNext() && e2.hasNext()) {
			Object o1 = e1.next();
			Object o2 = e2.next();
			if (!Objects.equals(o1, o2))
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
	 * An iterator backed by a Quiterator
	 *
	 * @param <E> The type of elements to iterate over
	 */
	class SpliteratorIterator<E> implements Iterator<E> {
		private final ElementSpliterator<E> theSpliterator;

		private boolean isNextCached;
		private boolean isDone;
		private CollectionElement<? extends E> cachedNext;

		public SpliteratorIterator(ElementSpliterator<E> spliterator) {
			theSpliterator = spliterator;
		}

		@Override
		public boolean hasNext() {
			cachedNext = null;
			if (!isNextCached && !isDone) {
				if (theSpliterator.tryAdvanceElement(element -> {
					cachedNext = element;
				}))
					isNextCached = true;
				else
					isDone = true;
			}
			return isNextCached;
		}

		@Override
		public E next() {
			if (!hasNext())
				throw new java.util.NoSuchElementException();
			isNextCached = false;
			return cachedNext.get();
		}

		@Override
		public void remove() {
			if (cachedNext == null)
				throw new IllegalStateException(
					"First element has not been read, element has already been removed, or iterator has finished");
			if (isNextCached)
				throw new IllegalStateException("remove() must be called after next() and before the next call to hasNext()");
			cachedNext.remove();
			cachedNext = null;
		}

		@Override
		public void forEachRemaining(Consumer<? super E> action) {
			if (isNextCached)
				action.accept(next());
			cachedNext = null;
			isDone = true;
			theSpliterator.forEachRemaining(action);
		}
	}

	/**
	 * Static implementations to Collection methods that may be implemented in a generic way. These are not implemented as default methods
	 * In order to highly encourage subclasses to implement them in a more performant way if possible.
	 */
	class DefaultCollectionMethods {
		public static boolean contains(ObservableCollection<?> coll, Object value) {
			try (Transaction t = coll.lock(false, null)) {
				ElementSpliterator<?> iter = coll.spliterator();
				boolean[] found = new boolean[1];
				while (!found[0] && iter.tryAdvance(v -> {
					if (Objects.equals(v, value))
						found[0] = true;
				})) {
				}
				return found[0];
			}
		}

		public static boolean containsAny(ObservableCollection<?> coll, Collection<?> values) {
			try (Transaction t = coll.lock(false, null)) {
				for (Object o : values)
					if (coll.contains(o))
						return true;
			}
			return false;
		}

		public static boolean containsAll(ObservableCollection<?> coll, Collection<?> values) {
			if (values.isEmpty())
				return true;
			ArrayList<Object> copy = new ArrayList<>(values);
			BitSet found = new BitSet(copy.size());
			try (Transaction t = coll.lock(false, null)) {
				ElementSpliterator<?> iter = coll.spliterator();
				boolean[] foundOne = new boolean[1];
				while (iter.tryAdvance(next -> {
					int stop = found.previousClearBit(copy.size());
					for (int i = found.nextClearBit(0); i < stop; i = found.nextClearBit(i + 1))
						if (Objects.equals(next, copy.get(i))) {
							found.set(i);
							foundOne[0] = true;
						}
				})) {
					if (foundOne[0] && found.cardinality() == copy.size()) {
						break;
					}
					foundOne[0] = false;
				}
				return found.cardinality() == copy.size();
			}
		}

		public static <E> boolean addAll(ObservableCollection<E> coll, Collection<? extends E> values) {
			boolean mod = false;
			try (Transaction t = coll.lock(true, null)) {
				for (E o : values)
					mod |= coll.add(o);
			}
			return mod;
		}

		public static boolean removeAll(ObservableCollection<?> coll, Collection<?> values) {
			if (values.isEmpty())
				return false;
			try (Transaction t = coll.lock(true, null)) {
				boolean modified = false;
				Iterator<?> it = coll.iterator();
				while (it.hasNext()) {
					if (values.contains(it.next())) {
						it.remove();
						modified = true;
					}
				}
				return modified;
			}
		}

		public static boolean retainAll(ObservableCollection<?> coll, Collection<?> values) {
			if (values.isEmpty()) {
				coll.clear();
				return false;
			}
			try (Transaction t = coll.lock(true, null)) {
				boolean modified = false;
				Iterator<?> it = coll.iterator();
				while (it.hasNext()) {
					if (!values.contains(it.next())) {
						it.remove();
						modified = true;
					}
				}
				return modified;
			}
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

		protected ObservableCollection<E> getQollection() {
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
	 * Used to query {@link Qollection.FilterMapDef}
	 *
	 * @see Qollection.FilterMapDef#checkSourceValue(FilterMapResult)
	 * @see Qollection.FilterMapDef#map(FilterMapResult)
	 * @see Qollection.FilterMapDef#reverse(FilterMapResult)
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
	 * Implements {@link ObservableCollection#safe()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class SafeObservableCollection<E> extends ObservableCollectionWrapper<E> {
		private final ReentrantLock theLock;

		protected SafeObservableCollection(ObservableCollection<E> wrap) {
			super(wrap);
			theLock = new ReentrantLock();
		}

		protected ReentrantLock getLock() {
			return theLock;
		}

		@Override
		public TypeToken<E> getType() {
			return getWrapped().getType();
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return new ObservableValue<CollectionSession>() {
				@Override
				public TypeToken<CollectionSession> getType() {
					return getWrapped().getSession().getType();
				}

				@Override
				public CollectionSession get() {
					return getWrapped().getSession().get();
				}

				@Override
				public boolean isSafe() {
					return true;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<CollectionSession>> observer) {
					ObservableValue<CollectionSession> sessionObservable = this;
					return getWrapped().getSession().subscribe(new Observer<ObservableValueEvent<CollectionSession>>() {
						@Override
						public <V extends ObservableValueEvent<CollectionSession>> void onNext(V event) {
							theLock.lock();
							try {
								Observer.onNextAndFinish(observer, ObservableUtils.wrap(event, sessionObservable));
							} finally {
								theLock.unlock();
							}
						}

						@Override
						public <V extends ObservableValueEvent<CollectionSession>> void onCompleted(V event) {
							theLock.lock();
							try {
								Observer.onCompletedAndFinish(observer, ObservableUtils.wrap(event, sessionObservable));
							} finally {
								theLock.unlock();
							}
						}
					});
				}
			};
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return getWrapped().lock(write, cause);
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return getWrapped().onElement(element -> {
				theLock.lock();
				try {
					onElement.accept(wrapElement(element));
				} finally {
					theLock.unlock();
				}
			});
		}

		protected ObservableElement<E> wrapElement(ObservableElement<E> wrap) {
			return new ObservableElement<E>() {
				@Override
				public TypeToken<E> getType() {
					return wrap.getType();
				}

				@Override
				public E get() {
					return wrap.get();
				}

				@Override
				public boolean isSafe() {
					return true;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
					ObservableElement<E> wrapper = this;
					return wrap.subscribe(new Observer<ObservableValueEvent<E>>() {
						@Override
						public <V extends ObservableValueEvent<E>> void onNext(V event) {
							theLock.lock();
							try {
								Observer.onNextAndFinish(observer, ObservableUtils.wrap(event, wrapper));
							} finally {
								theLock.unlock();
							}
						}

						@Override
						public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
							theLock.lock();
							try {
								Observer.onCompletedAndFinish(observer, ObservableUtils.wrap(event, wrapper));
							} finally {
								theLock.unlock();
							}
						}
					});
				}

				@Override
				public ObservableValue<E> persistent() {
					return wrap.persistent();
				}
			};
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableCollection#map(Function)}
	 *
	 * @param <E> The type of the collection to map
	 * @param <T> The type of the mapped collection
	 */
	class MappedObservableCollection<E, T> implements PartialCollectionImpl<T> {
		private final ObservableCollection<E> theWrapped;

		private final TypeToken<T> theType;
		private final Function<? super E, T> theMap;
		private final Function<? super T, E> theReverse;

		protected MappedObservableCollection(ObservableCollection<E> wrap, TypeToken<T> type, Function<? super E, T> map,
			Function<? super T, E> reverse) {
			theWrapped = wrap;
			theType = type != null ? type : (TypeToken<T>) TypeToken.of(map.getClass()).resolveType(Function.class.getTypeParameters()[1]);
			theMap = map;
			theReverse = reverse;
		}

		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		protected Function<? super E, T> getMap() {
			return theMap;
		}

		protected Function<? super T, E> getReverse() {
			return theReverse;
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public Iterator<T> iterator() {
			return map(theWrapped.iterator());
		}

		protected Iterator<T> map(Iterator<E> iter) {
			return new Iterator<T>() {
				@Override
				public boolean hasNext() {
					return iter.hasNext();
				}

				@Override
				public T next() {
					return theMap.apply(iter.next());
				}

				@Override
				public void remove() {
					iter.remove();
				}
			};
		}

		@Override
		public boolean add(T e) {
			if(theReverse == null)
				return PartialCollectionImpl.super.add(e);
			else
				return theWrapped.add(theReverse.apply(e));
		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			if(theReverse == null)
				return PartialCollectionImpl.super.addAll(c);
			else
				return theWrapped.addAll(c.stream().map(theReverse).collect(Collectors.toList()));
		}

		@Override
		public boolean remove(Object o) {
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while(iter.hasNext()) {
					E el = iter.next();
					if(Objects.equals(getMap().apply(el), o)) {
						iter.remove();
						return true;
					}
				}
			}
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean ret = false;
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while(iter.hasNext()) {
					E el = iter.next();
					if(c.contains(getMap().apply(el))) {
						iter.remove();
						ret = true;
					}
				}
			}
			return ret;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean ret = false;
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while(iter.hasNext()) {
					E el = iter.next();
					if(!c.contains(getMap().apply(el))) {
						iter.remove();
						ret = true;
					}
				}
			}
			return ret;
		}

		@Override
		public void clear() {
			getWrapped().clear();
		}

		@Override
		public boolean canRemove(Object value) {
			if (theReverse != null && (value == null || theType.getRawType().isInstance(value)))
				return theWrapped.canRemove(theReverse.apply((T) value));
			else
				return false;
		}

		@Override
		public boolean canAdd(T value) {
			if (theReverse != null)
				return theWrapped.canAdd(theReverse.apply(value));
			else
				return false;
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<T>> onElement) {
			return theWrapped.onElement(element -> onElement.accept(element.mapV(theMap)));
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	/**
	 * The result of a filter/map operation
	 *
	 * @param <T> The type of the mapped value
	 */
	class FilterMapResult<T> {
		/** The mapped result */
		public final T mapped;
		/** Whether the value passed the filter */
		public final boolean passed;

		/**
		 * @param _mapped The mapped result
		 * @param _passed Whether the value passed the filter
		 */
		public FilterMapResult(T _mapped, boolean _passed) {
			mapped = _mapped;
			passed = _passed;
		}
	}

	/**
	 * Implements {@link #filterMap(TypeToken, Function, Function, boolean)}
	 *
	 * @param <E> The type of the collection to filter/map
	 * @param <T> The type of the filter/mapped collection
	 */
	abstract class FilteredCollection<E, T> implements PartialCollectionImpl<T> {
		private final ObservableCollection<E> theWrapped;
		private final TypeToken<T> theType;
		private final Function<? super E, FilterMapResult<T>> theMap;
		private final Function<? super T, E> theReverse;

		FilteredCollection(ObservableCollection<E> wrap, TypeToken<T> type, Function<? super E, FilterMapResult<T>> map,
			Function<? super T, E> reverse) {
			theWrapped = wrap;
			theType = type != null ? type : (TypeToken<T>) TypeToken.of(map.getClass()).resolveType(Function.class.getTypeParameters()[1]);
			theMap = map;
			theReverse = reverse;
		}

		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		protected Function<? super E, FilterMapResult<T>> getMap() {
			return theMap;
		}

		protected Function<? super T, E> getReverse() {
			return theReverse;
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public int size() {
			int ret = 0;
			for(E el : theWrapped)
				if (theMap.apply(el).passed)
					ret++;
			return ret;
		}

		@Override
		public Iterator<T> iterator() {
			return filter(theWrapped.iterator());
		}

		@Override
		public boolean add(T e) {
			if(theReverse == null)
				return PartialCollectionImpl.super.add(e);
			else {
				E reversed = theReverse.apply(e);
				if (!theMap.apply(reversed).passed)
					throw new IllegalArgumentException("The value " + e + " is not acceptable in this mapped list");
				return theWrapped.add(reversed);
			}
		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			if(theReverse == null)
				return PartialCollectionImpl.super.addAll(c);
			else {
				List<E> toAdd = c.stream().map(theReverse).collect(Collectors.toList());
				for(E value : toAdd)
					if (!theMap.apply(value).passed)
						throw new IllegalArgumentException("Value " + value + " is not acceptable in this mapped list");
				return theWrapped.addAll(toAdd);
			}
		}

		@Override
		public boolean remove(Object o) {
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while(iter.hasNext()) {
					E el = iter.next();
					FilterMapResult<T> mapped = getMap().apply(el);
					if (mapped.passed && Objects.equals(mapped.mapped, o)) {
						iter.remove();
						return true;
					}
				}
			}
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean ret = false;
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while(iter.hasNext()) {
					E el = iter.next();
					FilterMapResult<T> mapped = getMap().apply(el);
					if (mapped.passed && c.contains(mapped.mapped)) {
						iter.remove();
						ret = true;
					}
				}
			}
			return ret;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean ret = false;
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while(iter.hasNext()) {
					E el = iter.next();
					FilterMapResult<T> mapped = getMap().apply(el);
					if (mapped.passed && !c.contains(mapped.mapped)) {
						iter.remove();
						ret = true;
					}
				}
			}
			return ret;
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = getWrapped().iterator();
				while(iter.hasNext()) {
					E el = iter.next();
					FilterMapResult<T> mapped = getMap().apply(el);
					if (mapped.passed) {
						iter.remove();
					}
				}
			}
		}

		@Override
		public boolean canRemove(Object value) {
			if (theReverse != null && (value == null || theType.getRawType().isInstance(value)))
				return theWrapped.canRemove(theReverse.apply((T) value));
			else
				return false;
		}

		@Override
		public boolean canAdd(T value) {
			if (theReverse != null)
				return theWrapped.canAdd(theReverse.apply(value));
			else
				return false;
		}

		protected Iterator<T> filter(Iterator<E> iter) {
			return new Iterator<T>() {
				private FilterMapResult<T> nextVal;

				@Override
				public boolean hasNext() {
					while ((nextVal == null || !nextVal.passed) && iter.hasNext()) {
						nextVal = theMap.apply(iter.next());
					}
					return nextVal != null && nextVal.passed;
				}

				@Override
				public T next() {
					if ((nextVal == null || !nextVal.passed) && !hasNext())
						throw new java.util.NoSuchElementException();
					T ret = nextVal.mapped;
					nextVal = null;
					return ret;
				}

				@Override
				public void remove() {
					iter.remove();
				}
			};
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableCollection#filterMap(Function)} for static filtering
	 *
	 * @param <E> The type of the collection to be filter-mapped
	 * @param <T> The type of the mapped collection
	 */
	class StaticFilteredCollection<E, T> extends FilteredCollection<E, T> {
		public StaticFilteredCollection(ObservableCollection<E> wrap, TypeToken<T> type, Function<? super E, FilterMapResult<T>> map,
			Function<? super T, E> reverse) {
			super(wrap, type, map, reverse);
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<T>> observer) {
			return getWrapped().onElement(element -> {
				if (getMap().apply(element.get()).passed)
					observer.accept(element.mapV(value -> getMap().apply(value).mapped));
			});
		}
	}

	/**
	 * Implements {@link ObservableCollection#filterMap(Function)} for dynamic filtering
	 *
	 * @param <E> The type of the collection to be filter-mapped
	 * @param <T> The type of the mapped collection
	 */
	class DynamicFilteredCollection<E, T> extends FilteredCollection<E, T> {
		DynamicFilteredCollection(ObservableCollection<E> wrap, TypeToken<T> type, Function<? super E, FilterMapResult<T>> map,
			Function<? super T, E> reverse) {
			super(wrap, type, map, reverse);
		}

		protected DynamicFilteredElement<E, T> filter(ObservableElement<E> element, Object meta) {
			return d().debug(new DynamicFilteredElement<>(element, getMap(), getType())).from("element", this).tag("wrapped", element).get();
		}

		protected Subscription onElement(Consumer<? super ObservableElement<T>> onElement, Object meta) {
			SimpleObservable<Void> unSubObs = new SimpleObservable<>();
			Subscription collSub = getWrapped().onElement(element -> {
				DynamicFilteredElement<E, T> retElement = filter(element, meta);
				element.unsubscribeOn(unSubObs).act(elValue -> {
					if(!retElement.isIncluded()) {
						FilterMapResult<T> mapped = getMap().apply(elValue.getValue());
						if (mapped.passed)
							onElement.accept(retElement);
					}
				});
			});
			return () -> {
				collSub.unsubscribe();
				unSubObs.onNext(null);
			};
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<T>> onElement) {
			return onElement(onElement, null);
		}
	}

	/**
	 * The type of elements returned from {@link ObservableCollection#filterMap(Function)}
	 *
	 * @param <T> The type of this element
	 * @param <E> The type of element being wrapped
	 */
	class DynamicFilteredElement<E, T> implements ObservableElement<T> {
		private final ObservableElement<E> theWrappedElement;
		private final Function<? super E, FilterMapResult<T>> theMap;

		private final TypeToken<T> theType;

		private FilterMapResult<T> theValue;
		private boolean isIncluded;

		/**
		 * @param wrapped The element to wrap
		 * @param map The mapping function to filter on
		 * @param type The type of the element
		 */
		protected DynamicFilteredElement(ObservableElement<E> wrapped, Function<? super E, FilterMapResult<T>> map, TypeToken<T> type) {
			theWrappedElement = wrapped;
			theMap = map;
			theType = type;
		}

		@Override
		public ObservableValue<T> persistent() {
			return theWrappedElement.mapV(value -> theMap.apply(value).mapped);
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		@Override
		public T get() {
			return theValue.mapped;
		}

		/** @return The element that this filtered element wraps */
		protected ObservableElement<E> getWrapped() {
			return theWrappedElement;
		}

		/** @return The mapping function used by this element */
		protected Function<? super E, FilterMapResult<T>> getMap() {
			return theMap;
		}

		/** @return Whether this element is currently included in the filtered collection */
		protected boolean isIncluded() {
			return isIncluded;
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer2) {
			Subscription [] innerSub = new Subscription[1];
			innerSub[0] = theWrappedElement.subscribe(new Observer<ObservableValueEvent<E>>() {
				@Override
				public <V2 extends ObservableValueEvent<E>> void onNext(V2 elValue) {
					T oldValue = theValue == null ? null : theValue.mapped;
					theValue = theMap.apply(elValue.getValue());
					if (!theValue.passed) {
						if(!isIncluded)
							return;
						isIncluded = false;
						Observer.onCompletedAndFinish(observer2, createChangeEvent(oldValue, oldValue, elValue));
						if(innerSub[0] != null) {
							innerSub[0].unsubscribe();
							innerSub[0] = null;
						}
					} else {
						boolean initial = !isIncluded;
						isIncluded = true;
						if(initial)
							Observer.onNextAndFinish(observer2, createInitialEvent(theValue.mapped, elValue));
						else
							Observer.onNextAndFinish(observer2, createChangeEvent(oldValue, theValue.mapped, elValue));
					}
				}

				@Override
				public <V2 extends ObservableValueEvent<E>> void onCompleted(V2 elValue) {
					if(!isIncluded)
						return;
					T oldValue = theValue.mapped;
					Observer.onCompletedAndFinish(observer2, createChangeEvent(oldValue, oldValue, elValue));
				}
			});
			if(!isIncluded) {
				return () -> {
				};
			}
			return innerSub[0];
		}

		@Override
		public String toString() {
			return "filter(" + theWrappedElement + ")";
		}

		@Override
		public boolean isSafe() {
			return theWrappedElement.isSafe();
		}
	}

	/**
	 * Implements {@link ObservableCollection#combine(ObservableValue, BiFunction)}
	 *
	 * @param <E> The type of the collection to be combined
	 * @param <T> The type of the value to combine the collection elements with
	 * @param <V> The type of the combined collection
	 */
	class CombinedObservableCollection<E, T, V> implements PartialCollectionImpl<V> {
		private final ObservableCollection<E> theWrapped;

		private final TypeToken<V> theType;
		private final ObservableValue<T> theValue;
		private final BiFunction<? super E, ? super T, V> theMap;
		private final BiFunction<? super V, ? super T, E> theReverse;

		private final SubCollectionTransactionManager theTransactionManager;

		protected CombinedObservableCollection(ObservableCollection<E> wrap, TypeToken<V> type, ObservableValue<T> value,
			BiFunction<? super E, ? super T, V> map, BiFunction<? super V, ? super T, E> reverse) {
			theWrapped = wrap;
			theType = type;
			theValue = value;
			theMap = map;
			theReverse = reverse;

			theTransactionManager = new SubCollectionTransactionManager(theWrapped, value.noInit());
		}

		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		protected ObservableValue<T> getValue() {
			return theValue;
		}

		protected BiFunction<? super E, ? super T, V> getMap() {
			return theMap;
		}

		protected BiFunction<? super V, ? super T, E> getReverse() {
			return theReverse;
		}

		protected SubCollectionTransactionManager getManager() {
			return theTransactionManager;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theTransactionManager.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public TypeToken<V> getType() {
			return theType;
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public Iterator<V> iterator() {
			return combine(theWrapped.iterator());
		}

		@Override
		public boolean add(V e) {
			if(theReverse == null)
				return PartialCollectionImpl.super.add(e);
			else
				return theWrapped.add(theReverse.apply(e, theValue.get()));
		}

		@Override
		public boolean addAll(Collection<? extends V> c) {
			if(theReverse == null)
				return PartialCollectionImpl.super.addAll(c);
			else {
				T combineValue = theValue.get();
				return theWrapped.addAll(c.stream().map(o -> theReverse.apply(o, combineValue)).collect(Collectors.toList()));
			}
		}

		@Override
		public boolean remove(Object o) {
			try (Transaction t = lock(true, null)) {
				T combineValue = theValue.get();
				Iterator<E> iter = theWrapped.iterator();
				while(iter.hasNext()) {
					E el = iter.next();
					if(Objects.equals(getMap().apply(el, combineValue), o)) {
						iter.remove();
						return true;
					}
				}
			}
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean ret = false;
			try (Transaction t = lock(true, null)) {
				T combineValue = theValue.get();
				Iterator<E> iter = theWrapped.iterator();
				while(iter.hasNext()) {
					E el = iter.next();
					if(c.contains(getMap().apply(el, combineValue))) {
						iter.remove();
						ret = true;
					}
				}
			}
			return ret;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean ret = false;
			try (Transaction t = lock(true, null)) {
				T combineValue = theValue.get();
				Iterator<E> iter = theWrapped.iterator();
				while(iter.hasNext()) {
					E el = iter.next();
					if(!c.contains(getMap().apply(el, combineValue))) {
						iter.remove();
						ret = true;
					}
				}
			}
			return ret;
		}

		@Override
		public void clear() {
			getWrapped().clear();
		}

		@Override
		public boolean canRemove(Object value) {
			if (theReverse != null && (value == null || theType.getRawType().isInstance(value)))
				return theWrapped.canRemove(theReverse.apply((V) value, theValue.get()));
			else
				return false;
		}

		@Override
		public boolean canAdd(V value) {
			if (theReverse != null)
				return theWrapped.canAdd(theReverse.apply(value, theValue.get()));
			else
				return false;
		}

		protected Iterator<V> combine(Iterator<E> iter) {
			return new Iterator<V>() {
				@Override
				public boolean hasNext() {
					return iter.hasNext();
				}

				@Override
				public V next() {
					return theMap.apply(iter.next(), theValue.get());
				}

				@Override
				public void remove() {
					iter.remove();
				}
			};
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<V>> onElement) {
			DefaultObservable<Void> unSubObs = new DefaultObservable<>();
			Observer<Void> unSubControl = unSubObs.control(null);
			Subscription collSub = theTransactionManager.onElement(theWrapped,
				element -> onElement.accept(element.combineV(theMap, theValue).unsubscribeOn(unSubObs)), true);
			return () -> {
				unSubControl.onCompleted(null);
				collSub.unsubscribe();
			};
		}

		@Override
		public boolean isSafe() {
			return false;
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableCollection#groupBy(Function)}
	 *
	 * @param <K> The key type of the map
	 * @param <E> The value type of the map
	 */
	class GroupedMultiMap<K, E> implements ObservableMultiMap<K, E> {
		private final ObservableCollection<E> theWrapped;
		private final Function<E, K> theKeyMap;
		private final TypeToken<K> theKeyType;
		private final Equalizer theEqualizer;

		private final ObservableSet<K> theKeySet;

		GroupedMultiMap(ObservableCollection<E> wrap, Function<E, K> keyMap, TypeToken<K> keyType, Equalizer equalizer) {
			theWrapped = wrap;
			theKeyMap = keyMap;
			theKeyType = keyType != null ? keyType
				: (TypeToken<K>) TypeToken.of(keyMap.getClass()).resolveType(Function.class.getTypeParameters()[1]);
			theEqualizer = equalizer;

			ObservableCollection<K> mapped;
			if (theKeyMap != null)
				mapped = theWrapped.map(theKeyMap);
			else
				mapped = (ObservableCollection<K>) theWrapped;
			theKeySet = unique(mapped);
		}

		protected Equalizer getEqualizer() {
			return theEqualizer;
		}

		protected ObservableSet<K> unique(ObservableCollection<K> keyCollection) {
			return ObservableSet.unique(keyCollection, theEqualizer);
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theKeyType;
		}

		@Override
		public TypeToken<E> getValueType() {
			return theWrapped.getType();
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}

		@Override
		public ObservableSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public ObservableCollection<E> get(Object key) {
			return theWrapped.filter(el -> theEqualizer.equals(theKeyMap.apply(el), key));
		}

		@Override
		public ObservableSet<? extends ObservableMultiEntry<K, E>> entrySet() {
			return ObservableMultiMap.defaultEntrySet(this);
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}
	}

	/**
	 * An entry in a {@link ObservableCollection.GroupedMultiMap}
	 *
	 * @param <K> The key type of the entry
	 * @param <E> The value type of the entry
	 */
	class GroupedMultiEntry<K, E> implements ObservableMultiMap.ObservableMultiEntry<K, E> {
		private final K theKey;

		private final Function<E, K> theKeyMap;

		private final ObservableCollection<E> theElements;

		GroupedMultiEntry(K key, ObservableCollection<E> wrap, Function<E, K> keyMap) {
			theKey = key;
			theKeyMap = keyMap;
			theElements = wrap.filter(el -> Objects.equals(theKey, theKeyMap.apply(el)));
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public TypeToken<E> getType() {
			return theElements.getType();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return theElements.onElement(onElement);
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theElements.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theElements.lock(write, cause);
		}

		@Override
		public int size() {
			return theElements.size();
		}

		@Override
		public Iterator<E> iterator() {
			return theElements.iterator();
		}

		@Override
		public boolean add(E e) {
			return theElements.add(e);
		}

		@Override
		public boolean remove(Object o) {
			return theElements.remove(o);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return theElements.addAll(c);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theElements.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theElements.retainAll(c);
		}

		@Override
		public void clear() {
			theElements.clear();
		}

		@Override
		public boolean canRemove(Object value) {
			return theElements.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			return theElements.canAdd(value);
		}

		@Override
		public boolean isSafe() {
			return theElements.isSafe();
		}

		@Override
		public boolean equals(Object o) {
			if(this == o)
				return true;
			return o instanceof MultiEntry && Objects.equals(theKey, ((MultiEntry<?, ?>) o).getKey());
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theKey);
		}

		@Override
		public String toString() {
			return getKey() + "=" + ObservableCollection.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableCollection#groupBy(Function, Comparator)}
	 *
	 * @param <K> The key type of the map
	 * @param <E> The value type of the map
	 */
	class GroupedSortedMultiMap<K, E> implements ObservableSortedMultiMap<K, E> {
		private final ObservableCollection<E> theWrapped;
		private final Function<E, K> theKeyMap;
		private final TypeToken<K> theKeyType;
		private final Comparator<? super K> theCompare;

		private final ObservableSortedSet<K> theKeySet;

		GroupedSortedMultiMap(ObservableCollection<E> wrap, Function<E, K> keyMap, TypeToken<K> keyType, Comparator<? super K> compare) {
			theWrapped = wrap;
			theKeyMap = keyMap;
			theKeyType = keyType != null ? keyType
				: (TypeToken<K>) TypeToken.of(keyMap.getClass()).resolveType(Function.class.getTypeParameters()[1]);
			theCompare = compare;

			ObservableCollection<K> mapped;
			if (theKeyMap != null)
				mapped = theWrapped.map(theKeyMap);
			else
				mapped = (ObservableCollection<K>) theWrapped;
			theKeySet = unique(mapped);
		}

		@Override
		public Comparator<? super K> comparator() {
			return theCompare;
		}

		protected ObservableSortedSet<K> unique(ObservableCollection<K> keyCollection) {
			return ObservableSortedSet.unique(keyCollection, theCompare);
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theKeyType;
		}

		@Override
		public TypeToken<E> getValueType() {
			return theWrapped.getType();
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public ObservableCollection<E> get(Object key) {
			if (!theKeyType.getRawType().isInstance(key))
				return ObservableList.constant(getValueType());
			return theWrapped.filter(el -> theCompare.compare(theKeyMap.apply(el), (K) key) == 0);
		}

		@Override
		public ObservableSortedSet<? extends ObservableSortedMultiEntry<K, E>> entrySet() {
			return ObservableSortedMultiMap.defaultEntrySet(this);
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}
	}

	/**
	 * Implements {@link ObservableCollection#refresh(Observable)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	class RefreshingCollection<E> implements PartialCollectionImpl<E> {
		private final ObservableCollection<E> theWrapped;
		private final Observable<?> theRefresh;

		private final SubCollectionTransactionManager theTransactionManager;

		protected RefreshingCollection(ObservableCollection<E> wrap, Observable<?> refresh) {
			theWrapped = wrap;
			theRefresh = refresh;

			theTransactionManager = new SubCollectionTransactionManager(theWrapped, refresh);
		}

		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		protected Observable<?> getRefresh() {
			return theRefresh;
		}

		protected SubCollectionTransactionManager getManager() {
			return theTransactionManager;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theTransactionManager.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			theTransactionManager.startTransaction(cause);
			return () -> theTransactionManager.endTransaction();
		}

		@Override
		public Iterator<E> iterator() {
			return theWrapped.iterator();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			return theWrapped.canAdd(value);
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			DefaultObservable<Void> unSubObs = new DefaultObservable<>();
			Observer<Void> unSubControl = unSubObs.control(null);
			Subscription collSub = theTransactionManager.onElement(theWrapped,
				element -> onElement.accept(element.refresh(theRefresh).unsubscribeOn(unSubObs)), true);
			return () -> {
				unSubControl.onCompleted(null);
				collSub.unsubscribe();
			};
		}

		@Override
		public boolean isSafe() {
			return false;
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Implements {@link ObservableCollection#refreshEach(Function)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	class ElementRefreshingCollection<E> implements PartialCollectionImpl<E> {
		private final ObservableCollection<E> theWrapped;

		private final Function<? super E, Observable<?>> theRefresh;

		protected ElementRefreshingCollection(ObservableCollection<E> wrap, Function<? super E, Observable<?>> refresh) {
			theWrapped = wrap;
			theRefresh = refresh;
		}

		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		protected Function<? super E, Observable<?>> getRefresh() {
			return theRefresh;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public Iterator<E> iterator() {
			return theWrapped.iterator();
		}

		@Override
		public boolean canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			return theWrapped.canAdd(value);
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			DefaultObservable<Void> unSubObs = new DefaultObservable<>();
			Observer<Void> unSubControl = unSubObs.control(null);
			Subscription collSub = theWrapped.onElement(element -> onElement.accept(element.refreshForValue(theRefresh, unSubObs)));
			return () -> {
				unSubControl.onCompleted(null);
				collSub.unsubscribe();
			};
		}

		@Override
		public boolean isSafe() {
			return false;
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * An observable collection that cannot be modified directly, but reflects the value of a wrapped collection as it changes
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ImmutableObservableCollection<E> implements PartialCollectionImpl<E> {
		private final ObservableCollection<E> theWrapped;

		/** @param wrap The collection to wrap */
		protected ImmutableObservableCollection(ObservableCollection<E> wrap) {
			theWrapped = wrap;
		}

		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			if(write)
				throw new IllegalArgumentException("Immutable collections cannot be locked for writing");
			return theWrapped.lock(false, cause);
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> observer) {
			return theWrapped.onElement(observer);
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public Iterator<E> iterator() {
			return org.qommons.IterableUtils.immutableIterator(theWrapped.iterator());
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean canRemove(Object value) {
			return false;
		}

		@Override
		public boolean canAdd(E value) {
			return false;
		}

		@Override
		public ImmutableObservableCollection<E> immutable() {
			return this;
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Implements {@link ObservableCollection#filterModification(Predicate, Predicate)}
	 *
	 * @param <E> The type of the collection to control
	 */
	class ModFilteredCollection<E> implements PartialCollectionImpl<E> {
		private final ObservableCollection<E> theWrapped;

		private final Predicate<? super E> theRemoveFilter;

		private final Predicate<? super E> theAddFilter;

		public ModFilteredCollection(ObservableCollection<E> wrapped, Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
			theWrapped = wrapped;
			theRemoveFilter = removeFilter;
			theAddFilter = addFilter;
		}

		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		protected Predicate<? super E> getRemoveFilter() {
			return theRemoveFilter;
		}

		protected Predicate<? super E> getAddFilter() {
			return theAddFilter;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return theWrapped.onElement(onElement);
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public Iterator<E> iterator() {
			return new Iterator<E>() {
				private final Iterator<E> backing = theWrapped.iterator();

				private E theLast;

				@Override
				public boolean hasNext() {
					return backing.hasNext();
				}

				@Override
				public E next() {
					theLast = backing.next();
					return theLast;
				}

				@Override
				public void remove() {
					if(theRemoveFilter == null || theRemoveFilter.test(theLast))
						backing.remove();
				}
			};
		}

		@Override
		public boolean add(E value) {
			if(theAddFilter == null || theAddFilter.test(value))
				return theWrapped.add(value);
			else
				return false;
		}

		@Override
		public boolean addAll(Collection<? extends E> values) {
			if(theAddFilter != null)
				return theWrapped.addAll(values.stream().filter(theAddFilter).collect(Collectors.toList()));
			else
				return theWrapped.addAll(values);
		}

		@Override
		public boolean remove(Object value) {
			if(theRemoveFilter == null)
				return theWrapped.remove(value);

			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while(iter.hasNext()) {
					E next = iter.next();
					if(!Objects.equals(next, value))
						continue;
					if(theRemoveFilter.test(next)) {
						iter.remove();
						return true;
					} else
						return false;
				}
			}
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> values) {
			if(theRemoveFilter == null)
				return theWrapped.removeAll(values);

			BitSet remove = new BitSet();
			int i = 0;
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while(iter.hasNext()) {
					E next = iter.next();
					if(!values.contains(next))
						continue;
					if(theRemoveFilter.test(next))
						remove.set(i);
					i++;
				}

				if(!remove.isEmpty()) {
					i = 0;
					iter = theWrapped.iterator();
					while(iter.hasNext()) {
						iter.next();
						if(remove.get(i))
							iter.remove();
						i++;
					}
				}
			}
			return !remove.isEmpty();
		}

		@Override
		public boolean retainAll(Collection<?> values) {
			if(theRemoveFilter == null)
				return theWrapped.retainAll(values);

			BitSet remove = new BitSet();
			int i = 0;
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while(iter.hasNext()) {
					E next = iter.next();
					if(values.contains(next))
						continue;
					if(theRemoveFilter.test(next))
						remove.set(i);
					i++;
				}

				if(!remove.isEmpty()) {
					i = 0;
					iter = theWrapped.iterator();
					while(iter.hasNext()) {
						iter.next();
						if(remove.get(i))
							iter.remove();
						i++;
					}
				}
			}
			return !remove.isEmpty();
		}

		@Override
		public void clear() {
			if(theRemoveFilter == null) {
				theWrapped.clear();
				return;
			}

			BitSet remove = new BitSet();
			int i = 0;
			Iterator<E> iter = theWrapped.iterator();
			while(iter.hasNext()) {
				E next = iter.next();
				if(theRemoveFilter.test(next))
					remove.set(i);
				i++;
			}

			i = 0;
			iter = theWrapped.iterator();
			while(iter.hasNext()) {
				iter.next();
				if(remove.get(i))
					iter.remove();
				i++;
			}
		}

		@Override
		public boolean canRemove(Object value) {
			if (theRemoveFilter != null && (value == null || theWrapped.getType().getRawType().isInstance(value))
				&& !theRemoveFilter.test((E) value))
				return false;
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			if (theAddFilter != null && !theAddFilter.test(value))
				return false;
			return theWrapped.canAdd(value);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Implements {@link ObservableCollection#cached()} Caches the values in an observable collection. As long as this collection is being
	 * listened to, it will maintain a cache of the values in the given collection. When all observers to the collection have been
	 * unsubscribed, the cache is cleared and not maintained. If the cache is active, all access methods to this cache, including the native
	 * {@link Collection} methods, will use the cached values. If the cache is not active, the {@link Collection} methods will delegate to
	 * the wrapped collection.
	 *
	 * @param <E> The type of elements in the collection
	 */
	class SafeCachedObservableCollection<E> implements PartialCollectionImpl<E> {
		protected static class CachedElement<E> implements ObservableElement<E> {
			private final ObservableElement<E> theWrapped;
			private final ListenerSet<Observer<? super ObservableValueEvent<E>>> theElementListeners;

			private E theCachedValue;

			protected CachedElement(ObservableElement<E> wrap) {
				theWrapped = wrap;
				theElementListeners = new ListenerSet<>();
			}

			protected ObservableElement<E> getWrapped() {
				return theWrapped;
			}

			@Override
			public TypeToken<E> getType() {
				return theWrapped.getType();
			}

			@Override
			public boolean isSafe() {
				return true;
			}

			@Override
			public E get() {
				return theCachedValue;
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
				theElementListeners.add(observer);
				Observer.onNextAndFinish(observer, createInitialEvent(theCachedValue, null));
				return () -> theElementListeners.remove(observer);
			}

			@Override
			public ObservableValue<E> persistent() {
				return theWrapped.persistent();
			}

			private void newValue(ObservableValueEvent<E> event) {
				E oldValue = theCachedValue;
				theCachedValue = event.getValue();
				ObservableValueEvent<E> cachedEvent = createChangeEvent(oldValue, theCachedValue, event);
				theElementListeners.forEach(observer -> observer.onNext(cachedEvent));
				cachedEvent.finish();
			}

			private void completed(ObservableValueEvent<E> event) {
				ObservableValueEvent<E> cachedEvent = createChangeEvent(theCachedValue, theCachedValue, event);
				theElementListeners.forEach(observer -> observer.onCompleted(cachedEvent));
				cachedEvent.finish();
			}
		}

		private ObservableCollection<E> theWrapped;
		private final ListenerSet<Consumer<? super ObservableElement<E>>> theListeners;
		private final org.qommons.ConcurrentIdentityHashMap<ObservableElement<E>, CachedElement<E>> theCache;
		private final ReentrantLock theLock;
		private final Consumer<ObservableElement<E>> theWrappedOnElement;

		private Subscription theUnsubscribe;

		/** @param wrap The collection to cache */
		protected SafeCachedObservableCollection(ObservableCollection<E> wrap) {
			theWrapped = wrap;
			theListeners = new ListenerSet<>();
			theCache = new org.qommons.ConcurrentIdentityHashMap<>();
			theLock = new ReentrantLock();
			theWrappedOnElement = element -> {
				CachedElement<E> cached = d().debug(createElement(element)).from("element", this).tag("wrapped", element).get();
				d().debug(cached).from("cached", element).from("element", this);
				theCache.put(element, cached);
				element.subscribe(new Observer<ObservableValueEvent<E>>(){
					@Override
					public <V extends ObservableValueEvent<E>> void onNext(V event) {
						cached.newValue(event);
					}

					@Override
					public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
						cached.completed(event);
						theCache.remove(element);
					}
				});
				theListeners.forEach(onElement -> onElement.accept(cached));
			};

			theListeners.setUsedListener(this::setUsed);
		}

		protected ObservableCollection<E> getWrapped(){
			return theWrapped;
		}

		protected CachedElement<E> createElement(ObservableElement<E> element) {
			return new CachedElement<>(element);
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		protected Subscription addListener(Consumer<? super ObservableElement<E>> onElement) {
			theListeners.add(onElement);
			return () -> theListeners.remove(onElement);
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			Subscription ret = addListener(onElement);
			for(CachedElement<E> cached : theCache.values())
				onElement.accept(cached);
			return ret;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public Iterator<E> iterator() {
			Collection<E> ret = refresh();
			return new Iterator<E>() {
				private final Iterator<E> backing = ret.iterator();

				private E theLastRet;

				@Override
				public boolean hasNext() {
					return backing.hasNext();
				}

				@Override
				public E next() {
					return theLastRet = backing.next();
				}

				@Override
				public void remove() {
					backing.remove();
					theWrapped.remove(theLastRet);
				}
			};
		}

		@Override
		public int size() {
			Collection<E> ret = refresh();
			return ret.size();
		}

		@Override
		public boolean canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			return theWrapped.canAdd(value);
		}

		private void setUsed(boolean used) {
			if(used && theUnsubscribe == null) {
				theLock.lock();
				try {
					theCache.clear();
					theUnsubscribe = theWrapped.onElement(theWrappedOnElement);
				} finally {
					theLock.unlock();
				}
			} else if(!used && theUnsubscribe != null) {
				theUnsubscribe.unsubscribe();
				theUnsubscribe=null;
			}
		}

		protected Collection<E> refresh() {
			// If we're currently caching, then returned the cached values. Otherwise return the dynamic values.
			if(theUnsubscribe != null)
				return cachedElements().stream().map(CachedElement::get).collect(Collectors.toList());
			else
				return theWrapped;
		}

		protected Collection<? extends CachedElement<E>> cachedElements() {
			return theCache.values();
		}

		@Override
		public ObservableCollection<E> cached() {
			return this;
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Backs {@link ObservableCollection#takeUntil(Observable)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class TakenUntilObservableCollection<E> implements PartialCollectionImpl<E> {
		private final ObservableCollection<E> theWrapped;
		private final Observable<?> theUntil;
		private final boolean isTerminating;

		public TakenUntilObservableCollection(ObservableCollection<E> wrap, Observable<?> until, boolean terminate) {
			theWrapped = wrap;
			theUntil = until;
			isTerminating = terminate;
		}

		/** @return The collection that this taken until collection wraps */
		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		/** @return The observable that ends this collection */
		protected Observable<?> getUntil() {
			return theUntil;
		}

		/** @return Whether this collection's elements will be removed when the {@link #getUntil() until} observable fires */
		protected boolean isTerminating() {
			return isTerminating;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public Iterator<E> iterator() {
			return theWrapped.iterator();
		}

		@Override
		public boolean canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			return theWrapped.canAdd(value);
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			final Map<ObservableElement<E>, TakenUntilElement<E>> elements = new HashMap<>();
			Subscription[] collSub = new Subscription[] { theWrapped.onElement(element -> {
				TakenUntilElement<E> untilEl = new TakenUntilElement<>(element, isTerminating);
				elements.put(element, untilEl);
				onElement.accept(untilEl);
			}) };
			Subscription untilSub = theUntil.act(v -> {
				if (collSub[0] != null)
					collSub[0].unsubscribe();
				collSub[0] = null;
				for (TakenUntilElement<E> el : elements.values())
					el.end();
				elements.clear();
			});
			return () -> {
				if (collSub[0] != null)
					collSub[0].unsubscribe();
				collSub[0] = null;
				untilSub.unsubscribe();
			};
		}

		@Override
		public boolean isSafe() {
			return false;
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * An element in a {@link ObservableCollection.TakenUntilObservableCollection}
	 *
	 * @param <E> The type of value in the element
	 */
	class TakenUntilElement<E> implements ObservableElement<E> {
		private final ObservableElement<E> theWrapped;
		private final ObservableElement<E> theEndingElement;
		private final Observer<Void> theEndControl;
		private final boolean isTerminating;

		public TakenUntilElement(ObservableElement<E> wrap, boolean terminate) {
			theWrapped = wrap;
			isTerminating = terminate;
			DefaultObservable<Void> end = new DefaultObservable<>();
			if (isTerminating)
				theEndingElement = theWrapped.takeUntil(end);
			else
				theEndingElement = theWrapped.unsubscribeOn(end);
			theEndControl = end.control(null);
		}

		/** @return The element that this element wraps */
		protected ObservableElement<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public E get() {
			return theWrapped.get();
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
			return theEndingElement.subscribe(observer);
		}

		@Override
		public ObservableValue<E> persistent() {
			return theWrapped.persistent();
		}

		@Override
		public boolean isSafe() {
			return false;
		}

		protected void end() {
			theEndControl.onNext(null);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Implements {@link ObservableCollection#constant(TypeToken, Collection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ConstantObservableCollection<E> implements PartialCollectionImpl<E> {
		private final TypeToken<E> theType;
		private final Collection<E> theCollection;

		public ConstantObservableCollection(TypeToken<E> type, Collection<E> collection) {
			theType = type;
			theCollection = collection;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return ObservableValue.constant(TypeToken.of(CollectionSession.class), null);
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			for (E value : theCollection)
				onElement.accept(new ObservableElement<E>() {
					@Override
					public TypeToken<E> getType() {
						return theType;
					}

					@Override
					public E get() {
						return value;
					}

					@Override
					public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
						Observer.onNextAndFinish(observer, createInitialEvent(value, null));
						return () -> {
						};
					}

					@Override
					public boolean isSafe() {
						return true;
					}

					@Override
					public ObservableValue<E> persistent() {
						return this;
					}
				});
			return () -> {
			};
		}

		@Override
		public boolean canRemove(Object value) {
			return false;
		}

		@Override
		public boolean canAdd(E value) {
			return false;
		}

		@Override
		public int size() {
			return theCollection.size();
		}

		@Override
		public Iterator<E> iterator() {
			return IterableUtils.immutableIterator(theCollection.iterator());
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return () -> {
			};
		}
	}

	/**
	 * Implements {@link ObservableCollection#flattenValues(ObservableCollection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedValuesCollection<E> implements ObservableCollection.PartialCollectionImpl<E> {
		private ObservableCollection<? extends ObservableValue<? extends E>> theCollection;
		private final TypeToken<E> theType;

		protected FlattenedValuesCollection(ObservableCollection<? extends ObservableValue<? extends E>> collection) {
			theCollection = collection;
			theType = (TypeToken<E>) theCollection.getType().resolveType(ObservableValue.class.getTypeParameters()[0]);
		}

		/** @return The collection of values that this collection flattens */
		protected ObservableCollection<? extends ObservableValue<? extends E>> getWrapped() {
			return theCollection;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theCollection.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theCollection.lock(write, cause);
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public boolean isSafe() {
			return false;
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> observer) {
			return theCollection.onElement(element -> observer.accept(createFlattenedElement(element)));
		}

		protected FlattenedValueElement<E> createFlattenedElement(ObservableElement<? extends ObservableValue<? extends E>> element) {
			return new FlattenedValueElement<>(element, theType);
		}

		@Override
		public Iterator<E> iterator() {
			return new Iterator<E>() {
				private final Iterator<? extends ObservableValue<? extends E>> wrapped = theCollection.iterator();

				@Override
				public boolean hasNext() {
					return wrapped.hasNext();
				}

				@Override
				public E next() {
					return wrapped.next().get();
				}

				@Override
				public void remove() {
					wrapped.remove();
				}
			};
		}

		@Override
		public boolean canRemove(Object value) {
			return false;
		}

		@Override
		public boolean canAdd(E value) {
			return false;
		}

		@Override
		public int size() {
			return theCollection.size();
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	/**
	 * Implements elements for {@link ObservableCollection#flattenValues(ObservableCollection)}
	 *
	 * @param <E> The type value in the element
	 */
	class FlattenedValueElement<E> implements ObservableElement<E> {
		private final ObservableElement<? extends ObservableValue<? extends E>> theWrapped;
		private final TypeToken<E> theType;

		protected FlattenedValueElement(ObservableElement<? extends ObservableValue<? extends E>> wrap, TypeToken<E> type) {
			theWrapped = wrap;
			theType = type;
		}

		protected ObservableElement<? extends ObservableValue<? extends E>> getWrapped() {
			return theWrapped;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public boolean isSafe() {
			return false;
		}

		@Override
		public E get() {
			return get(theWrapped.get());
		}

		@Override
		public ObservableValue<E> persistent() {
			return ObservableValue.flatten(theWrapped.persistent());
		}

		private E get(ObservableValue<? extends E> value) {
			return value == null ? null : value.get();
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer2) {
			ObservableElement<E> retObs = this;
			return theWrapped.subscribe(new Observer<ObservableValueEvent<? extends ObservableValue<? extends E>>>() {
				@Override
				public <V2 extends ObservableValueEvent<? extends ObservableValue<? extends E>>> void onNext(V2 value) {
					if (value.getValue() != null) {
						Observable<?> until = ObservableUtils.makeUntil(theWrapped, value);
						value.getValue().takeUntil(until).act(innerEvent -> {
							Observer.onNextAndFinish(observer2, ObservableUtils.wrap(innerEvent, retObs));
						});
					} else if (value.isInitial())
						Observer.onNextAndFinish(observer2, retObs.createInitialEvent(null, value.getCause()));
					else
						Observer.onNextAndFinish(observer2, retObs.createChangeEvent(get(value.getOldValue()), null, value.getCause()));
				}

				@Override
				public <V2 extends ObservableValueEvent<? extends ObservableValue<? extends E>>> void onCompleted(V2 value) {
					if (value.isInitial())
						Observer.onCompletedAndFinish(observer2, retObs.createInitialEvent(get(value.getValue()), value.getCause()));
					else
						Observer.onCompletedAndFinish(observer2,
							retObs.createChangeEvent(get(value.getOldValue()), get(value.getValue()), value.getCause()));
				}
			});
		}
	}

	/**
	 * Implements {@link ObservableCollection#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedValueCollection<E> implements ObservableCollection.PartialCollectionImpl<E> {
		private final ObservableValue<? extends ObservableCollection<E>> theCollectionObservable;
		private final TypeToken<E> theType;

		protected FlattenedValueCollection(ObservableValue<? extends ObservableCollection<E>> collectionObservable) {
			theCollectionObservable = collectionObservable;
			theType = (TypeToken<E>) theCollectionObservable.getType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
		}

		protected ObservableValue<? extends ObservableCollection<E>> getWrapped() {
			return theCollectionObservable;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return ObservableValue.flatten(theCollectionObservable.mapV(coll -> coll.getSession()));
		}

		@Override
		public int size() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? 0 : coll.size();
		}

		@Override
		public Iterator<E> iterator() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? Collections.EMPTY_LIST.iterator() : (Iterator<E>) coll.iterator();
		}

		@Override
		public boolean canRemove(Object value) {
			ObservableCollection<E> current = theCollectionObservable.get();
			if (current == null)
				return false;
			return current.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			ObservableCollection<E> current = theCollectionObservable.get();
			if (current == null)
				return false;
			return current.canAdd(value);
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? () -> {
			} : coll.lock(write, cause);
		}

		@Override
		public boolean isSafe() {
			return false;
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			SimpleObservable<Void> unSubObs = new SimpleObservable<>();
			Subscription collSub = theCollectionObservable
				.subscribe(new Observer<ObservableValueEvent<? extends ObservableCollection<? extends E>>>() {
					@Override
					public <V extends ObservableValueEvent<? extends ObservableCollection<? extends E>>> void onNext(V event) {
						if (event.getValue() != null) {
							Observable<?> until = ObservableUtils.makeUntil(theCollectionObservable, event);
							((ObservableCollection<E>) event.getValue().takeUntil(until).unsubscribeOn(unSubObs)).onElement(onElement);
						}
					}
				});
			return () -> {
				collSub.unsubscribe();
				unSubObs.onNext(null);
			};
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableCollection#flatten(ObservableCollection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedObservableCollection<E> implements PartialCollectionImpl<E> {
		private final ObservableCollection<? extends ObservableCollection<? extends E>> theOuter;
		private final TypeToken<E> theType;
		private final CombinedCollectionSessionObservable theSession;

		protected FlattenedObservableCollection(ObservableCollection<? extends ObservableCollection<? extends E>> collection) {
			theOuter = collection;
			theType = (TypeToken<E>) theOuter.getType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
			theSession = new CombinedCollectionSessionObservable(theOuter);
		}

		protected ObservableCollection<? extends ObservableCollection<? extends E>> getOuter() {
			return theOuter;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theSession;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return CombinedCollectionSessionObservable.lock(theOuter, write, cause);
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public boolean isSafe() {
			return false;
		}

		@Override
		public int size() {
			int ret = 0;
			for (ObservableCollection<? extends E> subColl : theOuter)
				ret += subColl.size();
			return ret;
		}

		@Override
		public Iterator<E> iterator() {
			return (Iterator<E>) IterableUtils.flatten(theOuter).iterator();
		}

		@Override
		public boolean canRemove(Object value) {
			return false;
		}

		@Override
		public boolean canAdd(E value) {
			return false;
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> observer) {
			SimpleObservable<Void> unSubObs = new SimpleObservable<>();
			Subscription collSub = theOuter.onElement(element -> flattenValue((ObservableValue<ObservableCollection<E>>) element)
				.unsubscribeOn(unSubObs).onElement(observer));
			return () -> {
				collSub.unsubscribe();
				unSubObs.onNext(null);
			};
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}
}
