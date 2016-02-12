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
import org.observe.Subscription;
import org.observe.util.ObservableUtils;
import org.qommons.ArrayUtils;
import org.qommons.Transaction;

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
		return d().debug(new SortedOrderedCollectionWrapper<>(this, compare)).from("sorted", this).using("compare", compare).get();
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
		return d().debug(new TakenUntilOrderedCollection<>(this, until)).from("taken", this).from("until", until).get();
	}

	/**
	 * Flattens a collection of ordered collections. The inner collections must be sorted according to the given comparator in order for the
	 * result to be correctly sorted.
	 *
	 * @param <E> The super-type of all collections in the wrapping collection
	 * @param list The collection to flatten
	 * @return A collection containing all elements of all collections in the outer collection, sorted according to the given comparator,
	 *         then by order in the outer collection.
	 */
	public static <E> ObservableOrderedCollection<E> flatten(ObservableOrderedCollection<? extends ObservableOrderedCollection<E>> list) {
		return d().debug(new FlattenedOrderedCollection<>(list)).from("flatten", list).get();
	}


	/**
	 * Finds something in an {@link ObservableOrderedCollection}
	 *
	 * @param <E> The type of value to find
	 */
	public class OrderedCollectionFinder<E> implements ObservableValue<E> {
		private final ObservableOrderedCollection<E> theCollection;

		private final TypeToken<E> theType;

		private final Predicate<? super E> theFilter;

		private final boolean isForward;

		OrderedCollectionFinder(ObservableOrderedCollection<E> collection, Predicate<? super E> filter, boolean forward) {
			theCollection = collection;
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
									findNextBest(listIndex);
							}
						}

						@Override
						public <V3 extends ObservableValueEvent<E>> void onCompleted(V3 value) {
							theElements.remove(element.getIndex());
							int listIndex = ((ObservableOrderedElement<?>) value.getObservable()).getIndex();
							if(listIndex == index[0]) {
								findNextBest(listIndex);
							} else if(listIndex < index[0])
								index[0]--;
						}

						private boolean isBetterIndex(int test, int current) {
							if(isForward)
								return test <= current;
							else
								return test >= current;
						}

						private void findNextBest(int newIndex) {
							boolean found = false;
							if (isForward) {
								for (int i = newIndex + 1; i < theElements.size(); i++) {
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
		public String toString() {
			return "find in " + theCollection;
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
		StaticFilteredOrderedCollection(ObservableOrderedCollection<E> wrap, TypeToken<T> type, Function<? super E, T> map,
				Function<? super T, E> reverse) {
			super(wrap, type, map, reverse);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
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
	class DynamicFilteredOrderedCollection<E, T> extends DynamicFilteredCollection<E, T> implements ObservableOrderedCollection<T> {
		private List<FilteredOrderedElement<E, T>> theFilteredElements = new java.util.ArrayList<>();

		DynamicFilteredOrderedCollection(ObservableOrderedCollection<E> wrap, TypeToken<T> type, Function<? super E, T> map,
				Function<? super T, E> reverse) {
			super(wrap, type, map, reverse);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		protected FilteredOrderedElement<E, T> filter(ObservableElement<E> element) {
			ObservableOrderedElement<E> outerEl = (ObservableOrderedElement<E>) element;
			FilteredOrderedElement<E, T> retElement = d()
					.debug(new FilteredOrderedElement<>(outerEl, getMap(), getType(), theFilteredElements))
					.from("element", this).tag("wrapped", element).get();
			theFilteredElements.add(outerEl.getIndex(), retElement);
			outerEl.completed().act(elValue -> theFilteredElements.remove(outerEl.getIndex()));
			return retElement;
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<T>> onElement) {
			return onElement(element -> onElement.accept((ObservableOrderedElement<T>) element));
		}
	}

	/**
	 * The type of element in filtered ordered collections
	 *
	 * @param <T> The type of this element
	 * @param <E> The type of element wrapped by this element
	 */
	class FilteredOrderedElement<E, T> extends FilteredElement<E, T> implements ObservableOrderedElement<T> {
		private List<FilteredOrderedElement<E, T>> theFilteredElements;

		FilteredOrderedElement(ObservableOrderedElement<E> wrapped, Function<? super E, T> map, TypeToken<T> type,
				List<FilteredOrderedElement<E, T>> filteredEls) {
			super(wrapped, map, type);
			theFilteredElements = filteredEls;
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
	 * Backs elements in {@link ObservableOrderedCollection#sorted(Comparator)}
	 *
	 * @param <E> The type of the element
	 */
	static class SortedElementWrapper<E> implements ObservableOrderedElement<E> {
		private final SortedOrderedCollectionWrapper<E> theList;

		private final ObservableElement<E> theWrapped;

		private final SortedOrderedWrapperObserver<E> theParentObserver;

		SortedElementWrapper<E> theLeft;

		SortedElementWrapper<E> theRight;

		SortedElementWrapper(SortedOrderedCollectionWrapper<E> list, ObservableElement<E> wrap,
				SortedOrderedWrapperObserver<E> parentObs, SortedElementWrapper<E> anchor) {
			theList = list;
			theWrapped = wrap;
			theParentObserver = parentObs;
			if(anchor != null)
				findPlace(theWrapped.get(), anchor);
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
			return theWrapped.subscribe(new Observer<ObservableValueEvent<E>>() {
				@Override
				public <V extends ObservableValueEvent<E>> void onNext(V event) {
					if(theLeft != null && theList.getCompare().compare(event.getValue(), theLeft.get()) < 0) {
						observer.onCompleted(ObservableUtils.wrap(event, SortedElementWrapper.this));
						theLeft.theRight = theRight;
						if(theRight != null)
							theRight.theLeft = theLeft;
						findPlace(event.getValue(), theLeft);
						theParentObserver.theOuterObserver.accept(SortedElementWrapper.this);
					} else if(theRight != null && theList.getCompare().compare(event.getValue(), theRight.get()) > 0) {
						observer.onCompleted(ObservableUtils.wrap(event, SortedElementWrapper.this));
						if(theLeft != null)
							theLeft.theRight = theRight;
						theRight.theLeft = theLeft;
						findPlace(event.getValue(), theRight);
						theParentObserver.theOuterObserver.accept(SortedElementWrapper.this);
					} else
						observer.onNext(ObservableUtils.wrap(event, SortedElementWrapper.this));
				}

				@Override
				public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
					if(theParentObserver.theAnchor == SortedElementWrapper.this) {
						if(theLeft != null)
							theParentObserver.theAnchor = theLeft;
						else if(theRight != null)
							theParentObserver.theAnchor = theRight;
						else
							theParentObserver.theAnchor = null;
					}
					observer.onCompleted(ObservableUtils.wrap(event, SortedElementWrapper.this));
				}
			});
		}

		@Override
		public int getIndex() {
			SortedElementWrapper<E> left = theLeft;
			int ret = 0;
			while(left != null) {
				ret++;
				left = left.theLeft;
			}
			return ret;
		}

		private void findPlace(E value, SortedElementWrapper<E> anchor) {
			SortedElementWrapper<E> test = anchor;
			int comp = theList.getCompare().compare(value, test.get());
			if(comp >= 0) {
				while(test.theRight != null && comp >= 0) {
					test = test.theRight;
					comp = theList.getCompare().compare(value, test.get());
				}

				if(comp >= 0) { // New element is right-most
					theLeft = test;
					test.theRight = this;
				} else { // New element to be inserted to the left of test
					theLeft = test.theLeft;
					theRight = test;
					test.theLeft = this;
				}
			} else {
				while(test.theLeft != null && comp < 0) {
					test = test.theLeft;
					comp = theList.getCompare().compare(value, test.get());
				}

				if(comp < 0) { // New element is left-most
					theRight = test;
					test.theLeft = this;
				} else { // New element to be inserted to the right of test
					theLeft = test;
					theRight = test.theRight;
					test.theRight = this;
				}
			}
		}
	}

	/**
	 * Backs {@link ObservableOrderedCollection#sorted(Comparator)}
	 *
	 * @param <E> The type of the elements in the collection
	 */
	static class SortedOrderedCollectionWrapper<E> implements PartialCollectionImpl<E>, ObservableOrderedCollection<E> {
		private final ObservableCollection<E> theWrapped;

		private final Comparator<? super E> theCompare;

		SortedOrderedCollectionWrapper(ObservableCollection<E> wrap, Comparator<? super E> compare) {
			theWrapped = wrap;
			theCompare = compare;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		Comparator<? super E> getCompare() {
			return theCompare;
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
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return theWrapped.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theWrapped.containsAll(c);
		}

		@Override
		public Iterator<E> iterator() {
			ArrayList<E> list = new ArrayList<>(theWrapped);
			Collections.sort(new ArrayList<>(theWrapped), theCompare);
			return Collections.unmodifiableCollection(list).iterator();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> observer) {
			return theWrapped.onElement(new SortedOrderedWrapperObserver<>(this, observer));
		}

		@Override
		public boolean add(E e) {
			return theWrapped.add(e);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return theWrapped.addAll(c);
		}

		@Override
		public boolean remove(Object o) {
			return theWrapped.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theWrapped.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theWrapped.retainAll(c);
		}

		@Override
		public void clear() {
			theWrapped.clear();
		}
	}

	/**
	 * Used by {@link ObservableOrderedCollection#sorted(Comparator)}
	 *
	 * @param <E> The type of the elements in the collection
	 */
	static class SortedOrderedWrapperObserver<E> implements Consumer<ObservableElement<E>> {
		private final SortedOrderedCollectionWrapper<E> theList;

		final Consumer<? super ObservableOrderedElement<E>> theOuterObserver;

		private SortedElementWrapper<E> theAnchor;

		SortedOrderedWrapperObserver(SortedOrderedCollectionWrapper<E> list, Consumer<? super ObservableOrderedElement<E>> outerObs) {
			theList = list;
			theOuterObserver = outerObs;
		}

		@Override
		public void accept(ObservableElement<E> outerEl) {
			SortedElementWrapper<E> newEl = d().debug(new SortedElementWrapper<>(theList, outerEl, this, theAnchor))
					.from("element", theList)
					.tag("wrapped", outerEl).get();
			if(theAnchor == null)
				theAnchor = newEl;
			theOuterObserver.accept(newEl);
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
		public TakenUntilOrderedCollection(ObservableOrderedCollection<E> wrap, Observable<?> until) {
			super(wrap, until);
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
				TakenUntilOrderedElement<E> untilEl = new TakenUntilOrderedElement<>(element);
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
		public TakenUntilOrderedElement(ObservableOrderedElement<E> wrap) {
			super(wrap);
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
	 * Implements {@link ObservableOrderedCollection#flatten(ObservableOrderedCollection)}
	 *
	 * @param <E> The type of the collection
	 */
	class FlattenedOrderedCollection<E> implements PartialCollectionImpl<E>, ObservableOrderedCollection<E> {
		private final ObservableOrderedCollection<? extends ObservableOrderedCollection<E>> theOuter;

		private final CombinedCollectionSessionObservable theSession;

		protected FlattenedOrderedCollection(ObservableOrderedCollection<? extends ObservableOrderedCollection<E>> outer) {
			theOuter = outer;
			theSession = new CombinedCollectionSessionObservable(theOuter);
		}

		protected ObservableOrderedCollection<? extends ObservableOrderedCollection<E>> getOuter() {
			return theOuter;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theSession;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Transaction outerLock = theOuter.lock(write, cause);
			Transaction [] innerLocks = new Transaction[theOuter.size()];
			int i = 0;
			for(ObservableCollection<? extends E> c : theOuter) {
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
		public TypeToken<E> getType() {
			return (TypeToken<E>) theOuter.getType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
		}

		@Override
		public int size() {
			int ret = 0;
			for(ObservableOrderedCollection<E> subList : theOuter)
				ret += subList.size();
			return ret;
		}

		@Override
		public Iterator<E> iterator() {
			return ArrayUtils.flatten(theOuter).iterator();
		}

		@Override
		public boolean contains(Object o) {
			for(ObservableOrderedCollection<E> subList : theOuter)
				if (subList.contains(o))
					return true;
			return false;
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			class OuterNode {
				final ObservableOrderedElement<? extends ObservableOrderedCollection<E>> element;
				final List<ObservableOrderedElement<? extends E>> subElements;
				Subscription subscription;

				OuterNode(ObservableOrderedElement<? extends ObservableOrderedCollection<E>> el) {
					element = el;
					subElements = new ArrayList<>();
				}
			}
			List<OuterNode> nodes = new ArrayList<>();
			class InnerElement implements ObservableOrderedElement<E> {
				private final ObservableOrderedElement<E> theWrapped;
				private final OuterNode theOuterNode;

				InnerElement(ObservableOrderedElement<E> wrap, OuterNode outerNode) {
					theWrapped = wrap;
					theOuterNode = outerNode;
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
					return theWrapped.persistent();
				}

				@Override
				public String toString() {
					return getType() + "list[" + getIndex() + "]";
				}
			}
			Subscription outerSub = theOuter.onOrderedElement(outerEl -> {
				OuterNode outerNode = new OuterNode(outerEl);
				outerEl.subscribe(new Observer<ObservableValueEvent<? extends ObservableOrderedCollection<E>>>() {
					@Override
					public <E1 extends ObservableValueEvent<? extends ObservableOrderedCollection<E>>> void onNext(E1 outerEvent) {
						Observable<?> until = outerEl.noInit().fireOnComplete();
						if (outerEvent.isInitial())
							nodes.add(outerEl.getIndex(), outerNode);
						else
							until = until.skip(1);
						outerNode.subscription = outerEvent.getValue().takeUntil(until).onOrderedElement(innerEl -> {
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
					public <E1 extends ObservableValueEvent<? extends ObservableOrderedCollection<E>>> void onCompleted(E1 outerEvent) {
						nodes.remove(outerEl.getIndex());
					}
				});
			});
			return () -> {
				outerSub.unsubscribe();
				for (int i = nodes.size() - 1; i >= 0; i--)
					nodes.get(i).subscription.unsubscribe();
				nodes.clear();
			};
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}
}
