package org.observe.quick.swing;

/** Used by the Simple Shading Demo */
public class ShadeTestHelper {
	private boolean isIncreasingShade = true;

	/**
	 * Moves the sun around
	 *
	 * @param lightSource The light source orientation
	 * @return The new light source orientation
	 */
	public float advanceLightSource(float lightSource) {
		lightSource += 3;
		if (lightSource >= 360)
			lightSource = 0;
		return lightSource;
	}

	/**
	 * Changes the max shading
	 *
	 * @param maxShade The max shading
	 * @return The new max shading
	 */
	public float advanceMaxShading(float maxShade) {
		if (isIncreasingShade) {
			maxShade += 0.05;
			if (maxShade >= 1) {
				maxShade = 1;
				isIncreasingShade = false;
			}
		} else {
			maxShade -= 0.05;
			if (maxShade <= 0) {
				maxShade = 0;
				isIncreasingShade = true;
			}
		}
		return maxShade;
	}

	private static final float[] DROP_SHADING = new float[25];
	static {
		for (int i = 0; i < DROP_SHADING.length; i++)
			DROP_SHADING[i] = (float) Math.sin(i * Math.PI * 2 / DROP_SHADING.length);
	}

	/**
	 * @param x The x position to shade
	 * @param y The y position to shade
	 * @param w The width of a tile
	 * @param h The height of a tile
	 * @return The shade amount
	 */
	public float shadeCustom(int x, int y, int w, int h) {
		int wOver2 = w / 2;
		int hOver2 = h / 2;
		x -= wOver2;
		y -= hOver2;
		float r2 = (float) Math.sqrt(x * x + y * y);
		int R2 = (wOver2 + hOver2) / 2;
		int p = Math.round(r2 * DROP_SHADING.length / R2);
		return DROP_SHADING[p % DROP_SHADING.length];
	}
}
