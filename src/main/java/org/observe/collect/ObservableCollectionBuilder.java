package org.observe.collect;

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.Equivalence;
import org.observe.Equivalence.SortedEquivalence;
import org.observe.util.TypeTokens;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.CollectionSynchronizerE;
import org.qommons.collect.ElementId;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.ListenerList;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.ex.ExFunction;
import org.qommons.threading.QommonsTimer;
import org.qommons.threading.QommonsTimer.TaskHandle;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.RedBlackNodeList;
import org.qommons.tree.SortedTreeList;

import com.google.common.reflect.TypeToken;

/**
 * Builds modifiable instances of {@link ObservableCollection}
 *
 * @param <E> The type of elements in the collection
 * @param <B> The sub-type of the builder
 */
public interface ObservableCollectionBuilder<E, B extends ObservableCollectionBuilder<E, B>> {
	/**
	 * An {@link ObservableCollectionBuilder} that builds {@link ObservableSortedCollection} instances
	 *
	 * @param <E> The type of elements in the collection
	 * @param <B> The sub-type of the builder
	 */
	public interface SortedBuilder<E, B extends SortedBuilder<E, B>> extends ObservableCollectionBuilder<E, B> {
		@Override
		DistinctSortedBuilder<E, ?> distinct();

		@Override
		ObservableSortedCollection<E> build();
	}

	/**
	 * An {@link ObservableCollectionBuilder} that builds {@link ObservableSet} instances
	 *
	 * @param <E> The type of elements in the collection
	 * @param <B> The sub-type of the builder
	 */
	public interface DistinctBuilder<E, B extends DistinctBuilder<E, B>> extends ObservableCollectionBuilder<E, B> {
		@Override
		default DistinctBuilder<E, ?> distinct() {
			return this;
		}

		@Override
		ObservableSet<E> build();
	}

	/**
	 * An {@link ObservableCollectionBuilder} that builds {@link ObservableSortedSet} instances
	 *
	 * @param <E> The type of elements in the collection
	 * @param <B> The sub-type of the builder
	 */
	public interface DistinctSortedBuilder<E, B extends DistinctSortedBuilder<E, B>> extends SortedBuilder<E, B>, DistinctBuilder<E, B> {
		@Override
		default DistinctSortedBuilder<E, ?> distinct() {
			return this;
		}

		@Override
		ObservableSortedSet<E> build();
	}

	/**
	 * @param backing The pre-set backing for the collection
	 * @return This builder
	 */
	B withBacking(BetterList<E> backing);

	/**
	 * @param equivalence The equivalence for the collection
	 * @return This builder
	 */
	B withEquivalence(Equivalence<? super E> equivalence);

	/**
	 * @param locker The locker for the collection
	 * @return This builder
	 */
	default B withLocker(CollectionLockingStrategy locker) {
		return withLocker(__ -> locker);
	}

	/**
	 * @param locker The locker for the collection
	 * @return This builder
	 */
	B withLocker(Function<Object, CollectionLockingStrategy> locker);

	/**
	 * @param safe Whether the collection should be thread-safe
	 * @return This builder
	 */
	default B safe(boolean safe) {
		withLocker(v -> safe ? new StampedLockingStrategy(v) : new FastFailLockingStrategy());
		return (B) this;
	}

	/**
	 * Specifies that the collection should maintain an order (but not necessarily distinctness) among its elements
	 *
	 * @param sorting The sorting for the collection
	 * @return This builder
	 */
	SortedBuilder<E, ?> sortBy(Comparator<? super E> sorting);

	/**
	 * @param description The description for the collection
	 * @return This builder
	 */
	B withDescription(String description);

	/**
	 * @param elementSource A function to look up elements in the {@link #withBacking(BetterList) backing} collection by source element ID
	 * @return This builder
	 */
	B withElementSource(BiFunction<ElementId, BetterCollection<?>, ElementId> elementSource);

	/**
	 * @param sourceElements A function to look up elements in a source collection from an element in the {@link #withBacking(BetterList)
	 *        backing} collection
	 * @return This builder
	 */
	B withSourceElements(BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> sourceElements);

	/** @return A builder to build an {@link ObservableSet} with these characteristics */
	DistinctBuilder<E, ?> distinct();

	/**
	 * @param sorting The sorting for the set
	 * @return A builder to build an {@link ObservableSortedSet} with these characteristics
	 */
	DistinctSortedBuilder<E, ?> distinctSorted(Comparator<? super E> sorting);

	/** @return A new, empty collection build with these settings */
	ObservableCollection<E> build();

