package org.observe.expresso.ops;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

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
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
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
	public ModelType<?> getModelType(CompiledExpressoEnv env) {
		return ModelTypes.Value;
	}

	@Override
	public <M, MV extends M> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, InterpretedExpressoEnv env,
		int expressionOffset) throws ExpressoEvaluationException, ExpressoInterpretationException {
		if (type.getModelType() != ModelTypes.Value)
			throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				"An array access expression can only be evaluated as a value");

		EvaluatedExpression<SettableValue<?>, SettableValue<Object[]>> arrayValue;
		try {
			arrayValue = theArray.evaluate(ModelTypes.Value.forType(//
				(TypeToken<Object[]>) TypeTokens.get().getArrayType(type.getType(0), 1)), env, expressionOffset);
		} catch (TypeConversionException e) {
			throw new ExpressoEvaluationException(expressionOffset, theArray.getExpressionLength(),
				"array " + theArray + " cannot be evaluated as a " + type.getType(0) + "[]", e);
		}
		int indexOffset = expressionOffset + theArray.getExpressionLength() + 1;
		InterpretedExpressoEnv indexEnv = env.at(theArray.getExpressionLength() + 1);
		EvaluatedExpression<SettableValue<?>, SettableValue<Integer>> indexValue;
		try {
			indexValue = theIndex.evaluate(ModelTypes.Value.forType(int.class), indexEnv, indexOffset);
		} catch (TypeConversionException e) {
			throw new ExpressoEvaluationException(indexOffset, theIndex.getExpressionLength(),
				"index " + theArray + " cannot be evaluated as an integer", e);
		}
		return (EvaluatedExpression<M, MV>) this.<Object> doEval(//
			arrayValue, indexValue, env.reporting(), indexEnv.reporting());
	}

	private <T> EvaluatedExpression<SettableValue<?>, SettableValue<T>> doEval(
		EvaluatedExpression<SettableValue<?>, SettableValue<T[]>> arrayValue,
		EvaluatedExpression<SettableValue<?>, SettableValue<Integer>> indexValue, ErrorReporting arrayReporting,
		ErrorReporting indexReporting) throws ExpressoInterpretationException {
		TypeToken<T> targetType = (TypeToken<T>) arrayValue.getType().getType(0).getComponentType();
		if (targetType == null)
			throw new ExpressoInterpretationException("Value is not an array", arrayReporting.getFileLocation().getPosition(0),
				theArray.getExpressionLength());
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
			public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException {
				SettableValue<T[]> arrayV = arrayValue.get(models);
				SettableValue<Integer> indexV = indexValue.get(models);
				return createArrayValue(arrayV, indexV);
			}

			private SettableValue<T> createArrayValue(SettableValue<T[]> arrayV, SettableValue<Integer> indexV) {
				return arrayV.transformReversible(targetType, tx -> tx.combineWith(indexV)//
					.combine((a, idx) -> {
						if (a == null) {
							arrayReporting.error("Array " + theArray + " is null");
							return null;
						} else if (idx < 0 || idx >= a.length) {
							indexReporting.error("Index " + theIndex + " evaluates to " + idx + ", which is outside the array length of "
								+ theArray + "(" + a.length + ")");
							return null;
						} else
							return a[idx];
					}).modifySource((a, idx, newValue) -> {
						if (a == null) {
							arrayReporting.error("Array " + theArray + " is null");
						} else if (idx < 0 || idx >= a.length) {
							indexReporting.error("Index " + theIndex + " evaluates to " + idx + ", which is outside the array length of "
								+ theArray + "(" + a.length + ")");
						} else
							a[idx] = newValue;
					}));
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				SettableValue<T[]> sourceArray = arrayValue.get(sourceModels);
				SettableValue<T[]> newArray = arrayValue.get(newModels);
				SettableValue<Integer> sourceIndex = indexValue.get(sourceModels);
				SettableValue<Integer> newIndex = indexValue.get(newModels);
				if (sourceArray == newArray && sourceIndex == newIndex)
					return value;
				else
					return createArrayValue(newArray, newIndex);
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
}
