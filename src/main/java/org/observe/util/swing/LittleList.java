package org.observe.util.swing;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.observe.Subscription;
import org.observe.util.swing.ObservableCellRenderer.CellRenderContext;
import org.qommons.LambdaUtils;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.AdjustmentOrder;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;

public class LittleList<E> extends JComponent implements Scrollable {
	private static final int CLICK_TOLERANCE = 4;

	interface ItemAction<E> {
		void configureAction(Action action, E item, int index);

		void actionPerformed(E item, int index, Object cause);
	}

	private final SyntheticContainer theSyntheticContainer;
	private final CategoryRenderStrategy<E, E> theRenderStrategy;
	private Component theEditorComponent;
	private Subscription theEditing;
	private ObservableListModel<E> theModel;
	private ListSelectionModel theSelectionModel;
	private final List<ItemAction<? super E>> theItemActions;

	private BiConsumer<Border, ? super E> theBorderAdjuster;
	private boolean isTooltipOverridden;
	private String theTooltip;

	public LittleList(ObservableListModel<E> model) {
		theModel = model;
		theRenderStrategy = new CategoryRenderStrategy<>("Value", model.getWrapped().getType(), v -> v);
		setLayout(new ScrollableFlowLayout(FlowLayout.LEFT));
		theSyntheticContainer = new SyntheticContainer();
		add(theSyntheticContainer); // So the tree lock works right
		theRenderStrategy.withRenderer(new ObservableCellRenderer.DefaultObservableCellRenderer<E, E>((m, c) -> String.valueOf(c))//
			.decorate((cell, cd) -> cd.bold(cell.isSelected())));
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
					reEdit(e);
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
					if (clickCode > 0 && selected < theModel.getSize()) {
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
					reEdit(e);
					revalidate();
					repaint();
				}
			}
		};

		addMouseListener(mouseListener);
		addMouseMotionListener(mouseListener);
		addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				reEdit(null);// stop editing
			}
		});
		setItemBorder(BorderFactory.createLineBorder(Color.black, 1, true));
	}

	@Override
	public void setLayout(LayoutManager mgr) {
		if (!(mgr instanceof ScrollableSwingLayout))
			throw new IllegalArgumentException(
				"The layout for a " + getClass().getSimpleName() + " must implement " + ScrollableSwingLayout.class.getName());
		super.setLayout(mgr);
	}

	public ListSelectionModel getSelectionModel() {
		return theSelectionModel;
	}

	public LittleList<E> setSelectionModel(ListSelectionModel selectionModel) {
		theSelectionModel = selectionModel;
		reEdit(null);
		revalidate();
		repaint();
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

	public LittleList<E> setItemBorder(Border border) {
		theSyntheticContainer.theHolder.setBorder(border);
		return this;
	}

	public LittleList<E> adjustBorder(BiConsumer<Border, ? super E> borderAdjuster) {
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

	void reEdit(EventObject cause) {
		if (theEditing != null) {
			theEditing.unsubscribe();
			theEditing = null;
			remove(theEditorComponent);
			theEditorComponent = null;
		}
		if (cause == null)
			return;
		int selected = theSelectionModel.getMinSelectionIndex();
		ObservableCellEditor<E, E> editor;
		E row = null;
		boolean add;
		if (selected < 0 || selected != theSelectionModel.getMaxSelectionIndex()) {
			editor = null;
			add = false;
		} else if (selected < theModel.getSize()) {
			editor = theRenderStrategy.getMutator().getEditor();
			row = theModel.getElementAt(selected);
			add = false;
		} else if (theRenderStrategy.getAddRow() != null) {
			editor = theRenderStrategy.getAddRow().getMutator().getEditor();
			row = theRenderStrategy.getAddRow().getEditSeedRow().get();
			add = true;
		} else {
			editor = null;
			add = false;
		}
		if (editor != null) {
			boolean edit;
			if (add)
				edit = theModel.getWrapped().canAdd(row) == null && editor.isCellEditable(cause);
			else
				edit = theModel.getWrapped().mutableElement(theModel.getWrapped().getElement(selected).getElementId()).isEnabled() == null
				&& editor.isCellEditable(cause);
			if (edit) {
				boolean hovered = cause instanceof MouseEvent;
				ModelCell<E, E> cell = new ModelCell.Default<>(LambdaUtils.constantSupplier(row, row::toString, null), row, selected, 0,
					true, true, hovered, hovered, false, true);
				theEditorComponent = editor.getCellEditorComponent(this, cell, theModel.getWrapped(), theRenderStrategy);
				CellEditorListener editListener = new CellEditorListener() {
					private boolean isEditing = true;

					@Override
					public void editingStopped(ChangeEvent e) {
						if (!isEditing)
							return;
						if (add) {
							theModel.getWrapped().add((E) editor.getCellEditorValue());
						} else {
							theModel.getWrapped().set(selected, (E) editor.getCellEditorValue());
						}
						editingCanceled(e);
					}

					@Override
					public void editingCanceled(ChangeEvent e) {
						if (!isEditing)
							return;
						theEditing.unsubscribe();
						theEditing = null;
						remove(theEditorComponent);
						theEditorComponent = null;
						revalidate();
						repaint();
					}
				};
				editor.addCellEditorListener(editListener);
				KeyListener keyListener = new KeyAdapter() {
					@Override
					public void keyPressed(KeyEvent e) {
						if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
							editor.cancelCellEditing();
						else if (e.getKeyCode() == KeyEvent.VK_ENTER)
							editor.stopCellEditing();
					}
				};
				theEditorComponent.addKeyListener(keyListener);
				theEditing = () -> {
					editor.removeCellEditorListener(editListener);
					theEditorComponent.removeKeyListener(keyListener);
				};
				add(theEditorComponent, 0);
				EventQueue.invokeLater(() -> {
					theEditorComponent.requestFocus();
					repaint();
				});
			}
		}
	}

	public ObservableListModel<E> getModel() {
		return theModel;
	}

	public CategoryRenderStrategy<E, E> getRenderStrategy() {
		return theRenderStrategy;
	}

	public int getItemIndexAt(int x, int y) {
		for (int i = 0; i < theSyntheticContainer.getComponentCount(); i++) {
			if (theSyntheticContainer.getBounds(i).holderBounds.contains(x, y))
				return i;
		}
		return -1;
	}

	public Rectangle getItemBounds(int itemIndex) {
		if (itemIndex < 0 || itemIndex >= theSyntheticContainer.getComponentCount())
			throw new IndexOutOfBoundsException(itemIndex + " of " + theSyntheticContainer.getComponentCount());
		return theSyntheticContainer.getBounds(itemIndex).getItemBounds();
	}

	@Override
	public Dimension getMinimumSize() {
		return getLayout().minimumLayoutSize(theSyntheticContainer.setMode(true));
	}

	@Override
	public Dimension getPreferredSize() {
		return getLayout().preferredLayoutSize(theSyntheticContainer.setMode(true));
	}

	@Override
	public Dimension getMaximumSize() {
		return ((ScrollableSwingLayout) getLayout()).maximumLayoutSize(theSyntheticContainer.setMode(true));
	}

	@Override
	public void doLayout() {
		getLayout().layoutContainer(theSyntheticContainer.setMode(true));
	}

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		return ((ScrollableSwingLayout) getLayout()).getPreferredScrollableViewportSize(theSyntheticContainer.setMode(true));
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
		return ((ScrollableSwingLayout) getLayout()).getScrollableUnitIncrement(theSyntheticContainer.setMode(true), visibleRect,
			orientation, direction);
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
		return ((ScrollableSwingLayout) getLayout()).getScrollableBlockIncrement(theSyntheticContainer.setMode(true), visibleRect,
			orientation, direction);
	}

	@Override
	public boolean getScrollableTracksViewportWidth() {
		return ((ScrollableSwingLayout) getLayout()).getScrollableTracksViewportWidth(theSyntheticContainer.setMode(true));
	}

	@Override
	public boolean getScrollableTracksViewportHeight() {
		return ((ScrollableSwingLayout) getLayout()).getScrollableTracksViewportHeight(theSyntheticContainer.setMode(true));
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
			r.x += holderBounds.x;
			r.y += holderBounds.y;
			return r;
		}

		int getClickCode(int x, int y) {
			if (x < holderBounds.getX() || y < holderBounds.getY())
				return -1;
			x -= (int) holderBounds.getX();
			y -= (int) holderBounds.getY();
			if (x >= holderBounds.getWidth() || y >= holderBounds.getHeight())
				return -1;
			int itemX = x - (int) itemBounds.getX();
			int itemY = y - (int) itemBounds.getY();
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
		boolean isRecursive;

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
			int count = theModel.getSize();
			if (theRenderStrategy.getAddRow() != null)
				count++;
			return count;
		}

		@Override
		public Component getComponent(int n) {
			// This is called from ItemHolder.super.setBounds() within the forRender() call, via mixOnReshaping()
			// but the value is not really used because there are no heavyweight components in here.
			// It will mess up the rendering if we call the renderer here and it's wasted effort anyway
			if (isRecursive)
				return theHolder;
			isRecursive = true;
			try {
				E row;
				ObservableCellRenderer<E, E> renderer;
				if (n < theModel.getSize()) {
					row = theModel.getElementAt(n);
					renderer = (ObservableCellRenderer<E, E>) theRenderStrategy.getRenderer();
				} else if (n == theModel.getSize() && theRenderStrategy.getAddRow() != null) {
					row = theRenderStrategy.getAddRow().getEditSeedRow().get();
					renderer = (ObservableCellRenderer<E, E>) theRenderStrategy.getAddRow().getRenderer();
				} else
					throw new IndexOutOfBoundsException(n + " of " + getComponentCount());
				// TODO Support hover
				Component rendered = renderer
					.getCellRendererComponent(LittleList.this,
						new ModelCell.Default<>(LambdaUtils.constantSupplier(row, () -> String.valueOf(row), row), row, n, 0, //
							theSelectionModel.isSelectedIndex(n), theSelectionModel.isSelectedIndex(n), false, false, true, true),
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
			} finally {
				isRecursive = false;
			}
		}

		void addAction(int actionIndex) {
			for (ItemBoundsData bd : bounds)
				bd.actionBounds.add(actionIndex, new Rectangle());
		}

		void removeAction(int actionIndex) {
			for (ItemBoundsData bd : bounds)
				bd.actionBounds.remove(actionIndex);
		}

		String getItemTooltip(int selected) {
			E row;
			ObservableCellRenderer<E, E> renderer;
			ModelCell<E, E> cell;
			if (selected < theModel.getSize()) {
				row = theModel.getElementAt(selected);
				cell = new ModelCell.Default<>(LambdaUtils.constantSupplier(row, () -> String.valueOf(row), row), row, selected, 0, //
					theSelectionModel.isSelectedIndex(selected), theSelectionModel.isSelectedIndex(selected), false, false, true, true);
				String tt = theRenderStrategy.getTooltip(cell);
				if (tt != null)
					return tt;
				renderer = (ObservableCellRenderer<E, E>) theRenderStrategy.getRenderer();
			} else if (selected == theModel.getSize() && theRenderStrategy.getAddRow() != null) {
				row = theRenderStrategy.getAddRow().getEditSeedRow().get();
				cell = new ModelCell.Default<>(LambdaUtils.constantSupplier(row, () -> String.valueOf(row), row), row, selected, 0, //
					theSelectionModel.isSelectedIndex(selected), theSelectionModel.isSelectedIndex(selected), false, false, true, true);
				renderer = (ObservableCellRenderer<E, E>) theRenderStrategy.getAddRow().getRenderer();
			} else
				throw new IndexOutOfBoundsException(selected + " of " + getComponentCount());

			Component rendered = renderer.getCellRendererComponent(LittleList.this, cell, CellRenderContext.DEFAULT);
			return rendered instanceof JComponent ? ((JComponent) rendered).getToolTipText() : null;
		}

		String getActionTooltip(int itemIndex, ItemAction<? super E> action) {
			if (itemIndex < theModel.getSize()) {
				E row = theModel.getElementAt(itemIndex);
				return theHolder.getActionTooltip(row, itemIndex, action);
			} else
				return null;
		}

		boolean performAction(ItemAction<? super E> action, int itemIndex, Object cause) {
			if (itemIndex < theModel.getSize()) {
				E item = theModel.getElementAt(itemIndex);
				return theHolder.performAction(item, itemIndex, action, cause);
			} else
				return false;
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
				if (!getBounds().equals(theBounds.holderBounds)) {
					super.setBounds(theBounds.holderBounds.x, theBounds.holderBounds.y, theBounds.holderBounds.width,
						theBounds.holderBounds.height);
				}
				theComponent.internalSetBounds(theBounds.itemBounds);
			}
			if (renderIndex < theModel.getSize()) {
				CollectionUtils.synchronize(theItemActionLabels, theItemActions, (i1, i2) -> true)
				.adjust(new CollectionUtils.CollectionSynchronizer<JLabel, ItemAction<? super E>>() {
					@Override
					public boolean getOrder(ElementSyncInput<JLabel, ItemAction<? super E>> element) {
						return true;
					}

					@Override
					public ElementSyncAction leftOnly(ElementSyncInput<JLabel, ItemAction<? super E>> element) {
						((SyntheticContainer) getParent()).removeAction(element.getUpdatedLeftIndex());
						remove(element.getUpdatedLeftIndex());
						return element.remove();
					}

					@Override
					public ElementSyncAction rightOnly(ElementSyncInput<JLabel, ItemAction<? super E>> element) {
						JLabel label = new JLabel();
						((SyntheticContainer) getParent()).addAction(element.getTargetIndex());
						syncActionLabel(label, element.getRightValue(), renderIndex, element.getTargetIndex());
						add(label, element.getTargetIndex() + 1);
						return element.useValue(label);
					}

					@Override
					public ElementSyncAction common(ElementSyncInput<JLabel, ItemAction<? super E>> element) {
						syncActionLabel(element.getLeftValue(), element.getRightValue(), renderIndex, element.getUpdatedLeftIndex());
						return element.preserve();
					}
				}, AdjustmentOrder.RightOrder);
			} else {
				for (JLabel actionLabel : theItemActionLabels)
					actionLabel.setVisible(false);
			}
			invalidate();
			return this;
		}

		void syncActionLabel(JLabel actionLabel, ItemAction<? super E> action, int modelIndex, int actionIndex) {
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
					new FontAdjuster().configure(((Consumer<? super FontAdjuster>) font)).adjust(actionLabel);
				} catch (ClassCastException e) {}
			} else if (font instanceof Font) {
				if (!font.equals(actionLabel.getFont()))
					actionLabel.setFont((Font) font);
			}
			if (theAction.getValue("decorator") instanceof ComponentDecorator)
				((ComponentDecorator) theAction.getValue("decorator")).decorate(actionLabel);
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
			boolean layout = x != getX() || y != getY() || width != getWidth() || height != getHeight();
			boolean oldRecurse = theSyntheticContainer.isRecursive;
			theSyntheticContainer.isRecursive = true;
			try {
				super.setBounds(x, y, width, height);
			} finally {
				theSyntheticContainer.isRecursive = oldRecurse;
			}
			theBounds.holderBounds.setBounds(x, y, width, height);
			if (layout) {
				getLayout().layoutContainer(this);
				for (int i = 0; i < theItemActionLabels.size(); i++)
					theItemActionLabels.get(i).getBounds(theBounds.actionBounds.get(i));
				theBounds.itemBounds.setBounds(theComponent.getBounds());
			}
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
			boolean oldRecurse = theSyntheticContainer.isRecursive;
			theSyntheticContainer.isRecursive = true;
			try {
				super.setBounds(x, y, width, height);
			} finally {
				theSyntheticContainer.isRecursive = oldRecurse;
			}
			if (theRendered != null) {
				if (theRendered.getParent() == LittleList.this) {
					// The editor component belongs to the root, not this holder, so apply the offset
					x += getParent().getX();
					y += getParent().getY();
				}
				theRendered.setBounds(x, y, width, height);
			}
		}

		@Override
		protected void paintChildren(Graphics g) {
			super.paintChildren(g);
			if (theRendered != null)
				theRendered.paint(g);
		}
	}
}
