package org.observe.util.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

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
	private boolean isStretchingEqual;

	private final Insets theMargin;
	private int thePadding;
	private boolean isShowingInvisible;

	/**
	 * @param vertical Whether the layout will align components in a column or a row
	 */
	public JustifiedBoxLayout(boolean vertical) {
		isVertical = vertical;
		theMainAlign = Alignment.LEADING;
		theCrossAlign = Alignment.JUSTIFIED;
		thePadding = 2;
		theMargin = new Insets(0, 0, 0, 0);
	}

	/** @return Whether this layout lays its components out vertically or horizontally */
	public boolean isVertical() {
		return isVertical;
	}

	/** @return The alignment strategy for the main axis */
	public Alignment getMainAlignment() {
		return theMainAlign;
	}

	/** @return The alignment strategy for the cross axis */
	public Alignment getCrossAlignment() {
		return theCrossAlign;
	}

	/** @return The padding that this layout attempts to insert between components */
	public int getPadding() {
		return thePadding;
	}

	/**
	 * @return Whether this layout stretches its components equally instead of favoring more flexible components (only with
	 *         {@link Alignment#JUSTIFIED justified} {@link #setMainAlignment(Alignment) main alignment})
	 */
	public boolean isStretchingEqual() {
		return isStretchingEqual;
	}

	/** @return Whether this layout allocates space to invisible components (false by default) */
	public boolean isShowingInvisible() {
		return isShowingInvisible;
	}

	/**
	 * @param showingInvisible Whether this layout should allocate space to invisible components (false by default)
	 * @return This layout
	 */
	public JustifiedBoxLayout setShowingInvisible(boolean showingInvisible) {
		this.isShowingInvisible = showingInvisible;
		return this;
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
	 * Sets this layout's {@link #setMainAlignment(Alignment) main alignment} to {@link Alignment#LEADING}
	 *
	 * @return This layout
	 */
	public JustifiedBoxLayout mainLeading() {
		return setMainAlignment(Alignment.LEADING);
	}

	/**
	 * Sets this layout's {@link #setMainAlignment(Alignment) main alignment} to {@link Alignment#TRAILING}
	 *
	 * @return This layout
	 */
	public JustifiedBoxLayout mainTrailing() {
		return setMainAlignment(Alignment.TRAILING);
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
	 * Sets this layout's {@link #setCrossAlignment(Alignment) main alignment} to {@link Alignment#LEADING}
	 *
	 * @return This layout
	 */
	public JustifiedBoxLayout crossLeading() {
		return setCrossAlignment(Alignment.LEADING);
	}

	/**
	 * Sets this layout's {@link #setCrossAlignment(Alignment) main alignment} to {@link Alignment#TRAILING}
	 *
	 * @return This layout
	 */
	public JustifiedBoxLayout crossTrailing() {
		return setCrossAlignment(Alignment.TRAILING);
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

	/**
	 * Sets insets to add to the container's own insets
	 *
	 * @param top The top margin
	 * @param left The left margin
	 * @param bottom The bottom margin
	 * @param right The right margin
	 * @return This layout
	 */
	public JustifiedBoxLayout setMargin(int top, int left, int bottom, int right) {
		theMargin.set(top, left, bottom, right);
		return this;
	}

	/**
	 * @param stretchEqual Whether this layout should stretch its components equally instead of favoring more flexible components (only with
	 *        {@link Alignment#JUSTIFIED justified} {@link #setMainAlignment(Alignment) main alignment})
	 * @return This layout
	 */
	public JustifiedBoxLayout stretchEqual(boolean stretchEqual) {
		isStretchingEqual = stretchEqual;
		return this;
	}

	@Override
	public Dimension preferredLayoutSize(Container parent) {
		int main = 0;
		int cross = 0;
		boolean first = true;
		for (Component comp : parent.getComponents()) {
			if (!isShowingInvisible && !comp.isVisible())
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
		int hIns = insets.left + insets.right + theMargin.left + theMargin.right;
		int vIns = insets.top + insets.bottom + theMargin.top + theMargin.bottom;
		return new Dimension((isVertical ? cross : main) + hIns, (isVertical ? main : cross) + vIns);
	}

	@Override
	public Dimension minimumLayoutSize(Container parent) {
		int main = 0;
		int cross = 0;
		boolean first = true;
		for (Component comp : parent.getComponents()) {
			if (!isShowingInvisible && !comp.isVisible())
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
		int hIns = insets.left + insets.right + theMargin.left + theMargin.right;
		int vIns = insets.top + insets.bottom + theMargin.top + theMargin.bottom;
		return new Dimension((isVertical ? cross : main) + hIns, (isVertical ? main : cross) + vIns);
	}

	@Override
	public Dimension maximumLayoutSize(Container parent) {
		if (theMainAlign != Alignment.JUSTIFIED && theCrossAlign != Alignment.JUSTIFIED)
			return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
		int main = 0;
		int cross = theCrossAlign == Alignment.JUSTIFIED ? 0 : Integer.MAX_VALUE;
		boolean first = true;
		for (Component comp : parent.getComponents()) {
			if (!isShowingInvisible && !comp.isVisible())
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
		if (theMainAlign != Alignment.JUSTIFIED)
			main = Integer.MAX_VALUE;
		Insets insets = parent.getInsets();
		int hIns = insets.left + insets.right + theMargin.left + theMargin.right;
		int vIns = insets.top + insets.bottom + theMargin.top + theMargin.bottom;
		return new Dimension((isVertical ? cross : main) + hIns, (isVertical ? main : cross) + vIns);
	}

	@Override
	public void layoutContainer(Container parent) {
		Dimension parentSize = parent.getSize();
		if (parentSize.width == 0 || parentSize.height == 0)
			return;

		Insets insets = parent.getInsets();
		int totalLength = isVertical//
			? insets.top + insets.bottom + theMargin.top + theMargin.bottom//
				: insets.left + insets.right + theMargin.left + theMargin.right;
		boolean first = true;
		List<Component> components = new ArrayList<>(parent.getComponentCount());
		for (Component comp : parent.getComponents()) {
			if (isShowingInvisible || comp.isVisible())
				components.add(comp);
		}
		int[] preferredMainSizes = new int[components.size()];
		int[] preferredCrossSizes = new int[components.size()];
		for (int i = 0; i < components.size(); i++) {
			if (first)
				first = false;
			else
				totalLength += thePadding;
			Dimension ps = components.get(i).getPreferredSize();
			preferredMainSizes[i] = getMain(ps);
			preferredCrossSizes[i] = getCross(ps);
			totalLength += preferredMainSizes[i];
		}

		int parentLength = getMain(parentSize);
		if (totalLength > parentLength)
			layoutStretched(parent, components, preferredMainSizes, preferredCrossSizes, totalLength, parentLength);
		else if (theMainAlign == Alignment.JUSTIFIED && totalLength != parentLength) {
			if (isStretchingEqual)
				layoutStretchedEqual(parent, components, preferredMainSizes, preferredCrossSizes, totalLength, parentLength);
			else
				layoutStretched(parent, components, preferredMainSizes, preferredCrossSizes, totalLength, parentLength);
		} else
			layoutDistributed(parent, components, preferredMainSizes, preferredCrossSizes, totalLength, parentLength);
	}

	private void layoutDistributed(Container parent, List<Component> components, int[] preferredMainSizes, int[] preferredCrossSizes,
		int preferredLength, int parentLength) {
		Insets insets = parent.getInsets();
		int margin = isVertical ? insets.top + theMargin.top : insets.left + theMargin.left;
		int pos = margin;
		int pad = thePadding;
		switch (theMainAlign) {
		case LEADING:
			break;
		case CENTER:
			pad = (parentLength - preferredLength) / (components.size() + 1);
			pos += pad;
			break;
		case TRAILING:
			pos = parentLength - preferredLength;
			break;
		case JUSTIFIED:
			break;
		}
		Rectangle bounds = new Rectangle();
		int parentCross = getCross(parent.getSize()) - (isVertical//
			? insets.left + insets.right + theMargin.left + theMargin.right//
				: insets.top + insets.bottom + theMargin.top + theMargin.bottom);
		int crossMargin = isVertical ? insets.left + theMargin.left : insets.top + theMargin.top;
		for (int i = 0; i < components.size(); i++) {
			int main = preferredMainSizes[i];
			int cross = Math.max(preferredCrossSizes[i], parentCross);
			setBound(true, bounds, pos, main);
			pos += getMain(bounds.getSize()) + pad;
			switch (theCrossAlign) {
			case LEADING:
				setBound(false, bounds, crossMargin, cross);
				break;
			case CENTER:
				setBound(false, bounds, crossMargin + (parentCross - cross) / 2, cross);
				break;
			case TRAILING:
				setBound(false, bounds, parentCross - cross - crossMargin, cross);
				break;
			case JUSTIFIED:
				setBound(false, bounds, crossMargin, parentCross);
				break;
			}
			components.get(i).setBounds(bounds);
		}
	}

	private void layoutStretched(Container parent, List<Component> components, int[] preferredMainSizes, int[] preferredCrossSizes,
		int preferredLength, int parentLength) {
		boolean shrink = parentLength < preferredLength;
		float[] sizes = new float[components.size()];
		long totalExtremeSize = 0;
		for (int i = 0; i < components.size(); i++) {
			int size = getMain(shrink ? components.get(i).getMinimumSize() : components.get(i).getMaximumSize());
			sizes[i] = size;
			totalExtremeSize += size;
		}

		Insets insets = parent.getInsets();
		int margin = isVertical ? insets.top + theMargin.top : insets.left + theMargin.left;
		float padding = thePadding;
		int extraLength = margin * 2 + thePadding * (sizes.length - 1);
		if (shrink == (totalExtremeSize + extraLength < parentLength)) {
			// The extreme size is more than sufficient to fit in or expand to the parent length
			// Temper the extreme size to the right amount
			float num = parentLength - preferredLength;
			float den = totalExtremeSize - (preferredLength - extraLength);
			for (int i = 0; i < sizes.length; i++)
				sizes[i] = preferredMainSizes[i] + num * (sizes[i] - preferredMainSizes[i]) / den;
		} else {
			// The extreme size is not big/small enough to fit in or expand to the parent length
			// Modify the margin and padding accordingly
			int diff = parentLength - (int) totalExtremeSize;
			if (!shrink) {
				float padMod = diff * 1.0f / (sizes.length + 1);
				margin += (int) padMod;
				padding += padMod;
			} else {
				float padMod = diff * 1.0f / (sizes.length + 1);
				if (margin > 0) {
					int padModInt = (int) padMod;
					margin += padModInt;
					diff -= padModInt * 2;
					if (margin < 0) {
						diff -= margin * 2;
						margin = 0;
					}
					padMod = diff * 1.0f / (sizes.length - 1);
				}
				if (padding > 0 && sizes.length > 1) {
					padding += padMod;
					diff = 0;
					if (padding < 0) {
						diff = Math.round(-padding * (sizes.length - 1));
						padding = 0;
					}
				}
				if (diff > 0 && margin > 0)
					margin = Math.max(margin - (diff + 1) / 2, 0);
			}
		}
		float fPos = margin;
		Rectangle bounds = new Rectangle();
		int parentCross = getCross(parent.getSize()) - (isVertical//
			? insets.left + insets.right + theMargin.left + theMargin.right//
				: insets.top + insets.bottom + theMargin.top + theMargin.bottom);
		int crossMargin = isVertical ? insets.left + theMargin.left : insets.top + theMargin.top;
		int pos = margin;
		for (int i = 0; i < components.size(); i++) {
			float newFPos = fPos + sizes[i];
			int mainSize = Math.round(newFPos) - pos;
			setBound(true, bounds, pos, mainSize);
			newFPos += padding;
			fPos = newFPos;
			pos = Math.round(newFPos);

			int cross = Math.max(preferredCrossSizes[i], parentCross);
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
			components.get(i).setBounds(bounds);
		}
	}

	private void layoutStretchedEqual(Container parent, List<Component> components, int[] preferredMainSizes, int[] preferredCrossSizes,
		int preferredLength, int parentLength) {
		Insets insets = parent.getInsets();
		int margin = isVertical ? insets.top + theMargin.top : insets.left + theMargin.left;
		int extraLenth = (isVertical//
			? insets.top + insets.bottom + theMargin.top + theMargin.bottom//
				: insets.left + insets.right + theMargin.left + theMargin.right)//
			+ thePadding * (components.size() - 1);
		int pos = margin;
		float stretch = (parentLength - extraLenth) * 1.0f / (preferredLength - extraLenth);
		Rectangle bounds = new Rectangle();
		int parentCross = getCross(parent.getSize()) - (isVertical//
			? insets.left + insets.right + theMargin.left + theMargin.right//
				: insets.top + insets.bottom + theMargin.top + theMargin.bottom);
		int crossMargin = isVertical ? insets.left + theMargin.left : insets.top + theMargin.top;
		for (int i = 0; i < components.size(); i++) {
			int main = Math.round(preferredMainSizes[i] * stretch);
			int cross = Math.max(preferredCrossSizes[i], parentCross);
			setBound(true, bounds, pos, main);
			pos += getMain(bounds.getSize()) + thePadding;
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
			components.get(i).setBounds(bounds);
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
