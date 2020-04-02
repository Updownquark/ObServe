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

		/** @return Whether {@link #preserveSourceOrder(boolean)} is allowable for this option set */
		boolean canPreserveSourceOrder();
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
		private boolean isManyToOne;
		private boolean isOneToMany;

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

		/**
		 * @param manyToOne Whether the mapping may produce the same output from different source values
		 * @return This builder
		 */
		public MapOptions<E, T> manyToOne(boolean manyToOne) {
			isManyToOne = manyToOne;
			return this;
		}

		/**
		 * @param oneToMany Whether the reverse mapping may produce the same source value from different potential collection values
		 * @return This builder
		 */
		public MapOptions<E, T> oneToMany(boolean oneToMany) {
			isOneToMany = oneToMany;
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

		/** @return Whether the mapping may produce the same output from different source values */
		public boolean isManyToOne() {
			return isManyToOne;
		}
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
		private final boolean isManyToOne;
		private final boolean isOneToMany;

		public MapDef(MapOptions<E, T> options) {
			super(options);
			theReverse = options.getReverse();
			theElementReverse = options.getElementReverse();
			theEquivalence = options.getEquivalence();
			isManyToOne = options.isManyToOne;
			isOneToMany = options.isOneToMany;
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

		/** @return Whether the mapping may produce the same output from different source values */
		public boolean isManyToOne() {
			return isManyToOne;
		}

		/** @return Whether the reverse mapping may produce the same source value from different potential collection values */
		public boolean isOneToMany() {
			return isOneToMany;
		}
	}
}
