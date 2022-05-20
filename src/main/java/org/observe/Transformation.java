package org.observe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Lockable;
import org.qommons.Stamped;
import org.qommons.StringUtils;
import org.qommons.ThreadConstrained;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.TriConsumer;
import org.qommons.TriFunction;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement.StdMsg;

import com.google.common.reflect.TypeToken;

/**
 * <p>
 * Represents a mapping or combination operation capable of producing a single result value per source value.
 * </p>
 * <p>
 * This operation may represent a simple one-to-one mapping or it may include other data sources that contribute to the result in addition
 * to the source.
 * <p>
 * <p>
 * This operation may be configured with several options, including {@link #isCached() cachability}.
 * </p>
 * <p>
 * This operation may be {@link ReversibleTransformation reversible}, supporting the modification of source structures from the result
 * structure.
 * </p>
 *
 * <p>
 * Observable structures supporting transformation provide a method of the form <br>
 * <code> &lt;T> transform(TypeToken&lt;T>, Function&lt;{@link TransformationPrecursor}&lt;S, T>,
 *  {@link Transformation}&lt;S, T>>)</code><br>
 * or<br>
 * <code> &lt;T> transform(TypeToken&lt;T>, Function&lt;{@link ReversibleTransformationPrecursor}&lt;S, T>,
 *  {@link ReversibleTransformation}&lt;S, T>>)</code> if the structure supports modification.<br>
 * To configure a transformed structure call this method on the source structure with the target type of the transformation and a function
 * that uses the methods on {@link TransformationBuilder} to create a {@link Transformation}.
 * </p>
 * <p>
 * Transformation building only requires specifying the method of producing result values from source values. In the simplest case of a
 * simple mapping, this can be done via
 * <ul>
 * <li>{@link TransformationPrecursor#map(Function)},</li>
 * <li>{@link TransformationPrecursor#map(BiFunction)}, or</li>
 * <li>{@link TransformationPrecursor#build(BiFunction)}</li>
 * </ul>
 * But there are several options that may be modified or supplied for modified or added capability.
 * <ul>
 * <li>The {@link XformOptions transformation options} can be modified to change the behavior of the transformation</li>
 * <li>External argument values can be combined with source values via {@link TransformationBuilder#combineWith(ObservableValue)}</li>
 * <li>Reversible transformations (where the argument of the transformation configuration function is
 * {@link ReversibleTransformationPrecursor}) may be configured to support modification of source structures from result structures via
 * {@link MaybeReversibleTransformation#withReverse(TransformReverse)}</li>
 * </ul>
 * Transformation typically happens in stages:
 * <ol>
 * <li>The transformation options are specified to govern the behavior of the transformation</li>
 * <li>Any desired external values are combined into the transformation via {@link TransformationBuilder#combineWith(ObservableValue)}</li>
 * <li>The method of producing result values from source and environment is specified. This can be done generically with
 * {@link TransformationBuilder#build(BiFunction)} or with the custom map/combine methods provided by the {@link TransformationPrecursor
 * zero-argument}, {@link TransformationBuilder2 one-argument}, or {@link TransformationBuilder3 two-argument} builder extensions. These
 * methods all return a {@link Transformation} instance.</li>
 * <li>Equivalence for result values can be specified via {@link #withEquivalence(Equivalence)} to govern certain aspects of the
 * transformation behavior</li>
 * <li>If reverse operations are to be supported, a method must be supplied to support them.</li>
 * </ol>
 *
 * <p>
 * Reversibility may be supported in several ways. Generic reversibility support may be achieved by supplying an implementation of
 * {@link TransformReverse} to {@link MaybeReversibleTransformation#withReverse(TransformReverse)}, but typically reversal is supported by
 * calling one of the replaceSource or modifySource methods. These methods behave differently.
 * </p>
 * <p>
 * replaceSource and similar methods work by transforming a result value into a source value which must be added to the source structure,
 * either in place of an existing source value or as an addition. This method can be stateless, not requiring the current source value or
 * the previous value of the result, which can be more efficient and flexible in some situations.
 * </p>
 * <p>
 * modifySource works by modifying source values using the supplied result values. Such operations are always stateful. Additions are not
 * supported unless the {@link SourceModifyingReverse} instance is configured with {@link SourceModifyingReverse#createWith(TriFunction)}.
 * This method does not require the ability to modify the source structure, only the ability to fire update events on it.
 * </p>
 *
 * @param <S> The type of the source values that this transformation can map
 * @param <T> The type of the result values that this transformation can produce
 * @see ObservableValue#transform(TypeToken, Function)
 * @see SettableValue#transformReversible(TypeToken, Function)
 * @see org.observe.collect.ObservableCollection.CollectionDataFlow#transform(TypeToken, Function)
 * @see org.observe.collect.ObservableCollection.DistinctDataFlow#transformEquivalent(TypeToken, Function)
 * @see org.observe.collect.ObservableCollection.DistinctSortedDataFlow#transformEquivalent(TypeToken, Function, java.util.Comparator)
 */
public class Transformation<S, T> extends XformOptions.XformDef implements Identifiable {
	/**
	 * Contains information about a particular transformation operation
	 *
	 * @param <S> The source value type
	 * @param <T> The result value type
	 */
	public interface TransformationValues<S, T> {
		/** @return Whether this represents a source change */
		boolean isSourceChange();

		/** @return The current value of the source */
		S getCurrentSource();

		/**
		 * @return Whether the previous value of the result is known. It may be unknown for several reasons:
		 *         <ul>
		 *         <li>If the Transformation is {@link Transformation#isCached() uncached}, such that the previous result value is not
		 *         stored
		 *         </p>
		 *         <li>If this operation is an add operation</li>
		 *         <li>If this operation is to populate the initial value of the result</li>
		 *         </ul>
		 */
		boolean hasPreviousResult();

		/**
		 * @return The previous value of the result, or null if unavailable
		 * @see #hasPreviousResult()
		 */
		T getPreviousResult();

		/**
		 * @param value The observable value to check
		 * @return Whether this operation knows about the given value
		 */
		boolean has(ObservableValue<?> value);

		/**
		 * @param <V> The type of the value
		 * @param value The observable value to get the value of
		 * @return The value of the given observable value in this operation
		 * @throws IllegalArgumentException If the given value is not recognized by this operation
		 */
		<V> V get(ObservableValue<V> value) throws IllegalArgumentException;
	}

	private final Map<ObservableValue<?>, Integer> theArgs;
	private final BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> theCombination;
	private final Equivalence<? super T> theResultEquivalence;

	/**
	 * @param options The transformation options to copy into this definition
	 * @param args External arguments to affect the result, identity-keyed, index-valued
	 * @param combination The combination operation to convert source plus environment into results
	 * @param resultEquivalence The equivalence to use for the result values
	 */
	protected Transformation(XformOptions options, Map<ObservableValue<?>, Integer> args,
		BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> combination,
		Equivalence<? super T> resultEquivalence) {
		super(options);
		if (combination == null)
			throw new NullPointerException("Mapping/combination function cannot be null");
		theArgs = args;
		theCombination = combination;
		theResultEquivalence = resultEquivalence;
	}

	Map<ObservableValue<?>, Integer> _getArgs() {
		return theArgs;
	}

	/** @return The equivalence to use for result values */
	public Equivalence<? super T> equivalence() {
		return theResultEquivalence;
	}

	/**
	 * Changes result equivalence. This may affect the behavior of a transformation. For example:
	 * <ul>
	 * <li>If {@link #isFireIfUnchanged() fire-if-unchanged} is false, this operation will not fire update operations if a source or
	 * environment change produces 2 equivalent values</li>
	 * <li>For {@link Transformation.SourceReplacingReverse source-replacing} reversible operations, if
	 * {@link Transformation.MappingSourceReplacingReverse#isInexactReversible() inexact-reversible} is false (the default) and a result
	 * value is given for a reverse operation that is not equivalent the transformation of the reversed source value, the operation will be
	 * forbidden</li>
	 * </ul>
	 *
	 * @param resultEquivalence The equivalence to use for result values
	 * @return A new transformation that uses the given result equivalence
	 * @see Equivalence#DEFAULT
	 * @see Equivalence#ID
	 * @see Equivalence#sorted(Class, java.util.Comparator, boolean)
	 */
	public Transformation<S, T> withEquivalence(Equivalence<? super T> resultEquivalence) {
		if (resultEquivalence.equals(equivalence()))
			return this;
		return new Transformation<>(toOptions(), theArgs, theCombination, resultEquivalence);
	}

	@Override
	public Object getIdentity() {
		if (theArgs.isEmpty()) {
			return Identifiable.baseId(theCombination.toString(), theCombination);
		} else {
			StringBuilder descrip = new StringBuilder("combine(");
			List<Object> ids = new ArrayList<>(theArgs.size() + 1);
			ids.add(theCombination);
			descrip.append(theCombination).append(", ");
			ids.addAll(theArgs.keySet());
			StringUtils.conversational(", ", null).print(descrip, theArgs.keySet(), (str, v) -> str.append(v.getIdentity()));
			descrip.append(')');
			return Identifiable.baseId(descrip.toString(), ids);
		}
	}

	/** @return The observable values to combine with each source element */
	public Set<ObservableValue<?>> getArgs() {
		return theArgs.keySet();
	}

	/**
	 * @param arg The argument to test
	 * @return Whether this transformation uses the given argument
	 */
	public boolean hasArg(ObservableValue<?> arg) {
		return theArgs.containsKey(arg);
	}

	/**
	 * @param arg The argument observable value
	 * @return The index of the given value in this combination definition
	 * @throws IllegalArgumentException If the given argument was not added to this combination with
	 *         {@link Transformation.TransformationBuilder#combineWith(ObservableValue)}
	 */
	public int getArgIndex(ObservableValue<?> arg) throws IllegalArgumentException {
		Integer index = theArgs.get(arg);
		if (index == null) {
			theArgs.get(arg); // Debugging
			throw new IllegalArgumentException("Unrecognized argument: " + arg);
		}
		return index.intValue();
	}

	/**
	 * @param index The index of the argument to get
	 * @return The argument in this transformation at the given index
	 */
	public ObservableValue<?> getArg(int index) {
		// Inefficient, but I don't want to store the arguments twice, there generally won't be many arguments,
		// and this method is called often by the transformation architecture
		int i = 0;
		for (ObservableValue<?> v : theArgs.keySet()) {
			if (i == index)
				return v;
			i++;
		}
		throw new IndexOutOfBoundsException(index + " of " + theArgs.size());
	}

	/** @return The combination function to combine the source and argument values */
	public BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> getCombination() {
		return theCombination;
	}

	/**
	 * @param sourceEquivalence The equivalence to use for the source values. This can affect the behavior of the transformation via flags
	 *        like {@link #isReEvalOnUpdate() re-eval-on-update}.
	 * @return An engine to drive a transformed observable structure
	 */
	public Engine<S, T> createEngine(Equivalence<? super S> sourceEquivalence) {
		return new EngineImpl<>(this, sourceEquivalence);
	}

	@Override
	public int hashCode() {
		return super.hashCode() * 7 + Objects.hash(theArgs, theCombination);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (!(obj instanceof Transformation) || !super.equals(obj))
			return false;
		Transformation<?, ?> other = (Transformation<?, ?>) obj;
		return Objects.equals(theArgs, other.theArgs)//
			&& Objects.equals(theCombination, other.theCombination);
	}

	@Override
	public String toString() {
		if (theArgs.isEmpty())
			return theCombination.toString();
		StringBuilder str = new StringBuilder();
		str.append("combination(");
		StringUtils.print(str, ", ", theArgs.keySet(), (s, arg) -> s.append(arg.getIdentity()));
		str.append(") with ").append(theCombination).append(", ").append(super.toString());
		return str.toString();
	}

