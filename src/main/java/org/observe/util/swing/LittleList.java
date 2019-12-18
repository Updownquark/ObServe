package org.observe.util.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultListSelectionModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.border.Border;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.observe.util.swing.ObservableCellRenderer.CellRenderContext;
import org.qommons.ArrayUtils;
import org.qommons.LambdaUtils;

public class LittleList<E> extends JComponent {
	public static class ItemActionEvent<E> extends ActionEvent {
		private final E theItem;
		private final int theIndex;

		public ItemActionEvent(Object source, String command, E item, int index) {
			super(source, ACTION_PERFORMED, command);
			theItem = item;
			theIndex = index;
		}

		public E getItem() {
			return theItem;
		}

		public int getIndex() {
			return theIndex;
		}
	}

	private final SyntheticContainer theSyntheticContainer;
	private final CategoryRenderStrategy<E, E> theRenderStrategy;
	private ObservableCellRenderer<? super E, ? super E> theRenderer;
	private ObservableCellEditor<? super E, ? super E> theEditor;
	private Component theEditorComponent;
	private ObservableListModel<E> theModel;
	private ListSelectionModel theSelectionModel;
	private final List<Action> theItemActions;

	private BiConsumer<Border, ? super E> theBorderAdjuster;

	public LittleList(ObservableListModel<E> model) {
		theModel = model;
		theRenderStrategy = new CategoryRenderStrategy<>("Value", model.getWrapped().getType(), v -> v);
		setLayout(new FlowLayout(FlowLayout.LEFT));
		theSyntheticContainer = new SyntheticContainer();
		add(theSyntheticContainer); // So the tree lock works right
		theRenderer = new ObservableCellRenderer.DefaultObservableCellRenderer<>((m, c) -> String.valueOf(c))//
			.decorate((cell, cd) -> cd.bold(cell.isSelected()));
		theSelectionModel = new DefaultListSelectionModel();

		theItemActions = new ArrayList<>();
		theModel.addListDataListener(new ListDataListener() {
			@Override
			public void intervalRemoved(ListDataEvent e) {
				theSelectionModel.removeIndexInterval(e.getIndex0(), e.getIndex1());
				moveEditor();
				revalidate();
			}

			@Override
			public void intervalAdded(ListDataEvent e) {
				theSelectionModel.insertIndexInterval(e.getIndex0(), e.getIndex1() - e.getIndex1() + 1, true);
				moveEditor();
				revalidate();
			}

			@Override
			public void contentsChanged(ListDataEvent e) {
				int selected = theSelectionModel.getMinSelectionIndex();
				if (selected >= 0 && theSelectionModel.getMaxSelectionIndex() == selected && e.getIndex0() <= selected
					&& e.getIndex1() >= selected)
					reEdit();
				revalidate();
			}
		});

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int selected = getItemIndexAt(e.getX(), e.getY());
				if (selected >= 0) {
					int clickCode = theSyntheticContainer.getBounds(selected).getClickCode(e.getX(), e.getY());
					if (clickCode > 0) {
						Action action = theItemActions.get(clickCode - 1);
						ItemActionEvent<E> event = new ItemActionEvent<>(LittleList.this, "clicked", theModel.getElementAt(selected),
							selected);
						action.actionPerformed(event);
						return;
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
					selected = theSelectionModel.getMinSelectionIndex();
				}
				if (modified) {
					reEdit();
					revalidate();
					repaint();
				}
			}
		});
		setItemBorder(BorderFactory.createLineBorder(Color.black, 1, true));
	}

	public ListSelectionModel getSelectionModel() {
		return theSelectionModel;
	}

	public LittleList<E> setSelectionModel(ListSelectionModel selectionModel) {
		theSelectionModel = selectionModel;
		reEdit();
		revalidate();
		repaint();
		return this;
	}

	public LittleList<E> setEditor(ObservableCellEditor<? super E, ? super E> editor) {
		theEditor = editor;
		reEdit();
		return this;
	}

	public LittleList<E> addItemAction(Action action) {
		theItemActions.add(action);
		moveEditor();
		revalidate();
		return this;
	}

	public LittleList<E> removeItemAction(Action action) {
		if (theItemActions.remove(action)) {
			moveEditor();
			revalidate();
		}
		return this;
	}

	public void setItemBorder(Border border) {
		theSyntheticContainer.theHolder.setBorder(border);
	}

	public void adjustBorder(BiConsumer<Border, ? super E> borderAdjuster) {
		if (theBorderAdjuster == null)
			theBorderAdjuster = borderAdjuster;
		else if (borderAdjuster == null)
			theBorderAdjuster = null;
		else {
			BiConsumer<Border, ? super E> oldAdjuster = theBorderAdjuster;
			theBorderAdjuster = (border, item) -> {
				oldAdjuster.accept(border, item);
				borderAdjuster.accept(border, item);
			};
		}
	}

	void moveEditor() {
		if (theEditorComponent != null) {
			int selected = theSelectionModel.getMinSelectionIndex();
			Rectangle bounds = getItemBounds(selected);
			theEditorComponent.setBounds(bounds);
		}
	}

	void reEdit() {
		if (theEditorComponent != null) {
			remove(theEditorComponent);
			theEditorComponent = null;
		}
		int selected = theSelectionModel.getMinSelectionIndex();
		if (theEditor != null && selected >= 0 && selected == theSelectionModel.getMaxSelectionIndex()) {
			theEditorComponent = theEditor.getListCellEditorComponent(this, theModel.getElementAt(selected), selected, true);
			Rectangle bounds = getItemBounds(selected);
			theEditorComponent.setBounds(bounds);
			add(theEditorComponent);
		}
	}

	public ObservableListModel<E> getModel() {
		return theModel;
	}

	public CategoryRenderStrategy<E, E> getRenderStrategy() {
		return theRenderStrategy;
	}

	public int getItemIndexAt(int x, int y) {
		for (int i = 0; i < theModel.getSize(); i++) {
			if (theSyntheticContainer.getBounds(i).holderBounds.contains(x, y)) {
				if (theSyntheticContainer.getBounds(i).itemContains(x, y))
					return i;
				else
					return -1;
			}
		}
		return -1;
	}

	public Rectangle getItemBounds(int itemIndex) {
		if (itemIndex < 0 || itemIndex >= theModel.getSize())
			throw new IndexOutOfBoundsException(itemIndex + " of " + theModel.getSize());
		return theSyntheticContainer.getBounds(itemIndex).getItemBounds();
	}

	@Override
	public Dimension getPreferredSize() {
		Dimension d = getLayout().preferredLayoutSize(theSyntheticContainer.setMode(true));
		theSyntheticContainer.postRender();
		return d;
	}

	@Override
	public Dimension getMaximumSize() {
		Dimension d;
		if (getLayout() instanceof LayoutManager2)
			d = ((LayoutManager2) getLayout()).maximumLayoutSize(theSyntheticContainer.setMode(true));
		else
			d = getLayout().preferredLayoutSize(theSyntheticContainer.setMode(true));
		theSyntheticContainer.postRender();
		return d;
	}

	@Override
	public Dimension getMinimumSize() {
		Dimension d = getLayout().minimumLayoutSize(theSyntheticContainer.setMode(true));
		theSyntheticContainer.postRender();
		return d;
	}

	@Override
	public void doLayout() {
		getLayout().layoutContainer(theSyntheticContainer.setMode(true));
		theSyntheticContainer.postRender();
	}

	@Override
	protected void paintChildren(Graphics g) {
		theSyntheticContainer.setMode(false);
		super.paintChildren(g);
		theSyntheticContainer.postRender();
	}

	static class ItemBoundsData {
		final Rectangle holderBounds;
		final Rectangle itemBounds;
		final List<Rectangle> actionBounds;

		ItemBoundsData() {
			holderBounds = new Rectangle();
			itemBounds = new Rectangle();
			actionBounds = new ArrayList<>(3);
		}

		boolean itemContains(int x, int y) {
			x -= holderBounds.getX();
			y -= holderBounds.getY();
			return itemBounds.contains(x, y);
		}

		Rectangle getItemBounds() {
			Rectangle r = new Rectangle(itemBounds);
			r.add(holderBounds.getLocation());
			return r;
		}

		int getClickCode(int x, int y) {
			if (x < holderBounds.getX() || y < holderBounds.getY())
				return -1;
			x -= (int) holderBounds.getX();
			y -= (int) holderBounds.getY();
			if (x >= holderBounds.getWidth() || y >= holderBounds.getHeight())
				return -1;
			int itemX = (int) itemBounds.getX() - x;
			int itemY = (int) itemBounds.getY() - y;
			if (itemX >= 0 && itemX < itemBounds.getWidth() && itemY >= 0 && itemY < itemBounds.getHeight())
				return 0;
			for (int i = 0; i < actionBounds.size(); i++) {
				int actionX = (int) actionBounds.get(i).getX() - x;
				int actionY = (int) actionBounds.get(i).getY() - y;

				if (actionX >= 0 && actionX < actionBounds.get(i).getWidth() && actionY >= 0 && actionY < actionBounds.get(i).getHeight())
					return i + 1;
			}
			return 0;
		}
	}

	protected class SyntheticContainer extends JComponent {
		private final ItemHolder theHolder;
		private List<ItemBoundsData> bounds;
		private boolean layoutOrRender;

		SyntheticContainer() {
			theHolder = new ItemHolder();
			add(theHolder);
			bounds = new ArrayList<>();
		}

		SyntheticContainer setMode(boolean layoutOrRender) {
			this.layoutOrRender = layoutOrRender;
			theHolder.prepare();
			super.setBounds(0, 0, LittleList.this.getWidth(), LittleList.this.getHeight());
			return this;
		}

		ItemBoundsData getBounds(int renderIndex) {
			return bounds.get(renderIndex);
		}

		@Override
		public int getComponentCount() {
			return theModel.getSize();
		}

		@Override
		public Component getComponent(int n) {
			if (n > 0)
				theHolder.postRender(n - 1);
			E row = theModel.getElementAt(n);
			Component rendered = theRenderer.getCellRendererComponent(LittleList.this,
				new ModelCell.Default<>(LambdaUtils.constantSupplier(row, () -> String.valueOf(row), row), row, n, 0, //
					theSelectionModel.isSelectedIndex(n), theSelectionModel.isSelectedIndex(n), true, true),
				CellRenderContext.DEFAULT);
			boolean newBounds;
			if (n >= bounds.size()) {
				newBounds = true;
				bounds.add(new ItemBoundsData());
			} else
				newBounds = false;
			if (theEditorComponent != null && theSelectionModel.getMinSelectionIndex() == n) {
				if (layoutOrRender)
					theHolder.forRender(n, row, theEditorComponent, !newBounds);
				else
					theHolder.forRender(n, row, null, !newBounds);
			} else
				theHolder.forRender(n, row, rendered, !newBounds);
			return theHolder;
		}

		void postRender() {
			if (theModel.getSize() > 0)
				theHolder.postRender(theModel.getSize() - 1);
		}
	}

	protected class ItemHolder extends JPanel {
		private final SyntheticComponent theComponent;
		private final List<JLabel> theItemActionLabels;

		ItemHolder() {
			super(new JustifiedBoxLayout(false).mainJustified().crossCenter());
			theComponent = new SyntheticComponent();
			add(theComponent);
			theItemActionLabels = new ArrayList<>();
		}

		void prepare() {
			ArrayUtils.adjust(theItemActionLabels, theItemActions, new ArrayUtils.DifferenceListener<JLabel, Action>() {
				@Override
				public boolean identity(JLabel o1, Action o2) {
					return true;
				}

				@Override
				public JLabel added(Action o, int mIdx, int retIdx) {
					JLabel label = new JLabel();
					sync(label, o);
					add(label, retIdx + 1);
					return label;
				}

				@Override
				public JLabel removed(JLabel o, int oIdx, int incMod, int retIdx) {
					remove(incMod);
					return null;
				}

				@Override
				public JLabel set(JLabel o1, int idx1, int incMod, Action o2, int idx2, int retIdx) {
					sync(o1, o2);
					return o1;
				}
			});
		}

		void sync(JLabel actionLabel, Action action) {
			actionLabel.setText(string(action.getValue(Action.NAME)));
			actionLabel.setIcon((Icon) action.getValue(Action.SMALL_ICON));
		}

		private String string(Object s) {
			return s == null ? null : s.toString();
		}

		ItemHolder forRender(int renderIndex, E item, Component rendered, boolean initBounds) {
			if (theBorderAdjuster != null && getBorder() != null)
				theBorderAdjuster.accept(getBorder(), item);
			theComponent.forRender(renderIndex, rendered);
			if (initBounds) {
				ItemBoundsData bounds = theSyntheticContainer.getBounds(renderIndex);
				super.setBounds(bounds.holderBounds);
				theComponent.internalSetBounds(bounds.itemBounds);
			}
			return this;
		}

		void postRender(int renderIndex) {
			ItemBoundsData bounds = theSyntheticContainer.getBounds(renderIndex);
			bounds.holderBounds.setBounds(getBounds());
			bounds.itemBounds.setBounds(theComponent.getBounds());
			while (bounds.actionBounds.size() < theItemActionLabels.size())
				bounds.actionBounds.add(new Rectangle());
			while (bounds.actionBounds.size() > theItemActionLabels.size())
				bounds.actionBounds.remove(bounds.actionBounds.size() - 1);
			for (int i = 0; i < theItemActionLabels.size(); i++)
				theItemActionLabels.get(i).getBounds(bounds.actionBounds.get(i));
		}
	}

	protected class SyntheticComponent extends JComponent {
		int theRenderIndex;
		private Component theRendered;

		SyntheticComponent forRender(int index, Component rendered) {
			theRenderIndex = index;
			theRendered = rendered;
			return this;
		}

		void internalSetBounds(Rectangle bounds) {
			super.setBounds(bounds);
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
		}

		@Override
		public void setSize(int width, int height) {
			super.setSize(width, height);
			if (theRendered != null)
				theRendered.setSize(width, height);
		}

		@Override
		public void setBounds(int x, int y, int width, int height) {
			super.setBounds(x, y, width, height);
			if (theRendered != null)
				theRendered.setBounds(x, y, width, height);
		}

		@Override
		protected void paintChildren(Graphics g) {
			super.paintChildren(g);
			if (theRendered != null)
				theRendered.paint(g);
		}
	}
}
