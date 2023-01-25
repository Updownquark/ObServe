package org.observe.expresso.ops;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigEvaluationException;

import com.google.common.reflect.TypeToken;

/** An expression representing the assignment of one value into another */
public class AssignmentExpression implements ObservableExpression {
	private final ObservableExpression theTarget;
	private final ObservableExpression theValue;

	/**
	 * @param target The variable or field that will be assigned
	 * @param value The value to assign to the variable or field
	 */
	public AssignmentExpression(ObservableExpression target, ObservableExpression value) {
		theTarget = target;
		theValue = value;
	}

	/** @return The variable or field that will be assigned */
	public ObservableExpression getTarget() {
		return theTarget;
	}

	/** @return The value to assign to the variable or field */
	public ObservableExpression getValue() {
		return theValue;
	}

	@Override
	public int getExpressionOffset() {
		return theTarget.getExpressionOffset();
	}

	@Override
	public int getExpressionEnd() {
		return theValue.getExpressionEnd();
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return QommonsUtils.unmodifiableCopy(theTarget, theValue);
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		ObservableExpression replacement = replace.apply(this);
		if (replacement != this)
			return replacement;
		ObservableExpression target = theTarget.replaceAll(replace);
		ObservableExpression value = theValue.replaceAll(replace);
		if (target != theTarget || value != theValue)
			return new AssignmentExpression(target, value);
		return this;
	}

	@Override
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws ExpressoEvaluationException {
		if (type.getModelType() != ModelTypes.Action)
			throw new ExpressoEvaluationException(getExpressionOffset(), getExpressionEnd(),
				"Assignments cannot be used as " + type.getModelType() + "s");
		ValueContainer<SettableValue<?>, SettableValue<Object>> context = theTarget
			.evaluate((ModelInstanceType<SettableValue<?>, SettableValue<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any(), env);
		boolean isVoid = type.getType(0).getType() == void.class || type.getType(0).getType() == Void.class;
		try {
			if (!isVoid && !TypeTokens.get().isAssignable(type.getType(0), context.getType().getType(0)))
				throw new ExpressoEvaluationException(theValue.getExpressionOffset(), theValue.getExpressionEnd(),
					"Cannot assign " + context + ", type " + type.getType(0) + " to " + context.getType().getType(0));
		} catch (QonfigEvaluationException e) {
			throw new ExpressoEvaluationException(theTarget.getExpressionOffset(), theTarget.getExpressionEnd(), e.getMessage(), e);
		}
		ValueContainer<SettableValue<?>, SettableValue<Object>> value;
		try (Transaction t = Invocation.asAction()) {
			value = theValue.evaluate(
				ModelTypes.Value.forType((TypeToken<Object>) TypeTokens.get().getExtendsWildcard(context.getType().getType(0))), env);
		} catch (QonfigEvaluationException e) {
			throw new ExpressoEvaluationException(theValue.getExpressionOffset(), theValue.getExpressionEnd(), e.getMessage(), e);
		}
		return (ValueContainer<M, MV>) new ValueContainer<ObservableAction<?>, ObservableAction<?>>() {
			@Override
			public ModelInstanceType<ObservableAction<?>, ObservableAction<?>> getType() throws QonfigEvaluationException {
				if (isVoid)
					return (ModelInstanceType<ObservableAction<?>, ObservableAction<?>>) type;
				else
					return (ModelInstanceType<ObservableAction<?>, ObservableAction<?>>) (ModelInstanceType<?, ?>) ModelTypes.Action
						.forType(context.getType().getType(0));
			}

			@Override
			public ObservableAction<?> get(ModelSetInstance models) throws QonfigEvaluationException {
				SettableValue<Object> ctxValue = context.get(models);
				SettableValue<Object> valueValue = value.get(models);
				return ctxValue.assignmentTo(valueValue);
			}

			@Override
			public ObservableAction<?> forModelCopy(ObservableAction<?> value2, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws QonfigEvaluationException {
				SettableValue<Object> sourceCtx = context.get(sourceModels);
				SettableValue<Object> newCtx = context.get(newModels);
				SettableValue<Object> sourceValue = value.get(sourceModels);
				SettableValue<Object> newValue = value.get(newModels);
				if (sourceCtx == newCtx && sourceValue == newValue)
					return value2;
				else
					return newCtx.assignmentTo(newValue);
			}

			@Override
			public BetterList<ValueContainer<?, ?>> getCores() throws QonfigEvaluationException {
				return BetterList.of(Stream.of(context, value), vc -> vc.getCores().stream());
			}

			@Override
			public String toString() {
				return AssignmentExpression.this.toString();
			}
		};
	}

	@Override
	public int hashCode() {
		return Objects.hash(theTarget, theValue);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof AssignmentExpression && theTarget.equals(((AssignmentExpression) obj).theTarget)
			&& theValue.equals(((AssignmentExpression) obj).theValue);
	}

	@Override
	public String toString() {
		return theTarget + "=" + theValue;
	}
}