	/**
	 * A result of {@link Transformation.TransformReverse#reverse(Object, TransformationValues, boolean, boolean)}
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

				@Override
				public String toString() {
					return "rejected:" + message;
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

				@Override
				public String toString() {
					return "value:" + value;
				}
			};
		}
	}

	/**
	 * Allows the ability to add and/or set elements of a transformed data structure by providing source values from target values
	 *
	 * @param <S> The source type of the transformed structure
	 * @param <T> The target type of the transformed structure
	 */
	public interface TransformReverse<S, T> {
		/**
		 * @return Whether this reverse depends on the previous source or result values. If this is false, the
		 *         {@link Transformation.TransformationValues#getCurrentSource()} or
		 *         {@link Transformation.TransformationValues#getPreviousResult()} methods may throw exceptions if called by
		 *         {@link #reverse(Object, TransformationValues, boolean, boolean)}.
		 */
		boolean isStateful();

		/**
		 * @param transformValues The transformation environment to use
		 * @return Null if some values may be {@link #reverse(Object, TransformationValues, boolean, boolean) reversible} in the given
		 *         environment, or a reason why modification/addition is not enabled
		 */
		String isEnabled(TransformationValues<S, T> transformValues);

		/**
		 * @param newValue The new target value
		 * @param transformValues The source, argument, and current result values available to the transformation
		 * @param add Whether the operation is an addition (as opposed to a set operation)
		 * @param test Whether this is just a test or the operation should actually be performed if acceptable
		 * @return The result, either {@link Transformation.ReverseQueryResult#reject(String) rejected} or the reversed
		 *         {@link Transformation.ReverseQueryResult#value(Object) value}
		 */
		ReverseQueryResult<S> reverse(T newValue, TransformationValues<S, T> transformValues, boolean add, boolean test);
	}

	/**
	 * A sub-interface of {@link Transformation.TransformReverse} that allows some additional custom configuration for the types of
	 * operations supported
	 *
	 * @param <S> The source type of the transformed structure
	 * @param <T> The target type of the transformed structure
	 */
	public interface ConfigurableReverse<S, T> extends TransformReverse<S, T> {
		/**
		 * Supplies an additional filter for {@link #isEnabled(TransformationValues)}
		 *
		 * @param enabled A filter that returns null for environments for which set or addition operations are supported, or a reason why
		 *        they aren't
		 * @return A new reverse operation that supports all the same operations as this reverse, with the exception of those forbidden by
		 *         the given filter
		 */
		TransformReverse<S, T> disableWith(Function<? super TransformationValues<? extends S, ? extends T>, String> enabled);

		/**
		 * Supplies an additional filter for result values supplied to {@link #reverse(Object, TransformationValues, boolean, boolean)}
		 *
		 * @param acceptance The filter that returns null for result values that can be reversed, or a reason why they can't
		 * @return A new reverse operation that supports all the same operations as this reverse, with the exception of those forbidden by
		 *         the given filter
		 */
		default ConfigurableReverse<S, T> rejectWith(Function<? super T, String> acceptance) {
			return rejectWith(LambdaUtils.toBiFunction1(acceptance), true, false);
		}

		/**
		 * Supplies an additional filter for {@link #reverse(Object, TransformationValues, boolean, boolean) reverse} operations. Like
		 * {@link #rejectWith(Function)}, but may filter based on the current source value as well.
		 *
		 * @param acceptance The filter that returns null for source/result values that can be reversed, or a message why they can't
		 * @return A new reverse operation that supports all the same operations as this reverse, with the exception of those forbidden by
		 *         the given filter
		 */
		default ConfigurableReverse<S, T> rejectWith(BiFunction<? super S, ? super T, String> acceptance) {
			return rejectWith(acceptance == null ? null : LambdaUtils.printableBiFn((t, cv) -> {
				return acceptance.apply(cv.getCurrentSource(), t);
			}, acceptance::toString, null), false, true);
		}

		/**
		 * Enables addition operations by creating source values from supplied result values
		 *
		 * @param creator The function to create source values for additions
		 * @return A new reverse operation that support all the same operations as this reverse in addition to addition operations using the
		 *         given function
		 */
		default ConfigurableReverse<S, T> createWith(Function<? super T, ? extends S> creator) {
			return createWith(LambdaUtils.toTriFunction1(creator));
		}

		/**
		 * Enables addition operations by creating source values from supplied result and environment values
		 *
		 * @param creator The function to create source values for additions
		 * @return A new reverse operation that support all the same operations as this reverse in addition to addition operations using the
		 *         given function
		 */
		ConfigurableReverse<S, T> createWith(
			TriFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, Boolean, ? extends S> creator);

		/**
		 * Supplies an additional filter for {@link #reverse(Object, TransformationValues, boolean, boolean) reverse} operations
		 *
		 * @param acceptance The filter that returns null for reverse operations that can be reversed, or a reason why they can't
		 * @param forAddToo Whether to apply the filter to addition operations as well, or just sets
		 * @param stateful Whether the filter is stateful, that is, dependent on the current source and/or result value
		 * @return A new reverse operation that supports all the same operations as this reverse, with the exception of those forbidden by
		 *         the given filter
		 */
		ConfigurableReverse<S, T> rejectWith(
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> acceptance, boolean forAddToo,
			boolean stateful);

		/**
		 * Supplies an additional filter for additions
		 *
		 * @param addAcceptance The filter that returns null for result values that can be added, or a reason why they can't
		 * @return A new reverse operation that supports all the same operations as this reverse, with the exception of those forbidden by
		 *         the given filter
		 */
		default ConfigurableReverse<S, T> rejectAddWith(Function<? super T, String> addAcceptance) {
			return rejectAddWith(LambdaUtils.toBiFunction1(addAcceptance));
		}

		/**
		 * Supplies an additional filter for additions
		 *
		 * @param acceptance The filter that returns null for addition operations that can be completed, or a reason why they can't
		 * @return A new reverse operation that supports all the same operations as this reverse, with the exception of those forbidden by
		 *         the given filter
		 */
		ConfigurableReverse<S, T> rejectAddWith(
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> acceptance);
	}

	/**
	 * A {@link Transformation} that may be configured with reversibility via
	 * {@link #withReverse(org.observe.Transformation.TransformReverse)} to produce a {@link Transformation.ReversibleTransformation}.
	 *
	 * @param <S> The type of the source values
	 * @param <T> The type of the result values
	 */
	public static class MaybeReversibleTransformation<S, T> extends Transformation<S, T> {
		/** @see Transformation#Transformation(XformOptions, Map, BiFunction, Equivalence) */
		protected MaybeReversibleTransformation(XformOptions options, Map<ObservableValue<?>, Integer> args,
			BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> combination,
			Equivalence<? super T> resultEquivalence) {
			super(options, args, combination, resultEquivalence);
		}

		@Override
		public MaybeReversibleTransformation<S, T> withEquivalence(Equivalence<? super T> resultEquivalence) {
			return new MaybeReversibleTransformation<>(toOptions(), _getArgs(), getCombination(), resultEquivalence);
		}

		/**
		 * Supports a by supplying new source values for set or addition operations
		 *
		 * @param reverse The function to create source values from result and environment values
		 * @return The reversible transformation
		 */
		public ReversibleTransformation<S, T> replaceSourceWith(
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, ? extends S> reverse) {
			return replaceSourceWith(reverse, null);
		}

		/**
		 * Supports reverse by supplying new source values for set or addition operations
		 *
		 * @param reverse The function to create source values from result and environment values
		 * @param configure Optional function to further configure the reverse operation
		 * @return The reversible transformation
		 */
		public ReversibleTransformation<S, T> replaceSourceWith(
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, ? extends S> reverse,
			Function<? super SourceReplacingReverse<S, T>, ? extends SourceReplacingReverse<S, T>> configure) {
			TriFunction<T, TransformationValues<? extends S, ? extends T>, Boolean, ? extends S> creator = LambdaUtils
				.toTriFunction1And2(reverse);
			SourceReplacingReverse<S, T> srr = new SourceReplacingReverse<>(this, reverse, null, null, creator, null, true, false);
			if (configure != null)
				srr = configure.apply(srr);
			return withReverse(srr);
		}

		/**
		 * Supports reverse by modifying existing source values with result values for set operations. Additions will not be supported.
		 *
		 * @param modifier The function to modify source values with result and environment values
		 * @return The reversible transformation
		 */
		public ReversibleTransformation<S, T> modifySourceWith(
			BiConsumer<? super T, ? super TransformationValues<? extends S, ? extends T>> modifier) {
			return modifySourceWith(modifier, null);
		}

		/**
		 * Supports reverse by modifying existing source values with result values for set operations. Additions will not be supported
		 * unless configured with {@link Transformation.SourceModifyingReverse#createWith(TriFunction)}.
		 *
		 * @param modifier The function to modify source values with result and environment values
		 * @param configure Optional function to further configure the reverse operation
		 * @return The reversible transformation
		 */
		public ReversibleTransformation<S, T> modifySourceWith(
			BiConsumer<? super T, ? super TransformationValues<? extends S, ? extends T>> modifier,
				Function<SourceModifyingReverse<S, T>, SourceModifyingReverse<S, T>> configure) {
			SourceModifyingReverse<S, T> srr = new SourceModifyingReverse<>(modifier,
				LambdaUtils.printableFn(tx -> tx.getCurrentSource() == null ? "No source value" : null, "Non-null source", null), null,
				null, null);
			if (configure != null)
				srr = configure.apply(srr);
			return withReverse(srr);
		}

		/**
		 * Supports reverse operations
		 *
		 * @param reverse The reverse operation for the transformation
		 * @return The reversible transformation
		 */
		public ReversibleTransformation<S, T> withReverse(TransformReverse<S, T> reverse) {
			return new ReversibleTransformation<>(this, _getArgs(), getCombination(), equivalence(), reverse);
		}
	}

	/**
	 * A {@link Transformation} that facilitates modification of source values by setting or adding result values
	 *
	 * @param <S> The source type of the transformation
	 * @param <T> The result type of the transformation
	 */
	public static class ReversibleTransformation<S, T> extends Transformation<S, T> {
		private final TransformReverse<S, T> theReverse;

		/**
		 * @param options The transformation options to copy into this definition
		 * @param args External arguments to affect the result, identity-keyed, index-valued
		 * @param combination The combination operation to convert source plus environment into results
		 * @param resultEquivalence The equivalence to use for the result values
		 * @param reverse The reverse operation to convert result plus environment back into source values
		 * @see Transformation#Transformation(XformOptions, Map, BiFunction, Equivalence)
		 */
		protected ReversibleTransformation(Transformation<S, T> options, Map<ObservableValue<?>, Integer> args,
			BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> combination,
			Equivalence<? super T> resultEquivalence, TransformReverse<S, T> reverse) {
			super(options.toOptions(), args, combination, resultEquivalence);
			if (reverse == null)
				throw new NullPointerException("Reverse function cannot be null");
			theReverse = reverse;
		}

		@Override
		public ReversibleTransformation<S, T> withEquivalence(Equivalence<? super T> resultEquivalence) {
			return new ReversibleTransformation<>(this, _getArgs(), getCombination(), resultEquivalence, getReverse());
		}

		/** @return This transformation's reverse operation */
		public TransformReverse<S, T> getReverse() {
			return theReverse;
		}

		@Override
		public int hashCode() {
			return super.hashCode() * 3 + theReverse.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return super.equals(obj) && obj instanceof ReversibleTransformation
				&& theReverse.equals(((ReversibleTransformation<?, ?>) obj).theReverse);
		}
	}

