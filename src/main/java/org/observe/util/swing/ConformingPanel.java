package org.observe.util.swing;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.beans.PropertyChangeListener;

import javax.swing.JPanel;

/** A simple panel that does a better job than JPanel of respecting its components' min/max/preferred sizes */
public class ConformingPanel extends JPanel {
	private boolean isPrefSizeDirty = true;
	private boolean isMinSizeDirty = true;
	private boolean isMaxSizeDirty = true;
	private Shading theShading;

	private final PropertyChangeListener invalidateListener = evt -> invalidate();

	/** Creates a panel */
	public ConformingPanel() {
		super();
		addContainerListener(new ContainerListener() {
			@Override
			public void componentRemoved(ContainerEvent e) {
				e.getChild().removePropertyChangeListener("invalidate", invalidateListener);
				e.getChild().removePropertyChangeListener("minimumSize", invalidateListener);
				e.getChild().removePropertyChangeListener("preferredSize", invalidateListener);
				e.getChild().removePropertyChangeListener("maximumSize", invalidateListener);
			}

			@Override
			public void componentAdded(ContainerEvent e) {
				e.getChild().addPropertyChangeListener("invalidate", invalidateListener);
				e.getChild().addPropertyChangeListener("minimumSize", invalidateListener);
				e.getChild().addPropertyChangeListener("preferredSize", invalidateListener);
				e.getChild().addPropertyChangeListener("maximumSize", invalidateListener);
			}
		});
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

	public Shading getShading() {
		return theShading;
	}

	public ConformingPanel setShading(Shading shading) {
		theShading = shading;
		repaint();
		return this;
	}

	@Override
	public void invalidate() {
		isPrefSizeDirty = isMinSizeDirty = isMaxSizeDirty = true;
		super.invalidate();
		this.firePropertyChange("invalidate", null, null);
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

	@Override
	protected void paintComponent(Graphics g) {
		if (theShading != null)
			theShading.shade((Graphics2D) g, getSize(), getBackground());
		else
			super.paintComponent(g);
	}
}
