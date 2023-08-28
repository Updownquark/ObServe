package org.observe.expresso.ops;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedModelComponentNode;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.TypeConversionException;
import org.qommons.ex.ExceptionHandler;

/**
 * <p>
 * A reference to an attribute value via the name of the attribute that defines the value's name.
 * </p>
 * <p>
 * Expresso Qonfig contains functionality to define values whose names are the value of an attribute. Sometimes it is useful (or even
 * critical) to be able to reference that value without being able to know what the value of the attribute is.
 * </p>
 */
public class AttributeReferenceExpression implements ObservableExpression {
	private final String theAttributeName;

	/** @param attributeName The name of the attribute */
	public AttributeReferenceExpression(String attributeName) {
		theAttributeName = attributeName;
	}

	@Override
	public List<? extends ObservableExpression> getComponents() {
		return Collections.emptyList();
	}

	@Override
	public int getComponentOffset(int childIndex) {
		throw new IndexOutOfBoundsException(childIndex + " of 0");
	}

	@Override
	public int getExpressionLength() {
		return theAttributeName.length() + 2;
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		return replace.apply(this);
	}

	@Override
	public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset)
		throws ExpressoCompilationException, ExpressoEvaluationException {
		String modelValueName = env.getAttribute(theAttributeName);
		if (modelValueName == null)
			throw new ExpressoEvaluationException(expressionOffset + 1, theAttributeName.length(),
				"'" + theAttributeName + "' is not an available attribute name");
		try {
			return env.getModels().getComponent(modelValueName).getModelType(env);
		} catch (ModelException e) {
			throw new ExpressoEvaluationException(expressionOffset + 1, theAttributeName.length(), e.getMessage(), e);
		}
	}

	@Override
	public <M, MV extends M, TX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<TypeConversionException, TX> exHandler)
			throws ExpressoEvaluationException, ExpressoInterpretationException, TX {
		String modelValueName = env.getAttribute(theAttributeName);
		if (modelValueName == null)
			throw new ExpressoEvaluationException(expressionOffset + 1, theAttributeName.length(),
				"'" + theAttributeName + "' is not an available attribute name");
		InterpretedModelComponentNode<?, ?> node;
		try {
			node = env.getModels().getComponent(modelValueName).interpret(env);
		} catch (ModelException e) {
			throw new ExpressoEvaluationException(expressionOffset + 1, theAttributeName.length(), e.getMessage(), e);
		}
		InterpretedValueSynth<M, MV> value = node.as(type, env, exHandler, env.reporting().getPosition());
		return ObservableExpression.evEx(expressionOffset, getExpressionLength(), value, this);
	}

	@Override
	public String toString() {
		return "{" + theAttributeName + "}";
	}
}
