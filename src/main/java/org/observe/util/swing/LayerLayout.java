package org.observe.util.swing;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;

/** Simple layout that positions all components to the full size of the parent container */
public class LayerLayout extends AbstractLayout {
	@Override
	public boolean getScrollableTracksViewportWidth(Container parent) {
		return false;
	}

	@Override
	public boolean getScrollableTracksViewportHeight(Container parent) {
		return false;
	}

	@Override
	public boolean isShowingInvisible() {
		return false;
	}

	@Override
	public Dimension minimumLayoutSize(Dimension containerSize, Insets parentInsets, List<LayoutChild> components) {
		if (components.size() == 0)
			return new Dimension(0, 0);
		int maxMinW = Integer.MAX_VALUE, maxMinH = Integer.MAX_VALUE;
		for (LayoutChild c : components) {
			Dimension sz = c.getSize(-1);
			maxMinW = Math.min(maxMinW, sz.width);
			maxMinH = Math.min(maxMinH, sz.height);
		}
		maxMinW += parentInsets.left + parentInsets.right;
		maxMinH += parentInsets.top + parentInsets.bottom;
		return new Dimension(maxMinW, maxMinH);
	}

	@Override
	public Dimension preferredLayoutSize(Dimension containerSize, Insets parentInsets, List<LayoutChild> components) {
		if (components.size() == 0)
			return new Dimension(0, 0);
		int maxMinW = Integer.MAX_VALUE, maxMinH = Integer.MAX_VALUE;
		long sumPrefW = 0, sumPrefH = 0;
		int minMaxW = 0, minMaxH = 0;
		for (LayoutChild c : components) {
			Dimension sz = c.getSize(-1);
			maxMinW = Math.min(maxMinW, sz.width);
			maxMinH = Math.min(maxMinH, sz.height);

			sz = c.getSize(1);
			minMaxW = Math.max(minMaxW, sz.width);
			minMaxH = Math.max(minMaxH, sz.height);

			sz = c.getSize(0);
			sumPrefW += sz.width;
			sumPrefH += sz.height;
		}
		int prefW = (int) (sumPrefW / components.size());
		int prefH = (int) (sumPrefH / components.size());
		if (prefW < maxMinW)
			prefW = maxMinW;
		else if (prefW > minMaxW && minMaxW >= maxMinW)
			prefW = minMaxW;
		if (prefH < maxMinH)
			prefH = maxMinH;
		else if (prefH > minMaxH && minMaxH >= maxMinH)
			prefH = minMaxH;
		prefW += parentInsets.left + parentInsets.right;
		prefH += parentInsets.top + parentInsets.bottom;
		return new Dimension(prefW, prefH);
	}

	@Override
	public Dimension maximumLayoutSize(Dimension containerSize, Insets parentInsets, List<LayoutChild> components) {
		if (components.size() == 0)
			return new Dimension(0, 0);
		int minMaxW = 0, minMaxH = 0;
		for (LayoutChild c : components) {
			Dimension sz = c.getSize(1);
			minMaxW = Math.max(minMaxW, sz.width);
			minMaxH = Math.max(minMaxH, sz.height);
		}
		minMaxW = Math.max(minMaxW, minMaxW + parentInsets.left + parentInsets.right);
		minMaxH = Math.max(minMaxH, minMaxH + parentInsets.top + parentInsets.bottom);
		return new Dimension(minMaxW, minMaxH);
	}

	@Override
	public Rectangle[] layoutContainer(Dimension containerSize, Insets parentInsets, List<LayoutChild> components) {
		Rectangle[] bounds = new Rectangle[components.size()];
		int w = containerSize.width - parentInsets.left - parentInsets.right;
		int h = containerSize.height - parentInsets.top - parentInsets.bottom;
		Arrays.fill(bounds, new Rectangle(parentInsets.left, parentInsets.top, w, h));
		return bounds;
	}
}
