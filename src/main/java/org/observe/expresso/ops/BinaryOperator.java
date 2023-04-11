package org.observe.expresso.ops;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Transformation;
import org.observe.Transformation.ReverseQueryResult;
import org.observe.Transformation.TransformationValues;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.ops.BinaryOperatorSet.BinaryOp;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterList;

import com.google.common.reflect.TypeToken;

/** An expression representing an operation that takes 2 inputs */
public class BinaryOperator implements ObservableExpression {
	private final String theOperator;
	private final ObservableExpression theLeft;
	private final ObservableExpression theRight;

	/**
	 * @param operator The name of the operation
	 * @param left The first operation input
	 * @param right The second operation input
	 */
	public BinaryOperator(String operator, ObservableExpression left, ObservableExpression right) {
		theOperator = operator;
		theLeft = left;
		theRight = right;
	}

	/** @return The name of the operation */
	public String getOperator() {
		return theOperator;
	}

	/** @return The first operation input */
	public ObservableExpression getLeft() {
		return theLeft;
	}

	/** @return The second operation input */
	public ObservableExpression getRight() {
		return theRight;
	}

	@Override
	public int getChildOffset(int childIndex) {
		switch (childIndex) {
		case 0:
			return 0;
		case 1:
			return theLeft.getExpressionLength() + 1;
		default:
			throw new IndexOutOfBoundsException(childIndex + " of 2");
		}
	}

	@Override
	public int getExpressionLength() {
		return theLeft.getExpressionLength() + 1 + theRight.getExpressionLength();
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return QommonsUtils.unmodifiableCopy(theLeft, theRight);
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		ObservableExpression replacement = replace.apply(this);
		if (replacement != this)
			return replacement;
		ObservableExpression left = theLeft.replaceAll(replace);
		ObservableExpression right = theRight.replaceAll(replace);
		if (left != theLeft || right != theRight)
			return new BinaryOperator(theOperator, left, right);
		return this;
	}

