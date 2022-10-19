package org.observe.expresso;

import java.awt.Color;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.observe.expresso.ops.BinaryOperatorSet;
import org.observe.expresso.ops.UnaryOperatorSet;
import org.observe.util.TypeTokens;
import org.qommons.ClassMap;
import org.qommons.Colors;
import org.qommons.TimeUtils;

import com.google.common.reflect.TypeToken;

public class ExpressoEnv {
	public static final ExpressoEnv STANDARD_JAVA = new ExpressoEnv(ObservableModelSet.build(ObservableModelSet.JAVA_NAME_CHECKER),
		ClassView.build().withWildcardImport("java.lang.*").build(), null, UnaryOperatorSet.STANDARD_JAVA,
		BinaryOperatorSet.STANDARD_JAVA)//
		.withDefaultNonStructuredParsing();

	private final ObservableModelSet theModels;
	private final ClassView theClassView;
	private final ClassMap<Set<NonStructuredParser>> theNonStructuredParsers;
	private final UnaryOperatorSet theUnaryOperators;
	private final BinaryOperatorSet theBinaryOperators;

	public ExpressoEnv(ObservableModelSet models, ClassView classView, UnaryOperatorSet unaryOperators, BinaryOperatorSet binaryOperators) {
		this(models, classView, null, unaryOperators, binaryOperators);
	}

	ExpressoEnv(ObservableModelSet models, ClassView classView, ClassMap<Set<NonStructuredParser>> nonStructuredParsers,
		UnaryOperatorSet unaryOperators, BinaryOperatorSet binaryOperators) {
		theModels = models;
		theClassView = classView;
		theNonStructuredParsers = new ClassMap<>();
		if (nonStructuredParsers != null)
			theNonStructuredParsers.putAll(nonStructuredParsers);
		theUnaryOperators = unaryOperators;
		theBinaryOperators = binaryOperators;
	}

	public ObservableModelSet getModels() {
		return theModels;
	}

	public ClassView getClassView() {
		return theClassView;
	}

	public UnaryOperatorSet getUnaryOperators() {
		return theUnaryOperators;
	}

	public BinaryOperatorSet getBinaryOperators() {
		return theBinaryOperators;
	}

	public ExpressoEnv withNonStructuredParser(Class<?> type, NonStructuredParser parser) {
		theNonStructuredParsers.computeIfAbsent(type, () -> new LinkedHashSet<>()).add(parser);
		return this;
	}

	public ExpressoEnv removeNonStructuredParser(Class<?> type, NonStructuredParser parser) {
		theNonStructuredParsers.getOrDefault(type, ClassMap.TypeMatch.EXACT, Collections.emptySet()).remove(parser);
		return this;
	}

	public Set<NonStructuredParser> getNonStructuredParsers(Class<?> type) {
		return theNonStructuredParsers.getAll(type, null).stream().flatMap(Set::stream).collect(Collectors.toSet());
	}

	public ExpressoEnv withDefaultNonStructuredParsing() {
		withNonStructuredParser(String.class, NonStructuredParser.simple((t, s) -> s));
		withNonStructuredParser(Duration.class, NonStructuredParser.simple((t, s) -> TimeUtils.parseDuration(s)));
		withNonStructuredParser(Instant.class,
			NonStructuredParser.simple((t, s) -> TimeUtils.parseInstant(s, true, true, null).evaluate(Instant::now)));
		withNonStructuredParser(Enum.class, NonStructuredParser.simple((t, s) -> parseEnum(t, s)));
		withNonStructuredParser(Color.class, NonStructuredParser.simple((t, s) -> Colors.parseColor(s)));
		return this;
	}

	public ExpressoEnv with(ObservableModelSet models, ClassView classView) {
		if ((models == null || theModels == models) && (classView == null || theClassView == classView))
			return this;
		return new ExpressoEnv(models == null ? theModels : models, //
			classView == null ? theClassView : classView, //
				theNonStructuredParsers, theUnaryOperators, theBinaryOperators);
	}

	public ExpressoEnv withOperators(UnaryOperatorSet unaryOps, BinaryOperatorSet binaryOps) {
		if ((unaryOps == null || theUnaryOperators == unaryOps) && (binaryOps == null || theBinaryOperators == binaryOps))
			return this;
		return new ExpressoEnv(theModels, theClassView, theNonStructuredParsers, //
			unaryOps == null ? theUnaryOperators : unaryOps, //
				binaryOps == null ? theBinaryOperators : binaryOps);
	}

	public ExpressoEnv clearModels() {
		return new ExpressoEnv(null, null, theNonStructuredParsers, theUnaryOperators, theBinaryOperators);
	}

	public ExpressoEnv copy() {
		return new ExpressoEnv(theModels, theClassView, //
			theNonStructuredParsers == null ? null : theNonStructuredParsers.copy(), //
				theUnaryOperators, theBinaryOperators);
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
