package org.observe.util.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.awt.Rectangle;

/**
 * A very simple layout that lays out components in a column or a row, with support for 4 different {@link Alignment alignments} in each
 * dimension
 */
public class JustifiedBoxLayout implements LayoutManager2 {
	/** The types of alignment {@link JustifiedBoxLayout} supports */
	public static enum Alignment {
		/** Aligns components toward the left or top */
		LEADING,
		/** Aligns components toward the right or bottom */
		TRAILING,
		/** Aligns components with their preferred size (if possible) in the middle of the container */
		CENTER,
		/** Aligns components with the size of the container */
		JUSTIFIED;
	}
	private final boolean isVertical;

	private Alignment theMainAlign;
	private Alignment theCrossAlign;

	private int thePadding;

	/**
	 * @param vertical Whether the layout will align components in a column or a row
	 */
	public JustifiedBoxLayout(boolean vertical) {
		isVertical = vertical;
		theMainAlign = Alignment.LEADING;
		theCrossAlign = Alignment.JUSTIFIED;
		thePadding = 2;
	}

	public boolean isVertical() {
		return isVertical;
	}

	public Alignment getMainAlignment() {
		return theMainAlign;
	}

	public Alignment getCrossAlignment() {
		return theCrossAlign;
	}

	public int getPadding() {
		return thePadding;
	}

	/**
	 * @param mainAlignment The alignment along the main axis
	 * @return This layout, for chaining
	 */
	public JustifiedBoxLayout setMainAlignment(Alignment mainAlignment) {
		theMainAlign = mainAlignment;
		return this;
	}

	/**
	 * @param crossAlignment The alignment along the cross axis
	 * @return This layout, for chaining
	 */
	public JustifiedBoxLayout setCrossAlignment(Alignment crossAlignment) {
		theCrossAlign = crossAlignment;
		return this;
	}

	/**
	 * Sets this layout's {@link #setMainAlignment(Alignment) main alignment} to {@link Alignment#JUSTIFIED}
	 *
	 * @return This layout
	 */
	public JustifiedBoxLayout mainJustified() {
		return setMainAlignment(Alignment.JUSTIFIED);
	}

	/**
	 * Sets this layout's {@link #setMainAlignment(Alignment) main alignment} to {@link Alignment#CENTER}
	 *
	 * @return This layout
	 */
	public JustifiedBoxLayout mainCenter() {
		return setMainAlignment(Alignment.CENTER);
	}

	/**
	 * Sets this layout's {@link #setCrossAlignment(Alignment) cross alignment} to {@link Alignment#JUSTIFIED}
	 *
	 * @return This layout
	 */
	public JustifiedBoxLayout crossJustified() {
		return setCrossAlignment(Alignment.JUSTIFIED);
	}

	/**
	 * Sets this layout's {@link #setCrossAlignment(Alignment) cross alignment} to {@link Alignment#CENTER}
	 *
	 * @return This layout
	 */
	public JustifiedBoxLayout crossCenter() {
		return setCrossAlignment(Alignment.CENTER);
	}

	/**
	 * Sets the padding between components that this layout will use
	 *
	 * @param padding The padding
	 * @return This layout
	 */
	public JustifiedBoxLayout setPadding(int padding) {
		thePadding = padding;
		return this;
	}

	@Override
	public Dimension preferredLayoutSize(Container parent) {
		int main = 0;
		int cross = 0;
		boolean first = true;
		for (Component comp : parent.getComponents()) {
			if (!comp.isVisible())
				continue;
			if (first)
				first = false;
			else
				main += thePadding;
			Dimension pref = comp.getPreferredSize();
			main += getMain(pref);
			int compCross = getCross(pref);
			if (compCross > cross)
				cross = compCross;
		}
		Insets insets = parent.getInsets();
		int hIns = insets.left + insets.right;
		int vIns = insets.top + insets.bottom;
		return new Dimension((isVertical ? cross : main) + hIns, (isVertical ? main : cross) + vIns);
	}

