package org.observe.expresso;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;
import org.qommons.collect.BetterList;

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
		public int getChildOffset(int childIndex) {
			throw new IndexOutOfBoundsException(childIndex + " of 0");
		}

		@Override
		public int getExpressionLength() {
			return 0;
		}

		@Override
		public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
			return replace.apply(this);
		}

		@Override
		public ModelType<?> getModelType(ExpressoEnv env) {
			return ModelTypes.Value;
		}

		@Override
		public <M, MV extends M> ModelValueSynth<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env,
			int expressionOffset) throws ExpressoEvaluationException {
			return (ModelValueSynth<M, MV>) ModelValueSynth.literal(TypeTokens.get().WILDCARD, null, "(empty)");
		}

		@Override
		public String toString() {
			return "null";
		}
	};

	/** @return All expressions that are components of this expression */
	List<? extends ObservableExpression> getChildren();

	/**
	 * @param childIndex The index of the {@link #getChildren() child}
	 * @return The number of characters in this expression occurring before the start of the given child expression
	 */
	int getChildOffset(int childIndex);

	/** @return The total number of characters in the textual representation of this expression */
	int getExpressionLength();

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
	 * @param env The environment in which the expression will be evaluated
	 * @return A best guess as to the model type that this expression would evaluate to in the given environment
	 */
	ModelType<?> getModelType(ExpressoEnv env);

	/**
	 * Attempts to evaluate this expression
	 *
	 * @param <M> The model type to evaluate the expression as
	 * @param <MV> The model instance type to evaluate the expression as
	 * @param type The model instance type to evaluate the expression as
	 * @param env The environment in which to evaluate the expression
	 * @param expressionOffset The offset of this expression in the evaluated root
	 * @return A value container to generate the expression's value from a {@link ModelSetInstance model instance}
	 * @throws ExpressoEvaluationException If the expression cannot be evaluated in the given environment as the given type
	 * @throws ExpressoInterpretationException If an expression on which this expression depends fails to evaluate
	 * @throws TypeConversionException If this expression could not be interpreted as the given type
	 */
	default <M, MV extends M> InterpretedValueSynth<M, MV> evaluate(ModelInstanceType<M, MV> type, ExpressoEnv env,
		int expressionOffset) throws ExpressoEvaluationException, ExpressoInterpretationException, TypeConversionException {
		ModelValueSynth<M, MV> value = evaluateInternal(type, env, expressionOffset);
		if (value == null)
			return null;
		return value.as(type);
	}

	/**
	 * Does the work of interpreting this expression, but without type-checking or conversion
	 *
	 * @param <M> The model type to evaluate the expression as
	 * @param <MV> The model instance type to evaluate the expression as
	 * @param type The model instance type to evaluate the expression as
	 * @param env The environment in which to evaluate the expression
	 * @param expressionOffset The offset of this expression in the evaluated root
	 * @return A value container to generate the expression's value from a {@link ModelSetInstance model instance}
	 * @throws ExpressoEvaluationException If the expression cannot be evaluated in the given environment as the given type
	 * @throws ExpressoInterpretationException If a dependency
	 */
	<M, MV extends M> ModelValueSynth<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env, int expressionOffset)
		throws ExpressoEvaluationException, ExpressoInterpretationException;

	/**
	 * An expression that always returns a constant value
	 *
	 * @param <T> The type of the value
	 */
	class LiteralExpression<T> implements ObservableExpression {
		private final String theText;
		private final T theValue;

		/**
		 * @param text The parsed expression
		 * @param value The value
		 */
		public LiteralExpression(String text, T value) {
			theText = text;
			theValue = value;
		}

		public T getValue() {
			return theValue;
		}

		@Override
		public List<? extends ObservableExpression> getChildren() {
			return Collections.emptyList();
		}

		@Override
		public int getChildOffset(int childIndex) {
			throw new IndexOutOfBoundsException(childIndex + " of 0");
		}

		@Override
		public int getExpressionLength() {
			return theText.length();
		}

		@Override
		public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
			return replace.apply(this);
		}

		@Override
		public ModelType<?> getModelType(ExpressoEnv env) {
			return ModelTypes.Value;
		}

		@Override
		public <M, MV extends M> ModelValueSynth<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env,
			int expressionOffset) throws ExpressoEvaluationException {
			if (type.getModelType() != ModelTypes.Value)
				throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
					"'" + theText + "' cannot be evaluated as a " + type);
			if (theValue == null) {
				if (type.getType(0).isPrimitive())
					throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
						"Cannot assign null to a primitive type (" + type.getType(0));
				MV value = (MV) createValue(type.getType(0), null);
				return ModelValueSynth.of(type, LambdaUtils.constantExFn(value, theText, null));
			} else if (TypeTokens.get().isInstance(type.getType(0), theValue)) {
				MV value = (MV) createValue(type.getType(0), theValue);
				return ModelValueSynth.of((ModelInstanceType<M, MV>) ModelTypes.Value.forType(theValue.getClass()),
					LambdaUtils.constantExFn(value, theText, null));
			} else if (TypeTokens.get().isAssignable(type.getType(0), TypeTokens.get().of(theValue.getClass()))) {
				TypeTokens.TypeConverter<T, Object> convert = TypeTokens.get().getCast(TypeTokens.get().of((Class<T>) theValue.getClass()),
					(TypeToken<Object>) type.getType(0));
				MV value = (MV) createValue(type.getType(0), convert.apply(theValue));
				return ModelValueSynth.of((ModelInstanceType<M, MV>) ModelTypes.Value.forType(theValue.getClass()),
					LambdaUtils.constantExFn(value, theText, null));
			} else {
				// Don't throw this. Maybe the type architecture can convert it.
				// throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				// "'" + theText + "' cannot be evaluated as a " + type);
				MV value = (MV) createValue(TypeTokens.get().of(theValue.getClass()), theValue);
				return ModelValueSynth.of((ModelInstanceType<M, MV>) ModelTypes.Value.forType(theValue.getClass()),
					LambdaUtils.constantExFn(value, theText, null));
			}
		}

		SettableValue<?> createValue(TypeToken<?> type, Object value) {
			return SettableValue.asSettable(ObservableValue.of((TypeToken<Object>) type, value), //
				__ -> "Literal value '" + theText + "'");
		}

		@Override
		public String toString() {
			return theText;
		}
	}
}
