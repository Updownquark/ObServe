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
	private final boolean isExplicit;

	public ExpressionValueType(String name, ExpressoParser parser, boolean explicit) {
		theName = name;
		theParser = parser;
		isExplicit = explicit;
	}

	@Override
	public String getName() {
		return theName;
	}

	public boolean isExplicit() {
		return isExplicit;
	}

	@Override
	public Object parse(String value, QonfigToolkit tk, QonfigParseSession session) {
		if (isExplicit) {
			if (value.startsWith("${") && value.endsWith("}")) {
				value = value.substring(2, value.length() - 1);
			} else
				return new ObservableExpression.LiteralExpression<>(org.observe.expresso.Expression.create("literal", value), value);
		}
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
