package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.FlowOptions.MapDef;
import org.observe.collect.FlowOptions.MapOptions;
import org.observe.collect.FlowOptions.UniqueOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.ModFilterBuilder;
import org.observe.collect.ObservableCollectionDataFlowImpl.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.BaseCollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionElementListener;
import org.observe.collect.ObservableCollectionDataFlowImpl.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionDataFlowImpl.ElementAccepter;
import org.observe.collect.ObservableCollectionDataFlowImpl.FilterMapResult;
import org.observe.collect.ObservableCollectionDataFlowImpl.PassiveCollectionManager;
import org.observe.collect.ObservableCollectionImpl.ActiveDerivedCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedValueCollection;
import org.observe.collect.ObservableCollectionImpl.PassiveDerivedCollection;
import org.observe.collect.ObservableCollectionImpl.ReversedObservableCollection;
import org.observe.util.WeakListening;
import org.qommons.Ternian;
import org.qommons.Transaction;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.OptimisticContext;
import org.qommons.debug.Debug;
import org.qommons.debug.Debug.DebugData;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BinaryTreeEntry;

import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableSet} and {@link DistinctDataFlow} methods */
public class ObservableSetImpl {
	private ObservableSetImpl() {}

	/**
	 * Implements {@link ObservableSet#reverse()}
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
		public CollectionElement<E> getOrAdd(E value, boolean first, Runnable added) {
			return CollectionElement.reverse(getOrAdd(value, !first, added));
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
			return getWrapped();
		}

		@Override
		public String toString() {
			return BetterSet.toString(this);
		}
	}

	/**
	 * An abstract {@link DistinctDataFlow} implementation returning default {@link DistinctDataFlow} implementations for most
	 * operations that should produce one
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
		public <X> DistinctDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, Function<? super T, ? extends X> map,
			Function<? super X, ? extends T> reverse, Consumer<MapOptions<T, X>> options) {
			MapOptions<T, X> mapOptions = new MapOptions<>();
			options.accept(mapOptions);
			mapOptions.withReverse(reverse);
			return new DistinctMapOp<>(getSource(), this, target, map, new MapDef<>(mapOptions));
		}

		@Override
		public DistinctDataFlow<E, T, T> distinct(Consumer<UniqueOptions> options) {
			options.accept(new FlowOptions.SimpleUniqueOptions(equivalence() instanceof Equivalence.ComparatorEquivalence));
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
		public boolean supportsPassive() {
			return getParent().supportsPassive();
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return getParent().manageActive();
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
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new DistinctManager<>(getParent().manageActive(), equivalence(), isAlwaysUsingFirst, isPreservingSourceOrder);
		}
	}

	/**
	 * Implements {@link DistinctDataFlow#mapEquivalent(TypeToken, Function, Function)}
	 *
	 * @param <E> The type of the source collection
	 * @param <I> The type of the parent flow
	 * @param <T> The type of this flow
	 */
	public static class DistinctMapOp<E, I, T> extends ObservableCollectionDataFlowImpl.MapOp<E, I, T> implements DistinctDataFlow<E, I, T> {
		/**
		 * @param source The source collection
		 * @param parent The parent flow
		 * @param target The type of this flow
		 * @param map The mapping function to produce this flow's values from its source
		 * @param options The options governing certain aspects of this flow's behavior, e.g. caching
		 */
		public DistinctMapOp(ObservableCollection<E> source, DistinctDataFlow<E, ?, I> parent, TypeToken<T> target,
			Function<? super I, ? extends T> map, MapDef<I, T> options) {
			super(source, parent, target, map, options);
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
		public <X> DistinctDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, Function<? super T, ? extends X> map,
			Function<? super X, ? extends T> reverse, Consumer<MapOptions<T, X>> options) {
			MapOptions<T, X> mapOptions = new MapOptions<>();
			options.accept(mapOptions);
			return new DistinctMapOp<>(getSource(), this, target, map, new MapDef<>(mapOptions));
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
		protected DistinctBaseFlow(ObservableSet<E> source) {
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
		public <X> DistinctDataFlow<E, E, X> mapEquivalent(TypeToken<X> target, Function<? super E, ? extends X> map,
			Function<? super X, ? extends E> reverse, Consumer<MapOptions<E, X>> options) {
			MapOptions<E, X> mapOptions = new MapOptions<>();
			options.accept(mapOptions);
			return new DistinctMapOp<>(getSource(), this, target, map, new MapDef<>(mapOptions));
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
		public ObservableSet<E> collect() {
			return (ObservableSet<E>) super.collect();
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

	/**
	 * Implements {@link DistinctOp#manageActive()}
	 *
	 * @param <E> The type of the source collection
	 * @param <T> The type of the derived set
	 */
	public static class DistinctManager<E, T> implements ActiveCollectionManager<E, T, T> {
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

			theDebug = Debug.d().debug(DistinctManager.class).add("distinct");
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
		public boolean isLockSupported() {
			return theParent.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return ObservableCollectionDataFlowImpl.structureAffectedPassLockThroughToParent(theParent, write, structural, cause);
		}

		@Override
		public Transaction tryLock(boolean write, boolean structural, Object cause) {
			return ObservableCollectionDataFlowImpl.structureAffectedTryPassLockThroughToParent(theParent, write, structural, cause);
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
				if (theElementsByValue.containsKey(value))
					return null;
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

		private ElementId valueElement(DerivedCollectionElement<T> el) {
			return el == null ? null : ((UniqueElement) el).theValueId;
		}

		private DerivedCollectionElement<T> parent(DerivedCollectionElement<T> el) {
			return el == null ? null : ((UniqueElement) el).theActiveElement;
		}

		@Override
		public boolean clear() {
			return theParent.clear();
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			if (elements.isEmpty())
				return;
			UniqueElement uniqueEl = theElementsByValue.get(newValue);
			if (uniqueEl != null && elements.stream().anyMatch(el -> uniqueEl != el))
				throw new IllegalArgumentException(StdMsg.ELEMENT_EXISTS);
			// Change the first element to be the element at the new value
			UniqueElement first = (UniqueElement) elements.iterator().next();
			T oldValue = first.theValue;
			first.moveTo(newValue);
			try {
				theParent.setValues(//
					elements.stream().flatMap(el -> {
						UniqueElement uel = (UniqueElement) el;
						return Stream.concat(Stream.of(uel.theActiveElement), //
							uel.theParentElements.keySet().stream().filter(pe -> pe != uel.theActiveElement));
					}).collect(Collectors.toList()), newValue);
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
				theElementsByValue.computeIfAbsent(value, v -> createUniqueElement(v)).addParent(parentEl, cause);
			}, listening);
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
				theParentElements = new BetterTreeMap<>(false, DerivedCollectionElement::compareTo);
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
				theDebug.debugIf(!theDebug.is("internalAdd")).act("addSource").param("@", theValue).exec();
				if (only) {
					// The parent is the first representing this element
					theDebug.act("add:new").param("value", theValue).exec();
					theActiveElement = parentEl;
					theAccepter.accept(this, cause);
				} else if (isAlwaysUsingFirst && node.getClosest(true) == null) {
					theDebug.act("add:representativeChanged").param("value", theValue).exec();
					// The new element takes precedence over the current one
					T oldValue = theActiveElement.get();
					T newActiveValue = parentEl.get();
					theActiveElement = parentEl;
					if (oldValue != newActiveValue)
						ObservableCollectionDataFlowImpl.update(theListener, oldValue, newActiveValue, cause);
				} else
					theDebug.act("add:no-effect").param("value", theValue).exec();

				parentEl.setListener(new CollectionElementListener<T>() {
					@Override
					public void update(T oldValue, T newValue, Object innerCause) {
						T realOldValue = node.getValue();
						theDebug.act("update").param("@", theValue).param("newValue", newValue).exec();
						if (isInternallySetting) {
							if (theActiveElement == parentEl) {
								theDebug.act("update:trueUpdate").exec();
								ObservableCollectionDataFlowImpl.update(theListener, realOldValue, newValue, innerCause);
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
									ObservableCollectionDataFlowImpl.update(theListener, realOldValue, newValue, innerCause);
								} else
									reInsert = true;
							} else {
								// The updated parent element is not the representative for the value--no effect
								theDebug.act("update:no-effect").exec();
							}
							if (!reInsert) {
								theParentElements.mutableEntry(node.getElementId()).setValue(newValue);
								parentUpdated(node, realOldValue, newValue, innerCause);
							}
						} else if (ue == null && theElementsByValue.keySet().mutableElement(theValueId).isAcceptable(newValue) == null
							&& consistent.getAsBoolean()) {
							// If we can just fire an update instead of an add/remove, let's do that
							theDebug.act("update:move").exec();
							moveTo(newValue);
							theParentElements.mutableEntry(node.getElementId()).setValue(newValue);
							ObservableCollectionDataFlowImpl.update(theListener, realOldValue, newValue, innerCause);
							parentUpdated(node, realOldValue, newValue, innerCause);
							reInsert = false;
						} else
							reInsert = true;

						if (reInsert) {
							theParentElements.mutableEntry(node.getElementId()).remove();
							if (consistent.getAsBoolean()) {
								// TODO
							}
							if (theParentElements.isEmpty()) {
								theDebug.act("update:remove").param("value", theValue).exec();
								// This element is no longer represented
								theElementsByValue.mutableEntry(theValueId).remove();
								ObservableCollectionDataFlowImpl.removed(theListener, realOldValue, innerCause);
								parentRemoved(node, realOldValue, innerCause);
							} else if (theActiveElement == parentEl) {
								Map.Entry<DerivedCollectionElement<T>, T> activeEntry = theParentElements.firstEntry();
								theActiveElement = activeEntry.getKey();
								theDebug.act("update:remove:representativeChange").exec();
								theValue = activeEntry.getValue();
								if (realOldValue != theValue)
									ObservableCollectionDataFlowImpl.update(theListener, realOldValue, theValue, innerCause);
								parentUpdated(node, realOldValue, newValue, innerCause);
							} else
								theDebug.act("update:remove:no-effect").exec();

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
						}
					}

					@Override
					public void removed(T value, Object innerCause) {
						theDebug.act("remove").param("@", theValue).exec();
						theParentElements.mutableEntry(node.getElementId()).remove();
						if (theParentElements.isEmpty()) {
							theDebug.act("remove:remove").param("value", theValue).exec();
							// This element is no longer represented
							theElementsByValue.mutableEntry(theValueId).remove();
							ObservableCollectionDataFlowImpl.removed(theListener, theValue, innerCause);
						} else if (theActiveElement == parentEl) {
							theDebug.act("remove:representativeChange").exec();
							Map.Entry<DerivedCollectionElement<T>, T> activeEntry = theParentElements.firstEntry();
							theActiveElement = activeEntry.getKey();
							T oldValue = theValue;
							theValue = activeEntry.getValue();
							if (oldValue != theValue)
								ObservableCollectionDataFlowImpl.update(theListener, oldValue, theValue, innerCause);
						}
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
			protected void parentUpdated(BinaryTreeEntry<DerivedCollectionElement<T>, T> node, T oldValue, T newValue, Object cause) {}

			/**
			 * Called after a source element has been removed or if the element's value has been changed such that it must be moved to a
			 * different distinct element
			 *
			 * @param parentEl The (already removed) tree entry of the source element
			 * @param value The source element's previously-reported value
			 * @param cause The cause of the removal
			 */
			protected void parentRemoved(BinaryTreeEntry<DerivedCollectionElement<T>, T> parentEl, T value, Object cause) {}

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
			public boolean isAssociated(ObservableCollection<?> source, ElementId sourceEl) {
				// Only represent the active elements--the others are basically filtered out
				return theActiveElement.isAssociated(source, sourceEl);
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
					msg = theElementsByValue.keySet().mutableElement(theValueId).isAcceptable(value);
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
					theParentElements.keySet().stream().filter(pe -> pe != theActiveElement).forEach(parentEls::add);
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
					theElementsByValue.keySet().mutableElement(theValueId).set(newValue);
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
		protected PassiveDerivedSet(ObservableSet<E> source, PassiveCollectionManager<E, ?, T> flow) {
			super(source, flow);
		}

		@Override
		protected ObservableSet<E> getSource() {
			return (ObservableSet<E>) super.getSource();
		}

		@Override
		public CollectionElement<T> getOrAdd(T value, boolean first, Runnable added) {
			try (Transaction t = lock(true, null)) {
				// Lock so the reversed value is consistent until it is added
				FilterMapResult<T, E> reversed = getFlow().reverse(value, true);
				if (reversed.throwIfError(IllegalArgumentException::new) != null)
					return null;
				CollectionElement<E> srcEl = getSource().getOrAdd(reversed.result, first, added);
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
		public ActiveDerivedSet(ActiveCollectionManager<?, ?, T> flow, Observable<?> until) {
			super(flow, until);
		}

		@Override
		public CollectionElement<T> getOrAdd(T value, boolean first, Runnable added) {
			// At the moment, the flow doesn't support this operation directly, so we have to do a double-dive
			try (Transaction t = lock(true, null)) {
				CollectionElement<T> element = getElement(value, first);
				if (element == null) {
					element = addElement(value, first);
					if (element != null && added != null)
						added.run();
				}
				return element;
			}
		}

		@Override
		public String toString() {
			return BetterSet.toString(this);
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
		public CollectionElement<E> getOrAdd(E value, boolean first, Runnable added) {
			// *Possibly* could figure out how to do this more efficiently, but for the moment this will work
			try (Transaction t = lock(true, null)) {
				CollectionElement<E> element = getElement(value, first);
				if (element == null) {
					element = addElement(value, first);
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
			ObservableSet<E> wrapped=getWrapped().get();
			if(wrapped==null)
				throw new NoSuchElementException();
			return wrapped.repair(listener);
		}
	}
}
