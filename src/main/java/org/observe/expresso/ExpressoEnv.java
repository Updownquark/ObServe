package org.observe.expresso;

import java.awt.Color;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ops.BinaryOperatorSet;
import org.observe.expresso.ops.ExternalLiteral;
import org.observe.expresso.ops.UnaryOperatorSet;
import org.observe.util.TypeTokens;
import org.qommons.ClassMap;
import org.qommons.Colors;
import org.qommons.TimeUtils;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedContentPosition;

import com.google.common.reflect.TypeToken;

/** An environment in which to evaluate {@link ObservableExpression}s */
public class ExpressoEnv {
	/**
	 * Standard java environment, including imported java.lang.* and standard java operators, plus some
	 * {@link #withDefaultNonStructuredParsing() default} non-structured parsing
	 */
	public static final ExpressoEnv STANDARD_JAVA = new ExpressoEnv(
		ObservableModelSet.build("StandardJava", ObservableModelSet.JAVA_NAME_CHECKER),
		ClassView.build().withWildcardImport("java.lang").build(), null, UnaryOperatorSet.STANDARD_JAVA, BinaryOperatorSet.STANDARD_JAVA,
		new ErrorReporting.Default(null))//
		.withDefaultNonStructuredParsing();

	private final ObservableModelSet theModels;
	private final ClassView theClassView;
	private final ClassMap<Set<NonStructuredParser>> theNonStructuredParsers;
	private final UnaryOperatorSet theUnaryOperators;
	private final BinaryOperatorSet theBinaryOperators;
	private final ErrorReporting theErrorReporting;

	private ObservableModelSet theElementModel;

	/**
	 * @param models The model set containing all values and sub-models available to expressions
	 * @param classView The class view for expressions to obtain types with
	 * @param unaryOperators The set of unary operators available for expressions
	 * @param binaryOperators The set of binary operators available for expressions
	 * @param reporting The error reporting for this environment
	 */
	public ExpressoEnv(ObservableModelSet models, ClassView classView, UnaryOperatorSet unaryOperators, BinaryOperatorSet binaryOperators,
		ErrorReporting reporting) {
		this(models, classView, null, unaryOperators, binaryOperators, reporting);
	}

	ExpressoEnv(ObservableModelSet models, ClassView classView, ClassMap<Set<NonStructuredParser>> nonStructuredParsers,
		UnaryOperatorSet unaryOperators, BinaryOperatorSet binaryOperators, ErrorReporting reporting) {
		theModels = models;
		theClassView = classView;
		theNonStructuredParsers = new ClassMap<>();
		if (nonStructuredParsers != null)
			theNonStructuredParsers.putAll(nonStructuredParsers);
		theUnaryOperators = unaryOperators;
		theBinaryOperators = binaryOperators;
		theErrorReporting = reporting;
	}

	/** @return The model set containing all values and sub-models available to expressions */
	public ObservableModelSet getModels() {
		return theModels;
	}

	public ObservableModelSet.Built getBuiltModels() {
		if (theModels instanceof ObservableModelSet.Built)
			return (ObservableModelSet.Built) theModels;
		else if (theModels instanceof ObservableModelSet.Builder)
			return ((ObservableModelSet.Builder) theModels).build();
		else
			throw new IllegalStateException("Models is a " + theModels.getClass().getName() + ", not either built or a builder");
	}

	/** @return The class view for expressions to obtain types with */
	public ClassView getClassView() {
		return theClassView;
	}

	/** @return The set of unary operators available for expressions */
	public UnaryOperatorSet getUnaryOperators() {
		return theUnaryOperators;
	}

	/** @return The set of binary operators available for expressions */
	public BinaryOperatorSet getBinaryOperators() {
		return theBinaryOperators;
	}

	public ErrorReporting reporting() {
		return theErrorReporting;
	}

	public void saveLocalModel() {
		theElementModel = theModels;
	}

	/** @throws ExpressoInterpretationException If the local model could not be interpreted */
	public void interpretLocalModel() throws ExpressoInterpretationException {
		ObservableModelSet elementModel = theElementModel;
		ObservableModelSet.Built built;
		if (elementModel instanceof ObservableModelSet.Built)
			built = (ObservableModelSet.Built) elementModel;
		else if (elementModel instanceof ObservableModelSet.Builder)
			built = ((ObservableModelSet.Builder) elementModel).build();
		else if (elementModel == null)
			built = null;
		else
			throw new IllegalStateException("Local model is " + elementModel.getClass().getName() + ", not either built or a builder");
		if (built != null)
			theElementModel = built.interpret();
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
		ObservableModelSet elementModel = theElementModel;
		ObservableModelSet.Built built;
		if (elementModel instanceof ObservableModelSet.Built)
			built = (ObservableModelSet.Built) elementModel;
		else if (elementModel instanceof ObservableModelSet.Builder)
			built = ((ObservableModelSet.Builder) elementModel).build();
		else if (elementModel == null)
			built = null;
		else
			throw new IllegalStateException("Local model is " + elementModel.getClass().getName() + ", not either built or a builder");
		if (built != null && !models.getModel().isRelated(built.getIdentity())) {
			if (!(built instanceof ObservableModelSet.InterpretedModelSet))
				throw new ModelInstantiationException("Local element model was not interpreted.  Should have called interpretLocalModel()",
					theErrorReporting.getFileLocation().getPosition(0), 0);
			models = ((ObservableModelSet.InterpretedModelSet) built).createInstance(models.getUntil()).withAll(models).build();
		}
		return models;
	}

