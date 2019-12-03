package org.observe.util.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import org.qommons.LambdaUtils;

public class LittleList<E> extends JComponent {
	private final SyntheticContainer theSyntheticContainer;
	private ObservableCellRenderer<? super E, ? super E> theRenderer;
	private ObservableCellEditor<? super E, ? super E> theEditor;
	private ObservableListModel<E> theModel;
	private int theSelectedItem;

	public LittleList(ObservableListModel<E> model) {
		theModel = model;
		setLayout(new FlowLayout(FlowLayout.LEFT));
		theSyntheticContainer = new SyntheticContainer();
		add(theSyntheticContainer); // So the tree lock works right
		theRenderer = new ObservableCellRenderer.DefaultObservableCellRenderer<>((m, c) -> String.valueOf(c));
	}

	@Override
	public Dimension getPreferredSize() {
		return getLayout().preferredLayoutSize(theSyntheticContainer);
	}

	@Override
	public Dimension getMaximumSize() {
		if (getLayout() instanceof LayoutManager2)
			return ((LayoutManager2) getLayout()).maximumLayoutSize(theSyntheticContainer);
		else
			return getLayout().preferredLayoutSize(theSyntheticContainer);
	}

	@Override
	public Dimension getMinimumSize() {
		return getLayout().minimumLayoutSize(theSyntheticContainer);
	}

	@Override
	public void doLayout() {
		getLayout().layoutContainer(theSyntheticContainer);
	}

	@Override
	protected void paintChildren(Graphics g) {
		super.paintChildren(g);
		// TODO
	}

	protected class SyntheticContainer extends java.awt.Container {
		List<Rectangle> storedBounds = new ArrayList<>();

		SyntheticContainer clear() {
		}

		@Override
		public Dimension getSize() {
			return LittleList.this.getSize();
		}

		@Override
		public Rectangle getBounds() {
			return LittleList.this.getBounds();
		}

		@Override
		public int getWidth() {
			return LittleList.this.getWidth();
		}

		@Override
		public int getHeight() {
			return LittleList.this.getHeight();
		}

		@Override
		public int getComponentCount() {
			return theModel.getSize();
		}

		@Override
		public Component getComponent(int n) {
			E row = theModel.getElementAt(n);
			boolean selected = theSelectedItem == n;
			return theRenderer.getCellRendererComponent(LittleList.this, LambdaUtils.constantSupplier(row, () -> String.valueOf(row), row),
				row, selected, false, true, selected, n, 0, null);
		}

		@Override
		public Insets getInsets() {
			return LittleList.this.getInsets();
		}
	}
}
