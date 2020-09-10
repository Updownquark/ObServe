package org.observe.collect;

import org.observe.collect.ObservableCollection.CollectionDataFlow;

/** Options for various {@link ObservableCollection.CollectionDataFlow} */
public interface FlowOptions {
	/** Allows customization of the behavior of a {@link CollectionDataFlow#distinct(java.util.function.Consumer) distinct} set */
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

		/** @return Whether {@link #preserveSourceOrder(boolean)} is allowable for this option set */
		boolean canPreserveSourceOrder();
	}

	/** Simple {@link UniqueOptions} implementation */
	class SimpleUniqueOptions implements UniqueOptions {
		private final boolean isSorted;
		private boolean isUsingFirst = false;
		private boolean isPreservingSourceOrder = false;

		public SimpleUniqueOptions(boolean sorted) {
			isSorted = sorted;
		}

		@Override
		public SimpleUniqueOptions useFirst(boolean useFirst) {
			this.isUsingFirst = useFirst;
			return this;
		}

		@Override
		public boolean canPreserveSourceOrder() {
			return !isSorted;
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
}
