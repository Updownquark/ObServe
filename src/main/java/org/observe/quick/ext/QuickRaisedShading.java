package org.observe.quick.ext;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.base.QuickSize;
import org.observe.util.swing.Shading;

/** A simple shader that makes a component look raised out of the screen above the level of the rest of the UI. E.g. like a button. */
public class QuickRaisedShading implements QuickShading {
	/** The default light source direction, in degrees East of North */
	public static final float DEFAULT_LIGHT_SOURCE = 45;
	/** The default corner radius */
	public static final QuickSize DEFAULT_CORNER_RADIUS = QuickSize.ofPixels(4);
	/** The default maximum shading amount */
	public static final float DEFAULT_MAX_SHADING = 0.5f;

	private final boolean isRound;
	private final boolean isHorizontal;
	private final boolean isVertical;
	private final ObservableValue<Double> theOpacity;

	QuickRaisedShading(boolean round, boolean horizontal, boolean vertical, ObservableValue<Double> opacity) {
		isRound = round;
		isHorizontal = horizontal;
		isVertical = vertical;
		theOpacity = opacity;
	}

	/** @return Whether to draw corners rounded or square (sharp) */
	public boolean isRound() {
		return isRound;
	}

	/** @return Whether this shading applies to the horizontal edges (left and right) */
	public boolean isHorizontal() {
		return isHorizontal;
	}

	/** @return Whether this shading applies to the vertical edges (top and bottom) */
	public boolean isVertical() {
		return isVertical;
	}

	/** @return The opacity for the shading */
	public ObservableValue<Double> getOpacity() {
		return theOpacity;
	}

	@Override
	public Shading createShading(ExElement widget, Runnable repaint) throws ModelInstantiationException {
		// Round corners don't matter if there are no corners
		if (isRound && (isHorizontal && isVertical))
			return new RoundShading(this, widget.getAddOn(QuickShaded.class), repaint);
		else
			return new SquareShading(this, widget.getAddOn(QuickShaded.class), repaint);
	}

	/** Abstract shading implementation for raised shading */
	protected static abstract class AbstractShading implements Shading {
		private final boolean isHorizontal;
		private final boolean isVertical;

		private final ObservableValue<Double> theOpacity;
		private final ObservableValue<Float> theLightSource;
		private final ObservableValue<Color> theLightColor;
		private final ObservableValue<Color> theShadowColor;
		private final ObservableValue<QuickSize> theCornerRadius;
		private final ObservableValue<Float> theMaxShadingAmount;

		AbstractShading(QuickRaisedShading type, QuickShaded shaded, Runnable repaint) {
			isHorizontal = type.isHorizontal();
			isVertical = type.isVertical();

			theOpacity = type.getOpacity();
			theLightSource = shaded.getLightSource();
			theLightColor = shaded.getLightColor();
			theShadowColor = shaded.getShadowColor();
			theCornerRadius = shaded.getCornerRadius();
			theMaxShadingAmount = shaded.getMaxShadeAmount();

			Observable
			.onRootFinish(ObservableValue.orChanges(false, theOpacity, theLightSource, theLightColor, theShadowColor, theCornerRadius,
				theMaxShadingAmount))//
			.act(__ -> repaint.run());
		}

		/** @return Whether this shading applies to the horizontal edges (left and right) */
		public boolean isHorizontal() {
			return isHorizontal;
		}

		/** @return Whether this shading applies to the vertical edges (top and bottom) */
		public boolean isVertical() {
			return isVertical;
		}

