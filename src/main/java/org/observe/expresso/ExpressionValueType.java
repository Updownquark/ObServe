package org.observe.expresso;

import org.qommons.config.CustomValueType;
import org.qommons.config.ErrorReporting;
import org.qommons.config.QonfigToolkit;

/** A Qonfig {@link CustomValueType} for parsing expressions */
public class ExpressionValueType implements CustomValueType {
	/** The name of this type */
	public static final String NAME = "expression";

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public QonfigExpression parse(String value, QonfigToolkit tk, ErrorReporting session) {
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
