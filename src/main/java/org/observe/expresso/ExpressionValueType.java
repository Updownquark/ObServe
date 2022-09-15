package org.observe.expresso;

import org.qommons.config.CustomValueType;
import org.qommons.config.QonfigParseSession;
import org.qommons.config.QonfigToolkit;

public class ExpressionValueType implements CustomValueType {
	public static final String NAME = "expression";

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public QonfigExpression parse(String value, QonfigToolkit tk, QonfigParseSession session) {
		return new QonfigExpression(value);
	}

	@Override
	public boolean isInstance(Object value) {
		return value instanceof QonfigExpression;
	}

	@Override
	public String toString() {
		return NAME;
	}
}
