package org.observe.expresso.ops;

import java.util.List;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionImpl;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSetImpl;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
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
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstrained;
import org.qommons.ThreadConstraint;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;

import com.google.common.reflect.TypeToken;

/** Represents an operator that evaluates and returns the value of one expression or another depending on a boolean condition */
public class ConditionalExpression implements ObservableExpression {
	private final ObservableExpression theCondition;
	private final ObservableExpression thePrimary;
	private final ObservableExpression theSecondary;

	/**
	 * @param condition The condition to use to determine which expression to evaluate
	 * @param primary The expression to evaluate when the condition is true
	 * @param secondary The expression to evaluate when the condition is false
	 */
	public ConditionalExpression(ObservableExpression condition, ObservableExpression primary, ObservableExpression secondary) {
		theCondition = condition;
		thePrimary = primary;
		theSecondary = secondary;
	}

	/** @return The condition to use to determine which expression to evaluate */
	public ObservableExpression getCondition() {
		return theCondition;
	}

	/** @return The expression to evaluate when the condition is true */
	public ObservableExpression getPrimary() {
		return thePrimary;
	}

	/** @return The expression to evaluate when the condition is false */
	public ObservableExpression getSecondary() {
		return theSecondary;
	}

	@Override
	public int getComponentOffset(int childIndex) {
		switch (childIndex) {
		case 0:
			return 0;
		case 1:
			return theCondition.getExpressionLength() + 1;
		case 2:
			return theCondition.getExpressionLength() + thePrimary.getExpressionLength() + 2;
		default:
			throw new IndexOutOfBoundsException(childIndex + " of 3");
		}
	}

	@Override
	public int getExpressionLength() {
		return theCondition.getExpressionLength() + thePrimary.getExpressionLength() + theSecondary.getExpressionLength() + 2;
	}

	@Override
	public List<? extends ObservableExpression> getComponents() {
		return QommonsUtils.unmodifiableCopy(theCondition, thePrimary, theSecondary);
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		ObservableExpression replacement = replace.apply(this);
		if (replacement != this)
			return replacement;
		ObservableExpression condition = theCondition.replaceAll(replace);
		ObservableExpression primary = thePrimary.replaceAll(replace);
		ObservableExpression secondary = theSecondary.replaceAll(replace);
		if (condition != theCondition || primary != thePrimary || secondary != theSecondary)
			return new ConditionalExpression(condition, primary, secondary);
		return this;
	}

