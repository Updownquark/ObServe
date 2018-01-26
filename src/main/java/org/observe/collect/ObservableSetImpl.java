package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
import org.observe.collect.ObservableCollection.ModFilterBuilder;
import org.observe.collect.ObservableCollection.UniqueDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.BaseCollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionElementListener;
import org.observe.collect.ObservableCollectionDataFlowImpl.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionDataFlowImpl.ElementAccepter;
import org.observe.collect.ObservableCollectionDataFlowImpl.PassiveCollectionManager;
import org.observe.collect.ObservableCollectionImpl.ActiveDerivedCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedValueCollection;
import org.observe.collect.ObservableCollectionImpl.PassiveDerivedCollection;
import org.observe.collect.ObservableCollectionImpl.ReversedObservableCollection;
import org.observe.util.WeakListening;
import org.qommons.Transaction;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterSet;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.debug.Debug;
import org.qommons.debug.Debug.DebugData;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BinaryTreeEntry;

import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableSet} methods */
public class ObservableSetImpl {
	private ObservableSetImpl() {}

	public static class ReversedSet<E> extends ReversedObservableCollection<E> implements ObservableSet<E> {
		public ReversedSet(ObservableSet<E> wrapped) {
			super(wrapped);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
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

	public static class UniqueDataFlowWrapper<E, T> extends ObservableCollectionDataFlowImpl.AbstractDataFlow<E, T, T>
	implements UniqueDataFlow<E, T, T> {
		protected UniqueDataFlowWrapper(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent,
			Equivalence<? super T> equivalence) {
			super(source, parent, parent.getTargetType(), equivalence);
		}

		@Override
		public UniqueDataFlow<E, T, T> reverse() {
			return new UniqueDataFlowWrapper<>(getSource(), super.reverse(), equivalence());
		}

		@Override
		public UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filter(filter), equivalence());
		}

		@Override
		public <X> UniqueDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			return new UniqueDataFlowWrapper<>(getSource(), super.whereContained(other, include), equivalence());
		}

