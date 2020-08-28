package org.observe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.qommons.Identifiable;
import org.qommons.StringUtils;
import org.qommons.TriFunction;
import org.qommons.collect.BetterHashSet;

import com.google.common.reflect.TypeToken;

/**
 * Classes and interfaces used by {@link org.observe.collect.ObservableCollection.CollectionDataFlow#combine(TypeToken, Function)} and
 * {@link ObservableValue#combine(TypeToken, Function)}
 */
public class Combination {
	/**
	 * The starting point of a combination operation
	 *
	 * @param <E> The source type
	 * @param <T> The target type
	 * @see ObservableValue#combine(TypeToken, Function)
	 */
	public static class CombinationPrecursor<E, T> extends XformOptions.SimpleXformOptions {
		@Override
		public CombinationPrecursor<E, T> reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return this;
		}

		@Override
		public CombinationPrecursor<E, T> fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return this;
		}

		@Override
		public CombinationPrecursor<E, T> cache(boolean cache) {
			super.cache(cache);
			return this;
		}

		@Override
		public CombinationPrecursor<E, T> manyToOne(boolean manyToOne) {
			super.manyToOne(manyToOne);
			return this;
		}

		@Override
		public CombinationPrecursor<E, T> oneToMany(boolean oneToMany) {
			super.oneToMany(oneToMany);
			return this;
		}

		/**
		 * @param <V> The type of the argument value
		 * @param value The argument value to combine with the source elements
		 * @return A binary (source + argument) combined collection builder
		 */
		public <V> CombinedValueBuilder2<E, V, T> with(ObservableValue<V> value) {
			return new CombinedValueBuilder2Impl<>(this, value);
		}
	}

	/**
	 * The starting point of a reversible combination operation
	 *
	 * @param <E> The source type
	 * @param <T> The target type
	 * @see SettableValue#combineReversible(TypeToken, Function)
	 * @see org.observe.collect.ObservableCollection.CollectionDataFlow#combine(TypeToken, Function)
	 */
	public static class ReversibleCombinationPrecursor<E, T> extends CombinationPrecursor<E, T> {
		@Override
		public ReversibleCombinationPrecursor<E, T> reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return this;
		}

		@Override
		public ReversibleCombinationPrecursor<E, T> fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return this;
		}

		@Override
		public ReversibleCombinationPrecursor<E, T> cache(boolean cache) {
			super.cache(cache);
			return this;
		}

		@Override
		public ReversibleCombinationPrecursor<E, T> manyToOne(boolean manyToOne) {
			super.manyToOne(manyToOne);
			return this;
		}

		@Override
		public ReversibleCombinationPrecursor<E, T> oneToMany(boolean oneToMany) {
			super.oneToMany(oneToMany);
			return this;
		}

		@Override
		public <V> ReversibleCombinedValueBuilder2<E, V, T> with(ObservableValue<V> value) {
			return new ReversibleCombinedValueBuilder2Impl<>(this, value);
		}
	}

	/**
	 * The complete, immutable definition of a combination operation
	 *
	 * @param <E> The source type
	 * @param <T> The target type
	 */
	public static class CombinationDef<E, T> extends XformOptions.XformDef implements Identifiable {
		private final Map<ObservableValue<?>, Integer> theArgs;
		private final BiFunction<? super CombinedValues<? extends E>, ? super T, ? extends T> theCombination;

		/**
		 * @param builder The builder containing the option data
		 * @param args The observable values to combine with each source element
		 * @param combination The combination function to combine the source and argument values
		 */
		public CombinationDef(CombinedValueBuilder<E, T> builder, Map<ObservableValue<?>, Integer> args,
			BiFunction<? super CombinedValues<? extends E>, ? super T, ? extends T> combination) {
			super(builder);
			theArgs = args;
			theCombination = combination;
		}

		@Override
		public Object getIdentity() {
			StringBuilder descrip = new StringBuilder("combine(");
			List<Object> ids = new ArrayList<>(theArgs.size() + 1);
			ids.add(theCombination);
			descrip.append(theCombination).append(", ");
			ids.addAll(theArgs.keySet());
			StringUtils.conversational(", ", null).print(descrip, theArgs.keySet(), (str, v) -> str.append(v.getIdentity()));
			descrip.append(')');
			return Identifiable.baseId(descrip.toString(), ids);
		}

		/** @return The observable values to combine with each source element */
		public Set<ObservableValue<?>> getArgs() {
			return theArgs.keySet();
		}

		/**
		 * @param arg The argument observable value
		 * @return The index of the given value in this combination definition
		 * @throws IllegalArgumentException If the given argument was not added to this combination with
		 *         {@link CombinedValueBuilder#with(ObservableValue)}
		 */
		public int getArgIndex(ObservableValue<?> arg) throws IllegalArgumentException {
			Integer index = theArgs.get(arg);
			if (index == null)
				throw new IllegalArgumentException("Unrecognized argument: " + arg);
			return index.intValue();
		}

		/** @return The combination function to combine the source and argument values */
		public BiFunction<? super CombinedValues<? extends E>, ? super T, ? extends T> getCombination() {
			return theCombination;
		}

		@Override
		public int hashCode() {
			return super.hashCode() * 7 + Objects.hash(theArgs, theCombination);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof CombinationDef) || !super.equals(obj))
				return false;
			CombinationDef<?, ?> other = (CombinationDef<?, ?>) obj;
			return Objects.equals(theArgs, other.theArgs)//
				&& Objects.equals(theCombination, other.theCombination);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append("combination(");
			StringUtils.print(str, ", ", theArgs.keySet(), (s, arg) -> s.append(arg.getIdentity()));
			str.append(") with ").append(theCombination).append(", ").append(super.toString());
			return str.toString();
		}
	}

	/**
	 * The complete, immutable definition of a reversible combination operation
	 *
	 * @param <E> The source type
	 * @param <T> The target type
	 * @see SettableValue#combineReversible(TypeToken, Function)
	 * @see org.observe.collect.ObservableCollection.CollectionDataFlow#combine(TypeToken, Function)
	 */
	public static class ReversibleCombinationDef<E, T> extends CombinationDef<E, T> {
		private final Function<? super CombinedValues<T>, ? extends E> theReverse;

		/**
		 * @param builder The builder containing the option data
		 * @param args The observable values to combine with each source element
		 * @param combination The combination function to combine the source and argument values
		 * @param reverse The reverse function to map from result values to source values, for adding values to the result, etc.
		 */
		public ReversibleCombinationDef(ReversibleCombinedValueBuilder<E, T> builder, Map<ObservableValue<?>, Integer> args,
			BiFunction<? super CombinedValues<? extends E>, ? super T, ? extends T> combination,
			Function<? super CombinedValues<T>, ? extends E> reverse) {
			super(builder, args, combination);
			theReverse = reverse;
		}

		/** @return The reverse function to map from result values to source values, for adding values to the result, etc. */
		public Function<? super CombinedValues<T>, ? extends E> getReverse() {
			return theReverse;
		}

		@Override
		public int hashCode() {
			return super.hashCode() * 7 + Objects.hash(getArgs(), getCombination(), theReverse);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof ReversibleCombinationDef) || !super.equals(obj))
				return false;
			ReversibleCombinationDef<?, ?> other = (ReversibleCombinationDef<?, ?>) obj;
			return Objects.equals(getArgs(), other.getArgs())//
				&& Objects.equals(getCombination(), other.getCombination())//
				&& Objects.equals(theReverse, other.theReverse);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append("combination(");
			StringUtils.print(str, ", ", getArgs(), (s, arg) -> s.append(arg.getIdentity()));
			str.append(") with ").append(getCombination()).append(", ").append(super.toString());
			return str.toString();
		}
	}

	/**
	 * A structure that is operated on to produce the elements of a combined value or collection
	 *
	 * @param <E> The type of the source element (or the value to be reversed)
	 * @see ObservableValue#combine(TypeToken, Function)
	 */
	public interface CombinedValues<E> {
		/** @return The value of the element from the collection */
		E getElement();

		/**
		 * @param <T> The type of the combined value
		 * @param arg The observable value combined into this combination
		 * @return The value of the given observable in this combined element
		 */
		<T> T get(ObservableValue<T> arg);
	}

	/**
	 * A structure that may be used to define a value whose elements are those of a single source combined with one or more values
	 *
	 * @param <E> The type of elements in the source
	 * @param <T> The type of elements in the resulting value
	 * @see ObservableValue#combine(TypeToken, Function)
	 */
	public interface CombinedValueBuilder<E, T> extends XformOptions {
		/**
		 * Adds another observable value to the combination mix
		 *
		 * @param arg The observable value to combine to obtain the result
		 * @return This builder
		 */
		<X> CombinedValueBuilder<E, T> with(ObservableValue<X> arg);

		@Override
		CombinedValueBuilder<E, T> nullToNull(boolean nullToNull);

		@Override
		CombinedValueBuilder<E, T> propagateUpdateToParent(boolean propagate);

		@Override
		CombinedValueBuilder<E, T> reEvalOnUpdate(boolean reEval);

		/**
		 * @param cache Whether this operation caches its result to avoid re-evaluation on access. True by default.
		 * @return This builder
		 */
		@Override
		CombinedValueBuilder<E, T> cache(boolean cache);

		@Override
		CombinedValueBuilder<E, T> fireIfUnchanged(boolean fire);

		@Override
		CombinedValueBuilder<E, T> manyToOne(boolean manyToOne);

		@Override
		CombinedValueBuilder<E, T> oneToMany(boolean oneToMany);

		/**
		 * @param combination A function producing a combination value from a structure containing the source value and all combined values
		 * @return The combined collection definition
		 */
		default CombinationDef<E, T> build(Function<? super CombinedValues<? extends E>, ? extends T> combination) {
			return build((cv, o) -> combination.apply(cv));
		}

		/**
		 * @param combination A function producing a combination value from a structure containing the source value and all combined values,
		 *        as well as the previous combination value
		 * @return The combined collection definition
		 */
		CombinationDef<E, T> build(BiFunction<? super CombinedValues<? extends E>, ? super T, ? extends T> combination);
	}

	/**
	 * A structure that may be used to define a value or collection whose elements are those of a single source combined with one or more
	 * values. This class allows specification of a reverse method that can allow values to flow from combined value to source as well.
	 *
	 * @param <E> The type of elements in the source
	 * @param <T> The type of elements in the resulting value or collection
	 * @see SettableValue#combineReversible(TypeToken, Function)
	 * @see org.observe.collect.ObservableCollection.CollectionDataFlow#combine(TypeToken, Function)
	 */
	public interface ReversibleCombinedValueBuilder<E, T> extends CombinedValueBuilder<E, T> {
		/**
		 * Allows specification of a reverse function that may enable adding values to the result of this operation
		 *
		 * @param reverse A function capable of taking a result of this operation and reversing it to a source-compatible value
		 * @return This builder
		 */
		ReversibleCombinedValueBuilder<E, T> withReverse(Function<? super CombinedValues<? extends T>, ? extends E> reverse);

		@Override
		<X> ReversibleCombinedValueBuilder<E, T> with(ObservableValue<X> arg);

		@Override
		ReversibleCombinedValueBuilder<E, T> nullToNull(boolean nullToNull);

		@Override
		ReversibleCombinedValueBuilder<E, T> propagateUpdateToParent(boolean propagate);

		@Override
		ReversibleCombinedValueBuilder<E, T> reEvalOnUpdate(boolean reEval);

		@Override
		ReversibleCombinedValueBuilder<E, T> cache(boolean cache);

		@Override
		ReversibleCombinedValueBuilder<E, T> fireIfUnchanged(boolean fire);

		@Override
		ReversibleCombinedValueBuilder<E, T> manyToOne(boolean manyToOne);

		@Override
		ReversibleCombinedValueBuilder<E, T> oneToMany(boolean oneToMany);

		@Override
		default ReversibleCombinationDef<E, T> build(Function<? super CombinedValues<? extends E>, ? extends T> combination) {
			return build((cv, o) -> combination.apply(cv));
		}

		@Override
		ReversibleCombinationDef<E, T> build(BiFunction<? super CombinedValues<? extends E>, ? super T, ? extends T> combination);
	}

	/**
	 * A {@link CombinedValueBuilder} for the combination of a value with a single value. Use {@link #with(ObservableValue)} to combine with
	 * additional values.
	 *
	 * @param <E> The type of elements in the source
	 * @param <V> The type of the combined value
	 * @param <T> The type of elements in the resulting value
	 * @see ObservableValue#combine(TypeToken, Function)
	 * @see org.observe.collect.ObservableCollection.CollectionDataFlow#combine(TypeToken, Function)
	 */
	public interface CombinedValueBuilder2<E, V, T> extends CombinedValueBuilder<E, T> {
		@Override
		<U> CombinedValueBuilder3<E, V, U, T> with(ObservableValue<U> arg3);

		@Override
		CombinedValueBuilder2<E, V, T> nullToNull(boolean nullToNull);

		@Override
		CombinedValueBuilder2<E, V, T> propagateUpdateToParent(boolean propagate);

		@Override
		CombinedValueBuilder2<E, V, T> reEvalOnUpdate(boolean reEval);

		@Override
		CombinedValueBuilder2<E, V, T> cache(boolean cache);

		@Override
		CombinedValueBuilder2<E, V, T> fireIfUnchanged(boolean fire);

		@Override
		CombinedValueBuilder2<E, V, T> manyToOne(boolean manyToOne);

		@Override
		CombinedValueBuilder2<E, V, T> oneToMany(boolean oneToMany);
	}

	/**
	 * A {@link ReversibleCombinedValueBuilder} for the combination of a value or collection with a single value. Use
	 * {@link #with(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source
	 * @param <V> The type of the combined value
	 * @param <T> The type of elements in the resulting value or collection
	 * @see SettableValue#combineReversible(TypeToken, Function)
	 * @see org.observe.collect.ObservableCollection.CollectionDataFlow#combine(TypeToken, Function)
	 */
	public interface ReversibleCombinedValueBuilder2<E, V, T> extends CombinedValueBuilder2<E, V, T>, ReversibleCombinedValueBuilder<E, T> {
		@Override
		<U> ReversibleCombinedValueBuilder3<E, V, U, T> with(ObservableValue<U> arg3);

		@Override
		ReversibleCombinedValueBuilder2<E, V, T> nullToNull(boolean nullToNull);

		@Override
		ReversibleCombinedValueBuilder2<E, V, T> propagateUpdateToParent(boolean propagate);

		@Override
		ReversibleCombinedValueBuilder2<E, V, T> reEvalOnUpdate(boolean reEval);

		@Override
		ReversibleCombinedValueBuilder2<E, V, T> cache(boolean cache);

		@Override
		ReversibleCombinedValueBuilder2<E, V, T> fireIfUnchanged(boolean fire);

		@Override
		ReversibleCombinedValueBuilder2<E, V, T> manyToOne(boolean manyToOne);

		@Override
		ReversibleCombinedValueBuilder2<E, V, T> oneToMany(boolean oneToMany);

		@Override
		ReversibleCombinedValueBuilder2<E, V, T> withReverse(Function<? super CombinedValues<? extends T>, ? extends E> reverse);

		/**
		 * Allows specification of a reverse function that may enable adding values to the result of this operation
		 *
		 * @param reverse A function capable of taking a result of this operation and reversing it to a source-compatible value
		 * @return This builder
		 */
		ReversibleCombinedValueBuilder2<E, V, T> withReverse(BiFunction<? super T, ? super V, ? extends E> reverse);
	}

	/**
	 * A {@link CombinedValueBuilder} for the combination of a value with 2 other values. Use {@link #with(ObservableValue)} to combine with
	 * additional values.
	 *
	 * @param <E> The type of elements in the source
	 * @param <V1> The type of the first combined value
	 * @param <V2> The type of the second combined value
	 * @param <T> The type of elements in the resulting value
	 * @see ObservableValue#combine(TypeToken, Function)
	 * @see org.observe.collect.ObservableCollection.CollectionDataFlow#combine(TypeToken, Function)
	 * @see ReversibleCombinedValueBuilder2Impl#with(ObservableValue)
	 */
	public interface CombinedValueBuilder3<E, V1, V2, T> extends CombinedValueBuilder<E, T> {
		@Override
		CombinedValueBuilder3<E, V1, V2, T> nullToNull(boolean nullToNull);

		@Override
		CombinedValueBuilder3<E, V1, V2, T> propagateUpdateToParent(boolean propagate);

		@Override
		CombinedValueBuilder3<E, V1, V2, T> reEvalOnUpdate(boolean reEval);

		@Override
		CombinedValueBuilder3<E, V1, V2, T> cache(boolean cache);

		@Override
		CombinedValueBuilder3<E, V1, V2, T> fireIfUnchanged(boolean fire);

		@Override
		CombinedValueBuilder3<E, V1, V2, T> manyToOne(boolean manyToOne);

		@Override
		CombinedValueBuilder3<E, V1, V2, T> oneToMany(boolean oneToMany);
	}

	/**
	 * A {@link ReversibleCombinedValueBuilder} for the combination of a value or collection with 2 other values. Use
	 * {@link #with(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source
	 * @param <V1> The type of the first combined value
	 * @param <V2> The type of the second combined value
	 * @param <T> The type of elements in the resulting value or collection
	 * @see SettableValue#combineReversible(TypeToken, Function)
	 * @see org.observe.collect.ObservableCollection.CollectionDataFlow#combine(TypeToken, Function)
	 * @see ReversibleCombinedValueBuilder2Impl#with(ObservableValue)
	 */
	public interface ReversibleCombinedValueBuilder3<E, V1, V2, T>
	extends CombinedValueBuilder3<E, V1, V2, T>, ReversibleCombinedValueBuilder<E, T> {
		@Override
		ReversibleCombinedValueBuilder3<E, V1, V2, T> nullToNull(boolean nullToNull);

		@Override
		ReversibleCombinedValueBuilder3<E, V1, V2, T> propagateUpdateToParent(boolean propagate);

		@Override
		ReversibleCombinedValueBuilder3<E, V1, V2, T> reEvalOnUpdate(boolean reEval);

		@Override
		ReversibleCombinedValueBuilder3<E, V1, V2, T> cache(boolean cache);

		@Override
		ReversibleCombinedValueBuilder3<E, V1, V2, T> fireIfUnchanged(boolean fire);

		@Override
		ReversibleCombinedValueBuilder3<E, V1, V2, T> manyToOne(boolean manyToOne);

		@Override
		ReversibleCombinedValueBuilder3<E, V1, V2, T> oneToMany(boolean oneToMany);

		@Override
		ReversibleCombinedValueBuilder3<E, V1, V2, T> withReverse(Function<? super CombinedValues<? extends T>, ? extends E> reverse);

		/**
		 * @param reverse The reverse function to enable adding values to the result
		 * @return This builder
		 */
		ReversibleCombinedValueBuilder3Impl<E, V1, V2, T> withReverse(TriFunction<? super T, ? super V1, ? super V2, ? extends E> reverse);
	}

	private static class CombinedValueBuilder2Impl<E, V, T> extends Combination.AbstractCombinedValueBuilder<E, T>
	implements CombinedValueBuilder2<E, V, T> {
		private final ObservableValue<V> theArg2;

		/**
		 * @param precursor The combination precursor at the beginning of the combination
		 * @param arg2 The argument to combine with the flow values
		 */
		protected CombinedValueBuilder2Impl(CombinationPrecursor<E, T> precursor, ObservableValue<V> arg2) {
			super(precursor);
			theArg2 = arg2;
			addArg(arg2);
		}

		@Override
		public CombinedValueBuilder2Impl<E, V, T> nullToNull(boolean nullToNull) {
			super.nullToNull(nullToNull);
			return this;
		}

		@Override
		public CombinedValueBuilder2Impl<E, V, T> propagateUpdateToParent(boolean propagate) {
			super.propagateUpdateToParent(propagate);
			return this;
		}

		@Override
		public CombinedValueBuilder2Impl<E, V, T> cache(boolean cache) {
			super.cache(cache);
			return this;
		}

		@Override
		public CombinedValueBuilder2Impl<E, V, T> reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return this;
		}

		@Override
		public CombinedValueBuilder2Impl<E, V, T> fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return this;
		}

		@Override
		public CombinedValueBuilder2Impl<E, V, T> manyToOne(boolean manyToOne) {
			super.manyToOne(manyToOne);
			return this;
		}

		@Override
		public CombinedValueBuilder2Impl<E, V, T> oneToMany(boolean oneToMany) {
			super.oneToMany(oneToMany);
			return this;
		}

		@Override
		public <U> CombinedValueBuilder3Impl<E, V, U, T> with(ObservableValue<U> arg3) {
			return new CombinedValueBuilder3Impl<>(this, theArg2, arg3);
		}
	}

	private static class ReversibleCombinedValueBuilder2Impl<E, V, T> extends Combination.AbstractReversibleCombinedValueBuilder<E, T>
	implements ReversibleCombinedValueBuilder2<E, V, T> {
		private final ObservableValue<V> theArg2;

		/**
		 * @param precursor The combination precursor at the beginning of the combination
		 * @param arg2 The argument to combine with the flow values
		 */
		protected ReversibleCombinedValueBuilder2Impl(ReversibleCombinationPrecursor<E, T> precursor, ObservableValue<V> arg2) {
			super(precursor);
			theArg2 = arg2;
			addArg(arg2);
		}

		@Override
		public ReversibleCombinedValueBuilder2Impl<E, V, T> nullToNull(boolean nullToNull) {
			super.nullToNull(nullToNull);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder2Impl<E, V, T> propagateUpdateToParent(boolean propagate) {
			super.propagateUpdateToParent(propagate);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder2Impl<E, V, T> cache(boolean cache) {
			return (ReversibleCombinedValueBuilder2Impl<E, V, T>) super.cache(cache);
		}

		@Override
		public ReversibleCombinedValueBuilder2Impl<E, V, T> reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder2Impl<E, V, T> fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder2Impl<E, V, T> manyToOne(boolean manyToOne) {
			super.manyToOne(manyToOne);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder2Impl<E, V, T> oneToMany(boolean oneToMany) {
			super.oneToMany(oneToMany);
			return this;
		}

		/**
		 * @param reverse The reverse function to enable adding values to the result
		 * @return This builder
		 */
		@Override
		public ReversibleCombinedValueBuilder2Impl<E, V, T> withReverse(BiFunction<? super T, ? super V, ? extends E> reverse) {
			return withReverse(cv -> reverse.apply(cv.getElement(), cv.get(theArg2)));
		}

		@Override
		public ReversibleCombinedValueBuilder2Impl<E, V, T> withReverse(
			Function<? super CombinedValues<? extends T>, ? extends E> reverse) {
			super.withReverse(reverse);
			return this;
		}

		@Override
		public <U> ReversibleCombinedValueBuilder3Impl<E, V, U, T> with(ObservableValue<U> arg3) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new ReversibleCombinedValueBuilder3Impl<>(this, theArg2, arg3);
		}
	}

	private static class CombinedValueBuilder3Impl<E, V1, V2, T> extends Combination.AbstractCombinedValueBuilder<E, T>
	implements CombinedValueBuilder3<E, V1, V2, T> {
		private final ObservableValue<V1> theArg2;
		private final ObservableValue<V2> theArg3;

		/**
		 * @param template The 2-argument combination that this is based on
		 * @param arg2 The first argument to combine with the flow values
		 * @param arg3 The second argument to combine with the flow values
		 */
		protected CombinedValueBuilder3Impl(CombinedValueBuilder2Impl<E, V1, T> template, ObservableValue<V1> arg2,
			ObservableValue<V2> arg3) {
			super(template);
			theArg2 = arg2;
			theArg3 = arg3;
			addArg(arg2);
			addArg(arg3);
		}

		@Override
		public CombinedValueBuilder3Impl<E, V1, V2, T> nullToNull(boolean nullToNull) {
			super.nullToNull(nullToNull);
			return this;
		}

		@Override
		public CombinedValueBuilder3Impl<E, V1, V2, T> propagateUpdateToParent(boolean propagate) {
			super.propagateUpdateToParent(propagate);
			return this;
		}

		@Override
		public CombinedValueBuilder3Impl<E, V1, V2, T> cache(boolean cache) {
			super.cache(cache);
			return this;
		}

		@Override
		public CombinedValueBuilder3Impl<E, V1, V2, T> reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return this;
		}

		@Override
		public CombinedValueBuilder3Impl<E, V1, V2, T> fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return this;
		}

		@Override
		public CombinedValueBuilder3Impl<E, V1, V2, T> manyToOne(boolean manyToOne) {
			super.manyToOne(manyToOne);
			return this;
		}

		@Override
		public CombinedValueBuilder3Impl<E, V1, V2, T> oneToMany(boolean oneToMany) {
			super.oneToMany(oneToMany);
			return this;
		}

		@Override
		public <T2> CombinedValueBuilder<E, T> with(ObservableValue<T2> arg) {
			return new CombinedValueBuilderNImpl<>(this, theArg2, theArg3, arg);
		}
	}

	private static class ReversibleCombinedValueBuilder3Impl<E, V1, V2, T> extends Combination.AbstractReversibleCombinedValueBuilder<E, T>
	implements ReversibleCombinedValueBuilder3<E, V1, V2, T> {
		private final ObservableValue<V1> theArg2;
		private final ObservableValue<V2> theArg3;

		/**
		 * @param template The 2-argument combination that this is based on
		 * @param arg2 The first argument to combine with the flow values
		 * @param arg3 The second argument to combine with the flow values
		 */
		protected ReversibleCombinedValueBuilder3Impl(ReversibleCombinedValueBuilder2Impl<E, V1, T> template, ObservableValue<V1> arg2,
			ObservableValue<V2> arg3) {
			super(template);
			theArg2 = arg2;
			theArg3 = arg3;
			addArg(arg2);
			addArg(arg3);
		}

		@Override
		public ReversibleCombinedValueBuilder3Impl<E, V1, V2, T> nullToNull(boolean nullToNull) {
			super.nullToNull(nullToNull);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder3Impl<E, V1, V2, T> propagateUpdateToParent(boolean propagate) {
			super.propagateUpdateToParent(propagate);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder3Impl<E, V1, V2, T> cache(boolean cache) {
			super.cache(cache);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder3Impl<E, V1, V2, T> reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder3Impl<E, V1, V2, T> fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder3Impl<E, V1, V2, T> manyToOne(boolean manyToOne) {
			super.manyToOne(manyToOne);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder3Impl<E, V1, V2, T> oneToMany(boolean oneToMany) {
			super.oneToMany(oneToMany);
			return this;
		}

		/**
		 * @param reverse The reverse function to enable adding values to the result
		 * @return This builder
		 */
		@Override
		public ReversibleCombinedValueBuilder3Impl<E, V1, V2, T> withReverse(
			TriFunction<? super T, ? super V1, ? super V2, ? extends E> reverse) {
			return withReverse(cv -> reverse.apply(cv.getElement(), cv.get(theArg2), cv.get(theArg3)));
		}

		@Override
		public ReversibleCombinedValueBuilder3Impl<E, V1, V2, T> withReverse(
			Function<? super CombinedValues<? extends T>, ? extends E> reverse) {
			super.withReverse(reverse);
			return this;
		}

		@Override
		public <T2> ReversibleCombinedValueBuilder<E, T> with(ObservableValue<T2> arg) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new ReversibleCombinedValueBuilderNImpl<>(this, theArg2, theArg3, arg);
		}
	}

	private static class CombinedValueBuilderNImpl<E, T> extends Combination.AbstractCombinedValueBuilder<E, T> {
		/**
		 * @param template The 3-argument combination that this is based on
		 * @param arg2 The first argument value
		 * @param arg3 The second argument value
		 * @param arg4 The third argument value
		 */
		protected CombinedValueBuilderNImpl(CombinedValueBuilder3Impl<E, ?, ?, T> template, ObservableValue<?> arg2,
			ObservableValue<?> arg3, ObservableValue<?> arg4) {
			super(template);
			addArg(arg2);
			addArg(arg3);
			addArg(arg4);
		}

		@Override
		public CombinedValueBuilderNImpl<E, T> nullToNull(boolean nullToNull) {
			super.nullToNull(nullToNull);
			return this;
		}

		@Override
		public CombinedValueBuilderNImpl<E, T> propagateUpdateToParent(boolean propagate) {
			super.propagateUpdateToParent(propagate);
			return this;
		}

		@Override
		public CombinedValueBuilderNImpl<E, T> cache(boolean cache) {
			super.cache(cache);
			return this;
		}

		@Override
		public CombinedValueBuilderNImpl<E, T> reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return this;
		}

		@Override
		public CombinedValueBuilderNImpl<E, T> fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return this;
		}

		@Override
		public CombinedValueBuilderNImpl<E, T> manyToOne(boolean manyToOne) {
			super.manyToOne(manyToOne);
			return this;
		}

		@Override
		public CombinedValueBuilderNImpl<E, T> oneToMany(boolean oneToMany) {
			super.oneToMany(oneToMany);
			return this;
		}

		@Override
		public <X> CombinedValueBuilderNImpl<E, T> with(ObservableValue<X> arg) {
			addArg(arg);
			return this;
		}
	}

	private static class ReversibleCombinedValueBuilderNImpl<E, T> extends Combination.AbstractReversibleCombinedValueBuilder<E, T> {
		/**
		 * @param template The 3-argument combination that this is based on
		 * @param arg2 The first argument value
		 * @param arg3 The second argument value
		 * @param arg4 The third argument value
		 */
		protected ReversibleCombinedValueBuilderNImpl(ReversibleCombinedValueBuilder3Impl<E, ?, ?, T> template, ObservableValue<?> arg2,
			ObservableValue<?> arg3, ObservableValue<?> arg4) {
			super(template);
			addArg(arg2);
			addArg(arg3);
			addArg(arg4);
		}

		@Override
		public ReversibleCombinedValueBuilderNImpl<E, T> nullToNull(boolean nullToNull) {
			super.nullToNull(nullToNull);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilderNImpl<E, T> propagateUpdateToParent(boolean propagate) {
			super.propagateUpdateToParent(propagate);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder<E, T> cache(boolean cache) {
			super.cache(cache);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder<E, T> reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder<E, T> fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilderNImpl<E, T> manyToOne(boolean manyToOne) {
			super.manyToOne(manyToOne);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilderNImpl<E, T> oneToMany(boolean oneToMany) {
			super.oneToMany(oneToMany);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder<E, T> withReverse(Function<? super CombinedValues<? extends T>, ? extends E> reverse) {
			super.withReverse(reverse);
			return this;
		}

		@Override
		public <X> ReversibleCombinedValueBuilder<E, T> with(ObservableValue<X> arg) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			addArg(arg);
			return this;
		}
	}

	private static abstract class AbstractCombinedValueBuilder<E, R> extends XformOptions.SimpleXformOptions
	implements CombinedValueBuilder<E, R> {
		private final BetterHashSet<ObservableValue<?>> theArgs;

		protected AbstractCombinedValueBuilder(XformOptions template) {
			super(template);
			theArgs = BetterHashSet.build().identity().unsafe().buildSet();
		}

		protected void addArg(ObservableValue<?> arg) {
			if (theArgs.contains(arg))
				throw new IllegalArgumentException("Argument " + arg + " is already combined");
			theArgs.add(arg);
		}

		@Override
		public CombinedValueBuilder<E, R> nullToNull(boolean nullToNull) {
			super.nullToNull(nullToNull);
			return this;
		}

		@Override
		public CombinedValueBuilder<E, R> propagateUpdateToParent(boolean propagate) {
			super.propagateUpdateToParent(propagate);
			return this;
		}

		@Override
		public CombinedValueBuilder<E, R> reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return this;
		}

		@Override
		public CombinedValueBuilder<E, R> fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return this;
		}

		@Override
		public CombinedValueBuilder<E, R> cache(boolean cache) {
			super.cache(cache);
			return this;
		}

		@Override
		public CombinedValueBuilder<E, R> manyToOne(boolean manyToOne) {
			super.manyToOne(manyToOne);
			return this;
		}

		@Override
		public CombinedValueBuilder<E, R> oneToMany(boolean oneToMany) {
			super.oneToMany(oneToMany);
			return this;
		}

		@Override
		public CombinationDef<E, R> build(Function<? super CombinedValues<? extends E>, ? extends R> combination) {
			return build((cv, o) -> combination.apply(cv));
		}

		Map<ObservableValue<?>, Integer> buildArgs() {
			Map<ObservableValue<?>, Integer> args = new HashMap<>(
				theArgs.size() <= 1 ? theArgs.size() : (int) Math.ceil(theArgs.size() * 3.0 / 2));
			int i = 0;
			for (ObservableValue<?> arg : theArgs)
				args.put(arg, i++);
			return Collections.unmodifiableMap(args);
		}

		@Override
		public CombinationDef<E, R> build(BiFunction<? super CombinedValues<? extends E>, ? super R, ? extends R> combination) {
			Map<ObservableValue<?>, Integer> args = buildArgs();
			return new CombinationDef<>(this, args, combination);
		}
	}

	private static abstract class AbstractReversibleCombinedValueBuilder<E, R> extends AbstractCombinedValueBuilder<E, R>
	implements ReversibleCombinedValueBuilder<E, R> {
		private Function<? super CombinedValues<? extends R>, ? extends E> theReverse;

		protected AbstractReversibleCombinedValueBuilder(XformOptions template) {
			super(template);
		}

		protected Function<? super CombinedValues<? extends R>, ? extends E> getReverse() {
			return theReverse;
		}

		@Override
		public ReversibleCombinedValueBuilder<E, R> nullToNull(boolean nullToNull) {
			super.nullToNull(nullToNull);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder<E, R> propagateUpdateToParent(boolean propagate) {
			super.propagateUpdateToParent(propagate);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder<E, R> reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder<E, R> fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder<E, R> cache(boolean cache) {
			super.cache(cache);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder<E, R> manyToOne(boolean manyToOne) {
			super.manyToOne(manyToOne);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder<E, R> oneToMany(boolean oneToMany) {
			super.oneToMany(oneToMany);
			return this;
		}

		@Override
		public ReversibleCombinedValueBuilder<E, R> withReverse(Function<? super CombinedValues<? extends R>, ? extends E> reverse) {
			theReverse = reverse;
			return this;
		}

		@Override
		public ReversibleCombinationDef<E, R> build(Function<? super CombinedValues<? extends E>, ? extends R> combination) {
			return build((cv, o) -> combination.apply(cv));
		}

		@Override
		public ReversibleCombinationDef<E, R> build(BiFunction<? super CombinedValues<? extends E>, ? super R, ? extends R> combination) {
			Map<ObservableValue<?>, Integer> args = buildArgs();
			return new ReversibleCombinationDef<>(this, args, combination, theReverse);
		}
	}
}
