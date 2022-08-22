package org.observe.expresso.ops;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Transformation;
import org.observe.Transformation.ReverseQueryResult;
import org.observe.Transformation.TransformReverse;
import org.observe.Transformation.TransformationValues;
import org.observe.expresso.ClassView;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ops.BinaryOperatorSet.BinaryOp;
import org.observe.util.TypeTokens;
import org.observe.util.TypeTokens.TypeConverter;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class BinaryOperator implements ObservableExpression {
	private final String theOperator;
	private final ObservableExpression theLeft;
	private final ObservableExpression theRight;

	public BinaryOperator(String operator, ObservableExpression left, ObservableExpression right) {
		theOperator = operator;
		theLeft = left;
		theRight = right;
	}

	public String getOperator() {
		return theOperator;
	}

	public ObservableExpression getLeft() {
		return theLeft;
	}

	public ObservableExpression getRight() {
		return theRight;
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return QommonsUtils.unmodifiableCopy(theLeft, theRight);
	}

	@Override
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException {
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
		Set<Class<?>> types = env.getBinaryOperators().getSupportedSourceTypes(operator);
		TypeToken<?> targetOpType;
		switch (types.size()) {
		case 0:
			throw new QonfigInterpretationException("Unsupported or unimplemented binary operator '" + theOperator + "'");
		case 1:
			targetOpType = TypeTokens.get().of(types.iterator().next());
			break;
		default:
			targetOpType = TypeTokens.get().WILDCARD;
			break;
		}
		ValueContainer<SettableValue<?>, ? extends SettableValue<?>> left = theLeft.evaluate(ModelTypes.Value.forType(targetOpType), env);
		Class<?> leftType = TypeTokens.getRawType(left.getType().getType(0));
		types = env.getBinaryOperators().getSupportedOperandTypes(operator, leftType);
		switch (types.size()) {
		case 0:
			throw new QonfigInterpretationException(
				"Binary operator '" + theOperator + "' is not supported or implemented for left operand type " + left.getType().getType(0));
		case 1:
			targetOpType = TypeTokens.get().of(types.iterator().next());
			break;
		default:
			targetOpType = TypeTokens.get().WILDCARD;
			break;
		}
		ValueContainer<SettableValue<?>, ? extends SettableValue<?>> right = theRight.evaluate(ModelTypes.Value.forType(targetOpType), env);
		BinaryOp<Object, Object, Object> op = (BinaryOp<Object, Object, Object>) env.getBinaryOperators().getOperator(operator, leftType, //
			TypeTokens.getRawType(right.getType().getType(0)));
		if (op == null)
			throw new QonfigInterpretationException(
				"Binary operator '" + theOperator + "' is not supported or implemented for operand types " + left.getType().getType(0)
				+ " and " + right.getType().getType(0));
		TypeToken<Object> resultType = TypeTokens.get().of(op.getTargetType());
		if (action) {
			if (type.getModelType() != ModelTypes.Action)
				throw new QonfigInterpretationException("Binary operator " + theOperator + " can only be evaluated as an action");
			TypeToken<Object> actionType;
			boolean voidAction = TypeTokens.get().unwrap(TypeTokens.getRawType(type.getType(0))) == void.class;
			if (voidAction)
				actionType = (TypeToken<Object>) (TypeToken<?>) TypeTokens.get().VOID;
			else if (TypeTokens.get().isAssignable(type.getType(0), resultType))
				actionType = resultType;
			else
				throw new QonfigInterpretationException(
					this + " cannot be evaluated as an " + ModelTypes.Action.getName() + "<" + type.getType(0) + ">");
			return (ValueContainer<M, MV>) new ValueContainer<ObservableAction<?>, ObservableAction<Object>>() {
				@Override
				public ModelInstanceType<ObservableAction<?>, ObservableAction<Object>> getType() {
					return ModelTypes.Action.forType(actionType);
				}

				@Override
				public ObservableAction<Object> get(ModelSetInstance msi) {
					SettableValue<Object> leftV = (SettableValue<Object>) left.get(msi);
					SettableValue<Object> rightV = (SettableValue<Object>) right.get(msi);
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
				public String toString() {
					return BinaryOperator.this.toString();
				}
			};
		} else {
			if (type.getModelType() != ModelTypes.Value)
				throw new QonfigInterpretationException("Binary operator " + theOperator + " can only be evaluated as a value");
			return (ValueContainer<M, MV>) new ValueContainer<SettableValue<?>, SettableValue<Object>>() {
				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<Object>> getType() {
					return ModelTypes.Value.forType(resultType);
				}

				@Override
				public SettableValue<Object> get(ModelSetInstance msi) {
					SettableValue<Object> leftV = (SettableValue<Object>) left.get(msi);
					SettableValue<Object> rightV = (SettableValue<Object>) right.get(msi);
					return leftV.transformReversible(resultType, tx -> tx.combineWith(rightV)//
						.combine((lft, rgt) -> op.apply(lft, rgt))//
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
				public String toString() {
					return BinaryOperator.this.toString();
				}
			};
		}
	}

	private <M, MV extends M> ValueContainer<M, MV> oldEvaluate(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException {
		if (type.getModelType() != ModelTypes.Value)
			throw new QonfigInterpretationException("Cannot evaluate a binary operator as anything but a value: " + type);
		ValueContainer<SettableValue<?>, SettableValue<?>> left = theLeft.evaluate(ModelTypes.Value.any(), env);
		ValueContainer<SettableValue<?>, SettableValue<?>> right = theRight.evaluate(ModelTypes.Value.any(), env);
		TypeToken<?> resultType = TypeTokens.get().getCommonType(left.getType().getType(0), right.getType().getType(0));
		Class<?> rawResult = TypeTokens.get().unwrap(TypeTokens.getRawType(resultType));
		// & | && || ^ + - * / % == != < > <= >= << >> >>>
		Class<?> leftType;
		switch (theOperator) {
		case "&":
		case "|":
		case "^": {
			// int, long, or boolean args, return type same
			if (rawResult == boolean.class)
				return (ValueContainer<M, MV>) logicalOp((ValueContainer<SettableValue<?>, SettableValue<Boolean>>) (Function<?, ?>) left,
					(ValueContainer<SettableValue<?>, SettableValue<Boolean>>) (Function<?, ?>) right);
			else if (rawResult == long.class)
				return (ValueContainer<M, MV>) longBitwiseOp(
					(ValueContainer<SettableValue<?>, SettableValue<Number>>) (Function<?, ?>) left,
					(ValueContainer<SettableValue<?>, SettableValue<Number>>) (Function<?, ?>) right);
			else if (rawResult == int.class || rawResult == short.class || rawResult == char.class || rawResult == byte.class)
				return (ValueContainer<M, MV>) bitwiseOp((ValueContainer<SettableValue<?>, SettableValue<Number>>) (Function<?, ?>) left,
					(ValueContainer<SettableValue<?>, SettableValue<Number>>) (Function<?, ?>) right);
			else
				throw new QonfigInterpretationException("Cannot apply binary operator '" + theOperator + "' to arguments of type "
					+ left.getType().getType(0) + " and " + right.getType().getType(0));
		}
		case "&&":
		case "||":
			if (!TypeTokens.get().isAssignable(TypeTokens.get().BOOLEAN, resultType))
				throw new QonfigInterpretationException("Cannot apply binary operator '" + theOperator + "' to arguments of type "
					+ left.getType().getType(0) + " and " + right.getType().getType(0));
			return (ValueContainer<M, MV>) logicalOp((ValueContainer<SettableValue<?>, SettableValue<Boolean>>) (Function<?, ?>) left,
				(ValueContainer<SettableValue<?>, SettableValue<Boolean>>) (Function<?, ?>) right);
		case "+":
			if (TypeTokens.getRawType(left.getType().getType(0)) == String.class
			|| TypeTokens.getRawType(right.getType().getType(0)) == String.class) {
				return (ValueContainer<M, MV>) appendString(//
					(ValueContainer<SettableValue<?>, SettableValue<Object>>) (ValueContainer<?, ?>) left, //
					(ValueContainer<SettableValue<?>, SettableValue<Object>>) (ValueContainer<?, ?>) right);
			}
			//$FALL-THROUGH$
		case "-":
		case "*":
		case "/":
		case "%":
			// number
			return (ValueContainer<M, MV>) mathOp((ValueContainer<SettableValue<?>, SettableValue<Number>>) (Function<?, ?>) left,
				(ValueContainer<SettableValue<?>, SettableValue<Number>>) (Function<?, ?>) right);
		case "==":
		case "!=":
			if (!TypeTokens.get().isAssignable(resultType, TypeTokens.get().BOOLEAN))
				throw new QonfigInterpretationException(
					"Cannot assign boolean result of binary operator '" + theOperator + "' to result of type " + resultType);
			boolean primitive = left.getType().getType(0).isPrimitive() || right.getType().getType(0).isPrimitive();
			boolean equalOp = theOperator.equals("==");
			BiFunction<Object, Object, Boolean> op = (l, r) -> {
				boolean equal;
				if (primitive)
					equal = Objects.equals(l, r);
				else
					equal = l == r;
				return equal == equalOp;
			};
			Function<SettableValue<?>, TransformReverse<Object, Boolean>> reverse;
			if (TypeTokens.get().isAssignable(left.getType().getType(0), right.getType().getType(0))) {
				reverse = rightV -> new TransformReverse<Object, Boolean>() {
					@Override
					public boolean isStateful() {
						return true;
					}

					@Override
					public String isEnabled(TransformationValues<Object, Boolean> transformValues) {
						return null;
					}

					@Override
					public ReverseQueryResult<Object> reverse(Boolean newValue, TransformationValues<Object, Boolean> transformValues,
						boolean add, boolean test) {
						if (transformValues.hasPreviousResult() && transformValues.getPreviousResult() == equalOp)
							return ReverseQueryResult.value(transformValues.getPreviousResult());
						else if (newValue == equalOp)
							return ReverseQueryResult.value(transformValues.get(rightV));
						else
							return ReverseQueryResult.value("Cannot set to arbitrary value");
					}
				};
			} else {
				reverse = rightV -> new TransformReverse<Object, Boolean>() {
					@Override
					public boolean isStateful() {
						return true;
					}

					@Override
					public String isEnabled(TransformationValues<Object, Boolean> transformValues) {
						return null;
					}

					@Override
					public ReverseQueryResult<Object> reverse(Boolean newValue, TransformationValues<Object, Boolean> transformValues,
						boolean add, boolean test) {
						if (transformValues.hasPreviousResult() && transformValues.getPreviousResult() == equalOp)
							return ReverseQueryResult.value(transformValues.getPreviousResult());
						else if (newValue == equalOp)
							return ReverseQueryResult.reject("Cannot assign value of type " + right.getType().getType(0)
								+ " to value of type " + left.getType().getType(0));
						else
							return ReverseQueryResult.value("Cannot set to arbitrary value");
					}
				};
			}
			return (ValueContainer<M, MV>) new ValueContainer<SettableValue<?>, SettableValue<Boolean>>() {
				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<Boolean>> getType() {
					return ModelTypes.Value.forType(boolean.class);
				}

				@Override
				public SettableValue<Boolean> get(ModelSetInstance models) {
					SettableValue<?> leftV = left.apply(models);
					SettableValue<?> rightV = right.apply(models);
					return ((SettableValue<Object>) leftV).transformReversible(boolean.class,
						tx -> tx.combineWith(rightV).combine(op).withReverse(reverse.apply(rightV)));
				}
			};
		case "<":
		case ">":
		case "<=":
		case ">=":
			if (!TypeTokens.get().isAssignable(resultType, TypeTokens.get().BOOLEAN))
				throw new QonfigInterpretationException(
					"Cannot assign boolean result of binary operator '" + theOperator + "' to result of type " + resultType);
			// number args, return type boolean
			throw new QonfigInterpretationException("Binary operator '" + theOperator + "' is not implemented yet");
		case "<<":
		case ">>":
		case ">>>":
			// int or long left, int right, return type same as left
			throw new QonfigInterpretationException("Binary operator '" + theOperator + "' is not implemented yet");
		case "+=":
		case "-=":
		case "*=":
		case "/=":
		case "%=":
		case "&=":
		case "|=":
		case "^=":
		case "<<=":
		case ">>=":
		case ">>>=":
			throw new QonfigInterpretationException("Binary operator '" + theOperator + "' is not implemented yet");
		case "instanceof":
			throw new QonfigInterpretationException("Binary operator '" + theOperator + "' is not implemented yet");
		default:
			throw new QonfigInterpretationException("Unrecognized operator: " + theOperator + " in expression " + this);
		}
	}

	private <M, MV extends M> ValueContainer<M, MV> doOperation(ModelInstanceType<M, MV> type,
		ValueContainer<SettableValue<?>, ? extends SettableValue<?>> left,
			ValueContainer<SettableValue<?>, ? extends SettableValue<?>> right,
				String operator, boolean action, ObservableModelSet models, ClassView classView) {
		// TODO Auto-generated method stub
		return null;
	}

	private static final String MIN_INT_STR = String.valueOf(Integer.MIN_VALUE).substring(1);
	private static final String MAX_INT_STR = String.valueOf(Integer.MAX_VALUE);
	private static final String MIN_LONG_STR = String.valueOf(Long.MIN_VALUE).substring(1);
	private static final String MAX_LONG_STR = String.valueOf(Long.MAX_VALUE);

	private static <L, R> ValueContainer<SettableValue<?>, SettableValue<String>> appendString(
		ValueContainer<SettableValue<?>, SettableValue<L>> left, ValueContainer<SettableValue<?>, SettableValue<R>> right)
			throws QonfigInterpretationException {
		// Unfortunately I don't support general string reversal here, but at least for some simple cases we can
		if (TypeTokens.getRawType(right.getType().getType(0)) == String.class) {
			Class<?> leftType = TypeTokens.get().unwrap(TypeTokens.getRawType(left.getType().getType(0)));
			Function<String, L> reverse;
			Function<String, String> accept;
			String disabledMsg;
			if (leftType == String.class || leftType == CharSequence.class) {
				disabledMsg = null;
				accept = s -> null;
				reverse = s -> (L) s;
			} else if (leftType == boolean.class) {
				disabledMsg = null;
				accept = s -> (s.equals("true") || s.equals("false")) ? null : "'true' or 'false' expected";
				reverse = s -> (L) Boolean.valueOf(s);
			} else if (leftType == int.class) {
				disabledMsg = null;
				accept = s -> {
					int i = 0;
					boolean neg = i < s.length() && s.charAt(i) == '-';
					if (neg)
						i++;
					for (; i < s.length(); i++)
						if (s.charAt(i) < '0' || s.charAt(i) > '9')
							return "integer expected";
					if (!neg && StringUtils.compareNumberTolerant(s, MAX_INT_STR, false, true) > 0)
						return "integer is too large for int type";
					else if (neg && StringUtils.compareNumberTolerant(StringUtils.cheapSubSequence(s, 1, s.length()), MIN_INT_STR,
						false, true) > 0)
						return "negative integer is too large for int type";
					return null;
				};
				reverse = s -> (L) Integer.valueOf(s);
			} else if (leftType == long.class) {
				disabledMsg = null;
				accept = s -> {
					int i = 0;
					boolean neg = i < s.length() && s.charAt(i) == '-';
					if (neg)
						i++;
					for (; i < s.length(); i++)
						if (s.charAt(i) < '0' || s.charAt(i) > '9')
							return "integer expected";
					if (!neg && StringUtils.compareNumberTolerant(s, MAX_LONG_STR, false, true) > 0)
						return "integer is too large for long type";
					else if (neg && StringUtils.compareNumberTolerant(StringUtils.cheapSubSequence(s, 1, s.length()), MIN_LONG_STR,
						false, true) > 0)
						return "negative integer is too large for long type";
					return null;
				};
				reverse = s -> (L) Long.valueOf(s);
			} else if (leftType == double.class) {
				disabledMsg = null;
				accept = s -> {
					// Can't think of a better way to do this than to just parse it twice
					try {
						Double.parseDouble(s);
						return null;
					} catch (NumberFormatException e) {
						return e.getMessage();
					}
				};
				reverse = s -> (L) Double.valueOf(s);
				// TODO Other standard types
			} else {
				disabledMsg = "Cannot parse type " + left.getType().getType(0);
				reverse = null;
				accept = null;
			}
			return new ValueContainer<SettableValue<?>, SettableValue<String>>() {
				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<String>> getType() {
					return ModelTypes.Value.forType(String.class);
				}

				@Override
				public SettableValue<String> get(ModelSetInstance models) {
					SettableValue<L> leftV = left.apply(models);
					SettableValue<R> rightV = right.apply(models);
					if (reverse != null) {
						return leftV.transformReversible(String.class, tx -> tx.combineWith(rightV).combine(LambdaUtils.printableBiFn(//
							(v1, v2) -> new StringBuilder().append(v1).append(v2).toString(), "+", null))
							.withReverse(new TransformReverse<L, String>() {
								@Override
								public boolean isStateful() {
									return true;
								}

								@Override
								public String isEnabled(TransformationValues<L, String> transformValues) {
									return null;
								}

								@Override
								public ReverseQueryResult<L> reverse(String newValue, TransformationValues<L, String> transformValues,
									boolean add, boolean test) {
									String end = String.valueOf(transformValues.get(rightV));
									if (!newValue.endsWith(end))
										return ReverseQueryResult.reject("String does not end with '" + end + "'");
									String leftS = newValue.substring(0, newValue.length() - end.length());
									String msg = accept.apply(leftS);
									if (msg != null)
										return ReverseQueryResult.reject(msg);
									return ReverseQueryResult.value(reverse.apply(leftS));
								}
							}));
					} else
						return SettableValue.asSettable(//
							leftV.transform(String.class, tx -> tx.combineWith(rightV).combine(LambdaUtils.printableBiFn(//
								(v1, v2) -> new StringBuilder().append(v1).append(v2).toString(), "+", null))),
							__ -> disabledMsg);
				}
			};
		} else
			return new ValueContainer<SettableValue<?>, SettableValue<String>>() {
			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<String>> getType() {
				return ModelTypes.Value.forType(String.class);
			}

			@Override
			public SettableValue<String> get(ModelSetInstance models) {
				SettableValue<L> leftV = left.apply(models);
				SettableValue<R> rightV = right.apply(models);
				return SettableValue.asSettable(//
					leftV.transform(String.class, tx -> tx.combineWith(rightV).combine(LambdaUtils.printableBiFn(//
						(v1, v2) -> new StringBuilder().append(v1).append(v2).toString(), "+", null))),
					__ -> "Cannot reverse string");
			}
		};
	}

	private ValueContainer<SettableValue<?>, SettableValue<Boolean>> logicalOp(
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> left, ValueContainer<SettableValue<?>, SettableValue<Boolean>> right)
			throws QonfigInterpretationException {
		java.util.function.BinaryOperator<Boolean> op;
		abstract class LogicalReverse implements TransformReverse<Boolean, Boolean> {
			protected final SettableValue<Boolean> right;

			LogicalReverse(SettableValue<Boolean> right) {
				this.right = right;
			}

			@Override
			public boolean isStateful() {
				return false;
			}

			@Override
			public String isEnabled(TransformationValues<Boolean, Boolean> transformValues) {
				return null; // We support updates
			}
		}
		;
		Function<SettableValue<Boolean>, LogicalReverse> reverse;
		switch (theOperator) {
		case "|":
		case "||":
			op = (b1, b2) -> b1 || b2;
			reverse = rightV -> new LogicalReverse(rightV) {
				@Override
				public ReverseQueryResult<Boolean> reverse(Boolean newValue, TransformationValues<Boolean, Boolean> transformValues,
					boolean add, boolean test) {
					if (newValue)
						return ReverseQueryResult.value(newValue); // Setting left to true will do the trick
					// Otherwise we need to set both to false
					if (transformValues.get(right)) {
						String accept = right.isAcceptable(false);
						if (accept != null)
							return ReverseQueryResult.reject(accept);
						else if (!test)
							right.set(false, null);
					}
					return ReverseQueryResult.value(newValue);
				}
			};
			break;
		case "&":
		case "&&":
			op = (b1, b2) -> b1 && b2;
			reverse = rightV -> new LogicalReverse(rightV) {
				@Override
				public ReverseQueryResult<Boolean> reverse(Boolean newValue, TransformationValues<Boolean, Boolean> transformValues,
					boolean add, boolean test) {
					if (!newValue)
						return ReverseQueryResult.value(newValue); // Setting left to false will do the trick
					// Otherwise we need to set both to true
					if (!transformValues.get(right)) {
						String accept = right.isAcceptable(true);
						if (accept != null)
							return ReverseQueryResult.reject(accept);
						else if (!test)
							right.set(true, null);
					}
					return ReverseQueryResult.value(newValue);
				}
			};
			break;
		case "^":
			op = (b1, b2) -> b1 ^ b2;
			reverse = rightV -> new LogicalReverse(rightV) {
				@Override
				public ReverseQueryResult<Boolean> reverse(Boolean newValue, TransformationValues<Boolean, Boolean> transformValues,
					boolean add, boolean test) {
					if (newValue.booleanValue() == transformValues.get(right)) {
						return ReverseQueryResult.value(!newValue);
					} else
						return ReverseQueryResult.value(newValue);
				}
			};
			break;
		default:
			throw new QonfigInterpretationException("Unrecognized locigal operator '" + theOperator + "'");
		}
		return new ValueContainer<SettableValue<?>, SettableValue<Boolean>>() {
			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<Boolean>> getType() {
				return left.getType();
			}

			@Override
			public SettableValue<Boolean> get(ModelSetInstance models) {
				SettableValue<Boolean> leftV = left.apply(models);
				SettableValue<Boolean> rightV = right.apply(models);
				return leftV.transformReversible(boolean.class,
					tx -> tx.combineWith(rightV).combine(op).withReverse(reverse.apply(rightV)));
			}
		};
	}

	private ValueContainer<SettableValue<?>, SettableValue<? extends Number>> longBitwiseOp(
		ValueContainer<SettableValue<?>, SettableValue<Number>> left, ValueContainer<SettableValue<?>, SettableValue<Number>> right)
			throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Binary operator '" + theOperator + "' is not immplemented yet");
	}

	private ValueContainer<SettableValue<?>, SettableValue<? extends Number>> bitwiseOp(
		ValueContainer<SettableValue<?>, SettableValue<Number>> left, ValueContainer<SettableValue<?>, SettableValue<Number>> right)
			throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Binary operator '" + theOperator + "' is not immplemented yet");
	}

	private ValueContainer<SettableValue<?>, ? extends SettableValue<? extends Number>> mathOp(
		ValueContainer<SettableValue<?>, SettableValue<Number>> left, ValueContainer<SettableValue<?>, SettableValue<Number>> right)
			throws QonfigInterpretationException {
		Class<?> resultType = TypeTokens.get()
			.unwrap(TypeTokens.getRawType(TypeTokens.get().getCommonType(left.getType().getType(0), right.getType().getType(0))));
		if (resultType == char.class || resultType == short.class || resultType == byte.class)
			resultType = int.class;
		Function<ModelSetInstance, SettableValue<? extends Number>> left2, right2;
		if (!left.getType().getType(0).isPrimitive()
			|| TypeTokens.get().unwrap(TypeTokens.getRawType(left.getType().getType(0))) != resultType) {
			TypeConverter<Number, Number> leftConverter = (TypeConverter<Number, Number>) TypeTokens.get()
				.getCast(left.getType().getType(0), TypeTokens.get().of(resultType));
			TypeConverter<Number, Number> leftReverse = (TypeConverter<Number, Number>) TypeTokens.get()
				.getCast(TypeTokens.get().of(resultType), left.getType().getType(0));
			Class<?> fResultType = resultType;
			left2 = msi -> left.apply(msi).transformReversible((Class<Number>) fResultType, tx -> tx.cache(false)//
				.map(leftConverter).withReverse(leftReverse));
		} else
			left2 = (ValueContainer<SettableValue<?>, SettableValue<? extends Number>>) (ValueContainer<?, ?>) left;
		if (!right.getType().getType(0).isPrimitive()
			|| TypeTokens.get().unwrap(TypeTokens.getRawType(right.getType().getType(0))) != resultType) {
			TypeConverter<Number, Number> rightConverter = (TypeConverter<Number, Number>) TypeTokens.get()
				.getCast(right.getType().getType(0), TypeTokens.get().of(resultType));
			TypeConverter<Number, Number> rightReverse = (TypeConverter<Number, Number>) TypeTokens.get()
				.getCast(TypeTokens.get().of(resultType), right.getType().getType(0));
			Class<?> fResultType = resultType;
			right2 = msi -> right.apply(msi).transformReversible((Class<Number>) fResultType, tx -> tx.cache(false)//
				.map(rightConverter).withReverse(rightReverse));
		} else
			right2 = (ValueContainer<SettableValue<?>, SettableValue<? extends Number>>) (ValueContainer<?, ?>) right;
		if (resultType == double.class) {
			if (theOperator.equals("%")) { // This one is special
				return ObservableModelSet.container(msi -> {
					SettableValue<Double> leftV = (SettableValue<Double>) left2.apply(msi);
					SettableValue<Double> rightV = (SettableValue<Double>) right2.apply(msi);
					return leftV.transformReversible(double.class, tx -> tx.cache(false)//
						.combineWith(rightV)//
						.combine((l, r) -> l % r)//
						.replaceSourceWith((res, txv) -> {
							long div = (long) (txv.getCurrentSource() / txv.get(rightV));
							return div + res;
						}, rep -> {
							return rep.rejectWith((res, txv) -> {
								if (Math.abs(res) >= Math.abs(txv.get(rightV)))
									return "The result of a modulo expression must be less than the modulus";
								return null;
							}, true, true);
						}));
				}, ModelTypes.Value.forType(double.class));
			}
			BiFunction<Double, Double, Double> op, reverse;
			switch (theOperator) {
			case "+":
				op = (l, r) -> l + r;
				reverse = (res, r) -> res - r;
				break;
			case "-":
				op = (l, r) -> l - r;
				reverse = (res, r) -> res + r;
				break;
			case "*":
				op = (l, r) -> l * r;
				reverse = (res, r) -> res / r;
				break;
			case "/":
				op = (l, r) -> l / r;
				reverse = (res, r) -> res * r;
				break;
			default:
				throw new IllegalStateException("Unimplemented binary operator '" + theOperator + "'");
			}
			return ObservableModelSet.container(msi -> {
				SettableValue<Double> leftV = (SettableValue<Double>) left2.apply(msi);
				SettableValue<Double> rightV = (SettableValue<Double>) right2.apply(msi);
				return leftV.transformReversible(double.class, tx -> tx.cache(false)//
					.combineWith(rightV)//
					.combine(op)//
					.replaceSource(reverse, null));
			}, ModelTypes.Value.forType(double.class));
		} else if (resultType == long.class) {
			if (theOperator.equals("%")) { // This one is special
				return ObservableModelSet.container(msi -> {
					SettableValue<Long> leftV = (SettableValue<Long>) left2.apply(msi);
					SettableValue<Long> rightV = (SettableValue<Long>) right2.apply(msi);
					return leftV.transformReversible(long.class, tx -> tx.cache(false)//
						.combineWith(rightV)//
						.combine((l, r) -> l % r)//
						.replaceSourceWith((res, txv) -> {
							long div = txv.getCurrentSource() / txv.get(rightV);
							return div + res;
						}, rep -> {
							return rep.rejectWith((res, txv) -> {
								if (Math.abs(res) >= Math.abs(txv.get(rightV)))
									return "The result of a modulo expression must be less than the modulus";
								return null;
							}, true, true);
						}));
				}, ModelTypes.Value.forType(long.class));
			}
			BiFunction<Long, Long, Long> op, reverse;
			switch (theOperator) {
			case "+":
				op = (l, r) -> l + r;
				reverse = (res, r) -> res - r;
				break;
			case "-":
				op = (l, r) -> l - r;
				reverse = (res, r) -> res + r;
				break;
			case "*":
				op = (l, r) -> l * r;
				reverse = (res, r) -> res / r;
				break;
			case "/":
				op = (l, r) -> l / r;
				reverse = (res, r) -> res * r;
				break;
			default:
				throw new IllegalStateException("Unimplemented binary operator '" + theOperator + "'");
			}
			return ObservableModelSet.container(msi -> {
				SettableValue<Long> leftV = (SettableValue<Long>) left2.apply(msi);
				SettableValue<Long> rightV = (SettableValue<Long>) right2.apply(msi);
				return leftV.transformReversible(long.class, tx -> tx.cache(false)//
					.combineWith(rightV)//
					.combine(op)//
					.replaceSource(reverse, null));
			}, ModelTypes.Value.forType(long.class));
		} else if (resultType == int.class) {
			if (theOperator.equals("%")) { // This one is special
				return ObservableModelSet.container(msi -> {
					SettableValue<Integer> leftV = (SettableValue<Integer>) left2.apply(msi);
					SettableValue<Integer> rightV = (SettableValue<Integer>) right2.apply(msi);
					return leftV.transformReversible(int.class, tx -> tx.cache(false)//
						.combineWith(rightV)//
						.combine((l, r) -> l % r)//
						.replaceSourceWith((res, txv) -> {
							int div = txv.getCurrentSource() / txv.get(rightV);
							return div + res;
						}, rep -> {
							return rep.rejectWith((res, txv) -> {
								if (Math.abs(res) >= Math.abs(txv.get(rightV)))
									return "The result of a modulo expression must be less than the modulus";
								return null;
							}, true, true);
						}));
				}, ModelTypes.Value.forType(int.class));
			}
			BiFunction<Integer, Integer, Integer> op, reverse;
			switch (theOperator) {
			case "+":
				op = (l, r) -> l + r;
				reverse = (res, r) -> res - r;
				break;
			case "-":
				op = (l, r) -> l - r;
				reverse = (res, r) -> res + r;
				break;
			case "*":
				op = (l, r) -> l * r;
				reverse = (res, r) -> res / r;
				break;
			case "/":
				op = (l, r) -> l / r;
				reverse = (res, r) -> res * r;
				break;
			default:
				throw new IllegalStateException("Unimplemented binary operator '" + theOperator + "'");
			}
			return ObservableModelSet.container(msi -> {
				SettableValue<Integer> leftV = (SettableValue<Integer>) left2.apply(msi);
				SettableValue<Integer> rightV = (SettableValue<Integer>) right2.apply(msi);
				return leftV.transformReversible(int.class, tx -> tx.cache(false)//
					.combineWith(rightV)//
					.combine(op)//
					.replaceSource(reverse, null));
			}, ModelTypes.Value.forType(int.class));
		} else
			throw new QonfigInterpretationException("Cannot apply binary operator '" + theOperator + " to arguments of type "
				+ left.getType().getType(0) + " and " + right.getType().getType(0));
	}

	@Override
	public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ExpressoEnv env)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not supported for binary operators");
	}

	@Override
	public String toString() {
		return theLeft + " " + theOperator + " " + theRight;
	}
}