package org.observe.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.Equivalence;
import org.observe.ObservableValueEvent;
import org.observe.Transformation;
import org.observe.collect.ObservableCollectionDataFlowImpl.AbstractMappingManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionOperation;
import org.observe.collect.ObservableCollectionDataFlowImpl.FlowElementSetter;
import org.observe.collect.ObservableCollectionDataFlowImpl.RepairListener;
import org.observe.collect.ObservableCollectionImpl.ActiveDerivedCollection;
import org.observe.util.WeakListening;
import org.qommons.BiTuple;
import org.qommons.Identifiable;
import org.qommons.QommonsUtils;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.ValueStoredCollection;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.BinaryTreeNode;

import com.google.common.reflect.TypeToken;

/** Contains some implementations of {@link ActiveCollectionManager} and its dependencies */
public class ObservableCollectionActiveManagers {
	private ObservableCollectionActiveManagers() {}

	/**
	 * A collection element structure in an actively-derived collection
	 *
	 * @param <E> The type of the element
	 * @see ActiveCollectionManager
	 * @see ActiveDerivedCollection
	 */
	public static interface DerivedCollectionElement<E> extends Comparable<DerivedCollectionElement<E>> {
		/**
		 * Installs a listener in this element. To uninstall the listener, re-invoke this method with null.
		 *
		 * @param listener The listener for this element to notify of changes
		 */
		void setListener(CollectionElementListener<E> listener);

		/** @return The current value of this element */
		E get();

		/**
		 * @return null if the {@link #set(Object) set} operation may succeed on this element for any possible argument, or a message why it
		 *         would not
		 */
		String isEnabled();

		/**
		 * @param value The value to test
		 * @return null if the {@link #set(Object) set} operation would succeed on this element for the given argument, or a message why it
		 *         would not
		 */
		String isAcceptable(E value);

		/**
		 * @param value The value to set for this element
		 * @throws UnsupportedOperationException If the given operation does not succeed for a reason that does not depend on the argument
		 * @throws IllegalArgumentException If the given operation does not succeed due to some property of the argument
		 */
		void set(E value) throws UnsupportedOperationException, IllegalArgumentException;

		/**
		 * @return null if the {@link #remove() remove} operation would succeed on this element with its current value, or a message why it
		 *         would not
		 */
		String canRemove();

		/**
		 * Removes this element
		 *
		 * @throws UnsupportedOperationException If the operation does not succeed
		 */
		void remove() throws UnsupportedOperationException;

		/** @return An element identical to this, but with its order reversed in the derived collection */
		default DerivedCollectionElement<E> reverse() {
			DerivedCollectionElement<E> outer = this;
			return new DerivedCollectionElement<E>() {
				@Override
				public int compareTo(DerivedCollectionElement<E> o) {
					return -outer.compareTo(o.reverse());
				}

				@Override
				public void setListener(CollectionElementListener<E> listener) {
					outer.setListener(listener);
				}

				@Override
				public E get() {
					return outer.get();
				}

				@Override
				public String isEnabled() {
					return outer.isEnabled();
				}

				@Override
				public String isAcceptable(E value) {
					return outer.isAcceptable(value);
				}

				@Override
				public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
					outer.set(value);
				}

				@Override
				public String canRemove() {
					return outer.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					outer.remove();
				}

				@Override
				public DerivedCollectionElement<E> reverse() {
					return outer;
				}

				@Override
				public String toString() {
					return outer.toString();
				}
			};
		}

