package org.observe.expresso.ops;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ops.UnaryOperatorSet.UnaryOp;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class UnaryOperator implements ObservableExpression {
	private final String theOperator;
	private final ObservableExpression theOperand;
	private final boolean isPrefix;

	public UnaryOperator(String operator, ObservableExpression operand, boolean prefix) {
		theOperator = operator;
		theOperand = operand;
		isPrefix = prefix;
	}

	public String getOperator() {
		return theOperator;
	}

	public ObservableExpression getOperand() {
		return theOperand;
	}

	public boolean isPrefix() {
		return isPrefix;
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return Collections.singletonList(theOperand);
	}

	@Override
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException {
		Set<Class<?>> types = env.getUnaryOperators().getSupportedSourceTypes(theOperator);
		TypeToken<?> targetOpType;
		switch (types.size()) {
		case 0:
			throw new QonfigInterpretationException("Unsupported or unimplemented unary operator '" + theOperator + "'");
		case 1:
			targetOpType = TypeTokens.get().of(types.iterator().next());
			break;
		default:
			targetOpType = TypeTokens.get().WILDCARD;
			break;
		}
		return (ValueContainer<M, MV>) doOperation(type, //
			theOperand.evaluate(ModelTypes.Value.forType(targetOpType), env), env);
	}

	private <M, MV extends M, S> MV doOperation(ModelInstanceType<M, MV> type, ValueContainer<SettableValue<?>, SettableValue<S>> op,
		ExpressoEnv env) throws QonfigInterpretationException {
		UnaryOp<S, ?> operator = env.getUnaryOperators().getOperator(theOperator,
			(Class<S>) TypeTokens.getRawType(op.getType().getType(0)));
		if (operator == null)
			throw new QonfigInterpretationException(
				"Unary operator " + theOperator + " is not supported for operand type " + op.getType().getType(0));
		else if (operator.isActionOnly()) {
			if (type.getModelType() != ModelTypes.Action)
				throw new QonfigInterpretationException("Unary operator " + theOperator + " can only be evaluated as an action");
			return (MV) evaluateAction(type.getType(0), op, (UnaryOp<S, S>) operator, env);
		} else {
			if (!operator.isActionOnly() && type.getModelType() != ModelTypes.Value)
				throw new QonfigInterpretationException("Unary operator " + theOperator + " can only be evaluated as a value");
			return (MV) evaluateValue((TypeToken<Object>) type.getType(0), op, (UnaryOp<S, Object>) operator, env);
		}
	}

	private <T, A> ValueContainer<ObservableAction<?>, ObservableAction<A>> evaluateAction(TypeToken<A> actionType,
		ValueContainer<SettableValue<?>, SettableValue<T>> op, UnaryOp<T, T> operator, ExpressoEnv env)
			throws QonfigInterpretationException {
		boolean voidAction = TypeTokens.get().unwrap(TypeTokens.getRawType(actionType)) == void.class;
		if (!voidAction) {
			if (TypeTokens.get().isAssignable(actionType, TypeTokens.get().of(operator.getTargetType())))
				actionType = (TypeToken<A>) TypeTokens.get().of(operator.getTargetType());
			else
				throw new QonfigInterpretationException(
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
					T newValue = operator.apply(opV.get());
					T oldValue = opV.set(newValue, cause);
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

	private <S, T> ValueContainer<SettableValue<?>, SettableValue<T>> evaluateValue(TypeToken<T> type,
		ValueContainer<SettableValue<?>, SettableValue<S>> op, UnaryOp<S, T> operator, ExpressoEnv env)
			throws QonfigInterpretationException {
		if (TypeTokens.get().isAssignable(type, TypeTokens.get().of(operator.getTargetType())))
			type = TypeTokens.get().of(operator.getTargetType());
		else
			throw new QonfigInterpretationException(
				this + " cannot be evaluated as an " + ModelTypes.Action.getName() + "<" + op.getType().getType(0) + ">");
		TypeToken<T> fType = type;
		return op.map(ModelTypes.Value.forType(type), opV -> opV.transformReversible(fType, tx -> tx//
			.map(operator::apply).withReverse(operator::reverse)));
	}

	@Override
	public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ExpressoEnv env)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not supported for unary operators");
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
			return theOperator + theOperand;
		else
			return theOperand + theOperator;
	}
}