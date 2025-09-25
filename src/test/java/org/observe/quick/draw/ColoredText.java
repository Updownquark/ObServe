package org.observe.quick.draw;

public interface ColoredText extends PositionedShape {
	String getText();

	ColoredText setText(String text);

	int getFontSize();

	ColoredText setFontSize(int fontSize);
}
