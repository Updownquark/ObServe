package org.observe.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.FlowOptions.MapDef;
import org.observe.collect.FlowOptions.MapOptions;
import org.observe.collect.FlowOptions.UniqueOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableCollection.ModFilterBuilder;
import org.observe.collect.ObservableCollectionDataFlowImpl.ActiveSetManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.PassiveCollectionManager;
import org.observe.collect.ObservableSetImpl.DistinctBaseFlow;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableSortedSet} and {@link DistinctSortedDataFlow} methods */
public class ObservableSortedSetImpl {
	private ObservableSortedSetImpl() {}

	/**
	 * Implements {@link ObservableSortedSet#observeRelative(Comparable, SortedSearchFilter, java.util.function.Supplier)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class RelativeFinder<E> extends ObservableCollectionImpl.AbstractObservableElementFinder<E> {
		private final Comparable<? super E> theSearch;
		private final BetterSortedList.SortedSearchFilter theFilter;

		/**
		 * @param set The sorted set to search
		 * @param search The search comparable
		 * @param filter The search filter
		 */
		public RelativeFinder(ObservableSortedSet<E> set, Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter) {
			super(set, (el1, el2) -> {
				int comp1 = search.compareTo(el1.get());
				int comp2 = search.compareTo(el2.get());
				if (comp1 == 0) {
					if (comp2 == 0)
						return 0;
					else
						return -1;
				} else if (comp2 == 0)
					return 1;
				// Neither are a perfect match.
				// From here on, it's safe to assume filter.less.value is non-null because otherwise one or the other element
				// would not have passed the test method.
				// Keep in mind that the comparisons were based on the search value first, so the signs here are a bit counterintuitive
				else if (comp1 > 0) {
					if (comp2 > 0)
						return -el1.getElementId().compareTo(el2.getElementId());// Both less, so take the greater of the two
					else
						return filter.less.value ? -1 : 1;
				} else {
					if (comp2 > 0)
						return filter.less.value ? 1 : -1;
					else
						return el1.getElementId().compareTo(el2.getElementId());// Both greater, so take the lesser of the two
				}
			}, () -> null, null);
			theSearch = search;
			theFilter = filter;
		}

		@Override
		protected ObservableSortedSet<E> getCollection() {
			return (ObservableSortedSet<E>) super.getCollection();
		}

