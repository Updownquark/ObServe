package org.observe.util.swing;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseWheelEvent;

import javax.swing.BoundedRangeModel;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.ViewportLayout;
import javax.swing.plaf.LayerUI;

/**
 * <p>
 * This class exists to fix a flaw in {@link JScrollPane}, that when the user scrolls the mouse wheel over an inner scroll pane, an outer
 * scroll pane will NEVER see that event, even if the inner scroll pane is already scrolled to the end.
 * </p>
 * <p>
 * This can cause a sitation where an inner scroll pane which dominates the view port of the outer pane prevents the user from scrolling
 * down or up past it, forcing them to use the scroll bar itself.
 * </p>
 * <p>
 * I find this situation extremely annoying, and though this fix is slightly annoying, it works and is therefore worth it.
 * </p>
 * <p>
 * Using this class and adding the fixed scroll pane's {@link #getLayer() layer} instead of the scroll pane itself will cause scroll events
 * over scroll panes inside this one to not be swallowed (if the inner scroll pane is scrolled to the appropriate end) and to be applied to
 * this scroll pane appropriately.
 * </p>
 * <p>
 * This class also addresses some other annoying behavior of JScrollPane with the {@link #scrollable(boolean, boolean)} method, which is
 * that even when a scroll pane is set to never display a horizontal scroll bar, the scroll pane gives no resistance to being resized below
 * the minimum size of its view, eclipsing part of it such that it cannot be displayed.
 * </p>
 */
public class FixedScrollPane {
	private final JLayer<JScrollPane> theLayer;

	/** @param content The component to scroll */
	public FixedScrollPane(Component content) {
		this(new JScrollPane(content));
	}

	/** @param scroll The scroll pane to fix */
	public FixedScrollPane(JScrollPane scroll) {
		scroll.getVerticalScrollBar().setUnitIncrement(15);
		scroll.getHorizontalScrollBar().setUnitIncrement(15);
		theLayer = new JLayer<>(scroll, new LayerUI<JScrollPane>() {
			@Override
			public void installUI(JComponent c) {
				super.installUI(c);
				if (c instanceof JLayer)
					((JLayer<?>) c).setLayerEventMask(AWTEvent.MOUSE_WHEEL_EVENT_MASK);
			}

			@Override
			public void uninstallUI(JComponent c) {
				if (c instanceof JLayer)
					((JLayer<?>) c).setLayerEventMask(0);
				super.uninstallUI(c);
			}

			@Override
			protected void processMouseWheelEvent(MouseWheelEvent e, JLayer<? extends JScrollPane> l) {
				if (!(e.getComponent() instanceof JScrollPane) || e.getComponent() == l.getView())
					return;
				JScrollPane child = (JScrollPane) e.getComponent();
				boolean dispatch = false;
				if (!dispatch && !child.getVerticalScrollBar().isVisible())
					dispatch = true;
				BoundedRangeModel model = child.getVerticalScrollBar().getModel();
				if (!dispatch && e.getWheelRotation() > 0 //
					&& model.getValue() + model.getExtent() >= model.getMaximum())
					dispatch = true;
				if (!dispatch && e.getWheelRotation() < 0 //
					&& model.getValue() <= model.getMinimum())
					dispatch = true;
				if (dispatch)
					l.getView().dispatchEvent(SwingUtilities.convertMouseEvent(child, e, l.getView()));
			}
		});
	}

	/**
	 * @param vertical Whether the scroll pane should show a vertical scroll bar (as needed) or just always display all of its vertical
	 *        content
	 * @param horizontal Whether the scroll pane should show a vertical scroll bar (as needed) or just always display all of its horizontal
	 *        content
	 * @return This fixer
	 */
	public FixedScrollPane scrollable(boolean vertical, boolean horizontal) {
		theLayer.getView()
		.setVerticalScrollBarPolicy(vertical ? JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED : JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		theLayer.getView()
		.setHorizontalScrollBarPolicy(horizontal ? JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED : JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		if (!vertical || !horizontal) {
			getScrollPane().getViewport().setLayout(new ConstrainedViewportLayout(!vertical, !horizontal));
		}
		return this;
	}

	/** @return The layer to add to a container instead of the actual scroll pane */
	public JLayer<JScrollPane> getLayer() {
		return theLayer;
	}

	/** @return The scroll pane that this fixer fixes. Do NOT add this to a container. */
	public JScrollPane getScrollPane() {
		return theLayer.getView();
	}

	private static final class ConstrainedViewportLayout extends ViewportLayout {
		private final boolean isVerticalFixed;
		private final boolean isHorizontalFixed;

		ConstrainedViewportLayout(boolean verticalFixed, boolean horizontalFixed) {
			isVerticalFixed = verticalFixed;
			isHorizontalFixed = horizontalFixed;
		}

		@Override
		public Dimension minimumLayoutSize(Container parent) {
			Dimension superSize = super.minimumLayoutSize(parent);
			Component view = ((JViewport) parent).getView();
			return view == null ? superSize : new Dimension(//
				isHorizontalFixed ? view.getMinimumSize().width : superSize.width, //
					isVerticalFixed ? view.getMinimumSize().height : superSize.height);
		}

		@Override
		public void layoutContainer(Container parent) {
			JViewport vp = (JViewport) parent;
			Component view = vp.getView();
			Scrollable scrollableView = null;

			if (view == null) {
				return;
			} else if (view instanceof Scrollable) {
				scrollableView = (Scrollable) view;
			}

			Dimension viewPrefSize = view.getPreferredSize();
			Dimension vpSize = vp.getSize();
			Dimension extentSize = vp.toViewCoordinates(vpSize);
			Dimension viewSize = new Dimension(viewPrefSize);

			Point viewPosition = vp.getViewPosition();

			if (isHorizontalFixed) {
				viewSize.width = vpSize.width;
				if (scrollableView != null) {
					if (scrollableView.getScrollableTracksViewportHeight()) {
						viewSize.height = vpSize.height;
					}
				}

				if ((viewPosition.y + extentSize.height) > viewSize.height) {
					viewPosition.y = Math.max(0, viewSize.height - extentSize.height);
				}
				if (scrollableView == null) {
					if ((viewPosition.y == 0) && (vpSize.height > viewPrefSize.height)) {
						viewSize.height = vpSize.height;
					}
				}
			}
			if (isVerticalFixed) {
				viewSize.height = vpSize.height;
				if (scrollableView != null) {
					if (scrollableView.getScrollableTracksViewportWidth()) {
						viewSize.width = vpSize.width;
					}
				}

				if ((viewPosition.x + extentSize.width) > viewSize.width) {
					viewPosition.x = Math.max(0, viewSize.width - extentSize.width);
				}
				if (scrollableView == null) {
					if ((viewPosition.x == 0) && (vpSize.width > viewPrefSize.width)) {
						viewSize.width = vpSize.width;
					}
				}
			}

			vp.setViewPosition(viewPosition);
			vp.setViewSize(viewSize);
		}
	}
}
