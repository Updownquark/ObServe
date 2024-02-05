package org.observe.quick.qwysiwyg;

import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ops.BinaryOperator;
import org.observe.expresso.ops.BufferedExpression;
import org.observe.expresso.qonfig.LocatedExpression;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

/** A LocatedExpression that is the AND binary operator of two other located expressions */
public class LocatedAndExpression implements LocatedExpression {
	private final LocatedExpression theLeft;
	private final LocatedExpression theRight;
	private final BinaryOperator theExpression;

	/**
	 * @param left The left operand
	 * @param right The right operand
	 */
	public LocatedAndExpression(LocatedExpression left, LocatedExpression right) {
		theLeft = left;
		theRight = right;
		theExpression = new BinaryOperator("&&", //
			BufferedExpression.buffer(0, left.getExpression(), 1), //
			BufferedExpression.buffer(1, right.getExpression(), 0));
	}

	@Override
	public ObservableExpression getExpression() {
		return theExpression;
	}

	@Override
	public int length() {
		return theExpression.getExpressionLength();
	}

	@Override
	public LocatedPositionedContent getFilePosition() {
		return new LocatedAndLocation(theLeft.getFilePosition(), " && ", theRight.getFilePosition());
	}

	@Override
	public LocatedFilePosition getFilePosition(int offset) {
		if (offset < theLeft.length())
			return theLeft.getFilePosition(offset);
		else if (offset < theLeft.length() + 4)
			return new LocatedFilePosition("StyleApplicationDef.java", 0, 0, 0);
		else
			return theRight.getFilePosition(offset - theLeft.length() - 4);
	}

	@Override
	public String toString() {
		return theLeft + " && " + theRight;
	}
}
