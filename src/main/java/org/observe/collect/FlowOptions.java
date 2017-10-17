package org.observe.collect;

import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.XformOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.ElementSetter;

import com.google.common.reflect.TypeToken;

/** Options for various {@link ObservableCollection.CollectionDataFlow} */
public interface FlowOptions {
	/** Allows customization of the behavior of a {@link CollectionDataFlow#distinct(Consumer) distinct} set */
	interface UniqueOptions {
		/**
		 * @param useFirst Whether to always use the first element in the source to represent other equivalent values. If this is false (the
		 *        default), the produced collection may be able to fire fewer events because elements that are added earlier in the
		 *        collection can be ignored if they are already represented.
		 * @return This option set
		 */
		UniqueOptions useFirst(boolean useFirst);

		/**
		 * <p>
		 * Adjusts whether the order of elements in the source collection should be preserved in the result.
		 * </p>
		 * <p>
		 * This option may cause extra events in the unique collection, as elements may change their order as a result of the representative
		 * source element being removed or changed.
		 * </p>
		 * <p>
		 * This option is unavailable for sorted uniqueness, in which case this call will be ignored.
		 * </p>
		 *
		 * @param preserveOrder Whether to preserve the source element order in the unique flow
		 * @return This option set
		 */
		UniqueOptions preserveSourceOrder(boolean preserveOrder);

		/** @return Whether {@link #useFirst(boolean) useFirst} is set */
		boolean isUseFirst();

		/** @return Whether {@link #preserveSourceOrder(boolean) preserving source order} is set */
		boolean isPreservingSourceOrder();
	}

	/**
	 * Allows customization of the behavior of a {@link CollectionDataFlow#map(TypeToken, Function, Consumer) mapped} collection
	 *
	 * @param <E> The source type
	 * @param <T> The mapped type
	 */
	class MapOptions<E, T> extends XformOptions.SimpleXformOptions {
		private Function<? super T, ? extends E> theReverse;
		private ElementSetter<? super E, ? super T> theElementReverse;
		private Equivalence<? super T> theEquivalence;

		@Override
		public MapOptions<E, T> reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return this;
		}

