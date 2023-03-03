package org.observe.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.Equivalence;
import org.observe.Equivalence.SortedEquivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.Transformation;
import org.observe.Transformation.ReversibleTransformation;
import org.observe.Transformation.ReversibleTransformationPrecursor;
import org.observe.collect.FlowOptions.UniqueOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableCollection.ModFilterBuilder;
import org.observe.collect.ObservableCollectionActiveManagers.ActiveValueStoredManager;
import org.observe.collect.ObservableCollectionBuilder.DataControlAutoRefresher;
import org.observe.collect.ObservableCollectionPassiveManagers.PassiveCollectionManager;
import org.observe.collect.ObservableSetImpl.DistinctBaseFlow;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionUtils.AdjustmentOrder;
import org.qommons.collect.CollectionUtils.CollectionSynchronizerE;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableSortedSet} and {@link DistinctSortedDataFlow} methods */
public class ObservableSortedSetImpl {
	private ObservableSortedSetImpl() {
	}

	/**
	 * Implements {@link ObservableSortedSet#subSet(Comparable, Comparable)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class ObservableSubSet<E> extends ObservableSortedCollectionImpl.ObservableSubSequence<E>
	implements ObservableSortedSet<E> {
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
		public Equivalence.SortedEquivalence<? super E> equivalence() {
			return (SortedEquivalence<? super E>) super.equivalence();
		}

		@Override
		public boolean add(E value) {
			return ObservableSortedSet.super.add(value);
		}

		@Override
		public ObservableSortedSet<E> subSequence(Comparable<? super E> from, Comparable<? super E> to) {
			return subSet(from, to);
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
						thePresentElements = BetterTreeSet.<ElementId> buildTreeSet(ElementId::compareTo).build();
						for (CollectionElement<E> el : elements())
							thePresentElements.add(unwrap(el.getElementId()));
					}

					@Override
					public void accept(ObservableCollectionEvent<? extends E> evt) {
						int inRange = isInRange(evt.getNewValue());
						int oldInRange = evt.getType() == CollectionChangeType.set ? isInRange(evt.getOldValue()) : 0;
						CollectionElement<ElementId> presentEl;
						if (inRange != 0) {
							switch (evt.getType()) {
							case set:
								if (oldInRange == 0) {
									presentEl = thePresentElements.getElement(evt.getElementId(), true);// Get by value
									int index = thePresentElements.getElementsBefore(presentEl.getElementId());
									thePresentElements.mutableElement(presentEl.getElementId()).remove();
									fire(evt, CollectionChangeType.remove, null, index, evt.getOldValue(), evt.getOldValue());
								}
								break;
							default:
							}
						} else {
							switch (evt.getType()) {
							case add:
								presentEl = thePresentElements.addElement(evt.getElementId(), false);
								int index = thePresentElements.getElementsBefore(presentEl.getElementId());
								fire(evt, evt.getType(), evt.getMovement(), index, null, evt.getNewValue());
								break;
							case remove:
								presentEl = thePresentElements.getElement(evt.getElementId(), true);// Get by value
								index = thePresentElements.getElementsBefore(presentEl.getElementId());
								thePresentElements.mutableElement(presentEl.getElementId()).remove();
								fire(evt, evt.getType(), evt.getMovement(), index, evt.getOldValue(), evt.getNewValue());
								break;
							case set:
								if (oldInRange != 0) {
									presentEl = thePresentElements.addElement(evt.getElementId(), false);
									index = thePresentElements.getElementsBefore(presentEl.getElementId());
									fire(evt, CollectionChangeType.add, null, index, null, evt.getNewValue());
								} else {
									presentEl = thePresentElements.getElement(evt.getElementId(), true);// Get by value
									index = thePresentElements.getElementsBefore(presentEl.getElementId());
									fire(evt, CollectionChangeType.set, null, index, evt.getOldValue(), evt.getNewValue());
								}
							}
						}
					}

					void fire(ObservableCollectionEvent<? extends E> evt, CollectionChangeType type, CollectionElementMove move, int index,
						E oldValue, E newValue) {
						ObservableCollectionEvent<? extends E> evt2 = new ObservableCollectionEvent<>(wrap(evt.getElementId()),
							index, type, oldValue, newValue, evt, move);
						try (Transaction evtT = evt2.use()) {
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
		private final Equivalence.SortedEquivalence<? super E> theEquivalence;

		/** @param wrap The sorted set to reverse */
		public ReversedSortedSet(ObservableSortedSet<E> wrap) {
			super(wrap);
			theEquivalence = wrap.equivalence().reverse();
		}

