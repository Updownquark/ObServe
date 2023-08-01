package org.observe.expresso;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.observe.expresso.ObservableModelSet.ExternalModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
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
				ClassView.build().withWildcardImport("java.lang").build(), new ErrorReporting.Default(LocatedPositionedContent.EMPTY),
				null);
			env.getModels().interpret(env);
		} catch (ExpressoInterpretationException e) {
			throw new IllegalStateException(e);
		}
		INTERPRETED_STANDARD_JAVA = env;
	}

	private final ExternalModelSet theExtModels;
	private final ClassView theClassView;
	private final ErrorReporting theErrorReporting;
	private final Map<Object, Object> theProperties;

	InterpretedExpressoEnv(InterpretedModelSet models, ExternalModelSet extModels, ClassView classView,
		ClassMap<Set<NonStructuredParser>> nonStructuredParsers, UnaryOperatorSet unaryOperators, BinaryOperatorSet binaryOperators,
		ErrorReporting reporting, Map<Object, Object> properties) {
		super(models, nonStructuredParsers, unaryOperators, binaryOperators);
		theExtModels = extModels;
		theClassView = classView;
		theErrorReporting = reporting;
		theProperties = properties;
	}

	@Override
	protected CompiledExpressoEnv copy(ObservableModelSet models, ClassMap<Set<NonStructuredParser>> nonStructuredParsers,
		UnaryOperatorSet unaryOperators, BinaryOperatorSet binaryOperators) {
		if (models != null && !(models instanceof InterpretedModelSet))
			return super.copy(models, nonStructuredParsers, unaryOperators, binaryOperators);
		return new InterpretedExpressoEnv((InterpretedModelSet) models, theExtModels, theClassView, nonStructuredParsers, unaryOperators,
			binaryOperators, theErrorReporting, theProperties);
	}

	public InterpretedExpressoEnv forChild(CompiledExpressoEnv child) {
		InterpretedExpressoEnv env = this;
		if (getModels() != null) {
			if (child.getModels() != null && !getModels().getIdentity().equals(child.getModels().getIdentity()))
				env = env.with(child.getBuiltModels().createInterpreted(this));
		} else if (child.getModels() != null)
			env = env.with(child.getBuiltModels().createInterpreted(this));
		env = env.withAllNonStructuredParsers(child);
		return env;
	}

	@Override
	public InterpretedModelSet getModels() {
		return (InterpretedModelSet) super.getModels();
	}

	public ExternalModelSet getExtModels() {
		return theExtModels;
	}

	/** @return The class view for expressions to obtain types with */
	public ClassView getClassView() {
		return theClassView;
	}

	/** @return The error reporting for expressions evaluated in this environment to use */
	public ErrorReporting reporting() {
		return theErrorReporting;
	}

	/**
	 * All {@link ObservableModelSet.ModelValueSynth}s for expressions parsed under this session should be
	 * {@link ObservableModelSet.ModelValueSynth#get(ModelSetInstance) satisfied} with a model set wrapped by this method if this element
	 * extends with-local-models.
	 *
	 * @param models The model instance
	 * @return The wrapped model instance containing data for this element's local models
	 * @throws ModelInstantiationException If the local model instantiation fails
	 */
	public ModelSetInstance wrapLocal(ModelSetInstance models) throws ModelInstantiationException {
		if (getModels().getIdentity() != models.getModel().getIdentity())
			return getModels().createInstance(models.getUntil()).withAll(models).build();
		else
			return models;
	}

	@Override
	public InterpretedExpressoEnv interpret(ExternalModelSet extModels, ClassView classView, ErrorReporting reporting,
		Function<InterpretedExpressoEnv, InterpretedExpressoEnv> configure) {
		return this;
	}

	public InterpretedExpressoEnv with(InterpretedModelSet models) {
		if (models == getModels())
			return this;
		return new InterpretedExpressoEnv(models, theExtModels, theClassView, getNonStructuredParsers(), getUnaryOperators(),
			getBinaryOperators(), reporting(), theProperties);
	}

	public InterpretedExpressoEnv with(ClassView classView) {
		if (classView == theClassView)
			return this;
		return new InterpretedExpressoEnv(getModels(), theExtModels, classView, getNonStructuredParsers(), getUnaryOperators(),
			getBinaryOperators(), reporting(), theProperties);
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

	public InterpretedExpressoEnv withExt(ExternalModelSet extModels) {
		if (extModels == theExtModels)
			return this;
		return new InterpretedExpressoEnv(getModels(), extModels, theClassView, getNonStructuredParsers(), getUnaryOperators(),
			getBinaryOperators(), reporting(), theProperties);
	}

	/**
	 * @param position The position at which to report errors for the new expresso environment
	 * @return The new environment
	 */
	public InterpretedExpressoEnv at(LocatedPositionedContent position) {
		ErrorReporting reporting = reporting().at(position);
		if (reporting == reporting())
			return this;
		return new InterpretedExpressoEnv(getModels(), theExtModels, theClassView, getNonStructuredParsers(), getUnaryOperators(),
			getBinaryOperators(), reporting, theProperties);
	}

	/**
	 * @param positionOffset The relative position offset at which to report errors for the new expresso environment
	 * @return The new environment
	 */
	public InterpretedExpressoEnv at(int positionOffset) {
		ErrorReporting reporting = reporting().at(positionOffset);
		if (reporting == reporting())
			return this;
		return new InterpretedExpressoEnv(getModels(), theExtModels, theClassView, getNonStructuredParsers(), getUnaryOperators(),
			getBinaryOperators(), reporting, theProperties);
	}

	public <T> T getProperty(Object key, Class<T> type) {
		Object value = theProperties.get(key);
		if (value == null || type == null)
			return (T) value;
		return type.cast(value);
	}

	public InterpretedExpressoEnv setProperty(Object key, Object value) {
		theProperties.put(key, value);
		return this;
	}

	@Override
	public String toString() {
		return new StringBuilder().append("models=").append(getModels()).append(", cv=" + theClassView).toString();
	}
}
