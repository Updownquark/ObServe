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
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableCollection.ModFilterBuilder;
import org.observe.collect.ObservableCollection.SortedDataFlow;
import org.observe.collect.ObservableCollectionActiveManagers.ActiveValueStoredManager;
import org.observe.collect.ObservableCollectionActiveManagers.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionBuilder.DataControlAutoRefresher;
import org.observe.collect.ObservableCollectionDataFlowImpl.BaseCollectionDataFlow;
import org.observe.collect.ObservableCollectionPassiveManagers.PassiveCollectionManager;
import org.observe.collect.ObservableSetImpl.ActiveSetMgrPlaceholder;
import org.observe.collect.ObservableSetImpl.ValueStoredBaseManager;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionUtils.AdjustmentOrder;
import org.qommons.collect.CollectionUtils.CollectionSynchronizerE;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableSortedCollection} and {@link SortedDataFlow} methods */
public class ObservableSortedCollectionImpl {
	private ObservableSortedCollectionImpl() {
	}

	/**
	 * Implements {@link ObservableSortedCollection#observeRelative(Comparable, SortedSearchFilter, java.util.function.Supplier)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class RelativeFinder<E> extends ObservableCollectionImpl.AbstractObservableElementFinder<E> {
		private final Comparable<? super E> theSearch;
		private final BetterSortedList.SortedSearchFilter theFilter;

		/**
		 * @param collection The sorted collection to search
		 * @param search The search comparable
		 * @param filter The search filter
		 */
		public RelativeFinder(ObservableSortedCollection<E> collection, Comparable<? super E> search,
			BetterSortedList.SortedSearchFilter filter) {
			super(collection, (el1, el2) -> {
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
			}, () -> null, null, null);
			theSearch = search;
			theFilter = filter;
		}