	/**
	 * Creates a builder for an observable collection whose data is controlled
	 *
	 * @param <V> The type of the backing data
	 * @param data The data source for the collection
	 * @return A builder for a data-controlled collection
	 */
	<V> DataControlledCollectionBuilder<E, V, ?> withData(Supplier<? extends List<? extends V>> data);

	/** Refreshes {@link DataControlledCollection}s periodically in the background */
	public interface DataControlAutoRefresher {
		/**
		 * @param collection The collection to refresh in the background
		 * @return A Runnable to {@link Runnable#run() call} to stop refreshing the collection
		 */
		Runnable add(DataControlledCollection<?, ?> collection);
	}

	/**
	 * Builds a {@link DataControlledCollection}
	 *
	 * @param <E> The type of the values in the collection
	 * @param <V> The type of the source data
	 * @param <B> The sub-type of this builder
	 */
	interface DataControlledCollectionBuilder<E, V, B extends DataControlledCollectionBuilder<E, V, B>> {
		/**
		 * @param equals An equals tester to preserve elements still present in the collection after refresh
		 * @return This builder
		 */
		B withEquals(BiPredicate<? super E, ? super V> equals);

		/**
		 * @param adjustmentOrder The adjustment order for refreshes
		 * @see org.qommons.collect.CollectionUtils.CollectionAdjustment#adjust(CollectionSynchronizerE,
		 *      org.qommons.collect.CollectionUtils.AdjustmentOrder)
		 * @return This refresher
		 */
		B withOrder(CollectionUtils.AdjustmentOrder adjustmentOrder);

		/**
		 * @param frequency The maximum refresh frequency for the collection
		 * @see DataControlledCollection#setMaxRefreshFrequency(long)
		 * @return This builder
		 */
		B withMaxRefreshFrequency(long frequency);

		/**
		 * @param refresh Whether the collection should synchronously refresh each time it is accessed
		 * @return This builder
		 */
		B refreshOnAccess(boolean refresh);

		/**
		 * @param refresher The asynchronous auto-refresher for the collection
		 * @return This builder
		 */
		B autoRefreshWith(DataControlAutoRefresher refresher);

		/**
		 * @param frequency The frequency with which to asynchronously auto-refresh the collection
		 * @return This builder
		 */
		default B autoRefreshEvery(Duration frequency) {
			return autoRefreshWith(new DefaultDataControlAutoRefresher(frequency));
		}

		/**
		 * @param synchronizer The synchronizer to perform the refresh operation between the collection and the source data
		 * @return The data-controlled collection
		 */
		DataControlledCollection<E, V> build(CollectionUtils.CollectionSynchronizerE<E, ? super V, ?> synchronizer);

		/**
		 * @param <X> The type of exception that may be thrown by the synchronization operation
		 * @param map Produces values for the collection from source data values
		 * @param synchronizer Allows customization of the synchronization behavior between collection elements and source values
		 * @return The data-controlled collection
		 */
		default <X extends Throwable> DataControlledCollection<E, V> build(ExFunction<? super V, ? extends E, ? extends X> map,
			Consumer<CollectionUtils.SimpleCollectionSynchronizer<E, ? super V, X, ?>> synchronizer) {
			CollectionUtils.SimpleCollectionSynchronizer<E, ? super V, X, ?> sync = CollectionUtils.simpleSyncE(map);
			if (synchronizer != null)
				synchronizer.accept(sync);
			return build(sync);
		}
	}

	/**
	 * Builds a {@link DataControlledCollection.Sorted data controlled sorted collection}
	 *
	 * @param <E> The type of the values in the collection
	 * @param <V> The type of the source data
	 * @param <B> The sub-type of this builder
	 */
	interface DataControlledSortedCollectionBuilder<E, V, B extends DataControlledSortedCollectionBuilder<E, V, B>>
	extends DataControlledCollectionBuilder<E, V, B> {
		@Override
		DataControlledCollection.Sorted<E, V> build(CollectionSynchronizerE<E, ? super V, ?> synchronizer);

		@Override
		default <X extends Throwable> DataControlledCollection.Sorted<E, V> build(ExFunction<? super V, ? extends E, ? extends X> map,
			Consumer<CollectionUtils.SimpleCollectionSynchronizer<E, ? super V, X, ?>> synchronizer) {
			return (DataControlledCollection.Sorted<E, V>) DataControlledCollectionBuilder.super.build(map, synchronizer);
		}
	}

	/**
	 * Builds a {@link DataControlledCollection.Set data controlled set}
	 *
	 * @param <E> The type of the values in the set
	 * @param <V> The type of the source data
	 * @param <B> The sub-type of this builder
	 */
	interface DataControlledSetBuilder<E, V, B extends DataControlledSetBuilder<E, V, B>> extends DataControlledCollectionBuilder<E, V, B> {
		@Override
		DataControlledCollection.Set<E, V> build(CollectionSynchronizerE<E, ? super V, ?> synchronizer);

