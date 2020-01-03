package org.observe.util.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Rectangle;

/** A {@link FlowLayout} that implements {@link ScrollableSwingLayout} */
public class ScrollableFlowLayout extends FlowLayout implements ScrollableSwingLayout {
	/** @see FlowLayout#FlowLayout() */
	public ScrollableFlowLayout() {
		super();
	}

	/** @see FlowLayout#FlowLayout(int) */
	public ScrollableFlowLayout(int align) {
		super(align);
	}

	/** @see FlowLayout#FlowLayout(int, int, int) */
	public ScrollableFlowLayout(int align, int hgap, int vgap) {
		super(align, hgap, vgap);
	}

	@Override
	public void addLayoutComponent(Component comp, Object constraints) {}

	@Override
	public float getLayoutAlignmentX(Container target) {
		return 0;
	}

	@Override
	public float getLayoutAlignmentY(Container target) {
		return 0;
	}

	@Override
	public void invalidateLayout(Container target) {}

	@Override
	public Dimension minimumLayoutSize(Container target) {
		return layoutSize(target, '-');
	}

	@Override
	public Dimension preferredLayoutSize(Container target) {
		return layoutSize(target, 'p');
	}

	@Override
	public Dimension maximumLayoutSize(Container target) {
		return layoutSize(target, '+');
	}

	private Dimension layoutSize(Container target, char type) {
		int mainLimit, mainPadding, crossPadding;
		mainLimit = target.getWidth();
		mainPadding = getHgap();
		crossPadding = getVgap();
		int cross = 0;
		int main = mainPadding;
		int maxRowLength = 0;
		int maxRowThickness = 0;
		for (int i = 0; i < target.getComponentCount(); i++) {
			Component c = target.getComponent(i);
			if (!c.isVisible())
				continue;
			Dimension ps;
			switch (type) {
			case '-':
				ps = c.getMinimumSize();
				break;
			case '+':
				ps = c.getMaximumSize();
				break;
			default:
				ps = c.getPreferredSize();
				break;
			}
			int psMain = ps.width;
			int psCross = ps.height;
			if (main == 0 || main + mainPadding + psMain <= mainLimit) {
				maxRowThickness = Math.max(maxRowThickness, psCross);
			} else {
				if (cross > 0)
					cross += crossPadding;
				cross += maxRowThickness;
				main = mainPadding;
				maxRowThickness = psCross;
			}
			main += mainPadding + psMain;
			maxRowLength = Math.max(maxRowLength, main);
		}
		if (maxRowThickness > 0) {
			if (cross > 0)
				cross += crossPadding;
			cross += maxRowThickness;
		}
		cross += crossPadding;
		Dimension d;
		d = new Dimension(maxRowLength, cross);
		Insets ins = target.getInsets();
		d.width += ins.left + ins.right;
		d.height += ins.top + ins.bottom;
		return d;
	}

	@Override
	public Dimension getPreferredScrollableViewportSize(Container parent) {
		return super.preferredLayoutSize(parent);
	}

	@Override
	public int getScrollableUnitIncrement(Container parent, Rectangle visibleRect, int orientation, int direction) {
		return 10;
	}

	@Override
	public int getScrollableBlockIncrement(Container parent, Rectangle visibleRect, int orientation, int direction) {
		return 100;
	}

	@Override
	public boolean getScrollableTracksViewportWidth(Container parent) {
		return true;
	}

	@Override
	public boolean getScrollableTracksViewportHeight(Container parent) {
		return false;
	}
}
