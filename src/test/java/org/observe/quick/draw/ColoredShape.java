package org.observe.quick.draw;

import java.awt.Color;
import java.beans.Transient;

import org.qommons.Nameable;

public interface ColoredShape extends Nameable {
	class ShapeBounds {
		public final int centerX;
		public final int centerY;
		public final int maxDimension;

		public ShapeBounds(int centerX, int centerY, int maxDimension) {
			this.centerX = centerX;
			this.centerY = centerY;
			this.maxDimension = maxDimension;
		}

		@Override
		public String toString() {
			return "[" + centerX + ", " + centerY + "]x" + maxDimension;
		}
	}

	ShapeType getType();

	boolean isVisible();

	ColoredShape setVisible(boolean visible);

	Color getColor();

	ColoredShape setColor(Color color);

	@Transient
	ShapeBounds getBounds();
}
