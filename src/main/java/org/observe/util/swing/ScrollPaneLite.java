package org.observe.util.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.lang.reflect.Field;

import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneLayout;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** A scrollable pane that does not take up as much space as a typical {@link JScrollPane} */
public class ScrollPaneLite extends JScrollPane {
	private int theScrollBarThickness;

	/**
	 * @param view The component to scroll
	 * @param vsbPolicy The vertical scroll bar policy
	 * @param hsbPolicy The horizontal scroll bar policy
	 * @see JScrollPane#VERTICAL_SCROLLBAR_NEVER
	 * @see JScrollPane#VERTICAL_SCROLLBAR_AS_NEEDED
	 * @see JScrollPane#VERTICAL_SCROLLBAR_ALWAYS
	 * @see JScrollPane#HORIZONTAL_SCROLLBAR_NEVER
	 * @see JScrollPane#HORIZONTAL_SCROLLBAR_AS_NEEDED
	 * @see JScrollPane#HORIZONTAL_SCROLLBAR_ALWAYS
	 */
	public ScrollPaneLite(Component view, int vsbPolicy, int hsbPolicy) {
		super(view, vsbPolicy, hsbPolicy);
		theScrollBarThickness = 5;
		getVerticalScrollBar().setUnitIncrement(10);
		getHorizontalScrollBar().setUnitIncrement(10);
	}

	/** Creates an empty scroll pane */
	public ScrollPaneLite() {
		this(null, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
	}

	/** @param view The component to scroll */
	public ScrollPaneLite(Component view) {
		this(view, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
	}

	/**
	 * Creates an empty scroll pane
	 *
	 * @param vsbPolicy The vertical scroll bar policy
	 * @param hsbPolicy The horizontal scroll bar policy
	 * @see JScrollPane#VERTICAL_SCROLLBAR_NEVER
	 * @see JScrollPane#VERTICAL_SCROLLBAR_AS_NEEDED
	 * @see JScrollPane#VERTICAL_SCROLLBAR_ALWAYS
	 * @see JScrollPane#HORIZONTAL_SCROLLBAR_NEVER
	 * @see JScrollPane#HORIZONTAL_SCROLLBAR_AS_NEEDED
	 * @see JScrollPane#HORIZONTAL_SCROLLBAR_ALWAYS
	 */
	public ScrollPaneLite(int vsbPolicy, int hsbPolicy) {
		this(null, vsbPolicy, hsbPolicy);
	}

	@Override
	public JScrollBar createHorizontalScrollBar() {
		return new ScrollBarLite(JScrollBar.HORIZONTAL);
	}

	@Override
	public JScrollBar createVerticalScrollBar() {
		return new ScrollBarLite(JScrollBar.VERTICAL);
	}

	class ScrollBarLite extends JScrollBar {
		public ScrollBarLite(int orientation) {
			super(orientation);
		}

		@Override
		public void setUI(ScrollBarUI ui) {
			super.setUI(adjust(ui));
		}

		protected ScrollBarUI adjust(ScrollBarUI sbui) {
			if (sbui instanceof BasicScrollBarUI) {
				try {
					Field field = BasicScrollBarUI.class.getField("incrButton");
					if (!field.isAccessible())
						field.setAccessible(true);
					field.set(sbui, null);

					field = BasicScrollBarUI.class.getField("decrButton");
					if (!field.isAccessible())
						field.setAccessible(true);
					field.set(sbui, null);
				} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
					// If we can't, we can't
				}
			}
			return sbui;
		}

		protected Dimension adjust(Dimension d) {
			switch (getOrientation()) {
			case HORIZONTAL:
				d.height = Math.min(d.height, theScrollBarThickness);
				break;
			case VERTICAL:
				d.width = Math.min(d.width, theScrollBarThickness);
				break;
			}
			return d;
		}

		@Override
		public Dimension getPreferredSize() {
			return adjust(super.getPreferredSize());
		}

		@Override
		public Dimension getMinimumSize() {
			return adjust(super.getMinimumSize());
		}

		@Override
		public Dimension getMaximumSize() {
			return adjust(super.getMaximumSize());
		}
	}

	static class LiteScrollLayout extends ScrollPaneLayout.UIResource {
	}
}
