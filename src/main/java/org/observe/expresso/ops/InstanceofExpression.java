package org.observe.expresso.ops;

import java.util.Collections;
import java.util.List;

import org.observe.SettableValue;
import org.observe.expresso.ClassView;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class InstanceofExpression implements ObservableExpression {
	private final ObservableExpression theLeft;
	private final String theType;

	public InstanceofExpression(ObservableExpression left, String type) {
		theLeft = left;
		theType = type;
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return Collections.singletonList(theLeft);
	}

	@Override
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ObservableModelSet models,
		ClassView classView) throws QonfigInterpretationException {
		if (type.getModelType() != ModelTypes.Value && !TypeTokens.get().isAssignable(type.getType(0), TypeTokens.get().BOOLEAN))
			throw new QonfigInterpretationException("instanceof expressions can only be evaluated to Value<Boolean>");
		ValueContainer<SettableValue, SettableValue<?>> leftValue = theLeft.evaluate(ModelTypes.Value.any(), models, classView);
		Class<?> testType = classView.getType(theType);
		ValueContainer<SettableValue, SettableValue<Boolean>> container = leftValue.map(ModelTypes.Value.forType(boolean.class),
			(lv, msi) -> {
				return SettableValue.asSettable(lv.map(TypeTokens.get().BOOLEAN, v -> v != null && testType.isInstance(v)),
					__ -> "instanceof expressions are not reversible");
			});
		return (ValueContainer<M, MV>) container;
	}

	@Override
	public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ObservableModelSet models, ClassView classView)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not supported for instanceof operators");
	}

	@Override
	public String toString() {
		return theLeft + " instanceof " + theType;
	}
}
