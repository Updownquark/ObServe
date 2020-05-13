package org.observe.collect;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.observe.XformOptions;
import org.observe.collect.FlowOptions.MapReverse;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.qommons.LambdaUtils;
import org.qommons.TriFunction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * Options for creating flat-mapped {@link CollectionDataFlow}s
 *
 * @param <S> The type of the source/primary stream
 * @param <V> The type of the mapped/secondary stream(s)
 * @param <X> The type of the target stream
 */
public interface FlatMapOptions<S, V, X> extends XformOptions {
	/**
	 * Finalized options for a flat-mapped {@link CollectionDataFlow}
	 *
	 * @param <S> The type of the source/primary stream
	 * @param <V> The type of the mapped/secondary stream(s)
	 * @param <X> The type of the target stream
	 */
	public class FlatMapDef<S, V, X> extends XformOptions.XformDef {
		private final TriFunction<? super S, ? super V, ? super X, ? extends X> theMap;
		private final FlatMapReverse<S, V, X> theReverse;

		FlatMapDef(SimpleFlatMapOptions<S, V, X> options, TriFunction<? super S, ? super V, ? super X, ? extends X> map) {
			super(options);
			theMap = map;
			theReverse = options.theReverse;
		}

		@Override
		public int hashCode() {
			return super.hashCode() * 7 + Objects.hash(theMap, theReverse);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof FlatMapDef) || !super.equals(obj))
				return false;
			FlatMapDef<?, ?, ?> other = (FlatMapDef<?, ?, ?>) obj;
			return theMap.equals(other.theMap)//
				&& Objects.equals(theReverse, other.theReverse);
		}

		@Override
		public String toString() {
			return new StringBuilder("flatMap(").append(theMap).append(super.toString()).toString();
		}

		/**
		 * @param source The value of an element from the source/primary flow
		 * @param value The value of an element from a mapped/secondary flow
		 * @param previous The previous value of the mapped/secondary element--may be null for a new element or if this option set is not
		 *        {@link #isCached()}
		 * @return The value for the target flow
		 */
		public X map(S source, V value, X previous) {
			return theMap.apply(source, value, previous);
		}

		/** @return The reversal mechanism for this flat map def */
		public FlatMapReverse<S, V, X> getReverse() {
			return theReverse;
		}
	}

	@Override
	FlatMapOptions<S, V, X> cache(boolean cache);

	@Override
	FlatMapOptions<S, V, X> reEvalOnUpdate(boolean reEval);

	@Override
	FlatMapOptions<S, V, X> fireIfUnchanged(boolean fire);

	@Override
	FlatMapOptions<S, V, X> propagateUpdateToParent(boolean propagate);

	@Override
	FlatMapOptions<S, V, X> manyToOne(boolean manyToOne);

	@Override
	FlatMapOptions<S, V, X> oneToMany(boolean oneToMany);

	/**
	 * <p>
	 * Enables element setting in a flat-mapped flow by defining a function able to produce source element values from target values.
	 * </p>
	 * <p>
	 * Using this method (as opposed to with the {@link FlatMapOptions#replaceSource(TriFunction, TriFunction) ternary} reverse method) is
	 * better if the reverse mechanism does not require the previous source value. Flows created with this option may perform better for
	 * some operations.
	 * </p>
	 *
	 * @param reverse The function to produce source values from mapped/secondary and target value tuples
	 * @param enabled An optional function to disable addition/update operations
	 * @return This option set
	 */
	default FlatMapOptions<S, V, X> replaceSource(BiFunction<? super V, ? super X, ? extends S> reverse,
		BiFunction<? super V, ? super X, String> enabled){
		return withReverse(new SimpleFlatMapReverse<>(//
			LambdaUtils.<S, V, X, S> printableTriFn((s, v, x) -> reverse.apply(v, x), reverse::toString,
				LambdaUtils.getIdentifier(reverse)),
			null,
			enabled == null ? null : LambdaUtils.<S, V, X, String> printableTriFn((s, v, x) -> enabled.apply(v, x), enabled::toString,
				LambdaUtils.getIdentifier(enabled)),
				false));
	}

	/**
	 * Enables element setting in a flat-mapped flow by defining a function able to produce source element values from target values.
	 *
	 * @param reverse Function to produce source values from previous source, mapped/secondary, and target values
	 * @param enabled An optional function to disable addition/update operations
	 * @return This option set
	 */
	default FlatMapOptions<S, V, X> replaceSource(TriFunction<? super S, ? super V, ? super X, ? extends S> reverse,
		TriFunction<? super S, ? super V, ? super X, String> enabled){
		return withReverse(new SimpleFlatMapReverse<>(reverse, null, enabled, false));
	}

	/**
	 * <p>
	 * Enables element setting in a flat-mapped flow by defining a function able to produce mapped/secondary element values from target
	 * values.
	 * </p>
	 * <p>
	 * Using this method (as opposed to with the {@link FlatMapOptions#replaceValue(TriFunction, TriFunction) ternary} reverse method) is
	 * better if the reverse mechanism does not require the previous mapped/secondary value. Flows created with this option may perform
	 * better for some operations.
	 * </p>
	 *
	 * @param reverse The function to produce mapped/secondary values from source/primary and target value tuples
	 * @param enabled An optional function to disable addition/update operations
	 * @return This option set
	 */
	default FlatMapOptions<S, V, X> replaceValue(BiFunction<? super S, ? super X, ? extends V> reverse,
		BiFunction<? super S, ? super X, String> enabled){
		return withReverse(new SimpleFlatMapReverse<>(null,
			LambdaUtils.<S, V, X, V> printableTriFn((s, v, x) -> reverse.apply(s, x), reverse::toString,
				LambdaUtils.getIdentifier(reverse)),
			enabled == null ? null : LambdaUtils.<S, V, X, String> printableTriFn((s, v, x) -> enabled.apply(s, x), enabled::toString,
				LambdaUtils.getIdentifier(enabled)),
				false));
	}

	/**
	 * <p>
	 * Enables element setting in a flat-mapped flow by defining a function able to produce mapped/secondary element values from target
	 * values.
	 * </p>
	 * <p>
	 * If the reverse mechanism does not require the previous mapped/secondary value, the {@link #replaceValue(BiFunction, BiFunction)
	 * binary} reverse method is preferred, since flows created with the ternary option may perform worse for some operations.
	 * </p>
	 *
	 * @param reverse Function to produce mapped/secondary values from source/primary, previous mapped/secondary, and target values
	 * @param enabled An optional function to disable addition/update operations
	 * @return This option set
	 */
	default FlatMapOptions<S, V, X> replaceValue(TriFunction<? super S, ? super V, ? super X, ? extends V> reverse,
		TriFunction<? super S, ? super V, ? super X, String> enabled) {
		return withReverse(new SimpleFlatMapReverse<>(null, reverse, enabled, true));
	}

	/**
	 * Enables element setting in a mapped flow by defining a function that performs some operation on the source value with the target
	 * value, causing the mapping operation to become the new target value
	 *
	 * @param setter The field setter
	 * @param enabled The function to disable such operations
	 * @return This option set
	 */
	default FlatMapOptions<S, V, X> sourceFieldSetter(BiConsumer<? super S, ? super X> setter,
		BiFunction<? super S, ? super X, String> enabled) {
		return withReverse(new FieldSettingFlatMapReverse<>(setter, null, //
			enabled == null ? null : LambdaUtils.<S, V, X, String> printableTriFn((s, v, x) -> enabled.apply(s, x), enabled::toString,
				LambdaUtils.getIdentifier(enabled)),
				null, null));
	}

	/**
	 * Enables element setting in a mapped flow by defining a function that performs some operation on the mapped/secondary value with the
	 * target value, causing the mapping operation to produce the new target value
	 *
	 * @param setter The field setter
	 * @param enabled The function to disable such operations
	 * @return This option set
	 */
	default FlatMapOptions<S, V, X> valueFieldSetter(BiConsumer<? super V, ? super X> setter,
		BiFunction<? super V, ? super X, String> enabled) {
		return withReverse(new FieldSettingFlatMapReverse<>(null, setter, //
			enabled == null ? null : LambdaUtils.<S, V, X, String> printableTriFn((s, v, x) -> enabled.apply(v, x), enabled::toString,
				LambdaUtils.getIdentifier(enabled)),
				null, null));
	}

	/**
	 * Enables element setting in a mapped flow by defining a function that performs some operation on the mapped/secondary value with the
	 * target value, causing the mapping operation to produce the new target value
	 *
	 * @param setter The field setter
	 * @param enabled The function to disable such operations
	 * @param creator The function to create source values from target values (third function argument is false for capability queries (like
	 *        {@link BetterCollection#canAdd(Object)}), true for intentional operations (like
	 *        {@link BetterCollection#addElement(Object, boolean)})
	 * @param createEnabled The function to disable some create operations
	 * @return This option set
	 */
	default FlatMapOptions<S, V, X> valueFieldSetter(BiConsumer<? super V, ? super X> setter,
		BiFunction<? super V, ? super X, String> enabled, TriFunction<? super S, ? super X, Boolean, ? extends V> creator,
		BiFunction<? super S, ? super X, String> createEnabled) {
		return withReverse(new FieldSettingFlatMapReverse<>(null, setter, //
			enabled == null ? null : LambdaUtils.<S, V, X, String> printableTriFn((s, v, x) -> enabled.apply(v, x), enabled::toString,
				LambdaUtils.getIdentifier(enabled)),
				creator, createEnabled));
	}

	/**
	 * @param reverse The reverse mechanism to enable addition and element set operations
	 * @return This flow
	 */
	FlatMapOptions<S, V, X> withReverse(FlatMapReverse<S, V, X> reverse);

	/**
	 * Produces a flat-map definition using a mapping of source/primary and mapped/secondary values to target value
	 *
	 * @param map The function to produce target values from source/primary and mapped/secondary tuples
	 * @return The flat map definition to use to produce the flow
	 */
	default FlatMapDef<S, V, X> map(BiFunction<? super S, ? super V, ? extends X> map) {
		return map((s, v, x) -> map.apply(s, v));
	}

	/**
	 * Produces a flat-map definition using a mapping of source/primary, mapped/secondary, and previous target values to target value
	 *
	 * @param map The function to produce target values from source/primary, mapped/secondary, and previous target value triples
	 * @return The flat map definition to use to produce the flow
	 */
	FlatMapDef<S, V, X> map(TriFunction<? super S, ? super V, ? super X, ? extends X> map);


	/**
	 * A result of {@link MapReverse#canReverse(Supplier, Object)}
	 *
	 * @param <S> The source type of the flow
	 * @param <V> The type of the mapped/secondary flow(s)
	 */
	public interface FlatMapReverseQueryResult<S, V> {
		/** @return The error for the operation, if it was rejected */
		String getError();

		/** @return Whether the result of the operation should update the value in the source flow */
		boolean replaceSource();

		/**
		 * @return The reversed source value, if the operation was accepted
		 * @throws UnsupportedOperationException If the operation was unsupported
		 * @throws IllegalArgumentException If the argument was illegal
		 */
		S getSource() throws UnsupportedOperationException, IllegalArgumentException;

		/** @return Whether the result of the operation should update the value in the source flow */
		boolean replaceSecondary();

		/**
		 * @return The reversed secondary value, if the operation was accepted
		 * @throws UnsupportedOperationException If the operation was unsupported
		 * @throws IllegalArgumentException If the argument was illegal
		 */
		V getSecondary() throws UnsupportedOperationException, IllegalArgumentException;

		/** @return A result that accepts the value and does no replacement in any source flow */
		public static <S, V> FlatMapReverseQueryResult<S, V> noOp() {
			return new FlatMapReverseQueryResult<S, V>() {
				@Override
				public String getError() {
					return null;
				}

				@Override
				public boolean replaceSource() {
					return false;
				}

				@Override
				public S getSource() throws UnsupportedOperationException, IllegalArgumentException {
					throw new IllegalStateException("No source to replace");
				}

				@Override
				public boolean replaceSecondary() {
					return false;
				}

				@Override
				public V getSecondary() throws UnsupportedOperationException, IllegalArgumentException {
					throw new IllegalStateException("No value to replace");
				}
			};
		}

		/**
		 * @param message The rejection message
		 * @return The error result
		 */
		public static <S, V> FlatMapReverseQueryResult<S, V> reject(String message) {
			return new FlatMapReverseQueryResult<S, V>() {
				@Override
				public String getError() {
					return message;
				}

				@Override
				public boolean replaceSource() {
					return false;
				}

				@Override
				public S getSource() {
					if (message.equals(StdMsg.UNSUPPORTED_OPERATION))
						throw new UnsupportedOperationException(message);
					else
						throw new IllegalArgumentException(message);
				}

				@Override
				public boolean replaceSecondary() {
					return false;
				}

				@Override
				public V getSecondary() {
					if (message.equals(StdMsg.UNSUPPORTED_OPERATION))
						throw new UnsupportedOperationException(message);
					else
						throw new IllegalArgumentException(message);
				}
			};
		}

		/**
		 * @param value value to replace in the mapped/secondary flow
		 * @return A result that will replace the given value for the element in the mapped/secondary flow
		 */
		public static <S, V> FlatMapReverseQueryResult<S, V> value(V value) {
			return new FlatMapReverseQueryResult<S, V>() {
				@Override
				public String getError() {
					return null;
				}

				@Override
				public boolean replaceSource() {
					return false;
				}

				@Override
				public S getSource() throws UnsupportedOperationException, IllegalArgumentException {
					throw new IllegalStateException("No source to replace");
				}

				@Override
				public boolean replaceSecondary() {
					return true;
				}

				@Override
				public V getSecondary() throws UnsupportedOperationException, IllegalArgumentException {
					return value;
				}
			};
		}

		/**
		 * @param source value to replace in the source flow
		 * @return A result that will replace the given value for the element in the source flow
		 */
		public static <S, V> FlatMapReverseQueryResult<S, V> sourceValue(S source) {
			return new FlatMapReverseQueryResult<S, V>() {
				@Override
				public String getError() {
					return null;
				}

				@Override
				public boolean replaceSource() {
					return true;
				}

				@Override
				public S getSource() throws UnsupportedOperationException, IllegalArgumentException {
					return source;
				}

				@Override
				public boolean replaceSecondary() {
					return true;
				}

				@Override
				public V getSecondary() throws UnsupportedOperationException, IllegalArgumentException {
					throw new IllegalStateException("No value to replace");
				}
			};
		}

		/**
		 * @param source value to replace in the source flow
		 * @param value value to replace in the mapped/secondary flow
		 * @return A result that will replace the given values for the elements in the source and mapped/secondary flow
		 */
		public static <S, V> FlatMapReverseQueryResult<S, V> values(S source, V value) {
			return new FlatMapReverseQueryResult<S, V>() {
				@Override
				public String getError() {
					return null;
				}

				@Override
				public boolean replaceSource() {
					return true;
				}

				@Override
				public S getSource() throws UnsupportedOperationException, IllegalArgumentException {
					return source;
				}

				@Override
				public boolean replaceSecondary() {
					return true;
				}

				@Override
				public V getSecondary() throws UnsupportedOperationException, IllegalArgumentException {
					return value;
				}
			};
		}
	}

	/**
	 * Allows the ability to add and/or set elements in a flat-mapped flow by providing source values from target values
	 *
	 * @param <S> The type of the source flow
	 * @param <V> The type of mapped/secondary flow
	 * @param <X> The target type of the flow
	 */
	public interface FlatMapReverse<S, V, X> {
		/** @return Whether this reverse depends on the previous mapped/secondary values */
		boolean isStateful();

		/**
		 * @param sourceValue Supplies the previous source value for the element--null for addition and may be null if this reverse is not
		 *        {@link #isStateful() stateful}
		 * @param secondary Supplies the previous secondary value for the element--null for addition and may be null if this reverse is not
		 *        {@link #isStateful() stateful}
		 * @param newValue The new result value
		 * @return The result with either {@link FlatMapOptions.FlatMapReverseQueryResult#getSource() source} and
		 *         {@link FlatMapOptions.FlatMapReverseQueryResult#getSecondary() secondary} values, or an
		 *         {@link FlatMapOptions.FlatMapReverseQueryResult#getError() error}
		 */
		FlatMapReverseQueryResult<S, V> canReverse(Supplier<? extends S> sourceValue, Supplier<? extends V> secondary, X newValue);

		/**
		 * @param sourceValue Supplies the previous source value for the element--null for addition and may be null if this reverse is not
		 *        {@link #isStateful() stateful}
		 * @param secondary Supplies the previous secondary value for the element--null for addition and may be null if this reverse is not
		 *        {@link #isStateful() stateful}
		 * @param newValue The new result value
		 * @return The result with either {@link FlatMapOptions.FlatMapReverseQueryResult#getSource() source} and
		 *         {@link FlatMapOptions.FlatMapReverseQueryResult#getSecondary() secondary} values, or an
		 *         {@link FlatMapOptions.FlatMapReverseQueryResult#getError() error}
		 */
		FlatMapReverseQueryResult<S, V> reverse(Supplier<? extends S> sourceValue, Supplier<? extends V> secondary, X newValue);
	}

	/**
	 * Simple function-based {@link FlatMapOptions.FlatMapReverse} implementation
	 *
	 * @param <S> The type of the source flow
	 * @param <V> The type of the mapped/secondary flow(s)
	 * @param <X> The type of the target flow
	 */
	public static class SimpleFlatMapReverse<S, V, X> implements FlatMapReverse<S, V, X> {
		private final TriFunction<? super S, ? super V, ? super X, ? extends S> theSourceUpdate;
		private final TriFunction<? super S, ? super V, ? super X, ? extends V> theValueUpdate;
		private final TriFunction<? super S, ? super V, ? super X, String> theUpdateEnabled;
		private final boolean isStateful;

		/**
		 * @param sourceUpdate The function to produce replacement source values
		 * @param valueUpdate The function to produce replacement mapped/secondary values
		 * @param updateEnabled The function to disable some operations
		 * @param stateful Whether this reverse depends on previous mapped/secondary values
		 */
		public SimpleFlatMapReverse(TriFunction<? super S, ? super V, ? super X, ? extends S> sourceUpdate,
			TriFunction<? super S, ? super V, ? super X, ? extends V> valueUpdate,
			TriFunction<? super S, ? super V, ? super X, String> updateEnabled, boolean stateful) {
			theSourceUpdate = sourceUpdate;
			theValueUpdate = valueUpdate;
			theUpdateEnabled = updateEnabled;
			isStateful = stateful;
		}

		@Override
		public boolean isStateful() {
			return isStateful;
		}

		@Override
		public FlatMapReverseQueryResult<S, V> canReverse(Supplier<? extends S> sourceValue, Supplier<? extends V> secondary, X newValue) {
			return reverse(sourceValue, secondary, newValue);
		}

		@Override
		public FlatMapReverseQueryResult<S, V> reverse(Supplier<? extends S> sourceValue, Supplier<? extends V> secondary, X newValue) {
			S source = sourceValue == null ? null : sourceValue.get();
			V value = secondary == null ? null : secondary.get();
			if (theUpdateEnabled != null) {
				String msg = theUpdateEnabled.apply(source, value, newValue);
				if (msg != null)
					return FlatMapReverseQueryResult.reject(msg);
			}

			boolean withSource = theSourceUpdate != null;
			boolean withValue = theValueUpdate != null;
			S sourceUpdate = null;
			V valueUpdate = null;
			if (withSource)
				sourceUpdate = theSourceUpdate.apply(source, value, newValue);
			if (withValue)
				valueUpdate = theValueUpdate.apply(source, value, newValue);
			if (withSource && withValue)
				return FlatMapReverseQueryResult.values(sourceUpdate, valueUpdate);
			else if (withValue)
				return FlatMapReverseQueryResult.value(valueUpdate);
			else if (withSource)
				return FlatMapReverseQueryResult.sourceValue(sourceUpdate);
			else
				return FlatMapReverseQueryResult.noOp();
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSourceUpdate, theValueUpdate, theUpdateEnabled, isStateful);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof SimpleFlatMapReverse))
				return false;
			SimpleFlatMapReverse<?, ?, ?> other = (SimpleFlatMapReverse<?, ?, ?>) obj;
			return Objects.equals(theSourceUpdate, other.theSourceUpdate)//
				&& Objects.equals(theValueUpdate, other.theValueUpdate)//
				&& Objects.equals(theUpdateEnabled, other.theUpdateEnabled)//
				&& isStateful == other.isStateful;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			if (theSourceUpdate != null)
				str.append("replaceSource:").append(theSourceUpdate);
			if (theValueUpdate != null) {
				if (str.length() > 0)
					str.append(", ");
				str.append("replaceValue:").append(theValueUpdate);
			}
			return str.toString();
		}
	}

	/**
	 * A {@link FlatMapOptions.FlatMapReverse} implementation that sets a field on the object in either the source or mapped/secondary flow
	 * to a value in the result flow, or some analogous operation
	 *
	 * @param <S> The type of the source flow
	 * @param <V> The type of the mapped/secondary flow(s)
	 * @param <X> The type of the target flow
	 */
	public static class FieldSettingFlatMapReverse<S, V, X> implements FlatMapReverse<S, V, X> {
		private final BiConsumer<? super S, ? super X> theSourceFieldSetter;
		private final BiConsumer<? super V, ? super X> theValueFieldSetter;
		private final TriFunction<? super S, ? super V, ? super X, String> theEnablement;
		private final TriFunction<? super S, ? super X, Boolean, ? extends V> theCreator;
		private final BiFunction<? super S, ? super X, String> theCreationEnablement;

		/**
		 * @param sourceFieldSetter Function to update the source value with the new target value
		 * @param valueFieldSetter Function to update the mapped/secondary value with the new target value
		 * @param enabled Function to disable some operations
		 * @param creator The function to create source values from target values (third function argument is false for capability queries
		 *        (like {@link BetterCollection#canAdd(Object)}), true for intentional operations (like
		 *        {@link BetterCollection#addElement(Object, boolean)})
		 * @param createEnabled The function to disable some create operations
		 */
		public FieldSettingFlatMapReverse(BiConsumer<? super S, ? super X> sourceFieldSetter,
			BiConsumer<? super V, ? super X> valueFieldSetter, TriFunction<? super S, ? super V, ? super X, String> enabled,
			TriFunction<? super S, ? super X, Boolean, ? extends V> creator, BiFunction<? super S, ? super X, String> createEnabled) {
			theSourceFieldSetter = sourceFieldSetter;
			theValueFieldSetter = valueFieldSetter;
			theEnablement = enabled;
			theCreator = creator;
			theCreationEnablement = createEnabled;
		}

		@Override
		public boolean isStateful() {
			return true;
		}

		@Override
		public FlatMapReverseQueryResult<S, V> canReverse(Supplier<? extends S> sourceValue, Supplier<? extends V> secondary, X newValue) {
			return reverse(sourceValue, secondary, newValue, true);
		}

		@Override
		public FlatMapReverseQueryResult<S, V> reverse(Supplier<? extends S> sourceValue, Supplier<? extends V> secondary, X newValue) {
			return reverse(sourceValue, secondary, newValue, false);
		}

		FlatMapReverseQueryResult<S, V> reverse(Supplier<? extends S> sourceValue, Supplier<? extends V> secondary, X newValue,
			boolean query) {
			S source = sourceValue.get();
			if (secondary == null) { // Addition
				if (theCreator == null)
					return FlatMapReverseQueryResult.reject(StdMsg.UNSUPPORTED_OPERATION);
				if (theCreationEnablement != null) {
					String msg = theCreationEnablement.apply(source, newValue);
					if (msg != null)
						return FlatMapReverseQueryResult.reject(msg);
				}
				return FlatMapReverseQueryResult.value(theCreator.apply(source, newValue, !query));
			}

			V value = secondary.get();
			if (theEnablement != null) {
				String msg = theEnablement.apply(source, value, newValue);
				if (msg != null)
					return FlatMapReverseQueryResult.reject(msg);
			}

			boolean withSource = theSourceFieldSetter != null;
			boolean withValue = theValueFieldSetter != null;
			if (withSource)
				theSourceFieldSetter.accept(source, newValue);
			if (withValue)
				theValueFieldSetter.accept(value, newValue);
			if (withSource && withValue)
				return FlatMapReverseQueryResult.values(source, value);
			else if (withValue)
				return FlatMapReverseQueryResult.value(value);
			else if (withSource)
				return FlatMapReverseQueryResult.sourceValue(source);
			else
				return FlatMapReverseQueryResult.noOp();
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSourceFieldSetter, theValueFieldSetter, theEnablement);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof FieldSettingFlatMapReverse))
				return false;
			FieldSettingFlatMapReverse<?, ?, ?> other = (FieldSettingFlatMapReverse<?, ?, ?>) obj;
			return Objects.equals(theSourceFieldSetter, other.theSourceFieldSetter)//
				&& Objects.equals(theValueFieldSetter, other.theValueFieldSetter)//
				&& Objects.equals(theEnablement, other.theEnablement);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			if (theSourceFieldSetter != null)
				str.append("updateSource:").append(theSourceFieldSetter);
			if (theValueFieldSetter != null) {
				if (str.length() > 0)
					str.append(", ");
				str.append("updateValue:").append(theValueFieldSetter);
			}
			return str.toString();
		}
	}

	/**
	 * Default {@link FlatMapOptions} implementation
	 *
	 * @param <S> The type of the source/primary stream
	 * @param <V> The type of the mapped/secondary stream(s)
	 * @param <X> The type of the target stream
	 */
	public class SimpleFlatMapOptions<S, V, X> extends XformOptions.SimpleXformOptions implements FlatMapOptions<S, V, X> {
		private FlatMapReverse<S, V, X> theReverse;

		@Override
		public FlatMapOptions<S, V, X> cache(boolean cache) {
			super.cache(cache);
			return this;
		}

		@Override
		public FlatMapOptions<S, V, X> reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return this;
		}

		@Override
		public FlatMapOptions<S, V, X> fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return this;
		}

		@Override
		public FlatMapOptions<S, V, X> propagateUpdateToParent(boolean propagate) {
			super.propagateUpdateToParent(propagate);
			return this;
		}

		@Override
		public FlatMapOptions<S, V, X> manyToOne(boolean manyToOne) {
			super.manyToOne(manyToOne);
			return this;
		}

		@Override
		public FlatMapOptions<S, V, X> oneToMany(boolean oneToMany) {
			super.oneToMany(oneToMany);
			return this;
		}

		@Override
		public FlatMapOptions<S, V, X> withReverse(FlatMapReverse<S, V, X> reverse) {
			theReverse = reverse;
			return this;
		}

		@Override
		public FlatMapDef<S, V, X> map(TriFunction<? super S, ? super V, ? super X, ? extends X> map) {
			return new FlatMapDef<>(this, map);
		}
	}
}
