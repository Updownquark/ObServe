package org.observe.expresso.ops;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ValueContainer;

/** An expression in parentheses */
public class ParentheticExpression implements ObservableExpression {
	private final ObservableExpression theContent;
	private int theOffset;
	private int theEnd;

	/**
	 * @param content The content of this parenthetic
	 * @param offset The starting position of this expression in the root sequence
	 * @param end The ending position of this expression in the root sequence
	 */
	public ParentheticExpression(ObservableExpression content, int offset, int end) {
		theContent = content;
		theOffset = offset;
		theEnd = end;
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
		return Collections.singletonList(theContent);
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		ObservableExpression replacement = replace.apply(this);
		if (replacement != this)
			return replacement;
		replacement = theContent.replaceAll(replace);
		if (replacement != theContent)
			return new ParentheticExpression(replacement, theOffset, theEnd);
		return this;
	}

	@Override
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws ExpressoEvaluationException, ExpressoInterpretationException {
		return theContent.evaluateInternal(type, env);
	}

	@Override
	public int hashCode() {
		return theContent.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof ParentheticExpression && theContent.equals(((ParentheticExpression) obj).theContent);
	}

	@Override
	public String toString() {
		return "(" + theContent + ")";
	}
}
