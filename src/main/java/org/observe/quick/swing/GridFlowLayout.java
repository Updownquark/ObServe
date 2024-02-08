package org.observe.quick.swing;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;

import org.observe.quick.base.QuickGridFlowLayout;
import org.observe.util.swing.AbstractLayout;
import org.observe.util.swing.JustifiedBoxLayout;

/**
 * Arranges components next to each other up to a maximum number of components before wrapping to a new row, similarly to a
 * {@link JustifiedBoxLayout} for a container containing a series of containers with {@link JustifiedBoxLayout}s of opposite
 * {@link JustifiedBoxLayout#isVertical() orientation}.
 */
public class GridFlowLayout implements AbstractLayout {
	private QuickGridFlowLayout.Edge thePrimaryStart;
	private QuickGridFlowLayout.Edge theSecondaryStart;
	private int theMaxRowCount;

	private JustifiedBoxLayout theRowsLayout;
	private JustifiedBoxLayout theRowLayout;

	/** Creates the layout */
	public GridFlowLayout() {
		thePrimaryStart = QuickGridFlowLayout.Edge.Left;
		theSecondaryStart = QuickGridFlowLayout.Edge.Top;
		theMaxRowCount = 1;

		theRowsLayout = new JustifiedBoxLayout(true);
		theRowLayout = new JustifiedBoxLayout(false).crossJustified();
	}

	/**
	 * @return The primary direction in which to lay out components:
	 *         <ul>
	 *         <li>'left' means the components will be laid out left-to-right starting at the left edge.</li>
	 *         <li>'right' means right-to-left starting at the right edge.</li>
	 *         <li>'top' means top-to-bottom starting at the top edge.</li>
	 *         <li>'bottom' means bottom-to-top starting at the bottom edge.</li>
	 *         </ul>
	 */
	public QuickGridFlowLayout.Edge getPrimaryStart() {
		return thePrimaryStart;
	}

	/**
	 * @param primaryStart The primary direction in which to lay out components
	 * @return This layout
	 * @see #getPrimaryStart()
	 */
	public GridFlowLayout setPrimaryStart(QuickGridFlowLayout.Edge primaryStart) {
		thePrimaryStart = primaryStart;
		if (theRowLayout.isVertical() != primaryStart.vertical) {
			theRowsLayout = new JustifiedBoxLayout(!primaryStart.vertical)//
				.setMainAlignment(theRowsLayout.getMainAlignment()).setCrossAlignment(theRowsLayout.getCrossAlignment())
				.setPadding(theRowsLayout.getPadding());
			theRowLayout = new JustifiedBoxLayout(primaryStart.vertical)//
				.setMainAlignment(theRowLayout.getMainAlignment()).setCrossAlignment(theRowLayout.getCrossAlignment())
				.setPadding(theRowLayout.getPadding());
		}
		return this;
	}

	/**
	 * @return The direction in which to lay out multiple rows of components:
	 *         <ul>
	 *         <li>'left' or 'top' means rows will be wrapped left-to-right (for 'top' or 'bottom' primary-start) or top-to-bottom (for
	 *         'left' or 'right' primary-direction).</li>
	 *         <li>'right' or 'bottom' means rows will be wrapped right-to-left (for 'top' or 'bottom' primary-start) or bottom-to-top (for
	 *         'left' or 'right' primary-direction).</li>
	 *         </ul>
	 *         Although a primary-direction of 'left' or 'right' would logically imply requiring only 'top' or 'bottom' and 'top' or
	 *         'bottom' primary would imply 'left' or 'right' secondary, this constraint is not enforced.
	 */
	public QuickGridFlowLayout.Edge getSecondaryStart() {
		return theSecondaryStart;
	}

	/**
	 * @param secondaryStart The direction in which to lay out multiple rows of components
	 * @return This layout
	 * @see #getSecondaryStart()
	 */
	public GridFlowLayout setSecondaryStart(QuickGridFlowLayout.Edge secondaryStart) {
		theSecondaryStart = secondaryStart;
		return this;
	}

	/** @return The maximum number of components to lay out in the primary direction before wrapping to a new row */
	public int getMaxRowCount() {
		return theMaxRowCount;
	}

