package org.observe.collect;

import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.ElementSetter;
import org.observe.collect.ObservableCollection.UniqueDataFlow;
import org.observe.collect.ObservableCollection.UniqueMappedCollectionBuilder;
import org.observe.collect.ObservableCollection.UniqueModFilterBuilder;
import org.observe.collect.ObservableCollectionDataFlowImpl.BaseCollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionManager;
import org.observe.collect.ObservableCollectionImpl.DerivedCollection;
import org.observe.collect.ObservableCollectionImpl.DerivedLWCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedValueCollection;
import org.observe.collect.ObservableCollectionImpl.ReversedObservableCollection;
import org.qommons.collect.BetterMap;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
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

	public static interface UniqueElementFinder<T> {
		ElementId getUniqueElement(T value);
	}

	public static class UniqueDataFlowWrapper<E, T> extends ObservableCollectionDataFlowImpl.AbstractDataFlow<E, T, T> implements UniqueDataFlow<E, T, T> {
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
		public <X> UniqueMappedCollectionBuilder<E, T, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueMappedCollectionBuilder<>(getSource(), this, target);
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
		public UniqueModFilterBuilder<E, T> filterModification() {
			return new UniqueModFilterBuilder<>(getSource(), this);
		}

		@Override
		public boolean isLightWeight() {
			return false;
		}

		@Override
		public ObservableCollectionDataFlowImpl.CollectionManager<E, ?, T> manageCollection() {
			return getParent().manageCollection();
		}

		@Override
		public ObservableSet<T> collectLW() {
			if (!isLightWeight())
				throw new IllegalStateException("This data flow is not light-weight");
			return new DerivedLWSet<>((ObservableSet<E>) getSource(), manageCollection());
		}

		@Override
		public ObservableSet<T> collect(Observable<?> until) {
			return new DerivedSet<>(getSource(), manageCollection(), until);
		}
	}

	public static class UniqueOp<E, T> extends UniqueDataFlowWrapper<E, T> {
		private final boolean isAlwaysUsingFirst;

		protected UniqueOp(ObservableCollection<E> source, ObservableCollectionDataFlowImpl.AbstractDataFlow<E, ?, T> parent, boolean alwaysUseFirst) {
			super(source, parent);
			isAlwaysUsingFirst = alwaysUseFirst;
		}

		@Override
		public ObservableCollectionDataFlowImpl.CollectionManager<E, ?, T> manageCollection() {
			return new UniqueManager<>(getParent().manageCollection(), isAlwaysUsingFirst);
		}
	}

	public static class UniqueMapOp<E, I, T> extends ObservableCollectionDataFlowImpl.MapOp<E, I, T> implements UniqueDataFlow<E, I, T> {
		protected UniqueMapOp(ObservableCollection<E> source, ObservableCollectionDataFlowImpl.AbstractDataFlow<E, ?, I> parent, TypeToken<T> target,
			Function<? super I, ? extends T> map, Function<? super T, ? extends I> reverse,
			ElementSetter<? super I, ? super T> elementReverse, boolean reEvalOnUpdate, boolean fireIfUnchanged, boolean isCached) {
			super(source, parent, target, map, reverse, elementReverse, reEvalOnUpdate, fireIfUnchanged, isCached);
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
		public <X> UniqueMappedCollectionBuilder<E, T, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueMappedCollectionBuilder<>(getSource(), this, target);
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
		public UniqueModFilterBuilder<E, T> filterModification() {
			return new UniqueModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableSet<T> collectLW() {
			if (!isLightWeight())
				throw new IllegalStateException("This data flow is not light-weight");
			return new DerivedLWSet<>((ObservableSet<E>) getSource(), manageCollection());
		}

		@Override
		public ObservableSet<T> collect(Observable<?> until) {
			return new DerivedSet<>(getSource(), manageCollection(), until);
		}
	}

	public static class UniqueModFilteredOp<E, T> extends ObservableCollectionDataFlowImpl.ModFilteredOp<E, T> implements UniqueDataFlow<E, T, T> {
		public UniqueModFilteredOp(ObservableCollection<E> source, ObservableCollectionDataFlowImpl.AbstractDataFlow<E, ?, T> parent, String immutableMsg,
			boolean allowUpdates, String addMsg, String removeMsg, Function<? super T, String> addMsgFn,
			Function<? super T, String> removeMsgFn) {
			super(source, parent, immutableMsg, allowUpdates, addMsg, removeMsg, addMsgFn, removeMsgFn);
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
		public <X> UniqueMappedCollectionBuilder<E, T, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueMappedCollectionBuilder<>(getSource(), this, target);
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
		public UniqueModFilterBuilder<E, T> filterModification() {
			return new UniqueModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableSet<T> collectLW() {
			if (!isLightWeight())
				throw new IllegalStateException("This data flow is not light-weight");
			return new DerivedLWSet<>((ObservableSet<E>) getSource(), manageCollection());
		}

		@Override
		public ObservableSet<T> collect(Observable<?> until) {
			return new DerivedSet<>(getSource(), manageCollection(), until);
		}
	}

	public static class UniqueManager<E, T> extends ObservableCollectionDataFlowImpl.AbstractCollectionManager<E, T, T> {
		private final BetterMap<T, BetterTreeSet<UniqueElement>> theElementsByValue;
		private final boolean isAlwaysUsingFirst;

		protected UniqueManager(ObservableCollectionDataFlowImpl.CollectionManager<E, ?, T> parent, boolean alwaysUseFirst) {
			super(parent, parent.getTargetType());
			theElementsByValue = parent.equivalence().createMap();
			isAlwaysUsingFirst = alwaysUseFirst;
		}

		@Override
		public UniqueElementFinder<T> getElementFinder() {
			return this::getUniqueElement;
		}

		public ElementId getUniqueElement(T value) {
			BetterTreeSet<UniqueElement> valueElements = theElementsByValue.get(value);
			if (valueElements == null)
				return null;
			for (UniqueElement el : valueElements)
				if (el.isPresent)
					return el.getElementId();
			// Although this state is incorrect, since one value should be present, this state is reachable temporarily during
			// modifications. It represents a state where the present element for the value has been removed or deactivated
			// and the new one has not yet been installed. So according to what the derived collection knows, the element doesn't exist.
			return null;
		}

		@Override
		public Comparator<? super T> comparator() {
			return getParent().comparator();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return getParent().equivalence();
		}

		@Override
		public boolean isDynamicallyFiltered() {
			return true;
		}

		@Override
		public ObservableCollectionDataFlowImpl.FilterMapResult<T, T> mapTop(
			ObservableCollectionDataFlowImpl.FilterMapResult<T, T> source) {
			source.result = source.source;
			return source;
		}

		@Override
		public ObservableCollectionDataFlowImpl.FilterMapResult<T, T> reverseTop(
			ObservableCollectionDataFlowImpl.FilterMapResult<T, T> dest) {
			dest.result = dest.source;
			return dest;
		}

		@Override
		public ObservableCollectionDataFlowImpl.FilterMapResult<T, T> canAddTop(
			ObservableCollectionDataFlowImpl.FilterMapResult<T, T> toAdd) {
			if (theElementsByValue.containsKey(toAdd.source))
				toAdd.error = MutableCollectionElement.StdMsg.ELEMENT_EXISTS;
			else
				toAdd.result = toAdd.source;
			return toAdd;
		}

		@Override
		protected void begin(Observable<?> until) {}

		@Override
		public ObservableCollectionDataFlowImpl.CollectionElementManager<E, ?, T> createElement(ElementId id, E init, Object cause) {
			return new UniqueElement(id, init, cause);
		}

		void update(UniqueElement element, Object cause) {
			getUpdateListener().accept(new ObservableCollectionDataFlowImpl.CollectionUpdate(this, element.getElementId(), cause));
		}

		class UniqueElement extends ObservableCollectionDataFlowImpl.CollectionElementManager<E, T, T> {
			private T theValue;
			private boolean isPresent;
			private BetterTreeSet<UniqueElement> theValueElements;
			private BinaryTreeNode<UniqueElement> theNode;

			protected UniqueElement(ElementId id, E init, Object cause) {
				super(UniqueManager.this, UniqueManager.this.getParent().createElement(id, init, cause), id, init, cause);

				T value = getParent().get();
				theValue = value;
				addToSet(cause);
			}

			@Override
			public boolean isPresent() {
				return isPresent;
			}

			@Override
			public T get() {
				return theValue;
			}

			@Override
			protected void init(T source, Object cause) {
				theValue = source;
				addToSet(cause);
			}

			@Override
			protected boolean refresh(T source, Object cause) {
				if (theElementsByValue.get(source) != theValueElements) {
					removeFromSet(cause);
					theValue = source;
					addToSet(cause);
				}
				return true;
			}

			@Override
			public void removed(Object cause) {
				super.removed(cause);
				removeFromSet(cause);
			}

			private void removeFromSet(Object cause) {
				theValueElements.forMutableElement(//
					theNode.getElementId(), el -> el.remove());
				theNode = null;
				if (theValueElements.isEmpty())
					theElementsByValue.remove(theValue);
				else {
					if (isPresent) {
						// We're the first value
						isPresent = false;
						UniqueElement newFirst = theValueElements.first();
						// Delay installing the new value this node has been reported removed so the set is always unique
						postChange(() -> {
							newFirst.isPresent = true;
							UniqueManager.this.update(newFirst, cause);
						});
					}
					theValueElements = null; // Other elements are using that set, so we can't re-use it
				}
			}

			private void addToSet(Object cause) {
				theValueElements = theElementsByValue.computeIfAbsent(theValue, v -> new BetterTreeSet<>(false, UniqueElement::compareTo));
				// Grab our node, since we can use it to remove even if the comparison properties change
				theNode = theValueElements.addElement(this, false);
				if (theValueElements.size() == 1) {
					// We're currently the only element for the value
					isPresent = true;
				} else if (isAlwaysUsingFirst && theNode.getNodesBefore() == 0) {
					isPresent = true;
					// We're replacing the existing representative for the value
					UniqueElement replaced = theValueElements.higher(this);
					// Remove the replaced node before this one is installed so the set is always unique
					replaced.isPresent = false;
					UniqueManager.this.update(replaced, cause);
				} else {
					// There are other elements for the value that we will not replace
					isPresent = false;
				}
			}

			@Override
			protected String filterRemove(boolean isRemoving) {
				String msg = super.filterRemove(isRemoving);
				if (isRemoving && msg == null) {
					// The remove operation for this element needs to remove not only the source element mapping to to this element,
					// but also all other equivalent elements.
					// Remove the other elements first, since they are not present in the derived collection and won't result in events
					// for the collection if they're removed while this element is still present.
					UniqueElement[] elements = theValueElements.toArray(new UniqueManager.UniqueElement[theValueElements.size()]);
					for (UniqueElement other : elements) {
						if (other == this)
							continue;
						getUpdateListener().accept(
							new ObservableCollectionDataFlowImpl.RemoveElementUpdate(UniqueManager.this, other.getElementId(), null));
					}
				}
				return msg;
			}

			@Override
			protected ObservableCollectionDataFlowImpl.FilterMapResult<T, E> filterAccept(
				ObservableCollectionDataFlowImpl.FilterMapResult<T, E> value, boolean isReplacing) {
				if (value.source != theValue && !equivalence().elementEquals(theValue, value.source)) {
					value.error = "Cannot change equivalence of a unique element";
					return value;
				} else
					return super.filterAccept(value, isReplacing);
			}

			@Override
			public ObservableCollectionDataFlowImpl.ElementUpdateResult update(ObservableCollectionDataFlowImpl.CollectionUpdate update,
				Consumer<Consumer<MutableCollectionElement<? extends E>>> sourceElement) {
				if (update instanceof ObservableCollectionDataFlowImpl.RemoveElementUpdate && applies(update)) {
					sourceElement.accept(el -> el.remove());
					return ObservableCollectionDataFlowImpl.ElementUpdateResult.AppliedNoUpdate; // We're removed now, so obviously don't
					// update
				} else
					return super.update(update, sourceElement);
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
		public <X> UniqueMappedCollectionBuilder<E, E, X> mapEquivalent(TypeToken<X> target) {
			return new UniqueMappedCollectionBuilder<>(getSource(), this, target);
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
		public UniqueModFilterBuilder<E, E> filterModification() {
			return new UniqueModFilterBuilder<>(getSource(), this);
		}

		@Override
		public ObservableSet<E> collectLW() {
			return getSource();
		}

		@Override
		public ObservableSet<E> collect() {
			return (ObservableSet<E>) super.collect();
		}

		@Override
		public ObservableSet<E> collect(Observable<?> until) {
			if (until == Observable.empty)
				return getSource();
			else
				return new DerivedSet<>(getSource(), manageCollection(), until);
		}
	}

	public static class DerivedLWSet<E, T> extends DerivedLWCollection<E, T> implements ObservableSet<T> {
		/**
		 * @param source The source set. The unique operation is not light-weight, so the input must be a set
		 * @param flow The data flow used to create the modified collection
		 */
		public DerivedLWSet(ObservableSet<E> source, CollectionManager<E, ?, T> flow) {
			super(source, flow);
		}

		@Override
		protected ObservableSet<E> getSource() {
			return (ObservableSet<E>) super.getSource();
		}
	}

	public static class DerivedSet<E, T> extends DerivedCollection<E, T> implements ObservableSet<T> {
		public DerivedSet(ObservableCollection<E> source, CollectionManager<E, ?, T> flow, Observable<?> until) {
			super(source, flow, until);
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
