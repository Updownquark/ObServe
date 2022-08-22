package org.observe.expresso.ops;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.qommons.BiTuple;
import org.qommons.ClassMap;
import org.qommons.ClassMap.TypeMatch;
import org.qommons.StringUtils;
import org.qommons.TriFunction;

public class BinaryOperatorSet {
	public interface BinaryOp<S, T, V> {
		Class<V> getTargetType();

		V apply(S source, T other);

		String canReverse(S currentSource, T other, V value);

		S reverse(S currentSource, T other, V value);

		static <S, T> BinaryOp<S, T, S> of(Class<S> type, BiFunction<? super S, ? super T, ? extends S> op,
			TriFunction<? super S, ? super T, ? super S, ? extends S> reverse,
			TriFunction<? super S, ? super T, ? super S, String> reverseEnabled) {
			return of2(type, op, reverse, reverseEnabled);
		}

		static <S, T, V> BinaryOp<S, T, V> of2(Class<V> type, BiFunction<? super S, ? super T, ? extends V> op,
			TriFunction<? super S, ? super T, ? super V, ? extends S> reverse,
			TriFunction<? super S, ? super T, ? super V, String> reverseEnabled) {
			return new BinaryOp<S, T, V>() {
				@Override
				public Class<V> getTargetType() {
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
			};
		}
	}

	public interface CastOp<S, T> {
		CastOp<Byte, Short> byteShort = CastOp.of(s -> unwrapS(s),
			v -> (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE) ? "Short value is too large for a byte" : null, v -> unwrapByte(v));
		CastOp<Byte, Integer> byteInt = CastOp.of(s -> unwrapI(s),
			v -> (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE) ? "Integer value is too large for a byte" : null, v -> unwrapByte(v));
		CastOp<Byte, Long> byteLong = CastOp.of(s -> unwrapL(s),
			v -> (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE) ? "Long value is too large for a byte" : null, v -> unwrapByte(v));
		CastOp<Byte, Character> byteChar = CastOp.of(s -> s == null ? (char) 0 : (char) s.byteValue(),
			v -> v > Byte.MAX_VALUE ? "Character value is too large for a byte" : null, v -> v == null ? (byte) 0 : (byte) v.charValue());
		CastOp<Byte, Float> byteFloat = CastOp.of(s -> unwrapF(s), v -> {
			if (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE)
				return "Float value is too large for a byte";
			float rem = v % 1;
			if (rem != 0)
				return "Float value has decimal--cannot be assigned to an integer";
			return null;
		}, v -> unwrapByte(v));
		CastOp<Byte, Double> byteDouble = CastOp.of(s -> unwrapD(s), v -> {
			if (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE)
				return "Double value is too large for a byte";
			double rem = v % 1;
			if (rem != 0)
				return "Double value has decimal--cannot be assigned to an integer";
			return null;
		}, v -> unwrapByte(v));

		CastOp<Short, Integer> shortInt = CastOp.of(s -> unwrapI(s),
			v -> (v < Short.MIN_VALUE || v > Short.MAX_VALUE) ? "Integer value is too large for a short" : null, v -> unwrapS(v));
		CastOp<Short, Long> shortLong = CastOp.of(s -> unwrapL(s),
			v -> (v < Short.MIN_VALUE || v > Short.MAX_VALUE) ? "Long value is too large for a short" : null, v -> unwrapS(v));
		CastOp<Short, Character> shortChar = CastOp.of(s -> s == null ? (char) 0 : (char) s.shortValue(),
			v -> v > Short.MAX_VALUE ? "Character value is too large for a short" : null,
				v -> v == null ? (short) 0 : (short) v.charValue());
		CastOp<Short, Float> shortFloat = CastOp.of(s -> unwrapF(s), v -> {
			if (v < Short.MIN_VALUE || v > Short.MAX_VALUE)
				return "Float value is too large for a short";
			float rem = v % 1;
			if (rem != 0)
				return "Float value has decimal--cannot be assigned to an integer";
			return null;
		}, v -> unwrapS(v));
		CastOp<Short, Double> shortDouble = CastOp.of(s -> unwrapD(s), v -> {
			if (v < Short.MIN_VALUE || v > Short.MAX_VALUE)
				return "Double value is too large for a short";
			double rem = v % 1;
			if (rem != 0)
				return "Double value has decimal--cannot be assigned to an integer";
			return null;
		}, v -> unwrapS(v));

		CastOp<Integer, Long> intLong = CastOp.of(s -> unwrapL(s),
			v -> (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) ? "Long value is too large for an integer" : null, v -> unwrapI(v));
		CastOp<Integer, Character> intChar = CastOp.of(s -> s == null ? (char) 0 : (char) s.intValue(), null,
			v -> v == null ? (int) 0 : (int) v.charValue());
		CastOp<Integer, Float> intFloat = CastOp.of(s -> unwrapF(s), v -> {
			if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE)
				return "Float value is too large for an integer";
			float rem = v % 1;
			if (rem != 0)
				return "Float value has decimal--cannot be assigned to an integer";
			return null;
		}, v -> unwrapI(v));
		CastOp<Integer, Double> intDouble = CastOp.of(s -> unwrapD(s), v -> {
			if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE)
				return "Double value is too large for an integer";
			double rem = v % 1;
			if (rem != 0)
				return "Double value has decimal--cannot be assigned to an integer";
			return null;
		}, v -> unwrapI(v));

		CastOp<Long, Character> longChar = CastOp.of(s -> s == null ? (char) 0 : (char) s.longValue(), null,
			v -> v == null ? (long) 0 : (long) v.charValue());
		CastOp<Long, Float> longFloat = CastOp.of(s -> unwrapF(s), v -> {
			if (v < Long.MIN_VALUE || v > Long.MAX_VALUE)
				return "Float value is too large for a long";
			float rem = v % 1;
			if (rem != 0)
				return "Float value has decimal--cannot be assigned to an integer";
			return null;
		}, v -> unwrapL(v));
		CastOp<Long, Double> longDouble = CastOp.of(s -> unwrapD(s), v -> {
			if (v < Long.MIN_VALUE || v > Long.MAX_VALUE)
				return "Double value is too large for a long";
			double rem = v % 1;
			if (rem != 0)
				return "Double value has decimal--cannot be assigned to an integer";
			return null;
		}, v -> unwrapL(v));