		@Override
		public <X> UniqueDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, Function<? super T, ? extends X> map,
			Function<? super X, ? extends T> reverse, Consumer<MapOptions<T, X>> options) {
			MapOptions<T, X> mapOptions = new MapOptions<>();
			options.accept(mapOptions);
			mapOptions.withReverse(reverse);
			return new UniqueMapOp<>(getSource(), this, target, map, new MapDef<>(mapOptions));
		}

		@Override
		public UniqueDataFlow<E, T, T> distinct(Consumer<UniqueOptions> options) {
			options.accept(new FlowOptions.SimpleUniqueOptions(equivalence() instanceof Equivalence.ComparatorEquivalence));
			return this; // No-op
		}

		@Override
		public UniqueDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), super.refresh(refresh), equivalence());
		}

		@Override
		public UniqueDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), super.refreshEach(refresh), equivalence());
		}

		@Override
		public UniqueDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filterMod(options), equivalence());
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

	public static class UniqueOp<E, T> extends UniqueDataFlowWrapper<E, T> {
		private final boolean isAlwaysUsingFirst;
		private final boolean isPreservingSourceOrder;

		protected UniqueOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, Equivalence<? super T> equivalence,
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
			return new UniqueManager<>(getParent().manageActive(), equivalence(), isAlwaysUsingFirst, isPreservingSourceOrder);
		}
	}

	public static class UniqueMapOp<E, I, T> extends ObservableCollectionDataFlowImpl.MapOp<E, I, T> implements UniqueDataFlow<E, I, T> {
		public UniqueMapOp(ObservableCollection<E> source, UniqueDataFlow<E, ?, I> parent, TypeToken<T> target,
			Function<? super I, ? extends T> map, MapDef<I, T> options) {
			super(source, parent, target, map, options);
		}

		@Override
		public UniqueDataFlow<E, T, T> reverse() {
			return new UniqueDataFlowWrapper<>(getSource(), super.reverse(), equivalence());
		}

		@Override
		public UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filter(filter), equivalence());
		}

		@Override
		public <X> UniqueDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			return new UniqueDataFlowWrapper<>(getSource(), super.whereContained(other, include), equivalence());
		}

		@Override
		public <X> UniqueDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, Function<? super T, ? extends X> map,
			Function<? super X, ? extends T> reverse, Consumer<MapOptions<T, X>> options) {
			MapOptions<T, X> mapOptions = new MapOptions<>();
			options.accept(mapOptions);
			return new UniqueMapOp<>(getSource(), this, target, map, new MapDef<>(mapOptions));
		}

		@Override
		public UniqueDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), super.refresh(refresh), equivalence());
		}

		@Override
		public UniqueDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), super.refreshEach(refresh), equivalence());
		}

		@Override
		public UniqueDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filterMod(options), equivalence());
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

	public static class UniqueManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;
		private final BetterMap<T, UniqueElement> theElementsByValue;
		private final Equivalence<? super T> theEquivalence;
		private final boolean isAlwaysUsingFirst;
		private final boolean isPreservingSourceOrder;
		private ElementAccepter<T> theAccepter;

		private DebugData theDebug;

		protected UniqueManager(ActiveCollectionManager<E, ?, T> parent, Equivalence<? super T> equivalence, boolean alwaysUseFirst,
			boolean preserveSourceOrder) {
			theParent = parent;
			theEquivalence = equivalence;
			theElementsByValue = parent.equivalence().createMap();
			isAlwaysUsingFirst = alwaysUseFirst;
			isPreservingSourceOrder = preserveSourceOrder;

			theDebug = Debug.d().add("distinct");
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
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theParent.lock(write, structural, cause);
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

		protected MapEntryHandle<T, UniqueElement> getElement(T value) {
			return theElementsByValue.getEntry(value);
		}

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

		protected UniqueElement createUniqueElement(T value) {
			return new UniqueElement(value);
		}

		protected class UniqueElement implements DerivedCollectionElement<T> {
			private final BetterTreeMap<DerivedCollectionElement<T>, T> theParentElements;
			private T theValue;
			private ElementId theValueId;
			private DerivedCollectionElement<T> theActiveElement;
			private CollectionElementListener<T> theListener;
			private boolean isInternallySetting;

			protected UniqueElement(T value) {
				theValue = value;
				theParentElements = new BetterTreeMap<>(false, DerivedCollectionElement::compareTo);
			}

			protected DerivedCollectionElement<T> getActiveElement() {
				return theActiveElement;
			}

			protected BetterTreeMap<DerivedCollectionElement<T>, T> getParentElements() {
				return theParentElements;
			}

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
						UniqueElement ue = theElementsByValue.get(newValue);
						if (ue == UniqueElement.this) {
							if (theActiveElement == parentEl) {
								theDebug.act("update:trueUpdate").exec();
								theValue = newValue;
								ObservableCollectionDataFlowImpl.update(theListener, realOldValue, newValue, innerCause);
							} else
								theDebug.act("update:no-effect").exec();
							theParentElements.mutableEntry(node.getElementId()).setValue(newValue);
							parentUpdated(node, realOldValue, newValue, innerCause);
						} else {
							if (ue == null && theParentElements.size() == 1
								&& theElementsByValue.keySet().mutableElement(theValueId).isAcceptable(newValue) == null) {
								theDebug.act("update:move").exec();
								// If we can just fire an update instead of an add/remove, let's do that
								moveTo(newValue);
								theParentElements.mutableEntry(node.getElementId()).setValue(newValue);
								ObservableCollectionDataFlowImpl.update(theListener, realOldValue, newValue, innerCause);
								parentUpdated(node, realOldValue, newValue, innerCause);
								return;
							}
							if (ue == null) {
								ue = createUniqueElement(newValue);
								theElementsByValue.put(newValue, ue);
							}
							theParentElements.mutableEntry(node.getElementId()).remove();
							if (theParentElements.isEmpty()) {
								theDebug.act("remove:remove").param("value", theValue).exec();
								// This element is no longer represented
								theElementsByValue.mutableEntry(theValueId).remove();
								ObservableCollectionDataFlowImpl.removed(theListener, realOldValue, innerCause);
								parentRemoved(node, realOldValue, innerCause);
							} else if (theActiveElement == parentEl) {
								Map.Entry<DerivedCollectionElement<T>, T> activeEntry = theParentElements.firstEntry();
								theActiveElement = activeEntry.getKey();
								theDebug.act("remove:representativeChange").exec();
								theValue = activeEntry.getValue();
								if (realOldValue != theValue)
									ObservableCollectionDataFlowImpl.update(theListener, realOldValue, theValue, innerCause);
								parentUpdated(node, realOldValue, newValue, innerCause);
							} else
								theDebug.act("remove:no-effect").exec();
							// The new UniqueElement will call setListener and we won't get its events anymore
							theDebug.setField("internalAdd", true);
							ue.addParent(parentEl, innerCause);
							theDebug.setField("internalAdd", null);
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

			protected void parentUpdated(BinaryTreeEntry<DerivedCollectionElement<T>, T> node, T oldValue, T newValue, Object cause) {}

			protected void parentRemoved(BinaryTreeEntry<DerivedCollectionElement<T>, T> parentEl, T value, Object cause) {}

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

	public static class UniqueBaseFlow<E> extends BaseCollectionDataFlow<E> implements UniqueDataFlow<E, E, E> {
		protected UniqueBaseFlow(ObservableSet<E> source) {
			super(source);
		}

		@Override
		protected ObservableSet<E> getSource() {
			return (ObservableSet<E>) super.getSource();
		}

		@Override
		public UniqueDataFlow<E, E, E> reverse() {
			return new UniqueDataFlowWrapper<>(getSource(), super.reverse(), equivalence());
		}

		@Override
		public UniqueDataFlow<E, E, E> filter(Function<? super E, String> filter) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filter(filter), equivalence());
		}

		@Override
		public <X> UniqueDataFlow<E, E, E> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			return new UniqueDataFlowWrapper<>(getSource(), super.whereContained(other, include), equivalence());
		}

		@Override
		public <X> UniqueDataFlow<E, E, X> mapEquivalent(TypeToken<X> target, Function<? super E, ? extends X> map,
			Function<? super X, ? extends E> reverse, Consumer<MapOptions<E, X>> options) {
			MapOptions<E, X> mapOptions = new MapOptions<>();
			options.accept(mapOptions);
			return new UniqueMapOp<>(getSource(), this, target, map, new MapDef<>(mapOptions));
		}

		@Override
		public UniqueDataFlow<E, E, E> refresh(Observable<?> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), super.refresh(refresh), equivalence());
		}

		@Override
		public UniqueDataFlow<E, E, E> refreshEach(Function<? super E, ? extends Observable<?>> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), super.refreshEach(refresh), equivalence());
		}

		@Override
		public UniqueDataFlow<E, E, E> filterMod(Consumer<ModFilterBuilder<E>> options) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filterMod(options), equivalence());
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

	static class PassiveDerivedSet<E, T> extends PassiveDerivedCollection<E, T> implements ObservableSet<T> {
		/**
		 * @param source The source set. The unique operation is not light-weight, so the input must be a set
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
		public String toString() {
			return BetterSet.toString(this);
		}
	}

	public static class ActiveDerivedSet<T> extends ActiveDerivedCollection<T> implements ObservableSet<T> {
		public ActiveDerivedSet(ActiveCollectionManager<?, ?, T> flow, Observable<?> until) {
			super(flow, until);
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
		protected ObservableValue<? extends ObservableSet<E>> getWrapped() {
			return (ObservableValue<? extends ObservableSet<E>>) super.getWrapped();
		}
	}
}
