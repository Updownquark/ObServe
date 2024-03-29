package org.observe.expresso.ops;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.qommons.BiTuple;
import org.qommons.ClassMap;
import org.qommons.ClassMap.TypeMatch;
import org.qommons.QommonsUtils;
import org.qommons.SelfDescribed;

/** A set of unary operations that a {@link UnaryOperator} can use to evaluate itself */
public class UnaryOperatorSet {
	/**
	 * A unary operator that knows how to operate on 1 input
	 *
	 * @param <S> The super-type of the first input that this operator knows how to handle
	 * @param <T> The type of output produced by this operator
	 */
	public interface UnaryOp<S, T> extends SelfDescribed {
		/** @return The type of output produced by this operator */
		Class<T> getTargetType();

		/**
		 * Performs the operation
		 *
		 * @param source The input value
		 * @return The output value
		 */
		T apply(S source);

		/**
		 * @return Whether this operation makes sense only as an action, and not as a value. Such operations cannot be
		 *         {@link #reverse(Object) reversed}
		 */
		boolean isActionOnly();

		/**
		 * @param value The output value
		 * @return An input value for which {@link #apply(Object) apply} on that value would produce the given output value
		 */
		S reverse(T value);

		/**
		 * Produces a unary operator whose output type is the same as that of the input from functions
		 *
		 * @param <T> The input and output type of the operator
		 * @param name The name of the operator
		 * @param type The input and output type of the operator
		 * @param op The function to use for {@link UnaryOp#apply(Object)}
		 * @param reverse The function to use for {@link UnaryOp#reverse(Object)}
		 * @param description The description for the operator
		 * @return The unary operator composed of the given functions
		 */
		static <T> UnaryOp<T, T> of(String name, Class<T> type, Function<? super T, ? extends T> op,
			Function<? super T, ? extends T> reverse, String description) {
			return of2(name, type, op, reverse, description);
		}

		/**
		 * Produces a symmetric unary operator--one whose {@link #apply(Object)} is the same as {@link #reverse(Object)}
		 *
		 * @param <T> The input and output type of the operator
		 * @param name The name of the operator
		 * @param type The input and output type of the operator
		 * @param op The function to use for {@link UnaryOp#apply(Object)} and {@link UnaryOp#reverse(Object)}
		 * @param description The description for the operator
		 * @return The unary operator composed of the given function
		 */
		static <T> UnaryOp<T, T> ofSym(String name, Class<T> type, Function<T, T> op, String description) {
			return of2(name, type, op, op, description);
		}

		/**
		 * Produces a unary operator
		 *
		 * @param <S> The type of the input
		 * @param <T> The type of the output
		 * @param name The name of the operator
		 * @param type The type of the output
		 * @param op The function to use for {@link UnaryOp#apply(Object)}
		 * @param reverse The function to use for {@link UnaryOp#reverse(Object)}
		 * @param description The description for the operator
		 * @return The unary operator composed of the given functions
		 */
		static <S, T> UnaryOp<S, T> of2(String name, Class<T> type, Function<? super S, ? extends T> op,
			Function<? super T, ? extends S> reverse, String description) {
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

		/**
		 * @param <T> The type of the input and output
		 * @param name The name of the operator
		 * @param type The type of the input and output
		 * @param description The description for the operator
		 * @return A unary operator whose output is the same as the input
		 */
		static <T> UnaryOp<T, T> identity(String name, Class<T> type, String description) {
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

		/**
		 * Produces an {@link #isActionOnly() action-only} unary operator
		 *
		 * @param <T> The type of the input and output
		 * @param type The type of the input and output
		 * @param op The function to use for {@link UnaryOp#apply(Object)}
		 * @param description The description for the operator
		 * @return The unary operator
		 */
		static <T> UnaryOp<T, T> ofAction(Class<T> type, Function<? super T, ? extends T> op, String description) {
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
				public String getDescription() {
					return description;
				}

				@Override
				public T reverse(T value) {
					throw new IllegalStateException("Operator not supported as a value");
				}
			};
		}
	}

	/** Represents something that can configure a {@link Builder} to support some set of unary operations */
	public interface UnaryOperatorConfiguration {
		/**
		 * Configures a unary operator set builder to support some set of unary operations
		 *
		 * @param operators The builder to configure
		 * @return The builder
		 */
		Builder configure(Builder operators);
	}

