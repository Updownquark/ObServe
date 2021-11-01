package org.observe.expresso;

import org.observe.expresso.Expression.ExpressoParseException;

public interface ExpressoParser {
	ObservableExpression parse(String text) throws ExpressoParseException;
}
