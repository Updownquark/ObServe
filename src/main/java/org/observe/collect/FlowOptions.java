package org.observe.collect;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.XformOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.FilterMapResult;
import org.qommons.LambdaUtils;
import org.qommons.TriFunction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;

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
		private MapReverse<E, T> theReverse;
		private Equivalence<? super T> theEquivalence;

		@Override
		public MapOptions<E, T> cache(boolean cache) {
			super.cache(cache);
			return this;
		}

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
		public MapOptions<E, T> propagateUpdateToParent(boolean propagate) {
			super.propagateUpdateToParent(propagate);
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

		public MapOptions<E, T> withReverse(Function<? super T, ? extends E> reverse) {
			return withReverse(reverse, null);
		}

		/**
		 * <p>
		 * Enables element setting in a mapped flow by defining a function able to produce source element values from mapped values.
		 * </p>
		 * <p>
		 * Using this method (as opposed to with the {@link MapOptions#withReverse(TriFunction, BiFunction) binary} reverse method) is
		 * better if the reverse mechanism does not require the previous source value. Flows created with this option may perform better for
		 * some operations.
		 * </p>
		 *
		 * @param reverse The function to produce source values from mapped values
		 * @param enabled An optional function to filter enabled mapped values
		 * @return This option set
		 */
		public MapOptions<E, T> withReverse(Function<? super T, ? extends E> reverse, Function<? super T, String> enabled) {
			theReverse = new SimpleMapReverse<>(reverse, enabled);
			return this;
		}

		/**
		 * <p>
		 * Enables element setting in a mapped flow by defining a function able to produce source element values from mapped values.
		 * </p>
		 * <p>
		 * If the reverse mechanism does not require the previous source value, the {@link #withReverse(Function, Function) unary} reverse
		 * method is preferred, since flows created with the binary option may perform worse for some operations.
		 * </p>
		 *
		 * @param reverse The function to produce source values from mapped and previous source values (the third argument false when
		 *        testing whether the operation is enabled, true when the operation is performed)
		 * @param enabled An optional function to filter enabled mapped values
		 * @return This option set
		 */
		public MapOptions<E, T> withReverse(TriFunction<? super E, ? super T, Boolean, ? extends E> reverse,
			BiFunction<? super E, ? super T, String> enabled) {
			theReverse = new SimpleMapReverse<>(reverse, enabled, true);
			return this;
		}

		/**
		 * Enables element setting in a mapped flow by defining a function that performs some operation on the source value with the target
		 * value, causing the mapping operation to produce the new target value
		 *
		 * @param setter The field setter
		 * @param enabled The function to disable such operations
		 * @return This option set
		 */
		public MapOptions<E, T> withFieldSetReverse(BiConsumer<? super E, ? super T> setter,
			BiFunction<? super E, ? super T, String> enabled) {
			return withFieldSetReverse(setter, enabled, null, null);
		}

		/**
		 * <p>
		 * Enables element setting in a mapped flow by defining a function that performs some operation on the source value with the target
		 * value, causing the mapping operation to become the new target value.
		 * </p>
		 * <p>
		 * This method also allow the creation of new source values as a function of a target value.
		 * </p>
		 *
		 * @param setter The field setter
		 * @param enabled The function to disable some set operations
		 * @param creator The function to create source values from target values (second function argument is false for capability queries
		 *        (like {@link BetterCollection#canAdd(Object)}), true for intentional operations (like
		 *        {@link BetterCollection#addElement(Object, boolean)})
		 * @param creationEnabled The function to disable some create operations
		 * @return This option set
		 */
		public MapOptions<E, T> withFieldSetReverse(BiConsumer<? super E, ? super T> setter,
			BiFunction<? super E, ? super T, String> enabled, BiFunction<? super T, Boolean, ? extends E> creator,
			Function<? super T, String> creationEnabled) {
			theReverse = new FieldSettingMapReverse<>(setter, enabled, creator, creationEnabled);
			return this;
		}

		public MapOptions<E, T> withReverse(MapReverse<E, T> reverse) {
			theReverse = reverse;
			return this;
		}

		@Override
		public MapOptions<E, T> manyToOne(boolean manyToOne) {
			super.manyToOne(manyToOne);
			return this;
		}

		@Override
		public MapOptions<E, T> oneToMany(boolean oneToMany) {
			super.oneToMany(oneToMany);
			return this;
		}

		/** @return The equivalence set to use for the mapped values */
		public Equivalence<? super T> getEquivalence() {
			return theEquivalence;
		}

		/** @return The reverse function, if set */
		public MapReverse<E, T> getReverse() {
			return theReverse;
		}
	}

	/**
	 * A result of {@link MapReverse#canReverse(Supplier, Object)}
	 *
	 * @param <E> The source type of the flow
	 */
	public interface ReverseQueryResult<E> {
		/** @return The error for the operation, if it was rejected */
		String getError();

		/**
		 * @return The reversed value, if the operation was accepted
		 * @throws UnsupportedOperationException If the operation was unsupported
		 * @throws IllegalArgumentException If the argument was illegal
		 */
		E getReversed() throws UnsupportedOperationException, IllegalArgumentException;

		/**
		 * @param message The rejection message
		 * @return The error result
		 */
		public static <E> ReverseQueryResult<E> reject(String message) {
			return new ReverseQueryResult<E>() {
				@Override
				public String getError() {
					return message;
				}

				@Override
				public E getReversed() {
					if (message.equals(StdMsg.UNSUPPORTED_OPERATION))
						throw new UnsupportedOperationException(message);
					else
						throw new IllegalArgumentException(message);
				}
			};
		}

		/**
		 * @param value The reversed value
		 * @return The value result
		 */
		public static <E> ReverseQueryResult<E> value(E value) {
			return new ReverseQueryResult<E>() {
				@Override
				public String getError() {
					return null;
				}

				@Override
				public E getReversed() {
					return value;
				}
			};
		}
	}

	/**
	 * Allows the ability to add and/or set elements in a mapped flow by providing source values from target values
	 *
	 * @param <E> The source type of the flow
	 * @param <T> The target type of the flow
	 */
	public interface MapReverse<E, T> {
		/** @return Whether this reverse depends on the previous source */
		boolean isStateful();

		/**
		 * @param previousSource Supplies the previous source value, if needed--will be null for add operation queries
		 * @param newValue The new target value
		 * @return The result, either {@link FilterMapResult#reject(String, boolean) rejected} or with its source set to the reversed value
		 */
		ReverseQueryResult<E> canReverse(Supplier<? extends E> previousSource, T newValue);

		/**
		 * @param previousSource Supplies the previous source value, if needed--will be null for add operation queries
		 * @param newValue The value to reverse
		 * @return The value to set in the source collection
		 */
		E reverse(Supplier<? extends E> previousSource, T newValue);
	}

	/**
	 * Simple {@link MapReverse} implementation
	 *
	 * @param <E> The source type of the flow
	 * @param <T> The target type of the flow
	 */
	public class SimpleMapReverse<E, T> implements MapReverse<E, T> {
		private final TriFunction<? super E, ? super T, Boolean, ? extends E> theReverse;
		private final BiFunction<? super E, ? super T, String> theEnablement;
		private final boolean isStateful;

		/**
		 * @param reverse A function accepting the previous source value (which may be null for additions or if the reverse is not
		 *        stateful), the value to reverse, and a boolean indicating if the reversed value is needed for a query (like
		 *        {@link MutableCollectionElement#isAcceptable(Object)}) or a real operation (like
		 *        {@link MutableCollectionElement#set(Object)})
		 * @param enabled An optional function to provide enablement for reverse operations
		 * @param stateful Whether the reverse function depends on the previous source value
		 */
		public SimpleMapReverse(TriFunction<? super E, ? super T, Boolean, ? extends E> reverse,
			BiFunction<? super E, ? super T, String> enabled, boolean stateful) {
			theReverse = reverse;
			theEnablement = enabled;
			isStateful = stateful;
		}

		/**
		 * @param reverse A function to provide source values from target values
		 * @param enabled An optional function to provide enablement for reverse operations
		 */
		public SimpleMapReverse(Function<? super T, ? extends E> reverse, Function<? super T, String> enabled) {
			this(
				LambdaUtils.printableTriFn((preSource, value, apply) -> reverse.apply(value), reverse::toString,
					LambdaUtils.getIdentifier(reverse)), //
				enabled == null ? null
					: LambdaUtils.printableBiFn((preSource, value) -> enabled.apply(value), enabled::toString,
						LambdaUtils.getIdentifier(enabled)), //
					false);
		}

		@Override
		public boolean isStateful() {
			return isStateful;
		}

		@Override
		public ReverseQueryResult<E> canReverse(Supplier<? extends E> previousSource, T newValue) {
			E sourceValue;
			if (previousSource == null || !isStateful)
				sourceValue = null;
			else
				sourceValue = previousSource.get();
			if (theEnablement != null) {
				String msg = theEnablement.apply(sourceValue, newValue);
				return ReverseQueryResult.reject(msg);
			}
			return ReverseQueryResult.value(theReverse.apply(sourceValue, newValue, false));
		}

		@Override
		public E reverse(Supplier<? extends E> previousSource, T newValue) {
			E sourceValue;
			if (previousSource == null || !isStateful)
				sourceValue = null;
			else
				sourceValue = previousSource.get();
			if (theEnablement != null) {
				String msg = theEnablement.apply(sourceValue, newValue);
				if (StdMsg.UNSUPPORTED_OPERATION.equals(msg))
					throw new UnsupportedOperationException(msg);
				else if (msg != null)
					throw new IllegalArgumentException(msg);
			}
			return theReverse.apply(sourceValue, newValue, true);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theReverse, theEnablement, isStateful);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof SimpleMapReverse))
				return false;
			SimpleMapReverse<?, ?> other = (SimpleMapReverse<?, ?>) obj;
			return theReverse.equals(other.theReverse)//
				&& isStateful == other.isStateful//
				&& Objects.equals(theEnablement, other.theEnablement);
		}

		@Override
		public String toString() {
			return theReverse.toString();
		}
	}

	/**
	 * A {@link MapReverse} implementation that sets a field on the object in the source flow to a value in the result flow, or some
	 * analogous operation
	 *
	 * @param <E> The source type of the flow
	 * @param <T> The target type of the flow
	 */
	public class FieldSettingMapReverse<E, T> implements MapReverse<E, T> {
		private final BiConsumer<? super E, ? super T> theFieldSetter;
		private final BiFunction<? super E, ? super T, String> theEnablement;
		private final BiFunction<? super T, Boolean, ? extends E> theCreator;
		private final Function<? super T, String> theCreationEnablement;

		/**
		 * @param fieldSetter Sets the field in the source value to the target value
		 * @param enabled An optional function to provide enablement for reverse operations
		 * @param creator An optional function to create new source values for additions (the second argument is whether the result is
		 *        intended for a query operation (like {@link BetterCollection#canAdd(Object)}) or a real operation (like
		 *        {@link BetterCollection#addElement(Object, boolean)})
		 * @param creationEnabled An optional function to provide enablement for create operations
		 */
		public FieldSettingMapReverse(BiConsumer<? super E, ? super T> fieldSetter, BiFunction<? super E, ? super T, String> enabled,
			BiFunction<? super T, Boolean, ? extends E> creator, Function<? super T, String> creationEnabled) {
			theFieldSetter = fieldSetter;
			theEnablement = enabled;
			theCreator = creator;
			theCreationEnablement = creationEnabled;
		}

		@Override
		public boolean isStateful() {
			return true;
		}

		@Override
		public ReverseQueryResult<E> canReverse(Supplier<? extends E> previousSource, T newValue) {
			if (previousSource != null) {
				E sourceValue = previousSource.get();
				if (theEnablement != null) {
					String msg = theEnablement.apply(sourceValue, newValue);
					if (msg != null)
						return ReverseQueryResult.reject(msg);
				}
				// No source change
				return ReverseQueryResult.value(sourceValue);
			} else if (theCreator == null)
				return ReverseQueryResult.reject(StdMsg.UNSUPPORTED_OPERATION);
			else {
				if (theCreationEnablement != null) {
					String msg = theCreationEnablement.apply(newValue);
					if (msg != null)
						return ReverseQueryResult.reject(msg);
				}
				return ReverseQueryResult.value(theCreator.apply(newValue, false));
			}
		}

		@Override
		public E reverse(Supplier<? extends E> previousSource, T newValue) {
			if (previousSource != null) {
				E sourceValue = previousSource.get();
				if (theEnablement != null) {
					String msg = theEnablement.apply(sourceValue, newValue);
					if (StdMsg.UNSUPPORTED_OPERATION.equals(msg))
						throw new UnsupportedOperationException(msg);
					else if (msg != null)
						throw new IllegalArgumentException(msg);
				}
				theFieldSetter.accept(sourceValue, newValue);
				return sourceValue;
			} else if (theCreator == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else {
				if (theCreationEnablement != null) {
					String msg = theCreationEnablement.apply(newValue);
					if (StdMsg.UNSUPPORTED_OPERATION.equals(msg))
						throw new UnsupportedOperationException(msg);
					else if (msg != null)
						throw new IllegalArgumentException(msg);
				}
				return theCreator.apply(newValue, true);
			}
		}

		@Override
		public int hashCode() {
			return Objects.hash(theFieldSetter, theEnablement);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof SimpleMapReverse))
				return false;
			FieldSettingMapReverse<?, ?> other = (FieldSettingMapReverse<?, ?>) obj;
			return theFieldSetter.equals(other.theFieldSetter)//
				&& Objects.equals(theEnablement, other.theEnablement);
		}

		@Override
		public String toString() {
			return theFieldSetter.toString();
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
		private final MapReverse<E, T> theReverse;
		private final Equivalence<? super T> theEquivalence;

		public MapDef(MapOptions<E, T> options) {
			super(options);
			theReverse = options.getReverse();
			theEquivalence = options.getEquivalence();
		}

		/** @return The equivalence set to use for the mapped values */
		public Equivalence<? super T> getEquivalence() {
			return theEquivalence;
		}

		/** @return The reverse function, if set */
		public MapReverse<E, T> getReverse() {
			return theReverse;
		}

		@Override
		public int hashCode() {
			return super.hashCode() * 7 + Objects.hash(theReverse, theEquivalence);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!(obj instanceof MapDef) || !super.equals(obj))
				return false;
			MapDef<?, ?> other = (MapDef<?, ?>) obj;
			return Objects.equals(theReverse, other.theReverse)//
				&& Objects.equals(theEquivalence, other.theEquivalence);
		}
	}
}
