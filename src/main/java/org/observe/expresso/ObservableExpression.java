package org.observe.expresso;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.util.ClassView;
import org.observe.util.ModelType.ModelInstanceType;
import org.observe.util.ModelTypes;
import org.observe.util.ObservableModelSet;
import org.observe.util.ObservableModelSet.ModelSetInstance;
import org.observe.util.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;
import org.qommons.TriFunction;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigInterpreter.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface ObservableExpression {
	ObservableExpression EMPTY = new ObservableExpression() {
		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			return null;
		}

		@Override
		public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			throw new QonfigInterpretationException("Empty expression");
		}
	};

	default <M, MV extends M> ValueContainer<M, MV> evaluate(ModelInstanceType<M, MV> type, ObservableModelSet models, ClassView classView)
		throws QonfigInterpretationException {
		ValueContainer<M, MV> value = evaluateInternal(type, models, classView);
		if (value == null)
			return null;
		return value.getType().as(value, type);
	}

	<M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ObservableModelSet models,
		ClassView classView) throws QonfigInterpretationException;

	<P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ObservableModelSet models, ClassView classView)
		throws QonfigInterpretationException;

	default <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(Class<T> targetType, ObservableModelSet models, ClassView classView)
		throws QonfigInterpretationException {
		return findMethod(TypeTokens.get().of(targetType), models, classView);
	}

	interface ArgMaker<T, U, V> {
		void makeArgs(T t, U u, V v, Object[] args, ModelSetInstance models);
	}

	interface Args {
		int size();

		boolean matchesType(int arg, TypeToken<?> paramType);

		TypeToken<?> resolveFirst() throws QonfigInterpretationException;
	}

	abstract class MethodFinder<P1, P2, P3, T> {
		protected final List<MethodOption> theOptions;
		protected final TypeToken<T> theTargetType;
		protected TypeToken<? extends T> theResultType;

		protected MethodFinder(TypeToken<T> targetType) {
			theOptions = new ArrayList<>(5);
			theTargetType = targetType;
		}

		public MethodFinder<P1, P2, P3, T> withOption(BetterList<TypeToken<?>> argTypes, ArgMaker<P1, P2, P3> args) {
			theOptions.add(new MethodOption(argTypes.toArray(new TypeToken[argTypes.size()]), args));
			return this;
		}

		public MethodFinder<P1, P2, P3, T> withOption0() {
			return withOption(BetterList.empty(), null);
		}

		public <A> MethodFinder<P1, P2, P3, T> withOption1(TypeToken<A> paramType, Function<? super P1, ? extends A> cast) {
			return withOption(BetterList.of(paramType), new ArgMaker<P1, P2, P3>() {
				@Override
				public void makeArgs(P1 t, P2 u, P3 v, Object[] args, ModelSetInstance models) {
					args[0] = cast == null ? (P1) t : cast.apply(t);
				}
			});
		}

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

		public Function<ModelSetInstance, Supplier<T>> find0() throws QonfigInterpretationException {
			Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> found = find3();
			return found.andThen(triF -> triF.curryAll(null, null, null));
		}

		public Function<ModelSetInstance, Function<P1, T>> find1() throws QonfigInterpretationException {
			Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> found = find3();
			return found.andThen(triF -> triF.curry2And3(null, null));
		}

		public Function<ModelSetInstance, BiFunction<P1, P2, T>> find2() throws QonfigInterpretationException {
			Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> found = find3();
			return found.andThen(triF -> triF.curry3(null));
		}

		public abstract Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> find3() throws QonfigInterpretationException;

		public TypeToken<? extends T> getResultType() {
			return theResultType;
		}

		protected void setResultType(TypeToken<? extends T> resultType) {
			theResultType = resultType;
		}

		protected class MethodOption implements Args {
			final TypeToken<?>[] argTypes;
			final ArgMaker<P1, P2, P3> argMaker;

			MethodOption(TypeToken<?>[] argTypes, ArgMaker<P1, P2, P3> argMaker) {
				this.argTypes = argTypes;
				this.argMaker = argMaker;
			}

			@Override
			public int size() {
				return argTypes.length;
			}

			@Override
			public TypeToken<?> resolveFirst() {
				return argTypes[0];
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

	class LiteralExpression<T> implements ObservableExpression {
		private final Expression theExpression;
		private final T theValue;

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
		public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			if (type.getModelType() != ModelTypes.Value)
				throw new QonfigInterpretationException("'" + theExpression.getText() + "' cannot be evaluated as a " + type);
			if (theValue == null) {
				if (type.getType(0).isPrimitive())
					throw new QonfigInterpretationException("Cannot assign null to a primitive type (" + type.getType(0));
				MV value = (MV) createValue(type.getType(0), null);
				return ObservableModelSet.container(LambdaUtils.constantFn(value, theExpression.getText(), null), type);
			} else if (TypeTokens.get().isInstance(type.getType(0), theValue)) {
				MV value = (MV) createValue(type.getType(0), theValue);
				return ObservableModelSet.container(LambdaUtils.constantFn(value, theExpression.getText(), null), type);
			} else if (TypeTokens.get().isAssignable(type.getType(0), TypeTokens.get().of(theValue.getClass()))) {
				MV value = (MV) createValue(type.getType(0), TypeTokens.get().cast(type.getType(0), theValue));
				return ObservableModelSet.container(LambdaUtils.constantFn(value, theExpression.getText(), null), type);
			} else
				throw new QonfigInterpretationException("'" + theExpression.getText() + "' cannot be evaluated as a " + type);
		}

		@Override
		public <P1, P2, P3, T2> MethodFinder<P1, P2, P3, T2> findMethod(TypeToken<T2> targetType, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			throw new QonfigInterpretationException("Literal '" + theValue + "' cannot be evaluated as a method");
		}

		SettableValue<?> createValue(TypeToken<?> type, Object value) {
			return SettableValue.asSettable(ObservableValue.of((TypeToken<Object>) type, value), //
				__ -> "Literal value '" + theExpression.getText() + "'");
		}

		@Override
		public String toString() {
			return String.valueOf(theValue);
		}
	}
}