	/**
	 * A {@link Transformation.TransformReverse} implementation that generates new source values from result values
	 *
	 * @param <S> The source type of the transformed structure
	 * @param <T> The target type of the transformed structure
	 */
	public static class SourceReplacingReverse<S, T> implements ConfigurableReverse<S, T> {
		@SuppressWarnings("javadoc")
		protected final Transformation<S, T> theTransformation;
		@SuppressWarnings("javadoc")
		protected final BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, ? extends S> theReverse;
		@SuppressWarnings("javadoc")
		protected final Function<? super TransformationValues<? extends S, ? extends T>, String> theEnabled;
		@SuppressWarnings("javadoc")
		protected final BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> theAcceptability;
		@SuppressWarnings("javadoc")
		protected final TriFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, Boolean, ? extends S> theCreator;
		@SuppressWarnings("javadoc")
		protected final BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> theAddAcceptability;
		@SuppressWarnings("javadoc")
		protected final boolean isStateful;
		@SuppressWarnings("javadoc")
		protected final boolean isInexactReversible;

		/**
		 * An abbreviated constructor
		 *
		 * @param transformation The transformation producing result values that uses this reverse
		 * @param reverse A function to produce source values from result value inputs
		 * @param stateful Whether the reverse function depends on the previous source value
		 * @param inexactReversible See {@link Transformation.MappingSourceReplacingReverse#allowInexactReverse(boolean)}
		 */
		public SourceReplacingReverse(Transformation<S, T> transformation,
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, ? extends S> reverse, boolean stateful,
			boolean inexactReversible) {
			this(transformation, reverse, //
				null, null, null, null, stateful, inexactReversible);
		}

		/**
		 * @param transformation The transformation producing result values that uses this reverse
		 * @param reverse A function to produce source values from result value inputs
		 * @param enabled An optional function to provide enablement for reverse operations
		 * @param acceptability An optional function to approve or reject values for set operations
		 * @param create A function to create new source values for values added to the result structure. The third argument is whether the
		 *        value will actually be passed to the source structure (as opposed to just a test operation)
		 * @param addAcceptability A function to approve or reject new result values for addition to the result structure
		 * @param stateful Whether the reverse function depends on the previous source value
		 * @param inexactReversible See {@link Transformation.MappingSourceReplacingReverse#allowInexactReverse(boolean)}
		 */
		public SourceReplacingReverse(Transformation<S, T> transformation,
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, ? extends S> reverse,
			Function<? super TransformationValues<? extends S, ? extends T>, String> enabled,
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> acceptability,
			TriFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, Boolean, ? extends S> create,
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> addAcceptability, boolean stateful,
			boolean inexactReversible) {
			theTransformation = transformation;
			theReverse = reverse;
			theEnabled = enabled;
			theAcceptability = acceptability;
			theCreator = create;
			theAddAcceptability = addAcceptability;
			isStateful = stateful;
			if (inexactReversible && !transformation.getArgs().isEmpty()) {
				throw new IllegalArgumentException(
					"Inexact reversal is not supported with combination.  It breaks equivalence-related contracts.");
			}
			isInexactReversible = inexactReversible;
		}

		@Override
		public SourceReplacingReverse<S, T> disableWith(Function<? super TransformationValues<? extends S, ? extends T>, String> enabled) {
			Function<? super TransformationValues<? extends S, ? extends T>, String> oldEnabled = theEnabled;
			Function<? super TransformationValues<? extends S, ? extends T>, String> newEnabled;
			if (oldEnabled == null || enabled == null)
				newEnabled = enabled;
			else {
				newEnabled = tx -> {
					String msg = enabled.apply(tx);
					if (msg == null)
						msg = oldEnabled.apply(tx);
					return msg;
				};
			}
			return new SourceReplacingReverse<>(theTransformation, theReverse, newEnabled, theAcceptability, theCreator,
				theAddAcceptability, isStateful, isInexactReversible);
		}

		@Override
		public SourceReplacingReverse<S, T> rejectWith(Function<? super T, String> acceptance) {
			return (SourceReplacingReverse<S, T>) ConfigurableReverse.super.rejectWith(acceptance);
		}

		@Override
		public SourceReplacingReverse<S, T> rejectWith(BiFunction<? super S, ? super T, String> acceptance) {
			return (SourceReplacingReverse<S, T>) ConfigurableReverse.super.rejectWith(acceptance);
		}

		@Override
		public SourceReplacingReverse<S, T> createWith(Function<? super T, ? extends S> creator) {
			return (SourceReplacingReverse<S, T>) ConfigurableReverse.super.createWith(creator);
		}

		@Override
		public SourceReplacingReverse<S, T> rejectAddWith(Function<? super T, String> addAcceptance) {
			return (SourceReplacingReverse<S, T>) ConfigurableReverse.super.rejectAddWith(addAcceptance);
		}

		@Override
		public SourceReplacingReverse<S, T> rejectWith(
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> acceptance, boolean forAddToo,
			boolean stateful) {
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> oldAcceptance = theAcceptability;
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> newAcceptance;
			if (oldAcceptance == null || acceptance == null)
				newAcceptance = acceptance;
			else {
				newAcceptance = (newResult, tx) -> {
					String msg = acceptance.apply(newResult, tx);
					if (msg == null)
						msg = oldAcceptance.apply(newResult, tx);
					return msg;
				};
			}
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> newAddAcceptance;
			if (!forAddToo)
				newAddAcceptance = theAddAcceptability;
			else {
				BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> oldAddAcceptance = theAddAcceptability;
				if (oldAddAcceptance == null || acceptance == null)
					newAddAcceptance = acceptance;
				else {
					newAddAcceptance = (newResult, tx) -> {
						String msg = acceptance.apply(newResult, tx);
						if (msg == null)
							msg = oldAddAcceptance.apply(newResult, tx);
						return msg;
					};
				}
			}
			return new SourceReplacingReverse<>(theTransformation, theReverse, theEnabled, newAcceptance, theCreator, newAddAcceptance,
				isStateful || stateful, isInexactReversible);
		}

		@Override
		public SourceReplacingReverse<S, T> createWith(
			TriFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, Boolean, ? extends S> creator) {
			return new SourceReplacingReverse<>(theTransformation, theReverse, theEnabled, theAcceptability, creator, theAddAcceptability,
				isStateful, isInexactReversible);
		}

		@Override
		public SourceReplacingReverse<S, T> rejectAddWith(
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> acceptance) {
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> oldAcceptance = theAddAcceptability;
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> newAcceptance;
			if (oldAcceptance == null || acceptance == null)
				newAcceptance = acceptance;
			else {
				newAcceptance = (newResult, tx) -> {
					String msg = acceptance.apply(newResult, tx);
					if (msg == null)
						msg = oldAcceptance.apply(newResult, tx);
					return msg;
				};
			}
			return new SourceReplacingReverse<>(theTransformation, theReverse, theEnabled, theAcceptability, theCreator, newAcceptance,
				isStateful, isInexactReversible);
		}

		@Override
		public boolean isStateful() {
			return isStateful;
		}

		@Override
		public String isEnabled(TransformationValues<S, T> transformValues) {
			if (theEnabled == null)
				return null;
			else
				return theEnabled.apply(transformValues);
		}