		/**
		 * @param el The element to reverse
		 * @return The reversed element, or null if the element was null
		 */
		public static <E> DerivedCollectionElement<E> reverse(DerivedCollectionElement<E> el) {
			return el == null ? null : el.reverse();
		}
	}

	/**
	 * A manager for a {@link ActiveDerivedCollection actively-}derived collection
	 *
	 * @param <E> The type of the source collection
	 * @param <I> An intermediate type
	 * @param <T> The type of the derived collection that this manager can power
	 */
	public static interface ActiveCollectionManager<E, I, T> extends CollectionOperation<E, I, T> {
		/**
		 * @param value The value to find
		 * @return A finder that can navigate a sorted set of elements by collection order to find an element with the given value, or null
		 *         if this manager does not possess information that could allow a faster-than-linear search
		 */
		Comparable<DerivedCollectionElement<T>> getElementFinder(T value);

		/**
		 * @param sourceEl The source element to find
		 * @param sourceCollection The collection, potentially a source parent or ancestor of this collection, that the given source element
		 *        is from
		 * @return The elements in this flow influenced by the given element from a source
		 */
		BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection);

		/**
		 * @param localElement The element in this flow
		 * @param sourceCollection The source collection to get source elements from
		 * @return The elements in the given collection that influence the given local element
		 */
		BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement,
			BetterCollection<?> sourceCollection);

		/**
		 * Satisfies {@link BetterCollection#getEquivalentElement(ElementId)} for actively-derived collection elements
		 *
		 * @param flowEl An element from a different collection flow
		 * @return The element in this flow equivalent to the given element, or null if no such element exists
		 */
		DerivedCollectionElement<T> getEquivalentElement(DerivedCollectionElement<?> flowEl);

		/**
		 * @param toAdd The value to add
		 * @param after The element to insert the value after (or null for no lower bound)
		 * @param before The element to insert the value before (or null if no upper bound)
		 * @return null If such an addition is allowable, or a message why it is not
		 */
		String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before);

		/**
		 * @param value The value to add
		 * @param after The element to insert the value after (or null for no lower bound)
		 * @param before The element to insert the value before (or null if no upper bound)
		 * @param first Whether to prefer inserting the value toward the beginning of the designated range or toward the end
		 * @return The element that was added
		 * @throws UnsupportedOperationException If the operation is not allowed, regardless of any of the arguments
		 * @throws IllegalArgumentException If the operation is not allowed due to one or more of the argument values
		 */
		DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first)
				throws UnsupportedOperationException, IllegalArgumentException;

		/**
		 * @param valueEl The element to move
		 * @param after The lower bound of the range to move the element to (or null to leave the range lower-unbounded)
		 * @param before The upper bound of the range to move the element to (or null to leave the range upper-unbounded)
		 * @return null If the move can be made, or a reason otherwise
		 */
		String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before);

		/**
		 * @param valueEl The element to move
		 * @param after The lower bound of the range to move the element to (or null to leave the range lower-unbounded)
		 * @param before The upper bound of the range to move the element to (or null to leave the range upper-unbounded)
		 * @param first Whether to try to move the element toward the beginning (true) or end (false) of the range
		 * @param afterRemove A callback to call after the element is removed from its original place and before it is added in its new
		 *        position
		 * @return The moved element
		 */
		DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove);

		/**
		 * Removes all elements in this manager, if possible
		 *
		 * @return Whether this method removed all elements. If false, the derived collection may need to remove elements itself,
		 *         one-by-one.
		 */
		boolean clear();

		/**
		 * @param elements The elements to modify
		 * @param newValue The value for the elements
		 * @throws UnsupportedOperationException If the operation fails regardless of the argument value
		 * @throws IllegalArgumentException If the operation fails due to the value of the argument
		 */
		void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException;

		/**
		 * Begins listening to this manager's elements
		 *
		 * @param fromStart Whether to initialize the derived collection using the elements from the beginning or end of the source
		 *        collection
		 * @param onElement The listener to accept initial and added elements for the collection
		 * @param listening The weakly-listening structure that contains the derived collection's listening chains
		 */
		void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening);
	}

	/**
	 * An {@link ActiveCollectionManager} that will produce an observable {@link org.qommons.collect.ValueStoredCollection}
	 *
	 * @param <E> The type of the source collection
	 * @param <I> An intermediate type
	 * @param <T> The type of the derived collection that this manager can power
	 */
	public static interface ActiveValueStoredManager<E, I, T> extends ActiveCollectionManager<E, I, T> {
		/**
		 * @param element The element to check the structure's consistency at
		 * @return Whether the collection's storage appears to be consistent at the given element
		 * @see ValueStoredCollection#isConsistent(ElementId)
		 */
		boolean isConsistent(DerivedCollectionElement<T> element);

		/**
		 * @return Whether the collection's storage is consistent
		 * @see ValueStoredCollection#checkConsistency()
		 */
		boolean checkConsistency();

		/**
		 * @param <X> The tracking data type
		 * @param element The element to repair the structure's consistency at
		 * @param listener The listener to monitor repairs. May be null.
		 * @return Whether any inconsistencies were found
		 * @see ValueStoredCollection#repair(ElementId, org.qommons.collect.ValueStoredCollection.RepairListener)
		 */
		<X> boolean repair(DerivedCollectionElement<T> element, RepairListener<T, X> listener);

		/**
		 * @param <X> The tracking data type
		 * @param listener The listener to monitor repairs. May be null.
		 * @return Whether any inconsistencies were found
		 * @see ValueStoredCollection#repair(org.qommons.collect.ValueStoredCollection.RepairListener)
		 */
		<X> boolean repair(RepairListener<T, X> listener);
	}

	/**
	 * Accepts elements in an actively-derived collection
	 *
	 * @param <E> The type of element to accept
	 * @see ActiveCollectionManager
	 * @see ActiveDerivedCollection
	 */
	public static interface ElementAccepter<E> {
		/**
		 * @param element The initial or added element in the collection
		 * @param cause The cause of the addition
		 */
		void accept(DerivedCollectionElement<E> element, Object cause);
	}

	/**
	 * A listener for changes to or removal of an element in an actively-derived collection
	 *
	 * @param <E> The type of the element
	 * @see DerivedCollectionElement
	 * @see ActiveDerivedCollection
	 */
	public static interface CollectionElementListener<E> {
		/**
		 * Alerts the derived collection that the source element's content (value) has been changed or updated
		 *
		 * @param oldValue The element's previous value. This is not guaranteed to be the last <code>newValue</code> from this method or the
		 *        initial value of the element as passed to the {@link ElementAccepter}. Some flows do not keep track of their current
		 *        value, so this value might be a guess. If an element's actual previous value is important to a listener, it must be
		 *        tracked independently.
		 * @param newValue The new (current) value of the element
		 * @param cause The cause of the change
		 */
		void update(E oldValue, E newValue, Object cause);

		/**
		 * Alerts the derived collection that the element has been removed from the source flow
		 *
		 * @param value The element's previous value before it was removed. As with <code>oldValue</code> in the
		 *        {@link #update(Object, Object, Object) update} method, this value is not guaranteed to be the last that the listener knew
		 *        about.
		 * @param cause The cause of the element's removal
		 */
		void removed(E value, Object cause);
	}

	static <T> void update(CollectionElementListener<T> listener, T oldValue, T newValue, Object cause) {
		if (listener != null)
			listener.update(oldValue, newValue, cause);
	}

	static <T> void removed(CollectionElementListener<T> listener, T value, Object cause) {
		if (listener != null)
			listener.removed(value, cause);
	}

	static Transactable structureAffectedPassLockThroughToParent(CollectionOperation<?, ?, ?> parent) {
		return new Transactable() {
			@Override
			public boolean isLockSupported() {
				return parent.isLockSupported();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return structureAffectedPassLockThroughToParent(parent, write, cause);
			}

			@Override
			public Transaction tryLock(boolean write, Object cause) {
				return structureAffectedTryPassLockThroughToParent(parent, write, cause);
			}
		};
	}

	/**
	 * This method is to be used by derived operations that affect the structure of the result. E.g. filtering, sorting, etc. where an
	 * update from the parent collection may cause values to be added, removed, or moved in the derived collection.
	 *
	 * @param parent The parent operation
	 * @param write Whether to obtain a write (exclusive) lock or a read (non-exclusive) lock
	 * @param cause The cause of the lock
	 * @return The parent lock to close to release it
	 * @see Transactable#lock(boolean, Object)
	 */
	public static Transaction structureAffectedPassLockThroughToParent(CollectionOperation<?, ?, ?> parent, boolean write, Object cause) {
		if (write) {
			// If the caller is doing the modifications, we can prevent any potential updates that would affect the child's structure
			// Because a write lock is always exclusive (no external modifications, even updates, may be performed while any write lock
			// is held), we don't need to worry about updates from the parent
			return parent.lock(true, cause);
		} else {
			// Because updates to the parent can affect this derived structure,
			// we need to prevent updates from the parent even when the caller would allow updates
			return parent.lock(false, cause);
		}
	}

	static Transaction structureAffectedTryPassLockThroughToParent(CollectionOperation<?, ?, ?> parent, boolean write, Object cause) {
		if (write) {
			// If the caller is doing the modifications, we can prevent any potential updates that would affect the child's structure
			// Because a write lock is always exclusive (no external modifications, even updates, may be performed while any write lock
			// is held), we don't need to worry about updates from the parent
			return parent.tryLock(true, cause);
		} else {
			// Because updates to the parent can affect this derived structure,
			// we need to prevent updates from the parent even when the caller would allow updates
			return parent.tryLock(false, cause);
		}
	}

	static class BaseCollectionManager<E> implements ActiveCollectionManager<E, E, E> {
		private final ObservableCollection<E> theSource;
		private final BetterTreeMap<ElementId, CollectionElementListener<E>> theElementListeners;

		BaseCollectionManager(ObservableCollection<E> source) {
			theSource = source;
			theElementListeners = new BetterTreeMap<>(false, ElementId::compareTo);
		}

		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theSource.getIdentity(), "activeManager");
		}

		@Override
		public boolean isLockSupported() {
			return theSource.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theSource.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theSource.tryLock(write, cause);
		}

		@Override
		public TypeToken<E> getTargetType() {
			return theSource.getType();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theSource.equivalence();
		}

		@Override
		public boolean isContentControlled() {
			return theSource.isContentControlled();
		}

		@Override
		public Comparable<DerivedCollectionElement<E>> getElementFinder(E value) {
			return null;
		}

		@Override
		public BetterList<DerivedCollectionElement<E>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(theSource.getElementsBySource(sourceEl, sourceCollection),
				el -> new BaseDerivedElement(theSource.mutableElement(el.getElementId())));
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<E> localElement,
			BetterCollection<?> sourceCollection) {
			return theSource.getSourceElements(((BaseDerivedElement) localElement).getElementId(), sourceCollection);
		}

		@Override
		public DerivedCollectionElement<E> getEquivalentElement(DerivedCollectionElement<?> flowEl) {
			if (!(flowEl instanceof BaseCollectionManager.BaseDerivedElement))
				return null;
			BaseDerivedElement other = (BaseDerivedElement) flowEl;
			if (other.getMgr() == this)
				return other;
			ElementId found = theSource.getEquivalentElement(other.getElementId());
			return found == null ? null : new BaseDerivedElement(theSource.mutableElement(found));
		}

		@Override
		public boolean clear() {
			theSource.clear();
			return true;
		}

		@Override
		public String canAdd(E toAdd, DerivedCollectionElement<E> after, DerivedCollectionElement<E> before) {
			return theSource.canAdd(toAdd, //
				strip(after), strip(before));
		}

		@Override
		public DerivedCollectionElement<E> addElement(E value, DerivedCollectionElement<E> after, DerivedCollectionElement<E> before,
			boolean first) {
			CollectionElement<E> srcEl = theSource.addElement(value, //
				strip(after), strip(before), first);
			return srcEl == null ? null : new BaseDerivedElement(theSource.mutableElement(srcEl.getElementId()));
		}

		@Override
		public String canMove(DerivedCollectionElement<E> valueEl, DerivedCollectionElement<E> after, DerivedCollectionElement<E> before) {
			return theSource.canMove(//
				strip(valueEl), strip(after), strip(before));
		}

		@Override
		public DerivedCollectionElement<E> move(DerivedCollectionElement<E> valueEl, DerivedCollectionElement<E> after,
			DerivedCollectionElement<E> before, boolean first, Runnable afterRemove) {
			return new BaseDerivedElement(theSource.mutableElement(theSource.move(//
				strip(valueEl), strip(after), strip(before), first, afterRemove).getElementId()));
		}

		private ElementId strip(DerivedCollectionElement<E> el) {
			return el == null ? null : ((BaseDerivedElement) el).getElementId();
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<E>> elements, E newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			theSource.setValue(//
				elements.stream().map(el -> ((BaseDerivedElement) el).getElementId()).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<E> onElement, WeakListening listening) {
			listening.withConsumer((ObservableCollectionEvent<? extends E> evt) -> {
				switch (evt.getType()) {
				case add:
					BaseDerivedElement el = new BaseDerivedElement(theSource.mutableElement(evt.getElementId()));
					onElement.accept(el, evt);
					break;
				case remove:
					CollectionElementListener<E> listener = theElementListeners.remove(evt.getElementId());
					if (listener != null)
						listener.removed(evt.getOldValue(), evt);
					break;
				case set:
					listener = theElementListeners.get(evt.getElementId());
					if (listener != null)
						listener.update(evt.getOldValue(), evt.getNewValue(), evt);
					break;
				}
			}, action -> theSource.subscribe(action, fromStart).removeAll());
		}

		class BaseDerivedElement implements DerivedCollectionElement<E> {
			private final MutableCollectionElement<E> source;

			BaseDerivedElement(MutableCollectionElement<E> src) {
				source = src;
			}

			BaseCollectionManager<E> getMgr() {
				return BaseCollectionManager.this;
			}

			ElementId getElementId() {
				return source.getElementId();
			}

			@Override
			public int compareTo(DerivedCollectionElement<E> o) {
				return source.getElementId().compareTo(((BaseDerivedElement) o).source.getElementId());
			}

			@Override
			public void setListener(CollectionElementListener<E> listener) {
				theElementListeners.put(source.getElementId(), listener);
			}

			@Override
			public E get() {
				return source.get();
			}

			@Override
			public String isEnabled() {
				return source.isEnabled();
			}

			@Override
			public String isAcceptable(E value) {
				return source.isAcceptable(value);
			}

			@Override
			public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
				source.set(value);
			}

			@Override
			public String canRemove() {
				return source.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				source.remove();
			}

			@Override
			public String toString() {
				return source.getElementId().toString();
			}
		}
	}

	static class ActiveReversedManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;

		ActiveReversedManager(ActiveCollectionManager<E, ?, T> parent) {
			theParent = parent;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "reverse");
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			Equivalence<? super T> equiv = theParent.equivalence();
			if (equiv instanceof Equivalence.SortedEquivalence)
				return ((Equivalence.SortedEquivalence<? super T>) equiv).reverse();
			else
				return equiv;
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
		public boolean isContentControlled() {
			return theParent.isContentControlled();
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			Comparable<DerivedCollectionElement<T>> parentFinder = theParent.getElementFinder(value);
			if (parentFinder == null)
				return null;
			return el -> -parentFinder.compareTo(el.reverse());
		}

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(theParent.getElementsBySource(sourceEl.reverse(), sourceCollection), el -> el.reverse());
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement,
			BetterCollection<?> sourceCollection) {
			return theParent.getSourceElements(localElement.reverse(), sourceCollection);
		}

		@Override
		public DerivedCollectionElement<T> getEquivalentElement(DerivedCollectionElement<?> flowEl) {
			return DerivedCollectionElement.reverse(theParent.getEquivalentElement(flowEl.reverse()));
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			return theParent.canAdd(toAdd, DerivedCollectionElement.reverse(before), DerivedCollectionElement.reverse(after));
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, DerivedCollectionElement.reverse(before),
				DerivedCollectionElement.reverse(after), !first);
			return parentEl == null ? null : parentEl.reverse();
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			return theParent.canMove(DerivedCollectionElement.reverse(valueEl), DerivedCollectionElement.reverse(before),
				DerivedCollectionElement.reverse(after));
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			return theParent.move(DerivedCollectionElement.reverse(valueEl), DerivedCollectionElement.reverse(before),
				DerivedCollectionElement.reverse(after), !first, afterRemove).reverse();
		}

		@Override
		public boolean clear() {
			return theParent.clear();
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			theParent.setValues(//
				elements.stream().map(el -> el.reverse()).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theParent.begin(!fromStart, (el, cause) -> onElement.accept(el.reverse(), cause), listening);
		}
	}

	/**
	 * An {@link ActiveCollectionManager active manager} that sorts its elements by value
	 *
	 * @param <E> The type of the source collection
	 * @param <T> The type of this manager
	 */
	protected static class SortedManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;
		private final Comparator<? super T> theCompare;
		// Need to keep track of the values to enforce the set-does-not-reorder policy
		private final BetterTreeList<BiTuple<T, DerivedCollectionElement<T>>> theValues;
		private final Comparator<BiTuple<T, DerivedCollectionElement<T>>> theTupleCompare;
		private ElementAccepter<T> theAccepter;

		/**
		 * @param parent The parent manager
		 * @param compare The comparator to use to sort the elements
		 */
		protected SortedManager(ActiveCollectionManager<E, ?, T> parent, Comparator<? super T> compare) {
			theParent = parent;
			theCompare = compare;
			theValues = new BetterTreeList<>(false);
			theTupleCompare = (t1, t2) -> {
				int comp = theCompare.compare(t1.getValue1(), t2.getValue1());
				if (comp == 0)
					comp = t1.getValue2().compareTo(t2.getValue2());
				return comp;
			};
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "sorted", theCompare);
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
		public boolean isLockSupported() {
			return theParent.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return structureAffectedPassLockThroughToParent(theParent, write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return structureAffectedTryPassLockThroughToParent(theParent, write, cause);
		}

		@Override
		public boolean clear() {
			return theParent.clear();
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			// Most likely, this manager's equivalence is not the same as its comparison order, so we can't take advantage of the
			// sorting to find the element.
			// And even if the parent could've found it, the order will be mixed up now.
			return null;
		}

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(theParent.getElementsBySource(sourceEl, sourceCollection), el -> new SortedElement(el, true));
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement,
			BetterCollection<?> sourceCollection) {
			return theParent.getSourceElements(((SortedElement) localElement).theParentEl, sourceCollection);
		}

		@Override
		public DerivedCollectionElement<T> getEquivalentElement(DerivedCollectionElement<?> flowEl) {
			if (!(flowEl instanceof SortedManager.SortedElement))
				return null;
			SortedElement other = (SortedElement) flowEl;
			if (other.getMgr() == this)
				return other;
			DerivedCollectionElement<T> found = theParent.getEquivalentElement(other.theParentEl);
			return found == null ? null : new SortedElement(found, true);
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			int afterComp = after == null ? 0 : theCompare.compare(after.get(), toAdd);
			if (afterComp > 0)
				return StdMsg.ILLEGAL_ELEMENT_POSITION;
			int beforeComp = before == null ? 0 : theCompare.compare(before.get(), toAdd);
			if (beforeComp < 0)
				return StdMsg.ILLEGAL_ELEMENT_POSITION;
			DerivedCollectionElement<T> requiredAfter = (after != null && afterComp == 0)
				? ((SortedElement) after).theParentEl : null;
				DerivedCollectionElement<T> requiredBefore = (before != null && beforeComp == 0)
					? ((SortedElement) before).theParentEl : null;
					return theParent.canAdd(toAdd, requiredAfter, requiredBefore);
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			int afterComp = after == null ? 0 : theCompare.compare(after.get(), value);
			if (afterComp > 0)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
			int beforeComp = before == null ? 0 : theCompare.compare(before.get(), value);
			if (beforeComp < 0)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
			DerivedCollectionElement<T> requiredAfter = (after != null && afterComp == 0)
				? ((SortedElement) after).theParentEl : null;
				DerivedCollectionElement<T> requiredBefore = (before != null && beforeComp == 0)
					? ((SortedElement) before).theParentEl : null;
					// Try to add relative to the specified elements if possible,
					// but if such a positional add is unsupported by the parent, we need to ensure
					// that a position-less add will insert the new element in the right spot
					DerivedCollectionElement<T> parentEl;
					if (requiredAfter != null || requiredBefore != null)
						parentEl = theParent.addElement(value, requiredAfter, requiredBefore, first);
					else if (first && after != null && theParent.canAdd(value, ((SortedElement) after).theParentEl, null) == null)
						parentEl = theParent.addElement(value, ((SortedElement) after).theParentEl, null, true);
					else if (!first && before != null && theParent.canAdd(value, null, ((SortedElement) before).theParentEl) == null)
						parentEl = theParent.addElement(value, null, ((SortedElement) before).theParentEl, false);
					else
						parentEl = theParent.addElement(value, null, null, first);
					return parentEl == null ? null : new SortedElement(parentEl, true);
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			int afterComp = after == null ? 0 : theCompare.compare(after.get(), valueEl.get());
			if (afterComp > 0)
				return StdMsg.ILLEGAL_ELEMENT_POSITION;
			int beforeComp = before == null ? 0 : theCompare.compare(before.get(), valueEl.get());
			if (beforeComp < 0)
				return StdMsg.ILLEGAL_ELEMENT_POSITION;
			DerivedCollectionElement<T> requiredAfter = (after != null && afterComp == 0)
				? ((SortedElement) after).theParentEl : null;
				DerivedCollectionElement<T> requiredBefore = (before != null && beforeComp == 0)
					? ((SortedElement) before).theParentEl : null;
					return theParent.canMove(((SortedElement) valueEl).theParentEl, requiredAfter, requiredBefore);
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			int afterComp = after == null ? 0 : theCompare.compare(after.get(), valueEl.get());
			if (afterComp > 0)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
			int beforeComp = before == null ? 0 : theCompare.compare(before.get(), valueEl.get());
			if (beforeComp < 0)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
			DerivedCollectionElement<T> requiredAfter = (after != null && afterComp == 0)
				? ((SortedElement) after).theParentEl : null;
				DerivedCollectionElement<T> requiredBefore = (before != null && beforeComp == 0)
					? ((SortedElement) before).theParentEl : null;
					return new SortedElement(theParent.move(//
						((SortedElement) valueEl).theParentEl, requiredAfter, requiredBefore, first, afterRemove), true);
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			theParent.setValues(elements.stream().map(el -> ((SortedElement) el).theParentEl).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theAccepter = onElement;
			theParent.begin(fromStart, (parentEl, cause) -> onElement.accept(new SortedElement(parentEl, false), cause), listening);
		}

		private BinaryTreeNode<BiTuple<T, DerivedCollectionElement<T>>> insertIntoValues(T value, DerivedCollectionElement<T> parentEl) {
			BiTuple<T, DerivedCollectionElement<T>> tuple = new BiTuple<>(value, parentEl);
			BinaryTreeNode<BiTuple<T, DerivedCollectionElement<T>>> node = theValues.getRoot();
			if (node == null)
				return theValues.addElement(tuple, false);
			else {
				node = node.findClosest(n -> theTupleCompare.compare(tuple, n.get()), true, false, null);
				return theValues.getElement(theValues.mutableNodeFor(node).add(tuple, theTupleCompare.compare(tuple, node.get()) < 0));
			}
		}

		class SortedElement implements DerivedCollectionElement<T> {
			final DerivedCollectionElement<T> theParentEl;
			private BinaryTreeNode<BiTuple<T, DerivedCollectionElement<T>>> theValueNode;
			private CollectionElementListener<T> theListener;

			SortedElement(DerivedCollectionElement<T> parentEl, boolean synthetic) {
				theParentEl = parentEl;
				if (!synthetic) {
					theValueNode = insertIntoValues(parentEl.get(), parentEl);
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause) {
							T realOldValue = theValueNode.get().getValue1();
							if (!isInCorrectOrder(newValue, theParentEl)) {
								// The order of this element has changed
								theValues.mutableNodeFor(theValueNode).remove();
								theValueNode = insertIntoValues(newValue, theParentEl);
								/* We could just do an update here and let the derived collection do the re-order.
								 * But this would potentially be inconsistent in the case of child flows that also affect order.
								 * E.g. the update could be swallowed by the derived flow and the element not reordered,
								 * resulting in just an update in the terminal collection.
								 * But the same flow order, collected in between, would yield different ordering
								 * for the same set of operations.
								 * The use case of such a situation is probably minuscule, since why would anyone apply a sorted flow
								 * and then apply another order-governing flow on top of it.
								 * In such situations the benefit of just firing an update
								 * instead of a remove/add is probably negligible.
								 * So, to summarize, we'll fire an add/remove combo here instead of just an update. */
								ObservableCollectionActiveManagers.removed(theListener, realOldValue, cause);
								theAccepter.accept(SortedElement.this, cause);
							} else {
								theValues.mutableNodeFor(theValueNode).set(new BiTuple<>(newValue, theParentEl));
								ObservableCollectionActiveManagers.update(theListener, realOldValue, newValue, cause);
							}
						}

						@Override
						public void removed(T value, Object cause) {
							T realOldValue = theValueNode.get().getValue1();
							theValues.mutableNodeFor(theValueNode).remove();
							ObservableCollectionActiveManagers.removed(theListener, realOldValue, cause);
						}
					});
				}
			}

			SortedManager<E, T> getMgr() {
				return SortedManager.this;
			}

			boolean isInCorrectOrder(T newValue, DerivedCollectionElement<T> parentEl) {
				BiTuple<T, DerivedCollectionElement<T>> tuple = new BiTuple<>(newValue, parentEl);
				BinaryTreeNode<BiTuple<T, DerivedCollectionElement<T>>> left = theValueNode
					.getClosest(true);
				BinaryTreeNode<BiTuple<T, DerivedCollectionElement<T>>> right = theValueNode
					.getClosest(false);
				return (left == null || theTupleCompare.compare(left.get(), tuple) <= 0)//
					&& (right == null || theTupleCompare.compare(tuple, right.get()) <= 0);
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				SortedElement sorted = (SortedElement) o;
				if (theValueNode != null && sorted.theValueNode != null)
					return theValueNode.compareTo(sorted.theValueNode);
				else { // Synthetic
					BiTuple<T, DerivedCollectionElement<T>> tuple1 = theValueNode != null
						? theValueNode.get() : new BiTuple<>(theParentEl.get(), theParentEl);
						BiTuple<T, DerivedCollectionElement<T>> tuple2 = sorted.theValueNode != null
							? sorted.theValueNode.get() : new BiTuple<>(sorted.theParentEl.get(), sorted.theParentEl);
							return theTupleCompare.compare(tuple1, tuple2);
				}
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public T get() {
				if (theValueNode != null)
					return theValueNode.get().getValue1();
				else
					return theParentEl.get(); // Synthetic
			}

			@Override
			public String isEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				if (theCompare.compare(theValueNode.get().getValue1(), value) != 0 && !isInCorrectOrder(value, theParentEl))
					return StdMsg.ILLEGAL_ELEMENT_POSITION;
				return theParentEl.isAcceptable(value);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				// It is not allowed to change the order of an element via set
				// However, if the order has already been changed (e.g. due to changes in the value or the comparator),
				// it is permitted (and required) to use set to notify the collection of the change
				if (theCompare.compare(theValueNode.get().getValue1(), value) != 0 && !isInCorrectOrder(value, theParentEl))
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				theParentEl.set(value);
				theValues.mutableNodeFor(theValueNode).set(new BiTuple<>(value, theParentEl));
			}

			@Override
			public String canRemove() {
				return theParentEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theParentEl.remove();
			}

			@Override
			public String toString() {
				return theParentEl.toString();
			}
		}
	}

	static class FilteredCollectionManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;
		private final Function<? super T, String> theFilter;
		private ElementAccepter<T> theElementAccepter;

		FilteredCollectionManager(ActiveCollectionManager<E, ?, T> parent, Function<? super T, String> filter) {
			theParent = parent;
			theFilter = filter;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "filter", theFilter);
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
		public boolean isLockSupported() {
			return theParent.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return structureAffectedPassLockThroughToParent(theParent, write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return structureAffectedTryPassLockThroughToParent(theParent, write, cause);
		}

		@Override
		public boolean clear() {
			return false;
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			Comparable<DerivedCollectionElement<T>> parentFinder = theParent.getElementFinder(value);
			if (parentFinder == null)
				return null;
			return el -> parentFinder.compareTo(((FilteredElement) el).theParentEl);
		}

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(theParent.getElementsBySource(sourceEl, sourceCollection), el -> new FilteredElement(el, true, true));
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement,
			BetterCollection<?> sourceCollection) {
			return theParent.getSourceElements(((FilteredElement) localElement).theParentEl, sourceCollection);
		}

		@Override
		public DerivedCollectionElement<T> getEquivalentElement(DerivedCollectionElement<?> flowEl) {
			if (!(flowEl instanceof FilteredCollectionManager.FilteredElement))
				return null;
			FilteredElement other = (FilteredElement) flowEl;
			if (other.getMgr() == this)
				return other;
			DerivedCollectionElement<T> found = theParent.getEquivalentElement(other.theParentEl);
			if (found == null)
				return null;
			return new FilteredElement(found, true, true);
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			String msg = theFilter.apply(toAdd);
			if (msg == null)
				msg = theParent.canAdd(toAdd, //
					strip(after), strip(before));
			return msg;
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			String msg = theFilter.apply(value);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, //
				strip(after), strip(before), first);
			if (parentEl == null)
				return null;
			return new FilteredElement(parentEl, true, true);
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			return theParent.canMove(strip(valueEl), strip(after), strip(before));
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			return new FilteredElement(theParent.move(//
				strip(valueEl), strip(after), strip(before), first, afterRemove), true, true);
		}

		private DerivedCollectionElement<T> strip(DerivedCollectionElement<T> el) {
			return el == null ? null : ((FilteredElement) el).theParentEl;
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			String msg = theFilter.apply(newValue);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			theParent.setValues(elements.stream().map(el -> ((FilteredElement) el).theParentEl).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theElementAccepter = onElement;
			theParent.begin(fromStart, (parentEl, cause) -> {
				String msg = theFilter.apply(parentEl.get());
				if (msg == null)
					onElement.accept(new FilteredElement(parentEl, false, true), cause);
				else
					new FilteredElement(parentEl, false, false);
			}, listening);
		}

		class FilteredElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<T> theParentEl;
			private final boolean isSynthetic;
			private boolean included;
			private CollectionElementListener<T> theListener;

			FilteredElement(DerivedCollectionElement<T> parentEl, boolean synthetic, boolean included) {
				theParentEl = parentEl;
				isSynthetic = synthetic;
				this.included = included;
				if (!isSynthetic) {
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause) {
							boolean oldIncluded = FilteredElement.this.included;
							boolean newIncluded = theFilter.apply(newValue) == null;
							FilteredElement.this.included = newIncluded;
							if (!oldIncluded && newIncluded) {
								theElementAccepter.accept(FilteredElement.this, cause);
							} else if (oldIncluded && !newIncluded && theListener != null) {
								theListener.removed(oldValue, cause);
								theListener = null;
							} else if (oldIncluded && newIncluded && theListener != null)
								theListener.update(oldValue, newValue, cause);
						}

						@Override
						public void removed(T value, Object cause) {
							if (FilteredElement.this.included && theListener != null) {
								theListener.removed(value, cause);
								theListener = null;
							}
						}
					});
				}
			}

			FilteredCollectionManager<E, T> getMgr() {
				return FilteredCollectionManager.this;
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((FilteredElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public T get() {
				return theParentEl.get();
			}

			@Override
			public String isEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				String msg = theFilter.apply(value);
				if (msg == null)
					msg = theParentEl.isAcceptable(value);
				return msg;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				String msg = theFilter.apply(value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				theParentEl.set(value);
			}

			@Override
			public String canRemove() {
				return theParentEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theParentEl.remove();
			}

			@Override
			public String toString() {
				return theParentEl.toString();
			}
		}
	}

	static class ActiveEquivalenceSwitchedManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;
		private final Equivalence<? super T> theEquivalence;

		ActiveEquivalenceSwitchedManager(ActiveCollectionManager<E, ?, T> parent, Equivalence<? super T> equivalence) {
			theParent = parent;
			theEquivalence = equivalence;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "withEquivalence", theEquivalence);
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
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theParent.tryLock(write, cause);
		}

		@Override
		public boolean clear() {
			return theParent.clear();
		}

		@Override
		public boolean isContentControlled() {
			return theParent.isContentControlled();
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			return null;
		}

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			return theParent.getElementsBySource(sourceEl, sourceCollection);
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement,
			BetterCollection<?> sourceCollection) {
			return theParent.getSourceElements(localElement, sourceCollection);
		}

		@Override
		public DerivedCollectionElement<T> getEquivalentElement(DerivedCollectionElement<?> flowEl) {
			return theParent.getEquivalentElement(flowEl);
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			return theParent.canAdd(toAdd, after, before);
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			return theParent.addElement(value, after, before, first);
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			return theParent.canMove(valueEl, after, before);
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			return theParent.move(valueEl, after, before, first, afterRemove);
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			theParent.setValues(elements, newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theParent.begin(fromStart, onElement, listening);
		}
	}

	static class ActiveTransformedCollectionManager<E, I, T> extends AbstractMappingManager<E, I, T>
	implements ActiveCollectionManager<E, I, T> {
		// Need to keep track of these to update them when the combined values change
		private final BetterSortedSet<TransformedElement> theElements;

		ActiveTransformedCollectionManager(ActiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType, Transformation<I, T> def,
			Equivalence<? super T> equivalence) {
			super(parent, targetType, def, equivalence);

			theElements = new BetterTreeSet<>(false, TransformedElement::compareTo);
		}

		@Override
		protected ActiveCollectionManager<E, ?, I> getParent() {
			return (ActiveCollectionManager<E, ?, I>) super.getParent();
		}

		@Override
		public boolean clear() {
			return getParent().clear();
		}

		protected TransformedElement map(DerivedCollectionElement<I> parentEl, boolean synthetic) {
			return new TransformedElement(parentEl, synthetic);
		}

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(getParent().getElementsBySource(sourceEl, sourceCollection), el -> map(el, true));
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection) {
			return getParent().getSourceElements(((TransformedElement) localElement).getParentEl(), sourceCollection);
		}

		@Override
		public DerivedCollectionElement<T> getEquivalentElement(DerivedCollectionElement<?> flowEl) {
			if (!(flowEl instanceof ActiveTransformedCollectionManager.TransformedElement))
				return null;
			DerivedCollectionElement<I> found = getParent().getEquivalentElement(((TransformedElement) flowEl).getParentEl());
			return found == null ? null : map(found, true);
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			return getParent().canMove(strip(valueEl), strip(after), strip(before));
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			return map(getParent().move(strip(valueEl), strip(after), strip(before), first, afterRemove), true);
		}

		@Override
		protected void doParentMultiSet(Collection<AbstractMappedElement> elements, I newValue) {
			getParent().setValues(elements.stream().map(el -> ((TransformedElement) el).getParentEl()).collect(Collectors.toList()),
				newValue);
		}

		private DerivedCollectionElement<I> strip(DerivedCollectionElement<T> el) {
			return el == null ? null : ((TransformedElement) el).getParentEl();
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			if (!isReversible() || getTransformation().isManyToOne())
				return null;
			Transformation.ReverseQueryResult<I> qr = getEngine().reverse(value, false, true);
			if (qr.getError() != null)
				return null;
			I reversed = qr.getReversed();
			Comparable<DerivedCollectionElement<I>> pef = getParent().getElementFinder(reversed);
			if (pef == null)
				return null;
			return el -> pef.compareTo(((TransformedElement) el).getParentEl());
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			if (!isReversible())
				return StdMsg.UNSUPPORTED_OPERATION;
			Transformation.ReverseQueryResult<I> qr = getEngine().reverse(toAdd, true, true);
			if (qr.getError() != null)
				return qr.getError();
			I reversed = qr.getReversed();
			return getParent().canAdd(reversed, strip(after), strip(before));
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			if (!isReversible())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			Transformation.ReverseQueryResult<I> qr = getEngine().reverse(value, true, false);
			if (StdMsg.UNSUPPORTED_OPERATION.equals(qr.getError()))
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else if (qr.getError() != null)
				throw new IllegalArgumentException(qr.getError());
			I reversed = qr.getReversed();
			DerivedCollectionElement<I> parentEl = getParent().addElement(reversed, //
				strip(after), strip(before), first);
			return parentEl == null ? null : map(parentEl, true);
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			Runnable postSet = null;
			if (theEngine.getTransformation() instanceof Transformation.ReversibleTransformation) {
				Transformation.ReversibleTransformation<I, T> reversible = (Transformation.ReversibleTransformation<I, T>) theEngine
					.getTransformation();
				if (reversible.getReverse() instanceof FlowElementSetter) {
					TransformedElement first = (TransformedElement) elements.iterator().next();
					postSet = ((FlowElementSetter<I, T>) reversible.getReverse()).preSet(first.getParentEl(), newValue);
				}
			}
			try {
				setElementsValue(elements, newValue);
			} finally {
				if (postSet != null)
					postSet.run();
			}
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			listening.withConsumer((ObservableValueEvent<Transformation.TransformationState> evt) -> {
				try (Transaction t = getParent().lock(false, null)) {
					// The order of update here may be different than the order in the derived collection
					// It's a lot of work to keep the elements in order (since the order may change),
					// so we'll just let order of addition be good enough
					for (TransformedElement el : theElements)
						el.updated(evt);
				}
			}, action -> getEngine().noInitChanges().act(action));
			getParent().begin(fromStart, (parentEl, cause) -> {
				try (Transaction t = getEngine().lock()) {
					TransformedElement el = new TransformedElement(parentEl, false);
					onElement.accept(el, cause);
				}
			}, listening);
		}

		private class TransformedElement extends AbstractMappedElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<I> theParentEl;
			private final ElementId theStoredId;
			CollectionElementListener<T> theListener;

			TransformedElement(DerivedCollectionElement<I> parentEl, boolean synthetic) {
				super(parentEl::get);
				theParentEl = parentEl;
				if (!synthetic) {
					theParentEl.setListener(new CollectionElementListener<I>() {
						@Override
						public void update(I oldSource, I newSource, Object cause) {
							BiTuple<T, T> values = transformElement.sourceChanged(oldSource, newSource, getEngine().get());
							if (values != null)
								ObservableCollectionActiveManagers.update(theListener, values.getValue1(), values.getValue2(), cause);
						}

						@Override
						public void removed(I value, Object cause) {
							T val = transformElement.getCurrentValue(value, getEngine().get());
							theElements.mutableElement(theStoredId).remove();
							ObservableCollectionActiveManagers.removed(theListener, val, cause);
							theListener = null;
						}
					});
				}
				theStoredId = synthetic ? null : theElements.addElement(this, false).getElementId();
			}

			protected DerivedCollectionElement<I> getParentEl() {
				return theParentEl;
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			protected String isParentEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			protected String isParentAcceptable(I value) {
				return theParentEl.isAcceptable(value);
			}

			@Override
			void setParent(I parentValue) {
				theParentEl.set(parentValue);
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((TransformedElement) o).theParentEl);
			}

			@Override
			public T get() {
				return transformElement.getCurrentValue(getEngine().get());
			}

			@Override
			public String isEnabled() {
				return isEnabledLocal();
			}

			@Override
			public String isAcceptable(T value) {
				return super.isAcceptable(value);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				Runnable postSet = null;
				if (theEngine.getTransformation() instanceof Transformation.ReversibleTransformation) {
					Transformation.ReversibleTransformation<I, T> reversible = (Transformation.ReversibleTransformation<I, T>) theEngine
						.getTransformation();
					if (reversible.getReverse() instanceof FlowElementSetter) {
						postSet = ((FlowElementSetter<I, T>) reversible.getReverse()).preSet(theParentEl, value);
					}
				}
				try {
					super.set(value);
				} finally {
					if (postSet != null)
						postSet.run();
				}
			}

			@Override
			public String canRemove() {
				return theParentEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theParentEl.remove(); // The parent will call the listener to remove the stored ID
			}

			void updated(ObservableValueEvent<Transformation.TransformationState> cause) {
				BiTuple<T, T> values = transformElement.transformationStateChanged(cause.getOldValue(), cause.getNewValue());
				if (values != null)
					ObservableCollectionActiveManagers.update(theListener, values.getValue1(), values.getValue2(), cause);
			}

			@Override
			public String toString() {
				return getParentEl().toString();
			}
		}
	}
}