	/**
	 * @param type The type to query the parser for
	 * @param parser The non-structured parser to use to evaluate {@link ExternalLiteral} expressions
	 * @return This environment
	 */
	public ExpressoEnv withNonStructuredParser(Class<?> type, NonStructuredParser parser) {
		theNonStructuredParsers.computeIfAbsent(type, () -> new LinkedHashSet<>()).add(parser);
		return this;
	}

	/**
	 * Removes a parser registered with {@link #withNonStructuredParser(Class, NonStructuredParser)} to parse {@link ExternalLiteral}
	 * expressions
	 *
	 * @param type The type that the non-structured parser is registered for
	 * @param parser The non-structured parser to remove
	 * @return This environment
	 */
	public ExpressoEnv removeNonStructuredParser(Class<?> type, NonStructuredParser parser) {
		theNonStructuredParsers.getOrDefault(type, ClassMap.TypeMatch.EXACT, Collections.emptySet()).remove(parser);
		return this;
	}

	/**
	 * @param type The type to parse
	 * @return All non-structured parsers that may be able to parse a value of the given type
	 */
	public Set<NonStructuredParser> getNonStructuredParsers(Class<?> type) {
		return theNonStructuredParsers.getAll(type, null).stream().flatMap(Set::stream).collect(Collectors.toSet());
	}

	/**
	 * Registers some simple default non-structured parsers for utility
	 *
	 * @return This environment
	 */
	public ExpressoEnv withDefaultNonStructuredParsing() {
		withNonStructuredParser(String.class, NonStructuredParser.simple((t, s) -> s));
		withNonStructuredParser(Duration.class, NonStructuredParser.simple((t, s) -> TimeUtils.parseDuration(s)));
		withNonStructuredParser(Instant.class,
			NonStructuredParser.simple((t, s) -> TimeUtils.parseInstant(s, true, true, null).evaluate(Instant::now)));
		withNonStructuredParser(Enum.class, NonStructuredParser.simple((t, s) -> parseEnum(t, s)));
		withNonStructuredParser(Color.class, NonStructuredParser.simple((t, s) -> Colors.parseColor(s)));
		return this;
	}

	/**
	 * @param models The model set to use (or null to keep this environment's)
	 * @param classView The class view to use (or null to keep this environment's)
	 * @return A new environment with the given model set and class view
	 */
	public ExpressoEnv with(ObservableModelSet models, ClassView classView) {
		if ((models == null || theModels == models) && (classView == null || theClassView == classView))
			return this;
		return new ExpressoEnv(models == null ? theModels : models, //
			classView == null ? theClassView : classView, //
				theNonStructuredParsers, theUnaryOperators, theBinaryOperators, theErrorReporting);
	}

	/**
	 * @param unaryOps The unary operator set to use (or null to keep this environment's)
	 * @param binaryOps The binary operator set to use (or null to keep this environment's)
	 * @return A new environment with the given unary and binary operator sets
	 */
	public ExpressoEnv withOperators(UnaryOperatorSet unaryOps, BinaryOperatorSet binaryOps) {
		if ((unaryOps == null || theUnaryOperators == unaryOps) && (binaryOps == null || theBinaryOperators == binaryOps))
			return this;
		return new ExpressoEnv(theModels, theClassView, theNonStructuredParsers, //
			unaryOps == null ? theUnaryOperators : unaryOps, //
				binaryOps == null ? theBinaryOperators : binaryOps, theErrorReporting);
	}

	public ExpressoEnv at(LocatedContentPosition position) {
		return new ExpressoEnv(theModels, theClassView, theNonStructuredParsers, theUnaryOperators, theBinaryOperators,
			theErrorReporting.at(position));
	}

	public ExpressoEnv at(int positionOffset) {
		return new ExpressoEnv(theModels, theClassView, theNonStructuredParsers, theUnaryOperators, theBinaryOperators,
			theErrorReporting.at(positionOffset));
	}

	/** @return A new environment with null model and class view */
	public ExpressoEnv clearModels() {
		return new ExpressoEnv(null, null, theNonStructuredParsers, theUnaryOperators, theBinaryOperators, theErrorReporting);
	}

	/** @return An independent copy of this environment */
	public ExpressoEnv copy() {
		return new ExpressoEnv(theModels, theClassView, //
			theNonStructuredParsers == null ? null : theNonStructuredParsers.copy(), //
				theUnaryOperators, theBinaryOperators, theErrorReporting);
	}

	@Override
	public String toString() {
		return new StringBuilder().append("models=").append(theModels).append(", cv=" + theClassView).toString();
	}

	private static <E extends Enum<E>> E parseEnum(TypeToken<?> type, String text) throws ParseException {
		try {
			return Enum.valueOf((Class<E>) TypeTokens.getRawType(type), text);
		} catch (IllegalArgumentException e) {
			throw new ParseException(e.getMessage(), 0);
		}
	}
}
