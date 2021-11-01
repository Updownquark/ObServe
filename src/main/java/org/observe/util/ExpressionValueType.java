package org.observe.util;

import org.observe.expresso.Expression.ExpressoParseException;
import org.observe.expresso.ExpressoParser;
import org.observe.expresso.ObservableExpression;
import org.qommons.config.CustomValueType;
import org.qommons.config.QonfigParseSession;
import org.qommons.config.QonfigToolkit;

public class ExpressionValueType implements CustomValueType {
	private final String theName;
	private final ExpressoParser theParser;

	public ExpressionValueType(String name, ExpressoParser parser) {
		theName = name;
		theParser = parser;
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public Object parse(String value, QonfigToolkit tk, QonfigParseSession session) {
		try {
			return theParser.parse(value);
		} catch (ExpressoParseException e) {
			session.withError(e.getMessage(), e);
			return null;
		}
	}

	@Override
	public boolean isInstance(Object value) {
		return value instanceof ObservableExpression;
	}
}
