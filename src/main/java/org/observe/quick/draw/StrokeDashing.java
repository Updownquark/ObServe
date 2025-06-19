package org.observe.quick.draw;

import java.awt.BasicStroke;
import java.awt.Graphics2D;

public class StrokeDashing {
	public static final StrokeDashing full = new StrokeDashing(null);

	public static final StrokeDashing dotted = new StrokeDashing(new float[] { 3, 3 });

	public static final StrokeDashing dashed = new StrokeDashing(new float[] { 6, 6 });

	private final float[] theDashing;

	public StrokeDashing(float[] dashing) {
		theDashing = dashing;
	}

	public void apply(Graphics2D gfx, float thickness, boolean round) {
		gfx.setStroke(new BasicStroke(thickness, //
			round ? BasicStroke.CAP_ROUND : BasicStroke.CAP_SQUARE, //
			round ? BasicStroke.JOIN_ROUND : BasicStroke.JOIN_BEVEL, 0, theDashing, 0));
	}
}
