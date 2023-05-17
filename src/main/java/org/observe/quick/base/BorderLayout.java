package org.observe.quick.base;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager2;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.observe.quick.base.QuickBorderLayout.Region;
import org.qommons.LambdaUtils;

public class BorderLayout implements LayoutManager2 {
	public static class Constraints {
		public final QuickBorderLayout.Region region;
		public final Supplier<QuickSize> size;
		public final Supplier<Integer> minSize;
		public final Supplier<Integer> prefSize;
		public final Supplier<Integer> maxSize;

		public Constraints(Region region, Supplier<QuickSize> size, Supplier<Integer> minSize, Supplier<Integer> prefSize,
			Supplier<Integer> maxSize) {
			this.region = region;
			this.size = size;
			this.minSize = minSize;
			this.prefSize = prefSize;
			this.maxSize = maxSize;
		}

		Integer getSize(int type) {
			Supplier<Integer> size;
			switch (type) {
			case -1:
				size = minSize;
				break;
			case 0:
				size = prefSize;
				break;
			case 1:
				size = maxSize;
				break;
			default:
				throw new IllegalStateException("Expected -1, 0, or 1 for size type, not " + type);
			}
			return size == null ? null : size.get();
		}

		public static Constraints parse(String constraints) {
			constraints = constraints.trim().toLowerCase();
			QuickBorderLayout.Region region = null;
			for (QuickBorderLayout.Region r : QuickBorderLayout.Region.values()) {
				if (constraints.startsWith(r.name().toLowerCase())) {
					region = r;
					break;
				}
			}
			if (region == null)
				throw new IllegalArgumentException("Expected a region: '" + constraints + "'");
			constraints = constraints.substring(region.name().length()).trim();
			if (constraints.isEmpty())
				return new Constraints(region, null, null, null, null);
			else if (region == QuickBorderLayout.Region.Center)
				throw new IllegalArgumentException("No size allowed for center region");
			else {
				QuickSize size = QuickSize.parseSize(constraints);
				return new Constraints(region, LambdaUtils.constantSupplier(size, constraints, null), null, null, null);
			}
		}
	}

	private final Map<Component, Constraints> theConstraints = new HashMap<>();

	@Override
	public void addLayoutComponent(String name, Component comp) {
		addLayoutComponent(comp, null);
	}

	@Override
	public void addLayoutComponent(Component comp, Object constraints) {
		if (constraints instanceof Constraints)
			theConstraints.put(comp, (Constraints) constraints);
		else if (constraints instanceof QuickBorderLayout.Region)
			theConstraints.put(comp, new Constraints((QuickBorderLayout.Region) constraints,
				LambdaUtils.constantSupplier(null, "null", null), null, null, null));
		else if (constraints instanceof String)
			theConstraints.put(comp, Constraints.parse((String) constraints));
		else if (constraints != null)
			throw new IllegalArgumentException(
				"Unrecognized constraints of type " + constraints.getClass().getName() + " for layout " + getClass().getName());
	}

	@Override
	public void removeLayoutComponent(Component comp) {
		theConstraints.remove(comp);
	}

	@Override
	public Dimension minimumLayoutSize(Container parent) {
		return layoutSize(parent, -1);
	}

	@Override
	public Dimension preferredLayoutSize(Container parent) {
		return layoutSize(parent, 0);
	}

	@Override
	public Dimension maximumLayoutSize(Container parent) {
		return layoutSize(parent, 1);
	}

