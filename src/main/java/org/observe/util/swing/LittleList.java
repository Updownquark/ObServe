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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.DefaultListSelectionModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.border.Border;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.observe.util.swing.ObservableCellRenderer.CellRenderContext;
import org.qommons.ArrayUtils;
import org.qommons.LambdaUtils;

public class LittleList<E> extends JComponent implements Scrollable {
	private static final int CLICK_TOLERANCE = 4;

	interface ItemAction<E> {
		void configureAction(Action action, E item, int index);

		void actionPerformed(E item, int index, Object cause);
	}

	private final SyntheticContainer theSyntheticContainer;
	private final CategoryRenderStrategy<E, E> theRenderStrategy;
	private boolean isVerticalScrolling;
	private ObservableCellRenderer<? super E, ? super E> theRenderer;
	private ObservableCellEditor<? super E, ? super E> theEditor;
	private Component theEditorComponent;
	private ObservableListModel<E> theModel;
	private ListSelectionModel theSelectionModel;
	private final List<ItemAction<? super E>> theItemActions;

	private BiConsumer<Border, ? super E> theBorderAdjuster;
	private boolean isTooltipOverridden;
	private String theTooltip;

	public LittleList(ObservableListModel<E> model) {
		theModel = model;
		theRenderStrategy = new CategoryRenderStrategy<>("Value", model.getWrapped().getType(), v -> v);
		isVerticalScrolling = true;
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
				repaint();
			}

			@Override
			public void intervalAdded(ListDataEvent e) {
				theSelectionModel.insertIndexInterval(e.getIndex0(), e.getIndex1() - e.getIndex1() + 1, true);
				moveEditor();
				revalidate();
				repaint();
				if (getModel().getSize() == e.getIndex1() - e.getIndex0() + 1 && getParent() instanceof JViewport
					&& getParent().getParent().getParent() != null) {
					getParent().getParent().getParent().revalidate();
				}
			}

