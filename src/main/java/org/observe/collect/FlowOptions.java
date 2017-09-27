package org.observe.collect;

import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.ElementSetter;

import com.google.common.reflect.TypeToken;

/** Options for various {@link ObservableCollection.CollectionDataFlow} */
public interface FlowOptions {
	/** Super-interface used for options by several map-type operations */
	interface XformOptions {
		XformOptions reEvalOnUpdate(boolean reEval);

		XformOptions fireIfUnchanged(boolean fire);

		XformOptions cache(boolean cache);

		boolean isReEvalOnUpdate();

		boolean isFireIfUnchanged();

		boolean isCached();
	}

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

		boolean isUseFirst();

		boolean isPreservingSourceOrder();
	}

	/** A simple abstract implementation of XformOptions */
	abstract class AbstractXformOptions implements XformOptions {
		private boolean reEvalOnUpdate;
		private boolean fireIfUnchanged;
		private boolean isCached;

		public AbstractXformOptions() {
			reEvalOnUpdate = true;
			fireIfUnchanged = true;
			isCached = true;
		}

		@Override
		public XformOptions reEvalOnUpdate(boolean reEval) {
			reEvalOnUpdate = reEval;
			return this;
		}

		@Override
		public XformOptions fireIfUnchanged(boolean fire) {
			fireIfUnchanged = fire;
			return this;
		}

		@Override
		public XformOptions cache(boolean cache) {
			isCached = cache;
			return this;
		}

		@Override
		public boolean isReEvalOnUpdate() {
			return reEvalOnUpdate;
		}

		@Override
		public boolean isFireIfUnchanged() {
			return fireIfUnchanged;
		}

		@Override
		public boolean isCached() {
			return isCached;
		}
	}

	/**
	 * Allows customization of the behavior of a {@link CollectionDataFlow#map(TypeToken, Function, Consumer) mapped} collection
	 *
	 * @param <E> The source type
	 * @param <T> The mapped type
	 */
	class MapOptions<E, T> extends AbstractXformOptions {
		private Function<? super T, ? extends E> theReverse;
		private ElementSetter<? super E, ? super T> theElementReverse;

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

		public Function<? super T, ? extends E> getReverse() {
			return theReverse;
		}

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
	class GroupingOptions extends AbstractXformOptions implements UniqueOptions {
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

		public boolean isStaticCategories() {
			return isStaticCategories;
		}
	}

	/** An immutable version of {@link AbstractXformOptions} */
	abstract class XformDef {
		private final boolean reEvalOnUpdate;
		private final boolean fireIfUnchanged;
		private final boolean isCached;

		public XformDef(XformOptions options) {
			reEvalOnUpdate = options.isReEvalOnUpdate();
			fireIfUnchanged = options.isFireIfUnchanged();
			isCached = options.isCached();
		}

		public boolean isReEvalOnUpdate() {
			return reEvalOnUpdate;
		}

		public boolean isFireIfUnchanged() {
			return fireIfUnchanged;
		}

		public boolean isCached() {
			return isCached;
		}
	}

	/**
	 * An immutable version of {@link MapOptions}
	 *
	 * @param <E> The source type
	 * @param <T> The mapped type
	 */
	class MapDef<E, T> extends XformDef {
		private final Function<? super T, ? extends E> theReverse;
		private final ElementSetter<? super E, ? super T> theElementReverse;

		public MapDef(MapOptions<E, T> options) {
			super(options);
			theReverse = options.getReverse();
			theElementReverse = options.getElementReverse();
		}

		public ElementSetter<? super E, ? super T> getElementReverse() {
			return theElementReverse;
		}

		public Function<? super T, ? extends E> getReverse() {
			return theReverse;
		}
	}
}
