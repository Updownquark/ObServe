package org.observe.util.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

public interface AbstractLayout extends ScrollableSwingLayout {
	interface LayoutChild {
		/**
		 * @param type The type of the size to get:
		 *        <ul>
		 *        <li>-1 (or any number &lt;0) for minimum size)</li>
		 *        <li>0 for preferred size)</li>
		 *        <li>1 (or any number &gt;0) for maximum size)</li>
		 *        </ul>
		 * @return The min/preferred/max size of the layout child
		 */
		Dimension getSize(int type);

		public static class ComponentLayoutChild implements LayoutChild {
			private final Component component;

			public ComponentLayoutChild(Component component) {
				this.component = component;
			}

			@Override
			public Dimension getSize(int type) {
				if (type < 0)
					return component.getMinimumSize();
				else if (type == 0)
					return component.getPreferredSize();
				else
					return component.getMaximumSize();
			}
		}
	}

	/** @return Whether this layout allocates space to invisible components */
	boolean isShowingInvisible();

	@Override
	default Dimension minimumLayoutSize(Container parent) {
		return minimumLayoutSize(parent.getSize(), parent.getInsets(), layoutChildren(parent, isShowingInvisible()));
	}

	@Override
	default Dimension preferredLayoutSize(Container parent) {
		return preferredLayoutSize(parent.getSize(), parent.getInsets(), layoutChildren(parent, isShowingInvisible()));
	}

	@Override
	default Dimension maximumLayoutSize(Container parent) {
		return maximumLayoutSize(parent.getSize(), parent.getInsets(), layoutChildren(parent, isShowingInvisible()));
	}

	@Override
	default void layoutContainer(Container parent) {
		Dimension parentSize = parent.getSize();
		if (parentSize.width == 0 || parentSize.height == 0)
			return;

		List<Component> components = new ArrayList<>(parent.getComponentCount());
		List<AbstractLayout.LayoutChild> layoutComponents = new ArrayList<>(parent.getComponentCount());
		for (int c = 0; c < parent.getComponentCount(); c++) {
			Component comp = parent.getComponent(c);
			if (isShowingInvisible() || comp.isVisible()) {
				components.add(comp);
				layoutComponents.add(new AbstractLayout.LayoutChild.ComponentLayoutChild(comp));
			}
		}
		Rectangle[] componentBounds = layoutContainer(parentSize, parent.getInsets(), layoutComponents);
		for (int c = 0; c < componentBounds.length; c++)
			components.get(c).setBounds(componentBounds[c]);
	}

	default Dimension layoutSize(int type, Dimension containerSize, Insets parentInsets, List<LayoutChild> components) {
		if (type < 0)
			return minimumLayoutSize(containerSize, parentInsets, components);
		else if (type == 0)
			return preferredLayoutSize(containerSize, parentInsets, components);
		else
			return maximumLayoutSize(containerSize, parentInsets, components);
	}

	Dimension minimumLayoutSize(Dimension containerSize, Insets parentInsets, List<LayoutChild> components);

	Dimension preferredLayoutSize(Dimension containerSize, Insets parentInsets, List<LayoutChild> components);

	Dimension maximumLayoutSize(Dimension containerSize, Insets parentInsets, List<LayoutChild> components);

	Rectangle[] layoutContainer(Dimension containerSize, Insets parentInsets, List<AbstractLayout.LayoutChild> components);

	static List<LayoutChild> layoutChildren(Container container, boolean showInvisible) {
		List<LayoutChild> children = new ArrayList<>(container.getComponentCount());
		for (int c = 0; c < container.getComponentCount(); c++) {
			Component comp = container.getComponent(c);
			if (!showInvisible && !comp.isVisible())
				continue;
			children.add(new LayoutChild.ComponentLayoutChild(comp));
		}
		return children;
	}

	@Override
	default void addLayoutComponent(Component comp, Object constraints) {
	}

	@Override
	default void invalidateLayout(Container target) {
	}

	@Override
	default void addLayoutComponent(String name, Component comp) {
	}

	@Override
	default void removeLayoutComponent(Component comp) {
	}

	@Override
	default float getLayoutAlignmentX(Container target) {
		return 0;
	}

	@Override
	default float getLayoutAlignmentY(Container target) {
		return 0;
	}

	@Override
	default Dimension getPreferredScrollableViewportSize(Container parent) {
		return preferredLayoutSize(parent);
	}

	@Override
	default int getScrollableUnitIncrement(Container parent, Rectangle visibleRect, int orientation, int direction) {
		return 10;
	}

	@Override
	default int getScrollableBlockIncrement(Container parent, Rectangle visibleRect, int orientation, int direction) {
		return 100;
	}
}
