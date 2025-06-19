package org.observe.quick.swing;

import java.awt.Color;

public class ColoredRectangle {
	public enum AnchorEnd {
		Leading, Center, Trailing;
	}

	public int x;
	public AnchorEnd xAnchor;
	public int width;
	public int y;
	public AnchorEnd yAnchor;
	public int height;
	public Color background = Color.black;
	public Color borderColor = Color.black;
	public int borderThickness;
	public float rotation;

	public ColoredRectangle(int x, int y, int width, int height) {
		this.x = x;
		this.width = width;
		this.y = y;
		this.height = height;
		xAnchor = yAnchor = AnchorEnd.Leading;
	}

	public Integer getX(AnchorEnd end) {
		if (end != xAnchor)
			return null;
		return x;
	}

	public Integer getY(AnchorEnd end) {
		if (end != yAnchor)
			return null;
		return y;
	}

	public ColoredRectangle bg(Color newBG) {
		background = newBG;
		return this;
	}

	public ColoredRectangle border(int thickness, Color color) {
		borderThickness = thickness;
		borderColor = color;
		return this;
	}
}
