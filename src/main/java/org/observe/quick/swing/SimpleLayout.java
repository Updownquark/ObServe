package org.observe.quick.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.observe.quick.QuickSize;
import org.observe.util.swing.PanelPopulation;
import org.qommons.BreakpointHere;
import org.qommons.LambdaUtils;
import org.qommons.TriFunction;

/**
 * <p>
 * Not really all that "simple", but I couldn't think of a better name.
 * </p>
 * <p>
 * This class supports dynamic constraints for each component's edges, center, and size independently, and positions can be specified
 * relative to the container's edges or its size.
 * </p>
 * <p>
 * While this class provides extreme control for positioning each widget, it does not provide any ability to position elements relative to
 * each other.
 * </p>
 */
public class SimpleLayout implements LayoutManager2 {
	/** The component constraints for children of a container using a {@link SimpleLayout} */
	public static class SimpleConstraints {
		/** The horizontal component of this set of constraints */
		public final DimensionConstraints h;
		/** The vertical component of this set of constraints */
		public final DimensionConstraints v;

		/**
		 * @param h The horizontal constraints for the component
		 * @param v The vertical constraints for the component
		 */
		public SimpleConstraints(DimensionConstraints h, DimensionConstraints v) {
			this.h = h;
			this.v = v;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			String hStr = h.toString();
			if (!hStr.isEmpty())
				str.append("h:{").append(hStr).append("}");
			String vStr = v.toString();
			if (!vStr.isEmpty()) {
				if (!hStr.isEmpty())
					str.append(',');
				str.append("v:{").append(vStr).append("}");
			} else if (hStr.isEmpty())
				return "(empty)";
			return str.toString();
		}

		private static final Pattern CONSTRAINT_PATTERN = Pattern.compile("(?<type>[a-zA-Z]+)[=:](?<value>.+)");

