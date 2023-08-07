package org.observe.expresso;

import java.awt.Color;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.expresso.ObservableModelSet.ExternalModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ops.BinaryOperatorSet;
import org.observe.expresso.ops.ExternalLiteral;
import org.observe.expresso.ops.UnaryOperatorSet;
import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.ClassMap;
import org.qommons.Colors;
import org.qommons.TimeUtils;
import org.qommons.io.ErrorReporting;

import com.google.common.reflect.TypeToken;

public class CompiledExpressoEnv {
	public static final CompiledExpressoEnv STANDARD_JAVA = new CompiledExpressoEnv(//
		ObservableModelSet.build("StandardJava", ObservableModelSet.JAVA_NAME_CHECKER).build(), //
		UnaryOperatorSet.STANDARD_JAVA, BinaryOperatorSet.STANDARD_JAVA).withDefaultNonStructuredParsing();

	private final ObservableModelSet theModels;
	private final UnaryOperatorSet theUnaryOperators;
	private final BinaryOperatorSet theBinaryOperators;
	private final ClassMap<Set<NonStructuredParser>> theNonStructuredParsers;

	/**
	 * @param models The model set containing all values and sub-models available to expressions
	 * @param unaryOperators The set of unary operators available for expressions
	 * @param binaryOperators The set of binary operators available for expressions
	 * @param reporting The error reporting for this environment
	 */
	public CompiledExpressoEnv(ObservableModelSet models, UnaryOperatorSet unaryOperators, BinaryOperatorSet binaryOperators) {
		this(models, null, unaryOperators, binaryOperators);
	}

	protected CompiledExpressoEnv(ObservableModelSet models, ClassMap<Set<NonStructuredParser>> nonStructuredParsers,
		UnaryOperatorSet unaryOperators, BinaryOperatorSet binaryOperators) {
		theModels = models;
		theUnaryOperators = unaryOperators;
		theBinaryOperators = binaryOperators;
		theNonStructuredParsers = new ClassMap<>();
		if (nonStructuredParsers != null)
			theNonStructuredParsers.putAll(nonStructuredParsers);
	}

	protected CompiledExpressoEnv copy(ObservableModelSet models, ClassMap<Set<NonStructuredParser>> nonStructuredParsers,
		UnaryOperatorSet unaryOperators, BinaryOperatorSet binaryOperators) {
		return new CompiledExpressoEnv(models, nonStructuredParsers, unaryOperators, binaryOperators);
	}

	/** @return The model set containing all values and sub-models available to expressions */
	public ObservableModelSet getModels() {
		return theModels;
	}

	/** @return This environment's {@link #getModels() model}, built if it is a {@link ObservableModelSet.Builder} */
	public ObservableModelSet.Built getBuiltModels() {
		return buildModel(theModels);
	}

	protected ClassMap<Set<NonStructuredParser>> getNonStructuredParsers() {
		return theNonStructuredParsers;
	}

	/** @return The set of unary operators available for expressions */
	public UnaryOperatorSet getUnaryOperators() {
		return theUnaryOperators;
	}

	/** @return The set of binary operators available for expressions */
	public BinaryOperatorSet getBinaryOperators() {
		return theBinaryOperators;
	}

	private static ObservableModelSet.Built buildModel(ObservableModelSet model) {
		if (model == null)
			throw new IllegalStateException("No models set");
		else if (model instanceof ObservableModelSet.Built)
			return (ObservableModelSet.Built) model;
		else if (model instanceof ObservableModelSet.Builder)
			return ((ObservableModelSet.Builder) model).build();
		else
			throw new IllegalStateException("Models is a " + model.getClass().getName() + ", not either built or a builder");
	}

	public InterpretedExpressoEnv interpret(ExternalModelSet extModels, ClassView classView, ErrorReporting reporting,
		Function<InterpretedExpressoEnv, InterpretedExpressoEnv> configure) {
		if (extModels == null)
			extModels = ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER).build();

		InterpretedExpressoEnv interpreted = new InterpretedExpressoEnv(null, extModels, classView, getNonStructuredParsers(),
			getUnaryOperators(), getBinaryOperators(), reporting, new HashMap<>(), false);
		if (configure != null)
			interpreted = configure.apply(interpreted);

