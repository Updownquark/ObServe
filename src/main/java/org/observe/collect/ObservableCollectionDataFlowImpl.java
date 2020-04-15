package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.XformOptions;
import org.observe.assoc.ObservableMultiMap.MultiMapFlow;
import org.observe.assoc.ObservableSortedMultiMap.SortedMultiMapFlow;
import org.observe.assoc.impl.AddKeyHolder;
import org.observe.assoc.impl.DefaultMultiMapFlow;
import org.observe.assoc.impl.DefaultSortedMultiMapFlow;
import org.observe.collect.Combination.CombinationPrecursor;
import org.observe.collect.Combination.CombinedFlowDef;
import org.observe.collect.FlowOptions.MapDef;
import org.observe.collect.FlowOptions.MapOptions;
import org.observe.collect.FlowOptions.SimpleUniqueOptions;
import org.observe.collect.FlowOptions.UniqueOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableCollection.ModFilterBuilder;
import org.observe.collect.ObservableCollectionImpl.ActiveDerivedCollection;
import org.observe.collect.ObservableCollectionImpl.PassiveDerivedCollection;
import org.observe.util.TypeTokens;
import org.observe.util.WeakListening;
import org.qommons.BiTuple;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Lockable;
import org.qommons.QommonsUtils;
import org.qommons.Stamped;
import org.qommons.Ternian;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterHashMap;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.OptimisticContext;
import org.qommons.collect.ValueStoredCollection;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.BinaryTreeNode;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/** Contains implementations of {@link CollectionDataFlow} and its dependencies */
public class ObservableCollectionDataFlowImpl {
	private ObservableCollectionDataFlowImpl() {}

	/**
	 * Used in mapping/filtering collection data {@link PassiveCollectionManager passively}
	 *
	 * @param <E> The source type
	 * @param <T> The destination type
	 */
	public static class FilterMapResult<E, T> {
		/** The pre-mapped source value */
		public E source;
		/** The mapped result */
		public T result;
		private boolean isError;
		private String rejectReason;

		/** Creates an empty mapping structure */
		public FilterMapResult() {}

		/** @param src The pre-mapped source value for this mapping structure */
		public FilterMapResult(E src) {
			source = src;
		}

		/** @return Whether the mapping operation was successful */
		public boolean isAccepted() {
			return rejectReason == null;
		}

		/** @return Whether the mapping operation, if attempted, should throw an exception */
		public boolean isError() {
			return isError;
		}

		/** @return null if the mapping operation was successful, or a reason why it would fail */
		public String getRejectReason() {
			return rejectReason;
		}

		/** Clears the {@link #isError() error} and {@link #getRejectReason() reject reason} fields */
		public void clearRejection() {
			rejectReason = null;
			isError = false;
		}

		/**
		 * @param <X> The type of exception to throw
		 * @param type The function to create the exception to throw
		 * @return The {@link #getRejectReason() reject reason}, if {@link #isError() error} is false
		 * @throws X If {@link #isError() error} is true
		 */
		public <X extends Throwable> String throwIfError(Function<String, X> type) throws X {
			if (rejectReason == null)
				return null;
			if (isError)
				throw type.apply(rejectReason);
			return rejectReason;
		}

		/**
		 * @throws UnsupportedOperationException If the rejection reason is that the operation is unsupported
		 * @throws IllegalArgumentException If the rejection reason is that the argument was illegal
		 */
		public void throwIfRejected() throws UnsupportedOperationException, IllegalArgumentException {
			if (rejectReason == null)
				return;
			else if (StdMsg.UNSUPPORTED_OPERATION.equals(rejectReason))
				throw new UnsupportedOperationException(rejectReason);
			else
				throw new IllegalArgumentException(rejectReason);
		}

		/**
		 * Marks this mapping operation as rejected
		 *
		 * @param reason The reason for the rejection
		 * @param error Whether the mapping operation, if attempted, should throw an exception
		 * @return This structure
		 */
		public FilterMapResult<E, T> reject(String reason, boolean error) {
			if (error && reason == null)
				throw new IllegalArgumentException("Need a reason for the error");
			result = null;
			rejectReason = reason;
			isError = error;
			return this;
		}

		/**
		 * Marks this mapping operation as rejected (if <code>reason</code> is non-null)
		 *
		 * @param reason The reason for the rejection
		 * @param error Whether the mapping operation, if attempted, should throw an exception
		 * @return This structure
		 */
		public FilterMapResult<E, T> maybeReject(String reason, boolean error) {
			if (reason != null)
				reject(reason, error);
			return this;
		}

		/**
		 * A little generic trick. For performance, this method does not create a new structure, but simply converts this structure's source
		 * using the given map and returns this structure as a structure of a given type.
		 *
		 * @param map The mapping operation to apply to the source
		 * @return This structure
		 */
		public <X> FilterMapResult<X, T> map(Function<? super E, ? extends X> map) {
			FilterMapResult<X, T> mapped = (FilterMapResult<X, T>) this;
			mapped.source = map.apply(source);
			return mapped;
		}
	}

	/**
	 * The super type of the {@link PassiveCollectionManager passive} and {@link ActiveCollectionManager active} collection managers
	 * produced by {@link CollectionDataFlow}s to do the work of managing a derived collection.
	 *
	 * @param <E> The type of the source collection
	 * @param <I> An intermediate type
	 * @param <T> The type of the derived collection that can use this manager
	 */
	public static interface CollectionOperation<E, I, T> extends Identifiable, Transactable {
		/** @return The type of collection that this operation would produce */
		TypeToken<T> getTargetType();

		/** @return The equivalence of the collection that this operation would produce */
		Equivalence<? super T> equivalence();

		/** @return Whether this manager will prevent its collection from being able to manage elements arbitrarily */
		boolean isContentControlled();
	}

	/**
	 * A manager for a {@link ObservableCollection.CollectionDataFlow#supportsPassive() passively-}derived collection
	 *
	 * @param <E> The type of the source collection
	 * @param <I> An intermediate type
	 * @param <T> The type of the derived collection that this manager can power
	 */
	public static interface PassiveCollectionManager<E, I, T> extends CollectionOperation<E, I, T> {
		/** @return Whether this manager is the result of an odd number of {@link CollectionDataFlow#reverse() reverse} operations */
		boolean isReversed();

		/** @return The observable value of this manager's mapping function that produces values from source values */
		ObservableValue<? extends Function<? super E, ? extends T>> map();

		/**
		 * @return Whether this manager has the ability to convert its values to source values for at least some sub-set of possible values
		 */
		String canReverse();

		/**
		 * @param dest The filter-map structure whose source is the value to convert
		 * @param forAdd Whether this operation is a precursor to inserting the value into the collection
		 * @return The filter-mapped result (typically the same instance as <code>dest</code>)
		 */
		FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd);

		/**
		 * A shortcut for reversing a value
		 *
		 * @param dest The value to convert
		 * @param forAdd Whether this operation is a precursor to inserting the value into the collection
		 * @return The filter-mapped result
		 */
		default FilterMapResult<T, E> reverse(T dest, boolean forAdd) {
			return reverse(new FilterMapResult<>(dest), forAdd);
		}

		/** @return Whether this manager may disallow some remove operations */
		boolean isRemoveFiltered();

		/** @return null if this manager allows derived collections to move elements around, or a reason otherwise */
		default String canMove() {
			return null;
		}