	Dimension layoutSize(Container parent, int type) {
		List<QuickSize> hStacks = new ArrayList<>();
		List<QuickSize> vStacks = new ArrayList<>();
		QuickSize width = QuickSize.ZERO, height = QuickSize.ZERO;
		boolean hasCenter = false;
		for (Component c : parent.getComponents()) {
			if (!c.isVisible())
				continue;
			Constraints constraints = theConstraints.get(c);
			if (constraints == null) {
				System.err.println("No constraints for component of border layout--region required");
				continue;
			}
			QuickSize size = constraints.size == null ? null : constraints.size.get();
			if (size == null) {
				Integer sz = constraints.getSize(type);
				if (sz != null)
					size = QuickSize.ofPixels(sz);
			}
			switch (constraints.region) {
			case North:
			case South:
				if (size == null)
					size = QuickSize.ofPixels(getComponentSize(c, type, true));
				int cross = getComponentSize(c, type, false);
				for (int i = 0; i < hStacks.size(); i++)
					hStacks.set(i, hStacks.get(i).plus(cross));
				height = height.plus(size);
				break;
			case East:
			case West:
				if (size == null)
					size = QuickSize.ofPixels(getComponentSize(c, type, false));
				cross = getComponentSize(c, type, true);
				for (int i = 0; i < vStacks.size(); i++)
					vStacks.set(i, vStacks.get(i).plus(cross));
				width = width.plus(size);
				break;
			default:
				if (hasCenter) {
					System.out.println("Multiple components found fulfulling center role--only the first will be used");
					break;
				}
				hasCenter = true;
				Dimension d;
				if (type < 0)
					d = c.getMinimumSize();
				else if (type == 0)
					d = c.getPreferredSize();
				else
					d = c.getMaximumSize();
				if (d == null)
					break;
				width = width.plus(d.width);
				height = height.plus(d.height);
				break;
			}
		}
		int maxW = width.resolveExponential();
		int maxH = height.resolveExponential();
		for (QuickSize w : hStacks)
			maxW = Math.max(maxW, w.resolveExponential());
		for (QuickSize h : vStacks)
			maxH = Math.max(maxH, h.resolveExponential());
		if (type > 0) {
			if (maxW == 0)
				maxW = Integer.MAX_VALUE;
			if (maxH == 0)
				maxH = Integer.MAX_VALUE;
		}
		return new Dimension(maxW, maxH);
	}

	private static int getComponentSize(Component c, int type, boolean vertical) {
		Dimension d;
		if (type < 0)
			d = c.getMinimumSize();
		else if (type == 0)
			d = c.getPreferredSize();
		else
			d = c.getMaximumSize();
		if (d == null)
			return -1;
		return vertical ? d.height : d.width;
	}

