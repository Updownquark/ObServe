package org.observe.util.swing;

import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;

import javax.swing.JPanel;

/** A simple panel that does a better job than JPanel of respecting its components' min/max/preferred sizes */
public class ConformingPanel extends JPanel {
	private boolean isPrefSizeDirty = true;
	private boolean isMinSizeDirty = true;
	private boolean isMaxSizeDirty = true;

	/** Creates a panel */
	public ConformingPanel() {
		super();
	}

	/** @param layout The layout for the panel */
	public ConformingPanel(LayoutManager layout) {
		super(layout);
	}

	/**
	 * @param name The name for this panel
	 * @return This panel
	 */
	public ConformingPanel withName(String name) {
		setName(name);
		return this;
	}

	@Override
	public void invalidate() {
		isPrefSizeDirty = isMinSizeDirty = isMaxSizeDirty = true;
		super.invalidate();
	}

	@Override
	public Dimension getPreferredSize() {
		if (isPrefSizeDirty || !isPreferredSizeSet()) {
			LayoutManager layout = getLayout();
			if (layout != null) {
				setPreferredSize(layout.preferredLayoutSize(this));
				isPrefSizeDirty = false;
			}
		}
		return super.getPreferredSize();
	}

	@Override
	public Dimension getMaximumSize() {
		if (isMaxSizeDirty || !isMaximumSizeSet()) {
			LayoutManager layout = getLayout();
			if (layout instanceof LayoutManager2) {
				setMaximumSize(((LayoutManager2) layout).maximumLayoutSize(this));
				isMaxSizeDirty = false;
			}
		}
		return super.getMaximumSize();
	}

	@Override
	public Dimension getMinimumSize() {
		if (isMinSizeDirty || !isMinimumSizeSet()) {
			LayoutManager layout = getLayout();
			if (layout != null) {
				setMinimumSize(layout.minimumLayoutSize(this));
				isMinSizeDirty = false;
			}
		}
		return super.getMinimumSize();
	}
}
