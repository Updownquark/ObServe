package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Transformation;
import org.observe.Transformation.ReversibleTransformation;
import org.observe.Transformation.ReversibleTransformationPrecursor;
import org.observe.collect.FlowOptions.UniqueOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.ModFilterBuilder;
import org.observe.collect.ObservableCollectionActiveManagers.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionActiveManagers.ActiveValueStoredManager;
import org.observe.collect.ObservableCollectionActiveManagers.CollectionElementListener;
import org.observe.collect.ObservableCollectionActiveManagers.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionActiveManagers.ElementAccepter;
import org.observe.collect.ObservableCollectionBuilder.DataControlAutoRefresher;
import org.observe.collect.ObservableCollectionDataFlowImpl.BaseCollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.FilterMapResult;
import org.observe.collect.ObservableCollectionDataFlowImpl.RepairListener;
import org.observe.collect.ObservableCollectionImpl.ActiveDerivedCollection;
import org.observe.collect.ObservableCollectionImpl.ConstantCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedValueCollection;
import org.observe.collect.ObservableCollectionImpl.PassiveDerivedCollection;
import org.observe.collect.ObservableCollectionImpl.ReversedObservableCollection;
import org.observe.collect.ObservableCollectionPassiveManagers.PassiveCollectionManager;
import org.observe.util.WeakListening;
import org.qommons.Identifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.QommonsUtils;
import org.qommons.Ternian;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionUtils.AdjustmentOrder;
import org.qommons.collect.CollectionUtils.CollectionSynchronizerE;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.OptimisticContext;
import org.qommons.collect.ValueStoredCollection;
import org.qommons.debug.Debug;
import org.qommons.debug.Debug.DebugData;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BinaryTreeEntry;

import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableSet} and {@link DistinctDataFlow} methods */
public class ObservableSetImpl {
	private ObservableSetImpl() {
	}

	/**
	 * Implements {@link ObservableSet#reverse()}
	 *
	 * @param <E> The type of the set
	 */
	public static class ReversedSet<E> extends ReversedObservableCollection<E> implements ObservableSet<E> {
		/** @param wrapped The set to reverse */
		public ReversedSet(ObservableSet<E> wrapped) {
			super(wrapped);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}

		@Override
		public CollectionElement<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable added) {
			return CollectionElement.reverse(getOrAdd(value, ElementId.reverse(before), ElementId.reverse(after), !first, added));
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

		@Override
		public ObservableSet<E> reverse() {
			if (BetterCollections.simplifyDuplicateOperations())
				return getWrapped();
			else
				return ObservableSet.super.reverse();
		}

		@Override
		public String toString() {
			return BetterSet.toString(this);
		}
	}

	/**
	 * An abstract {@link DistinctDataFlow} implementation returning default {@link DistinctDataFlow} implementations for most operations
	 * that should produce one
	 *
	 * @param <E> The type of the source collection
	 * @param <T> The type of this flow
	 */
	public static class DistinctDataFlowWrapper<E, T> extends ObservableCollectionDataFlowImpl.AbstractDataFlow<E, T, T>
	implements DistinctDataFlow<E, T, T> {
		/**
		 * @param source The source collection
		 * @param parent The parent flow
		 * @param equivalence The equivalence for this flow
		 */
		protected DistinctDataFlowWrapper(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent,
			Equivalence<? super T> equivalence) {
			super(source, parent, parent.getTargetType(), equivalence);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "distinct");
		}

		@Override
		public DistinctDataFlow<E, T, T> reverse() {
			return new DistinctDataFlowWrapper<>(getSource(), super.reverse(), equivalence());
		}