		@Override
		default <X extends Throwable> DataControlledCollection.Set<E, V> build(ExFunction<? super V, ? extends E, ? extends X> map,
			Consumer<CollectionUtils.SimpleCollectionSynchronizer<E, ? super V, X, ?>> synchronizer) {
			return (DataControlledCollection.Set<E, V>) DataControlledCollectionBuilder.super.build(map, synchronizer);
		}
	}

	/**
	 * Builds a {@link DataControlledCollection.SortedSet data controlled sorted set}
	 *
	 * @param <E> The type of the values in the set
	 * @param <V> The type of the source data
	 * @param <B> The sub-type of this builder
	 */
	interface DataControlledSortedSetBuilder<E, V, B extends DataControlledSortedSetBuilder<E, V, B>>
	extends DataControlledSetBuilder<E, V, B>, DataControlledSortedCollectionBuilder<E, V, B> {
		@Override
		DataControlledCollection.SortedSet<E, V> build(CollectionSynchronizerE<E, ? super V, ?> synchronizer);

		@Override
		default <X extends Throwable> DataControlledCollection.SortedSet<E, V> build(ExFunction<? super V, ? extends E, ? extends X> map,
			Consumer<CollectionUtils.SimpleCollectionSynchronizer<E, ? super V, X, ?>> synchronizer) {
			return (DataControlledCollection.SortedSet<E, V>) DataControlledSortedCollectionBuilder.super.build(map, synchronizer);
		}
	}

	/**
	 * Default implementation of {@link ObservableCollectionBuilder}
	 *
	 * @param <E> The type of element for the collection
	 * @param <B> The sub-type of the builder
	 */
	public static class CollectionBuilderImpl<E, B extends ObservableCollectionBuilder<E, B>> implements ObservableCollectionBuilder<E, B> {
		private final TypeToken<E> theType;
		private BetterList<E> theBacking;
		private Function<Object, CollectionLockingStrategy> theLocker;
		private Comparator<? super E> theSorting;
		private BiFunction<ElementId, BetterCollection<?>, ElementId> theElementSource;
		private BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> theSourceElements;
		private Equivalence<? super E> theEquivalence;
		private String theDescription;

		/**
		 * @param type The type of elements in the collection
		 * @param initDescrip The initial (default) description for the collection
		 */
		public CollectionBuilderImpl(TypeToken<E> type, String initDescrip) {
			theType = type;
			theDescription = initDescrip;
		}

		/**
		 * Copy constructor
		 *
		 * @param toCopy The builder to copy
		 */
		protected CollectionBuilderImpl(CollectionBuilderImpl<E, ?> toCopy) {
			this(toCopy.theType, toCopy.theDescription);
			theBacking = toCopy.theBacking;
			theLocker = toCopy.theLocker;
			theSorting = toCopy.theSorting;
			theElementSource = toCopy.theElementSource;
			theSourceElements = toCopy.theSourceElements;
			theEquivalence = toCopy.theEquivalence;
		}

		@Override
		public B withBacking(BetterList<E> backing) {
			theBacking = backing;
			return (B) this;
		}

		@Override
		public B withEquivalence(Equivalence<? super E> equivalence) {
			theEquivalence = equivalence;
			return (B) this;
		}

		@Override
		public B withLocker(CollectionLockingStrategy locker) {
			return withLocker(__ -> locker);
		}

		@Override
		public B withLocker(Function<Object, CollectionLockingStrategy> locker) {
			theLocker = locker;
			return (B) this;
		}

		@Override
		public B safe(boolean safe) {
			withLocker(v -> safe ? new StampedLockingStrategy(v) : new FastFailLockingStrategy());
			return (B) this;
		}

		@Override
		public SortedBuilder<E, ?> sortBy(Comparator<? super E> sorting) {
			return new SortedBuilderImpl<>(this, sorting);
		}

		@Override
		public B withDescription(String description) {
			theDescription = description;
			return (B) this;
		}

		@Override
		public B withElementSource(BiFunction<ElementId, BetterCollection<?>, ElementId> elementSource) {
			theElementSource = elementSource;
			return (B) this;
		}

		@Override
		public B withSourceElements(BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> sourceElements) {
			theSourceElements = sourceElements;
			return (B) this;
		}

		@Override
		public DistinctBuilder<E, ?> distinct() {
			if (theSorting != null)
				return new DistinctSortedBuilderImpl<>(this, theSorting);
			else
				return new DistinctBuilderImpl<>(this);
		}