	@Override
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env, int expressionOffset)
		throws ExpressoEvaluationException, ExpressoInterpretationException {
		if (type.getModelType() == ModelTypes.Action) {//
		} else if (type.getModelType() == ModelTypes.Value) {//
		} else
			throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				"Binary operator " + theOperator + " can only be evaluated as a value or an action");
		boolean action = theOperator.charAt(theOperator.length() - 1) == '=';
		if (action) {
			switch (theOperator) {
			case "==":
			case "!=":
			case "<=":
			case ">=":
				action = false;
				break;
			}
		}
		String operator = action ? theOperator.substring(0, theOperator.length() - 1) : theOperator;
		Class<?> targetType = TypeTokens.getRawType(type.getType(0));
		Set<Class<?>> types = env.getBinaryOperators().getSupportedPrimaryInputTypes(operator, targetType);
		TypeToken<?> targetOpType;
		switch (types.size()) {
		case 0:
			throw new ExpressoEvaluationException(expressionOffset + theLeft.getExpressionLength(), theOperator.length(),
				"Unsupported or unimplemented binary operator '" + theOperator + "' targeting type " + targetType.getName());
		case 1:
			targetOpType = TypeTokens.get().of(types.iterator().next());
			break;
		default:
			targetOpType = TypeTokens.get().WILDCARD;
			break;
		}
		ValueContainer<SettableValue<?>, SettableValue<Object>> left;
		try {
			left = theLeft.evaluate(ModelTypes.Value.forType((TypeToken<Object>) targetOpType), env, expressionOffset);
		} catch (TypeConversionException e) {
			throw new ExpressoEvaluationException(expressionOffset, theLeft.getExpressionLength(), e.getMessage(), e);
		}
		TypeToken<?> leftTypeT;
		try {
			leftTypeT = left.getType().getType(0);
		} catch (ExpressoInterpretationException e) {
			throw new ExpressoEvaluationException(expressionOffset, theLeft.getExpressionLength(), e.getMessage(), e);
		}
		Class<?> leftType = TypeTokens.getRawType(leftTypeT);
		types = env.getBinaryOperators().getSupportedSecondaryInputTypes(operator, targetType, leftType);
		switch (types.size()) {
		case 0:
			throw new ExpressoEvaluationException(expressionOffset + theLeft.getExpressionLength(), theOperator.length(),
				"Binary operator '" + theOperator + "' is not supported or implemented for left operand type " + leftTypeT
				+ ", target type " + targetType.getName());
		case 1:
			targetOpType = TypeTokens.get().of(types.iterator().next());
			break;
		default:
			targetOpType = TypeTokens.get().WILDCARD;
			break;
		}
		int rightOffset = expressionOffset + theLeft.getExpressionLength() + 1;
		ValueContainer<SettableValue<?>, SettableValue<Object>> right;
		try {
			right = theRight.evaluate(ModelTypes.Value.forType((TypeToken<Object>) targetOpType), env, rightOffset);
		} catch (TypeConversionException e) {
			throw new ExpressoEvaluationException(rightOffset, theRight.getExpressionLength(), e.getMessage(), e);
		}
		TypeToken<?> rightTypeT;
		try {
			rightTypeT = right.getType().getType(0);
		} catch (ExpressoInterpretationException e) {
			throw new ExpressoEvaluationException(rightOffset, theRight.getExpressionLength(), e.getMessage(), e);
		}
		BinaryOp<Object, Object, Object> op;
		op = (BinaryOp<Object, Object, Object>) env.getBinaryOperators().getOperator(operator, targetType, //
			leftType, TypeTokens.getRawType(rightTypeT));
		if (op == null)
			throw new ExpressoEvaluationException(expressionOffset + theLeft.getExpressionLength(), theOperator.length(),
				"Binary operator '" + theOperator + "' is not supported or implemented for operand types " + leftTypeT + " and "
					+ rightTypeT + ", target type " + targetType.getName());
		TypeToken<Object> resultType = op.getTargetType(//
			leftTypeT, rightTypeT);
		if (action) {
			if (type.getModelType() != ModelTypes.Action)
				throw new ExpressoEvaluationException(expressionOffset + theLeft.getExpressionLength(), theOperator.length(),
					"Binary operator " + theOperator + " can only be evaluated as an action");
			TypeToken<Object> actionType;
			boolean voidAction = TypeTokens.get().unwrap(TypeTokens.getRawType(type.getType(0))) == void.class;
			if (voidAction)
				actionType = (TypeToken<Object>) (TypeToken<?>) TypeTokens.get().VOID;
			else if (TypeTokens.get().isAssignable(type.getType(0), resultType))
				actionType = resultType;
			else
				throw new ExpressoEvaluationException(expressionOffset + theLeft.getExpressionLength(), theOperator.length(),
					this + " cannot be evaluated as an " + ModelTypes.Action.getName() + "<" + type.getType(0) + ">");
			return (ValueContainer<M, MV>) new ValueContainer<ObservableAction<?>, ObservableAction<Object>>() {
				@Override
				public ModelInstanceType<ObservableAction<?>, ObservableAction<Object>> getType() {
					return ModelTypes.Action.forType(actionType);
				}

				@Override
				public ObservableAction<Object> get(ModelSetInstance msi) throws ModelInstantiationException {
					SettableValue<Object> leftV = left.get(msi);
					SettableValue<Object> rightV = right.get(msi);
					return createOpAction(leftV, rightV);
				}

				private ObservableAction<Object> createOpAction(SettableValue<Object> leftV, SettableValue<Object> rightV) {
					ObservableValue<Object> result = leftV.transform(resultType, tx -> tx.combineWith(rightV)//
						.combine((lft, rgt) -> op.apply(lft, rgt)));
					ObservableValue<String> leftEnabled = leftV.isEnabled();
					ObservableValue<String> enabled = result.transform(String.class, tx -> tx.combineWith(leftEnabled)//
						.combine((res, le) -> {
							if (le != null)
								return le;
							Object leftVV = leftV.get();
							Object rightVV = rightV.get();
							String msg = op.canReverse(leftVV, rightVV, res);
							if (msg != null)
								return msg;
							Object lft = op.reverse(leftVV, rightVV, res);
							return leftV.isAcceptable(lft);
						}));
					return new ObservableAction<Object>() {
						@Override
						public TypeToken<Object> getType() {
							return resultType;
						}

						@Override
						public Object act(Object cause) throws IllegalStateException {
							return leftV.set(result.get(), cause);
						}

						@Override
						public ObservableValue<String> isEnabled() {
							return enabled;
						}
					};
				}

				@Override
				public ObservableAction<Object> forModelCopy(ObservableAction<Object> value, ModelSetInstance sourceModels,
					ModelSetInstance newModels) throws ModelInstantiationException {
					SettableValue<Object> sourceLeft = left.get(sourceModels);
					SettableValue<Object> newLeft = left.get(newModels);
					SettableValue<Object> sourceRight = right.get(sourceModels);
					SettableValue<Object> newRight = right.get(newModels);
					if (sourceLeft == newLeft && sourceRight == newRight)
						return value;
					else
						return createOpAction(newLeft, newRight);
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.of(left, right), vc -> vc.getCores().stream());
				}

				@Override
				public String toString() {
					return BinaryOperator.this.toString();
				}
			};
		} else {
			if (type.getModelType() != ModelTypes.Value)
				throw new ExpressoEvaluationException(expressionOffset + theLeft.getExpressionLength(), theOperator.length(),
					"Binary operator " + theOperator + " can only be evaluated as a value");
			ValueContainer<SettableValue<?>, SettableValue<Object>> operated = new ValueContainer<SettableValue<?>, SettableValue<Object>>() {
				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<Object>> getType() {
					return ModelTypes.Value.forType(resultType);
				}

				@Override
				public SettableValue<Object> get(ModelSetInstance msi) throws ModelInstantiationException {
					SettableValue<Object> leftV = left.get(msi);
					SettableValue<Object> rightV = right.get(msi);
					return createOpValue(leftV, rightV);
				}

				private SettableValue<Object> createOpValue(SettableValue<Object> leftV, SettableValue<Object> rightV) {
					return leftV.transformReversible(resultType, tx -> tx.combineWith(rightV)//
						.combine(LambdaUtils.printableBiFn((lft, rgt) -> op.apply(lft, rgt), op::toString, op))//
						.withReverse(new Transformation.TransformReverse<Object, Object>() {
							@Override
							public boolean isStateful() {
								return true;
							}

							@Override
							public String isEnabled(TransformationValues<Object, Object> transformValues) {
								return null;
							}

							@Override
							public ReverseQueryResult<Object> reverse(Object newValue, TransformationValues<Object, Object> transformValues,
								boolean add, boolean test) {
								Object rgt = transformValues.get(rightV);
								String msg = op.canReverse(transformValues.getCurrentSource(), rgt, newValue);
								if (msg != null)
									return ReverseQueryResult.reject(msg);
								return ReverseQueryResult.value(op.reverse(transformValues.getCurrentSource(), rgt, newValue));
							}
						}));
				}

				@Override
				public SettableValue<Object> forModelCopy(SettableValue<Object> value, ModelSetInstance sourceModels,
					ModelSetInstance newModels) throws ModelInstantiationException {
					SettableValue<Object> sourceLeft = left.get(sourceModels);
					SettableValue<Object> newLeft = left.get(newModels);
					SettableValue<Object> sourceRight = right.get(sourceModels);
					SettableValue<Object> newRight = right.get(newModels);
					if (sourceLeft == newLeft && sourceRight == newRight)
						return value;
					else
						return createOpValue(newLeft, newRight);
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() throws ExpressoInterpretationException {
					return BetterList.of(Stream.of(left, right), vc -> vc.getCores().stream());
				}

				@Override
				public String toString() {
					return BinaryOperator.this.toString();
				}
			};
			return (ValueContainer<M, MV>) operated;
		}
	}

	@Override
	public String toString() {
		return theLeft + " " + theOperator + " " + theRight;
	}
}