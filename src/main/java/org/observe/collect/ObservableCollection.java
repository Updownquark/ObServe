package org.observe.collect;

import static org.observe.ObservableDebug.d;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.datastruct.ObservableMultiMap;
import org.observe.util.ListenerSet;
import org.observe.util.ObservableUtils;
import org.observe.util.Transaction;

import prisms.lang.Type;

/**
 * A collection whose content can be observed
 *
 * @param <E> The type of element in the collection
 */
public interface ObservableCollection<E> extends TransactableCollection<E> {
	/** @return The type of elements in this collection */
	Type getType();

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

	@Override
	default boolean isEmpty() {
		return size() == 0;
	}

	@Override
	default boolean contains(Object o) {
		try (Transaction t = lock(false, null)) {
			for(Object value : this)
				if(Objects.equals(value, o))
					return true;
			return false;
		}
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		if(c.isEmpty())
			return true;
		ArrayList<Object> copy = new ArrayList<>(c);
		BitSet found = new BitSet(copy.size());
		try (Transaction t = lock(false, null)) {
			Iterator<E> iter = iterator();
			while(iter.hasNext()) {
				E next = iter.next();
				int stop = found.previousClearBit(copy.size());
				for(int i = found.nextClearBit(0); i < stop; i = found.nextClearBit(i + 1))
					if(Objects.equals(next, copy.get(i)))
						found.set(i);
			}
			return found.cardinality() == copy.size();
		}
	}

	@Override
	default E [] toArray() {
		ArrayList<E> ret = new ArrayList<>();
		try (Transaction t = lock(false, null)) {
			for(E value : this)
				ret.add(value);
		}
		Class<?> base = getType().toClass();
		if(base.isPrimitive())
			base = Type.getWrapperType(base);
		return ret.toArray((E []) java.lang.reflect.Array.newInstance(base, ret.size()));
	}

	@Override
	default <T> T [] toArray(T [] a) {
		ArrayList<E> ret = new ArrayList<>();
		try (Transaction t = lock(false, null)) {
			for(E value : this)
				ret.add(value);
		}
		return ret.toArray(a);
	}