		/**
		 * @param sorting The sorting for the set
		 * @return A builder to build an {@link ObservableSortedSet} with these characteristics
		 */
		@Override
		public DistinctSortedBuilder<E, ?> distinctSorted(Comparator<? super E> sorting) {
			return new DistinctSortedBuilderImpl<>(this, sorting);
		}

		/** @return The type for the collection */
		protected TypeToken<E> getType() {
			return theType;
		}

		/** @return The pre-set backing for the collection */
		protected BetterList<E> getBacking() {
			BetterList<E> backing = theBacking;
			theBacking = null; // Can only be used once
			return backing;
		}

		/** @return The equivalence for the collection */
		protected Equivalence<? super E> getEquivalence() {
			return theEquivalence;
		}

		/** @return The element source for the collection */
		protected BiFunction<ElementId, BetterCollection<?>, ElementId> getElementSource() {
			return theElementSource;
		}

		/** @return The source element lookup function for the collection */
		protected BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> getSourceElements() {
			return theSourceElements;
		}

		/** @return The description for the collection */
		protected String getDescription() {
			return theDescription;
		}

		/**
		 * @param built The built collection
		 * @return The locker for the collection
		 */
		protected CollectionLockingStrategy getLocker(Object built) {
			if (theLocker != null)
				return theLocker.apply(built);
			else
				return new StampedLockingStrategy(built);
		}

		/** @return The sorting for the collection */
		protected Comparator<? super E> getSorting() {
			return theSorting;
		}

		@Override
		public ObservableCollection<E> build() {
			BetterList<E> backing = theBacking;
			if (backing == null) {
				RedBlackNodeList.RBNLBuilder<E, ?> builder = theSorting != null ? SortedTreeList.buildTreeList(theSorting)
					: BetterTreeList.build();
				backing = builder.withDescription(theDescription).withLocker(this::getLocker).build();
			}
			return new DefaultObservableCollection<>(theType, backing, theElementSource, theSourceElements, theEquivalence);
		}

		@Override
		public <V> DataControlledCollectionBuilder<E, V, ?> withData(Supplier<? extends List<? extends V>> data) {
			return new DataControlledCollectionBuilderImpl<>(build(), data);
		}
	}

	/**
	 * Default implementation of {@link ObservableCollectionBuilder.DataControlledCollectionBuilder}
	 *
	 * @param <E> The type of the values in the collection
	 * @param <V> The type of the source data
	 * @param <B> The sub-type of this builder
	 */
	public static class DataControlledCollectionBuilderImpl<E, V, B extends DataControlledCollectionBuilder<E, V, B>>
	implements DataControlledCollectionBuilder<E, V, B> {
		private final ObservableCollection<E> theBackingCollection;
		private final Supplier<? extends List<? extends V>> theBackingData;
		private DataControlAutoRefresher theAutoRefresh;
		private boolean isRefreshingOnAccess;
		private BiPredicate<? super E, ? super V> theEqualsTester;
		private CollectionUtils.AdjustmentOrder theAdjustmentOrder;
		private long theMaxRefreshFrequency;

		/**
		 * @param backingCollection The observable collection providing the observable functionality
		 * @param backingData Supplies backing data for each refresh
		 */
		public DataControlledCollectionBuilderImpl(ObservableCollection<E> backingCollection,
			Supplier<? extends List<? extends V>> backingData) {
			theBackingCollection = backingCollection;
			theBackingData = backingData;
			theEqualsTester = Objects::equals;
			theAdjustmentOrder = theBackingCollection.isContentControlled() ? CollectionUtils.AdjustmentOrder.AddLast
				: CollectionUtils.AdjustmentOrder.RightOrder;
			isRefreshingOnAccess = true;
		}

		/** @return The equals tester to preserve elements that are still present on refresh */
		protected BiPredicate<? super E, ? super V> getEqualsTester() {
			return theEqualsTester;
		}

		/**
		 * @return Affects the synchronization between existing data and backing data on refresh
		 * @see org.qommons.collect.CollectionUtils.CollectionAdjustment#adjust(CollectionSynchronizerE,
		 *      org.qommons.collect.CollectionUtils.AdjustmentOrder)
		 */
		protected CollectionUtils.AdjustmentOrder getAdjustmentOrder() {
			return theAdjustmentOrder;
		}

		/** @return The initial {@link DataControlledCollection#setMaxRefreshFrequency(long) max refresh frequency} */
		protected long getMaxRefreshFrequency() {
			return theMaxRefreshFrequency;
		}

		/** @return The observable collection to provide the observable functionality */
		protected ObservableCollection<E> getBackingCollection() {
			return theBackingCollection;
		}

		/** @return The supplier of backing data for each refresh */
		protected Supplier<? extends List<? extends V>> getBackingData() {
			return theBackingData;
		}

		/** @return The auto-refresher */
		protected DataControlAutoRefresher getAutoRefresh() {
			return theAutoRefresh;
		}

		/** @return Whether the collection should be refreshed synchronously each time it is accessed */
		protected boolean isRefreshingOnAccess() {
			return isRefreshingOnAccess;
		}

		@Override
		public B withEquals(BiPredicate<? super E, ? super V> equals) {
			theEqualsTester = equals;
			return (B) this;
		}

		@Override
		public B withOrder(CollectionUtils.AdjustmentOrder adjustmentOrder) {
			theAdjustmentOrder = adjustmentOrder;
			return (B) this;
		}

		@Override
		public B withMaxRefreshFrequency(long frequency) {
			theMaxRefreshFrequency = frequency;
			return (B) this;
		}

		@Override
		public B autoRefreshWith(DataControlAutoRefresher refresher) {
			theAutoRefresh = refresher;
			return (B) this;
		}

		@Override
		public B refreshOnAccess(boolean refresh) {
			isRefreshingOnAccess = refresh;
			return (B) this;
		}

		@Override
		public DataControlledCollection<E, V> build(CollectionUtils.CollectionSynchronizerE<E, ? super V, ?> synchronizer) {
			return new ObservableCollectionImpl.DataControlledCollectionImpl<>(theBackingCollection, theBackingData, theAutoRefresh,
				isRefreshingOnAccess, theEqualsTester, synchronizer, theAdjustmentOrder)//
				.setMaxRefreshFrequency(theMaxRefreshFrequency);
		}
	}

