package org.observe.expresso;

import java.util.Map;
import java.util.Set;

import org.observe.expresso.ObservableModelSet.ExternalModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ops.BinaryOperatorSet;
import org.observe.expresso.ops.ExternalLiteral;
import org.observe.expresso.ops.UnaryOperatorSet;
import org.qommons.ClassMap;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedPositionedContent;

/**
 * An environment in which to
 * {@link ObservableExpression#evaluate(org.observe.expresso.ModelType.ModelInstanceType, InterpretedExpressoEnv, int) evaluate}
 * {@link ObservableExpression}s
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
	private final Map<Object, Object> theProperties;
	private final boolean isTesting;

	InterpretedExpressoEnv(InterpretedModelSet models, ExternalModelSet extModels, ClassView classView,
		Map<String, ModelComponentId> attributes,
		ClassMap<Set<NonStructuredParser>> nonStructuredParsers, UnaryOperatorSet unaryOperators, BinaryOperatorSet binaryOperators,
		ErrorReporting reporting, Map<Object, Object> properties, boolean testing) {
		super(models, attributes, nonStructuredParsers, unaryOperators, binaryOperators, reporting);
		theExtModels = extModels;
		theClassView = classView;
		theProperties = properties;
		isTesting = testing;
	}

	@Override
	protected CompiledExpressoEnv copy(ObservableModelSet models, Map<String, ModelComponentId> attributes,
		ClassMap<Set<NonStructuredParser>> nonStructuredParsers, UnaryOperatorSet unaryOperators, BinaryOperatorSet binaryOperators) {
		if (models != null && !(models instanceof InterpretedModelSet))
			return super.copy(models, attributes, nonStructuredParsers, unaryOperators, binaryOperators);
		return new InterpretedExpressoEnv((InterpretedModelSet) models, theExtModels, theClassView, attributes, nonStructuredParsers,
			unaryOperators, binaryOperators, reporting(), theProperties, isTesting);
	}

	/**
	 * @param child The compilation of the interpreted environment to create
	 * @return An interpreted environment that is an amalgamation of this environment and the child
	 */
	public InterpretedExpressoEnv forChild(CompiledExpressoEnv child) {
		InterpretedExpressoEnv env = this;
		for (Map.Entry<String, ModelComponentId> attr : child.getAttributes().entrySet())
			env = env.withAttribute(attr.getKey(), attr.getValue());
		env = env.withAllNonStructuredParsers(child);
		env = env.withOperators(child.getUnaryOperators(), child.getBinaryOperators());
		if (getModels() != null) {
			if (child.getModels() != null && !getModels().getIdentity().equals(child.getModels().getIdentity()))
				env = env.with(child.getBuiltModels().createInterpreted(env));
		} else if (child.getModels() != null)
			env = env.with(child.getBuiltModels().createInterpreted(env));
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
			getUnaryOperators(), getBinaryOperators(), reporting, theProperties, isTesting);
	}

	/** @return Whether this environment is to be used for testing */
	public boolean isTesting() {
		return isTesting;
	}

	/**
	 * All {@link ObservableModelSet.InterpretedValueSynth}s for expressions parsed under this session should be
	 * {@link ObservableModelSet.InterpretedValueSynth#get(ModelSetInstance) satisfied} with a model set wrapped by this method if this
	 * element extends with-local-models.
	 *
	 * @param models The model instance
	 * @return The wrapped model instance containing data for this element's local models
	 * @throws ModelInstantiationException If the local model instantiation fails
	 */
	// public ModelSetInstance wrapLocal(ModelSetInstance models) throws ModelInstantiationException {
	// if (getModels().getIdentity() != models.getModel().getIdentity())
	// return getModels().createInstance(models.getUntil()).withAll(models).build();
	// else
	// return models;
	// }

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
			getUnaryOperators(), getBinaryOperators(), reporting(), theProperties, isTesting);
	}

	/**
	 * @param classView The class view
	 * @return A copy of this environment with the given class view
	 */
	public InterpretedExpressoEnv with(ClassView classView) {
		if (classView == theClassView)
			return this;
		return new InterpretedExpressoEnv(getModels(), theExtModels, classView, getAttributes(), getNonStructuredParsers(),
			getUnaryOperators(), getBinaryOperators(), reporting(), theProperties, isTesting);
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
			getUnaryOperators(), getBinaryOperators(), reporting(), theProperties, isTesting);
	}

	/**
	 * @param position The position at which to report errors for the new expresso environment
	 * @return The new environment
	 */
	public InterpretedExpressoEnv at(LocatedPositionedContent position) {
		ErrorReporting reporting = reporting().at(position);
		if (reporting == reporting())
			return this;
		return new InterpretedExpressoEnv(getModels(), theExtModels, theClassView, getAttributes(), getNonStructuredParsers(),
			getUnaryOperators(), getBinaryOperators(), reporting, theProperties, isTesting);
	}

	/**
	 * @param positionOffset The relative position offset at which to report errors for the new expresso environment
	 * @return The new environment
	 */
	public InterpretedExpressoEnv at(int positionOffset) {
		ErrorReporting reporting = reporting().at(positionOffset);
		if (reporting == reporting())
			return this;
		return new InterpretedExpressoEnv(getModels(), theExtModels, theClassView, getAttributes(), getNonStructuredParsers(),
			getUnaryOperators(), getBinaryOperators(), reporting, theProperties, isTesting);
	}

	/**
	 * @param testing Whether the environment is for testing
	 * @return A copy of this environment with the {@link #isTesting() testing} flag set to the given value
	 */
	public InterpretedExpressoEnv forTesting(boolean testing) {
		if (isTesting == testing)
			return this;
		return new InterpretedExpressoEnv(getModels(), theExtModels, theClassView, getAttributes(), getNonStructuredParsers(),
			getUnaryOperators(), getBinaryOperators(), reporting(), theProperties, testing);
	}

	/**
	 * @param <T> The type of the property
	 * @param key The key for the property
	 * @param type The type of the property
	 * @return The value of the property in this environment
	 */
	public <T> T getProperty(Object key, Class<T> type) {
		Object value = theProperties.get(key);
		if (value == null || type == null)
			return (T) value;
		return type.cast(value);
	}

	/**
	 * @param key The key for the property
	 * @param value The value for the given property for this environment
	 * @return This environment
	 */
	public InterpretedExpressoEnv setProperty(Object key, Object value) {
		theProperties.put(key, value);
		return this;
	}

	@Override
	public String toString() {
		return new StringBuilder().append("models=").append(getModels()).append(", cv=" + theClassView).toString();
	}
}
