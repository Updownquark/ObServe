package org.observe.util.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.CellRendererPane;
import javax.swing.JComponent;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;

import org.observe.Observable;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.util.swing.ObservableCellRenderer.CellRenderContext;
import org.qommons.LambdaUtils;
import org.qommons.ThreadConstraint;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;

/**
 * <p>
 * A component that renders each value of a collection as a separate component. Only the hovered and focused components are proper children
 * of this container, and all the rest are synthetically rendered, making it more efficient for large collections.
 * </p>
 * <p>
 * This is designed to support the &lt;tiled-pane> widget in the Quick-X toolkit. Outside of that use case there are a few things to know:
 * <ul>
 * <li>The {@link #setRendering(ObservableCellRenderer, ObservableCellRenderer, ObservableCellRenderer) setRendering} method must be called
 * with 3 independent renderers.</li>
 * <li>This container only supports layouts of type {@link AbstractLayout}, which don't require actual components or component
 * constraints.<br>
 * The two provided implementations of this at this moment are {@link JustifiedBoxLayout} and
 * {@link org.observe.quick.swing.GridFlowLayout}.</li>
 * </ul>
 * </p>
 *
 * @param <T> The type of the values in the collection
 */
public class TiledPane<T> extends JComponent implements Scrollable {
	private final ObservableCollection<T> theValues;
	private ObservableCellRenderer<? super T, ? super T> theRenderer;

	private RenderChild theHover;
	private RenderChild theFocus;

	private final CellRendererPane theRenderPane;
	private final Component theGlassPane;
	private final Component thePreFocus;
	private final Component theMidFocus;
	private final Component thePostFocus;

	private final List<Rectangle> theValueBounds;

	/**
	 * @param values The values to render
	 * @param until The observable that will cause this tiled pane to unsubscribe from the collection
	 */
	public TiledPane(ObservableCollection<T> values, Observable<?> until) {
		theValues = values.safe(ThreadConstraint.EDT, until);
		super.setLayout(new JustifiedBoxLayout(true));
		theValueBounds = new ArrayList<>();
		Subscription valueSub = theValues.onChange(evt -> {
			switch (evt.getType()) {
			case add:
				theValueBounds.add(evt.getIndex(), null);
				break;
			case remove:
				theValueBounds.remove(evt.getIndex());
				if (theHover.valueEquals(evt.getElementId()))
					theHover.replace(false, null, evt.getIndex(), false);
				if (theFocus.valueEquals(evt.getElementId()))
					theFocus.replace(true, null, evt.getIndex(), false);
				break;
			case set:
				if (theHover.valueEquals(evt.getElementId()))
					theHover.replace(false, theValues.getElement(evt.getElementId()), evt.getIndex(), true);
				if (theFocus.valueEquals(evt.getElementId()))
					theFocus.replace(true, theValues.getElement(evt.getElementId()), evt.getIndex(), true);
				break; // Just re-validate below
			}
			revalidate();
			repaint();
		});
		until.take(1).act(__ -> valueSub.unsubscribe());
		theGlassPane = new Component() {
			@Override
			public boolean isOpaque() {
				return false;
			}

			@Override
			public void paint(Graphics g) {
			}
		};
		add(theGlassPane);
		MouseAdapter glassPaneListener = new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				mouse(e.getPoint());
				reDispatch(e);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				theHover.replace(false, null, -1, false);
				reDispatch(e);
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				mouse(e.getPoint());
				reDispatch(e);
			}

