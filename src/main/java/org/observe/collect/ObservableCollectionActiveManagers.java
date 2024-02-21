package org.observe.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.Equivalence;
import org.observe.Eventable;
import org.observe.ObservableValueEvent;
import org.observe.Transformation;
import org.observe.collect.ObservableCollectionDataFlowImpl.AbstractTransformedManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionOperation;
import org.observe.collect.ObservableCollectionDataFlowImpl.FlowElementSetter;
import org.observe.collect.ObservableCollectionDataFlowImpl.RepairListener;
import org.observe.collect.ObservableCollectionImpl.ActiveDerivedCollection;
import org.observe.util.WeakListening;
import org.qommons.BiTuple;
import org.qommons.Identifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.ValueStoredCollection;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.SortedTreeList;

/** Contains some implementations of {@link ActiveCollectionManager} and its dependencies */
public class ObservableCollectionActiveManagers {
	private static boolean isStrictMode;

	/** @return Whether strict mode is set, which governs performance-to-predictability decisions made by some derived collections */
	public static boolean isStrictMode() {
		return isStrictMode;
	}

	/**
	 * @param strictMode Whether to set strict mode, which governs performance-to-predictability decisions made by some derived collections
	 */
	public static void setStrictMode(boolean strictMode) {
		isStrictMode = strictMode;
	}

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
	public static interface ActiveCollectionManager<E, I, T> extends CollectionOperation<E, I, T>, Eventable {
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
		 * @param causes The causes of the addition
		 */
		void accept(DerivedCollectionElement<E> element, Object... causes);
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
		 * @param internalOnly If true, this signifies that the event causing this invocation should not normally be reported to a terminal
		 *        result (e.g. the derived collection). This may happen in response to a change in a source collection which does not affect
		 *        the content of the derived flow. Notification of the change may nevertheless be needed downstream for some purposes,
		 *        especially derived gathered multi-maps. When this is true, most managers may simply pass it along, and most termination
		 *        points may ignore it.
		 * @param causes The causes of the change
		 */
		void update(E oldValue, E newValue, boolean internalOnly, Object... causes);

		/**
		 * Alerts the derived collection that the element has been removed from the source flow
		 *
		 * @param value The element's previous value before it was removed. As with <code>oldValue</code> in the
		 *        {@link #update(Object, Object, boolean, Object[]) update} method, this value is not guaranteed to be the last that the
		 *        listener knew about.
		 * @param causes The causes of the element's removal
		 */
		void removed(E value, Object... causes);
	}

	static <T> void update(CollectionElementListener<T> listener, T oldValue, T newValue, boolean internalOnly, Object... causes) {
		if (listener != null)
			listener.update(oldValue, newValue, internalOnly, causes);
	}

	static <T> void removed(CollectionElementListener<T> listener, T value, Object... causes) {
		if (listener != null)
			listener.removed(value, causes);
	}

	static class BaseCollectionManager<E> implements ActiveCollectionManager<E, E, E> {
		private final ObservableCollection<E> theSource;
		private final BetterTreeMap<ElementId, CollectionElementListener<E>> theElementListeners;

		BaseCollectionManager(ObservableCollection<E> source) {
			theSource = source;
			theElementListeners = BetterTreeMap.<ElementId> build(ElementId::compareTo).buildMap();
		}

		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theSource.getIdentity(), "activeManager");
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theSource.getThreadConstraint();
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
		public Collection<Cause> getCurrentCauses() {
			return theSource.getCurrentCauses();
		}

		@Override
		public CoreId getCoreId() {
			return theSource.getCoreId();
		}

