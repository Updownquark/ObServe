package org.observe.expresso.ops;

import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.NonStructuredParser;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.util.TypeTokens;

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
	public int getComponentOffset(int childIndex) {
		throw new IndexOutOfBoundsException(childIndex + " of 0");
	}

	@Override
	public int getExpressionLength() {
		return theText.length() + 2;
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
	public ModelType<?> getModelType(CompiledExpressoEnv env) {
		return ModelTypes.Value;
	}

	@Override
	public <M, MV extends M> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, InterpretedExpressoEnv env,
		int expressionOffset) throws ExpressoEvaluationException {
		if (type.getModelType() != ModelTypes.Value)
			throw new ExpressoEvaluationException(expressionOffset, theText.length(), "'" + theText + "' cannot be evaluated as a " + type);
		NonStructuredParser[] parser = new NonStructuredParser[1];
		InterpretedValueSynth<SettableValue<?>, ?> value = _parseValue(type.getType(0), env, expressionOffset, parser);
		return (EvaluatedExpression<M, MV>) ObservableExpression.evEx(value, parser[0]);
	}

	private <T> InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<? extends T>> _parseValue(TypeToken<T> asType,
		InterpretedExpressoEnv env, int expressionOffset,
		NonStructuredParser[] parserUsed) throws ExpressoEvaluationException {
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
		parserUsed[0] = parser;
		InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<? extends T>> value;
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