	/**
	 * Configures a binary operator set to support all the standard Java unary operations
	 *
	 * @param operators The builder to configure
	 * @return The builder
	 */
	public static Builder standardJava(Builder operators) {
		operators.withSymmetric("!", boolean.class, b -> !b, "Boolean NOT operator");

		operators.withSymmetric("~", int.class, i -> i == null ? ~0 : ~i, "Integer complement operator");
		operators.withIdentity("+", int.class, "Integer identity operator");
		operators.withSymmetric("-", int.class, i -> i == null ? 0 : -i, "Integer negation operator");
		operators.withAction("++", Integer.class, i -> i == null ? 1 : i + 1, "Integer increment operator");
		operators.withAction("--", Integer.class, i -> i == null ? 1 : i - 1, "Integer decrement operator");

		operators.withSymmetric("~", long.class, i -> i == null ? ~0 : ~i, "Long integer complement operator");
		operators.withIdentity("+", long.class, "Long integer identity operator");
		operators.withSymmetric("-", long.class, i -> i == null ? 0 : -i, "Long integer negation operator");
		operators.withAction("++", Long.class, i -> i == null ? 1 : i + 1, "Long integer increment operator");
		operators.withAction("--", Long.class, i -> i == null ? 1 : i - 1, "Long integer decrement operator");

		operators.with2("~", byte.class, int.class, i -> i == null ? ~0 : ~i, i -> (byte) (~i), "Byte complement operator");
		operators.with2("+", byte.class, int.class, i -> i == null ? 0 : (int) i, i -> (byte) i.intValue(), "Byte identity operator");
		operators.with2("-", byte.class, int.class, i -> i == null ? 0 : -i, i -> (byte) (-i), "Byte negation operator");
		operators.withAction("++", Byte.class, i -> (byte) (i == null ? 1 : i + 1), "Byte increment operator");
		operators.withAction("--", Byte.class, i -> (byte) (i == null ? 1 : i - 1), "Byte decrement operator");

		operators.with2("~", char.class, int.class, i -> i == null ? ~0 : ~i, i -> (char) (~i), "Character complement operator");
		operators.with2("+", char.class, int.class, i -> i == null ? 0 : (int) i, i -> (char) i.intValue(), "Character identity operator");
		operators.with2("-", char.class, int.class, i -> i == null ? 0 : -i, i -> (char) (-i), "Character negation operator");
		operators.withAction("++", Character.class, i -> (char) (i == null ? 1 : i + 1), "Character increment operator");
		operators.withAction("--", Character.class, i -> (char) (i == null ? 1 : i - 1), "Character decrement operator");

		operators.with2("~", short.class, int.class, i -> i == null ? ~0 : ~i, i -> (short) (~i), "Short integer complement operator");
		operators.with2("+", short.class, int.class, i -> i == null ? 0 : (int) i, i -> (short) i.intValue(),
			"Short integer identity operator");
		operators.with2("-", short.class, int.class, i -> i == null ? 0 : -i, i -> (short) (-i), "Short integer negation operator");
		operators.withAction("++", Short.class, i -> (short) (i == null ? 1 : i + 1), "Short integer increment operator");
		operators.withAction("--", Short.class, i -> (short) (i == null ? 1 : i - 1), "Short integer decrement operator");

		operators.withIdentity("+", double.class, "Double-precision floating-point identity operator");
		operators.withSymmetric("-", double.class, i -> i == null ? 0 : -i, "Double-precision floating-point negation operator");
		operators.withAction("++", Double.class, i -> i == null ? 1 : i + 1, "Double-precision floating-point increment operator");
		operators.withAction("--", Double.class, i -> i == null ? 1 : i - 1, "Double-precision floating-point decrement operator");

		operators.withIdentity("+", float.class, "Floating-point identity operator");
		operators.withSymmetric("-", float.class, i -> i == null ? 0 : -i, "Floating-point negation operator");
		operators.withAction("++", Float.class, i -> i == null ? 1 : i + 1, "Floating-point increment operator");
		operators.withAction("--", Float.class, i -> i == null ? 1 : i - 1, "Floating-point decrement operator");

		return operators;
	}

	/** A {@link UnaryOperatorSet} that configures a builder to support the standard set of Java unary operators */
	public static final UnaryOperatorSet STANDARD_JAVA = standardJava(build()).build();

	private final Map<String, ClassMap<UnaryOp<?, ?>>> theOperators;

	private UnaryOperatorSet(Map<String, ClassMap<UnaryOp<?, ?>>> operators) {
		theOperators = operators;
	}

	/**
	 * @param operator The name of the operator(s) to get
	 * @return All operators in this operator set with the given name
	 */
	public Set<UnaryOp<?, ?>> getOperators(String operator) {
		ClassMap<UnaryOp<?, ?>> ops = theOperators.get(operator);
		if (ops == null)
			return Collections.emptySet();
		return QommonsUtils.unmodifiableDistinctCopy(ops.getAllValues());
	}

	/**
	 * @param operator The name of the operator
	 * @return All input types that this operator set knows of for which the given operator may be applied
	 */
	public Set<Class<?>> getSupportedInputTypes(String operator) {
		ClassMap<UnaryOp<?, ?>> ops = theOperators.get(operator);
		return ops == null ? Collections.emptySet() : ops.getTopLevelKeys();
	}

