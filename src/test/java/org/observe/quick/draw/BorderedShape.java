package org.observe.quick.draw;

import java.awt.Color;

public interface BorderedShape extends ColoredShape {
	Color getBorderColor();

	BorderedShape setBorderColor(Color color);

	int getBorderThickness();

	BorderedShape setBorderThickness(int thickness);
}