		@Override
		protected ObservableSortedSet<E> getWrapped() {
			return (ObservableSortedSet<E>) super.getWrapped();
		}

		@Override
		public Equivalence.SortedEquivalence<? super E> equivalence() {
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
			Equivalence.SortedEquivalence<? super T> equivalence) {
			super(source, parent, equivalence);
		}

		/**
		 * @param source The source collection
		 * @param parent This flow's parent
		 * @param compare The comparator that this flow's elements are ordered by
		 */
		protected DistinctSortedDataFlowWrapper(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent,
			Comparator<? super T> compare) {
			super(source, parent, parent.equivalence().sorted(TypeTokens.getRawType(parent.getTargetType()), compare, true));
		}

		@Override
		public Equivalence.SortedEquivalence<? super T> equivalence() {
			return (Equivalence.SortedEquivalence<? super T>) super.equivalence();
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
			return new DistinctSortedTransformOp<>(getSource(), this, target, def);
		}

		@Override
		public <X> DistinctSortedDataFlow<E, T, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, ? extends Transformation<T, X>> transform,
				Comparator<? super X> compare) {
			Transformation<T, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			return new DistinctSortedTransformOp<>(getSource(), this, target, def, compare);
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
		public DistinctSortedDataFlow<E, T, T> catchUpdates(ThreadConstraint constraint) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.catchUpdates(constraint), equivalence());
		}

		@Override
		public ActiveValueStoredManager<E, ?, T> manageActive() {
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
		public ActiveValueStoredManager<E, ?, T> manageActive() {
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
		private final Equivalence.SortedEquivalence<? super T> theSortedEquivalence;

		/**
		 * @param source The source collection
		 * @param parent The parent flow
		 * @param target The type of this flow
		 * @param def The transformation definition of this operation
		 * @param compare The sorting to use
		 */
		public DistinctSortedTransformOp(ObservableCollection<E> source, DistinctDataFlow<E, ?, I> parent, TypeToken<T> target,
			Transformation<I, T> def, Comparator<? super T> compare) {
			super(source, parent, target, def);
			theSortedEquivalence = def.equivalence().sorted(TypeTokens.getRawType(target), compare, true);
		}

		/**
		 * @param source The source collection
		 * @param parent The parent flow
		 * @param target The type of this flow
		 * @param def The transformation definition of this operation
		 */
		public DistinctSortedTransformOp(ObservableCollection<E> source, DistinctDataFlow<E, ?, I> parent, TypeToken<T> target,
			ReversibleTransformation<I, T> def) {
			super(source, parent, target, def);
			theSortedEquivalence = (SortedEquivalence<? super T>) super.equivalence();
		}

		@Override
		public Equivalence.SortedEquivalence<? super T> equivalence() {
			return theSortedEquivalence;
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
			return new DistinctSortedTransformOp<>(getSource(), this, target, def);
		}

		@Override
		public <X> DistinctSortedDataFlow<E, T, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, ? extends Transformation<T, X>> transform,
				Comparator<? super X> compare) {
			Transformation<T, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			return new DistinctSortedTransformOp<>(getSource(), this, target, def, compare);
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
		public DistinctSortedDataFlow<E, T, T> distinct(Consumer<UniqueOptions> options) {
			return (DistinctSortedDataFlow<E, T, T>) super.distinct(options);
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.filterMod(options), equivalence());
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> catchUpdates(ThreadConstraint constraint) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.catchUpdates(constraint), equivalence());
		}

		@Override
		public ActiveValueStoredManager<E, ?, T> manageActive() {
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
		public Equivalence.SortedEquivalence<? super E> equivalence() {
			return (SortedEquivalence<? super E>) super.equivalence();
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
			return new DistinctSortedTransformOp<>(getSource(), this, target, def);
		}

		@Override
		public <X> DistinctSortedDataFlow<E, E, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<E, X, ?>, ? extends Transformation<E, X>> transform,
				Comparator<? super X> compare) {
			Transformation<E, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			return new DistinctSortedTransformOp<>(getSource(), this, target, def, compare);
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
		public DistinctSortedDataFlow<E, E, E> distinct(Consumer<UniqueOptions> options) {
			return (DistinctSortedDataFlow<E, E, E>) super.distinct(options);
		}

		@Override
		public DistinctSortedDataFlow<E, E, E> filterMod(Consumer<ModFilterBuilder<E>> options) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.filterMod(options), comparator());
		}

		@Override
		public DistinctSortedDataFlow<E, E, E> catchUpdates(ThreadConstraint constraint) {
			return new DistinctSortedDataFlowWrapper<>(getSource(), super.catchUpdates(constraint), equivalence());
		}

		@Override
		public ObservableSortedSet<E> collect() {
			return (ObservableSortedSet<E>) super.collect();
		}

		@Override
		public ActiveValueStoredManager<E, ?, E> manageActive() {
			return new ObservableSortedCollectionImpl.SortedBaseManager<>(getSource());
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
		private final Equivalence.SortedEquivalence<? super T> theEquivalence;

		/**
		 * @param source The source set
		 * @param flow The passive manager
		 * @param compare The comparator by which the values are sorted
		 */
		public PassiveDerivedSortedSet(ObservableSortedSet<E> source, PassiveCollectionManager<E, ?, T> flow,
			Comparator<? super T> compare) {
			super(source, flow);
			theEquivalence = flow.equivalence() instanceof Equivalence.SortedEquivalence
				? (Equivalence.SortedEquivalence<? super T>) flow.equivalence()
					: flow.equivalence().sorted(TypeTokens.getRawType(flow.getTargetType()), compare, true);
		}

		@Override
		protected ObservableSortedSet<E> getSource() {
			return (ObservableSortedSet<E>) super.getSource();
		}

		@Override
		public Equivalence.SortedEquivalence<? super T> equivalence() {
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
		private final Equivalence.SortedEquivalence<? super T> theEquivalence;

		/**
		 * @param flow The active manager to drive this set
		 * @param compare The comparator by which this set's values will be sorted
		 * @param until The observable to terminate this derived set
		 */
		public ActiveDerivedSortedSet(ActiveValueStoredManager<?, ?, T> flow, Comparator<? super T> compare, Observable<?> until) {
			super(flow, until);
			theEquivalence = flow.equivalence() instanceof Equivalence.SortedEquivalence
				? (Equivalence.SortedEquivalence<? super T>) flow.equivalence()
					: flow.equivalence().sorted(TypeTokens.getRawType(flow.getTargetType()), compare, true);
		}

		@Override
		public Equivalence.SortedEquivalence<? super T> equivalence() {
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
		private final Equivalence.SortedEquivalence<? super E> theEquivalence;

		/**
		 * @param collectionObservable The value containing a sorted set
		 * @param compare The comparator that all values the observable value may contain must use
		 */
		public FlattenedValueSortedSet(ObservableValue<? extends ObservableSortedSet<? extends E>> collectionObservable,
			Comparator<? super E> compare) {
			super(collectionObservable, Equivalence.DEFAULT.sorted(extractElementType(collectionObservable), compare, false));
			theEquivalence = Equivalence.DEFAULT.sorted(TypeTokens.getRawType(getType()), compare, true);
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
		public Equivalence.SortedEquivalence<? super E> equivalence() {
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
		public CollectionElement<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
			ObservableSortedSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			return wrapped.getOrAdd(value, before, after, first, preAdd, postAdd);
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

	/**
	 * Default {@link DataControlledCollection.SortedSet data controlled sorted set} implementation
	 *
	 * @param <E> The type of the collection values
	 * @param <V> The type of the source data
	 */
	public static class DataControlledSortedSetImpl<E, V> extends ObservableSortedCollectionImpl.DataControlledSortedCollectionImpl<E, V>
	implements DataControlledCollection.SortedSet<E, V> {
		/**
		 * @param backing The collection to control all the observable functionality
		 * @param backingData Supplies backing data for refresh operations
		 * @param autoRefresh The asynchronous auto refresher for this collection
		 * @param refreshOnAccess Whether this collection should refresh synchronously each time it is accessed
		 * @param equals The equals tester to preserve elements between refreshes
		 * @param synchronizer The synchronizer to perform the refresh operation
		 * @param adjustmentOrder The adjustment order for the synchronization
		 */
		protected DataControlledSortedSetImpl(ObservableSortedSet<E> backing, Supplier<? extends List<? extends V>> backingData,
			DataControlAutoRefresher autoRefresh, boolean refreshOnAccess, BiPredicate<? super E, ? super V> equals,
			CollectionSynchronizerE<E, ? super V, ?> synchronizer, AdjustmentOrder adjustmentOrder) {
			super(backing, backingData, autoRefresh, refreshOnAccess, equals, synchronizer, adjustmentOrder);
		}

		@Override
		protected ObservableSortedSet<E> getWrapped() throws IllegalStateException {
			return (ObservableSortedSet<E>) super.getWrapped();
		}

		@Override
		public DataControlledSortedSetImpl<E, V> setMaxRefreshFrequency(long frequency) {
			super.setMaxRefreshFrequency(frequency);
			return this;
		}
	}
}
