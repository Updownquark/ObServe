package org.observe.expresso.ops;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.ops.UnaryOperatorSet.UnaryOp;
import org.observe.util.TypeTokens;
import org.observe.util.TypeTokens.TypeConverter;
import org.qommons.LambdaUtils;
import org.qommons.io.ErrorReporting;

import com.google.common.reflect.TypeToken;

/** An expression representing an operation that takes 1 input */
public class UnaryOperator implements ObservableExpression {
	private final String theOperator;
	private final ObservableExpression theOperand;
	private final boolean isPrefix;

	/**
	 * @param operator The name of the operator
	 * @param operand The operand
	 * @param prefix Whether the operator is a prefix or suffix operation
	 */
	public UnaryOperator(String operator, ObservableExpression operand, boolean prefix) {
		theOperator = operator;
		theOperand = operand;
		isPrefix = prefix;
	}

	/** @return The name of the operator */
	public String getOperator() {
		return theOperator;
	}

	/** @return The operand of this operation */
	public ObservableExpression getOperand() {
		return theOperand;
	}

	/** @return Whether this is a prefix or suffix operation (whether the operator occurred before or after the operand) */
	public boolean isPrefix() {
		return isPrefix;
	}

	@Override
	public int getComponentOffset(int childIndex) {
		if (childIndex != 0)
			throw new IndexOutOfBoundsException(childIndex + " of 1");
		if (isPrefix)
			return theOperator.length();
		else
			return 0;
	}

	@Override
	public int getExpressionLength() {
		return theOperator.length() + theOperand.getExpressionLength();
	}

	/** @return The starting position of this expression's operator in this expression */
	public int getOperatorOffset() {
		if (isPrefix)
			return 0;
		else
			return theOperand.getExpressionLength();
	}

	@Override
	public List<? extends ObservableExpression> getComponents() {
		return Collections.singletonList(theOperand);
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		ObservableExpression replacement = replace.apply(this);
		if (replacement != this)
			return replacement;
		ObservableExpression op = theOperand.replaceAll(replace);
		if (op != theOperand)
			return new UnaryOperator(theOperator, op, isPrefix);
		return this;
	}