	/**
	 * @param <T> The input type
	 * @param operator The name of the operator
	 * @param type The type of the input
	 * @return The unary operator supported by this operator set with the given operator and input type
	 */
	public <T> UnaryOp<T, ?> getOperator(String operator, Class<T> type) {
		ClassMap<UnaryOp<?, ?>> ops = theOperators.get(operator);
		return ops == null ? null : (UnaryOp<T, ?>) ops.get(type, TypeMatch.SUPER_TYPE);
	}

	/** @return A builder pre-configured for all of this operator set's operations */
	public Builder copy() {
		Builder copy = build();
		for (Map.Entry<String, ClassMap<UnaryOp<?, ?>>> op : theOperators.entrySet()) {
			for (BiTuple<Class<?>, UnaryOp<?, ?>> op2 : op.getValue().getAllEntries()) {
				copy.with(op.getKey(), (Class<Object>) op2.getValue1(), (UnaryOp<Object, ?>) op2.getValue2());
			}
		}
		return copy;
	}

	/** @return A builder that may be configured to support various unary operations */
	public static Builder build() {
		return new Builder();
	}

	/** A builder that may be configured to support various unary operations */
	public static class Builder {
		private final Map<String, ClassMap<UnaryOp<?, ?>>> theOperators;

		Builder() {
			theOperators = new LinkedHashMap<>();
		}

		/**
		 * Installs support for an operator
		 *
		 * @param <S> The type of the input
		 * @param operator The name of the operator
		 * @param type The type of the input
		 * @param op The operator to support the given operation
		 * @return This builder
		 */
		public <S> Builder with(String operator, Class<S> type, UnaryOp<? super S, ?> op) {
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>()).with(type, op);
			return this;
		}

		/**
		 * Installs support for an operator whose output type is the same as that of the input
		 *
		 * @param <T> The type of the input
		 * @param operator The name of the operator
		 * @param type The type of the input
		 * @param op The function to apply the operation
		 * @param reverse The function to reverse the operation
		 * @param description The description for the operator
		 * @return This builder
		 * @see UnaryOp#reverse(Object)
		 */
		public <T> Builder with(String operator, Class<T> type, Function<? super T, ? extends T> op,
			Function<? super T, ? extends T> reverse, String description) {
			return with(operator, type, //
				UnaryOp.of(operator, type, op, reverse, description));
		}

		/**
		 * Installs support for an operator
		 *
		 * @param <S> The type of the input
		 * @param <T> The type of the output
		 * @param operator The name of the operator
		 * @param source The type of the input
		 * @param target The type of the output
		 * @param op The function to apply the operation
		 * @param reverse The function to reverse the operation
		 * @param description The description for the operator
		 * @return This builder
		 * @see UnaryOp#reverse(Object)
		 */
		public <S, T> Builder with2(String operator, Class<S> source, Class<T> target, Function<? super S, ? extends T> op,
			Function<? super T, ? extends S> reverse, String description) {
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>()).with(source,
				UnaryOp.of2(operator, target, op, reverse, description));
			return this;
		}

		/**
		 * Installs support for a symmetric operator--one whose {@link UnaryOp#reverse(Object) reverse} is the same as its
		 * {@link UnaryOp#apply(Object) application}
		 *
		 * @param <T> The type of the operator
		 * @param operator The name of the operator
		 * @param type The type of the operator
		 * @param op The function to apply and reverse the operator
		 * @param description The description for the operator
		 * @return This builder
		 */
		public <T> Builder withSymmetric(String operator, Class<T> type, Function<T, T> op, String description) {
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>()).with(type, UnaryOp.ofSym(operator, type, op, description));
			return this;
		}

		/**
		 * Installs an operator that is actually a no-op--one whose output is the same as the input
		 *
		 * @param <T> The type of the operator
		 * @param operator The name of the operator
		 * @param type The type of the operator
		 * @param description The description for the operator
		 * @return This builder
		 */
		public <T> Builder withIdentity(String operator, Class<T> type, String description) {
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>()).with(type, UnaryOp.identity(operator, type, description));
			return this;
		}

		/**
		 * Installs an action-only operator
		 *
		 * @param <T> The type of the operator
		 * @param operator The name of the operator
		 * @param type The type of the operator
		 * @param op The function to apply the operator
		 * @param description The description for the operator
		 * @return This builder
		 */
		public <T> Builder withAction(String operator, Class<T> type, Function<? super T, ? extends T> op, String description) {
			theOperators.computeIfAbsent(operator, __ -> new ClassMap<>()).with(type, UnaryOp.ofAction(type, op, description));
			return this;
		}

		/** @return A unary operator set with the support installed in this builder */
		public UnaryOperatorSet build() {
			Map<String, ClassMap<UnaryOp<?, ?>>> operators = new LinkedHashMap<>();
			for (Map.Entry<String, ClassMap<UnaryOp<?, ?>>> op : theOperators.entrySet())
				operators.put(op.getKey(), op.getValue().copy());
			return new UnaryOperatorSet(operators);
		}
	}
}
