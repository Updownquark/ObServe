package org.observe.expresso.ops;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;

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
	public int getChildOffset(int childIndex) {
		switch (childIndex) {
		case 0:
			return 0;
		case 1:
			return theTarget.getExpressionLength() + 1;
		default:
			throw new IndexOutOfBoundsException(childIndex + " of 2");
		}
	}

	@Override
	public int getExpressionLength() {
		return theTarget.getExpressionLength() + 1 + theValue.getExpressionLength();
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
	public ModelType<?> getModelType(ExpressoEnv env) {
		return ModelTypes.Action;
	}

	@Override
	public <M, MV extends M> ModelValueSynth<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env, int expressionOffset)
		throws ExpressoEvaluationException, ExpressoInterpretationException {
		if (type.getModelType() != ModelTypes.Action)
			throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				"Assignments cannot be used as " + type.getModelType() + "s");
		ModelValueSynth<SettableValue<?>, SettableValue<Object>> context;
		try {
			context = theTarget.evaluate(
				(ModelInstanceType<SettableValue<?>, SettableValue<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any(), env,
				expressionOffset);
		} catch (TypeConversionException e) {
			throw new ExpressoEvaluationException(expressionOffset, theTarget.getExpressionLength(), e.getMessage(), e);
		}
		boolean isVoid = type.getType(0).getType() == void.class || type.getType(0).getType() == Void.class;
		if (!isVoid && !TypeTokens.get().isAssignable(type.getType(0), context.getType().getType(0)))
			throw new ExpressoEvaluationException(expressionOffset, theTarget.getExpressionLength(),
				"Cannot assign " + context + ", type " + type.getType(0) + " to " + context.getType().getType(0));
		ModelValueSynth<SettableValue<?>, SettableValue<Object>> value;
		int valueOffset = expressionOffset + theTarget.getExpressionLength() + 1;
		try (Transaction t = Invocation.asAction()) {
			value = theValue.evaluate(
				ModelTypes.Value.forType((TypeToken<Object>) TypeTokens.get().getExtendsWildcard(context.getType().getType(0))), env,
				valueOffset);
		} catch (TypeConversionException e) {
			throw new ExpressoEvaluationException(valueOffset, theValue.getExpressionLength(), e.getMessage(), e);
		}
		return (ModelValueSynth<M, MV>) new ModelValueSynth<ObservableAction<?>, ObservableAction<?>>() {
			@Override
			public ModelType<ObservableAction<?>> getModelType() {
				return ModelTypes.Action;
			}

			@Override
			public ModelInstanceType<ObservableAction<?>, ObservableAction<?>> getType() throws ExpressoInterpretationException {
				if (isVoid)
					return (ModelInstanceType<ObservableAction<?>, ObservableAction<?>>) type;
				else
					return (ModelInstanceType<ObservableAction<?>, ObservableAction<?>>) (ModelInstanceType<?, ?>) ModelTypes.Action
						.forType(context.getType().getType(0));
			}

			@Override
			public ObservableAction<?> get(ModelSetInstance models) throws ModelInstantiationException {
				SettableValue<Object> ctxValue = context.get(models);
				SettableValue<Object> valueValue = value.get(models);
				return ctxValue.assignmentTo(valueValue);
			}

			@Override
			public ObservableAction<?> forModelCopy(ObservableAction<?> value2, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
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
			public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
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