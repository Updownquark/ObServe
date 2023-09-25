package org.observe.expresso;

import java.text.ParseException;
import java.util.function.Predicate;

import org.observe.SettableValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.util.TypeTokens;
import org.qommons.SelfDescribed;
import org.qommons.ex.ExBiFunction;

import com.google.common.reflect.TypeToken;

/** A parser to interpret 'external literal' values (by default, enclosed within grave accents, `like this`) */
public interface NonStructuredParser extends SelfDescribed {
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
	<T> InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<? extends T>> parse(TypeToken<T> type, String text)
		throws ParseException;

	/**
	 * A simple parser that produces constant values
	 *
	 * @param <T> The type of values this parser can parse
	 */
	public abstract class Simple<T> implements NonStructuredParser {
		private final TypeToken<T> theType;

		/** @param type The type of values this parser may be able to parse */
		protected Simple(TypeToken<T> type) {
			theType = type;
		}

		@Override
		public boolean canParse(TypeToken<?> type, String text) {
			if (theType == null)
				return checkText(text);
			// If this parser's type is asked for specifically, use it. If they're asking for a super-type, check the text.
			if (theType.equals(type))
				return true;
			else if (TypeTokens.get().isAssignable(type, theType))
				return checkText(text);
			else
				return false;
		}

		public abstract boolean checkText(String text);

		@Override
		public <T2> InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<? extends T2>> parse(TypeToken<T2> type, String text)
			throws ParseException {
			if (theType != null && !TypeTokens.get().isAssignable(type, theType))
				throw new IllegalArgumentException("This literal parser can only parse " + theType + ", not " + type);
			T2 value = (T2) parseValue((TypeToken<? extends T>) type, text);
			if (value != null && !TypeTokens.get().isInstance(type, value))
				throw new IllegalStateException("Parser " + this + " parsed a value of type " + value.getClass() + " for type " + type);
			return InterpretedValueSynth.literal(ModelTypes.Value.forType(type), SettableValue.of(type, value, "Literal"), text);
		}

		/**
		 * @param type The type to parse the expression as
		 * @param text The text of the expression to parse
		 * @return The value of the expression
		 * @throws ParseException If the expression could not be parsed
		 */
		protected abstract <T2 extends T> T2 parseValue(TypeToken<T2> type, String text) throws ParseException;
	}

	/**
	 * Creates a simple parser from a function
	 *
	 * @param textChecker The checker to check text to see if it matches the parser's capability
	 * @param parser The parser
	 * @param description The description for the parser
	 * @return The parser
	 */
	static <T> Simple<T> simple(Predicate<String> textChecker, ExBiFunction<TypeToken<? extends T>, String, T, ParseException> parser,
		String description) {
		return simple(null, textChecker, parser, description);
	}

	/**
	 * Creates a simple parser from a function
	 *
	 * @param <T> The type of values to parser
	 * @param type The type of values to parse
	 * @param textChecker The checker to check text to see if it matches the parser's capability
	 * @param parser The parser
	 * @param description The description for the parser
	 * @return The parser
	 */
	static <T> Simple<T> simple(TypeToken<T> type, Predicate<String> textChecker,
		ExBiFunction<TypeToken<? extends T>, String, T, ParseException> parser, String description) {
		return new Simple<T>(null) {
			@Override
			public boolean checkText(String text) {
				return textChecker.test(text);
			}

			@Override
			protected <T2 extends T> T2 parseValue(TypeToken<T2> type2, String text) throws ParseException {
				return (T2) parser.apply(type2, text);
			}

			@Override
			public String getDescription() {
				return description;
			}

			@Override
			public String toString() {
				return description;
			}
		};
	}
}
