package org.observe.expresso.ops;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntPredicate;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.ClassMap;
import org.qommons.ClassMap.TypeMatch;
import org.qommons.SelfDescribed;
import org.qommons.StringUtils;
import org.qommons.TriFunction;
import org.qommons.ex.ExceptionHandler;
import org.qommons.io.LocatedFilePosition;

import com.google.common.reflect.TypeToken;

/** A set of binary operations that a {@link BinaryOperator} can use to evaluate itself */
public class BinaryOperatorSet {
	/**
	 * A binary operator that knows how to operate on 2 inputs
	 *
	 * @param <S> The super-type of the first input that this operator knows how to handle
	 * @param <T> The super-type of the second input that this operator knows how to handle
	 * @param <V> The type of output produced by this operator
	 */
	public interface BinaryOp<S, T, V> extends SelfDescribed {
		/** @return The super-type of output produced by this operator */
		Class<V> getTargetSuperType();

		/**
		 * @param leftOpType The type of the left operand
		 * @param rightOpType The type of the right operand
		 * @param position The position of the operator in the file
		 * @param length The length of the expression
		 * @return The type of output produced by this operator for the given operand types
		 * @throws EX If this operation cannot be used for the combination of the given operand types
		 */
		<EX extends Throwable> TypeToken<V> getTargetType(TypeToken<? extends S> leftOpType, TypeToken<? extends T> rightOpType,
			LocatedFilePosition position, int length, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws EX;

		/**
		 * Performs the operation
		 *
		 * @param source The first input value
		 * @param other The second input value
		 * @return The output value
		 */
		V apply(S source, T other);

		/**
		 * @param currentSource The current value of the first input
		 * @param other The value of the second input
		 * @param value The output value
		 * @return Null if this operator is capable of producing a primary input value for which {@link #apply(Object, Object) apply} on
		 *         that value and the given secondary input would produce the given output value; otherwise a reason why this is not
		 *         possible
		 */
		String canReverse(S currentSource, T other, V value);

		/**
		 * @param currentSource The current value of the first input
		 * @param other The value of the second input
		 * @param value The output value
		 * @return A primary input value for which {@link #apply(Object, Object) apply} on that value and the given secondary input would
		 *         produce the given output value
		 */
		S reverse(S currentSource, T other, V value);

		/**
		 * Produces a binary operator whose output type is the same as that of the primary input from functions
		 *
		 * @param <S> The primary input and output type of the operator
		 * @param <T> The secondary input type of the operator
		 * @param name The name of the operator
		 * @param type The primary input and output type of the operator
		 * @param op The function to use for {@link BinaryOp#apply(Object, Object)}
		 * @param reverse The function to use for {@link BinaryOp#reverse(Object, Object, Object)}
		 * @param reverseEnabled The function to use for {@link BinaryOp#canReverse(Object, Object, Object)}, or null if the operator is
		 *        always reversible
		 * @param description The description for the operator
		 * @return The binary operator composed of the given functions
		 */
		static <S, T> BinaryOp<S, T, S> of(String name, TypeToken<S> type, BiFunction<? super S, ? super T, ? extends S> op,
			TriFunction<? super S, ? super T, ? super S, ? extends S> reverse,
			TriFunction<? super S, ? super T, ? super S, String> reverseEnabled, String description) {
			return of2(name, type, op, reverse, reverseEnabled, description);
		}

		/**
		 * Produces a binary operator from functions
		 *
		 * @param <S> The primary input type of the operator
		 * @param <T> The secondary input type of the operator
		 * @param <V> The output type of the operator
		 * @param name The name of the operator
		 * @param type The output type of the operator
		 * @param op The function to use for {@link BinaryOp#apply(Object, Object)}
		 * @param reverse The function to use for {@link BinaryOp#reverse(Object, Object, Object)}
		 * @param reverseEnabled The function to use for {@link BinaryOp#canReverse(Object, Object, Object)}, or null if the operator is
		 *        always reversible
		 * @param description The description for the operator
		 * @return The binary operator composed of the given functions
		 */
		static <S, T, V> BinaryOp<S, T, V> of2(String name, TypeToken<V> type, BiFunction<? super S, ? super T, ? extends V> op,
			TriFunction<? super S, ? super T, ? super V, ? extends S> reverse,
			TriFunction<? super S, ? super T, ? super V, String> reverseEnabled, String description) {
			return new BinaryOp<S, T, V>() {
				@Override
				public Class<V> getTargetSuperType() {
					return TypeTokens.getRawType(type);
				}

				@Override
				public <EX extends Throwable> TypeToken<V> getTargetType(TypeToken<? extends S> leftOpType,
					TypeToken<? extends T> rightOpType, LocatedFilePosition position, int length,
					ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) {
					return type;
				}

				@Override
				public V apply(S source, T other) {
					return op.apply(source, other);
				}

				@Override
				public String canReverse(S currentSource, T other, V value) {
					if (reverseEnabled == null)
						return null;
					return reverseEnabled.apply(currentSource, other, value);
				}

				@Override
				public S reverse(S currentSource, T other, V value) {
					return reverse.apply(currentSource, other, value);
				}

				@Override
				public String getDescription() {
					return description;
				}

				@Override
				public String toString() {
					return name;
				}
			};
		}
	}

	/**
	 * <p>
	 * A binary operator for which the first argument may be decisive, meaning that the second argument is not needed to determine the
	 * result.
	 * </p>
	 * <p>
	 * The classic examples of this are the binary OR and AND operations. E.g. for an OR operation on booleans <b>a</b> and <b>b</b>, if
	 * <b>a</b> is true, the result will be true regardless of the value of <b>b</b>.
	 * </p>
	 * <p>
	 * This distinction is important because it can avoid unnecessary evaluation of values. This can be even more important because
	 * sometimes expressions are structured assuming this will be the case. E.g. in the expression<code>a!=null && a.b!=null</code>, the
	 * <code>a.b!=null</code> should not be evaluated if a is null, since this would cause a null pointer exception.
	 * </p>
	 *
	 * @param <S> The super-type of the first input that this operator knows how to handle
	 * @param <T> The super-type of the second input that this operator knows how to handle
	 * @param <V> The type of output produced by this operator
	 */
	public interface FirstArgDecisiveBinaryOp<S, T, V> extends BinaryOp<S, T, V> {
		/**
		 * @param source The source value to test
		 * @return The result of the operation if it is known from only the first argument; otherwise null
		 */
		V getFirstArgDecisiveValue(S source);

		/**
		 * Produces a first-arg-decisive binary operator whose input and output types are all the same
		 *
		 * @param <T> The type of the operator
		 * @param name The name of the operator
		 * @param type The primary input and output type of the operator
		 * @param decisiveFn The function to use for {@link #getFirstArgDecisiveValue(Object)}
		 * @param op The function to use for {@link BinaryOp#apply(Object, Object)}
		 * @param reverse The function to use for {@link BinaryOp#reverse(Object, Object, Object)}
		 * @param reverseEnabled The function to use for {@link BinaryOp#canReverse(Object, Object, Object)}, or null if the operator is
		 *        always reversible
		 * @param description The description for the operator
		 * @return The binary operator composed of the given functions
		 */
		static <T> FirstArgDecisiveBinaryOp<T, T, T> of(String name, TypeToken<T> type, Function<? super T, ? extends T> decisiveFn,
			BiFunction<? super T, ? super T, ? extends T> op, TriFunction<? super T, ? super T, ? super T, ? extends T> reverse,
			TriFunction<? super T, ? super T, ? super T, String> reverseEnabled, String description) {
			return new FirstArgDecisiveBinaryOp<T, T, T>() {
				@Override
				public Class<T> getTargetSuperType() {
					return TypeTokens.getRawType(type);
				}

				@Override
				public <EX extends Throwable> TypeToken<T> getTargetType(TypeToken<? extends T> leftOpType,
					TypeToken<? extends T> rightOpType, LocatedFilePosition position, int length,
					ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) {
					return type;
				}

				@Override
				public T apply(T source, T other) {
					return op.apply(source, other);
				}

				@Override
				public String canReverse(T currentSource, T other, T value) {
					if (reverseEnabled == null)
						return null;
					return reverseEnabled.apply(currentSource, other, value);
				}

				@Override
				public T reverse(T currentSource, T other, T value) {
					return reverse.apply(currentSource, other, value);
				}

				@Override
				public T getFirstArgDecisiveValue(T source) {
					return decisiveFn.apply(source);
				}

				@Override
				public String getDescription() {
					return description;
				}

				@Override
				public String toString() {
					return name;
				}
			};
		}
	}

	/** The boolean OR operation */
	public static FirstArgDecisiveBinaryOp<Boolean, Boolean, Boolean> OR = FirstArgDecisiveBinaryOp.of("||", TypeTokens.get().BOOLEAN, //
		b -> unwrapBool(b) ? true : null, (b1, b2) -> unwrapBool(b1) || unwrapBool(b2), //
			(s, b2, r) -> {
				if (unwrapBool(r)) { // Trying to make the expression true
					if (unwrapBool(b2))
						return s; // Leave it alone--the expression will be true regardless
					else
						return true;
				} else { // Trying to make the expression false
					if (unwrapBool(b2))
						return null; // Can't make it false--should be prevented by the enabled fn
					else
						return false;
				}
			}, (s, b2, r) -> (!unwrapBool(r) && unwrapBool(b2)) ? "Or expression cannot be made false" : null, "Boolean OR operator");
	/** The boolean AND operation */
	public static FirstArgDecisiveBinaryOp<Boolean, Boolean, Boolean> AND = FirstArgDecisiveBinaryOp.of("&&", TypeTokens.get().BOOLEAN, //
		b -> !unwrapBool(b) ? false : null, (b1, b2) -> unwrapBool(b1) && unwrapBool(b2), //
			(s, b2, r) -> {
				if (unwrapBool(r)) { // Trying to make the expression true
					if (unwrapBool(b2))
						return true;
					else
						return null; // Can't make it true--should be prevented by the enabled fn
				} else { // Trying to make the expression false
					if (unwrapBool(b2))
						return false;
					else
						return s; // Leave it alone--the expression will be false regardless
				}
			}, (s, b2, r) -> (!unwrapBool(b2) && unwrapBool(r)) ? "And expression cannot be made true" : null, "Boolean AND operator");

	static class CastOp<S, T> {
		static final CastOp<Byte, Short> byteShort = new CastOp<>(byte.class, short.class);
		static final CastOp<Byte, Integer> byteInt = new CastOp<>(byte.class, int.class);
		static final CastOp<Byte, Long> byteLong = new CastOp<>(byte.class, long.class);
		static final CastOp<Byte, Character> byteChar = new CastOp<>(byte.class, char.class);
		static final CastOp<Byte, Float> byteFloat = new CastOp<>(byte.class, float.class);
		static final CastOp<Byte, Double> byteDouble = new CastOp<>(byte.class, double.class);

