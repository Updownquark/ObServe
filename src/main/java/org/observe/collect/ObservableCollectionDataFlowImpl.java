package org.observe.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Transformation;
import org.observe.Transformation.ReversibleTransformationPrecursor;
import org.observe.assoc.ObservableMultiMap.MultiMapFlow;
import org.observe.assoc.ObservableSortedMultiMap.SortedMultiMapFlow;
import org.observe.assoc.impl.AddKeyHolder;
import org.observe.assoc.impl.DefaultMultiMapFlow;
import org.observe.assoc.impl.DefaultSortedMultiMapFlow;
import org.observe.collect.FlatMapOptions.FlatMapDef;
import org.observe.collect.FlowOptions.SimpleUniqueOptions;
import org.observe.collect.FlowOptions.UniqueOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableCollection.ModFilterBuilder;
import org.observe.collect.ObservableCollection.SortedDataFlow;
import org.observe.collect.ObservableCollectionActiveManagers.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionActiveManagers.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionImpl.ActiveDerivedCollection;
import org.observe.collect.ObservableCollectionImpl.PassiveDerivedCollection;
import org.observe.collect.ObservableCollectionPassiveManagers.PassiveCollectionManager;
import org.observe.util.TypeTokens;
import org.qommons.CausalLock;
import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.LambdaUtils;
import org.qommons.Lockable;
import org.qommons.Lockable.CoreId;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstrained;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;

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
	public static interface CollectionOperation<E, I, T> extends Identifiable, CausalLock {
		/** @return The type of collection that this operation would produce */
		TypeToken<T> getTargetType();

		/** @return The equivalence of the collection that this operation would produce */
		Equivalence<? super T> equivalence();

		/** @return Whether this manager will prevent its collection from being able to manage elements arbitrarily */
		boolean isContentControlled();
	}

	/**
	 * Implements a nested map
	 *
	 * @param <E> The source type
	 * @param <I> The intermediate type
	 * @param <T> The result type
	 */
	public static class MapWithParent<E, I, T> implements Function<E, T> {
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

		/** @return The function producing intermediate values from source values */
		public Function<? super E, ? extends I> getParentMap() {
			return theParentMap;
		}

		/**
		 * @param source The intermediate value
		 * @return The mapped result value
		 */
		public T mapIntermediate(I source) {
			return theMap.apply(source);
		}
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
		X removed(ObservableCollectionActiveManagers.DerivedCollectionElement<E> element);

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
		void transferred(ObservableCollectionActiveManagers.DerivedCollectionElement<E> element, X data);
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
		public int hashCode() {
			return Objects.hash(theUnmodifiableMessage, areUpdatesAllowed, theAddMessage, theRemoveMessage, theMoveMessage, //
				theAddFilter, theRemoveFilter);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof ModFilterer))
				return false;
			ModFilterer<?> other = (ModFilterer<?>) obj;
			return Objects.equals(theUnmodifiableMessage, other.theUnmodifiableMessage)//
				&& areUpdatesAllowed == other.areUpdatesAllowed//
				&& Objects.equals(theAddMessage, other.theAddMessage)//
				&& Objects.equals(theRemoveMessage, other.theRemoveMessage)//
				&& Objects.equals(theMoveMessage, other.theMoveMessage)//
				&& Objects.equals(theAddFilter, other.theAddFilter)//
				&& Objects.equals(theRemoveFilter, other.theRemoveFilter);
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
	public static abstract class AbstractDataFlow<E, I, T> extends AbstractIdentifiable implements CollectionDataFlow<E, I, T> {
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
		public <X> CollectionDataFlow<E, T, X> transform(TypeToken<X> targetType,
			Function<? super Transformation.ReversibleTransformationPrecursor<T, X, ?>, ? extends Transformation<T, X>> combination) {
			Transformation<T, X> def = combination.apply(new ReversibleTransformationPrecursor<>());
			return new TransformedCollectionOp<>(theSource, this, targetType, def);
		}

		@Override
		public <X> CollectionDataFlow<E, ?, X> flattenValues(TypeToken<X> target,
			Function<? super T, ? extends ObservableValue<? extends X>> map) {
			return new FlattenedValuesOp<>(theSource, this, target, map);
		}

		@Override
		public <X> CollectionDataFlow<E, ?, X> flatMap(TypeToken<X> target,
			Function<? super T, ? extends CollectionDataFlow<?, ?, ? extends X>> map) {
			return new FlattenedOp<>(theSource, this, target, map, null);
		}

		@Override
		public <V, X> CollectionDataFlow<E, ?, X> flatMap(TypeToken<X> target,
			Function<? super T, ? extends CollectionDataFlow<?, ?, ? extends V>> map,
				Function<FlatMapOptions<T, V, X>, FlatMapDef<T, V, X>> options) {
			if (options == null)
				throw new IllegalArgumentException("options required");
			FlatMapDef<T, V, X> def = options.apply(new FlatMapOptions.SimpleFlatMapOptions<>());
			if (def == null)
				throw new IllegalArgumentException("options required");
			return new FlattenedOp<>(theSource, this, target, map, def);
		}

		@Override
		public CollectionDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options) {
			ModFilterBuilder<T> filter = new ModFilterBuilder<>();
			options.accept(filter);
			return new ModFilteredOp<>(theSource, this, new ModFilterer<>(filter));
		}

		@Override
		public CollectionDataFlow<E, T, T> catchUpdates(ThreadConstraint constraint) {
			return new UpdateCatchingOp<>(theSource, this, constraint);
		}

		@Override
		public SortedDataFlow<E, T, T> sorted(Comparator<? super T> compare) {
			return new ObservableSortedCollectionImpl.SortedOp<>(theSource, this, compare);
		}

		@Override
		public DistinctDataFlow<E, T, T> distinct(Consumer<UniqueOptions> options) {
			SimpleUniqueOptions uo = new SimpleUniqueOptions(equivalence() instanceof Equivalence.SortedEquivalence);
			if (options != null)
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
			return transform(getTargetType(), tx -> tx.map(LambdaUtils.identity()).withReverse(LambdaUtils.printableFn(v -> {
				if (addKey.get() != null)
					return reverse.apply(addKey.get(), v);
				else
					return v;
			}, "identity", null)));
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
		protected Object createIdentity() {
			return Identifiable.wrap(getSource().getIdentity(), "flow");
		}

		@Override
		public boolean supportsPassive() {
			return true;
		}

		@Override
		public PassiveCollectionManager<E, ?, E> managePassive() {
			return new ObservableCollectionPassiveManagers.BaseCollectionPassThrough<>(getSource());
		}

		@Override
		public ActiveCollectionManager<E, ?, E> manageActive() {
			return new ObservableCollectionActiveManagers.BaseCollectionManager<>(getSource());
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
		protected Object createIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "reverse");
		}

		@Override
		public boolean supportsPassive() {
			return getParent().supportsPassive();
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new ObservableCollectionPassiveManagers.PassiveReversedManager<>(getParent().managePassive());
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ObservableCollectionActiveManagers.ActiveReversedManager<>(getParent().manageActive());
		}
	}

	private static class FilterOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Function<? super T, String> theFilter;

		FilterOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, Function<? super T, String> filter) {
			super(source, parent, parent.getTargetType(), parent.equivalence());
			theFilter = filter;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "filter", theFilter);
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
			return new ObservableCollectionActiveManagers.FilteredCollectionManager<>(getParent().manageActive(), theFilter);
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
		protected Object createIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), isExclude ? "without" : "intersect", theFilter.getIdentity());
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
			return new ObservableCollectionActiveManagers2.IntersectionManager<>(getParent().manageActive(), theFilter, isExclude);
		}
	}

	private static class EquivalenceSwitchOp<E, T> extends AbstractDataFlow<E, T, T> {
		EquivalenceSwitchOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, Equivalence<? super T> equivalence) {
			super(source, parent, parent.getTargetType(), equivalence);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "withEquivalence", equivalence());
		}

		@Override
		public boolean supportsPassive() {
			return true;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new ObservableCollectionPassiveManagers.PassiveEquivalenceSwitchedManager<>(getParent().managePassive(), equivalence());
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ObservableCollectionActiveManagers.ActiveEquivalenceSwitchedManager<>(getParent().manageActive(), equivalence());
		}
	}

	/**
	 * Defines a combination of a single source collection with one or more observable values
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of elements in the resulting collection
	 */
	public static class TransformedCollectionOp<E, I, T> extends AbstractDataFlow<E, I, T> {
		private final Transformation<I, T> theDef;

		/**
		 * @param source The source collection
		 * @param parent This flow's parent (not null)
		 * @param target The type of this flow
		 * @param def The combination definition used to produce this flow's values from its parent's and to govern certain aspects of this
		 *        flow's behavior, e.g. caching
		 */
		protected TransformedCollectionOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, I> parent, TypeToken<T> target,
			Transformation<I, T> def) {
			super(source, parent, target, transformEquivalence(parent.equivalence(), TypeTokens.getRawType(target), def));
			theDef = def;
		}

		private static <I, T> Equivalence<? super T> transformEquivalence(Equivalence<? super I> sourceEquivalence, Class<T> target,
			Transformation<I, T> def) {
			if (def instanceof Transformation.ReversibleTransformation) {
				Transformation.TransformReverse<I, T> reverse = ((Transformation.ReversibleTransformation<I, T>) def).getReverse();
				if (reverse instanceof Transformation.MappingSourceReplacingReverse
					&& ((Transformation.MappingSourceReplacingReverse<I, T>) reverse).isInexactReversible()) {
					// Inexact reversibility has some interesting consequences, especially if further links, particularly distinct links,
					// are derived from inexact-reversible transformations
					// E.g. take a simple integer x2 inexact transform derived with distinctness.
					// If 1 is added to the distinct collection, the actual value that will be added is 0 ( 1 / 2 )
					// This difference must be handled correctly and this is done though the peculiar mapping equivalence.
					Function<? super I, ? extends T> map = LambdaUtils.printableFn(v -> {
						Transformation.Engine<I, T> engine = def.createEngine(null, sourceEquivalence);
						return engine.map(v, engine.get());
					}, def::toString, def);
					Equivalence<T> mappedEquivalence = sourceEquivalence.map(target, //
						LambdaUtils.printablePred(v -> {
							Transformation.Engine<I, T> engine = def.createEngine(null, sourceEquivalence);
							Transformation.ReverseQueryResult<I> rq = engine.reverse(v, false, true);
							return rq.getError() == null;
						}, def + ".filter", def), map, //
						LambdaUtils.printableFn(v -> {
							Transformation.Engine<I, T> engine = def.createEngine(null, sourceEquivalence);
							Transformation.ReverseQueryResult<I> rq = engine.reverse(v, false, true);
							return rq.getReversed();
						}, reverse::toString, reverse));
					return mappedEquivalence;
				} else
					return def.equivalence();
			} else
				return def.equivalence();
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "combine", theDef);
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
			return new ObservableCollectionPassiveManagers.PassiveTransformedCollectionManager<>(getParent().managePassive(),
				getTargetType(), theDef, equivalence());
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ObservableCollectionActiveManagers.ActiveTransformedCollectionManager<>(getParent().manageActive(), getTargetType(),
				theDef, equivalence());
		}
	}

	private static class RefreshOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Observable<?> theRefresh;

		RefreshOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, Observable<?> refresh) {
			super(source, parent, parent.getTargetType(), parent.equivalence());
			theRefresh = refresh;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "refresh", theRefresh.getIdentity());
		}

		@Override
		public boolean supportsPassive() {
			return getParent().supportsPassive();
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new ObservableCollectionPassiveManagers.PassiveRefreshingCollectionManager<>(getParent().managePassive(), theRefresh);
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ObservableCollectionActiveManagers2.ActiveRefreshingCollectionManager<>(getParent().manageActive(), theRefresh);
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
		protected Object createIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "refreshEach", theElementRefresh);
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
			return new ObservableCollectionActiveManagers2.ElementRefreshingCollectionManager<>(getParent().manageActive(),
				theElementRefresh);
		}
	}

	/**
	 * An ElementSetter that may also receive the source collection element instead of just its value
	 *
	 * @param <I> The type of the source element to set values of
	 * @param <T> The type of the values to set in the source elements
	 */
	public interface FlowElementSetter<I, T> extends Transformation.TransformReverse<I, T> {
		/**
		 * Called prior to {@link DerivedCollectionElement#set(Object)}
		 *
		 * @param element The source element
		 * @param newValue The new mapped value
		 * @return The action to call after the set operation
		 */
		Runnable preSet(DerivedCollectionElement<? extends I> element, T newValue);
	}

	private static class FlattenedValuesOp<E, I, T> extends AbstractDataFlow<E, I, T> {
		private final Function<? super I, ? extends ObservableValue<? extends T>> theMap;

		FlattenedValuesOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends ObservableValue<? extends T>> map) {
			super(source, parent, targetType.wrap(), Equivalence.DEFAULT);
			theMap = map;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "flattenValues", theMap);
		}

		@Override
		public boolean supportsPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			TypeToken<ObservableValue<? extends T>> valueType = TypeTokens.get().keyFor(ObservableValue.class)
				.parameterized(getTargetType());
			ValueHolder<DerivedCollectionElement<? extends ObservableValue<? extends T>>> settingElement = new ValueHolder<>();
			class RefreshingMapReverse implements FlowElementSetter<ObservableValue<? extends T>, T> {
				@Override
				public boolean isStateful() {
					return true;
				}

				@Override
				public String isEnabled(Transformation.TransformationValues<ObservableValue<? extends T>, T> transformValues) {
					ObservableValue<? extends T> value = transformValues.getCurrentSource();
					if (!(value instanceof SettableValue))
						return StdMsg.UNSUPPORTED_OPERATION;
					else
						return ((SettableValue<? extends T>) value).isEnabled().get();
				}

				@Override
				public org.observe.Transformation.ReverseQueryResult<ObservableValue<? extends T>> reverse(T newValue,
					Transformation.TransformationValues<ObservableValue<? extends T>, T> transformValues, boolean add, boolean test) {
					ObservableValue<? extends T> sourceValue = transformValues.getCurrentSource();
					if (!(sourceValue instanceof SettableValue))
						return Transformation.ReverseQueryResult.reject(StdMsg.UNSUPPORTED_OPERATION);
					if (!TypeTokens.get().isInstance(sourceValue.getType(), newValue))
						return Transformation.ReverseQueryResult.reject(StdMsg.BAD_TYPE);
					if(test){
						String msg = ((SettableValue<T>) sourceValue).isAcceptable(newValue);
						if (msg != null)
							return Transformation.ReverseQueryResult.reject(msg);
					} else
						((SettableValue<T>) sourceValue).set(newValue, null);
					return Transformation.ReverseQueryResult.value(sourceValue);
				}

				@Override
				public Runnable preSet(DerivedCollectionElement<? extends ObservableValue<? extends T>> element, T newValue) {
					settingElement.accept(element);
					return settingElement::clear;
				}

				@Override
				public int hashCode() {
					return RefreshingMapReverse.class.hashCode();
				}

				@Override
				public boolean equals(Object obj) {
					return obj != null && obj.getClass() == getClass();
				}

				@Override
				public String toString() {
					return "flattenedValueSet";
				}
			}
			ActiveCollectionManager<E, ?, T> manager = getParent()//
				.map(valueType, theMap)//
				.refreshEach(LambdaUtils.printableFn(ObservableValue::noInitChanges, "noInitChanges", "ObservableValue.noInitChanges"))//
				.transform(getTargetType(), tx -> {
					return tx.map(LambdaUtils.printableFn(obs -> obs == null ? null : obs.get(), () -> "flatten"))
						.withReverse(new RefreshingMapReverse());
				})//
				.manageActive();
			if (manager instanceof AbstractTransformedManager) {
				((AbstractTransformedManager<E, ?, I>) manager).propagateUpdatesToParent = false;
				if (((AbstractTransformedManager<?, ?, ?>) manager)
					.getParent() instanceof ObservableCollectionActiveManagers2.ElementRefreshingCollectionManager) {
					/* There is a very narrow condition in which a child collection calls set on a settable element of this manager,
					 * but the element refresh affects more than just the desired element, which may result in re-ordering in the child
					 * collection and removal of the element that the set operation was called on.
					 *
					 * This is a violation of the set contract, so we have to punch a few holes here to prevent this.
					 * Basically, we ensure that when set is called on an element in the mapped manager, it notifies the refreshEach manager
					 * that that particular element should be taken care of first.
					 * This should always result in the target element being preserved, though other affected elements may be reordered.
					 */
					ObservableCollectionActiveManagers2.ElementRefreshingCollectionManager<E, ObservableValue<? extends T>> refresh;
					refresh = (ObservableCollectionActiveManagers2.ElementRefreshingCollectionManager<E, ObservableValue<? extends T>>) //
						((AbstractTransformedManager<?, ?, ?>) manager).getParent();
					refresh.withSettingElement(settingElement);
				}
			}
			return manager;
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
		protected Object createIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "filterMod", theOptions);
		}

		@Override
		public boolean supportsPassive() {
			return getParent().supportsPassive();
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new ObservableCollectionPassiveManagers.PassiveModFilteredManager<>(getParent().managePassive(), theOptions);
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ObservableCollectionActiveManagers2.ActiveModFilteredManager<>(getParent().manageActive(), theOptions);
		}
	}

	private static class UpdateCatchingOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final ThreadConstraint theConstraint;

		UpdateCatchingOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, ThreadConstraint constraint) {
			super(source, parent, parent.getTargetType(), parent.equivalence());
			theConstraint = constraint;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "catchUpdates");
		}

		@Override
		public boolean supportsPassive() {
			// In a passive collection, the event capability is entirely managed by the active ancestor parent--the source collection.
			// Since this operation is to NOT pass updates to the parent but still to propagate them to the descendants,
			// passivity cannot be supported.
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ObservableCollectionActiveManagers2.UpdateCatchingManager<>(getParent().manageActive(), theConstraint);
		}
	}

	private static class FlattenedOp<E, I, V, T> extends AbstractDataFlow<E, I, T> {
		private final Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends V>> theMap;
		private final FlatMapDef<I, V, T> theOptions;

		FlattenedOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends V>> map, FlatMapDef<I, V, T> options) {
			super(source, parent, targetType, Equivalence.DEFAULT);
			theMap = map;
			theOptions = options;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "flatMap", theMap, theOptions);
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
			return new ObservableCollectionActiveManagers2.FlattenedManager<>(getParent().manageActive(), getTargetType(), theMap,
				theOptions);
		}
	}

	static abstract class AbstractTransformedManager<E, I, T> implements CollectionOperation<E, I, T> {
		final CollectionOperation<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		final Transformation.Engine<I, T> theEngine;
		private final Equivalence<? super T> theEquivalence;
		/**
		 * This boolean is a back door for FlattenedValuesOp to allow it to avoid firing events to the collection made of ObservableValues
		 */
		boolean propagateUpdatesToParent;

		protected AbstractTransformedManager(CollectionOperation<E, ?, I> parent, TypeToken<T> targetType,
			Transformation<I, T> transformation, Equivalence<? super T> equivalence) {
			theParent = parent;
			theTargetType = targetType;
			theEngine = transformation.createEngine(null, theParent.equivalence());
			propagateUpdatesToParent = true;
			theEquivalence = equivalence;
		}

		protected CollectionOperation<E, ?, I> getParent() {
			return theParent;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		protected Transformation.Engine<I, T> getEngine() {
			return theEngine;
		}

		protected Transformation<I, T> getTransformation() {
			return theEngine.getTransformation();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return ThreadConstrained.getThreadConstraint(theParent, theEngine);
		}

		@Override
		public boolean isLockSupported() {
			return theParent.isLockSupported() || theEngine.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(Lockable.lockable(theParent, write, cause), theEngine);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(Lockable.lockable(theParent, write, cause), theEngine);
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
		public boolean isContentControlled() {
			return !isReversible() || theEngine.getTransformation().isOneToMany() || theParent.isContentControlled();
		}

		protected boolean isReversible() {
			return theEngine.getTransformation() instanceof Transformation.ReversibleTransformation;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "xform", theEngine.getTransformation().getIdentity());
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		protected abstract void doParentMultiSet(Collection<AbstractTransformedElement> elements, I newValue);

		protected void setElementsValue(Collection<?> elements, T newValue) throws UnsupportedOperationException, IllegalArgumentException {
			List<Transformation.TransformedElement<I, T>> txElements = QommonsUtils.map(elements,
				el -> ((AbstractTransformedElement) el).transformElement, false);
			List<I> sourceValues = theEngine.setElementsValue(txElements, newValue);
			if (!propagateUpdatesToParent) { // Don't notify the parent
			} else if (sourceValues.size() == 1)
				doParentMultiSet((Collection<AbstractTransformedElement>) elements, sourceValues.get(0));
			else {
				int i = 0;
				for (AbstractTransformedElement el : (Collection<AbstractTransformedElement>) elements) {
					I sourceVal = sourceValues.get(i);
					el.setParent(sourceVal);
					i++;
				}
			}
		}

		protected abstract class AbstractTransformedElement {
			protected final Transformation.TransformedElement<I, T> transformElement;

			AbstractTransformedElement(Supplier<I> sourceValue) {
				transformElement = theEngine.createElement(sourceValue);
			}

			protected abstract String isParentEnabled();

			protected abstract String isParentAcceptable(I value);

			abstract void setParent(I parentValue);

			protected String isEnabledLocal() {
				String msg = transformElement.isEnabled(theEngine.get());
				if (msg == null && propagateUpdatesToParent)
					msg = isParentEnabled();
				return msg;
			}

			protected String isAcceptable(T value) {
				org.observe.Transformation.ReverseQueryResult<I> rq = transformElement.set(value, //
					theEngine.get(), true);
				if (rq.getError() != null)
					return rq.getError();
				return propagateUpdatesToParent ? isParentAcceptable(rq.getReversed()) : null;
			}

			protected void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				I reversed = transformElement.set(value, theEngine.get(), false).getReversed();
				if (propagateUpdatesToParent)
					setParent(reversed);
			}
		}
	}
}
