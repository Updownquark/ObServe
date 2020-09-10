package org.observe.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.Equivalence.ComparatorEquivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.Transformation;
import org.observe.Transformation.ReverseQueryResult;
import org.observe.Transformation.ReversibleTransformation;
import org.observe.Transformation.ReversibleTransformationPrecursor;
import org.observe.collect.FlowOptions.UniqueOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableCollection.ModFilterBuilder;
import org.observe.collect.ObservableCollectionActiveManagers.ActiveSetManager;
import org.observe.collect.ObservableCollectionPassiveManagers.PassiveCollectionManager;
import org.observe.collect.ObservableSetImpl.DistinctBaseFlow;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
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
		public Equivalence.ComparatorEquivalence<? super E> equivalence() {
			return getWrapped().equivalence();
		}

		@Override
		public E[] toArray() {
			return ObservableSortedSet.super.toArray();
		}

		@Override
		public void setValue(Collection<ElementId> elements, E value) {
			getWrapped().setValue(BetterList.of(elements.stream().map(BetterSortedSet.BetterSubSet::unwrap)), value);
		}

		@Override
		public ObservableSortedSet<E> subSet(Comparable<? super E> from, Comparable<? super E> to) {
			if (BetterCollections.simplifyDuplicateOperations())
				return new ObservableSubSet<>(getWrapped(), BetterSortedList.and(getFrom(), from, true),
					BetterSortedList.and(getTo(), to, false));
			else
				return ObservableSortedSet.super.subSet(from, to);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			try (Transaction t = lock(false, null)) {
				return getWrapped().onChange(new Consumer<ObservableCollectionEvent<? extends E>>() {
					private final BetterSortedSet<ElementId> thePresentElements;
					{
						thePresentElements = new BetterTreeSet<>(false, ElementId::compareTo);
						for (CollectionElement<E> el : elements())
							thePresentElements.add(unwrap(el.getElementId()));
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
						ObservableCollectionEvent<? extends E> evt2 = new ObservableCollectionEvent<>(wrap(evt.getElementId()), getType(),
							index, type, oldValue, newValue, evt);
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
		private final Equivalence.ComparatorEquivalence<? super E> theEquivalence;

		/** @param wrap The sorted set to reverse */
		public ReversedSortedSet(ObservableSortedSet<E> wrap) {
			super(wrap);
			theEquivalence = Equivalence.of(TypeTokens.getRawType(wrap.getType()),
				BetterSortedSet.ReversedSortedSet.reverse(wrap.comparator()), true);
		}

		@Override
		protected ObservableSortedSet<E> getWrapped() {
			return (ObservableSortedSet<E>) super.getWrapped();
		}

		@Override
		public Equivalence.ComparatorEquivalence<? super E> equivalence() {
			return theEquivalence;
		}

		@Override
		public Comparator<? super E> comparator() {
			return theEquivalence.comparator();
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

		@Override
		public ObservableSortedSet<E> reverse() {
			if (BetterCollections.simplifyDuplicateOperations())
				return getWrapped();
			else
				return ObservableSortedSet.super.reverse();
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
		/**
		 * @param source The source collection
		 * @param parent This flow's parent
		 * @param equivalence The equivalence with the comparator that this flow's elements are ordered by
		 */
		protected DistinctSortedDataFlowWrapper(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent,
			Equivalence.ComparatorEquivalence<? super T> equivalence) {
			super(source, parent, equivalence);
		}

		/**
		 * @param source The source collection
		 * @param parent This flow's parent
		 * @param compare The comparator that this flow's elements are ordered by
		 */
		protected DistinctSortedDataFlowWrapper(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent,
			Comparator<? super T> compare) {
			super(source, parent, Equivalence.of(TypeTokens.getRawType(parent.getTargetType()), compare, true));
		}

		@Override
		public Equivalence.ComparatorEquivalence<? super T> equivalence() {
			return (Equivalence.ComparatorEquivalence<? super T>) super.equivalence();
		}

		@Override
		public Comparator<? super T> comparator() {
			return equivalence().comparator();
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "distinctSorted", comparator());
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> reverse() {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.reverse(),
				BetterSortedSet.ReversedSortedSet.reverse(comparator()));
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.filter(filter), equivalence());
		}

		@Override
		public <X> DistinctSortedDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.whereContained(other, include), equivalence());
		}

		@Override
		public <X> DistinctSortedDataFlow<E, T, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, ReversibleTransformation<T, X>> transform) {
			ReversibleTransformation<T, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			Comparator<X> compare = new TransformedReverseComparator<>(def, equivalence());
			return new DistinctSortedTransformOp<>(getSource(), this, target, def,
				Equivalence.of(TypeTokens.getRawType(target), compare, true));
		}

		@Override
		public <X> DistinctSortedDataFlow<E, T, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, Transformation<T, X>> transform, Comparator<? super X> compare) {
			Transformation<T, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			return new DistinctSortedTransformOp<>(getSource(), this, target, def,
				Equivalence.of(TypeTokens.getRawType(target), compare, true));
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> distinct(Consumer<UniqueOptions> options) {
			options.accept(new FlowOptions.SimpleUniqueOptions(true));
			return this; // No-op
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.refresh(refresh), equivalence());
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.refreshEach(refresh), equivalence());
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.filterMod(options), equivalence());
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
	 * A comparator for mapped values
	 *
	 * @param <T> The type of the source value
	 * @param <X> The type of the mapped value
	 */
	public static class TransformedReverseComparator<T, X> implements Comparator<X> {
		private final ReversibleTransformation<T, X> theTransform;
		private final ComparatorEquivalence<? super T> theSourceCompare;

		/**
		 * @param transform The reversible transformation
		 * @param sourceCompare The source comparator
		 */
		public TransformedReverseComparator(ReversibleTransformation<T, X> transform, ComparatorEquivalence<? super T> sourceCompare) {
			theTransform = transform;
			theSourceCompare = sourceCompare;
		}

		@Override
		public int compare(X o1, X o2) {
			Transformation.Engine<T, X> engine = theTransform.createEngine(theSourceCompare);
			ReverseQueryResult<T> t1 = engine.reverse(o1, false, true);
			ReverseQueryResult<T> t2 = engine.reverse(o2, false, true);
			if (t1.getError() != null) {
				if (t2.getError() != null)
					return 0;
				else
					return 1;
			} else if (t2.getError() != null)
				return -1;
			return theSourceCompare.comparator().compare(t1.getReversed(), t2.getReversed());
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSourceCompare, theTransform);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof TransformedReverseComparator))
				return false;
			TransformedReverseComparator<?, ?> other = (TransformedReverseComparator<?, ?>) obj;
			return theTransform.equals(other.theTransform) && theSourceCompare.equals(other.theSourceCompare);
		}

		@Override
		public String toString() {
			return new StringBuilder(theSourceCompare.toString()).append('.').append(theTransform).toString();
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
				new ObservableCollectionActiveManagers.ActiveEquivalenceSwitchedManager<>(getParent().manageActive(), equivalence()),
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
	public static class DistinctSortedTransformOp<E, I, T> extends ObservableSetImpl.DistinctTransformOp<E, I, T>
	implements DistinctSortedDataFlow<E, I, T> {
		/**
		 * @param source The source collection
		 * @param parent The parent flow
		 * @param target The type of this flow
		 * @param def The transformation definition of this operation
		 * @param equivalence The equivalence containing the comparator that this flow's values are sorted by
		 */
		public DistinctSortedTransformOp(ObservableCollection<E> source, DistinctDataFlow<E, ?, I> parent, TypeToken<T> target,
			Transformation<I, T> def, Equivalence.ComparatorEquivalence<? super T> equivalence) {
			super(source, parent, target, def, equivalence);
		}

		@Override
		public Equivalence.ComparatorEquivalence<? super T> equivalence() {
			return (ComparatorEquivalence<? super T>) super.equivalence();
		}

		@Override
		public Comparator<? super T> comparator() {
			return equivalence().comparator();
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> reverse() {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.reverse(),
				BetterSortedSet.ReversedSortedSet.reverse(comparator()));
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.filter(filter), comparator());
		}

		@Override
		public <X> DistinctSortedDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.whereContained(other, include), comparator());
		}

		@Override
		public <X> DistinctSortedDataFlow<E, T, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, ReversibleTransformation<T, X>> transform) {
			ReversibleTransformation<T, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			return new DistinctSortedTransformOp<>(getSource(), this, target, def, //
				Equivalence.of(TypeTokens.getRawType(target), new TransformedReverseComparator<>(def, equivalence()), true));
		}

		@Override
		public <X> DistinctSortedDataFlow<E, T, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, Transformation<T, X>> transform, Comparator<? super X> compare) {
			Transformation<T, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			return new DistinctSortedTransformOp<>(getSource(), this, target, def,
				Equivalence.of(TypeTokens.getRawType(target), compare, true));
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
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.filterMod(options), equivalence());
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
		public Equivalence.ComparatorEquivalence<? super E> equivalence() {
			return (ComparatorEquivalence<? super E>) super.equivalence();
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
		public <X> DistinctSortedDataFlow<E, E, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<E, X, ?>, ReversibleTransformation<E, X>> transform) {
			ReversibleTransformation<E, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			Comparator<X> compare = new TransformedReverseComparator<>(def, equivalence());
			return new DistinctSortedTransformOp<>(getSource(), this, target, def,
				Equivalence.of(TypeTokens.getRawType(target), compare, true));
		}

		@Override
		public <X> DistinctSortedDataFlow<E, E, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<E, X, ?>, Transformation<E, X>> transform, Comparator<? super X> compare) {
			Transformation<E, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			return new DistinctSortedTransformOp<>(getSource(), this, target, def,
				Equivalence.of(TypeTokens.getRawType(target), compare, true));
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
		private final Equivalence.ComparatorEquivalence<? super T> theEquivalence;

		/**
		 * @param source The source set
		 * @param flow The passive manager
		 * @param compare The comparator by which the values are sorted
		 */
		public PassiveDerivedSortedSet(ObservableSortedSet<E> source, PassiveCollectionManager<E, ?, T> flow,
			Comparator<? super T> compare) {
			super(source, flow);
			theEquivalence = flow.equivalence() instanceof Equivalence.ComparatorEquivalence
				? (Equivalence.ComparatorEquivalence<? super T>) flow.equivalence()
					: Equivalence.of(TypeTokens.getRawType(flow.getTargetType()), compare, true);
		}

		@Override
		protected ObservableSortedSet<E> getSource() {
			return (ObservableSortedSet<E>) super.getSource();
		}

		@Override
		public Equivalence.ComparatorEquivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public Comparator<? super T> comparator() {
			return theEquivalence.comparator();
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
		private final Equivalence.ComparatorEquivalence<? super T> theEquivalence;

		/**
		 * @param flow The active manager to drive this set
		 * @param compare The comparator by which this set's values will be sorted
		 * @param until The observable to terminate this derived set
		 */
		public ActiveDerivedSortedSet(ActiveSetManager<?, ?, T> flow, Comparator<? super T> compare, Observable<?> until) {
			super(flow, until);
			theEquivalence = flow.equivalence() instanceof Equivalence.ComparatorEquivalence
				? (Equivalence.ComparatorEquivalence<? super T>) flow.equivalence()
					: Equivalence.of(TypeTokens.getRawType(flow.getTargetType()), compare, true);
		}

		@Override
		public Equivalence.ComparatorEquivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public Comparator<? super T> comparator() {
			return theEquivalence.comparator();
		}

		@Override
		public int indexFor(Comparable<? super T> search) {
			return getPresentElements().indexFor(el -> search.compareTo(el.get()));
		}

		@Override
		public CollectionElement<T> search(Comparable<? super T> search, BetterSortedList.SortedSearchFilter filter) {
			CollectionElement<DerivedElementHolder<T>> presentEl = getPresentElements().search(el -> search.compareTo(el.get()), filter);
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
		private final Equivalence.ComparatorEquivalence<? super E> theEquivalence;

		/**
		 * @param collectionObservable The value containing a sorted set
		 * @param compare The comparator that all values the observable value may contain must use
		 */
		public FlattenedValueSortedSet(ObservableValue<? extends ObservableSortedSet<? extends E>> collectionObservable,
			Comparator<? super E> compare) {
			super(collectionObservable, Equivalence.of(extractElementType(collectionObservable), compare, false));
			theEquivalence = Equivalence.of(TypeTokens.getRawType(getType()), compare, true);
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
		public Equivalence.ComparatorEquivalence<? super E> equivalence() {
			return theEquivalence;
		}

		@Override
		public Comparator<? super E> comparator() {
			return theEquivalence.comparator();
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
