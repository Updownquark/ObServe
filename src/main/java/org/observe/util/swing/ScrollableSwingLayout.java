package org.observe.util.swing;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager2;
import java.awt.Rectangle;

import javax.swing.Scrollable;

/** A layout manager that also implements the {@link Scrollable} functionality */
public interface ScrollableSwingLayout extends LayoutManager2 {
	/**
	 * @param parent The container
	 * @return The size to allocate to the container inside its viewport
	 */
	Dimension getPreferredScrollableViewportSize(Container parent);

	/**
	 * @param parent The container
	 * @param visibleRect The rectangle of the container that is visible in its viewport
	 * @param orientation The scrolling orientation
	 * @param direction The scrolling direction
	 * @return The unit increment for the scroll
	 * @see Scrollable#getScrollableUnitIncrement(Rectangle, int, int)
	 */
	int getScrollableUnitIncrement(Container parent, Rectangle visibleRect, int orientation, int direction);
	/**
	 * @param parent The container
	 * @param visibleRect The rectangle of the container that is visible in its viewport
	 * @param orientation The scrolling orientation
	 * @param direction The scrolling direction
	 * @return The block increment for the scroll
	 * @see Scrollable#getScrollableBlockIncrement(Rectangle, int, int)
	 */
	int getScrollableBlockIncrement(Container parent, Rectangle visibleRect, int orientation, int direction);

	/**
	 * @param parent The container
	 * @return Whether the width of the container should be the same as that of the scroll pane (i.e. no horizontal scroll bar)
	 * @see Scrollable#getScrollableTracksViewportWidth()
	 */
	boolean getScrollableTracksViewportWidth(Container parent);
	/**
	 * @param parent The container
	 * @return Whether the height of the container should be the same as that of the scroll pane (i.e. no vertical scroll bar)
	 * @see Scrollable#getScrollableTracksViewportHeight()
	 */
	boolean getScrollableTracksViewportHeight(Container parent);
}
