package org.observe.expresso.ops;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.qommons.ClassMap;
import org.qommons.ClassMap.TypeMatch;

public class UnaryOperatorSet {
	interface UnaryOp<S, T> {
		Class<T> getTargetType();

		T apply(S source);

		boolean isActionOnly();

		S reverse(T value);

		static <T> UnaryOp<T, T> of(Class<T> type, Function<? super T, ? extends T> op, Function<? super T, ? extends T> reverse) {
			return of2(type, op, reverse);
		}

		static <T> UnaryOp<T, T> ofSym(Class<T> type, Function<T, T> op) {
			return of2(type, op, op);
		}

		static <S, T> UnaryOp<S, T> of2(Class<T> type, Function<? super S, ? extends T> op, Function<? super T, ? extends S> reverse) {
			return new UnaryOp<S, T>() {
				@Override
				public Class<T> getTargetType() {
					return type;
				}

				@Override
				public T apply(S source) {
					return op.apply(source);
				}

				@Override
				public boolean isActionOnly() {
					return false;
				}

				@Override
				public S reverse(T value) {
					return reverse.apply(value);
				}
			};
		}

		static <T> UnaryOp<T, T> identity(Class<T> type) {
			return new UnaryOp<T, T>() {
				@Override
				public Class<T> getTargetType() {
					return type;
				}

				@Override
				public T apply(T source) {
					return source;
				}

				@Override
				public boolean isActionOnly() {
					return false;
				}

				@Override
				public T reverse(T value) {
					return value;
				}
			};
		}

		static <T> UnaryOp<T, T> ofAction(Class<T> type, Function<? super T, ? extends T> op) {
			return new UnaryOp<T, T>() {
				@Override
				public Class<T> getTargetType() {
					return type;
				}

				@Override
				public T apply(T source) {
					return op.apply(source);
				}

				@Override
				public boolean isActionOnly() {
					return true;
				}

				@Override
				public T reverse(T value) {
					throw new IllegalStateException("Operator not supported as a value");
				}
			};
		}
	}

	private final Map<String, ClassMap<UnaryOp<?, ?>>> theOperators;

	private UnaryOperatorSet(Map<String, ClassMap<UnaryOp<?, ?>>> operators) {
		theOperators = operators;
	}

	public Set<Class<?>> getSupportedSourceTypes(String operator) {
		ClassMap<UnaryOp<?, ?>> ops = theOperators.get(operator);
		return ops == null ? Collections.emptySet() : ops.getTopLevelKeys();
	}

	public <T> UnaryOp<T, ?> getOperator(String operator, Class<T> type) {
		ClassMap<UnaryOp<?, ?>> ops = theOperators.get(operator);
		return ops == null ? null : (UnaryOp<T, ?>) ops.get(type, TypeMatch.SUPER_TYPE);
	}

	public static Builder build() {
		return new Builder();
	}

	public static class Builder {
		private final Map<String, ClassMap<UnaryOp<?, ?>>> theOperators;

		Builder() {
			theOperators = new LinkedHashMap<>();
		}

		public <T> Builder with(String operator, Class<T> type, Function<? super T, ? extends T> op,
			Function<? super T, ? extends T> reverse) {
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>()).with(type, UnaryOp.of(type, op, reverse));
			return this;
		}

		public <S, T> Builder with2(String operator, Class<S> source, Class<T> target, Function<? super S, ? extends T> op,
			Function<? super T, ? extends S> reverse) {
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>()).with(source, UnaryOp.of2(target, op, reverse));
			return this;
		}

		public <T> Builder withSymmetric(String operator, Class<T> type, Function<T, T> op) {
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>()).with(type, UnaryOp.ofSym(type, op));
			return this;
		}

		public <T> Builder withIdentity(String operator, Class<T> type) {
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>()).with(type, UnaryOp.identity(type));
			return this;
		}

		public <T> Builder withAction(String operator, Class<T> type, Function<? super T, ? extends T> op) {
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>()).with(type, UnaryOp.ofAction(type, op));
			return this;
		}

		public Builder withStandardJavaOps() {
			withSymmetric("!", Boolean.class, b -> b == null ? true : !b);

			withSymmetric("~", Integer.class, i -> i == null ? ~0 : ~i);
			withIdentity("+", Integer.class);
			withSymmetric("-", Integer.class, i -> i == null ? 0 : -i);
			withAction("++", Integer.class, i -> i == null ? 1 : i + 1);
			withAction("--", Integer.class, i -> i == null ? 1 : i - 1);

			withSymmetric("~", Long.class, i -> i == null ? ~0 : ~i);
			withIdentity("+", Long.class);
			withSymmetric("-", Long.class, i -> i == null ? 0 : -i);
			withAction("++", Long.class, i -> i == null ? 1 : i + 1);
			withAction("--", Long.class, i -> i == null ? 1 : i - 1);

			with2("~", Byte.class, Integer.class, i -> i == null ? ~0 : ~i, i -> (byte) (i == null ? 0 : ~i));
			with2("+", Byte.class, Integer.class, i -> i == null ? 0 : (int) i, i -> (byte) (i == null ? 0 : i));
			with2("-", Byte.class, Integer.class, i -> i == null ? 0 : -i, i -> (byte) (i == null ? 0 : -i));
			withAction("++", Byte.class, i -> (byte) (i == null ? 1 : i + 1));
			withAction("--", Byte.class, i -> (byte) (i == null ? 1 : i - 1));

			with2("~", Character.class, Integer.class, i -> i == null ? ~0 : ~i, i -> (char) (i == null ? 0 : ~i));
			with2("+", Character.class, Integer.class, i -> i == null ? 0 : (int) i, i -> (char) (i == null ? 0 : i));
			with2("-", Character.class, Integer.class, i -> i == null ? 0 : -i, i -> (char) (i == null ? 0 : -i));
			withAction("++", Character.class, i -> (char) (i == null ? 1 : i + 1));
			withAction("--", Character.class, i -> (char) (i == null ? 1 : i - 1));

			with2("~", Short.class, Integer.class, i -> i == null ? ~0 : ~i, i -> (short) (i == null ? 0 : ~i));
			with2("+", Short.class, Integer.class, i -> i == null ? 0 : (int) i, i -> (short) (i == null ? 0 : i));
			with2("-", Short.class, Integer.class, i -> i == null ? 0 : -i, i -> (short) (i == null ? 0 : -i));
			withAction("++", Short.class, i -> (short) (i == null ? 1 : i + 1));
			withAction("--", Short.class, i -> (short) (i == null ? 1 : i - 1));

			withIdentity("+", Double.class);
			withSymmetric("-", Double.class, i -> i == null ? 0 : -i);
			withAction("++", Double.class, i -> i == null ? 1 : i + 1);
			withAction("--", Double.class, i -> i == null ? 1 : i - 1);

			withIdentity("+", Float.class);
			withSymmetric("-", Float.class, i -> i == null ? 0 : -i);
			withAction("++", Float.class, i -> i == null ? 1 : i + 1);
			withAction("--", Float.class, i -> i == null ? 1 : i - 1);

			return this;
		}

		public UnaryOperatorSet build() {
			Map<String, ClassMap<UnaryOp<?, ?>>> operators = new LinkedHashMap<>();
			for (Map.Entry<String, ClassMap<UnaryOp<?, ?>>> op : theOperators.entrySet())
				operators.put(op.getKey(), op.getValue().copy());
			return new UnaryOperatorSet(operators);
		}
	}
}
