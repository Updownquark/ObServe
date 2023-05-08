package org.observe.quick.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager2;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.observe.quick.base.QuickSize;
import org.qommons.LambdaUtils;

public class SimpleLayout implements LayoutManager2 {
	public static class SimpleConstraints {
		public final Supplier<QuickSize> left;
		public final Supplier<QuickSize> hCenter;
		public final Supplier<QuickSize> right;
		public final Supplier<QuickSize> top;
		public final Supplier<QuickSize> vCenter;
		public final Supplier<QuickSize> bottom;
		public final Supplier<QuickSize> width;
		public final Supplier<Integer> minWidth;
		public final Supplier<Integer> prefWidth;
		public final Supplier<Integer> maxWidth;
		public final Supplier<QuickSize> height;
		public final Supplier<Integer> minHeight;
		public final Supplier<Integer> prefHeight;
		public final Supplier<Integer> maxHeight;

		public SimpleConstraints(Supplier<QuickSize> left, Supplier<QuickSize> hCenter, Supplier<QuickSize> right, //
			Supplier<QuickSize> top, Supplier<QuickSize> vCenter, Supplier<QuickSize> bottom, //
			Supplier<QuickSize> width, Supplier<Integer> minWidth, Supplier<Integer> prefWidth, Supplier<Integer> maxWidth, //
			Supplier<QuickSize> height, Supplier<Integer> minHeight, Supplier<Integer> prefHeight, Supplier<Integer> maxHeight)
				throws IllegalArgumentException {
			this.left = left;
			this.hCenter = hCenter;
			this.right = right;
			this.top = top;
			this.vCenter = vCenter;
			this.bottom = bottom;
			this.width = width;
			this.minWidth = minWidth;
			this.prefWidth = prefWidth;
			this.maxWidth = maxWidth;
			this.height = height;
			this.minHeight = minHeight;
			this.prefHeight = prefHeight;
			this.maxHeight = maxHeight;
		}

		QuickSize getPos(boolean vertical, int type) {
			Supplier<QuickSize> pos;
			if (vertical) {
				if (type < 0)
					pos = top;
				else if (type == 0)
					pos = vCenter;
				else
					pos = bottom;
			} else {
				if (type < 0)
					pos = left;
				else if (type == 0)
					pos = hCenter;
				else
					pos = right;
			}
			return pos == null ? null : pos.get();
		}

		QuickSize getSize(boolean vertical) {
			return (vertical ? height : width).get();
		}

		Integer getSize(boolean vertical, int type) {
			Supplier<Integer> size;
			switch (type) {
			case -1:
				size = vertical ? minHeight : minWidth;
				break;
			case 0:
				size = vertical ? prefHeight : prefWidth;
				break;
			case 1:
				size = vertical ? maxHeight : maxWidth;
				break;
			default:
				throw new IllegalStateException("Expected -1, 0, or 1 for size type, not " + type);
			}
			return size == null ? null : size.get();
		}