		CastOp<Character, Short> charShort = CastOp.of(s -> s == null ? (short) 0 : (short) s.charValue(),
			v -> v < 0 ? "A character cannot be negative" : null, v -> v == null ? (char) 0 : (char) v.shortValue());
		CastOp<Character, Integer> charInt = CastOp.of(s -> s == null ? (int) 0 : (int) s.charValue(), v -> {
			if (v < 0)
				return "A character cannot be negative";
			else if (v > Character.MAX_VALUE)
				return "Integer value is to large for a character";
			else
				return null;
		}, v -> v == null ? (char) 0 : (char) v.intValue());
		CastOp<Character, Long> charLong = CastOp.of(s -> s == null ? (long) 0 : (long) s.charValue(), v -> {
			if (v < 0)
				return "A character cannot be negative";
			else if (v > Character.MAX_VALUE)
				return "Long value is to large for a character";
			else
				return null;
		}, v -> v == null ? (char) 0 : (char) v.intValue());
		CastOp<Character, Float> charFloat = CastOp.of(s -> s == null ? (float) 0 : (float) s.charValue(), v -> {
			if (v < 0)
				return "A character cannot be negative";
			else if (v > Character.MAX_VALUE)
				return "Float value is to large for a character";
			float rem = v % 1;
			if (rem != 0)
				return "Float value has decimal--cannot be assigned to a character";
			return null;
		}, v -> v == null ? (char) 0 : (char) v.intValue());
		CastOp<Character, Double> charDouble = CastOp.of(s -> s == null ? (double) 0 : (double) s.charValue(), v -> {
			if (v < 0)
				return "A character cannot be negative";
			else if (v > Character.MAX_VALUE)
				return "Double value is to large for a character";
			double rem = v % 1;
			if (rem != 0)
				return "Double value has decimal--cannot be assigned to a character";
			return null;
		}, v -> v == null ? (char) 0 : (char) v.intValue());

		CastOp<Float, Double> floatDouble = CastOp.of(s -> unwrapD(s), v -> {
			if (v < Float.MIN_VALUE || v > Float.MAX_VALUE)
				return "Float value is too large for a byte";
			float f = v.floatValue();
			if (f != v)
				return "Double value has decimal precision that cannot be stored in a float";
			return null;
		}, v -> unwrapF(v));

		T cast(S source);

		String canReverse(T target);

		S reverse(T target);

