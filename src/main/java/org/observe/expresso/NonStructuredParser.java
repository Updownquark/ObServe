package org.observe.expresso;

import java.text.ParseException;

import org.observe.ObservableValue;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

/** A parser to interpret 'external literal' values (by default, enclosed within grave accents, `like this`) */
public interface NonStructuredParser {
	/**
	 * @param type The type to parse the expression as
	 * @param text The text of the expression
	 * @return Whether this parser supports parsing the given expression as the given type
	 */
	default boolean canParse(TypeToken<?> type, String text) {
		return true;
	}

	/**
	 * @param <T> The type to parse the expression as
	 * @param type The type to parse the expression as
	 * @param text The text of the expression to parse
	 * @return The value of the expression
	 * @throws ParseException If the expression could not be parsed
	 */
	<T> ObservableValue<? extends T> parse(TypeToken<T> type, String text) throws ParseException;

	/** A simple parser that produces constant values */
	public interface Simple extends NonStructuredParser {
		@Override
		default <T> ObservableValue<? extends T> parse(TypeToken<T> type, String text) throws ParseException {
			T value = (T) parseValue(type, text);
			if (value != null && !TypeTokens.get().isInstance(type, value))
				throw new IllegalStateException("Parser " + this + " parsed a value of type " + value.getClass() + " for type " + type);
			return ObservableValue.of(type, value);
		}

		/**
		 * @param type The type to parse the expression as
		 * @param text The text of the expression to parse
		 * @return The value of the expression
		 * @throws ParseException If the expression could not be parsed
		 */
		Object parseValue(TypeToken<?> type, String text) throws ParseException;
	}

	/**
	 * Just a utility to force the compiler to recognize a parser-typed lambda
	 *
	 * @param parser The parser
	 * @return The parser
	 */
	static Simple simple(Simple parser) {
		return parser;
	}
}