	@Override
	public Dimension minimumLayoutSize(Container parent) {
		int main = 0;
		int cross = 0;
		boolean first = true;
		for (Component comp : parent.getComponents()) {
			if (!comp.isVisible())
				continue;
			if (first)
				first = false;
			else
				main += thePadding;
			Dimension min = comp.getMinimumSize();
			Dimension pref = comp.getPreferredSize();
			main += getMain(pref);
			int compCross = getCross(min);
			if (compCross > cross)
				cross = compCross;
		}
		Insets insets = parent.getInsets();
		int hIns = insets.left + insets.right;
		int vIns = insets.top + insets.bottom;
		return new Dimension((isVertical ? cross : main) + hIns, (isVertical ? main : cross) + vIns);
	}

	@Override
	public Dimension maximumLayoutSize(Container parent) {
		int main = 0;
		int cross = 0;
		boolean first = true;
		for (Component comp : parent.getComponents()) {
			if (!comp.isVisible())
				continue;
			if (first)
				first = false;
			else
				main += thePadding;
			Dimension max = comp.getMaximumSize();
			Dimension pref = comp.getPreferredSize();
			main += getMain(pref);
			int compCross = getCross(max);
			if (compCross > cross)
				cross = compCross;
		}
		Insets insets = parent.getInsets();
		int hIns = insets.left + insets.right;
		int vIns = insets.top + insets.bottom;
		return new Dimension((isVertical ? cross : main) + hIns, (isVertical ? main : cross) + vIns);
	}

	@Override
	public void layoutContainer(Container parent) {
		Insets insets = parent.getInsets();
		int compCount = 0;
		int totalLength = isVertical ? insets.top + insets.bottom : insets.left + insets.right;
		boolean first = true;
		for (Component comp : parent.getComponents()) {
			if (!comp.isVisible())
				continue;
			if (first)
				first = false;
			else
				totalLength += thePadding;
			compCount++;
			totalLength += getMain(comp.getPreferredSize());
		}

		int margin = isVertical ? insets.top : insets.left;
		int pos = margin;
		int pad = 0;
		float stretch = 1;
		switch (theMainAlign) {
		case LEADING:
			pos = 0;
			break;
		case CENTER:
			pad = (getMain(parent.getSize()) - totalLength) / (compCount + 1);
			pos = pad;
			break;
		case TRAILING:
			pos = getMain(parent.getSize()) - totalLength;
			break;
		case JUSTIFIED:
			pos = 0;
			stretch = getMain(parent.getSize()) * 1.0f / totalLength;
			break;
		}
		if (pos < margin)
			pos = margin;
		Rectangle bounds = new Rectangle();
		for (Component comp : parent.getComponents()) {
			if (!comp.isVisible())
				continue;
			Dimension ps = comp.getPreferredSize();
			int main = getMain(ps);
			int cross = getCross(ps);
			int parentCross = getCross(parent.getSize()) - (isVertical ? insets.left + insets.right : insets.top + insets.bottom);
			setBound(true, bounds, pos, Math.round(main * stretch));
			pos += getMain(bounds.getSize()) + pad;
			int crossMargin = isVertical ? insets.left : insets.top;
			switch (theCrossAlign) {
			case LEADING:
				setBound(false, bounds, crossMargin, cross);
				break;
			case CENTER:
				setBound(false, bounds, (parentCross - cross) / 2, cross);
				break;
			case TRAILING:
				setBound(false, bounds, parentCross - cross - crossMargin, cross);
				break;
			case JUSTIFIED:
				setBound(false, bounds, crossMargin, parentCross);
				break;
			}
			comp.setBounds(bounds);
			pos += thePadding;
		}
	}

	private int getMain(Dimension dim) {
		return isVertical ? dim.height : dim.width;
	}

	private int getCross(Dimension dim) {
		return isVertical ? dim.width : dim.height;
	}

	private void setBound(boolean main, Rectangle bounds, int pos, int size) {
		if (isVertical ^ !main) {
			bounds.y = pos;
			bounds.height = size;
		} else {
			bounds.x = pos;
			bounds.width = size;
		}
	}

	@Override
	public void addLayoutComponent(String name, Component comp) {}

	@Override
	public void removeLayoutComponent(Component comp) {}

	@Override
	public void addLayoutComponent(Component comp, Object constraints) {}

	@Override
	public float getLayoutAlignmentX(Container target) {
		return 0;
	}

	@Override
	public float getLayoutAlignmentY(Container target) {
		return 0;
	}

	@Override
	public void invalidateLayout(Container target) {}
}