	/**
	 * @param maxRowCount The maximum number of components to lay out in the primary direction before wrapping to a new row
	 * @return This layout
	 */
	public GridFlowLayout setMaxRowCount(int maxRowCount) {
		if (maxRowCount < 1)
			throw new IllegalArgumentException("max row count must be at least 1");
		theMaxRowCount = maxRowCount;
		return this;
	}

	/** @return The alignment strategy determining how all the components will share space in each row of the layout */
	public JustifiedBoxLayout.Alignment getMainAlign() {
		return theRowLayout.getMainAlignment();
	}

	/**
	 * @param mainAlign The alignment strategy determining how all the components will share space in each row of the layout
	 * @return This layout
	 */
	public GridFlowLayout setMainAlign(JustifiedBoxLayout.Alignment mainAlign) {
		theRowLayout.setMainAlignment(mainAlign);
		return this;
	}

	/** @return The alignment strategy for each component along the cross axis in each row of the layout */
	public JustifiedBoxLayout.Alignment getCrossAlign() {
		return theRowLayout.getCrossAlignment();
	}

	/**
	 * @param crossAlign The alignment strategy for each component along the cross axis in each row of the layout
	 * @return tHis layout
	 */
	public GridFlowLayout setCrossAlign(JustifiedBoxLayout.Alignment crossAlign) {
		theRowLayout.setCrossAlignment(crossAlign);
		return this;
	}

	/** @return The alignment strategy determining how all the rows in the layout will share space */
	public JustifiedBoxLayout.Alignment getRowAlign() {
		return theRowsLayout.getMainAlignment();
	}

	/**
	 * @param rowAlign The alignment strategy determining how all the rows in the layout will share space
	 * @return This layout
	 */
	public GridFlowLayout setRowAlign(JustifiedBoxLayout.Alignment rowAlign) {
		theRowsLayout.setMainAlignment(rowAlign);
		return this;
	}

	/** @return How much space to put between components in a row and between rows */
	public int getPadding() {
		return theRowsLayout.getPadding();
	}

	/**
	 * @param padding How much space to put between components in a row and between rows
	 * @return This layout
	 */
	public GridFlowLayout setPadding(int padding) {
		theRowsLayout.setPadding(padding);
		theRowLayout.setPadding(padding);
		return this;
	}

	@Override
	public boolean getScrollableTracksViewportWidth(Container parent) {
		switch (thePrimaryStart) {
		case Left:
		case Right:
			return true;
		default:
			return false;
		}
	}

	@Override
	public boolean getScrollableTracksViewportHeight(Container parent) {
		switch (thePrimaryStart) {
		case Top:
		case Bottom:
			return true;
		default:
			return false;
		}
	}

	@Override
	public boolean isShowingInvisible() {
		return false;
	}

	static class LayoutRow extends AbstractList<LayoutChild> implements LayoutChild {
		private final boolean isVertical;
		private final int thePadding;
		public final List<LayoutChild> components;
		public final Dimension[][] sizes;

		LayoutRow(boolean vertical, int padding, List<LayoutChild> components) {
			isVertical = vertical;
			thePadding = padding;
			this.components = components;
			sizes = new Dimension[components.size()][3];
		}

		@Override
		public Dimension getSize(int type) {
			int w = 0, h = 0;
			for (int c = 0; c < sizes.length; c++) {
				if (sizes[c][type + 1] == null) {
					if (c < sizes.length)
						sizes[c][type + 1] = components.get(c).getSize(type);
					else
						break;
				}
				if (isVertical) {
					if (sizes[c][type + 1].width > w)
						w = sizes[c][type + 1].width;
					if (c > 0)
						h += thePadding;
					h += sizes[c][type + 1].height;
				} else {
					if (c > 0)
						w += thePadding;
					w += sizes[c][type + 1].width;
					if (sizes[c][type + 1].height > h)
						h = sizes[c][type + 1].height;
				}
			}
			return new Dimension(w, h);
		}

		@Override
		public int size() {
			return components.size();
		}

		@Override
		public LayoutChild get(int index) {
			return new LayoutChild() {
				@Override
				public Dimension getSize(int type) {
					// Sizes are only asked for once, so no need to cache it, just use the cache if available
					if (sizes[index][type + 1] != null)
						return sizes[index][type + 1];
					else
						return components.get(index).getSize(type);
				}
			};
		}
	}

