package org.observe.expresso.ops;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

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
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;
import org.qommons.io.ErrorReporting;

import com.google.common.reflect.TypeToken;

/** An expression representing access to an array value by index */
public class ArrayAccessExpression implements ObservableExpression {
	private final ObservableExpression theArray;
	private final ObservableExpression theIndex;

	/**
	 * @param array The expression representing the array being accessed
	 * @param index The expression representing the index at which the array is being accessed
	 */
	public ArrayAccessExpression(ObservableExpression array, ObservableExpression index) {
		theArray = array;
		theIndex = index;
	}

	/** @return The expression representing the array being accessed */
	public ObservableExpression getArray() {
		return theArray;
	}

	/** @return The expression representing the index at which the array is being accessed */
	public ObservableExpression getIndex() {
		return theIndex;
	}

	@Override
	public int getComponentOffset(int childIndex) {
		switch (childIndex) {
		case 0:
			return 0;
		case 1:
			return theArray.getExpressionLength() + 1;
		default:
			throw new IndexOutOfBoundsException(childIndex + " of 2");
		}
	}

	@Override
	public int getExpressionLength() {
		return theArray.getExpressionLength() + theIndex.getExpressionLength() + 2;
	}

	@Override
	public List<? extends ObservableExpression> getComponents() {
		return Collections.unmodifiableList(Arrays.asList(theArray, theIndex));
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		ObservableExpression replacement = replace.apply(this);
		if (replacement != this)
			return replacement;
		ObservableExpression array = theArray.replaceAll(replace);
		ObservableExpression index = theIndex.replaceAll(replace);
		if (array != theArray || index != theIndex)
			return new ArrayAccessExpression(array, index);
		return this;
	}