	/**
	 * Default implementation of {@link ObservableCollectionBuilder.SortedBuilder}
	 *
	 * @param <E> The type of element for the collection
	 * @param <B> The sub-type of the builder
	 */
	public static class SortedBuilderImpl<E, B extends SortedBuilder<E, B>> extends CollectionBuilderImpl<E, B>
	implements SortedBuilder<E, B> {
		/**
		 * @param toCopy The builder to copy
		 * @param sorting The sorting for the collection
		 */
		public SortedBuilderImpl(CollectionBuilderImpl<E, ?> toCopy, Comparator<? super E> sorting) {
			super(toCopy);
			super.withEquivalence(Equivalence.DEFAULT.sorted(TypeTokens.getRawType(getType()), sorting, true));
		}

		/**
		 * @param type The type for the collection
		 * @param initDescrip The initial (default) description for the collection
		 * @param sorting The sorting for the collection
		 */
		public SortedBuilderImpl(TypeToken<E> type, String initDescrip, Comparator<? super E> sorting) {
			super(type, initDescrip);
			super.withEquivalence(Equivalence.DEFAULT.sorted(TypeTokens.getRawType(getType()), sorting, true));
		}

		@Override
		protected Comparator<? super E> getSorting() {
			return getEquivalence().comparator();
		}

		@Override
		public B withBacking(BetterList<E> backing) {
			if (backing != null && !(backing instanceof BetterSortedList))
				throw new IllegalStateException("An ObservableSortedCollection must be backed by an instance of BetterSortedList");
			return (B) this;
		}

		@Override
		public B withEquivalence(Equivalence<? super E> equivalence) {
			throw new UnsupportedOperationException("Equivalence for sorted collections is defined by the comparator");
		}

		@Override
		protected Equivalence.SortedEquivalence<? super E> getEquivalence() {
			return (SortedEquivalence<? super E>) super.getEquivalence();
		}

		@Override
		public DistinctSortedBuilder<E, ?> distinct() {
			return new DistinctSortedBuilderImpl<>(this, getEquivalence().comparator());
		}

		@Override
		public ObservableSortedCollection<E> build() {
			BetterList<E> backing = getBacking();
			if (backing == null)
				backing = SortedTreeList.<E> buildTreeList(getSorting()).withDescription(getDescription()).withLocker(this::getLocker)
				.build();
			else if (!(backing instanceof BetterSortedList))
				throw new IllegalStateException("An ObservableSortedCollection must be backed by an instance of BetterSortedList");
			return new DefaultObservableSortedCollection<>(getType(), (BetterSortedList<E>) backing, getElementSource(),
				getSourceElements());
		}

		@Override
		public <V> DataControlledSortedCollectionBuilder<E, V, ?> withData(Supplier<? extends List<? extends V>> data) {
			return new DataControlledSortedCollectionBuilderImpl<>(build(), data);
		}
	}

