package org.observe.quick;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager2;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.observe.quick.QuickPosition.PositionUnit;
import org.observe.quick.QuickSize.SizeUnit;

public class SimpleLayout implements LayoutManager2 {
	public static class SimpleConstraints {
		public final Supplier<QuickPosition> left;
		public final Supplier<QuickPosition> right;
		public final Supplier<QuickPosition> top;
		public final Supplier<QuickPosition> bottom;
		public final Supplier<QuickSize> minWidth;
		public final Supplier<QuickSize> prefWidth;
		public final Supplier<QuickSize> maxWidth;
		public final Supplier<QuickSize> minHeight;
		public final Supplier<QuickSize> prefHeight;
		public final Supplier<QuickSize> maxHeight;

		public SimpleConstraints(Supplier<QuickPosition> left, Supplier<QuickPosition> right, //
			Supplier<QuickPosition> top, Supplier<QuickPosition> bottom, //
			Supplier<QuickSize> minWidth, Supplier<QuickSize> prefWidth, Supplier<QuickSize> maxWidth, //
			Supplier<QuickSize> minHeight, Supplier<QuickSize> prefHeight, Supplier<QuickSize> maxHeight) throws IllegalArgumentException {
			this.left = left;
			this.right = right;
			this.top = top;
			this.bottom = bottom;
			this.minWidth = minWidth;
			this.prefWidth = prefWidth;
			this.maxWidth = maxWidth;
			this.minHeight = minHeight;
			this.prefHeight = prefHeight;
			this.maxHeight = maxHeight;
		}

		QuickPosition getPos(boolean vertical, boolean leading) {
			Supplier<QuickPosition> pos;
			if (vertical)
				pos = leading ? top : bottom;
			else
				pos = leading ? left : right;
			return pos == null ? null : pos.get();
		}

		QuickSize getSize(boolean vertical, int type) {
			Supplier<QuickSize> size;
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

		private static Pattern CONSTRAINT_PATTERN = Pattern.compile("(?<type>[a-zA-Z]+)[=:](?<value>.+)");

		public static SimpleConstraints parse(String constraints) throws IllegalArgumentException {
			QuickPosition left = null;
			QuickPosition right = null;
			QuickPosition top = null;
			QuickPosition bottom = null;
			QuickSize minWidth = null, prefWidth = null, maxWidth = null;
			QuickSize minHeight = null, prefHeight = null, maxHeight = null;
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
					left = QuickPosition.parse(m.group("value"));
					break;
				case "right":
					if (right != null)
						throw new IllegalArgumentException("right specified twice as " + right + " and " + m.group("value"));
					right = QuickPosition.parse(m.group("value"));
					break;
				case "top":
				case "y":
					if (top != null)
						throw new IllegalArgumentException("top/y specified twice as " + top + " and " + m.group("value"));
					top = QuickPosition.parse(m.group("value"));
					break;
				case "bottom":
					if (bottom != null)
						throw new IllegalArgumentException("bottom specified twice as " + bottom + " and " + m.group("value"));
					bottom = QuickPosition.parse(m.group("value"));
					break;
				case "min-width":
				case "min-w":
					if (minWidth != null)
						throw new IllegalArgumentException("min-width specified twice as " + minWidth + " and " + m.group("value"));
					minWidth = QuickSize.parse(m.group("value"));
					break;
				case "pref-width":
				case "pref-w":
					if (prefWidth != null)
						throw new IllegalArgumentException("pref-width specified twice as " + prefWidth + " and " + m.group("value"));
					prefWidth = QuickSize.parse(m.group("value"));
					break;
				case "max-width":
				case "max-w":
					if (maxWidth != null)
						throw new IllegalArgumentException("max-width specified twice as " + maxWidth + " and " + m.group("value"));
					maxWidth = QuickSize.parse(m.group("value"));
					break;
				case "min-height":
				case "min-h":
					if (minHeight != null)
						throw new IllegalArgumentException("min-height specified twice as " + minHeight + " and " + m.group("value"));
					minHeight = QuickSize.parse(m.group("value"));
					break;
				case "pref-height":
				case "pref-h":
					if (prefHeight != null)
						throw new IllegalArgumentException("pref-height specified twice as " + prefHeight + " and " + m.group("value"));
					prefHeight = QuickSize.parse(m.group("value"));
					break;
				case "max-height":
				case "max-h":
					if (maxHeight != null)
						throw new IllegalArgumentException("max-height specified twice as " + maxHeight + " and " + m.group("value"));
					maxHeight = QuickSize.parse(m.group("value"));
					break;
				case "width":
				case "w":
					if (minWidth != null || prefWidth != null || maxWidth != null)
						throw new IllegalArgumentException(
							"width specified when [min-/max-/pref-]width already specified: " + m.group("value"));
					minWidth = prefWidth = maxWidth = QuickSize.parse(m.group("value"));
					break;
				case "height":
				case "h":
					if (minHeight != null || prefHeight != null || maxHeight != null)
						throw new IllegalArgumentException(
							"height specified when [min-/max-/pref-]height already specified: " + m.group("value"));
					minHeight = prefHeight = maxHeight = QuickSize.parse(m.group("value"));
					break;
				default:
					throw new IllegalArgumentException("Unrecognized constraint type: " + type);
				}
			}
			QuickPosition fLeft = left, fRight = right, fTop = top, fBottom = bottom;
			QuickSize fMinW = minWidth, fPrefW = prefWidth, fMaxW = maxWidth, fMinH = minHeight, fPrefH = prefHeight, fMaxH = maxHeight;
			return new SimpleConstraints(//
				left == null ? null : () -> fLeft, //
					right == null ? null : () -> fRight, //
						top == null ? null : () -> fTop, //
							bottom == null ? null : () -> fBottom, //
								minWidth == null ? null : () -> fMinW, //
									prefWidth == null ? null : () -> fPrefW, //
										maxWidth == null ? null : () -> fMaxW, //
											minHeight == null ? null : () -> fMinH, //
												prefHeight == null ? null : () -> fPrefH, //
													maxHeight == null ? null : () -> fMaxH);
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

