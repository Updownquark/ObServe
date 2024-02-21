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
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;
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
	public <M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX {
		Set<Class<?>> types = env.getUnaryOperators().getSupportedInputTypes(theOperator);
		TypeToken<?> targetOpType;
		switch (types.size()) {
		case 0:
			exHandler.handle1(new ExpressoInterpretationException("Unsupported or unimplemented unary operator '" + theOperator + "'",
				env.reporting().at(getOperatorOffset()).getPosition(), theOperator.length()));
			return null;
		case 1:
			targetOpType = TypeTokens.get().of(types.iterator().next());
			break;
		default:
			targetOpType = TypeTokens.get().WILDCARD;
			break;
		}
		int operandOffset = expressionOffset + getComponentOffset(0);
		InterpretedExpressoEnv valueEnv = env.at(getComponentOffset(0));
		ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, EX, NeverThrown> doubleX = exHandler
			.stack(ExceptionHandler.holder());
		EvaluatedExpression<SettableValue<?>, SettableValue<Object>> op = theOperand
			.evaluate(ModelTypes.Value.forType((TypeToken<Object>) targetOpType), valueEnv, operandOffset, doubleX);
		if (doubleX.get2() != null) {
			exHandler.handle1(new ExpressoInterpretationException(doubleX.get2().getMessage(),
				env.reporting().at(getComponentOffset(0)).getPosition(), theOperand.getExpressionLength()));
			return null;
		} else if (op == null)
			return null;
		return (EvaluatedExpression<M, MV>) doOperation(type, op, env, expressionOffset, valueEnv.reporting(), exHandler);
	}

	private <M, MV extends M, S, EX extends Throwable> MV doOperation(ModelInstanceType<M, MV> type,
		EvaluatedExpression<SettableValue<?>, SettableValue<S>> op, InterpretedExpressoEnv env, int expressionOffset,
		ErrorReporting valueReporting, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX {
		TypeToken<S> opType = (TypeToken<S>) op.getType().getType(0);
		UnaryOp<S, ?> operator = env.getUnaryOperators().getOperator(theOperator, TypeTokens.getRawType(opType));
		ErrorReporting operatorReporting;
		if (isPrefix)
			operatorReporting = env.reporting();
		else
			operatorReporting = env.reporting().at(theOperand.getExpressionLength());
		if (operator == null) {
			exHandler.handle1(
				new ExpressoInterpretationException("Unary operator " + theOperator + " is not supported for operand type " + opType,
					env.reporting().getPosition(), getExpressionLength()));
			return null;
		} else if (operator.isActionOnly()) {
			if (type.getModelType() != ModelTypes.Action)
				throw new ExpressoInterpretationException("Unary operator " + theOperator + " can only be evaluated as an action",
					env.reporting().getPosition(), getExpressionLength());
			return (MV) evaluateAction(op, (UnaryOp<S, S>) operator, expressionOffset, valueReporting, operatorReporting, exHandler);
		} else {
			if (!operator.isActionOnly() && type.getModelType() != ModelTypes.Value)
				throw new ExpressoInterpretationException("Unary operator " + theOperator + " can only be evaluated as a value",
					env.reporting().getPosition(), getExpressionLength());
			return (MV) evaluateValue(opType, (TypeToken<Object>) type.getType(0), op, (UnaryOp<S, Object>) operator, expressionOffset,
				operatorReporting, exHandler);
		}
	}

	private <T, A, EX extends Throwable> EvaluatedExpression<ObservableAction, ObservableAction> evaluateAction(
		EvaluatedExpression<SettableValue<?>, SettableValue<T>> op, UnaryOp<T, T> operator, int expressionOffset,
		ErrorReporting valueReporting, ErrorReporting operatorReporting,
		ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws EX {
		return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
			op.map(ModelTypes.Action.instance(), opV -> new ActionInstantiator<>(opV, operator, valueReporting, operatorReporting)),
			operator, op);
	}

	private <S, T, EX extends Throwable> EvaluatedExpression<SettableValue<?>, SettableValue<T>> evaluateValue(TypeToken<S> opType,
		TypeToken<T> type, EvaluatedExpression<SettableValue<?>, SettableValue<S>> op, UnaryOp<S, T> operator, int expressionOffset,
		ErrorReporting operatorReporting, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws EX {
		TypeToken<T> operatorType = TypeTokens.get().of(operator.getTargetType());
		TypeTokens.TypeConverter<T, T, T, T> cast;
		if (TypeTokens.get().wrap(type).equals(TypeTokens.get().wrap(operatorType))) {
			type = operatorType;
			cast = null;
		} else {
			ExceptionHandler.Single<IllegalArgumentException, NeverThrown> iae = ExceptionHandler.<IllegalArgumentException> holder()
				.fillStackTrace(true);
			cast = (TypeConverter<T, T, T, T>) TypeTokens.get().getCast(type, operatorType, false, true, iae);
			if (cast == null) {
				exHandler.handle1(new ExpressoInterpretationException(
					this + " cannot be evaluated as a " + ModelTypes.Value.getName() + "<" + opType + ">", operatorReporting.getPosition(),
					theOperator.length(), iae.get1()));
				return null;
			}
			type = cast.getConvertedType();
		}
		return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
			op.map(ModelTypes.Value.forType(type), opV -> new ValueInstantiator<>(op.instantiate(), operator, cast, operatorReporting)),
			operator, op);
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

	static class ActionInstantiator<T, R> implements ModelValueInstantiator<ObservableAction> {
		private final ModelValueInstantiator<SettableValue<T>> theValue;
		private final UnaryOp<T, T> theOperator;
		private final ErrorReporting theValueReporting;
		private final ErrorReporting theOperatorReporting;

		ActionInstantiator(ModelValueInstantiator<SettableValue<T>> value, UnaryOp<T, T> operator, ErrorReporting valueReporting,
			ErrorReporting operatorReporting) {
			theValue = value;
			theOperator = operator;
			theValueReporting = valueReporting;
			theOperatorReporting = operatorReporting;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			theValue.instantiate();
		}

		@Override
		public ObservableAction get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			SettableValue<T> value = theValue.get(models);
			return actionFor(value);
		}

		private ObservableAction actionFor(SettableValue<T> value) {
			ObservableValue<String> enabled = value.assignmentTo(value.map(theOperator::apply)).isEnabled();
			return new ObservableAction() {
				@Override
				public void act(Object cause) throws IllegalStateException {
					T newValue;
					try {
						newValue = theOperator.apply(value.get());
					} catch (RuntimeException | Error e) {
						theValueReporting.error(null, e);
						return;
					}
					try {
						value.set(newValue, cause);
					} catch (RuntimeException | Error e) {
						theOperatorReporting.error(null, e);
						return;
					}
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return enabled;
				}
			};
		}

		@Override
		public ObservableAction forModelCopy(ObservableAction value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			SettableValue<T> oldSource = theValue.get(sourceModels);
			SettableValue<T> newSource = theValue.forModelCopy(oldSource, sourceModels, newModels);
			if (oldSource == newSource)
				return value;
			return actionFor(newSource);
		}
	}

	static class ValueInstantiator<S, T> implements ModelValueInstantiator<SettableValue<T>> {
		private final ModelValueInstantiator<SettableValue<S>> theSource;
		private final UnaryOp<S, T> theOperator;
		private final TypeTokens.TypeConverter<T, T, T, T> theCast;
		private final ErrorReporting theOperatorReporting;

		ValueInstantiator(ModelValueInstantiator<SettableValue<S>> source, UnaryOp<S, T> operator, TypeConverter<T, T, T, T> cast,
			ErrorReporting operatorReporting) {
			theSource = source;
			theOperator = operator;
			theCast = cast;
			theOperatorReporting = operatorReporting;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			theSource.instantiate();
		}

		@Override
		public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			SettableValue<S> sourceV = theSource.get(models);
			return transform(sourceV);
		}

		private SettableValue<T> transform(SettableValue<S> sourceV) {
			return sourceV.transformReversible(tx -> tx//
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
