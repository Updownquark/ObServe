package org.observe.util.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public interface Shading {
	void shade(Graphics2D graphics, Dimension size, Color background);
}