		/**
		 * Parses constraints from text
		 *
		 * @param constraints The text to parse
		 * @return The parsed constraints
		 * @throws IllegalArgumentException If the constraints could not be parsed
		 */
		public static SimpleConstraints parse(String constraints) throws IllegalArgumentException {
			QuickSize left = null;
			QuickSize hCenter = null;
			QuickSize right = null;
			QuickSize top = null;
			QuickSize vCenter = null;
			QuickSize bottom = null;
			QuickSize width = null;
			Integer minWidth = null, prefWidth = null, maxWidth = null;
			QuickSize height = null;
			Integer minHeight = null, prefHeight = null, maxHeight = null;
			for (String c : constraints.split("\\s+")) {
				Matcher m = CONSTRAINT_PATTERN.matcher(c);
				if (!m.matches())
					throw new IllegalArgumentException("Unrecognized constraint: \"" + c + "\"--expecting \"type[=:]value\"");
				String type = m.group("type");
				switch (type.toLowerCase()) {
				case "left":
				case "x":
					if (left != null)
						throw new IllegalArgumentException("left/x specified twice as " + left + " and " + m.group("value"));
					left = QuickSize.parsePosition(m.group("value"));
					break;
				case "h-center":
					if (hCenter != null)
						throw new IllegalArgumentException("h-center specified twice as " + hCenter + " and " + m.group("value"));
					hCenter = QuickSize.parsePosition(m.group("value"));
					break;
				case "right":
					if (right != null)
						throw new IllegalArgumentException("right specified twice as " + right + " and " + m.group("value"));
					right = QuickSize.parsePosition(m.group("value"));
					break;
				case "top":
				case "y":
					if (top != null)
						throw new IllegalArgumentException("top/y specified twice as " + top + " and " + m.group("value"));
					top = QuickSize.parsePosition(m.group("value"));
					break;
				case "v-center":
					if (vCenter != null)
						throw new IllegalArgumentException("v-center specified twice as " + vCenter + " and " + m.group("value"));
					vCenter = QuickSize.parsePosition(m.group("value"));
					break;
				case "bottom":
					if (bottom != null)
						throw new IllegalArgumentException("bottom specified twice as " + bottom + " and " + m.group("value"));
					bottom = QuickSize.parsePosition(m.group("value"));
					break;
				case "min-width":
				case "min-w":
					if (minWidth != null)
						throw new IllegalArgumentException("min-width specified twice as " + minWidth + " and " + m.group("value"));
					else if (width != null && width.percent == 0.0f)
						throw new IllegalArgumentException("min-width specified, but width is absolute");
					QuickSize size = QuickSize.parsePosition(m.group("value"));
					if (size.percent != 0.0f)
						throw new IllegalArgumentException("min-width must be specified in pixels");
					minWidth = size.pixels;
					break;
				case "pref-width":
				case "pref-w":
					if (prefWidth != null)
						throw new IllegalArgumentException("pref-width specified twice as " + prefWidth + " and " + m.group("value"));
					else if (width != null && width.percent == 0.0f)
						throw new IllegalArgumentException("pref-width specified, but width is absolute");
					size = QuickSize.parsePosition(m.group("value"));
					if (size.percent != 0.0f)
						throw new IllegalArgumentException("pref-width must be specified in pixels");
					prefWidth = size.pixels;
					break;
				case "max-width":
				case "max-w":
					if (maxWidth != null)
						throw new IllegalArgumentException("max-width specified twice as " + maxWidth + " and " + m.group("value"));
					else if (width != null && width.percent == 0.0f)
						throw new IllegalArgumentException("max-width specified, but width is absolute");
					size = QuickSize.parsePosition(m.group("value"));
					if (size.percent != 0.0f)
						throw new IllegalArgumentException("max-width must be specified in pixels");
					maxWidth = size.pixels;
					break;
				case "min-height":
				case "min-h":
					if (minHeight != null)
						throw new IllegalArgumentException("min-height specified twice as " + minHeight + " and " + m.group("value"));
					else if (height != null && height.percent == 0.0f)
						throw new IllegalArgumentException("min-height specified, but height is absolute");
					size = QuickSize.parsePosition(m.group("value"));
					if (size.percent != 0.0f)
						throw new IllegalArgumentException("min-height must be specified in pixels");
					minHeight = size.pixels;
					break;
				case "pref-height":
				case "pref-h":
					if (prefHeight != null)
						throw new IllegalArgumentException("pref-height specified twice as " + prefHeight + " and " + m.group("value"));
					else if (height != null && height.percent == 0.0f)
						throw new IllegalArgumentException("pref-height specified, but height is absolute");
					size = QuickSize.parsePosition(m.group("value"));
					if (size.percent != 0.0f)
						throw new IllegalArgumentException("pref-height must be specified in pixels");
					prefHeight = size.pixels;
					break;
				case "max-height":
				case "max-h":
					if (maxHeight != null)
						throw new IllegalArgumentException("max-height specified twice as " + maxHeight + " and " + m.group("value"));
					else if (height != null && height.percent == 0.0f)
						throw new IllegalArgumentException("max-height specified, but height is absolute");
					size = QuickSize.parsePosition(m.group("value"));
					if (size.percent != 0.0f)
						throw new IllegalArgumentException("max-height must be specified in pixels");
					maxHeight = size.pixels;
					break;
				case "width":
				case "w":
					if (width != null)
						throw new IllegalArgumentException("width specified twice as " + width + " and " + m.group("value"));
					width = QuickSize.parsePosition(m.group("value"));
					if (width.percent != 0.0f) {
						if (minWidth != null)
							throw new IllegalArgumentException("min-width specified, but width is absolute");
						else if (prefWidth != null)
							throw new IllegalArgumentException("pref-width specified, but width is absolute");
						else if (maxWidth != null)
							throw new IllegalArgumentException("max-width specified, but width is absolute");
					}
					break;
				case "height":
				case "h":
					if (height != null)
						throw new IllegalArgumentException("height specified twice as " + width + " and " + m.group("value"));
					height = QuickSize.parsePosition(m.group("value"));
					if (height.percent != 0.0f) {
						if (minHeight != null)
							throw new IllegalArgumentException("min-height specified, but width is absolute");
						else if (prefHeight != null)
							throw new IllegalArgumentException("pref-height specified, but width is absolute");
						else if (maxHeight != null)
							throw new IllegalArgumentException("max-height specified, but width is absolute");
					}
					break;
				default:
					throw new IllegalArgumentException("Unrecognized constraint type: " + type);
				}
			}
			QuickSize fLeft = left, fHCenter = hCenter, fRight = right, fTop = top, fVCenter = vCenter, fBottom = bottom;
			QuickSize fWidth = width, fHeight = height;
			Integer fMinW = minWidth, fPrefW = prefWidth, fMaxW = maxWidth, fMinH = minHeight, fPrefH = prefHeight, fMaxH = maxHeight;
			DimensionConstraints h = new DimensionConstraints(//
				fLeft == null ? null : LambdaUtils.constantSupplier(fLeft, fLeft::toString, fLeft), //
					fHCenter == null ? null : LambdaUtils.constantSupplier(fHCenter, fHCenter::toString, fHCenter), //
						fRight == null ? null : LambdaUtils.constantSupplier(fRight, fRight::toString, fRight), //
							fWidth == null ? null : LambdaUtils.constantSupplier(fWidth, fWidth::toString, fWidth), //
								fMinW == null ? null : LambdaUtils.constantSupplier(fMinW, fMinW::toString, fMinW), //
									fPrefW == null ? null : LambdaUtils.constantSupplier(fPrefW, fPrefW::toString, fPrefW), //
										fMaxW == null ? null : LambdaUtils.constantSupplier(fMaxW, fMaxW::toString, fMaxW));
			DimensionConstraints v = new DimensionConstraints(//
				fTop == null ? null : LambdaUtils.constantSupplier(fTop, fTop::toString, fTop), //
					fVCenter == null ? null : LambdaUtils.constantSupplier(fVCenter, fVCenter::toString, fVCenter), //
						fBottom == null ? null : LambdaUtils.constantSupplier(fBottom, fBottom::toString, fBottom), //
							fHeight == null ? null : LambdaUtils.constantSupplier(fHeight, fHeight::toString, fHeight), //
								fMinH == null ? null : LambdaUtils.constantSupplier(fMinH, fMinH::toString, fMinH), //
									fPrefH == null ? null : LambdaUtils.constantSupplier(fPrefH, fPrefH::toString, fPrefH), //
										fMaxH == null ? null : LambdaUtils.constantSupplier(fMaxH, fMaxH::toString, fMaxH));
			return new SimpleConstraints(h, v);
		}
	}