		@Override
		public DistinctDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new DistinctDataFlowWrapper<>(getSource(), super.filter(filter), equivalence());
		}

		@Override
		public <X> DistinctDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			return new DistinctDataFlowWrapper<>(getSource(), super.whereContained(other, include), equivalence());
		}

		@Override
		public <X> DistinctDataFlow<E, T, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, ReversibleTransformation<T, X>> transform) {
			ReversibleTransformation<T, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			return new DistinctTransformOp<>(getSource(), this, target, def);
		}

		@Override
		public DistinctDataFlow<E, T, T> distinct(Consumer<UniqueOptions> options) {
			options.accept(new FlowOptions.SimpleUniqueOptions(equivalence() instanceof Equivalence.SortedEquivalence));
			return this; // No-op
		}

		@Override
		public DistinctDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new DistinctDataFlowWrapper<>(getSource(), super.refresh(refresh), equivalence());
		}

		@Override
		public DistinctDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new DistinctDataFlowWrapper<>(getSource(), super.refreshEach(refresh), equivalence());
		}

		@Override
		public DistinctDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options) {
			return new DistinctDataFlowWrapper<>(getSource(), super.filterMod(options), equivalence());
		}

		@Override
		public DistinctDataFlow<E, T, T> catchUpdates(ThreadConstraint constraint) {
			return new DistinctDataFlowWrapper<>(getSource(), super.catchUpdates(constraint), equivalence());
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
		public ObservableSet<T> collectPassive() {
			if (!supportsPassive())
				throw new UnsupportedOperationException("Passive collection not supported");
			return new PassiveDerivedSet<>((ObservableSet<E>) getSource(), managePassive());
		}

		@Override
		public ObservableSet<T> collectActive(Observable<?> until) {
			return new ActiveDerivedSet<>(manageActive(), until);
		}
	}

	static class ActiveSetMgrPlaceholder<E, I, T> implements ActiveValueStoredManager<E, I, T> {
		private final ActiveCollectionManager<E, I, T> theWrapped;

		ActiveSetMgrPlaceholder(ActiveCollectionManager<E, I, T> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public Object getIdentity() {
			return theWrapped.getIdentity();
		}

		@Override
		public boolean isEventing() {
			return theWrapped.isEventing();
		}

		@Override
		public boolean isContentControlled() {
			return theWrapped.isContentControlled();
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			return theWrapped.getElementFinder(value);
		}

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			return theWrapped.getElementsBySource(sourceEl, sourceCollection);
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection) {
			return theWrapped.getSourceElements(localElement, sourceCollection);
		}

		@Override
		public DerivedCollectionElement<T> getEquivalentElement(DerivedCollectionElement<?> flowEl) {
			return theWrapped.getEquivalentElement(flowEl);
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			return theWrapped.canAdd(toAdd, after, before);
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) throws UnsupportedOperationException, IllegalArgumentException {
			return theWrapped.addElement(value, after, before, first);
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			return theWrapped.canMove(valueEl, after, before);
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			return theWrapped.move(valueEl, after, before, first, afterRemove);
		}

		@Override
		public boolean clear() {
			return theWrapped.clear();
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			theWrapped.setValues(elements, newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theWrapped.begin(fromStart, onElement, listening);
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theWrapped.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theWrapped.equivalence();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theWrapped.getThreadConstraint();
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theWrapped.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return theWrapped.getCoreId();
		}

		@Override
		public boolean isConsistent(DerivedCollectionElement<T> element) {
			throw new UnsupportedOperationException("Not implemented yet"); // TODO
		}

		@Override
		public boolean checkConsistency() {
			throw new UnsupportedOperationException("Not implemented yet"); // TODO
		}

		@Override
		public <X> boolean repair(DerivedCollectionElement<T> element, RepairListener<T, X> listener) {
			throw new UnsupportedOperationException("Not implemented yet"); // TODO
		}

		@Override
		public <X> boolean repair(RepairListener<T, X> listener) {
			throw new UnsupportedOperationException("Not implemented yet"); // TODO
		}
	}

	/**
	 * Implements {@link CollectionDataFlow#distinct()}
	 *
	 * @param <E> The type of the source collection
	 * @param <T> The type of this flow
	 */
	public static class DistinctOp<E, T> extends DistinctDataFlowWrapper<E, T> {
		private final boolean isAlwaysUsingFirst;
		private final boolean isPreservingSourceOrder;

		/**
		 * @param source The source collection
		 * @param parent The parent flow
		 * @param equivalence The equivalence for this flow
		 * @param alwaysUseFirst Whether to always use the earliest element in a category of equivalent values to represent the group in
		 *        this flow
		 * @param preserveSourceOrder Whether to order the derived elements by their representative's order in the parent collection, as
		 *        opposed to the value's order in the equivalence's {@link Equivalence#createSet() set}
		 */
		protected DistinctOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, Equivalence<? super T> equivalence,
			boolean alwaysUseFirst, boolean preserveSourceOrder) {
			super(source, parent, equivalence);
			isAlwaysUsingFirst = alwaysUseFirst;
			isPreservingSourceOrder = preserveSourceOrder;
		}

		@Override
		public boolean supportsPassive() {
			return false;
		}

		@Override
		public ActiveValueStoredManager<E, ?, T> manageActive() {
			DistinctManager<E, T> mgr = new DistinctManager<>(getParent().manageActive(), equivalence(), isAlwaysUsingFirst,
				isPreservingSourceOrder);
			Debug.DebugData d = Debug.d().debug(this);
			if (d.isActive())
				mgr.theDebug = Debug.d().debug(mgr, true).merge(d);
			return mgr;
		}
	}

	/**
	 * Implements {@link DistinctDataFlow#transformEquivalent(TypeToken, Function)}
	 *
	 * @param <E> The type of the source collection
	 * @param <I> The type of the parent flow
	 * @param <T> The type of this flow
	 */
	public static class DistinctTransformOp<E, I, T> extends ObservableCollectionDataFlowImpl.TransformedCollectionOp<E, I, T>
	implements DistinctDataFlow<E, I, T> {
		/**
		 * @param source The source collection
		 * @param parent The parent flow
		 * @param target The type of this flow
		 * @param def The transform definition of this flow
		 */
		public DistinctTransformOp(ObservableCollection<E> source, DistinctDataFlow<E, ?, I> parent, TypeToken<T> target,
			Transformation<I, T> def) {
			super(source, parent, target, def);
		}

		@Override
		public DistinctDataFlow<E, T, T> reverse() {
			return new DistinctDataFlowWrapper<>(getSource(), super.reverse(), equivalence());
		}

		@Override
		public DistinctDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new DistinctDataFlowWrapper<>(getSource(), super.filter(filter), equivalence());
		}

		@Override
		public <X> DistinctDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			return new DistinctDataFlowWrapper<>(getSource(), super.whereContained(other, include), equivalence());
		}

		@Override
		public <X> DistinctDataFlow<E, T, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<T, X, ?>, ReversibleTransformation<T, X>> transform) {
			ReversibleTransformation<T, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			return new DistinctTransformOp<>(getSource(), this, target, def);
		}

		@Override
		public DistinctDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new DistinctDataFlowWrapper<>(getSource(), super.refresh(refresh), equivalence());
		}

		@Override
		public DistinctDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new DistinctDataFlowWrapper<>(getSource(), super.refreshEach(refresh), equivalence());
		}

		@Override
		public DistinctDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options) {
			return new DistinctDataFlowWrapper<>(getSource(), super.filterMod(options), equivalence());
		}

		@Override
		public DistinctDataFlow<E, T, T> catchUpdates(ThreadConstraint constraint) {
			return new DistinctDataFlowWrapper<>(getSource(), super.catchUpdates(constraint), equivalence());
		}

		@Override
		public ActiveValueStoredManager<E, ?, T> manageActive() {
			return new ActiveSetMgrPlaceholder<>(super.manageActive());
		}

		@Override
		public ObservableSet<T> collectPassive() {
			return new PassiveDerivedSet<>((ObservableSet<E>) getSource(), managePassive());
		}

		@Override
		public ObservableSet<T> collectActive(Observable<?> until) {
			return new ActiveDerivedSet<>(manageActive(), until);
		}
	}

	/**
	 * Implements {@link ObservableSet#flow()}
	 *
	 * @param <E> The type of this flow
	 */
	public static class DistinctBaseFlow<E> extends BaseCollectionDataFlow<E> implements DistinctDataFlow<E, E, E> {
		/** @param source The source set */
		public DistinctBaseFlow(ObservableSet<E> source) {
			super(source);
		}

		@Override
		protected ObservableSet<E> getSource() {
			return (ObservableSet<E>) super.getSource();
		}

		@Override
		public DistinctDataFlow<E, E, E> reverse() {
			return new DistinctDataFlowWrapper<>(getSource(), super.reverse(), equivalence());
		}

		@Override
		public DistinctDataFlow<E, E, E> filter(Function<? super E, String> filter) {
			return new DistinctDataFlowWrapper<>(getSource(), super.filter(filter), equivalence());
		}

		@Override
		public <X> DistinctDataFlow<E, E, E> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			return new DistinctDataFlowWrapper<>(getSource(), super.whereContained(other, include), equivalence());
		}

		@Override
		public <X> DistinctDataFlow<E, E, X> transformEquivalent(TypeToken<X> target,
			Function<? super ReversibleTransformationPrecursor<E, X, ?>, ReversibleTransformation<E, X>> transform) {
			ReversibleTransformation<E, X> def = transform.apply(new ReversibleTransformationPrecursor<>());
			return new DistinctTransformOp<>(getSource(), this, target, def);
		}

		@Override
		public DistinctDataFlow<E, E, E> refresh(Observable<?> refresh) {
			return new DistinctDataFlowWrapper<>(getSource(), super.refresh(refresh), equivalence());
		}

		@Override
		public DistinctDataFlow<E, E, E> refreshEach(Function<? super E, ? extends Observable<?>> refresh) {
			return new DistinctDataFlowWrapper<>(getSource(), super.refreshEach(refresh), equivalence());
		}

		@Override
		public DistinctDataFlow<E, E, E> filterMod(Consumer<ModFilterBuilder<E>> options) {
			return new DistinctDataFlowWrapper<>(getSource(), super.filterMod(options), equivalence());
		}

		@Override
		public DistinctDataFlow<E, E, E> catchUpdates(ThreadConstraint constraint) {
			return new DistinctDataFlowWrapper<>(getSource(), super.catchUpdates(constraint), equivalence());
		}

		@Override
		public ObservableSet<E> collect() {
			return (ObservableSet<E>) super.collect();
		}

		@Override
		public ActiveValueStoredManager<E, ?, E> manageActive() {
			return new ValueStoredBaseManager<>(getSource());
		}

		@Override
		public ObservableSet<E> collectPassive() {
			return getSource();
		}

		@Override
		public ObservableSet<E> collectActive(Observable<?> until) {
			return new ActiveDerivedSet<>(manageActive(), until);
		}
	}

	static class ValueStoredBaseManager<E> extends ObservableCollectionActiveManagers.BaseCollectionManager<E>
	implements ActiveValueStoredManager<E, E, E> {
		ValueStoredBaseManager(ObservableCollection<E> source) {
			super(source);
			if (!(source instanceof ValueStoredCollection))
				throw new IllegalArgumentException(
					"This manager can only be used with " + ValueStoredCollection.class.getSimpleName() + " implementations");
		}

		protected ValueStoredCollection<E> getVSCSource() {
			return (ValueStoredCollection<E>) getSource();
		}

		@Override
		public boolean isConsistent(DerivedCollectionElement<E> element) {
			return getVSCSource().isConsistent(((BaseDerivedElement) element).getElementId());
		}

		@Override
		public boolean checkConsistency() {
			return getVSCSource().checkConsistency();
		}

		@Override
		public <X> boolean repair(DerivedCollectionElement<E> element,
			org.observe.collect.ObservableCollectionDataFlowImpl.RepairListener<E, X> listener) {
			return getVSCSource().repair(((BaseDerivedElement) element).getElementId(),
				listener == null ? null : new BaseRepairListener<>(listener));
		}

		@Override
		public <X> boolean repair(org.observe.collect.ObservableCollectionDataFlowImpl.RepairListener<E, X> listener) {
			return getVSCSource().repair(listener == null ? null : new BaseRepairListener<>(listener));
		}

		private class BaseRepairListener<X> implements ValueStoredCollection.RepairListener<E, X> {
			private final org.observe.collect.ObservableCollectionDataFlowImpl.RepairListener<E, X> theWrapped;

			BaseRepairListener(org.observe.collect.ObservableCollectionDataFlowImpl.RepairListener<E, X> wrapped) {
				theWrapped = wrapped;
			}

			@Override
			public X removed(CollectionElement<E> element) {
				return theWrapped.removed(new BaseDerivedElement(getSource().mutableElement(element.getElementId())));
			}

			@Override
			public void disposed(E value, X data) {
				theWrapped.disposed(value, data);
			}

			@Override
			public void transferred(CollectionElement<E> element, X data) {
				theWrapped.transferred(new BaseDerivedElement(getSource().mutableElement(element.getElementId())), data);
			}
		}
	}

	/**
	 * Implements {@link DistinctOp#manageActive()}
	 *
	 * @param <E> The type of the source collection
	 * @param <T> The type of the derived set
	 */
	public static class DistinctManager<E, T> implements ActiveValueStoredManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;
		private final BetterMap<T, UniqueElement> theElementsByValue;
		private final Equivalence<? super T> theEquivalence;
		private final boolean isAlwaysUsingFirst;
		private final boolean isPreservingSourceOrder;
		private ElementAccepter<T> theAccepter;

		private DebugData theDebug;

		/**
		 * @param parent The parent manager
		 * @param equivalence The equivalence for this manager
		 * @param alwaysUseFirst Whether to always use the earliest element in a category of equivalent values to represent the group in
		 *        this flow
		 * @param preserveSourceOrder Whether to order the derived elements by their representative's order in the parent collection, as
		 *        opposed to the value's order in the equivalence's {@link Equivalence#createSet() set}
		 */
		protected DistinctManager(ActiveCollectionManager<E, ?, T> parent, Equivalence<? super T> equivalence, boolean alwaysUseFirst,
			boolean preserveSourceOrder) {
			theParent = parent;
			theEquivalence = equivalence;
			theElementsByValue = parent.equivalence().createMap();
			isAlwaysUsingFirst = alwaysUseFirst;
			isPreservingSourceOrder = preserveSourceOrder;

			theDebug = Debug.d().debug(this);
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "distinct");
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theParent.getThreadConstraint();
		}

		@Override
		public boolean isLockSupported() {
			return theParent.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theParent.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return theParent.getCoreId();
		}

		@Override
		public boolean isEventing() {
			return theParent.isEventing();
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			UniqueElement element = theElementsByValue.get(value);
			return element == null ? el -> -1 : element;
		}

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(theParent.getElementsBySource(sourceEl, sourceCollection),
				parentEl -> theElementsByValue.get(parentEl.get()));
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection) {
			BetterList<ElementId> sourceEls = BetterTreeList.<ElementId> build().build();
			DerivedCollectionElement<T> activeEl = ((UniqueElement) localElement).theActiveElement;
			sourceEls.addAll(theParent.getSourceElements(activeEl, sourceCollection));
			for (DerivedCollectionElement<T> parentEl : ((UniqueElement) localElement).theParentElements.keySet()) {
				if (parentEl != activeEl)
					sourceEls.addAll(theParent.getSourceElements(parentEl, sourceCollection));
			}
			return sourceEls.isEmpty() ? BetterList.empty() : sourceEls;
		}

		@Override
		public DerivedCollectionElement<T> getEquivalentElement(DerivedCollectionElement<?> flowEl) {
			if (!(flowEl instanceof DistinctManager.UniqueElement))
				return null;
			UniqueElement other = (UniqueElement) flowEl;
			if (other.getMgr() == this)
				return other;
			DerivedCollectionElement<T> found = theParent.getEquivalentElement(other.theActiveElement);
			return found == null ? null : theElementsByValue.get(found.get());
		}

		/**
		 * @param value The value to get the element for
		 * @return The handle for the element at the given value
		 */
		protected MapEntryHandle<T, UniqueElement> getElement(T value) {
			return theElementsByValue.getEntry(value);
		}

		/**
		 * @param valueId The entry ID of the value to get the element for
		 * @return The handle for the element with the given ID in the value map
		 */
		protected MapEntryHandle<T, UniqueElement> getElement(ElementId valueId) {
			return theElementsByValue.getEntryById(valueId);
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			if (isPreservingSourceOrder) {
				if (theElementsByValue.containsKey(toAdd))
					return StdMsg.ELEMENT_EXISTS;
				return theParent.canAdd(toAdd, parent(after), parent(before));
			} else {
				String msg = theElementsByValue.keySet().canAdd(toAdd, valueElement(after), valueElement(before));
				if (msg != null)
					return msg;
				return theParent.canAdd(toAdd, null, null);
			}
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			try (Transaction t = lock(true, null)) {
				String msg = theElementsByValue.keySet().canAdd(value, valueElement(after), valueElement(before));
				if (StdMsg.ELEMENT_EXISTS.equals(msg))
					return null;
				else if (StdMsg.UNSUPPORTED_OPERATION.equals(msg))
					throw new UnsupportedOperationException(msg);
				else if (msg != null)
					throw new IllegalArgumentException(msg);
				UniqueElement element = null;
				DerivedCollectionElement<T> parentAfter, parentBefore;
				if (isPreservingSourceOrder) {
					parentAfter = parent(after);
					parentBefore = parent(before);
				} else {
					// Since the element map is determining order, we forward the endian request (first or last) to the unique map
					element = createUniqueElement(value);
					// First, install the (currently empty) unique element in the element map so that the position is correct
					element.theValueId = theElementsByValue.putEntry(value, element, valueElement(after), valueElement(before), first)
						.getElementId();
					parentAfter = parentBefore = null;
				}
				try {
					// Parent collection order does not matter, so the first boolean does not apply here.
					// Just add it to the end.
					DerivedCollectionElement<T> parentEl = theParent.addElement(value, parentAfter, parentBefore, false);
					if (parentEl == null) {
						if (element != null)
							theElementsByValue.mutableEntry(element.theValueId).remove();
						return null;
					}
				} catch (RuntimeException e) {
					if (element != null)
						theElementsByValue.mutableEntry(element.theValueId).remove();
					throw e;
				}
				if (element == null) {
					// Look the element up.
					// If it's not there, here I'm returning null, implying that the element was not added to the unique set
					// This would probably be a bug though.
					element = theElementsByValue.get(value);
				}
				return element;
			}
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			if (isPreservingSourceOrder) {
				/*
				String msg = null;
				for (DerivedCollectionElement<T> parentEl : ((UniqueElement) valueEl).getParentElements().keySet()) {
					msg = theParent.canMove(parentEl, parent(after), parent(before));
					if (msg != null)
						break;
				}
				return msg;
				 */
				return "Not Implemented";
			} else {
				return theElementsByValue.keySet().canMove(((UniqueElement) valueEl).theValueId,
					after == null ? null : ((UniqueElement) after).theValueId, before == null ? null : ((UniqueElement) before).theValueId);
			}
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			if (isPreservingSourceOrder) {
				throw new UnsupportedOperationException("Not Implemented");
			} else {
				try (Transaction t = lock(true, null)) {
					UniqueElement ue = (UniqueElement) valueEl;
					CollectionElement<T> newValueId = theElementsByValue.keySet().move(ue.theValueId, //
						after == null ? null : ((UniqueElement) after).theValueId,
							before == null ? null : ((UniqueElement) before).theValueId, first, () -> {
								ObservableCollectionActiveManagers.removed(ue.theListener, ue.theValue, null);
							});
					if (newValueId.getElementId().equals(ue.theValueId))
						return ue;
					else {
						UniqueElement newEl = createUniqueElement(ue.theValue);
						newEl.theValue = ue.theValue;
						newEl.theValueId = newValueId.getElementId();
						theElementsByValue.mutableEntry(newValueId.getElementId()).setValue(newEl);
						for (DerivedCollectionElement<T> parentEl : ue.theParentElements.keySet())
							newEl.addParent(parentEl, null);
						newEl.theActiveElement = ue.theActiveElement;
						return newEl;
					}
				}
			}
		}

		private ElementId valueElement(DerivedCollectionElement<T> el) {
			return el == null ? null : ((UniqueElement) el).theValueId;
		}

		private DerivedCollectionElement<T> parent(DerivedCollectionElement<T> el) {
			return el == null ? null : ((UniqueElement) el).theActiveElement;
		}

		@Override
		public boolean clear() {
			boolean hasFail = false;
			for (UniqueElement el : theElementsByValue.values()) {
				if (el.canRemove() != null) {
					hasFail = true;
					break;
				}
			}
			if (!hasFail)
				return theParent.clear();
			for (UniqueElement el : theElementsByValue.values()) {
				if (el.canRemove() == null)
					el.remove();
			}
			return false;
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			if (elements.isEmpty())
				return;
			UniqueElement uniqueEl = theElementsByValue.get(newValue);
			if (uniqueEl != null) {
				if (elements.stream().anyMatch(el -> uniqueEl != el))
					throw new IllegalArgumentException(StdMsg.ELEMENT_EXISTS);
			} else {
				String msg = elements.stream()
					.map(el -> theElementsByValue.keySet().mutableElement(((UniqueElement) el).theValueId).isAcceptable(newValue))
					.filter(m -> m != null).findAny().orElse(null);
				if (StdMsg.UNSUPPORTED_OPERATION.equals(msg))
					throw new UnsupportedOperationException(msg);
				else if (msg != null)
					throw new IllegalArgumentException(msg);
			}
			// Change the first element to be the element at the new value
			UniqueElement first = (UniqueElement) elements.iterator().next();
			T oldValue = first.theValue;
			first.moveTo(newValue);
			try {
				List<DerivedCollectionElement<T>> parentEls = elements.stream().flatMap(el -> {
					UniqueElement uel = (UniqueElement) el;
					return Stream.concat(Stream.of(uel.theActiveElement), //
						uel.theParentElements.keySet().stream().filter(pe -> pe != uel.theActiveElement).sorted());
				}).collect(Collectors.toList());
				theParent.setValues(parentEls, newValue);
			} catch (RuntimeException e) {
				first.moveTo(oldValue);
				throw e;
			}
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theAccepter = onElement;
			theParent.begin(fromStart, (parentEl, cause) -> {
				T value = parentEl.get();
				theElementsByValue.computeIfAbsent(value, //
					v -> createUniqueElement(v))//
				.addParent(parentEl, cause);
			}, listening);
		}

		@Override
		public boolean isConsistent(DerivedCollectionElement<T> element) {
			return theElementsByValue.isConsistent(((UniqueElement) element).getValueElement());
		}

		@Override
		public boolean checkConsistency() {
			return theElementsByValue.checkConsistency();
		}

		@Override
		public <X> boolean repair(DerivedCollectionElement<T> element, RepairListener<T, X> listener) {
			throw new UnsupportedOperationException("Not implemented yet"); // TODO
		}

		@Override
		public <X> boolean repair(RepairListener<T, X> listener) {
			throw new UnsupportedOperationException("Not implemented yet"); // TODO
		}

		/**
		 * @param value The value to create new element for
		 * @return The element to use for the value
		 */
		protected UniqueElement createUniqueElement(T value) {
			return new UniqueElement(value);
		}

		/** A {@link DerivedCollectionElement} for a {@link DistinctManager} */
		protected class UniqueElement implements DerivedCollectionElement<T> {
			private final BetterTreeMap<DerivedCollectionElement<T>, T> theParentElements;
			private T theValue;
			private ElementId theValueId;
			private DerivedCollectionElement<T> theActiveElement;
			private CollectionElementListener<T> theListener;
			private boolean isInternallySetting;

			/** @param value The value that the element is for */
			protected UniqueElement(T value) {
				theValue = value;
				theParentElements = BetterTreeMap.<DerivedCollectionElement<T>> build(DerivedCollectionElement::compareTo).buildMap();
			}

			DistinctManager<E, T> getMgr() {
				return DistinctManager.this;
			}

			/** @return The source element that currently represents this element in the distinct collection */
			protected DerivedCollectionElement<T> getActiveElement() {
				return theActiveElement;
			}

			/** @return All elements (and their last known values) grouped into this distinct element */
			protected BetterTreeMap<DerivedCollectionElement<T>, T> getParentElements() {
				return theParentElements;
			}

			/**
			 * Called when an element whose value is equivalent to this element's is added to the source collection (also for initial
			 * values)
			 *
			 * @param parentEl The added source element
			 * @param cause The cause of the addition
			 * @return The entry in the {@link #theParentElements element map} map where the parent was added
			 */
			protected BinaryTreeEntry<DerivedCollectionElement<T>, T> addParent(DerivedCollectionElement<T> parentEl, Object cause) {
				if (theValueId == null)
					theValueId = theElementsByValue.getEntry(theValue).getElementId();
				boolean only = theParentElements.isEmpty();
				BinaryTreeEntry<DerivedCollectionElement<T>, T> node = theParentElements.putEntry(parentEl, parentEl.get(), false);
				if (node == null) {
					theDebug.act("duplicate").param("value", theValue).exec();
					throw new IllegalStateException("Duplicate parent added: " + parentEl);
				}
				if (only) {
					// The parent is the first representing this element
					theDebug.act("add:new").param("value", theValue).exec();
					theActiveElement = parentEl;
					theAccepter.accept(this, cause);
				} else if (isAlwaysUsingFirst && node.getClosest(true) == null) {
					theDebug.act("add:repChange").param("value", theValue).exec();
					// The new element takes precedence over the current one
					T oldValue = theActiveElement.get();
					T newActiveValue = parentEl.get();
					theActiveElement = parentEl;
					//Fire an internal-only event if there's not actually a change
					ObservableCollectionActiveManagers.update(theListener, oldValue, newActiveValue, cause, oldValue == newActiveValue);
				} else {
					theDebug.act("add:no-effect").param("value", theValue).exec();
					T value = theActiveElement.get();
					ObservableCollectionActiveManagers.update(theListener, value, value, cause, true);
				}

				parentEl.setListener(new CollectionElementListener<T>() {
					@Override
					public void update(T oldValue, T newValue, Object innerCause, boolean internalOnly) {
						T realOldValue = node.getValue();
						theDebug.act("update").param("@", theValue).param("newValue", newValue).exec();
						if (isInternallySetting) {
							if (theActiveElement == parentEl) {
								theDebug.act("update:trueUpdate").exec();
								ObservableCollectionActiveManagers.update(theListener, realOldValue, newValue, innerCause, internalOnly);
							} else
								theDebug.act("update:no-effect").exec();
							theParentElements.mutableEntry(node.getElementId()).setValue(newValue);
							parentUpdated(node, realOldValue, newValue, innerCause);
							return;
						}
						/* As originally written, this code made the assumption that at the time of an update event, only that element may
						 * have changed in the collection.  Unfortunately, this assumption makes many good use cases of this tool invalid.
						 *
						 * E.g. if there is a collection of entities sorted by name, there may be an operation which changes the names of
						 * multiple entities at once (outside the scope of the collection) and then attempts to alert the collection to all
						 * the changes after the fact.
						 *
						 * This use case is prone to failure because multiple nodes in the tree backing the collection may have become
						 * corrupt.  Attempts to fix this by one-by-one repair may fail, leaving the collection unsorted and some elements
						 * unsearchable.
						 *
						 * See https://github.com/Updownquark/Qommons/issues/2 for more details.
						 *
						 * I am modifying this code to attempt to address this vulnerability.
						 */
						UniqueElement ue = theElementsByValue.get(newValue);
						boolean reInsert;
						BooleanSupplier consistent;
						if (node.getValue() != newValue)
							consistent = OptimisticContext.TRUE;
						else
							consistent = new BooleanSupplier() {
							private Ternian value = Ternian.NONE;

							@Override
							public boolean getAsBoolean() {
								if (value == Ternian.NONE)
									value = Ternian.ofBoolean(theElementsByValue.isConsistent(theValueId));
								return value.value;
							}
						};
						if (ue == UniqueElement.this) {
							reInsert = false;
							if (theActiveElement == parentEl) {
								// Check to see if the value is still consistent in the structure
								if (consistent.getAsBoolean()) {
									theDebug.act("update:trueUpdate").exec();
									theValue = newValue;
									ObservableCollectionActiveManagers.update(theListener, realOldValue, newValue, innerCause, false);
								} else
									reInsert = true;
							} else {
								// The updated parent element is not the representative for the value--no effect
								theDebug.act("update:no-effect").exec();
							}
							if (!reInsert) {
								theParentElements.mutableEntry(node.getElementId()).setValue(newValue);
								ObservableCollectionActiveManagers.update(theListener, realOldValue, newValue, innerCause, true);
								parentUpdated(node, realOldValue, newValue, innerCause);
							}
						} else if (ue == null && theParentElements.size() == 1
							&& theElementsByValue.keySet().mutableElement(theValueId).isAcceptable(newValue) == null
							&& consistent.getAsBoolean()) {
							// If we can just fire an update instead of an add/remove, let's do that
							theDebug.act("update:move").exec();
							moveTo(newValue);
							theParentElements.mutableEntry(node.getElementId()).setValue(newValue);
							ObservableCollectionActiveManagers.update(theListener, realOldValue, newValue, innerCause, false);
							parentUpdated(node, realOldValue, newValue, innerCause);
							reInsert = false;
						} else
							reInsert = true;

						if (reInsert) {
							theParentElements.mutableEntry(node.getElementId()).remove();
							if (consistent.getAsBoolean()) {
								// TODO
							}
							boolean updateActive = false;
							if (theParentElements.isEmpty()) {
								theDebug.act("update:remove").param("value", theValue).exec();
								// This element is no longer represented
								theElementsByValue.mutableEntry(theValueId).remove();
								ObservableCollectionActiveManagers.removed(theListener, realOldValue, innerCause);
								parentRemoved(node, realOldValue, innerCause);
							} else {
								updateActive = true;
								if (theActiveElement == parentEl) {
									Map.Entry<DerivedCollectionElement<T>, T> activeEntry = theParentElements.firstEntry();
									theActiveElement = activeEntry.getKey();
									theDebug.act("update:remove:repChange").exec();
									theValue = activeEntry.getValue();
									parentUpdated(node, realOldValue, newValue, innerCause);
								} else
									theDebug.act("update:remove:no-effect").exec();
							}

							if (ue == null) {
								ue = createUniqueElement(newValue);
								theElementsByValue.put(newValue, ue);
							}
							if (ue != UniqueElement.this) {
								// The new UniqueElement will call setListener and we won't get its events anymore
								theDebug.setField("internalAdd", true);
								ue.addParent(parentEl, innerCause);
								theDebug.setField("internalAdd", null);
							}
							if (updateActive) {
								// Fire an internal-only event if there's no actual change
								ObservableCollectionActiveManagers.update(theListener, realOldValue, theValue, innerCause,
									realOldValue == theValue);
							}
						}
					}

					@Override
					public void removed(T value, Object innerCause) {
						theParentElements.mutableEntry(//
							node.getElementId())//
						.remove();
						if (theParentElements.isEmpty()) {
							theDebug.act("remove:remove").param("value", theValue).exec();
							// This element is no longer represented
							theElementsByValue.mutableEntry(theValueId).remove();
							ObservableCollectionActiveManagers.removed(theListener, theValue, innerCause);
						} else if (theActiveElement == parentEl) {
							theDebug.act("remove:repChange").exec();
							Map.Entry<DerivedCollectionElement<T>, T> activeEntry = theParentElements.firstEntry();
							theActiveElement = activeEntry.getKey();
							T oldValue = theValue;
							theValue = activeEntry.getValue();
							if (isPreservingSourceOrder) {
								// If the active element of a unique element is removed,
								// then the order of the unique elements may have changed
								// because the new active element may not be in the same order
								// relative to the active elements of all the other unique elements.
								// Since the manager doesn't attempt to keep the elements in source order (which would be expensive),
								// there's no quick way to determine if the new active element actually is out of order.
								// So we need to just remove and re-add this element
								ObservableCollectionActiveManagers.removed(theListener, theValue, innerCause);
								theAccepter.accept(UniqueElement.this, innerCause);
							} else //Fire an internal-only event if there's no actual change
								ObservableCollectionActiveManagers.update(theListener, oldValue, theValue, innerCause,
									oldValue == theValue);
						} else
							ObservableCollectionActiveManagers.update(theListener, theValue, theValue, innerCause, true);
					}
				});
				return node;
			}

			/**
			 * Called after a source element's value has been reported as changed, which did not require moving the source element to a
			 * different distinct element
			 *
			 * @param node The tree entry of the source element
			 * @param oldValue The source element's previously-reported value
			 * @param newValue The source element's new value
			 * @param cause The cause of the change
			 */
			protected void parentUpdated(BinaryTreeEntry<DerivedCollectionElement<T>, T> node, T oldValue, T newValue, Object cause) {
			}

			/**
			 * Called after a source element has been removed or if the element's value has been changed such that it must be moved to a
			 * different distinct element
			 *
			 * @param parentEl The (already removed) tree entry of the source element
			 * @param value The source element's previously-reported value
			 * @param cause The cause of the removal
			 */
			protected void parentRemoved(BinaryTreeEntry<DerivedCollectionElement<T>, T> parentEl, T value, Object cause) {
			}

			/**
			 * @return The element ID of the map entry where this distinct element is stored in the manager's
			 *         {@link DistinctManager#theElementsByValue value map}
			 */
			protected ElementId getValueElement() {
				if (theValueId == null)
					theValueId = theElementsByValue.getEntry(theActiveElement.get()).getElementId();
				return theValueId;
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				if (isPreservingSourceOrder)
					return theActiveElement.compareTo(((UniqueElement) o).theActiveElement);
				else
					return theValueId.compareTo(((UniqueElement) o).theValueId);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public T get() {
				return theActiveElement.get();
			}

			@Override
			public String isEnabled() {
				// A replacement operation involves replacing the values for each element composing this unique element
				for (DerivedCollectionElement<T> el : theParentElements.keySet()) {
					String msg = el.isEnabled();
					if (msg != null)
						return msg;
				}
				return null;
			}

			@Override
			public String isAcceptable(T value) {
				// A replacement operation involves replacing the values for each element composing this unique element
				String msg = isEnabled();
				if (msg != null)
					return msg;
				UniqueElement ue = theElementsByValue.get(value);
				if (ue != null && ue != this) {
					// If the value already exists, then replacing the underlying values would just remove the unique element
					return StdMsg.ELEMENT_EXISTS;
				}
				if (!isPreservingSourceOrder) {
					msg = theElementsByValue.keySet().mutableElement(theValueId)//
						.isAcceptable(value);
					if (msg != null)
						return msg;
				}
				for (DerivedCollectionElement<T> el : theParentElements.keySet()) {
					msg = el.isAcceptable(value);
					if (msg != null)
						return msg;
				}
				return msg;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				// A replacement operation involves replacing the values for each element composing this unique element
				try (Transaction t = lock(true, null)) {
					String msg = isEnabled();
					if (msg != null)
						throw new UnsupportedOperationException(msg);
					UniqueElement ue = theElementsByValue.get(value);
					if (ue != null && ue != this) {
						// If the value already exists, then replacing the underlying values would just remove the unique element
						throw new IllegalArgumentException(StdMsg.ELEMENT_EXISTS);
					}
					String valueSetMsg = theElementsByValue.keySet().mutableElement(theValueId).isAcceptable(value);
					if (!isPreservingSourceOrder && valueSetMsg != null)
						throw new IllegalArgumentException(valueSetMsg);
					for (DerivedCollectionElement<T> el : theParentElements.keySet()) {
						msg = el.isAcceptable(value);
						if (msg != null)
							throw new IllegalArgumentException(msg);
					}
					T oldValue = theValue;
					// Move this element to the new value
					moveTo(value);
					isInternallySetting = true;
					// Make the active element the first element that gets updated. This will ensure a set operation instead of a remove/add
					List<DerivedCollectionElement<T>> parentEls = new ArrayList<>(theParentElements.size());
					parentEls.add(theActiveElement);
					theParentElements.keySet().stream().filter(pe -> pe != theActiveElement).sorted().forEach(parentEls::add);
					try {
						theParent.setValues(parentEls, value);
						isInternallySetting = false;
					} finally {
						if (isInternallySetting) {
							// An exception was thrown from a parent set operation. Need to reset the key back to the old value.
							isInternallySetting = false;
							moveTo(oldValue);
						}
					}
				}
			}

			void moveTo(T newValue) {
				theValue = newValue;
				if (theElementsByValue.keySet().mutableElement(theValueId).isAcceptable(newValue) == null)
					theElementsByValue.keySet().mutableElement(theValueId)//
					.set(newValue);
				else {
					theElementsByValue.mutableEntry(theValueId).remove();
					theValueId = theElementsByValue.putEntry(newValue, this, false).getElementId();
				}
			}

			@Override
			public String canRemove() {
				// A removal operation involves removing each element composing this unique element
				for (DerivedCollectionElement<T> el : theParentElements.keySet()) {
					String msg = el.canRemove();
					if (msg != null)
						return msg;
				}
				return null;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				// A removal operation involves removing each element composing this unique element
				String msg = canRemove();
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				// Make a copy since theValueElements will be modified with each remove
				List<DerivedCollectionElement<T>> elCopies = new ArrayList<>(theParentElements.keySet());
				for (DerivedCollectionElement<T> el : elCopies) {
					el.remove();
				}
			}

			@Override
			public String toString() {
				if (theActiveElement != null)
					return theActiveElement.toString();
				else if (!theParentElements.isEmpty())
					return theParentElements.firstKey().toString();
				else
					return "null";
			}
		}
	}

	/**
	 * A {@link DistinctDataFlow#collect() collected}, {@link DistinctDataFlow#supportsPassive() passive}ly-derived set
	 *
	 * @param <E> The type of the source set
	 * @param <T> The type of this set
	 */
	public static class PassiveDerivedSet<E, T> extends PassiveDerivedCollection<E, T> implements ObservableSet<T> {
		/**
		 * @param source The source set
		 * @param flow The data flow used to create the modified collection
		 */
		public PassiveDerivedSet(ObservableSet<E> source, PassiveCollectionManager<E, ?, T> flow) {
			super(source, flow);
		}

		@Override
		protected ObservableSet<E> getSource() {
			return (ObservableSet<E>) super.getSource();
		}

		@Override
		public CollectionElement<T> getOrAdd(T value, ElementId after, ElementId before, boolean first, Runnable added) {
			try (Transaction t = lock(true, null)) {
				// Lock so the reversed value is consistent until it is added
				FilterMapResult<T, E> reversed = getFlow().reverse(value, true, false);
				if (reversed.throwIfError(IllegalArgumentException::new) != null)
					return null;
				CollectionElement<E> srcEl = getSource().getOrAdd(reversed.result, after, before, first, added);
				return srcEl == null ? null : elementFor(srcEl, null);
			}
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

		@Override
		public String toString() {
			return BetterSet.toString(this);
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
	 * A {@link DistinctDataFlow#collect() collected}, {@link DistinctDataFlow#supportsPassive() active}ly-derived set
	 *
	 * @param <T> The type of this set
	 */
	public static class ActiveDerivedSet<T> extends ActiveDerivedCollection<T> implements ObservableSet<T> {
		/**
		 * @param flow The active manager to drive this set
		 * @param until The observable to terminate this derived set
		 */
		public ActiveDerivedSet(ActiveValueStoredManager<?, ?, T> flow, Observable<?> until) {
			super(flow, until);
		}

		@Override
		protected ActiveValueStoredManager<?, ?, T> getFlow() {
			return (ActiveValueStoredManager<?, ?, T>) super.getFlow();
		}

		@Override
		public CollectionElement<T> getOrAdd(T value, ElementId after, ElementId before, boolean first, Runnable added) {
			// At the moment, the flow doesn't support this operation directly, so we have to do a double-dive
			try (Transaction t = lock(true, null)) {
				CollectionElement<T> element = getElement(value, first);
				if (element == null) {
					element = addElement(value, after, before, first);
					if (element != null && added != null)
						added.run();
				}
				return element;
			}
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

		@Override
		public String toString() {
			return BetterSet.toString(this);
		}
	}

	/**
	 * An {@link ObservableSet} whose content cannot change
	 *
	 * @param <T> The type of values in the set
	 */
	public static class ConstantObservableSet<T> extends ConstantCollection<T> implements ObservableSet<T> {
		private final Equivalence<? super T> theEquivalence;
		private final BetterMap<T, ElementId> theIndex;

		/**
		 * @param type The type of values in the set
		 * @param equivalence The equivalence of the set
		 * @param values The values for the set
		 */
		public ConstantObservableSet(TypeToken<T> type, Equivalence<? super T> equivalence, BetterList<? extends T> values) {
			super(type, values);
			theEquivalence = equivalence;
			theIndex = theEquivalence.createMap();
			CollectionElement<T> el = getTerminalElement(true);
			while (el != null) {
				ElementId id = el.getElementId();
				if (theIndex.computeIfAbsent(el.get(), v -> id) != id)
					values.mutableElement(el.getElementId()).remove();
				el = getAdjacentElement(el.getElementId(), true);
			}
		}

		@Override
		public CollectionElement<T> getOrAdd(T value, ElementId after, ElementId before, boolean first, Runnable added) {
			ElementId el = theIndex.get(value);
			if (el != null)
				return getElement(el);
			else
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return true;
		}

		@Override
		public boolean checkConsistency() {
			return false;
		}

		@Override
		public <X> boolean repair(ElementId element, org.qommons.collect.ValueStoredCollection.RepairListener<T, X> listener) {
			return false;
		}

		@Override
		public <X> boolean repair(org.qommons.collect.ValueStoredCollection.RepairListener<T, X> listener) {
			return false;
		}
	}

	/**
	 * Implements {@link ObservableSet#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class FlattenedValueSet<E> extends FlattenedValueCollection<E> implements ObservableSet<E> {
		/**
		 * @param collectionObservable The value containing a set to flatten
		 * @param equivalence The equivalence for the set
		 */
		protected FlattenedValueSet(ObservableValue<? extends ObservableSet<E>> collectionObservable, Equivalence<? super E> equivalence) {
			super(collectionObservable, equivalence);
		}

		@Override
		public CollectionElement<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable added) {
			// *Possibly* could figure out how to do this more efficiently, but for the moment this will work
			try (Transaction t = lock(true, null)) {
				CollectionElement<E> element = getElement(value, first);
				if (element == null) {
					element = addElement(value, after, before, first);
					if (element != null && added != null)
						added.run();
				}
				return element;
			}
		}

		@Override
		protected ObservableValue<? extends ObservableSet<E>> getWrapped() {
			return (ObservableValue<? extends ObservableSet<E>>) super.getWrapped();
		}

		@Override
		public boolean isConsistent(ElementId element) {
			ObservableSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				throw new NoSuchElementException();
			return wrapped.isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			ObservableSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				throw new NoSuchElementException();
			return wrapped.checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
			ObservableSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				throw new NoSuchElementException();
			return wrapped.repair(element, listener);
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			ObservableSet<E> wrapped = getWrapped().get();
			if (wrapped == null)
				throw new NoSuchElementException();
			return wrapped.repair(listener);
		}
	}

	/**
	 * Default {@link DataControlledCollection.Set data controlled set} implementation
	 *
	 * @param <E> The type of the collection values
	 * @param <V> The type of the source data
	 */
	public static class DataControlledSetImpl<E, V> extends ObservableCollectionImpl.DataControlledCollectionImpl<E, V>
	implements DataControlledCollection.Set<E, V> {
		/**
		 * @param backing The set to control all the observable functionality
		 * @param backingData Supplies backing data for refresh operations
		 * @param autoRefresh The asynchronous auto refresher for this collection
		 * @param refreshOnAccess Whether this collection should refresh synchronously each time it is accessed
		 * @param equals The equals tester to preserve elements between refreshes
		 * @param synchronizer The synchronizer to perform the refresh operation
		 * @param adjustmentOrder The adjustment order for the synchronization
		 */
		public DataControlledSetImpl(ObservableSet<E> backing, Supplier<? extends List<? extends V>> backingData,
			DataControlAutoRefresher autoRefresh, boolean refreshOnAccess, BiPredicate<? super E, ? super V> equals,
			CollectionSynchronizerE<E, ? super V, ?> synchronizer, AdjustmentOrder adjustmentOrder) {
			super(backing, backingData, autoRefresh, refreshOnAccess, equals, synchronizer, adjustmentOrder);
		}

		@Override
		protected ObservableSet<E> getWrapped() throws IllegalStateException {
			return (ObservableSet<E>) super.getWrapped();
		}

		@Override
		public DataControlledSetImpl<E, V> setMaxRefreshFrequency(long frequency) {
			super.setMaxRefreshFrequency(frequency);
			return this;
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

		@Override
		public CollectionElement<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable added) {
			refresh();
			return getWrapped().getOrAdd(value, after, before, first, added);
		}
	}
}
