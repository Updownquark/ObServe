package org.observe.expresso.ops;

import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.NonStructuredParser;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;

import com.google.common.reflect.TypeToken;

/**
 * A value to be interpreted by a {@link NonStructuredParser}, not by the expresso parser itself. External literals are useful for literals
 * of types like dates that isn't usually parsed by a programming language.
 */
public class ExternalLiteral implements ObservableExpression {
	private final String theText;

	/** @param text The text of the literal */
	public ExternalLiteral(String text) {
		theText = text;
	}

	/** @return The text of the literal */
	public String getText() {
		return theText;
	}

	@Override
	public int getChildOffset(int childIndex) {
		throw new IndexOutOfBoundsException(childIndex + " of 0");
	}

	@Override
	public int getExpressionLength() {
		return theText.length() + 2;
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
			throw new ExpressoEvaluationException(expressionOffset, theText.length(),
				"'" + theText + "' cannot be evaluated as a " + type);
		ObservableValue<?> value = parseValue(type.getType(0), env, expressionOffset);
		return ModelValueSynth.of((ModelInstanceType<M, MV>) ModelTypes.Value.forType(value.getType()), //
			LambdaUtils.constantExFn(//
				(MV) SettableValue.asSettable(value, __ -> "Literal value cannot be modified"), theText, null));
	}

	/**
	 * @param <T> The type to parse the expression as
	 * @param asType The type to parse the expression as
	 * @param env The environment to use to parse the expression
	 * @param expressionOffset The offset of this expression in the root
	 * @return The parsed expression
	 * @throws ExpressoEvaluationException If an error occurs parsing the expression
	 */
	public <T> ObservableValue<? extends T> parseValue(TypeToken<T> asType, ExpressoEnv env, int expressionOffset)
		throws ExpressoEvaluationException {
		// Get all parsers that may possibly be able to generate an appropriate value
		Class<T> rawType = TypeTokens.getRawType(asType);
		Set<NonStructuredParser> parsers = env.getNonStructuredParsers(rawType);
		if (parsers.isEmpty())
			throw new ExpressoEvaluationException(expressionOffset + 1, theText.length(),
				"No literal parsers available for type " + rawType.getName());
		NonStructuredParser parser = null;
		for (NonStructuredParser p : parsers) {
			if (p.canParse(asType, theText)) {
				parser = p;
				break;
			}
		}
		if (parser == null)
			throw new ExpressoEvaluationException(expressionOffset + 1, theText.length(),
				"No literal parsers for value `" + theText + "` as type " + rawType.getName());
		ObservableValue<? extends T> value;
		try {
			value = parser.parse(asType, theText);
		} catch (ParseException e) {
			if (e.getErrorOffset() == 0)
				throw new ExpressoEvaluationException(expressionOffset + 1, getExpressionLength(),
					"Literal parsing failed for value `" + theText + "` as type " + rawType.getName(), e);
			else
				throw new ExpressoEvaluationException(expressionOffset + 1 + e.getErrorOffset(), 0,
					"Literal parsing failed for value `" + theText + "` as type " + rawType.getName(), e);
		}
		return value;
	}

	@Override
	public int hashCode() {
		return theText.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (!(obj instanceof ExternalLiteral))
			return false;
		return theText.equals(((ExternalLiteral) obj).theText);
	}

	@Override
	public String toString() {
		return "`" + theText + "`";
	}
}