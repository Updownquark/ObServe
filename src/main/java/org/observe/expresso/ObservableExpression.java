package org.observe.expresso;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.util.ClassView;
import org.observe.util.ModelType.ModelInstanceType;
import org.observe.util.ObservableModelSet;
import org.observe.util.ObservableModelSet.ModelSetInstance;
import org.observe.util.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.TriFunction;
import org.qommons.collect.BetterList;

import com.google.common.reflect.TypeToken;

public interface ObservableExpression {
	ObservableExpression EMPTY = new ObservableExpression() {
		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluate(ModelInstanceType<M, MV> type, ObservableModelSet models,
			ClassView classView) throws ParseException {
			return null;
		}

		@Override
		public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType) throws ParseException {
			throw new ParseException("Empty expression", 0);
		}
	};

	<M, MV extends M> ObservableModelSet.ValueContainer<M, MV> evaluate(ModelInstanceType<M, MV> type, ObservableModelSet models,
		ClassView classView) throws ParseException;

	<P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType) throws ParseException;

	default <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(Class<T> targetType) throws ParseException {
		return findMethod(TypeTokens.get().of(targetType));
	}

	interface ArgMaker<T, U, V> {
		void makeArgs(T t, U u, V v, Object[] args, ModelSetInstance models);
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

		public Function<ModelSetInstance, Supplier<T>> find0() throws ParseException {
			Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> found = find3();
			return found.andThen(triF -> triF.curryAll(null, null, null));
		}

		public Function<ModelSetInstance, Function<P1, T>> find1() throws ParseException {
			Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> found = find3();
			return found.andThen(triF -> triF.curry2And3(null, null));
		}

		public Function<ModelSetInstance, BiFunction<P1, P2, T>> find2() throws ParseException {
			Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> found = find3();
			return found.andThen(triF -> triF.curry3(null));
		}

		public abstract Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> find3() throws ParseException;

		public TypeToken<? extends T> getResultType() {
			return theResultType;
		}

		protected class MethodOption {
			final TypeToken<?>[] argTypes;
			final ArgMaker<P1, P2, P3> argMaker;

			MethodOption(TypeToken<?>[] argTypes, ArgMaker<P1, P2, P3> argMaker) {
				this.argTypes = argTypes;
				this.argMaker = argMaker;
			}

			public TypeToken<?>[] getArgTypes() {
				return argTypes;
			}

			public ArgMaker<P1, P2, P3> getArgMaker() {
				return argMaker;
			}
		}
	}
}
