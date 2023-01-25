package org.observe.expresso;

/** An ExpressoParser is a parser that interprets text into {@link ObservableExpression} objects */
public interface ExpressoParser {
	/**
	 * @param text The text to interpret
	 * @return The {@link ObservableExpression} represented by the text
	 * @throws ExpressoParseException If the expression cannot be parsed
	 */
	ObservableExpression parse(String text) throws ExpressoParseException;
}