		@Override
		public boolean isEventing() {
			return theSource.isEventing();
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
						listener.update(evt.getOldValue(), evt.getNewValue(), false, evt);
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

	/**
	 * Abstract class to eliminate boilerplate for managers with the same target type as their parent
	 *
	 * @param <E> The source type of the manager
	 * @param <T> The target type of the manager
	 */
	public static abstract class AbstractSameTypeActiveManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;

		/** @param parent The parent manager */
		public AbstractSameTypeActiveManager(ActiveCollectionManager<E, ?, T> parent) {
			theParent = parent;
		}

		/** @return This manager's parent */
		protected ActiveCollectionManager<E, ?, T> getParent() {
			return theParent;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public boolean isContentControlled() {
			return theParent.isContentControlled();
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
		public Collection<Cause> getCurrentCauses() {
			return theParent.getCurrentCauses();
		}

		@Override
		public CoreId getCoreId() {
			return theParent.getCoreId();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theParent.getThreadConstraint();
		}

		/** @return Whether this manager wraps its parent's elements */
		protected abstract boolean areElementsWrapped();

		/**
		 * @param parentEl The parent element to wrap
		 * @param synthetic Whether the element is to be synthetic--just a search or real and representative of the element in the
		 *        collection
		 * @return The wrapped element
		 */
		protected abstract DerivedCollectionElement<T> wrap(DerivedCollectionElement<T> parentEl, boolean synthetic);

		/**
		 * @param myEl The element of this flow
		 * @return The parent element of the given element
		 */
		protected abstract DerivedCollectionElement<T> peel(DerivedCollectionElement<T> myEl);

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			if (!areElementsWrapped())
				return theParent.getElementFinder(value);
			Comparable<DerivedCollectionElement<T>> parentFinder = getParent().getElementFinder(value);
			if (parentFinder == null)
				return null;
			return el -> parentFinder.compareTo(peel(el));
		}

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (!areElementsWrapped())
				return theParent.getElementsBySource(sourceEl, sourceCollection);
			return QommonsUtils.map2(getParent().getElementsBySource(sourceEl, sourceCollection), el -> wrap(el, true));
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection) {
			return theParent.getSourceElements(peel(localElement), sourceCollection);
		}

		@Override
		public DerivedCollectionElement<T> getEquivalentElement(DerivedCollectionElement<?> flowEl) {
			return theParent.getEquivalentElement(flowEl);
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			if (!areElementsWrapped())
				return theParent.canAdd(toAdd, after, before);
			return getParent().canAdd(toAdd, peel(after), peel(before));
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			if (!areElementsWrapped())
				return theParent.addElement(value, after, before, first);
			return wrap(getParent().addElement(value, peel(after), peel(before), first), true);
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			if (!areElementsWrapped())
				return theParent.canMove(valueEl, after, before);
			return getParent().canMove(peel(valueEl), peel(after), peel(before));
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			if (!areElementsWrapped())
				return theParent.move(valueEl, after, before, first, afterRemove);
			return wrap(getParent().move(peel(valueEl), peel(after), peel(before), first, afterRemove), true);
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			if (!areElementsWrapped())
				theParent.setValues(elements, newValue);
			else
				getParent().setValues(//
					elements.stream().map(this::peel).collect(Collectors.toList()), newValue);
		}

		@Override
		public boolean clear() {
			return theParent.clear();
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			if (!areElementsWrapped())
				theParent.begin(fromStart, onElement, listening);
			else
				getParent().begin(fromStart, (el, cause) -> onElement.accept(wrap(el, false), cause), listening);
		}
	}

	/**
	 * A wrapping element that has the same type as its parent
	 *
	 * @param <T> The type of the element
	 */
	public static abstract class AbstractSameTypeElement<T> implements DerivedCollectionElement<T> {
		/** The parent element */
		protected final DerivedCollectionElement<T> theParentEl;

		/** @param parentEl The parent element */
		public AbstractSameTypeElement(DerivedCollectionElement<T> parentEl) {
			theParentEl = parentEl;
		}

		@Override
		public int compareTo(DerivedCollectionElement<T> o) {
			return theParentEl.compareTo(((AbstractSameTypeElement<T>) o).theParentEl);
		}

		@Override
		public void setListener(CollectionElementListener<T> listener) {
			theParentEl.setListener(listener);
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
			return theParentEl.isAcceptable(value);
		}

		@Override
		public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
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
		public int hashCode() {
			return theParentEl.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof AbstractSameTypeElement && theParentEl.equals(((AbstractSameTypeElement<T>) obj).theParentEl);
		}

		@Override
		public String toString() {
			return theParentEl.toString();
		}
	}