		@Override
		protected boolean useCachedMatch(E value) {
			return false; // Can't use cached values for this
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getCollection().getIdentity(), "find", theSearch, theFilter);
		}

		@Override
		protected boolean find(Consumer<? super CollectionElement<? extends E>> onElement) {
			CollectionElement<E> el = getCollection().search(theSearch, theFilter);
			if (el == null)
				return false;
			onElement.accept(el);
			return true;
		}

		@Override
		protected boolean test(E value) {
			// Keep in mind that the comparisons were based on the search value first, so the signs here are a bit counterintuitive
			switch (theFilter) {
			case Less:
				return theSearch.compareTo(value) >= 0;
			case OnlyMatch:
				return theSearch.compareTo(value) == 0;
			case Greater:
				return theSearch.compareTo(value) <= 0;
			default:
				return true;
			}
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#subSet(Comparable, Comparable)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class ObservableSubSet<E> extends BetterSortedSet.BetterSubSet<E> implements ObservableSortedSet<E> {
		/**
		 * @param set The super set
		 * @param from The lower bound of this sub-set
		 * @param to The upper bound of this sub-set
		 */
		public ObservableSubSet(ObservableSortedSet<E> set, Comparable<? super E> from, Comparable<? super E> to) {
			super(set, from, to);
		}

		@Override
		public ObservableSortedSet<E> getWrapped() {
			return (ObservableSortedSet<E>) super.getWrapped();
		}

		@Override
		public TypeToken<E> getType() {
			return getWrapped().getType();
		}

		@Override
		public boolean isLockSupported() {
			return getWrapped().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return getWrapped().lock(write, cause);
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return getWrapped().equivalence();
		}

		@Override
		public E[] toArray() {
			return ObservableSortedSet.super.toArray();
		}

		@Override
		public void setValue(Collection<ElementId> elements, E value) {
			getWrapped().setValue(elements, value);
		}

		@Override
		public ObservableSortedSet<E> subSet(Comparable<? super E> from, Comparable<? super E> to) {
			return new ObservableSubSet<>(getWrapped(), BetterSortedList.and(getFrom(), from, true),
				BetterSortedList.and(getTo(), to, false));
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			try (Transaction t = lock(false, null)) {
				return getWrapped().onChange(new Consumer<ObservableCollectionEvent<? extends E>>() {
					private final BetterSortedSet<ElementId> thePresentElements;
					{
						thePresentElements = new BetterTreeSet<>(false, ElementId::compareTo);
						for (CollectionElement<E> el : elements())
							thePresentElements.add(el.getElementId());
					}

					@Override
					public void accept(ObservableCollectionEvent<? extends E> evt) {
						int inRange = isInRange(evt.getNewValue());
						int oldInRange = evt.getType() == CollectionChangeType.set ? isInRange(evt.getOldValue()) : 0;
						CollectionElement<ElementId> presentEl;
						if (inRange < 0) {
							switch (evt.getType()) {
							case add:
								break;
							case remove:
								break;
							case set:
								if (oldInRange == 0) {
									presentEl = thePresentElements.getElement(evt.getElementId(), true);// Get by value
									int index = thePresentElements.getElementsBefore(presentEl.getElementId());
									thePresentElements.mutableElement(presentEl.getElementId()).remove();
									fire(evt, CollectionChangeType.remove, index, evt.getOldValue(), evt.getOldValue());
								}
							}
						} else if (inRange > 0) {
							switch (evt.getType()) {
							case set:
								if (oldInRange == 0) {
									presentEl = thePresentElements.getElement(evt.getElementId(), true);// Get by value
									int index = thePresentElements.getElementsBefore(presentEl.getElementId());
									thePresentElements.mutableElement(presentEl.getElementId()).remove();
									fire(evt, CollectionChangeType.remove, index, evt.getOldValue(), evt.getOldValue());
								}
								break;
							default:
							}
						} else {
							switch (evt.getType()) {
							case add:
								presentEl = thePresentElements.addElement(evt.getElementId(), false);
								int index = thePresentElements.getElementsBefore(presentEl.getElementId());
								fire(evt, evt.getType(), index, null, evt.getNewValue());
								break;
							case remove:
								presentEl = thePresentElements.getElement(evt.getElementId(), true);// Get by value
								index = thePresentElements.getElementsBefore(presentEl.getElementId());
								thePresentElements.mutableElement(presentEl.getElementId()).remove();
								fire(evt, evt.getType(), index, evt.getOldValue(), evt.getNewValue());
								break;
							case set:
								if (oldInRange != 0) {
									presentEl = thePresentElements.addElement(evt.getElementId(), false);
									index = thePresentElements.getElementsBefore(presentEl.getElementId());
									fire(evt, CollectionChangeType.add, index, null, evt.getNewValue());
								} else {
									presentEl = thePresentElements.getElement(evt.getElementId(), true);// Get by value
									index = thePresentElements.getElementsBefore(presentEl.getElementId());
									fire(evt, CollectionChangeType.set, index, evt.getOldValue(), evt.getNewValue());
								}
							}
						}
					}

					void fire(ObservableCollectionEvent<? extends E> evt, CollectionChangeType type, int index, E oldValue, E newValue) {
						ObservableCollectionEvent<? extends E> evt2 = new ObservableCollectionEvent<>(evt.getElementId(), getType(), index,
							type, oldValue, newValue, evt);
						try (Transaction evtT = Causable.use(evt2)) {
							observer.accept(evt2);
						}
					}
				});
			}
		}

		@Override
		public String toString() {
			return BetterSet.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class ReversedSortedSet<E> extends ObservableSetImpl.ReversedSet<E> implements ObservableSortedSet<E> {
		/** @param wrap The sorted set to reverse */
		public ReversedSortedSet(ObservableSortedSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableSortedSet<E> getWrapped() {
			return (ObservableSortedSet<E>) super.getWrapped();
		}

		@Override
		public Comparator<? super E> comparator() {
			return BetterSortedSet.ReversedSortedSet.reverse(getWrapped().comparator());
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			try (Transaction t = lock(false, null)) {
				int index = getWrapped().indexFor(BetterSortedSet.ReversedSortedSet.reverse(search));
				if (index >= 0)
					return size() - index - 1;
				else {
					index = -index - 1;
					index = size() - index;
					return -(index + 1);
				}
			}
		}

		@Override
		public CollectionElement<E> search(Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter) {
			return CollectionElement.reverse(getWrapped().search(v -> -search.compareTo(v), filter.opposite()));
		}
	}

	/**
	 * An abstract {@link DistinctSortedDataFlow} implementation returning default {@link DistinctSortedDataFlow} implementations for most
	 * operations that should produce one
	 *
	 * @param <E> The type of the source collection
	 * @param <T> The type of this flow
	 */
	public static class DistinctSortedDataFlowWrapper<E, T> extends ObservableSetImpl.DistinctDataFlowWrapper<E, T>
	implements DistinctSortedDataFlow<E, T, T> {
		private final Comparator<? super T> theCompare;

		/**
		 * @param source The source collection
		 * @param parent This flow's parent
		 * @param compare The comparator that this flow's elements are ordered by
		 */
		protected DistinctSortedDataFlowWrapper(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent,
			Comparator<? super T> compare) {
			super(source, parent, Equivalence.of(TypeTokens.getRawType(parent.getTargetType()), compare, true));
			theCompare = compare;
		}

		@Override
		public Equivalence.ComparatorEquivalence<? super T> equivalence() {
			return (Equivalence.ComparatorEquivalence<? super T>) super.equivalence();
		}

		@Override
		public Comparator<? super T> comparator() {
			return theCompare;
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> reverse() {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.reverse(), BetterSortedSet.ReversedSortedSet.reverse(theCompare));
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.filter(filter), theCompare);
		}

		@Override
		public <X> DistinctSortedDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.whereContained(other, include), theCompare);
		}

		@Override
		public <X> DistinctSortedDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, BiFunction<? super T, ? super X, ? extends X> map,
			Function<? super X, ? extends T> reverse, Consumer<MapOptions<T, X>> options) {
			MapOptions<T, X> mapOptions = new MapOptions<>();
			options.accept(mapOptions);
			mapOptions.withReverse(reverse);
			return new DistinctSortedMapOp<>(getSource(), this, target, map, new MapDef<>(mapOptions), (x1, x2) -> {
				return theCompare.compare(reverse.apply(x1), reverse.apply(x2));
			});
		}

		@Override
		public <X> DistinctSortedDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, BiFunction<? super T, ? super X, ? extends X> map,
			Comparator<? super X> compare, Consumer<MapOptions<T, X>> options) {
			MapOptions<T, X> mapOptions = new MapOptions<>();
			options.accept(mapOptions);
			return new DistinctSortedMapOp<>(getSource(), this, target, map, new MapDef<>(mapOptions), compare);
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> distinct(Consumer<UniqueOptions> options) {
			options.accept(new FlowOptions.SimpleUniqueOptions(true));
			return this; // No-op
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.refresh(refresh), theCompare);
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.refreshEach(refresh), theCompare);
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.filterMod(options), theCompare);
		}

		@Override
		public ActiveSetManager<E, ?, T> manageActive() {
			return super.manageActive();
		}

		@Override
		public ObservableSortedSet<T> collectPassive() {
			if (!supportsPassive())
				throw new UnsupportedOperationException("Passive collection not supported");
			return new PassiveDerivedSortedSet<>((ObservableSortedSet<E>) getSource(), managePassive(), comparator());
		}

		@Override
		public ObservableSortedSet<T> collectActive(Observable<?> until) {
			return new ActiveDerivedSortedSet<>(manageActive(), comparator(), until);
		}
	}

	/**
	 * Implements {@link CollectionDataFlow#distinctSorted(Comparator, boolean)}
	 *
	 * @param <E> The type of the source collection
	 * @param <T> The type of this flow
	 */
	public static class DistinctSortedOp<E, T> extends DistinctSortedDataFlowWrapper<E, T> {
		private final boolean isAlwaysUsingFirst;

		/**
		 * @param source The source collection
		 * @param parent The parent flow
		 * @param compare The comparator to order this flow's elements
		 * @param alwaysUseFirst Whether to always use the earliest element in a category of equivalent values to represent the group in
		 *        this flow
		 */
		protected DistinctSortedOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, Comparator<? super T> compare,
			boolean alwaysUseFirst) {
			super(source, parent, compare);
			isAlwaysUsingFirst = alwaysUseFirst;
		}

		@Override
		public boolean supportsPassive() {
			return false;
		}

		@Override
		public ActiveSetManager<E, ?, T> manageActive() {
			return new ObservableSetImpl.DistinctManager<>(
				new ObservableCollectionDataFlowImpl.ActiveEquivalenceSwitchedManager<>(getParent().manageActive(), equivalence()),
				equivalence(), isAlwaysUsingFirst, false);
		}
	}

	/**
	 * Implements {@link DistinctSortedDataFlow#mapEquivalent(TypeToken, Function, Comparator)}
	 *
	 * @param <E> The type of the source collection
	 * @param <I> The type of the parent flow
	 * @param <T> The type of this flow
	 */
	public static class DistinctSortedMapOp<E, I, T> extends ObservableSetImpl.DistinctMapOp<E, I, T> implements DistinctSortedDataFlow<E, I, T> {
		private final Comparator<? super T> theCompare;

		/**
		 * @param source The source collection
		 * @param parent The parent flow
		 * @param target The type of this flow
		 * @param map The mapping function to produce this flow's values from its source
		 * @param options The options governing certain aspects of this flow's behavior, e.g. caching
		 * @param compare The comparator that this flow's values are sorted by
		 */
		public DistinctSortedMapOp(ObservableCollection<E> source, DistinctDataFlow<E, ?, I> parent, TypeToken<T> target,
			BiFunction<? super I, ? super T, ? extends T> map, MapDef<I, T> options, Comparator<? super T> compare) {
			super(source, parent, target, map, options);
			theCompare = compare;
		}

		@Override
		public Comparator<? super T> comparator() {
			return theCompare;
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> reverse() {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.reverse(), BetterSortedSet.ReversedSortedSet.reverse(theCompare));
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.filter(filter), comparator());
		}

		@Override
		public <X> DistinctSortedDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.whereContained(other, include), theCompare);
		}

		@Override
		public <X> DistinctSortedDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, BiFunction<? super T, ? super X, ? extends X> map,
			Function<? super X, ? extends T> reverse, Consumer<MapOptions<T, X>> options) {
			MapOptions<T, X> mapOptions = new MapOptions<>();
			options.accept(mapOptions);
			mapOptions.withReverse(reverse);
			return new DistinctSortedMapOp<>(getSource(), this, target, map, new MapDef<>(mapOptions), (x1, x2) -> {
				return theCompare.compare(reverse.apply(x1), reverse.apply(x2));
			});
		}

		@Override
		public <X> DistinctSortedDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, BiFunction<? super T, ? super X, ? extends X> map,
			Comparator<? super X> compare, Consumer<MapOptions<T, X>> options) {
			MapOptions<T, X> mapOptions = new MapOptions<>();
			options.accept(mapOptions);
			return new DistinctSortedMapOp<>(getSource(), this, target, map, new MapDef<>(mapOptions), compare);
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.refresh(refresh), comparator());
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.refreshEach(refresh), comparator());
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.filterMod(options), theCompare);
		}

		@Override
		public ActiveSetManager<E, ?, T> manageActive() {
			return super.manageActive();
		}

		@Override
		public ObservableSortedSet<T> collectPassive() {
			return new PassiveDerivedSortedSet<>((ObservableSortedSet<E>) getSource(), managePassive(), comparator());
		}

		@Override
		public ObservableSortedSet<T> collectActive(Observable<?> until) {
			return new ActiveDerivedSortedSet<>(manageActive(), comparator(), until);
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#flow()}
	 *
	 * @param <E> The type of this flow
	 */
	public static class DistinctSortedBaseFlow<E> extends DistinctBaseFlow<E> implements DistinctSortedDataFlow<E, E, E> {
		/** @param source The source set */
		public DistinctSortedBaseFlow(ObservableSortedSet<E> source) {
			super(source);
		}

		@Override
		protected ObservableSortedSet<E> getSource() {
			return (ObservableSortedSet<E>) super.getSource();
		}

		@Override
		public Comparator<? super E> comparator() {
			return getSource().comparator();
		}

		@Override
		public DistinctSortedDataFlow<E, E, E> reverse() {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.reverse(),
				BetterSortedSet.ReversedSortedSet.reverse(comparator()));
		}

		@Override
		public DistinctSortedDataFlow<E, E, E> filter(Function<? super E, String> filter) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.filter(filter), getSource().comparator());
		}

		@Override
		public <X> DistinctSortedDataFlow<E, E, E> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.whereContained(other, include), comparator());
		}

		@Override
		public <X> DistinctSortedDataFlow<E, E, X> mapEquivalent(TypeToken<X> target, BiFunction<? super E, ? super X, ? extends X> map,
			Function<? super X, ? extends E> reverse, Consumer<MapOptions<E, X>> options) {
			MapOptions<E, X> mapOptions = new MapOptions<>();
			options.accept(mapOptions);
			mapOptions.withReverse(reverse);
			return new DistinctSortedMapOp<>(getSource(), this, target, map, new MapDef<>(mapOptions), (x1, x2) -> {
				return comparator().compare(reverse.apply(x1), reverse.apply(x2));
			});
		}

		@Override
		public <X> DistinctSortedDataFlow<E, E, X> mapEquivalent(TypeToken<X> target, BiFunction<? super E, ? super X, ? extends X> map,
			Comparator<? super X> compare, Consumer<MapOptions<E, X>> options) {
			MapOptions<E, X> mapOptions = new MapOptions<>();
			options.accept(mapOptions);
			return new DistinctSortedMapOp<>(getSource(), this, target, map, new MapDef<>(mapOptions), compare);
		}

		@Override
		public DistinctSortedDataFlow<E, E, E> refresh(Observable<?> refresh) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.refresh(refresh), getSource().comparator());
		}

		@Override
		public DistinctSortedDataFlow<E, E, E> refreshEach(Function<? super E, ? extends Observable<?>> refresh) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.refreshEach(refresh), getSource().comparator());
		}

		@Override
		public DistinctSortedDataFlow<E, E, E> filterMod(Consumer<ModFilterBuilder<E>> options) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.filterMod(options), comparator());
		}

		@Override
		public ObservableSortedSet<E> collect() {
			return (ObservableSortedSet<E>) super.collect();
		}

		@Override
		public ActiveSetManager<E, ?, E> manageActive() {
			return super.manageActive();
		}

		@Override
		public ObservableSortedSet<E> collectPassive() {
			return getSource();
		}

		@Override
		public ObservableSortedSet<E> collectActive(Observable<?> until) {
			return new ActiveDerivedSortedSet<>(manageActive(), comparator(), until);
		}
	}

	/**
	 * A {@link DistinctSortedDataFlow#collect() collected}, {@link DistinctSortedDataFlow#supportsPassive() passive}ly-derived sorted set
	 *
	 * @param <E> The type of the source set
	 * @param <T> The type of this set
	 */
	public static class PassiveDerivedSortedSet<E, T> extends ObservableSetImpl.PassiveDerivedSet<E, T> implements ObservableSortedSet<T> {
		private final Comparator<? super T> theCompare;

		/**
		 * @param source The source set
		 * @param flow The passive manager
		 * @param compare The comparator by which the values are sorted
		 */
		public PassiveDerivedSortedSet(ObservableSortedSet<E> source, PassiveCollectionManager<E, ?, T> flow,
			Comparator<? super T> compare) {
			super(source, flow);
			theCompare = compare;
		}

		@Override
		protected ObservableSortedSet<E> getSource() {
			return (ObservableSortedSet<E>) super.getSource();
		}

		@Override
		public Comparator<? super T> comparator() {
			return theCompare;
		}

		/**
		 * @param search The search comparable for this set
		 * @return The search comparable to use on the source set
		 */
		protected Comparable<? super E> mappedSearch(Comparable<? super T> search) {
			Comparable<? super T> fSearch = isReversed() ? BetterSortedSet.ReversedSortedSet.reverse(search) : search;
			Function<? super E, ? extends T> map = getFlow().map().get();
			if (LambdaUtils.getIdentifier(map) == LambdaUtils.IDENTITY)
				return (Comparable<? super E>) fSearch;
			return LambdaUtils.printableComparable(v -> fSearch.compareTo(map.apply(v)), () -> fSearch + ".mapFrom(" + map + ")");
		}

		@Override
		public int indexFor(Comparable<? super T> search) {
			int sourceIndex = getSource().indexFor(mappedSearch(search));
			if (isReversed()) {
				if (sourceIndex < 0)
					sourceIndex = -(size() + sourceIndex) - 2;
				else
					sourceIndex = size() - 1 - sourceIndex;
			}
			return sourceIndex;
		}

		@Override
		public CollectionElement<T> search(Comparable<? super T> search, BetterSortedList.SortedSearchFilter filter) {
			if (isReversed())
				filter = filter.opposite();
			CollectionElement<E> srcEl = getSource().search(mappedSearch(search), filter);
			return srcEl == null ? null : elementFor(srcEl, null);
		}
	}

	/**
	 * A {@link DistinctSortedDataFlow#collect() collected}, {@link DistinctSortedDataFlow#supportsPassive() active}ly-derived sorted set
	 *
	 * @param <T> The type of this set
	 */
	public static class ActiveDerivedSortedSet<T> extends ObservableSetImpl.ActiveDerivedSet<T> implements ObservableSortedSet<T> {
		private final Comparator<? super T> theCompare;

		/**
		 * @param flow The active manager to drive this set
		 * @param compare The comparator by which this set's values will be sorted
		 * @param until The observable to terminate this derived set
		 */
		public ActiveDerivedSortedSet(ActiveSetManager<?, ?, T> flow, Comparator<? super T> compare,
			Observable<?> until) {
			super(flow, until);
			theCompare = compare;
		}

		@Override
		public Comparator<? super T> comparator() {
			return theCompare;
		}

		@Override
		public int indexFor(Comparable<? super T> search) {
			return getPresentElements().indexFor(el -> search.compareTo(el.get()));
		}

		@Override
		public CollectionElement<T> search(Comparable<? super T> search, BetterSortedList.SortedSearchFilter filter) {
			CollectionElement<DerivedElementHolder<T>> presentEl = getPresentElements().search(el -> search.compareTo(el.get()),
				filter);
			return presentEl == null ? null : elementFor(presentEl.get());
		}
	}

	/**
	 * Implements {@link ObservableSortedSet#flattenValue(ObservableValue, Comparator)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class FlattenedValueSortedSet<E> extends ObservableCollectionImpl.FlattenedValueCollection<E>
	implements ObservableSortedSet<E> {
		private final Comparator<? super E> theCompare;

		/**
		 * @param collectionObservable The value containing a sorted set
		 * @param compare The comparator that all values the observable value may contain must use
		 */
		public FlattenedValueSortedSet(ObservableValue<? extends ObservableSortedSet<? extends E>> collectionObservable,
			Comparator<? super E> compare) {
			super(collectionObservable, Equivalence.of(extractElementType(collectionObservable), compare, false));
			theCompare = compare;
		}

		private static <E> Class<E> extractElementType(ObservableValue<? extends ObservableSortedSet<? extends E>> collectionObservable) {
			return (Class<E>) TypeTokens
				.getRawType(collectionObservable.getType().resolveType(ObservableCollection.class.getTypeParameters()[0]));
		}

		@Override
		protected ObservableValue<? extends ObservableSortedSet<E>> getWrapped() {
			return (ObservableValue<? extends ObservableSortedSet<E>>) super.getWrapped();
		}

		@Override
		public Comparator<? super E> comparator() {
			return theCompare;
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return ObservableSortedSet.super.addAll(c);
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			return ObservableSortedSet.super.addAll(index, c);
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return -1;
			return wrapped.indexFor(search);
		}

		@Override
		public CollectionElement<E> search(Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter) {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return null;
			return wrapped.search(search, filter);
		}

		@Override
		public CollectionElement<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable added) {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			return wrapped.getOrAdd(value, before, after, first, added);
		}

		@Override
		public boolean isConsistent(ElementId element) {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				throw new NoSuchElementException();
			return wrapped.isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return true;
			return wrapped.checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				throw new NoSuchElementException();
			return wrapped.repair(element, listener);
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return false;
			return wrapped.repair(listener);
		}
	}
}
