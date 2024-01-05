package org.observe.expresso;

import java.util.Map;
import java.util.Set;

import org.observe.expresso.ObservableModelSet.ExternalModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ops.BinaryOperatorSet;
import org.observe.expresso.ops.ExternalLiteral;
import org.observe.expresso.ops.UnaryOperatorSet;
import org.qommons.ClassMap;
import org.qommons.config.SessionValues;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedPositionedContent;

/**
 * An environment in which to
 * {@link ObservableExpression#evaluate(org.observe.expresso.ModelType.ModelInstanceType, InterpretedExpressoEnv, int, org.qommons.ex.ExceptionHandler.Double)
 * evaluate} {@link ObservableExpression}s
 */
public class InterpretedExpressoEnv extends CompiledExpressoEnv {
	/**
	 * Standard java environment, including imported java.lang.* and standard java operators, plus some
	 * {@link #withDefaultNonStructuredParsing() default} non-structured parsing
	 */
	public static final InterpretedExpressoEnv INTERPRETED_STANDARD_JAVA;
	static {
		InterpretedExpressoEnv env;
		try {
			env = CompiledExpressoEnv.STANDARD_JAVA.interpret(ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER),
				ClassView.build().withWildcardImport("java.lang").build());
			env.getModels().interpret(env);
		} catch (ExpressoInterpretationException e) {
			throw new IllegalStateException(e);
		}
		INTERPRETED_STANDARD_JAVA = env;
	}

	private final ExternalModelSet theExtModels;
	private final ClassView theClassView;
	private final boolean isTesting;

	InterpretedExpressoEnv(InterpretedModelSet models, ExternalModelSet extModels, ClassView classView,
		Map<String, ModelComponentId> attributes,
		ClassMap<Set<NonStructuredParser>> nonStructuredParsers, UnaryOperatorSet unaryOperators, BinaryOperatorSet binaryOperators,
		ErrorReporting reporting, SessionValues properties, boolean testing) {
		super(models, attributes, nonStructuredParsers, unaryOperators, binaryOperators, reporting, properties);
		theExtModels = extModels;
		theClassView = classView;
		isTesting = testing;
	}

	@Override
	protected CompiledExpressoEnv copy(ObservableModelSet models, Map<String, ModelComponentId> attributes,
		ClassMap<Set<NonStructuredParser>> nonStructuredParsers, UnaryOperatorSet unaryOperators, BinaryOperatorSet binaryOperators,
		ErrorReporting reporting, SessionValues properties) {
		if (models != null && !(models instanceof InterpretedModelSet))
			return super.copy(models, attributes, nonStructuredParsers, unaryOperators, binaryOperators, reporting, getProperties());
		return new InterpretedExpressoEnv((InterpretedModelSet) models, theExtModels, theClassView, attributes, nonStructuredParsers,
			unaryOperators, binaryOperators, reporting, properties, isTesting);
	}

	/**
	 * @param child The compilation of the interpreted environment to create
	 * @return An interpreted environment that is an amalgamation of this environment and the child
	 */
	public InterpretedExpressoEnv forChild(CompiledExpressoEnv child) {
		InterpretedExpressoEnv env = at(child.reporting().getFileLocation());
		for (Map.Entry<String, ModelComponentId> attr : child.getAttributes().entrySet())
			env = env.withAttribute(attr.getKey(), attr.getValue());
		env = env.withAllNonStructuredParsers(child);
		env = env.withOperators(child.getUnaryOperators(), child.getBinaryOperators());
		if (getModels() != null) {
			if (child.getModels() != null && !getModels().getIdentity().equals(child.getModels().getIdentity()))
				env = env.with(child.getBuiltModels().createInterpreted(env));
		} else if (child.getModels() != null)
			env = env.with(child.getBuiltModels().createInterpreted(env));
		if (env == this && !child.keySet().isEmpty()) {
			env = (InterpretedExpressoEnv) copy();
			for (String key : child.keySet()) {
				if (child.getSource(key) == SessionValues.ValueSource.Local)
					env.putLocal(key, child.get(key));
				else
					env.put(key, child.get(key));
			}
		}
		return env;
	}

	@Override
	public InterpretedModelSet getModels() {
		return (InterpretedModelSet) super.getModels();
	}

	/** @return The external model set in this environment */
	public ExternalModelSet getExtModels() {
		return theExtModels;
	}

	/** @return The class view for expressions to obtain types with */
	public ClassView getClassView() {
		return theClassView;
	}

	/**
	 * @param reporting The reporting for the new environment
	 * @return A copy of this environment with the given reporting
	 */
	public InterpretedExpressoEnv withErrorReporting(ErrorReporting reporting) {
		return new InterpretedExpressoEnv(getModels(), theExtModels, theClassView, getAttributes(), getNonStructuredParsers(),
			getUnaryOperators(), getBinaryOperators(), reporting, getProperties(), isTesting);
	}

	/** @return Whether this environment is to be used for testing */
	public boolean isTesting() {
		return isTesting;
	}

	@Override
	public InterpretedExpressoEnv interpret(ExternalModelSet extModels, ClassView classView) {
		return this;
	}

	/**
	 * @param models The model set
	 * @return A copy of this environment with the given models set
	 */
	public InterpretedExpressoEnv with(InterpretedModelSet models) {
		if (models == getModels())
			return this;
		return new InterpretedExpressoEnv(models, theExtModels, theClassView, getAttributes(), getNonStructuredParsers(),
			getUnaryOperators(), getBinaryOperators(), reporting(), getProperties(), isTesting);
	}

	/**
	 * @param classView The class view
	 * @return A copy of this environment with the given class view
	 */
	public InterpretedExpressoEnv with(ClassView classView) {
		if (classView == theClassView)
			return this;
		return new InterpretedExpressoEnv(getModels(), theExtModels, classView, getAttributes(), getNonStructuredParsers(),
			getUnaryOperators(), getBinaryOperators(), reporting(), getProperties(), isTesting);
	}

	@Override
	public InterpretedExpressoEnv withOperators(UnaryOperatorSet unaryOps, BinaryOperatorSet binaryOps) {
		return (InterpretedExpressoEnv) super.withOperators(unaryOps, binaryOps);
	}

	/**
	 * @param type The type to query the parser for
	 * @param parser The non-structured parser to use to evaluate {@link ExternalLiteral} expressions
	 * @return This environment
	 */
	@Override
	public InterpretedExpressoEnv withNonStructuredParser(Class<?> type, NonStructuredParser parser) {
		return (InterpretedExpressoEnv) super.withNonStructuredParser(type, parser);
	}

	/**
	 * Removes a parser registered with {@link #withNonStructuredParser(Class, NonStructuredParser)} to parse {@link ExternalLiteral}
	 * expressions
	 *
	 * @param type The type that the non-structured parser is registered for
	 * @param parser The non-structured parser to remove
	 * @return This environment
	 */
	@Override
	public InterpretedExpressoEnv removeNonStructuredParser(Class<?> type, NonStructuredParser parser) {
		return (InterpretedExpressoEnv) super.removeNonStructuredParser(type, parser);
	}

	/**
	 * @param type The type to parse
	 * @return All non-structured parsers that may be able to parse a value of the given type
	 */
	@Override
	public Set<NonStructuredParser> getNonStructuredParsers(Class<?> type) {
		return super.getNonStructuredParsers(type);
	}

	@Override
	public InterpretedExpressoEnv withAllNonStructuredParsers(CompiledExpressoEnv env) {
		return (InterpretedExpressoEnv) super.withAllNonStructuredParsers(env);
	}

	@Override
	public InterpretedExpressoEnv withAttribute(String attributeName, ModelComponentId value) {
		return (InterpretedExpressoEnv) super.withAttribute(attributeName, value);
	}

	/**
	 * @param extModels The external model set
	 * @return A copy of this environment with the given external model set
	 */
	public InterpretedExpressoEnv withExt(ExternalModelSet extModels) {
		if (extModels == theExtModels)
			return this;
		return new InterpretedExpressoEnv(getModels(), extModels, theClassView, getAttributes(), getNonStructuredParsers(),
			getUnaryOperators(), getBinaryOperators(), reporting(), getProperties(), isTesting);
	}

	@Override
	public InterpretedExpressoEnv at(LocatedPositionedContent position) {
		return (InterpretedExpressoEnv) super.at(position);
	}

	@Override
	public InterpretedExpressoEnv at(int positionOffset) {
		return (InterpretedExpressoEnv) super.at(positionOffset);
	}

	/**
	 * @param testing Whether the environment is for testing
	 * @return A copy of this environment with the {@link #isTesting() testing} flag set to the given value
	 */
	public InterpretedExpressoEnv forTesting(boolean testing) {
		if (isTesting == testing)
			return this;
		return new InterpretedExpressoEnv(getModels(), theExtModels, theClassView, getAttributes(), getNonStructuredParsers(),
			getUnaryOperators(), getBinaryOperators(), reporting(), getProperties(), testing);
	}

	@Override
	public InterpretedExpressoEnv put(String sessionKey, Object value) {
		super.put(sessionKey, value);
		return this;
	}

	@Override
	public InterpretedExpressoEnv putLocal(String sessionKey, Object value) {
		super.putLocal(sessionKey, value);
		return this;
	}

	@Override
	public InterpretedExpressoEnv putGlobal(String sessionKey, Object value) {
		super.putGlobal(sessionKey, value);
		return this;
	}

	@Override
	public String toString() {
		return new StringBuilder().append("models=").append(getModels()).append(", cv=" + theClassView).toString();
	}
}
