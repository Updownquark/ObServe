package org.observe.util.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager2;

/** Simple layout that positions all components to the full size of the parent container */
public class LayerLayout implements LayoutManager2 {
	@Override
	public Dimension minimumLayoutSize(Container parent) {
		if (parent.getComponentCount() == 0)
			return new Dimension(0, 0);
		int maxMinW = Integer.MAX_VALUE, maxMinH = Integer.MAX_VALUE;
		for (Component c : parent.getComponents()) {
			Dimension sz = c.getMinimumSize();
			maxMinW = Math.min(maxMinW, sz.width);
			maxMinH = Math.min(maxMinH, sz.height);
		}
		return new Dimension(maxMinW, maxMinH);
	}

	@Override
	public Dimension preferredLayoutSize(Container parent) {
		if (parent.getComponentCount() == 0)
			return new Dimension(0, 0);
		int maxMinW = Integer.MAX_VALUE, maxMinH = Integer.MAX_VALUE;
		long sumPrefW = 0, sumPrefH = 0;
		int minMaxW = 0, minMaxH = 0;
		for (Component c : parent.getComponents()) {
			Dimension sz = c.getMinimumSize();
			maxMinW = Math.min(maxMinW, sz.width);
			maxMinH = Math.min(maxMinH, sz.height);

			sz = c.getMaximumSize();
			minMaxW = Math.max(minMaxW, sz.width);
			minMaxH = Math.max(minMaxH, sz.height);

			sz = c.getPreferredSize();
			sumPrefW += sz.width;
			sumPrefH += sz.height;
		}
		int prefW = (int) (sumPrefW / parent.getComponentCount());
		int prefH = (int) (sumPrefH / parent.getComponentCount());
		if (prefW < maxMinW)
			prefW = maxMinW;
		else if (prefW > minMaxW && minMaxW >= maxMinW)
			prefW = minMaxW;
		if (prefH < maxMinH)
			prefH = maxMinH;
		else if (prefH > minMaxH && minMaxH >= maxMinH)
			prefH = minMaxH;
		return new Dimension(prefW, prefH);
	}

	@Override
	public Dimension maximumLayoutSize(Container parent) {
		if (parent.getComponentCount() == 0)
			return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
		int minMaxW = 0, minMaxH = 0;
		for (Component c : parent.getComponents()) {
			Dimension sz = c.getMaximumSize();
			minMaxW = Math.max(minMaxW, sz.width);
			minMaxH = Math.max(minMaxH, sz.height);
		}
		return new Dimension(minMaxW, minMaxH);
	}

	@Override
	public void layoutContainer(Container parent) {
		Dimension sz = parent.getSize();
		for (Component c : parent.getComponents()) {
			c.setLocation(0, 0);
			c.setSize(sz);
		}
	}

	@Override
	public void addLayoutComponent(String name, Component comp) {
	}

	@Override
	public void addLayoutComponent(Component comp, Object constraints) {
	}

	@Override
	public void removeLayoutComponent(Component comp) {
	}

	@Override
	public float getLayoutAlignmentX(Container target) {
		return 0;
	}

	@Override
	public float getLayoutAlignmentY(Container target) {
		return 0;
	}

	@Override
	public void invalidateLayout(Container target) {
	}
}
