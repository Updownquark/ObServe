package org.observe.quick.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager2;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.observe.quick.base.QuickBorderLayout;
import org.observe.quick.base.QuickBorderLayout.Region;
import org.observe.quick.base.QuickSize;
import org.qommons.LambdaUtils;

/**
 * <p>
 * A layout that positions components along the edges of the container with an optional single component filling the rest of the space.
 * </p>
 * <p>
 * This layout improves on Swing's {@link BorderLayout} in several ways:
 * <ul>
 * <li>This layout can accept any number of components along each edge. Edge components are arranged such that they appear closer to the
 * opposite edge of the container than all previous components with the same edge. Edge components stretch out to the edges of the
 * container, or to the edge of previous components along those edges.</li>
 * <li>The amount of space that each component takes up can be specified by layout constraints, and can be specified relative to the edges
 * or size of the container.</li>
 * </ul>
 * </p>
 * <p>
 * Like Swing's border layout, only a single center component may be specified. If specified, the center component will occupy all the space
 * not taken up by the edge components. Size constraints on the center component are not allowed.
 * </p>
 */
public class BetterBorderLayout implements LayoutManager2 {
	/** Constraints on a child of a container using a {@link BetterBorderLayout} */
	public static class Constraints {
		/** The edge along which the component should be placed, or the center of the container */
		public final QuickBorderLayout.Region region;
		/**
		 * The absolute size for the component. If this is specified, {@link #minSize}, {@link #prefSize}, and {@link #maxSize} will be
		 * ignored.
		 */
		public final Supplier<QuickSize> size;
		/** The minimum size for the component, in pixels */
		public final Supplier<Integer> minSize;
		/** The preferred size for the component, in pixels */
		public final Supplier<Integer> prefSize;
		/** The maximum size of the component, in pixels */
		public final Supplier<Integer> maxSize;

		/**
		 * @param region The edge along which the component should be placed, or the center of the container
		 * @param size The absolute size for the component. If this is specified, {@link #minSize}, {@link #prefSize}, and {@link #maxSize}
		 *        will be ignored.
		 * @param minSize The minimum size for the component, in pixels
		 * @param prefSize The preferred size for the component, in pixels
		 * @param maxSize The maximum size of the component, in pixels
		 */
		public Constraints(Region region, Supplier<QuickSize> size, Supplier<Integer> minSize, Supplier<Integer> prefSize,
			Supplier<Integer> maxSize) {
			this.region = region;
			this.size = size;
			this.minSize = minSize;
			this.prefSize = prefSize;
			this.maxSize = maxSize;
		}

		Integer getSize(int type) {
			Supplier<Integer> sizeV;
			switch (type) {
			case -1:
				sizeV = minSize;
				break;
			case 0:
				sizeV = prefSize;
				break;
			case 1:
				sizeV = maxSize;
				break;
			default:
				throw new IllegalStateException("Expected -1, 0, or 1 for size type, not " + type);
			}
			return sizeV == null ? null : sizeV.get();
		}

		/**
		 * Parses constraints from text
		 *
		 * @param constraints The text to parse
		 * @return The parsed constraints
		 * @throws IllegalArgumentException If the constraints could not be parsed
		 */
		public static Constraints parse(String constraints) throws IllegalArgumentException {
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
		Function<Component, Dimension> sizeFn;
		if (type < 0)
			sizeFn = Component::getMinimumSize;
		else if (type == 0)
			sizeFn = Component::getPreferredSize;
		else
			sizeFn = Component::getMaximumSize;
		return layoutSize(parent, type, ci -> sizeFn.apply(parent.getComponent(ci)));
	}