	/**
	 * Default implementation of {@link ObservableCollectionBuilder.DataControlledSortedCollectionBuilder}
	 *
	 * @param <E> The type of the values in the collection
	 * @param <V> The type of the source data
	 * @param <B> The sub-type of this builder
	 */
	public static class DataControlledSortedCollectionBuilderImpl<E, V, B extends DataControlledSortedCollectionBuilder<E, V, B>>
	extends DataControlledCollectionBuilderImpl<E, V, B> implements DataControlledSortedCollectionBuilder<E, V, B> {
		/**
		 * @param backingCollection The observable collection providing the observable functionality
		 * @param backingData Supplies backing data for each refresh
		 */
		public DataControlledSortedCollectionBuilderImpl(ObservableSortedCollection<E> backingCollection,
			Supplier<? extends List<? extends V>> backingData) {
			super(backingCollection, backingData);
		}

		@Override
		protected ObservableSortedCollection<E> getBackingCollection() {
			return (ObservableSortedCollection<E>) super.getBackingCollection();
		}

		@Override
		public DataControlledCollection.Sorted<E, V> build(CollectionSynchronizerE<E, ? super V, ?> synchronizer) {
			return new ObservableSortedCollectionImpl.DataControlledSortedCollectionImpl<>(getBackingCollection(), getBackingData(),
				getAutoRefresh(), isRefreshingOnAccess(), getEqualsTester(), synchronizer, getAdjustmentOrder())//
				.setMaxRefreshFrequency(getMaxRefreshFrequency());
		}
	}

	/**
	 * Default implementation of {@link ObservableCollectionBuilder.DistinctBuilder}
	 *
	 * @param <E> The type of element for the collection
	 * @param <B> The sub-type of the builder
	 */
	public static class DistinctBuilderImpl<E, B extends DistinctBuilder<E, B>> extends CollectionBuilderImpl<E, B>
	implements DistinctBuilder<E, B> {
		/** @see ObservableCollectionBuilder.CollectionBuilderImpl#CollectionBuilderImpl(CollectionBuilderImpl) */
		public DistinctBuilderImpl(CollectionBuilderImpl<E, ?> toCopy) {
			super(toCopy);
		}

		/** @see ObservableCollectionBuilder.CollectionBuilderImpl#CollectionBuilderImpl(TypeToken, String) */
		public DistinctBuilderImpl(TypeToken<E> type, String initDescrip) {
			super(type, initDescrip);
		}

		@Override
		public ObservableSet<E> build() {
			return super.build().flow().distinct().collect();
		}

		@Override
		public <V> DataControlledSetBuilder<E, V, ?> withData(Supplier<? extends List<? extends V>> data) {
			return new DataControlledSetBuilderImpl<>(build(), data);
		}
	}

	/**
	 * Default implementation of {@link ObservableCollectionBuilder.DataControlledSetBuilder}
	 *
	 * @param <E> The type of the values in the set
	 * @param <V> The type of the source data
	 * @param <B> The sub-type of this builder
	 */
	public static class DataControlledSetBuilderImpl<E, V, B extends DataControlledSetBuilder<E, V, B>>
	extends DataControlledCollectionBuilderImpl<E, V, B> implements DataControlledSetBuilder<E, V, B> {
		/**
		 * @param backingCollection The observable collection providing the observable functionality
		 * @param backingData Supplies backing data for each refresh
		 */
		public DataControlledSetBuilderImpl(ObservableSet<E> backingCollection, Supplier<? extends List<? extends V>> backingData) {
			super(backingCollection, backingData);
		}

		@Override
		protected ObservableSet<E> getBackingCollection() {
			return (ObservableSet<E>) super.getBackingCollection();
		}

		@Override
		public DataControlledCollection.Set<E, V> build(CollectionSynchronizerE<E, ? super V, ?> synchronizer) {
			return new ObservableSetImpl.DataControlledSetImpl<>(getBackingCollection(), getBackingData(), getAutoRefresh(),
				isRefreshingOnAccess(), getEqualsTester(), synchronizer, getAdjustmentOrder())//
				.setMaxRefreshFrequency(getMaxRefreshFrequency());
		}
	}