	@Override
	public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset) {
		return ModelTypes.Value;
	}

	@Override
	public <M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
		throws ExpressoInterpretationException, EX {
		if (type.getModelType() != ModelTypes.Value) {
			exHandler.handle1(new ExpressoInterpretationException("An array access expression can only be evaluated as a value",
				env.reporting().getPosition(), getExpressionLength()));
			return null;
		}

		EvaluatedExpression<SettableValue<?>, SettableValue<Object[]>> arrayValue;
		ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, NeverThrown, NeverThrown> tce = ExceptionHandler
			.holder2();
		arrayValue = theArray.evaluate(ModelTypes.Value.forType(//
			(TypeToken<Object[]>) TypeTokens.get().getArrayType(type.getType(0), 1)), env, expressionOffset, tce);
		if (arrayValue == null) {
			if (tce.get1() != null)
				exHandler.handle1(
					new ExpressoInterpretationException(tce.get1().getMessage(), tce.get1().getPosition(), theArray.getExpressionLength()));
			else
				exHandler.handle1(
					new ExpressoInterpretationException(tce.get2().getMessage(), env.reporting().getPosition(), getExpressionLength()));
			return null;
		}
		int indexOffset = expressionOffset + theArray.getExpressionLength() + 1;
		InterpretedExpressoEnv indexEnv = env.at(theArray.getExpressionLength() + 1);
		EvaluatedExpression<SettableValue<?>, SettableValue<Integer>> indexValue;
		indexValue = theIndex.evaluate(ModelTypes.Value.forType(int.class), indexEnv, indexOffset, tce);
		if (indexValue == null) {
			exHandler.handle1(new ExpressoInterpretationException(tce.get1().getMessage(),
				env.reporting().at(getComponentOffset(1)).getPosition(), theIndex.getExpressionLength()));
			return null;
		}
		return (EvaluatedExpression<M, MV>) this.<Object, EX> doEval(expressionOffset, //
			arrayValue, indexValue, env.reporting(), indexEnv.reporting(), exHandler);
	}

	private <T, EX extends Throwable> EvaluatedExpression<SettableValue<?>, SettableValue<T>> doEval(int expressionOffset,
		EvaluatedExpression<SettableValue<?>, SettableValue<T[]>> arrayValue,
		EvaluatedExpression<SettableValue<?>, SettableValue<Integer>> indexValue, ErrorReporting arrayReporting,
		ErrorReporting indexReporting, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws EX {
		TypeToken<T> targetType = (TypeToken<T>) arrayValue.getType().getType(0).getComponentType();
		if (targetType == null) {
			exHandler.handle1(new ExpressoInterpretationException("Value is not an array", arrayReporting.getFileLocation().getPosition(0),
				theArray.getExpressionLength()));
			return null;
		}
		ModelInstanceType<SettableValue<?>, SettableValue<T>> targetModelType = ModelTypes.Value.forType(targetType);
		return new EvaluatedExpression<SettableValue<?>, SettableValue<T>>() {
			@Override
			public ModelType<SettableValue<?>> getModelType() {
				return ModelTypes.Value;
			}

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
				return targetModelType;
			}

			@Override
			public ModelValueInstantiator<SettableValue<T>> instantiate() {
				return new Instantiator<>(targetType, arrayValue.instantiate(), arrayReporting, indexValue.instantiate(), indexReporting);
			}

			@Override
			public int getExpressionOffset() {
				return expressionOffset;
			}

			@Override
			public int getExpressionLength() {
				return ArrayAccessExpression.this.getExpressionLength();
			}

			@Override
			public Object getDescriptor() {
				return null;
			}

			@Override
			public List<? extends EvaluatedExpression<?, ?>> getComponents() {
				return QommonsUtils.unmodifiableCopy(arrayValue, indexValue);
			}
		};
	}

	@Override
	public String toString() {
		return theArray + "[" + theIndex + "]";
	}

	static class Instantiator<T> implements ModelValueInstantiator<SettableValue<T>> {
		private final TypeToken<T> theTargetType;
		private final ModelValueInstantiator<SettableValue<T[]>> theArray;
		private final ErrorReporting theArrayReporting;
		private final ModelValueInstantiator<SettableValue<Integer>> theIndex;
		private final ErrorReporting theIndexReporting;

		public Instantiator(TypeToken<T> targetType, ModelValueInstantiator<SettableValue<T[]>> array, ErrorReporting arrayReporting,
			ModelValueInstantiator<SettableValue<Integer>> index, ErrorReporting indexReporting) {
			theTargetType = targetType;
			theArray = array;
			theArrayReporting = arrayReporting;
			theIndex = index;
			theIndexReporting = indexReporting;
		}

		@Override
		public void instantiate() {
			theArray.instantiate();
			theIndex.instantiate();
		}

		@Override
		public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			SettableValue<T[]> arrayV = theArray.get(models);
			SettableValue<Integer> indexV = theIndex.get(models);
			return createArrayValue(arrayV, indexV);
		}

		private SettableValue<T> createArrayValue(SettableValue<T[]> arrayV, SettableValue<Integer> indexV) {
			return arrayV.transformReversible(theTargetType, tx -> tx.combineWith(indexV)//
				.combine((a, idx) -> {
					if (a == null) {
						theArrayReporting.error("Array " + theArray + " is null");
						return null;
					} else if (idx < 0 || idx >= a.length) {
						theIndexReporting.error("Index " + theIndex + " evaluates to " + idx + ", which is outside the array length of "
							+ theArray + "(" + a.length + ")");
						return null;
					} else
						return a[idx];
				}).modifySource((a, idx, newValue) -> {
					if (a == null) {
						theArrayReporting.error("Array " + theArray + " is null");
					} else if (idx < 0 || idx >= a.length) {
						theIndexReporting.error("Index " + theIndex + " evaluates to " + idx + ", which is outside the array length of "
							+ theArray + "(" + a.length + ")");
					} else
						a[idx] = newValue;
				}));
		}

		@Override
		public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			SettableValue<T[]> sourceArray = theArray.get(sourceModels);
			SettableValue<T[]> newArray = theArray.forModelCopy(sourceArray, sourceModels, newModels);
			SettableValue<Integer> sourceIndex = theIndex.get(sourceModels);
			SettableValue<Integer> newIndex = theIndex.forModelCopy(sourceIndex, sourceModels, newModels);
			if (sourceArray == newArray && sourceIndex == newIndex)
				return value;
			else
				return createArrayValue(newArray, newIndex);
		}

		@Override
		public String toString() {
			return theArray + "[" + theIndex + "]";
		}
	}
}
