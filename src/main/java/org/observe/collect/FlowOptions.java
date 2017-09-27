package org.observe.collect;

import java.util.function.Function;

import org.observe.collect.ObservableCollection.ElementSetter;

public interface FlowOptions {
	interface XformOptions {
		XformOptions reEvalOnUpdate(boolean reEval);

		XformOptions fireIfUnchanged(boolean fire);

		XformOptions cache(boolean cache);

		boolean isReEvalOnUpdate();

		boolean isFireIfUnchanged();

		boolean isCached();
	}

	interface UniqueOptions {
		UniqueOptions useFirst(boolean useFirst);

		UniqueOptions preserveSourceOrder(boolean preserveOrder);

		boolean isUseFirst();

		boolean isPreservingSourceOrder();
	}

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

	class SimpleUniqueOptions implements UniqueOptions {
		private boolean useFirst = false;
		private boolean isPreservingSourceOrder = false;

		@Override
		public SimpleUniqueOptions useFirst(boolean useFirst) {
			this.useFirst = useFirst;
			return this;
		}

		@Override
		public SimpleUniqueOptions preserveSourceOrder(boolean preserveOrder) {
			isPreservingSourceOrder = preserveOrder;
			return this;
		}

		@Override
		public boolean isUseFirst() {
			return useFirst;
		}

		@Override
		public boolean isPreservingSourceOrder() {
			return isPreservingSourceOrder;
		}

	}

	class GroupingOptions extends AbstractXformOptions implements UniqueOptions {
		private final boolean isSorted;
		private boolean staticCategories = false;
		private boolean useFirst = false;
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

		public GroupingOptions withStaticCategories(boolean staticCategories) {
			this.staticCategories = staticCategories;
			return this;
		}

		@Override
		public GroupingOptions useFirst(boolean useFirst) {
			this.useFirst = useFirst;
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
			return useFirst;
		}

		@Override
		public boolean isPreservingSourceOrder() {
			return isPreservingSourceOrder;
		}

		public boolean isStaticCategories() {
			return staticCategories;
		}
	}

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
