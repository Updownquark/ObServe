package org.observe.collect;

import java.util.Collections;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.XformOptions;
import org.qommons.TriFunction;
import org.qommons.collect.BetterHashSet;

import com.google.common.reflect.TypeToken;

/** Classes and interfaces used by {@link ObservableCollection.CollectionDataFlow#combine(TypeToken, Function)} */
public class Combination {
	/**
	 * The starting point of a combination operation
	 *
	 * @param <E> The source type
	 * @param <T> The target type
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

		/**
		 * @param <V> The type of the argument value
		 * @param value The argument value to combine with the source elements
		 * @return A binary (source + argument) combined collection builder
		 */
		public <V> CombinedCollectionBuilder2<E, V, T> with(ObservableValue<V> value) {
			return new CombinedCollectionBuilder2<>(value);
		}
	}

	/**
	 * The complete, immutable definition of a combination operation
	 *
	 * @param <E> The source type
	 * @param <T> The target type
	 */
	public static class CombinedFlowDef<E, T> extends XformOptions.XformDef {
		private final Set<ObservableValue<?>> theArgs;
		private final BiFunction<? super CombinedValues<? extends E>, ? super T, ? extends T> theCombination;
		private final Function<? super CombinedValues<T>, ? extends E> theReverse;
		private final boolean isManyToOne;

		/**
		 * @param builder The builder containing the option data
		 * @param args The observable values to combine with each source element
		 * @param combination The combination function to combine the source and argument values
		 * @param reverse The reverse function to map from result values to source values, for adding values to the result, etc.
		 * @param manyToOne Whether the mapping may produce the same output from different source (collection) values
		 */
		public CombinedFlowDef(CombinedCollectionBuilder<E, T> builder, Set<ObservableValue<?>> args,
			BiFunction<? super CombinedValues<? extends E>, ? super T, ? extends T> combination,
			Function<? super CombinedValues<T>, ? extends E> reverse, boolean manyToOne) {
			super(builder);
			theArgs = args;
			theCombination = combination;
			theReverse = reverse;
			isManyToOne = manyToOne;
		}

		/** @return The observable values to combine with each source element */
		public Set<ObservableValue<?>> getArgs() {
			return theArgs;
		}

		/** @return The combination function to combine the source and argument values */
		public BiFunction<? super CombinedValues<? extends E>, ? super T, ? extends T> getCombination() {
			return theCombination;
		}

		/** @return The reverse function to map from result values to source values, for adding values to the result, etc. */
		public Function<? super CombinedValues<T>, ? extends E> getReverse() {
			return theReverse;
		}

		/** @return Whether the mapping may produce the same output from different source (collection) values */
		public boolean isManyToOne() {
			return isManyToOne;
		}
	}

	/**
	 * A structure that may be used to define a collection whose elements are those of a single source collection combined with one or more
	 * values
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of elements in the resulting collection
	 * @see ObservableCollection.CollectionDataFlow#combineWith(ObservableValue, TypeToken)
	 */
	interface CombinedCollectionBuilder<E, T> extends XformOptions {
		/**
		 * Adds another observable value to the combination mix
		 *
		 * @param arg The observable value to combine to obtain the result
		 * @return This builder
		 */
		<X> CombinedCollectionBuilder<E, T> with(ObservableValue<X> arg);

		/**
		 * Allows specification of a reverse function that may enable adding values to the result of this operation
		 *
		 * @param reverse A function capable of taking a result of this operation and reversing it to a source-compatible value
		 * @return This builder
		 */
		CombinedCollectionBuilder<E, T> withReverse(Function<? super CombinedValues<? extends T>, ? extends E> reverse);

		@Override
		CombinedCollectionBuilder<E, T> reEvalOnUpdate(boolean reEval);

		/**
		 * @param cache Whether this operation caches its result to avoid re-evaluation on access. True by default.
		 * @return This builder
		 */
		@Override
		CombinedCollectionBuilder<E, T> cache(boolean cache);

		@Override
		CombinedCollectionBuilder<E, T> fireIfUnchanged(boolean fire);

		default CombinedFlowDef<E, T> build(Function<? super CombinedValues<? extends E>, ? extends T> combination) {
			return build((cv, o) -> combination.apply(cv));
		}

		CombinedFlowDef<E, T> build(BiFunction<? super CombinedValues<? extends E>, ? super T, ? extends T> combination);
	}

	/**
	 * A {@link CombinedCollectionBuilder} for the combination of a collection with a single value. Use {@link #with(ObservableValue)} to
	 * combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <V> The type of the combined value
	 * @param <T> The type of elements in the resulting collection
	 * @see ObservableCollection.CollectionDataFlow#combine(TypeToken, Function)
	 */
	public static class CombinedCollectionBuilder2<E, V, T> extends Combination.AbstractCombinedCollectionBuilder<E, T> {
		private final ObservableValue<V> theArg2;

		/** @param arg2 The argument to combine with the flow values */
		protected CombinedCollectionBuilder2(ObservableValue<V> arg2) {
			super(null);
			theArg2 = arg2;
			addArg(arg2);
		}

		/** @return This builder's value argument */
		protected ObservableValue<V> getArg2() {
			return theArg2;
		}

		@Override
		public CombinedCollectionBuilder2<E, V, T> cache(boolean cache) {
			return (CombinedCollectionBuilder2<E, V, T>) super.cache(cache);
		}

		@Override
		public CombinedCollectionBuilder2<E, V, T> reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return this;
		}

		@Override
		public CombinedCollectionBuilder2<E, V, T> fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return this;
		}

		/**
		 * @param reverse The reverse function to enable adding values to the result
		 * @return This builder
		 */
		public CombinedCollectionBuilder2<E, V, T> withReverse(BiFunction<? super T, ? super V, ? extends E> reverse) {
			return withReverse(cv -> reverse.apply(cv.getElement(), cv.get(theArg2)));
		}

		@Override
		public CombinedCollectionBuilder2<E, V, T> withReverse(Function<? super CombinedValues<? extends T>, ? extends E> reverse) {
			super.withReverse(reverse);
			return this;
		}

		@Override
		public <U> CombinedCollectionBuilder3<E, V, U, T> with(ObservableValue<U> arg3) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedCollectionBuilder3<>(this, theArg2, arg3);
		}
	}

	/**
	 * A {@link CombinedCollectionBuilder} for the combination of a collection with 2 values. Use {@link #with(ObservableValue)} to combine
	 * with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <V1> The type of the first combined value
	 * @param <V2> The type of the second combined value
	 * @param <T> The type of elements in the resulting collection
	 * @see ObservableCollection.CollectionDataFlow#combine(TypeToken, Function)
	 * @see CombinedCollectionBuilder2#with(ObservableValue)
	 */
	public static class CombinedCollectionBuilder3<E, V1, V2, T> extends Combination.AbstractCombinedCollectionBuilder<E, T> {
		private final ObservableValue<V1> theArg2;
		private final ObservableValue<V2> theArg3;

		/**
		 * @param template The 2-argument combination that this is based on
		 * @param arg2 The first argument to combine with the flow values
		 * @param arg3 The second argument to combine with the flow values
		 */
		protected CombinedCollectionBuilder3(CombinedCollectionBuilder2<E, V1, T> template, ObservableValue<V1> arg2,
			ObservableValue<V2> arg3) {
			super(template);
			theArg2 = arg2;
			theArg3 = arg3;
			addArg(arg2);
			addArg(arg3);
		}

		/** @return This builder's first value argument */
		protected ObservableValue<V1> getArg2() {
			return theArg2;
		}

		/** @return This builder's second value argument */
		protected ObservableValue<V2> getArg3() {
			return theArg3;
		}

		@Override
		public CombinedCollectionBuilder3<E, V1, V2, T> cache(boolean cache) {
			super.cache(cache);
			return this;
		}

		@Override
		public CombinedCollectionBuilder3<E, V1, V2, T> reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return this;
		}

		@Override
		public CombinedCollectionBuilder3<E, V1, V2, T> fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return this;
		}

		/**
		 * @param reverse The reverse function to enable adding values to the result
		 * @return This builder
		 */
		public CombinedCollectionBuilder3<E, V1, V2, T> withReverse(TriFunction<? super T, ? super V1, ? super V2, ? extends E> reverse) {
			return withReverse(cv -> reverse.apply(cv.getElement(), cv.get(theArg2), cv.get(theArg3)));
		}

		@Override
		public CombinedCollectionBuilder3<E, V1, V2, T> withReverse(Function<? super CombinedValues<? extends T>, ? extends E> reverse) {
			super.withReverse(reverse);
			return this;
		}

		/**
		 * @param combination The function to combine the source value and the 2 argument values
		 * @return The combination definition
		 */
		public CombinedFlowDef<E, T> build(TriFunction<? super E, ? super V1, ? super V2, ? extends T> combination) {
			return build(cv -> combination.apply(cv.getElement(), cv.get(theArg2), cv.get(theArg3)));
		}

		@Override
		public <T2> CombinedCollectionBuilder<E, T> with(ObservableValue<T2> arg) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedCollectionBuilderN<>(this, theArg2, theArg3, arg);
		}
	}

	/**
	 * A {@link CombinedCollectionBuilder} for the combination of a collection with one or more (typically at least 3) values. Use
	 * {@link #with(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <T> The type of elements in the resulting collection
	 * @see ObservableCollection.CollectionDataFlow#combine(TypeToken, Function)
	 * @see CombinedCollectionBuilder3#with(ObservableValue)
	 */
	public static class CombinedCollectionBuilderN<E, T> extends Combination.AbstractCombinedCollectionBuilder<E, T> {
		/**
		 * @param template The 3-argument combination that this is based on
		 * @param arg2 The first argument value
		 * @param arg3 The second argument value
		 * @param arg4 The third argument value
		 */
		protected CombinedCollectionBuilderN(CombinedCollectionBuilder3<E, ?, ?, T> template, ObservableValue<?> arg2,
			ObservableValue<?> arg3, ObservableValue<?> arg4) {
			super(template);
			addArg(arg2);
			addArg(arg3);
			addArg(arg4);
		}

		@Override
		public CombinedCollectionBuilder<E, T> cache(boolean cache) {
			super.cache(cache);
			return this;
		}

		@Override
		public CombinedCollectionBuilder<E, T> reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return this;
		}

		@Override
		public CombinedCollectionBuilder<E, T> fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return this;
		}

		@Override
		public CombinedCollectionBuilder<E, T> withReverse(Function<? super CombinedValues<? extends T>, ? extends E> reverse) {
			super.withReverse(reverse);
			return this;
		}

		@Override
		public <X> CombinedCollectionBuilder<E, T> with(ObservableValue<X> arg) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			addArg(arg);
			return this;
		}
	}

	/**
	 * A structure that is operated on to produce the elements of a combined collection
	 *
	 * @param <E> The type of the source element (or the value to be reversed)
	 * @see ObservableCollection.CollectionDataFlow#combineWith(ObservableValue, TypeToken)
	 */
	interface CombinedValues<E> {
		E getElement();

		<T> T get(ObservableValue<T> arg);
	}

	private static abstract class AbstractCombinedCollectionBuilder<E, R> extends XformOptions.SimpleXformOptions
	implements CombinedCollectionBuilder<E, R> {
		private final BetterHashSet<ObservableValue<?>> theArgs;
		private Function<? super CombinedValues<? extends R>, ? extends E> theReverse;
		private final boolean isManyToOne;

		protected AbstractCombinedCollectionBuilder(AbstractCombinedCollectionBuilder<E, R> template) {
			if (template != null) {
				cache(template.isCached());
				reEvalOnUpdate(template.isReEvalOnUpdate());
				fireIfUnchanged(template.isFireIfUnchanged());
				isManyToOne = template.isManyToOne;
			} else
				isManyToOne = false;
			theArgs = BetterHashSet.build().identity().unsafe().buildSet();
		}

		protected void addArg(ObservableValue<?> arg) {
			if (theArgs.contains(arg))
				throw new IllegalArgumentException("Argument " + arg + " is already combined");
			theArgs.add(arg);
		}

		protected Function<? super CombinedValues<? extends R>, ? extends E> getReverse() {
			return theReverse;
		}

		@Override
		public CombinedCollectionBuilder<E, R> reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return this;
		}

		@Override
		public CombinedCollectionBuilder<E, R> fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return this;
		}

		@Override
		public CombinedCollectionBuilder<E, R> cache(boolean cache) {
			super.cache(cache);
			return this;
		}

		@Override
		public CombinedCollectionBuilder<E, R> withReverse(Function<? super CombinedValues<? extends R>, ? extends E> reverse) {
			theReverse = reverse;
			return this;
		}

		@Override
		public CombinedFlowDef<E, R> build(Function<? super CombinedValues<? extends E>, ? extends R> combination) {
			return build((cv, o) -> combination.apply(cv));
		}

		@Override
		public CombinedFlowDef<E, R> build(BiFunction<? super CombinedValues<? extends E>, ? super R, ? extends R> combination) {
			Set<ObservableValue<?>> args = Collections.unmodifiableSet(BetterHashSet.build().identity().unsafe().buildSet(theArgs));
			return new CombinedFlowDef<>(this, args, combination, theReverse, isManyToOne);
		}
	}
}
