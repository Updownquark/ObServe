package org.observe.expresso.ops;

import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

/** An expression that produces a {@link Class} instance from a type literal */
public class ClassInstanceExpression implements ObservableExpression {
	private final BufferedType theType;
	private final int theOpSpacing;

	/**
	 * @param type The name of the type to get the class value for
	 * @param opSpacing The number of spaces between '.' and 'class' in this expression
	 */
	public ClassInstanceExpression(BufferedType type, int opSpacing) {
		theType = type;
		theOpSpacing = opSpacing;
	}

	/** @return The name of the type to get the class value for */
	public BufferedType getType() {
		return theType;
	}

	/** @return The number of spaces between '.' and 'class' in this expression */
	public int getOpSpacing() {
		return theOpSpacing;
	}

	@Override
	public int getChildOffset(int childIndex) {
		throw new IndexOutOfBoundsException(childIndex + " of 0");
	}

	@Override
	public int getExpressionLength() {
		return theType.length() + 6 + theOpSpacing;
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
	public ModelType<?> getModelType(ExpressoEnv env) {
		return ModelTypes.Value;
	}

	@Override
	public <M, MV extends M> ModelValueSynth<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env, int expressionOffset)
		throws ExpressoEvaluationException {
		if (type.getModelType() != ModelTypes.Value)
			throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				"A class instance expression can only be evaluated to a value");
		Class<?> clazz;
		try {
			clazz = TypeTokens.getRawType(env.getClassView().parseType(theType.getName()));
		} catch (ParseException e) {
			if (e.getErrorOffset() == 0)
				throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(), e.getMessage(), e);
			else
				throw new ExpressoEvaluationException(expressionOffset + e.getErrorOffset(), 0, e.getMessage(), e);
		}
		TypeToken<Class<?>> classType = TypeTokens.get().keyFor(Class.class).parameterized(clazz);
		if (!TypeTokens.get().isAssignable(type.getType(0), classType))
			throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				theType + ".class cannot be evaluated as a " + type.getType(0));
		return (ModelValueSynth<M, MV>) ModelValueSynth.literal(ModelTypes.Value.forType(classType), clazz, theType + ".class");
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append(theType).append('.');
		for (int i = 0; i < theOpSpacing; i++)
			str.append(' ');
		return str.append("class").toString();
	}
}