			@Override
			public void mousePressed(MouseEvent e) {
				if (theHover.component != null && theHover.component.getBounds().contains(e.getX(), e.getY())) {
					// Swap the hover and focus. Don't clear anything so the focus events can properly propagate.
					RenderChild oldFocus = theFocus;
					theFocus = theHover;
					theHover = oldFocus;
				}
				reDispatch(theFocus.component, e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				reDispatch(e);
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				reDispatch(theFocus.component, e);
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				reDispatch(e);
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				reDispatch(e);
			}

			private void reDispatch(MouseEvent e) {
				reDispatch(theFocus.component, e);
				reDispatch(theHover.component, e);
			}

			private void reDispatch(Component target, MouseEvent e) {
				if (target == null)
					return;
				int hoverX = e.getX() - target.getX();
				int hoverY = e.getY() - target.getY();
				Component deepest = getDeepestComponentAt(target, hoverX, hoverY);
				if (deepest != null) {
					Point deepestPoint = SwingUtilities.convertPoint(target, hoverX, hoverY, deepest);
					if (theGlassPane.getCursor() != deepest.getCursor())
						theGlassPane.setCursor(deepest.getCursor());
					deepest.dispatchEvent(new MouseEvent(deepest, e.getID(), e.getWhen(), e.getModifiers(), deepestPoint.x, deepestPoint.y,
						e.getXOnScreen(), e.getYOnScreen(), e.getClickCount(), e.isPopupTrigger(), e.getButton()));
				}
			}

			private Component getDeepestComponentAt(Component parent, int x, int y) {
				if (x < 0 || y < 0 || x >= parent.getWidth() || y >= parent.getHeight())
					return null;
				if (!(parent instanceof Container))
					return parent;
				for (int c = ((Container) parent).getComponentCount() - 1; c >= 0; c--) {
					Component comp = ((Container) parent).getComponent(c);
					if (comp.getBounds().contains(x, y)) {
						if (comp instanceof Container) {
							parent = comp;
							c = ((Container) parent).getComponentCount();
							x -= parent.getX();
							y -= parent.getY();
						} else
							return comp;
					}
				}
				return parent;
			}

			private void mouse(Point cursor) {
				int index = 0;
				for (Rectangle vb : theValueBounds) {
					if (vb != null && vb.contains(cursor))
						break;
					index++;
				}
				if (index < theValueBounds.size()) {
					CollectionElement<T> moused = theValues.getElement(index);
					if (theFocus.valueEquals(moused.getElementId()))
						theHover.replace(false, null, -1, false);
					else
						theHover.replace(false, moused, index, false);
				} else
					theHover.replace(false, null, -1, false);
			}
		};
		theGlassPane.addMouseListener(glassPaneListener);
		theGlassPane.addMouseMotionListener(glassPaneListener);
		theGlassPane.addMouseWheelListener(glassPaneListener);

		theRenderPane = new CellRendererPane();
		add(theRenderPane);

		thePreFocus = new SimpleFocusComponent();
		theMidFocus = new SimpleFocusComponent();
		thePostFocus = new SimpleFocusComponent();
		thePreFocus.setFocusable(true);
		theMidFocus.setFocusable(true);
		thePostFocus.setFocusable(true);
		thePreFocus.setBounds(-100_000_000, -100_000_000, 0, 0); // Always first
		thePostFocus.setBounds(100_000_000, 100_000_000, 0, 0); // Always last
		add(thePreFocus);
		add(theMidFocus);
		add(thePostFocus);

		theHover = new RenderChild(null, thePreFocus, theMidFocus);
		theFocus = new RenderChild(null, theMidFocus, thePostFocus);

