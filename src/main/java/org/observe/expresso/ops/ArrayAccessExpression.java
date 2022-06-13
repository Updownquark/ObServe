package org.observe.expresso.ops;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class ArrayAccessExpression implements ObservableExpression {
	private final ObservableExpression theArray;
	private final ObservableExpression theIndex;

	public ArrayAccessExpression(ObservableExpression array, ObservableExpression index) {
		theArray = array;
		theIndex = index;
	}

	public ObservableExpression getArray() {
		return theArray;
	}

	public ObservableExpression getIndex() {
		return theIndex;
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return Collections.unmodifiableList(Arrays.asList(theArray, theIndex));
	}

	@Override
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException {
		if (type.getModelType() != ModelTypes.Value)
			throw new QonfigInterpretationException("An array access expression can only be evaluated as a value");

		ValueContainer<SettableValue, ? extends SettableValue<?>> arrayValue = theArray.evaluate(ModelTypes.Value.forType(//
			TypeTokens.get().getArrayType(type.getType(0), 1)), env);
		ValueContainer<SettableValue, SettableValue<Integer>> indexValue = theIndex.evaluate(ModelTypes.Value.forType(int.class), env);
		return (ValueContainer<M, MV>) this.<Object> doEval(arrayValue, indexValue, env);
	}

	private <T> ValueContainer<SettableValue, SettableValue<T>> doEval(
		ValueContainer<SettableValue, ? extends SettableValue<T[]>> arrayValue,
		ValueContainer<SettableValue, SettableValue<Integer>> indexValue, ExpressoEnv env) {
		TypeToken<T> targetType = (TypeToken<T>) arrayValue.getType().getType(0).getComponentType();
		ModelInstanceType<SettableValue, SettableValue<T>> targetModelType = ModelTypes.Value.forType(targetType);
		return new ValueContainer<SettableValue, SettableValue<T>>() {
			@Override
			public ModelInstanceType<SettableValue, SettableValue<T>> getType() {
				return targetModelType;
			}

			@Override
			public SettableValue<T> get(ModelSetInstance models) {
				SettableValue<T[]> arrayV = arrayValue.get(models);
				SettableValue<Integer> indexV = indexValue.get(models);
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
		};
	}

	@Override
	public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ExpressoEnv env)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not supported for array access expressions");
	}

	@Override
	public String toString() {
		return theArray + "[" + theIndex + "]";
	}
}
