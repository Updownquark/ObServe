package org.observe.util.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.lang.reflect.Field;

import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneLayout;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;

public class ScrollPaneLite extends JScrollPane {
	public ScrollPaneLite() {
		super();
	}

	public ScrollPaneLite(Component view, int vsbPolicy, int hsbPolicy) {
		super(view, vsbPolicy, hsbPolicy);
	}

	public ScrollPaneLite(Component view) {
		super(view);
	}

	public ScrollPaneLite(int vsbPolicy, int hsbPolicy) {
		super(vsbPolicy, hsbPolicy);
	}

	@Override
	public JScrollBar createHorizontalScrollBar() {
		return new ScrollBarLite(JScrollBar.HORIZONTAL);
	}

	@Override
	public JScrollBar createVerticalScrollBar() {
		return new ScrollBarLite(JScrollBar.VERTICAL);
	}

	static class ScrollBarLite extends JScrollBar {
		public ScrollBarLite(int orientation) {
			super(orientation);
		}

		@Override
		public void setUI(ScrollBarUI ui) {
			super.setUI(adjust(ui));
		}

		protected ScrollBarUI adjust(ScrollBarUI ui) {
			if (ui instanceof BasicScrollBarUI) {
				try {
					Field field = BasicScrollBarUI.class.getField("incrButton");
					if (!field.isAccessible())
						field.setAccessible(true);
					field.set(ui, null);

					field = BasicScrollBarUI.class.getField("decrButton");
					if (!field.isAccessible())
						field.setAccessible(true);
					field.set(ui, null);
				} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
					// If we can't, we can't
				}
			}
			return ui;
		}

		protected Dimension adjust(Dimension d) {
			switch (getOrientation()) {
			case HORIZONTAL:
				d.height = Math.min(d.height, 2);
				break;
			case VERTICAL:
				d.width = Math.min(d.width, 2);
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