		private static final Pattern CONSTRAINT_PATTERN = Pattern.compile("(?<type>[a-zA-Z]+)[=:](?<value>.+)");

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
			return new SimpleConstraints(//
				fLeft == null ? null : LambdaUtils.constantSupplier(fLeft, fLeft::toString, fLeft), //
					fHCenter == null ? null : LambdaUtils.constantSupplier(fHCenter, fHCenter::toString, fHCenter), //
						fRight == null ? null : LambdaUtils.constantSupplier(fRight, fRight::toString, fRight), //
							fTop == null ? null : LambdaUtils.constantSupplier(fTop, fTop::toString, fTop), //
								fVCenter == null ? null : LambdaUtils.constantSupplier(fVCenter, fVCenter::toString, fVCenter), //
									fBottom == null ? null : LambdaUtils.constantSupplier(fBottom, fBottom::toString, fBottom), //
										fWidth == null ? null : LambdaUtils.constantSupplier(fWidth, fWidth::toString, fWidth), //
											fMinW == null ? null : LambdaUtils.constantSupplier(fMinW, fMinW::toString, fMinW), //
												fPrefW == null ? null : LambdaUtils.constantSupplier(fPrefW, fPrefW::toString, fPrefW), //
													fMaxW == null ? null : LambdaUtils.constantSupplier(fMaxW, fMaxW::toString, fMaxW), //
														fHeight == null ? null : LambdaUtils.constantSupplier(fHeight, fHeight::toString, fHeight), //
															fMinH == null ? null : LambdaUtils.constantSupplier(fMinH, fMinH::toString, fMinH), //
																fPrefH == null ? null : LambdaUtils.constantSupplier(fPrefH, fPrefH::toString, fPrefH), //
																	fMaxH == null ? null : LambdaUtils.constantSupplier(fMaxH, fMaxH::toString, fMaxH));
		}
	}

	private final Map<Component, SimpleConstraints> theConstraints = new HashMap<>();

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
		Dimension size = new Dimension();
		for (Component c : parent.getComponents()) {
			if (!c.isVisible())
				continue;
			SimpleConstraints constraints = theConstraints.get(c);
			int w = getContainerSizeFor(c, false, type, constraints);
			if (w > size.width)
				size.width = w;
			int h = getContainerSizeFor(c, true, type, constraints);
			if (h > size.height)
				size.height = h;
		}
		return size;
	}

	private int getContainerSizeFor(Component c, boolean vertical, int type, SimpleConstraints constraints) {
		if (constraints == null)
			return getComponentSize(c, vertical, type);
		QuickSize trail = constraints.getPos(vertical, 1);
		if (trail != null && trail.percent == 0.0f)
			return trail.pixels;
		QuickSize lead = constraints.getPos(vertical, -1);
		if (lead != null && lead.percent == 100.0f)
			return lead.pixels;
		QuickSize center = constraints.getPos(vertical, 0);
		QuickSize size = constraints.getSize(vertical);
		Integer absSize = null;
		float relSize = 0.0f;
		if (size != null) {
			absSize = size.pixels;
			relSize = size.percent;
		} else {
			absSize = constraints.getSize(vertical, type);
			if (absSize == null)
				absSize = getComponentSize(c, vertical, type);
		}
		if (lead != null) {
			absSize += Math.abs(lead.pixels);
			relSize += lead.percent;
		}
		if (trail != null) {
			absSize += Math.abs(trail.pixels);
			relSize += trail.percent;
		}
		if (absSize > 0 && relSize != 0)
			return new QuickSize(relSize, absSize).resolveExponential();
		else if (type > 0)
			return Integer.MAX_VALUE;
		else if (absSize != 0)
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
		Rectangle childBounds = new Rectangle();
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
			layoutChild(c, constraints, parentSize.height, true, childBounds);
			layoutChild(c, constraints, parentSize.width, false, childBounds);
			c.setBounds(childBounds);
		}
	}

	private void layoutChild(Component c, SimpleConstraints constraints, int parentSize, boolean vertical, Rectangle childBounds) {
		QuickSize lead = constraints.getPos(vertical, -1);
		QuickSize center = constraints.getPos(vertical, 0);
		QuickSize trail = constraints.getPos(vertical, 1);
		QuickSize size = constraints.getSize(vertical);
		if (size != null) {
			int absSize = size.evaluate(parentSize);
			setSize(childBounds, vertical, absSize);
			if (lead != null)
				setPos(childBounds, vertical, lead.evaluate(parentSize));
			else if (trail != null) {
				int absTrail = trail.evaluate(parentSize);
				setPos(childBounds, vertical, parentSize - absSize - absTrail);
			} else if (center != null) {
				int absCenter = center.evaluate(parentSize);
				setPos(childBounds, vertical, absCenter - (absSize + 1) / 2);
			} else
				setPos(childBounds, vertical, 0);
		} else {
			if (lead != null) {
				int absLead = lead.evaluate(parentSize);
				setPos(childBounds, vertical, absLead);
				if (trail != null) {
					int absTrail = trail.evaluate(parentSize);
					setSize(childBounds, vertical, Math.max(0, absTrail - absLead));
				} else if (center != null) {
					int absCenter = center.evaluate(parentSize);
					setSize(childBounds, vertical, absCenter < absLead ? 0 : (absCenter - absLead) * 2);
				} else {
					Integer pref = constraints.getSize(vertical, 0);
					if (pref == null)
						pref = getComponentSize(c, vertical, 0);
					setSize(childBounds, vertical, Math.min(pref, parentSize - absLead));
				}
			} else if (trail != null) {
				int absTrail = trail.evaluate(parentSize);
				if (center != null) {
					int absCenter = center.evaluate(parentSize);
					if (absCenter < absTrail) {
						setPos(childBounds, vertical, (absCenter + absCenter - absTrail));
						setSize(childBounds, vertical, (absTrail - absCenter) * 2);
					} else {
						setPos(childBounds, vertical, absCenter);
						setSize(childBounds, vertical, 0);
					}
				} else {
					Integer pref = constraints.getSize(vertical, 0);
					if (pref == null)
						pref = getComponentSize(c, vertical, 0);
					setPos(childBounds, vertical, absTrail - pref);
					setSize(childBounds, vertical, pref);
				}
			} else if (center != null) {
				int absCenter = center.evaluate(parentSize);
				Integer pref = constraints.getSize(vertical, 0);
				if (pref == null)
					pref = getComponentSize(c, vertical, 0);
				setPos(childBounds, vertical, absCenter - (pref + 1) / 2);
				setSize(childBounds, vertical, pref);
			} else {
				Integer pref = constraints.getSize(vertical, 0);
				if (pref == null)
					pref = getComponentSize(c, vertical, 0);
				setSize(childBounds, vertical, pref);
				setPos(childBounds, vertical, 0);
			}
		}
	}

	private static void setSize(Rectangle bounds, boolean vertical, int size) {
		if (vertical)
			bounds.height = size;
		else
			bounds.width = size;
	}

	private static void setPos(Rectangle bounds, boolean vertical, int size) {
		if (vertical)
			bounds.y = size;
		else
			bounds.x = size;
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