	/**
	 * Default implementation of {@link ObservableCollectionBuilder.DistinctSortedBuilder}
	 *
	 * @param <E> The type of element for the collection
	 * @param <B> The sub-type of the builder
	 */
	public static class DistinctSortedBuilderImpl<E, B extends DistinctSortedBuilder<E, B>> extends SortedBuilderImpl<E, B>
	implements DistinctSortedBuilder<E, B> {
		/** @see ObservableCollectionBuilder.SortedBuilderImpl#SortedBuilderImpl(CollectionBuilderImpl, Comparator) */
		public DistinctSortedBuilderImpl(CollectionBuilderImpl<E, ?> toCopy, Comparator<? super E> sorting) {
			super(toCopy, sorting);
		}

		/** @see ObservableCollectionBuilder.SortedBuilderImpl#SortedBuilderImpl(TypeToken, String, Comparator) */
		public DistinctSortedBuilderImpl(TypeToken<E> type, String initDescrip, Comparator<? super E> sorting) {
			super(type, initDescrip, sorting);
		}

		@Override
		public B withBacking(BetterList<E> backing) {
			if (backing != null && !(backing instanceof BetterSortedSet))
				throw new IllegalStateException("An ObservableSortedSet must be backed by an instance of BetterSortedSet");
			return (B) this;
		}

		@Override
		public B withEquivalence(Equivalence<? super E> equivalence) {
			throw new UnsupportedOperationException("Equivalence for sorted sets is defined by the comparator");
		}

		@Override
		public DistinctSortedBuilder<E, ?> distinct() {
			return super.distinct();
		}

		@Override
		public ObservableSortedSet<E> build() {
			BetterList<E> backing = getBacking();
			if (backing == null)
				backing = BetterTreeSet.<E> buildTreeSet(getSorting()).withDescription(getDescription()).withLocker(this::getLocker)
				.build();
			else if (!(backing instanceof BetterSortedSet))
				throw new IllegalStateException("An ObservableSortedCollection must be backed by an instance of BetterSortedList");
			return new DefaultObservableSortedSet<>(getType(), (BetterSortedSet<E>) backing, getElementSource(), getSourceElements());
		}

		@Override
		public <V> DataControlledSortedSetBuilder<E, V, ?> withData(Supplier<? extends List<? extends V>> data) {
			return new DataControlledSortedSetBuilderImpl<>(build(), data);
		}
	}

	/**
	 * Default implementation of {@link ObservableCollectionBuilder.DataControlledSortedSetBuilder}
	 *
	 * @param <E> The type of the values in the set
	 * @param <V> The type of the source data
	 * @param <B> The sub-type of this builder
	 */
	public static class DataControlledSortedSetBuilderImpl<E, V, B extends DataControlledSortedSetBuilder<E, V, B>>
	extends DataControlledCollectionBuilderImpl<E, V, B> implements DataControlledSortedSetBuilder<E, V, B> {
		/**
		 * @param backingCollection The observable collection providing the observable functionality
		 * @param backingData Supplies backing data for each refresh
		 */
		public DataControlledSortedSetBuilderImpl(ObservableSortedSet<E> backingCollection,
			Supplier<? extends List<? extends V>> backingData) {
			super(backingCollection, backingData);
		}

		@Override
		protected ObservableSortedSet<E> getBackingCollection() {
			return (ObservableSortedSet<E>) super.getBackingCollection();
		}

		@Override
		public DataControlledCollection.SortedSet<E, V> build(CollectionSynchronizerE<E, ? super V, ?> synchronizer) {
			return new ObservableSortedSetImpl.DataControlledSortedSetImpl<>(getBackingCollection(), getBackingData(), getAutoRefresh(),
				isRefreshingOnAccess(), getEqualsTester(), synchronizer, getAdjustmentOrder())//
				.setMaxRefreshFrequency(getMaxRefreshFrequency());
		}
	}

	/** Default {@link DataControlAutoRefresher} implementation */
	public static class DefaultDataControlAutoRefresher implements DataControlAutoRefresher {
		private static class CollectionRefresher {
			final DataControlledCollection<?, ?> collection;
			final QommonsTimer.TaskHandle taskHandle;

			CollectionRefresher(DataControlledCollection<?, ?> collection, TaskHandle taskHandle) {
				this.collection = collection;
				this.taskHandle = taskHandle;
			}
		}

		private final QommonsTimer theTimer;
		private final ListenerList<CollectionRefresher> theRefreshers;
		private Duration theFrequency;
		private boolean isInitRefresh;
		private boolean isActive;
		private boolean isClosed;
		private double theAdaptive;

		/** @param frequency Global auto-refresh frequency */
		public DefaultDataControlAutoRefresher(Duration frequency) {
			this(QommonsTimer.getCommonInstance(), frequency);
		}

		/**
		 * @param timer The timer to auto-refresh with
		 * @param frequency Global auto-refresh frequency
		 */
		public DefaultDataControlAutoRefresher(QommonsTimer timer, Duration frequency) {
			theTimer = timer;
			theRefreshers = ListenerList.build().build();
			theFrequency = frequency;
			isActive = true;
			isInitRefresh = true;
		}

		/** @return Whether this auto-refresher is currently active */
		public boolean isActive() {
			return isActive;
		}

		/**
		 * Activates or temporarily deactivates this auto-refresher
		 *
		 * @param active Whether this auto-refresher should be currently active
		 * @return This refresher
		 */
		public DefaultDataControlAutoRefresher setActive(boolean active) {
			synchronized (this) {
				if (active == isActive)
					return this;
				isActive = active;
			}
			theRefreshers.forEach(r -> r.taskHandle.setActive(active));
			return this;
		}

