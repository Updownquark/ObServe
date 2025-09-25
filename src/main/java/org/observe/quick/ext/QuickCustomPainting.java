package org.observe.quick.ext;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.util.swing.Shading;
import org.qommons.Causable;
import org.qommons.Transaction;

/** A custom shading class, allowing specification of the color of at each pixel independently */
public class QuickCustomPainting implements QuickShading {
	private final SettableValue<Integer> theContainerWidth;
	private final SettableValue<Integer> theContainerHeight;
	private final SettableValue<Integer> thePixelX;
	private final SettableValue<Integer> thePixelY;
	private final SettableValue<Integer> theUnitWidth;
	private final SettableValue<Integer> theUnitHeight;
	private final boolean isStretchX;
	private final boolean isStretchY;
	private final SettableValue<Color> theColor;
	private final SettableValue<Float> theOpacity;
	private final Observable<?> theRefresh;

	/**
	 * @param containerWidth The value container for the width of the container widget
	 * @param containerHeight The value container for the height of the container widget
	 * @param pixelX The container for the x-coordinate of the pixel being shaded
	 * @param pixelY The container for the y-coordinate of the pixel being shaded
	 * @param unitWidth The width of the tile for the shader
	 * @param unitHeight The height for the tile of the shader
	 * @param stretchX Whether to stretch the tile across the container's width, or repeat it
	 * @param stretchY Whether to stretch the tile across the container's height, or repeat it
	 * @param color The color for each pixel
	 * @param opacity The opacity for each pixel
	 * @param refresh The event that will cause this shading to redraw
	 */
	public QuickCustomPainting(SettableValue<Integer> containerWidth, SettableValue<Integer> containerHeight, //
		SettableValue<Integer> pixelX, SettableValue<Integer> pixelY, //
		SettableValue<Integer> unitWidth, SettableValue<Integer> unitHeight, boolean stretchX, boolean stretchY, //
		SettableValue<Color> color, SettableValue<Float> opacity, Observable<?> refresh) {
		theContainerWidth = containerWidth;
		theContainerHeight = containerHeight;
		thePixelX = pixelX;
		thePixelY = pixelY;
		theUnitWidth = unitWidth;
		theUnitHeight = unitHeight;
		isStretchX = stretchX;
		isStretchY = stretchY;
		theColor = color;
		theOpacity = opacity;
		theRefresh = refresh;
	}

	@Override
	public CustomPainting createShading(ExElement box, Runnable repaint) throws ModelInstantiationException {
		return new CustomPainting(this, box.getAddOn(QuickShaded.class), repaint);
	}

	/** Shading implementation for {@link QuickCustomPainting} */
	public static class CustomPainting implements Shading {
		private final SettableValue<Integer> theContainerWidth;
		private final SettableValue<Integer> theContainerHeight;
		private final SettableValue<Integer> thePixelX;
		private final SettableValue<Integer> thePixelY;
		private final SettableValue<Integer> theUnitWidth;
		private final SettableValue<Integer> theUnitHeight;
		private final boolean isStretchX;
		private final boolean isStretchY;
		private final SettableValue<Color> theColor;
		private final SettableValue<Float> theOpacity;

		private boolean isDirty;
		private BufferedImage theImage;

		CustomPainting(QuickCustomPainting type, QuickShaded shaded, Runnable repaint) {
			theContainerWidth = type.theContainerWidth;
			theContainerHeight = type.theContainerHeight;
			thePixelX = type.thePixelX;
			thePixelY = type.thePixelY;
			theUnitWidth = type.theUnitWidth;
			theUnitHeight = type.theUnitHeight;
			isStretchX = type.isStretchX;
			isStretchY = type.isStretchY;
			theColor = type.theColor;
			theOpacity = type.theOpacity;

			if (type.theRefresh != null) {
				type.theRefresh.act(__ -> {
					isDirty = true;
					repaint.run();
				});
			}
		}

		@Override
		public void shade(Graphics2D graphics, Dimension size, Color background) {
			int cellW = theUnitWidth == null ? size.width : theUnitWidth.get();
			int cellH = theUnitHeight == null ? size.height : theUnitHeight.get();

			int cellWP2 = nextP2(cellW);
			int cellHP2 = nextP2(cellH);

			if (theImage == null || isDirty || theImage.getWidth() < cellWP2 || theImage.getHeight() < cellHP2) {
				isDirty = false;
				if (theImage == null || theImage.getWidth() < cellWP2 || theImage.getHeight() < cellHP2)
					theImage = new BufferedImage(cellWP2, cellHP2, BufferedImage.TYPE_INT_ARGB);
				try (Causable.CausableInUse cause = Causable.cause(); //
					Transaction xt = thePixelX.lock(true, cause); //
					Transaction yt = thePixelY.lock(true, cause)) {
					if (!Objects.equals(cellWP2, theContainerWidth.get()))
						theContainerWidth.set(cellWP2, cause);
					if (!Objects.equals(cellHP2, theContainerHeight.get()))
						theContainerHeight.set(cellHP2, cause);
					for (int y = 0; y < cellHP2; y++) {
						for (int x = 0; x < cellWP2; x++) {
							thePixelX.set(x, cause);
							thePixelY.set(y, cause);
							Color color = theColor.get();
							if (color == null || color.getAlpha() == 0)
								theImage.setRGB(x, y, 0); // Transparent
							else if (theOpacity != null) {
								Float opacity = theOpacity.get();
								if (opacity != null) {
									if (opacity.floatValue() <= 0.0f)
										theImage.setRGB(x, y, 0); // Transparent
									else if (opacity.floatValue() >= 1.0f)
										theImage.setRGB(x, y, color.getRGB());
									else {
										color = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(opacity * 255));
										theImage.setRGB(x, y, color.getRGB());
									}
								} else
									theImage.setRGB(x, y, color.getRGB());
							} else
								theImage.setRGB(x, y, color.getRGB());
						}
					}
				}
			}

			// Shade the graphics using the buffered image
			if (isStretchY) {
				if (isStretchX) {
					graphics.drawImage(theImage, 0, 0, size.width, size.height, 0, 0, theImage.getWidth(), theImage.getHeight(), null);
				} else {
					for (int x = 0; x < size.width; x += cellW)
						graphics.drawImage(theImage, x, 0, x + cellW, size.height, 0, 0, theImage.getWidth(), theImage.getHeight(), null);
				}
			} else if (isStretchX) {
				for (int y = 0; y < size.height; y += cellH)
					graphics.drawImage(theImage, 0, y, size.width, y + cellH, 0, 0, theImage.getWidth(), theImage.getHeight(), null);
			} else {
				for (int y = 0; y < size.height; y += cellH) {
					for (int x = 0; x < size.width; x += cellW)
						graphics.drawImage(theImage, x, y, x + cellW, y + cellH, 0, 0, theImage.getWidth(), theImage.getHeight(), null);
				}
			}
		}

		private static final double LOG_2 = Math.log(2);
		private static final int[] POWERS_OF_2 = new int[] { 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384 };

		private static int nextP2(int height) {
			if (height < 16)
				return 16;
			int log2 = (int) Math.ceil(Math.log(height) / LOG_2);
			int pow2Index = log2 - 5;
			return POWERS_OF_2[Math.min(POWERS_OF_2.length - 1, pow2Index)];
		}
	}
}