		@Override
		protected ObservableSortedCollection<E> getCollection() {
			return (ObservableSortedCollection<E>) super.getCollection();
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
	public static class ObservableSubSequence<E> extends BetterSortedList.BetterSubSequence<E> implements ObservableSortedCollection<E> {
		/**
		 * @param set The super collection
		 * @param from The lower bound of this sub-list
		 * @param to The upper bound of this sub-list
		 */
		public ObservableSubSequence(ObservableSortedCollection<E> set, Comparable<? super E> from, Comparable<? super E> to) {
			super(set, from, to);
		}

		@Override
		public ObservableSortedCollection<E> getWrapped() {
			return (ObservableSortedCollection<E>) super.getWrapped();
		}

		@Override
		public TypeToken<E> getType() {
			return getWrapped().getType();
		}

		@Override
		public boolean isEventing() {
			return getWrapped().isEventing();
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
			return ObservableSortedCollection.super.toArray();
		}

		@Override
		public void setValue(Collection<ElementId> elements, E value) {
			getWrapped().setValue(BetterList.of(elements.stream().map(BetterSortedSet.BetterSubSet::unwrap)), value);
		}

		@Override
		public ObservableSortedCollection<E> subSequence(Comparable<? super E> from, Comparable<? super E> to) {
			if (BetterCollections.simplifyDuplicateOperations())
				return new ObservableSubSequence<>(getWrapped(), BetterSortedList.and(getFrom(), from, true),
					BetterSortedList.and(getTo(), to, false));
			else
				return ObservableSortedCollection.super.subSequence(from, to);
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
									fire(evt, CollectionChangeType.remove, false, index, evt.getOldValue(), evt.getOldValue());
								}
								break;
							default:
							}
						} else {
							switch (evt.getType()) {
							case add:
								presentEl = thePresentElements.addElement(evt.getElementId(), false);
								int index = thePresentElements.getElementsBefore(presentEl.getElementId());
								fire(evt, evt.getType(), evt.isMove(), index, null, evt.getNewValue());
								break;
							case remove:
								presentEl = thePresentElements.getElement(evt.getElementId(), true);// Get by value
								index = thePresentElements.getElementsBefore(presentEl.getElementId());
								thePresentElements.mutableElement(presentEl.getElementId()).remove();
								fire(evt, evt.getType(), evt.isMove(), index, evt.getOldValue(), evt.getNewValue());
								break;
							case set:
								if (oldInRange != 0) {
									presentEl = thePresentElements.addElement(evt.getElementId(), false);
									index = thePresentElements.getElementsBefore(presentEl.getElementId());
									fire(evt, CollectionChangeType.add, evt.isMove(), index, null, evt.getNewValue());
								} else {
									presentEl = thePresentElements.getElement(evt.getElementId(), true);// Get by value
									index = thePresentElements.getElementsBefore(presentEl.getElementId());
									fire(evt, CollectionChangeType.set, evt.isMove(), index, evt.getOldValue(), evt.getNewValue());
								}
							}
						}
					}

					void fire(ObservableCollectionEvent<? extends E> evt, CollectionChangeType type, boolean move, int index, E oldValue,
						E newValue) {
						ObservableCollectionEvent<? extends E> evt2 = new ObservableCollectionEvent<>(wrap(evt.getElementId()),
							index, type, move, oldValue, newValue, evt);
						try (Transaction evtT = evt2.use()) {
							observer.accept(evt2);
						}
					}
				});
			}
		}

		@Override
		public String toString() {
			return BetterCollection.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableSortedCollection#reverse()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class ReversedSortedCollection<E> extends ObservableCollectionImpl.ReversedObservableCollection<E>
	implements ObservableSortedCollection<E> {
		private final Equivalence<? super E> theEquivalence;

		/** @param wrap The sorted collection to reverse */
		public ReversedSortedCollection(ObservableSortedCollection<E> wrap) {
			super(wrap);
			if (wrap.equivalence() instanceof Equivalence.SortedEquivalence)
				theEquivalence = ((Equivalence.SortedEquivalence<? super E>) wrap.equivalence()).reverse();
			else
				theEquivalence = wrap.equivalence();
		}

		@Override
		protected ObservableSortedCollection<E> getWrapped() {
			return (ObservableSortedCollection<E>) super.getWrapped();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theEquivalence;
		}

		@Override
		public Comparator<? super E> comparator() {
			return getWrapped().comparator().reversed();
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			try (Transaction t = lock(false, null)) {
				int index = getWrapped().indexFor(BetterSortedList.ReversedSortedList.reverse(search));
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
		public ObservableSortedCollection<E> reverse() {
			if (BetterCollections.simplifyDuplicateOperations())
				return getWrapped();
			else
				return ObservableSortedCollection.super.reverse();
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return getWrapped().isConsistent(element.reverse());
		}

		@Override
		public boolean checkConsistency() {
			return getWrapped().checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
			return getWrapped().repair(element.reverse(),
				listener == null ? null : new BetterSet.ReversedBetterSet.ReversedRepairListener<>(listener));
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			return getWrapped().repair(listener == null ? null : new BetterSet.ReversedBetterSet.ReversedRepairListener<>(listener));
		}
	}

	/**
	 * An abstract {@link SortedDataFlow} implementation returning default {@link SortedDataFlow} implementations for most operations that
	 * should produce one
	 *
	 * @param <E> The type of the source collection
	 * @param <T> The type of this flow
	 */
	public static class SortedDataFlowWrapper<E, T> extends ObservableCollectionDataFlowImpl.AbstractDataFlow<E, T, T>
	implements SortedDataFlow<E, T, T> {
		/**
		 * @param source The source collection
		 * @param parent This flow's parent
		 * @param equivalence The equivalence with the comparator that this flow's elements are ordered by
		 */
		protected SortedDataFlowWrapper(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent,
			Equivalence.SortedEquivalence<? super T> equivalence) {
			super(source, parent, parent.getTargetType(), equivalence);
		}

		/**
		 * @param source The source collection
		 * @param parent This flow's parent
		 * @param compare The comparator that this flow's elements are ordered by
		 */
		protected SortedDataFlowWrapper(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, Comparator<? super T> compare) {
			super(source, parent, parent.getTargetType(),
				parent.equivalence().sorted(TypeTokens.getRawType(parent.getTargetType()), compare, true));
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
			return Identifiable.wrap(getParent().getIdentity(), "distinct");
		}

		@Override
		public SortedDataFlow<E, T, T> reverse() {
			return new SortedDataFlowWrapper<>(getSource(), super.reverse(), equivalence());
		}

		@Override
		public SortedDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new SortedDataFlowWrapper<>(getSource(), super.filter(filter), equivalence());
		}

		@Override
		public <X> SortedDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			return new SortedDataFlowWrapper<>(getSource(), super.whereContained(other, include), equivalence());
		}

		@Override
		public <X> SortedDataFlow<E, T, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, ReversibleTransformation<T, X>> transform) {
			ReversibleTransformation<T, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			return new SortedTransformOp<>(getSource(), this, target, def);
		}

		@Override
		public <X> SortedDataFlow<E, T, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, ? extends Transformation<T, X>> transform,
				Comparator<? super X> compare) {
			Transformation<T, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			return new SortedTransformOp<>(getSource(), this, target, def);
		}

		@Override
		public SortedDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new SortedDataFlowWrapper<>(getSource(), super.refresh(refresh), equivalence());
		}

		@Override
		public SortedDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new SortedDataFlowWrapper<>(getSource(), super.refreshEach(refresh), equivalence());
		}

		@Override
		public SortedDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options) {
			return new SortedDataFlowWrapper<>(getSource(), super.filterMod(options), equivalence());
		}

		@Override
		public SortedDataFlow<E, T, T> catchUpdates(ThreadConstraint constraint) {
			return new SortedDataFlowWrapper<>(getSource(), super.catchUpdates(constraint), equivalence());
		}

		@Override
		public boolean supportsPassive() {
			return getParent().supportsPassive();
		}

		@Override
		public ActiveValueStoredManager<E, ?, T> manageActive() {
			return new ActiveSetMgrPlaceholder<>(getParent().manageActive());
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return getParent().managePassive();
		}

		@Override
		public ObservableSortedCollection<T> collectPassive() {
			if (!supportsPassive())
				throw new UnsupportedOperationException("Passive collection not supported");
			return new PassiveDerivedSortedCollection<>((ObservableSortedCollection<E>) getSource(), managePassive(), comparator());
		}

		@Override
		public ObservableSortedCollection<T> collectActive(Observable<?> until) {
			return new ActiveDerivedSortedCollection<>(manageActive(), comparator(), until);
		}
	}

	/**
	 * Implements {@link CollectionDataFlow#sorted(Comparator)}
	 *
	 * @param <E> The type of the source collection
	 * @param <T> The type of this flow
	 */
	public static class SortedOp<E, T> extends SortedDataFlowWrapper<E, T> {
		/**
		 * @param source The source collection
		 * @param parent The parent flow
		 * @param compare The comparator to order this flow's elements
		 */
		protected SortedOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, Comparator<? super T> compare) {
			super(source, parent, compare);
		}

		@Override
		public boolean supportsPassive() {
			return false;
		}

		@Override
		public ActiveValueStoredManager<E, ?, T> manageActive() {
			return new ObservableCollectionActiveManagers.SortedManager<>(getParent().manageActive(), comparator());
		}
	}

	/**
	 * Implements {@link DistinctSortedDataFlow#mapEquivalent(TypeToken, Function, Comparator)}
	 *
	 * @param <E> The type of the source collection
	 * @param <I> The type of the parent flow
	 * @param <T> The type of this flow
	 */
	public static class SortedTransformOp<E, I, T> extends ObservableCollectionDataFlowImpl.TransformedCollectionOp<E, I, T>
	implements SortedDataFlow<E, I, T> {
		/**
		 * @param source The source collection
		 * @param parent The parent flow
		 * @param target The type of this flow
		 * @param def The transformation definition of this operation
		 */
		public SortedTransformOp(ObservableCollection<E> source, SortedDataFlow<E, ?, I> parent, TypeToken<T> target,
			Transformation<I, T> def) {
			super(source, parent, target, def);
		}

		@Override
		public Equivalence.SortedEquivalence<? super T> equivalence() {
			return (SortedEquivalence<? super T>) super.equivalence();
		}

		@Override
		public Comparator<? super T> comparator() {
			return equivalence().comparator();
		}

		@Override
		public SortedDataFlow<E, T, T> reverse() {
			return new SortedDataFlowWrapper<>(getSource(), super.reverse(), BetterSortedList.ReversedSortedList.reverse(comparator()));
		}

		@Override
		public SortedDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new SortedDataFlowWrapper<>(getSource(), super.filter(filter), comparator());
		}

		@Override
		public <X> SortedDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			return new SortedDataFlowWrapper<>(getSource(), super.whereContained(other, include), comparator());
		}

		@Override
		public <X> SortedDataFlow<E, T, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, ReversibleTransformation<T, X>> transform) {
			ReversibleTransformation<T, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			return new SortedTransformOp<>(getSource(), this, target, def);
		}

		@Override
		public <X> SortedDataFlow<E, T, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, ? extends Transformation<T, X>> transform,
				Comparator<? super X> compare) {
			Transformation<T, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			return new SortedTransformOp<>(getSource(), this, target, def);
		}

		@Override
		public SortedDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new SortedDataFlowWrapper<>(getSource(), super.refresh(refresh), comparator());
		}

		@Override
		public SortedDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new SortedDataFlowWrapper<>(getSource(), super.refreshEach(refresh), comparator());
		}

		@Override
		public SortedDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options) {
			return new SortedDataFlowWrapper<>(getSource(), super.filterMod(options), equivalence());
		}

		@Override
		public SortedDataFlow<E, T, T> catchUpdates(ThreadConstraint constraint) {
			return new SortedDataFlowWrapper<>(getSource(), super.catchUpdates(constraint), equivalence());
		}

		@Override
		public ActiveValueStoredManager<E, ?, T> manageActive() {
			return new ActiveSetMgrPlaceholder<>(super.manageActive());
		}

		@Override
		public ObservableSortedCollection<T> collectPassive() {
			return new PassiveDerivedSortedCollection<>((ObservableSortedCollection<E>) getSource(), managePassive(), comparator());
		}

		@Override
		public ObservableSortedCollection<T> collectActive(Observable<?> until) {
			return new ActiveDerivedSortedCollection<>(manageActive(), comparator(), until);
		}
	}

	/**
	 * Implements {@link ObservableSortedCollection#flow()}
	 *
	 * @param <E> The type of this flow
	 */
	public static class SortedBaseFlow<E> extends BaseCollectionDataFlow<E> implements SortedDataFlow<E, E, E> {
		/** @param source The source collection */
		public SortedBaseFlow(ObservableSortedCollection<E> source) {
			super(source);
		}

		@Override
		protected ObservableSortedCollection<E> getSource() {
			return (ObservableSortedCollection<E>) super.getSource();
		}

		@Override
		public Comparator<? super E> comparator() {
			return getSource().comparator();
		}

		@Override
		public SortedDataFlow<E, E, E> reverse() {
			return new SortedDataFlowWrapper<>(getSource(), super.reverse(), BetterSortedList.ReversedSortedList.reverse(comparator()));
		}

		@Override
		public SortedDataFlow<E, E, E> filter(Function<? super E, String> filter) {
			return new SortedDataFlowWrapper<>(getSource(), super.filter(filter), getSource().comparator());
		}

		@Override
		public <X> SortedDataFlow<E, E, E> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			return new SortedDataFlowWrapper<>(getSource(), super.whereContained(other, include), comparator());
		}

		@Override
		public <X> SortedDataFlow<E, E, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<E, X, ?>, ReversibleTransformation<E, X>> transform) {
			ReversibleTransformation<E, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			return new SortedTransformOp<>(getSource(), this, target, def);
		}

		@Override
		public <X> SortedDataFlow<E, E, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<E, X, ?>, ? extends Transformation<E, X>> transform,
				Comparator<? super X> compare) {
			Transformation<E, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			return new SortedTransformOp<>(getSource(), this, target, def);
		}

		@Override
		public SortedDataFlow<E, E, E> refresh(Observable<?> refresh) {
			return new SortedDataFlowWrapper<>(getSource(), super.refresh(refresh), getSource().comparator());
		}

		@Override
		public SortedDataFlow<E, E, E> refreshEach(Function<? super E, ? extends Observable<?>> refresh) {
			return new SortedDataFlowWrapper<>(getSource(), super.refreshEach(refresh), getSource().comparator());
		}

		@Override
		public SortedDataFlow<E, E, E> filterMod(Consumer<ModFilterBuilder<E>> options) {
			return new SortedDataFlowWrapper<>(getSource(), super.filterMod(options), comparator());
		}

		@Override
		public SortedDataFlow<E, E, E> catchUpdates(ThreadConstraint constraint) {
			return new SortedDataFlowWrapper<>(getSource(), super.catchUpdates(constraint), comparator());
		}

		@Override
		public ObservableSortedCollection<E> collect() {
			return (ObservableSortedCollection<E>) super.collect();
		}

		@Override
		public ActiveValueStoredManager<E, ?, E> manageActive() {
			return new SortedBaseManager<>(getSource());
		}

		@Override
		public ObservableSortedCollection<E> collectPassive() {
			return getSource();
		}

		@Override
		public ObservableSortedCollection<E> collectActive(Observable<?> until) {
			return new ActiveDerivedSortedCollection<>(manageActive(), comparator(), until);
		}
	}

	/**
	 * Base active manager for sorted collections
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class SortedBaseManager<E> extends ValueStoredBaseManager<E> {
		SortedBaseManager(ObservableSortedCollection<E> source) {
			super(source);
		}

		@Override
		protected ObservableSortedCollection<E> getSource() {
			return (ObservableSortedCollection<E>) super.getSource();
		}

		@Override
		public Comparable<DerivedCollectionElement<E>> getElementFinder(E value) {
			return LambdaUtils.<DerivedCollectionElement<E>> printableComparable(el -> getSource().comparator().compare(value, el.get()),
				() -> getSource().comparator().toString());
		}
	}

	/**
	 * A {@link SortedDataFlow#collect() collected}, {@link SortedDataFlow#supportsPassive() passive}ly-derived sorted collection
	 *
	 * @param <E> The type of the source collection
	 * @param <T> The type of this collection
	 */
	public static class PassiveDerivedSortedCollection<E, T> extends ObservableCollectionImpl.PassiveDerivedCollection<E, T>
	implements ObservableSortedCollection<T> {
		private final Equivalence.SortedEquivalence<? super T> theEquivalence;

		/**
		 * @param source The source collection
		 * @param flow The passive manager
		 * @param compare The comparator by which the values are sorted
		 */
		public PassiveDerivedSortedCollection(ObservableSortedCollection<E> source, PassiveCollectionManager<E, ?, T> flow,
			Comparator<? super T> compare) {
			super(source, flow);
			if (flow.equivalence() instanceof Equivalence.SortedEquivalence
				&& ((Equivalence.SortedEquivalence<? super T>) flow.equivalence()).comparator().equals(compare))
				theEquivalence = (Equivalence.SortedEquivalence<? super T>) flow.equivalence();
			else
				theEquivalence = flow.equivalence().sorted(TypeTokens.getRawType(flow.getTargetType()), compare, true);
		}

		@Override
		protected ObservableSortedCollection<E> getSource() {
			return (ObservableSortedCollection<E>) super.getSource();
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
		 * @param search The search comparable for this collection
		 * @return The search comparable to use on the source collection
		 */
		protected Comparable<? super E> mappedSearch(Comparable<? super T> search) {
			Comparable<? super T> fSearch = isReversed() ? BetterSortedList.ReversedSortedList.reverse(search) : search;
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

		@Override
		public boolean isConsistent(ElementId element) {
			return getSource().isConsistent(mapId(element));
		}

		@Override
		public boolean checkConsistency() {
			return getSource().checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<T, X> listener) {
			RepairListener<E, X> mappedListener;
			if (listener != null) {
				if (getFlow().isReversed())
					listener = new BetterSet.ReversedBetterSet.ReversedRepairListener<>(listener);
				mappedListener = new MappedRepairListener<>(listener);
			} else
				mappedListener = null;
			return getSource().repair(mapId(element), mappedListener);
		}

		@Override
		public <X> boolean repair(RepairListener<T, X> listener) {
			RepairListener<E, X> mappedListener;
			if (listener != null) {
				if (getFlow().isReversed())
					listener = new BetterSet.ReversedBetterSet.ReversedRepairListener<>(listener);
				mappedListener = new MappedRepairListener<>(listener);
			} else
				mappedListener = null;
			return getSource().repair(mappedListener);
		}

		private class MappedRepairListener<X> implements RepairListener<E, X> {
			private final RepairListener<T, X> theWrapped;
			private final Function<? super E, ? extends T> theMap;

			MappedRepairListener(RepairListener<T, X> wrapped) {
				theWrapped = wrapped;
				theMap = getFlow().map().get();
			}

			@Override
			public X removed(CollectionElement<E> element) {
				return theWrapped.removed(elementFor(element, theMap));
			}

			@Override
			public void disposed(E value, X data) {
				theWrapped.disposed(theMap.apply(value), data);
			}

			@Override
			public void transferred(CollectionElement<E> element, X data) {
				theWrapped.transferred(elementFor(element, theMap), data);
			}
		}
	}

	/**
	 * A {@link SortedDataFlow#collect() collected}, {@link SortedDataFlow#supportsPassive() active}ly-derived sorted collection
	 *
	 * @param <T> The type of this collection
	 */
	public static class ActiveDerivedSortedCollection<T> extends ObservableCollectionImpl.ActiveDerivedCollection<T>
	implements ObservableSortedCollection<T> {
		private final Comparator<? super T> theCompare;

		/**
		 * @param flow The active manager to drive this collection
		 * @param compare The comparator by which this collection's values will be sorted
		 * @param until The observable to terminate this derived collection
		 */
		public ActiveDerivedSortedCollection(ActiveValueStoredManager<?, ?, T> flow, Comparator<? super T> compare, Observable<?> until) {
			super(flow, until);
			theCompare=compare;
		}

		@Override
		protected ActiveValueStoredManager<?, ?, T> getFlow() {
			return (ActiveValueStoredManager<?, ?, T>) super.getFlow();
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
			CollectionElement<DerivedElementHolder<T>> presentEl = getPresentElements().search(el -> search.compareTo(el.get()), filter);
			return presentEl == null ? null : elementFor(presentEl.get());
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return getFlow().isConsistent(((DerivedElementHolder<T>) element).element);
		}

		@Override
		public boolean checkConsistency() {
			return getFlow().checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<T, X> listener) {
			throw new UnsupportedOperationException("Not implemented yet"); // TODO
			// return getFlow().repair(((DerivedElementHolder<T>) element).element, blah);
		}

		@Override
		public <X> boolean repair(RepairListener<T, X> listener) {
			throw new UnsupportedOperationException("Not implemented yet"); // TODO
			// return getFlow().repair(blah);
		}
	}

	/**
	 * Implements {@link ObservableSortedCollection#flattenValue(ObservableValue, Comparator)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedValueSortedCollection<E> extends ObservableCollectionImpl.FlattenedValueCollection<E>
	implements ObservableSortedCollection<E> {
		private final Equivalence.SortedEquivalence<? super E> theEquivalence;

		/**
		 * @param collectionObservable The value containing a sorted collection
		 * @param compare The comparator that all values the observable value may contain must use
		 */
		public FlattenedValueSortedCollection(ObservableValue<? extends ObservableSortedCollection<? extends E>> collectionObservable,
			Comparator<? super E> compare) {
			super(collectionObservable, Equivalence.DEFAULT.sorted(extractElementType(collectionObservable), compare, false));
			theEquivalence = Equivalence.DEFAULT.sorted(TypeTokens.getRawType(getType()), compare, true);
		}

		private static <E> Class<E> extractElementType(
			ObservableValue<? extends ObservableSortedCollection<? extends E>> collectionObservable) {
			return (Class<E>) TypeTokens
				.getRawType(collectionObservable.getType().resolveType(ObservableCollection.class.getTypeParameters()[0]));
		}

		@Override
		protected ObservableValue<? extends ObservableSortedCollection<E>> getWrapped() {
			return (ObservableValue<? extends ObservableSortedCollection<E>>) super.getWrapped();
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
			return ObservableSortedCollection.super.addAll(c);
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			return ObservableSortedCollection.super.addAll(index, c);
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			ObservableSortedCollection<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return -1;
			return wrapped.indexFor(search);
		}

		@Override
		public CollectionElement<E> search(Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter) {
			ObservableSortedCollection<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return null;
			return wrapped.search(search, filter);
		}

		@Override
		public CollectionElement<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable added) {
			ObservableSortedCollection<E> wrapped = getWrapped().get();
			if (wrapped == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			return wrapped.getOrAdd(value, before, after, first, added);
		}

		@Override
		public boolean isConsistent(ElementId element) {
			ObservableSortedCollection<E> wrapped = getWrapped().get();
			if (wrapped == null)
				throw new NoSuchElementException();
			return wrapped.isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			ObservableSortedCollection<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return true;
			return wrapped.checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
			ObservableSortedCollection<E> wrapped = getWrapped().get();
			if (wrapped == null)
				throw new NoSuchElementException();
			return wrapped.repair(element, listener);
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			ObservableSortedCollection<E> wrapped = getWrapped().get();
			if (wrapped == null)
				return false;
			return wrapped.repair(listener);
		}
	}

	/**
	 * Default {@link DataControlledCollection.Sorted data controlled sorted collection} implementation
	 *
	 * @param <E> The type of the collection values
	 * @param <V> The type of the source data
	 */
	public static class DataControlledSortedCollectionImpl<E, V> extends ObservableCollectionImpl.DataControlledCollectionImpl<E, V>
	implements DataControlledCollection.Sorted<E, V> {
		/**
		 * @param backing The collection to control all the observable functionality
		 * @param backingData Supplies backing data for refresh operations
		 * @param autoRefresh The asynchronous auto refresher for this collection
		 * @param refreshOnAccess Whether this collection should refresh synchronously each time it is accessed
		 * @param equals The equals tester to preserve elements between refreshes
		 * @param synchronizer The synchronizer to perform the refresh operation
		 * @param adjustmentOrder The adjustment order for the synchronization
		 */
		public DataControlledSortedCollectionImpl(ObservableSortedCollection<E> backing, Supplier<? extends List<? extends V>> backingData,
			DataControlAutoRefresher autoRefresh, boolean refreshOnAccess, BiPredicate<? super E, ? super V> equals,
			CollectionSynchronizerE<E, ? super V, ?> synchronizer, AdjustmentOrder adjustmentOrder) {
			super(backing, backingData, autoRefresh, refreshOnAccess, equals, synchronizer, adjustmentOrder);
		}

		@Override
		protected ObservableSortedCollection<E> getWrapped() throws IllegalStateException {
			return (ObservableSortedCollection<E>) super.getWrapped();
		}

		@Override
		public DataControlledSortedCollectionImpl<E, V> setMaxRefreshFrequency(long frequency) {
			super.setMaxRefreshFrequency(frequency);
			return this;
		}

		@Override
		public Equivalence.ComparatorEquivalence<? super E> equivalence() {
			return (Equivalence.ComparatorEquivalence<? super E>) super.equivalence();
		}

		@Override
		public Comparator<? super E> comparator() {
			return getWrapped().comparator();
		}

		@Override
		public CollectionElement<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
			try (Transaction t = lock(false, null)) {
				return getWrapped().search(search, filter);
			}
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			try (Transaction t = lock(false, null)) {
				return getWrapped().indexFor(search);
			}
		}

		@Override
		public boolean isConsistent(ElementId element) {
			try (Transaction t = lock(false, null)) {
				return getWrapped().isConsistent(element);
			}
		}

		@Override
		public boolean checkConsistency() {
			try (Transaction t = lock(false, null)) {
				return getWrapped().checkConsistency();
			}
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
			try (Transaction t = lock(false, null)) {
				return getWrapped().repair(element, listener);
			}
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			try (Transaction t = lock(false, null)) {
				return getWrapped().repair(listener);
			}
		}
	}
}