		@Override
		public void shade(Graphics2D graphics, Dimension size, Color background) {
			if (!isHorizontal && !isVertical)
				return;
			QuickSize radius = getOrDefault(theCornerRadius, DEFAULT_CORNER_RADIUS);
			int wRad = radius.evaluate(size.width);
			int hRad = radius.evaluate(size.height);
			double opacity = theOpacity == null ? 1.0 : theOpacity.get();
			Color bg = background;
			if (opacity > 0 && bg.getAlpha() > 0) {
				if (opacity < 1)
					bg = new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), (int) Math.round(opacity * 255));
				graphics.setColor(bg);
				graphics.fillRect(0, 0, size.width, size.height);
			}
			if (wRad == 0 && hRad == 0)
				return;
			float source = getOrDefault(theLightSource, DEFAULT_LIGHT_SOURCE);
			float maxShading = getOrDefault(theMaxShadingAmount, DEFAULT_MAX_SHADING);
			Color light = getOrDefault(theLightColor, Color.white);
			Color shadow = getOrDefault(theShadowColor, Color.black);
			shade(graphics, size, light, shadow, source, maxShading, wRad, hRad);
		}

		/**
		 * @param graphics The graphics to draw on
		 * @param size The size of the widget to shade
		 * @param light The color of the light
		 * @param shadow The color of the shadow
		 * @param source The direction that the light is coming from, in degrees East of North
		 * @param maxShading The max shading amount
		 * @param wRad The corner radius in the horizontal dimension
		 * @param hRad The corner radius in the vertical dimension
		 */
		protected abstract void shade(Graphics2D graphics, Dimension size, Color light, Color shadow, float source, float maxShading,
			int wRad, int hRad);

		private static <T> T getOrDefault(ObservableValue<T> style, T defaultValue) {
			T styleValue = style.get();
			return styleValue != null ? styleValue : defaultValue;
		}
	}

	/** Rounded raised shader */
	public static class RoundShading extends AbstractShading {
		private static final Map<CornerRenderKey, SoftReference<CornerRender>> CORNER_RENDER_CACHE = new LinkedHashMap<>();

		static CornerRender getOrComputeCorner(CornerRenderKey key, int minRadius) {
			SoftReference<CornerRender> ref = CORNER_RENDER_CACHE.get(key);
			CornerRender corner = ref == null ? null : ref.get();
			if (corner == null || corner.getRadius() < minRadius) {
				if (ref != null)
					CORNER_RENDER_CACHE.remove(key);
				corner = new CornerRender(minRadius);
				corner.render(key.source, key.maxShading);
				CORNER_RENDER_CACHE.put(key, new SoftReference<>(corner));
			}
			return corner;
		}

		RoundShading(QuickRaisedShading type, QuickShaded shaded, Runnable repaint) {
			super(type, shaded, repaint);
		}

		@Override
		protected void shade(Graphics2D graphics, Dimension size, Color light, Color shadow, float source, float maxShading, int wRad,
			int hRad) {
			int w = size.width;
			int h = size.height;
			if (wRad * 2 > w)
				wRad = w / 2;
			if (hRad * 2 > h)
				hRad = h / 2;
			// The radius of the corner render we get needs to be either at least the width or height radius for good resolution.
			int maxRad = wRad;
			if (hRad > maxRad)
				maxRad = hRad;
			Color bg = graphics.getColor();
			int bgRGB = bg.getRGB();
			int lightRGB = light.getRGB() & 0xffffff;
			int shadowRGB = shadow.getRGB() & 0xffffff;
			if (wRad == 0 || hRad == 0)
				return;
			BufferedImage cornerBgImg = new BufferedImage(wRad, hRad, BufferedImage.TYPE_4BYTE_ABGR);
			BufferedImage cornerShadeImg = new BufferedImage(wRad, hRad, BufferedImage.TYPE_4BYTE_ABGR);
			BufferedImage tbEdgeImg = new BufferedImage(1, hRad, BufferedImage.TYPE_4BYTE_ABGR);
			BufferedImage lrEdgeImg = new BufferedImage(wRad, 1, BufferedImage.TYPE_4BYTE_ABGR);
			int[][][] rot = new int[4][][];
			rot[0] = null;
			rot[1] = new int[][] { new int[] { 0, 1 }, new int[] { -1, 0 } };
			rot[2] = new int[][] { new int[] { -1, 0 }, new int[] { 0, -1 } };
			rot[3] = new int[][] { new int[] { 0, -1 }, new int[] { 1, 0 } };
			for (int i = 0; i < 4; i++) {
				float tempSource = source - 90 * i;
				while (tempSource < 0)
					tempSource += 360;
				int radius = maxRad * 3 + 3; // Scale up the key to make the buttons prettier
				CornerRenderKey key = new CornerRenderKey(tempSource, maxShading);
				CornerRender cr = getOrComputeCorner(key, radius);

				// Draw the corner
				int renderX = 0, renderY = 0;
				switch (i) {
				case 0:
					renderX = 0;
					renderY = 0;
					break;
				case 1:
					renderX = w - wRad - 1;
					renderY = 0;
					break;
				case 2:
					renderX = w - wRad - 1;
					renderY = h - hRad - 1;
					break;
				case 3:
					renderX = 0;
					renderY = h - hRad - 1;
					break;
				}
				graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				for (int x = 0; x < wRad; x++)
					for (int y = 0; y < hRad; y++) {
						int crX = x, crY = y;
						crX = Math.round(crX * cr.getRadius() * 1.0f / wRad);
						crY = Math.round(crY * cr.getRadius() * 1.0f / hRad);
						if (rot[i] != null) {
							int preCrX = crX;
							crX = rot[i][0][0] * crX + rot[i][0][1] * crY;
							crY = rot[i][1][0] * preCrX + rot[i][1][1] * crY;
						}
						if (crX < 0)
							crX += cr.getRadius();
						else if (crX >= cr.getRadius())
							crX = cr.getRadius() - 1;
						if (crY < 0)
							crY += cr.getRadius();
						else if (crY >= cr.getRadius())
							crY = cr.getRadius() - 1;

						if (cr.contains(crX, crY))
							cornerBgImg.setRGB(x, y, bgRGB);
						else
							cornerBgImg.setRGB(x, y, 0);

						int alpha = cr.getShadeAmount(crX, crY);
						if (alpha > 0)
							cornerShadeImg.setRGB(x, y, lightRGB | (alpha << 24));
						else if (alpha < 0)
							cornerShadeImg.setRGB(x, y, shadowRGB | ((-alpha) << 24));
						else
							cornerShadeImg.setRGB(x, y, 0);
					}
				if (!graphics.drawImage(cornerBgImg, renderX, renderY, renderX + wRad, renderY + hRad, 0, 0, wRad, hRad, null))
					cornerBgImg = new BufferedImage(wRad, hRad, BufferedImage.TYPE_4BYTE_ABGR);
				if (!graphics.drawImage(cornerShadeImg, renderX, renderY, renderX + wRad, renderY + hRad, 0, 0, wRad, hRad, null))
					cornerShadeImg = new BufferedImage(wRad, hRad, BufferedImage.TYPE_4BYTE_ABGR);

				// Corner drawn, now draw the edge
				switch (i) {
				case 0:
					for (int y = 0; y < hRad; y++) {
						tbEdgeImg.setRGB(0, y, 0);
						int crY = (int) (y * cr.getRadius() * 1.0f / hRad);
						int alpha = cr.getShadeAmount(cr.getRadius(), crY);
						if (alpha > 0)
							tbEdgeImg.setRGB(0, y, lightRGB | (alpha << 24));
						else if (alpha < 0)
							tbEdgeImg.setRGB(0, y, shadowRGB | ((-alpha) << 24));
					}
					graphics.drawImage(tbEdgeImg, wRad, 0, w - wRad, hRad, 0, 0, 1, hRad, null);
					break;
				case 1:
					for (int x = 0; x < wRad; x++) {
						lrEdgeImg.setRGB(x, 0, 0);
						int crY = (int) ((wRad - x - 1) * cr.getRadius() * 1.0f / wRad) + 1;
						int alpha = cr.getShadeAmount(cr.getRadius(), crY);
						if (alpha > 0)
							lrEdgeImg.setRGB(x, 0, lightRGB | (alpha << 24));
						else if (alpha < 0)
							lrEdgeImg.setRGB(x, 0, shadowRGB | ((-alpha) << 24));
					}
					graphics.drawImage(lrEdgeImg, w - wRad, hRad, w, h - hRad, 0, 0, wRad, 1, null);
					break;
				case 2:
					for (int y = 0; y < hRad; y++) {
						tbEdgeImg.setRGB(0, y, 0);
						int crY = (int) ((hRad - y - 1) * cr.getRadius() * 1.0f / hRad) + 1;
						int alpha = cr.getShadeAmount(cr.getRadius(), crY);
						if (alpha > 0)
							tbEdgeImg.setRGB(0, y, lightRGB | (alpha << 24));
						else if (alpha < 0)
							tbEdgeImg.setRGB(0, y, shadowRGB | ((-alpha) << 24));
					}
					graphics.drawImage(tbEdgeImg, wRad, h - hRad, w - wRad, h, 0, 0, 1, hRad, null);
					break;
				case 3:
					for (int x = 0; x < wRad; x++) {
						lrEdgeImg.setRGB(x, 0, 0);
						int crY = (int) (x * cr.getRadius() * 1.0f / wRad);
						int alpha = cr.getShadeAmount(cr.getRadius(), crY);
						if (alpha > 0)
							lrEdgeImg.setRGB(x, 0, lightRGB | (alpha << 24));
						else if (alpha < 0)
							lrEdgeImg.setRGB(x, 0, shadowRGB | ((-alpha) << 24));
					}
					graphics.drawImage(lrEdgeImg, 0, hRad, wRad, h - hRad, 0, 0, wRad, 1, null);
					break;
				}
			}
		}

		private static class CornerRender {
			private short[][] theShadeAmt;

			private java.util.BitSet theContainment;

			CornerRender(int radius) {
				theShadeAmt = new short[radius][radius + 1];
				theContainment = new java.util.BitSet(radius * radius);
			}

			void render(float lightSource, float maxShading) {
				lightSource -= 90;
				if (lightSource < 0)
					lightSource += 360;
				lightSource *= Math.PI / 180;
				int xL = (int) Math.round(-Math.cos(lightSource) * 255);
				int yL = (int) Math.round(-Math.sin(lightSource) * 255);
				int rad = theShadeAmt.length;
				int rad2 = rad * rad;
				// -1 so it renders some pixels for the edge too
				for (int x = -1; x < rad; x++) {
					int x2 = (x + 1) * (x + 1);
					for (int y = 0; y < rad; y++) {
						int contIdx = (rad - y - 1) * rad + (rad - x - 1);
						theContainment.set(contIdx, x2 + (y + 1) * (y + 1) < rad2);
						if (!theContainment.get(contIdx))
							continue;
						int dot = (x + 1) * xL / (rad + 1) + (y + 1) * yL / (rad + 1);
						if (dot > 255)
							dot = 255;
						else if (dot < -255)
							dot = -255;
						theShadeAmt[rad - y - 1][rad - x - 1] = (short) (maxShading * dot);
					}
				}
			}

			int getRadius() {
				return theShadeAmt.length;
			}

			int getShadeAmount(int x, int y) {
				return theShadeAmt[y][x];
			}

			boolean contains(int x, int y) {
				int contIdx = theShadeAmt.length * y + x;
				return theContainment.get(contIdx);
			}
		}

		private static class CornerRenderKey {
			final float source;

			final float maxShading;

			CornerRenderKey(float src, float maxShade) {
				source = src;
				maxShading = maxShade;
			}

			@Override
			public boolean equals(Object o) {
				if (!(o instanceof CornerRenderKey))
					return false;
				CornerRenderKey key = (CornerRenderKey) o;
				return Math.abs(key.source - source) <= 1 && Math.abs(key.maxShading - maxShading) <= .01f;
			}

			@Override
			public int hashCode() {
				return Float.floatToIntBits(source) ^ Float.floatToIntBits(maxShading);
			}
		}
	}

	/** Square raised shader */
	public static class SquareShading extends AbstractShading {
		SquareShading(QuickRaisedShading type, QuickShaded shaded, Runnable repaint) {
			super(type, shaded, repaint);
		}

		@Override
		protected void shade(Graphics2D graphics, Dimension size, Color light, Color shadow, float source, float maxShading, int wRad,
			int hRad) {
			if (!isHorizontal())
				wRad = 0;
			if (!isVertical())
				hRad = 0;
			int lightRGB = light.getRGB() & 0xffffff;
			int shadowRGB = shadow.getRGB() & 0xffffff;
			int sin = -(int) Math.round(255 * Math.sin(source * Math.PI / 180));
			int cos = (int) Math.round(255 * Math.cos(source * Math.PI / 180));

			// Top and bottom sides
			for (int y = 0; y < hRad; y++) {
				int startX = wRad * y / hRad;
				int endX = size.width - startX;
				if (wRad > 0)
					endX--;
				// Top
				int shading = (int) (maxShading * cos * (hRad - y) / hRad);
				Color lineColor;
				if (shading > 0)
					lineColor = new Color(lightRGB | (shading << 24), true);
				else if (shading < 0)
					lineColor = new Color(shadowRGB | ((-shading) << 24), true);
				else
					lineColor = null;

				if (lineColor != null) {
					graphics.setColor(lineColor);
					graphics.drawLine(startX, y, endX, y);
				}

				// Bottom
				shading = (int) (maxShading * -cos * (hRad - y) / hRad);
				if (shading > 0)
					lineColor = new Color(lightRGB | (shading << 24), true);
				else if (shading < 0)
					lineColor = new Color(shadowRGB | ((-shading) << 24), true);
				else
					lineColor = null;

				if (lineColor != null) {
					graphics.setColor(lineColor);
					graphics.drawLine(startX, size.height - y - 1, endX, size.height - y - 1);
				}
			}

			// Left and right sides
			for (int x = 0; x < wRad; x++) {
				int startY = hRad * x / wRad;
				int endY = size.height - startY;
				if (hRad > 0) {
					startY++;
					endY -= 2;
				}
				// Left
				int shading = (int) (maxShading * sin * (wRad - x) / wRad);
				Color lineColor;
				if (shading > 0)
					lineColor = new Color(lightRGB | (shading << 24), true);
				else if (shading < 0)
					lineColor = new Color(shadowRGB | ((-shading) << 24), true);
				else
					lineColor = null;

				if (lineColor != null) {
					graphics.setColor(lineColor);
					graphics.drawLine(x, startY, x, endY);
				}

				// Right
				shading = (int) (maxShading * -sin * (wRad - x) / wRad);
				if (shading > 0)
					lineColor = new Color(lightRGB | (shading << 24), true);
				else if (shading < 0)
					lineColor = new Color(shadowRGB | ((-shading) << 24), true);
				else
					lineColor = null;

				if (lineColor != null) {
					graphics.setColor(lineColor);
					graphics.drawLine(size.width - x - 1, startY, size.width - x - 1, endY);
				}
			}
			// TODO Auto-generated method stub
		}
	}
}