	// Priorities here are min, max, preferred for leading edge, trailing edge, and size

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
		int pixX = 0, pixY = 0;
		int fullX, fullY;
		boolean first = true, percentX = false, percentY = false;
		do {
			fullX = pixX;
			fullY = pixY;
			for (Component c : parent.getComponents()) {
				if (!c.isVisible())
					continue;
				SimpleConstraints constraints = theConstraints.get(c);
				if (constraints != null) {
					if (first) {
						if (!percentX && (constraints.left != null && constraints.left.get().type == PositionUnit.Percent)//
							|| (constraints.right != null && constraints.right.get().type == PositionUnit.Percent)//
							|| (constraints.minWidth != null && constraints.minWidth.get().type == SizeUnit.Percent))
							percentX = true;
						if (!percentY && (constraints.top != null && constraints.top.get().type == PositionUnit.Percent)//
							|| (constraints.bottom != null && constraints.bottom.get().type == PositionUnit.Percent)//
							|| (constraints.minHeight != null && constraints.minHeight.get().type == SizeUnit.Percent))
							percentY = true;
					}
					if (first || percentX) {
						int cx = getConstraintSize(constraints, false, type, c, fullX);
						pixX = Math.max(pixX, cx);
					}
					if (first || percentY) {
						int cy = getConstraintSize(constraints, true, type, c, fullY);
						pixY = Math.max(pixY, cy);
					}
				} else if (first) {
					Dimension componentLayoutSize;
					switch (type) {
					case -1:
						componentLayoutSize = c.getMinimumSize();
						break;
					case 0:
						componentLayoutSize = c.getPreferredSize();
						break;
					case 1:
						componentLayoutSize = c.getMaximumSize();
						break;
					default:
						throw new IllegalStateException("Bad size type: " + type);
					}
					pixX = Math.max(pixX, componentLayoutSize.width);
					pixY = Math.max(pixY, componentLayoutSize.height);
				}
			}
			if (first && !percentX && !percentY)
				break;
			first = false;
		} while (fullX != pixX || fullY != pixY);
		fullX = pixX;
		fullY = pixY;
		return new Dimension(fullX, fullY);
	}

	private static int getConstraintSize(SimpleConstraints constraints, boolean vertical, int type, Component component,
		int containerSize) {
		int sz = _getConstraintSize(constraints, vertical, type, component, containerSize);
		if (type >= 0) {
			int min = _getConstraintSize(constraints, vertical, -1, component, containerSize);
			if (min > sz)
				return min;
		}
		if (type == 0) {
			int max = _getConstraintSize(constraints, vertical, 1, component, containerSize);
			if (max < sz)
				return max;
		}
		return sz;
	}

