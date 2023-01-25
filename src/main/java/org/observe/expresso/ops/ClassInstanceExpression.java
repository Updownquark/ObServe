package org.observe.expresso.ops;

import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

/** An expression that produces a {@link Class} instance from a type literal */
public class ClassInstanceExpression implements ObservableExpression {
	private final String theType;
	private final int theOffset;
	private final int theEnd;

	/** @param type The name of the type to get the class value for */
	public ClassInstanceExpression(String type, int offset, int end) {
		theType = type;
		theOffset = offset;
		theEnd = end;
	}

	/** @return The name of the type to get the class value for */
	public String getType() {
		return theType;
	}

	@Override
	public int getExpressionOffset() {
		return theOffset;
	}

	@Override
	public int getExpressionEnd() {
		return theEnd;
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return Collections.emptyList();
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		return replace.apply(this);
	}

	@Override
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws ExpressoEvaluationException {
		if (type.getModelType() != ModelTypes.Value)
			throw new ExpressoEvaluationException(theOffset, theEnd, "A class instance expression can only be evaluated to a value");
		Class<?> clazz;
		try {
			clazz = TypeTokens.getRawType(TypeTokens.get().parseType(theType));
		} catch (ParseException e) {
			throw new ExpressoEvaluationException(theOffset, theEnd, e.getMessage(), e);
		}
		TypeToken<Class<?>> classType = TypeTokens.get().keyFor(Class.class).parameterized(clazz);
		if (!TypeTokens.get().isAssignable(type.getType(0), classType))
			throw new ExpressoEvaluationException(theOffset, theEnd, theType + ".class cannot be evaluated as a " + type.getType(0));
		return (ValueContainer<M, MV>) ValueContainer.literal(ModelTypes.Value.forType(classType), clazz, theType + ".class");
	}

	@Override
	public String toString() {
		return theType + ".class";
	}
}
