package org.observe.collect;

import static org.observe.ObservableDebug.d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.util.ObservableUtils;
import org.qommons.Equalizer;
import org.qommons.Transaction;
import org.qommons.tree.CountedRedBlackNode;
import org.qommons.tree.CountedRedBlackNode.DefaultNode;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeSet;

import com.google.common.reflect.TypeToken;

/**
 * An ordered collection whose content can be observed. All {@link ObservableElement}s returned by this observable will be instances of
 * {@link ObservableOrderedElement}. In addition, it is guaranteed that the {@link ObservableOrderedElement#getIndex() index} of an element
 * given to the observer passed to {@link #onElement(Consumer)} will be less than or equal to the number of uncompleted elements previously
 * passed to the observer. This means that, for example, the first element passed to an observer will always be index 0. The second may be 0
 * or 1. If one of these is then completed, the next element may be 0 or 1 as well.
 *
 * @param <E> The type of element in the collection
 */
public interface ObservableOrderedCollection<E> extends ObservableCollection<E> {
	/**
	 * @param onElement The listener to be notified when new elements are added to the collection
	 * @return The function to call when the calling code is no longer interested in this collection
	 */
	Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement);

	@Override
	default Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
		return onOrderedElement(onElement);
	}

	/**
	 * @return An observable that returns null whenever any elements in this collection are added, removed or changed. The order of events as
	 *         reported by this observable may not be the same as their occurrence in the collection. Any discrepancy will be resolved when
	 *         the transaction ends.
	 */
	@Override
	default Observable<? extends OrderedCollectionChangeEvent<E>> changes() {
		return d().debug(new OrderedCollectionChangesObservable<>(this)).from("changes", this).get();
	}

	/**
	 * @param filter The filter function
	 * @return The first value in this collection passing the filter, or null if none of this collection's elements pass
	 */
	default ObservableValue<E> findFirst(Predicate<E> filter) {
		return d().debug(new OrderedCollectionFinder<>(this, filter, true)).from("find", this).using("filter", filter).get();
	}

	/**
	 * @param filter The filter function
	 * @return The first value in this collection passing the filter, or null if none of this collection's elements pass
	 */
	default ObservableValue<E> findLast(Predicate<E> filter) {
		return d().debug(new OrderedCollectionFinder<>(this, filter, false)).from("findLast", this).using("filter", filter).get();
	}

	/** @return The first value in this collection, or null if this collection is empty */
	default ObservableValue<E> getFirst() {
		return d().debug(new OrderedCollectionFinder<>(this, value -> true, true)).from("first", this).get();
	}

	/**
	 * Finds the last value in this list. The get() method of this observable may have linear time unless this is an instance of
	 * {@link ObservableRandomAccessList}
	 *
	 * @return The last value in this collection, or null if this collection is empty
	 */
	default ObservableValue<E> getLast() {
		return d().debug(new OrderedCollectionFinder<>(this, value -> true, false)).from("last", this).get();
	}

	// Ordered collections need to know the indexes of their elements in a somewhat efficient way, so these index methods make sense here

	/**
	 * @param index The index of the element to get
	 * @return The element of this collection at the given index
	 */
	default E get(int index) {
		try (Transaction t = lock(false, null)) {
			if(index < 0 || index >= size())
				throw new IndexOutOfBoundsException(index + " of " + size());
			Iterator<E> iter = iterator();
			for(int i = 0; i < index; i++)
				iter.next();
			return iter.next();
		}
	}

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the first position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	default int indexOf(Object value) {
		try (Transaction t = lock(false, null)) {
			Iterator<E> iter = iterator();
			for(int i = 0; iter.hasNext(); i++) {
				if(Objects.equals(iter.next(), value))
					return i;
			}
			return -1;
		}
	}

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the last position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	default int lastIndexOf(Object value) {
		try (Transaction t = lock(false, null)) {
			int ret = -1;
			Iterator<E> iter = iterator();
			for(int i = 0; iter.hasNext(); i++) {
				if(Objects.equals(iter.next(), value))
					ret = i;
			}
			return ret;
		}
	}

	/**
	 * @param index The index to observe the value of
	 * @param defValueGen The function to generate the value for the observable if this collection's size is {@code &lt;=index}. The
	 *        argument is the current size. This function may throw a runtime exception, such as {@link IndexOutOfBoundsException}. Null is
	 *        acceptable here, which will mean a null default value.
	 * @return The observable value at the given position in the collection
	 */
	default ObservableValue<E> observeAt(int index, Function<Integer, E> defValueGen) {
		return new PositionObservable<>(this, index, defValueGen);
	}

	@Override
	default ObservableOrderedCollection<E> safe() {
		if (isSafe())
			return this;
		else
			return d().debug(new SafeOrderedCollection<>(this)).from("safe", this).get();
	}

	@Override
	default <T> ObservableOrderedCollection<T> map(Function<? super E, T> map) {
		return (ObservableOrderedCollection<T>) ObservableCollection.super.map(map);
	}

	@Override
	default <T> ObservableOrderedCollection<T> map(TypeToken<T> type, Function<? super E, T> map) {
		return (ObservableOrderedCollection<T>) ObservableCollection.super.map(type, map);
	}

	@Override
	default <T> ObservableOrderedCollection<T> map(TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse) {
		return d().debug(new MappedOrderedCollection<>(this, type, map, reverse)).from("map", this).using("map", map)
			.using("reverse", reverse).get();
	}

	@Override
	default ObservableOrderedCollection<E> filter(Predicate<? super E> filter) {
		return (ObservableOrderedCollection<E>) ObservableCollection.super.filter(filter);
	}

	@Override
	default ObservableOrderedCollection<E> filter(Predicate<? super E> filter, boolean staticFilter) {
		return (ObservableOrderedCollection<E>) ObservableCollection.super.filter(filter, staticFilter);
	}

	@Override
	default ObservableOrderedCollection<E> filterDynamic(Predicate<? super E> filter) {
		return (ObservableOrderedCollection<E>) ObservableCollection.super.filterDynamic(filter);
	}

	@Override
	default ObservableOrderedCollection<E> filterStatic(Predicate<? super E> filter) {
		return (ObservableOrderedCollection<E>) ObservableCollection.super.filterStatic(filter);
	}

	@Override
	default <T> ObservableOrderedCollection<T> filter(Class<T> type) {
		return (ObservableOrderedCollection<T>) ObservableCollection.super.filter(type);
	}

	@Override
	default <T> ObservableOrderedCollection<T> filterMap(Function<? super E, T> filterMap) {
		return (ObservableOrderedCollection<T>) ObservableCollection.super.filterMap(filterMap);
	}

	@Override
	default <T> ObservableOrderedCollection<T> filterMap(TypeToken<T> type, Function<? super E, T> map, boolean staticFilter) {
		return (ObservableOrderedCollection<T>) ObservableCollection.super.filterMap(type, map, staticFilter);
	}

	@Override
	default <T> ObservableOrderedCollection<T> filterMap(TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse,
		boolean staticFilter) {
		return (ObservableOrderedCollection<T>) ObservableCollection.super.filterMap(type, map, reverse, staticFilter);
	}

	@Override
	default <T> ObservableOrderedCollection<T> filterMap2(TypeToken<T> type, Function<? super E, FilterMapResult<T>> map,
		Function<? super T, E> reverse, boolean staticFilter) {
		if(staticFilter)
			return d().debug(new StaticFilteredOrderedCollection<>(this, type, map, reverse)).from("filterMap", this).using("map", map)
				.using("reverse", reverse).get();
		else
			return d().debug(new DynamicFilteredOrderedCollection<>(this, type, map, reverse)).from("filterMap", this).using("map", map)
				.using("reverse", reverse).get();
	}

	@Override
	default <T, V> ObservableOrderedCollection<V> combine(ObservableValue<T> arg, BiFunction<? super E, ? super T, V> func) {
		return (ObservableOrderedCollection<V>) ObservableCollection.super.combine(arg, func);
	}

	@Override
	default <T, V> ObservableOrderedCollection<V> combine(ObservableValue<T> arg, TypeToken<V> type,
		BiFunction<? super E, ? super T, V> func) {
		return (ObservableOrderedCollection<V>) ObservableCollection.super.combine(arg, type, func);
	}

	@Override
	default <T, V> ObservableOrderedCollection<V> combine(ObservableValue<T> arg, TypeToken<V> type,
		BiFunction<? super E, ? super T, V> func,
		BiFunction<? super V, ? super T, E> reverse) {
		return d().debug(new CombinedOrderedCollection<>(this, arg, type, func, reverse)).from("combine", this).from("with", arg)
			.using("combination", func).using("reverse", reverse).get();
	}

	// @Override
	// default <K> ObservableMultiMap<K, E> groupBy(TypeToken<K> keyType, Function<E, K> keyMap, Equalizer equalizer) {
	// return d().debug(new GroupedOrderedMultiMap<>(this, keyMap, keyType, equalizer)).from("grouped", this).using("keyMap", keyMap)
	// .using("equalizer", equalizer).get();
	// }

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A collection whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableOrderedCollection<E> refresh(Observable<?> refresh) {
		return d().debug(new RefreshingOrderedCollection<>(this, refresh)).from("refresh", this).from("on", refresh).get();
	}

	@Override
	default ObservableOrderedCollection<E> refreshEach(Function<? super E, Observable<?>> refire) {
		return d().debug(new ElementRefreshingOrderedCollection<>(this, refire)).from("refreshEach", this).using("on", refire).get();
	}

	/**
	 * @param compare The comparator to use to sort this collection's elements
	 * @return A new collection containing all the same elements as this collection, but ordered according to the given comparator
	 */
	default ObservableOrderedCollection<E> sorted(Comparator<? super E> compare) {
		return d().debug(new SortedObservableCollection<>(this, compare)).from("sorted", this).using("compare", compare).get();
	}

	@Override
	default ObservableOrderedCollection<E> immutable() {
		return d().debug(new ImmutableOrderedCollection<>(this)).from("immutable", this).get();
	}

	@Override
	default ObservableOrderedCollection<E> filterRemove(Predicate<? super E> filter) {
		return (ObservableOrderedCollection<E>) ObservableCollection.super.filterRemove(filter);
	}

	@Override
	default ObservableOrderedCollection<E> noRemove() {
		return (ObservableOrderedCollection<E>) ObservableCollection.super.noRemove();
	}

	@Override
	default ObservableOrderedCollection<E> filterAdd(Predicate<? super E> filter) {
		return (ObservableOrderedCollection<E>) ObservableCollection.super.filterAdd(filter);
	}

	@Override
	default ObservableOrderedCollection<E> noAdd() {
		return (ObservableOrderedCollection<E>) ObservableCollection.super.noAdd();
	}

	@Override
	default ObservableOrderedCollection<E> filterModification(Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
		return new ModFilteredOrderedCollection<>(this, removeFilter, addFilter);
	}

	@Override
	default ObservableOrderedCollection<E> cached() {
		return d().debug(new SafeCachedOrderedCollection<>(this)).from("cached", this).get();
	}

	@Override
	default ObservableOrderedCollection<E> takeUntil(Observable<?> until) {
		return d().debug(new TakenUntilOrderedCollection<>(this, until, true)).from("taken", this).from("until", until)
			.tag("terminate", true).get();
	}

	@Override
	default ObservableOrderedCollection<E> unsubscribeOn(Observable<?> until) {
		return d().debug(new TakenUntilOrderedCollection<>(this, until, false)).from("taken", this).from("until", until)
			.tag("terminate", false).get();
	}

	/**
	 * Turns a collection of observable values into a collection composed of those holders' values
	 *
	 * @param <E> The type of elements held in the values
	 * @param collection The collection to flatten
	 * @return The flattened collection
	 */
	public static <E> ObservableOrderedCollection<E> flattenValues(
		ObservableOrderedCollection<? extends ObservableValue<? extends E>> collection) {
		return d().debug(new FlattenedOrderedValuesCollection<E>(collection)).from("flatten", collection).get();
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> ObservableOrderedCollection<E> flattenValue(
		ObservableValue<? extends ObservableOrderedCollection<E>> collectionObservable) {
		return d().debug(new FlattenedOrderedValueCollection<>(collectionObservable)).from("flatten", collectionObservable).get();
	}

	/**
	 * Flattens a collection of ordered collections
	 *
	 * @param <E> The super-type of all collections in the wrapping collection
	 * @param list The collection to flatten
	 * @return A collection containing all elements of all collections in the outer collection
	 */
	public static <E> ObservableOrderedCollection<E> flatten(ObservableOrderedCollection<? extends ObservableOrderedCollection<E>> list) {
		return d().debug(new FlattenedOrderedCollection<>(list)).from("flatten", list).get();
	}

	/**
	 * Finds something in an {@link ObservableOrderedCollection}
	 *
	 * @param <E> The type of value to find
	 */
	class OrderedCollectionFinder<E> implements ObservableValue<E> {
		private final ObservableOrderedCollection<E> theCollection;

		private final TypeToken<E> theType;

		private final Predicate<? super E> theFilter;

		private final boolean isForward;

		OrderedCollectionFinder(ObservableOrderedCollection<E> collection, Predicate<? super E> filter, boolean forward) {
			theCollection = collection.safe();
			theType = theCollection.getType().wrap();
			theFilter = filter;
			isForward = forward;
		}

		/** @return The collection that this finder searches */
		public ObservableOrderedCollection<E> getCollection() {
			return theCollection;
		}

		/** @return The function to test elements with */
		public Predicate<? super E> getFilter() {
			return theFilter;
		}

		/** @return Whether this finder searches forward or backward in the collection */
		public boolean isForward() {
			return isForward;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public E get() {
			if(isForward) {
				for(E element : theCollection) {
					if(theFilter.test(element))
						return element;
				}
				return null;
			} else {
				E ret = null;
				for(E element : theCollection) {
					if(theFilter.test(element))
						ret = element;
				}
				return ret;
			}
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
			final Object key = new Object();
			int [] index = new int[] {-1};
			Subscription collSub = theCollection.onOrderedElement(new Consumer<ObservableOrderedElement<E>>() {
				private List<ObservableOrderedElement<E>> theElements = new ArrayList<>();
				private E theValue;

				@Override
				public void accept(ObservableOrderedElement<E> element) {
					element.subscribe(new Observer<ObservableValueEvent<E>>() {
						@Override
						public <V3 extends ObservableValueEvent<E>> void onNext(V3 value) {
							int listIndex = element.getIndex();
							if (value.isInitial())
								theElements.add(listIndex, element);
							if(index[0] < 0 || isBetterIndex(listIndex, index[0])) {
								if(theFilter.test(value.getValue()))
									newBest(value.getValue(), listIndex);
								else if(listIndex == index[0])
									findNextBest(listIndex, false);
							}
						}

						@Override
						public <V3 extends ObservableValueEvent<E>> void onCompleted(V3 value) {
							int listIndex = element.getIndex();
							theElements.remove(listIndex);
							if(listIndex == index[0]) {
								findNextBest(listIndex, true);
							} else if(listIndex < index[0])
								index[0]--;
						}

						private boolean isBetterIndex(int test, int current) {
							if(isForward)
								return test <= current;
							else
								return test >= current;
						}

						private void findNextBest(int newIndex, boolean removed) {
							boolean found = false;
							if (isForward) {
								if (!removed)
									newIndex++;
								for (int i = newIndex; i < theElements.size(); i++) {
									E value = theElements.get(i).get();
									if (theFilter.test(value)) {
										found = true;
										newBest(value, i);
										break;
									}
								}
							} else {
								for (int i = newIndex - 1; i >= 0; i--) {
									E value = theElements.get(i).get();
									if (theFilter.test(value)) {
										found = true;
										newBest(value, i);
										break;
									}
								}
							}
							if(!found)
								newBest(null, -1);
						}
					});
				}

				void newBest(E value, int newIndex) {
					E oldValue = theValue;
					theValue = value;
					index[0] = newIndex;
					CollectionSession session = theCollection.getSession().get();
					if(session == null)
						observer.onNext(createChangeEvent(oldValue, theValue, null));
					else {
						session.putIfAbsent(key, "oldBest", oldValue);
						session.put(key, "newBest", theValue);
					}
				}
			});
			if(index[0] < 0)
				observer.onNext(createInitialEvent(null));
			Subscription transSub = theCollection.getSession().subscribe(new Observer<ObservableValueEvent<CollectionSession>>() {
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
		public boolean isSafe() {
			return theCollection.isSafe();
		}

		@Override
		public String toString() {
			return "find in " + theCollection;
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#observeAt(int, Function)}
	 *
	 * @param <E> The type of the element
	 */
	class PositionObservable<E> implements ObservableValue<E> {
		private final ObservableOrderedCollection<E> theCollection;
		private final int theIndex;
		private final Function<Integer, E> theDefaultValueGenerator;

		protected PositionObservable(ObservableOrderedCollection<E> collection, int index, Function<Integer, E> defValueGen) {
			theCollection = collection;
			theIndex = index;
			theDefaultValueGenerator = defValueGen;
		}

		protected ObservableOrderedCollection<E> getCollection() {
			return theCollection;
		}

		protected int getIndex() {
			return theIndex;
		}

		protected Function<Integer, E> getDefaultValueGenerator() {
			return theDefaultValueGenerator;
		}

		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}

		@Override
		public E get() {
			if (theIndex < theCollection.size())
				return theCollection.get(theIndex);
			else if (theDefaultValueGenerator != null)
				return theDefaultValueGenerator.apply(theCollection.size());
			else
				throw new IndexOutOfBoundsException(theIndex + " of " + theCollection.size());
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
			final boolean[] initialized = new boolean[1];
			final Object sessionKey = new Object();
			final boolean[] hasValue = new boolean[1];
			class ElConsumer implements Consumer<ObservableOrderedElement<E>> {
				class ElObserver implements Observer<ObservableValueEvent<E>> {
					private final ObservableOrderedElement<E> element;

					ElObserver(ObservableOrderedElement<E> el) {
						element = el;
					}

					@Override
					public <V extends ObservableValueEvent<E>> void onNext(V evt) {
						if (element.getIndex() == theIndex) {
							if (!initialized[0]) {
								hasValue[0] = true;
								currentValue = evt.getValue();
								observer.onNext(createInitialEvent(currentValue));
							} else
								newValue(evt.getValue());
						}
					}

					@Override
					public <V extends ObservableValueEvent<E>> void onCompleted(V evt) {
						elSubs.add(els.get(theIndex).subscribe(new ElObserver(els.get(theIndex))));
					}
				}

				private final List<ObservableOrderedElement<E>> els;
				private final List<Subscription> elSubs;
				E currentValue;

				{
					if (theCollection.isSafe()) {
						els = new ArrayList<>(theCollection.size());
						elSubs = new ArrayList<>(theCollection.size());
					} else {
						els = Collections.synchronizedList(new ArrayList<>(theCollection.size()));
						elSubs = Collections.synchronizedList(new ArrayList<>(theCollection.size()));
					}
				}

				@Override
				public void accept(ObservableOrderedElement<E> el) {
					els.add(el.getIndex(), el);
					if (el.getIndex() <= theIndex) {
						elSubs.add(el.getIndex(), el.subscribe(new ElObserver(el)));
						if (elSubs.size() > theIndex + 1)
							elSubs.remove(theIndex + 1).unsubscribe();
						if (initialized[0] && el.getIndex() != theIndex) {
							newValue(els.get(theIndex).get());
						}
					}
				}

				private void newValue(E newValue) {
					hasValue[0] = true;
					E oldValue = currentValue;
					currentValue = newValue;

					CollectionSession session = theCollection.getSession().get();
					if (session == null) {
						observer.onNext(createChangeEvent(oldValue, currentValue, null));
					} else {
						if (session.get(sessionKey, "changed") == null) {
							session.put(sessionKey, "changed", true);
							session.put(sessionKey, "oldValue", oldValue);
						}
						session.put(sessionKey, "newValue", currentValue);
					}
				}
			}
			ElConsumer consumer = new ElConsumer();
			Subscription listSub = theCollection.onOrderedElement(consumer);
			initialized[0] = true;
			Subscription sessionSub = theCollection.getSession().act(evt -> {
				if (evt.getOldValue() != null && evt.getOldValue().get(sessionKey, "changed") != null) {
					observer.onNext(createChangeEvent((E) evt.getOldValue().get(sessionKey, "oldValue"),
						(E) evt.getOldValue().get(sessionKey, "newValue"), evt.getCause()));
				}
			});
			if (!hasValue[0]) {
				if (theDefaultValueGenerator != null) {
					try {
						consumer.currentValue = theDefaultValueGenerator.apply(theCollection.size());
					} catch (RuntimeException e) {
						// Just set a null value if the value generator throws an exception
						consumer.currentValue = null;
					}
				}
				hasValue[0] = true;
				observer.onNext(createInitialEvent(consumer.currentValue));
			}
			return () -> {
				listSub.unsubscribe();
				sessionSub.unsubscribe();
			};
		}

		@Override
		public boolean isSafe() {
			return theCollection.isSafe();
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#safe()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class SafeOrderedCollection<E> extends SafeObservableCollection<E> implements ObservableOrderedCollection<E> {
		public SafeOrderedCollection(ObservableOrderedCollection<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return onElement(element -> onElement.accept((ObservableOrderedElement<E>) element));
		}

		@Override
		protected ObservableOrderedElement<E> wrapElement(ObservableElement<E> wrap) {
			return new ObservableOrderedElement<E>() {
				@Override
				public TypeToken<E> getType() {
					return wrap.getType();
				}

				@Override
				public E get() {
					return wrap.get();
				}

				@Override
				public int getIndex() {
					return ((ObservableOrderedElement<E>) wrap).getIndex();
				}

				@Override
				public boolean isSafe() {
					return true;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
					ObservableOrderedElement<E> wrapper = this;
					return wrap.subscribe(new Observer<ObservableValueEvent<E>>() {
						@Override
						public <V extends ObservableValueEvent<E>> void onNext(V event) {
							getLock().lock();
							try {
								observer.onNext(ObservableUtils.wrap(event, wrapper));
							} finally {
								getLock().unlock();
							}
						}

						@Override
						public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
							getLock().lock();
							try {
								observer.onCompleted(ObservableUtils.wrap(event, wrapper));
							} finally {
								getLock().unlock();
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
	}

	/**
	 * Implements {@link ObservableOrderedCollection#map(Function)}
	 *
	 * @param <E> The type of the collection to map
	 * @param <T> The type of the mapped collection
	 */
	class MappedOrderedCollection<E, T> extends MappedObservableCollection<E, T> implements ObservableOrderedCollection<T> {
		protected MappedOrderedCollection(ObservableOrderedCollection<E> wrap, TypeToken<T> type, Function<? super E, T> map,
			Function<? super T, E> reverse) {
			super(wrap, type, map, reverse);
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<T>> onElement) {
			return onElement(element -> onElement.accept((ObservableOrderedElement<T>) element));
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#filterMap(Function)}
	 *
	 * @param <E> The type of the collection to be filter-mapped
	 * @param <T> The type of the mapped collection
	 */
	class StaticFilteredOrderedCollection<E, T> extends StaticFilteredCollection<E, T> implements ObservableOrderedCollection<T> {
		StaticFilteredOrderedCollection(ObservableOrderedCollection<E> wrap, TypeToken<T> type, Function<? super E, FilterMapResult<T>> map,
			Function<? super T, E> reverse) {
			super(wrap.safe(), type, map, reverse);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<T>> onElement) {
			return onElement(element -> onElement.accept((ObservableOrderedElement<T>) element));
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<T>> onElement) {
			final List<StaticFilteredOrderedElement<T>> filteredElements = new java.util.ArrayList<>();
			return super.onElement(element -> {
				StaticFilteredOrderedElement<T> filteredEl = new StaticFilteredOrderedElement<>((ObservableOrderedElement<T>) element,
					filteredElements);
				int filteredIndex = Collections.binarySearch(filteredElements, filteredEl,
					(fEl1, fEl2) -> fEl1.getWrapped().getIndex() - fEl2.getWrapped().getIndex());
				if (filteredIndex >= 0)
					throw new IllegalStateException(
						"Index " + filteredEl.getWrapped().getIndex() + " already present in filtered elements");
				filteredElements.add(-filteredIndex - 1, filteredEl);
				onElement.accept(filteredEl);
				filteredEl.getWrapped().completed().act(elValue -> filteredElements.remove(filteredEl.getIndex()));
				// onElement.accept((ObservableOrderedElement<T>) element);
			});
		}
	}

	/**
	 * The type of element in dynamically filtered ordered collections
	 *
	 * @param <E> The type of this element
	 */
	class StaticFilteredOrderedElement<E> implements ObservableOrderedElement<E> {
		private final ObservableOrderedElement<E> theWrapped;
		private final List<StaticFilteredOrderedElement<E>> theFilteredElements;

		StaticFilteredOrderedElement(ObservableOrderedElement<E> wrapped, List<StaticFilteredOrderedElement<E>> filteredEls) {
			theWrapped = wrapped;
			theFilteredElements = filteredEls;
		}

		@Override
		public ObservableValue<E> persistent() {
			return theWrapped.persistent();
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
			return ObservableUtils.wrap(theWrapped, this, observer);
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}

		protected ObservableOrderedElement<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public int getIndex() {
			return theFilteredElements.indexOf(this);
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#filterMap(Function)}
	 *
	 * @param <E> The type of the collection to be filter-mapped
	 * @param <T> The type of the mapped collection
	 */
	class DynamicFilteredOrderedCollection<E, T> extends DynamicFilteredCollection<E, T> implements ObservableOrderedCollection<T> {
		DynamicFilteredOrderedCollection(ObservableOrderedCollection<E> wrap, TypeToken<T> type,
			Function<? super E, FilterMapResult<T>> map, Function<? super T, E> reverse) {
			super(wrap.safe(), type, map, reverse);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		protected DynamicFilteredOrderedElement<E, T> filter(ObservableElement<E> element, Object meta) {
			List<DynamicFilteredOrderedElement<E, T>> filteredElements = (List<DynamicFilteredOrderedElement<E, T>>) meta;
			ObservableOrderedElement<E> outerEl = (ObservableOrderedElement<E>) element;
			DynamicFilteredOrderedElement<E, T> retElement = d()
				.debug(new DynamicFilteredOrderedElement<>(outerEl, getMap(), getType(), filteredElements))
				.from("element", this).tag("wrapped", element).get();
			filteredElements.add(outerEl.getIndex(), retElement);
			outerEl.completed().act(elValue -> filteredElements.remove(outerEl.getIndex()));
			return retElement;
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<T>> onElement) {
			return onOrderedElement(onElement);
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<T>> onElement) {
			return super.onElement(element -> onElement.accept((ObservableOrderedElement<T>) element), new ArrayList<>());
		}
	}

	/**
	 * The type of element in dynamically filtered ordered collections
	 *
	 * @param <T> The type of this element
	 * @param <E> The type of element wrapped by this element
	 */
	class DynamicFilteredOrderedElement<E, T> extends DynamicFilteredElement<E, T> implements ObservableOrderedElement<T> {
		private final List<DynamicFilteredOrderedElement<E, T>> theFilteredElements;

		DynamicFilteredOrderedElement(ObservableOrderedElement<E> wrapped, Function<? super E, FilterMapResult<T>> map, TypeToken<T> type,
			List<DynamicFilteredOrderedElement<E, T>> filteredElements) {
			super(wrapped, map, type);
			theFilteredElements = filteredElements;
		}

		@Override
		protected ObservableOrderedElement<E> getWrapped() {
			return (ObservableOrderedElement<E>) super.getWrapped();
		}

		@Override
		public int getIndex() {
			int ret = 0;
			int outerIdx = getWrapped().getIndex();
			for(int i = 0; i < outerIdx; i++)
				if(theFilteredElements.get(i).isIncluded())
					ret++;
			return ret;
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#combine(ObservableValue, BiFunction)}
	 *
	 * @param <E> The type of the collection to be combined
	 * @param <T> The type of the value to combine the collection elements with
	 * @param <V> The type of the combined collection
	 */
	class CombinedOrderedCollection<E, T, V> extends CombinedObservableCollection<E, T, V> implements ObservableOrderedCollection<V> {
		CombinedOrderedCollection(ObservableOrderedCollection<E> collection, ObservableValue<T> value, TypeToken<V> type,
			BiFunction<? super E, ? super T, V> map, BiFunction<? super V, ? super T, E> reverse) {
			super(collection, type, value, map, reverse);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<V>> onElement) {
			return onElement(element -> onElement.accept((ObservableOrderedElement<V>) element));
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#groupBy(Function, Equalizer)}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class GroupedOrderedMultiMap<K, V> extends GroupedMultiMap<K, V> {
		public GroupedOrderedMultiMap(ObservableOrderedCollection<V> wrap, Function<V, K> keyMap, TypeToken<K> keyType,
			Equalizer equalizer) {
			super(wrap, keyMap, keyType, equalizer);
		}

		@Override
		protected ObservableSet<K> unique(ObservableCollection<K> keyCollection) {
			return ObservableOrderedSet.unique((ObservableOrderedCollection<K>) keyCollection, getEqualizer(), false);
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#refresh(Observable)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	class RefreshingOrderedCollection<E> extends RefreshingCollection<E> implements ObservableOrderedCollection<E> {
		protected RefreshingOrderedCollection(ObservableOrderedCollection<E> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return onElement(element -> onElement.accept((ObservableOrderedElement<E>) element));
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#refreshEach(Function)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	class ElementRefreshingOrderedCollection<E> extends ElementRefreshingCollection<E> implements ObservableOrderedCollection<E> {
		protected ElementRefreshingOrderedCollection(ObservableOrderedCollection<E> wrap, Function<? super E, Observable<?>> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return onElement(element -> onElement.accept((ObservableOrderedElement<E>) element));
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#sorted(Comparator)}
	 *
	 * @param <E> The type of the elements in the collection
	 */
	class SortedObservableCollection<E> implements PartialCollectionImpl<E>, ObservableOrderedCollection<E> {
		private final ObservableOrderedCollection<E> theWrapped;
		private final Comparator<? super E> theCompare;

		public SortedObservableCollection(ObservableOrderedCollection<E> wrap, Comparator<? super E> compare) {
			theWrapped = wrap;
			theCompare = compare;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		/** @return The comparator sorting this collection's elements */
		public Comparator<? super E> comparator() {
			return theCompare;
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
		public boolean canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			return theWrapped.canAdd(value);
		}

		@Override
		public Iterator<E> iterator() {
			ArrayList<E> sorted = new ArrayList<>(theWrapped);
			Collections.sort(sorted, theCompare);
			return sorted.iterator();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			class SortedElement implements ObservableOrderedElement<E>, Comparable<SortedElement> {
				private final ObservableOrderedElement<E> theWrappedEl;
				private final DefaultTreeSet<SortedElement> theElements;
				private final SimpleObservable<Void> theRemoveObservable;
				private DefaultNode<SortedElement> theNode;
				private int theRemovedIndex;

				SortedElement(ObservableOrderedElement<E> wrap, DefaultTreeSet<SortedElement> elements) {
					theWrappedEl=wrap;
					theElements = elements;
					theRemoveObservable = new SimpleObservable<>();
				}

				@Override
				public TypeToken<E> getType() {
					return theWrappedEl.getType();
				}

				@Override
				public boolean isSafe() {
					return theWrappedEl.isSafe();
				}

				@Override
				public int getIndex() {
					return theNode != null ? theNode.getIndex() : theRemovedIndex;
				}

				@Override
				public E get() {
					return theWrappedEl.get();
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
					return ObservableUtils.wrap(theWrappedEl.takeUntil(theRemoveObservable), this, observer);
				}

				@Override
				public ObservableValue<E> persistent() {
					return theWrappedEl.persistent();
				}

				@Override
				public int compareTo(SortedElement el) {
					int compare = theCompare.compare(get(), el.get());
					if (compare != 0)
						return compare;
					return theWrappedEl.getIndex() - el.theWrappedEl.getIndex();
				}

				void delete() {
					theRemovedIndex = theNode.getIndex();
					theNode.delete();
					theNode = null;
				}

				void checkOrdering() {
					CountedRedBlackNode<SortedElement> parent = theNode.getParent();
					boolean isLeft = theNode.getSide();
					CountedRedBlackNode<SortedElement> left = theNode.getLeft();
					CountedRedBlackNode<SortedElement> right = theNode.getRight();

					boolean changed = false;
					if (parent != null) {
						int compare = theCompare.compare(parent.getValue().get(), get());
						if (compare == 0)
							changed = true;
						else if (compare > 0 != isLeft)
							changed = true;
					}
					if (!changed && left != null) {
						if (theCompare.compare(left.getValue().get(), get()) >= 0)
							changed = true;
					}
					if (!changed && right != null) {
						if (theCompare.compare(right.getValue().get(), get()) <= 0)
							changed = true;
					}
					if (changed) {
						theRemoveObservable.onNext(null);
						theNode.delete();
						addIn();
					}
				}

				void addIn() {
					theNode = theElements.addGetNode(this);
					onElement.accept(this);
				}
			}
			DefaultTreeSet<SortedElement> elements = new DefaultTreeSet<>(SortedElement::compareTo);
			SimpleObservable<Void> unSubObs = new SimpleObservable<>();
			Subscription collSub = theWrapped.onOrderedElement(element -> {
				SortedElement sortedEl = new SortedElement(element, elements);
				element.unsubscribeOn(unSubObs).subscribe(new Observer<ObservableValueEvent<E>>() {
					@Override
					public <V extends ObservableValueEvent<E>> void onNext(V event) {
						if (event.isInitial())
							sortedEl.addIn();
						else
							sortedEl.checkOrdering();// Compensate if the value change has changed the sorting
					}

					@Override
					public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
						sortedEl.delete();
					}
				});
			});
			return () -> {
				collSub.unsubscribe();
				unSubObs.onNext(null);
				elements.clear();
			};
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	/**
	 * An observable ordered collection that cannot be modified directly, but reflects the value of a wrapped collection as it changes
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ImmutableOrderedCollection<E> extends ImmutableObservableCollection<E> implements
	ObservableOrderedCollection<E> {
		protected ImmutableOrderedCollection(ObservableOrderedCollection<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> observer) {
			return getWrapped().onOrderedElement(observer);
		}

		@Override
		public ImmutableOrderedCollection<E> immutable() {
			return this;
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#filterModification(Predicate, Predicate)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ModFilteredOrderedCollection<E> extends ModFilteredCollection<E> implements ObservableOrderedCollection<E> {
		public ModFilteredOrderedCollection(ObservableOrderedCollection<E> wrapped, Predicate<? super E> removeFilter,
			Predicate<? super E> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return getWrapped().onOrderedElement(onElement);
		}
	}

	/**
	 * Backs {@link ObservableOrderedCollection#cached()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class SafeCachedOrderedCollection<E> extends SafeCachedObservableCollection<E> implements ObservableOrderedCollection<E> {
		protected static class OrderedCachedElement<E> extends CachedElement<E> implements ObservableOrderedElement<E> {
			protected OrderedCachedElement(ObservableOrderedElement<E> wrap) {
				super(wrap);
			}

			@Override
			protected ObservableOrderedElement<E> getWrapped() {
				return (ObservableOrderedElement<E>) super.getWrapped();
			}

			@Override
			public int getIndex() {
				return getWrapped().getIndex();
			}
		}

		protected SafeCachedOrderedCollection(ObservableOrderedCollection<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		protected List<E> refresh() {
			return new ArrayList<>(super.refresh());
		}

		@Override
		protected List<OrderedCachedElement<E>> cachedElements() {
			ArrayList<OrderedCachedElement<E>> ret = new ArrayList<>(
				(Collection<OrderedCachedElement<E>>) (Collection<?>) super.cachedElements());
			Comparator<OrderedCachedElement<E>> compare = (OrderedCachedElement<E> el1, OrderedCachedElement<E> el2) -> el1.getIndex()
				- el2.getIndex();
			Collections.sort(ret, compare);
			return ret;
		}

		@Override
		protected CachedElement<E> createElement(ObservableElement<E> element) {
			return new OrderedCachedElement<>((ObservableOrderedElement<E>) element);
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			Subscription ret = addListener(element -> onElement.accept((ObservableOrderedElement<E>) element));
			for(OrderedCachedElement<E> el : cachedElements())
				onElement.accept(el.cached());
			return ret;
		}

		@Override
		public ObservableOrderedCollection<E> cached() {
			return this;
		}
	}

	/**
	 * Backs {@link ObservableOrderedCollection#takeUntil(Observable)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class TakenUntilOrderedCollection<E> extends TakenUntilObservableCollection<E> implements ObservableOrderedCollection<E> {
		public TakenUntilOrderedCollection(ObservableOrderedCollection<E> wrap, Observable<?> until, boolean terminate) {
			super(wrap, until, terminate);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return onOrderedElement(onElement);
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			List<TakenUntilOrderedElement<E>> elements = new ArrayList<>();
			Subscription[] collSub = new Subscription[] { getWrapped().onOrderedElement(element -> {
				TakenUntilOrderedElement<E> untilEl = new TakenUntilOrderedElement<>(element, isTerminating());
				elements.add(element.getIndex(), untilEl);
				onElement.accept(untilEl);
			}) };
			Subscription untilSub = getUntil().act(v -> {
				if (collSub[0] != null)
					collSub[0].unsubscribe();
				collSub[0] = null;
				for (int i = elements.size() - 1; i >= 0; i--)
					elements.get(i).end();
				elements.clear();
			});
			return () -> {
				if (collSub[0] != null)
					collSub[0].unsubscribe();
				collSub[0] = null;
				untilSub.unsubscribe();
			};
		}
	}

	/**
	 * An element in a {@link ObservableOrderedCollection.TakenUntilOrderedCollection}
	 *
	 * @param <E> The type of value in the element
	 */
	class TakenUntilOrderedElement<E> extends TakenUntilElement<E> implements ObservableOrderedElement<E> {
		public TakenUntilOrderedElement(ObservableOrderedElement<E> wrap, boolean terminate) {
			super(wrap, terminate);
		}

		@Override
		public int getIndex() {
			return getWrapped().getIndex();
		}

		@Override
		protected ObservableOrderedElement<E> getWrapped() {
			return (ObservableOrderedElement<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#flattenValues(ObservableOrderedCollection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedOrderedValuesCollection<E> extends FlattenedValuesCollection<E> implements ObservableOrderedCollection<E> {
		protected FlattenedOrderedValuesCollection(ObservableOrderedCollection<? extends ObservableValue<? extends E>> collection) {
			super(collection);
		}

		@Override
		protected ObservableOrderedCollection<? extends ObservableValue<? extends E>> getWrapped() {
			return (ObservableOrderedCollection<? extends ObservableValue<? extends E>>) super.getWrapped();
		}

		@Override
		protected FlattenedOrderedValueElement<E> createFlattenedElement(
			ObservableElement<? extends ObservableValue<? extends E>> element) {
			return new FlattenedOrderedValueElement<>((ObservableOrderedElement<? extends ObservableValue<? extends E>>) element,
				getType());
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return getWrapped().onOrderedElement(element -> onElement.accept(createFlattenedElement(element)));
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return onOrderedElement(onElement);
		}
	}

	/**
	 * Implements elements for {@link ObservableOrderedCollection#flattenValues(ObservableOrderedCollection)}
	 *
	 * @param <E> The type of value in the element
	 */
	class FlattenedOrderedValueElement<E> extends FlattenedValueElement<E> implements ObservableOrderedElement<E> {
		public FlattenedOrderedValueElement(ObservableOrderedElement<? extends ObservableValue<? extends E>> wrap, TypeToken<E> type) {
			super(wrap, type);
		}

		@Override
		protected ObservableOrderedElement<? extends ObservableValue<? extends E>> getWrapped() {
			return (ObservableOrderedElement<? extends ObservableValue<? extends E>>) super.getWrapped();
		}

		@Override
		public int getIndex() {
			return getWrapped().getIndex();
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedOrderedValueCollection<E> extends FlattenedValueCollection<E> implements ObservableOrderedCollection<E> {
		public FlattenedOrderedValueCollection(ObservableValue<? extends ObservableOrderedCollection<E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected ObservableValue<? extends ObservableOrderedCollection<E>> getWrapped() {
			return (ObservableValue<? extends ObservableOrderedCollection<E>>) super.getWrapped();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			SimpleObservable<Void> unSubObs = new SimpleObservable<>();
			Subscription collSub = getWrapped()
				.subscribe(new Observer<ObservableValueEvent<? extends ObservableOrderedCollection<? extends E>>>() {
					@Override
					public <V extends ObservableValueEvent<? extends ObservableOrderedCollection<? extends E>>> void onNext(V event) {
						if (event.getValue() != null) {
							Observable<?> until = ObservableUtils.makeUntil(getWrapped(), event);
							((ObservableOrderedCollection<E>) event.getValue().takeUntil(until).unsubscribeOn(unSubObs))
							.onOrderedElement(onElement);
						}
					}
				});
			return () -> {
				collSub.unsubscribe();
				unSubObs.onNext(null);
			};
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return onOrderedElement(onElement);
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#flatten(ObservableOrderedCollection)}
	 *
	 * @param <E> The type of the collection
	 */
	class FlattenedOrderedCollection<E> extends FlattenedObservableCollection<E> implements ObservableOrderedCollection<E> {
		protected FlattenedOrderedCollection(ObservableOrderedCollection<? extends ObservableOrderedCollection<? extends E>> outer) {
			super(outer);
		}

		@Override
		protected ObservableOrderedCollection<? extends ObservableOrderedCollection<? extends E>> getOuter() {
			return (ObservableOrderedCollection<? extends ObservableOrderedCollection<? extends E>>) super.getOuter();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return onElement(ObservableOrderedCollection::onOrderedElement, onElement);
		}

		protected interface ElementSubscriber {
			<E> Subscription onElement(ObservableOrderedCollection<E> coll, Consumer<? super ObservableOrderedElement<E>> onElement);
		}

		protected Subscription onElement(ElementSubscriber subscriber, Consumer<? super ObservableOrderedElement<E>> onElement) {
			class OuterNode {
				final ObservableOrderedElement<? extends ObservableOrderedCollection<? extends E>> element;
				final List<ObservableOrderedElement<? extends E>> subElements;

				OuterNode(ObservableOrderedElement<? extends ObservableOrderedCollection<? extends E>> el) {
					element = el;
					subElements = new ArrayList<>();
				}
			}
			List<OuterNode> nodes = new ArrayList<>();
			class InnerElement implements ObservableOrderedElement<E> {
				private final ObservableOrderedElement<? extends E> theWrapped;
				private final OuterNode theOuterNode;

				InnerElement(ObservableOrderedElement<? extends E> wrap, OuterNode outerNode) {
					theWrapped = wrap;
					theOuterNode = outerNode;
				}

				@Override
				public TypeToken<E> getType() {
					return FlattenedOrderedCollection.this.getType();
				}

				@Override
				public boolean isSafe() {
					return theWrapped.isSafe();
				}

				@Override
				public E get() {
					return theWrapped.get();
				}

				@Override
				public int getIndex() {
					int index = 0;
					for (int i = 0; i < theOuterNode.element.getIndex(); i++) {
						index += nodes.get(i).subElements.size();
					}
					index += theWrapped.getIndex();
					return index;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
					return ObservableUtils.wrap(theWrapped, this, observer);
				}

				@Override
				public ObservableValue<E> persistent() {
					return (ObservableValue<E>) theWrapped.persistent();
				}

				@Override
				public String toString() {
					return getType() + " list[" + getIndex() + "]";
				}
			}
			SimpleObservable<Void> unSubObs = new SimpleObservable<>();
			Subscription outerSub = subscriber.onElement(getOuter(), outerEl -> {
				OuterNode outerNode = new OuterNode(outerEl);
				outerEl.subscribe(new Observer<ObservableValueEvent<? extends ObservableOrderedCollection<? extends E>>>() {
					@Override
					public <E1 extends ObservableValueEvent<? extends ObservableOrderedCollection<? extends E>>> void onNext(
						E1 outerEvent) {
						Observable<?> until = ObservableUtils.makeUntil(outerEl, outerEvent);
						if (outerEvent.isInitial())
							nodes.add(outerEl.getIndex(), outerNode);
						outerEvent.getValue().safe().takeUntil(until).unsubscribeOn(unSubObs).onOrderedElement(innerEl -> {
							innerEl.subscribe(new Observer<ObservableValueEvent<? extends E>>() {
								@Override
								public <E2 extends ObservableValueEvent<? extends E>> void onNext(E2 innerEvent) {
									if (innerEvent.isInitial())
										outerNode.subElements.add(innerEl.getIndex(), innerEl);
								}

								@Override
								public <E2 extends ObservableValueEvent<? extends E>> void onCompleted(E2 innerEvent) {
									outerNode.subElements.remove(innerEl.getIndex());
								}
							});
							InnerElement innerWrappedEl = new InnerElement(innerEl, outerNode);
							onElement.accept(innerWrappedEl);
						});
					}

					@Override
					public <E1 extends ObservableValueEvent<? extends ObservableOrderedCollection<? extends E>>> void onCompleted(
						E1 outerEvent) {
						nodes.remove(outerEl.getIndex());
					}
				});
			});
			return () -> {
				outerSub.unsubscribe();
				unSubObs.onNext(null);
				nodes.clear();
			};
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> observer) {
			return onOrderedElement(observer);
		}
	}
}
