package org.observe.quick.draw;

import org.observe.config.ObservableValueSet;

public interface ColoredPolygon extends BorderedShape {
	ObservableValueSet<DrawDemoPoint> getVertices();

	@Override
	default ShapeBounds getBounds() {
		if (getVertices().getValues().isEmpty())
			return new ShapeBounds(0, 0, 0);
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (DrawDemoPoint v : getVertices().getValues()) {
			int x = v.getX();
			if (x < minX)
				minX = x;
			if (x > maxX)
				maxX = x;
			int y = v.getY();
			if (y < minY)
				minY = y;
			if (y > maxY)
				maxY = y;
		}
		return new ShapeBounds((minX + maxX) / 2, (minY + maxY) / 2, Math.max(maxX - minX, maxY - minY));
	}
}
