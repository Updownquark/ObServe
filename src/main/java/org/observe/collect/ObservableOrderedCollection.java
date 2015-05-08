package org.observe.collect;

import static org.observe.ObservableDebug.debug;
import static org.observe.ObservableDebug.label;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.util.ObservableUtils;

import prisms.lang.Type;

/**
 * An ordered collection whose content can be observed. All {@link ObservableElement}s returned by this observable will be instances of
 * {@link OrderedObservableElement}. In addition, it is guaranteed that the {@link OrderedObservableElement#getIndex() index} of an element
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
	Runnable onOrderedElement(Consumer<? super OrderedObservableElement<E>> onElement);

	@Override
	default Runnable onElement(Consumer<? super ObservableElement<E>> onElement) {
		return onOrderedElement(onElement);
	}

	/** @return An observable that returns null whenever any elements in this collection are added, removed or changed */
	@Override
	default Observable<? extends OrderedCollectionChangeEvent<E>> changes() {
		return debug(new OrderedCollectionChangesObservable<>(this)).from("changes", this).get();
	}

	/**
	 * @param filter The filter function
	 * @return The first value in this collection passing the filter, or null if none of this collection's elements pass
	 */
	@Override
	default ObservableValue<E> find(Predicate<E> filter) {
		return debug(new OrderedCollectionFinder<>(this, filter, true)).from("find", this).using("filter", filter).get();
	}

	/**
	 * @param filter The filter function
	 * @return The first value in this collection passing the filter, or null if none of this collection's elements pass
	 */
	default ObservableValue<E> findLast(Predicate<E> filter) {
		return debug(new OrderedCollectionFinder<>(this, filter, false)).from("findLast", this).using("filter", filter).get();
	}

	/** @return The first value in this collection, or null if this collection is empty */
	default ObservableValue<E> first() {
		return debug(new OrderedCollectionFinder<>(this, value -> true, true)).from("first", this).get();
	}

	/**
	 * Finds the last value in this list. The get() method of this observable may have linear time unless this is an instance of
	 * {@link ObservableRandomAccessList}
	 *
	 * @return The last value in this collection, or null if this collection is empty
	 */
	default ObservableValue<E> last() {
		return debug(new OrderedCollectionFinder<>(this, value -> true, false)).from("last", this).get();
	}

	@Override
	default <T> ObservableOrderedCollection<T> map(Function<? super E, T> map) {
		return map(ObservableUtils.getReturnType(map), map);
	}

	@Override
	default <T> ObservableOrderedCollection<T> map(Type type, Function<? super E, T> map) {
		return debug(new MappedObservableOrderedCollection<>(this, type, map)).from("map", this).using("map", map).get();
	}

	@Override
	default ObservableOrderedCollection<E> filter(Predicate<? super E> filter) {
		return label(filterMap(value -> {
			return (value != null && filter.test(value)) ? value : null;
		})).label("filter").tag("filter", filter).get();
	}

	@Override
	default <T> ObservableOrderedCollection<T> filter(Class<T> type) {
		return label(filterMap(value -> type.isInstance(value) ? type.cast(value) : null)).tag("filterType", type).get();
	}

	@Override
	default <T> ObservableOrderedCollection<T> filterMap(Function<? super E, T> filterMap) {
		return filterMap(ObservableUtils.getReturnType(filterMap), filterMap);
	}

	@Override
	default <T> ObservableOrderedCollection<T> filterMap(Type type, Function<? super E, T> map) {
		return debug(new FilteredOrderedCollection<>(this, type, map)).from("filterMap", this).using("map", map).get();
	}

	@Override
	default <T, V> ObservableOrderedCollection<V> combine(ObservableValue<T> arg, BiFunction<? super E, ? super T, V> func) {
		return combine(arg, ObservableUtils.getReturnType(func), func);
	}

	@Override
	default <T, V> ObservableOrderedCollection<V> combine(ObservableValue<T> arg, Type type, BiFunction<? super E, ? super T, V> func) {
		return debug(new CombinedObservableOrderedCollection<>(this, arg, type, func)).from("combine", this).from("with", arg)
			.using("combination", func).get();
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A collection whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableOrderedCollection<E> refresh(Observable<?> refresh) {
		return debug(new RefreshingOrderedCollection<>(this, refresh)).from("refresh", this).from("on", refresh).get();
	}

	@Override
	default ObservableOrderedCollection<E> refreshEach(Function<? super E, Observable<?>> refire) {
		return debug(new ElementRefreshingOrderedCollection<>(this, refire)).from("refreshEach", this).using("on", refire).get();
	}

	@Override
	default ObservableOrderedCollection<E> immutable() {
		return debug(new ImmutableOrderedObservableCollection<>(this)).from("immutable", this).get();
	}

	@Override
	default ObservableOrderedCollection<E> cached() {
		return debug(new SafeCachedOrderedObservableCollection<>(this)).from("cached", this).get();
	}

	/**
	 * @param <E> The type of the collection to sort
	 * @param coll The collection whose elements to sort
	 * @param compare The comparator to sort the elements
	 * @return An observable collection containing all elements in <code>coll</code> (even multiples for which <code>compare</code>
	 *         {@link Comparator#compare(Object, Object) .compare} returns 0), sorted according to <code>compare()</code>.
	 */
	public static <E> ObservableOrderedCollection<E> sort(ObservableCollection<E> coll, java.util.Comparator<? super E> compare) {
		if(compare == null) {
			if(!new Type(Comparable.class).isAssignable(coll.getType()))
				throw new IllegalArgumentException("No natural ordering for collection of type " + coll.getType());
			compare = (Comparator<? super E>) (Comparable<Comparable<?>> o1, Comparable<Comparable<?>> o2) -> o1.compareTo(o2);
		}
		return debug(new SortedObservableCollectionWrapper<>(coll, compare)).from("sort", coll).using("compare", compare).get();
	}

	/**
	 * Flattens a collection of ordered collections. The inner collections must be sorted according to the given comparator in order for the
	 * result to be correctly sorted.
	 *
	 * @param <E> The super-type of all collections in the wrapping collection
	 * @param list The collection to flatten
	 * @param compare The comparator to compare elements between collections
	 * @return A collection containing all elements of all collections in the outer collection, sorted according to the given comparator,
	 *         then by order in the outer collection.
	 */
	public static <E> ObservableOrderedCollection<E> flatten(ObservableOrderedCollection<? extends ObservableOrderedCollection<E>> list,
		Comparator<? super E> compare) {
		if(compare == null) {
			compare = (Comparator<? super E>) (Comparable<Comparable<?>> o1, Comparable<Comparable<?>> o2) -> o1.compareTo(o2);
		}
		Comparator<? super E> fCompare = compare;
		class FlattenedOrderedElement extends FlattenedElement<E> implements OrderedObservableElement<E> {
			FlattenedOrderedElement(OrderedObservableElement<E> subEl, ObservableElement<? extends ObservableOrderedCollection<E>> subList) {
				super(subEl, subList);
			}

			@Override
			protected OrderedObservableElement<? extends ObservableOrderedCollection<E>> getSubCollectionElement() {
				return (OrderedObservableElement<? extends ObservableOrderedCollection<E>>) super.getSubCollectionElement();
			}

			@Override
			protected OrderedObservableElement<E> getSubElement() {
				return (OrderedObservableElement<E>) super.getSubElement();
			}

			@Override
			public int getIndex() {
				E value = get();
				int subListIndex = getSubCollectionElement().getIndex();
				int subElIdx = getSubElement().getIndex();
				int ret = 0;
				int index = 0;
				for(ObservableOrderedCollection<E> sub : list) {
					if(index == subListIndex) {
						ret += subElIdx;
						index++;
						continue;
					}
					for(E el : sub) {
						int comp = fCompare.compare(value, el);
						if(comp < 0)
							break;
						if(index > subListIndex && comp == 0)
							break;
						ret++;
					}
					index++;
				}
				return ret;
			}
		}
		class FlattenedObservableOrderedCollection extends AbstractCollection<E> implements ObservableOrderedCollection<E> {
			private final CombinedCollectionSessionObservable theSession = new CombinedCollectionSessionObservable(list);

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return theSession;
			}

			@Override
			public Type getType() {
				return list.getType().getParamTypes().length == 0 ? new Type(Object.class) : list.getType().getParamTypes()[0];
			}

			@Override
			public int size() {
				int ret = 0;
				for(ObservableOrderedCollection<E> subList : list)
					ret += subList.size();
				return ret;
			}

			@Override
			public Iterator<E> iterator() {
				ArrayList<Iterator<? extends E>> iters = new ArrayList<>();
				for(ObservableOrderedCollection<E> subList : list)
					iters.add(subList.iterator());
				return new Iterator<E>() {
					private Object [] subValues = new Object[iters.size()];

					/** This list represents the indexes of the sub values, as they would be in a list sorted according to the comparator. */
					private prisms.util.IntList indexes = new prisms.util.IntList(subValues.length);

					private boolean initialized;

					@Override
					public boolean hasNext() {
						if(!initialized)
							init();
						return !indexes.isEmpty();
					}

					@Override
					public E next() {
						if(!initialized)
							init();
						if(indexes.isEmpty())
							throw new java.util.NoSuchElementException();
						int nextIndex = indexes.remove(0);
						E ret = (E) subValues[nextIndex];
						if(iters.get(nextIndex).hasNext()) {
							E nextVal = iters.get(nextIndex).next();
							subValues[nextIndex] = nextVal;
							int i;
							for(i = 0; i < indexes.size(); i++) {
								int comp = fCompare.compare(nextVal, (E) subValues[indexes.get(i)]);
								if(comp < 0)
									continue;
								if(comp == 0 && nextIndex > indexes.get(i))
									continue;
							}
							indexes.add(i, nextIndex);
						} else
							subValues[nextIndex] = null;
						return ret;
					}

					private void init() {
						initialized = true;
						for(int i = 0; i < subValues.length; i++) {
							if(iters.get(i).hasNext()) {
								subValues[i] = iters.get(i).next();
								indexes.add(i);
							} else
								indexes.add(-1);
						}
						prisms.util.ArrayUtils.sort(subValues.clone(), new prisms.util.ArrayUtils.SortListener<Object>() {
							@Override
							public int compare(Object o1, Object o2) {
								if(o1 == null && o2 == null)
									return 0;
								// Place nulls last
								else if(o1 == null)
									return 1;
								else if(o2 == null)
									return -1;
								return fCompare.compare((E) o1, (E) o2);
							}

							@Override
							public void swapped(Object o1, int idx1, Object o2, int idx2) {
								int firstIdx = indexes.get(idx1);
								indexes.set(idx1, indexes.get(idx2));
								indexes.set(idx2, firstIdx);
							}
						});
						for(int i = 0; i < indexes.size(); i++) {
							if(indexes.get(i) < 0) {
								indexes.remove(i);
								i--;
							}
						}
					}
				};
			}

			@Override
			public boolean contains(Object o) {
				for(ObservableOrderedCollection<E> subList : list)
					if(subList.contains(o))
						return true;
				return false;
			}

			@Override
			public boolean containsAll(Collection<?> c) {
				Object [] ca = c.toArray();
				BitSet contained = new BitSet(ca.length);
				for(ObservableOrderedCollection<E> subList : list) {
					for(int i = contained.nextClearBit(0); i < ca.length; i = contained.nextClearBit(i))
						if(subList.contains(ca[i]))
							contained.set(i);
					if(contained.nextClearBit(0) == ca.length)
						break;
				}
				return contained.nextClearBit(0) == ca.length;
			}

			@Override
			public Runnable onOrderedElement(Consumer<? super OrderedObservableElement<E>> observer) {
				/* This has to be handled a little differently.  If the initial elements were simply handed to the observer, they could be
				 * out of order, since the first sub-collection might contain elements that compare greater to elements in a following
				 * sub-collection.  Thus, for example, the first element fed to the observer might have non-zero index.  This violates the
				 * contract of an ordered collection and will frequently cause problems in the observer.
				 * Thus, we compile an ordered list of the initial elements and then send them all to the observer after all the lists have
				 * delivered their initial elements.  Subsequent elements can be delivered in the normal way. */
				final List<FlattenedOrderedElement> initialElements = new ArrayList<>();
				final boolean [] initializing = new boolean[] {true};
				Runnable ret = list.onElement(new Consumer<ObservableElement<? extends ObservableOrderedCollection<E>>>() {
					private Map<ObservableOrderedCollection<E>, Runnable> subListSubscriptions;

					{
						subListSubscriptions = new org.observe.util.ConcurrentIdentityHashMap<>();
					}

					@Override
					public void accept(ObservableElement<? extends ObservableOrderedCollection<E>> subList) {
						subList.subscribe(new Observer<ObservableValueEvent<? extends ObservableOrderedCollection<E>>>() {
							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableOrderedCollection<E>>> void onNext(V2 subListEvent) {
								if(subListEvent.getOldValue() != null && subListEvent.getOldValue() != subListEvent.getValue()) {
									Runnable subListSub = subListSubscriptions.get(subListEvent.getOldValue());
									if(subListSub != null)
										subListSub.run();
								}
								Runnable subListSub = subListEvent.getValue().onElement(
									subElement -> {
										OrderedObservableElement<E> subListEl = (OrderedObservableElement<E>) subElement;
										FlattenedOrderedElement flatEl = debug(new FlattenedOrderedElement(subListEl, subList))
											.from("element", this).tag("wrappedCollectionElement", subList)
											.tag("wrappedSubElement", subListEl).get();
										if(initializing[0]) {
											int index = flatEl.getIndex();
											while(initialElements.size() <= index)
												initialElements.add(null);
											initialElements.set(index, flatEl);
										} else
											observer.accept(flatEl);
									});
								subListSubscriptions.put(subListEvent.getValue(), subListSub);
							}

							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableOrderedCollection<E>>> void onCompleted(
								V2 subListEvent) {
								subListSubscriptions.remove(subListEvent.getValue()).run();
							}
						});
					}
				});
				initializing[0] = false;
				for(FlattenedOrderedElement el : initialElements)
					observer.accept(el);
				initialElements.clear();
				return ret;
			}
		}
		return debug(new FlattenedObservableOrderedCollection()).from("flatten", list).using("compare", compare).get();
	}

	/**
	 * Implements {@link ObservableOrderedCollection#map(Function)}
	 *
	 * @param <E> The type of the collection to map
	 * @param <T> The type of the mapped collection
	 */
	class MappedObservableOrderedCollection<E, T> extends ObservableCollection.MappedObservableCollection<E, T> implements
	ObservableOrderedCollection<T> {
		protected MappedObservableOrderedCollection(ObservableOrderedCollection<E> wrap, Type type, Function<? super E, T> map) {
			super(wrap, type, map);
		}

		@Override
		public Runnable onOrderedElement(Consumer<? super OrderedObservableElement<T>> onElement) {
			return onElement(element -> onElement.accept((OrderedObservableElement<T>) element));
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#filterMap(Function)}
	 *
	 * @param <E> The type of the collection to be filter-mapped
	 * @param <T> The type of the mapped collection
	 */
	class FilteredOrderedCollection<E, T> extends ObservableCollection.FilteredCollection<E, T> implements ObservableOrderedCollection<T> {
		private List<FilteredOrderedElement<E, T>> theFilteredElements = new java.util.ArrayList<>();

		FilteredOrderedCollection(ObservableOrderedCollection<E> wrap, Type type, Function<? super E, T> map) {
			super(wrap, type, map);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		protected FilteredOrderedElement<E, T> filter(ObservableElement<E> element) {
			OrderedObservableElement<E> outerEl = (OrderedObservableElement<E>) element;
			FilteredOrderedElement<E, T> retElement = debug(new FilteredOrderedElement<>(outerEl, getMap(), getType(), theFilteredElements))
				.from("element", this).tag("wrapped", element).get();
			theFilteredElements.add(outerEl.getIndex(), retElement);
			outerEl.completed().act(elValue -> theFilteredElements.remove(outerEl.getIndex()));
			return retElement;
		}

		@Override
		public Runnable onOrderedElement(Consumer<? super OrderedObservableElement<T>> onElement) {
			return onElement(element -> onElement.accept((OrderedObservableElement<T>) element));
		}
	}

	/**
	 * The type of element in filtered ordered collections
	 *
	 * @param <T> The type of this element
	 * @param <E> The type of element wrapped by this element
	 */
	class FilteredOrderedElement<E, T> extends FilteredElement<E, T> implements OrderedObservableElement<T> {
		private List<FilteredOrderedElement<E, T>> theFilteredElements;

		FilteredOrderedElement(OrderedObservableElement<E> wrapped, Function<? super E, T> map, Type type,
			List<FilteredOrderedElement<E, T>> filteredEls) {
			super(wrapped, map, type);
			theFilteredElements = filteredEls;
		}

		@Override
		protected OrderedObservableElement<E> getWrapped() {
			return (OrderedObservableElement<E>) super.getWrapped();
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
	class CombinedObservableOrderedCollection<E, T, V> extends ObservableCollection.CombinedObservableCollection<E, T, V> implements
	ObservableOrderedCollection<V> {
		CombinedObservableOrderedCollection(ObservableOrderedCollection<E> collection, ObservableValue<T> value, Type type,
			BiFunction<? super E, ? super T, V> map) {
			super(collection, type, value, map);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public Runnable onOrderedElement(Consumer<? super OrderedObservableElement<V>> onElement) {
			return onElement(element -> onElement.accept((OrderedObservableElement<V>) element));
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#refresh(Observable)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	class RefreshingOrderedCollection<E> extends ObservableCollection.RefreshingCollection<E> implements ObservableOrderedCollection<E> {
		protected RefreshingOrderedCollection(ObservableOrderedCollection<E> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public Runnable onOrderedElement(Consumer<? super OrderedObservableElement<E>> onElement) {
			return onElement(element -> onElement.accept((OrderedObservableElement<E>) element));
		}
	}

	/**
	 * Implements {@link ObservableOrderedCollection#refreshEach(Function)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	class ElementRefreshingOrderedCollection<E> extends ObservableCollection.ElementRefreshingCollection<E> implements
	ObservableOrderedCollection<E> {
		protected ElementRefreshingOrderedCollection(ObservableOrderedCollection<E> wrap, Function<? super E, Observable<?>> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public Runnable onOrderedElement(Consumer<? super OrderedObservableElement<E>> onElement) {
			return onElement(element -> onElement.accept((OrderedObservableElement<E>) element));
		}
	}

	/**
	 * Backs elements in {@link ObservableOrderedCollection#sort(ObservableCollection, Comparator)}
	 *
	 * @param <E> The type of the element
	 */
	static class SortedElementWrapper<E> implements OrderedObservableElement<E> {
		private final SortedObservableCollectionWrapper<E> theList;

		private final ObservableElement<E> theWrapped;

		private final SortedObservableWrapperObserver<E> theParentObserver;

		SortedElementWrapper<E> theLeft;

		SortedElementWrapper<E> theRight;

		SortedElementWrapper(SortedObservableCollectionWrapper<E> list, ObservableElement<E> wrap,
			SortedObservableWrapperObserver<E> parentObs, SortedElementWrapper<E> anchor) {
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
		public Type getType() {
			return theWrapped.getType();
		}

		@Override
		public E get() {
			return theWrapped.get();
		}

		@Override
		public Runnable observe(Observer<? super ObservableValueEvent<E>> observer) {
			return theWrapped.observe(new Observer<ObservableValueEvent<E>>() {
				@Override
				public <V extends ObservableValueEvent<E>> void onNext(V event) {
					if(theLeft != null && theList.getCompare().compare(event.getValue(), theLeft.get()) < 0) {
						observer.onCompleted(new ObservableValueEvent<>(SortedElementWrapper.this, event.getOldValue(), event.getValue(),
							event));
						theLeft.theRight = theRight;
						if(theRight != null)
							theRight.theLeft = theLeft;
						findPlace(event.getValue(), theLeft);
						theParentObserver.theOuterObserver.accept(SortedElementWrapper.this);
					} else if(theRight != null && theList.getCompare().compare(event.getValue(), theRight.get()) > 0) {
						observer.onCompleted(new ObservableValueEvent<>(SortedElementWrapper.this, event.getOldValue(), event.getValue(),
							event));
						if(theLeft != null)
							theLeft.theRight = theRight;
						theRight.theLeft = theLeft;
						findPlace(event.getValue(), theRight);
						theParentObserver.theOuterObserver.accept(SortedElementWrapper.this);
					} else
						observer.onNext(new ObservableValueEvent<>(SortedElementWrapper.this, event.getOldValue(), event.getValue(), event));
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
					observer.onCompleted(new ObservableValueEvent<>(SortedElementWrapper.this, event.getOldValue(), event.getValue(), event));
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
	 * Backs {@link ObservableOrderedCollection#sort(ObservableCollection, Comparator)}
	 *
	 * @param <E> The type of the elements in the collection
	 */
	static class SortedObservableCollectionWrapper<E> implements PartialCollectionImpl<E>, ObservableOrderedCollection<E> {
		private final ObservableCollection<E> theWrapped;

		private final Comparator<? super E> theCompare;

		SortedObservableCollectionWrapper(ObservableCollection<E> wrap, Comparator<? super E> compare) {
			theWrapped = wrap;
			theCompare = compare;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		Comparator<? super E> getCompare() {
			return theCompare;
		}

		@Override
		public Type getType() {
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
		public Runnable onOrderedElement(Consumer<? super OrderedObservableElement<E>> observer) {
			return theWrapped.onElement(new SortedObservableWrapperObserver<>(this, observer));
		}
	}

	/**
	 * Used by {@link ObservableOrderedCollection#sort(ObservableCollection, Comparator)}
	 *
	 * @param <E> The type of the elements in the collection
	 */
	static class SortedObservableWrapperObserver<E> implements Consumer<ObservableElement<E>> {
		private final SortedObservableCollectionWrapper<E> theList;

		final Consumer<? super OrderedObservableElement<E>> theOuterObserver;

		private SortedElementWrapper<E> theAnchor;

		SortedObservableWrapperObserver(SortedObservableCollectionWrapper<E> list, Consumer<? super OrderedObservableElement<E>> outerObs) {
			theList = list;
			theOuterObserver = outerObs;
		}

		@Override
		public void accept(ObservableElement<E> outerEl) {
			SortedElementWrapper<E> newEl = debug(new SortedElementWrapper<>(theList, outerEl, this, theAnchor)).from("element", theList)
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
	class ImmutableOrderedObservableCollection<E> extends ImmutableObservableCollection<E> implements
	ObservableOrderedCollection<E> {
		protected ImmutableOrderedObservableCollection(ObservableOrderedCollection<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		@Override
		public Runnable onOrderedElement(Consumer<? super OrderedObservableElement<E>> observer) {
			return getWrapped().onOrderedElement(observer);
		}

		@Override
		public ImmutableOrderedObservableCollection<E> immutable() {
			return this;
		}
	}

	/**
	 * Backs {@link ObservableOrderedCollection#cached()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class SafeCachedOrderedObservableCollection<E> extends SafeCachedObservableCollection<E> implements ObservableOrderedCollection<E> {
		protected static class OrderedCachedElement<E> extends CachedElement<E> implements OrderedObservableElement<E> {
			protected OrderedCachedElement(OrderedObservableElement<E> wrap) {
				super(wrap);
			}

			@Override
			protected OrderedObservableElement<E> getWrapped() {
				return (OrderedObservableElement<E>) super.getWrapped();
			}

			@Override
			public int getIndex() {
				return getWrapped().getIndex();
			}
		}

		protected SafeCachedOrderedObservableCollection(ObservableOrderedCollection<E> wrap) {
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
			return new OrderedCachedElement<>((OrderedObservableElement<E>) element);
		}

		@Override
		public Runnable onOrderedElement(Consumer<? super OrderedObservableElement<E>> onElement) {
			return onElement(element -> onElement.accept((OrderedObservableElement<E>) element));
		}

		@Override
		public ObservableOrderedCollection<E> cached() {
			return this;
		}
	}
}