		FocusListener focusManager = new FocusListener() {
			@Override
			public void focusGained(FocusEvent e) {
				if (theFocus.component != null && e.getOppositeComponent() != null
					&& SwingUtilities.isDescendingFrom(e.getOppositeComponent(), theFocus.component)) {
					// Tabbing from the focused component. Focus on the adjacent value.
					boolean forward = e.getComponent() == theFocus.postFocus;
					CollectionElement<T> adj = theValues.getAdjacentElement(theFocus.value.getElementId(), forward);
					if (adj != null) {
						theFocus.replace(true, adj, theValues.getElementsBefore(adj.getElementId()), false);
						// Now cycle in to the renderer.
						cycleIntoFocus(forward);
					} else { // No more values, tab away from this tiled pane
						cycleAway(forward);
						theFocus.replace(true, null, -1, false);
					}
				} else if (e.getComponent() != theMidFocus) { // Tabbing into this component from outside. Focus on the terminal value.
					boolean forward = e.getComponent() == thePreFocus;
					if (theValues.isEmpty()) {// No values to focus on. Cycle away.
						cycleAway(forward);
					} else {
						theFocus.replace(true, theValues.getTerminalElement(forward), forward ? 0 : theValues.size() - 1, false);
						// Now cycle in to the renderer.
						cycleIntoFocus(forward);
					}
				}
			}

			private void cycleIntoFocus(boolean forward) {
				EventQueue.invokeLater(() -> {
					if (forward)
						KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent(theFocus.preFocus);
					else
						KeyboardFocusManager.getCurrentKeyboardFocusManager().focusPreviousComponent(theFocus.postFocus);
				});
			}

			private void cycleAway(boolean forward) {
				EventQueue.invokeLater(() -> {
					if (forward)
						KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent(thePostFocus);
					else
						KeyboardFocusManager.getCurrentKeyboardFocusManager().focusPreviousComponent(thePreFocus);
				});
			}

			@Override
			public void focusLost(FocusEvent e) {
			}
		};
		thePreFocus.addFocusListener(focusManager);
		theMidFocus.addFocusListener(focusManager);
		thePostFocus.addFocusListener(focusManager);
	}

	/** @return The values that this component is rendering */
	public ObservableCollection<T> getValues() {
		return theValues;
	}

	/**
	 * <p>
	 * Installs renderers in this container to render values in the collection.
	 * </p>
	 * <p>
	 * The first of these will be used to paint all the values in the collection that are not being interacted with by the user.
	 * </p>
	 * <p>
	 * The second and third are to provide a component when the user is hovering the mouse over a value in the collection and when the user
	 * is focused on a value in the collection. These two are used interchangeably--one may function as hover and another as focus, and then
	 * this may switch as the user continues interacting with this component.
	 * </p>
	 * <p>
	 * The components provided by the second and third renderers will be installed as first-class children of this container. That provided
	 * by the first renderer will not, and will only be used to paint representations of collection elements.
	 * </p>
	 *
	 * @param painter The renderer to paint all the values in the collection that are not being interacted with by the user
	 * @param renderer2 A renderer to render and handle input for an element in the collection that the user is interacting with
	 * @param renderer3 A second renderer to render and handle input for an element in the collection that the user is interacting with
	 * @return This tiled pane
	 */
	public TiledPane<T> setRendering(ObservableCellRenderer<? super T, ? super T> painter,
		ObservableCellRenderer<? super T, ? super T> renderer2, ObservableCellRenderer<? super T, ? super T> renderer3) {
		theRenderer = painter;
		theHover = new RenderChild(renderer2, thePreFocus, theMidFocus);
		theFocus = new RenderChild(renderer3, theMidFocus, thePostFocus);
		return this;
	}

	@Override
	public AbstractLayout getLayout() {
		return (AbstractLayout) super.getLayout();
	}

	@Override
	public void setLayout(LayoutManager mgr) {
		if (!(mgr instanceof AbstractLayout))
			throw new IllegalArgumentException(
				TiledPane.class.getSimpleName() + " requires an instance of " + AbstractLayout.class.getName());
		super.setLayout(mgr);
	}

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
		return getLayout().getScrollableUnitIncrement(this, visibleRect, orientation, direction);
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
		return getLayout().getScrollableBlockIncrement(this, visibleRect, orientation, direction);
	}

	@Override
	public boolean getScrollableTracksViewportWidth() {
		return getLayout().getScrollableTracksViewportWidth(this);
	}

	@Override
	public boolean getScrollableTracksViewportHeight() {
		return getLayout().getScrollableTracksViewportHeight(this);
	}

	@Override
	public Dimension getPreferredSize() {
		return getLayoutSize(0);
	}

	@Override
	public Dimension getMaximumSize() {
		return getLayoutSize(1);
	}

	@Override
	public Dimension getMinimumSize() {
		return getLayoutSize(-1);
	}

	private Dimension getLayoutSize(int type) {
		return getLayout().layoutSize(type, getSize(), getInsets(), layoutChildren());
	}

	private List<AbstractLayout.LayoutChild> layoutChildren() {
		if (theValues.isEmpty())
			Collections.emptyList();
		T value = theValues.getFirst();
		ModelCell<T, T> cell = new ModelCell.Default<>(LambdaUtils.constantSupplier(value, value::toString, null), value, 0, 0, false,
			false, false, false, false, false);
		Component renderer = theRenderer.getCellRendererComponent(this, cell, CellRenderContext.DEFAULT);
		return Collections.nCopies(theValues.size(), new AbstractLayout.LayoutChild.ComponentLayoutChild(renderer));
	}

	@Override
	public void doLayout() {
		theGlassPane.setBounds(0, 0, getWidth(), getHeight());
		theValueBounds.clear();
		theValueBounds.addAll(Arrays.asList(getLayout().layoutContainer(getSize(), getInsets(), layoutChildren())));

		if (theHover.component != null) {
			if (!theHover.value.getElementId().isPresent()) {
				theHover.replace(false, null, -1, false);
			} else {
				int hoverIndex = theValues.getElementsBefore(theHover.value.getElementId());
				theHover.component.setBounds(theValueBounds.get(hoverIndex));
			}
		}

		if (theFocus.component != null) {
			if (!theFocus.value.getElementId().isPresent()) {
				theFocus.replace(true, null, -1, false);
			} else {
				int focusIndex = theValues.getElementsBefore(theFocus.value.getElementId());
				theFocus.component.setBounds(theValueBounds.get(focusIndex));
			}
		}

		// Place the mid-focus component correctly
		if (theFocus.component != null) {
			if (theFocus.preFocus == theMidFocus)
				theMidFocus.setBounds(theFocus.component.getX() - 1, theFocus.component.getY() - 1, 0, 0);
			else
				theMidFocus.setBounds(theFocus.component.getX() + 1, theFocus.component.getY() + 1, 0, 0);
		} else
			theMidFocus.setBounds(0, 0, 0, 0);
	}

	@Override
	protected void paintChildren(Graphics g) {
		Rectangle clip = g.getClipBounds();
		TiledPanelRenderMC<T> cell = new TiledPanelRenderMC<>();
		int row = -1;
		for (CollectionElement<T> value : theValues.elements()) {
			row++;
			Rectangle bounds = theValueBounds.get(row);
			if (bounds == null || !clip.intersects(bounds))
				continue;
			if (theHover.component != null && theHover.valueEquals(value.getElementId()))
				continue; // Handled by the actual component
			else if (theFocus.component != null && theFocus.valueEquals(value.getElementId()))
				continue; // Handled by the actual component

			cell.set(value.get(), row).setEnabled(theValues.mutableElement(value.getElementId()).isEnabled());
			Component renderer = theRenderer.getCellRendererComponent(this, cell, CellRenderContext.DEFAULT);
			theRenderPane.paintComponent(g, renderer, this, bounds.x, bounds.y, bounds.width, bounds.height, true);
		}
		super.paintChildren(g);
	}

	class RenderChild {
		final ObservableCellRenderer<? super T, ? super T> renderer;
		final Component preFocus;
		final Component postFocus;
		CollectionElement<T> value;
		Component component;

		RenderChild(ObservableCellRenderer<? super T, ? super T> renderer, Component preFocus, Component postFocus) {
			this.renderer = renderer;
			this.preFocus = preFocus;
			this.postFocus = postFocus;
		}

		boolean valueEquals(ElementId id) {
			return value != null && value.getElementId().equals(id);
		}

		void replace(boolean focus, CollectionElement<T> newValue, int index, boolean refresh) {
			boolean repaint = false;
			if (newValue == null) {
				if (value == null)
					return;
				if (component != null) {
					remove(component);
					component = null;
				}
				value = null;
				repaint = true;
			} else if (!refresh && valueEquals(newValue.getElementId()))
				return;
			else {
				value = newValue;
				T v = newValue.get();
				ModelCell<T, T> cell = new ModelCell.Default<>(LambdaUtils.constantSupplier(v, v::toString, null), v, index, 0, focus,
					focus, !focus, !focus, false, true)//
					.setEnabled(theValues.mutableElement(newValue.getElementId()).isEnabled());
				Component newRender = renderer.getCellRendererComponent(TiledPane.this, cell, CellRenderContext.DEFAULT);
				if (component != newRender) {
					repaint = true;
					if (component != null)
						remove(component);
					if (newRender != null) {
						// If we're the first component, add between the pre-focus and the mid-focus, otherwise just before the post-focus.
						add(newRender, getComponentIndex());
					}
				}
				if (newRender != null) {
					Rectangle bounds = theValueBounds.get(index);
					if (!bounds.equals(newRender.getBounds())) {
						repaint = true;
						newRender.setBounds(bounds);
					}
				}
				component = newRender;

				if (focus && theHover.valueEquals(newValue.getElementId()))
					theHover.replace(false, null, -1, false);
			}

			if (repaint) {
				revalidate();
				repaint();
			}
		}

		private int getComponentIndex() {
			for (int i = 0; i < getComponentCount(); i++) {
				if (getComponent(i) == preFocus)
					return i + 1;
			}
			throw new IllegalStateException();
		}
	}

	static class SimpleFocusComponent extends Component {
	}

	static class TiledPanelRenderMC<T> implements ModelCell<T, T> {
		private T theValue;
		private int theRow;
		private String isEnabled;

		TiledPanelRenderMC<T> set(T value, int row) {
			theValue = value;
			theRow = row;
			return this;
		}

		@Override
		public T getModelValue() {
			return theValue;
		}

		@Override
		public int getRowIndex() {
			return theRow;
		}

		@Override
		public boolean isSelected() {
			return false;
		}

		@Override
		public boolean hasFocus() {
			return false;
		}

		@Override
		public boolean isRowHovered() {
			return false;
		}

		@Override
		public boolean isExpanded() {
			return false;
		}

		@Override
		public boolean isLeaf() {
			return true;
		}

		@Override
		public String isEnabled() {
			return isEnabled;
		}

		@Override
		public T getCellValue() {
			return theValue;
		}

		@Override
		public int getColumnIndex() {
			return 0;
		}

		@Override
		public boolean isCellHovered() {
			return false;
		}

		@Override
		public boolean isCellFocused() {
			return false;
		}

		@Override
		public ModelCell<T, T> setEnabled(String enabled) {
			isEnabled=enabled;
			return this;
		}
	}
}