	Dimension layoutSize(Container parent, int type, IntFunction<Dimension> componentSize) {
		List<QuickSize> hStacks = new ArrayList<>();
		List<QuickSize> vStacks = new ArrayList<>();
		QuickSize width = QuickSize.ZERO, height = QuickSize.ZERO;
		int centerIdx = -1;
		int compIdx = 0;
		for (Component c : parent.getComponents()) {
			if (!c.isVisible()) {
				compIdx++;
				continue;
			}
			Constraints constraints = theConstraints.get(c);
			if (constraints == null) {
				System.err.println("No constraints for component of border layout--region required");
				compIdx++;
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
				Dimension cs = componentSize.apply(compIdx);
				if (size == null)
					size = QuickSize.ofPixels(cs.height);
				int cross = cs.width;
				hStacks.add(width.plus(cross));
				height = height.plus(size);
				break;
			case East:
			case West:
				cs = componentSize.apply(compIdx);
				if (size == null)
					size = QuickSize.ofPixels(cs.width);
				cross = cs.height;
				vStacks.add(height.plus(cross));
				width = width.plus(size);
				break;
			default:
				if (centerIdx >= 0) {
					System.out.println("Multiple components found fulfulling center role--only the first will be used");
					break;
				}
				break; // Handle center last
			}
			compIdx++;
		}
		int maxW;
		int maxH;
		if (centerIdx >= 0) {
			Dimension d = componentSize.apply(centerIdx);
			if (d != null) {
				width = width.plus(d.width);
				height = height.plus(d.height);
			}
			maxW = width.resolveExponential();
			maxH = height.resolveExponential();
		} else if (type <= 0) {
			maxW = width.resolveExponential();
			maxH = height.resolveExponential();
		} else
			maxW = maxH = 0;
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

	@Override
	public void layoutContainer(Container parent) {
		Dimension parentSize = parent.getSize();
		if (parentSize.width == 0 || parentSize.height == 0)
			return;
		Component[] components = parent.getComponents();

		// First, compile all the relevante component sizes
		QuickBorderLayout.Region[] compRegions = new QuickBorderLayout.Region[components.length];
		Dimension[][] compSizes = new Dimension[components.length][3];
		int compIdx = 0;
		int maxSize = Integer.MAX_VALUE / components.length;
		for (Component c : components) {
			if (!c.isVisible()) {
				compIdx++;
				continue;
			}
			Constraints constraints = theConstraints.get(c);
			if (constraints == null) {
				compIdx++;
				continue;
			}
			compRegions[compIdx] = constraints.region;
			Dimension compMin = c.getMinimumSize();
			Dimension compPref = c.getPreferredSize();
			Dimension compMax = c.getMaximumSize();
			QuickSize size = constraints.size == null ? null : constraints.size.get();
			switch (constraints.region) {
			case North:
			case South:
				Integer evalSz = size == null ? null : size.evaluate(parentSize.height);
				Integer minSz = size == null ? constraints.getSize(-1) : evalSz;
				Integer prefSz = size == null ? constraints.getSize(0) : evalSz;
				Integer maxSz = size == null ? constraints.getSize(1) : evalSz;
				compSizes[compIdx][0] = minSz == null ? compMin : new Dimension(compMin.width, minSz);
				compSizes[compIdx][1] = prefSz == null ? compPref : new Dimension(compPref.width, prefSz);
				compSizes[compIdx][2] = maxSz == null ? compMax : new Dimension(compMax.width, maxSz);
				break;
			case East:
			case West:
				evalSz = size == null ? null : size.evaluate(parentSize.width);
				minSz = size == null ? constraints.getSize(-1) : evalSz;
				prefSz = size == null ? constraints.getSize(0) : evalSz;
				maxSz = size == null ? constraints.getSize(1) : evalSz;
				compSizes[compIdx][0] = minSz == null ? compMin : new Dimension(minSz, compMin.height);
				compSizes[compIdx][1] = prefSz == null ? compPref : new Dimension(prefSz, compPref.height);
				compSizes[compIdx][2] = maxSz == null ? compMax : new Dimension(maxSz, compMax.height);
				break;
			case Center:
				compSizes[compIdx][0] = compMin;
				compSizes[compIdx][1] = compPref;
				compSizes[compIdx][2] = compMax;
			}
			// Correct for maximum
			if (compSizes[compIdx][2].width > maxSize)
				compSizes[compIdx][2].width = maxSize;
			if (compSizes[compIdx][2].height > maxSize)
				compSizes[compIdx][2].height = maxSize;
			compIdx++;
		}

		// Now determine how much we need to stretch or squish in each dimension
		Dimension pref = layoutSize(parent, 0, ci -> compSizes[ci][1]);
		Dimension min = null, max = null;
		double wStretch;
		if (parentSize.width < pref.width) {
			min = layoutSize(parent, -1, ci -> compSizes[ci][0]);
			if (parentSize.width <= min.width)
				wStretch = -1;
			else
				wStretch = -(parentSize.width - min.width) * 1.0 / (pref.width - min.width);
		} else if (parentSize.width > pref.width) {
			max = layoutSize(parent, 1, ci -> compSizes[ci][2]);
			if (parentSize.width > max.width)
				wStretch = 1;
			else
				wStretch = (parentSize.width - pref.width) * 1.0 / (max.width - pref.width);
		} else
			wStretch = 0;
		double hStretch;
		if (parentSize.height < pref.height) {
			if (min == null)
				min = layoutSize(parent, -1, ci -> compSizes[ci][0]);
			if (parentSize.height <= min.height)
				hStretch = -1;
			else
				hStretch = -(parentSize.height - min.height) * 1.0 / (pref.height - min.height);
		} else if (parentSize.height > pref.height) {
			if (max == null)
				max = layoutSize(parent, 1, ci -> compSizes[ci][2]);
			if (parentSize.height > max.height)
				hStretch = 1;
			else
				hStretch = (parentSize.height - pref.height) * 1.0 / (max.height - pref.height);
		} else
			hStretch = 0;

		ToIntFunction<Integer> compWidth, compHeight;
		if (wStretch == 0) {
			compWidth = ci -> compSizes[ci][1].width;
		} else if (wStretch < 0) {
			compWidth = ci -> (int) Math.round(compSizes[ci][0].width + (compSizes[ci][1].width - compSizes[ci][0].width) * wStretch);
		} else {
			compWidth = ci -> (int) Math.round(compSizes[ci][1].width + (compSizes[ci][2].width - compSizes[ci][1].width) * wStretch);
		}
		if (hStretch == 0) {
			compHeight = ci -> compSizes[ci][1].height;
		} else if (hStretch < 0) {
			compHeight = ci -> (int) Math.round(compSizes[ci][0].height + (compSizes[ci][1].height - compSizes[ci][0].height) * wStretch);
		} else {
			compHeight = ci -> (int) Math.round(compSizes[ci][1].height + (compSizes[ci][2].height - compSizes[ci][1].height) * wStretch);
		}
		compIdx = 0;
		int left = 0, right = parentSize.width, top = 0, bottom = parentSize.height;
		Component center = null;
		for (int c = 0; c < components.length; c++) {
			if (!components[c].isVisible()) {
				compIdx++;
				continue;
			}
			Constraints constraints = theConstraints.get(components[c]);
			if (constraints == null) {
				compIdx++;
				continue;
			}
			int compSz;
			switch (constraints.region) {
			case North:
				compSz = compHeight.applyAsInt(compIdx);
				components[c].setBounds(left, top, right - left, compSz);
				top += compSz;
				break;
			case South:
				compSz = compHeight.applyAsInt(compIdx);
				bottom -= compSz;
				components[c].setBounds(left, bottom, right - left, compSz);
				break;
			case East:
				compSz = compWidth.applyAsInt(compIdx);
				right -= compSz;
				components[c].setBounds(right, top, compSz, bottom - top);
				break;
			case West:
				compSz = compWidth.applyAsInt(compIdx);
				components[c].setBounds(left, top, compSz, bottom - top);
				left += compSz;
				break;
			case Center:
				if (center == null)
					center = components[c];
				break;
			}
			compIdx++;
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
