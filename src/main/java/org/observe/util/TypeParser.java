package org.observe.util;

import java.text.ParseException;

import org.observe.util.TypeTokens.TypeRetriever;

import com.google.common.reflect.TypeToken;

/** Parses {@link TypeToken}s from strings */
public interface TypeParser {
	/**
	 * Parses a type from a string
	 *
	 * @param text The string to parse
	 * @return The parsed type
	 * @throws ParseException If the string cannot be parsed as a type
	 */
	TypeToken<?> parseType(CharSequence text) throws ParseException;

	/**
	 * @param typeRetriever The type retriever to retrieve types by name (for {@link #parseType(CharSequence)}
	 * @return This instance
	 */
	TypeParser addTypeRetriever(TypeRetriever typeRetriever);

	/**
	 * @param typeRetriever The type retriever (added with {@link #addTypeRetriever(TypeRetriever)}) to remove
	 * @return Whether the type retriever was found in the list
	 */
	boolean removeTypeRetriever(TypeRetriever typeRetriever);
}