	private LayoutRow[] makeRows(boolean vertical, List<LayoutChild> components) {
		LayoutRow[] rows = new LayoutRow[rowCount(components.size(), theMaxRowCount)];
		int offset = 0;
		for (int r = 0; r < rows.length; r++) {
			int end = offset + theMaxRowCount;
			if (components.size() < end)
				end = components.size();
			rows[r] = new LayoutRow(vertical, theRowLayout.getPadding(), components.subList(offset, end));
			offset = end;
		}
		return rows;
	}

	private static int rowCount(int components, int maxRowCount) {
		int rows = components / maxRowCount;
		if ((components % maxRowCount) != 0)
			rows++;
		return rows;
	}

	private Insets reverseInsets(Insets insets, boolean horizontal, boolean vertical) {
		if (horizontal) {
			if (vertical)
				return new Insets(insets.bottom, insets.right, insets.top, insets.left);
			else
				return new Insets(insets.top, insets.right, insets.top, insets.left);
		} else if (vertical)
			return new Insets(insets.bottom, insets.left, insets.top, insets.right);
		else
			return insets;
	}

	@Override
	public Dimension minimumLayoutSize(Dimension containerSize, Insets parentInsets, List<LayoutChild> components) {
		return theRowsLayout.minimumLayoutSize(containerSize, parentInsets, //
			Arrays.asList(makeRows(thePrimaryStart.vertical, components)));
	}

	@Override
	public Dimension preferredLayoutSize(Dimension containerSize, Insets parentInsets, List<LayoutChild> components) {
		return theRowsLayout.preferredLayoutSize(containerSize, parentInsets, //
			Arrays.asList(makeRows(thePrimaryStart.vertical, components)));
	}

	@Override
	public Dimension maximumLayoutSize(Dimension containerSize, Insets parentInsets, List<LayoutChild> components) {
		return theRowsLayout.maximumLayoutSize(containerSize, parentInsets, //
			Arrays.asList(makeRows(thePrimaryStart.vertical, components)));
	}

	@Override
	public Rectangle[] layoutContainer(Dimension containerSize, Insets parentInsets, List<LayoutChild> components) {
		// This method arranges its components in rows and then pretends that it's a JustifiedBoxLayout
		// with each row being a container also managed by a JustifiedBoxLayout

		// First, some setup
		boolean reverseHorizontal = thePrimaryStart.vertical ? theSecondaryStart.reversed : thePrimaryStart.reversed;
		boolean reverseVertical = thePrimaryStart.vertical ? thePrimaryStart.reversed : theSecondaryStart.reversed;

		// Now, arrange the components in rows.
		LayoutRow[] rows = makeRows(thePrimaryStart.vertical, components);

		// Now use a JustifiedBoxLayout to determine the arrangement the rows in the container
		Rectangle[] rowBounds = theRowsLayout.layoutContainer(containerSize,
			reverseInsets(parentInsets, reverseHorizontal, reverseVertical), Arrays.asList(rows));
		reverseRowBounds(containerSize, parentInsets, rowBounds, reverseHorizontal, reverseVertical);

		// Now we use another JustifiedBoxLayout to arrange the components in each row
		Insets zero = new Insets(0, 0, 0, 0);
		Rectangle[] compBounds = new Rectangle[components.size()];
		int compIdx = 0;
		for (int r = 0; r < rows.length; r++) {
			Dimension rowSize = rowBounds[r].getSize();
			Rectangle[] rowCompBounds = theRowLayout.layoutContainer(rowSize, zero, rows[r]);
			reverseRowBounds(rowSize, zero, rowCompBounds, reverseHorizontal, reverseVertical);
			for (int c = 0; c < rowCompBounds.length; c++) {
				rowCompBounds[c].translate(rowBounds[r].x, rowBounds[r].y);
				compBounds[compIdx++] = rowCompBounds[c];
			}
		}
		return compBounds;
	}

	private static void reverseRowBounds(Dimension containerSize, Insets parentInsets, Rectangle[] bounds, boolean horizontal,
		boolean vertical) {
		if (!vertical && !horizontal)
			return;
		for (Rectangle bound : bounds) {
			if (horizontal)
				bound.x = parentInsets.left + containerSize.width - parentInsets.right - bound.x - bound.width;
			if (vertical)
				bound.y = parentInsets.top + containerSize.height - parentInsets.bottom - bound.y - bound.height;
		}
	}
}
