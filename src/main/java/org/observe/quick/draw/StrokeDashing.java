package org.observe.quick.draw;

public class StrokeDashing {
	public static final StrokeDashing full = new StrokeDashing(null);

	public static final StrokeDashing dotted = new StrokeDashing(new float[] { 3, 3 });

	public static final StrokeDashing dashed = new StrokeDashing(new float[] { 6, 6 });

	private final float[] theDashing;

	public StrokeDashing(float[] dashing) {
		theDashing = dashing;
	}
}
