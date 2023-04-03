package org.observe.expresso;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.InterpretedValueContainer;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
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
		public int getExpressionOffset() {
			return 0;
		}

		@Override
		public int getExpressionEnd() {
			return 0;
		}

		@Override
		public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
			return replace.apply(this);
		}

		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
			throws ExpressoEvaluationException {
			return (ValueContainer<M, MV>) ValueContainer.literal(TypeTokens.get().WILDCARD, null, "(empty)");
		}

		@Override
		public String toString() {
			return "null";
		}
	};

	/** @return All expressions that are components of this expression */
	List<? extends ObservableExpression> getChildren();

	/** @return The starting position of this expression within the expression root */
	int getExpressionOffset();

	/** @return The ending position of this expression in the root sequence */
	int getExpressionEnd();

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
	 * @throws ExpressoEvaluationException If the expression cannot be evaluated in the given environment as the given type
	 * @throws ExpressoInterpretationException If an expression on which this expression depends fails to evaluate
	 * @throws TypeConversionException If this expression could not be interpreted as the given type
	 */
	default <M, MV extends M> InterpretedValueContainer<M, MV> evaluate(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws ExpressoEvaluationException, ExpressoInterpretationException, TypeConversionException {
		ValueContainer<M, MV> value = evaluateInternal(type, env);
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
	 * @return A value container to generate the expression's value from a {@link ModelSetInstance model instance}
	 * @throws ExpressoEvaluationException If the expression cannot be evaluated in the given environment as the given type
	 * @throws ExpressoInterpretationException If a dependency
	 */
	<M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws ExpressoEvaluationException, ExpressoInterpretationException;

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
		public int getExpressionOffset() {
			return theExpression == null ? 0 : theExpression.getStartIndex();
		}

		@Override
		public int getExpressionEnd() {
			return theExpression == null ? 0 : theExpression.getEndIndex();
		}

		@Override
		public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
			return replace.apply(this);
		}

		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
			throws ExpressoEvaluationException {
			String text = theExpression != null ? theExpression.getText() : toString();
			if (type.getModelType() != ModelTypes.Value)
				throw new ExpressoEvaluationException(theExpression, "'" + text + "' cannot be evaluated as a " + type);
			if (theValue == null) {
				if (type.getType(0).isPrimitive())
					throw new ExpressoEvaluationException(theExpression, "Cannot assign null to a primitive type (" + type.getType(0));
				MV value = (MV) createValue(type.getType(0), null);
				return ValueContainer.of(type, LambdaUtils.constantExFn(value, text, null));
			} else if (TypeTokens.get().isInstance(type.getType(0), theValue)) {
				MV value = (MV) createValue(type.getType(0), theValue);
				return ValueContainer.of((ModelInstanceType<M, MV>) ModelTypes.Value.forType(theValue.getClass()),
					LambdaUtils.constantExFn(value, text, null));
			} else if (TypeTokens.get().isAssignable(type.getType(0), TypeTokens.get().of(theValue.getClass()))) {
				TypeTokens.TypeConverter<T, Object> convert = TypeTokens.get().getCast(TypeTokens.get().of((Class<T>) theValue.getClass()),
					(TypeToken<Object>) type.getType(0));
				MV value = (MV) createValue(type.getType(0), convert.apply(theValue));
				return ValueContainer.of((ModelInstanceType<M, MV>) ModelTypes.Value.forType(theValue.getClass()),
					LambdaUtils.constantExFn(value, text, null));
			} else
				throw new ExpressoEvaluationException(theExpression, "'" + text + "' cannot be evaluated as a " + type);
		}

		SettableValue<?> createValue(TypeToken<?> type, Object value) {
			String text = theExpression != null ? theExpression.getText() : toString();
			return SettableValue.asSettable(ObservableValue.of((TypeToken<Object>) type, value), //
				__ -> "Literal value '" + text + "'");
		}

		@Override
		public String toString() {
			if (theExpression != null)
				return theExpression.toString();
			else
				return String.valueOf(theValue);
		}
	}
}
