package org.observe.expresso;

import java.awt.Color;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.observe.expresso.ops.BinaryOperatorSet;
import org.observe.expresso.ops.UnaryOperatorSet;
import org.observe.util.TypeTokens;
import org.qommons.ClassMap;
import org.qommons.Colors;
import org.qommons.TimeUtils;

import com.google.common.reflect.TypeToken;

public class ExpressoEnv {
	private final ObservableModelSet theModels;
	private final ClassView theClassView;
	private final ClassMap<List<NonStructuredParser>> theNonStructuredParsers;
	private final UnaryOperatorSet theUnaryOperators;
	private final BinaryOperatorSet theBinaryOperators;

	public ExpressoEnv(ObservableModelSet models, ClassView classView, UnaryOperatorSet unaryOperators, BinaryOperatorSet binaryOperators) {
		this(models, classView, null, unaryOperators, binaryOperators);
	}

	ExpressoEnv(ObservableModelSet models, ClassView classView, ClassMap<List<NonStructuredParser>> nonStructuredParsers,
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
		theNonStructuredParsers.computeIfAbsent(type, () -> new ArrayList<>(3)).add(parser);
		return this;
	}

	public ExpressoEnv removeNonStructuredParser(Class<?> type, NonStructuredParser parser) {
		theNonStructuredParsers.getOrDefault(type, ClassMap.TypeMatch.EXACT, Collections.emptyList()).remove(parser);
		return this;
	}

	public List<NonStructuredParser> getNonStructuredParsers(Class<?> type) {
		return theNonStructuredParsers.getAll(type, null).stream().flatMap(List::stream).collect(Collectors.toList());
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
		return new ExpressoEnv(models == null ? theModels : models, //
			classView == null ? theClassView : classView, //
			theNonStructuredParsers, theUnaryOperators, theBinaryOperators);
	}

	private static <E extends Enum<E>> E parseEnum(TypeToken<?> type, String text) throws ParseException {
		try {
			return Enum.valueOf((Class<E>) TypeTokens.getRawType(type), text);
		} catch (IllegalArgumentException e) {
			throw new ParseException(e.getMessage(), 0);
		}
	}
}
