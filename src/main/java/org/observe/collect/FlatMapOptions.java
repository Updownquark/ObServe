package org.observe.collect;

import java.util.Objects;
import java.util.function.BiFunction;

import org.observe.XformOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.qommons.LambdaUtils;
import org.qommons.TriFunction;
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
		private final TriFunction<? super S, ? super V, ? super X, ? extends V> theReverse;
		private final boolean isReverseStateful;

		FlatMapDef(SimpleFlatMapOptions<S, V, X> options, TriFunction<? super S, ? super V, ? super X, ? extends X> map) {
			super(options);
			theMap = map;
			theReverse = options.theReverse;
			isReverseStateful = options.isReverseStateful;
			if (isReverseStateful && !isCached())
				throw new IllegalStateException("Stateful reverse is not supported without caching");
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

		/** @return Whether this option set supports {@link #reverse(Object, Object, Object) reversal} */
		public boolean isReversible() {
			return theReverse != null;
		}

		/**
		 * @return Whether this option set's {@link #reverse(Object, Object, Object) reverse} depends on the previous mapped/secondary value
		 */
		public boolean isReverseStateful() {
			return isReverseStateful;
		}

		/**
		 * @param source The value of the element from the source/primary flow
		 * @param previousValue The previous value of the mapped/secondary element--may be null if this option set is not
		 *        {@link #isCached()}
		 * @param result The new value for the target flow
		 * @return The value for the mapped/secondary element that should {@link #map(Object, Object, Object) map} to <code>result</code>
		 */
		public V reverse(S source, V previousValue, X result) {
			if (theReverse == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			return theReverse.apply(source, previousValue, result);
		}
	}

	/**
	 * <p>
	 * Enables element setting in a flat-mapped flow by defining a function able to produce mapped/secondary element values from target
	 * values.
	 * </p>
	 * <p>
	 * Using this method (as opposed to with the {@link FlatMapOptions#withReverse(TriFunction) ternary} reverse method) is better if the
	 * reverse mechanism does not require the previous mapped/secondary value. Flows created with this option may perform better for some
	 * operations.
	 * </p>
	 *
	 * @param reverse The function to produce mapped/secondary values from source/primary and target value tuples
	 * @return This option set
	 */
	FlatMapOptions<S, V, X> withReverse(BiFunction<? super S, ? super X, ? extends V> reverse);

	/**
	 * <p>
	 * Enables element setting in a flat-mapped flow by defining a function able to produce mapped/secondary element values from target
	 * values.
	 * </p>
	 * <p>
	 * If the reverse mechanism does not require the previous mapped/secondary value, the {@link #withReverse(BiFunction) binary} reverse
	 * method is preferred, since flows created with the ternary option may perform worse for some operations.
	 * </p>
	 *
	 * @param reverse The function to produce mapped/secondary values from source/primary, previous mapped/secondary, and target value
	 *        triples
	 * @return This option set
	 */
	FlatMapOptions<S, V, X> withReverse(TriFunction<? super S, ? super V, ? super X, ? extends V> reverse);

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
	 * Default {@link FlatMapOptions} implementation
	 *
	 * @param <S> The type of the source/primary stream
	 * @param <V> The type of the mapped/secondary stream(s)
	 * @param <X> The type of the target stream
	 */
	public class SimpleFlatMapOptions<S, V, X> extends XformOptions.SimpleXformOptions implements FlatMapOptions<S, V, X> {
		private TriFunction<? super S, ? super V, ? super X, ? extends V> theReverse;
		private boolean isReverseStateful;

		@Override
		public FlatMapOptions<S, V, X> withReverse(BiFunction<? super S, ? super X, ? extends V> reverse) {
			theReverse = LambdaUtils.printableTriFn((s, v, x) -> reverse.apply(s, x), //
				reverse::toString, LambdaUtils.getIdentifier(reverse));
			isReverseStateful = false;
			return this;
		}

		@Override
		public FlatMapOptions<S, V, X> withReverse(TriFunction<? super S, ? super V, ? super X, ? extends V> reverse) {
			theReverse = reverse;
			isReverseStateful = reverse != null;
			return this;
		}

		@Override
		public FlatMapDef<S, V, X> map(TriFunction<? super S, ? super V, ? super X, ? extends X> map) {
			return new FlatMapDef<>(this, map);
		}
	}
}
