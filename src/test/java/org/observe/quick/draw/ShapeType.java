package org.observe.quick.draw;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.config.ConfigurableValueCreator;
import org.qommons.Colors;

public enum ShapeType {
	Rectangle(ColoredRectangle.class) {
		@Override
		public ConfigurableValueCreator<ColoredShape, ? extends ColoredShape> initShape(
			ConfigurableValueCreator<ColoredShape, ? extends ColoredShape> shape, int shapeCount, int canvasWidth, int canvasHeight) {
			return initBorder(//
				initPositioning(//
					initShape((ConfigurableValueCreator<ColoredShape, ColoredRectangle>) shape, this, shapeCount), canvasWidth,
					canvasHeight),
				shapeCount);
		}
	},
	Ellipse(ColoredEllipse.class) {
		@Override
		public ConfigurableValueCreator<ColoredShape, ? extends ColoredShape> initShape(
			ConfigurableValueCreator<ColoredShape, ? extends ColoredShape> shape, int shapeCount, int canvasWidth, int canvasHeight) {
			return initBorder(//
				initPositioning(//
					initShape((ConfigurableValueCreator<ColoredShape, ColoredEllipse>) shape, this, shapeCount), canvasWidth, canvasHeight),
				shapeCount);
		}
	},
	Polygon(ColoredPolygon.class) {
		@Override
		public ConfigurableValueCreator<ColoredShape, ? extends ColoredShape> initShape(
			ConfigurableValueCreator<ColoredShape, ? extends ColoredShape> shape, int shapeCount, int canvasWidth, int canvasHeight) {
			return initBorder(//
				initShape((ConfigurableValueCreator<ColoredShape, ColoredPolygon>) shape, this, shapeCount), shapeCount);
		}
	},
	Text(ColoredText.class) {
		@Override
		public ConfigurableValueCreator<ColoredShape, ? extends ColoredShape> initShape(
			ConfigurableValueCreator<ColoredShape, ? extends ColoredShape> shape, int shapeCount, int canvasWidth, int canvasHeight) {
			return initPositioning(//
				initShape((ConfigurableValueCreator<ColoredShape, ColoredText>) shape, this, shapeCount), //
				canvasWidth, canvasHeight)//
				.with(ColoredText::getFontSize, 10);
		}
	};

	public final Class<? extends ColoredShape> shapeType;

	private ShapeType(Class<? extends ColoredShape> shapeType) {
		this.shapeType = shapeType;
	}

	public abstract ConfigurableValueCreator<ColoredShape, ? extends ColoredShape> initShape(
		ConfigurableValueCreator<ColoredShape, ? extends ColoredShape> shape, int shapeCount, int canvasWidth, int canvasHeight);

	static <S extends PositionedShape> ConfigurableValueCreator<ColoredShape, S> initPositioning(
		ConfigurableValueCreator<ColoredShape, S> creator, int canvasWidth, int canvasHeight) {
		return creator//
			.with(PositionedShape::getXAnchor, AnchorEnd.Leading)//
			.with(PositionedShape::getX, canvasWidth / 4)//
			.with(PositionedShape::getWidth, canvasWidth / 2)//
			.with(PositionedShape::getYAnchor, AnchorEnd.Leading)//
			.with(PositionedShape::getY, canvasHeight / 4)//
			.with(PositionedShape::getHeight, canvasHeight / 2);
	}

	static <S extends BorderedShape> ConfigurableValueCreator<ColoredShape, S> initBorder(ConfigurableValueCreator<ColoredShape, S> creator,
		int shapeCount) {
		return creator//
			.with(BorderedShape::getBorderColor, COLORS.get((shapeCount + COLORS.size() / 2) % COLORS.size()))//
			.with(BorderedShape::getBorderThickness, 1);
	}

	static <S extends ColoredShape> ConfigurableValueCreator<ColoredShape, S> initShape(ConfigurableValueCreator<ColoredShape, S> creator,
		ShapeType type, int shapeCount) {
		return creator//
			.with(ColoredShape::getType, type)//
			.with(ColoredShape::getName, "New " + type + " " + (shapeCount + 1))//
			// Skip over transparent, black, and white
			.with(ColoredShape::getColor, COLORS.get((shapeCount + 3) % COLORS.size()));
	}

	static final List<Color> COLORS;

	static {
		List<Color> colors = new ArrayList<>(Colors.getColorNames().size());
		for (String name : Colors.getColorNames())
			colors.add(Colors.getColorByName(name));
		COLORS = Collections.unmodifiableList(colors);
	}
}
