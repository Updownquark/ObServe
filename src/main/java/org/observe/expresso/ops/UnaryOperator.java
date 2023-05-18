package org.observe.expresso.ops;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.ops.UnaryOperatorSet.UnaryOp;
import org.observe.util.TypeTokens;
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
	public int getChildOffset(int childIndex) {
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
	public List<? extends ObservableExpression> getChildren() {
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
	public ModelType<?> getModelType(ExpressoEnv env) {
		Set<UnaryOp<?, ?>> operators = env.getUnaryOperators().getOperators(theOperator);
		if (operators.isEmpty())
			throw new IllegalStateException("No such operator found: " + theOperator);
		return operators.iterator().next().isActionOnly() ? ModelTypes.Action : ModelTypes.Value;
	}

	@Override
	public <M, MV extends M> ModelValueSynth<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env, int expressionOffset)
		throws ExpressoEvaluationException, ExpressoInterpretationException {
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
		int operandOffset = expressionOffset + getChildOffset(0);
		ExpressoEnv valueEnv = env.at(getChildOffset(0));
		try {
			return (ModelValueSynth<M, MV>) doOperation(type,
				theOperand.evaluate(ModelTypes.Value.forType(targetOpType), valueEnv, operandOffset), env, expressionOffset,
				valueEnv.reporting());
		} catch (TypeConversionException e) {
			throw new ExpressoEvaluationException(operandOffset, theOperand.getExpressionLength(), e.getMessage(), e);
		}
	}

	private <M, MV extends M, S> MV doOperation(ModelInstanceType<M, MV> type, ModelValueSynth<SettableValue<?>, SettableValue<S>> op,
		ExpressoEnv env, int expressionOffset, ErrorReporting valueReporting)
			throws ExpressoEvaluationException, ExpressoInterpretationException {
		TypeToken<S> opType;
		int operandOffset = expressionOffset + getChildOffset(0);
		try {
			opType = (TypeToken<S>) op.getType().getType(0);
		} catch (ExpressoInterpretationException e) {
			throw new ExpressoEvaluationException(operandOffset, theOperand.getExpressionLength(), e.getMessage(), e);
		}
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

	private <T, A> ModelValueSynth<ObservableAction<?>, ObservableAction<A>> evaluateAction(TypeToken<A> actionType,
		ModelValueSynth<SettableValue<?>, SettableValue<T>> op, UnaryOp<T, T> operator, int expressionOffset, ErrorReporting valueReporting,
		ErrorReporting operatorReporting) throws ExpressoEvaluationException {
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
		return op.map(ModelTypes.Action.forType(actionType), opV -> {
			ObservableAction<T> assignmentAction = opV.assignmentTo(opV.map(operator::apply));
			return new ObservableAction<A>() {
				@Override
				public TypeToken<A> getType() {
					return fActionType;
				}

				@Override
				public A act(Object cause) throws IllegalStateException {
					T newValue;
					try {
						newValue = operator.apply(opV.get());
					} catch (RuntimeException | Error e) {
						valueReporting.error(null, e);
						return null;
					}
					T oldValue;
					try {
						oldValue = opV.set(newValue, cause);
					} catch (RuntimeException | Error e) {
						operatorReporting.error(null, e);
						return null;
					}
					if (voidAction)
						return null;
					else if (prefix)
						return (A) newValue;
					else
						return (A) oldValue;
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return assignmentAction.isEnabled();
				}
			};
		});
	}

	private <S, T> ModelValueSynth<SettableValue<?>, SettableValue<T>> evaluateValue(TypeToken<S> opType, TypeToken<T> type,
		ModelValueSynth<SettableValue<?>, SettableValue<S>> op, UnaryOp<S, T> operator, int expressionOffset,
		ErrorReporting operatorReporting)
			throws ExpressoEvaluationException {
		if (TypeTokens.get().isAssignable(type, TypeTokens.get().of(operator.getTargetType())))
			type = TypeTokens.get().of(operator.getTargetType());
		else
			throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				this + " cannot be evaluated as a " + ModelTypes.Value.getName() + "<" + opType + ">");
		TypeToken<T> fType = type;
		return op.map(ModelTypes.Value.forType(type), opV -> opV.transformReversible(fType, tx -> tx//
			.map(LambdaUtils.printableFn(v -> {
				try {
					return operator.apply(v);
				} catch (RuntimeException | Error e) {
					operatorReporting.error(null, e);
					return null;
				}
			}, operator::toString, operator))//
			.withReverse(v -> {
				try {
					return operator.reverse(v);
				} catch (RuntimeException | Error e) {
					operatorReporting.error(null, e);
					return null;
				}
			})));
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
}