		/**
		 * @return The adaptivity setting for this refresher
		 * @see #setAdaptive(double)
		 */
		public double getAdaptive() {
			return theAdaptive;
		}

		/**
		 * @param adaptive the adaptivity setting for this refresher. If greater than zero, this refresher will scale back refresh
		 *        frequencies for individual collections based on the amount of time it takes to refresh them. E.g. if a particular
		 *        collection takes 1s to perform a refresh operation and the refresher's adaptivity is 2.0, the refresher will wait a
		 *        minimum of 2 seconds between refreshers (e.g. the frequency will be ~3s, 1s for the operation and 2s waiting).
		 * @return This refresher
		 */
		public DefaultDataControlAutoRefresher setAdaptive(double adaptive) {
			theAdaptive = adaptive;
			return this;
		}

		/** @return Whether this refresher will refresh each collection as it is {@link #add(DataControlledCollection) added} */
		public boolean isInitRefresh() {
			return isInitRefresh;
		}

		/**
		 * @param initRefresh Whether this refresher should refresh each collection as it is {@link #add(DataControlledCollection) added}
		 * @return This refresher
		 */
		public DefaultDataControlAutoRefresher setInitRefresh(boolean initRefresh) {
			isInitRefresh = initRefresh;
			return this;
		}

		/** @return The global auto-refresh frequency with which this refresher refreshes its collections */
		public Duration getFrequency() {
			return theFrequency;
		}

		/**
		 * @param frequency The global auto-refresh frequency with which this refresher should refresh its collections
		 * @return This refresher
		 */
		public DefaultDataControlAutoRefresher setFrequency(Duration frequency) {
			theFrequency = frequency;
			theRefreshers.forEach(r -> {
				long maxRefresh = r.collection.getMaxRefreshFrequency();
				Duration collFreq;
				if (maxRefresh > 0) { // Don't try to refresh more often than the max refresh
					collFreq = Duration.ofMillis(maxRefresh);
					if (frequency.compareTo(collFreq) > 0)
						collFreq = frequency;
				} else
					collFreq = frequency;
				r.taskHandle.setFrequency(collFreq, false);
			});
			return this;
		}

		/** Causes asynchronous refresh ASAP of all collections managed by this refresher */
		public void refreshAll() {
			theRefreshers.forEach(r -> r.taskHandle.runImmediately());
		}

		/** @return Whether this refresher has been {@link #close() closed} */
		public boolean isClosed() {
			return isClosed;
		}

		/** Closes this refresher, ceasing all refresh activity and disabling {@link #add(DataControlledCollection)} */
		public void close() {
			isClosed = true;
			for (ListenerList.Element<CollectionRefresher> node = theRefreshers.poll(0); node != null; node = theRefreshers.poll(0)) {
				node.get().taskHandle.setActive(false);
			}
		}

		@Override
		public Runnable add(DataControlledCollection<?, ?> collection) {
			if (isClosed)
				throw new IllegalStateException("This refresher is closed");
			QommonsTimer.TaskHandle[] handle = new QommonsTimer.TaskHandle[1];
			long[] refreshTimes = new long[4];
			Arrays.fill(refreshTimes, -1);
			handle[0] = theTimer.build(() -> {
				if (theAdaptive > 0) {
					System.arraycopy(refreshTimes, 1, refreshTimes, 0, refreshTimes.length - 1);
					refreshTimes[refreshTimes.length - 1] = -1;
					int refreshes = 0;
					long totalRefresh = 0;
					for (long rt : refreshTimes) {
						if (rt < 0)
							break;
						refreshes++;
						totalRefresh += rt;
					}
					long now = System.currentTimeMillis();
					collection.refresh();
					refreshTimes[refreshes] = System.currentTimeMillis() - now;
					totalRefresh += refreshTimes[refreshes];
					long maxFreq = Math.max(theFrequency.toMillis(),
						Math.max(collection.getMaxRefreshFrequency(), (long) Math.ceil(totalRefresh / (refreshes + 1) * theAdaptive)));
					// System.out.println("Adaptive frequency for " + collection.getIdentity() + " to " + maxFreq);
					handle[0].setFrequency(Duration.ofMillis(maxFreq), false);
				} else
					collection.refresh();
			}, theFrequency, false);
			Runnable remove = theRefreshers.add(new CollectionRefresher(collection, handle[0]), true);
			if (isActive) {
				handle[0].setActive(true);
				if (isInitRefresh)
					handle[0].runImmediately();
			}
			return () -> {
				remove.run();
				handle[0].setActive(false);
			};
		}
	}
}
