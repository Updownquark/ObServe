package org.observe.collect;

import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.Equivalence.SortedEquivalence;
import org.observe.util.TypeTokens;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.StampedLockingStrategy;
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
	}
}
