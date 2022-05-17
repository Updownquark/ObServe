package org.observe.expresso;

import java.text.ParseException;

import org.observe.ObservableValue;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

public interface NonStructuredParser {
	default boolean canParse(TypeToken<?> type, String text) {
		return true;
	}

	<T> ObservableValue<? extends T> parse(TypeToken<T> type, String text) throws ParseException;

	public interface Simple extends NonStructuredParser {
		@Override
		default <T> ObservableValue<? extends T> parse(TypeToken<T> type, String text) throws ParseException {
			T value = (T) parseValue(type, text);
			if (value != null && !TypeTokens.get().isInstance(type, value))
				throw new IllegalStateException("Parser " + this + " parsed a value of type " + value.getClass() + " for type " + type);
			return ObservableValue.of(type, value);
		}

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