	@Override
	public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset) throws ExpressoCompilationException {
		int primaryOffset = expressionOffset + getComponentOffset(1);
		return thePrimary.getModelType(env, primaryOffset).getCommonType(//
			theSecondary.getModelType(env, primaryOffset + thePrimary.getExpressionLength() + 1));
	}

	@Override
	public <M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX {
		if (type.getModelType() == ModelTypes.Action || type.getModelType() == ModelTypes.Value
			|| type.getModelType() == ModelTypes.Collection || type.getModelType() == ModelTypes.Set) {//
		} else {
			throw new ExpressoInterpretationException(
				"Conditional expressions not supported for model type " + type.getModelType() + " (" + this + ")",
				env.reporting().getPosition(), getExpressionLength());
		}
		ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, EX, NeverThrown> doubleX = exHandler
			.stack(ExceptionHandler.holder(exHandler.isInstantiating()));
		EvaluatedExpression<SettableValue<?>, SettableValue<Boolean>> conditionV = theCondition.evaluate(//
			ModelTypes.Value.forType(boolean.class), env, expressionOffset, doubleX);
		if (doubleX.hasException1())
			return null;
		else if (doubleX.hasException2()) {
			exHandler.handle1(() -> new ExpressoInterpretationException(doubleX.get2().getMessage(), env.reporting().getPosition(),
				theCondition.getExpressionLength()));
			return null;
		} else if (conditionV == null)
			return null;
		int primaryOffset = expressionOffset + theCondition.getExpressionLength() + 1;
		InterpretedExpressoEnv primaryEnv = env.at(theCondition.getExpressionLength() + 1);
		EvaluatedExpression<M, MV> primaryV = thePrimary.evaluate(type, primaryEnv, primaryOffset, doubleX.use());
		if (doubleX.hasException1())
			return null;
		else if (doubleX.hasException2()) {
			exHandler.handle1(() -> new ExpressoInterpretationException(doubleX.get2().getMessage(),
				env.reporting().at(getComponentOffset(1)).getPosition(), thePrimary.getExpressionLength()));
			return null;
		} else if (primaryV == null)
			return null;
		int secondaryOffset = primaryOffset + thePrimary.getExpressionLength() + 1;
		InterpretedExpressoEnv secondaryEnv = primaryEnv.at(thePrimary.getExpressionLength() + 1);
		EvaluatedExpression<M, MV> secondaryV = theSecondary.evaluate(type, secondaryEnv, secondaryOffset, doubleX.use());
		if (doubleX.hasException1())
			return null;
		else if (doubleX.hasException2()) {
			exHandler.handle1(() -> new ExpressoInterpretationException(doubleX.get2().getMessage(),
				env.reporting().at(getComponentOffset(2)).getPosition(), theSecondary.getExpressionLength()));
			return null;
		} else if (secondaryV == null)
			return null;

		ModelInstanceType<M, MV> resultType;
		// If either target is literal null, use the type of the other
		if (isLiteralNull(thePrimary))
			resultType = secondaryV.getType();
		else if (isLiteralNull(theSecondary))
			resultType = primaryV.getType();
		else if (primaryV.getType().equals(secondaryV.getType()))
			resultType = primaryV.getType();
		else {
			TypeToken<?>[] types = new TypeToken[primaryV.getType().getModelType().getTypeCount()];
			for (int i = 0; i < types.length; i++)
				types[i] = TypeTokens.get().getCommonType(primaryV.getType().getType(i), secondaryV.getType().getType(i));
			resultType = (ModelInstanceType<M, MV>) primaryV.getType().getModelType().forTypes(types);
		}
		return new EvaluatedExpression<M, MV>() {
			@Override
			public ModelType<M> getModelType() {
				return resultType.getModelType();
			}

			@Override
			public ModelInstanceType<M, MV> getType() {
				return resultType;
			}

			@Override
			public int getExpressionOffset() {
				return expressionOffset;
			}

			@Override
			public int getExpressionLength() {
				return ConditionalExpression.this.getExpressionLength();
			}

			@Override
			public Object getDescriptor() {
				return null;
			}

			@Override
			public List<? extends EvaluatedExpression<?, ?>> getComponents() {
				return QommonsUtils.unmodifiableCopy(conditionV, primaryV, secondaryV);
			}

			@Override
			public ModelValueInstantiator<MV> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(resultType, conditionV.instantiate(), primaryV.instantiate(), secondaryV.instantiate());
			}

			@Override
			public String toString() {
				return conditionV + " ? " + primaryV + " : " + secondaryV;
			}
		};
	}

	private static boolean isLiteralNull(ObservableExpression ex) {
		if (ex instanceof BufferedExpression || ex instanceof ParentheticExpression)
			return isLiteralNull(ex.getComponents().get(0));
		else if (ex instanceof LiteralExpression && ((LiteralExpression<?>) ex).getValue() == null)
			return true;
		else
			return false;
	}

	@Override
	public String toString() {
		return theCondition + "?" + thePrimary + ":" + theSecondary;
	}

	static class Instantiator<M, MV extends M> implements ModelValueInstantiator<MV> {
		private final ModelInstanceType<M, MV> theType;
		private final ModelValueInstantiator<SettableValue<Boolean>> theCondition;
		private final ModelValueInstantiator<MV> thePrimary;
		private final ModelValueInstantiator<MV> theSecondary;

		public Instantiator(ModelInstanceType<M, MV> type, ModelValueInstantiator<SettableValue<Boolean>> condition,
			ModelValueInstantiator<MV> primary, ModelValueInstantiator<MV> secondary) {
			theType = type;
			theCondition = condition;
			thePrimary = primary;
			theSecondary = secondary;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			theCondition.instantiate();
			thePrimary.instantiate();
			theSecondary.instantiate();
		}

		@Override
		public MV get(ModelSetInstance msi) throws ModelInstantiationException {
			SettableValue<Boolean> conditionX = theCondition.get(msi);
			Object primaryX = thePrimary.get(msi);
			Object secondaryX = theSecondary.get(msi);
			return createValue(conditionX, primaryX, secondaryX);
		}

		private MV createValue(SettableValue<Boolean> conditionX, Object primaryX, Object secondaryX) {
			if (theType.getModelType() == ModelTypes.Value) {
				return (MV) new ConditionalValue<>(conditionX, (SettableValue<Object>) primaryX, (SettableValue<Object>) secondaryX);
			} else if (theType.getModelType() == ModelTypes.Collection) {
				ObservableValue<? extends ObservableCollection<?>> toFlatten = conditionX.map(LambdaUtils.printableFn(c -> {
					if (c != null && c)
						return (ObservableCollection<Object>) primaryX;
					else
						return (ObservableCollection<Object>) secondaryX;
				}, () -> "? " + primaryX + ": " + secondaryX, null));
				return (MV) new ObservableCollectionImpl.FlattenedValueCollection<Object>(toFlatten, Equivalence.DEFAULT) {
					@Override
					public ThreadConstraint getThreadConstraint() {
						return ThreadConstrained.getThreadConstraint(conditionX, (ObservableCollection<?>) primaryX,
							(ObservableCollection<?>) secondaryX);
					}
				};
			} else if (theType.getModelType() == ModelTypes.Set) {
				ObservableValue<? extends ObservableSet<Object>> toFlatten = conditionX.map(LambdaUtils.printableFn(c -> {
					if (c != null && c)
						return (ObservableSet<Object>) primaryX;
					else
						return (ObservableSet<Object>) secondaryX;
				}, () -> "? " + primaryX + ": " + secondaryX, null));
				return (MV) new ObservableSetImpl.FlattenedValueSet<Object>(toFlatten, Equivalence.DEFAULT) {
					@Override
					public ThreadConstraint getThreadConstraint() {
						return ThreadConstrained.getThreadConstraint(conditionX, (ObservableCollection<?>) primaryX,
							(ObservableCollection<?>) secondaryX);
					}
				};
			} else if (theType.getModelType() == ModelTypes.Action) {
				return (MV) ObservableAction.of(LambdaUtils.printableConsumer(evt -> {
					if (Boolean.TRUE.equals(conditionX.get()))
						((ObservableAction) primaryX).act(evt);
					else
						((ObservableAction) secondaryX).act(evt);
				}, () -> conditionX + " ? " + primaryX + " : " + secondaryX, null));
			} else
				throw new IllegalStateException("Conditional expressions not supported for model type " + theType.getModelType());
		}

		@Override
		public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
			SettableValue<Boolean> sourceCondition = theCondition.get(sourceModels);
			SettableValue<Boolean> newCondition = theCondition.forModelCopy(sourceCondition, sourceModels, newModels);
			MV sourcePrimary = thePrimary.get(sourceModels);
			MV newPrimary = thePrimary.forModelCopy(sourcePrimary, sourceModels, newModels);
			MV sourceSecondary = theSecondary.get(sourceModels);
			MV newSecondary = theSecondary.forModelCopy(sourceSecondary, sourceModels, newModels);
			if (sourceCondition == newCondition && sourcePrimary == newPrimary && sourceSecondary == newSecondary)
				return value;
			return createValue(newCondition, newPrimary, newSecondary);
		}

		@Override
		public String toString() {
			return theCondition + "?" + thePrimary + ":" + theSecondary;
		}
	}

	static class ConditionalValue<T> extends SettableValue.SettableFlattenedObservableValue<T> {
		private final SettableValue<Boolean> theCondition;
		private final SettableValue<T> thePrimary;
		private final SettableValue<T> theSecondary;

		ConditionalValue(SettableValue<Boolean> condition, SettableValue<T> primary, SettableValue<T> secondary) {
			super(//
				condition.map(LambdaUtils.printableFn(c -> {
					if (Boolean.TRUE.equals(c))
						return primary;
					else
						return secondary;
				}, () -> "? " + primary + " : " + secondary, null)), null);
			theCondition = condition;
			thePrimary = primary;
			theSecondary = secondary;
		}

		@Override
		protected Object createIdentity() {
			// We can support a prettier print here
			return Identifiable.buildId()//
				.append("(")//
				.withPrintedId(theCondition)//
				.append(") ? (")//
				.withPrintedId(thePrimary)//
				.append(") : (")//
				.withPrintedId(theSecondary)//
				.append(")")//
				.build();
		}

		@Override
		public ConditionalValue<T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return super.isEnabled().transform(tx -> tx//
				.combineWith(theCondition.isEnabled())//
				.combine((fd, cd) -> {
					// It may be possible to set this value even when the active conditional value is disabled.
					if (fd == null || cd == null)
						return null;
					// This one's hard. Which message to expose?
					// We'll defer to the conditional value. If they want a different message, they can use a transform <disable with="?">.
					return cd;
				}));
		}

		@Override
		public String isAcceptable(T value) {
			String msg = super.isAcceptable(value);
			if (msg == null)
				return null;
			else if (theCondition.isEnabled().get() != null)
				return msg;
			if (value == thePrimary.get()) {
				if (theCondition.isAcceptable(true) == null)
					return null;
			} else if (value == theSecondary.get()) {
				if (theCondition.isAcceptable(false) == null)
					return null;
			}
			return msg;
		}

		@Override
		public T set(T value) throws IllegalArgumentException, UnsupportedOperationException {
			String msg = super.isAcceptable(value);
			if (msg == null)
				return super.set(value);
			else if (value == thePrimary.get()) {
				if (theCondition.isAcceptable(true) == null) {
					T prev = get();
					theCondition.set(true);
					return prev;
				}
			} else if (value == theSecondary.get()) {
				if (theCondition.isAcceptable(false) == null) {
					T prev = get();
					theCondition.set(false);
					return prev;
				}
			}
			return super.set(value);
		}

		@Override
		public String toString() {
			return getIdentity().toString();
		}
	}
}