		InterpretedModelSet interpretedModels = getBuiltModels().createInterpreted(interpreted);
		return interpreted.with(interpretedModels);
	}

	public CompiledExpressoEnv with(ObservableModelSet models) {
		if (models == theModels)
			return this;
		else
			return copy(models, theNonStructuredParsers, theUnaryOperators, theBinaryOperators);
	}

	/**
	 * @param unaryOps The unary operator set to use (or null to keep this environment's)
	 * @param binaryOps The binary operator set to use (or null to keep this environment's)
	 * @return A new environment with the given unary and binary operator sets
	 */
	public CompiledExpressoEnv withOperators(UnaryOperatorSet unaryOps, BinaryOperatorSet binaryOps) {
		if ((unaryOps == null || theUnaryOperators == unaryOps) && (binaryOps == null || theBinaryOperators == binaryOps))
			return this;
		return copy(theModels, theNonStructuredParsers, //
			unaryOps == null ? theUnaryOperators : unaryOps, //
				binaryOps == null ? theBinaryOperators : binaryOps);
	}

	/**
	 * @param type The type to query the parser for
	 * @param parser The non-structured parser to use to evaluate {@link ExternalLiteral} expressions
	 * @return This environment
	 */
	public CompiledExpressoEnv withNonStructuredParser(Class<?> type, NonStructuredParser parser) {
		Set<NonStructuredParser> parsers = theNonStructuredParsers.get(type, ClassMap.TypeMatch.EXACT);
		if (parsers != null && parsers.contains(parser))
			return this;
		ClassMap<Set<NonStructuredParser>> nspCopy = nspCopy();
		nspCopy.computeIfAbsent(type, () -> new LinkedHashSet<>()).add(parser);
		return copy(theModels, nspCopy, theUnaryOperators, theBinaryOperators);
	}

	ClassMap<Set<NonStructuredParser>> nspCopy() {
		ClassMap<Set<NonStructuredParser>> nspCopy = new ClassMap<>();
		for (BiTuple<Class<?>, Set<NonStructuredParser>> entry : theNonStructuredParsers.getAllEntries())
			nspCopy.put(entry.getValue1(), new LinkedHashSet<>(entry.getValue2()));
		return nspCopy;
	}

	/**
	 * Removes a parser registered with {@link #withNonStructuredParser(Class, NonStructuredParser)} to parse {@link ExternalLiteral}
	 * expressions
	 *
	 * @param type The type that the non-structured parser is registered for
	 * @param parser The non-structured parser to remove
	 * @return This environment
	 */
	public CompiledExpressoEnv removeNonStructuredParser(Class<?> type, NonStructuredParser parser) {
		Set<NonStructuredParser> parsers = theNonStructuredParsers.get(type, ClassMap.TypeMatch.EXACT);
		if (parsers == null || !parsers.contains(parser))
			return this;
		ClassMap<Set<NonStructuredParser>> nspCopy = nspCopy();
		nspCopy.get(type, ClassMap.TypeMatch.EXACT).remove(parser);
		return copy(theModels, nspCopy, theUnaryOperators, theBinaryOperators);
	}

	/**
	 * @param type The type to parse
	 * @return All non-structured parsers that may be able to parse a value of the given type
	 */
	public Set<NonStructuredParser> getNonStructuredParsers(Class<?> type) {
		return theNonStructuredParsers.getAll(type, null).stream().flatMap(Set::stream).collect(Collectors.toSet());
	}

	public CompiledExpressoEnv withAllNonStructuredParsers(CompiledExpressoEnv env) {
		ClassMap<Set<NonStructuredParser>> nspCopy = null;
		for (BiTuple<Class<?>, Set<NonStructuredParser>> entry : env.theNonStructuredParsers.getAllEntries()) {
			if (nspCopy == null && theNonStructuredParsers.getOrDefault(entry.getValue1(), ClassMap.TypeMatch.EXACT, Collections.emptySet())
				.containsAll(entry.getValue2()))
				continue;
			if (nspCopy == null)
				nspCopy = nspCopy();
			Set<NonStructuredParser> forType = nspCopy.get(entry.getValue1(), ClassMap.TypeMatch.EXACT);
			if (forType == null) {
				forType = new LinkedHashSet<>();
				nspCopy.put(entry.getValue1(), forType);
			}
			forType.addAll(entry.getValue2());
		}
		if (nspCopy == null)
			return this;
		return copy(theModels, nspCopy, theUnaryOperators, theBinaryOperators);
	}

	/**
	 * Registers some simple default non-structured parsers for utility
	 *
	 * @return This environment
	 */
	public CompiledExpressoEnv withDefaultNonStructuredParsing() {
		return withNonStructuredParser(String.class, NonStructuredParser.simple((t, s) -> s, "Simple String literal"))//
			.withNonStructuredParser(Duration.class,
				NonStructuredParser.simple((t, s) -> TimeUtils.parseDuration(s), "Simple duration literal"))//
			.withNonStructuredParser(Instant.class,
				NonStructuredParser.simple((t, s) -> TimeUtils.parseInstant(s, true, true, null).evaluate(Instant::now),
					"Simple date/time literal"))//
			.withNonStructuredParser(Enum.class, NonStructuredParser.simple((t, s) -> parseEnum(t, s), "Enum literal"))//
			.withNonStructuredParser(Color.class, NonStructuredParser.simple((t, s) -> Colors.parseColor(s), "Color literal"));
	}

	/** @return A new environment with null model and class view */
	public CompiledExpressoEnv clearModels() {
		return copy(null, theNonStructuredParsers, theUnaryOperators, theBinaryOperators);
	}

	public CompiledExpressoEnv copy() {
		return copy(theModels, theNonStructuredParsers.copy(), theUnaryOperators, theBinaryOperators);
	}

	@Override
	public String toString() {
		return new StringBuilder().append("models=").append(theModels).toString();
	}

	private static <E extends Enum<E>> E parseEnum(TypeToken<?> type, String text) throws ParseException {
		try {
			return Enum.valueOf((Class<E>) TypeTokens.getRawType(type), text);
		} catch (IllegalArgumentException e) {
			throw new ParseException(e.getMessage(), 0);
		}
	}
}
