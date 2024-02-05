package org.observe.quick.qwysiwyg;

import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

/** {@link LocatedPositionedContent} for a {@link LocatedAndExpression} */
public class LocatedAndLocation implements LocatedPositionedContent {
	private final LocatedPositionedContent theLeft;
	private final String theOperator;
	private final LocatedPositionedContent theRight;

	LocatedAndLocation(LocatedPositionedContent left, String operator, LocatedPositionedContent right) {
		theLeft = left;
		theOperator = operator;
		theRight = right;
	}

	@Override
	public int length() {
		return theLeft.length() + theOperator.length() + theRight.length();
	}

	@Override
	public char charAt(int index) {
		if (index < theLeft.length())
			return theLeft.charAt(index);
		else if (index < theLeft.length() + theOperator.length())
			return theOperator.charAt(index - theLeft.length());
		else
			return theRight.charAt(index - theLeft.length() - theOperator.length());
	}

	@Override
	public String getFileLocation() {
		return theLeft.getFileLocation();
	}

	@Override
	public LocatedFilePosition getPosition(int index) {
		if (index < theLeft.length())
			return theLeft.getPosition(index);
		else if (index < theLeft.length() + theOperator.length())
			return theRight.getPosition(0);
		else
			return theRight.getPosition(index);
	}

	@Override
	public int getSourceLength(int from, int to) {
		int leftLen = theLeft.length();
		if (from < leftLen) {
			if (to <= leftLen)
				return theLeft.getSourceLength(from, to);
			else if (to <= leftLen + theOperator.length())
				return theLeft.getSourceLength(from, theLeft.length());
			else
				return theLeft.getSourceLength(from, theLeft.length()) + theOperator.length()
				+ theRight.getSourceLength(0, to - leftLen - theOperator.length());
		} else if (from < theLeft.length() + theOperator.length()) {
			if (to < leftLen + theOperator.length())
				return 0;
			else
				return theRight.getSourceLength(from - leftLen - theOperator.length(), to - leftLen - theOperator.length());
		} else
			return theRight.getSourceLength(from - leftLen - theOperator.length(), to - leftLen - theOperator.length());
	}

	@Override
	public CharSequence getSourceContent(int from, int to) {
		int leftLen = theLeft.length();
		if (from < leftLen) {
			if (to <= leftLen)
				return theLeft.getSourceContent(from, to);
			else if (to <= leftLen + theOperator.length())
				return theLeft.getSourceContent(from, theLeft.length());
			else
				return theLeft.getSourceContent(from, theLeft.length()).toString() + theOperator
					+ theRight.getSourceContent(0, to - leftLen - theOperator.length());
		} else if (from < theLeft.length() + theOperator.length()) {
			if (to < leftLen + theOperator.length())
				return "";
			else
				return theRight.getSourceContent(from - leftLen - theOperator.length(), to - leftLen - theOperator.length());
		} else
			return theRight.getSourceContent(from - leftLen - theOperator.length(), to - leftLen - theOperator.length());
	}

	@Override
	public LocatedPositionedContent subSequence(int startIndex) {
		if (startIndex < theLeft.length())
			return new LocatedAndLocation(theLeft.subSequence(startIndex), theOperator, theRight);
		else if (startIndex < theLeft.length() + theOperator.length())
			return theRight;
		else
			return theRight.subSequence(startIndex - theLeft.length() - theOperator.length());
	}

	@Override
	public LocatedPositionedContent subSequence(int startIndex, int endIndex) {
		int leftLen = theLeft.length();
		if (startIndex < leftLen) {
			if (endIndex <= leftLen)
				return theLeft.subSequence(startIndex, endIndex);
			else if (endIndex <= leftLen + theOperator.length())
				return theLeft.subSequence(startIndex);
			else
				return new LocatedAndLocation(theLeft.subSequence(startIndex), theOperator,
					theRight.subSequence(0, endIndex - leftLen - theOperator.length()));
		} else if (startIndex < theLeft.length() + theOperator.length()) {
			if (endIndex < leftLen + theOperator.length())
				return theRight.subSequence(0, 0);
			else
				return theRight.subSequence(startIndex - leftLen - theOperator.length(), endIndex - leftLen - theOperator.length());
		} else
			return theRight.subSequence(startIndex - leftLen - theOperator.length(), endIndex - leftLen - theOperator.length());
	}

	@Override
	public String toString() {
		return theLeft.toString();
	}
}
