package org.observe.expresso.ops;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.config.QonfigInterpretationException;

/** An expression in parentheses */
public class ParentheticExpression implements ObservableExpression {
	private final ObservableExpression theContent;

	/** @param content The content of this parenthetic */
	public ParentheticExpression(ObservableExpression content) {
		theContent = content;
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
			return new ParentheticExpression(replacement);
		return this;
	}

	@Override
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException {
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