	private static int _getConstraintSize(SimpleConstraints constraints, boolean vertical, int type, Component component,
		int containerSize) {
		int sz;
		boolean withPercent = containerSize > 0;
		Dimension componentLayoutSize;
		switch (type) {
		case -1:
			componentLayoutSize = component.getMinimumSize();
			break;
		case 0:
			componentLayoutSize = component.getPreferredSize();
			break;
		case 1:
			componentLayoutSize = component.getMaximumSize();
			break;
		default:
			throw new IllegalStateException("Bad size type: " + type);
		}
		int componentLayoutDim;
		if (componentLayoutSize != null)
			componentLayoutDim = vertical ? componentLayoutSize.height : componentLayoutSize.width;
		else
			componentLayoutDim = 0;
		QuickSize size = constraints.getSize(vertical, type);
		if (constraints.left != null && (withPercent || constraints.left.get().type != PositionUnit.Percent)) {
			sz = constraints.left.get().evaluate(containerSize);
			if (constraints.left.get().type != PositionUnit.Lexips) {
				if (size != null) {
					if (withPercent || size.type != SizeUnit.Percent)
						sz += size.evaluate(containerSize);
				} else
					sz += componentLayoutDim;
				if (constraints.right != null && constraints.right.get().type != PositionUnit.Pixels
					&& (withPercent || constraints.right.get().type != PositionUnit.Percent))
					sz += constraints.right.get().evaluate(containerSize);
			}
		} else if (constraints.right != null && (withPercent || constraints.right.get().type != PositionUnit.Percent)) {
			sz = (int) constraints.right.get().value;
			if (constraints.right.get().type != PositionUnit.Pixels) {
				if (size != null) {
					if (withPercent || size.type != SizeUnit.Percent)
						sz += size.evaluate(containerSize);
				} else
					sz += componentLayoutDim;
			}
		} else if (size != null && withPercent || size.type != SizeUnit.Percent)
			sz = size.evaluate(containerSize);
		else
			sz = componentLayoutDim;
		return sz;
	}

	@Override
	public void layoutContainer(Container parent) {
		for (Component c : parent.getComponents()) {
			layoutChild(parent, c);
		}
	}

	public void layoutChild(Container parent, Component c) {
		Dimension parentSize = parent.getSize();
		if (!c.isVisible())
			return;
		SimpleConstraints constraints = theConstraints.get(c);
		if (constraints != null) {
			int x, w;
			if (constraints.left != null) {
				x = constraints.left.get().evaluate(parentSize.width);
				if (constraints.right != null) {
					int right = constraints.right.get().evaluate(parentSize.width);
					w = right - x;
				} else
					w = evaluateSize(constraints, c, parentSize, false);
			} else if (constraints.right != null) {
				int right = constraints.right.get().evaluate(parentSize.width);
				w = evaluateSize(constraints, c, parentSize, false);
				x = right - w;
			} else
				x = 0;

			int y, h;
			if (constraints.top != null) {
				y = constraints.top.get().evaluate(parentSize.height);
				if (constraints.bottom != null) {
					int right = constraints.bottom.get().evaluate(parentSize.height);
					h = right - y;
				} else
					h = evaluateSize(constraints, c, parentSize, true);
			} else if (constraints.bottom != null) {
				int right = constraints.bottom.get().evaluate(parentSize.height);
				h = evaluateSize(constraints, c, parentSize, true);
				y = right - h;
			} else
				y = 0;
		} else {
			Dimension ps = c.getPreferredSize();
			if (ps != null)
				c.setBounds(0, 0, ps.width, ps.height);
			else
				c.setBounds(0, 0, 0, 0);
		}
	}

	private static int evaluateSize(SimpleConstraints constraints, Component c, Dimension parentSize, boolean vertical) {
		int sz;
		QuickSize size = constraints.getSize(vertical, 0);
		if (size != null)
			sz = size.evaluate(parentSize.width);
		else {
			Dimension prefS = c.getPreferredSize();
			sz = prefS == null ? 0 : (vertical ? prefS.height : prefS.width);
		}
		size = constraints.getSize(vertical, -1);
		if (size != null) {
			int min = size.evaluate(vertical ? parentSize.height : parentSize.width);
			if (min > sz)
				sz = min;
			else {
				size = constraints.getSize(vertical, 1);
				if (size != null) {
					int max = size.evaluate(vertical ? parentSize.height : parentSize.width);
					if (max < sz)
						sz = max;
				}
			}
		} else {
			size = constraints.getSize(vertical, 1);
			if (constraints.maxWidth != null) {
				int max = constraints.maxWidth.get().evaluate(vertical ? parentSize.height : parentSize.width);
				if (max < sz)
					sz = max;
			}
		}
		return sz;
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