		default <V> BinaryOp<S, T, V> castSource(BinaryOp<T, T, V> op) {
			return new BinaryOp<S, T, V>() {
				@Override
				public Class<V> getTargetType() {
					return op.getTargetType();
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
			};
		}

		default <V> BinaryOp<T, S, V> castOther(BinaryOp<T, T, V> op) {
			return new BinaryOp<T, S, V>() {
				@Override
				public Class<V> getTargetType() {
					return op.getTargetType();
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
			};
		}

		static <S, T, T2, V> BinaryOp<S, T, V> castBoth(BinaryOp<T2, T2, V> op, CastOp<S, T2> sourceCast, CastOp<T, T2> otherCast) {
			return new BinaryOp<S, T, V>() {
				@Override
				public Class<V> getTargetType() {
					return op.getTargetType();
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
			};
		}

		static <S, T> CastOp<S, T> of(Function<? super S, ? extends T> cast, Function<? super T, String> canReverse,
			Function<? super T, ? extends S> reverse) {
			return new CastOp<S, T>() {
				@Override
				public T cast(S source) {
					return cast.apply(source);
				}

				@Override
				public String canReverse(T target) {
					return canReverse == null ? null : canReverse.apply(target);
				}

				@Override
				public S reverse(T target) {
					return reverse.apply(target);
				}
			};
		}
	}

	public interface BinaryOperatorConfiguration {
		Builder configure(Builder operators);
	}

	public static Builder standardJava(Builder operators) {
		// Do equality first, which is special
		// First, same-type primitive equality
		operators.with("==", Boolean.class, Boolean.class, (b1, b2) -> unwrapBool(b1) == unwrapBool(b2),
			(s, b2, r) -> unwrapBool(b2) ? unwrapBool(r) : !unwrapBool(r), null);
		operators.with("!=", Boolean.class, Boolean.class, (b1, b2) -> unwrapBool(b1) != unwrapBool(b2),
			(s, b2, r) -> unwrapBool(b2) ? !unwrapBool(r) : unwrapBool(r), null);

		operators.with2("==", Integer.class, Integer.class, Boolean.class, (i1, i2) -> unwrapI(i1) == unwrapI(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) // Trying to make the expression true
					return i2;
				else if (unwrapI(s) != unwrapI(i2))
					return s; // Leave it alone--it's already false
				else
					return null; // Don't make up a value to make it false--prevent with the enabled fn
			}, (s, i2, r) -> (!unwrapBool(r) && unwrapI(s) == unwrapI(i2)) ? "Equal expression cannot be negated" : null);
		operators.with2("!=", Integer.class, Integer.class, Boolean.class, (i1, i2) -> unwrapI(i1) != unwrapI(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) {// Trying to make the expression true
					if (unwrapI(s) == unwrapI(i2))
						return s; // Leave it alone--it's already true
					else
						return null; // Don't make up a value to make it true--prevent with the enabled fn
				} else
					return i2;
			}, (s, i2, r) -> (unwrapBool(r) && unwrapI(s) != unwrapI(i2)) ? "Not-equal expression cannot be made true" : null);
		operators.with2("==", Long.class, Long.class, Boolean.class, (i1, i2) -> unwrapL(i1) == unwrapL(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) // Trying to make the expression true
					return i2;
				else if (unwrapL(s) != unwrapL(i2))
					return s; // Leave it alone--it's already false
				else
					return null; // Don't make up a value to make it false--prevent with the enabled fn
			}, (s, i2, r) -> (!unwrapBool(r) && unwrapL(s) == unwrapL(i2)) ? "Equal expression cannot be negated" : null);
		operators.with2("!=", Long.class, Long.class, Boolean.class, (i1, i2) -> unwrapL(i1) != unwrapL(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) {// Trying to make the expression true
					if (unwrapL(s) == unwrapL(i2))
						return s; // Leave it alone--it's already true
					else
						return null; // Don't make up a value to make it true--prevent with the enabled fn
				} else
					return i2;
			}, (s, i2, r) -> (unwrapBool(r) && unwrapL(s) != unwrapL(i2)) ? "Not-equal expression cannot be made true" : null);
		operators.with2("==", Double.class, Double.class, Boolean.class, (i1, i2) -> unwrapD(i1) == unwrapD(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) // Trying to make the expression true
					return i2;
				else if (unwrapD(s) != unwrapD(i2))
					return s; // Leave it alone--it's already false
				else
					return null; // Don't make up a value to make it false--prevent with the enabled fn
			}, (s, i2, r) -> (!unwrapBool(r) && unwrapD(s) == unwrapD(i2)) ? "Equal expression cannot be negated" : null);
		operators.with2("!=", Double.class, Double.class, Boolean.class, (i1, i2) -> unwrapD(i1) != unwrapD(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) {// Trying to make the expression true
					if (unwrapD(s) == unwrapD(i2))
						return s; // Leave it alone--it's already true
					else
						return null; // Don't make up a value to make it true--prevent with the enabled fn
				} else
					return i2;
			}, (s, i2, r) -> (unwrapBool(r) && unwrapD(s) != unwrapD(i2)) ? "Not-equal expression cannot be made true" : null);
		operators.with2("==", Float.class, Float.class, Boolean.class, (i1, i2) -> unwrapF(i1) == unwrapF(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) // Trying to make the expression true
					return i2;
				else if (unwrapF(s) != unwrapF(i2))
					return s; // Leave it alone--it's already false
				else
					return null; // Don't make up a value to make it false--prevent with the enabled fn
			}, (s, i2, r) -> (!unwrapBool(r) && unwrapF(s) == unwrapF(i2)) ? "Equal expression cannot be negated" : null);
		operators.with2("!=", Float.class, Float.class, Boolean.class, (i1, i2) -> unwrapF(i1) != unwrapF(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) {// Trying to make the expression true
					if (unwrapF(s) == unwrapF(i2))
						return s; // Leave it alone--it's already true
					else
						return null; // Don't make up a value to make it true--prevent with the enabled fn
				} else
					return i2;
			}, (s, i2, r) -> (unwrapBool(r) && unwrapF(s) != unwrapF(i2)) ? "Not-equal expression cannot be made true" : null);
		operators.with2("==", Byte.class, Byte.class, Boolean.class, (i1, i2) -> unwrapByte(i1) == unwrapByte(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) // Trying to make the expression true
					return i2;
				else if (unwrapByte(s) != unwrapByte(i2))
					return s; // Leave it alone--it's already false
				else
					return null; // Don't make up a value to make it false--prevent with the enabled fn
			}, (s, i2, r) -> (!unwrapBool(r) && unwrapByte(s) == unwrapByte(i2)) ? "Equal expression cannot be negated" : null);
		operators.with2("!=", Byte.class, Byte.class, Boolean.class, (i1, i2) -> unwrapByte(i1) != unwrapByte(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) {// Trying to make the expression true
					if (unwrapByte(s) == unwrapByte(i2))
						return s; // Leave it alone--it's already true
					else
						return null; // Don't make up a value to make it true--prevent with the enabled fn
				} else
					return i2;
			}, (s, i2, r) -> (unwrapBool(r) && unwrapByte(s) != unwrapByte(i2)) ? "Not-equal expression cannot be made true" : null);
		operators.with2("==", Short.class, Short.class, Boolean.class, (i1, i2) -> unwrapS(i1) == unwrapS(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) // Trying to make the expression true
					return i2;
				else if (unwrapS(s) != unwrapS(i2))
					return s; // Leave it alone--it's already false
				else
					return null; // Don't make up a value to make it false--prevent with the enabled fn
			}, (s, i2, r) -> (!unwrapBool(r) && unwrapS(s) == unwrapS(i2)) ? "Equal expression cannot be negated" : null);
		operators.with2("!=", Short.class, Short.class, Boolean.class, (i1, i2) -> unwrapS(i1) != unwrapS(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) {// Trying to make the expression true
					if (unwrapS(s) == unwrapS(i2))
						return s; // Leave it alone--it's already true
					else
						return null; // Don't make up a value to make it true--prevent with the enabled fn
				} else
					return i2;
			}, (s, i2, r) -> (unwrapBool(r) && unwrapS(s) != unwrapS(i2)) ? "Not-equal expression cannot be made true" : null);
		operators.with2("==", Character.class, Character.class, Boolean.class, (i1, i2) -> unwrapC(i1) == unwrapC(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) // Trying to make the expression true
					return i2;
				else if (unwrapC(s) != unwrapC(i2))
					return s; // Leave it alone--it's already false
				else
					return null; // Don't make up a value to make it false--prevent with the enabled fn
			}, (s, i2, r) -> (!unwrapBool(r) && unwrapC(s) == unwrapC(i2)) ? "Equal expression cannot be negated" : null);
		operators.with2("!=", Character.class, Character.class, Boolean.class, (i1, i2) -> unwrapC(i1) != unwrapC(i2), //
			(s, i2, r) -> {
				if (unwrapBool(r)) {// Trying to make the expression true
					if (unwrapC(s) == unwrapC(i2))
						return s; // Leave it alone--it's already true
					else
						return null; // Don't make up a value to make it true--prevent with the enabled fn
				} else
					return i2;
			}, (s, i2, r) -> (unwrapBool(r) && unwrapC(s) != unwrapC(i2)) ? "Not-equal expression cannot be made true" : null);

		// Use the same-type equality methods above to implement cross-type primitive equal comparisons

		operators.withCastOther("==", Integer.class, Byte.class, CastOp.byteInt);
		operators.withCastOther("!=", Integer.class, Byte.class, CastOp.byteInt);
		operators.withCastOther("==", Integer.class, Short.class, CastOp.shortInt);
		operators.withCastOther("!=", Integer.class, Short.class, CastOp.shortInt);
		operators.withCastOther("==", Integer.class, Character.class, CastOp.charInt);
		operators.withCastOther("!=", Integer.class, Character.class, CastOp.charInt);
		operators.withCastSource("==", Integer.class, Long.class, CastOp.intLong);
		operators.withCastSource("!=", Integer.class, Long.class, CastOp.intLong);
		operators.withCastSource("==", Integer.class, Float.class, CastOp.intFloat);
		operators.withCastSource("!=", Integer.class, Float.class, CastOp.intFloat);
		operators.withCastSource("==", Integer.class, Double.class, CastOp.intDouble);
		operators.withCastSource("!=", Integer.class, Double.class, CastOp.intDouble);

		operators.withCastOther("==", Long.class, Integer.class, CastOp.intLong);
		operators.withCastOther("!=", Long.class, Integer.class, CastOp.intLong);
		operators.withCastOther("==", Long.class, Byte.class, CastOp.byteLong);
		operators.withCastOther("!=", Long.class, Byte.class, CastOp.byteLong);
		operators.withCastOther("==", Long.class, Short.class, CastOp.shortLong);
		operators.withCastOther("!=", Long.class, Short.class, CastOp.shortLong);
		operators.withCastOther("==", Long.class, Character.class, CastOp.charLong);
		operators.withCastOther("!=", Long.class, Character.class, CastOp.charLong);
		operators.withCastSource("==", Long.class, Float.class, CastOp.longFloat);
		operators.withCastSource("!=", Long.class, Float.class, CastOp.longFloat);
		operators.withCastSource("==", Long.class, Double.class, CastOp.longDouble);
		operators.withCastSource("!=", Long.class, Double.class, CastOp.longDouble);

		operators.withCastOther("==", Short.class, Byte.class, CastOp.byteShort);
		operators.withCastOther("!=", Short.class, Byte.class, CastOp.byteShort);
		operators.withCastSource("==", Short.class, Integer.class, CastOp.shortInt);
		operators.withCastSource("!=", Short.class, Integer.class, CastOp.shortInt);
		operators.withCastSource("==", Short.class, Long.class, CastOp.shortLong);
		operators.withCastSource("!=", Short.class, Long.class, CastOp.shortLong);
		operators.withCastSource("==", Short.class, Character.class, CastOp.shortChar);
		operators.withCastSource("!=", Short.class, Character.class, CastOp.shortChar);
		operators.withCastSource("==", Short.class, Float.class, CastOp.shortFloat);
		operators.withCastSource("!=", Short.class, Float.class, CastOp.shortFloat);
		operators.withCastSource("==", Short.class, Double.class, CastOp.shortDouble);
		operators.withCastSource("!=", Short.class, Double.class, CastOp.shortDouble);

		operators.withCastSource("==", Byte.class, Integer.class, CastOp.byteInt);
		operators.withCastSource("!=", Byte.class, Integer.class, CastOp.byteInt);
		operators.withCastSource("==", Byte.class, Short.class, CastOp.byteShort);
		operators.withCastSource("!=", Byte.class, Short.class, CastOp.byteShort);
		operators.withCastSource("==", Byte.class, Long.class, CastOp.byteLong);
		operators.withCastSource("!=", Byte.class, Long.class, CastOp.byteLong);
		operators.withCastSource("==", Byte.class, Character.class, CastOp.byteChar);
		operators.withCastSource("!=", Byte.class, Character.class, CastOp.byteChar);
		operators.withCastSource("==", Byte.class, Float.class, CastOp.byteFloat);
		operators.withCastSource("!=", Byte.class, Float.class, CastOp.byteFloat);
		operators.withCastSource("==", Byte.class, Double.class, CastOp.byteDouble);
		operators.withCastSource("!=", Byte.class, Double.class, CastOp.byteDouble);

		operators.withCastOther("==", Float.class, Long.class, CastOp.longFloat);
		operators.withCastOther("!=", Float.class, Long.class, CastOp.longFloat);
		operators.withCastOther("==", Float.class, Integer.class, CastOp.intFloat);
		operators.withCastOther("!=", Float.class, Integer.class, CastOp.intFloat);
		operators.withCastOther("==", Float.class, Short.class, CastOp.shortFloat);
		operators.withCastOther("!=", Float.class, Short.class, CastOp.shortFloat);
		operators.withCastOther("==", Float.class, Byte.class, CastOp.byteFloat);
		operators.withCastOther("!=", Float.class, Byte.class, CastOp.byteFloat);
		operators.withCastOther("==", Float.class, Character.class, CastOp.charFloat);
		operators.withCastOther("!=", Float.class, Character.class, CastOp.charFloat);
		operators.withCastSource("==", Float.class, Double.class, CastOp.floatDouble);
		operators.withCastSource("!=", Float.class, Double.class, CastOp.floatDouble);

		operators.withCastOther("==", Double.class, Float.class, CastOp.floatDouble);
		operators.withCastOther("!=", Double.class, Float.class, CastOp.floatDouble);
		operators.withCastOther("==", Double.class, Long.class, CastOp.longDouble);
		operators.withCastOther("!=", Double.class, Long.class, CastOp.longDouble);
		operators.withCastOther("==", Double.class, Integer.class, CastOp.intDouble);
		operators.withCastOther("!=", Double.class, Integer.class, CastOp.intDouble);
		operators.withCastOther("==", Double.class, Short.class, CastOp.shortDouble);
		operators.withCastOther("!=", Double.class, Short.class, CastOp.shortDouble);
		operators.withCastOther("==", Double.class, Byte.class, CastOp.byteDouble);
		operators.withCastOther("!=", Double.class, Byte.class, CastOp.byteDouble);
		operators.withCastOther("==", Double.class, Character.class, CastOp.charDouble);
		operators.withCastOther("!=", Double.class, Character.class, CastOp.charDouble);

		operators.withCastOther("==", Character.class, Byte.class, CastOp.byteChar);
		operators.withCastOther("!=", Character.class, Byte.class, CastOp.byteChar);
		operators.withCastSource("==", Character.class, Long.class, CastOp.charLong);
		operators.withCastSource("!=", Character.class, Long.class, CastOp.charLong);
		operators.withCastSource("==", Character.class, Integer.class, CastOp.charInt);
		operators.withCastSource("!=", Character.class, Integer.class, CastOp.charInt);
		operators.withCastSource("==", Character.class, Short.class, CastOp.charShort);
		operators.withCastSource("!=", Character.class, Short.class, CastOp.charShort);
		operators.withCastSource("==", Character.class, Float.class, CastOp.charFloat);
		operators.withCastSource("!=", Character.class, Float.class, CastOp.charFloat);
		operators.withCastSource("==", Character.class, Double.class, CastOp.charDouble);
		operators.withCastSource("!=", Character.class, Double.class, CastOp.charDouble);

		// Now, non-primitive equality
		operators.with2("==", Object.class, Object.class, Boolean.class, (o1, o2) -> o1 == o2, (s, o2, r) -> {
			if (unwrapBool(r)) // Trying to make the expression true
				return o2;
			else
				return s;
		}, (s, o2, r) -> (!unwrapBool(r) && s != o2) ? "Equals expression cannot be made false" : null);
		operators.with2("!=", Object.class, Object.class, Boolean.class, (o1, o2) -> o1 != o2, (s, o2, r) -> {
			if (unwrapBool(r)) // Trying to make the expression true
				return s;
			else
				return o2;
		}, (s, o2, r) -> (unwrapBool(r) && s != o2) ? "Not-equals expression cannot be made true" : null);

		// Boolean ops
		operators.with("||", Boolean.class, Boolean.class, (b1, b2) -> unwrapBool(b1) || unwrapBool(b2), //
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
			}, (s, b2, r) -> (!unwrapBool(r) && unwrapBool(b2)) ? "Or expression cannot be made false" : null);
		operators.with("|", Boolean.class, Boolean.class, (b1, b2) -> unwrapBool(b1) || unwrapBool(b2), //
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
			}, (s, b2, r) -> (!unwrapBool(r) && unwrapBool(b2)) ? "Or expression cannot be made false" : null);
		operators.with("&&", Boolean.class, Boolean.class, (b1, b2) -> unwrapBool(b1) && unwrapBool(b2), //
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
			}, (s, b2, r) -> (!unwrapBool(b2) && unwrapBool(r)) ? "And expression cannot be made true" : null);
		operators.with("&", Boolean.class, Boolean.class, (b1, b2) -> unwrapBool(b1) && unwrapBool(b2), //
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
			}, (s, b2, r) -> (!unwrapBool(b2) && unwrapBool(r)) ? "And expression cannot be made true" : null);
		operators.with("^", Boolean.class, Boolean.class, (b1, b2) -> unwrapBool(b1) ^ unwrapBool(b2), //
			(s, b2, r) -> {
				if (unwrapBool(r)) { // Trying to make the expression true
					return !unwrapBool(b2);
				} else { // Trying to make the expression false
					return unwrapBool(b2);
				}
			}, null);

		// Arithmetic ops
		operators.withIntArithmeticOp("+", (s1, s2) -> unwrapI(s1) + unwrapI(s2), (s, s2, v) -> unwrapI(v) - unwrapI(s2), null);
		operators.withLongArithmeticOp("+", (s1, s2) -> unwrapL(s1) + unwrapL(s2), (s, s2, v) -> unwrapL(v) - unwrapL(s2), null);
		operators.withFloatArithmeticOp("+", (s1, s2) -> unwrapF(s1) + unwrapF(s2), (s, s2, v) -> unwrapF(v) - unwrapF(s2), null);
		operators.withDoubleArithmeticOp("+", (s1, s2) -> unwrapD(s1) + unwrapD(s2), (s, s2, v) -> unwrapD(v) - unwrapD(s2), null);

		operators.withIntArithmeticOp("-", (s1, s2) -> unwrapI(s1) - unwrapI(s2), (s, s2, v) -> unwrapI(v) + unwrapI(s2), null);
		operators.withLongArithmeticOp("-", (s1, s2) -> unwrapL(s1) - unwrapL(s2), (s, s2, v) -> unwrapL(v) + unwrapL(s2), null);
		operators.withFloatArithmeticOp("-", (s1, s2) -> unwrapF(s1) - unwrapF(s2), (s, s2, v) -> unwrapF(v) + unwrapF(s2), null);
		operators.withDoubleArithmeticOp("-", (s1, s2) -> unwrapD(s1) - unwrapD(s2), (s, s2, v) -> unwrapD(v) + unwrapD(s2), null);

		operators.withIntArithmeticOp("*", (s1, s2) -> unwrapI(s1) * unwrapI(s2), (s, s2, v) -> unwrapI(v) / unwrapI(s2), null);
		operators.withLongArithmeticOp("*", (s1, s2) -> unwrapL(s1) * unwrapL(s2), (s, s2, v) -> unwrapL(v) / unwrapL(s2), null);
		operators.withFloatArithmeticOp("*", (s1, s2) -> unwrapF(s1) * unwrapF(s2), (s, s2, v) -> unwrapF(v) / unwrapF(s2), null);
		operators.withDoubleArithmeticOp("*", (s1, s2) -> unwrapD(s1) * unwrapD(s2), (s, s2, v) -> unwrapD(v) / unwrapD(s2), null);

		operators.withIntArithmeticOp("/", (s1, s2) -> unwrapI(s1) / unwrapI(s2), (s, s2, v) -> unwrapI(v) * unwrapI(s2), null);
		operators.withLongArithmeticOp("/", (s1, s2) -> unwrapL(s1) / unwrapL(s2), (s, s2, v) -> unwrapL(v) * unwrapL(s2), null);
		operators.withFloatArithmeticOp("/", (s1, s2) -> unwrapF(s1) / unwrapF(s2), (s, s2, v) -> unwrapF(v) * unwrapF(s2), null);
		operators.withDoubleArithmeticOp("/", (s1, s2) -> unwrapD(s1) / unwrapD(s2), (s, s2, v) -> unwrapD(v) * unwrapD(s2), null);

		operators.withIntArithmeticOp("%", (s1, s2) -> unwrapI(s1) % unwrapI(s2), (s, s2, v) -> unwrapI(v), //
			(s, s2, v) -> Math.abs(unwrapI(v)) >= Math.abs(unwrapI(s2)) ? "Cannot set a modulus to less than the divisor" : null);
		operators.withLongArithmeticOp("%", (s1, s2) -> unwrapL(s1) % unwrapL(s2), (s, s2, v) -> unwrapL(v) * unwrapL(s2), //
			(s, s2, v) -> Math.abs(unwrapL(v)) >= Math.abs(unwrapL(s2)) ? "Cannot set a modulus to less than the divisor" : null);
		operators.withFloatArithmeticOp("%", (s1, s2) -> unwrapF(s1) % unwrapF(s2), (s, s2, v) -> unwrapF(v) * unwrapF(s2), //
			(s, s2, v) -> Math.abs(unwrapF(v)) >= Math.abs(unwrapF(s2)) ? "Cannot set a modulus to less than the divisor" : null);
		operators.withDoubleArithmeticOp("%", (s1, s2) -> unwrapD(s1) % unwrapD(s2), (s, s2, v) -> unwrapD(v) * unwrapD(s2), //
			(s, s2, v) -> Math.abs(unwrapD(v)) >= Math.abs(unwrapD(s2)) ? "Cannot set a modulus to less than the divisor" : null);

		// Comparison ops
		operators.withIntComparisonOp("<", (s1, s2) -> unwrapI(s1) < unwrapI(s2));
		operators.withIntComparisonOp("<=", (s1, s2) -> unwrapI(s1) <= unwrapI(s2));
		operators.withIntComparisonOp(">", (s1, s2) -> unwrapI(s1) > unwrapI(s2));
		operators.withIntComparisonOp(">=", (s1, s2) -> unwrapI(s1) >= unwrapI(s2));

		operators.withLongComparisonOp("<", (s1, s2) -> unwrapL(s1) < unwrapL(s2));
		operators.withLongComparisonOp("<=", (s1, s2) -> unwrapL(s1) <= unwrapL(s2));
		operators.withLongComparisonOp(">", (s1, s2) -> unwrapL(s1) > unwrapL(s2));
		operators.withLongComparisonOp(">=", (s1, s2) -> unwrapL(s1) >= unwrapL(s2));

		operators.withFloatComparisonOp("<", (s1, s2) -> unwrapF(s1) < unwrapF(s2));
		operators.withFloatComparisonOp("<=", (s1, s2) -> unwrapF(s1) <= unwrapF(s2));
		operators.withFloatComparisonOp(">", (s1, s2) -> unwrapF(s1) > unwrapF(s2));
		operators.withFloatComparisonOp(">=", (s1, s2) -> unwrapF(s1) >= unwrapF(s2));

		operators.withDoubleComparisonOp("<", (s1, s2) -> unwrapD(s1) < unwrapD(s2));
		operators.withDoubleComparisonOp("<=", (s1, s2) -> unwrapD(s1) <= unwrapD(s2));
		operators.withDoubleComparisonOp(">", (s1, s2) -> unwrapD(s1) > unwrapD(s2));
		operators.withDoubleComparisonOp(">=", (s1, s2) -> unwrapD(s1) >= unwrapD(s2));

		// Bit shifting
		operators.withIntArithmeticOp("<<", (s1, s2) -> unwrapI(s1) << unwrapI(s2), (s, s2, r) -> unwrapI(r) >>> unwrapI(s2), null);
		operators.withIntArithmeticOp(">>", (s1, s2) -> unwrapI(s1) >> unwrapI(s2), (s, s2, r) -> unwrapI(r) << unwrapI(s2), null);
		operators.withIntArithmeticOp(">>", (s1, s2) -> unwrapI(s1) >>> unwrapI(s2), (s, s2, r) -> unwrapI(r) << unwrapI(s2), null);
		operators.withLongArithmeticOp("<<", (s1, s2) -> unwrapL(s1) << unwrapL(s2), (s, s2, r) -> unwrapL(r) >>> unwrapL(s2), null);
		operators.withLongArithmeticOp(">>", (s1, s2) -> unwrapL(s1) >> unwrapL(s2), (s, s2, r) -> unwrapL(r) << unwrapL(s2), null);
		operators.withLongArithmeticOp(">>", (s1, s2) -> unwrapL(s1) >>> unwrapL(s2), (s, s2, r) -> unwrapL(r) << unwrapL(s2), null);

		// Bitwise operators
		operators.withIntArithmeticOp("|", (s1, s2) -> unwrapI(s1) | unwrapI(s2), (s, s2, r) -> unwrapI(r), (s, s2, r) -> {
			if ((unwrapI(r) & ~unwrapI(s2)) != 0)
				return "Invalid bitwise operator reverse";
			return null;
		});

		// String append
		operators.with2("+", String.class, Object.class, String.class, (s1, s2) -> s1 + s2, null,
			(s, s2, r) -> "Cannot reverse string append");
		operators.with2("+", Object.class, String.class, String.class, (s1, s2) -> s1 + s2, null,
			(s, s2, r) -> "Cannot reverse string append");
		// Unfortunately I don't support general string reversal here, but at least for some simple cases we can
		operators.with2("+", String.class, String.class, String.class, (s1, s2) -> s1 + s2, (s, s2, r) -> {
			return r.substring(0, r.length() - s2.length());
		}, (s, s2, r) -> {
			if (r == null || !r.endsWith(s2))
				return "String does not end with \"" + s2 + "\"";
			return null;
		});
		operators.with2("+", Boolean.class, String.class, String.class, (s1, s2) -> s1 + s2, (s, s2, r) -> {
			return Boolean.valueOf(r.substring(0, r.length() - s2.length()));
		}, (s, s2, r) -> {
			if (r == null || !r.endsWith(s2))
				return "String does not end with \"" + s2 + "\"";
			String begin = r.substring(0, r.length() - s2.length());
			switch (begin) {
			case "true":
			case "false":
				return null;
			default:
				return "'true' or 'false' expected";
			}
		});
		String MIN_INT_STR = String.valueOf(Integer.MIN_VALUE).substring(1);
		String MAX_INT_STR = String.valueOf(Integer.MAX_VALUE);
		operators.with2("+", Integer.class, String.class, String.class, (s1, s2) -> s1 + s2, (s, s2, r) -> {
			return Integer.valueOf(r.substring(0, r.length() - s2.length()));
		}, (s, s2, r) -> {
			if (r == null || !r.endsWith(s2))
				return "String does not end with \"" + s2 + "\"";
			String begin = r.substring(0, r.length() - s2.length());
			int i = 0;
			boolean neg = i < begin.length() && begin.charAt(i) == '-';
			if (neg)
				i++;
			for (; i < begin.length(); i++)
				if (begin.charAt(i) < '0' || begin.charAt(i) > '9')
					return "integer expected";
			if (!neg && StringUtils.compareNumberTolerant(begin, MAX_INT_STR, false, true) > 0)
				return "integer is too large for int type";
			else if (neg
				&& StringUtils.compareNumberTolerant(StringUtils.cheapSubSequence(begin, 1, begin.length()), MIN_INT_STR, false, true) > 0)
				return "negative integer is too large for int type";
			return null;
		});
		String MIN_LONG_STR = String.valueOf(Long.MIN_VALUE).substring(1);
		String MAX_LONG_STR = String.valueOf(Long.MAX_VALUE);
		operators.with2("+", Long.class, String.class, String.class, (s1, s2) -> s1 + s2, (s, s2, r) -> {
			return Long.valueOf(r.substring(0, r.length() - s2.length()));
		}, (s, s2, r) -> {
			if (r == null || !r.endsWith(s2))
				return "String does not end with \"" + s2 + "\"";
			String begin = r.substring(0, r.length() - s2.length());
			int i = 0;
			boolean neg = i < begin.length() && begin.charAt(i) == '-';
			if (neg)
				i++;
			for (; i < begin.length(); i++)
				if (begin.charAt(i) < '0' || begin.charAt(i) > '9')
					return "integer expected";
			if (!neg && StringUtils.compareNumberTolerant(begin, MAX_LONG_STR, false, true) > 0)
				return "integer is too large for long type";
			else if (neg
				&& StringUtils.compareNumberTolerant(StringUtils.cheapSubSequence(begin, 1, begin.length()), MIN_LONG_STR, false, true) > 0)
				return "negative integer is too large for long type";
			return null;
		});
		operators.with2("+", Double.class, String.class, String.class, (s1, s2) -> s1 + s2, (s, s2, r) -> {
			return Double.valueOf(r.substring(0, r.length() - s2.length()));
		}, (s, s2, r) -> {
			if (r == null || !r.endsWith(s2))
				return "String does not end with \"" + s2 + "\"";
			String begin = r.substring(0, r.length() - s2.length());
			// Can't think of a better way to do this than to just parse it twice
			try {
				Double.parseDouble(begin);
				return null;
			} catch (NumberFormatException e) {
				return e.getMessage();
			}
		});
		operators.with2("+", Float.class, String.class, String.class, (s1, s2) -> s1 + s2, (s, s2, r) -> {
			return Float.valueOf(r.substring(0, r.length() - s2.length()));
		}, (s, s2, r) -> {
			if (r == null || !r.endsWith(s2))
				return "String does not end with \"" + s2 + "\"";
			String begin = r.substring(0, r.length() - s2.length());
			// Can't think of a better way to do this than to just parse it twice
			try {
				Float.parseFloat(begin);
				return null;
			} catch (NumberFormatException e) {
				return e.getMessage();
			}
		});
		return operators;
	}

	public static final BinaryOperatorSet STANDARD_JAVA = standardJava(build()).build();

	private final Map<String, ClassMap<ClassMap<BinaryOp<?, ?, ?>>>> theOperators;

	private BinaryOperatorSet(Map<String, ClassMap<ClassMap<BinaryOp<?, ?, ?>>>> operators) {
		theOperators = operators;
	}

	public Set<Class<?>> getSupportedSourceTypes(String operator) {
		ClassMap<ClassMap<BinaryOp<?, ?, ?>>> ops = theOperators.get(operator);
		return ops == null ? Collections.emptySet() : ops.getTopLevelKeys();
	}

	public Set<Class<?>> getSupportedOperandTypes(String operator, Class<?> sourceType) {
		ClassMap<ClassMap<BinaryOp<?, ?, ?>>> ops = theOperators.get(operator);
		ClassMap<BinaryOp<?, ?, ?>> ops2 = ops == null ? null : ops.get(sourceType, TypeMatch.SUPER_TYPE);
		return ops2 == null ? Collections.emptySet() : ops2.getTopLevelKeys();
	}

	public <S, T> BinaryOp<S, T, ?> getOperator(String operator, Class<S> sourceType, Class<T> otherType) {
		ClassMap<ClassMap<BinaryOp<?, ?, ?>>> ops = theOperators.get(operator);
		ClassMap<BinaryOp<?, ?, ?>> ops2 = ops == null ? null : ops.get(sourceType, TypeMatch.SUPER_TYPE);
		return ops2 == null ? null : (BinaryOp<S, T, ?>) ops2.get(otherType, TypeMatch.SUPER_TYPE);
	}

	public Builder copy() {
		Builder copy = build();
		for (Map.Entry<String, ClassMap<ClassMap<BinaryOp<?, ?, ?>>>> op : theOperators.entrySet()) {
			for (BiTuple<Class<?>, ClassMap<BinaryOp<?, ?, ?>>> op2 : op.getValue().getAllEntries()) {
				for (BiTuple<Class<?>, BinaryOp<?, ?, ?>> op3 : op2.getValue2().getAllEntries()) {
					copy.with(op.getKey(), (Class<Object>) op2.getValue1(), (Class<Object>) op3.getValue1(),
						(BinaryOp<Object, Object, ?>) op3.getValue2());
				}
			}
		}
		return copy;
	}

	public static Builder build() {
		return new Builder();
	}

	public static class Builder {
		private final Map<String, ClassMap<ClassMap<BinaryOp<?, ?, ?>>>> theOperators;

		Builder() {
			theOperators = new LinkedHashMap<>();
		}

		public <S, T> Builder with(String operator, Class<S> source, Class<T> other, BinaryOp<S, T, ?> op) {
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>())//
			.computeIfAbsent(source, () -> new ClassMap<>())//
			.with(other, op);
			return this;
		}

		public <S, T> Builder with(String operator, Class<S> source, Class<T> other,
			BiFunction<? super S, ? super T, ? extends S> op, TriFunction<? super S, ? super T, ? super S, ? extends S> reverse,
			TriFunction<? super S, ? super T, ? super S, String> reverseEnabled) {
			with(operator, source, other, //
				BinaryOp.of(source, op, reverse, reverseEnabled));
			return this;
		}

		public <S, T, V> Builder with2(String operator, Class<S> source, Class<T> other, Class<V> target,
			BiFunction<? super S, ? super T, ? extends V> op, TriFunction<? super S, ? super T, ? super V, ? extends S> reverse,
			TriFunction<? super S, ? super T, ? super V, String> reverseEnabled) {
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>())//
			.computeIfAbsent(source, () -> new ClassMap<>())//
			.with(other, BinaryOp.of2(target, op, reverse, reverseEnabled));
			return this;
		}

		public <S, T, V> Builder withCastOther(String operator, Class<S> source, Class<T> other, CastOp<T, S> cast) {
			BinaryOp<S, S, V> sourceOp = (BinaryOp<S, S, V>) theOperators.get(operator).get(source, TypeMatch.EXACT).get(source,
				TypeMatch.EXACT);
			BinaryOp<S, T, V> castOp = cast.castOther(sourceOp);
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>())//
			.computeIfAbsent(source, () -> new ClassMap<>())//
			.with(other, castOp);
			return this;
		}

		public <S, T, V> Builder withCastSource(String operator, Class<S> source, Class<T> other, CastOp<S, T> cast) {
			BinaryOp<T, T, V> otherOp = (BinaryOp<T, T, V>) theOperators.get(operator).get(other, TypeMatch.EXACT).get(other,
				TypeMatch.EXACT);
			BinaryOp<S, T, V> castOp = cast.castSource(otherOp);
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>())//
			.computeIfAbsent(source, () -> new ClassMap<>())//
			.with(other, castOp);
			return this;
		}

		public <S, T, T2, V> Builder withCastBoth(String operator, Class<S> source, Class<T> other, Class<T2> target,
			CastOp<S, T2> sourceCast, CastOp<T, T2> otherCast) {
			BinaryOp<T2, T2, V> otherOp = (BinaryOp<T2, T2, V>) theOperators.get(operator).get(target, TypeMatch.EXACT).get(target,
				TypeMatch.EXACT);
			BinaryOp<S, T, V> castOp = CastOp.castBoth(otherOp, sourceCast, otherCast);
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>())//
			.computeIfAbsent(source, () -> new ClassMap<>())//
			.with(other, castOp);
			return this;
		}

		public Builder withIntArithmeticOp(String operator, BiFunction<Integer, Integer, Integer> op,
			TriFunction<Integer, Integer, Integer, Integer> reverse, TriFunction<Integer, Integer, Integer, String> canReverse) {
			with(operator, Integer.class, Integer.class, op, reverse, canReverse);

			withCastOther(operator, Integer.class, Character.class, CastOp.charInt);
			withCastOther(operator, Integer.class, Byte.class, CastOp.byteInt);
			withCastOther(operator, Integer.class, Short.class, CastOp.shortInt);

			// Every possible combination of byte, short, and char
			withCastBoth(operator, Byte.class, Byte.class, Integer.class, CastOp.byteInt, CastOp.byteInt);
			withCastBoth(operator, Short.class, Short.class, Integer.class, CastOp.shortInt, CastOp.shortInt);
			withCastBoth(operator, Character.class, Character.class, Integer.class, CastOp.charInt, CastOp.charInt);
			withCastBoth(operator, Byte.class, Short.class, Integer.class, CastOp.byteInt, CastOp.shortInt);
			withCastBoth(operator, Short.class, Byte.class, Integer.class, CastOp.shortInt, CastOp.byteInt);
			withCastBoth(operator, Byte.class, Character.class, Integer.class, CastOp.byteInt, CastOp.charInt);
			withCastBoth(operator, Character.class, Byte.class, Integer.class, CastOp.charInt, CastOp.byteInt);
			withCastBoth(operator, Short.class, Character.class, Integer.class, CastOp.shortInt, CastOp.charInt);
			withCastBoth(operator, Character.class, Short.class, Integer.class, CastOp.charInt, CastOp.shortInt);

			return this;
		}

		public Builder withLongArithmeticOp(String operator, BiFunction<Long, Long, Long> op, TriFunction<Long, Long, Long, Long> reverse,
			TriFunction<Long, Long, Long, String> canReverse) {
			with(operator, Long.class, Long.class, op, reverse, canReverse);

			withCastOther(operator, Long.class, Character.class, CastOp.charLong);
			withCastOther(operator, Long.class, Byte.class, CastOp.byteLong);
			withCastOther(operator, Long.class, Short.class, CastOp.shortLong);
			withCastOther(operator, Long.class, Integer.class, CastOp.intLong);

			withCastSource(operator, Integer.class, Long.class, CastOp.intLong);

			return this;
		}

		public Builder withFloatArithmeticOp(String operator, BiFunction<Float, Float, Float> op,
			TriFunction<Float, Float, Float, Float> reverse, TriFunction<Float, Float, Float, String> canReverse) {
			with(operator, Float.class, Float.class, op, reverse, canReverse);

			withCastOther(operator, Float.class, Character.class, CastOp.charFloat);
			withCastOther(operator, Float.class, Byte.class, CastOp.byteFloat);
			withCastOther(operator, Float.class, Short.class, CastOp.shortFloat);
			withCastOther(operator, Float.class, Integer.class, CastOp.intFloat);
			withCastOther(operator, Float.class, Long.class, CastOp.longFloat);

			withCastSource(operator, Integer.class, Float.class, CastOp.intFloat);
			withCastSource(operator, Long.class, Float.class, CastOp.longFloat);

			return this;
		}

		public Builder withDoubleArithmeticOp(String operator, BiFunction<Double, Double, Double> op,
			TriFunction<Double, Double, Double, Double> reverse, TriFunction<Double, Double, Double, String> canReverse) {
			with(operator, Double.class, Double.class, op, reverse, canReverse);

			withCastOther(operator, Double.class, Character.class, CastOp.charDouble);
			withCastOther(operator, Double.class, Byte.class, CastOp.byteDouble);
			withCastOther(operator, Double.class, Short.class, CastOp.shortDouble);
			withCastOther(operator, Double.class, Integer.class, CastOp.intDouble);
			withCastOther(operator, Double.class, Long.class, CastOp.longDouble);
			withCastOther(operator, Double.class, Float.class, CastOp.floatDouble);

			withCastSource(operator, Integer.class, Double.class, CastOp.intDouble);
			withCastSource(operator, Long.class, Double.class, CastOp.longDouble);
			withCastSource(operator, Float.class, Double.class, CastOp.floatDouble);

			return this;
		}

		public Builder withIntComparisonOp(String operator, BiFunction<Integer, Integer, Boolean> op) {
			with2(operator, Integer.class, Integer.class, Boolean.class, op, null,
				(s, s2, v) -> "Comparison operations cannot be reversed");

			withCastOther(operator, Integer.class, Character.class, CastOp.charInt);
			withCastOther(operator, Integer.class, Byte.class, CastOp.byteInt);
			withCastOther(operator, Integer.class, Short.class, CastOp.shortInt);

			// Every possible combination of byte, short, and char
			withCastBoth(operator, Byte.class, Byte.class, Integer.class, CastOp.byteInt, CastOp.byteInt);
			withCastBoth(operator, Short.class, Short.class, Integer.class, CastOp.shortInt, CastOp.shortInt);
			withCastBoth(operator, Character.class, Character.class, Integer.class, CastOp.charInt, CastOp.charInt);
			withCastBoth(operator, Byte.class, Short.class, Integer.class, CastOp.byteInt, CastOp.shortInt);
			withCastBoth(operator, Short.class, Byte.class, Integer.class, CastOp.shortInt, CastOp.byteInt);
			withCastBoth(operator, Byte.class, Character.class, Integer.class, CastOp.byteInt, CastOp.charInt);
			withCastBoth(operator, Character.class, Byte.class, Integer.class, CastOp.charInt, CastOp.byteInt);
			withCastBoth(operator, Short.class, Character.class, Integer.class, CastOp.shortInt, CastOp.charInt);
			withCastBoth(operator, Character.class, Short.class, Integer.class, CastOp.charInt, CastOp.shortInt);

			return this;
		}

		public Builder withLongComparisonOp(String operator, BiFunction<Long, Long, Boolean> op) {
			with2(operator, Long.class, Long.class, Boolean.class, op, null, (s, s2, v) -> "Comparison operations cannot be reversed");

			withCastOther(operator, Long.class, Character.class, CastOp.charLong);
			withCastOther(operator, Long.class, Byte.class, CastOp.byteLong);
			withCastOther(operator, Long.class, Short.class, CastOp.shortLong);
			withCastOther(operator, Long.class, Integer.class, CastOp.intLong);

			withCastSource(operator, Integer.class, Long.class, CastOp.intLong);

			return this;
		}

		public Builder withFloatComparisonOp(String operator, BiFunction<Float, Float, Boolean> op) {
			with2(operator, Float.class, Float.class, Boolean.class, op, null, (s, s2, v) -> "Comparison operations cannot be reversed");

			withCastOther(operator, Float.class, Character.class, CastOp.charFloat);
			withCastOther(operator, Float.class, Byte.class, CastOp.byteFloat);
			withCastOther(operator, Float.class, Short.class, CastOp.shortFloat);
			withCastOther(operator, Float.class, Integer.class, CastOp.intFloat);
			withCastOther(operator, Float.class, Long.class, CastOp.longFloat);

			withCastSource(operator, Integer.class, Float.class, CastOp.intFloat);
			withCastSource(operator, Long.class, Float.class, CastOp.longFloat);

			return this;
		}

		public Builder withDoubleComparisonOp(String operator, BiFunction<Double, Double, Boolean> op) {
			with2(operator, Double.class, Double.class, Boolean.class, op, null, (s, s2, v) -> "Comparison operations cannot be reversed");

			withCastOther(operator, Double.class, Character.class, CastOp.charDouble);
			withCastOther(operator, Double.class, Byte.class, CastOp.byteDouble);
			withCastOther(operator, Double.class, Short.class, CastOp.shortDouble);
			withCastOther(operator, Double.class, Integer.class, CastOp.intDouble);
			withCastOther(operator, Double.class, Long.class, CastOp.longDouble);
			withCastOther(operator, Double.class, Float.class, CastOp.floatDouble);

			withCastSource(operator, Integer.class, Double.class, CastOp.intDouble);
			withCastSource(operator, Long.class, Double.class, CastOp.longDouble);
			withCastSource(operator, Float.class, Double.class, CastOp.floatDouble);

			return this;
		}

		public BinaryOperatorSet build() {
			Map<String, ClassMap<ClassMap<BinaryOp<?, ?, ?>>>> operators = new LinkedHashMap<>();
			for (Map.Entry<String, ClassMap<ClassMap<BinaryOp<?, ?, ?>>>> op : theOperators.entrySet()) {
				ClassMap<ClassMap<BinaryOp<?, ?, ?>>> ops = new ClassMap<>();
				operators.put(op.getKey(), ops);
				for (BiTuple<Class<?>, ClassMap<BinaryOp<?, ?, ?>>> op2 : op.getValue().getAllEntries())
					ops.with(op2.getValue1(), op2.getValue2().copy());
			}
			return new BinaryOperatorSet(operators);
		}
	}

	static boolean unwrapBool(Boolean b) {
		return b == null ? false : b.booleanValue();
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