	@Override
	public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset) {
		Set<UnaryOp<?, ?>> operators = env.getUnaryOperators().getOperators(theOperator);
		if (operators.isEmpty())
			throw new IllegalStateException("No such operator found: " + theOperator);
		return operators.iterator().next().isActionOnly() ? ModelTypes.Action : ModelTypes.Value;
	}

	@Override
	public <M, MV extends M> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, InterpretedExpressoEnv env,
		int expressionOffset) throws ExpressoEvaluationException, ExpressoInterpretationException {
		Set<Class<?>> types = env.getUnaryOperators().getSupportedInputTypes(theOperator);
		TypeToken<?> targetOpType;
		switch (types.size()) {
		case 0:
			throw new ExpressoEvaluationException(expressionOffset + getOperatorOffset(), theOperator.length(),
				"Unsupported or unimplemented unary operator '" + theOperator + "'");
		case 1:
			targetOpType = TypeTokens.get().of(types.iterator().next());
			break;
		default:
			targetOpType = TypeTokens.get().WILDCARD;
			break;
		}
		int operandOffset = expressionOffset + getComponentOffset(0);
		InterpretedExpressoEnv valueEnv = env.at(getComponentOffset(0));
		try {
			return (EvaluatedExpression<M, MV>) doOperation(type,
				theOperand.evaluate(ModelTypes.Value.forType(targetOpType), valueEnv, operandOffset), env, expressionOffset,
				valueEnv.reporting());
		} catch (TypeConversionException e) {
			throw new ExpressoEvaluationException(operandOffset, theOperand.getExpressionLength(), e.getMessage(), e);
		}
	}

	private <M, MV extends M, S> MV doOperation(ModelInstanceType<M, MV> type, EvaluatedExpression<SettableValue<?>, SettableValue<S>> op,
		InterpretedExpressoEnv env, int expressionOffset, ErrorReporting valueReporting)
			throws ExpressoEvaluationException, ExpressoInterpretationException {
		TypeToken<S> opType = (TypeToken<S>) op.getType().getType(0);
		UnaryOp<S, ?> operator = env.getUnaryOperators().getOperator(theOperator, TypeTokens.getRawType(opType));
		ErrorReporting operatorReporting;
		if (isPrefix)
			operatorReporting = env.reporting();
		else
			operatorReporting = env.reporting().at(theOperand.getExpressionLength());
		if (operator == null)
			throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				"Unary operator " + theOperator + " is not supported for operand type " + opType);
		else if (operator.isActionOnly()) {
			if (type.getModelType() != ModelTypes.Action)
				throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
					"Unary operator " + theOperator + " can only be evaluated as an action");
			return (MV) evaluateAction(type.getType(0), op, (UnaryOp<S, S>) operator, expressionOffset, valueReporting, operatorReporting);
		} else {
			if (!operator.isActionOnly() && type.getModelType() != ModelTypes.Value)
				throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
					"Unary operator " + theOperator + " can only be evaluated as a value");
			return (MV) evaluateValue(opType, (TypeToken<Object>) type.getType(0), op, (UnaryOp<S, Object>) operator, expressionOffset,
				operatorReporting);
		}
	}

	private <T, A> EvaluatedExpression<ObservableAction<?>, ObservableAction<A>> evaluateAction(TypeToken<A> actionType,
		EvaluatedExpression<SettableValue<?>, SettableValue<T>> op, UnaryOp<T, T> operator, int expressionOffset,
		ErrorReporting valueReporting, ErrorReporting operatorReporting) throws ExpressoEvaluationException {
		boolean voidAction = TypeTokens.get().unwrap(TypeTokens.getRawType(actionType)) == void.class;
		if (!voidAction) {
			if (TypeTokens.get().isAssignable(actionType, TypeTokens.get().of(operator.getTargetType())))
				actionType = (TypeToken<A>) TypeTokens.get().of(operator.getTargetType());
			else
				throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
					this + " cannot be evaluated as an " + ModelTypes.Action.getName() + "<" + actionType + ">");
		}
		boolean prefix = isPrefix;
		TypeToken<A> fActionType = actionType;
		return ObservableExpression.evEx(expressionOffset, getExpressionLength(), op.map(ModelTypes.Action.forType(actionType),
			opV -> new ActionInstantiator<>(voidAction, prefix, opV, operator, valueReporting, operatorReporting)), operator, op);
	}

	private <S, T> EvaluatedExpression<SettableValue<?>, SettableValue<T>> evaluateValue(TypeToken<S> opType, TypeToken<T> type,
		EvaluatedExpression<SettableValue<?>, SettableValue<S>> op, UnaryOp<S, T> operator, int expressionOffset,
		ErrorReporting operatorReporting) throws ExpressoEvaluationException {
		TypeToken<T> operatorType = TypeTokens.get().of(operator.getTargetType());
		TypeTokens.TypeConverter<T, T, T, T> cast;
		if (TypeTokens.get().wrap(type).equals(TypeTokens.get().wrap(operatorType))) {
			type = operatorType;
			cast = null;
		} else {
			try {
				cast = (TypeConverter<T, T, T, T>) TypeTokens.get().getCast(type, operatorType);
			} catch (IllegalArgumentException e) {
				throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
					this + " cannot be evaluated as a " + ModelTypes.Value.getName() + "<" + opType + ">", e);
			}
			type = cast.getConvertedType();
		}
		TypeToken<T> fType = type;
		return ObservableExpression.evEx(expressionOffset, getExpressionLength(), op.map(ModelTypes.Value.forType(type),
			opV -> new ValueInstantiator<>(fType, op.instantiate(), operator, cast, operatorReporting)), operator, op);
	}

	@Override
	public int hashCode() {
		return Objects.hash(theOperator, theOperand, isPrefix);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (!(obj instanceof UnaryOperator))
			return false;
		UnaryOperator other = (UnaryOperator) obj;
		return theOperator.equals(other.theOperator) && theOperand.equals(other.theOperand) && isPrefix == other.isPrefix;
	}

	@Override
	public String toString() {
		if (isPrefix)
			return theOperator.toString() + theOperand;
		else
			return theOperand + theOperator.toString();
	}

	static class ActionInstantiator<T, R> implements ModelValueInstantiator<ObservableAction<R>> {
		private final boolean isVoid;
		private final boolean isPrefix;
		private final ModelValueInstantiator<SettableValue<T>> theValue;
		private final UnaryOp<T, T> theOperator;
		private final ErrorReporting theValueReporting;
		private final ErrorReporting theOperatorReporting;

		ActionInstantiator(boolean void1, boolean prefix, ModelValueInstantiator<SettableValue<T>> value, UnaryOp<T, T> operator,
			ErrorReporting valueReporting, ErrorReporting operatorReporting) {
			isVoid = void1;
			isPrefix = prefix;
			theValue = value;
			theOperator = operator;
			theValueReporting = valueReporting;
			theOperatorReporting = operatorReporting;
		}

		@Override
		public void instantiate() {
			theValue.instantiate();
		}

		@Override
		public ObservableAction<R> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			SettableValue<T> value = theValue.get(models);
			return actionFor(value);
		}

		private ObservableAction<R> actionFor(SettableValue<T> value) {
			ObservableValue<String> enabled = value.assignmentTo(value.map(theOperator::apply)).isEnabled();
			return new ObservableAction<R>() {
				@Override
				public TypeToken<R> getType() {
					return (TypeToken<R>) (isVoid ? TypeTokens.get().VOID : value.getType());
				}

				@Override
				public R act(Object cause) throws IllegalStateException {
					T newValue;
					try {
						newValue = theOperator.apply(value.get());
					} catch (RuntimeException | Error e) {
						theValueReporting.error(null, e);
						return null;
					}
					T oldValue;
					try {
						oldValue = value.set(newValue, cause);
					} catch (RuntimeException | Error e) {
						theOperatorReporting.error(null, e);
						return null;
					}
					if (isVoid)
						return null;
					else if (isPrefix)
						return (R) newValue;
					else
						return (R) oldValue;
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return enabled;
				}
			};
		}

		@Override
		public ObservableAction<R> forModelCopy(ObservableAction<R> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			SettableValue<T> oldSource = theValue.get(sourceModels);
			SettableValue<T> newSource = theValue.forModelCopy(oldSource, sourceModels, newModels);
			if (oldSource == newSource)
				return value;
			return actionFor(newSource);
		}
	}

	static class ValueInstantiator<S, T> implements ModelValueInstantiator<SettableValue<T>> {
		private final TypeToken<T> theResultType;
		private final ModelValueInstantiator<SettableValue<S>> theSource;
		private final UnaryOp<S, T> theOperator;
		private final TypeTokens.TypeConverter<T, T, T, T> theCast;
		private final ErrorReporting theOperatorReporting;

		ValueInstantiator(TypeToken<T> resultType, ModelValueInstantiator<SettableValue<S>> source, UnaryOp<S, T> operator,
			TypeConverter<T, T, T, T> cast, ErrorReporting operatorReporting) {
			theResultType = resultType;
			theSource = source;
			theOperator = operator;
			theCast = cast;
			theOperatorReporting = operatorReporting;
		}

		@Override
		public void instantiate() {
			theSource.instantiate();
		}

		@Override
		public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			SettableValue<S> sourceV = theSource.get(models);
			return transform(sourceV);
		}

		private SettableValue<T> transform(SettableValue<S> sourceV) {
			return sourceV.transformReversible(theResultType, tx -> tx//
				.map(LambdaUtils.printableFn(v -> {
					try {
						T result = theOperator.apply(v);
						if (theCast != null)
							result = theCast.apply(result);
						return result;
					} catch (RuntimeException | Error e) {
						theOperatorReporting.error(null, e);
						return null;
					}
				}, theOperator::toString, theOperator))//
				.replaceSource(v -> {
					try {
						if (theCast != null)
							v = theCast.reverse(v);
						return theOperator.reverse(v);
					} catch (RuntimeException | Error e) {
						theOperatorReporting.error(null, e);
						return null;
					}
				}, rev -> {
					if (theCast == null)
						return rev;
					else
						return rev.rejectWith(theCast::isReversible);
				}));
		}

		@Override
		public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			SettableValue<S> oldSource = theSource.get(sourceModels);
			SettableValue<S> newSource = theSource.forModelCopy(oldSource, sourceModels, newModels);
			if (oldSource == newSource)
				return value;
			else
				return transform(newSource);
		}
	}
}