			@Override
			public void contentsChanged(ListDataEvent e) {
				int selected = theSelectionModel.getMinSelectionIndex();
				if (selected >= 0 && theSelectionModel.getMaxSelectionIndex() == selected && e.getIndex0() <= selected
					&& e.getIndex1() >= selected)
					reEdit();
				revalidate();
				repaint();
			}
		});

		MouseAdapter mouseListener = new MouseAdapter() {
			private Point pressLocation;

			@Override
			public void mousePressed(MouseEvent e) {
				pressLocation = e.getPoint();
			}

			private boolean testReleaseForClick(MouseEvent e) {
				return Math.abs(e.getX() - pressLocation.x) <= CLICK_TOLERANCE//
					&& Math.abs(e.getY() - pressLocation.y) <= CLICK_TOLERANCE;
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (pressLocation != null && testReleaseForClick(e))//
					clicked(e);
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				if (pressLocation != null && !testReleaseForClick(e))
					pressLocation = null;
				int selected = getItemIndexAt(e.getX(), e.getY());
				String tooltip;
				if (selected >= 0) {
					int clickCode = theSyntheticContainer.getBounds(selected).getClickCode(e.getX(), e.getY());
					if (clickCode > 0) {
						ItemAction<? super E> action = theItemActions.get(clickCode - 1);
						tooltip = theSyntheticContainer.getActionTooltip(selected, action);
						if (tooltip != null) {
							isTooltipOverridden = true;
							LittleList.super.setToolTipText(tooltip);
						} else {
							tooltip = theSyntheticContainer.getItemTooltip(selected);
							if (tooltip != null) {
								isTooltipOverridden = true;
								LittleList.super.setToolTipText(tooltip);
							} else if (isTooltipOverridden) {
								isTooltipOverridden = false;
								LittleList.super.setToolTipText(theTooltip);
							}
						}
					} else {
						tooltip = theSyntheticContainer.getItemTooltip(selected);
						if (tooltip != null) {
							isTooltipOverridden = true;
							LittleList.super.setToolTipText(tooltip);
						} else if (isTooltipOverridden) {
							isTooltipOverridden = false;
							LittleList.super.setToolTipText(theTooltip);
						}
					}
				} else if (isTooltipOverridden) {
					isTooltipOverridden = false;
					LittleList.super.setToolTipText(theTooltip);
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (pressLocation != null)
					pressLocation = null;
			}

			@Override
			public void mouseExited(MouseEvent e) {
				if (pressLocation != null)
					pressLocation = null;
				isTooltipOverridden = false;
				setToolTipText(theTooltip);
			}

			private void clicked(MouseEvent e) {
				int selected = getItemIndexAt(e.getX(), e.getY());
				if (selected >= 0) {
					int clickCode = theSyntheticContainer.getBounds(selected).getClickCode(e.getX(), e.getY());
					if (clickCode > 0) {
						ItemAction<? super E> action = theItemActions.get(clickCode - 1);
						theSyntheticContainer.performAction(action, selected, e);
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
		};

		addMouseListener(mouseListener);
		addMouseMotionListener(mouseListener);
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

	public LittleList<E> addItemAction(ItemAction<? super E> action) {
		theItemActions.add(action);
		moveEditor();
		revalidate();
		return this;
	}

	public LittleList<E> removeItemAction(ItemAction<? super E> action) {
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

	public LittleList<E> scrollAxis(boolean vertical) {
		isVerticalScrolling = vertical;
		return this;
	}

	@Override
	public void setToolTipText(String text) {
		theTooltip = text;
		if (!isTooltipOverridden)
			super.setToolTipText(text);
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
			if (theSyntheticContainer.getBounds(i).holderBounds.contains(x, y))
				return i;
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
		int mainLimit, mainPadding, crossPadding;
		FlowLayout layout = (FlowLayout) getLayout();
		if (isVerticalScrolling) {
			mainLimit = getWidth();
			mainPadding = layout.getHgap();
			crossPadding = layout.getVgap();
		} else {
			mainLimit = getHeight();
			mainPadding = layout.getVgap();
			crossPadding = layout.getHgap();
		}
		theSyntheticContainer.setMode(true);
		int cross = 0;
		int main = 0;
		int maxRowLength = 0;
		int maxRowThickness = 0;
		for (int i = 0; i < theModel.getSize(); i++) {
			Dimension ps = theSyntheticContainer.getComponent(i).getPreferredSize();
			int psMain = isVerticalScrolling ? ps.width : ps.height;
			int psCross = isVerticalScrolling ? ps.height : ps.width;
			if (main == 0 || main + mainPadding + psMain <= mainLimit) {
				main += mainPadding + psMain;
				maxRowLength = Math.max(maxRowLength, main);
				maxRowThickness = Math.max(maxRowThickness, psCross);
			} else {
				if (cross > 0)
					cross += crossPadding;
				cross += maxRowThickness;
				main = psMain;
				maxRowThickness = psCross;
			}
		}
		if (cross > 0)
			cross += crossPadding;
		cross += maxRowThickness;
		Dimension d;
		if (isVerticalScrolling)
			d = new Dimension(maxRowLength, cross);
		else
			d = new Dimension(cross, maxRowLength);
		Insets ins = getInsets();
		d.width += ins.left + ins.right + layout.getHgap() * 2;
		d.height += ins.top + ins.bottom + layout.getVgap() * 2;
		return d;
	}

	@Override
	public Dimension getMaximumSize() {
		Dimension d;
		if (getLayout() instanceof LayoutManager2)
			d = ((LayoutManager2) getLayout()).maximumLayoutSize(theSyntheticContainer.setMode(true));
		else
			d = getLayout().preferredLayoutSize(theSyntheticContainer.setMode(true));
		return d;
	}

	@Override
	public Dimension getMinimumSize() {
		Dimension d = getLayout().minimumLayoutSize(theSyntheticContainer.setMode(true));
		return d;
	}

	@Override
	public void doLayout() {
		getLayout().layoutContainer(theSyntheticContainer.setMode(true));
	}

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		Dimension d = getLayout().preferredLayoutSize(theSyntheticContainer.setMode(true));
		return d;
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
		// TODO Auto-generated method stub
		return 10;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
		// TODO Auto-generated method stub
		return 100;
	}

	@Override
	public boolean getScrollableTracksViewportWidth() {
		return isVerticalScrolling;
	}

	@Override
	public boolean getScrollableTracksViewportHeight() {
		return !isVerticalScrolling;
	}

	@Override
	protected void paintChildren(Graphics g) {
		theSyntheticContainer.setMode(false);
		super.paintChildren(g);
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
				int actionX = x - (int) actionBounds.get(i).getX();
				int actionY = y - (int) actionBounds.get(i).getY();

				if (actionX >= 0 && actionX < actionBounds.get(i).getWidth() && actionY >= 0 && actionY < actionBounds.get(i).getHeight())
					return i + 1;
			}
			return 0;
		}

		StringBuilder print(StringBuilder str) {
			printPoint(str, holderBounds.getLocation());
			printSize(str, holderBounds.getSize());
			printPoint(str, itemBounds.getLocation());
			printSize(str, itemBounds.getSize());
			return str;
		}

		@Override
		public String toString() {
			return print(new StringBuilder()).toString();
		}
	}

	private static void printPoint(StringBuilder str, Point location) {
		str.append('(').append(location.x).append(", ").append(location.y).append(')');
	}

	private static void printSize(StringBuilder str, Dimension size) {
		str.append('[').append(size.width).append(", ").append(size.height).append(']');
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
			super.setBounds(0, 0, LittleList.this.getWidth(), LittleList.this.getHeight());
			return this;
		}

		ItemBoundsData getBounds(int renderIndex) {
			return bounds.get(renderIndex);
		}

		@Override
		public void invalidate() {}

		@Override
		public void repaint(long tm, int x, int y, int width, int height) {}

		@Override
		public int getComponentCount() {
			return theModel.getSize();
		}

		@Override
		public Component getComponent(int n) {
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

		void addAction(int actionIndex){
			for(ItemBoundsData bd : bounds)
				bd.actionBounds.add(actionIndex, new Rectangle());
		}

		void removeAction(int actionIndex){
			for (ItemBoundsData bd : bounds)
				bd.actionBounds.remove(actionIndex);
		}

		String getItemTooltip(int selected) {
			E row = theModel.getElementAt(selected);
			Component rendered = theRenderer.getCellRendererComponent(LittleList.this,
				new ModelCell.Default<>(LambdaUtils.constantSupplier(row, () -> String.valueOf(row), row), row, selected, 0, //
					theSelectionModel.isSelectedIndex(selected), theSelectionModel.isSelectedIndex(selected), true, true),
				CellRenderContext.DEFAULT);
			return rendered instanceof JComponent ? ((JComponent) rendered).getToolTipText() : null;
		}

		String getActionTooltip(int itemIndex, ItemAction<? super E> action) {
			E row = theModel.getElementAt(itemIndex);
			return theHolder.getActionTooltip(row, itemIndex, action);
		}

		boolean performAction(ItemAction<? super E> action, int itemIndex, Object cause) {
			E item = theModel.getElementAt(itemIndex);
			return theHolder.performAction(item, itemIndex, action, cause);
		}

		void printSizes() {
			StringBuilder str = new StringBuilder();
			printSize(str, getSize());
			for (ItemBoundsData b : bounds) {
				str.append("\n\t");
				b.print(str);
			}
			System.out.println(str);
		}
	}

	protected class ItemHolder extends JPanel {
		private final SyntheticComponent theComponent;
		private final List<JLabel> theItemActionLabels;
		private final Action theAction;
		private ItemBoundsData theBounds;

		ItemHolder() {
			super(new JustifiedBoxLayout(false).setMargin(2, 2, 0, 2).mainJustified().crossCenter());
			theComponent = new SyntheticComponent();
			add(theComponent);
			theAction = new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent e) {}
			};
			theItemActionLabels = new ArrayList<>();
		}

		ItemHolder forRender(int renderIndex, E item, Component rendered, boolean initBounds) {
			if (theBorderAdjuster != null && getBorder() != null)
				theBorderAdjuster.accept(getBorder(), item);
			theBounds = theSyntheticContainer.getBounds(renderIndex);
			while (theBounds.actionBounds.size() < theItemActionLabels.size())
				theBounds.actionBounds.add(new Rectangle());
			theComponent.forRender(renderIndex, rendered);
			if (initBounds) {
				super.setBounds(theBounds.holderBounds.x, theBounds.holderBounds.y, theBounds.holderBounds.width,
					theBounds.holderBounds.height);
				theComponent.internalSetBounds(theBounds.itemBounds);
			}
			ArrayUtils.adjust(theItemActionLabels, theItemActions, new ArrayUtils.DifferenceListener<JLabel, ItemAction<? super E>>() {
				@Override
				public boolean identity(JLabel o1, ItemAction<? super E> o2) {
					return true;
				}

				@Override
				public JLabel added(ItemAction<? super E> o, int mIdx, int retIdx) {
					JLabel label = new JLabel();
					((SyntheticContainer) getParent()).addAction(retIdx);
					sync(label, o, renderIndex, retIdx);
					add(label, retIdx + 1);
					return label;
				}

				@Override
				public JLabel removed(JLabel o, int oIdx, int incMod, int retIdx) {
					((SyntheticContainer) getParent()).removeAction(incMod);
					remove(incMod);
					return null;
				}

				@Override
				public JLabel set(JLabel o1, int idx1, int incMod, ItemAction<? super E> o2, int idx2, int retIdx) {
					sync(o1, o2, renderIndex, incMod);
					return o1;
				}
			});
			invalidate();
			return this;
		}

		void sync(JLabel actionLabel, ItemAction<? super E> action, int modelIndex, int actionIndex) {
			action.configureAction(theAction, null, modelIndex);
			String text = string(theAction.getValue(Action.NAME));
			boolean diff = false;
			if (!Objects.equals(text, actionLabel.getText())) {
				diff = true;
				actionLabel.setText(text);
			}
			Icon icon = (Icon) theAction.getValue(Action.SMALL_ICON);
			if (!Objects.equals(icon, actionLabel.getIcon())) {
				diff = true;
				actionLabel.setIcon(icon);
			}
			Object font = theAction.getValue("font");
			if (font instanceof Consumer) {
				try {
					((Consumer<Object>) font).accept(ObservableSwingUtils.label(actionLabel));
				} catch (ClassCastException e) {}
			} else if (font instanceof Font) {
				if (!font.equals(actionLabel.getFont()))
					actionLabel.setFont((Font) font);
			}
			boolean visible = !Boolean.FALSE.equals(theAction.getValue("visible"));
			if (actionLabel.isVisible() != visible) {
				diff = true;
				actionLabel.setVisible(visible);
			}
			if (actionLabel.isEnabled() != theAction.isEnabled()) {
				diff = true;
				actionLabel.setEnabled(theAction.isEnabled());
			}
			if (diff)
				actionLabel.invalidate();
			actionLabel.setBounds(theBounds.actionBounds.get(actionIndex));
		}

		private String string(Object s) {
			return s == null ? null : s.toString();
		}

		@Override
		public void setBounds(int x, int y, int width, int height) {
			super.setBounds(x, y, width, height);
			theBounds.holderBounds.setBounds(x, y, width, height);
			getLayout().layoutContainer(this);
			theBounds.itemBounds.setBounds(theComponent.getBounds());
			for (int i = 0; i < theItemActionLabels.size(); i++)
				theItemActionLabels.get(i).getBounds(theBounds.actionBounds.get(i));
		}

		@Override
		public boolean isValidateRoot() {
			return true;
		}

		String getActionTooltip(E item, int itemIndex, ItemAction<? super E> action) {
			action.configureAction(theAction, item, itemIndex);
			return string(theAction.getValue(Action.LONG_DESCRIPTION));
		}

		boolean performAction(E item, int itemIndex, ItemAction<? super E> action, Object cause) {
			action.configureAction(theAction, item, itemIndex);
			if (!theAction.isEnabled())
				return false;
			action.actionPerformed(item, itemIndex, cause);
			return true;
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