		static final CastOp<Short, Integer> shortInt = new CastOp<>(short.class, int.class);
		static final CastOp<Short, Long> shortLong = new CastOp<>(short.class, long.class);
		static final CastOp<Short, Character> shortChar = new CastOp<>(short.class, char.class);
		static final CastOp<Short, Float> shortFloat = new CastOp<>(short.class, float.class);
		static final CastOp<Short, Double> shortDouble = new CastOp<>(short.class, double.class);

		static final CastOp<Integer, Long> intLong = new CastOp<>(int.class, long.class);
		static final CastOp<Integer, Character> intChar = new CastOp<>(int.class, char.class);
		static final CastOp<Integer, Float> intFloat = new CastOp<>(int.class, float.class);
		static final CastOp<Integer, Double> intDouble = new CastOp<>(int.class, double.class);

		static final CastOp<Long, Character> longChar = new CastOp<>(long.class, char.class);
		static final CastOp<Long, Float> longFloat = new CastOp<>(long.class, float.class);
		static final CastOp<Long, Double> longDouble = new CastOp<>(long.class, double.class);

		static final CastOp<Character, Short> charShort = new CastOp<>(char.class, short.class);
		static final CastOp<Character, Integer> charInt = new CastOp<>(char.class, int.class);
		static final CastOp<Character, Long> charLong = new CastOp<>(char.class, long.class);
		static final CastOp<Character, Float> charFloat = new CastOp<>(char.class, float.class);
		static final CastOp<Character, Double> charDouble = new CastOp<>(char.class, double.class);

		static final CastOp<Float, Double> floatDouble = new CastOp<>(float.class, double.class);

		private final TypeTokens.TypeConverter<S, S, T, T> theConverter;

		CastOp(Class<S> sourceType, Class<T> targetType) {
			theConverter = TypeTokens.get().primitiveKeyFor(targetType).getPrimitiveCast(sourceType);
		}

		/**
		 * @param sourceType The type of the source argument being cast
		 * @return The result type of the cast
		 */
		TypeToken<? extends T> getType(TypeToken<? extends S> sourceType) {
			return theConverter.getConvertedType();
		}

		/**
		 * @param source The source value
		 * @return A target-type value equivalent to the given source value
		 */
		T cast(S source) {
			return theConverter.apply(source);
		}

		/**
		 * @param target The target value
		 * @return Whether the given value can be reverse-cast to an equivalent source-type value
		 */
		String canReverse(T target) {
			return theConverter.isReversible(target);
		}

		/**
		 * @param target The target value
		 * @return A source-type value equivalent to the given target value
		 */
		S reverse(T target) {
			return theConverter.reverse(target);
		}