		/**
		 * @param element The source element to map
		 * @param map The mapping function to apply to the element's value
		 * @return The element for the derived collection
		 */
		MutableCollectionElement<T> map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map);

		/**
		 * Maps the old and new source values for an event from the source collection to this manager's value type
		 *
		 * @param oldSource The old value from the source event
		 * @param newSource The new value from the source event
		 * @param map The mapping function to apply to the values
		 * @return The old and new values for the event to propagate from the manager's derived collection
		 */
		BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map);

		/**
		 * @return Whether this manager may produce a single mapped value for many different source values. Affects searching by value in
		 *         the derived collection.
		 */
		boolean isManyToOne();

		/**
		 * @param elements The elements to set the value for en masse
		 * @param value The value to set for the elements
		 */
		void setValue(Collection<MutableCollectionElement<T>> elements, T value);
	}

	/**
	 * Derives a function type from its parameter types
	 *
	 * @param <E> The compiler type of the function's source parameter
	 * @param <T> The compiler type of the function's product parameter
	 * @param srcType The run-time type of the function's source parameter
	 * @param destType The run-time type of the function's product parameter
	 * @return The type of a function with the given parameter types
	 */
	static <E, T> TypeToken<Function<? super E, T>> functionType(TypeToken<E> srcType, TypeToken<T> destType) {
		return new TypeToken<Function<? super E, T>>() {}.where(new TypeParameter<E>() {}, srcType.wrap()).where(new TypeParameter<T>() {},
			destType.wrap());
	}

	static class MapWithParent<E, I, T> implements Function<E, T> {
		private final Function<? super E, ? extends I> theParentMap;
		private final Function<? super I, ? extends T> theMap;

		MapWithParent(Function<? super E, ? extends I> parentMap, Function<? super I, ? extends T> map) {
			theParentMap = parentMap;
			theMap = map;
		}

		@Override
		public T apply(E source) {
			I intermediate = theParentMap.apply(source);
			T dest = mapIntermediate(intermediate);
			return dest;
		}

		public Function<? super E, ? extends I> getParentMap() {
			return theParentMap;
		}

		public T mapIntermediate(I source) {
			return theMap.apply(source);
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
		BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection);

		/**
		 * @param localElement The element in this flow
		 * @param sourceCollection The source collection to get source elements from
		 * @return The elements in the given collection that influence the given local element
		 */
		BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection);

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
			boolean first) throws UnsupportedOperationException, IllegalArgumentException;

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
	public static interface ActiveSetManager<E, I, T> extends ActiveCollectionManager<E, I, T> {
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
	 * The data flow equivalent of the {@link org.qommons.collect.ValueStoredCollection.RepairListener}
	 *
	 * @param <E> The type of values in the collection/flow
	 * @param <X> The type of the tracking data created by this listener
	 */
	public interface RepairListener<E, X> {
		/**
		 * @param element The element removed
		 * @return The tracking data for the element
		 * @see org.qommons.collect.ValueStoredCollection.RepairListener#removed(CollectionElement)
		 */
		X removed(DerivedCollectionElement<E> element);

		/**
		 * @param value The value previously {@link #removed(DerivedCollectionElement)} and not re-added
		 * @param data The tracking data from {@link #removed(DerivedCollectionElement)}
		 * @see org.qommons.collect.ValueStoredCollection.RepairListener#disposed(Object, Object)
		 */
		void disposed(E value, X data);

		/**
		 * @param element The element {@link #removed(DerivedCollectionElement) removed} and re-added to the collection/flow
		 * @param data The tracking data from {@link #removed(DerivedCollectionElement)}
		 * @see org.qommons.collect.ValueStoredCollection.RepairListener#transferred(CollectionElement, Object)
		 */
		void transferred(DerivedCollectionElement<E> element, X data);
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

		/**
		 * @param sourceEl The element that is possibly a source of this element
		 * @return Whether this element is derived from the given element
		 */
		boolean isDerivedFrom(ElementId sourceEl);

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
				public boolean isDerivedFrom(ElementId sourceEl) {
					return outer.isDerivedFrom(sourceEl);
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
	public static Transaction structureAffectedPassLockThroughToParent(CollectionOperation<?, ?, ?> parent, boolean write,
		Object cause) {
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

	static Transaction structureAffectedTryPassLockThroughToParent(CollectionOperation<?, ?, ?> parent, boolean write,
		Object cause) {
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

	/**
	 * An immutable structure with the configuration from a {@link CollectionDataFlow#filterMod(Consumer) filterMod} operation
	 *
	 * @param <T> The type of elements to filter modification for
	 */
	public static class ModFilterer<T> {
		private final String theUnmodifiableMessage;
		private final boolean areUpdatesAllowed;
		private final String theAddMessage;
		private final String theRemoveMessage;
		private final String theMoveMessage;
		private final Function<? super T, String> theAddFilter;
		private final Function<? super T, String> theRemoveFilter;

		/** @param options The mod-filtering options to copy */
		public ModFilterer(ModFilterBuilder<T> options) {
			theUnmodifiableMessage = options.getUnmodifiableMsg();
			this.areUpdatesAllowed = options.areUpdatesAllowed();
			theAddMessage = options.getAddMsg();
			theRemoveMessage = options.getRemoveMsg();
			theMoveMessage = options.getMoveMsg();
			theAddFilter = options.getAddMsgFn();
			theRemoveFilter = options.getRemoveMsgFn();
		}

		/** @return The message that this filter returns for modifications that are not prevented by other settings */
		public String getUnmodifiableMessage() {
			return theUnmodifiableMessage;
		}

		/**
		 * @return True if updates (sets where the new value is identical to the current value) are allowed even for an
		 *         {@link #getUnmodifiableMessage() unmodifiable} filter
		 */
		public boolean areUpdatesAllowed() {
			return areUpdatesAllowed;
		}

		/** @return The message that this filter returns for adds that are not prevented by the {@link #getAddFilter() add filter} */
		public String getAddMessage() {
			return theAddMessage;
		}

		/**
		 * @return The message that this filter returns for removals that are not prevented by the {@link #getRemoveFilter() remove filter}
		 */
		public String getRemoveMessage() {
			return theRemoveMessage;
		}

		/**
		 * @return The message function that tests values for addition to the collection and returns null if the addition is allowed, or a
		 *         reason code if not
		 */
		public Function<? super T, String> getAddFilter() {
			return theAddFilter;
		}

		/**
		 * @return The message function that tests values for removal from the collection and returns null if the removal is allowed, or a
		 *         reason code if not
		 */
		public Function<? super T, String> getRemoveFilter() {
			return theRemoveFilter;
		}

		/**
		 * @return null if any possible operation may be allowed by this filter, or the {@link #getUnmodifiableMessage() unmodifiable}
		 *         message if not
		 */
		public String isEnabled() {
			if (areUpdatesAllowed)
				return null;
			return theUnmodifiableMessage;
		}

		/**
		 * @param value The value to test
		 * @param oldValue Supplies the current value of the element, if relevant
		 * @return null if the value is acceptable to replace the old value in an element according to this filter's configuration, or a
		 *         reason code otherwise
		 */
		public String isAcceptable(T value, Supplier<T> oldValue) {
			String msg = null;
			if (isAddFiltered() || isRemoveFiltered() || (theUnmodifiableMessage != null && areUpdatesAllowed)) {
				T old = oldValue.get();
				if (old == value && areUpdatesAllowed) {
					// An update. These are treated differently. These can only be prevented explicitly.
				} else {
					// Non-updates are treated
					if (theRemoveFilter != null)
						msg = theRemoveFilter.apply(old);
					if (msg == null)
						msg = theRemoveMessage;
					if (msg == null && theAddFilter != null)
						msg = theAddFilter.apply(value);
					if (msg == null)
						msg = theAddMessage;
					if (msg == null)
						msg = theUnmodifiableMessage;
				}
			} else {
				// Not add- or remove-filtered, and don't care about updates, so no need to get the old value. Possibly unmodifiable.
				msg = theUnmodifiableMessage;
			}
			return msg;
		}

		/**
		 * @param value The value to set for an element
		 * @param oldValue Supplies the current value of the element, if relevant
		 * @throws UnsupportedOperationException If this filter prevents such an operation, regardless of the values involved
		 * @throws IllegalArgumentException If this filter prevents such an operation due to the value of either the new value or the
		 *         current value
		 */
		public void assertSet(T value, Supplier<T> oldValue) throws UnsupportedOperationException, IllegalArgumentException {
			String msg = null;
			if (isAddFiltered() || isRemoveFiltered() || (theUnmodifiableMessage != null && areUpdatesAllowed)) {
				T old = oldValue.get();
				if (old == value && areUpdatesAllowed) {
					// An update. These are treated differently. These can only be prevented explicitly.
				} else {
					if (theRemoveFilter != null)
						msg = theRemoveFilter.apply(old);
					if (msg != null)
						throw new IllegalArgumentException(msg);
					msg = theRemoveMessage;
					if (msg != null)
						throw new UnsupportedOperationException(msg);
					if (msg == null && theAddFilter != null)
						msg = theAddFilter.apply(value);
					if (msg != null)
						throw new IllegalArgumentException(msg);
					msg = theAddMessage;
					if (msg == null)
						msg = theUnmodifiableMessage;
					if (msg != null)
						throw new UnsupportedOperationException(msg);
				}
			} else {
				// Not add- or remove-filtered, and don't care about updates, so no need to get the old value. Possibly unmodifiable.
				msg = theUnmodifiableMessage;
				if (msg != null)
					throw new UnsupportedOperationException(msg);
			}
		}

		/**
		 * @return Whether this filter may prevent any adds <b>specifically</b> (not as a result of general {@link #getUnmodifiableMessage()
		 *         unmodifiability})
		 */
		public boolean isAddFiltered() {
			return theAddFilter != null || theAddMessage != null;
		}

		/**
		 * @return Whether this filter may prevent any removals <b>specifically</b> (not as a result of general
		 *         {@link #getUnmodifiableMessage() unmodifiability})
		 */
		public boolean isRemoveFiltered() {
			return theRemoveFilter != null || theRemoveMessage != null;
		}

		/**
		 * @param oldValue Supplies the current value of the element to remove, if relevant
		 * @return null if the element can be removed according to this filter's configuration, or a reason code otherwise
		 */
		public String canRemove(Supplier<T> oldValue) {
			String msg = null;
			if (theRemoveFilter != null)
				msg = theRemoveFilter.apply(oldValue.get());
			if (msg == null)
				msg = theRemoveMessage;
			if (msg == null)
				msg = theUnmodifiableMessage;
			return msg;
		}

		/**
		 * @param oldValue The current value of the element to remove, if relevant
		 * @throws UnsupportedOperationException If this filter prevents the operation
		 */
		public void assertRemove(Supplier<T> oldValue) throws UnsupportedOperationException {
			String msg = null;
			if (theRemoveFilter != null)
				msg = theRemoveFilter.apply(oldValue.get());
			if (msg == null)
				msg = theRemoveMessage;
			if (msg == null)
				msg = theUnmodifiableMessage;
			if (msg != null)
				throw new UnsupportedOperationException(msg);
		}

		/**
		 * @return null if this filter may allow some additions, or a reason code otherwise
		 * @see #getAddMessage()
		 * @see #getUnmodifiableMessage()
		 */
		public String canAdd() {
			if (theAddMessage != null)
				return theAddMessage;
			if (theUnmodifiableMessage != null)
				return theUnmodifiableMessage;
			return null;
		}

		/**
		 * @param value The value to add
		 * @return null if this filter allows the given value to be added, or a reason code otherwise
		 * @see #getAddFilter()
		 * @see #getAddMessage()
		 * @see #getUnmodifiableMessage()
		 */
		public String canAdd(T value) {
			String msg = null;
			if (theAddFilter != null)
				msg = theAddFilter.apply(value);
			if (msg == null && theAddMessage != null)
				msg = theAddMessage;
			if (msg == null && theUnmodifiableMessage != null)
				msg = theUnmodifiableMessage;
			return msg;
		}

		/**
		 * @param value The value to add
		 * @throws UnsupportedOperationException If this filter prevents the addition regardless of the value
		 * @throws IllegalArgumentException If this filter prevents the operation due to the value
		 */
		public void assertAdd(T value) throws UnsupportedOperationException, IllegalArgumentException {
			String msg = null;
			if (theAddFilter != null)
				msg = theAddFilter.apply(value);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			if (msg == null && theAddMessage != null)
				msg = theAddMessage;
			if (msg == null && theUnmodifiableMessage != null)
				msg = theUnmodifiableMessage;
			if (msg != null)
				throw new UnsupportedOperationException(msg);
		}

		/** @return null if this filter may allow some movement operations, or a reason code otherwise */
		public String canMove() {
			String msg = null;
			if (theMoveMessage != null)
				msg = theMoveMessage;
			if (msg == null && theUnmodifiableMessage != null)
				msg = theUnmodifiableMessage;
			return msg;
		}

		/** @throws UnsupportedOperationException If this filter does not allow movement operations */
		public void assertMovable() throws UnsupportedOperationException {
			String msg = null;
			if (theMoveMessage != null)
				msg = theMoveMessage;
			if (msg == null && theUnmodifiableMessage != null)
				msg = theUnmodifiableMessage;
			if (msg != null)
				throw new UnsupportedOperationException(msg);
		}

		/** @return True if this filter does not prevent any operations */
		public boolean isEmpty() {
			return theUnmodifiableMessage == null && theAddMessage == null && theRemoveMessage == null && theAddFilter == null
				&& theRemoveFilter == null && theMoveMessage==null;
		}

		@Override
		public String toString() {
			StringBuilder s = new StringBuilder();
			if (theAddFilter != null)
				s.append("addFilter:").append(theAddFilter).append(',');
			if (theAddMessage != null)
				s.append("noAdd:").append(theAddMessage).append(',');
			if (theRemoveFilter != null)
				s.append("removeFilter:").append(theRemoveFilter).append(',');
			if (theRemoveMessage != null)
				s.append("noRemove:").append(theRemoveMessage).append(',');
			if (theMoveMessage != null)
				s.append("noMove:").append(theMoveMessage).append(',');
			if (theUnmodifiableMessage != null)
				s.append("unmodifiable:").append(theUnmodifiableMessage).append('(').append(areUpdatesAllowed ? "" : "not ")
				.append("updatable)").append(',');
			if (s.length() > 0)
				s.deleteCharAt(s.length() - 1);
			else
				s.append("not filtered");
			return s.toString();
		}
	}

	/**
	 * An abstract {@link CollectionDataFlow} that delegates many of the derivation methods to standard implementations of this type
	 *
	 * @param <E> The source collection this flow is derived from
	 * @param <I> The type this flow's parent
	 * @param <T> This flow's type, i.e. the type of the collection that would be produced by {@link #collect()}
	 */
	public static abstract class AbstractDataFlow<E, I, T> implements CollectionDataFlow<E, I, T> {
		private final ObservableCollection<E> theSource;
		private final CollectionDataFlow<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private final Equivalence<? super T> theEquivalence;

		/**
		 * @param source The source collection
		 * @param parent The parent flow (may be null)
		 * @param targetType The type of this flow
		 * @param equivalence The equivalence of this flow
		 */
		protected AbstractDataFlow(ObservableCollection<E> source, CollectionDataFlow<E, ?, I> parent, TypeToken<T> targetType,
			Equivalence<? super T> equivalence) {
			theSource = source;
			theParent = parent;
			theTargetType = targetType;
			theEquivalence = equivalence;
		}

		/** @return The source collection */
		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		/** @return This flow's parent flow (may be null) */
		protected CollectionDataFlow<E, ?, I> getParent() {
			return theParent;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public CollectionDataFlow<E, T, T> reverse() {
			return new ReverseOp<>(theSource, this);
		}

		@Override
		public CollectionDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new FilterOp<>(theSource, this, filter);
		}

		@Override
		public <X> CollectionDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			return new IntersectionFlow<>(theSource, this, other, !include);
		}

		@Override
		public CollectionDataFlow<E, T, T> withEquivalence(Equivalence<? super T> equivalence) {
			return new EquivalenceSwitchOp<>(theSource, this, equivalence);
		}

		@Override
		public CollectionDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new RefreshOp<>(theSource, this, refresh);
		}

		@Override
		public CollectionDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new ElementRefreshOp<>(theSource, this, refresh);
		}

		@Override
		public <X> CollectionDataFlow<E, T, X> map(TypeToken<X> target, BiFunction<? super T, ? super X, ? extends X> map,
			Consumer<MapOptions<T, X>> options) {
			MapOptions<T, X> mapOptions = new MapOptions<>();
			options.accept(mapOptions);
			return new MapOp<>(theSource, this, target, map, new MapDef<>(mapOptions));
		}

		@Override
		public <X> CollectionDataFlow<E, T, X> combine(TypeToken<X> targetType,
			Function<CombinationPrecursor<T, X>, CombinedFlowDef<T, X>> combination) {
			CombinedFlowDef<T, X> def = combination.apply(new CombinationPrecursor<>());
			return new CombinedCollectionOp<>(theSource, this, targetType, def);
		}

		@Override
		public <X> CollectionDataFlow<E, ?, X> flatMap(TypeToken<X> target,
			Function<? super T, ? extends CollectionDataFlow<?, ?, ? extends X>> map) {
			return new FlattenedOp<>(theSource, this, target, map);
		}

		@Override
		public CollectionDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options) {
			ModFilterBuilder<T> filter = new ModFilterBuilder<>();
			options.accept(filter);
			return new ModFilteredOp<>(theSource, this, new ModFilterer<>(filter));
		}

		@Override
		public CollectionDataFlow<E, T, T> sorted(Comparator<? super T> compare) {
			return new SortedDataFlow<>(theSource, this, theEquivalence, compare);
		}

		@Override
		public DistinctDataFlow<E, T, T> distinct(Consumer<UniqueOptions> options) {
			SimpleUniqueOptions uo = new SimpleUniqueOptions(equivalence() instanceof Equivalence.ComparatorEquivalence);
			options.accept(uo);
			return new ObservableSetImpl.DistinctOp<>(theSource, this, equivalence(), uo.isUseFirst(), uo.isPreservingSourceOrder());
		}

		@Override
		public DistinctSortedDataFlow<E, T, T> distinctSorted(Comparator<? super T> compare, boolean alwaysUseFirst) {
			return new ObservableSortedSetImpl.DistinctSortedOp<>(theSource, this, compare, alwaysUseFirst);
		}

		@Override
		public <K> MultiMapFlow<K, T> groupBy(Function<? super CollectionDataFlow<E, I, T>, DistinctDataFlow<E, ?, K>> keyFlow,
			BiFunction<K, T, T> reverse) {
			DistinctDataFlow<E, ?, K> keys = keyFlow.apply(this);
			CollectionDataFlow<E, ?, T> values;
			AddKeyHolder.Default<K> addKey;
			if (reverse != null) {
				addKey = new AddKeyHolder.Default<>();
				values = gatherValues(addKey, reverse);
			} else {
				addKey = null;
				values = this;
			}
			if (keys instanceof DistinctSortedDataFlow)
				return new DefaultSortedMultiMapFlow<E, K, T, K, T>(null, getSource(), (DistinctSortedDataFlow<E, ?, K>) keys, values,
					addKey);
			else
				return new DefaultMultiMapFlow<>(null, getSource(), keys, values, addKey);
		}

		@Override
		public <K> SortedMultiMapFlow<K, T> groupSorted(
			Function<? super CollectionDataFlow<E, I, T>, DistinctSortedDataFlow<E, ?, K>> keyFlow, BiFunction<K, T, T> reverse) {
			DistinctSortedDataFlow<E, ?, K> keys = keyFlow.apply(this);
			CollectionDataFlow<E, ?, T> values;
			AddKeyHolder.Default<K> addKey;
			if (reverse != null) {
				addKey = new AddKeyHolder.Default<>();
				values = gatherValues(addKey, reverse);
			} else {
				addKey = null;
				values = this;
			}
			return new DefaultSortedMultiMapFlow<>(null, getSource(), keys, values, addKey);
		}

		private <K> CollectionDataFlow<E, ?, T> gatherValues(AddKeyHolder.Default<K> addKey, BiFunction<K, T, T> reverse) {
			return map(getTargetType(), v -> v, opts -> opts.withReverse(v -> {
				if (addKey.get() != null)
					return reverse.apply(addKey.get(), v);
				else
					return v;
			}));
		}

		@Override
		public ObservableCollection<T> collectPassive() {
			if (!supportsPassive())
				throw new UnsupportedOperationException("This flow does not support passive collection");
			return new PassiveDerivedCollection<>(getSource(), managePassive());
		}

		@Override
		public ObservableCollection<T> collectActive(Observable<?> until) {
			return new ActiveDerivedCollection<>(manageActive(), until);
		}
	}

	private static class SortedDataFlow<E, T> extends AbstractDataFlow<E, T, T> {
		private final Comparator<? super T> theCompare;

		SortedDataFlow(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, Equivalence<? super T> equivalence,
			Comparator<? super T> compare) {
			super(source, parent, parent.getTargetType(), equivalence);
			theCompare = compare;
		}

		/** @return The comparator used to re-order element values */
		@SuppressWarnings("unused")
		protected Comparator<? super T> getCompare() {
			return theCompare;
		}

		@Override
		public boolean supportsPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new SortedManager<>(getParent().manageActive(), theCompare);
		}
	}

	/**
	 * Implements {@link CollectionDataFlow}
	 *
	 * @param <E> The type of the source collection
	 */
	public static class BaseCollectionDataFlow<E> extends AbstractDataFlow<E, E, E> {
		/** @param source The source collection */
		protected BaseCollectionDataFlow(ObservableCollection<E> source) {
			super(source, null, source.getType(), source.equivalence());
		}

		@Override
		public boolean supportsPassive() {
			return true;
		}

		@Override
		public PassiveCollectionManager<E, ?, E> managePassive() {
			return new BaseCollectionPassThrough<>(getSource());
		}

		@Override
		public ActiveCollectionManager<E, ?, E> manageActive() {
			return new BaseCollectionManager<>(getSource());
		}

		@Override
		public ObservableCollection<E> collectPassive() {
			return getSource();
		}
	}

	private static class ReverseOp<E, T> extends AbstractDataFlow<E, T, T> {
		ReverseOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent) {
			super(source, parent, parent.getTargetType(), parent.equivalence());
		}

		@Override
		public boolean supportsPassive() {
			return getParent().supportsPassive();
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new PassiveReversedManager<>(getParent().managePassive());
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ActiveReversedManager<>(getParent().manageActive());
		}
	}

	private static class FilterOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Function<? super T, String> theFilter;

		FilterOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, Function<? super T, String> filter) {
			super(source, parent, parent.getTargetType(), parent.equivalence());
			theFilter = filter;
		}

		@Override
		public boolean supportsPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new FilteredCollectionManager<>(getParent().manageActive(), theFilter);
		}
	}

	private static class IntersectionFlow<E, T, X> extends AbstractDataFlow<E, T, T> {
		private final CollectionDataFlow<?, ?, X> theFilter;
		private final boolean isExclude;

		IntersectionFlow(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, CollectionDataFlow<?, ?, X> filter,
			boolean exclude) {
			super(source, parent, parent.getTargetType(), parent.equivalence());
			theFilter = filter;
			isExclude = exclude;
		}

		@Override
		public boolean supportsPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new IntersectionManager<>(getParent().manageActive(), theFilter, isExclude);
		}
	}

	private static class EquivalenceSwitchOp<E, T> extends AbstractDataFlow<E, T, T> {
		EquivalenceSwitchOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, Equivalence<? super T> equivalence) {
			super(source, parent, parent.getTargetType(), equivalence);
		}

		@Override
		public boolean supportsPassive() {
			return true;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new PassiveEquivalenceSwitchedManager<>(getParent().managePassive(), equivalence());
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ActiveEquivalenceSwitchedManager<>(getParent().manageActive(), equivalence());
		}
	}

	/**
	 * Implements {@link CollectionDataFlow#map(TypeToken, Function, Consumer)}
	 *
	 * @param <E> The type of the source collection
	 * @param <I> The target type of this flow's parent flow
	 * @param <T> The type of values produced by this flow
	 */
	protected static class MapOp<E, I, T> extends AbstractDataFlow<E, I, T> {
		private final BiFunction<? super I, ? super T, ? extends T> theMap;
		private final MapDef<I, T> theOptions;

		/**
		 * @param source The source collection
		 * @param parent This flow's parent (not null)
		 * @param target The type of this flow
		 * @param map The mapping function to produce this flow's values from its parent's
		 * @param options The mapping options governing certain aspects of this flow's behavior, e.g. caching
		 */
		protected MapOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, I> parent, TypeToken<T> target,
			BiFunction<? super I, ? super T, ? extends T> map, MapDef<I, T> options) {
			super(source, parent, target, mapEquivalence(parent.getTargetType(), parent.equivalence(), target, map, options));
			theMap = map;
			theOptions = options;
		}

		@Override
		public boolean supportsPassive() {
			if (theOptions.isCached())
				return false;
			return getParent().supportsPassive();
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new PassiveMappedCollectionManager<>(getParent().managePassive(), getTargetType(), src -> theMap.apply(src, null),
				equivalence(), theOptions);
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ActiveMappedCollectionManager<>(getParent().manageActive(), getTargetType(), theMap, equivalence(), theOptions);
		}
	}

	private static <I, T> Equivalence<? super T> mapEquivalence(TypeToken<I> srcType, Equivalence<? super I> equivalence,
		TypeToken<T> targetType, BiFunction<? super I, ? super T, ? extends T> map, MapDef<I, T> options) {
		if (options.getEquivalence() != null)
			return options.getEquivalence();
		else if (srcType.equals(targetType))
			return (Equivalence<? super T>) equivalence;
		else
			return Equivalence.DEFAULT;
	}

	/**
	 * Defines a combination of a single source collection with one or more observable values
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of elements in the resulting collection
	 */
	public static class CombinedCollectionOp<E, I, T> extends AbstractDataFlow<E, I, T> {
		private final CombinedFlowDef<I, T> theDef;

		/**
		 * @param source The source collection
		 * @param parent This flow's parent (not null)
		 * @param target The type of this flow
		 * @param def The combination definition used to produce this flow's values from its parent's and to govern certain aspects of this
		 *        flow's behavior, e.g. caching
		 */
		protected CombinedCollectionOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, I> parent, TypeToken<T> target,
			CombinedFlowDef<I, T> def) {
			super(source, parent, target,
				parent.getTargetType().equals(target) ? (Equivalence<? super T>) parent.equivalence() : Equivalence.DEFAULT);
			theDef = def;
		}

		@Override
		public boolean supportsPassive() {
			if (theDef.isCached())
				return false;
			return getParent().supportsPassive();
		}

		@Override
		public boolean prefersPassive() {
			return false; // I think it's better just to do active here
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new PassiveCombinedCollectionManager<>(getParent().managePassive(), getTargetType(), theDef);
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ActiveCombinedCollectionManager<>(getParent().manageActive(), getTargetType(), theDef);
		}
	}

	private static class RefreshOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Observable<?> theRefresh;

		RefreshOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, Observable<?> refresh) {
			super(source, parent, parent.getTargetType(), parent.equivalence());
			theRefresh = refresh;
		}

		@Override
		public boolean supportsPassive() {
			return getParent().supportsPassive();
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new PassiveRefreshingCollectionManager<>(getParent().managePassive(), theRefresh);
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ActiveRefreshingCollectionManager<>(getParent().manageActive(), theRefresh);
		}
	}

	private static class ElementRefreshOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Function<? super T, ? extends Observable<?>> theElementRefresh;

		ElementRefreshOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent,
			Function<? super T, ? extends Observable<?>> elementRefresh) {
			super(source, parent, parent.getTargetType(), parent.equivalence());
			theElementRefresh = elementRefresh;
		}

		@Override
		public boolean supportsPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ElementRefreshingCollectionManager<>(getParent().manageActive(), theElementRefresh);
		}
	}

	/**
	 * A flow produced from a {@link CollectionDataFlow#filterMod(Consumer) filterMod} operation
	 *
	 * @param <E> The type of the source collection
	 * @param <T> The type of this flow
	 */
	protected static class ModFilteredOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final ModFilterer<T> theOptions;

		/**
		 * @param source The source collection
		 * @param parent The parent flow
		 * @param options The modification filter options to enforce
		 */
		protected ModFilteredOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, ModFilterer<T> options) {
			super(source, parent, parent.getTargetType(), parent.equivalence());
			theOptions = options;
		}

		@Override
		public boolean supportsPassive() {
			return getParent().supportsPassive();
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new PassiveModFilteredManager<>(getParent().managePassive(), theOptions);
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ActiveModFilteredManager<>(getParent().manageActive(), theOptions);
		}
	}

	private static class FlattenedOp<E, I, T> extends AbstractDataFlow<E, I, T> {
		private final Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends T>> theMap;

		FlattenedOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends T>> map) {
			super(source, parent, targetType, Equivalence.DEFAULT);
			theMap = map;
		}

		@Override
		public boolean supportsPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new FlattenedManager<>(getParent().manageActive(), getTargetType(), theMap);
		}
	}

	/**
	 * Supports passive collection of a base collection flow
	 *
	 * @param <E> The type of the collection and therefore the flow
	 */
	public static class BaseCollectionPassThrough<E> implements PassiveCollectionManager<E, E, E> {
		private static final ConcurrentHashMap<TypeToken<?>, TypeToken<? extends Function<?, ?>>> thePassThroughFunctionTypes = new ConcurrentHashMap<>();

		private final ObservableCollection<E> theSource;
		private final ObservableValue<Function<? super E, E>> theFunctionValue;

		/** @param source The source collection */
		public BaseCollectionPassThrough(ObservableCollection<E> source) {
			theSource = source;

			TypeToken<E> srcType = theSource.getType();
			TypeToken<Function<? super E, E>> functionType = (TypeToken<Function<? super E, E>>) thePassThroughFunctionTypes
				.computeIfAbsent(srcType, st -> functionType(st, st));
			theFunctionValue = ObservableValue.of(functionType, LambdaUtils.identity());
		}

		@Override
		public Object getIdentity() {
			return theSource.getIdentity();
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
		public boolean isReversed() {
			return false;
		}

		@Override
		public ObservableValue<Function<? super E, E>> map() {
			return theFunctionValue;
		}

		@Override
		public String canReverse() {
			return null;
		}

		@Override
		public FilterMapResult<E, E> reverse(FilterMapResult<E, E> dest, boolean forAdd) {
			dest.result = dest.source;
			return dest;
		}

		@Override
		public boolean isRemoveFiltered() {
			return false;
		}

		@Override
		public MutableCollectionElement<E> map(MutableCollectionElement<E> mapped, Function<? super E, ? extends E> map) {
			return mapped;
		}

		@Override
		public BiTuple<E, E> map(E oldSource, E newSource, Function<? super E, ? extends E> map) {
			return new BiTuple<>(oldSource, newSource);
		}

		@Override
		public boolean isManyToOne() {
			return false;
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<E>> elements, E value) {
			theSource.setValue(//
				elements.stream().map(el -> el.getElementId()).collect(Collectors.toList()), value);
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
			return theSource.getIdentity();
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
		public BetterList<DerivedCollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(theSource.getElementsBySource(sourceEl, sourceCollection),
				el -> new BaseDerivedElement(theSource.mutableElement(el.getElementId())));
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<E> localElement, BetterCollection<?> sourceCollection) {
			return theSource.getSourceElements(((BaseDerivedElement) localElement).getElementId(), sourceCollection);
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
			return new BaseDerivedElement(
				theSource.mutableElement(theSource.move(//
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
			public boolean isDerivedFrom(ElementId sourceEl) {
				return source.getElementId().equals(sourceEl) || source.getElementId().isDerivedFrom(sourceEl);
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

	private static class PassiveReversedManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;

		PassiveReversedManager(PassiveCollectionManager<E, ?, T> parent) {
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
			return theParent.equivalence();
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
		public boolean isReversed() {
			return !theParent.isReversed();
		}

		@Override
		public ObservableValue<? extends Function<? super E, ? extends T>> map() {
			return theParent.map();
		}

		@Override
		public String canReverse() {
			return theParent.canReverse();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd) {
			return theParent.reverse(dest, forAdd);
		}

		@Override
		public boolean isRemoveFiltered() {
			return theParent.isRemoveFiltered();
		}

		@Override
		public MutableCollectionElement<T> map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map) {
			return theParent.map(element, map); // Don't reverse here--the passive collection takes care of it
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			return theParent.map(oldSource, newSource, map);
		}

		@Override
		public boolean isManyToOne() {
			return theParent.isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			theParent.setValue(//
				elements, value);
			// elements.stream().map(el -> el.reverse()).collect(Collectors.toList()), value);
		}
	}

	private static class ActiveReversedManager<E, T> implements ActiveCollectionManager<E, T, T> {
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
			return theParent.equivalence();
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
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(theParent.getElementsBySource(sourceEl.reverse(), sourceCollection), el -> el.reverse());
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection) {
			return theParent.getSourceElements(localElement.reverse(), sourceCollection);
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
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(theParent.getElementsBySource(sourceEl, sourceCollection), el -> new SortedElement(el, true));
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection) {
			return theParent.getSourceElements(((SortedElement) localElement).theParentEl, sourceCollection);
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			int afterComp = after == null ? 0 : theCompare.compare(after.get(), toAdd);
			if (afterComp > 0)
				return StdMsg.ILLEGAL_ELEMENT_POSITION;
			int beforeComp = before == null ? 0 : theCompare.compare(before.get(), toAdd);
			if (beforeComp < 0)
				return StdMsg.ILLEGAL_ELEMENT_POSITION;
			DerivedCollectionElement<T> requiredAfter = (after != null && afterComp == 0) ? ((SortedElement) after).theParentEl : null;
			DerivedCollectionElement<T> requiredBefore = (before != null && beforeComp == 0) ? ((SortedElement) before).theParentEl : null;
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
			DerivedCollectionElement<T> requiredAfter = (after != null && afterComp == 0) ? ((SortedElement) after).theParentEl : null;
			DerivedCollectionElement<T> requiredBefore = (before != null && beforeComp == 0) ? ((SortedElement) before).theParentEl : null;
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
			DerivedCollectionElement<T> requiredAfter = (after != null && afterComp == 0) ? ((SortedElement) after).theParentEl : null;
			DerivedCollectionElement<T> requiredBefore = (before != null && beforeComp == 0) ? ((SortedElement) before).theParentEl : null;
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
			DerivedCollectionElement<T> requiredAfter = (after != null && afterComp == 0) ? ((SortedElement) after).theParentEl : null;
			DerivedCollectionElement<T> requiredBefore = (before != null && beforeComp == 0) ? ((SortedElement) before).theParentEl : null;
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
								ObservableCollectionDataFlowImpl.removed(theListener, realOldValue, cause);
								theAccepter.accept(SortedElement.this, cause);
							} else {
								theValues.mutableNodeFor(theValueNode).set(new BiTuple<>(newValue, theParentEl));
								ObservableCollectionDataFlowImpl.update(theListener, realOldValue, newValue, cause);
							}
						}

						@Override
						public void removed(T value, Object cause) {
							T realOldValue = theValueNode.get().getValue1();
							theValues.mutableNodeFor(theValueNode).remove();
							ObservableCollectionDataFlowImpl.removed(theListener, realOldValue, cause);
						}
					});
				}
			}

			boolean isInCorrectOrder(T newValue, DerivedCollectionElement<T> parentEl) {
				BiTuple<T, DerivedCollectionElement<T>> tuple = new BiTuple<>(newValue, parentEl);
				BinaryTreeNode<BiTuple<T, DerivedCollectionElement<T>>> left = theValueNode.getClosest(true);
				BinaryTreeNode<BiTuple<T, DerivedCollectionElement<T>>> right = theValueNode.getClosest(false);
				return (left == null || theTupleCompare.compare(left.get(), tuple) <= 0)//
					&& (right == null || theTupleCompare.compare(tuple, right.get()) <= 0);
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				SortedElement sorted = (SortedElement) o;
				if (theValueNode != null && sorted.theValueNode != null)
					return theValueNode.compareTo(sorted.theValueNode);
				else { // Synthetic
					BiTuple<T, DerivedCollectionElement<T>> tuple1 = theValueNode != null ? theValueNode.get()
						: new BiTuple<>(theParentEl.get(), theParentEl);
					BiTuple<T, DerivedCollectionElement<T>> tuple2 = sorted.theValueNode != null ? sorted.theValueNode.get()
						: new BiTuple<>(sorted.theParentEl.get(), sorted.theParentEl);
					return theTupleCompare.compare(tuple1, tuple2);
				}
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public boolean isDerivedFrom(ElementId sourceEl) {
				return theParentEl.isDerivedFrom(sourceEl);
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

	private static class FilteredCollectionManager<E, T> implements ActiveCollectionManager<E, T, T> {
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
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(theParent.getElementsBySource(sourceEl, sourceCollection), el -> new FilteredElement(el, true, true));
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection) {
			return theParent.getSourceElements(((FilteredElement) localElement).theParentEl, sourceCollection);
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

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((FilteredElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public boolean isDerivedFrom(ElementId sourceEl) {
				return theParentEl.isDerivedFrom(sourceEl);
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

	private static class IntersectionManager<E, T, X> implements ActiveCollectionManager<E, T, T> {
		private class IntersectionElement {
			private final T value;
			final List<IntersectedCollectionElement> leftElements;
			final List<ElementId> rightElements;

			IntersectionElement(T value) {
				this.value = value;
				leftElements = new ArrayList<>();
				rightElements = new ArrayList<>();
			}

			boolean isPresent() {
				return rightElements.isEmpty() == isExclude;
			}

			void incrementRight(ElementId rightEl, Object cause) {
				boolean preEmpty = rightElements.isEmpty();
				rightElements.add(rightEl);
				if (preEmpty)
					presentChanged(cause);
			}

			void decrementRight(ElementId rightEl, Object cause) {
				rightElements.remove(rightEl);
				if (rightElements.isEmpty()) {
					presentChanged(cause);
					if (leftElements.isEmpty())
						theValues.remove(value);
				}
			}

			void addLeft(IntersectedCollectionElement element) {
				leftElements.add(element);
			}

			void removeLeft(IntersectedCollectionElement element) {
				leftElements.remove(element);
				if (leftElements.isEmpty() && rightElements.isEmpty())
					theValues.remove(value);
			}

			private void presentChanged(Object cause) {
				if (isPresent()) {
					for (IntersectedCollectionElement el : leftElements)
						theAccepter.accept(el, cause);
				} else {
					for (IntersectedCollectionElement el : leftElements)
						el.fireRemove(cause);
				}
			}
		}

		class IntersectedCollectionElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<T> theParentEl;
			private IntersectionElement intersection;
			private CollectionElementListener<T> theListener;

			IntersectedCollectionElement(DerivedCollectionElement<T> parentEl, IntersectionElement intersect, boolean synthetic) {
				theParentEl = parentEl;
				intersection = intersect;
				if (!synthetic)
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause) {
							if (theEquivalence.elementEquals(intersection.value, newValue)) {
								ObservableCollectionDataFlowImpl.update(theListener, oldValue, newValue, cause);
							} else {
								boolean oldPresent = intersection.isPresent();
								intersection.removeLeft(IntersectedCollectionElement.this);
								intersection = theValues.computeIfAbsent(newValue, v -> new IntersectionElement(newValue));
								intersection.addLeft(IntersectedCollectionElement.this);
								boolean newPresent = intersection.isPresent();
								if (oldPresent && newPresent)
									ObservableCollectionDataFlowImpl.update(theListener, oldValue, newValue, cause);
								else if (oldPresent && !newPresent) {
									ObservableCollectionDataFlowImpl.removed(theListener, oldValue, cause);
									theListener = null;
								} else if (!oldPresent && newPresent)
									theAccepter.accept(IntersectedCollectionElement.this, cause);
								else { // Wasn't present before, still isn't present. Nothing to do.
								}
							}
						}

						@Override
						public void removed(T value, Object cause) {
							if (intersection.isPresent())
								ObservableCollectionDataFlowImpl.removed(theListener, value, cause);
							intersection.removeLeft(IntersectedCollectionElement.this);
							intersection = null;
						}
					});
			}

			void fireRemove(Object cause) {
				removed(theListener, theParentEl.get(), cause);
				theListener = null;
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((IntersectedCollectionElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public boolean isDerivedFrom(ElementId sourceEl) {
				if (theParentEl.isDerivedFrom(sourceEl))
					return true;
				for (ElementId rightEl : intersection.rightElements)
					if (sourceEl.equals(rightEl))
						return true;
				for (ElementId rightEl : intersection.rightElements)
					if (rightEl.isDerivedFrom(sourceEl))
						return true;
				return false;
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
				String msg = theParentEl.isAcceptable(value);
				if (msg != null)
					return msg;
				if (theEquivalence.elementEquals(theParentEl.get(), value))
					return null;
				IntersectionElement intersect = theValues.get(value);

				boolean filterHas = intersect == null ? false : !intersect.rightElements.isEmpty();
				if (filterHas == isExclude)
					return StdMsg.ILLEGAL_ELEMENT;
				return null;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				if (!theEquivalence.elementEquals(theParentEl.get(), value)) {
					IntersectionElement intersect = theValues.get(value);
					boolean filterHas = intersect == null ? false : !intersect.rightElements.isEmpty();
					if (filterHas == isExclude)
						throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				}
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
			public boolean equals(Object o) {
				return o instanceof IntersectionManager.IntersectedCollectionElement
					&& theParentEl.compareTo(((IntersectedCollectionElement) o).theParentEl) == 0;
			}
		}

		private final ActiveCollectionManager<E, ?, T> theParent;
		private final ObservableCollection<X> theFilter;
		private final Equivalence<? super T> theEquivalence; // Make this a field since we'll need it often
		/** Whether a value's presence in the right causes the value in the left to be present (false) or absent (true) in the result */
		private final boolean isExclude;
		private Map<T, IntersectionElement> theValues;
		// The following two fields are needed because the values may mutate
		private Map<ElementId, IntersectionElement> theRightElementValues;

		private ElementAccepter<T> theAccepter;

		IntersectionManager(ActiveCollectionManager<E, ?, T> parent, CollectionDataFlow<?, ?, X> filter, boolean exclude) {
			theParent = parent;
			theFilter = filter.collect();
			theEquivalence = parent.equivalence();
			isExclude = exclude;
			theValues = theEquivalence.createMap();
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), isExclude ? "without" : "intersect", theFilter);
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public boolean isLockSupported() {
			return theParent.isLockSupported() || theFilter.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(Lockable.lockable(structureAffectedPassLockThroughToParent(theParent), write, cause),
				Lockable.lockable(theFilter));
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(Lockable.lockable(structureAffectedPassLockThroughToParent(theParent), write, cause),
				Lockable.lockable(theFilter));
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
			return el -> parentFinder.compareTo(((IntersectedCollectionElement) el).theParentEl);
		}

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			return BetterList.of(Stream.concat(//
				theParent.getElementsBySource(sourceEl, sourceCollection).stream()
				.map(el -> new IntersectedCollectionElement(el, null, true)), //
				theFilter.getElementsBySource(sourceEl, sourceCollection).stream().flatMap(el -> {
					IntersectionElement intEl = theRightElementValues.get(el.getElementId());
					return intEl == null ? Stream.empty() : intEl.leftElements.stream();
				}).distinct()));
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection) {
			return BetterList.of(Stream.concat(//
				theParent.getSourceElements(((IntersectedCollectionElement) localElement).theParentEl, sourceCollection).stream(), //
				((IntersectedCollectionElement) localElement).intersection.rightElements.stream().flatMap(//
					el -> theFilter.getSourceElements(el, sourceCollection).stream())//
				));
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			IntersectionElement intersect = theValues.get(toAdd);
			boolean filterHas = intersect == null ? false : !intersect.rightElements.isEmpty();
			if (filterHas == isExclude)
				return StdMsg.ILLEGAL_ELEMENT;
			return theParent.canAdd(toAdd, strip(after), strip(before));
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			IntersectionElement intersect = theValues.get(value);
			boolean filterHas = intersect == null ? false : !intersect.rightElements.isEmpty();
			if (filterHas == isExclude)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, strip(after), strip(before), first);
			return parentEl == null ? null : new IntersectedCollectionElement(parentEl, null, true);
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			return theParent.canMove(strip(valueEl), strip(after), strip(before));
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			return new IntersectedCollectionElement(theParent.move(strip(valueEl), strip(after), strip(before), first, afterRemove), null,
				true);
		}

		private DerivedCollectionElement<T> strip(DerivedCollectionElement<T> el) {
			return el == null ? null : ((IntersectedCollectionElement) el).theParentEl;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			IntersectionElement intersect = theValues.get(newValue);
			boolean filterHas = intersect == null ? false : !intersect.rightElements.isEmpty();
			if (filterHas == isExclude)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			theParent.setValues(elements.stream().map(el -> ((IntersectedCollectionElement) el).theParentEl).collect(Collectors.toList()),
				newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			listening.withConsumer((ObservableCollectionEvent<? extends X> evt) -> {
				// We're not modifying, but we want to obtain an exclusive lock
				// to ensure that nothing above or below us is firing events at the same time.
				try (Transaction t = theParent.lock(true, evt)) {
					IntersectionElement element;
					switch (evt.getType()) {
					case add:
						if (!theEquivalence.isElement(evt.getNewValue()))
							return;
						element = theValues.computeIfAbsent((T) evt.getNewValue(), v -> new IntersectionElement(v));
						element.incrementRight(evt.getElementId(), evt);
						theRightElementValues.put(evt.getElementId(), element);
						break;
					case remove:
						element = theRightElementValues.remove(evt.getElementId());
						if (element == null)
							return; // Must not have belonged to the flow's equivalence
						element.decrementRight(evt.getElementId(), evt);
						break;
					case set:
						element = theRightElementValues.get(evt.getElementId());
						if (element != null && theEquivalence.elementEquals(element.value, evt.getNewValue()))
							return; // No change;
						boolean newIsElement = equivalence().isElement(evt.getNewValue());
						if (element != null) {
							theRightElementValues.remove(evt.getElementId());
							element.decrementRight(evt.getElementId(), evt);
						}
						if (newIsElement) {
							element = theValues.computeIfAbsent((T) evt.getNewValue(), v -> new IntersectionElement(v));
							element.incrementRight(evt.getElementId(), evt);
							theRightElementValues.put(evt.getElementId(), element);
						}
						break;
					}
				}
			}, action -> theFilter.subscribe(action, fromStart).removeAll());
			theParent.begin(fromStart, (parentEl, cause) -> {
				IntersectionElement element = theValues.computeIfAbsent(parentEl.get(), v -> new IntersectionElement(v));
				IntersectedCollectionElement el = new IntersectedCollectionElement(parentEl, element, false);
				element.addLeft(el);
				if (element.isPresent())
					onElement.accept(el, cause);
			}, listening);
		}
	}

	private static class PassiveEquivalenceSwitchedManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;
		private final Equivalence<? super T> theEquivalence;

		PassiveEquivalenceSwitchedManager(PassiveCollectionManager<E, ?, T> parent, Equivalence<? super T> equivalence) {
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
		public boolean isContentControlled() {
			return theParent.isContentControlled();
		}

		@Override
		public boolean isReversed() {
			return theParent.isReversed();
		}

		@Override
		public ObservableValue<? extends Function<? super E, ? extends T>> map() {
			return theParent.map();
		}

		@Override
		public String canReverse() {
			return theParent.canReverse();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd) {
			return theParent.reverse(dest, forAdd);
		}

		@Override
		public boolean isRemoveFiltered() {
			return theParent.isRemoveFiltered();
		}

		@Override
		public MutableCollectionElement<T> map(MutableCollectionElement<E> mapped, Function<? super E, ? extends T> map) {
			return theParent.map(mapped, map);
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			return theParent.map(oldSource, newSource, map);
		}

		@Override
		public boolean isManyToOne() {
			return theParent.isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			theParent.setValue(elements, value);
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
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			return theParent.getElementsBySource(sourceEl, sourceCollection);
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection) {
			return theParent.getSourceElements(localElement, sourceCollection);
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

	static abstract class AbstractMappingManager<E, I, T> implements CollectionOperation<E, I, T> {
		private final CollectionOperation<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private final XformOptions.XformDef theOptions;

		protected AbstractMappingManager(CollectionOperation<E, ?, I> parent, TypeToken<T> targetType, XformOptions.XformDef options) {
			theParent = parent;
			theTargetType = targetType;
			theOptions = options;
		}

		protected CollectionOperation<E, ?, I> getParent() {
			return theParent;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		protected XformOptions.XformDef getOptions() {
			return theOptions;
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
			return !isReversible() || theOptions.isOneToMany() || theParent.isContentControlled();
		}

		protected abstract boolean isReversible();

		protected abstract boolean isElementReversible();

		protected abstract T map(I value, T previousValue);

		protected abstract I reverse(T value);

		protected abstract String elementReverse(I source, T newValue, boolean replace);

		protected abstract void doParentMultiSet(Collection<AbstractMappedElement> elements, I newValue);

		protected void setElementsValue(Collection<?> elements, T newValue) throws UnsupportedOperationException, IllegalArgumentException {
			Collection<AbstractMappedElement> remaining;
			if (isElementReversible()) {
				remaining = new ArrayList<>();
				for (AbstractMappedElement el : (Collection<AbstractMappedElement>) elements) {
					if (elementReverse(el.getParentValue(), newValue, true) == null)
						remaining.add(el);
				}
			} else
				remaining = (Collection<AbstractMappedElement>) elements;
			if (remaining.isEmpty())
				return;
			if (theOptions.isCached()) {
				I oldValue = null;
				boolean first = true, allUpdates = true, allIdenticalUpdates = true;
				for (AbstractMappedElement el : remaining) {
					allUpdates &= equivalence().elementEquals(el.getValue(), newValue);
					allIdenticalUpdates &= allUpdates;
					if (!allUpdates)
						break;
					if (allIdenticalUpdates) {
						I elOldValue = el.getCachedSource();
						if (first) {
							oldValue = elOldValue;
							first = false;
						} else
							allIdenticalUpdates &= theParent.equivalence().elementEquals(oldValue, elOldValue);
					}
				}
				if (allIdenticalUpdates) {
					doParentMultiSet(remaining, oldValue);
					return;
				} else if (allUpdates) {
					for (AbstractMappedElement el : remaining)
						el.setParent(el.getCachedSource());
					return;
				}
			}
			if (isReversible()) {
				I reversed = reverse(newValue);
				if (!equivalence().elementEquals(map(reversed, newValue), newValue))
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				doParentMultiSet(remaining, reversed);
			} else
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		protected abstract class AbstractMappedElement {
			abstract T getValue();

			abstract I getParentValue();

			abstract I getCachedSource();

			protected abstract String isParentAcceptable(I value);

			abstract void setParent(I parentValue);

			public T mapForElement(I source, T value) {
				return map(source, value);
			}

			public I reverseForElement(T source) {
				return reverse(source);
			}

			protected String isEnabledLocal() {
				if (isElementReversible())
					return null;
				// If we're caching, updates are enabled even without a reverse map
				if (!isReversible() && !theOptions.isCached())
					return StdMsg.UNSUPPORTED_OPERATION;
				return null;
			}

			protected String isAcceptable(T value) {
				String msg = null;
				if (isElementReversible()) {
					msg = elementReverse(getParentValue(), value, false);
					if (msg == null)
						return null;
				}
				I reversed;
				if (theOptions.isCached() && equivalence().elementEquals(getValue(), value)) {
					reversed = getCachedSource();
				} else {
					if (!isReversible())
						return StdMsg.UNSUPPORTED_OPERATION;
					reversed = reverseForElement(value);
					if (!equivalence().elementEquals(mapForElement(reversed, value), value))
						return StdMsg.ILLEGAL_ELEMENT;
				}
				// If the element reverse is set, it should get the final word on the error message
				if (msg == null)
					msg = isParentAcceptable(reversed);
				return msg;
			}

			protected void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				if (isElementReversible()) {
					if (elementReverse(getParentValue(), value, true) == null)
						return;
				}
				I reversed;
				if (theOptions.isCached() && equivalence().elementEquals(getValue(), value))
					reversed = getCachedSource();
				else if (!isReversible())
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				else {
					reversed = reverseForElement(value);
					if (!equivalence().elementEquals(mapForElement(reversed, value), value))
						throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				}
				setParent(reversed);
			}
		}
	}

	private static abstract class AbstractPassiveMappingManager<E, I, T> extends AbstractMappingManager<E, I, T>
	implements PassiveCollectionManager<E, I, T> {
		AbstractPassiveMappingManager(PassiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType, XformOptions.XformDef options) {
			super(parent, targetType, options);
		}

		@Override
		protected PassiveCollectionManager<E, ?, I> getParent() {
			return (PassiveCollectionManager<E, ?, I>) super.getParent();
		}

		@Override
		public boolean isReversed() {
			return getParent().isReversed();
		}

		@Override
		protected boolean isElementReversible() {
			return false;
		}

		@Override
		protected String elementReverse(I source, T newValue, boolean replace) {
			throw new IllegalStateException();
		}

		@Override
		protected void doParentMultiSet(Collection<AbstractMappedElement> elements, I newValue) {
			getParent().setValue(elements.stream().map(el -> ((PassiveMappedElement) el).getParentEl()).collect(Collectors.toList()),
				newValue);
		}

		@Override
		public String canReverse() {
			if (!isReversible())
				return StdMsg.UNSUPPORTED_OPERATION;
			return getParent().canReverse();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd) {
			if (!isReversible())
				return dest.reject(StdMsg.UNSUPPORTED_OPERATION, true);
			T value = dest.source;
			FilterMapResult<I, E> intermediate = dest.map(this::reverse);
			if (intermediate.isAccepted() && !equivalence().elementEquals(map(intermediate.source, null), value))
				return dest.reject(StdMsg.ILLEGAL_ELEMENT, true);
			return (FilterMapResult<T, E>) getParent().reverse(intermediate, forAdd);
		}

		@Override
		public boolean isRemoveFiltered() {
			return getParent().isRemoveFiltered();
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			if (oldSource == newSource) {
				if (!getOptions().isFireIfUnchanged())
					return null;
				if (!getOptions().isReEvalOnUpdate()) {
					T newDest = map.apply(newSource);
					return new BiTuple<>(newDest, newDest);
				}
			}
			MapWithParent<E, I, T> mwp = (MapWithParent<E, I, T>) map;
			BiTuple<I, I> interm = getParent().map(oldSource, newSource, mwp.getParentMap());
			if (interm == null)
				return null;
			if (interm.getValue1() == interm.getValue2()) {
				if (!getOptions().isFireIfUnchanged())
					return null;
				if (!getOptions().isReEvalOnUpdate()) {
					T newDest = mwp.mapIntermediate(interm.getValue2());
					return new BiTuple<>(newDest, newDest);
				}
			}
			T v1 = mwp.mapIntermediate(interm.getValue1());
			T v2 = mwp.mapIntermediate(interm.getValue2());
			return new BiTuple<>(v1, v2);
		}

		@Override
		public boolean isManyToOne() {
			return getOptions().isManyToOne() || getParent().isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			setElementsValue(elements, value);
		}

		@Override
		public abstract PassiveMappedElement map(MutableCollectionElement<E> source, Function<? super E, ? extends T> map);

		protected abstract class PassiveMappedElement extends AbstractMappedElement implements MutableCollectionElement<T> {
			private final MutableCollectionElement<I> theParentEl;

			protected PassiveMappedElement(MutableCollectionElement<I> parentEl) {
				theParentEl = parentEl;
			}

			protected MutableCollectionElement<I> getParentEl() {
				return theParentEl;
			}

			@Override
			public ElementId getElementId() {
				return theParentEl.getElementId();
			}

			@Override
			public T get() {
				return mapForElement(theParentEl.get(), null);
			}

			@Override
			public BetterCollection<T> getCollection() {
				return null;
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
				super.set(value);
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
			T getValue() {
				return mapForElement(theParentEl.get(), null);
			}

			@Override
			I getParentValue() {
				return theParentEl.get();
			}

			@Override
			I getCachedSource() {
				throw new IllegalStateException();
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
			public String canAdd(T value, boolean before) {
				String msg = canReverse();
				if (msg != null)
					return msg;
				I intermVal = reverseForElement(value);
				return theParentEl.canAdd(intermVal, before);
			}

			@Override
			public ElementId add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				String msg = canReverse();
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				I intermVal = reverseForElement(value);
				return theParentEl.add(intermVal, before);
			}

			@Override
			public String toString() {
				return theParentEl.toString();
			}
		}
	}

	static abstract class AbstractActiveMappingManager<E, I, T> extends AbstractMappingManager<E, I, T>
	implements ActiveCollectionManager<E, I, T> {
		protected AbstractActiveMappingManager(ActiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			XformOptions.XformDef options) {
			super(parent, targetType, options);
		}

		@Override
		protected ActiveCollectionManager<E, ?, I> getParent() {
			return (ActiveCollectionManager<E, ?, I>) super.getParent();
		}

		@Override
		public boolean isLockSupported() {
			return getParent().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return getParent().lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return getParent().tryLock(write, cause);
		}

		@Override
		public boolean clear() {
			return getParent().clear();
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			if (!isReversible() || getOptions().isManyToOne())
				return null;
			I reversed = reverse(value);
			if (!equivalence().elementEquals(map(reversed, value), value))
				return null;
			Comparable<DerivedCollectionElement<I>> pef = getParent().getElementFinder(reversed);
			if (pef == null)
				return null;
			return el -> pef.compareTo(((ActiveMappedElement) el).getParentEl());
		}

		protected abstract ActiveMappedElement map(DerivedCollectionElement<I> parentEl, boolean synthetic);

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(getParent().getElementsBySource(sourceEl, sourceCollection), el -> map(el, true));
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection) {
			return getParent().getSourceElements(((ActiveMappedElement) localElement).getParentEl(), sourceCollection);
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			if (!isReversible())
				return StdMsg.UNSUPPORTED_OPERATION;
			I reversed = reverse(toAdd);
			if (!equivalence().elementEquals(map(reversed, toAdd), toAdd))
				return StdMsg.ILLEGAL_ELEMENT;
			return getParent().canAdd(reversed, strip(after), strip(before));
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			if (!isReversible())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			I reversed = reverse(value);
			if (!equivalence().elementEquals(map(reversed, value), value))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			DerivedCollectionElement<I> parentEl = getParent().addElement(reversed, strip(after), strip(before), first);
			return parentEl == null ? null : map(parentEl, true);
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
			getParent().setValues(elements.stream().map(el -> ((ActiveMappedElement) el).getParentEl()).collect(Collectors.toList()),
				newValue);
		}

		private DerivedCollectionElement<I> strip(DerivedCollectionElement<T> el) {
			return el == null ? null : ((ActiveMappedElement) el).getParentEl();
		}

		protected abstract class ActiveMappedElement extends AbstractMappedElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<I> theParentEl;
			CollectionElementListener<T> theListener;
			T theValue;
			final XformOptions.XformCacheHandler<I, T> theCacheHandler;

			ActiveMappedElement(DerivedCollectionElement<I> parentEl, boolean synthetic) {
				theParentEl = parentEl;
				if (!synthetic) {
					theCacheHandler = getOptions().createCacheHandler(new XformOptions.XformCacheHandlingInterface<I, T>() {
						@Override
						public BiFunction<? super I, ? super T, ? extends T> map() {
							return ActiveMappedElement.this::mapForElement;
						}

						@Override
						public Transaction lock() {
							// No need to lock, as modifications only come from one source
							return Transaction.NONE;
						}

						@Override
						public T getDestCache() {
							return theValue;
						}

						@Override
						public void setDestCache(T value) {
							theValue = value;
						}
					});
					// Populate the initial values if these are needed
					if (getOptions().isCached() || !getOptions().isFireIfUnchanged()) {
						I srcVal = parentEl.get();
						theCacheHandler.initialize(srcVal);
						if (getOptions().isCached())
							theValue = mapForElement(srcVal, null);
					}
					theParentEl.setListener(new CollectionElementListener<I>() {
						@Override
						public void update(I oldSource, I newSource, Object cause) {
							BiTuple<T, T> values = theCacheHandler.handleChange(oldSource, newSource);
							if (values != null)
								ObservableCollectionDataFlowImpl.update(theListener, values.getValue1(), values.getValue2(), cause);
						}

						@Override
						public void removed(I value, Object cause) {
							T val = getOptions().isCached() ? theValue : mapForElement(value, null);
							ActiveMappedElement.this.removed();
							ObservableCollectionDataFlowImpl.removed(theListener, val, cause);
							theListener = null;
							theCacheHandler.initialize(null);
						}
					});
				} else {
					theCacheHandler = null;
					if (getOptions().isCached())
						theValue = mapForElement(parentEl.get(), null);
				}
			}

			protected void removed() {}

			protected DerivedCollectionElement<I> getParentEl() {
				return theParentEl;
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			T getValue() {
				return getOptions().isCached() ? theValue : mapForElement(theParentEl.get(), null);
			}

			@Override
			I getCachedSource() {
				return theCacheHandler.getSourceCache();
			}

			@Override
			I getParentValue() {
				return theParentEl.get();
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
				return theParentEl.compareTo(((ActiveMappedElement) o).theParentEl);
			}

			@Override
			public boolean isDerivedFrom(ElementId sourceEl) {
				return theParentEl.isDerivedFrom(sourceEl);
			}

			@Override
			public T get() {
				return getValue();
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
				super.set(value);
			}

			@Override
			public String canRemove() {
				return theParentEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theParentEl.remove();
			}
		}
	}

	private static class PassiveMappedCollectionManager<E, I, T> extends AbstractPassiveMappingManager<E, I, T> {
		private final Function<? super I, ? extends T> theMap;
		private final Equivalence<? super T> theEquivalence;

		PassiveMappedCollectionManager(PassiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends T> map, Equivalence<? super T> equivalence, MapDef<I, T> options) {
			super(parent, targetType, options);
			theMap = map;
			theEquivalence = equivalence;
		}

		@Override
		protected MapDef<I, T> getOptions() {
			return (MapDef<I, T>) super.getOptions();
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "map", theMap);
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public ObservableValue<Function<? super E, T>> map() {
			return getParent().map().map(parentMap -> new MapWithParent<>(parentMap, theMap));
		}

		@Override
		protected boolean isReversible() {
			return getOptions().getReverse() != null;
		}

		@Override
		protected T map(I value, T previousValue) {
			return theMap.apply(value);
		}

		@Override
		protected I reverse(T value) {
			return getOptions().getReverse().apply(value);
		}

		@Override
		public MappedElement map(MutableCollectionElement<E> source, Function<? super E, ? extends T> map) {
			return new MappedElement(source, map);
		}

		class MappedElement extends PassiveMappedElement {
			MappedElement(MutableCollectionElement<E> source, Function<? super E, ? extends T> map) {
				super(getParent().map(source, ((MapWithParent<E, I, I>) map).getParentMap()));
			}
		}
	}

	/**
	 * An {@link ActiveCollectionManager active manager} produced by a {@link CollectionDataFlow#map(TypeToken, Function) mapping} operation
	 *
	 * @param <E> The type of the source collection
	 * @param <I> The type of the parent flow
	 * @param <T> The type of this manager
	 */
	public static class ActiveMappedCollectionManager<E, I, T> extends AbstractActiveMappingManager<E, I, T> {
		private final BiFunction<? super I, ? super T, ? extends T> theMap;
		private final Equivalence<? super T> theEquivalence;

		/**
		 * @param parent The parent manager
		 * @param targetType The type of this manager
		 * @param map The mapping function to produce this manager's values from the source values
		 * @param equivalence The equivalence for this manager
		 * @param options The mapping options governing some of the manager's behavior, e.g. caching
		 */
		public ActiveMappedCollectionManager(ActiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			BiFunction<? super I, ? super T, ? extends T> map, Equivalence<? super T> equivalence, MapDef<I, T> options) {
			super(parent, targetType, options);
			theMap = map;
			theEquivalence = equivalence;
		}

		@Override
		protected MapDef<I, T> getOptions() {
			return (MapDef<I, T>) super.getOptions();
		}

		@Override
		protected MappedElement map(DerivedCollectionElement<I> parentEl, boolean synthetic) {
			return new MappedElement(parentEl, synthetic);
		}

		@Override
		protected boolean isReversible() {
			return getOptions().getReverse() != null;
		}

		@Override
		protected boolean isElementReversible() {
			return getOptions().getElementReverse() != null;
		}

		@Override
		protected T map(I value, T previousValue) {
			return theMap.apply(value, previousValue);
		}

		@Override
		protected I reverse(T value) {
			return getOptions().getReverse().apply(value);
		}

		@Override
		protected String elementReverse(I source, T newValue, boolean replace) {
			return getOptions().getElementReverse().setElement(source, newValue, replace);
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "map", theMap);
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			setElementsValue(elements, newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			getParent().begin(fromStart, (parentEl, cause) -> {
				onElement.accept(new MappedElement(parentEl, false), cause);
			}, listening);
		}

		/** A {@link DerivedCollectionElement} implementation for {@link ActiveMappedCollectionManager} */
		public class MappedElement extends ActiveMappedElement {
			MappedElement(DerivedCollectionElement<I> parentEl, boolean synthetic) {
				super(parentEl, synthetic);
			}

			@Override
			public String toString() {
				return String.valueOf(get());
			}
		}
	}

	private static class PassiveCombinedCollectionManager<E, I, T> extends AbstractPassiveMappingManager<E, I, T> {
		PassiveCombinedCollectionManager(PassiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType, CombinedFlowDef<I, T> def) {
			super(parent, targetType, def);
		}

		@Override
		protected CombinedFlowDef<I, T> getOptions() {
			return (CombinedFlowDef<I, T>) super.getOptions();
		}

		@Override
		protected boolean isReversible() {
			return getOptions().getReverse() != null;
		}

		@Override
		protected T map(I value, T previousValue) {
			return getOptions().getCombination().apply(new Combination.CombinedValues<I>() {
				@Override
				public I getElement() {
					return value;
				}

				@Override
				public <X> X get(ObservableValue<X> arg) {
					return arg.get();
				}
			}, previousValue);
		}

		@Override
		protected I reverse(T value) {
			return getOptions().getReverse().apply(new Combination.CombinedValues<T>() {
				@Override
				public T getElement() {
					return value;
				}

				@Override
				public <X> X get(ObservableValue<X> arg) {
					return arg.get();
				}
			});
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "map", getOptions().getIdentity());
		}

		@Override
		public Equivalence<? super T> equivalence() {
			if (getTargetType().equals(getParent().getTargetType()))
				return (Equivalence<? super T>) getParent().equivalence();
			else
				return Equivalence.DEFAULT;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(Lockable.lockable(getParent(), write, cause), getOptions().getArgs());
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(Lockable.lockable(getParent(), write, cause), getOptions().getArgs());
		}

		private Transaction lockArgs() {
			return Lockable.lockAll(getOptions().getArgs());
		}

		@Override
		public ObservableValue<? extends Function<? super E, ? extends T>> map() {
			ObservableValue<? extends Function<? super E, ? extends I>> parentMap = getParent().map();
			return new ObservableValue<Function<? super E, T>>() {
				/** Can't imagine why this would ever be used, but we'll fill it out I guess */
				private TypeToken<Function<? super E, T>> theType;

				@Override
				public Object getIdentity() {
					return Identifiable.wrap(parentMap.getIdentity(), "combine", getOptions().getIdentity());
				}

				@Override
				public long getStamp() {
					return parentMap.getStamp() ^ Stamped.compositeStamp(getOptions().getArgs(), Stamped::getStamp);
				}

				@Override
				public TypeToken<Function<? super E, T>> getType() {
					if (theType == null)
						theType = functionType((TypeToken<E>) parentMap.getType().resolveType(Function.class.getTypeParameters()[0]),
							getTargetType());
					return theType;
				}

				@Override
				public Transaction lock() {
					Transaction parentLock = parentMap.lock();
					Transaction valueLock = lockArgs();
					return () -> {
						valueLock.close();
						parentLock.close();
					};
				}

				@Override
				public Function<? super E, T> get() {
					Function<? super E, ? extends I> parentMapVal = parentMap.get();
					Map<ObservableValue<?>, ObservableValue<?>> values = new IdentityHashMap<>();
					for (ObservableValue<?> v : getOptions().getArgs())
						values.put(v, v);

					return new CombinedMap(parentMapVal, null, values);
				}

				@Override
				public Observable<ObservableValueEvent<Function<? super E, T>>> changes() {
					Observable<? extends ObservableValueEvent<? extends Function<? super E, ? extends I>>> parentChanges = parentMap
						.changes();
					return new Observable<ObservableValueEvent<Function<? super E, T>>>() {
						@Override
						public Object getIdentity() {
							return Identifiable.wrap(parentMap.changes().getIdentity(), "combine", getOptions().getIdentity());
						}

						@Override
						public boolean isSafe() {
							return false;
						}

						@Override
						public Subscription subscribe(Observer<? super ObservableValueEvent<Function<? super E, T>>> observer) {
							CombinedMap[] currentMap = new PassiveCombinedCollectionManager.CombinedMap[1];
							Subscription parentSub = parentChanges
								.act(new Consumer<ObservableValueEvent<? extends Function<? super E, ? extends I>>>() {
									@Override
									public void accept(ObservableValueEvent<? extends Function<? super E, ? extends I>> parentEvt) {
										if (parentEvt.isInitial()) {
											currentMap[0] = new CombinedMap(parentEvt.getNewValue(), null, new IdentityHashMap<>());
											ObservableValueEvent<Function<? super E, T>> evt = createInitialEvent(currentMap[0], null);
											try (Transaction t = Causable.use(evt)) {
												observer.onNext(evt);
											}
											return;
										}
										try (Transaction valueLock = lockArgs()) {
											CombinedMap oldMap = currentMap[0];
											currentMap[0] = new CombinedMap(parentEvt.getNewValue(), null, oldMap.theValues);
											ObservableValueEvent<Function<? super E, T>> evt2 = createChangeEvent(oldMap, currentMap[0],
												parentEvt);
											try (Transaction evtT = Causable.use(evt2)) {
												observer.onNext(evt2);
											}
										}
									}
								});
							Subscription[] argSubs = new Subscription[getOptions().getArgs().size()];
							int a = 0;
							for (ObservableValue<?> arg : getOptions().getArgs()) {
								int argIndex = a++;
								argSubs[argIndex] = arg.changes().act(new Consumer<ObservableValueEvent<?>>() {
									@Override
									public void accept(ObservableValueEvent<?> argEvent) {
										if (argEvent.isInitial()) {
											((Map<ObservableValue<?>, SimpleSupplier>) currentMap[0].theValues).put(arg,
												new SimpleSupplier(argEvent.getNewValue()));
											return;
										}
										try (Transaction t = lock()) {
											CombinedMap oldMap = currentMap[0];
											Map<ObservableValue<?>, SimpleSupplier> newValues = new IdentityHashMap<>(
												(Map<ObservableValue<?>, SimpleSupplier>) oldMap.theValues);
											newValues.put(arg, new SimpleSupplier(argEvent.getNewValue()));
											currentMap[0] = new CombinedMap(oldMap.getParentMap(), null, newValues);
											ObservableValueEvent<Function<? super E, T>> evt = createChangeEvent(oldMap, currentMap[0],
												argEvent);
											try (Transaction evtT = Causable.use(evt)) {
												observer.onNext(evt);
											}
										}
									}
								});
							}
							return () -> {
								try (Transaction t = lock()) {
									for (int i = 0; i < argSubs.length; i++)
										argSubs[i].unsubscribe();
									parentSub.unsubscribe();
								}
							};
						}

						@Override
						public Transaction lock() {
							return Lockable.lockAll(getOptions().getArgs());
						}

						@Override
						public Transaction tryLock() {
							return Lockable.tryLockAll(getOptions().getArgs());
						}
					};
				}

				@Override
				public Observable<ObservableValueEvent<Function<? super E, T>>> noInitChanges() {
					return changes().noInit();
				}
			};
		}

		@Override
		public boolean isRemoveFiltered() {
			return false;
		}

		@Override
		public CombinedElement map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map) {
			return new CombinedElement(element, map);
		}

		class SimpleSupplier implements Supplier<Object> {
			final Object value;

			SimpleSupplier(Object value) {
				this.value = value;
			}

			@Override
			public Object get() {
				return value;
			}
		}

		class CombinedElement extends PassiveMappedElement {
			private final CombinedMap theCombinedMap;

			CombinedElement(MutableCollectionElement<E> source, Function<? super E, ? extends T> map) {
				super(getParent().map(source, ((CombinedMap) map).getParentMap()));
				theCombinedMap = (CombinedMap) map;
			}

			@Override
			public T mapForElement(I source, T value) {
				return theCombinedMap.mapIntermediate(source);
			}

			@Override
			public I reverseForElement(T source) {
				return theCombinedMap.reverse(source);
			}
		}

		class CombinedMap extends MapWithParent<E, I, T> {
			final Map<ObservableValue<?>, ? extends Supplier<?>> theValues;

			CombinedMap(Function<? super E, ? extends I> parentMap, Function<? super I, ? extends T> map,
				Map<ObservableValue<?>, ? extends Supplier<?>> values) {
				super(parentMap, map);
				theValues = values;
			}

			@Override
			public T mapIntermediate(I interm) {
				return getOptions().getCombination().apply(new Combination.CombinedValues<I>() {
					@Override
					public I getElement() {
						return interm;
					}

					@Override
					public <X> X get(ObservableValue<X> arg) {
						Supplier<?> holder = theValues.get(arg);
						if (holder == null)
							throw new IllegalArgumentException("Unrecognized value: " + arg);
						return (X) holder.get();
					}
				}, null);
			}

			I reverse(T dest) {
				return getOptions().getReverse().apply(new Combination.CombinedValues<T>() {
					@Override
					public T getElement() {
						return dest;
					}

					@Override
					public <X> X get(ObservableValue<X> arg) {
						Supplier<?> holder = theValues.get(arg);
						if (holder == null)
							throw new IllegalArgumentException("Unrecognized value: " + arg);
						return (X) holder.get();
					}
				});
			}
		}
	}

	private static class ActiveCombinedCollectionManager<E, I, T> extends AbstractActiveMappingManager<E, I, T> {
		private final Map<ObservableValue<?>, XformOptions.XformCacheHandler<Object, Void>> theArgs;

		// Need to keep track of these to update them when the combined values change
		private final BetterSortedSet<CombinedElement> theElements;

		ActiveCombinedCollectionManager(ActiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType, CombinedFlowDef<I, T> def) {
			super(parent, targetType, def);
			theArgs = new HashMap<>();
			for (ObservableValue<?> arg : def.getArgs())
				theArgs.put(arg, getOptions().createCacheHandler(new XformOptions.XformCacheHandlingInterface<Object, Void>() {
					@Override
					public BiFunction<? super Object, ? super Void, ? extends Void> map() {
						return (v, o) -> null;
					}

					@Override
					public Transaction lock() {
						// Should not be called, though
						return ActiveCombinedCollectionManager.this.lock(true, null);
					}

					@Override
					public Void getDestCache() {
						return null;
					}

					@Override
					public void setDestCache(Void value) {}
				}));

			theElements = new BetterTreeSet<>(false, CombinedElement::compareTo);
		}

		@Override
		protected CombinedFlowDef<I, T> getOptions() {
			return (CombinedFlowDef<I, T>) super.getOptions();
		}

		@Override
		protected CombinedElement map(DerivedCollectionElement<I> parentEl, boolean synthetic) {
			return new CombinedElement(parentEl, synthetic);
		}

		@Override
		protected boolean isReversible() {
			return getOptions().getReverse() != null;
		}

		@Override
		protected boolean isElementReversible() {
			return false;
		}

		@Override
		protected T map(I value, T previousValue) {
			return getOptions().getCombination().apply(new Combination.CombinedValues<I>() {
				@Override
				public I getElement() {
					return value;
				}

				@Override
				public <V> V get(ObservableValue<V> arg) {
					return getArgValue(arg);
				}
			}, previousValue);
		}

		@Override
		protected I reverse(T value) {
			return getOptions().getReverse().apply(new Combination.CombinedValues<T>() {
				@Override
				public T getElement() {
					return value;
				}

				@Override
				public <V> V get(ObservableValue<V> arg) {
					return getArgValue(arg);
				}
			});
		}

		@Override
		protected String elementReverse(I source, T newValue, boolean replace) {
			throw new IllegalStateException();
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "map", getOptions().getIdentity());
		}

		@Override
		public Equivalence<? super T> equivalence() {
			if (getTargetType().equals(getParent().getTargetType()))
				return (Equivalence<? super T>) getParent().equivalence();
			else
				return Equivalence.DEFAULT;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(Lockable.lockable(getParent(), write, cause), getOptions().getArgs());
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(Lockable.lockable(getParent(), write, cause), getOptions().getArgs());
		}

		private Transaction lockArgs() {
			return Lockable.lockAll(getOptions().getArgs());
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			setElementsValue(elements, newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			for (Map.Entry<ObservableValue<?>, XformOptions.XformCacheHandler<Object, Void>> arg : theArgs.entrySet()) {
				XformOptions.XformCacheHandler<Object, Void> holder = arg.getValue();
				listening.withConsumer((ObservableValueEvent<?> evt) -> {
					if (evt.isInitial()) {
						holder.initialize(evt.getNewValue());
						return;
					}
					Object oldValue = getOptions().isCached() ? holder.getSourceCache() : evt.getOldValue();
					Ternian update = holder.isUpdate(evt.getOldValue(), evt.getNewValue());
					if (update == Ternian.NONE)
						return; // No change, no event
					try (Transaction t = lock(false, null)) {
						// The old values are not needed if we're caching each element value
						Object[] source = getOptions().isCached() ? null : new Object[1];
						Combination.CombinedValues<I> oldValues = getOptions().isCached() ? null : getCopy(source, arg.getKey(), oldValue);
						// The order of update here may be different than the order in the derived collection
						// It's a lot of work to keep the elements in order (since the order may change),
						// so we'll just let order of addition be good enough
						for (CombinedElement el : theElements)
							el.updated(src -> {
								source[0] = src;
								return oldValues;
							}, evt, update.value);
					}
				}, action -> arg.getKey().changes().act(action));
			}
			getParent().begin(fromStart, (parentEl, cause) -> {
				try (Transaction t = lockArgs()) {
					CombinedElement el = new CombinedElement(parentEl, false);
					onElement.accept(el, cause);
				}
			}, listening);
		}

		private <V> V getArgValue(ObservableValue<V> arg) {
			XformOptions.XformCacheHandler<Object, Void> holder = theArgs.get(arg);
			if (holder == null)
				throw new IllegalArgumentException("Unrecognized value: " + arg);
			if (getOptions().isCached())
				return (V) holder.getSourceCache();
			else
				return arg.get();
		}

		private Combination.CombinedValues<I> getCopy(Object[] source, ObservableValue<?> replaceArg, Object value) {
			Map<ObservableValue<?>, Object> theValues = new HashMap<>();
			for (Map.Entry<ObservableValue<?>, XformOptions.XformCacheHandler<Object, Void>> holder : theArgs.entrySet()) {
				if (holder.getKey() == replaceArg)
					theValues.put(holder.getKey(), value);
				else if (getOptions().isCached())
					theValues.put(holder.getKey(), holder.getValue().getSourceCache());
				else
					theValues.put(holder.getKey(), holder.getKey().get());
			}
			return new Combination.CombinedValues<I>() {
				@Override
				public I getElement() {
					return (I) source[0];
				}

				@Override
				public <X> X get(ObservableValue<X> arg) {
					return (X) theValues.get(arg);
				}
			};
		}

		private class CombinedElement extends ActiveMappedElement {
			private final ElementId theStoredId;

			CombinedElement(DerivedCollectionElement<I> parentEl, boolean synthetic) {
				super(parentEl, synthetic);
				theStoredId = synthetic ? null : theElements.addElement(this, false).getElementId();
			}

			void updated(Function<I, Combination.CombinedValues<I>> oldValues, Object cause, boolean isUpdate) {
				I parentValue = getOptions().isCached() ? theCacheHandler.getSourceCache() : getParentEl().get();
				Function<? super I, ? extends T> oldMap = oldSrc -> getOptions().getCombination()//
					.apply(//
						oldValues.apply(oldSrc), null);
				BiTuple<T, T> values = theCacheHandler.handleChange(parentValue, oldMap, parentValue, isUpdate);
				if (values != null)
					ObservableCollectionDataFlowImpl.update(theListener, values.getValue1(), values.getValue2(), cause);
			}

			@Override
			protected void removed() {
				theElements.mutableElement(theStoredId).remove();
			}

			@Override
			public String toString() {
				return getParentEl().toString();
			}
		}
	}

	private static class PassiveRefreshingCollectionManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;
		private final Observable<?> theRefresh;

		PassiveRefreshingCollectionManager(PassiveCollectionManager<E, ?, T> parent, Observable<?> refresh) {
			theParent = parent;
			theRefresh = refresh;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "refresh", theRefresh.getIdentity());
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
			return theParent.isLockSupported() && theRefresh.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(Lockable.lockable(theParent, write, cause), theRefresh);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(Lockable.lockable(theParent, write, cause), theRefresh);
		}

		@Override
		public boolean isContentControlled() {
			return theParent.isContentControlled();
		}

		@Override
		public boolean isReversed() {
			return theParent.isReversed();
		}

		@Override
		public ObservableValue<? extends Function<? super E, ? extends T>> map() {
			return theParent.map().refresh(theRefresh);
		}

		@Override
		public String canReverse() {
			return theParent.canReverse();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd) {
			return theParent.reverse(dest, forAdd);
		}

		@Override
		public boolean isRemoveFiltered() {
			return theParent.isRemoveFiltered();
		}

		@Override
		public MutableCollectionElement<T> map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map) {
			return theParent.map(element, map);
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			return theParent.map(oldSource, newSource, map);
		}

		@Override
		public boolean isManyToOne() {
			return theParent.isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			theParent.setValue(elements, value);
		}
	}

	private static class ActiveRefreshingCollectionManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;
		private final Observable<?> theRefresh;
		private final BetterList<RefreshingElement> theElements;

		ActiveRefreshingCollectionManager(ActiveCollectionManager<E, ?, T> parent, Observable<?> refresh) {
			theParent = parent;
			theRefresh = refresh;
			theElements = new BetterTreeList<>(false);
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "refresh", theRefresh.getIdentity());
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
			return theParent.isLockSupported() && theRefresh.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(Lockable.lockable(theParent, write, cause), theRefresh);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(Lockable.lockable(theParent, write, cause), theRefresh);
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			Comparable<DerivedCollectionElement<T>> parentFinder = theParent.getElementFinder(value);
			if (parentFinder == null)
				return null;
			return el -> parentFinder.compareTo(((RefreshingElement) el).theParentEl);
		}

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(theParent.getElementsBySource(sourceEl, sourceCollection), el -> new RefreshingElement(el, true));
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection) {
			return theParent.getSourceElements(((RefreshingElement) localElement).theParentEl, sourceCollection);
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			return theParent.canAdd(toAdd, strip(after), strip(before));
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
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, strip(after), strip(before), first);
			return parentEl == null ? null : new RefreshingElement(parentEl, true);
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			return theParent.canMove(strip(valueEl), strip(after), strip(before));
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			return new RefreshingElement(theParent.move(strip(valueEl), strip(after), strip(before), first, afterRemove), true);
		}

		private DerivedCollectionElement<T> strip(DerivedCollectionElement<T> el) {
			return el == null ? null : ((RefreshingElement) el).theParentEl;
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			theParent.setValues(//
				elements.stream().map(el -> ((RefreshingElement) el).theParentEl).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theParent.begin(fromStart, (parentEl, cause) -> {
				// Make sure the refresh doesn't fire while we're firing notifications from the parent change
				try (Transaction t = theRefresh.lock()) {
					onElement.accept(new RefreshingElement(parentEl, false), cause);
				}
			}, listening);
			listening.withConsumer((Object r) -> {
				// Make sure the parent doesn't fire while we're firing notifications from the refresh
				try (Transaction t = theParent.lock(false, r)) {
					// Refreshing should be done in element order
					Collections.sort(theElements);
					CollectionElement<RefreshingElement> el = theElements.getTerminalElement(true);
					while (el != null) {
						// But now need to re-set the correct element ID for each element
						el.get().theElementId = el.getElementId();
						el.get().refresh(r);
						el = theElements.getAdjacentElement(el.getElementId(), true);
					}
				}
			}, theRefresh::act);
		}

		private class RefreshingElement implements DerivedCollectionElement<T> {
			final DerivedCollectionElement<T> theParentEl;
			private ElementId theElementId;
			private CollectionElementListener<T> theListener;

			RefreshingElement(DerivedCollectionElement<T> parent, boolean synthetic) {
				theParentEl = parent;
				if (!synthetic) {
					theElementId = theElements.addElement(this, false).getElementId();
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause) {
							try (Transaction t = theRefresh.lock()) {
								ObservableCollectionDataFlowImpl.update(theListener, oldValue, newValue, cause);
							}
						}

						@Override
						public void removed(T value, Object cause) {
							theElements.mutableElement(theElementId).remove();
							try (Transaction t = theRefresh.lock()) {
								ObservableCollectionDataFlowImpl.removed(theListener, value, cause);
							}
						}
					});
				} else
					theElementId = null;
			}

			void refresh(Object cause) {
				T value = get();
				ObservableCollectionDataFlowImpl.update(theListener, value, value, cause);
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((RefreshingElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public boolean isDerivedFrom(ElementId sourceEl) {
				return theParentEl.isDerivedFrom(sourceEl);
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
			public String toString() {
				return theParentEl.toString();
			}
		}
	}

	private static class ElementRefreshingCollectionManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private class RefreshHolder {
			private final ElementId theElementId;
			private final Subscription theSub;
			final BetterCollection<RefreshingElement> elements;

			RefreshHolder(Observable<?> refresh) {
				theElementId = theRefreshObservables.putEntry(refresh, this, false).getElementId();
				elements = new BetterTreeList<>(false);
				theSub = theListening.withConsumer(r -> {
					try (Transaction t = Lockable.lockAll(Lockable.lockable(theLock, true),
						Lockable.lockable(theParent, false, null))) {
						for (RefreshingElement el : elements)
							el.refresh(r);
					}
				}, action -> refresh.act(action));
			}

			void remove(ElementId element) {
				elements.mutableElement(element).remove();
				if (elements.isEmpty()) {
					theSub.unsubscribe();
					theRefreshObservables.mutableEntry(theElementId).remove();
				}
			}
		}

		private final ActiveCollectionManager<E, ?, T> theParent;
		private final Function<? super T, ? extends Observable<?>> theRefresh;
		private final BetterMap<Observable<?>, RefreshHolder> theRefreshObservables;
		private WeakListening theListening;
		private final ReentrantReadWriteLock theLock;

		ElementRefreshingCollectionManager(ActiveCollectionManager<E, ?, T> parent, Function<? super T, ? extends Observable<?>> refresh) {
			theParent = parent;
			theRefresh = refresh;
			theRefreshObservables = BetterHashMap.build().unsafe().buildMap();
			theLock = new ReentrantReadWriteLock();
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "refresh", theRefresh);
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
			return true;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			// The purpose of the refresh lock is solely to prevent simultaneous refresh events,
			// or any refresh events that would violate the contract of a held lock
			// If this lock method will obtain any exclusive locks, then locking the refresh lock is unnecessary,
			// because incoming refresh updates obtain a read lock on the parent
			if (write)
				return theParent.lock(write, cause);
			else
				return Lockable.lockAll(Lockable.lockable(theParent, write, cause), Lockable.lockable(theLock, false));
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			// The purpose of the refresh lock is solely to prevent simultaneous refresh events,
			// or any refresh events that would violate the contract of a held lock
			// If this lock method will obtain any exclusive locks, then locking the refresh lock is unnecessary,
			// because incoming refresh updates obtain a read lock on the parent
			if (write)
				return theParent.tryLock(write, cause);
			else
				return Lockable.tryLockAll(Lockable.lockable(theParent, write, cause), Lockable.lockable(theLock, false));
		}

		Transaction lockRefresh(boolean exclusive) {
			return Lockable.lock(theLock, exclusive);
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
			return el -> parentFinder.compareTo(((RefreshingElement) el).theParentEl);
		}

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(theParent.getElementsBySource(sourceEl, sourceCollection), el -> new RefreshingElement(el));
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection) {
			return theParent.getSourceElements(((RefreshingElement) localElement).theParentEl, sourceCollection);
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			return theParent.canAdd(toAdd, strip(after), strip(before));
		}

		@Override
		public boolean clear() {
			return theParent.clear();
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, strip(after), strip(before), first);
			return parentEl == null ? null : new RefreshingElement(parentEl);
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			return theParent.canMove(strip(valueEl), strip(after), strip(before));
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			return new RefreshingElement(theParent.move(strip(valueEl), strip(after), strip(before), first, afterRemove));
		}

		private DerivedCollectionElement<T> strip(DerivedCollectionElement<T> el) {
			return el == null ? null : ((RefreshingElement) el).theParentEl;
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			theParent.setValues(elements.stream().map(el -> ((RefreshingElement) el).theParentEl).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theListening = listening;
			theParent.begin(fromStart, (parentEl, cause) -> {
				try (Transaction t = lockRefresh(false)) {
					onElement.accept(new RefreshingElement(parentEl), cause);
				}
			}, listening);
		}

		private class RefreshingElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<T> theParentEl;
			private Observable<?> theCurrentRefresh;
			private RefreshHolder theCurrentHolder;
			private ElementId theElementId;
			private CollectionElementListener<T> theListener;
			private boolean isInstalled;

			RefreshingElement(DerivedCollectionElement<T> parentEl) {
				theParentEl = parentEl;
			}

			/**
			 * Called when the value of the element changes. Handles the case where the element's refresh observable changes as a result.
			 *
			 * @param value The new value for the element
			 */
			void updated(T value) {
				Observable<?> newRefresh = theRefresh.apply(value);
				if (newRefresh == theCurrentRefresh)
					return;

				// Refresh is different, need to remove from old refresh and add to new
				if (theCurrentHolder != null) { // Remove from old refresh if non-null
					theCurrentHolder.remove(theElementId);
					theElementId = null;
					theCurrentHolder = null;
				}
				theCurrentRefresh = newRefresh;
				if (newRefresh != null) {
					RefreshHolder newHolder = theRefreshObservables.get(newRefresh);
					if (newHolder == null)
						newHolder = new RefreshHolder(newRefresh); // Adds itself
					theCurrentHolder = newHolder;
					theElementId = newHolder.elements.addElement(this, false).getElementId();
				}
			}

			private void installOrUninstall() {
				if (!isInstalled && theListener != null) {
					isInstalled = true;
					updated(theParentEl.get()); // Subscribe to the initial refresh value
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause) {
							try (Transaction t = lockRefresh(false)) {
								ObservableCollectionDataFlowImpl.update(theListener, oldValue, newValue, cause);
								updated(newValue);
							}
						}

						@Override
						public void removed(T value, Object cause) {
							try (Transaction t = lockRefresh(false)) {
								if (theCurrentHolder != null) { // Remove from old refresh if non-null
									theCurrentHolder.remove(theElementId);
									theElementId = null;
									theCurrentHolder = null;
								}
								theCurrentRefresh = null;
								ObservableCollectionDataFlowImpl.removed(theListener, value, cause);
							}
						}
					});
				} else if (isInstalled && theListener == null) {
					theParentEl.setListener(null);
					if (theCurrentHolder != null) { // Remove from old refresh if non-null
						theCurrentHolder.remove(theElementId);
						theElementId = null;
						theCurrentHolder = null;
					}
					theCurrentRefresh = null;
				}
			}

			private void refresh(Object cause) {
				T value = get();
				ObservableCollectionDataFlowImpl.update(theListener, value, value, cause);
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((RefreshingElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
				installOrUninstall();
			}

			@Override
			public boolean isDerivedFrom(ElementId sourceEl) {
				return theParentEl.isDerivedFrom(sourceEl);
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
		}
	}

	private static class PassiveModFilteredManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;
		private final ModFilterer<T> theFilter;

		PassiveModFilteredManager(PassiveCollectionManager<E, ?, T> parent, ModFilterer<T> filter) {
			theParent = parent;
			theFilter = filter;
		}

		@Override
		public Object getIdentity() {
			return theParent.getIdentity();
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
			return theParent.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theParent.tryLock(write, cause);
		}

		@Override
		public boolean isContentControlled() {
			return !theFilter.isEmpty() || theParent.isContentControlled();
		}

		@Override
		public boolean isReversed() {
			return theParent.isReversed();
		}

		@Override
		public ObservableValue<? extends Function<? super E, ? extends T>> map() {
			return theParent.map();
		}

		@Override
		public String canReverse() {
			String msg = theFilter.canAdd();
			if (msg != null)
				return msg;
			return theParent.canReverse();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd) {
			if (forAdd) {
				dest.maybeReject(theFilter.canAdd(dest.source), true);
				if (!dest.isAccepted())
					return dest;
			}
			return theParent.reverse(dest, forAdd);
		}

		@Override
		public boolean isRemoveFiltered() {
			return theFilter.isRemoveFiltered() || theFilter.getUnmodifiableMessage() != null || theParent.isRemoveFiltered();
		}

		@Override
		public String canMove() {
			return theFilter.canMove();
		}

		@Override
		public MutableCollectionElement<T> map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map) {
			return new ModFilteredElement(element, map);
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			return theParent.map(oldSource, newSource, map);
		}

		@Override
		public boolean isManyToOne() {
			return theParent.isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			theParent.setValue(elements.stream().map(el -> ((ModFilteredElement) el).theParentMapped).collect(Collectors.toList()), value);
		}

		private class ModFilteredElement implements MutableCollectionElement<T> {
			private final MutableCollectionElement<T> theParentMapped;

			ModFilteredElement(MutableCollectionElement<E> element, Function<? super E, ? extends T> map) {
				theParentMapped = theParent.map(element, map);
			}

			@Override
			public BetterCollection<T> getCollection() {
				return null;
			}

			@Override
			public ElementId getElementId() {
				return theParentMapped.getElementId();
			}

			@Override
			public T get() {
				return theParentMapped.get();
			}

			@Override
			public String isEnabled() {
				String msg = theFilter.isEnabled();
				if (msg == null)
					msg = theParentMapped.isEnabled();
				return msg;
			}

			@Override
			public String isAcceptable(T value) {
				String msg = theFilter.isAcceptable(value, //
					this::get);
				if (msg == null)
					msg = theParentMapped.isAcceptable(value);
				return msg;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				theFilter.assertSet(value, //
					this::get);
				theParentMapped.set(value);
			}

			@Override
			public String canRemove() {
				String msg = theFilter.canRemove(//
					this::get);
				if (msg == null)
					msg = theParentMapped.canRemove();
				return msg;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theFilter.assertRemove(//
					this::get);
				theParentMapped.remove();
			}

			@Override
			public String canAdd(T value, boolean before) {
				String msg = theFilter.canAdd(value);
				if (msg == null)
					msg = theParentMapped.canAdd(value, before);
				return msg;
			}

			@Override
			public ElementId add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				theFilter.assertAdd(value);
				return theParentMapped.add(value, before);
			}

			@Override
			public String toString() {
				return theParentMapped.toString();
			}
		}
	}

	private static class ActiveModFilteredManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;

		private final ModFilterer<T> theFilter;

		ActiveModFilteredManager(ActiveCollectionManager<E, ?, T> parent, ModFilterer<T> filter) {
			theParent = parent;
			theFilter = filter;
		}

		@Override
		public Object getIdentity() {
			return theParent.getIdentity();
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
			return theParent.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theParent.tryLock(write, cause);
		}

		@Override
		public boolean clear() {
			if (!theFilter.isRemoveFiltered() && theFilter.getUnmodifiableMessage() == null)
				return theParent.clear();
			if (theFilter.theUnmodifiableMessage != null || theFilter.theRemoveMessage != null)
				return true;
			else
				return false;
		}

		@Override
		public boolean isContentControlled() {
			return !theFilter.isEmpty() || theParent.isContentControlled();
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			Comparable<DerivedCollectionElement<T>> parentFinder = theParent.getElementFinder(value);
			if (parentFinder == null)
				return null;
			return el -> parentFinder.compareTo(((ModFilteredElement) el).theParentEl);
		}

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			return QommonsUtils.map2(theParent.getElementsBySource(sourceEl, sourceCollection), el -> new ModFilteredElement(el));
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection) {
			return theParent.getSourceElements(((ModFilteredElement) localElement).theParentEl, sourceCollection);
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			String msg = theFilter.canAdd(toAdd);
			if (msg == null)
				msg = theParent.canAdd(toAdd, strip(after), strip(before));
			return msg;
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			theFilter.assertAdd(value);
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, strip(after), strip(before), first);
			return parentEl == null ? null : new ModFilteredElement(parentEl);
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			String msg = theFilter.canMove();
			if (msg == null)
				return theParent.canMove(//
					strip(valueEl), strip(after), strip(before));
			else if ((after == null || valueEl.compareTo(after) >= 0) && (before == null || valueEl.compareTo(before) <= 0))
				return null;
			return msg;
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			if (theFilter.canMove() != null && (after == null || valueEl.compareTo(after) >= 0)
				&& (before == null || valueEl.compareTo(before) <= 0))
				return valueEl;
			theFilter.assertMovable();
			return new ModFilteredElement(theParent.move(//
				strip(valueEl), strip(after), strip(before), first, afterRemove));
		}

		private DerivedCollectionElement<T> strip(DerivedCollectionElement<T> el) {
			return el == null ? null : ((ModFilteredElement) el).theParentEl;
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			for (DerivedCollectionElement<T> el : elements)
				theFilter.assertSet(newValue, el::get);
			theParent.setValues(//
				elements.stream().map(el -> ((ModFilteredElement) el).theParentEl).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theParent.begin(fromStart, (parentEl, cause) -> onElement.accept(new ModFilteredElement(parentEl), cause), listening);
		}

		private class ModFilteredElement implements DerivedCollectionElement<T> {
			final DerivedCollectionElement<T> theParentEl;

			ModFilteredElement(DerivedCollectionElement<T> parentEl) {
				theParentEl = parentEl;
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((ModFilteredElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theParentEl.setListener(listener);
			}

			@Override
			public boolean isDerivedFrom(ElementId sourceEl) {
				return theParentEl.isDerivedFrom(sourceEl);
			}

			@Override
			public T get() {
				return theParentEl.get();
			}

			@Override
			public String isEnabled() {
				String msg = theFilter.isEnabled();
				if (msg == null)
					msg = theParentEl.isEnabled();
				return msg;
			}

			@Override
			public String isAcceptable(T value) {
				String msg = theFilter.isAcceptable(value, //
					this::get);
				if (msg == null)
					msg = theParentEl.isAcceptable(value);
				return msg;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				theFilter.assertSet(value, //
					this::get);
				theParentEl.set(value);
			}

			@Override
			public String canRemove() {
				String msg = theFilter.canRemove(//
					this::get);
				if (msg == null)
					msg = theParentEl.canRemove();
				return msg;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theFilter.assertRemove(//
					this::get);
				theParentEl.remove();
			}

			@Override
			public String toString() {
				return theParentEl.toString();
			}
		}
	}

	private static class FlattenedManager<E, I, T> implements ActiveCollectionManager<E, I, T> {
		private final ActiveCollectionManager<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private final Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends T>> theMap;

		private ElementAccepter<T> theAccepter;
		private WeakListening theListening;
		private final BetterTreeList<FlattenedHolder> theOuterElements;
		private final ReentrantReadWriteLock theLock;

		public FlattenedManager(ActiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends T>> map) {
			theParent = parent;
			theTargetType = targetType;
			theMap = map;

			theOuterElements = new BetterTreeList<>(false);
			theLock = new ReentrantReadWriteLock();
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "flatten", theMap);
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public boolean isLockSupported() {
			return true; // No way to know if any of the outer collection's elements will ever support locking
		}

		Transaction lockLocal() {
			Lock localLock = theLock.writeLock();
			localLock.lock();
			return localLock::unlock;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			/* No operations against this manager can affect the parent collection, but only its content collections */
			return Lockable.lockAll(Lockable.lockable(theParent), () -> theOuterElements, //
				oe -> Lockable.lockable(oe.manager, write, cause));
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			/* No operations against this manager can affect the parent collection, but only its content collections */
			return Lockable.tryLockAll(Lockable.lockable(theParent), () -> theOuterElements, //
				oe -> Lockable.lockable(oe.manager, write, cause));
		}

		@Override
		public boolean isContentControlled() {
			try (Transaction t = theParent.lock(false, null)) {
				boolean anyControlled = false;
				for (FlattenedHolder outerEl : theOuterElements) {
					if (outerEl.manager == null)
						continue;
					anyControlled |= outerEl.manager.isContentControlled();
				}
				return anyControlled;
			}
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			return null;
		}

		@Override
		public BetterList<DerivedCollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			try (Transaction t = lock(false, null)) {
				BetterList<DerivedCollectionElement<I>> parentEBS = theParent.getElementsBySource(sourceEl, sourceCollection);
				return BetterList.of(Stream.concat(//
					parentEBS.stream().flatMap(outerEl -> {
						BinaryTreeNode<FlattenedHolder> fh = theOuterElements.getRoot()
							.findClosest(fhEl -> outerEl.compareTo(fhEl.get().theParentEl), true, true, OptimisticContext.TRUE);
						return fh == null ? Stream.empty() : fh.get().theElements.stream();
					}), //
					// Unfortunately, I think the only way to do this reliably is to ask every outer element
					theOuterElements.stream().flatMap(holder -> {
						for (DerivedCollectionElement<I> fromParent : parentEBS)
							if (fromParent.compareTo(holder.theParentEl) == 0)
								return Stream.empty();
						return holder.manager.getElementsBySource(sourceEl, sourceCollection).stream().flatMap(innerEl -> {
							BinaryTreeNode<FlattenedElement> fe = holder.theElements.getRoot()
								.findClosest(
									feEl -> ((DerivedCollectionElement<T>) innerEl)
									.compareTo((DerivedCollectionElement<T>) feEl.get().theParentEl),
									true, true, OptimisticContext.TRUE);
							if (fe != null && fe.get().theParentEl.equals(innerEl))
								return Stream.of(fe.get());
							else
								return Stream.empty();
						});
					})//
					));
			}
		}

		@Override
		public BetterList<ElementId> getSourceElements(DerivedCollectionElement<T> localElement, BetterCollection<?> sourceCollection) {
			FlattenedElement flatEl = (FlattenedElement) localElement;
			return BetterList.of(Stream.concat(//
				theParent.getSourceElements(flatEl.theHolder.theParentEl, sourceCollection).stream(), //
				((ActiveCollectionManager<?, ?, T>) ((FlattenedElement) localElement).theHolder.manager)
				.getSourceElements((DerivedCollectionElement<T>) flatEl.theParentEl, sourceCollection).stream()//
				));
		}

		@Override
		public boolean clear() {
			try (Transaction t = theParent.lock(false, null)) {
				boolean allCleared = true;
				for (FlattenedHolder outerEl : theOuterElements) {
					if (outerEl.manager == null)
						continue;
					allCleared &= outerEl.manager.clear();
				}
				return allCleared;
			}
		}

		class FlattenedHolderIter {
			FlattenedHolder holder;
			DerivedCollectionElement<T> lowBound;
			DerivedCollectionElement<T> highBound;
		}

		class InterElementIterable implements Iterable<FlattenedHolderIter> {
			private final FlattenedElement start;
			private final FlattenedElement end;
			private final boolean ascending;

			InterElementIterable(DerivedCollectionElement<T> start, DerivedCollectionElement<T> end, boolean ascending) {
				this.start = (FlattenedManager<E, I, T>.FlattenedElement) start;
				this.end = (FlattenedManager<E, I, T>.FlattenedElement) end;
				this.ascending = ascending;
			}

			@Override
			public Iterator<FlattenedHolderIter> iterator() {
				return new Iterator<FlattenedHolderIter>() {
					private FlattenedHolder theHolder;
					private FlattenedHolderIter theIterStruct;

					{
						if (start == null)
							theHolder = CollectionElement.get(theOuterElements.getTerminalElement(ascending));
						else
							theHolder = start.theHolder;
						theIterStruct = new FlattenedHolderIter();
					}

					@Override
					public boolean hasNext() {
						if (theHolder == null)
							return false;
						else if (end == null)
							return true;
						int comp = theHolder.holderElement.compareTo(end.theHolder.holderElement);
						if (comp == 0)
							return true;
						else
							return (comp < 0) == ascending;
					}

					@Override
					public FlattenedHolderIter next() {
						if (!hasNext())
							throw new NoSuchElementException();
						theIterStruct.holder = theHolder;
						if (ascending) {
							if (start == null || !theHolder.equals(start.theHolder))
								theIterStruct.lowBound = null;
							else
								theIterStruct.lowBound = (DerivedCollectionElement<T>) start.theParentEl;
							if (end == null || !theHolder.equals(end.theHolder))
								theIterStruct.highBound = null;
							else
								theIterStruct.highBound = (DerivedCollectionElement<T>) end.theParentEl;
						} else {
							if (end == null || !theHolder.equals(end.theHolder))
								theIterStruct.lowBound = null;
							else
								theIterStruct.lowBound = (DerivedCollectionElement<T>) end.theParentEl;
							if (start == null || !theHolder.equals(start.theHolder))
								theIterStruct.highBound = null;
							else
								theIterStruct.highBound = (DerivedCollectionElement<T>) start.theParentEl;
						}
						do {
							theHolder = CollectionElement.get(theOuterElements.getAdjacentElement(theHolder.holderElement, ascending));
						} while (theHolder.manager == null);
						return theIterStruct;
					}
				};
			}
		}

		@Override
		public String canAdd(T toAdd, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			String firstMsg = null;
			try (Transaction t = theParent.lock(false, null)) {
				for (FlattenedHolderIter holder : new InterElementIterable(after, before, true)) {
					String msg;
					if (!TypeTokens.get().isInstance(holder.holder.manager.getTargetType(), toAdd)
						|| !holder.holder.manager.equivalence().isElement(toAdd)) {
						msg = StdMsg.ILLEGAL_ELEMENT;
					} else
						msg = ((ActiveCollectionManager<?, ?, T>) holder.holder.manager).canAdd(toAdd, holder.lowBound, holder.highBound);
					if (msg == null)
						return null;
					else if (firstMsg == null)
						firstMsg = msg;
				}
			}
			if (firstMsg == null)
				firstMsg = StdMsg.UNSUPPORTED_OPERATION;
			return firstMsg;
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before,
			boolean first) {
			try (Transaction t = theParent.lock(false, null)) {
				String firstMsg = null;
				for (FlattenedHolderIter holder : new InterElementIterable(after, before, first)) {
					String msg;
					if (!TypeTokens.get().isInstance(holder.holder.manager.getTargetType(), value)
						|| !holder.holder.manager.equivalence().isElement(value)) {
						msg = StdMsg.ILLEGAL_ELEMENT;
					} else
						msg = ((ActiveCollectionManager<?, ?, T>) holder.holder.manager).canAdd(value, holder.lowBound, holder.highBound);
					if (msg == null) {
						DerivedCollectionElement<T> added = ((ActiveCollectionManager<?, ?, T>) holder.holder.manager).addElement(value,
							holder.lowBound, holder.highBound, first);
						if (added != null)
							return new FlattenedElement(holder.holder, added, true);
					}
					else if (firstMsg == null)
						firstMsg = msg;
				}
				if (firstMsg == null || firstMsg.equals(StdMsg.UNSUPPORTED_OPERATION))
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				else
					throw new IllegalArgumentException(firstMsg);
			}
		}

		@Override
		public String canMove(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after, DerivedCollectionElement<T> before) {
			FlattenedElement flatV = (FlattenedManager<E, I, T>.FlattenedElement) valueEl;
			String firstMsg = null;
			try (Transaction t = theParent.lock(false, null)) {
				String removable = flatV.theParentEl.canRemove();
				T value = flatV.theParentEl.get();
				for (FlattenedHolderIter holder : new InterElementIterable(after, before, true)) {
					String msg;
					if (holder.holder.equals(flatV.theHolder))
						msg = ((ActiveCollectionManager<?, ?, T>) holder.holder.manager)
						.canMove((DerivedCollectionElement<T>) flatV.theParentEl, holder.lowBound, holder.highBound);
					else if (!TypeTokens.get().isInstance(holder.holder.manager.getTargetType(), value)
						|| !holder.holder.manager.equivalence().isElement(value)) {
						msg = StdMsg.ILLEGAL_ELEMENT;
					} else if (removable != null)
						msg = removable;
					else
						msg = ((ActiveCollectionManager<?, ?, T>) holder.holder.manager).canAdd(value, holder.lowBound, holder.highBound);
					if (msg == null)
						return null;
					else if (firstMsg == null)
						firstMsg = msg;
				}
			}
			if (firstMsg == null)
				firstMsg = StdMsg.UNSUPPORTED_OPERATION;
			return firstMsg;
		}

		@Override
		public DerivedCollectionElement<T> move(DerivedCollectionElement<T> valueEl, DerivedCollectionElement<T> after,
			DerivedCollectionElement<T> before, boolean first, Runnable afterRemove) {
			FlattenedElement flatV = (FlattenedManager<E, I, T>.FlattenedElement) valueEl;
			String firstMsg = null;
			try (Transaction t = theParent.lock(false, null)) {
				String removable = flatV.theParentEl.canRemove();
				T value = flatV.theParentEl.get();
				DerivedCollectionElement<T> moved = null;
				for (FlattenedHolderIter holder : new InterElementIterable(after, before, true)) {
					String msg;
					if (holder.holder.equals(flatV.theHolder)) {
						msg = ((ActiveCollectionManager<?, ?, T>) holder.holder.manager)
							.canMove((DerivedCollectionElement<T>) flatV.theParentEl, holder.lowBound, holder.highBound);
						if (msg == null)
							moved = ((ActiveCollectionManager<?, ?, T>) holder.holder.manager).move(
								(DerivedCollectionElement<T>) flatV.theParentEl, holder.lowBound, holder.highBound, first, afterRemove);
					} else if (!TypeTokens.get().isInstance(holder.holder.manager.getTargetType(), value)
						|| !holder.holder.manager.equivalence().isElement(value)) {
						msg = StdMsg.ILLEGAL_ELEMENT;
					} else if (removable != null)
						msg = removable;
					else {
						msg = ((ActiveCollectionManager<?, ?, T>) holder.holder.manager).canAdd(value, holder.lowBound, holder.highBound);
						if (msg == null) {
							flatV.theParentEl.remove();
							if (afterRemove != null)
								afterRemove.run();
							moved = ((ActiveCollectionManager<?, ?, T>) holder.holder.manager).addElement(value, holder.lowBound,
								holder.highBound, first);
							if (moved == null)
								throw new IllegalStateException("Removed, but unable to re-add");
						}
					}
					if (moved != null)
						return new FlattenedElement(holder.holder, moved, true);
					else if (firstMsg == null)
						firstMsg = msg;
				}
				if (firstMsg == null || firstMsg.equals(StdMsg.UNSUPPORTED_OPERATION))
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				else
					throw new IllegalArgumentException(firstMsg);
			}
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			// Group by collection
			BetterMap<ActiveCollectionManager<?, ?, T>, List<DerivedCollectionElement<T>>> grouped = BetterHashMap.build().identity()
				.unsafe().buildMap();
			for (DerivedCollectionElement<T> el : elements)
				grouped
				.computeIfAbsent((ActiveCollectionManager<?, ?, T>) ((FlattenedElement) el).theHolder.manager, h -> new ArrayList<>())
				.add((DerivedCollectionElement<T>) ((FlattenedElement) el).theParentEl);

			for (Map.Entry<ActiveCollectionManager<?, ?, T>, List<DerivedCollectionElement<T>>> entry : grouped.entrySet())
				entry.getKey().setValues(entry.getValue(), newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theAccepter = onElement;
			theListening = listening;
			boolean[] init = new boolean[] { true }; // Only honor fromStart for the initial collections
			theParent.begin(fromStart, (parentEl, cause) -> {
				FlattenedHolder holder = new FlattenedHolder(parentEl, listening, cause, !init[0] || fromStart);
				holder.holderElement = theOuterElements.addElement(holder, false).getElementId();
			}, listening);
			init[0] = false;
		}

		private class FlattenedHolder {
			private final DerivedCollectionElement<I> theParentEl;
			private final BetterTreeList<FlattenedElement> theElements;
			private final WeakListening.Builder theChildListening = theListening.child();
			private final boolean isFromStart;
			ElementId holderElement;
			private CollectionDataFlow<?, ?, ? extends T> theFlow;
			ActiveCollectionManager<?, ?, ? extends T> manager;

			FlattenedHolder(DerivedCollectionElement<I> parentEl, WeakListening listening, Object cause, boolean fromStart) {
				theParentEl = parentEl;
				theElements = new BetterTreeList<>(false);
				isFromStart = fromStart;
				updated(theParentEl.get(), cause);
				theParentEl.setListener(new CollectionElementListener<I>() {
					@Override
					public void update(I oldValue, I newValue, Object innerCause) {
						updated(newValue, innerCause);
					}

					@Override
					public void removed(I value, Object innerCause) {
						try (Transaction parentT = theParent.lock(false, null); Transaction innerT = lockLocal()) {
							clearSubElements(innerCause);
						}
					}
				});
			}

			void updated(I newValue, Object cause) {
				try (Transaction parentT = theParent.lock(false, null); Transaction t = lockLocal()) {
					CollectionDataFlow<?, ?, ? extends T> newFlow = theMap.apply(newValue);
					if (newFlow == theFlow)
						return;
					clearSubElements(cause);
					theFlow = newFlow;
					manager = newFlow.manageActive();
					manager.begin(isFromStart, (childEl, innerCause) -> {
						try (Transaction innerParentT = theParent.lock(false, null); Transaction innerLocalT = lockLocal()) {
							FlattenedElement flatEl = new FlattenedElement(this, childEl, false);
							theAccepter.accept(flatEl, innerCause);
						}
					}, theChildListening.getListening());
				}
			}

			void clearSubElements(Object cause) {
				if (manager == null)
					return;
				try (Transaction t = manager.lock(true, cause)) {
					theChildListening.unsubscribe(); // unsubscribe here removes all elements
					manager = null;
				}
			}
		}

		private class FlattenedElement implements DerivedCollectionElement<T> {
			private final FlattenedHolder theHolder;
			private final DerivedCollectionElement<? extends T> theParentEl;
			private final ElementId theElementId;
			private CollectionElementListener<T> theListener;

			<X extends T> FlattenedElement(FlattenedHolder holder, DerivedCollectionElement<X> parentEl, boolean synthetic) {
				theHolder = holder;
				theParentEl = parentEl;
				if (!synthetic) {
					theElementId = theHolder.theElements.addElement(this, false).getElementId();
					parentEl.setListener(new CollectionElementListener<X>() {
						@Override
						public void update(X oldValue, X newValue, Object cause) {
							// Need to make sure that the flattened collection isn't firing at the same time as the child collection
							try (Transaction parentT = theParent.lock(false, null); Transaction localT = lockLocal()) {
								ObservableCollectionDataFlowImpl.update(theListener, oldValue, newValue, cause);
							}
						}

						@Override
						public void removed(X value, Object cause) {
							theHolder.theElements.mutableElement(theElementId).remove();
							// Need to make sure that the flattened collection isn't firing at the same time as the child collection
							try (Transaction parentT = theParent.lock(false, null); Transaction localT = lockLocal()) {
								ObservableCollectionDataFlowImpl.removed(theListener, value, cause);
							}
						}
					});
				} else
					theElementId = null;
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				FlattenedElement flat = (FlattenedElement) o;
				int comp = theHolder.theParentEl.compareTo(flat.theHolder.theParentEl);
				if (comp == 0)
					comp = ((DerivedCollectionElement<T>) theParentEl).compareTo((DerivedCollectionElement<T>) flat.theParentEl);
				return comp;
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public boolean isDerivedFrom(ElementId sourceEl) {
				return theParentEl.isDerivedFrom(sourceEl) || theHolder.theParentEl.isDerivedFrom(sourceEl);
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
				if (value != null && !TypeTokens.get().isInstance(theHolder.manager.getTargetType(), value))
					return StdMsg.BAD_TYPE;
				return ((DerivedCollectionElement<T>) theParentEl).isAcceptable(value);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				if (value != null && !TypeTokens.get().isInstance(theHolder.manager.getTargetType(), value))
					throw new IllegalArgumentException(StdMsg.BAD_TYPE);
				((DerivedCollectionElement<T>) theParentEl).set(value);
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
				return theHolder.theParentEl.toString() + "/" + theParentEl.toString();
			}
		}
	}
}