	/** @return An observable value for the size of this collection */
	default ObservableValue<Integer> observeSize() {
		return d().debug(new ObservableValue<Integer>() {
			private final Type intType = new Type(Integer.TYPE);

			@Override
			public Type getType() {
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
							observer.onNext(sizeObs.createChangeEvent(oldSize, newSize, cause));
					}
				});
				initialized[0] = true;
				observer.onNext(sizeObs.createInitialEvent(size()));
				return sub;
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
	 * @return An observable that fires a (null) value whenever anything in this collection changes. Unlike {@link #changes()}, this
	 *         observable will only fire 1 event per transaction.
	 */
	default Observable<Void> simpleChanges() {
		return observer -> {
			boolean [] initialized = new boolean[1];
			Object key = new Object();
			Subscription collSub = onElement(element -> {
				element.subscribe(new Observer<Object>() {
					@Override
					public void onNext(Object value) {
						if(!initialized[0])
							return;
						CollectionSession session = getSession().get();
						if(session == null)
							observer.onNext(null);
						else
							session.put(key, "changed", true);
					}

					@Override
					public void onCompleted(Object value) {
						if(!initialized[0])
							return;
						CollectionSession session = getSession().get();
						if(session == null)
							observer.onNext(null);
						else
							session.put(key, "changed", true);
					}
				});
			});
			Subscription transSub = getSession().act(event -> {
				if(!initialized[0])
					return;
				if(event.getOldValue() != null && event.getOldValue().put(key, "changed", null) != null) {
					observer.onNext(null);
				}
			});
			initialized[0] = true;
			return () -> {
				collSub.unsubscribe();
				transSub.unsubscribe();
			};
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
			public String toString() {
				return "removes(" + coll + ")";
			}
		}).from("removes", this).get();
	}

	/** @return This collection, as an observable value containing an immutable collection */
	default ObservableValue<Collection<E>> asValue() {
		ObservableCollection<E> outer = this;
		return new ObservableValue<Collection<E>>() {
			final Type theType = new Type(ObservableCollection.class, outer.getType());

			@Override
			public Type getType() {
				return theType;
			}

			@Override
			public Collection<E> get() {
				return Collections.unmodifiableCollection(new ArrayList<>(outer));
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<Collection<E>>> observer) {
				Collection<E> [] value = new Collection[] {get()};
				observer.onNext(createInitialEvent(value[0]));
				return outer.simpleChanges().act(v -> {
					Collection<E> old = value[0];
					value[0] = get();
					observer.onNext(createChangeEvent(old, value[0], null));
				});
			}
		};
	}

	/**
	 * @param <T> The type of the new collection
	 * @param map The mapping function
	 * @return An observable collection of a new type backed by this collection and the mapping function
	 */
	default <T> ObservableCollection<T> map(Function<? super E, T> map) {
		return map(ObservableUtils.getReturnType(map), map);
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type for the mapped collection (may be null)
	 * @param map The mapping function to map the elements of this collection
	 * @return The mapped collection
	 */
	default <T> ObservableCollection<T> map(Type type, Function<? super E, T> map) {
		return map(type, map, null);
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type for the mapped collection (may be null)
	 * @param map The mapping function to map the elements of this collection
	 * @param reverse The reverse function if addition support is desired for the mapped collection
	 * @return The mapped collection
	 */
	default <T> ObservableCollection<T> map(Type type, Function<? super E, T> map, Function<? super T, E> reverse) {
		return d().debug(new MappedObservableCollection<>(this, type, map, reverse)).from("map", this).using("map", map)
			.using("reverse", reverse).get();
	}

	/**
	 * @param filter The filter function
	 * @return A collection containing all non-null elements passing the given test
	 */
	default ObservableCollection<E> filter(Predicate<? super E> filter) {
		return d().label(filterMap(getType(), value -> {
			return (value != null && filter.test(value)) ? value : null;
		}, value -> value)).tag("filter", filter).get();
	}

	/**
	 * @param <T> The type for the new collection
	 * @param type The type to filter this collection by
	 * @return A collection backed by this collection, consisting only of elements in this collection whose values are instances of the
	 *         given class
	 */
	default <T> ObservableCollection<T> filter(Class<T> type) {
		return d().label(filterMap(new Type(type), value -> type.isInstance(value) ? type.cast(value) : null, value -> (E) value))
			.tag("filterType", type).get();
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param filterMap The mapping function
	 * @return An observable collection of a new type backed by this collection and the mapping function
	 */
	default <T> ObservableCollection<T> filterMap(Function<? super E, T> filterMap) {
		return filterMap(ObservableUtils.getReturnType(filterMap), filterMap, null);
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type of the mapped collection
	 * @param map The mapping function
	 * @return A collection containing every element in this collection for which the mapping function returns a non-null value
	 */
	default <T> ObservableCollection<T> filterMap(Type type, Function<? super E, T> map) {
		return filterMap(type, map, null);
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type of the mapped collection
	 * @param map The mapping function
	 * @param reverse The reverse function if addition support is desired for the filtered collection
	 * @return A collection containing every element in this collection for which the mapping function returns a non-null value
	 */
	default <T> ObservableCollection<T> filterMap(Type type, Function<? super E, T> map, Function<? super T, E> reverse) {
		if(type == null)
			type = ObservableUtils.getReturnType(map);
		return d().debug(new FilteredCollection<>(this, type, map, reverse)).from("filterMap", this).using("map", map)
			.using("reverse", reverse).get();
	}

	/**
	 * Searches in this collection for an element. Since an ObservableCollection's order may or may not be significant, the element
	 * reflected in the value may not be the first element in the collection (by {@link #iterator()}) to match the filter.
	 *
	 * @param filter The filter function
	 * @return A value in this list passing the filter, or null if none of this collection's elements pass.
	 */
	default ObservableValue<E> find(Predicate<E> filter) {
		ObservableCollection<E> outer = this;
		return d().debug(new ObservableValue<E>() {
			private final Type type = outer.getType().isPrimitive() ? new Type(Type.getWrapperType(outer.getType().getBaseType())) : outer
				.getType();

			@Override
			public Type getType() {
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
				if(isEmpty())
					observer.onNext(createInitialEvent(null));
				final Object key = new Object();
				Subscription collSub = ObservableCollection.this.onElement(new Consumer<ObservableElement<E>>() {
					private E theValue;

					private boolean isFound;

					@Override
					public void accept(ObservableElement<E> element) {
						element.subscribe(new Observer<ObservableValueEvent<E>>() {
							@Override
							public <V3 extends ObservableValueEvent<E>> void onNext(V3 value) {
								if(!isFound && filter.test(value.getValue())) {
									isFound = true;
									newBest(value.getValue());
								}
							}

							@Override
							public <V3 extends ObservableValueEvent<E>> void onCompleted(V3 value) {
								if(theValue == value.getOldValue())
									findNextBest();
							}

							private void findNextBest() {
								isFound = false;
								for(E value : ObservableCollection.this) {
									if(filter.test(value)) {
										isFound = true;
										newBest(value);
										break;
									}
								}
								if(!isFound)
									newBest(null);
							}
						});
					}

					void newBest(E value) {
						E oldValue = theValue;
						theValue = value;
						CollectionSession session = getSession().get();
						if(session == null)
							observer.onNext(createChangeEvent(oldValue, theValue, null));
						else {
							session.putIfAbsent(key, "oldBest", oldValue);
							session.put(key, "newBest", theValue);
						}
					}
				});
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
						observer.onNext(createChangeEvent(oldBest, newBest, value));
					}
				});
				return () -> {
					collSub.unsubscribe();
					transSub.unsubscribe();
				};
			}

			@Override
			public String toString() {
				return "find in " + ObservableCollection.this;
			}
		}).from("find", this).using("filter", filter).get();
	}

	/**
	 * @param <T> The type of the argument value
	 * @param <V> The type of the new observable collection
	 * @param arg The value to combine with each of this collection's elements
	 * @param func The combination function to apply to this collection's elements and the given value
	 * @return An observable collection containing this collection's elements combined with the given argument
	 */
	default <T, V> ObservableCollection<V> combine(ObservableValue<T> arg, BiFunction<? super E, ? super T, V> func) {
		return combine(arg, ObservableUtils.getReturnType(func), func);
	}

	/**
	 * @param <T> The type of the argument value
	 * @param <V> The type of the new observable collection
	 * @param arg The value to combine with each of this collection's elements
	 * @param type The type for the new collection
	 * @param func The combination function to apply to this collection's elements and the given value
	 * @return An observable collection containing this collection's elements combined with the given argument
	 */
	default <T, V> ObservableCollection<V> combine(ObservableValue<T> arg, Type type, BiFunction<? super E, ? super T, V> func) {
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
	default <T, V> ObservableCollection<V> combine(ObservableValue<T> arg, Type type, BiFunction<? super E, ? super T, V> func,
		BiFunction<? super V, ? super T, E> reverse) {
		return d().debug(new CombinedObservableCollection<>(this, type, arg, func, reverse)).from("combine", this).from("with", arg)
			.using("combination", func).using("reverse", reverse).get();
	}

	/**
	 * @param <K> The type of the key
	 * @param keyMap The mapping function to group this collection's values by
	 * @return A multi-map containing each of this collection's elements, each in the collection of the value mapped by the given function
	 *         applied to the element
	 */
	default <K> ObservableMultiMap<K, E> groupBy(Function<E, K> keyMap) {
		return groupBy(null, keyMap);
	}

	/**
	 * @param <K> The type of the key
	 * @param keyType The type of the key
	 * @param keyMap The mapping function to group this collection's values by
	 * @return A multi-map containing each of this collection's elements, each in the collection of the value mapped by the given function
	 *         applied to the element
	 */
	default <K> ObservableMultiMap<K, E> groupBy(Type keyType, Function<E, K> keyMap) {
		return new GroupedMultiMap<>(this, keyMap, keyType);
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
	 * @param <T> An observable collection that contains all elements in all collections in the wrapping collection
	 * @param coll The collection to flatten
	 * @return A collection containing all elements of all collections in the outer collection
	 */
	public static <T> ObservableCollection<T> flatten(ObservableCollection<? extends ObservableCollection<? extends T>> coll) {
		class ComposedObservableCollection implements PartialCollectionImpl<T> {
			private final CombinedCollectionSessionObservable theSession = new CombinedCollectionSessionObservable(coll);

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return theSession;
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				Transaction outerLock = coll.lock(write, cause);
				Transaction [] innerLocks = new Transaction[coll.size()];
				int i = 0;
				for(ObservableCollection<? extends T> c : coll) {
					innerLocks[i++] = c.lock(write, cause);
				}
				return new Transaction() {
					private volatile boolean hasRun;

					@Override
					public void close() {
						if(hasRun)
							return;
						hasRun = true;
						for(int j = innerLocks.length - 1; j >= 0; j--)
							innerLocks[j].close();
						outerLock.close();
					}

					@Override
					protected void finalize() {
						if(!hasRun)
							close();
					}
				};
			}

			@Override
			public Type getType() {
				return coll.getType().getParamTypes().length == 0 ? new Type(Object.class) : coll.getType().getParamTypes()[0];
			}

			@Override
			public int size() {
				int ret = 0;
				for(ObservableCollection<? extends T> subColl : coll)
					ret += subColl.size();
				return ret;
			}

			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private Iterator<? extends ObservableCollection<? extends T>> outerBacking = coll.iterator();
					private Iterator<? extends T> innerBacking;

					@Override
					public boolean hasNext() {
						while((innerBacking == null || !innerBacking.hasNext()) && outerBacking.hasNext())
							innerBacking = outerBacking.next().iterator();
						return innerBacking != null && innerBacking.hasNext();
					}

					@Override
					public T next() {
						if(!hasNext())
							throw new java.util.NoSuchElementException();
						return innerBacking.next();
					}
				};
			}

			@Override
			public Subscription onElement(Consumer<? super ObservableElement<T>> observer) {
				return coll.onElement(new Consumer<ObservableElement<? extends ObservableCollection<? extends T>>>() {
					private java.util.Map<ObservableCollection<?>, Subscription> subCollSubscriptions;

					{
						subCollSubscriptions = new org.observe.util.ConcurrentIdentityHashMap<>();
					}

					@Override
					public void accept(ObservableElement<? extends ObservableCollection<? extends T>> subColl) {
						subColl.subscribe(new Observer<ObservableValueEvent<? extends ObservableCollection<? extends T>>>() {
							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableCollection<? extends T>>> void onNext(
								V2 subCollEvent) {
								if(subCollEvent.getOldValue() != null && subCollEvent.getOldValue() != subCollEvent.getValue()) {
									Subscription subCollSub = subCollSubscriptions.get(subCollEvent.getOldValue());
									if(subCollSub != null)
										subCollSub.unsubscribe();
								}
								Subscription subCollSub = subCollEvent.getValue().onElement(
									subElement -> observer.accept(d()
										.debug(new FlattenedElement<>((ObservableElement<T>) subElement, subColl))
										.from("element", ComposedObservableCollection.this).tag("wrappedCollectionElement", subColl)
										.tag("wrappedSubElement", subElement).get()));
								subCollSubscriptions.put(subCollEvent.getValue(), subCollSub);
							}

							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableCollection<? extends T>>> void onCompleted(
								V2 subCollEvent) {
								subCollSubscriptions.remove(subCollEvent.getValue()).unsubscribe();
							}
						});
					}
				});
			}
		}
		return d().debug(new ComposedObservableCollection()).from("flatten", coll).get();
	}

	/**
	 * @param <T> An observable collection that contains all elements the given collections
	 * @param colls The collections to flatten
	 * @return A collection containing all elements of the given collections
	 */
	public static <T> ObservableCollection<T> flattenCollections(ObservableCollection<? extends T>... colls) {
		return flatten(ObservableList.constant(new Type(ObservableCollection.class, new Type(Object.class, true)), colls));
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
							value.getValue().takeUntil(element.noInit()).subscribe(new Observer<T>() {
								@Override
								public <V3 extends T> void onNext(V3 value3) {
									observer.onNext(value3);
								}

								@Override
								public void onError(Throwable e) {
									observer.onError(e);
								}
							});
						}

						@Override
						public void onError(Throwable e) {
							observer.onError(e);
						}
					});
				});
				return ret;
			}

			@Override
			public String toString() {
				return "fold(" + coll + ")";
			}
		}).from("fold", coll).get();
	}

	/**
	 * An extension of ObservableCollection that implements some of the redundant methods and throws UnsupportedOperationExceptions for
	 * modifications. Mostly copied from {@link java.util.AbstractCollection}.
	 *
	 * @param <E> The type of element in the collection
	 */
	interface PartialCollectionImpl<E> extends ObservableCollection<E> {
		@Override
		default boolean add(E e) {
			throw new UnsupportedOperationException(getClass().getName() + " does not implement add(value)");
		}

		@Override
		default boolean addAll(Collection<? extends E> c) {
			try (Transaction t = lock(true, null)) {
				boolean modified = false;
				for(E e : c)
					if(add(e))
						modified = true;
				return modified;
			}
		}

		@Override
		default boolean remove(Object o) {
			try (Transaction t = lock(true, null)) {
				Iterator<E> it = iterator();
				while(it.hasNext()) {
					if(Objects.equals(it.next(), o)) {
						it.remove();
						return true;
					}
				}
				return false;
			}
		}

		@Override
		default boolean removeAll(Collection<?> c) {
			if(c.isEmpty())
				return false;
			try (Transaction t = lock(true, null)) {
				boolean modified = false;
				Iterator<?> it = iterator();
				while(it.hasNext()) {
					if(c.contains(it.next())) {
						it.remove();
						modified = true;
					}
				}
				return modified;
			}
		}

		@Override
		default boolean retainAll(Collection<?> c) {
			if(c.isEmpty()) {
				clear();
				return false;
			}
			try (Transaction t = lock(true, null)) {
				boolean modified = false;
				Iterator<E> it = iterator();
				while(it.hasNext()) {
					if(!c.contains(it.next())) {
						it.remove();
						modified = true;
					}
				}
				return modified;
			}
		}

		@Override
		default void clear() {
			try (Transaction t = lock(true, null)) {
				Iterator<E> it = iterator();
				while(it.hasNext()) {
					it.next();
					it.remove();
				}
			}
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
		private final Type theType;
		private final Function<? super E, T> theMap;
		private final Function<? super T, E> theReverse;

		protected MappedObservableCollection(ObservableCollection<E> wrap, Type type, Function<? super E, T> map,
			Function<? super T, E> reverse) {
			theWrapped = wrap;
			theType = type;
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
		public Type getType() {
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
		public Subscription onElement(Consumer<? super ObservableElement<T>> onElement) {
			return theWrapped.onElement(element -> onElement.accept(element.mapV(theMap)));
		}
	}

	/**
	 * Implements {@link ObservableCollection#filterMap(Function)}
	 *
	 * @param <E> The type of the collection to be filter-mapped
	 * @param <T> The type of the mapped collection
	 */
	class FilteredCollection<E, T> implements PartialCollectionImpl<T> {
		private final ObservableCollection<E> theWrapped;
		private final Type theType;
		private final Function<? super E, T> theMap;
		private final Function<? super T, E> theReverse;

		FilteredCollection(ObservableCollection<E> wrap, Type type, Function<? super E, T> map, Function<? super T, E> reverse) {
			theWrapped = wrap;
			theType = type;
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
		public Type getType() {
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
				if(theMap.apply(el) != null)
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
					T mapped = getMap().apply(el);
					if(mapped != null && Objects.equals(mapped, o)) {
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
					T mapped = getMap().apply(el);
					if(mapped != null && c.contains(mapped)) {
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
					T mapped = getMap().apply(el);
					if(mapped != null && !c.contains(mapped)) {
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
					T mapped = getMap().apply(el);
					if(mapped != null) {
						iter.remove();
					}
				}
			}
		}

		protected Iterator<T> filter(Iterator<E> iter) {
			return new Iterator<T>() {
				private T nextVal;

				@Override
				public boolean hasNext() {
					while(nextVal == null && iter.hasNext()) {
						nextVal = theMap.apply(iter.next());
					}
					return nextVal != null;
				}

				@Override
				public T next() {
					if(nextVal == null && !hasNext())
						throw new java.util.NoSuchElementException();
					T ret = nextVal;
					nextVal = null;
					return ret;
				}

				@Override
				public void remove() {
					iter.remove();
				}
			};
		}

		protected FilteredElement<E, T> filter(ObservableElement<E> element) {
			return d().debug(new FilteredElement<>(element, theMap, theType)).from("element", this).tag("wrapped", element).get();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<T>> observer) {
			return theWrapped.onElement(element -> {
				FilteredElement<E, T> retElement = filter(element);
				element.act(elValue -> {
					if(!retElement.isIncluded()) {
						T mapped = theMap.apply(elValue.getValue());
						if(mapped != null)
							observer.accept(retElement);
					}
				});
			});
		}
	}

	/**
	 * The type of elements returned from {@link ObservableCollection#filterMap(Function)}
	 *
	 * @param <T> The type of this element
	 * @param <E> The type of element being wrapped
	 */
	class FilteredElement<E, T> implements ObservableElement<T> {
		private final ObservableElement<E> theWrappedElement;
		private final Function<? super E, T> theMap;
		private final Type theType;

		private T theValue;
		private boolean isIncluded;

		/**
		 * @param wrapped The element to wrap
		 * @param map The mapping function to filter on
		 * @param type The type of the element
		 */
		protected FilteredElement(ObservableElement<E> wrapped, Function<? super E, T> map, Type type) {
			theWrappedElement = wrapped;
			theMap = map;
			theType = type;
		}

		@Override
		public ObservableValue<T> persistent() {
			return theWrappedElement.mapV(theMap);
		}

		@Override
		public Type getType() {
			return theType;
		}

		@Override
		public T get() {
			return theValue;
		}

		/** @return The element that this filtered element wraps */
		protected ObservableElement<E> getWrapped() {
			return theWrappedElement;
		}

		/** @return The mapping function used by this element */
		protected Function<? super E, T> getMap() {
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
					T oldValue = theValue;
					theValue = theMap.apply(elValue.getValue());
					if(theValue == null) {
						if(!isIncluded)
							return;
						isIncluded = false;
						theValue = null;
						observer2.onCompleted(createChangeEvent(oldValue, oldValue, elValue));
						if(innerSub[0] != null) {
							innerSub[0].unsubscribe();
							innerSub[0] = null;
						}
					} else {
						isIncluded = true;
						observer2.onNext(createChangeEvent(oldValue, theValue, elValue));
					}
				}

				@Override
				public <V2 extends ObservableValueEvent<E>> void onCompleted(V2 elValue) {
					if(!isIncluded)
						return;
					T oldValue = theValue;
					T newValue = elValue == null ? null : theMap.apply(elValue.getValue());
					observer2.onCompleted(createChangeEvent(oldValue, newValue, elValue));
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
		private final Type theType;
		private final ObservableValue<T> theValue;
		private final BiFunction<? super E, ? super T, V> theMap;
		private final BiFunction<? super V, ? super T, E> theReverse;

		private final SubCollectionTransactionManager theTransactionManager;

		protected CombinedObservableCollection(ObservableCollection<E> wrap, Type type, ObservableValue<T> value,
			BiFunction<? super E, ? super T, V> map, BiFunction<? super V, ? super T, E> reverse) {
			theWrapped = wrap;
			theType = type;
			theValue = value;
			theMap = map;
			theReverse = reverse;

			theTransactionManager = new SubCollectionTransactionManager(theWrapped);
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
		public Type getType() {
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
			return theTransactionManager.onElement(theWrapped, theValue, element -> onElement.accept(element.combineV(theMap, theValue)),
				true);
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

		private final Type theKeyType;

		GroupedMultiMap(ObservableCollection<E> wrap, Function<E, K> keyMap, Type keyType) {
			theWrapped = wrap;
			theKeyMap = keyMap;
			theKeyType = keyType != null ? keyType : ObservableUtils.getReturnType(keyMap);
		}

		@Override
		public Type getKeyType() {
			return theKeyType;
		}

		@Override
		public Type getValueType() {
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
		public ObservableSet<K> keySet() {
			return ObservableSet.unique(theWrapped.map(theKeyMap));
		}

		@Override
		public ObservableCollection<E> get(Object key) {
			return theWrapped.filter(el -> Objects.equals(theKeyMap.apply(el), key));
		}

		@Override
		public ObservableSet<? extends ObservableMultiEntry<K, E>> observeEntries() {
			return ObservableMultiMap.defaultObserveEntries(this);
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
		public Type getType() {
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
		public boolean equals(Object o) {
			if(this == o)
				return true;
			return o instanceof GroupedMultiEntry && Objects.equals(theKey, ((GroupedMultiEntry<?, ?>) o).theKey);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theKey);
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

			theTransactionManager = new SubCollectionTransactionManager(theWrapped);
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
		public Type getType() {
			return theWrapped.getType();
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
		public Iterator<E> iterator() {
			return theWrapped.iterator();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return theTransactionManager.onElement(theWrapped, theRefresh, element -> onElement.accept(element.refresh(theRefresh)), true);
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
		public Type getType() {
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
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return theWrapped.onElement(element -> onElement.accept(element.refreshForValue(theRefresh)));
		}
	}

	/**
	 * An element in a {@link ObservableCollection#flatten(ObservableCollection) flattened} collection
	 *
	 * @param <T> The type of the element
	 */
	class FlattenedElement<T> implements ObservableElement<T> {
		private final ObservableElement<T> subElement;

		private final ObservableElement<? extends ObservableCollection<? extends T>> subCollectionEl;
		private boolean isRemoved;

		/**
		 * @param subEl The sub-collection element to wrap
		 * @param subColl The element containing the sub-collection
		 */
		protected FlattenedElement(ObservableElement<T> subEl, ObservableElement<? extends ObservableCollection<? extends T>> subColl) {
			if(subEl == null)
				throw new NullPointerException();
			subElement = subEl;
			subCollectionEl = subColl;
			subColl.completed().act(value -> isRemoved = true);
		}

		/** @return The element in the outer collection containing the inner collection that contains this element's wrapped element */
		protected ObservableElement<? extends ObservableCollection<? extends T>> getSubCollectionElement() {
			return subCollectionEl;
		}

		/** @return The wrapped sub-collection element */
		protected ObservableElement<T> getSubElement() {
			return subElement;
		}

		@Override
		public ObservableValue<T> persistent() {
			return subElement;
		}

		/** @return Whether this element has been removed or not */
		protected boolean isRemoved() {
			return isRemoved;
		}

		@Override
		public Type getType() {
			return subElement.getType();
		}

		@Override
		public T get() {
			return subElement.get();
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer2) {
			return subElement.takeUntil(subCollectionEl.completed()).subscribe(new Observer<ObservableValueEvent<T>>() {
				@Override
				public <V extends ObservableValueEvent<T>> void onNext(V event) {
					observer2.onNext(ObservableUtils.wrap(event, FlattenedElement.this));
				}

				@Override
				public <V extends ObservableValueEvent<T>> void onCompleted(V event) {
					observer2.onCompleted(ObservableUtils.wrap(event, FlattenedElement.this));
				}
			});
		}

		@Override
		public String toString() {
			return "flattened(" + subElement.toString() + ")";
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
		public Type getType() {
			return theWrapped.getType();
		}

		@Override
		public Iterator<E> iterator() {
			return prisms.util.ArrayUtils.immutableIterator(theWrapped.iterator());
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public ImmutableObservableCollection<E> immutable() {
			return this;
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
		public Type getType() {
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
			public Type getType() {
				return theWrapped.getType();
			}

			@Override
			public E get() {
				return theCachedValue;
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
				theElementListeners.add(observer);
				observer.onNext(createInitialEvent(theCachedValue));
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
			}

			private void completed(ObservableValueEvent<E> event) {
				ObservableValueEvent<E> cachedEvent = createChangeEvent(theCachedValue, theCachedValue, event);
				theElementListeners.forEach(observer -> observer.onCompleted(cachedEvent));
			}
		}

		private ObservableCollection<E> theWrapped;
		private final ListenerSet<Consumer<? super ObservableElement<E>>> theListeners;
		private final org.observe.util.ConcurrentIdentityHashMap<ObservableElement<E>, CachedElement<E>> theCache;
		private final ReentrantLock theLock;
		private final Consumer<ObservableElement<E>> theWrappedOnElement;

		private Subscription theUnsubscribe;

		/** @param wrap The collection to cache */
		protected SafeCachedObservableCollection(ObservableCollection<E> wrap) {
			theWrapped = wrap;
			theListeners = new ListenerSet<>();
			theCache = new org.observe.util.ConcurrentIdentityHashMap<>();
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
		public Type getType() {
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
	}
}
