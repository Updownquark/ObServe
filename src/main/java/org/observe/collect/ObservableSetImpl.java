package org.observe.collect;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.FlowOptions.MapDef;
import org.observe.collect.FlowOptions.MapOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.ModFilterBuilder;
import org.observe.collect.ObservableCollection.UniqueDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.BaseCollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionElementListener;
import org.observe.collect.ObservableCollectionDataFlowImpl.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionDataFlowImpl.ElementAccepter;
import org.observe.collect.ObservableCollectionDataFlowImpl.ModFilterer;
import org.observe.collect.ObservableCollectionDataFlowImpl.PassiveCollectionManager;
import org.observe.collect.ObservableCollectionImpl.ActiveDerivedCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedValueCollection;
import org.observe.collect.ObservableCollectionImpl.PassiveDerivedCollection;
import org.observe.collect.ObservableCollectionImpl.ReversedObservableCollection;
import org.observe.util.WeakListening;
import org.qommons.Transaction;
import org.qommons.collect.BetterMap;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.BinaryTreeNode;

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
	}

	public static class UniqueDataFlowWrapper<E, T> extends ObservableCollectionDataFlowImpl.AbstractDataFlow<E, T, T>
	implements UniqueDataFlow<E, T, T> {
		protected UniqueDataFlowWrapper(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent) {
			super(source, parent, parent.getTargetType());
		}

		@Override
		public UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new UniqueDataFlowWrapper<>(getSource(), getParent().filter(filter));
		}

		@Override
		public UniqueDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return new UniqueDataFlowWrapper<>(getSource(), getParent().filterStatic(filter));
		}

		@Override
		public <X> UniqueDataFlow<E, T, T> whereContained(ObservableCollection<X> other, boolean include) {
			return new UniqueDataFlowWrapper<>(getSource(), getParent().whereContained(other, include));
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
		public UniqueDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), getParent().refresh(refresh));
		}

		@Override
		public UniqueDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), getParent().refreshEach(refresh));
		}

		@Override
		public UniqueDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options) {
			ModFilterBuilder<T> filter = new ModFilterBuilder<>();
			options.accept(filter);
			return new UniqueModFilteredOp<>(getSource(), this, new ModFilterer<>(filter));
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

		protected UniqueOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, boolean alwaysUseFirst,
			boolean preserveSourceOrder) {
			super(source, parent);
			isAlwaysUsingFirst = alwaysUseFirst;
			isPreservingSourceOrder = preserveSourceOrder;
		}

		@Override
		public boolean supportsPassive() {
			return false;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new UniqueManager<E, T>(getParent().manageActive(), isAlwaysUsingFirst, isPreservingSourceOrder);
		}
	}

	public static class UniqueMapOp<E, I, T> extends ObservableCollectionDataFlowImpl.MapOp<E, I, T> implements UniqueDataFlow<E, I, T> {
		public UniqueMapOp(ObservableCollection<E> source, UniqueDataFlow<E, ?, I> parent, TypeToken<T> target,
			Function<? super I, ? extends T> map, MapDef<I, T> options) {
			super(source, parent, target, map, options);
		}

		@Override
		public UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filter(filter));
		}

		@Override
		public UniqueDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filterStatic(filter));
		}

		@Override
		public <X> UniqueDataFlow<E, T, T> whereContained(ObservableCollection<X> other, boolean include) {
			return new UniqueDataFlowWrapper<>(getSource(), super.whereContained(other, include));
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
			return new UniqueDataFlowWrapper<>(getSource(), super.refresh(refresh));
		}

		@Override
		public UniqueDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), super.refreshEach(refresh));
		}

		@Override
		public UniqueDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options) {
			ModFilterBuilder<T> filter = new ModFilterBuilder<>();
			options.accept(filter);
			return new UniqueModFilteredOp<>(getSource(), this, new ModFilterer<>(filter));
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

	public static class UniqueModFilteredOp<E, T> extends ObservableCollectionDataFlowImpl.ModFilteredOp<E, T>
	implements UniqueDataFlow<E, T, T> {
		public UniqueModFilteredOp(ObservableCollection<E> source, UniqueDataFlow<E, ?, T> parent, ModFilterer<T> options) {
			super(source, parent, options);
		}

		@Override
		public UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filter(filter));
		}

		@Override
		public UniqueDataFlow<E, T, T> filterStatic(Function<? super T, String> filter) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filterStatic(filter));
		}

		@Override
		public <X> UniqueDataFlow<E, T, X> mapEquivalent(TypeToken<X> target, Function<? super T, ? extends X> map,
			Function<? super X, ? extends T> reverse, Consumer<MapOptions<T, X>> options) {
			MapOptions<T, X> mapOptions = new MapOptions<>();
			options.accept(mapOptions);
			return new UniqueMapOp<>(getSource(), this, target, map, new MapDef<>(mapOptions));
		}

		@Override
		public <X> UniqueDataFlow<E, T, T> whereContained(ObservableCollection<X> other, boolean include) {
			return new UniqueDataFlowWrapper<>(getSource(), super.whereContained(other, include));
		}

		@Override
		public UniqueDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), super.refresh(refresh));
		}

		@Override
		public UniqueDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), super.refreshEach(refresh));
		}

		@Override
		public UniqueDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options) {
			ModFilterBuilder<T> filter = new ModFilterBuilder<>();
			options.accept(filter);
			return new UniqueModFilteredOp<>(getSource(), this, new ModFilterer<>(filter));
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
		// private final BetterMap<T, BetterTreeSet<UniqueElement>> theElementsByValue;
		private final BetterMap<T, UniqueElement2> theElementsByValue;
		private final boolean isAlwaysUsingFirst;
		private final boolean isPreservingSourceOrder;
		private ElementAccepter<T> theAccepter;

		protected UniqueManager(ActiveCollectionManager<E, ?, T> parent, boolean alwaysUseFirst, boolean preserveSourceOrder) {
			theParent = parent;
			theElementsByValue = parent.equivalence().createMap();
			isAlwaysUsingFirst = alwaysUseFirst;
			isPreservingSourceOrder = preserveSourceOrder;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theParent.lock(write, structural, cause);
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			UniqueElement2 element = theElementsByValue.get(value);
			if (element == null)
				return el -> -1;
				return element;
		}

		@Override
		public String canAdd(T toAdd) {
			if (theElementsByValue.containsKey(toAdd))
				return StdMsg.ELEMENT_EXISTS;
			return theParent.canAdd(toAdd);
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			if (theElementsByValue.containsKey(value))
				return null;
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, first);
			if (parentEl == null)
				return null;
			// Look the element up.
			// If it's not there, here I'm returning null, implying that the element was not added to the unique set
			// This would probably be a bug though.
			return theElementsByValue.get(value);
		}

		@Override
		public boolean clear() {
			return theParent.clear();
		}

		@Override
		public void begin(ElementAccepter<T> onElement, WeakListening listening) {
			theAccepter = onElement;
			theParent.begin((parentEl, cause) -> {
				T value = parentEl.get();
				theElementsByValue.computeIfAbsent(value, v -> new UniqueElement2(v)).addParent(parentEl, cause);
			}, listening);
		}

		class UniqueElement2 implements DerivedCollectionElement<T> {
			private final ElementId theValueId;
			private final BetterTreeSet<DerivedCollectionElement<T>> theParentElements;
			private DerivedCollectionElement<T> theActiveElement;
			private CollectionElementListener<T> theListener;

			UniqueElement2(T value) {
				theValueId = theElementsByValue.putEntry(value, this).getElementId();
				theParentElements = new BetterTreeSet<>(false, DerivedCollectionElement::compareTo);
			}

			void addParent(DerivedCollectionElement<T> parentEl, Object cause) {
				boolean only = theParentElements.isEmpty();
				BinaryTreeNode<DerivedCollectionElement<T>> node = theParentElements.addElement(parentEl, false); // Order is ignored
				if (only) {
					// The parent is the first representing this element
					theActiveElement = parentEl;
					theAccepter.accept(this, cause);
				} else if (isAlwaysUsingFirst && node.getChild(true) == null) {
					// The new element takes precedence over the current one
					T oldValue = theActiveElement.get();
					T newActiveValue = parentEl.get();
					theActiveElement = parentEl;
					if (oldValue != newActiveValue)
						ObservableCollectionDataFlowImpl.update(theListener, oldValue, newActiveValue, cause);
				}

				parentEl.setListener(new CollectionElementListener<T>() {
					@Override
					public void update(T oldValue, T newValue, Object innerCause) {
						UniqueElement2 ue = theElementsByValue.computeIfAbsent(newValue, v -> new UniqueElement2(v));
						if (ue == UniqueElement2.this) {
							if (theActiveElement == parentEl)
								ObservableCollectionDataFlowImpl.update(theListener, oldValue, newValue, innerCause);
						} else {
							theParentElements.mutableElement(node.getElementId()).remove();
							if (theParentElements.isEmpty()) {
								// This element is no longer represented
								theElementsByValue.mutableEntry(theValueId).remove();
								ObservableCollectionDataFlowImpl.removed(theListener, oldValue, innerCause);
							} else if (theActiveElement == parentEl) {
								theActiveElement = theParentElements.first();
								T newActiveValue = theActiveElement.get();
								if (oldValue != newActiveValue)
									ObservableCollectionDataFlowImpl.update(theListener, oldValue, newActiveValue, innerCause);
							}
							// The new UniqueElement will call setListener and we won't get its events anymore
							ue.addParent(parentEl, innerCause);
						}
					}

					@Override
					public void removed(T value, Object innerCause) {
						if (theParentElements.isEmpty()) {
							// This element is no longer represented
							theElementsByValue.mutableEntry(theValueId).remove();
							ObservableCollectionDataFlowImpl.removed(theListener, value, innerCause);
						} else if (theActiveElement == parentEl) {
							theActiveElement = theParentElements.first();
							T newActiveValue = theActiveElement.get();
							if (value != newActiveValue)
								ObservableCollectionDataFlowImpl.update(theListener, value, newActiveValue, innerCause);
						}
					}
				});
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				if (isPreservingSourceOrder)
					return theActiveElement.compareTo(((UniqueElement2) o).theActiveElement);
				else
					return theValueId.compareTo(((UniqueElement2) o).theValueId);
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
				for (DerivedCollectionElement<T> el : theParentElements) {
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
				UniqueElement2 ue = theElementsByValue.get(value);
				if (ue != null && ue != this) {
					// If the value already exists, then replacing the underlying values would just remove the unique element
					return StdMsg.ELEMENT_EXISTS;
				}
				for (DerivedCollectionElement<T> el : theParentElements) {
					msg = el.isAcceptable(value);
					if (msg != null)
						return msg;
				}
				return null;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				// A replacement operation involves replacing the values for each element composing this unique element
				String msg = isEnabled();
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				UniqueElement2 ue = theElementsByValue.get(value);
				if (ue != null && ue != this) {
					// If the value already exists, then replacing the underlying values would just remove the unique element
					throw new IllegalArgumentException(StdMsg.ELEMENT_EXISTS);
				}
				for (DerivedCollectionElement<T> el : theParentElements) {
					msg = el.isAcceptable(value);
					if (msg != null)
						throw new IllegalArgumentException(msg);
				}
				// Make a copy since theValueElements will be modified with each set
				List<DerivedCollectionElement<T>> elCopies = new ArrayList<>(theParentElements);
				for (DerivedCollectionElement<T> el : elCopies)
					el.set(value);
			}

			@Override
			public String canRemove() {
				// A removal operation involves removing each element composing this unique element
				for (DerivedCollectionElement<T> el : theParentElements) {
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
				List<DerivedCollectionElement<T>> elCopies = new ArrayList<>(theParentElements);
				for (DerivedCollectionElement<T> el : elCopies) {
					el.remove();
				}
			}

			@Override
			public String canAdd(T value, boolean before) {
				if (!isPreservingSourceOrder)
					return StdMsg.UNSUPPORTED_OPERATION;
				if (theElementsByValue.containsKey(value))
					return StdMsg.ELEMENT_EXISTS;
				return theActiveElement.canAdd(value, before);
			}

			@Override
			public DerivedCollectionElement<T> add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				if (!isPreservingSourceOrder)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				if (theElementsByValue.containsKey(value))
					return null;
				DerivedCollectionElement<T> parentEl = theActiveElement.add(value, before);
				if (parentEl == null)
					return null;
				// Look the element up.
				// If it's not there, here I'm returning null, implying that the element was not added to the unique set
				// This would probably be a bug though.
				return theElementsByValue.get(value);
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
		public UniqueDataFlow<E, E, E> filter(Function<? super E, String> filter) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filter(filter));
		}

		@Override
		public UniqueDataFlow<E, E, E> filterStatic(Function<? super E, String> filter) {
			return new UniqueDataFlowWrapper<>(getSource(), super.filterStatic(filter));
		}

		@Override
		public <X> UniqueDataFlow<E, E, E> whereContained(ObservableCollection<X> other, boolean include) {
			return new UniqueDataFlowWrapper<>(getSource(), super.whereContained(other, include));
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
			return new UniqueDataFlowWrapper<>(getSource(), super.refresh(refresh));
		}

		@Override
		public UniqueDataFlow<E, E, E> refreshEach(Function<? super E, ? extends Observable<?>> refresh) {
			return new UniqueDataFlowWrapper<>(getSource(), super.refreshEach(refresh));
		}

		@Override
		public UniqueDataFlow<E, E, E> filterMod(Consumer<ModFilterBuilder<E>> options) {
			ModFilterBuilder<E> filter = new ModFilterBuilder<>();
			options.accept(filter);
			return new UniqueModFilteredOp<>(getSource(), this, new ModFilterer<>(filter));
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
	}

	public static class ActiveDerivedSet<E, T> extends ActiveDerivedCollection<E, T> implements ObservableSet<T> {
		protected ActiveDerivedSet(ActiveCollectionManager<E, ?, T> flow, Observable<?> until) {
			super(flow, until);
		}
	}

	/**
	 * Implements {@link ObservableSet#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class FlattenedValueSet<E> extends FlattenedValueCollection<E> implements ObservableSet<E> {
		/** @param collectionObservable The value containing a set to flatten */
		protected FlattenedValueSet(ObservableValue<? extends ObservableSet<E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected ObservableValue<? extends ObservableSet<E>> getWrapped() {
			return (ObservableValue<? extends ObservableSet<E>>) super.getWrapped();
		}
	}
}
