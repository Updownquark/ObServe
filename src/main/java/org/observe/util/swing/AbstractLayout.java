package org.observe.util.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.qommons.BreakpointHere;

/**
 * A swing layout that:
 * <ol>
 * <li>Does not require or recognize any constraints on layout children</li>
 * <li>Does not require the actual children, but may be called with {@link AbstractLayout.LayoutChild} instances instead.
 * </ol>
 */
public abstract class AbstractLayout implements ScrollableSwingLayout {
	/** A substitute for a component in a layout container */
	public interface LayoutChild {
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

		/** @return The layout constraints with which the component was added to its parent */
		Object getConstraints();

		/** A {@link LayoutChild} sourced from an AWT component */
		public static class ComponentLayoutChild implements LayoutChild {
			/** The component backing this layout child */
			protected final Component component;
			private final Object constraints;

			/**
			 * @param component The component to get size information from
			 * @param constraints The layout constraints with which the component was added to the parent
			 */
			public ComponentLayoutChild(Component component, Object constraints) {
				this.component = component;
				this.constraints = constraints;
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

			@Override
			public Object getConstraints() {
				return constraints;
			}
		}

		/**
		 * A {@link LayoutChild} that extracts information from another {@link LayoutChild} and caches it, discarding the reference to the
		 * source.
		 */
		public static class ExtractedLayoutChild implements LayoutChild {
			private final Dimension[] theSizes;
			private final Object constraints;

			/** @param toExtract The layout child to extract the layout information from */
			public ExtractedLayoutChild(ComponentLayoutChild toExtract) {
				theSizes = new Dimension[] { //
					toExtract.getSize(-1), //
					toExtract.getSize(0), //
					toExtract.getSize(1) };
				this.constraints = toExtract.getConstraints();
			}

			@Override
			public Dimension getSize(int type) {
				if (type < 0)
					return theSizes[0];
				else if (type == 0)
					return theSizes[1];
				else
					return theSizes[2];
			}

			@Override
			public Object getConstraints() {
				return constraints;
			}
		}
	}

	private final Map<Component, Object> theConstraints = new HashMap<>();

	/** @return Whether this layout allocates space to invisible components */
	public abstract boolean isShowingInvisible();

	@Override
	public Dimension minimumLayoutSize(Container parent) {
		return minimumLayoutSize(parent.getSize(), parent.getInsets(), layoutChildren(parent, isShowingInvisible()));
	}

	@Override
	public Dimension preferredLayoutSize(Container parent) {
		return preferredLayoutSize(parent.getSize(), parent.getInsets(), layoutChildren(parent, isShowingInvisible()));
	}

	@Override
	public Dimension maximumLayoutSize(Container parent) {
		return maximumLayoutSize(parent.getSize(), parent.getInsets(), layoutChildren(parent, isShowingInvisible()));
	}

	@Override
	public void layoutContainer(Container parent) {
		Dimension parentSize = parent.getSize();
		if (parentSize.width == 0 || parentSize.height == 0)
			return;

		List<Component> components = new ArrayList<>(parent.getComponentCount());
		List<AbstractLayout.LayoutChild> layoutComponents = new ArrayList<>(parent.getComponentCount());
		for (int c = 0; c < parent.getComponentCount(); c++) {
			Component comp = parent.getComponent(c);
			if (isShowingInvisible() || comp.isVisible()) {
				components.add(comp);
				layoutComponents.add(layoutChild(comp));
			}
		}
		String name = parent.getName();
		if (PanelPopulation.isDebugging(name, "abstract-layout"))
			BreakpointHere.breakpoint();
		Rectangle[] componentBounds = layoutContainer(parentSize, parent.getInsets(), layoutComponents);
		for (int c = 0; c < componentBounds.length; c++)
			components.get(c).setBounds(componentBounds[c]);
	}

	/**
	 * @param type &lt;0 for minimum, 0 for preferred, >0 for maximum size
	 * @param containerSize The container's size
	 * @param parentInsets The insets to leave around the container's border
	 * @param components The components to lay out
	 * @return The size of the given type for the container
	 */
	public Dimension layoutSize(int type, Dimension containerSize, Insets parentInsets, List<LayoutChild> components) {
		if (type < 0)
			return minimumLayoutSize(containerSize, parentInsets, components);
		else if (type == 0)
			return preferredLayoutSize(containerSize, parentInsets, components);
		else
			return maximumLayoutSize(containerSize, parentInsets, components);
	}

	/**
	 * @param containerSize The container's size
	 * @param parentInsets The insets to leave around the container's border
	 * @param components The components to lay out
	 * @return The minimum size for the container
	 */
	public abstract Dimension minimumLayoutSize(Dimension containerSize, Insets parentInsets, List<LayoutChild> components);

	/**
	 * @param containerSize The container's size
	 * @param parentInsets The insets to leave around the container's border
	 * @param components The components to lay out
	 * @return The preferred size for the container
	 */
	public abstract Dimension preferredLayoutSize(Dimension containerSize, Insets parentInsets, List<LayoutChild> components);

	/**
	 * @param containerSize The container's size
	 * @param parentInsets The insets to leave around the container's border
	 * @param components The components to lay out
	 * @return The maximum size for the container
	 */
	public abstract Dimension maximumLayoutSize(Dimension containerSize, Insets parentInsets, List<LayoutChild> components);

	/**
	 * @param containerSize The container's size
	 * @param parentInsets The insets to leave around the container's border
	 * @param components The components to lay out
	 * @return The bounds for each of the given components
	 */
	public abstract Rectangle[] layoutContainer(Dimension containerSize, Insets parentInsets, List<AbstractLayout.LayoutChild> components);

	/**
	 * @param container The container whose children to lay out
	 * @param showInvisible Whether to allocate size to invisible children
	 * @return A {@link LayoutChild} for each child to be laid out in the container
	 */
	public List<LayoutChild> layoutChildren(Container container, boolean showInvisible) {
		List<LayoutChild> children = new ArrayList<>(container.getComponentCount());
		for (int c = 0; c < container.getComponentCount(); c++) {
			Component comp = container.getComponent(c);
			if (!showInvisible && !comp.isVisible())
				continue;
			children.add(layoutChild(comp));
		}
		return children;
	}

	/**
	 * @param component The component to create the layout child for
	 * @return The layout child for the given component
	 */
	public LayoutChild layoutChild(Component component) {
		return new LayoutChild.ComponentLayoutChild(component, theConstraints.get(component));
	}

	@Override
	public void addLayoutComponent(Component comp, Object constraints) {
		if (constraints == null)
			theConstraints.remove(comp);
		else
			theConstraints.put(comp, constraints);
	}

	@Override
	public void invalidateLayout(Container target) {
	}

	@Override
	public void addLayoutComponent(String name, Component comp) {
	}

	@Override
	public void removeLayoutComponent(Component comp) {
		theConstraints.remove(comp);
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
	public Dimension getPreferredScrollableViewportSize(Container parent) {
		return preferredLayoutSize(parent);
	}

	@Override
	public int getScrollableUnitIncrement(Container parent, Rectangle visibleRect, int orientation, int direction) {
		return 10;
	}

	@Override
	public int getScrollableBlockIncrement(Container parent, Rectangle visibleRect, int orientation, int direction) {
		return 100;
	}
}