		@Override
		public MapOptions<E, T> fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return this;
		}

		@Override
		public MapOptions<E, T> cache(boolean cache) {
			super.cache(cache);
			return this;
		}

		/**
		 * @param equivalence The equivalence set to use for the mapped values
		 * @return This builder
		 */
		public MapOptions<E, T> withEquivalence(Equivalence<? super T> equivalence) {
			theEquivalence = equivalence;
			return this;
		}

		/**
		 * Specifies a reverse function for the operation, which can allow adding values to the derived collection
		 *
		 * @param reverse The function to convert a result of this map operation into a source-compatible value
		 * @return This builder
		 */
		public MapOptions<E, T> withReverse(Function<? super T, ? extends E> reverse) {
			theReverse = reverse;
			return this;
		}

		/**
		 * Specifies an intra-element reverse function for the operation, which can allow adding values to the derived collection collection
		 *
		 * @param reverse The function that may modify an element in the source via a result of this map operation without modifying the
		 *        source
		 * @return This builder
		 */
		public MapOptions<E, T> withElementSetting(ElementSetter<? super E, ? super T> reverse) {
			theElementReverse = reverse;
			return this;
		}

		/** @return The equivalence set to use for the mapped values */
		public Equivalence<? super T> getEquivalence() {
			return theEquivalence;
		}

		/** @return The reverse function, if set */
		public Function<? super T, ? extends E> getReverse() {
			return theReverse;
		}

		/** @return The element reverse function, if set */
		public ElementSetter<? super E, ? super T> getElementReverse() {
			return theElementReverse;
		}
	}

	/** Simple {@link UniqueOptions} implementation */
	class SimpleUniqueOptions implements UniqueOptions {
		private boolean isUsingFirst = false;
		private boolean isPreservingSourceOrder = false;

		@Override
		public SimpleUniqueOptions useFirst(boolean useFirst) {
			this.isUsingFirst = useFirst;
			return this;
		}

		@Override
		public SimpleUniqueOptions preserveSourceOrder(boolean preserveOrder) {
			isPreservingSourceOrder = preserveOrder;
			return this;
		}

		@Override
		public boolean isUseFirst() {
			return isUsingFirst;
		}

		@Override
		public boolean isPreservingSourceOrder() {
			return isPreservingSourceOrder;
		}

	}

	/** Options used by {@link ObservableCollection.CollectionDataFlow#groupBy(TypeToken, Function, Consumer)} */
	class GroupingOptions extends XformOptions.SimpleXformOptions implements UniqueOptions {
		private final boolean isSorted;
		private boolean isStaticCategories = false;
		private boolean isUsingFirst = false;
		private boolean isPreservingSourceOrder = false;

		public GroupingOptions(boolean sorted) {
			isSorted = sorted;
		}

		@Override
		public GroupingOptions reEvalOnUpdate(boolean reEval) {
			return (GroupingOptions) super.reEvalOnUpdate(reEval);
		}

		@Override
		public GroupingOptions fireIfUnchanged(boolean fire) {
			return (GroupingOptions) super.fireIfUnchanged(fire);
		}

		@Override
		public GroupingOptions cache(boolean cache) {
			return (GroupingOptions) super.cache(cache);
		}

		/**
		 * @param staticCategories Whether the categorization of the source values is static or dynamic
		 * @return This option set
		 */
		public GroupingOptions withStaticCategories(boolean staticCategories) {
			this.isStaticCategories = staticCategories;
			return this;
		}

		@Override
		public GroupingOptions useFirst(boolean useFirst) {
			this.isUsingFirst = useFirst;
			return this;
		}

		@Override
		public GroupingOptions preserveSourceOrder(boolean preserveOrder) {
			if (isSorted && preserveOrder) {
				System.err.println(
					"Preserve source order is not allowed for sorted maps," + " where ordering is determined by the uniqueness itself");
				return this;
			}
			isPreservingSourceOrder = preserveOrder;
			return this;
		}

		@Override
		public boolean isUseFirst() {
			return isUsingFirst;
		}

		@Override
		public boolean isPreservingSourceOrder() {
			return isPreservingSourceOrder;
		}

		/** @return Whether {@link #withStaticCategories(boolean) static categories} is set */
		public boolean isStaticCategories() {
			return isStaticCategories;
		}
	}

	/**
	 * An immutable version of {@link MapOptions}
	 *
	 * @param <E> The source type
	 * @param <T> The mapped type
	 */
	class MapDef<E, T> extends XformOptions.XformDef {
		private final Function<? super T, ? extends E> theReverse;
		private final ElementSetter<? super E, ? super T> theElementReverse;
		private final Equivalence<? super T> theEquivalence;

		public MapDef(MapOptions<E, T> options) {
			super(options);
			theReverse = options.getReverse();
			theElementReverse = options.getElementReverse();
			theEquivalence = options.getEquivalence();
		}

		/** @return The equivalence set to use for the mapped values */
		public Equivalence<? super T> getEquivalence() {
			return theEquivalence;
		}

		/** @return The element reverse function, if set */
		public ElementSetter<? super E, ? super T> getElementReverse() {
			return theElementReverse;
		}

		/** @return The reverse function, if set */
		public Function<? super T, ? extends E> getReverse() {
			return theReverse;
		}
	}

	class GroupingDef {
		private final boolean isStaticCategories;
		private final boolean isUsingFirst;
		private final boolean isPreservingSourceOrder;

		public GroupingDef(GroupingOptions options) {
			isStaticCategories = options.isStaticCategories();
			isUsingFirst = options.isUseFirst();
			isPreservingSourceOrder = options.isPreservingSourceOrder();
		}

		public boolean isStaticCategories() {
			return isStaticCategories;
		}

		public boolean isUsingFirst() {
			return isUsingFirst;
		}

		public boolean isPreservingSourceOrder() {
			return isPreservingSourceOrder;
		}
	}
}
