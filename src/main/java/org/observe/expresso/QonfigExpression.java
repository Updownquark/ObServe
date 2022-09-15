package org.observe.expresso;

/**
 * Represents the expression Qonfig type used by Expresso. This type doesn't do anything but hold the text of the expression for parsing at
 * a later stage, but it explicitly marks a value as being used in this way.
 */
public class QonfigExpression {
	/** The text of the expression */
	public final String text;

	/** @param text The text of the expression */
	public QonfigExpression(String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return text;
	}
}
