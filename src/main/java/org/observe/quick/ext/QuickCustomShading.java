package org.observe.quick.ext;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.util.swing.Shading;

public class QuickCustomShading implements QuickShading {
	private static final int MAX_SHADE_GRADATIONS = Byte.MAX_VALUE;
	// These buffers are used by the rendering. Since rendering should always be on the EDT, all shaders can use the same buffers.
	private static final int[] SHADING_COLOR_BUFFER = new int[256];
	private static final int UNKNOWN_COLOR = 0x77777777;

	private final SettableValue<Integer> theContainerWidth;
	private final SettableValue<Integer> theContainerHeight;
	private final SettableValue<Integer> thePixelX;
	private final SettableValue<Integer> thePixelY;
	private final SettableValue<Integer> theUnitWidth;
	private final SettableValue<Integer> theUnitHeight;
	private final boolean isStretchX;
	private final boolean isStretchY;
	private final SettableValue<Float> theLit;
	private final SettableValue<Float> theOpacity;
	private final Observable<?> theRefresh;

	public QuickCustomShading(SettableValue<Integer> containerWidth, SettableValue<Integer> containerHeight, //
		SettableValue<Integer> pixelX, SettableValue<Integer> pixelY, //
		SettableValue<Integer> unitWidth, SettableValue<Integer> unitHeight, boolean stretchX, boolean stretchY, //
		SettableValue<Float> lit, SettableValue<Float> opacity, Observable<?> refresh) {
		theContainerWidth = containerWidth;
		theContainerHeight = containerHeight;
		thePixelX = pixelX;
		thePixelY = pixelY;
		theUnitWidth = unitWidth;
		theUnitHeight = unitHeight;
		isStretchX = stretchX;
		isStretchY = stretchY;
		theLit = lit;
		theOpacity = opacity;
		theRefresh = refresh;
	}

	@Override
	public CustomShading createShading(ExElement box, Runnable repaint) throws ModelInstantiationException {
		return new CustomShading(this, box.getAddOn(QuickShaded.class), repaint);
	}

	public static class CustomShading implements Shading {
		private final SettableValue<Integer> theContainerWidth;
		private final SettableValue<Integer> theContainerHeight;
		private final SettableValue<Integer> thePixelX;
		private final SettableValue<Integer> thePixelY;
		private final SettableValue<Integer> theUnitWidth;
		private final SettableValue<Integer> theUnitHeight;
		private final boolean isStretchX;
		private final boolean isStretchY;
		private final SettableValue<Float> theLit;
		private final SettableValue<Float> theOpacity;

		private final ObservableValue<Color> theLightColor;
		private final ObservableValue<Color> theShadowColor;
		private final ObservableValue<Float> theMaxShadingAmount;

		private byte[][] theShading;
		private BufferedImage theImage;

		CustomShading(QuickCustomShading type, QuickShaded shaded, Runnable repaint) {
			theContainerWidth = type.theContainerWidth;
			theContainerHeight = type.theContainerHeight;
			thePixelX = type.thePixelX;
			thePixelY = type.thePixelY;
			theUnitWidth = type.theUnitWidth;
			theUnitHeight = type.theUnitHeight;
			isStretchX = type.isStretchX;
			isStretchY = type.isStretchY;
			theLit = type.theLit;
			theOpacity = type.theOpacity;

			theLightColor = shaded.getLightColor();
			theShadowColor = shaded.getShadowColor();
			theMaxShadingAmount = shaded.getMaxShadeAmount();

			if (type.theRefresh != null) {
				type.theRefresh.act(__ -> {
					clearCache();
					repaint.run();
				});
			}
			Observable.onRootFinish(ObservableValue.orChanges(false, theLightColor, theShadowColor, theMaxShadingAmount))
			.act(__ -> repaint.run());
		}