	@Override
	public void layoutContainer(Container parent) {
		Dimension parentSize = parent.getSize();
		if (parentSize.width == 0 || parentSize.height == 0)
			return;
		Component[] components = parent.getComponents();
		int[][] compSizeBounds = new int[components.length][3];
		int[] totalWidth = new int[3], totalHeight = new int[3];
		Component center = null;
		int compIdx = 0;
		for (Component c : components) {
			if (!c.isVisible())
				continue;
			Constraints constraints = theConstraints.get(c);
			if (constraints == null)
				continue;
			QuickSize size = constraints.size == null ? null : constraints.size.get();
			switch (constraints.region) {
			case North:
			case South:
				if (size != null) {
					int sz = size.evaluate(parentSize.height);
					compSizeBounds[compIdx][0] = sz;
					compSizeBounds[compIdx][1] = sz;
					compSizeBounds[compIdx][2] = sz;
				} else {
					Integer sz = constraints.getSize(-1);
					compSizeBounds[compIdx][0] = sz != null ? sz : getComponentSize(c, -1, true);
					sz = constraints.getSize(0);
					compSizeBounds[compIdx][1] = sz != null ? sz : getComponentSize(c, 0, true);
					sz = constraints.getSize(1);
					compSizeBounds[compIdx][2] = sz != null ? sz : getComponentSize(c, 1, true);
				}
				totalHeight[0] += compSizeBounds[compIdx][0];
				totalHeight[1] += compSizeBounds[compIdx][1];
				totalHeight[2] += compSizeBounds[compIdx][2];
				break;
			case East:
			case West:
				if (size != null) {
					int sz = size.evaluate(parentSize.width);
					compSizeBounds[compIdx][0] = sz;
					compSizeBounds[compIdx][1] = sz;
					compSizeBounds[compIdx][2] = sz;
				} else {
					Integer sz = constraints.getSize(-1);
					compSizeBounds[compIdx][0] = sz != null ? sz : getComponentSize(c, -1, false);
					sz = constraints.getSize(0);
					compSizeBounds[compIdx][1] = sz != null ? sz : getComponentSize(c, 0, false);
					sz = constraints.getSize(1);
					compSizeBounds[compIdx][2] = sz != null ? sz : getComponentSize(c, 1, false);
				}
				totalWidth[0] += compSizeBounds[compIdx][0];
				totalWidth[1] += compSizeBounds[compIdx][1];
				totalWidth[2] += compSizeBounds[compIdx][2];
				break;
			default:
				if (center != null)
					break;
				center = c;
				Dimension d = c.getMinimumSize();
				if (d != null) {
					totalWidth[0] += d.width;
					totalHeight[0] += d.height;
				}
				d = c.getPreferredSize();
				if (d != null) {
					totalWidth[1] += d.width;
					totalHeight[1] += d.height;
				}
				d = c.getMaximumSize();
				if (d != null) {
					totalWidth[2] += d.width;
					totalHeight[2] += d.height;
				}
				break;
			}
			compIdx++;
		}

		int[] compSizes = new int[components.length];

		if (parentSize.width <= totalWidth[0]) {
			float mult = totalWidth[0] * 1.0f / parentSize.width;
			for (int c = 0; c < components.length; c++) {
				if (!components[c].isVisible())
					continue;
				Constraints constraints = theConstraints.get(components[c]);
				if (constraints == null)
					continue;
				switch (constraints.region) {
				case East:
				case West:
					compSizes[c] = Math.round(compSizeBounds[c][0] * mult);
					break;
				default:
					break;
				}
			}
		} else if (parentSize.width <= totalWidth[1]) {
			float mult = (parentSize.width - totalWidth[0]) * 1.0f / (totalWidth[1] - totalWidth[0]);
			for (int c = 0; c < components.length; c++) {
				if (!components[c].isVisible())
					continue;
				Constraints constraints = theConstraints.get(components[c]);
				if (constraints == null)
					continue;
				switch (constraints.region) {
				case East:
				case West:
					compSizes[c] = compSizeBounds[c][0] + Math.round((compSizeBounds[c][1] - compSizeBounds[c][0]) * mult);
					break;
				default:
					break;
				}
			}
		} else {
			float mult = Math.min(1.0f, (parentSize.width - totalWidth[1]) * 1.0f / (totalWidth[2] - totalWidth[1]));
			for (int c = 0; c < components.length; c++) {
				if (!components[c].isVisible())
					continue;
				Constraints constraints = theConstraints.get(components[c]);
				if (constraints == null)
					continue;
				switch (constraints.region) {
				case East:
				case West:
					compSizes[c] = compSizeBounds[c][1] + Math.round((compSizeBounds[c][2] - compSizeBounds[c][1]) * mult);
					break;
				default:
					break;
				}
			}
		}
		if (parentSize.height <= totalHeight[0]) {
			float mult = totalHeight[0] * 1.0f / parentSize.height;
			for (int c = 0; c < components.length; c++) {
				if (!components[c].isVisible())
					continue;
				Constraints constraints = theConstraints.get(components[c]);
				if (constraints == null)
					continue;
				switch (constraints.region) {
				case North:
				case South:
					compSizes[c] = Math.round(compSizeBounds[c][0] * mult);
					break;
				default:
					break;
				}
			}
		} else if (parentSize.height <= totalHeight[1]) {
			float mult = (parentSize.height - totalHeight[0]) * 1.0f / (totalHeight[1] - totalHeight[0]);
			for (int c = 0; c < components.length; c++) {
				if (!components[c].isVisible())
					continue;
				Constraints constraints = theConstraints.get(components[c]);
				if (constraints == null)
					continue;
				switch (constraints.region) {
				case North:
				case South:
					compSizes[c] = compSizeBounds[c][0] + Math.round((compSizeBounds[c][1] - compSizeBounds[c][0]) * mult);
					break;
				default:
					break;
				}
			}
		} else {
			float mult = Math.min(1.0f, (parentSize.height - totalHeight[1]) * 1.0f / (totalHeight[2] - totalHeight[1]));
			for (int c = 0; c < components.length; c++) {
				if (!components[c].isVisible())
					continue;
				Constraints constraints = theConstraints.get(components[c]);
				if (constraints == null)
					continue;
				switch (constraints.region) {
				case North:
				case South:
					compSizes[c] = compSizeBounds[c][1] + Math.round((compSizeBounds[c][2] - compSizeBounds[c][1]) * mult);
					break;
				default:
					break;
				}
			}
		}
		int left = 0, right = parentSize.width, top = 0, bottom = parentSize.height;
		for (int c = 0; c < components.length; c++) {
			if (!components[c].isVisible())
				continue;
			Constraints constraints = theConstraints.get(components[c]);
			if (constraints == null)
				continue;
			switch (constraints.region) {
			case North:
				components[c].setBounds(left, top, right - left, compSizes[c]);
				top += compSizes[c];
				break;
			case South:
				bottom -= compSizes[c];
				components[c].setBounds(left, bottom, right - left, compSizes[c]);
				break;
			case East:
				right -= compSizes[c];
				components[c].setBounds(right, top, compSizes[c], bottom - top);
				break;
			case West:
				components[c].setBounds(left, top, compSizes[c], bottom - top);
				left += compSizes[c];
				break;
			default:
				break;
			}
		}
		if (center != null)
			center.setBounds(left, top, right - left, bottom - top);
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
