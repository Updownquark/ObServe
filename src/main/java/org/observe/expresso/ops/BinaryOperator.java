package org.observe.expresso.ops;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.Transformation;
import org.observe.Transformation.ReverseQueryResult;
import org.observe.Transformation.TransformationValues;
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
import org.observe.expresso.ops.BinaryOperatorSet.BinaryOp;
import org.observe.expresso.ops.BinaryOperatorSet.FirstArgDecisiveBinaryOp;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Lockable;
import org.qommons.QommonsUtils;
import org.qommons.Stamped;
import org.qommons.Transaction;
import org.qommons.collect.CollectionUtils;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;
import org.qommons.io.ErrorReporting;

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
	public int getComponentOffset(int childIndex) {
		switch (childIndex) {
		case 0:
			return 0;
		case 1:
			return theLeft.getExpressionLength() + theOperator.length();
		default:
			throw new IndexOutOfBoundsException(childIndex + " of 2");
		}
	}

	@Override
	public int getExpressionLength() {
		return theLeft.getExpressionLength() + theOperator.length() + theRight.getExpressionLength();
	}

	@Override
	public List<? extends ObservableExpression> getComponents() {
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
	public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset) {
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
		return action ? ModelTypes.Action : ModelTypes.Value;
	}

	@Override
	public <M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX {
		if (type.getModelType() == ModelTypes.Action) {//
		} else if (type.getModelType() == ModelTypes.Value) {//
		} else {
			exHandler.handle1(
				new ExpressoInterpretationException("Binary operator " + theOperator + " can only be evaluated as a value or an action",
					env.reporting().at(theLeft.getExpressionLength()).getPosition(), theOperator.length()));
			return null;
		}
		return _evaluate(type, env, expressionOffset, exHandler);
	}

	private <M, MV extends M, S, T, V, R, EX extends Throwable> EvaluatedExpression<M, MV> _evaluate(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX {
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
		Class<?> targetType = type.getModelType().getTypeCount() == 0 ? Object.class : TypeTokens.getRawType(type.getType(0));
		Set<Class<?>> types = env.getBinaryOperators().getSupportedPrimaryInputTypes(operator, targetType);
		TypeToken<?> targetOpType;
		switch (types.size()) {
		case 0:
			exHandler.handle1(new ExpressoInterpretationException(
				"Unsupported or unimplemented binary operator '" + theOperator + "' targeting type " + targetType.getName(),
				env.reporting().at(theLeft.getExpressionLength()).getPosition(), theOperator.length()));
			return null;
		case 1:
			targetOpType = TypeTokens.get().of(types.iterator().next());
			break;
		default:
			targetOpType = TypeTokens.get().WILDCARD;
			break;
		}
		ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, EX, NeverThrown> doubleX = exHandler
			.stack(ExceptionHandler.holder());
		EvaluatedExpression<SettableValue<?>, SettableValue<S>> left;
		left = theLeft.evaluate(ModelTypes.Value.forType((TypeToken<S>) targetOpType), env, expressionOffset, doubleX);
		if (doubleX.get2() != null) {
			exHandler.handle1(new ExpressoInterpretationException(doubleX.get2().getMessage(), env.reporting().getPosition(),
				theLeft.getExpressionLength()));
			return null;
		} else if (left == null)
			return null;
		TypeToken<S> leftTypeT = (TypeToken<S>) left.getType().getType(0);
		Class<S> leftType = TypeTokens.getRawType(leftTypeT);
		types = env.getBinaryOperators().getSupportedSecondaryInputTypes(operator, targetType, leftType);
		switch (types.size()) {
		case 0:
			exHandler.handle1(new ExpressoInterpretationException(
				"Binary operator '" + theOperator + "' is not supported or implemented for left operand type " + leftTypeT
				+ ", target type " + targetType.getName(),
				env.reporting().at(theLeft.getExpressionLength()).getPosition(), theOperator.length()));
			return null;
		case 1:
			targetOpType = TypeTokens.get().of(types.iterator().next());
			break;
		default:
			targetOpType = TypeTokens.get().WILDCARD;
			break;
		}
		int rightOffset = expressionOffset + theLeft.getExpressionLength() + theOperator.length();
		InterpretedExpressoEnv rightEnv = env.at(theLeft.getExpressionLength() + theOperator.length());
		EvaluatedExpression<SettableValue<?>, SettableValue<T>> right;
		right = theRight.evaluate(ModelTypes.Value.forType((TypeToken<T>) targetOpType), rightEnv, rightOffset, doubleX);
		if (doubleX.get2() != null) {
			exHandler.handle1(new ExpressoInterpretationException(doubleX.get2().getMessage(), rightEnv.reporting().getPosition(),
				theLeft.getExpressionLength()));
			return null;
		} else if (right == null)
			return null;
		TypeToken<T> rightTypeT = (TypeToken<T>) right.getType().getType(0);
		BinaryOp<S, T, V> op;
		op = (BinaryOp<S, T, V>) env.getBinaryOperators()//
			.getOperator(operator, targetType, leftType, //
				TypeTokens.getRawType(rightTypeT));
		ErrorReporting operatorReporting = env.reporting().at(theLeft.getExpressionLength());
		if (op == null) {
			exHandler.handle1(new ExpressoInterpretationException(
				"Binary operator '" + theOperator + "' is not supported or implemented for operand types " + leftTypeT + " and "
					+ rightTypeT + ", target type " + targetType.getName(),
					operatorReporting.getPosition(), theOperator.length()));
			return null;
		}
		TypeToken<V> resultType = op.getTargetType(leftTypeT, rightTypeT, operatorReporting.getPosition(), theOperator.length(), exHandler);
		if (resultType == null)
			return null;
		ErrorReporting reporting = env.reporting();
		if (action) {
			if (type.getModelType() != ModelTypes.Action)
				throw new ExpressoInterpretationException("Binary operator " + theOperator + " can only be evaluated as an action",
					operatorReporting.getPosition(), theOperator.length());
			return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
				(InterpretedValueSynth<M, MV>) new InterpretedValueSynth<ObservableAction, ObservableAction>() {
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
					return QommonsUtils.unmodifiableCopy(left, right);
				}

				@Override
					public ModelValueInstantiator<ObservableAction> instantiate() throws ModelInstantiationException {
					return new ActionInstantiator<>(left.instantiate(), right.instantiate(), op, reporting, operatorReporting);
				}

				@Override
				public String toString() {
					return BinaryOperator.this.toString();
				}
			}, op, left, right);
		} else {
			if (type.getModelType() != ModelTypes.Value)
				throw new ExpressoInterpretationException("Binary operator " + theOperator + " can only be evaluated as a value",
					operatorReporting.getPosition(), theOperator.length());
			InterpretedValueSynth<SettableValue<?>, SettableValue<V>> operated = new InterpretedValueSynth<SettableValue<?>, SettableValue<V>>() {
				@Override
				public ModelType<SettableValue<?>> getModelType() {
					return ModelTypes.Value;
				}

				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<V>> getType() {
					return ModelTypes.Value.forType(resultType);
				}

				@Override
				public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
					return QommonsUtils.unmodifiableCopy(left, right);
				}

				@Override
				public ModelValueInstantiator<SettableValue<V>> instantiate() throws ModelInstantiationException {
					if (op == BinaryOperatorSet.OR)
						return (ModelValueInstantiator<SettableValue<V>>) (ModelValueInstantiator<?>) new OrValue(
							(TypeToken<Boolean>) resultType, //
							(ModelValueInstantiator<SettableValue<Boolean>>) (ModelValueInstantiator<?>) left.instantiate(),
							(ModelValueInstantiator<SettableValue<Boolean>>) (ModelValueInstantiator<?>) right.instantiate(), //
							BinaryOperatorSet.OR, reporting);
					else if (op == BinaryOperatorSet.AND)
						return (ModelValueInstantiator<SettableValue<V>>) (ModelValueInstantiator<?>) new AndValue(
							(TypeToken<Boolean>) resultType, //
							(ModelValueInstantiator<SettableValue<Boolean>>) (ModelValueInstantiator<?>) left.instantiate(),
							(ModelValueInstantiator<SettableValue<Boolean>>) (ModelValueInstantiator<?>) right.instantiate(), //
							BinaryOperatorSet.AND, reporting);
					else
						return new ValueInstantiator<>(resultType, left.instantiate(), right.instantiate(), op, reporting);
				}

				@Override
				public String toString() {
					return BinaryOperator.this.toString();
				}
			};
			return ObservableExpression.evEx(expressionOffset, getExpressionLength(), (InterpretedValueSynth<M, MV>) operated, op, left,
				right);
		}
	}

	@Override
	public String toString() {
		return theLeft + theOperator + theRight;
	}

	// These classes can't be anonymous because anonymous classes would keep references to compiled objects that we don't want to keep

	static class ActionInstantiator<S, T, V, R> implements ModelValueInstantiator<ObservableAction> {
		private final ModelValueInstantiator<SettableValue<S>> theLeft;
		private final ModelValueInstantiator<SettableValue<T>> theRight;
		private final BinaryOp<S, T, V> theOperator;
		private final ErrorReporting theLeftReporting;
		private final ErrorReporting theOperatorReporting;

		ActionInstantiator(ModelValueInstantiator<SettableValue<S>> left, ModelValueInstantiator<SettableValue<T>> right,
			BinaryOp<S, T, V> operator, ErrorReporting leftReporting, ErrorReporting operatorReporting) {
			theLeft = left;
			theRight = right;
			theOperator = operator;
			theLeftReporting = leftReporting;
			theOperatorReporting = operatorReporting;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			theLeft.instantiate();
			theRight.instantiate();
		}

		@Override
		public ObservableAction get(ModelSetInstance msi) throws ModelInstantiationException {
			SettableValue<S> leftV = theLeft.get(msi);
			SettableValue<T> rightV = theRight.get(msi);
			return createOpAction(leftV, rightV);
		}

		private ObservableAction createOpAction(SettableValue<S> leftV, SettableValue<T> rightV) {
			ObservableValue<String> enabled = leftV.isEnabled().transform(String.class, tx -> tx//
				.combineWith(leftV).combineWith(rightV)//
				.combine((en, lft, rgt) -> {
					if (en != null)
						return en;
					V res;
					try {
						res = theOperator.apply(lft, rgt);
					} catch (RuntimeException | Error e) {
						theOperatorReporting.error(null, e);
						return "Error";
					}
					String msg = theOperator.canReverse(lft, rgt, res);
					if (msg != null)
						return msg;
					return leftV.isAcceptable((S) res);
				}));
			return new BinaryOperatorAction<>(leftV, rightV, theOperator, enabled, theLeftReporting, theOperatorReporting);
		}

		@Override
		public ObservableAction forModelCopy(ObservableAction value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			SettableValue<S> sourceLeft = theLeft.get(sourceModels);
			SettableValue<S> newLeft = theLeft.forModelCopy(sourceLeft, sourceModels, newModels);
			SettableValue<T> sourceRight = theRight.get(sourceModels);
			SettableValue<T> newRight = theRight.forModelCopy(sourceRight, sourceModels, newModels);
			if (sourceLeft == newLeft && sourceRight == newRight)
				return value;
			else
				return createOpAction(newLeft, newRight);
		}

		@Override
		public String toString() {
			return theLeft.toString() + theOperator + theRight;
		}
	}

	static class ValueInstantiator<S, T, V> implements ModelValueInstantiator<SettableValue<V>> {
		private final TypeToken<V> theResultType;
		private final ModelValueInstantiator<SettableValue<S>> theLeft;
		private final ModelValueInstantiator<SettableValue<T>> theRight;
		private final BinaryOp<S, T, V> theOperator;
		private final ErrorReporting theReporting;

		ValueInstantiator(TypeToken<V> resultType, ModelValueInstantiator<SettableValue<S>> left,
			ModelValueInstantiator<SettableValue<T>> right, BinaryOp<S, T, V> operator, ErrorReporting reporting) {
			theResultType = resultType;
			theLeft = left;
			theRight = right;
			theOperator = operator;
			theReporting = reporting;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			theLeft.instantiate();
			theRight.instantiate();
		}

		@Override
		public SettableValue<V> get(ModelSetInstance msi) throws ModelInstantiationException {
			SettableValue<S> leftV = theLeft.get(msi);
			SettableValue<T> rightV = theRight.get(msi);
			return createOpValue(leftV, rightV);
		}

		SettableValue<V> createOpValue(SettableValue<S> leftV, SettableValue<T> rightV) {
			BinaryOperatorReverseFn<S, T, V> reverse = new BinaryOperatorReverseFn<>(rightV, theOperator);
			SettableValue<V> transformedV = leftV.transformReversible(theResultType, tx -> tx.combineWith(rightV)//
				.combine(LambdaUtils.printableBiFn((lft, rgt) -> {
					try {
						return theOperator.apply(lft, rgt);
					} catch (RuntimeException | Error e) {
						theReporting.error(null, e);
						return null;
					}
				}, theOperator::toString, theOperator))//
				.replaceSourceWith(reverse, rev -> rev.rejectWith(reverse::canReverse, true, true)));
			if (theOperator instanceof BinaryOperatorSet.FirstArgDecisiveBinaryOp)
				return new FirstArgDecisiveBinaryValue<>(theResultType, leftV, rightV, (FirstArgDecisiveBinaryOp<S, T, V>) theOperator,
					transformedV);
			else
				return transformedV;
		}

		@Override
		public SettableValue<V> forModelCopy(SettableValue<V> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			SettableValue<S> sourceLeft = theLeft.get(sourceModels);
			SettableValue<S> newLeft = theLeft.forModelCopy(sourceLeft, sourceModels, newModels);
			SettableValue<T> sourceRight = theRight.get(sourceModels);
			SettableValue<T> newRight = theRight.forModelCopy(sourceRight, sourceModels, newModels);
			if (sourceLeft == newLeft && sourceRight == newRight)
				return value;
			else
				return createOpValue(newLeft, newRight);
		}

		@Override
		public String toString() {
			return theLeft.toString() + theOperator + theRight;
		}
	}

	static class OrValue extends ValueInstantiator<Boolean, Boolean, Boolean> {
		OrValue(TypeToken<Boolean> resultType, ModelValueInstantiator<SettableValue<Boolean>> left,
			ModelValueInstantiator<SettableValue<Boolean>> right, BinaryOp<Boolean, Boolean, Boolean> operator, ErrorReporting reporting) {
			super(resultType, left, right, operator, reporting);
		}

		@Override
		SettableValue<Boolean> createOpValue(SettableValue<Boolean> leftV, SettableValue<Boolean> rightV) {
			return SettableValue.firstValue(TypeTokens.get().BOOLEAN, LambdaUtils.printablePred(Boolean.TRUE::equals, "true", null),
				LambdaUtils.constantSupplier(false, "false", null), leftV, rightV);
		}
	}

	static class AndValue extends ValueInstantiator<Boolean, Boolean, Boolean> {
		AndValue(TypeToken<Boolean> resultType, ModelValueInstantiator<SettableValue<Boolean>> left,
			ModelValueInstantiator<SettableValue<Boolean>> right, BinaryOp<Boolean, Boolean, Boolean> operator, ErrorReporting reporting) {
			super(resultType, left, right, operator, reporting);
		}

		@Override
		SettableValue<Boolean> createOpValue(SettableValue<Boolean> leftV, SettableValue<Boolean> rightV) {
			return SettableValue.firstValue(TypeTokens.get().BOOLEAN,
				LambdaUtils.printablePred(b -> !Boolean.TRUE.equals(b), "false?", null), LambdaUtils.constantSupplier(true, "true", null),
				leftV, rightV);
		}
	}

	static class BinaryOperatorReverseFn<S, T, V>
	implements BiFunction<V, Transformation.TransformationValues<? extends S, ? extends V>, S> {
		private final SettableValue<T> theRight;
		private final BinaryOp<S, T, V> theOperator;

		BinaryOperatorReverseFn(SettableValue<T> right, BinaryOp<S, T, V> operator) {
			theRight = right;
			theOperator = operator;
		}

		@Override
		public S apply(V newValue, Transformation.TransformationValues<? extends S, ? extends V> transformValues) {
			T rgt = transformValues.get(theRight);
			String msg = theOperator.canReverse(transformValues.getCurrentSource(), rgt, newValue);
			if (msg != null)
				throw new IllegalArgumentException(msg);

			return theOperator.reverse(transformValues.getCurrentSource(), rgt, newValue);
		}

		public String canReverse(V newValue, Transformation.TransformationValues<? extends S, ? extends V> transformValues) {
			T rgt = transformValues.get(theRight);
			return theOperator.canReverse(transformValues.getCurrentSource(), rgt, newValue);
		}
	}

	static class BinaryOperatorReverse implements Transformation.TransformReverse<Object, Object> {
		private final SettableValue<Object> theRight;
		private final BinaryOp<Object, Object, Object> theOperator;
		private final ErrorReporting theReporting;

		BinaryOperatorReverse(SettableValue<Object> right, BinaryOp<Object, Object, Object> operator, ErrorReporting reporting) {
			theRight = right;
			theOperator = operator;
			theReporting = reporting;
		}

		@Override
		public boolean isStateful() {
			return true;
		}

		@Override
		public String isEnabled(TransformationValues<Object, Object> transformValues) {
			return null;
		}

		@Override
		public ReverseQueryResult<Object> reverse(Object newValue, TransformationValues<Object, Object> transformValues, boolean add,
			boolean test) {
			Object rgt = transformValues.get(theRight);
			String msg = theOperator.canReverse(transformValues.getCurrentSource(), rgt, newValue);
			if (msg != null)
				return ReverseQueryResult.reject(msg);

			try {
				return ReverseQueryResult.value(theOperator.reverse(transformValues.getCurrentSource(), rgt, newValue));
			} catch (RuntimeException | Error e) {
				theReporting.error(null, e);
				return ReverseQueryResult.value(null);
			}
		}
	}

	static class BinaryOperatorAction<S, T, V, R> implements ObservableAction {
		private final SettableValue<S> theLeft;
		private final SettableValue<T> theRight;
		private final BinaryOp<S, T, V> theOperator;
		private final ObservableValue<String> isEnabled;
		private final ErrorReporting theLeftReporting;
		private final ErrorReporting theOperatorReporting;

		BinaryOperatorAction(SettableValue<S> left, SettableValue<T> right, BinaryOp<S, T, V> operator, ObservableValue<String> enabled,
			ErrorReporting leftReporting, ErrorReporting operatorReporting) {
			theLeft = left;
			theRight = right;
			theOperator = operator;
			isEnabled = enabled;
			theLeftReporting = leftReporting;
			theOperatorReporting = operatorReporting;
		}

		@Override
		public void act(Object cause) throws IllegalStateException {
			V res;
			try {
				res = theOperator.apply(theLeft.get(), theRight.get());
			} catch (RuntimeException | Error e) {
				theOperatorReporting.error(null, e);
				return;
			}
			try {
				theLeft.set((S) res, cause);
			} catch (RuntimeException | Error e) {
				theLeftReporting.error(null, e);
				return;
			}
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return isEnabled;
		}

		@Override
		public String toString() {
			return theLeft.toString() + theOperator + "=" + theRight;
		}
	}

	static class FirstArgDecisiveBinaryValue<S, T, V> implements SettableValue<V> {
		private final TypeToken<V> theType;
		private final SettableValue<S> theValue1;
		private final SettableValue<T> theValue2;
		private final BinaryOperatorSet.FirstArgDecisiveBinaryOp<S, T, V> theOp;
		private final SettableValue<V> theTransformedValue;

		public FirstArgDecisiveBinaryValue(TypeToken<V> type, SettableValue<S> value1, SettableValue<T> value2,
			FirstArgDecisiveBinaryOp<S, T, V> op, SettableValue<V> transformedValue) {
			theType = type;
			theValue1 = value1;
			theValue2 = value2;
			theOp = op;
			theTransformedValue = transformedValue;
		}

		@Override
		public Object getIdentity() {
			return theTransformedValue.getIdentity();
		}

		@Override
		public TypeToken<V> getType() {
			return theType;
		}

		@Override
		public long getStamp() {
			return Stamped.compositeStamp(Arrays.asList(theValue1, theValue2));
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(Lockable.lockable(theValue1, write, cause), Lockable.lockable(theValue2, write, cause));
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(Lockable.lockable(theValue1, write, cause), Lockable.lockable(theValue2, write, cause));
		}

		@Override
		public boolean isLockSupported() {
			return theValue1.isLockSupported() || theValue2.isLockSupported();
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return CollectionUtils.concat(theValue1.getCurrentCauses(), theValue2.getCurrentCauses());
		}

		@Override
		public V get() {
			S v1 = theValue1.get();
			V result = theOp.getFirstArgDecisiveValue(v1);
			if (result != null)
				return result;
			return theOp.apply(v1, theValue2.get());
		}

		@Override
		public Observable<ObservableValueEvent<V>> noInitChanges() {
			return theTransformedValue.noInitChanges();
		}

		@Override
		public <V2 extends V> V set(V2 value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			return theTransformedValue.set(value, cause);
		}

		@Override
		public <V2 extends V> String isAcceptable(V2 value) {
			return theTransformedValue.isAcceptable(value);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return theTransformedValue.isEnabled();
		}

		@Override
		public int hashCode() {
			return getIdentity().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Identifiable)
				return getIdentity().equals(((Identifiable) obj).getIdentity());
			else
				return false;
		}

		@Override
		public String toString() {
			return getIdentity().toString();
		}
	}
}