		@Override
		public void shade(Graphics2D graphics, Dimension size, Color background) {
			// First, fill in the background
			double opacity = theOpacity == null ? 1.0 : theOpacity.get();
			if (opacity > 0 && background.getAlpha() > 0) {
				if (opacity < 1)
					background = new Color(background.getRed(), background.getGreen(), background.getBlue(),
						(int) Math.round(opacity * 255));
				graphics.setColor(background);
				graphics.fillRect(0, 0, size.width, size.height);
			}


			int cellW = theUnitWidth == null ? size.width : theUnitWidth.get();
			int cellH = theUnitHeight == null ? size.height : theUnitHeight.get();

			int cellWP2 = nextP2(cellW);
			int cellHP2 = nextP2(cellH);
			ensureCapacity(cellWP2, cellHP2);

			Arrays.fill(SHADING_COLOR_BUFFER, UNKNOWN_COLOR);

			// Populate the buffered image with the background color
			Color light = theLightColor.get();
			Color shadow = theShadowColor.get();
			Float msa = theMaxShadingAmount.get();
			int lightRGB = light == null ? 0x00ffffff : (light.getRGB() & 0x00ffffff);
			int shadowRGB = shadow == null ? 0 : (shadow.getRGB() & 0x00ffffff);
			int maxShading = msa == null ? 255 : Math.round(msa * 255);
			if (maxShading < 0)
				maxShading = 0;
			else if (maxShading > 255)
				maxShading = 255;
			if (maxShading == 0)
				return;
			int xMult = theImage.getWidth() / cellWP2;
			int yMult = theImage.getHeight() / cellHP2;
			int imgY = 0;
			for (int y = 0; y < cellHP2; y++) {
				int nextImgY = imgY + yMult;
				int imgX = 0;
				for (int x = 0; x < cellWP2; x++) {
					int nextImgX = imgX + xMult;
					int pixel = getPixel(imgX, imgY, lightRGB, shadowRGB, maxShading);
					for (int y2 = imgY; y2 < nextImgY; y2++) {
						for (int x2 = imgX; x2 < nextImgX; x2++)
							theImage.setRGB(imgX, imgY, pixel);
					}
					imgX = nextImgX;
				}
				imgY = nextImgY;
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

		private int getPixel(int x, int y, int light, int shadow, int max) {
			byte shading = theShading[y][x];
			if (shading == 0)
				return 0;
			int pixel = SHADING_COLOR_BUFFER[shading + 128];
			if (pixel == UNKNOWN_COLOR) {
				int color, alpha;
				if (shading < 0) {
					color = shadow;
					alpha = (-shading) << 1;
				} else {
					color = light;
					alpha = shading << 1;
				}
				if (alpha == 254)
					alpha = 255;
				if (max != 255)
					alpha = alpha * max / 255;
				pixel = color | (alpha << 24);
				SHADING_COLOR_BUFFER[shading + 128] = pixel;
			}
			return pixel;
		}

		private void clearCache() {
			theShading = null;
		}

		private void ensureCapacity(int width, int height) {
			if (theLit != null)
				theShading = ensureCapacity(theShading, width, height, theLit);
		}

		private byte[][] ensureCapacity(byte[][] array, int width, int height, SettableValue<Float> value) {
			if (array != null && array.length >= height && array[0].length >= width)
				return array;
			byte[][] newArray = new byte[height][width];
			theImage = new BufferedImage(newArray[0].length, newArray.length, BufferedImage.TYPE_INT_ARGB);
			theContainerWidth.set(width, null);
			theContainerHeight.set(height, null);
			if (array != null) {
				int iMult = newArray.length / array.length;
				int jMult = newArray[0].length / array.length;
				int prevI = 0;
				for (int i = 0; i < array.length; i++) {
					int newI = i * iMult;
					for (int i2 = prevI + 1; i2 < newI; i2++) {
						thePixelY.set(i2, null);
						populateRow(newArray[newI], value);
					}
					prevI = newI;
					thePixelY.set(newI, null);
					int prevJ = 0;
					for (int j = 0; j < array[i].length; j++) {
						int newJ = j * jMult;
						for (int j2 = prevJ + 1; j2 < newJ; j2++) {
							thePixelX.set(j2, null);
							newArray[newI][j2] = toShadeValue(value.get());
						}
						newArray[newI][newJ] = array[i][j];
					}
				}
			} else {
				for (int y = 0; y < height; y++) {
					thePixelY.set(y, null);
					populateRow(newArray[y], value);
				}
			}
			return newArray;
		}

		private void populateRow(byte[] array, SettableValue<Float> value) {
			for (int i = 0; i < array.length; i++) {
				thePixelX.set(i, null);
				array[i] = toShadeValue(value.get());
			}
		}

		private static byte toShadeValue(Float f) {
			if (f == null)
				return 0;
			float fv = f.floatValue();
			if (fv <= -1.0f)
				return -MAX_SHADE_GRADATIONS;
			else if (fv >= 1.0f)
				return MAX_SHADE_GRADATIONS;
			else
				return (byte) (fv * MAX_SHADE_GRADATIONS);
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
