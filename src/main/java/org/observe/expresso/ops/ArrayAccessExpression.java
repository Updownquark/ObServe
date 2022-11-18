package org.observe.expresso.ops;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigInterpretationException;

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
	public List<? extends ObservableExpression> getChildren() {
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
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException {
		if (type.getModelType() != ModelTypes.Value)
			throw new QonfigInterpretationException("An array access expression can only be evaluated as a value");

		ValueContainer<SettableValue<?>, SettableValue<Object[]>> arrayValue = theArray.evaluate(ModelTypes.Value.forType(//
			(TypeToken<Object[]>) TypeTokens.get().getArrayType(type.getType(0), 1)), env);
		ValueContainer<SettableValue<?>, SettableValue<Integer>> indexValue = theIndex.evaluate(ModelTypes.Value.forType(int.class), env);
		return (ValueContainer<M, MV>) this.<Object> doEval(arrayValue, indexValue, env);
	}

	private <T> ValueContainer<SettableValue<?>, SettableValue<T>> doEval(
		ValueContainer<SettableValue<?>, SettableValue<T[]>> arrayValue,
		ValueContainer<SettableValue<?>, SettableValue<Integer>> indexValue, ExpressoEnv env) {
		TypeToken<T> targetType = (TypeToken<T>) arrayValue.getType().getType(0).getComponentType();
		ModelInstanceType<SettableValue<?>, SettableValue<T>> targetModelType = ModelTypes.Value.forType(targetType);
		return new ValueContainer<SettableValue<?>, SettableValue<T>>() {
			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
				return targetModelType;
			}

			@Override
			public SettableValue<T> get(ModelSetInstance models) {
				SettableValue<T[]> arrayV = arrayValue.get(models);
				SettableValue<Integer> indexV = indexValue.get(models);
				return createArrayValue(arrayV, indexV);
			}

			private SettableValue<T> createArrayValue(SettableValue<T[]> arrayV, SettableValue<Integer> indexV) {
				return arrayV.transformReversible(targetType, tx -> tx.combineWith(indexV)//
					.combine((a, idx) -> {
						if (a == null) {
							System.err.println("Array " + theArray + " is null");
							return null;
						} else if (idx < 0 || idx >= a.length) {
							System.err.println("Index " + theIndex + " evaluates to " + idx + ", which is outside the array length of "
								+ theArray + "(" + a.length + ")");
							return null;
						} else
							return a[idx];
					}).modifySource((a, idx, newValue) -> {
						if (a == null) {
							System.err.println("Array " + theArray + " is null");
						} else if (idx < 0 || idx >= a.length) {
							System.err.println("Index " + theIndex + " evaluates to " + idx + ", which is outside the array length of "
								+ theArray + "(" + a.length + ")");
						} else
							a[idx] = newValue;
					}));
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels) {
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
			public BetterList<ValueContainer<?, ?>> getCores() {
				return BetterList.of(Stream.of(arrayValue, indexValue).flatMap(cv -> cv.getCores().stream()));
			}
		};
	}

	@Override
	public String toString() {
		return theArray + "[" + theIndex + "]";
	}
}
