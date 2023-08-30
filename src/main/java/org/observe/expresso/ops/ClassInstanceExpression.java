package org.observe.expresso.ops;

import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.util.TypeTokens;
import org.qommons.ex.ExceptionHandler;

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
	public int getComponentOffset(int childIndex) {
		throw new IndexOutOfBoundsException(childIndex + " of 0");
	}

	@Override
	public int getExpressionLength() {
		return theType.length() + 6 + theOpSpacing;
	}

	@Override
	public List<? extends ObservableExpression> getComponents() {
		return Collections.emptyList();
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		return replace.apply(this);
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
			throw new ExpressoInterpretationException("A class instance expression can only be evaluated to a value",
				env.reporting().getPosition(), getExpressionLength());
		}
		Class<?> clazz;
		try {
			clazz = TypeTokens.getRawType(env.getClassView().parseType(theType.getName()));
		} catch (ParseException e) {
			throw new ExpressoInterpretationException(e.getMessage(), env.reporting().at(e.getErrorOffset()).getPosition(),
				e.getErrorOffset() == 0 ? getExpressionLength() : 0, e);
		}
		TypeToken<Class<?>> classType = TypeTokens.get().keyFor(Class.class).parameterized(clazz);
		if (!TypeTokens.get().isAssignable(type.getType(0), classType)) {
			exHandler.handle1(new ExpressoInterpretationException(theType + ".class cannot be evaluated as a " + type.getType(0),
				env.reporting().getPosition(), getExpressionLength()));
			return null;
		}
		return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
			(InterpretedValueSynth<M, MV>) InterpretedValueSynth.literalValue(classType, clazz, theType + ".class"), clazz);
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