	static class ActiveReversedManager<E, T> extends AbstractSameTypeActiveManager<E, T> {
		ActiveReversedManager(ActiveCollectionManager<E, ?, T> parent) {
			super(parent);
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "reverse");
		}

		@Override
		public Equivalence<? super T> equivalence() {
			Equivalence<? super T> equiv = getParent().equivalence();
			if (equiv instanceof Equivalence.SortedEquivalence)
				return ((Equivalence.SortedEquivalence<? super T>) equiv).reverse();
			else
				return equiv;
		}

		@Override
		public boolean isEventing() {
			return getParent().isEventing();
		}

		@Override
		protected boolean areElementsWrapped() {
			return true;
		}

		@Override
		protected DerivedCollectionElement<T> wrap(DerivedCollectionElement<T> parentEl, boolean synthetic) {
			return parentEl == null ? null : parentEl.reverse();
		}

		@Override
		protected DerivedCollectionElement<T> peel(DerivedCollectionElement<T> myEl) {
			return myEl == null ? null : myEl.reverse();
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			Comparable<DerivedCollectionElement<T>> parentFinder = getParent().getElementFinder(value);
			if (parentFinder == null)
				return null;
			return el -> -parentFinder.compareTo(el.reverse());
		}

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(getParent().getElementsBySource(sourceEl.reverse(), sourceCollection), el -> el.reverse());
		}

		@Override
		public DerivedCollectionElement<T> getEquivalentElement(DerivedCollectionElement<?> flowEl) {
			return DerivedCollectionElement.reverse(getParent().getEquivalentElement(flowEl.reverse()));
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			return getParent().canAdd(toAdd, DerivedCollectionElement.reverse(before), DerivedCollectionElement.reverse(after));
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			DerivedCollectionElement<T> parentEl = getParent().addElement(value, DerivedCollectionElement.reverse(before),
				DerivedCollectionElement.reverse(after), !first);
			return parentEl == null ? null : parentEl.reverse();
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			return getParent().canMove(DerivedCollectionElement.reverse(valueEl), DerivedCollectionElement.reverse(before),
				DerivedCollectionElement.reverse(after));
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			return getParent().move(DerivedCollectionElement.reverse(valueEl), DerivedCollectionElement.reverse(before),
				DerivedCollectionElement.reverse(after), !first, afterRemove).reverse();
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			getParent().begin(!fromStart, (el, cause) -> onElement.accept(el.reverse(), cause), listening);
		}
	}

	/**
	 * An {@link ActiveCollectionManager active manager} that sorts its elements by value
	 *
	 * @param <E> The type of the source collection
	 * @param <T> The type of this manager
	 */
	protected static class SortedManager<E, T> extends AbstractSameTypeActiveManager<E, T> implements ActiveValueStoredManager<E, T, T> {
		private final Comparator<? super T> theCompare;
		// Need to keep track of the values to enforce the set-does-not-reorder policy
		private final SortedTreeList<SortedElement> theValues;
		private final Equivalence.SortedEquivalence<? super T> theEquivalence;

		/**
		 * @param parent The parent manager
		 * @param compare The comparator to use to sort the elements
		 */
		protected SortedManager(ActiveCollectionManager<E, ?, T> parent, Comparator<? super T> compare) {
			super(parent);
			theCompare = compare;
			theValues = SortedTreeList.<SortedElement> buildTreeList(SortedElement::compareWithValue).build();
			theEquivalence = parent.equivalence().sorted(theCompare, true);
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "sorted", theCompare);
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public boolean isEventing() {
			return getParent().isEventing();
		}

		@Override
		protected boolean areElementsWrapped() {
			return true;
		}

		@Override
		protected DerivedCollectionElement<T> wrap(DerivedCollectionElement<T> parentEl, boolean synthetic) {
			return parentEl == null ? null : new SortedElement(parentEl, synthetic);
		}

		@Override
		protected DerivedCollectionElement<T> peel(DerivedCollectionElement<T> myEl) {
			return myEl == null ? null : ((SortedElement) myEl).theParentEl;
		}

		@Override
		public boolean isConsistent(DerivedCollectionElement<T> element) {
			return theValues.isConsistent(((SortedElement) element).theValueElement);
		}

		@Override
		public boolean checkConsistency() {
			return theValues.checkConsistency();
		}

		@Override
		public <X> boolean repair(DerivedCollectionElement<T> element, RepairListener<T, X> listener) {
			return theValues.repair(//
				((SortedElement) element).theValueElement, new SMRepairListener<>(listener));
		}

		@Override
		public <X> boolean repair(RepairListener<T, X> listener) {
			return theValues.repair(new SMRepairListener<>(listener));
		}

		static class SMRepairListener<T, X> implements ValueStoredCollection.RepairListener<SortedManager<?, T>.SortedElement, X> {
			private final RepairListener<T, X> theWrapped;

			SMRepairListener(RepairListener<T, X> wrapped) {
				theWrapped = wrapped;
			}

			@Override
			public X removed(CollectionElement<SortedManager<?, T>.SortedElement> element) {
				return theWrapped.removed(element.get().theParentEl);
			}

			@Override
			public void disposed(SortedManager<?, T>.SortedElement value, X data) {
				theWrapped.disposed(value.get(), data);
			}

			@Override
			public void transferred(CollectionElement<SortedManager<?, T>.SortedElement> element, X data) {
				theWrapped.transferred(element.get().theParentEl, data);
			}
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			Comparable<DerivedCollectionElement<T>> sourceFinder = getParent().getElementFinder(value);
			return el -> {
				int comp = theCompare.compare(value, el.get());
				if (comp == 0 && sourceFinder != null)
					comp = sourceFinder.compareTo(peel(el));
				return comp;
			};
		}

		@Override
		public DerivedCollectionElement<T> getEquivalentElement(DerivedCollectionElement<?> flowEl) {
			if (!(flowEl instanceof SortedManager.SortedElement))
				return null;
			SortedElement other = (SortedElement) flowEl;
			if (other.getMgr() == this)
				return other;
			DerivedCollectionElement<T> found = getParent().getEquivalentElement(other.theParentEl);
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
			return getParent().canAdd(toAdd, requiredAfter, requiredBefore);
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
				parentEl = getParent().addElement(value, requiredAfter, requiredBefore, first);
			else if (first && after != null && getParent().canAdd(value, ((SortedElement) after).theParentEl, null) == null)
				parentEl = getParent().addElement(value, ((SortedElement) after).theParentEl, null, true);
			else if (!first && before != null && getParent().canAdd(value, null, ((SortedElement) before).theParentEl) == null)
				parentEl = getParent().addElement(value, null, ((SortedElement) before).theParentEl, false);
			else
				parentEl = getParent().addElement(value, null, null, first);
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
			return getParent().canMove(((SortedElement) valueEl).theParentEl, requiredAfter, requiredBefore);
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
			return new SortedElement(getParent().move(//
				((SortedElement) valueEl).theParentEl, requiredAfter, requiredBefore, first, afterRemove), true);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			super.begin(fromStart, onElement, listening);
		}

		class SortedElement implements DerivedCollectionElement<T> {
			final DerivedCollectionElement<T> theParentEl;
			private T theValue;
			private ElementId theValueElement;
			private CollectionElementListener<T> theListener;

			SortedElement(DerivedCollectionElement<T> parentEl, boolean synthetic) {
				theParentEl = parentEl;
				theValue = parentEl.get();
				if (!synthetic) {
					theValueElement = theValues.addElement(this, false).getElementId();
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, boolean internalOnly, Object... causes) {
							T realOldValue = theValue;
							theValue = newValue;
							if (internalOnly) {
								ObservableCollectionActiveManagers.update(theListener, realOldValue, newValue, true, causes);
								return;
							}
							if (!isInCorrectOrder(newValue, theParentEl)) {
								// The order of this element has changed.
								// Might just be this element, but it also might be the whole sorting strategy that's changed.
								// Do a repair to be safe.
								// We don't need to fire events in response to the sorting changes,
								// because the actively derived collection will also detect the mis-ordered state
								// and will use the sorting to figure out where everything goes.
								theValues.repair(theValueElement, //
									new ValueStoredCollection.RepairListener<SortedElement, Void>() {
									@Override
									public Void removed(CollectionElement<SortedManager<E, T>.SortedElement> element) {
										return null;
									}

									@Override
									public void disposed(SortedManager<E, T>.SortedElement element, Void data) {
										throw new IllegalStateException();
									}

									@Override
									public void transferred(CollectionElement<SortedManager<E, T>.SortedElement> element, Void data) {
										element.get().theValueElement = element.getElementId();
									}
								});
								/* Update: Regarding the older comment below, the add/remove combo instead of update was done
								 * for the benefit of the chain testing framework, which will randomly create chains of operations
								 * which would make no sense in any real context, like 2 sorting operations chained together.
								 * But it still made sense to address it because failures in the testing, even for ridiculous situations,
								 * break the testing.
								 *
								 * However, in this case, reading and re-reading this verbose comment does't fully help me
								 * to understand the problem I was actually solving here.
								 *
								 * I have just modified the actively-derived collection implementation to do a repair when it detects
								 * reordering on update, instead of just removing and re-adding the updated value.
								 * This is to fix a real-world issue that occurs when the entire ordering scheme
								 * is changed at once (e.g. table sorting), so it's possible that what I thought was happening here before
								 * was actually a manifestation of that bug.
								 *
								 * This code is preventing the derived collection from detecting the reordering (since no update is fired),
								 * and since I'm no longer sure of its value, I'm removing it and just firing an update.
								 * If the chain tester fails, maybe I'll have to revisit this.
								 */
								/* We could just do an update here and let the derived collection do the re-order.
								 * But this would potentially be inconsistent in the case of child flows that also affect order.
								 * E.g. the update could be swallowed by the derived flow and the element not reordered,
								 * resulting in just an update in the terminal collection.
								 * But the same flow order, collected in between, would yield different ordering
								 * for the same set of operations.
								 * The use case of such a situation is probably minuscule, since why would anyone apply a sorted flow
								 * and then apply another order-governing flow on top of it?
								 * In such situations the benefit of just firing an update
								 * instead of a remove/add is probably negligible.
								 * So, to summarize, we'll fire an add/remove combo here instead of just an update. */
								// ObservableCollectionActiveManagers.removed(theListener, realOldValue, cause);
								// theAccepter.accept(SortedElement.this, cause);
							} // else {
							ObservableCollectionActiveManagers.update(theListener, realOldValue, newValue, false, causes);
							// }
						}

						@Override
						public void removed(T value, Object... causes) {
							theValues.mutableElement(theValueElement).remove();
							ObservableCollectionActiveManagers.removed(theListener, theValue, causes);
						}
					});
				}
			}

			SortedManager<E, T> getMgr() {
				return SortedManager.this;
			}

			boolean isInCorrectOrder(T newValue, DerivedCollectionElement<T> parentEl) {
				SortedElement left = CollectionElement.get(theValues.getAdjacentElement(theValueElement, false));
				SortedElement right = CollectionElement.get(theValues.getAdjacentElement(theValueElement, true));
				return (left == null || compareWithValue(newValue, left) >= 0)//
					&& (right == null || compareWithValue(newValue, right) <= 0);
			}

			int compareWithValue(SortedElement o) {
				return compareWithValue(theValue, o);
			}

			int compareWithValue(T newValue, SortedElement o) {
				if (this == o)
					return 0;
				int comp = theCompare.compare(newValue, o.theValue);
				if (comp == 0)
					comp = theParentEl.compareTo(o.theParentEl);
				return comp;
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				SortedElement sorted = (SortedElement) o;
				if (theValueElement != null && sorted.theValueElement != null)
					return theValueElement.compareTo(sorted.theValueElement);
				else
					return compareWithValue(sorted); // One or both are synthetic
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public T get() {
				return theValue;
			}

			@Override
			public String isEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				if (theCompare.compare(theValue, value) != 0 && !isInCorrectOrder(value, theParentEl))
					return StdMsg.ILLEGAL_ELEMENT_POSITION;
				return theParentEl.isAcceptable(value);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				// It is not allowed to change the order of an element via set
				// However, if the order has already been changed (e.g. due to changes in the value or the comparator),
				// it is permitted (and required) to use set to notify the collection of the change
				if (theCompare.compare(theValue, value) != 0 && !isInCorrectOrder(value, theParentEl))
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				// The value will be set again when the parent fires an update, but keep the old value until then
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
				return theParentEl.toString() + "(" + theValue + ")";
			}
		}
	}

	static class FilteredCollectionManager<E, T> extends AbstractSameTypeActiveManager<E, T> {
		private final Function<? super T, String> theFilter;
		private ElementAccepter<T> theElementAccepter;

		FilteredCollectionManager(ActiveCollectionManager<E, ?, T> parent, Function<? super T, String> filter) {
			super(parent);
			theFilter = filter;
		}

		@Override
		public boolean isEventing() {
			return getParent().isEventing();
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "filter", theFilter);
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
		protected boolean areElementsWrapped() {
			return true;
		}

		@Override
		protected DerivedCollectionElement<T> wrap(DerivedCollectionElement<T> parentEl, boolean synthetic) {
			return parentEl == null ? null : new FilteredElement(parentEl, synthetic, true);
		}

		@Override
		protected DerivedCollectionElement<T> peel(DerivedCollectionElement<T> myEl) {
			return myEl == null ? null : ((FilteredElement) myEl).theParentEl;
		}

		@Override
		public DerivedCollectionElement<T> getEquivalentElement(DerivedCollectionElement<?> flowEl) {
			if (!(flowEl instanceof FilteredCollectionManager.FilteredElement))
				return null;
			FilteredElement other = (FilteredElement) flowEl;
			if (other.getMgr() == this)
				return other;
			DerivedCollectionElement<T> found = getParent().getEquivalentElement(other.theParentEl);
			if (found == null)
				return null;
			return new FilteredElement(found, true, true);
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			String msg = theFilter.apply(toAdd);
			if (msg == null)
				msg = super.canAdd(toAdd, after, before);
			return msg;
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			String msg = theFilter.apply(value);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			return super.addElement(value, after, before, first);
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			String msg = theFilter.apply(newValue);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			super.setValues(elements, newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theElementAccepter = onElement;
			getParent().begin(fromStart, (parentEl, cause) -> {
				String msg;
				try {
					msg = theFilter.apply(parentEl.get());
				} catch (RuntimeException e) {
					msg = "Exception evaluating filter " + theFilter;
					if (e.getMessage() != null)
						msg += ": " + e.getMessage();
					e.printStackTrace();
				}
				if (msg == null)
					onElement.accept(new FilteredElement(parentEl, false, true), cause);
				else
					new FilteredElement(parentEl, false, false);
			}, listening);
		}

		class FilteredElement extends AbstractSameTypeElement<T> {
			private final boolean isSynthetic;
			private boolean included;
			private CollectionElementListener<T> theListener;

			FilteredElement(DerivedCollectionElement<T> parentEl, boolean synthetic, boolean included) {
				super(parentEl);
				isSynthetic = synthetic;
				this.included = included;
				if (!isSynthetic) {
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, boolean internalOnly, Object... causes) {
							if (internalOnly) {
								ObservableCollectionActiveManagers.update(theListener, oldValue, newValue, internalOnly, causes);
								return;
							}
							boolean oldIncluded = FilteredElement.this.included;
							boolean newIncluded;
							try {
								newIncluded = theFilter.apply(newValue) == null;
							} catch (RuntimeException e) {
								newIncluded = false;
								e.printStackTrace();
							}
							FilteredElement.this.included = newIncluded;
							if (!oldIncluded && newIncluded) {
								theElementAccepter.accept(FilteredElement.this, causes);
							} else if (oldIncluded && !newIncluded && theListener != null) {
								theListener.removed(oldValue, causes);
								theListener = null;
							} else if (oldIncluded && newIncluded && theListener != null)
								theListener.update(oldValue, newValue, false, causes);
						}

						@Override
						public void removed(T value, Object... causes) {
							if (FilteredElement.this.included && theListener != null) {
								theListener.removed(value, causes);
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
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
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
		}
	}

	static class ActiveEquivalenceSwitchedManager<E, T> extends AbstractSameTypeActiveManager<E, T> {
		private final Equivalence<? super T> theEquivalence;

		ActiveEquivalenceSwitchedManager(ActiveCollectionManager<E, ?, T> parent, Equivalence<? super T> equivalence) {
			super(parent);
			theEquivalence = equivalence;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "withEquivalence", theEquivalence);
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public boolean isEventing() {
			return getParent().isEventing();
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			return null;
		}

		@Override
		protected boolean areElementsWrapped() {
			return false;
		}

		@Override
		protected DerivedCollectionElement<T> wrap(DerivedCollectionElement<T> parentEl, boolean synthetic) {
			return parentEl;
		}

		@Override
		protected DerivedCollectionElement<T> peel(DerivedCollectionElement<T> myEl) {
			return myEl;
		}
	}

	static class ActiveTransformedCollectionManager<E, I, T> extends AbstractTransformedManager<E, I, T>
	implements ActiveCollectionManager<E, I, T> {
		// Need to keep track of these to update them when the combined values change
		private final BetterSortedSet<TransformedElement> theElements;

		ActiveTransformedCollectionManager(ActiveCollectionManager<E, ?, I> parent, Transformation<I, T> def,
			Equivalence<? super T> equivalence) {
			super(parent, def, equivalence);

			theElements = BetterTreeSet.<TransformedElement> buildTreeSet(TransformedElement::compareTo).build();
		}

		@Override
		protected ActiveCollectionManager<E, ?, I> getParent() {
			return (ActiveCollectionManager<E, ?, I>) super.getParent();
		}

		@Override
		public boolean isEventing() {
			// We don't need to include the transformation engine, because that won't be affected by a modification
			return getParent().isEventing();
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
		protected void doParentMultiSet(Collection<AbstractTransformedElement> elements, I newValue) {
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
					for (TransformedElement el : theElements)
						el.updated(evt.getOldValue(), evt.getNewValue(), evt);
				}
			}, action -> getEngine().noInitChanges().act(action));
			getParent().begin(fromStart, (parentEl, cause) -> {
				try (Transaction t = getEngine().lock()) {
					TransformedElement el = new TransformedElement(parentEl, false);
					onElement.accept(el, cause);
				}
			}, listening);
		}

		private class TransformedElement extends AbstractTransformedElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<I> theParentEl;
			private ElementId theStoreElement;
			CollectionElementListener<T> theListener;

			TransformedElement(DerivedCollectionElement<I> parentEl, boolean synthetic) {
				super(parentEl::get);
				theParentEl = parentEl;
				if (!synthetic) {
					theParentEl.setListener(new CollectionElementListener<I>() {
						@Override
						public void update(I oldSource, I newSource, boolean internalOnly, Object... causes) {
							if (internalOnly) {
								T value = transformElement.getCurrentValue(getEngine().get());
								ObservableCollectionActiveManagers.update(theListener, value, value, true, causes);
								return;
							}
							BiTuple<T, T> values = transformElement.sourceChanged(oldSource, newSource, getEngine().get());
							if (values != null)
								ObservableCollectionActiveManagers.update(theListener, values.getValue1(), values.getValue2(), false,
									causes);
						}

						@Override
						public void removed(I value, Object... causes) {
							T val = transformElement.getCurrentValue(value, getEngine().get());
							theElements.mutableElement(theStoreElement).remove();
							theStoreElement = null;
							ObservableCollectionActiveManagers.removed(theListener, val, causes);
							theListener = null;
						}
					});
				}
				theStoreElement = synthetic ? null : theElements.addElement(this, false).getElementId();
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

			void updated(Transformation.TransformationState oldState, Transformation.TransformationState newState, Object cause) {
				BiTuple<T, T> values = transformElement.transformationStateChanged(oldState, newState);
				if (values != null)
					ObservableCollectionActiveManagers.update(theListener, values.getValue1(), values.getValue2(), false, cause);
			}

			@Override
			public String toString() {
				return getParentEl().toString();
			}
		}
	}
}
