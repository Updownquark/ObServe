package org.observe.util.swing;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/** Tests for the org.observe.util.swing package */
public class ObservableSwingUtilsTests {
	/** Tests {@link ObservableSwingUtils#fitBoundsToGraphicsEnv(int, int, int, int, List)} */
	@Test
	public void testBoundsFitting() {
		int x = 0, y = 0, w = 1000, h = 1000;
		Rectangle gBounds = new Rectangle();
		List<Rectangle> gList = Arrays.asList(gBounds);
		Rectangle moved;

		// Test moving across each of the 4 corners for disjoint windows
		gBounds.setBounds(-10000, -10000, 5000, 5000); // Upper-left
		moved = ObservableSwingUtils.fitBoundsToGraphicsEnv(x, y, w, h, gList);
		Assert.assertEquals(-6000, moved.x);
		Assert.assertEquals(-6000, moved.y);
		Assert.assertEquals(1000, moved.width);
		Assert.assertEquals(1000, moved.height);

		gBounds.setBounds(10000, -10000, 5000, 5000); // Upper-right
		moved = ObservableSwingUtils.fitBoundsToGraphicsEnv(x, y, w, h, gList);
		Assert.assertEquals(10000, moved.x);
		Assert.assertEquals(-6000, moved.y);
		Assert.assertEquals(1000, moved.width);
		Assert.assertEquals(1000, moved.height);

		gBounds.setBounds(-10000, 10000, 5000, 5000); // Lower-left
		moved = ObservableSwingUtils.fitBoundsToGraphicsEnv(x, y, w, h, gList);
		Assert.assertEquals(-6000, moved.x);
		Assert.assertEquals(10000, moved.y);
		Assert.assertEquals(1000, moved.width);
		Assert.assertEquals(1000, moved.height);

		gBounds.setBounds(10000, 10000, 5000, 5000); // Lower-right
		moved = ObservableSwingUtils.fitBoundsToGraphicsEnv(x, y, w, h, gList);
		Assert.assertEquals(10000, moved.x);
		Assert.assertEquals(10000, moved.y);
		Assert.assertEquals(1000, moved.width);
		Assert.assertEquals(1000, moved.height);

		// Same thing, but force the window to resize
		gBounds.setBounds(-10000, -10000, 500, 500); // Upper-left
		moved = ObservableSwingUtils.fitBoundsToGraphicsEnv(x, y, w, h, gList);
		Assert.assertEquals(gBounds, moved);

		gBounds.setBounds(10000, -10000, 500, 500); // Upper-right
		moved = ObservableSwingUtils.fitBoundsToGraphicsEnv(x, y, w, h, gList);
		Assert.assertEquals(gBounds, moved);

		gBounds.setBounds(-10000, 10000, 500, 500); // Lower-left
		moved = ObservableSwingUtils.fitBoundsToGraphicsEnv(x, y, w, h, gList);
		Assert.assertEquals(gBounds, moved);

		gBounds.setBounds(10000, 10000, 500, 500); // Lower-right
		moved = ObservableSwingUtils.fitBoundsToGraphicsEnv(x, y, w, h, gList);
		Assert.assertEquals(gBounds, moved);
	}
}