	/** The constraints on a child of a container using a {@link SimpleLayout} in one dimension */
	public static class DimensionConstraints {
		/** The position for the leading edge (left or top) of the component */
		public final Supplier<QuickSize> leading;
		/** The position for the horizontal or vertical center of the component */
		public final Supplier<QuickSize> center;
		/** The position for the trailing edge (right or bottom) of the component */
		public final Supplier<QuickSize> trailing;

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
		 * @param leading The position for the leading edge (left or top) of the component
		 * @param center The position for the horizontal or vertical center of the component
		 * @param trailing The position for the trailing edge (right or bottom) of the component
		 * @param size The absolute size for the component. If this is specified, {@link #minSize}, {@link #prefSize}, and {@link #maxSize}
		 *        will be ignored.
		 * @param minSize The minimum size for the component, in pixels
		 * @param prefSize The preferred size for the component, in pixels
		 * @param maxSize The maximum size of the component, in pixels
		 */
		public DimensionConstraints(Supplier<QuickSize> leading, Supplier<QuickSize> center, Supplier<QuickSize> trailing,
			Supplier<QuickSize> size, Supplier<Integer> minSize, Supplier<Integer> prefSize, Supplier<Integer> maxSize) {
			this.leading = leading;
			this.center = center;
			this.trailing = trailing;
			this.size = size;
			this.minSize = minSize;
			this.prefSize = prefSize;
			this.maxSize = maxSize;
		}

		QuickSize getPos(int type) {
			Supplier<QuickSize> pos;
			if (type < 0)
				pos = leading;
			else if (type == 0)
				pos = center;
			else
				pos = trailing;
			return pos == null ? null : pos.get();
		}

		QuickSize getSize() {
			return size == null ? null : size.get();
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

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			append(str, "lead", leading);
			append(str, "center", center);
			append(str, "trail", trailing);
			append(str, "size", size);
			append(str, "min-size", minSize);
			append(str, "pref-size", prefSize);
			append(str, "max-size", maxSize);
			return str.toString();
		}

		private static void append(StringBuilder str, String label, Supplier<?> value) {
			if (value == null)
				return;
			Object v = value.get();
			if (v == null)
				return;
			if (str.length() > 0)
				str.append(", ");
			str.append(label).append('=').append(v);
		}
	}

	private SimpleConstraints theContainerConstraints;
	private final Map<Component, SimpleConstraints> theConstraints = new HashMap<>();

	/** @return The size constraints on the container */
	public SimpleConstraints getContainerConstraints() {
		return theContainerConstraints;
	}

