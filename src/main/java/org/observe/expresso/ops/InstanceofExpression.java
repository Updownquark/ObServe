package org.observe.expresso.ops;

import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigInterpretationException;

/** An expression that returns a boolean for whether a given expression's value is an instance of a constant type */
public class InstanceofExpression implements ObservableExpression {
	private final ObservableExpression theLeft;
	private final String theType;

	/**
	 * @param left The expression whose type to check
	 * @param type The type to check against
	 */
	public InstanceofExpression(ObservableExpression left, String type) {
		theLeft = left;
		theType = type;
	}

	/** @return The expression whose type to check */
	public ObservableExpression getLeft() {
		return theLeft;
	}

	/** @return The type to check against */
	public String getType() {
		return theType;
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return Collections.singletonList(theLeft);
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		ObservableExpression replacement = replace.apply(this);
		if (replacement != this)
			return replacement;
		ObservableExpression left = theLeft.replaceAll(replace);
		if (left != theLeft)
			return new InstanceofExpression(left, theType);
		return this;
	}

	@Override
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException {
		if (type.getModelType() != ModelTypes.Value && !TypeTokens.get().isAssignable(type.getType(0), TypeTokens.get().BOOLEAN))
			throw new QonfigInterpretationException("instanceof expressions can only be evaluated to Value<Boolean>");
		ValueContainer<SettableValue<?>, SettableValue<?>> leftValue = theLeft.evaluate(ModelTypes.Value.any(), env);
		Class<?> testType;
		try {
			testType = TypeTokens.getRawType(TypeTokens.get().parseType(theType));
		} catch (ParseException e) {
			throw new QonfigInterpretationException(e.getMessage(), e);
		}
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> container = leftValue.map(ModelTypes.Value.forType(boolean.class),
			(lv, msi) -> {
				return SettableValue.asSettable(lv.map(TypeTokens.get().BOOLEAN, v -> v != null && testType.isInstance(v)),
					__ -> "instanceof expressions are not reversible");
			});
		return (ValueContainer<M, MV>) container;
	}

	@Override
	public String toString() {
		return theLeft + " instanceof " + theType;
	}
}
