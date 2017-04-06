package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.qommons.Equalizer;
import org.qommons.Transaction;
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
			return new ObservableCollectionImpl.SafeObservableCollection<>(this);
	}

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
	 * @param c collection to be checked for containment in this collection
	 * @return Whether this collection contains at least one of the elements in the specified collection
	 * @see #containsAll(Collection)
	 */
	boolean containsAny(Collection<?> c);

	/**
	 * @return The equalizer that this collection uses to determine containment with {@link #contains(Object)},
	 *         {@link #containsAll(Collection)}, or {@link #containsAny(Collection)}
	 */
	default Equalizer equalizer() {
		return Equalizer.object;
	}

	/** @return Any element in this collection, or null if the collection is empty */
	default ObservableValue<E> element() {
		return new ObservableValue<E>() {
			@Override
			public TypeToken<E> getType() {
				return ObservableCollection.this.getType();
			}

			@Override
			public boolean isSafe() {
				return ObservableCollection.this.isSafe();
			}

			@Override
			public E get() {
				Object[] value = new Object[1];
				if (!spliterator().tryAdvance(v -> value[0] = v))
					return null;
				return (E) value[0];
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
				// TODO Auto-generated method stub
			}
		};
	}

	// Default implementations of redundant Collection methods

	@Override
	default Betterator<E> iterator() {
		return new ObservableCollectionImpl.SpliteratorBetterator<>(spliterator());
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
				return ObservableCollection.this.isSafe();
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
		return new Observable<ObservableValueEvent<E>>() {
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
		};
	}

	/** @return This collection, as an observable value containing an immutable collection */
	default ObservableValue<Collection<E>> asValue() {
		// TODO This is inefficient. Keep an updated list
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

	default <X> ObservableValue<Boolean> observeContains(ObservableValue<X> value) {
		return new ObservableValue<Boolean>() {
			@Override
			public TypeToken<Boolean> getType() {
				return TypeToken.of(Boolean.TYPE);
			}

			@Override
			public boolean isSafe() {
				return ObservableCollection.this.isSafe();
			}

			@Override
			public Boolean get() {
				return contains(value.get());
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<Boolean>> observer) {
				// TODO Auto-generated method stub
				return null;
			}
		};
	}

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
	 *        should be done dyamically or statically. Dynamic filtering allows for the possibility that changes to individual elements in
	 *        the collection may result in those elements passing or failing the filter. Static filtering uses the initial (on subscription)
	 *        value of the element to determine whether that element is included in the collection and this inclusion does not change.
	 *        Static filtering may offer potentially large performance improvements, particularly for filtering a small subset of a large
	 *        collection.
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
				return ObservableCollection.this.isSafe();
			}
		};
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
		return new ObservableCollectionImpl.CombinedObservableCollection<>(this, type, arg, func, reverse);
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
				return ObservableCollection.this.isSafe();
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
		return new ObservableCollectionImpl.GroupedMultiMap<>(this, keyMap, keyType, equalizer);
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
		return new ObservableCollectionImpl.GroupedSortedMultiMap<>(this, keyMap, keyType, compare);
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
	 * @param modMsg The message to return when modification is requested
	 * @return An observable collection that cannot be modified directly but reflects the value of this collection as it changes
	 */
	default ObservableCollection<E> immutable(String modMsg) {
		return filterModification(v -> modMsg, v -> modMsg);
	}

	/**
	 * Creates a wrapper collection for which removals are filtered
	 *
	 * @param filter The filter to check removals with
	 * @return The removal-filtered collection
	 */
	default ObservableCollection<E> filterRemove(Function<? super E, String> filter) {
		return filterModification(filter, null);
	}

	/**
	 * Creates a wrapper collection for which removals are rejected with an {@link IllegalStateException}
	 *
	 * @param removeMsg The message to return when removal is requested
	 * @return The removal-disabled collection
	 */
	default ObservableCollection<E> noRemove(String removeMsg) {
		return filterModification(value -> removeMsg, null);
	}

	/**
	 * Creates a wrapper collection for which additions are filtered
	 *
	 * @param filter The filter to check additions with
	 * @return The addition-filtered collection
	 */
	default ObservableCollection<E> filterAdd(Function<? super E, String> filter) {
		return filterModification(null, filter);
	}

	/**
	 * Creates a wrapper collection for which additions are rejected with an {@link IllegalStateException}
	 *
	 * @param addMsg The message to return when addition is requested
	 * @return The addition-disabled collection
	 */
	default ObservableCollection<E> noAdd(String addMsg) {
		return filterModification(null, value -> addMsg);
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
	default ObservableCollection<E> filterModification(Function<? super E, String> removeFilter, Function<? super E, String> addFilter) {
		return new ObservableCollectionImpl.ModFilteredCollection<>(this, removeFilter, addFilter);
	}

	/**
	 * Creates a collection with the same elements as this collection, but cached, such that the
	 *
	 * @return The cached collection
	 */
	default ObservableCollection<E> cached() {
		return new ObservableCollectionImpl.SafeCachedObservableCollection<>(this);
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
	 * Used to query {@link ObservableCollection.FilterMapDef}
	 *
	 * @see ObservableCollection.FilterMapDef#checkSourceValue(FilterMapResult)
	 * @see ObservableCollection.FilterMapDef#map(FilterMapResult)
	 * @see ObservableCollection.FilterMapDef#reverse(FilterMapResult)
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
}
