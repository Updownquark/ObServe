package org.observe.expresso.ops;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;
import org.qommons.io.ErrorReporting;

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
	public int getComponentOffset(int childIndex) {
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
	public List<? extends ObservableExpression> getComponents() {
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
	public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset) {
		return ModelTypes.Action;
	}

	@Override
	public <M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX {
		if (type.getModelType() != ModelTypes.Action)
			throw new ExpressoInterpretationException("Assignments cannot be used as " + type.getModelType() + "s",
				env.reporting().getPosition(), getExpressionLength());
		return (EvaluatedExpression<M, MV>) this
			.<Object, Object, EX> _evaluate((ModelInstanceType<ObservableAction, ObservableAction>) type, env, expressionOffset, exHandler);
	}

	private <S, T extends S, EX extends Throwable> EvaluatedExpression<ObservableAction, ObservableAction> _evaluate(
		ModelInstanceType<ObservableAction, ObservableAction> type, InterpretedExpressoEnv env, int expressionOffset,
		ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws ExpressoInterpretationException, EX {
		EvaluatedExpression<SettableValue<?>, SettableValue<S>> target;
		ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, EX, NeverThrown> doubleX = exHandler
			.stack(ExceptionHandler.holder());
		target = theTarget.evaluate(ModelTypes.Value.anyAs(), env, expressionOffset, doubleX);
		if (doubleX.get2() != null) {
			exHandler.handle1(new ExpressoInterpretationException(doubleX.get2().getMessage(), env.reporting().getPosition(),
				theTarget.getExpressionLength(), doubleX.get2()));
			return null;
		} else if (target == null)
			return null;
		EvaluatedExpression<SettableValue<?>, SettableValue<T>> value;
		int valueOffset = expressionOffset + theTarget.getExpressionLength() + 1;
		try (Transaction t = Invocation.asAction()) {
			value = theValue.evaluate(
				ModelTypes.Value.forType((TypeToken<T>) TypeTokens.get().getExtendsWildcard(target.getType().getType(0))),
				env.at(theTarget.getExpressionLength() + 1), valueOffset, doubleX);
			if (doubleX.get2() != null) {
				exHandler.handle1(new ExpressoInterpretationException(doubleX.get2().getMessage(),
					env.reporting().at(valueOffset).getPosition(), theValue.getExpressionLength(), doubleX.get2()));
				return null;
			} else if (value == null)
				return null;
		}
		ErrorReporting reporting = env.reporting();
		return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
			new InterpretedValueSynth<ObservableAction, ObservableAction>() {
			@Override
			public ModelType<ObservableAction> getModelType() {
				return ModelTypes.Action;
			}

			@Override
			public ModelInstanceType<ObservableAction, ObservableAction> getType() {
					return ModelTypes.Action.instance();
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return QommonsUtils.unmodifiableCopy(target, value);
			}

			@Override
			public ModelValueInstantiator<ObservableAction> instantiate() {
				return new Instantiator<>(target.instantiate(), value.instantiate(), reporting);
			}

			@Override
			public String toString() {
				return AssignmentExpression.this.toString();
			}
		}, null, target, value);
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

	static class Instantiator<S, T extends S> implements ModelValueInstantiator<ObservableAction> {
		private final ModelValueInstantiator<SettableValue<S>> theTarget;
		private final ModelValueInstantiator<SettableValue<T>> theSource;
		private final ErrorReporting theReporting;

		Instantiator(ModelValueInstantiator<SettableValue<S>> target, ModelValueInstantiator<SettableValue<T>> source,
			ErrorReporting reporting) {
			theTarget = target;
			theSource = source;
			theReporting = reporting;
		}

		@Override
		public void instantiate() {
			theTarget.instantiate();
			theSource.instantiate();
		}

		@Override
		public ObservableAction get(ModelSetInstance models) throws ModelInstantiationException {
			SettableValue<S> ctxValue = theTarget.get(models);
			SettableValue<T> valueValue = theSource.get(models);
			return ctxValue.assignmentTo(valueValue, err -> theReporting.error(null, err));
		}

		@Override
		public ObservableAction forModelCopy(ObservableAction value2, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			SettableValue<S> oldTarget = theTarget.get(sourceModels);
			SettableValue<S> newTarget = theTarget.forModelCopy(oldTarget, sourceModels, newModels);
			SettableValue<T> oldSource = theSource.get(sourceModels);
			SettableValue<T> newSource = theSource.get(newModels);
			if (oldTarget == newTarget && oldSource == newSource)
				return value2;
			else
				return newTarget.assignmentTo(newSource);
		}
	}
}
