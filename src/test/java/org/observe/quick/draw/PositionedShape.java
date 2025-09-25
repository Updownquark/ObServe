package org.observe.quick.draw;

public interface PositionedShape extends ColoredShape {
	int getX();

	PositionedShape setX(int y);

	AnchorEnd getXAnchor();

	PositionedShape setXAnchor(AnchorEnd xAnchor);

	int getY();

	PositionedShape setY(int y);

	AnchorEnd getYAnchor();

	PositionedShape setYAnchor(AnchorEnd yAnchor);

	default Integer getX(AnchorEnd end) {
		if (getXAnchor() == end)
			return getX();
		else
			return null;
	}

	default Integer getY(AnchorEnd end) {
		if (getYAnchor() == end)
			return getY();
		else
			return null;
	}

	int getWidth();

	PositionedShape setWidth(int width);

	int getHeight();

	PositionedShape setHeight(int height);

	float getRotation();

	PositionedShape setRotation(float rotation);

	@Override
	default ShapeBounds getBounds() {
		double wxOffset;
		switch (getXAnchor()) {
		case Leading:
			wxOffset = 0.5;
			break;
		case Center:
			wxOffset = 0;
			break;
		default:
			wxOffset = -0.5;
			break;
		}
		double hyOffset;
		switch (getYAnchor()) {
		case Leading:
			hyOffset = 0.5;
			break;
		case Center:
			hyOffset = 0;
			break;
		default:
			hyOffset = -0.5;
			break;
		}
		double w = getWidth() * wxOffset, h = getHeight() * hyOffset;
		float rotation = getRotation();
		if (rotation == 0) {
			return new ShapeBounds(//
				getX() + (int) Math.round(w), //
				getY() + (int) Math.round(h), //
				Math.max(getWidth(), getHeight()));
		}
		rotation = -rotation * (float) Math.PI / 180;
		double cos = Math.cos(rotation);
		double sin = Math.sin(rotation);
		double cosW = cos * w;
		double sinH = sin * h;
		double cosH = cos * h;
		double sinW = sin * w;
		int x = getX() + (int) Math.round(cosW + sinH);
		int y = getY() + (int) Math.round(cosH - sinW);
		int maxWidth = (int) Math.round(Math.abs(cos) * getWidth() + Math.abs(sin) * getHeight());
		int maxHeight = (int) Math.round(Math.abs(cos) * getHeight() + Math.abs(sin) * getWidth());
		return new ShapeBounds(x, y, Math.max(maxWidth, maxHeight));
	}
}