		@Override
		public ReverseQueryResult<S> reverse(T newValue, TransformationValues<S, T> transformValues, boolean add, boolean test) {
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, ? extends S> reverse;
			if (!add) {
				if (theEnabled != null) {
					String msg = theEnabled.apply(transformValues);
					if (msg != null)
						return ReverseQueryResult.reject(msg);
				}
				reverse = theReverse;
			} else if (theCreator == null)
				return ReverseQueryResult.reject(StdMsg.UNSUPPORTED_OPERATION);
			else
				reverse = theCreator.curry3(!test);
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> enabled = add ? theAddAcceptability
				: theAcceptability;
			if (reverse == null)
				return ReverseQueryResult.reject(StdMsg.UNSUPPORTED_OPERATION);
			if (enabled != null) {
				String msg = enabled.apply(newValue, transformValues);
				if (msg != null)
					return ReverseQueryResult.reject(msg);
			}
			S reversed = reverse.apply(newValue, transformValues);
			if (!isInexactReversible) {
				T reTransformed = theTransformation.getCombination().apply(reversed, new TransformationValues<S, T>() {
					@Override
					public boolean isSourceChange() {
						return true;
					}

					@Override
					public S getCurrentSource() {
						return transformValues.getCurrentSource();
					}

					@Override
					public boolean hasPreviousResult() {
						return true;
					}

					@Override
					public T getPreviousResult() {
						return newValue;
					}

					@Override
					public boolean has(ObservableValue<?> value) {
						return transformValues.has(value);
					}

					@Override
					public <V> V get(ObservableValue<V> value) throws IllegalArgumentException {
						return transformValues.get(value);
					}
				});
				if (!theTransformation.equivalence().elementEquals(reTransformed, newValue))
					return ReverseQueryResult.reject(StdMsg.ILLEGAL_ELEMENT);
			}
			return ReverseQueryResult.value(reversed);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theReverse, theAcceptability, theCreator, theAddAcceptability, isStateful, isInexactReversible);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof SourceReplacingReverse))
				return false;
			SourceReplacingReverse<?, ?> other = (SourceReplacingReverse<?, ?>) obj;
			return theReverse.equals(other.theReverse)//
				&& Objects.equals(theCreator, other.theCreator)//
				&& isStateful == other.isStateful//
				&& Objects.equals(theAcceptability, other.theAcceptability)//
				&& Objects.equals(theAddAcceptability, other.theAddAcceptability)//
				&& isInexactReversible == other.isInexactReversible;
		}

		@Override
		public String toString() {
			return theReverse.toString();
		}
	}

	/**
	 * A subclass of {@link Transformation.SourceReplacingReverse} for mapping operations (transformations with no external arguments).
	 * Provides {@link #allowInexactReverse(boolean)}
	 *
	 * @param <S> The source type of the transformed structure
	 * @param <T> The target type of the transformed structure
	 */
	public static class MappingSourceReplacingReverse<S, T> extends SourceReplacingReverse<S, T> {
		/**
		 * @see Transformation.SourceReplacingReverse#SourceReplacingReverse(Transformation, BiFunction, Function, BiFunction, TriFunction,
		 *      BiFunction, boolean, boolean)
		 */
		public MappingSourceReplacingReverse(Transformation<S, T> transformation,
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, ? extends S> reverse,
			Function<? super TransformationValues<? extends S, ? extends T>, String> enabled,
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> acceptability,
			TriFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, Boolean, ? extends S> create,
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> addAcceptability, boolean stateful,
			boolean inexactReversible) {
			super(transformation, reverse, enabled, acceptability, create, addAcceptability, stateful, inexactReversible);
		}

		MappingSourceReplacingReverse(SourceReplacingReverse<S, T> reverse) {
			this(reverse.theTransformation, reverse.theReverse, reverse.theEnabled, reverse.theAcceptability, reverse.theCreator,
				reverse.theAddAcceptability, reverse.isStateful, reverse.isInexactReversible);
		}

		/**
		 * @return Whether this transformation allows setting some result values that cannot actually be the result of an operation on any
		 *         source value
		 */
		public boolean isInexactReversible() {
			return isInexactReversible;
		}

		/**
		 * <p>
		 * Determines whether the transformation will forbid setting result values that cannot actually be the result of an operation on any
		 * source value. False by default.
		 * </p>
		 * <p>
		 * For example, take a simple integer-typed value, mapped with a multiplication operation with 2, reversed by division with 2. If
		 * this flag is false, then setting a value of 1 on the result would throw an error, since 1 cannot ever be the result of
		 * <code>source*2</code> for any source. If the flag is true, such operations will be allowed, but the result will always be a
		 * mapping of the source value and may not match the result value given in the operation. E.g. for the example above, the result
		 * will be 0 after the operation, not 1.</code>
		 * </p>
		 * <p>
		 * If this option is used, it is important that the mapping and reverse operations never vary. I.e. for any given source value, it
		 * must always map to the same result value and vice versa. If the nature of the mapping can behave differently based on
		 * environmental or other factors, use of this option can cause serious problems, especially when used in conjunction with
		 * distinctness (e.g. {@link org.observe.collect.ObservableCollection.CollectionDataFlow#distinct()}).
		 * </p>
		 *
		 * @param allowInexactReverse Whether to allow inexact reversed values
		 * @return A new transformation with the given setting
		 */
		public MappingSourceReplacingReverse<S, T> allowInexactReverse(boolean allowInexactReverse) {
			if (allowInexactReverse == isInexactReversible)
				return this;
			return new MappingSourceReplacingReverse<>(theTransformation, theReverse, theEnabled, theAcceptability, theCreator,
				theAddAcceptability, isStateful, allowInexactReverse);
		}

		@Override
		public MappingSourceReplacingReverse<S, T> disableWith(
			Function<? super TransformationValues<? extends S, ? extends T>, String> enabled) {
			return new MappingSourceReplacingReverse<>(super.disableWith(enabled));
		}

		@Override
		public MappingSourceReplacingReverse<S, T> rejectWith(Function<? super T, String> acceptance) {
			return new MappingSourceReplacingReverse<>(super.rejectWith(acceptance));
		}

		@Override
		public MappingSourceReplacingReverse<S, T> rejectWith(BiFunction<? super S, ? super T, String> acceptance) {
			return new MappingSourceReplacingReverse<>(super.rejectWith(acceptance));
		}

		@Override
		public MappingSourceReplacingReverse<S, T> createWith(Function<? super T, ? extends S> creator) {
			return new MappingSourceReplacingReverse<>(super.createWith(creator));
		}

		@Override
		public MappingSourceReplacingReverse<S, T> rejectAddWith(Function<? super T, String> addAcceptance) {
			return new MappingSourceReplacingReverse<>(super.rejectAddWith(addAcceptance));
		}

		@Override
		public MappingSourceReplacingReverse<S, T> rejectWith(
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> acceptance, boolean forAddToo,
			boolean stateful) {
			return new MappingSourceReplacingReverse<>(super.rejectWith(acceptance, forAddToo, stateful));
		}

		@Override
		public MappingSourceReplacingReverse<S, T> createWith(
			TriFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, Boolean, ? extends S> creator) {
			return new MappingSourceReplacingReverse<>(super.createWith(creator));
		}

		@Override
		public MappingSourceReplacingReverse<S, T> rejectAddWith(
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> acceptance) {
			return new MappingSourceReplacingReverse<>(super.rejectAddWith(acceptance));
		}
	}

	/**
	 * A {@link Transformation.TransformReverse} implementation that modifies the object in the source structure with a value in the result
	 * flow
	 *
	 * @param <S> The source type of the transformed structure
	 * @param <T> The target type of the transformed structure
	 */
	public static class SourceModifyingReverse<S, T> implements ConfigurableReverse<S, T> {
		private final BiConsumer<? super T, ? super TransformationValues<? extends S, ? extends T>> theModifier;
		private final Function<? super TransformationValues<? extends S, ? extends T>, String> theEnabled;
		private final BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> theAcceptability;
		private final TriFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, Boolean, ? extends S> theCreator;
		private final BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> theAddAcceptability;

		/**
		 * @param modifier Modifies the source value with the target value
		 * @param enabled An optional function to provide enablement for reverse operations
		 * @param acceptability An optional function to approve or reject values for set operations
		 * @param create A function to create new source values for values added to the result structure. The third argument is whether the
		 *        value will actually be passed to the source structure (as opposed to just a test operation)
		 * @param addAcceptability A function to approve or reject new result values for addition to the result structure
		 */
		public SourceModifyingReverse(BiConsumer<? super T, ? super TransformationValues<? extends S, ? extends T>> modifier,
			Function<? super TransformationValues<? extends S, ? extends T>, String> enabled,
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> acceptability,
			TriFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, Boolean, ? extends S> create,
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> addAcceptability) {
			theModifier = modifier;
			theEnabled = enabled;
			theAcceptability = acceptability;
			theCreator = create;
			theAddAcceptability = addAcceptability;
		}

		@Override
		public SourceModifyingReverse<S, T> disableWith(Function<? super TransformationValues<? extends S, ? extends T>, String> enabled) {
			Function<? super TransformationValues<? extends S, ? extends T>, String> oldEnabled = theEnabled;
			Function<? super TransformationValues<? extends S, ? extends T>, String> newEnabled;
			if (oldEnabled == null || enabled == null)
				newEnabled = enabled;
			else {
				newEnabled = tx -> {
					String msg = enabled.apply(tx);
					if (msg == null)
						msg = oldEnabled.apply(tx);
					return msg;
				};
			}
			return new SourceModifyingReverse<>(theModifier, newEnabled, theAcceptability, theCreator, theAddAcceptability);
		}

		@Override
		public SourceModifyingReverse<S, T> rejectWith(Function<? super T, String> enablement) {
			return (SourceModifyingReverse<S, T>) ConfigurableReverse.super.rejectWith(enablement);
		}

		@Override
		public SourceModifyingReverse<S, T> rejectWith(BiFunction<? super S, ? super T, String> enablement) {
			return (SourceModifyingReverse<S, T>) ConfigurableReverse.super.rejectWith(enablement);
		}

		@Override
		public SourceModifyingReverse<S, T> createWith(Function<? super T, ? extends S> creator) {
			return (SourceModifyingReverse<S, T>) ConfigurableReverse.super.createWith(creator);
		}

		@Override
		public SourceModifyingReverse<S, T> rejectAddWith(Function<? super T, String> enablement) {
			return (SourceModifyingReverse<S, T>) ConfigurableReverse.super.rejectAddWith(enablement);
		}

		@Override
		public SourceModifyingReverse<S, T> rejectWith(
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> enablement, boolean forAddToo,
			boolean stateful) {
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> oldAcceptance = theAcceptability;
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> newAcceptance;
			if (oldAcceptance == null || enablement == null)
				newAcceptance = enablement;
			else {
				newAcceptance = (newResult, tx) -> {
					String msg = enablement.apply(newResult, tx);
					if (msg == null)
						msg = oldAcceptance.apply(newResult, tx);
					return msg;
				};
			}
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> newAddAcceptance;
			if (!forAddToo)
				newAddAcceptance = theAddAcceptability;
			else {
				BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> oldAddAcceptance = theAddAcceptability;
				if (oldAddAcceptance == null || enablement == null)
					newAddAcceptance = enablement;
				else {
					newAddAcceptance = (newResult, tx) -> {
						String msg = enablement.apply(newResult, tx);
						if (msg == null)
							msg = oldAddAcceptance.apply(newResult, tx);
						return msg;
					};
				}
			}
			return new SourceModifyingReverse<>(theModifier, theEnabled, newAcceptance, theCreator, newAddAcceptance);
		}

		@Override
		public SourceModifyingReverse<S, T> createWith(
			TriFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, Boolean, ? extends S> creator) {
			return new SourceModifyingReverse<>(theModifier, theEnabled, theAcceptability, creator, theAddAcceptability);
		}

		@Override
		public SourceModifyingReverse<S, T> rejectAddWith(
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> acceptance) {
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> oldAcceptance = theAddAcceptability;
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, String> newAcceptance;
			if (oldAcceptance == null || acceptance == null)
				newAcceptance = acceptance;
			else {
				newAcceptance = (newResult, tx) -> {
					String msg = acceptance.apply(newResult, tx);
					if (msg == null)
						msg = oldAcceptance.apply(newResult, tx);
					return msg;
				};
			}
			return new SourceModifyingReverse<>(theModifier, theEnabled, theAcceptability, theCreator, newAcceptance);
		}

		@Override
		public boolean isStateful() {
			return true;
		}

		@Override
		public String isEnabled(TransformationValues<S, T> transformValues) {
			if (theEnabled == null)
				return null;
			else
				return theEnabled.apply(transformValues);
		}

		@Override
		public ReverseQueryResult<S> reverse(T newValue, TransformationValues<S, T> transformValues, boolean add, boolean test) {
			if (!add) {
				if (theEnabled != null) {
					String msg = theEnabled.apply(transformValues);
					if (msg != null)
						return ReverseQueryResult.reject(msg);
				}
				if (theAcceptability != null) {
					String msg = theAcceptability.apply(newValue, transformValues);
					if (msg != null)
						return ReverseQueryResult.reject(msg);
				}
				if (!test)
					theModifier.accept(newValue, transformValues);
				// No source change
				return ReverseQueryResult.value(transformValues.getCurrentSource());
			} else if (theCreator == null)
				return ReverseQueryResult.reject(StdMsg.UNSUPPORTED_OPERATION);
			else {
				if (theAddAcceptability != null) {
					String msg = theAddAcceptability.apply(newValue, transformValues);
					if (msg != null)
						return ReverseQueryResult.reject(msg);
				}
				return ReverseQueryResult.value(theCreator.apply(newValue, transformValues, !test));
			}
		}

		@Override
		public int hashCode() {
			return Objects.hash(theModifier, theAcceptability);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof SourceReplacingReverse))
				return false;
			SourceModifyingReverse<?, ?> other = (SourceModifyingReverse<?, ?>) obj;
			return theModifier.equals(other.theModifier)//
				&& Objects.equals(theAcceptability, other.theAcceptability);
		}

		@Override
		public String toString() {
			return theModifier.toString();
		}
	}

	/**
	 * A builder to configure a {@link Transformation}
	 *
	 * @param <S> The type of the source value
	 * @param <T> The type of the result value
	 * @param <X> The sub-type of this builder
	 */
	public interface TransformationBuilder<S, T, X extends TransformationBuilder<S, T, X>> extends XformOptions {
		@Override
		X nullToNull(boolean nullToNull);

		@Override
		X cache(boolean cache);

		@Override
		X reEvalOnUpdate(boolean reEval);

		@Override
		X fireIfUnchanged(boolean fire);

		@Override
		X manyToOne(boolean manyToOne);

		@Override
		X oneToMany(boolean oneToMany);

		/**
		 * @param combination A function to provide result values given source and transformation data
		 * @return The transformation definition
		 */
		Transformation<S, T> build(BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> combination);

		/**
		 * @param <V> The type of the new value
		 * @param value The observable value to affect the result
		 * @return A new builder with options similar to this, but including the value in its definition
		 */
		<V> TransformationBuilder<S, T, ?> combineWith(ObservableValue<V> value);
	}

	/**
	 * A builder to configure a {@link Transformation.MaybeReversibleTransformation}
	 *
	 * @param <S> The type of the source value
	 * @param <T> The type of the result value
	 * @param <X> The sub-type of this builder
	 */
	public interface ReversibleTransformationBuilder<S, T, X extends ReversibleTransformationBuilder<S, T, X>>
	extends TransformationBuilder<S, T, X> {
		@Override
		<V> ReversibleTransformationBuilder<S, T, ?> combineWith(ObservableValue<V> value);

		@Override
		MaybeReversibleTransformation<S, T> build(
			BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> combination);
	}

	/**
	 * A precursor to build a {@link Transformation}
	 *
	 * @param <S> The type of the source value
	 * @param <T> The type of the result value
	 * @param <X> The sub-type of this builder
	 */
	public static class TransformationPrecursor<S, T, X extends TransformationPrecursor<S, T, X>>
	extends TransformationBuilderImpl<S, T, X> {
		/** Creates the precursor */
		public TransformationPrecursor() {
			super(null);
		}

		/**
		 * Creates a transformation
		 *
		 * @param map The function to create result values from source values
		 * @return The transformation
		 */
		public Transformation<S, T> map(Function<? super S, ? extends T> map) {
			return build(LambdaUtils.toBiFunction1(map));
		}

		/**
		 * Creates a transformation
		 *
		 * @param map The function to create result values from source and previous result values
		 * @return The transformation
		 */
		public Transformation<S, T> map(BiFunction<? super S, ? super T, ? extends T> map) {
			return build(LambdaUtils.printableBiFn((src, tx) -> {
				return map.apply(src, tx.getPreviousResult());
			}, map::toString, map));
		}

		@Override
		public <V> TransformationBuilder2<S, V, T, ?> combineWith(ObservableValue<V> value) {
			return new Transformation2<>(this, value);
		}

		@Override
		List<ObservableValue<?>> getArgs() {
			return Collections.emptyList();
		}
	}

	/**
	 * A precursor to build a {@link Transformation.MaybeReversibleTransformation}
	 *
	 * @param <S> The type of the source value
	 * @param <T> The type of the result value
	 * @param <X> The sub-type of this builder
	 */
	public static class ReversibleTransformationPrecursor<S, T, X extends ReversibleTransformationPrecursor<S, T, X>>
	extends TransformationPrecursor<S, T, X> implements ReversibleTransformationBuilder<S, T, X> {
		@Override
		public <V> ReversibleTransformationBuilder2<S, V, T, ?> combineWith(ObservableValue<V> value) {
			return new ReversibleTransformation2<>(this, value);
		}

		@Override
		public MaybeReversibleMapping<S, T> map(Function<? super S, ? extends T> map) {
			return (MaybeReversibleMapping<S, T>) super.map(map);
		}

		@Override
		public MaybeReversibleMapping<S, T> map(BiFunction<? super S, ? super T, ? extends T> map) {
			return (MaybeReversibleMapping<S, T>) super.map(map);
		}

		@Override
		public MaybeReversibleMapping<S, T> build(
			BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> combination) {
			return new MaybeReversibleMapping<>(this, getValues(), combination, Equivalence.DEFAULT);
		}
	}

	/**
	 * A {@link Transformation.MaybeReversibleTransformation} for a simple mapping operation with no external values
	 *
	 * @param <S> The type of the source value
	 * @param <T> The type of the result value
	 */
	public static class MaybeReversibleMapping<S, T> extends MaybeReversibleTransformation<S, T> {
		MaybeReversibleMapping(XformOptions source, Map<ObservableValue<?>, Integer> args,
			BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> combination,
			Equivalence<? super T> resultEquivalence) {
			super(source, args, combination, resultEquivalence);
		}

		@Override
		public MaybeReversibleMapping<S, T> withEquivalence(Equivalence<? super T> resultEquivalence) {
			return new MaybeReversibleMapping<>(toOptions(), _getArgs(), getCombination(), resultEquivalence);
		}

		/**
		 * Supports reverse by supplying new source values from result values for set or addition operations
		 *
		 * @param reverse The function to create new source values from result values
		 * @return The reversible transformation
		 */
		public ReversibleTransformation<S, T> withReverse(Function<? super T, ? extends S> reverse) {
			return replaceSource(reverse, null);
		}

		/**
		 * Supports reverse by supplying new source values for set or addition operations
		 *
		 * @param reverse The function to create source values from result and environment values
		 * @param configure Optional function to further configure the reverse operation
		 * @return The reversible transformation
		 */
		public ReversibleTransformation<S, T> replaceMappingSourceWith(
			BiFunction<? super T, ? super TransformationValues<? extends S, ? extends T>, ? extends S> reverse,
			Function<? super MappingSourceReplacingReverse<S, T>, ? extends SourceReplacingReverse<S, T>> configure) {
			TriFunction<T, TransformationValues<? extends S, ? extends T>, Boolean, ? extends S> creator = LambdaUtils
				.toTriFunction1And2(reverse);
			MappingSourceReplacingReverse<S, T> srr = new MappingSourceReplacingReverse<>(this, reverse, null, null, creator, null, true,
				false);
			SourceReplacingReverse<S, T> srr2 = srr;
			if (configure != null)
				srr2 = configure.apply(srr);
			return withReverse(srr2);
		}

		/**
		 * Supports reverse by supplying new source values from result values for set or addition operations
		 *
		 * @param reverse The function to create new source values from result values
		 * @param configure Optional function to configure the reverse operation
		 * @return The reversible transformation
		 */
		public ReversibleTransformation<S, T> replaceSource(Function<? super T, ? extends S> reverse, //
			Function<? super MappingSourceReplacingReverse<S, T>, ? extends SourceReplacingReverse<S, T>> configure) {
			BiFunction<T, TransformationValues<? extends S, ? extends T>, S> reverse2 = LambdaUtils.toBiFunction1(reverse);
			TriFunction<T, TransformationValues<? extends S, ? extends T>, Boolean, S> creator = LambdaUtils.toTriFunction1(reverse);
			MappingSourceReplacingReverse<S, T> srr = new MappingSourceReplacingReverse<>(this, reverse2, null, null, creator, null, false,
				false);
			SourceReplacingReverse<S, T> srr2 = srr;
			if (configure != null)
				srr2 = configure.apply(srr);
			return withReverse(srr2);
		}

		/**
		 * Supports reverse by modifying existing source values with result values for set operations. Additions will not be supported.
		 *
		 * @param modifier The function to modify source values with result values
		 * @return The reversible transformation
		 */
		public ReversibleTransformation<S, T> modifySource(BiConsumer<? super S, ? super T> modifier) {
			return modifySource(modifier, null);
		}

		/**
		 * Supports reverse by modifying existing source values with result values for set operations. Additions will not be supported
		 * unless configured with {@link Transformation.SourceModifyingReverse#createWith(TriFunction)}.
		 *
		 * @param modifier The function to modify source values with result values
		 * @param configure Optional function to configure the reverse operation
		 * @return The reversible transformation
		 */
		public ReversibleTransformation<S, T> modifySource(BiConsumer<? super S, ? super T> modifier, //
			Function<SourceModifyingReverse<S, T>, SourceModifyingReverse<S, T>> configure) {
			BiConsumer<? super T, TransformationValues<? extends S, ? extends T>> modifier2 = LambdaUtils
				.printableBiConsumer((result, tx) -> {
					S source = tx.getCurrentSource();
					modifier.accept(source, result);
				}, modifier::toString, modifier);
			SourceModifyingReverse<S, T> srr = new SourceModifyingReverse<>(modifier2,
				LambdaUtils.printableFn(tx -> tx.getCurrentSource() == null ? "No source value" : null, "Non-null source", null), null,
				null, null);
			if (configure != null)
				srr = configure.apply(srr);
			return withReverse(srr);
		}
	}

	/**
	 * A {@link Transformation.TransformationBuilder} containing one external argument value
	 *
	 * @param <S> The type of the source value
	 * @param <V> The type of the external value
	 * @param <T> The type of the result value
	 * @param <X> The sub-type of this builder
	 */
	public interface TransformationBuilder2<S, V, T, X extends TransformationBuilder2<S, V, T, X>> extends TransformationBuilder<S, T, X> {
		/** @return The external argument */
		ObservableValue<V> getArg2();

		/**
		 * @param combination The function combining the source and argument values
		 * @return The combination definition
		 */
		default Transformation<S, T> combine(BiFunction<? super S, ? super V, ? extends T> combination) {
			return build(LambdaUtils.printableBiFn((src, tx) -> {
				return combination.apply(src, tx.get(getArg2()));
			}, combination::toString, combination));
		}

		@Override
		<V2> TransformationBuilder3<S, V, V2, T, ?> combineWith(ObservableValue<V2> value);
	}

	/**
	 * A {@link Transformation.ReversibleTransformationBuilder} containing one external argument value
	 *
	 * @param <S> The type of the source value
	 * @param <V> The type of the external value
	 * @param <T> The type of the result value
	 * @param <X> The sub-type of this builder
	 */
	public interface ReversibleTransformationBuilder2<S, V, T, X extends ReversibleTransformationBuilder2<S, V, T, X>>
	extends TransformationBuilder2<S, V, T, X>, ReversibleTransformationBuilder<S, T, X> {
		@Override
		<V2> ReversibleTransformationBuilder3<S, V, V2, T, ?> combineWith(ObservableValue<V2> value);

		@Override
		default MaybeReversibleTransformation2<S, V, T> combine(BiFunction<? super S, ? super V, ? extends T> combination) {
			return (MaybeReversibleTransformation2<S, V, T>) TransformationBuilder2.super.combine(combination);
		}

		@Override
		MaybeReversibleTransformation2<S, V, T> build(
			BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> combination);
	}

	/**
	 * A {@link Transformation.MaybeReversibleTransformation} containing one external argument value
	 *
	 * @param <S> The type of the source value
	 * @param <V> The type of the external value
	 * @param <T> The type of the result value
	 */
	public static class MaybeReversibleTransformation2<S, V, T> extends MaybeReversibleTransformation<S, T> {
		private final ObservableValue<V> theArg2;

		MaybeReversibleTransformation2(XformOptions source, Map<ObservableValue<?>, Integer> args,
			BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> combination,
			Equivalence<? super T> resultEquivalence) {
			super(source, args, combination, resultEquivalence);
			theArg2 = (ObservableValue<V>) getArg(0);
		}

		ObservableValue<V> getArg2() {
			return theArg2;
		}

		@Override
		public MaybeReversibleTransformation2<S, V, T> withEquivalence(Equivalence<? super T> resultEquivalence) {
			return new MaybeReversibleTransformation2<>(toOptions(), _getArgs(), getCombination(), resultEquivalence);
		}

		/**
		 * Supports reverse by supplying new source values from result plus argument values for set or addition operations
		 *
		 * @param reverse The function to create new source values from result and argument values
		 * @return The reversible transformation
		 */
		public ReversibleTransformation<S, T> withReverse(BiFunction<? super T, ? super V, ? extends S> reverse) {
			return replaceSource(reverse, null);
		}

		/**
		 * Supports reverse by supplying new source values from result plus argument values for set or addition operations
		 *
		 * @param reverse The function to create new source values from result and argument values
		 * @param configure Optional function to configure the reverse operation
		 * @return The reversible transformation
		 */
		public ReversibleTransformation<S, T> replaceSource(BiFunction<? super T, ? super V, ? extends S> reverse, //
			Function<SourceReplacingReverse<S, T>, SourceReplacingReverse<S, T>> configure) {
			BiFunction<T, TransformationValues<? extends S, ? extends T>, S> reverse2 = LambdaUtils.printableBiFn((v, tx) -> {
				return reverse.apply(v, tx.get(getArg2()));
			}, reverse::toString, reverse);
			TriFunction<T, TransformationValues<? extends S, ? extends T>, Boolean, S> creator = LambdaUtils
				.printableTriFn((v, tx, create) -> {
					return reverse.apply(v, tx.get(getArg2()));
				}, reverse::toString, reverse);
			SourceReplacingReverse<S, T> srr = new SourceReplacingReverse<>(this, reverse2, null, null, creator, null, false, false);
			if (configure != null)
				srr = configure.apply(srr);
			return withReverse(srr);
		}

		/**
		 * Supports reverse by modifying existing source values with result plus argument values for set operations. Additions will not be
		 * supported.
		 *
		 * @param modifier The function to modify source values with result and argument values
		 * @return The reversible transformation
		 */
		public ReversibleTransformation<S, T> modifySource(TriConsumer<? super S, ? super V, ? super T> modifier) {
			return modifySource(modifier, null);
		}

		/**
		 * Supports reverse by modifying existing source values with result plus argument values for set operations. Additions will not be
		 * supported unless configured with {@link Transformation.SourceModifyingReverse#createWith(TriFunction)}.
		 *
		 * @param modifier The function to modify source values with result and argument values
		 * @param configure Optional function to configure the reverse operation
		 * @return The reversible transformation
		 */
		public ReversibleTransformation<S, T> modifySource(TriConsumer<? super S, ? super V, ? super T> modifier, //
			Function<SourceModifyingReverse<S, T>, SourceModifyingReverse<S, T>> configure) {
			BiConsumer<T, TransformationValues<? extends S, ? extends T>> reverse2 = LambdaUtils.printableBiConsumer((v, tx) -> {
				modifier.accept(tx.getCurrentSource(), tx.get(getArg2()), v);
			}, modifier::toString, modifier);
			SourceModifyingReverse<S, T> srr = new SourceModifyingReverse<>(reverse2, //
				LambdaUtils.printableFn(tx -> tx.getCurrentSource() == null ? "No source value" : null, "Non-null source", null), //
				null, null, null);
			if (configure != null)
				srr = configure.apply(srr);
			return withReverse(srr);
		}
	}

	/**
	 * A {@link Transformation.TransformationBuilder} containing two external argument values
	 *
	 * @param <S> The type of the source value
	 * @param <V1> The type of the first external value
	 * @param <V2> The type of the second external value
	 * @param <T> The type of the result value
	 * @param <X> The sub-type of this builder
	 */
	public interface TransformationBuilder3<S, V1, V2, T, X extends TransformationBuilder3<S, V1, V2, T, X>>
	extends TransformationBuilder<S, T, X> {
		/** @return The first external argument */
		ObservableValue<V1> getArg2();

		/** @return The second external argument */
		ObservableValue<V2> getArg3();

		/**
		 * @param combination The function combining the source and argument values
		 * @return The combination definition
		 */
		default Transformation<S, T> combine(TriFunction<? super S, ? super V1, ? super V2, ? extends T> combination) {
			return build(LambdaUtils.printableBiFn((src, tx) -> {
				return combination.apply(src, tx.get(getArg2()), tx.get(getArg3()));
			}, combination::toString, combination));
		}
	}

	/**
	 * A {@link Transformation.ReversibleTransformationBuilder} containing two external argument values
	 *
	 * @param <S> The type of the source value
	 * @param <V1> The type of the first external value
	 * @param <V2> The type of the second external value
	 * @param <T> The type of the result value
	 * @param <X> The sub-type of this builder
	 */
	public interface ReversibleTransformationBuilder3<S, V1, V2, T, X extends ReversibleTransformationBuilder3<S, V1, V2, T, X>>
	extends TransformationBuilder3<S, V1, V2, T, X>, ReversibleTransformationBuilder<S, T, X> {
		@Override
		default MaybeReversibleTransformation3<S, V1, V2, T> combine(
			TriFunction<? super S, ? super V1, ? super V2, ? extends T> combination) {
			return (MaybeReversibleTransformation3<S, V1, V2, T>) TransformationBuilder3.super.combine(combination);
		}

		@Override
		MaybeReversibleTransformation3<S, V1, V2, T> build(
			BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> combination);
	}

	/**
	 * A {@link Transformation.MaybeReversibleTransformation} containing two external argument values
	 *
	 * @param <S> The type of the source value
	 * @param <V1> The type of the first external value
	 * @param <V2> The type of the second external value
	 * @param <T> The type of the result value
	 */
	public static class MaybeReversibleTransformation3<S, V1, V2, T> extends MaybeReversibleTransformation<S, T> {
		private final ObservableValue<V1> theArg2;
		private final ObservableValue<V2> theArg3;

		MaybeReversibleTransformation3(XformOptions source, Map<ObservableValue<?>, Integer> args,
			BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> combination,
			Equivalence<? super T> resultEquivalence) {
			super(source, args, combination, resultEquivalence);
			theArg2 = (ObservableValue<V1>) getArg(0);
			theArg3 = (ObservableValue<V2>) getArg(1);
		}

		ObservableValue<V1> getArg2() {
			return theArg2;
		}

		ObservableValue<V2> getArg3() {
			return theArg3;
		}

		@Override
		public MaybeReversibleTransformation3<S, V1, V2, T> withEquivalence(Equivalence<? super T> resultEquivalence) {
			return new MaybeReversibleTransformation3<>(toOptions(), _getArgs(), getCombination(), resultEquivalence);
		}

		/**
		 * Supports reverse (sets and additions) by creating source values from result and current argument values
		 *
		 * @param reverse The function to create source values from result and argument values
		 * @return The reversible transformation
		 */
		public ReversibleTransformation<S, T> withReverse(TriFunction<? super T, ? super V1, ? super V2, ? extends S> reverse) {
			return replaceSource(reverse, null);
		}

		/**
		 * Supports reverse (sets and additions) by creating source values from result and current argument values
		 *
		 * @param reverse The function to create source values from result and argument values
		 * @param configure Optional function to configure the reverse operation further
		 * @return The reversible transformation
		 */
		public ReversibleTransformation<S, T> replaceSource(TriFunction<? super T, ? super V1, ? super V2, ? extends S> reverse, //
			Function<SourceReplacingReverse<S, T>, SourceReplacingReverse<S, T>> configure) {
			BiFunction<T, TransformationValues<? extends S, ? extends T>, S> reverse2 = LambdaUtils.printableBiFn((v, tx) -> {
				return reverse.apply(v, tx.get(getArg2()), tx.get(getArg3()));
			}, reverse::toString, reverse);
			TriFunction<T, TransformationValues<? extends S, ? extends T>, Boolean, S> creator = LambdaUtils
				.printableTriFn((v, tx, create) -> {
					return reverse.apply(v, tx.get(getArg2()), tx.get(getArg3()));
				}, reverse::toString, reverse);
			SourceReplacingReverse<S, T> srr = new SourceReplacingReverse<>(this, reverse2, null, null, creator, null, false, false);
			if (configure != null)
				srr = configure.apply(srr);
			return withReverse(srr);
		}
	}

	/** Represents the state of the set of external arguments to a transformation */
	public interface TransformationState {
		/**
		 * @param <V> The type of the argument
		 * @param argIndex The index of the argument in the {@link Transformation}
		 * @return The value of the argument
		 * @see Transformation#getArgIndex(ObservableValue)
		 */
		<V> V get(int argIndex);
	}

	/**
	 * Represents a single, persistent source value->result value mapping
	 *
	 * @param <S> The source type of the transformation
	 * @param <T> The result type of the transformation
	 */
	public interface TransformedElement<S, T> {
		/** @return The current source value of this element */
		S getSourceValue();

		/**
		 * @param state The transformation state to get the value for
		 * @return The result value of this element given the state
		 */
		T getCurrentValue(TransformationState state);

		/**
		 * @param source The source value for this element
		 * @param state The transformation state to get the value for
		 * @return The result of this element, given the source and state
		 */
		T getCurrentValue(S source, TransformationState state);

		/**
		 * Notifies this element that the source value has changed
		 *
		 * @param oldSource The previous value of the source, if known
		 * @param newSource The new value of the source
		 * @param state The transformation state of the environment
		 * @return A tuple containing the old and new values of this element, or null if the operation should not result in a change event
		 */
		BiTuple<T, T> sourceChanged(S oldSource, S newSource, TransformationState state);

		/**
		 * Notifies this element that the transformation state has changed
		 *
		 * @param oldState The previous state
		 * @param newState The new state
		 * @return A tuple containing the old and new values of this element, valid for the old and new transformation states, or null if
		 *         the operation should not result in a change event
		 */
		BiTuple<T, T> transformationStateChanged(TransformationState oldState, TransformationState newState);

		/**
		 * @param state The transformation state to use to make the determination
		 * @return Null if there may be values for which {@link #set(Object, TransformationState, boolean)} succeeds, or a reason why this
		 *         element cannot currently be modified
		 */
		String isEnabled(TransformationState state);

		/**
		 * @param value The value to set
		 * @param state The transformation state to use to make the determination
		 * @param test Whether the operation is just a test or if the set operation should actually be done
		 * @return Null if the given value can be {@link #set(Object, TransformationState, boolean) set} for this element, or a reason why
		 *         it cannot
		 */
		ReverseQueryResult<S> set(T value, TransformationState state, boolean test);
	}

	/**
	 * Assists observable structures in driving result structures based on source structures
	 *
	 * @param <S> The source type of the transformation
	 * @param <T> The result type of the transformation
	 */
	public interface Engine<S, T> extends ObservableValue<TransformationState> {
		/** @return The transformation definition of this engine */
		Transformation<S, T> getTransformation();

		/**
		 * Maps a source value to a result value with no context
		 *
		 * @param source The source value to map
		 * @param state The transformation state of the environment
		 * @return The result value
		 */
		T map(S source, TransformationState state);

		/**
		 * Reverses a result value into a source value with no context, if possible
		 *
		 * @param value The result value to reverse
		 * @param forAdd Whether the desired operation is an addition (as opposed to a set operation)
		 * @param test Whether the operation is just a test. Some reverse operations perform differently for tests compared to true actions.
		 * @return The result of the operation. If successful, the {@link Transformation.ReverseQueryResult#getError() error} will be null
		 *         and the {@link Transformation.ReverseQueryResult#getReversed() reversed value} will be the new value for the source.
		 */
		ReverseQueryResult<S> reverse(T value, boolean forAdd, boolean test);

		/**
		 * Creates an element representing a single source value to result value
		 *
		 * @param source Supplies the source value for the element
		 * @return The element
		 */
		TransformedElement<S, T> createElement(Supplier<S> source);

		/**
		 * Attempts to set the values of multiple transformed elements in a single operation
		 *
		 * @param elements The elements whose values to set
		 * @param newValue The new result value for the element
		 * @return A list of source values to set for the source elements of the given elements. Either:
		 *         <ul>
		 *         <li>A list with a single source value, indicating that the source elements can set to a single value in a similar
		 *         multi-set operation, or</li>
		 *         <li>A list with a source value for each element in the argument collection, in the same order</li>
		 *         </ul>
		 * @throws UnsupportedOperationException If the operation is not supported for any element in the given collection
		 * @throws IllegalArgumentException If the value is not valid for any element in the given collection
		 */
		List<S> setElementsValue(Collection<TransformedElement<S, T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException;
	}

	static abstract class TransformationBuilderImpl<S, T, X extends TransformationBuilder<S, T, X>> extends XformOptions.SimpleXformOptions
	implements TransformationBuilder<S, T, X> {
		TransformationBuilderImpl(XformOptions options) {
			super(options);
		}

		@Override
		public X nullToNull(boolean nullToNull) {
			super.nullToNull(nullToNull);
			return (X) this;
		}

		@Override
		public X cache(boolean cache) {
			super.cache(cache);
			return (X) this;
		}

		@Override
		public X reEvalOnUpdate(boolean reEval) {
			super.reEvalOnUpdate(reEval);
			return (X) this;
		}

		@Override
		public X fireIfUnchanged(boolean fire) {
			super.fireIfUnchanged(fire);
			return (X) this;
		}

		@Override
		public X manyToOne(boolean manyToOne) {
			super.manyToOne(manyToOne);
			return (X) this;
		}

		@Override
		public X oneToMany(boolean oneToMany) {
			super.oneToMany(oneToMany);
			return (X) this;
		}

		abstract List<ObservableValue<?>> getArgs();

		protected Map<ObservableValue<?>, Integer> getValues() {
			List<ObservableValue<?>> args = getArgs();
			if (args.isEmpty())
				return Collections.emptyMap();
			else if (args.size() == 1)
				return Collections.singletonMap(args.get(0), 0);
			Map<ObservableValue<?>, Integer> values = new LinkedHashMap<>(args.size() * 3 / 2);
			for (ObservableValue<?> value : args) {
				values.put(value, values.size());
			}
			return Collections.unmodifiableMap(values);
		}

		@Override
		public Transformation<S, T> build(
			BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> combination) {
			return new Transformation<>(this, getValues(), combination, Equivalence.DEFAULT);
		}
	}

	static class Transformation2<S, V, T, X extends TransformationBuilder2<S, V, T, X>> extends TransformationBuilderImpl<S, T, X>
	implements TransformationBuilder2<S, V, T, X> {
		private final ObservableValue<V> theArg2;

		Transformation2(TransformationBuilder<S, T, ?> source, ObservableValue<V> arg2) {
			super(source);
			theArg2 = arg2;
		}

		@Override
		public ObservableValue<V> getArg2() {
			return theArg2;
		}

		@Override
		List<ObservableValue<?>> getArgs() {
			return Arrays.asList(theArg2);
		}

		@Override
		public <V2> TransformationBuilder3<S, V, V2, T, ?> combineWith(ObservableValue<V2> value) {
			return new Transformation3<>(this, value);
		}
	}

	static class Transformation3<S, V1, V2, T, X extends TransformationBuilder3<S, V1, V2, T, X>> extends TransformationBuilderImpl<S, T, X>
	implements TransformationBuilder3<S, V1, V2, T, X> {
		private final ObservableValue<V1> theArg2;
		private final ObservableValue<V2> theArg3;

		public Transformation3(TransformationBuilder2<S, V1, T, ?> source, ObservableValue<V2> arg3) {
			super(source);
			theArg2 = source.getArg2();
			theArg3 = arg3;
		}

		@Override
		public ObservableValue<V1> getArg2() {
			return theArg2;
		}

		@Override
		public ObservableValue<V2> getArg3() {
			return theArg3;
		}

		@Override
		List<ObservableValue<?>> getArgs() {
			return Arrays.asList(theArg2, theArg3);
		}

		@Override
		public <V> TransformationBuilder<S, T, ?> combineWith(ObservableValue<V> value) {
			return new TransformationN<>(this, value);
		}
	}

	static class TransformationN<S, T, X extends TransformationBuilder<S, T, X>> extends TransformationBuilderImpl<S, T, X> {
		private List<ObservableValue<?>> theArgs;

		TransformationN(TransformationBuilderImpl<S, T, ?> source, ObservableValue<?> arg) {
			super(source);
			theArgs = new ArrayList<>(source.getArgs().size() + 1);
			theArgs.addAll(source.getArgs());
			theArgs.add(arg);
		}

		@Override
		List<ObservableValue<?>> getArgs() {
			return theArgs;
		}

		@Override
		public <V> TransformationBuilder<S, T, ?> combineWith(ObservableValue<V> value) {
			return new TransformationN<>(this, value);
		}
	}

	static class ReversibleTransformation2<S, V, T, X extends ReversibleTransformationBuilder2<S, V, T, X>>
	extends Transformation2<S, V, T, X> implements ReversibleTransformationBuilder2<S, V, T, X> {
		ReversibleTransformation2(ReversibleTransformationPrecursor<S, T, ?> source, ObservableValue<V> arg2) {
			super(source, arg2);
		}

		@Override
		public <V2> ReversibleTransformationBuilder3<S, V, V2, T, ?> combineWith(ObservableValue<V2> value) {
			return new ReversibleTransformation3<>(this, value);
		}

		@Override
		public MaybeReversibleTransformation2<S, V, T> build(
			BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> combination) {
			return new MaybeReversibleTransformation2<>(this, getValues(), combination, Equivalence.DEFAULT);
		}
	}

	static class ReversibleTransformation3<S, V1, V2, T, X extends ReversibleTransformationBuilder3<S, V1, V2, T, X>>
	extends Transformation3<S, V1, V2, T, X> implements ReversibleTransformationBuilder3<S, V1, V2, T, X> {
		ReversibleTransformation3(ReversibleTransformationBuilder2<S, V1, T, ?> source, ObservableValue<V2> arg3) {
			super(source, arg3);
		}

		@Override
		public <V> ReversibleTransformationBuilder<S, T, ?> combineWith(ObservableValue<V> value) {
			return new ReversibleTransformationN<>(this, value);
		}

		@Override
		public MaybeReversibleTransformation3<S, V1, V2, T> build(
			BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> combination) {
			return new MaybeReversibleTransformation3<>(this, getValues(), combination, Equivalence.DEFAULT);
		}
	}

	static class ReversibleTransformationN<S, T, X extends ReversibleTransformationBuilder<S, T, X>> extends TransformationN<S, T, X>
	implements ReversibleTransformationBuilder<S, T, X> {
		ReversibleTransformationN(TransformationBuilderImpl<S, T, ?> source, ObservableValue<?> arg) {
			super(source, arg);
		}

		@Override
		public <V> ReversibleTransformationBuilder<S, T, ?> combineWith(ObservableValue<V> value) {
			return new ReversibleTransformationN<>(this, value);
		}

		@Override
		public MaybeReversibleTransformation<S, T> build(
			BiFunction<? super S, ? super TransformationValues<? extends S, ? extends T>, ? extends T> combination) {
			return new MaybeReversibleTransformation<>(this, getValues(), combination, Equivalence.DEFAULT);
		}
	}

	static class StampedArgValues implements TransformationState {
		final Object[] argValues;
		final long stamp;

		StampedArgValues(Object[] argValues, long stamp) {
			this.argValues = argValues;
			this.stamp = stamp;
		}

		@Override
		public <V> V get(int argIndex) {
			return (V) argValues[argIndex];
		}

		@Override
		public String toString() {
			return Arrays.toString(argValues);
		}
	}

	static class EngineImpl<S, T> implements Engine<S, T> {
		final Transformation<S, T> theTransformation;
		private volatile StampedArgValues theCachedValues;
		private final ListenerList<Observer<? super ObservableValueEvent<TransformationState>>> theChanges;
		final Equivalence<? super S> theSourceEquivalence;

		EngineImpl(Transformation<S, T> transformation, Equivalence<? super S> sourceEquivalence) {
			theTransformation = transformation;
			theSourceEquivalence = sourceEquivalence;
			if (theTransformation.getArgs().isEmpty()) {
				theChanges = null;
				theCachedValues = new StampedArgValues(new Object[0], 0);
			} else {
				theChanges = ListenerList.build().withInUse(new ListenerList.InUseListener() {
					private final Subscription[] theArgSubs = new Subscription[theTransformation.getArgs().size()];

					@Override
					public void inUseChanged(boolean inUse) {
						if (inUse) {
							try (Transaction t = lock()) {
								ObservableValue<?>[] args = theTransformation.getArgs().toArray(//
									new ObservableValue[theTransformation.getArgs().size()]);
								long[] stamps = new long[args.length];
								long stamp = getStamp();
								StampedArgValues cached = theCachedValues;
								boolean[] allInitialized = new boolean[1];
								Object[] values = cached == null ? new Object[args.length] : cached.argValues.clone();
								int i = 0;
								for (ObservableValue<?> arg : args) {
									stamps[i] = arg.getStamp();
									int index = i;
									Lockable[] otherLocks = new Lockable[args.length - 1];
									int j = 0;
									for (ObservableValue<?> arg2 : args) {
										if (j < i)
											otherLocks[j] = arg2;
										else if (j > i)
											otherLocks[j - 1] = arg2;
										j++;
									}
									if (cached == null || stamp != cached.stamp) {
										boolean[] initialized = new boolean[1];
										arg.changes().act(LambdaUtils.printableConsumer(evt -> {
											if (evt.isInitial()) {
												if (initialized[0])
													throw new IllegalStateException("Multiple initialization events from " + arg);
												initialized[0] = true;
												values[index] = evt.getNewValue();
											} else
												argChanged(index, arg, evt, values, stamps, allInitialized[0], otherLocks);
										}, () -> arg + " changes for " + transformation, null));
										if (!(initialized[0]))
											throw new IllegalStateException("Value " + args[i] + " did not fire an initial event");
										theCachedValues = new StampedArgValues(values.clone(), stamp);
									} else {
										arg.noInitChanges().act(evt -> {
											argChanged(index, arg, evt, values, stamps, allInitialized[0], otherLocks);
										});
									}
									i++;
								}
								allInitialized[0] = true;
							}
						} else {
							Subscription.forAll(theArgSubs).unsubscribe();
							Arrays.fill(theArgSubs, null);
						}
					}

					private void argChanged(int argIndex, ObservableValue<?> arg, ObservableValueEvent<?> evt, Object[] argValues,
						long[] argStamps, boolean initialized, Lockable[] otherLocks) {
						try (Transaction t2 = Lockable.lockAll(otherLocks)) {
							argValues[argIndex] = evt.getNewValue();
							argStamps[argIndex] = arg.getStamp();
							Object[] valueCopy = argValues.clone();
							long newStamp = Stamped.compositeStamp(argStamps);
							StampedArgValues newState = new StampedArgValues(valueCopy, newStamp);
							if (initialized)
								newState(newState, evt);
							else
								theCachedValues = newState;
						}
					}

					private void newState(StampedArgValues newState, Object cause) {
						StampedArgValues oldState = theCachedValues;
						theCachedValues = newState;
						ObservableValueEvent<TransformationState> evt = EngineImpl.this.createChangeEvent(oldState, newState, cause);
						try (Transaction t = evt.use()) {
							theChanges.forEach(//
								l -> l.onNext(evt));
						}
					}
				}).build();
			}
		}

		@Override
		public Transformation<S, T> getTransformation() {
			return theTransformation;
		}

		@Override
		public TypeToken<TransformationState> getType() {
			return TypeTokens.get().of(TransformationState.class);
		}

		@Override
		public Object getIdentity() {
			return theTransformation.getIdentity();
		}

		@Override
		public boolean isLockSupported() {
			for (ObservableValue<?> arg : theTransformation.getArgs())
				if (arg.isLockSupported())
					return true;
			return false;
		}

		@Override
		public Transaction lock() {
			return Lockable.lockAll(theTransformation.getArgs());
		}

		@Override
		public Transaction tryLock() {
			return Lockable.tryLockAll(theTransformation.getArgs());
		}

		@Override
		public CoreId getCoreId() {
			return Lockable.getCoreId(theTransformation.getArgs());
		}

		@Override
		public long getStamp() {
			return Stamped.compositeStamp(theTransformation.getArgs());
		}

		@Override
		public TransformationState get() {
			StampedArgValues cached = theCachedValues;
			if (theChanges != null && theChanges.isEmpty()) {
				long stamp = getStamp();
				if (cached == null || cached.stamp != stamp) {
					try (Transaction t = lock()) {
						stamp = getStamp();
						cached = theCachedValues; // Re-check cached values in case some other thread got us up-to-date
						if (cached == null || cached.stamp != stamp) {
							Object[] args = new Object[theTransformation.getArgs().size()];
							int i = 0;
							for (ObservableValue<?> arg : theTransformation.getArgs())
								args[i++] = arg.get();
							theCachedValues = cached = new StampedArgValues(args, stamp);
						}
					}
				}
			}
			return cached;
		}

		@Override
		public Observable<ObservableValueEvent<TransformationState>> noInitChanges() {
			class Changes extends AbstractIdentifiable implements Observable<ObservableValueEvent<TransformationState>> {
				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(EngineImpl.this.getIdentity(), "noInitChanges");
				}

				@Override
				public ThreadConstraint getThreadConstraint() {
					return ThreadConstrained.getThreadConstraint(theTransformation.getArgs());
				}

				@Override
				public boolean isEventing() {
					return theChanges.isFiring();
				}

				@Override
				public boolean isSafe() {
					return EngineImpl.this.isLockSupported();
				}

				@Override
				public Transaction lock() {
					return EngineImpl.this.lock();
				}

				@Override
				public Transaction tryLock() {
					return EngineImpl.this.lock();
				}

				@Override
				public CoreId getCoreId() {
					return EngineImpl.this.getCoreId();
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<TransformationState>> observer) {
					if (theChanges == null)
						return Subscription.NONE;
					return theChanges.add(observer, true)::run;
				}
			}
			return new Changes();
		}

		@Override
		public TransformedElement<S, T> createElement(Supplier<S> source) {
			if (theTransformation.isCached())
				return new CachedTransformedElement(source.get(), get());
			else
				return new DynamicTransformedElement(source);
		}

		@Override
		public T map(S source, TransformationState state) {
			return transform(false, null, source, null, state, false);
		}

		@Override
		public ReverseQueryResult<S> reverse(T value, boolean forAdd, boolean test) {
			if (!(theTransformation instanceof ReversibleTransformation))
				return ReverseQueryResult.reject(StdMsg.UNSUPPORTED_OPERATION);
			ReversibleTransformation<S, T> reversible = (ReversibleTransformation<S, T>) theTransformation;
			TransformationState state = get();
			return reverse(reversible.getReverse(), value, null, null, state, forAdd, test);
		}

		T transform(boolean sourceChange, Supplier<S> oldSource, S newSource, Function<S, T> oldResult, TransformationState state,
			boolean stateChanged) {
			if (state == null)
				throw new NullPointerException();
			if (newSource == null && theTransformation.isNullToNull())
				return null;
			TransformationValuesImpl tv = new TransformationValuesImpl(oldSource, oldResult, state);
			if (!stateChanged && !theTransformation.isReEvalOnUpdate() && oldResult != null
				&& theSourceEquivalence.elementEquals(tv.getCurrentSource(), newSource))
				return tv.getPreviousResult();
			try {
				return theTransformation.getCombination().apply(newSource, tv);
			} catch (RuntimeException e) {
				e.printStackTrace();
				return null; // This will probably cause problems, but at least the works aren't gummed up
			}
		}

		ReverseQueryResult<S> reverse(TransformReverse<S, T> reverse, T value, Supplier<S> source, Function<S, T> previousResult,
			TransformationState state, boolean forAdd, boolean test) {
			TransformationValuesImpl tv = new TransformationValuesImpl(source, previousResult, state);
			if (previousResult != null && theTransformation.equivalence().elementEquals(tv.getPreviousResult(), value))
				return ReverseQueryResult.value(tv.getCurrentSource());
			return reverse.reverse(value, tv, forAdd, test);
		}

		@Override
		public List<S> setElementsValue(Collection<TransformedElement<S, T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			ReversibleTransformation<S, T> reversible = null;
			if (theTransformation instanceof ReversibleTransformation)
				reversible = (ReversibleTransformation<S, T>) theTransformation;
			TransformReverse<S, T> reverse = reversible == null ? null : reversible.getReverse();
			if (reverse != null && reverse.isStateful()) {
				// Since the reversal depends on the previous value of each individual element here, we can't really do anything in bulk
				// Don't perform the operation on the same parent value twice, even if it exists in multiple elements
				Map<S, S> parentValues = new IdentityHashMap<>();
				List<S> sourceValues = new ArrayList<>(elements.size());
				TransformationState state = get();
				boolean cached = theTransformation.isCached();
				for (TransformedElement<S, T> el : elements) {
					S parentValue = el.getSourceValue();
					S newSourceValue = parentValues.computeIfAbsent(parentValue, pv -> {
						return reverse(reverse, newValue, //
							() -> pv, cached ? src -> el.getCurrentValue(src, state) : null, state, false, false).getReversed();
					});
					sourceValues.add(newSourceValue);
				}
				return sourceValues;
			}
			// See if these are all updates. If they are we can take some shortcuts.
			S oldSource = null;
			boolean first = true, allUpdates = true, allIdenticalUpdates = true;
			TransformationState state = get();
			for (TransformedElement<S, T> el : elements) {
				boolean elementUpdate = theTransformation.equivalence().elementEquals(el.getCurrentValue(state), newValue);
				allUpdates &= elementUpdate;
				allIdenticalUpdates &= allUpdates;
				if (!allUpdates)
					break;
				if (allIdenticalUpdates) {
					S elOldValue = el.getSourceValue();
					if (first) {
						oldSource = elOldValue;
						first = false;
					} else
						allIdenticalUpdates &= theSourceEquivalence.elementEquals(oldSource, elOldValue);
				}
			}
			if (allIdenticalUpdates) {
				return Arrays.asList(oldSource);
			} else if (allUpdates) {
				List<S> sourceValues = new ArrayList<>(elements.size());
				for (TransformedElement<S, T> el : elements)
					sourceValues.add(el.getSourceValue());
				return sourceValues;
			}
			if (reverse != null) {
				// We already know the reverse is not stateful from the first block,
				// so the new value will map to the same source value for all elements
				S reversed = reverse(reverse, newValue, null, null, state, false, false).getReversed();
				return Arrays.asList(reversed);
			} else
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		class TransformationValuesImpl implements TransformationValues<S, T> {
			private final Supplier<S> theSource;
			private final Function<S, T> theOldResult;
			private final TransformationState theState;

			private boolean hasOldResult;
			private T cachedOldResult;

			boolean hasSource;
			S cachedSource;

			TransformationValuesImpl(Supplier<S> source, Function<S, T> oldResult, TransformationState state) {
				theSource = source;
				this.theOldResult = oldResult;
				theState = state;
			}

			@Override
			public boolean isSourceChange() {
				return false;
			}

			@Override
			public S getCurrentSource() {
				if (theSource == null)
					return null;
				if (!hasSource) {
					cachedSource = theSource.get();
					hasSource = true;
				}
				return cachedSource;
			}

			@Override
			public boolean hasPreviousResult() {
				return theOldResult != null;
			}

			@Override
			public T getPreviousResult() {
				if (theOldResult == null)
					return null;
				else if (!hasOldResult) {
					cachedOldResult = theOldResult.apply(getCurrentSource());
					hasOldResult = true;
				}
				return cachedOldResult;
			}

			@Override
			public boolean has(ObservableValue<?> arg) {
				return theTransformation.hasArg(arg);
			}

			@Override
			public <V> V get(ObservableValue<V> arg) throws IllegalArgumentException {
				return theState.get(theTransformation.getArgIndex(arg));
			}
		}

		abstract class AbstractTransformedElement implements TransformedElement<S, T> {
			@Override
			public BiTuple<T, T> sourceChanged(S oldSource, S newSource, TransformationState state) {
				boolean reEval = theTransformation.isReEvalOnUpdate()
					|| (oldSource != newSource && !theSourceEquivalence.elementEquals(oldSource, newSource));
				if (!reEval && !theTransformation.isFireIfUnchanged())
					return null;
				T oldResult = getCachedOrEvaluate(oldSource, state);
				T newResult = reEval ? transform(true, //
					() -> oldSource, newSource, __ -> oldResult, state, false) : oldResult;
					boolean fireChange = theTransformation.isFireIfUnchanged()
						|| (oldResult != newResult && !theTransformation.equivalence().elementEquals(oldResult, newResult));
					cacheSource(newSource);
					cacheResult(newResult);
					return fireChange ? new BiTuple<>(oldResult, newResult) : null;
			}

			@Override
			public String isEnabled(TransformationState state) {
				if (!(theTransformation instanceof ReversibleTransformation)) {
					// If the transformation is not explicitly reversible, it may still possible to apply updates
					return null;
				} else {
					TransformationValuesImpl tv = new TransformationValuesImpl(this::getSourceValue, src -> getCachedOrEvaluate(src, state),
						state);
					return ((ReversibleTransformation<S, T>) theTransformation).getReverse().isEnabled(tv);
				}
			}

			abstract T getCachedOrEvaluate(S oldSource, TransformationState state);

			abstract void cacheSource(S source);

			abstract void cacheResult(T result);

			ReverseQueryResult<S> set(T value, Supplier<S> currentSource, Function<S, T> oldResult, TransformationState state,
				boolean test) {
				TransformationValuesImpl tx = new TransformationValuesImpl(currentSource, oldResult, state);
				if (!(theTransformation instanceof ReversibleTransformation)) {
					if (oldResult != null && theTransformation.equivalence().elementEquals(tx.getPreviousResult(), value))
						return ReverseQueryResult.value(currentSource.get());
					return ReverseQueryResult.reject(StdMsg.UNSUPPORTED_OPERATION);
				}
				ReversibleTransformation<S, T> reversible = (ReversibleTransformation<S, T>) theTransformation;
				ReverseQueryResult<S> qr;
				if (oldResult != null && theTransformation.equivalence().elementEquals(tx.getPreviousResult(), value))
					qr = ReverseQueryResult.value(currentSource.get()); // Update, no need to do a reverse
				else
					qr = reversible.getReverse().reverse(value, tx, false, test);
				return qr;
			}
		}

		class CachedTransformedElement extends AbstractTransformedElement {
			volatile S theSource;
			volatile T theResult;

			CachedTransformedElement(S source, TransformationState state) {
				S sourceVal = source;
				this.theSource = sourceVal;
				this.theResult = transform(false, //
					() -> sourceVal, sourceVal, null, state, false);
			}

			@Override
			public S getSourceValue() {
				return theSource;
			}

			@Override
			public T getCurrentValue(TransformationState state) {
				return theResult;
			}

			@Override
			public T getCurrentValue(S source, TransformationState transformationState) {
				return theResult;
			}

			@Override
			public BiTuple<T, T> transformationStateChanged(TransformationState oldState, TransformationState newState) {
				S sourceVal = theSource;
				T oldResult = theResult;
				T newResult = transform(false, //
					() -> sourceVal, sourceVal, __ -> oldResult, newState, true);
				boolean fireChange = theTransformation.isFireIfUnchanged()
					|| !theTransformation.equivalence().elementEquals(oldResult, newResult);
				cacheResult(newResult);
				return fireChange ? new BiTuple<>(oldResult, newResult) : null;
			}

			@Override
			public ReverseQueryResult<S> set(T value, TransformationState state, boolean test) {
				return super.set(value, //
					() -> theSource, __ -> theResult, state, test);
			}

			@Override
			T getCachedOrEvaluate(S oldSource, TransformationState state) {
				return theResult;
			}

			@Override
			void cacheSource(S source) {
				theSource = source;
			}

			@Override
			void cacheResult(T result) {
				theResult = result;
			}
		}

		class DynamicTransformedElement extends AbstractTransformedElement {
			private final Supplier<S> theSource;

			DynamicTransformedElement(Supplier<S> source) {
				theSource = source;
			}

			@Override
			public S getSourceValue() {
				return theSource.get();
			}

			@Override
			public T getCurrentValue(TransformationState state) {
				return getCurrentValue(getSourceValue(), state);
			}

			@Override
			public T getCurrentValue(S source, TransformationState state) {
				return transform(false, () -> source, source, null, state, false);
			}

			@Override
			public BiTuple<T, T> transformationStateChanged(TransformationState oldState, TransformationState newState) {
				S sourceVal = theSource.get();
				T oldResult = getCachedOrEvaluate(sourceVal, oldState);
				T newResult = transform(false, () -> sourceVal, sourceVal, __ -> oldResult, newState, true);
				boolean fireChange = theTransformation.isFireIfUnchanged()
					|| !theTransformation.equivalence().elementEquals(oldResult, newResult);
				cacheResult(newResult);
				return fireChange ? new BiTuple<>(oldResult, newResult) : null;
			}

			@Override
			public ReverseQueryResult<S> set(T value, TransformationState state, boolean test) {
				// I had this previous-value function set before, but this is inconsistent with EngineImpl.setElementsValue(),
				// where no such function is supplied to Engine.reverse() for un-cached transformations.
				// In theory it could be either, but if the function is given, it requires several calls to evaluate,
				// which may be expensive.
				// The tester expects that this is not allowed, so I'm changing it here.
				return super.set(value, //
					theSource, null/*s -> getCachedOrEvaluate(s, state)*/, state, test);
			}

			@Override
			T getCachedOrEvaluate(S oldSource, TransformationState state) {
				return transform(false, () -> oldSource, oldSource, null, state, false);
			}

			@Override
			void cacheSource(S source) {}

			@Override
			void cacheResult(T result) {}
		}
	}
}
