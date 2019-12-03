package org.observe.util.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListSelectionModel;
import javax.swing.JComponent;
import javax.swing.ListSelectionModel;
import javax.swing.border.Border;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.observe.util.swing.ObservableCellRenderer.CellRenderContext;
import org.qommons.LambdaUtils;

public class LittleList<E> extends JComponent {
	private final SyntheticContainer theSyntheticContainer;
	private ObservableCellRenderer<? super E, ? super E> theRenderer;
	private ObservableCellEditor<? super E, ? super E> theEditor;
	private ObservableListModel<E> theModel;
	private ListSelectionModel theSelectionModel;
	private int theSelectedItem;

	public LittleList(ObservableListModel<E> model) {
		theModel = model;
		setLayout(new FlowLayout(FlowLayout.LEFT));
		theSyntheticContainer = new SyntheticContainer();
		add(theSyntheticContainer); // So the tree lock works right
		theRenderer = new ObservableCellRenderer.DefaultObservableCellRenderer<>((m, c) -> String.valueOf(c))//
			.modify(fv -> fv.bold(fv.isSelected()));
		theSelectionModel = new DefaultListSelectionModel();
		theSelectedItem = -1;

		theModel.addListDataListener(new ListDataListener() {
			@Override
			public void intervalRemoved(ListDataEvent e) {
				theSelectionModel.removeIndexInterval(e.getIndex0(), e.getIndex1());
				revalidate();
			}

			@Override
			public void intervalAdded(ListDataEvent e) {
				theSelectionModel.insertIndexInterval(e.getIndex0(), e.getIndex1() - e.getIndex1() + 1, true);
				revalidate();
			}

			@Override
			public void contentsChanged(ListDataEvent e) {
				revalidate();
			}
		});

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int selected = -1;
				for (int i = 0; i < theModel.getSize(); i++) {
					if (theSyntheticContainer.getBounds(i).contains(e.getX(), e.getY())) {
						selected = i;
						break;
					}
				}
				boolean modified = false;
				if (selected < 0) {
					if (e.isShiftDown() || e.isControlDown()) {} else {
						theSelectionModel.clearSelection();
						modified = true;
					}
				} else {
					modified = true;
					if (e.isShiftDown()) {
						if (theSelectionModel.getAnchorSelectionIndex() >= 0)
							theSelectionModel.setSelectionInterval(theSelectionModel.getAnchorSelectionIndex(), selected);
						else
							theSelectionModel.addSelectionInterval(selected, selected);
					} else if (e.isControlDown()) {
						if (theSelectionModel.isSelectedIndex(selected))
							theSelectionModel.removeSelectionInterval(selected, selected);
						else
							theSelectionModel.addSelectionInterval(selected, selected);
					} else
						theSelectionModel.setSelectionInterval(selected, selected);
				}
				if (modified) {
					revalidate();
					repaint();
				}
			}
		});
	}

	public ListSelectionModel getSelectionModel() {
		return theSelectionModel;
	}

	public LittleList<E> setSelectionModel(ListSelectionModel selectionModel) {
		theSelectionModel = selectionModel;
		invalidate();
		return this;
	}

	public ObservableListModel<E> getModel() {
		return theModel;
	}

	@Override
	public Dimension getPreferredSize() {
		return getLayout().preferredLayoutSize(theSyntheticContainer.setMode(true));
	}

	@Override
	public Dimension getMaximumSize() {
		if (getLayout() instanceof LayoutManager2)
			return ((LayoutManager2) getLayout()).maximumLayoutSize(theSyntheticContainer.setMode(true));
		else
			return getLayout().preferredLayoutSize(theSyntheticContainer.setMode(true));
	}

	@Override
	public Dimension getMinimumSize() {
		return getLayout().minimumLayoutSize(theSyntheticContainer.setMode(true));
	}

	@Override
	public void doLayout() {
		getLayout().layoutContainer(theSyntheticContainer.setMode(true));
	}

	@Override
	protected void paintChildren(Graphics g) {
		theSyntheticContainer.setMode(false);
		super.paintChildren(g);
	}

	protected class SyntheticContainer extends JComponent {
		private final SyntheticComponent theComponent;
		private List<Rectangle> storedBounds;
		private boolean layoutOrRender;

		SyntheticContainer() {
			theComponent = new SyntheticComponent();
			add(theComponent);
			storedBounds = new ArrayList<>();
		}

		SyntheticContainer setMode(boolean layoutOrRender) {
			this.layoutOrRender = layoutOrRender;
			super.setBounds(0, 0, LittleList.this.getWidth(), LittleList.this.getHeight());
			return this;
		}

		Rectangle getBounds(int renderIndex) {
			return storedBounds.get(renderIndex);
		}

		@Override
		public int getComponentCount() {
			return theModel.getSize();
		}

		@Override
		public Component getComponent(int n) {
			E row = theModel.getElementAt(n);
			Component rendered = theRenderer.getCellRendererComponent(LittleList.this,
				LambdaUtils.constantSupplier(row, () -> String.valueOf(row), row),
				row, theSelectionModel.isSelectedIndex(n), false, true, theSelectedItem == n, n, 0, CellRenderContext.DEFAULT);
			if (n >= storedBounds.size())
				storedBounds.add(new Rectangle(rendered.getBounds()));
			else
				rendered.setBounds(storedBounds.get(n));
			if (layoutOrRender)
				return theComponent.forRender(n, rendered);
			else if (n == theSelectedItem && theEditor != null)
				return theComponent.forRender(n, null);
			else
				return rendered;
		}
	}

	protected class SyntheticComponent extends JComponent {
		int theRenderIndex;
		private Component theRendered;

		SyntheticComponent forRender(int index, Component rendered) {
			theRenderIndex = index;
			theRendered = rendered;
			super.setBounds(theSyntheticContainer.getBounds(index));
			return this;
		}

		@Override
		public FontMetrics getFontMetrics(Font font) {
			return theRendered.getFontMetrics(font);
		}

		@Override
		public boolean isPreferredSizeSet() {
			return theRendered != null && theRendered.isPreferredSizeSet();
		}

		@Override
		public boolean isMinimumSizeSet() {
			return theRendered != null && theRendered.isMinimumSizeSet();
		}

		@Override
		public boolean isMaximumSizeSet() {
			return theRendered != null && theRendered.isMaximumSizeSet();
		}

		@Override
		public Dimension getPreferredSize() {
			return theRendered == null ? new Dimension(0, 0) : theRendered.getPreferredSize();
		}

		@Override
		public Dimension getMaximumSize() {
			return theRendered == null ? new Dimension(0, 0) : theRendered.getMaximumSize();
		}

		@Override
		public Dimension getMinimumSize() {
			return theRendered == null ? new Dimension(0, 0) : theRendered.getMinimumSize();
		}

		@Override
		public Border getBorder() {
			if (theRendered instanceof JComponent)
				return ((JComponent) theRendered).getBorder();
			else
				return null;
		}

		@Override
		public Insets getInsets() {
			if (theRendered instanceof JComponent)
				return ((JComponent) theRendered).getInsets();
			else
				return new Insets(0, 0, 0, 0);
		}

		@Override
		public Insets getInsets(Insets insets) {
			if (theRendered instanceof JComponent)
				return ((JComponent) theRendered).getInsets(insets);
			else {
				insets.set(0, 0, 0, 0);
				return insets;
			}
		}

		@Override
		public float getAlignmentY() {
			return theRendered == null ? 0 : theRendered.getAlignmentY();
		}

		@Override
		public float getAlignmentX() {
			return theRendered == null ? 0 : theRendered.getAlignmentX();
		}

		@Override
		public int getBaseline(int width, int height) {
			return theRendered == null ? 0 : theRendered.getBaseline(width, height);
		}

		@Override
		public BaselineResizeBehavior getBaselineResizeBehavior() {
			return theRendered == null ? BaselineResizeBehavior.CONSTANT_ASCENT : theRendered.getBaselineResizeBehavior();
		}

		@Override
		public boolean isVisible() {
			return theRendered != null && theRendered.isVisible();
		}

		@Override
		public boolean isEnabled() {
			return theRendered != null && theRendered.isEnabled();
		}

		@Override
		public boolean isFontSet() {
			return theRendered != null && theRendered.isFontSet();
		}

		@Override
		public void setLocation(int x, int y) {
			super.setLocation(x, y);
			if (theRendered != null)
				theRendered.setLocation(x, y);
			theSyntheticContainer.getBounds(theRenderIndex).setLocation(x, y);
		}

		@Override
		public void setSize(int width, int height) {
			super.setSize(width, height);
			if (theRendered != null)
				theRendered.setSize(width, height);
			theSyntheticContainer.getBounds(theRenderIndex).setSize(width, height);
		}

		@Override
		public void setBounds(int x, int y, int width, int height) {
			super.setBounds(x, y, width, height);
			if (theRendered != null)
				theRendered.setBounds(x, y, width, height);
			theSyntheticContainer.getBounds(theRenderIndex).setBounds(x, y, width, height);
		}
	}
}
