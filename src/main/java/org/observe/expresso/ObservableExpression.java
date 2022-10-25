package org.observe.expresso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;
import org.qommons.TriFunction;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/** A parsed expression that is capable of producing observable results */
public interface ObservableExpression {
	/** A placeholder expression signifying the lack of any attempt to provide an expression */
	ObservableExpression EMPTY = new ObservableExpression() {
		@Override
		public List<? extends ObservableExpression> getChildren() {
			return Collections.emptyList();
		}

		@Override
		public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
			return replace.apply(this);
		}

		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
			throws QonfigInterpretationException {
			throw new QonfigInterpretationException("Empty expression");
		}

		@Override
		public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ExpressoEnv env)
			throws QonfigInterpretationException {
			throw new QonfigInterpretationException("Empty expression");
		}

		@Override
		public String toString() {
			return "null";
		}
	};

	/** @return All expressions that are components of this expression */
	List<? extends ObservableExpression> getChildren();

	/**
	 * Allows replacement of this expression or one or more of its {@link #getChildren() children}. For any expression in the hierarchy:
	 * <ol>
	 * <li>if the function returns a different expression, that is returned. Otherwise...</li>
	 * <li>{@link #replaceAll(Function)} is called for each child. If any of the children are replaced with something different, a new
	 * expression of the same kind as this is returned with the children replaced. Otherwise...</li>
	 * <li>This expression is returned</li>
	 * </ol>
	 *
	 * @param replace The function to replace expressions in this hierarchy
	 * @return The replaced expression
	 */
	ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace);

	/**
	 * @param search The search to apply
	 * @return All of this expression or its descendants that match the given search
	 */
	default BetterList<ObservableExpression> find(Predicate<ObservableExpression> search) {
		boolean thisApplies = search.test(this);
		BetterList<ObservableExpression> children = BetterList.of(getChildren().stream().flatMap(child -> child.find(search).stream()));
		if (thisApplies) {
			if (children.isEmpty())
				return BetterList.of(this);
			else {
				ObservableExpression[] found = new ObservableExpression[children.size() + 1];
				found[0] = this;
				for (int i = 0; i < children.size(); i++)
					found[i + 1] = children.get(i);
				return BetterList.of(found);
			}
		} else
			return children;
	}

	/**
	 * Attempts to evaluate this expression
	 *
	 * @param <M> The model type to evaluate the expression as
	 * @param <MV> The model instance type to evaluate the expression as
	 * @param type The model instance type to evaluate the expression as
	 * @param env The environment in which to evaluate the expression
	 * @return A value container to generate the expression's value from a {@link ModelSetInstance model instance}
	 * @throws QonfigInterpretationException If the expression cannot be evaluated in the given environment as the given type
	 */
	default <M, MV extends M> ValueContainer<M, MV> evaluate(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException {
		ValueContainer<M, MV> value = evaluateInternal(type, env);
		if (value == null)
			return null;
		return value.getType().as(value, type);
	}

	/**
	 * Same as {@link #evaluate(ModelInstanceType, ExpressoEnv)}, but not type-checked or converted
	 *
	 * @param <M> The model type to evaluate the expression as
	 * @param <MV> The model instance type to evaluate the expression as
	 * @param type The model instance type to evaluate the expression as
	 * @param env The environment in which to evaluate the expression
	 * @return A value container to generate the expression's value from a {@link ModelSetInstance model instance}
	 * @throws QonfigInterpretationException If the expression cannot be evaluated in the given environment as the given type
	 */
	<M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException;

	/**
	 * Attempts to evaluate this expression as an invokable method
	 *
	 * @param <P1> The first parameter type of the method
	 * @param <P2> The second parameter type of the method
	 * @param <P3> The third parameter type of the method
	 * @param <T> The return type of the method
	 * @param targetType The return type for the method
	 * @param env The environment in which to invoke the method
	 * @return A structure to facilitate with method evaluation
	 * @throws QonfigInterpretationException If this expression cannot be evaluated as a method with the given return type in the given
	 *         environment
	 */
	<P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ExpressoEnv env) throws QonfigInterpretationException;

	/**
	 * Attempts to evaluate this expression as an invokable method
	 *
	 * @param <P1> The first parameter type of the method
	 * @param <P2> The second parameter type of the method
	 * @param <P3> The third parameter type of the method
	 * @param <T> The return type of the method
	 * @param targetType The return type for the method
	 * @param env The environment in which to invoke the method
	 * @return A structure to facilitate with method evaluation
	 * @throws QonfigInterpretationException If this expression cannot be evaluated as a method with the given return type in the given
	 *         environment
	 */
	default <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(Class<T> targetType, ExpressoEnv env)
		throws QonfigInterpretationException {
		return findMethod(TypeTokens.get().of(targetType), env);
	}

	/**
	 * @param <T> The type of the first parameter
	 * @param <U> The type of the second parameter
	 * @param <V> The type of the third parameter
	 */
	interface ArgMaker<T, U, V> {
		/**
		 * Makes an argument list for the core invokable using the values supplied to the {@link MethodFinder#find3() found} method.
		 *
		 * @param t The first parameter value
		 * @param u The second parameter value
		 * @param v The third parameter value
		 * @param args The argument list to populate
		 * @param models The {@link ModelSetInstance model instance} that the invocation is occurring in
		 */
		void makeArgs(T t, U u, V v, Object[] args, ModelSetInstance models);
	}

	/** Represents an argument option supplied to a {@link MethodFinder} */
	interface Args {
		/** @return The number of arguments in the option */
		int size();

		/**
		 * @param arg The index of the argument to check
		 * @param paramType The type of the input parameter
		 * @return Whether the given parameter can be matched to the given argument
		 * @throws QonfigInterpretationException If an error occurs making the determination
		 */
		boolean matchesType(int arg, TypeToken<?> paramType) throws QonfigInterpretationException;

		/**
		 * @param arg The argument index
		 * @return The argument type at the given index
		 * @throws QonfigInterpretationException If an error occurs making the determination
		 */
		TypeToken<?> resolve(int arg) throws QonfigInterpretationException;
	}

	/**
	 * <p>
	 * A structure to assist the caller in evaluating an invokable method.
	 * </p>
	 * <p>
	 * One of the {@link #withOption(BetterList, ArgMaker) withOption} method should be called one or more times to give the finder a
	 * parameter set to look for, then one of the {@link #find3() find} methods should be invoked.
	 * </p>
	 * <p>
	 * If the find method returns successfully, the return value is a function that produces the desired invokable from a
	 * {@link ModelSetInstance model instance}. The return type of the invokable can be found with {@link #getResultType()}.
	 * </p>
	 *
	 * @param <P1> The first parameter type of the method
	 * @param <P2> The second parameter type of the method
	 * @param <P3> The third parameter type of the method
	 * @param <T> The return type of the method
	 */
	abstract class MethodFinder<P1, P2, P3, T> {
		protected final List<MethodOption> theOptions;
		protected final TypeToken<T> theTargetType;
		protected TypeToken<? extends T> theResultType;

		protected MethodFinder(TypeToken<T> targetType) {
			theOptions = new ArrayList<>(5);
			theTargetType = targetType;
		}

		/**
		 * Gives the finder an option for a parameter set
		 *
		 * @param argTypes The argument types of the parameter set to look for
		 * @param args Makes an argument set for the core invokable from supplied arguments
		 * @return This finder
		 */
		public MethodFinder<P1, P2, P3, T> withOption(BetterList<TypeToken<?>> argTypes, ArgMaker<P1, P2, P3> args) {
			theOptions.add(new MethodOption(argTypes.toArray(new TypeToken[argTypes.size()]), args));
			return this;
		}

		/**
		 * Instructs the finder that a zero-argument parameter set is acceptable
		 *
		 * @return This finder
		 */
		public MethodFinder<P1, P2, P3, T> withOption0() {
			return withOption(BetterList.empty(), null);
		}

		/**
		 * Gives the finder an option for a single-argument parameter set
		 *
		 * @param <A> The type of the argument
		 * @param paramType The type of the argument
		 * @param cast Casts the first parameter of this finder to the given argument type
		 * @return This finder
		 */
		public <A> MethodFinder<P1, P2, P3, T> withOption1(TypeToken<A> paramType, Function<? super P1, ? extends A> cast) {
			return withOption(BetterList.of(paramType), new ArgMaker<P1, P2, P3>() {
				@Override
				public void makeArgs(P1 t, P2 u, P3 v, Object[] args, ModelSetInstance models) {
					args[0] = cast == null ? (P1) t : cast.apply(t);
				}
			});
		}

		/**
		 * Gives the finder an option for a double-argument parameter set
		 *
		 * @param <A1> The type of the first argument
		 * @param <A2> The type of the second argument
		 * @param paramType1 The type of the first argument
		 * @param paramType2 The type of the second argument
		 * @param cast1 Casts the first parameter of this finder to the given first argument type
		 * @param cast2 Casts the second parameter of this finder to the given second argument type
		 * @return This finder
		 */
		public <A1, A2> MethodFinder<P1, P2, P3, T> withOption2(TypeToken<A1> paramType1, TypeToken<A2> paramType2,
			Function<? super P1, ? extends A1> cast1, Function<? super P2, ? extends A2> cast2) {
			return withOption(BetterList.of(paramType1, paramType2), new ArgMaker<P1, P2, P3>() {
				@Override
				public void makeArgs(P1 t, P2 u, P3 v, Object[] args, ModelSetInstance models) {
					args[0] = cast1 == null ? (P1) t : cast1.apply(t);
					args[1] = cast2 == null ? (P2) u : cast2.apply(u);
				}
			});
		}

		/**
		 * Gives the finder an option for a triple-argument parameter set
		 *
		 * @param <A1> The type of the first argument
		 * @param <A2> The type of the second argument
		 * @param <A3> The type of the third argument
		 * @param paramType1 The type of the first argument
		 * @param paramType2 The type of the second argument
		 * @param paramType3 The type of the third argument
		 * @param cast1 Casts the first parameter of this finder to the given first argument type
		 * @param cast2 Casts the second parameter of this finder to the given second argument type
		 * @param cast3 Casts the third parameter of this finder to the given third argument type
		 * @return This finder
		 */
		public <A1, A2, A3> MethodFinder<P1, P2, P3, T> withOption3(TypeToken<A1> paramType1, TypeToken<A2> paramType2,
			TypeToken<A3> paramType3, Function<? super P1, ? extends A1> cast1, Function<? super P2, ? extends A2> cast2,
			Function<? super P3, ? extends A3> cast3) {
			return withOption(BetterList.of(paramType1, paramType2, paramType3), new ArgMaker<P1, P2, P3>() {
				@Override
				public void makeArgs(P1 t, P2 u, P3 v, Object[] args, ModelSetInstance models) {
					args[0] = cast1 == null ? (P1) t : cast1.apply(t);
					args[1] = cast2 == null ? (P2) u : cast2.apply(u);
					args[2] = cast3 == null ? (P3) v : cast3.apply(v);
				}
			});
		}

		/**
		 * Attempts to evaluate an invocation that accepts zero arguments
		 *
		 * @return The function to produce the {@link Supplier} from a {@link ModelSetInstance model instance}
		 * @throws QonfigInterpretationException If the expression could not be evaluated as a zero-argument invokable of the given target
		 *         type with the given argument options in the given environment
		 */
		public Function<ModelSetInstance, Supplier<T>> find0() throws QonfigInterpretationException {
			Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> found = find3();
			return found.andThen(triF -> triF.curryAll(null, null, null));
		}

		/**
		 * Attempts to evaluate an invocation that accepts one argument
		 *
		 * @return The function to produce the {@link Function} from a {@link ModelSetInstance model instance}
		 * @throws QonfigInterpretationException If the expression could not be evaluated as a single-argument invokable of the given target
		 *         type with the given argument options in the given environment
		 */
		public Function<ModelSetInstance, Function<P1, T>> find1() throws QonfigInterpretationException {
			Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> found = find3();
			return found.andThen(triF -> triF.curry2And3(null, null));
		}

		/**
		 * Attempts to evaluate an invocation that accepts two arguments
		 *
		 * @return The function to produce the {@link BiFunction} from a {@link ModelSetInstance model instance}
		 * @throws QonfigInterpretationException If the expression could not be evaluated as a double-argument invokable of the given target
		 *         type with the given argument options in the given environment
		 */
		public Function<ModelSetInstance, BiFunction<P1, P2, T>> find2() throws QonfigInterpretationException {
			Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> found = find3();
			return found.andThen(triF -> triF.curry3(null));
		}

		/**
		 * Attempts to evaluate an invocation that accepts three arguments
		 *
		 * @return The function to produce the {@link TriFunction} from a {@link ModelSetInstance model instance}
		 * @throws QonfigInterpretationException If the expression could not be evaluated as a triple-argument invokable of the given target
		 *         type with the given argument options in the given environment
		 */
		public abstract Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> find3() throws QonfigInterpretationException;

		/** @return The return type of the method found previously with one of the {@link #find3() find} methods */
		public TypeToken<? extends T> getResultType() {
			return theResultType;
		}

		/** @param resultType The result type for the {@link #getResultType()} method */
		protected void setResultType(TypeToken<? extends T> resultType) {
			theResultType = resultType;
		}

		protected class MethodOption implements Args {
			private final TypeToken<?>[] argTypes;
			private final ArgMaker<P1, P2, P3> argMaker;

			MethodOption(TypeToken<?>[] argTypes, ArgMaker<P1, P2, P3> argMaker) {
				this.argTypes = argTypes;
				this.argMaker = argMaker;
			}

			@Override
			public int size() {
				return argTypes.length;
			}

			@Override
			public TypeToken<?> resolve(int arg) throws QonfigInterpretationException {
				return argTypes[arg];
			}

			@Override
			public boolean matchesType(int arg, TypeToken<?> paramType) {
				return TypeTokens.get().isAssignable(paramType, argTypes[arg]);
			}

			public TypeToken<?>[] getArgTypes() {
				return argTypes;
			}

			public ArgMaker<P1, P2, P3> getArgMaker() {
				return argMaker;
			}
		}
	}

	/**
	 * An expression that always returns a constant value
	 *
	 * @param <T> The type of the value
	 */
	class LiteralExpression<T> implements ObservableExpression {
		private final Expression theExpression;
		private final T theValue;

		/**
		 * @param exp The parsed expression
		 * @param value The value
		 */
		public LiteralExpression(Expression exp, T value) {
			theExpression = exp;
			theValue = value;
		}

		public Expression getExpression() {
			return theExpression;
		}

		public T getValue() {
			return theValue;
		}

		@Override
		public List<? extends ObservableExpression> getChildren() {
			return Collections.emptyList();
		}

		@Override
		public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
			return replace.apply(this);
		}

		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
			throws QonfigInterpretationException {
			String text = theExpression != null ? theExpression.getText() : toString();
			if (type.getModelType() != ModelTypes.Value)
				throw new QonfigInterpretationException("'" + text + "' cannot be evaluated as a " + type);
			if (theValue == null) {
				if (type.getType(0).isPrimitive())
					throw new QonfigInterpretationException("Cannot assign null to a primitive type (" + type.getType(0));
				MV value = (MV) createValue(type.getType(0), null);
				return ValueContainer.of(type, LambdaUtils.constantFn(value, text, null));
			} else if (TypeTokens.get().isInstance(type.getType(0), theValue)) {
				MV value = (MV) createValue(type.getType(0), theValue);
				return ValueContainer.of((ModelInstanceType<M, MV>) ModelTypes.Value.forType(theValue.getClass()),
					LambdaUtils.constantFn(value, text, null));
			} else if (TypeTokens.get().isAssignable(type.getType(0), TypeTokens.get().of(theValue.getClass()))) {
				TypeTokens.TypeConverter<T, Object> convert = TypeTokens.get().getCast(TypeTokens.get().of((Class<T>) theValue.getClass()),
					(TypeToken<Object>) type.getType(0));
				MV value = (MV) createValue(type.getType(0), convert.apply(theValue));
				return ValueContainer.of((ModelInstanceType<M, MV>) ModelTypes.Value.forType(theValue.getClass()),
					LambdaUtils.constantFn(value, text, null));
			} else
				throw new QonfigInterpretationException("'" + text + "' cannot be evaluated as a " + type);
		}

		@Override
		public <P1, P2, P3, T2> MethodFinder<P1, P2, P3, T2> findMethod(TypeToken<T2> targetType, ExpressoEnv env)
			throws QonfigInterpretationException {
			if (!TypeTokens.get().isInstance(targetType, theValue))
				throw new QonfigInterpretationException("'" + theValue + "' is not an instance of " + targetType);
			return new MethodFinder<P1, P2, P3, T2>(targetType) {
				@Override
				public Function<ModelSetInstance, TriFunction<P1, P2, P3, T2>> find3() throws QonfigInterpretationException {
					for (MethodOption option : theOptions) {
						if (option.argTypes.length == 0)
							return __ -> (p1, p2, p3) -> (T2) theValue;
					}
					throw new QonfigInterpretationException("No zero-parameter option for literal '" + theValue + "'");
				}
			};
		}

		SettableValue<?> createValue(TypeToken<?> type, Object value) {
			String text = theExpression != null ? theExpression.getText() : toString();
			return SettableValue.asSettable(ObservableValue.of((TypeToken<Object>) type, value), //
				__ -> "Literal value '" + text + "'");
		}

		@Override
		public String toString() {
			return String.valueOf(theValue);
		}
	}
}