	/**
	 * @param containerConstraints The size constraints for the container
	 * @return This layout
	 */
	public SimpleLayout setContainerConstraints(SimpleConstraints containerConstraints) {
		theContainerConstraints = containerConstraints;
		return this;
	}

	@Override
	public void addLayoutComponent(String name, Component comp) {
		addLayoutComponent(comp, null);
	}

	@Override
	public void addLayoutComponent(Component comp, Object constraints) {
		if (constraints instanceof SimpleConstraints)
			theConstraints.put(comp, (SimpleConstraints) constraints);
		else if (constraints instanceof String)
			theConstraints.put(comp, SimpleConstraints.parse((String) constraints));
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
		boolean debugging = PanelPopulation.isDebugging(parent.getName(), "simple-layout", "size");
		if (debugging && parent.getName().contains(":break"))
			BreakpointHere.breakpoint();
		boolean print = debugging && parent.getName().contains("print");
		Dimension size = new Dimension();
		Integer cw = null, ch = null;
		if (theContainerConstraints != null) {
			int h = getSizeFor(parent, true, type, theContainerConstraints.v, (__, ___, ____) -> -1);
			if (h >= 0) {
				ch = h;
				size.height = h;
			}
			int w = getSizeFor(parent, false, type, theContainerConstraints.h, (__, ___, ____) -> -1);
			if (w >= 0) {
				cw = w;
				size.width = w;
			}
		}
		if (print)
			System.out.println("Simple layout size[" + type + "]:");
		Insets insets = parent.getInsets();
		for (Component c : parent.getComponents()) {
			if (!c.isVisible())
				continue;
			SimpleConstraints constraints = theConstraints.get(c);
			if (cw == null) {
				int w = getSizeFor(c, false, type, constraints.h, SimpleLayout::getComponentSize);
				if (w > size.width)
					size.width = w;
			}
			if (ch == null) {
				int h = getSizeFor(c, true, type, constraints.v, SimpleLayout::getComponentSize);
				if (h > size.height)
					size.height = h;
			}
			if (print)
				System.out.println("\t" + c.getClass().getSimpleName() + ": " + constraints + ": [" + size.width + "x" + size.height + "]");
		}
		if (cw == null)
			size.width = PanelPopulation.add(size.width, insets.left + insets.right);
		if (ch == null)
			size.height = PanelPopulation.add(size.height, insets.top + insets.bottom);
		if (print)
			System.out.println("\tOverall: [" + size.width + "x" + size.height + "]");
		return size;
	}

	private int getSizeFor(Component c, boolean vertical, int type, DimensionConstraints constraints,
		TriFunction<Component, Boolean, Integer, Integer> unconstrained) {
		if (constraints == null)
			return unconstrained.apply(c, vertical, type);
		QuickSize trail = constraints.getPos(1);
		if (trail != null && trail.percent == 0.0f)
			return trail.pixels;
		QuickSize lead = constraints.getPos(-1);
		if (lead != null && lead.percent == 100.0f)
			return lead.pixels;
		QuickSize size = constraints.getSize();
		Integer absSize = null;
		float relSize = 0.0f;
		if (size != null) {
			absSize = size.pixels;
			relSize = size.percent;
		} else {
			absSize = constraints.getSize(type);
			if (absSize == null)
				absSize = unconstrained.apply(c, vertical, type);
		}
		if (lead != null) {
			absSize += Math.abs(lead.pixels);
			relSize += lead.percent;
		}
		if (trail != null) {
			absSize += Math.abs(trail.pixels);
			relSize += 100f - trail.percent;
		}
		if (absSize.intValue() > 0 && relSize != 0)
			return new QuickSize(relSize, absSize).resolveExponential();
		else if (type > 0)
			return Integer.MAX_VALUE;
		else if (absSize.intValue() != 0)
			return absSize;
		else
			return 0;
	}

	private static int getComponentSize(Component c, boolean vertical, int type) {
		Dimension size;
		if (type < 0)
			size = c.getMinimumSize();
		else if (type == 0)
			size = c.getPreferredSize();
		else
			size = c.getMaximumSize();
		if (size == null)
			return 0;
		return vertical ? size.height : size.width;
	}

