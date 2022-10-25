package org.observe.expresso.ops;

import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.Expression;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.NonStructuredParser;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;
import org.qommons.TriFunction;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * A value to be interpreted by a {@link NonStructuredParser}, not by the expresso parser itself. External literals are useful for literals
 * of types like dates that isn't usually parsed by a programming language.
 */
public class ExternalLiteral implements ObservableExpression {
	private final Expression theExpression;
	private final String theText;

	/**
	 * @param expression The expression containing the literal
	 * @param text The text of the literal
	 */
	public ExternalLiteral(Expression expression, String text) {
		theExpression = expression;
		theText = text;
	}

	/** @return The expression containing the literal */
	public Expression getExpression() {
		return theExpression;
	}

	/** @return The text of the literal */
	public String getText() {
		return theText;
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
		throws QonfigInterpretationException {
		if (type.getModelType() != ModelTypes.Value)
			throw new QonfigInterpretationException("'" + theExpression.getText() + "' cannot be evaluated as a " + type);
		ObservableValue<?> value = parseValue(type.getType(0), env);
		return ValueContainer.of((ModelInstanceType<M, MV>) ModelTypes.Value.forType(value.getType()), //
			LambdaUtils.constantFn(//
				(MV) SettableValue.asSettable(value, __ -> "Literal value cannot be modified"), theExpression.getText(), null));
	}

	@Override
	public <P1, P2, P3, T2> MethodFinder<P1, P2, P3, T2> findMethod(TypeToken<T2> targetType, ExpressoEnv env)
		throws QonfigInterpretationException {
		ObservableValue<?> value = parseValue(targetType, env);
		return new MethodFinder<P1, P2, P3, T2>(targetType) {
			@Override
			public Function<ModelSetInstance, TriFunction<P1, P2, P3, T2>> find3() throws QonfigInterpretationException {
				for (MethodOption option : theOptions) {
					if (option.size() == 0)
						return __ -> (p1, p2, p3) -> (T2) value.get();
				}
				throw new QonfigInterpretationException("No zero-parameter option for literal `" + theText + "`");
			}
		};
	}

	/**
	 * @param <T> The type to parse the expression as
	 * @param asType The type to parse the expression as
	 * @param env The environment to use to parse the expression
	 * @return The parsed expression
	 * @throws QonfigInterpretationException If an error occurs parsing the expression
	 */
	public <T> ObservableValue<? extends T> parseValue(TypeToken<T> asType, ExpressoEnv env) throws QonfigInterpretationException {
		// Get all parsers that may possibly be able to generate an appropriate value
		Class<T> rawType = TypeTokens.getRawType(asType);
		Set<NonStructuredParser> parsers = env.getNonStructuredParsers(rawType);
		if (parsers.isEmpty())
			throw new QonfigInterpretationException("No literal parsers available for type " + rawType.getName());
		NonStructuredParser parser = null;
		for (NonStructuredParser p : parsers) {
			if (p.canParse(asType, theText)) {
				parser = p;
				break;
			}
		}
		if (parser == null)
			throw new QonfigInterpretationException("No literal parsers for value `" + theText + "` as type " + rawType.getName());
		ObservableValue<? extends T> value;
		try {
			value = parser.parse(asType, theText);
		} catch (ParseException e) {
			throw new QonfigInterpretationException("Literal parsing failed for value `" + theText + "` as type " + rawType.getName(), e);
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
		return theExpression.toString();
	}
}