		/**
		 * @param <V> The output type of the operator
		 * @param op The binary operator to convert
		 * @return An equivalent binary operator whose primary input type is this cast's source type
		 */
		<V> BinaryOp<S, T, V> castPrimary(BinaryOp<T, T, V> op) {
			return new BinaryOp<S, T, V>() {
				@Override
				public Class<V> getTargetSuperType() {
					return op.getTargetSuperType();
				}

				@Override
				public <EX extends Throwable> TypeToken<V> getTargetType(TypeToken<? extends S> leftOpType,
					TypeToken<? extends T> rightOpType, LocatedFilePosition position, int length,
					ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws EX {
					return op.getTargetType(getType(leftOpType), rightOpType, position, length, exHandler);
				}

				@Override
				public V apply(S source, T other) {
					return op.apply(cast(source), other);
				}

				@Override
				public String canReverse(S currentSource, T other, V value) {
					T cast = cast(currentSource);
					String msg = op.canReverse(cast, other, value);
					if (msg != null)
						return msg;
					T newTarget = op.reverse(cast, other, value);
					return CastOp.this.canReverse(newTarget);
				}

				@Override
				public S reverse(S currentSource, T other, V value) {
					T newTarget = op.reverse(cast(currentSource), other, value);
					return CastOp.this.reverse(newTarget);
				}

				@Override
				public String getDescription() {
					return op.getDescription();
				}
			};
		}

		/**
		 * @param <V> The output type of the operator
		 * @param op The binary operator to convert
		 * @return An equivalent binary operator whose secondary input type is this cast's source type
		 */
		<V> BinaryOp<T, S, V> castSecondary(BinaryOp<T, T, V> op) {
			return new BinaryOp<T, S, V>() {
				@Override
				public Class<V> getTargetSuperType() {
					return op.getTargetSuperType();
				}

				@Override
				public <EX extends Throwable> TypeToken<V> getTargetType(TypeToken<? extends T> leftOpType,
					TypeToken<? extends S> rightOpType, LocatedFilePosition position, int length,
					ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws EX {
					return op.getTargetType(leftOpType, getType(rightOpType), position, length, exHandler);
				}

				@Override
				public V apply(T source, S other) {
					return op.apply(source, cast(other));
				}

				@Override
				public String canReverse(T currentSource, S other, V value) {
					return op.canReverse(currentSource, cast(other), value);
				}

				@Override
				public T reverse(T currentSource, S other, V value) {
					return op.reverse(currentSource, cast(other), value);
				}

				@Override
				public String getDescription() {
					return op.getDescription();
				}
			};
		}

		/**
		 * @param <S> The primary input type for the new operator
		 * @param <T> The secondary input type for the new operator
		 * @param <T2> The primary and secondary input type for the source operator
		 * @param <V> The output type of the operator
		 * @param op The operator to convert
		 * @param sourceCast The cast for the primary input
		 * @param otherCast The cast for the secondary input
		 * @return The new operator with input types corresponding to the given casts
		 */
		static <S, T, T2, V> BinaryOp<S, T, V> castBoth(BinaryOp<T2, T2, V> op, CastOp<S, T2> sourceCast, CastOp<T, T2> otherCast) {
			return new BinaryOp<S, T, V>() {
				@Override
				public Class<V> getTargetSuperType() {
					return op.getTargetSuperType();
				}

				@Override
				public <EX extends Throwable> TypeToken<V> getTargetType(TypeToken<? extends S> leftOpType,
					TypeToken<? extends T> rightOpType, LocatedFilePosition position, int length,
					ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws EX {
					return op.getTargetType(sourceCast.getType(leftOpType), otherCast.getType(rightOpType), position, length, exHandler);
				}

				@Override
				public V apply(S source, T other) {
					return op.apply(sourceCast.cast(source), otherCast.cast(other));
				}

				@Override
				public String canReverse(S currentSource, T other, V value) {
					T2 castSource = sourceCast.cast(currentSource);
					T2 castOther = otherCast.cast(other);
					String msg = op.canReverse(castSource, castOther, value);
					if (msg != null)
						return msg;
					T2 newTarget = op.reverse(castSource, castOther, value);
					return sourceCast.canReverse(newTarget);
				}

				@Override
				public S reverse(S currentSource, T other, V value) {
					T2 newTarget = op.reverse(sourceCast.cast(currentSource), otherCast.cast(other), value);
					return sourceCast.reverse(newTarget);
				}

				@Override
				public String getDescription() {
					return op.getDescription();
				}
			};
		}
		//
		// /**
		// * Produces a cast object from functions
		// *
		// * @param <S> The source type for the cast
		// * @param <T> The target type for the cast
		// * @param type The target type of the cast
		// * @param cast The function to use for {@link CastOp#cast(Object)}
		// * @param canReverse The function to use for {@link CastOp#canReverse(Object)}, or null if the cast is always reversible
		// * @param reverse The function to use for {@link CastOp#reverse(Object)}
		// * @return The cast backed by the given functions
		// */
		// static <S, T> CastOp<S, T> of(TypeToken<T> type, Function<? super S, ? extends T> cast, Function<? super T, String> canReverse,
		// Function<? super T, ? extends S> reverse) {
		// return new CastOp<S, T>() {
		// @Override
		// public TypeToken<T> getType(TypeToken<? extends S> sourceType) {
		// return type;
		// }
		//
		// @Override
		// public T cast(S source) {
		// return cast.apply(source);
		// }
		//
		// @Override
		// public String canReverse(T target) {
		// return canReverse == null ? null : canReverse.apply(target);
		// }
		//
		// @Override
		// public S reverse(T target) {
		// return reverse.apply(target);
		// }
		// };
		// }
	}

	/** Represents something that can configure a {@link Builder} to support some set of binary operations */
	public interface BinaryOperatorConfiguration {
		/**
		 * Configures a binary operator set builder to support some set of binary operations
		 *
		 * @param operators The builder to configure
		 * @return The builder
		 */
		Builder configure(Builder operators);
	}

	/**
	 * Configures a binary operator set to support all the standard Java binary operations
	 *
	 * @param operators The builder to configure
	 * @return The builder
	 */
	public static Builder standardJava(Builder operators) {
		// Do equality first, which is special
		// First, same-type primitive equality
		operators.with("==", Boolean.class, Boolean.class, (b1, b2) -> unwrapBool(b1) == unwrapBool(b2),
			(s, b2, r) -> unwrapBool(b2) ? unwrapBool(r) : !unwrapBool(r), null, "Boolean equality comparison");
		operators.with("!=", Boolean.class, Boolean.class, (b1, b2) -> unwrapBool(b1) != unwrapBool(b2),
			(s, b2, r) -> unwrapBool(b2) ? !unwrapBool(r) : unwrapBool(r), null, "Boolean inequality comparison");

		operators.with2("==", Integer.class, Integer.class, Boolean.class, (i1, i2) -> unwrapI(i1) == unwrapI(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) // Trying to make the expression true
					return i2;
				else if (unwrapI(s) != unwrapI(i2))
					return s; // Leave it alone--it's already false
				else
					return null; // Don't make up a value to make it false--prevent with the enabled fn
			}, (s, i2, r) -> (!unwrapBool(r) && unwrapI(s) == unwrapI(i2)) ? "Equal expression cannot be negated" : null,
			"Integer equality comparison");
		operators.with2("!=", Integer.class, Integer.class, Boolean.class, (i1, i2) -> unwrapI(i1) != unwrapI(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) {// Trying to make the expression true
					if (unwrapI(s) == unwrapI(i2))
						return s; // Leave it alone--it's already true
					else
						return null; // Don't make up a value to make it true--prevent with the enabled fn
				} else
					return i2;
			}, (s, i2, r) -> (unwrapBool(r) && unwrapI(s) != unwrapI(i2)) ? "Not-equal expression cannot be made true" : null,
			"Integer inequality comparison");
		operators.with2("==", Long.class, Long.class, Boolean.class, (i1, i2) -> unwrapL(i1) == unwrapL(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) // Trying to make the expression true
					return i2;
				else if (unwrapL(s) != unwrapL(i2))
					return s; // Leave it alone--it's already false
				else
					return null; // Don't make up a value to make it false--prevent with the enabled fn
			}, (s, i2, r) -> (!unwrapBool(r) && unwrapL(s) == unwrapL(i2)) ? "Equal expression cannot be negated" : null,
			"Long integer equality comparison");
		operators.with2("!=", Long.class, Long.class, Boolean.class, (i1, i2) -> unwrapL(i1) != unwrapL(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) {// Trying to make the expression true
					if (unwrapL(s) == unwrapL(i2))
						return s; // Leave it alone--it's already true
					else
						return null; // Don't make up a value to make it true--prevent with the enabled fn
				} else
					return i2;
			}, (s, i2, r) -> (unwrapBool(r) && unwrapL(s) != unwrapL(i2)) ? "Not-equal expression cannot be made true" : null,
			"Long integer inequality comparison");
		operators.with2("==", Double.class, Double.class, Boolean.class, (i1, i2) -> unwrapD(i1) == unwrapD(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) // Trying to make the expression true
					return i2;
				else if (unwrapD(s) != unwrapD(i2))
					return s; // Leave it alone--it's already false
				else
					return null; // Don't make up a value to make it false--prevent with the enabled fn
			}, (s, i2, r) -> (!unwrapBool(r) && unwrapD(s) == unwrapD(i2)) ? "Equal expression cannot be negated" : null,
			"Double-precision floating-point value equality comparison");
		operators.with2("!=", Double.class, Double.class, Boolean.class, (i1, i2) -> unwrapD(i1) != unwrapD(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) {// Trying to make the expression true
					if (unwrapD(s) == unwrapD(i2))
						return s; // Leave it alone--it's already true
					else
						return null; // Don't make up a value to make it true--prevent with the enabled fn
				} else
					return i2;
			}, (s, i2, r) -> (unwrapBool(r) && unwrapD(s) != unwrapD(i2)) ? "Not-equal expression cannot be made true" : null,
			"Double-precision floating-point value inequality comparison");
		operators.with2("==", Float.class, Float.class, Boolean.class, (i1, i2) -> unwrapF(i1) == unwrapF(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) // Trying to make the expression true
					return i2;
				else if (unwrapF(s) != unwrapF(i2))
					return s; // Leave it alone--it's already false
				else
					return null; // Don't make up a value to make it false--prevent with the enabled fn
			}, (s, i2, r) -> (!unwrapBool(r) && unwrapF(s) == unwrapF(i2)) ? "Equal expression cannot be negated" : null,
			"Floating-point value equality comparison");
		operators.with2("!=", Float.class, Float.class, Boolean.class, (i1, i2) -> unwrapF(i1) != unwrapF(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) {// Trying to make the expression true
					if (unwrapF(s) == unwrapF(i2))
						return s; // Leave it alone--it's already true
					else
						return null; // Don't make up a value to make it true--prevent with the enabled fn
				} else
					return i2;
			}, (s, i2, r) -> (unwrapBool(r) && unwrapF(s) != unwrapF(i2)) ? "Not-equal expression cannot be made true" : null,
			"Floating-point value inequality comparison");
		operators.with2("==", Byte.class, Byte.class, Boolean.class, (i1, i2) -> unwrapByte(i1) == unwrapByte(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) // Trying to make the expression true
					return i2;
				else if (unwrapByte(s) != unwrapByte(i2))
					return s; // Leave it alone--it's already false
				else
					return null; // Don't make up a value to make it false--prevent with the enabled fn
			}, (s, i2, r) -> (!unwrapBool(r) && unwrapByte(s) == unwrapByte(i2)) ? "Equal expression cannot be negated" : null,
			"Byte equality comparison");
		operators.with2("!=", Byte.class, Byte.class, Boolean.class, (i1, i2) -> unwrapByte(i1) != unwrapByte(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) {// Trying to make the expression true
					if (unwrapByte(s) == unwrapByte(i2))
						return s; // Leave it alone--it's already true
					else
						return null; // Don't make up a value to make it true--prevent with the enabled fn
				} else
					return i2;
			}, (s, i2, r) -> (unwrapBool(r) && unwrapByte(s) != unwrapByte(i2)) ? "Not-equal expression cannot be made true" : null,
			"Byte inequality comparison");
		operators.with2("==", Short.class, Short.class, Boolean.class, (i1, i2) -> unwrapS(i1) == unwrapS(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) // Trying to make the expression true
					return i2;
				else if (unwrapS(s) != unwrapS(i2))
					return s; // Leave it alone--it's already false
				else
					return null; // Don't make up a value to make it false--prevent with the enabled fn
			}, (s, i2, r) -> (!unwrapBool(r) && unwrapS(s) == unwrapS(i2)) ? "Equal expression cannot be negated" : null,
			"Short integer equality comparison");
		operators.with2("!=", Short.class, Short.class, Boolean.class, (i1, i2) -> unwrapS(i1) != unwrapS(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) {// Trying to make the expression true
					if (unwrapS(s) == unwrapS(i2))
						return s; // Leave it alone--it's already true
					else
						return null; // Don't make up a value to make it true--prevent with the enabled fn
				} else
					return i2;
			}, (s, i2, r) -> (unwrapBool(r) && unwrapS(s) != unwrapS(i2)) ? "Not-equal expression cannot be made true" : null,
			"Short integer inequality comparison");
		operators.with2("==", Character.class, Character.class, Boolean.class, (i1, i2) -> unwrapC(i1) == unwrapC(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) // Trying to make the expression true
					return i2;
				else if (unwrapC(s) != unwrapC(i2))
					return s; // Leave it alone--it's already false
				else
					return null; // Don't make up a value to make it false--prevent with the enabled fn
			}, (s, i2, r) -> (!unwrapBool(r) && unwrapC(s) == unwrapC(i2)) ? "Equal expression cannot be negated" : null,
			"Character equality comparison");
		operators.with2("!=", Character.class, Character.class, Boolean.class, (i1, i2) -> unwrapC(i1) != unwrapC(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) {// Trying to make the expression true
					if (unwrapC(s) == unwrapC(i2))
						return s; // Leave it alone--it's already true
					else
						return null; // Don't make up a value to make it true--prevent with the enabled fn
				} else
					return i2;
			}, (s, i2, r) -> (unwrapBool(r) && unwrapC(s) != unwrapC(i2)) ? "Not-equal expression cannot be made true" : null,
			"Character inequality comparison");

		// Use the same-type equality methods above to implement cross-type primitive equal comparisons

		operators.withCastSecondary("==", Integer.class, Byte.class, boolean.class, CastOp.byteInt);
		operators.withCastSecondary("!=", Integer.class, Byte.class, boolean.class, CastOp.byteInt);
		operators.withCastSecondary("==", Integer.class, Short.class, boolean.class, CastOp.shortInt);
		operators.withCastSecondary("!=", Integer.class, Short.class, boolean.class, CastOp.shortInt);
		operators.withCastSecondary("==", Integer.class, Character.class, boolean.class, CastOp.charInt);
		operators.withCastSecondary("!=", Integer.class, Character.class, boolean.class, CastOp.charInt);
		operators.withCastPrimary("==", Integer.class, Long.class, boolean.class, CastOp.intLong);
		operators.withCastPrimary("!=", Integer.class, Long.class, boolean.class, CastOp.intLong);
		operators.withCastPrimary("==", Integer.class, Float.class, boolean.class, CastOp.intFloat);
		operators.withCastPrimary("!=", Integer.class, Float.class, boolean.class, CastOp.intFloat);
		operators.withCastPrimary("==", Integer.class, Double.class, boolean.class, CastOp.intDouble);
		operators.withCastPrimary("!=", Integer.class, Double.class, boolean.class, CastOp.intDouble);

		operators.withCastSecondary("==", Long.class, Integer.class, boolean.class, CastOp.intLong);
		operators.withCastSecondary("!=", Long.class, Integer.class, boolean.class, CastOp.intLong);
		operators.withCastSecondary("==", Long.class, Byte.class, boolean.class, CastOp.byteLong);
		operators.withCastSecondary("!=", Long.class, Byte.class, boolean.class, CastOp.byteLong);
		operators.withCastSecondary("==", Long.class, Short.class, boolean.class, CastOp.shortLong);
		operators.withCastSecondary("!=", Long.class, Short.class, boolean.class, CastOp.shortLong);
		operators.withCastSecondary("==", Long.class, Character.class, boolean.class, CastOp.charLong);
		operators.withCastSecondary("!=", Long.class, Character.class, boolean.class, CastOp.charLong);
		operators.withCastPrimary("==", Long.class, Float.class, boolean.class, CastOp.longFloat);
		operators.withCastPrimary("!=", Long.class, Float.class, boolean.class, CastOp.longFloat);
		operators.withCastPrimary("==", Long.class, Double.class, boolean.class, CastOp.longDouble);
		operators.withCastPrimary("!=", Long.class, Double.class, boolean.class, CastOp.longDouble);

		operators.withCastSecondary("==", Short.class, Byte.class, boolean.class, CastOp.byteShort);
		operators.withCastSecondary("!=", Short.class, Byte.class, boolean.class, CastOp.byteShort);
		operators.withCastPrimary("==", Short.class, Integer.class, boolean.class, CastOp.shortInt);
		operators.withCastPrimary("!=", Short.class, Integer.class, boolean.class, CastOp.shortInt);
		operators.withCastPrimary("==", Short.class, Long.class, boolean.class, CastOp.shortLong);
		operators.withCastPrimary("!=", Short.class, Long.class, boolean.class, CastOp.shortLong);
		operators.withCastPrimary("==", Short.class, Character.class, boolean.class, CastOp.shortChar);
		operators.withCastPrimary("!=", Short.class, Character.class, boolean.class, CastOp.shortChar);
		operators.withCastPrimary("==", Short.class, Float.class, boolean.class, CastOp.shortFloat);
		operators.withCastPrimary("!=", Short.class, Float.class, boolean.class, CastOp.shortFloat);
		operators.withCastPrimary("==", Short.class, Double.class, boolean.class, CastOp.shortDouble);
		operators.withCastPrimary("!=", Short.class, Double.class, boolean.class, CastOp.shortDouble);

		operators.withCastPrimary("==", Byte.class, Integer.class, boolean.class, CastOp.byteInt);
		operators.withCastPrimary("!=", Byte.class, Integer.class, boolean.class, CastOp.byteInt);
		operators.withCastPrimary("==", Byte.class, Short.class, boolean.class, CastOp.byteShort);
		operators.withCastPrimary("!=", Byte.class, Short.class, boolean.class, CastOp.byteShort);
		operators.withCastPrimary("==", Byte.class, Long.class, boolean.class, CastOp.byteLong);
		operators.withCastPrimary("!=", Byte.class, Long.class, boolean.class, CastOp.byteLong);
		operators.withCastPrimary("==", Byte.class, Character.class, boolean.class, CastOp.byteChar);
		operators.withCastPrimary("!=", Byte.class, Character.class, boolean.class, CastOp.byteChar);
		operators.withCastPrimary("==", Byte.class, Float.class, boolean.class, CastOp.byteFloat);
		operators.withCastPrimary("!=", Byte.class, Float.class, boolean.class, CastOp.byteFloat);
		operators.withCastPrimary("==", Byte.class, Double.class, boolean.class, CastOp.byteDouble);
		operators.withCastPrimary("!=", Byte.class, Double.class, boolean.class, CastOp.byteDouble);

		operators.withCastSecondary("==", Float.class, Long.class, boolean.class, CastOp.longFloat);
		operators.withCastSecondary("!=", Float.class, Long.class, boolean.class, CastOp.longFloat);
		operators.withCastSecondary("==", Float.class, Integer.class, boolean.class, CastOp.intFloat);
		operators.withCastSecondary("!=", Float.class, Integer.class, boolean.class, CastOp.intFloat);
		operators.withCastSecondary("==", Float.class, Short.class, boolean.class, CastOp.shortFloat);
		operators.withCastSecondary("!=", Float.class, Short.class, boolean.class, CastOp.shortFloat);
		operators.withCastSecondary("==", Float.class, Byte.class, boolean.class, CastOp.byteFloat);
		operators.withCastSecondary("!=", Float.class, Byte.class, boolean.class, CastOp.byteFloat);
		operators.withCastSecondary("==", Float.class, Character.class, boolean.class, CastOp.charFloat);
		operators.withCastSecondary("!=", Float.class, Character.class, boolean.class, CastOp.charFloat);
		operators.withCastPrimary("==", Float.class, Double.class, boolean.class, CastOp.floatDouble);
		operators.withCastPrimary("!=", Float.class, Double.class, boolean.class, CastOp.floatDouble);

		operators.withCastSecondary("==", Double.class, Float.class, boolean.class, CastOp.floatDouble);
		operators.withCastSecondary("!=", Double.class, Float.class, boolean.class, CastOp.floatDouble);
		operators.withCastSecondary("==", Double.class, Long.class, boolean.class, CastOp.longDouble);
		operators.withCastSecondary("!=", Double.class, Long.class, boolean.class, CastOp.longDouble);
		operators.withCastSecondary("==", Double.class, Integer.class, boolean.class, CastOp.intDouble);
		operators.withCastSecondary("!=", Double.class, Integer.class, boolean.class, CastOp.intDouble);
		operators.withCastSecondary("==", Double.class, Short.class, boolean.class, CastOp.shortDouble);
		operators.withCastSecondary("!=", Double.class, Short.class, boolean.class, CastOp.shortDouble);
		operators.withCastSecondary("==", Double.class, Byte.class, boolean.class, CastOp.byteDouble);
		operators.withCastSecondary("!=", Double.class, Byte.class, boolean.class, CastOp.byteDouble);
		operators.withCastSecondary("==", Double.class, Character.class, boolean.class, CastOp.charDouble);
		operators.withCastSecondary("!=", Double.class, Character.class, boolean.class, CastOp.charDouble);

		operators.withCastSecondary("==", Character.class, Byte.class, boolean.class, CastOp.byteChar);
		operators.withCastSecondary("!=", Character.class, Byte.class, boolean.class, CastOp.byteChar);
		operators.withCastPrimary("==", Character.class, Long.class, boolean.class, CastOp.charLong);
		operators.withCastPrimary("!=", Character.class, Long.class, boolean.class, CastOp.charLong);
		operators.withCastPrimary("==", Character.class, Integer.class, boolean.class, CastOp.charInt);
		operators.withCastPrimary("!=", Character.class, Integer.class, boolean.class, CastOp.charInt);
		operators.withCastPrimary("==", Character.class, Short.class, boolean.class, CastOp.charShort);
		operators.withCastPrimary("!=", Character.class, Short.class, boolean.class, CastOp.charShort);
		operators.withCastPrimary("==", Character.class, Float.class, boolean.class, CastOp.charFloat);
		operators.withCastPrimary("!=", Character.class, Float.class, boolean.class, CastOp.charFloat);
		operators.withCastPrimary("==", Character.class, Double.class, boolean.class, CastOp.charDouble);
		operators.withCastPrimary("!=", Character.class, Double.class, boolean.class, CastOp.charDouble);

		// Now, non-primitive equality
		operators.with2("==", Object.class, Object.class, Boolean.class, (o1, o2) -> o1 == o2, (s, o2, r) -> {
			if (unwrapBool(r)) // Trying to make the expression true
				return o2;
			else
				return s;
		}, (s, o2, r) -> (!unwrapBool(r) && s != o2) ? "Equals expression cannot be made false" : null, "Object identity comparison");
		operators.with2("!=", Object.class, Object.class, Boolean.class, (o1, o2) -> o1 != o2, (s, o2, r) -> {
			if (unwrapBool(r)) // Trying to make the expression true
				return s;
			else
				return o2;
		}, (s, o2, r) -> (unwrapBool(r) && s != o2) ? "Not-equals expression cannot be made true" : null,
			"Negative object identity comparison");

		// Boolean ops
		operators.with("||", Boolean.class, Boolean.class, OR);
		operators.with("|", Boolean.class, Boolean.class, OR);
		operators.with("&&", Boolean.class, Boolean.class, AND);
		operators.with("&", Boolean.class, Boolean.class, AND);
		operators.with("^", Boolean.class, Boolean.class, (b1, b2) -> unwrapBool(b1) ^ unwrapBool(b2), //
			(s, b2, r) -> {
				if (unwrapBool(r)) { // Trying to make the expression true
					return !unwrapBool(b2);
				} else { // Trying to make the expression false
					return unwrapBool(b2);
				}
			}, null, "Boolean XOR operator");

		// Arithmetic ops
		operators.withIntArithmeticOp("+", (s1, s2) -> unwrapI(s1) + unwrapI(s2), (s, s2, v) -> unwrapI(v) - unwrapI(s2), null,
			"Integer addition operator");
		operators.withLongArithmeticOp("+", (s1, s2) -> unwrapL(s1) + unwrapL(s2), (s, s2, v) -> unwrapL(v) - unwrapL(s2), null,
			"Long integer addition operator");
		operators.withFloatArithmeticOp("+", (s1, s2) -> unwrapF(s1) + unwrapF(s2), (s, s2, v) -> unwrapF(v) - unwrapF(s2), null,
			"Floating point addition operator");
		operators.withDoubleArithmeticOp("+", (s1, s2) -> unwrapD(s1) + unwrapD(s2), (s, s2, v) -> unwrapD(v) - unwrapD(s2), null,
			"Double-precision floating-point addition operator");

		operators.withIntArithmeticOp("-", (s1, s2) -> unwrapI(s1) - unwrapI(s2), (s, s2, v) -> unwrapI(v) + unwrapI(s2), null,
			"Integer subtraction operator");
		operators.withLongArithmeticOp("-", (s1, s2) -> unwrapL(s1) - unwrapL(s2), (s, s2, v) -> unwrapL(v) + unwrapL(s2), null,
			"Long integer subtraction operator");
		operators.withFloatArithmeticOp("-", (s1, s2) -> unwrapF(s1) - unwrapF(s2), (s, s2, v) -> unwrapF(v) + unwrapF(s2), null,
			"Floating point subtraction operator");
		operators.withDoubleArithmeticOp("-", (s1, s2) -> unwrapD(s1) - unwrapD(s2), (s, s2, v) -> unwrapD(v) + unwrapD(s2), null,
			"Double-precision floating-point subtraction operator");

		operators.withIntArithmeticOp("*", (s1, s2) -> unwrapI(s1) * unwrapI(s2), (s, s2, v) -> {
			int num = unwrapI(v);
			int den = unwrapI(s2);
			return den == 0 ? num : num / den;
		}, (s, s2, v) -> {
			int num = unwrapI(v);
			int den = unwrapI(s2);
			if (den == 0)
				return null;
			if (num % den != 0)
				return "Value assigned to a multiplication operation must be an even multiple of the denominator (" + den + ")";
			return null;
		}, "Integer multiplication operator");
		operators.withLongArithmeticOp("*", (s1, s2) -> unwrapL(s1) * unwrapL(s2), (s, s2, v) -> {
			long num = unwrapI(v);
			long den = unwrapI(s2);
			return den == 0 ? num : num / den;
		}, (s, s2, v) -> {
			long num = unwrapI(v);
			long den = unwrapI(s2);
			if (den == 0)
				return null;
			if (num % den != 0)
				return "Value assigned to a multiplication operation must be an even multiple of the denominator (" + den + ")";
			return null;
		}, "Long integer multiplication operator");
		operators.withFloatArithmeticOp("*", (s1, s2) -> unwrapF(s1) * unwrapF(s2), (s, s2, v) -> unwrapF(v) / unwrapF(s2), null,
			"Floating-point multiplication operator");
		operators.withDoubleArithmeticOp("*", (s1, s2) -> unwrapD(s1) * unwrapD(s2), (s, s2, v) -> unwrapD(v) / unwrapD(s2), null,
			"Double-precision floating-point multiplication operator");

		operators.withIntArithmeticOp("/", (s1, s2) -> {
			int num = unwrapI(s1);
			int den = unwrapI(s2);
			return den == 0 ? num : num / den;
		}, (s, s2, v) -> unwrapI(v) * unwrapI(s2), null, "Integer division operator");
		operators.withLongArithmeticOp("/", (s1, s2) -> {
			long num = unwrapL(s1);
			long den = unwrapL(s2);
			return den == 0 ? num : num / den;
		}, (s, s2, v) -> unwrapL(v) * unwrapL(s2), null, "Long integer division operator");
		operators.withFloatArithmeticOp("/", (s1, s2) -> unwrapF(s1) / unwrapF(s2), (s, s2, v) -> unwrapF(v) * unwrapF(s2), null,
			"Floating-point division operator");
		operators.withDoubleArithmeticOp("/", (s1, s2) -> unwrapD(s1) / unwrapD(s2), (s, s2, v) -> unwrapD(v) * unwrapD(s2), null,
			"Double-precision floating-point division operator");

		operators.withIntArithmeticOp("%", (s1, s2) -> {
			int num = unwrapI(s1);
			int den = unwrapI(s2);
			return den == 0 ? num : num % den;
		}, (s, s2, v) -> {
			int num = unwrapI(s);
			int den = unwrapI(s2);
			if (den == 0)
				return num;
			int currentMod = num % den;
			if (currentMod == v)
				return num;
			else
				return num - currentMod + v;
		}, (s, s2, v) -> Math.abs(unwrapI(v)) >= Math.abs(unwrapI(s2)) ? "Cannot set a modulus to greater than the divisor" : null,
			"Integer modulus operator");
		operators.withLongArithmeticOp("%", (s1, s2) -> {
			long num = unwrapL(s1);
			long den = unwrapL(s2);
			return den == 0 ? num : num % den;
		}, (s, s2, v) -> {
			long num = unwrapL(s);
			long den = unwrapL(s2);
			long currentMod = num % den;
			if (currentMod == v)
				return num;
			else
				return num - currentMod + v;
		}, (s, s2, v) -> Math.abs(unwrapL(v)) >= Math.abs(unwrapL(s2)) ? "Cannot set a modulus to greater than the divisor" : null,
			"Long integer modulus operator");
		operators.withFloatArithmeticOp("%", (s1, s2) -> unwrapF(s1) % unwrapF(s2), (s, s2, v) -> {
			float num = unwrapF(s);
			float den = unwrapF(s2);
			float currentMod = num % den;
			if (currentMod == v)
				return num;
			else
				return num - currentMod + v;
		}, (s, s2, v) -> Math.abs(unwrapF(v)) >= Math.abs(unwrapF(s2)) ? "Cannot set a modulus to greater than the divisor" : null,
			"Floating-point modulus operator");
		operators.withDoubleArithmeticOp("%", (s1, s2) -> unwrapD(s1) % unwrapD(s2), (s, s2, v) -> {
			double num = unwrapD(s);
			double den = unwrapD(s2);
			double currentMod = num % den;
			if (currentMod == v)
				return num;
			else
				return num - currentMod + v;
		}, (s, s2, v) -> Math.abs(unwrapD(v)) >= Math.abs(unwrapD(s2)) ? "Cannot set a modulus to greater than the divisor" : null,
			"Double-precision floating-point modulus operator");

		// Comparison ops
		operators.withIntComparisonOp("<", (s1, s2) -> unwrapI(s1) < unwrapI(s2), "Integer less than comparison");
		operators.withIntComparisonOp("<=", (s1, s2) -> unwrapI(s1) <= unwrapI(s2), "Integer less than or equal to comparison");
		operators.withIntComparisonOp(">", (s1, s2) -> unwrapI(s1) > unwrapI(s2), "Integer greater than comparison");
		operators.withIntComparisonOp(">=", (s1, s2) -> unwrapI(s1) >= unwrapI(s2), "Integer greater than or equal to comparison");

		operators.withLongComparisonOp("<", (s1, s2) -> unwrapL(s1) < unwrapL(s2), "Long integer less than comparison");
		operators.withLongComparisonOp("<=", (s1, s2) -> unwrapL(s1) <= unwrapL(s2), "Long integer less than or equal to comparison");
		operators.withLongComparisonOp(">", (s1, s2) -> unwrapL(s1) > unwrapL(s2), "Long integer greater than comparison");
		operators.withLongComparisonOp(">=", (s1, s2) -> unwrapL(s1) >= unwrapL(s2), "Long integer greater than or equal to comparison");

		operators.withFloatComparisonOp("<", (s1, s2) -> unwrapF(s1) < unwrapF(s2), "Floating-point less than comparison");
		operators.withFloatComparisonOp("<=", (s1, s2) -> unwrapF(s1) <= unwrapF(s2), "Floating-point less than or equal to comparison");
		operators.withFloatComparisonOp(">", (s1, s2) -> unwrapF(s1) > unwrapF(s2), "Floating-point greater than comparison");
		operators.withFloatComparisonOp(">=", (s1, s2) -> unwrapF(s1) >= unwrapF(s2), "Floating-point greater than or equal to comparison");

		operators.withDoubleComparisonOp("<", (s1, s2) -> unwrapD(s1) < unwrapD(s2),
			"Double-precision floating-point less than comparison");
		operators.withDoubleComparisonOp("<=", (s1, s2) -> unwrapD(s1) <= unwrapD(s2),
			"Double-precision floating-point less than or equal to comparison");
		operators.withDoubleComparisonOp(">", (s1, s2) -> unwrapD(s1) > unwrapD(s2),
			"Double-precision floating-point greater than comparison");
		operators.withDoubleComparisonOp(">=", (s1, s2) -> unwrapD(s1) >= unwrapD(s2),
			"Double-precision floating-point greater than or equal to comparison");

		// Comparable comparison ops
		class ComparableComparisonOp implements BinaryOp<Comparable<?>, Comparable<?>, Boolean> {
			private final String theName;
			private final String theDescription;
			private final IntPredicate theTest;

			public ComparableComparisonOp(String name, String description, IntPredicate test) {
				theName = name;
				theDescription = description + " comparison between java.lang.Comparables";
				theTest = test;
			}

			@Override
			public String getDescription() {
				return theDescription;
			}

			@Override
			public Class<Boolean> getTargetSuperType() {
				return boolean.class;
			}

			@Override
			public <EX extends Throwable> TypeToken<Boolean> getTargetType(TypeToken<? extends Comparable<?>> leftOpType,
				TypeToken<? extends Comparable<?>> rightOpType, LocatedFilePosition position, int length,
					ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws EX {
				TypeToken<?> leftTarget = leftOpType.resolveType(Comparable.class.getTypeParameters()[0]);
				if (!TypeTokens.get().isAssignable(leftTarget, rightOpType)) {
					exHandler.handle1(new ExpressoInterpretationException(
						"Comparable comparison cannot be used for incompatible types " + leftTarget + " and " + rightOpType, position,
						length));
					return null;
				}
				return TypeTokens.get().BOOLEAN;
			}

			@Override
			public Boolean apply(Comparable<?> source, Comparable<?> other) {
				// Put nulls at the end
				if (source == null) {
					if (other == null)
						return theTest.test(0);
					else
						return theTest.test(1);
				} else if (other == null)
					return theTest.test(-1);
				else
					return theTest.test(((Comparable<Object>) source).compareTo(other));
			}

			@Override
			public String canReverse(Comparable<?> currentSource, Comparable<?> other, Boolean value) {
				return "Comparison operations cannot be reversed";
			}

			@Override
			public Comparable<?> reverse(Comparable<?> currentSource, Comparable<?> other, Boolean value) {
				throw new IllegalStateException("Comparison operations cannot be reversed");
			}

			@Override
			public String toString() {
				return theName;
			}
		}
		operators.with("<", (Class<Comparable<?>>) (Class<?>) Comparable.class, (Class<Comparable<?>>) (Class<?>) Comparable.class,
			new ComparableComparisonOp("<", "Less than", i -> i < 0));
		operators.with("<=", (Class<Comparable<?>>) (Class<?>) Comparable.class, (Class<Comparable<?>>) (Class<?>) Comparable.class,
			new ComparableComparisonOp("<=", "Less than or equal", i -> i <= 0));
		operators.with(">", (Class<Comparable<?>>) (Class<?>) Comparable.class, (Class<Comparable<?>>) (Class<?>) Comparable.class,
			new ComparableComparisonOp(">", "Greater than", i -> i > 0));
		operators.with(">=", (Class<Comparable<?>>) (Class<?>) Comparable.class, (Class<Comparable<?>>) (Class<?>) Comparable.class,
			new ComparableComparisonOp(">=", "Greater than or equal", i -> i >= 0));

		// Bit shifting
		operators.withIntArithmeticOp("<<", (s1, s2) -> unwrapI(s1) << unwrapI(s2), (s, s2, r) -> unwrapI(r) >>> unwrapI(s2), null,
			"Integer left bit-shift operator");
		operators.withIntArithmeticOp(">>", (s1, s2) -> unwrapI(s1) >> unwrapI(s2), (s, s2, r) -> unwrapI(r) << unwrapI(s2), null,
			"Integer right bit-shift operator");
		operators.withIntArithmeticOp(">>>", (s1, s2) -> unwrapI(s1) >>> unwrapI(s2), (s, s2, r) -> unwrapI(r) << unwrapI(s2), null,
			"Integer unsigned right bit-shift operator");
		operators.withLongArithmeticOp("<<", (s1, s2) -> unwrapL(s1) << unwrapL(s2), (s, s2, r) -> unwrapL(r) >>> unwrapL(s2), null,
			"Long integer left bit-shift operator");
		operators.withLongArithmeticOp(">>", (s1, s2) -> unwrapL(s1) >> unwrapL(s2), (s, s2, r) -> unwrapL(r) << unwrapL(s2), null,
			"Long integer right bit-shift operator");
		operators.withLongArithmeticOp(">>>", (s1, s2) -> unwrapL(s1) >>> unwrapL(s2), (s, s2, r) -> unwrapL(r) << unwrapL(s2), null,
			"Long integer unsigned right bit-shift operator");

		// Bitwise operators
		operators.withIntArithmeticOp("|", (s1, s2) -> unwrapI(s1) | unwrapI(s2), (s, s2, r) -> unwrapI(r), (s, s2, r) -> {
			int ri = unwrapI(r);
			if ((ri | unwrapI(s2)) != ri)
				return "Invalid bitwise OR reverse";
			return null;
		}, "Integer bitwise OR operator");
		operators.withLongArithmeticOp("|", (s1, s2) -> unwrapL(s1) | unwrapL(s2), (s, s2, r) -> unwrapL(r), (s, s2, r) -> {
			long rl = unwrapL(r);
			if ((rl | unwrapL(s2)) != rl)
				return "Invalid bitwise OR reverse";
			return null;
		}, "Long integer bitwise OR operator");
		operators.withIntArithmeticOp("&", (s1, s2) -> unwrapI(s1) & unwrapI(s2), (s, s2, r) -> unwrapI(r), (s, s2, r) -> {
			int ri = unwrapI(r);
			if ((ri & unwrapI(s2)) != ri)
				return "Invalid bitwise AND reverse";
			return null;
		}, "Integer bitwise AND operator");
		operators.withLongArithmeticOp("&", (s1, s2) -> unwrapL(s1) & unwrapL(s2), (s, s2, r) -> unwrapL(r), (s, s2, r) -> {
			long rl = unwrapL(r);
			if ((rl & unwrapL(s2)) != rl)
				return "Invalid bitwise AND reverse";
			return null;
		}, "Long integer bitwise AND operator");
		operators.withIntArithmeticOp("^", (s1, s2) -> unwrapI(s1) ^ unwrapI(s2), (s, s2, r) -> unwrapI(r) ^ unwrapI(s2), //
			(s, s2, r) -> null, "Integer bitwise XOR operator");
		operators.withLongArithmeticOp("^", (s1, s2) -> unwrapL(s1) ^ unwrapL(s2), (s, s2, r) -> unwrapL(r) ^ unwrapL(s2), //
			(s, s2, r) -> null, "Long integer bitwise XOR operator");

		// String concatenation
		operators.with2("+", String.class, Object.class, String.class, //
			(s1, s2) -> (s1 == null ? "null" : s1) + (s2 == null ? "null" : s2), (s, o, r) -> {
				return r.substring(0, r.length() - (o == null ? 4 : o.toString().length()));
			}, //
			(s, o, r) -> {
				String oStr = o == null ? "null" : o.toString();
				if (r == null || !r.endsWith(oStr))
					return "String does not end with \"" + oStr + "\"";
				return null;
			}, "String concatenation operator");
		operators.with2("+", Object.class, String.class, String.class, //
			(s1, s2) -> (s1 == null ? "null" : s1) + (s2 == null ? "null" : s2), null, (s, s2, r) -> "Cannot reverse string append",
			"String concatenation operator");
		// Unfortunately I don't support general string reversal here, but at least for some simple cases we can
		operators.with2("+", String.class, String.class, String.class, //
			(s1, s2) -> (s1 == null ? "null" : s1) + (s2 == null ? "null" : s2), (s, s2, r) -> {
				return r.substring(0, r.length() - (s2 == null ? 4 : s2.length()));
			}, (s, s2, r) -> {
				if (r == null || !r.endsWith((s2 == null ? "null" : s2)))
					return "String does not end with \"" + s2 + "\"";
				return null;
			}, "String concatenation operator");
		operators.with2("+", Boolean.class, String.class, String.class, //
			(s1, s2) -> (s1 == null ? "null" : s1) + (s2 == null ? "null" : s2), (s, s2, r) -> {
				return Boolean.valueOf(r.substring(0, r.length() - (s2 == null ? 4 : s2.length())));
			}, (s, s2, r) -> {
				if (r == null || !r.endsWith((s2 == null ? "null" : s2)))
					return "String does not end with \"" + s2 + "\"";
				String begin = r.substring(0, r.length() - (s2 == null ? 4 : s2.length()));
				switch (begin) {
				case "true":
				case "false":
					return null;
				default:
					return "'true' or 'false' expected";
				}
			}, "String concatenation operator");
		String MIN_INT_STR = String.valueOf(Integer.MIN_VALUE).substring(1);
		String MAX_INT_STR = String.valueOf(Integer.MAX_VALUE);
		operators.with2("+", Integer.class, String.class, String.class, //
			(s1, s2) -> (s1 == null ? "null" : s1) + (s2 == null ? "null" : s2), (s, s2, r) -> {
				return Integer.valueOf(r.substring(0, r.length() - (s2 == null ? 4 : s2.length())));
			}, (s, s2, r) -> {
				if (r == null || !r.endsWith((s2 == null ? "null" : s2)))
					return "String does not end with \"" + s2 + "\"";
				String begin = r.substring(0, r.length() - (s2 == null ? 4 : s2.length()));
				int i = 0;
				boolean neg = i < begin.length() && begin.charAt(i) == '-';
				if (neg)
					i++;
				for (; i < begin.length(); i++)
					if (begin.charAt(i) < '0' || begin.charAt(i) > '9')
						return "integer expected";
				if (!neg && StringUtils.compareNumberTolerant(begin, MAX_INT_STR, false, true) > 0)
					return "integer is too large for int type";
				else if (neg && StringUtils.compareNumberTolerant(StringUtils.cheapSubSequence(begin, 1, begin.length()), MIN_INT_STR,
					false, true) > 0)
					return "negative integer is too large for int type";
				return null;
			}, "String concatenation operator");
		String MIN_LONG_STR = String.valueOf(Long.MIN_VALUE).substring(1);
		String MAX_LONG_STR = String.valueOf(Long.MAX_VALUE);
		operators.with2("+", Long.class, String.class, String.class, //
			(s1, s2) -> (s1 == null ? "null" : s1) + (s2 == null ? "null" : s2), (s, s2, r) -> {
				return Long.valueOf(r.substring(0, r.length() - (s2 == null ? 4 : s2.length())));
			}, (s, s2, r) -> {
				if (r == null || !r.endsWith((s2 == null ? "null" : s2)))
					return "String does not end with \"" + s2 + "\"";
				String begin = r.substring(0, r.length() - (s2 == null ? 4 : s2.length()));
				int i = 0;
				boolean neg = i < begin.length() && begin.charAt(i) == '-';
				if (neg)
					i++;
				for (; i < begin.length(); i++)
					if (begin.charAt(i) < '0' || begin.charAt(i) > '9')
						return "integer expected";
				if (!neg && StringUtils.compareNumberTolerant(begin, MAX_LONG_STR, false, true) > 0)
					return "integer is too large for long type";
				else if (neg && StringUtils.compareNumberTolerant(StringUtils.cheapSubSequence(begin, 1, begin.length()), MIN_LONG_STR,
					false, true) > 0)
					return "negative integer is too large for long type";
				return null;
			}, "String concatenation operator");
		operators.with2("+", Double.class, String.class, String.class, //
			(s1, s2) -> (s1 == null ? "null" : s1) + (s2 == null ? "null" : s2), (s, s2, r) -> {
				return Double.valueOf(r.substring(0, r.length() - (s2 == null ? 4 : s2.length())));
			}, (s, s2, r) -> {
				if (r == null || !r.endsWith((s2 == null ? "null" : s2)))
					return "String does not end with \"" + s2 + "\"";
				String begin = r.substring(0, r.length() - (s2 == null ? 4 : s2.length()));
				// Can't think of a better way to do this than to just parse it twice
				try {
					Double.parseDouble(begin);
					return null;
				} catch (NumberFormatException e) {
					return e.getMessage();
				}
			}, "String concatenation operator");
		operators.with2("+", Float.class, String.class, String.class, //
			(s1, s2) -> (s1 == null ? "null" : s1) + (s2 == null ? "null" : s2), (s, s2, r) -> {
				return Float.valueOf(r.substring(0, r.length() - (s2 == null ? 4 : s2.length())));
			}, (s, s2, r) -> {
				if (r == null || !r.endsWith((s2 == null ? "null" : s2)))
					return "String does not end with \"" + s2 + "\"";
				String begin = r.substring(0, r.length() - (s2 == null ? 4 : s2.length()));
				// Can't think of a better way to do this than to just parse it twice
				try {
					Float.parseFloat(begin);
					return null;
				} catch (NumberFormatException e) {
					return e.getMessage();
				}
			}, "String concatenation operator");
		return operators;
	}

	/** A {@link BinaryOperatorSet} that configures a builder to support the standard set of Java binary operators */
	public static final BinaryOperatorSet STANDARD_JAVA = standardJava(build()).build();

	/** Operators by name, target type, primary type, and secondary type */
	private final Map<String, ClassMap<ClassMap<ClassMap<BinaryOp<?, ?, ?>>>>> theOperators;

	private BinaryOperatorSet(Map<String, ClassMap<ClassMap<ClassMap<BinaryOp<?, ?, ?>>>>> operators) {
		theOperators = operators;
	}

	/**
	 * @param operator The name of the operator
	 * @param targetType The type of the operator's output
	 * @return All primary input types that this operator set knows of for which the given operator may be applied
	 */
	public Set<Class<?>> getSupportedPrimaryInputTypes(String operator, Class<?> targetType) {
		ClassMap<ClassMap<ClassMap<BinaryOp<?, ?, ?>>>> ops = theOperators.get(operator);
		if (ops == null || ops.isEmpty())
			return Collections.emptySet();
		Set<Class<?>> primaryTypes = new LinkedHashSet<>();
		for (ClassMap<ClassMap<BinaryOp<?, ?, ?>>> targetOps : ops.getAll(targetType, null))
			primaryTypes.addAll(targetOps.getTopLevelKeys());
		return primaryTypes;
	}

	/**
	 * @param operator The name of the operator
	 * @param targetType The type of the operator's output
	 * @param primaryType The type of the primary input
	 * @return All secondary input types that this operator set knows of for which the given operator may be applied with the given primary
	 *         input
	 */
	public Set<Class<?>> getSupportedSecondaryInputTypes(String operator, Class<?> targetType, Class<?> primaryType) {
		ClassMap<ClassMap<ClassMap<BinaryOp<?, ?, ?>>>> ops = theOperators.get(operator);
		if (ops == null || ops.isEmpty())
			return Collections.emptySet();
		Set<Class<?>> secondaryTypes = new LinkedHashSet<>();
		for (ClassMap<ClassMap<BinaryOp<?, ?, ?>>> targetOps : ops.getAll(targetType, null)) {
			for (ClassMap<BinaryOp<?, ?, ?>> ops2 : targetOps.getAll(primaryType, TypeMatch.SUPER_TYPE))
				secondaryTypes.addAll(ops2.getTopLevelKeys());
		}
		return secondaryTypes;
	}

	/**
	 * @param <S> The primary input type
	 * @param <T> The secondary input type
	 * @param operator The name of the operator
	 * @param targetType The type of the operator's output
	 * @param primaryType The type of the primary input
	 * @param secondaryType The type of the secondary input
	 * @return The binary operator supported by this operator set with the given operator and input types
	 */
	public <S, T> BinaryOp<S, T, ?> getOperator(String operator, Class<?> targetType, Class<S> primaryType, Class<T> secondaryType) {
		ClassMap<ClassMap<ClassMap<BinaryOp<?, ?, ?>>>> ops = theOperators.get(operator);
		if (ops == null || ops.isEmpty())
			return null;
		// Use the most specific type
		BinaryOp<S, T, ?> ret = null;
		for (ClassMap<ClassMap<BinaryOp<?, ?, ?>>> targetOps : ops.getAll(targetType, null)) {
			for (ClassMap<BinaryOp<?, ?, ?>> ops2 : targetOps.getAll(primaryType, TypeMatch.SUPER_TYPE)) {
				BinaryOp<?, ?, ?> op = ops2.getAll(secondaryType, TypeMatch.SUPER_TYPE).peekLast();
				if (op != null)
					ret = (BinaryOp<S, T, ?>) op;
			}
		}
		return ret;
	}

	/** @return A builder pre-configured for all of this operator set's operations */
	public Builder copy() {
		Builder copy = build();
		for (Map.Entry<String, ClassMap<ClassMap<ClassMap<BinaryOp<?, ?, ?>>>>> op : theOperators.entrySet()) {
			for (BiTuple<Class<?>, ClassMap<ClassMap<BinaryOp<?, ?, ?>>>> op2 : op.getValue().getAllEntries()) {
				for (BiTuple<Class<?>, ClassMap<BinaryOp<?, ?, ?>>> op3 : op2.getValue2().getAllEntries()) {
					for (BiTuple<Class<?>, BinaryOp<?, ?, ?>> op4 : op3.getValue2().getAllEntries()) {
						copy.with(op.getKey(), (Class<Object>) op3.getValue1(), (Class<Object>) op4.getValue1(),
							(BinaryOp<Object, Object, ?>) op4.getValue2());
					}
				}
			}
		}
		return copy;
	}

	/** @return A builder that may be configured to support various binary operations */
	public static Builder build() {
		return new Builder();
	}

	/** A builder that may be configured to support various binary operations */
	public static class Builder {
		private final Map<String, ClassMap<ClassMap<ClassMap<BinaryOp<?, ?, ?>>>>> theOperators;

		Builder() {
			theOperators = new LinkedHashMap<>();
		}

		/**
		 * Installs support for an operator
		 *
		 * @param <S> The type of the primary input
		 * @param <T> The type of the secondary input
		 * @param operator The name of the operator
		 * @param primary The type of the primary input
		 * @param secondary The type of the secondary input
		 * @param op The operator to support the given operation
		 * @return This builder
		 */
		public <S, T> Builder with(String operator, Class<S> primary, Class<T> secondary, BinaryOp<S, T, ?> op) {
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>())//
			.computeIfAbsent(op.getTargetSuperType(), () -> new ClassMap<>())//
			.computeIfAbsent(primary, () -> new ClassMap<>())//
			.with(secondary, op);
			return this;
		}

		/**
		 * Installs support for an operator whose output type is the same as that of the primary input
		 *
		 * @param <S> The type of the primary input
		 * @param <T> The type of the secondary input
		 * @param operator The name of the operator
		 * @param primary The type of the primary input
		 * @param secondary The type of the secondary input
		 * @param op The function to apply the operation
		 * @param reverse The function to reverse the operation
		 * @param reverseEnabled The function to detect support for reversing the operation, or null if the operation is always reversible
		 * @param description The description for the operator
		 * @return This builder
		 * @see BinaryOp#canReverse(Object, Object, Object)
		 * @see BinaryOp#reverse(Object, Object, Object)
		 */
		public <S, T> Builder with(String operator, Class<S> primary, Class<T> secondary, BiFunction<? super S, ? super T, ? extends S> op,
			TriFunction<? super S, ? super T, ? super S, ? extends S> reverse,
			TriFunction<? super S, ? super T, ? super S, String> reverseEnabled, String description) {
			with(operator, primary, secondary, //
				BinaryOp.of(operator, TypeTokens.get().of(primary), op, reverse, reverseEnabled, description));
			return this;
		}

		/**
		 * Installs support for an operator
		 *
		 * @param <S> The type of the primary input
		 * @param <T> The type of the secondary input
		 * @param operator The name of the operator
		 * @param primary The type of the primary input
		 * @param secondary The type of the secondary input
		 * @param target The type of the operator output
		 * @param op The function to apply the operation
		 * @param reverse The function to reverse the operation
		 * @param reverseEnabled The function to detect support for reversing the operation, or null if the operation is always reversible
		 * @param description The description for the operator
		 * @return This builder
		 * @see BinaryOp#canReverse(Object, Object, Object)
		 * @see BinaryOp#reverse(Object, Object, Object)
		 */
		public <S, T, V> Builder with2(String operator, Class<S> primary, Class<T> secondary, Class<V> target,
			BiFunction<? super S, ? super T, ? extends V> op, TriFunction<? super S, ? super T, ? super V, ? extends S> reverse,
			TriFunction<? super S, ? super T, ? super V, String> reverseEnabled, String description) {
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>())//
			.computeIfAbsent(target, () -> new ClassMap<>())//
			.computeIfAbsent(primary, () -> new ClassMap<>())//
			.with(secondary, BinaryOp.of2(operator, TypeTokens.get().of(target), op, reverse, reverseEnabled, description));
			return this;
		}

		/**
		 * Installs support for the application of a previously-installed operator with identical primary and secondary input types to a
		 * different primary input type via a cast
		 *
		 * @param <S> The type of the primary input to support
		 * @param <T> The type of the secondary input and of the primary input of the pre-installed operator
		 * @param <V> The output type
		 * @param operator The name of the operator
		 * @param primary The type of the primary input to support
		 * @param secondary The type of the secondary input and of the primary input of the pre-installed operator
		 * @param targetType The type of the operator's output
		 * @param cast The cast to use to convert the newly supported primary input type to that matching the operator
		 * @return This builder
		 */
		public <S, T, V> Builder withCastPrimary(String operator, Class<S> primary, Class<T> secondary, Class<V> targetType,
			CastOp<S, T> cast) {
			BinaryOp<T, T, V> otherOp = (BinaryOp<T, T, V>) theOperators.get(operator).get(targetType, TypeMatch.EXACT)
				.get(secondary, TypeMatch.EXACT).get(secondary, TypeMatch.EXACT);
			BinaryOp<S, T, V> castOp = cast.castPrimary(otherOp);
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>())//
			.computeIfAbsent(targetType, () -> new ClassMap<>())//
			.computeIfAbsent(primary, () -> new ClassMap<>())//
			.with(secondary, castOp);
			return this;
		}

		/**
		 * Installs support for the application of a previously-installed operator with identical primary and secondary input types to a
		 * different secondary input type via a cast
		 *
		 * @param <S> The type of the primary input and of the secondary input of the pre-installed operator
		 * @param <T> The type of the secondary input to support
		 * @param <V> The output type
		 * @param operator The name of the operator
		 * @param primary The type of the primary input and of the secondary input of the pre-installed operator
		 * @param secondary The type of the secondary input to support
		 * @param targetType The type of the operator's output
		 * @param cast The cast to use to convert the newly supported secondary input type to that matching the operator
		 * @return This builder
		 */
		public <S, T, V> Builder withCastSecondary(String operator, Class<S> primary, Class<T> secondary, Class<V> targetType,
			CastOp<T, S> cast) {
			BinaryOp<S, S, V> sourceOp = (BinaryOp<S, S, V>) theOperators.get(operator).get(targetType, TypeMatch.EXACT)
				.get(primary, TypeMatch.EXACT).get(primary, TypeMatch.EXACT);
			BinaryOp<S, T, V> castOp = cast.castSecondary(sourceOp);
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>())//
			.computeIfAbsent(targetType, () -> new ClassMap<>())//
			.computeIfAbsent(primary, () -> new ClassMap<>())//
			.with(secondary, castOp);
			return this;
		}

		/**
		 * Installs support for the application of a previously-installed operator with identical primary and secondary input types to
		 * different input types via a cast
		 *
		 * @param <S> The type of the primary input and of the secondary input of the pre-installed operator
		 * @param <T> The type of the secondary input to support
		 * @param <V> The output type
		 * @param operator The name of the operator
		 * @param primary The type of the primary input and of the secondary input of the pre-installed operator
		 * @param castTarget The output type
		 * @param opTarget The type of the operator's output
		 * @param secondary The type of the secondary input to support
		 * @param primaryCast The cast to use to convert the newly supported primary input type to that matching the operator
		 * @param secondaryCast The cast to use to convert the newly supported secondary input type to that matching the operator
		 * @return This builder
		 */
		public <S, T, T2, V> Builder withCastBoth(String operator, Class<S> primary, Class<T> secondary, Class<T2> castTarget,
			Class<V> opTarget, CastOp<S, T2> primaryCast, CastOp<T, T2> secondaryCast) {
			BinaryOp<T2, T2, V> otherOp = (BinaryOp<T2, T2, V>) theOperators.get(operator).get(opTarget, TypeMatch.EXACT)
				.get(castTarget, TypeMatch.EXACT).get(castTarget, TypeMatch.EXACT);
			BinaryOp<S, T, V> castOp = CastOp.castBoth(otherOp, primaryCast, secondaryCast);
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>())//
			.computeIfAbsent(castTarget, () -> new ClassMap<>())//
			.computeIfAbsent(primary, () -> new ClassMap<>())//
			.with(secondary, castOp);
			return this;
		}

		/**
		 * Installs support for an integer-type arithmetic operation, complete with casts from byte, short, and char
		 *
		 * @param operator The name of the operator
		 * @param op The function to apply the operation
		 * @param reverse The function to reverse the operation
		 * @param canReverse The function to detect support for reversing the operation, or null if the operation is always reversible
		 * @param description The description for the operator
		 * @return This builder
		 */
		public Builder withIntArithmeticOp(String operator, BiFunction<Integer, Integer, Integer> op,
			TriFunction<Integer, Integer, Integer, Integer> reverse, TriFunction<Integer, Integer, Integer, String> canReverse,
			String description) {
			with(operator, Integer.class, Integer.class, op, reverse, canReverse, description);

			withCastPrimary(operator, Character.class, Integer.class, int.class, CastOp.charInt);
			withCastPrimary(operator, Byte.class, Integer.class, int.class, CastOp.byteInt);
			withCastPrimary(operator, Short.class, Integer.class, int.class, CastOp.shortInt);

			withCastSecondary(operator, Integer.class, Character.class, int.class, CastOp.charInt);
			withCastSecondary(operator, Integer.class, Byte.class, int.class, CastOp.byteInt);
			withCastSecondary(operator, Integer.class, Short.class, int.class, CastOp.shortInt);

			// Every possible combination of byte, short, and char
			withCastBoth(operator, Byte.class, Byte.class, Integer.class, Integer.class, CastOp.byteInt, CastOp.byteInt);
			withCastBoth(operator, Short.class, Short.class, Integer.class, Integer.class, CastOp.shortInt, CastOp.shortInt);
			withCastBoth(operator, Character.class, Character.class, Integer.class, Integer.class, CastOp.charInt, CastOp.charInt);
			withCastBoth(operator, Byte.class, Short.class, Integer.class, Integer.class, CastOp.byteInt, CastOp.shortInt);
			withCastBoth(operator, Short.class, Byte.class, Integer.class, Integer.class, CastOp.shortInt, CastOp.byteInt);
			withCastBoth(operator, Byte.class, Character.class, Integer.class, Integer.class, CastOp.byteInt, CastOp.charInt);
			withCastBoth(operator, Character.class, Byte.class, Integer.class, Integer.class, CastOp.charInt, CastOp.byteInt);
			withCastBoth(operator, Short.class, Character.class, Integer.class, Integer.class, CastOp.shortInt, CastOp.charInt);
			withCastBoth(operator, Character.class, Short.class, Integer.class, Integer.class, CastOp.charInt, CastOp.shortInt);

			return this;
		}

		/**
		 * Installs support for a long-type arithmetic operation, complete with casts from byte, short, char, and int
		 *
		 * @param operator The name of the operator
		 * @param op The function to apply the operation
		 * @param reverse The function to reverse the operation
		 * @param canReverse The function to detect support for reversing the operation, or null if the operation is always reversible
		 * @param description The description for the operator
		 * @return This builder
		 */
		public Builder withLongArithmeticOp(String operator, BiFunction<Long, Long, Long> op, TriFunction<Long, Long, Long, Long> reverse,
			TriFunction<Long, Long, Long, String> canReverse, String description) {
			with(operator, Long.class, Long.class, op, reverse, canReverse, description);

			withCastPrimary(operator, Character.class, Long.class, long.class, CastOp.charLong);
			withCastPrimary(operator, Byte.class, Long.class, long.class, CastOp.byteLong);
			withCastPrimary(operator, Short.class, Long.class, long.class, CastOp.shortLong);
			withCastPrimary(operator, Integer.class, Long.class, long.class, CastOp.intLong);

			withCastSecondary(operator, Long.class, Character.class, long.class, CastOp.charLong);
			withCastSecondary(operator, Long.class, Byte.class, long.class, CastOp.byteLong);
			withCastSecondary(operator, Long.class, Short.class, long.class, CastOp.shortLong);
			withCastSecondary(operator, Long.class, Integer.class, long.class, CastOp.intLong);

			return this;
		}

		/**
		 * Installs support for a float-type arithmetic operation, complete with casts from byte, short, char, int, and long
		 *
		 * @param operator The name of the operator
		 * @param op The function to apply the operation
		 * @param reverse The function to reverse the operation
		 * @param canReverse The function to detect support for reversing the operation, or null if the operation is always reversible
		 * @param description The description for the operator
		 * @return This builder
		 */
		public Builder withFloatArithmeticOp(String operator, BiFunction<Float, Float, Float> op,
			TriFunction<Float, Float, Float, Float> reverse, TriFunction<Float, Float, Float, String> canReverse, String description) {
			with(operator, Float.class, Float.class, op, reverse, canReverse, description);

			withCastPrimary(operator, Character.class, Float.class, float.class, CastOp.charFloat);
			withCastPrimary(operator, Byte.class, Float.class, float.class, CastOp.byteFloat);
			withCastPrimary(operator, Short.class, Float.class, float.class, CastOp.shortFloat);
			withCastPrimary(operator, Integer.class, Float.class, float.class, CastOp.intFloat);
			withCastPrimary(operator, Long.class, Float.class, float.class, CastOp.longFloat);

			withCastSecondary(operator, Float.class, Character.class, float.class, CastOp.charFloat);
			withCastSecondary(operator, Float.class, Byte.class, float.class, CastOp.byteFloat);
			withCastSecondary(operator, Float.class, Short.class, float.class, CastOp.shortFloat);
			withCastSecondary(operator, Float.class, Integer.class, float.class, CastOp.intFloat);
			withCastSecondary(operator, Float.class, Long.class, float.class, CastOp.longFloat);

			return this;
		}

		/**
		 * Installs support for a double-type arithmetic operation, complete with casts from byte, short, char, int, long, and float
		 *
		 * @param operator The name of the operator
		 * @param op The function to apply the operation
		 * @param reverse The function to reverse the operation
		 * @param canReverse The function to detect support for reversing the operation, or null if the operation is always reversible
		 * @param description The description for the operator
		 * @return This builder
		 */
		public Builder withDoubleArithmeticOp(String operator, BiFunction<Double, Double, Double> op,
			TriFunction<Double, Double, Double, Double> reverse, TriFunction<Double, Double, Double, String> canReverse,
			String description) {
			with(operator, Double.class, Double.class, op, reverse, canReverse, description);

			withCastPrimary(operator, Character.class, Double.class, double.class, CastOp.charDouble);
			withCastPrimary(operator, Byte.class, Double.class, double.class, CastOp.byteDouble);
			withCastPrimary(operator, Short.class, Double.class, double.class, CastOp.shortDouble);
			withCastPrimary(operator, Integer.class, Double.class, double.class, CastOp.intDouble);
			withCastPrimary(operator, Long.class, Double.class, double.class, CastOp.longDouble);
			withCastPrimary(operator, Float.class, Double.class, double.class, CastOp.floatDouble);

			withCastSecondary(operator, Double.class, Character.class, double.class, CastOp.charDouble);
			withCastSecondary(operator, Double.class, Byte.class, double.class, CastOp.byteDouble);
			withCastSecondary(operator, Double.class, Short.class, double.class, CastOp.shortDouble);
			withCastSecondary(operator, Double.class, Integer.class, double.class, CastOp.intDouble);
			withCastSecondary(operator, Double.class, Long.class, double.class, CastOp.longDouble);
			withCastSecondary(operator, Double.class, Float.class, double.class, CastOp.floatDouble);

			return this;
		}

		/**
		 * Installs support for an integer-type comparison operation, complete with casts from byte, short, and char
		 *
		 * @param operator The name of the operator
		 * @param op The function to apply the operation
		 * @param description The description for the operator
		 * @return This builder
		 */
		public Builder withIntComparisonOp(String operator, BiFunction<Integer, Integer, Boolean> op, String description) {
			with2(operator, Integer.class, Integer.class, Boolean.class, op, null, (s, s2, v) -> "Comparison operations cannot be reversed",
				description);

			withCastSecondary(operator, Integer.class, Character.class, boolean.class, CastOp.charInt);
			withCastSecondary(operator, Integer.class, Byte.class, boolean.class, CastOp.byteInt);
			withCastSecondary(operator, Integer.class, Short.class, boolean.class, CastOp.shortInt);

			// Every possible combination of byte, short, and char
			withCastBoth(operator, Byte.class, Byte.class, Integer.class, boolean.class, CastOp.byteInt, CastOp.byteInt);
			withCastBoth(operator, Short.class, Short.class, Integer.class, boolean.class, CastOp.shortInt, CastOp.shortInt);
			withCastBoth(operator, Character.class, Character.class, Integer.class, boolean.class, CastOp.charInt, CastOp.charInt);
			withCastBoth(operator, Byte.class, Short.class, Integer.class, boolean.class, CastOp.byteInt, CastOp.shortInt);
			withCastBoth(operator, Short.class, Byte.class, Integer.class, boolean.class, CastOp.shortInt, CastOp.byteInt);
			withCastBoth(operator, Byte.class, Character.class, Integer.class, boolean.class, CastOp.byteInt, CastOp.charInt);
			withCastBoth(operator, Character.class, Byte.class, Integer.class, boolean.class, CastOp.charInt, CastOp.byteInt);
			withCastBoth(operator, Short.class, Character.class, Integer.class, boolean.class, CastOp.shortInt, CastOp.charInt);
			withCastBoth(operator, Character.class, Short.class, Integer.class, boolean.class, CastOp.charInt, CastOp.shortInt);

			return this;
		}

		/**
		 * Installs support for a long-type comparison operation, complete with casts from byte, short, char, and int
		 *
		 * @param operator The name of the operator
		 * @param op The function to apply the operation
		 * @param description The description for the operator
		 * @return This builder
		 */
		public Builder withLongComparisonOp(String operator, BiFunction<Long, Long, Boolean> op, String description) {
			with2(operator, Long.class, Long.class, Boolean.class, op, null, (s, s2, v) -> "Comparison operations cannot be reversed",
				description);

			withCastSecondary(operator, Long.class, Character.class, boolean.class, CastOp.charLong);
			withCastSecondary(operator, Long.class, Byte.class, boolean.class, CastOp.byteLong);
			withCastSecondary(operator, Long.class, Short.class, boolean.class, CastOp.shortLong);
			withCastSecondary(operator, Long.class, Integer.class, boolean.class, CastOp.intLong);

			withCastPrimary(operator, Integer.class, Long.class, boolean.class, CastOp.intLong);

			return this;
		}

		/**
		 * Installs support for a float-type comparison operation, complete with casts from byte, short, char, int, and long
		 *
		 * @param operator The name of the operator
		 * @param op The function to apply the operation
		 * @param description The description for the operator
		 * @return This builder
		 */
		public Builder withFloatComparisonOp(String operator, BiFunction<Float, Float, Boolean> op, String description) {
			with2(operator, Float.class, Float.class, Boolean.class, op, null, (s, s2, v) -> "Comparison operations cannot be reversed",
				description);

			withCastSecondary(operator, Float.class, Character.class, boolean.class, CastOp.charFloat);
			withCastSecondary(operator, Float.class, Byte.class, boolean.class, CastOp.byteFloat);
			withCastSecondary(operator, Float.class, Short.class, boolean.class, CastOp.shortFloat);
			withCastSecondary(operator, Float.class, Integer.class, boolean.class, CastOp.intFloat);
			withCastSecondary(operator, Float.class, Long.class, boolean.class, CastOp.longFloat);

			withCastPrimary(operator, Integer.class, Float.class, boolean.class, CastOp.intFloat);
			withCastPrimary(operator, Long.class, Float.class, boolean.class, CastOp.longFloat);

			return this;
		}

		/**
		 * Installs support for a double-type comparison operation, complete with casts from byte, short, char, int, long, and float
		 *
		 * @param operator The name of the operator
		 * @param op The function to apply the operation
		 * @param description The description for the operator
		 * @return This builder
		 */
		public Builder withDoubleComparisonOp(String operator, BiFunction<Double, Double, Boolean> op, String description) {
			with2(operator, Double.class, Double.class, Boolean.class, op, null, (s, s2, v) -> "Comparison operations cannot be reversed",
				description);

			withCastSecondary(operator, Double.class, Character.class, boolean.class, CastOp.charDouble);
			withCastSecondary(operator, Double.class, Byte.class, boolean.class, CastOp.byteDouble);
			withCastSecondary(operator, Double.class, Short.class, boolean.class, CastOp.shortDouble);
			withCastSecondary(operator, Double.class, Integer.class, boolean.class, CastOp.intDouble);
			withCastSecondary(operator, Double.class, Long.class, boolean.class, CastOp.longDouble);
			withCastSecondary(operator, Double.class, Float.class, boolean.class, CastOp.floatDouble);

			withCastPrimary(operator, Integer.class, Double.class, boolean.class, CastOp.intDouble);
			withCastPrimary(operator, Long.class, Double.class, boolean.class, CastOp.longDouble);
			withCastPrimary(operator, Float.class, Double.class, boolean.class, CastOp.floatDouble);

			return this;
		}

		/** @return A binary operator set with the support installed in this builder */
		public BinaryOperatorSet build() {
			Map<String, ClassMap<ClassMap<ClassMap<BinaryOp<?, ?, ?>>>>> operators = new LinkedHashMap<>();
			for (Map.Entry<String, ClassMap<ClassMap<ClassMap<BinaryOp<?, ?, ?>>>>> op : theOperators.entrySet()) {
				ClassMap<ClassMap<ClassMap<BinaryOp<?, ?, ?>>>> ops = new ClassMap<>();
				operators.put(op.getKey(), ops);
				for (BiTuple<Class<?>, ClassMap<ClassMap<BinaryOp<?, ?, ?>>>> op2 : op.getValue().getAllEntries()) {
					ClassMap<ClassMap<BinaryOp<?, ?, ?>>> ops2 = new ClassMap<>();
					ops.put(op2.getValue1(), ops2);
					for (BiTuple<Class<?>, ClassMap<BinaryOp<?, ?, ?>>> op3 : op2.getValue2().getAllEntries())
						ops2.with(op3.getValue1(), op3.getValue2().copy());
				}
			}
			return new BinaryOperatorSet(operators);
		}
	}

	static boolean unwrapBool(Boolean b) {
		return b != null && b.booleanValue();
	}

	static int unwrapI(Number i) {
		return i == null ? 0 : i.intValue();
	}

	static long unwrapL(Number i) {
		return i == null ? 0L : i.longValue();
	}

	static double unwrapD(Number i) {
		return i == null ? 0.0 : i.doubleValue();
	}

	static float unwrapF(Number i) {
		return i == null ? 0.0f : i.floatValue();
	}

	static byte unwrapByte(Number i) {
		return i == null ? (byte) 0 : i.byteValue();
	}

	static short unwrapS(Number i) {
		return i == null ? (short) 0 : i.shortValue();
	}

	static char unwrapC(Character i) {
		return i == null ? (char) 0 : i.charValue();
	}
}