	@Override
	public void layoutContainer(Container parent) {
		Dimension parentSize = parent.getSize();
		if (parentSize.width == 0 || parentSize.height == 0)
			return;
		Insets insets = parent.getInsets();
		parentSize.width -= insets.left + insets.right;
		parentSize.height -= insets.top + insets.bottom;
		boolean debugging = PanelPopulation.isDebugging(parent.getName(), "simple-layout", "layout");
		if (debugging && parent.getName().contains("break"))
			BreakpointHere.breakpoint();
		Rectangle childBounds = new Rectangle();
		boolean print = debugging && parent.getName().contains("print");
		if (print)
			System.out.println("Simple layout: [" + parentSize.width + "x" + parentSize.height + "]");
		for (Component c : parent.getComponents()) {
			if (!c.isVisible())
				continue;
			SimpleConstraints constraints = theConstraints.get(c);
			if (constraints == null) {
				Dimension ps = c.getPreferredSize();
				if (ps != null)
					c.setBounds(0, 0, ps.width, ps.height);
				else
					c.setBounds(0, 0, 0, 0);
				continue;
			}
			layoutChild(c, constraints.v, parentSize.height, true, insets.top, childBounds);
			layoutChild(c, constraints.h, parentSize.width, false, insets.left, childBounds);
			if (print)
				System.out.println("\t" + childBounds);
			c.setBounds(childBounds);
		}
	}

	private void layoutChild(Component c, DimensionConstraints constraints, int parentSize, boolean vertical, int offset,
		Rectangle childBounds) {
		QuickSize lead = constraints.getPos(-1);
		QuickSize center = constraints.getPos(0);
		QuickSize trail = constraints.getPos(1);
		QuickSize size = constraints.getSize();
		if (size != null) {
			int absSize = size.evaluate(parentSize);
			setSize(childBounds, vertical, absSize);
			if (lead != null)
				setPos(childBounds, vertical, offset + lead.evaluate(parentSize));
			else if (trail != null) {
				int absTrail = trail.evaluate(parentSize);
				setPos(childBounds, vertical, offset + absTrail - absSize);
			} else if (center != null) {
				int absCenter = center.evaluate(parentSize);
				setPos(childBounds, vertical, offset + absCenter - (absSize + 1) / 2);
			} else
				setPos(childBounds, vertical, offset);
		} else {
			if (lead != null) {
				int absLead = lead.evaluate(parentSize);
				setPos(childBounds, vertical, offset + absLead);
				if (trail != null) {
					int absTrail = trail.evaluate(parentSize);
					setSize(childBounds, vertical, Math.max(0, absTrail - absLead));
				} else if (center != null) {
					int absCenter = center.evaluate(parentSize);
					setSize(childBounds, vertical, absCenter < absLead ? 0 : (absCenter - absLead) * 2);
				} else {
					Integer pref = constraints.getSize(0);
					if (pref == null)
						pref = getComponentSize(c, vertical, 0);
					setSize(childBounds, vertical, Math.min(pref, parentSize - absLead));
				}
			} else if (trail != null) {
				int absTrail = trail.evaluate(parentSize);
				if (center != null) {
					int absCenter = center.evaluate(parentSize);
					if (absCenter < absTrail) {
						setPos(childBounds, vertical, offset + (absCenter + absCenter - absTrail));
						setSize(childBounds, vertical, offset + (absTrail - absCenter) * 2);
					} else {
						setPos(childBounds, vertical, offset + absCenter);
						setSize(childBounds, vertical, 0);
					}
				} else {
					Integer pref = constraints.getSize(0);
					if (pref == null)
						pref = getComponentSize(c, vertical, 0);
					setPos(childBounds, vertical, offset + absTrail - pref);
					setSize(childBounds, vertical, pref);
				}
			} else if (center != null) {
				int absCenter = center.evaluate(parentSize);
				Integer pref = constraints.getSize(0);
				if (pref == null)
					pref = getComponentSize(c, vertical, 0);
				setPos(childBounds, vertical, offset + absCenter - (pref + 1) / 2);
				setSize(childBounds, vertical, pref);
			} else {
				Integer pref = constraints.getSize(0);
				if (pref == null)
					pref = getComponentSize(c, vertical, 0);
				setSize(childBounds, vertical, pref);
				setPos(childBounds, vertical, offset);
			}
		}
	}

	private static void setSize(Rectangle bounds, boolean vertical, int size) {
		if (vertical)
			bounds.height = size;
		else
			bounds.width = size;
	}

	private static void setPos(Rectangle bounds, boolean vertical, int pos) {
		if (vertical)
			bounds.y = pos;
		else
			bounds.x = pos;
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
