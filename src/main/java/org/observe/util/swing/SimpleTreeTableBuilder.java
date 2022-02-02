package org.observe.util.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.BoundedRangeModel;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.jdesktop.swingx.JXTreeTable;
import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.PanelPopulation.AbstractComponentEditor;
import org.observe.util.swing.PanelPopulation.DataAction;
import org.observe.util.swing.PanelPopulation.SimpleButtonEditor;
import org.observe.util.swing.PanelPopulation.SimpleDataAction;
import org.observe.util.swing.PanelPopulation.TreeTableEditor;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * Implements {@link PanelPopulation}'s tree table.
 *
 * This class is just a bit of a kludge, but I can't think how to make it better.
 *
 * JXTreeTable does a pretty good job of combining the JTree and JTable APIs, but since JTree and JTable are both classes and java doesn't
 * support multiple inheritance, there's just only so much that can be done. JXTreeTable inherits JTable, but you can't use a normal
 * TableModel with it, so the usage there is different. And JXTreeTable isn't related to JTree at all--though it implements methods with the
 * same signatures, classes that use JTree can't use JXTreeTable, so all the code has to be duplicated and I can't see any way around this.
 * Maybe someone can some day.
 */
class SimpleTreeTableBuilder<F, P extends SimpleTreeTableBuilder<F, P>> extends AbstractComponentEditor<JXTreeTable, P>
implements TreeTableEditor<F, P> {
	private final ObservableValue<F> theRoot;
	private final Function<? super F, ? extends ObservableCollection<? extends F>> theChildren;
	private Predicate<? super F> theLeafTest;

	private String theItemName;

	private SettableValue<BetterList<F>> theSingleSelection;
	private boolean isSingleSelection;
	private ObservableCollection<BetterList<F>> theMultiSelection;
	private List<SimpleDataAction<BetterList<F>, ?>> theActions;

	private Function<? super F, String> theNameFunction;
	private CategoryRenderStrategy<? super F, F> theTreeColumn;
	private ObservableCollection<? extends CategoryRenderStrategy<? super F, ?>> theColumns;
	private List<ObservableTreeModel.PathMouseListener<? super F>> theMouseListeners;
	private int theAdaptiveMinRowHeight;
	private int theAdaptivePrefRowHeight;
	private int theAdaptiveMaxRowHeight;
	private boolean isScrollable;
	private boolean isPromisedSafe;

	private Component theBuiltComponent;

	public SimpleTreeTableBuilder(Supplier<Transactable> lock, ObservableValue<F> root,
		Function<? super F, ? extends ObservableCollection<? extends F>> children) {
		super(new JXTreeTable(), lock);
		theRoot = root;
		theChildren = children;
		theLeafTest = __ -> false;
		theActions = new ArrayList<>();
	}

	class PPTreeModel extends ObservableTreeModel<F> {
		PPTreeModel() {
			super(theRoot);
		}

		@Override
		protected ObservableCollection<? extends F> getChildren(F parent) {
			return theChildren.apply(parent);
		}

		@Override
		public void valueForPathChanged(TreePath path, Object newValue) {
		}

		@Override
		public boolean isLeaf(Object node) {
			Predicate<? super F> leafTest = theLeafTest;
			return leafTest != null && leafTest.test((F) node);
		}
	}

	@Override
	public List<BetterList<F>> getSelection() {
		TreePath[] selection = getEditor().getTreeSelectionModel().getSelectionPaths();
		return BetterList.of(Arrays.stream(selection)//
			.map(path -> (BetterList<F>) BetterList.of(path.getPath())));
	}

	@Override
	public P withRemove(Consumer<? super List<? extends BetterList<F>>> deletion, Consumer<DataAction<BetterList<F>, ?>> actionMod) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public P withMultiAction(Consumer<? super List<? extends BetterList<F>>> action, Consumer<DataAction<BetterList<F>, ?>> actionMod) {
		SimpleDataAction<BetterList<F>, ?> ta = new SimpleDataAction<>(this, action, this::getSelection);
		actionMod.accept(ta);
		theActions.add(ta);
		return (P) this;
	}

	@Override
	public P withItemName(String itemName) {
		theItemName = itemName;
		return (P) this;
	}

	@Override
	public String getItemName() {
		if (theItemName == null)
			return "item";
		else
			return theItemName;
	}

	@Override
	public ObservableValue<? extends F> getRoot() {
		return theRoot;
	}

	@Override
	public P withSelection(SettableValue<BetterList<F>> selection, boolean enforceSingleSelection) {
		theSingleSelection = selection;
		isSingleSelection = enforceSingleSelection;
		return (P) this;
	}

	@Override
	public P withSelection(ObservableCollection<BetterList<F>> selection) {
		theMultiSelection = selection;
		return (P) this;
	}

	@Override
	public P withLeafTest(Predicate<? super F> leafTest) {
		theLeafTest = leafTest;
		return (P) this;
	}

	@Override
	public boolean isVisible(List<? extends F> path) {
		return getEditor().isVisible(new TreePath(path.toArray()));
	}

	@Override
	public boolean isExpanded(List<? extends F> path) {
		return getEditor().isExpanded(new TreePath(path.toArray()));
	}

	@Override
	public ObservableValue<String> getTooltip() {
		return null;
	}

	@Override
	protected Component createFieldNameLabel(Observable<?> until) {
		return null;
	}

	@Override
	protected Component createPostLabel(Observable<?> until) {
		return null;
	}

	Function<? super F, String> getNameFunction() {
		return theNameFunction;
	}

	@Override
	public P withColumns(ObservableCollection<? extends CategoryRenderStrategy<? super F, ?>> columns) {
		theColumns = columns;
		return (P) this;
	}

	@Override
	public P withColumn(CategoryRenderStrategy<? super F, ?> column) {
		if (theColumns == null)
			theColumns = ObservableCollection.create(new TypeToken<CategoryRenderStrategy<? super F, ?>>() {
			}.where(new TypeParameter<F>() {
			}, theRoot.getType()));
		((ObservableCollection<CategoryRenderStrategy<? super F, ?>>) theColumns).add(column);
		return (P) this;
	}

	@Override
	public CategoryRenderStrategy<? super F, F> getTreeColumn() {
		return theTreeColumn;
	}

	@Override
	public P withTreeColumn(CategoryRenderStrategy<? super F, F> column) {
		theTreeColumn = column;
		return (P) this;
	}

	@Override
	public P withAdaptiveHeight(int minRows, int prefRows, int maxRows) {
		if (minRows < 0 || minRows > prefRows || prefRows > maxRows)
			throw new IllegalArgumentException("Required: 0<=min<=pref<=max: " + minRows + ", " + prefRows + ", " + maxRows);
		theAdaptiveMinRowHeight = minRows;
		theAdaptivePrefRowHeight = prefRows;
		theAdaptiveMaxRowHeight = maxRows;
		return (P) this;
	}

	@Override
	public P withMouseListener(ObservableTreeModel.PathMouseListener<? super F> listener) {
		if (theMouseListeners == null)
			theMouseListeners = new ArrayList<>();
		theMouseListeners.add(listener);
		return (P) this;
	}

	@Override
	public P scrollable(boolean scrollable) {
		isScrollable = scrollable;
		return (P) this;
	}

	@Override
	public P promiseSafe() {
		isPromisedSafe = true;
		return (P) this;
	}

	@Override
	public Component getOrCreateComponent(Observable<?> until) {
		if (theBuiltComponent != null)
			return theBuiltComponent;
		if (theColumns == null)
			throw new IllegalStateException("No columns configured");
		TypeToken<CategoryRenderStrategy<? super F, ?>> columnType = TypeTokens.get().keyFor(CategoryRenderStrategy.class).parameterized(//
			TypeTokens.get().getSuperWildcard(theRoot.getType()), //
			TypeTokens.get().WILDCARD);
		if (theTreeColumn == null)
			theTreeColumn = new CategoryRenderStrategy<>("Tree", theRoot.getType(), f -> f);
		ObservableCollection<? extends CategoryRenderStrategy<? super F, ?>> columns = theColumns;
		if (!isPromisedSafe)
			columns = ObservableSwingUtils.safe(columns, until);
		columns = ObservableCollection.flattenCollections(columnType, //
			ObservableCollection.of(columnType, theTreeColumn), //
			columns).collect();
		ObservableTreeTableModel<F> model = new ObservableTreeTableModel<>(new PPTreeModel(), columns, false);
		if (isPromisedSafe)
			model.getTreeModel().promiseSafe();
		JXTreeTable table = getEditor();
		table.setTreeTableModel(model);
		if (theMouseListeners != null) {
			for (ObservableTreeModel.PathMouseListener<? super F> listener : theMouseListeners)
				model.addMouseListener(listener);
		}
		Subscription sub = ObservableTreeTableModel.hookUp(table, model);
		if (until != null)
			until.take(1).act(__ -> sub.unsubscribe());

		JScrollPane scroll = new JScrollPane(table);
		if (isScrollable) {
			// Default scroll increments are ridiculously small
			scroll.getVerticalScrollBar().setUnitIncrement(10);
			scroll.getHorizontalScrollBar().setUnitIncrement(10);
			if (theAdaptivePrefRowHeight > 0) {
				class HeightAdjustmentListener implements TableModelListener, ChangeListener {
					private boolean isHSBVisible;
					private boolean isVSBVisible;

					@Override
					public void tableChanged(TableModelEvent e) {
						switch (e.getType()) {
						case TableModelEvent.DELETE:
							if (e.getFirstRow() < theAdaptiveMaxRowHeight)
								adjustHeight();
							break;
						case TableModelEvent.INSERT:
							if (e.getFirstRow() < theAdaptiveMaxRowHeight)
								adjustHeight();
							break;
						default:
							if (e.getFirstRow() < theAdaptiveMaxRowHeight)
								adjustHeight();
							break;
						}
					}

					@Override
					public void stateChanged(ChangeEvent e) {
						BoundedRangeModel hbm = scroll.getHorizontalScrollBar().getModel();
						if (hbm.getValueIsAdjusting())
							return;
						if (isHSBVisible != (hbm.getExtent() > hbm.getMaximum())) {
							adjustHeight();
						} else {
							BoundedRangeModel vbm = scroll.getVerticalScrollBar().getModel();
							if (vbm.getValueIsAdjusting())
								return;
							if (isVSBVisible != (vbm.getExtent() > vbm.getMaximum()))
								adjustHeight();
						}
					}

					void adjustHeight() {
						int minHeight = 0, prefHeight = 0, maxHeight = 0;
						if (table.getTableHeader() != null && table.getTableHeader().isVisible()) {
							minHeight += table.getTableHeader().getPreferredSize().height;
							maxHeight += table.getTableHeader().getPreferredSize().height;
						}
						int rowCount = table.getRowCount();
						for (int i = 0; i < theAdaptiveMaxRowHeight && i < rowCount; i++) {
							int rowHeight = table.getRowHeight(i);
							if (i < theAdaptiveMinRowHeight)
								minHeight += rowHeight;
							if (i < theAdaptivePrefRowHeight)
								prefHeight += rowHeight;
							if (i < theAdaptiveMaxRowHeight)
								maxHeight += rowHeight;
						}
						BoundedRangeModel hbm = scroll.getHorizontalScrollBar().getModel();
						isHSBVisible = hbm.getExtent() > hbm.getMaximum();
						if (isHSBVisible) {
							int sbh = scroll.getHorizontalScrollBar().getHeight();
							minHeight += sbh;
							maxHeight += sbh;
						}
						BoundedRangeModel vbm = scroll.getVerticalScrollBar().getModel();
						isVSBVisible = vbm.getExtent() > vbm.getMaximum();
						Dimension psvs = table.getPreferredScrollableViewportSize();
						if (psvs.height != prefHeight) {
							// int w = 0;
							// for (int c = 0; c < table.getColumnModel().getColumnCount(); c++)
							// w += table.getColumnModel().getColumn(c).getWidth();
							table.setPreferredScrollableViewportSize(new Dimension(psvs.width, prefHeight));
						}
						Dimension min = scroll.getMinimumSize();
						if (min.height != minHeight) {
							int w = 10;
							if (isVSBVisible)
								w += scroll.getVerticalScrollBar().getWidth();
							scroll.getViewport().setMinimumSize(new Dimension(w, minHeight));
						}
						Dimension max = scroll.getMaximumSize();
						if (max.height != maxHeight) {
							int w = 0;
							if (isVSBVisible)
								w += scroll.getVerticalScrollBar().getWidth();
							for (int c = 0; c < model.getColumnCount(); c++) {
								w += table.getColumnModel().getColumn(c).getMaxWidth();
								if (w < 0) {
									w = Integer.MAX_VALUE;
									break;
								}
							}
							scroll.getViewport().setMaximumSize(new Dimension(w, maxHeight));
						}
						if (scroll.getParent() != null)
							scroll.getParent().revalidate();
					}
				}
				HeightAdjustmentListener hal = new HeightAdjustmentListener();
				table.getModel().addTableModelListener(hal);
				scroll.getHorizontalScrollBar().getModel().addChangeListener(hal);
				scroll.getVerticalScrollBar().getModel().addChangeListener(hal);
				hal.adjustHeight();
			}
		} else {
			scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		}

		// Selection
		if (theMultiSelection != null)
			ObservableTreeTableModel.syncSelection(getEditor(), theMultiSelection, until);
		if (theSingleSelection != null) {
			ObservableTreeTableModel.syncSelection(getEditor(), theSingleSelection, false, Equivalence.DEFAULT, until);
			if (isSingleSelection)
				getEditor().getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		}

		if (!theActions.isEmpty()) {
			JPopupMenu popup = new JPopupMenu();
			SimpleDataAction<BetterList<F>, ?>[] actions = theActions.toArray(new SimpleDataAction[theActions.size()]);
			JMenuItem[] actionMenuItems = new JMenuItem[actions.length];
			for (int a = 0; a < actions.length; a++) {
				actionMenuItems[a] = new JMenuItem();
				SimpleDataAction<BetterList<F>, ?> action = actions[a];
				if (action.theButtonMod != null) {
					SimpleButtonEditor<?, ?> buttonEditor = new SimpleButtonEditor<>(null, actionMenuItems[a], null,
						action.theObservableAction, getLock(), false);
					action.theButtonMod.accept(buttonEditor);
					buttonEditor.getOrCreateComponent(until);
				}
			}
			getEditor().getTreeSelectionModel().addTreeSelectionListener(evt -> {
				List<BetterList<F>> selection = getSelection();
				for (SimpleDataAction<BetterList<F>, ?> action : actions)
					action.updateSelection(selection, evt);
			});
			getEditor().addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent evt) {
					if (!SwingUtilities.isRightMouseButton(evt))
						return;
					popup.removeAll();
					for (int a = 0; a < actions.length; a++) {
						if (actions[a].isEnabled() == null)
							popup.add(actionMenuItems[a]);
					}
					if (popup.getComponentCount() > 0)
						popup.show(getEditor(), evt.getX(), evt.getY());
				}
			});
		}
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

		// Set up transfer handling (DnD, copy/paste)
		boolean draggable = theTreeColumn.getDragSource() != null || theTreeColumn.getMutator().getDragAccepter() != null;
		for (CategoryRenderStrategy<? super F, ?> column : theColumns) {
			// TODO check the draggable flag
			if (column.getDragSource() != null || column.getMutator().getDragAccepter() != null) {
				draggable = true;
				break;
			}
		}
		if (draggable) {
			table.setDragEnabled(true);
			table.setDropMode(DropMode.INSERT_ROWS);
			TreeTableBuilderTransferHandler handler = new TreeTableBuilderTransferHandler(table);
			table.setTransferHandler(handler);
		}

		theBuiltComponent = new JScrollPane(decorate(getEditor()));
		return decorate(theBuiltComponent);
	}

	@Override
	public Component getComponent() {
		return theBuiltComponent;
	}

	class TreeTableBuilderTransferHandler extends TransferHandler {
		private final JXTreeTable theTable;

		TreeTableBuilderTransferHandler(JXTreeTable table) {
			theTable = table;
		}

		@Override
		protected Transferable createTransferable(JComponent c) {
			try (Transaction colT = theColumns.lock(false, null)) {
				if (theTable.getSelectedRowCount() == 0)
					return null;
				List<F> selectedRows = new ArrayList<>(theTable.getSelectedRowCount());
				for (int i = theTable.getSelectionModel().getMinSelectionIndex(); i <= theTable.getSelectionModel()
					.getMaxSelectionIndex(); i++) {
					if (theTable.getSelectionModel().isSelectedIndex(i))
						selectedRows.add((F) theTable.getPathForRow(i).getLastPathComponent());
				}
				int columnIndex = theTable.getSelectedColumn();
				if (columnIndex >= 0)
					columnIndex = theTable.convertColumnIndexToModel(columnIndex);
				CategoryRenderStrategy<? super F, ?> column = columnIndex >= 0 ? theColumns.get(columnIndex) : null;
				Transferable columnTransfer = null;
				if (column != null && column.getDragSource() != null) {
					Transferable[] columnTs = new Transferable[selectedRows.size()];
					for (int r = 0; r < selectedRows.size(); r++)
						columnTs[r] = ((Dragging.TransferSource<Object>) column.getDragSource())
						.createTransferable(column.getCategoryValue(selectedRows.get(r)));
					columnTransfer = columnTs.length == 1 ? columnTs[0] : new Dragging.AndTransferable(columnTs);
				}
				return columnTransfer;
			}
		}

		@Override
		public int getSourceActions(JComponent c) {
			int actions = 0;
			try (Transaction colT = theColumns.lock(false, null)) {
				if (theTable.getSelectedRowCount() == 0)
					return actions;
				int columnIndex = theTable.getSelectedColumn();
				if (columnIndex >= 0)
					columnIndex = theTable.convertColumnIndexToModel(columnIndex);
				CategoryRenderStrategy<? super F, ?> column = columnIndex >= 0 ? theColumns.get(columnIndex) : null;
				if (column != null && column.getDragSource() != null) {
					actions |= column.getDragSource().getSourceActions();
				}
			}
			return actions;
		}

		@Override
		public Icon getVisualRepresentation(Transferable t) {
			// TODO Auto-generated method stub
			return super.getVisualRepresentation(t);
		}

		@Override
		protected void exportDone(JComponent source, Transferable data, int action) {
			// TODO If removed, scroll
			super.exportDone(source, data, action);
		}

		@Override
		public boolean canImport(TransferSupport support) {
			try (Transaction colT = theColumns.lock(false, null)) {
				int rowIndex;
				if (support.isDrop()) {
					rowIndex = theTable.rowAtPoint(support.getDropLocation().getDropPoint());
					if (rowIndex < 0)
						return false;
				} else
					rowIndex = theTable.getSelectedRow();
				TreePath path = rowIndex < 0 ? new TreePath(new Object[] { theRoot.get() }) : theTable.getPathForRow(rowIndex);
				F target = (F) path.getLastPathComponent();
				F parent;
				ObservableCollection<? extends F> children;
				ElementId targetRow;
				if (path.getPathCount() == 1) {
					parent = null;
					children = null;
					targetRow = null;
				} else {
					parent = (F) path.getPathComponent(path.getPathCount() - 2);
					children = theChildren.apply(parent);
					int parentRow = theTable.getRowForPath(path.getParentPath());
					try {
						targetRow = children.getElement(rowIndex - parentRow - 1).getElementId();
					} catch (IndexOutOfBoundsException e) {
						return false; // Out-of-sync
					}
					if (children.getElement(targetRow).get() != target)
						return false; // Out-of-sync
				}
				if (rowIndex >= 0) {
					int columnIndex = support.isDrop() ? theTable.columnAtPoint(support.getDropLocation().getDropPoint())
						: theTable.getSelectedColumn();
					if (columnIndex >= 0)
						columnIndex = theTable.convertColumnIndexToModel(columnIndex);
					CategoryRenderStrategy<? super F, ?> column;
					if (columnIndex < 0)
						return false;
					else if (columnIndex == 0)
						column = theTreeColumn;
					else
						column = theColumns.get(columnIndex - 1);
					if (canImport(support, children, targetRow, rowIndex, column, false))
						return true;
				}
			} catch (RuntimeException | Error e) {
				e.printStackTrace();
				throw e;
			}
			return false;
		}

		private <C> boolean canImport(TransferSupport support, ObservableCollection<? extends F> children, ElementId childEl, int rowIndex,
			CategoryRenderStrategy<? super F, C> column, boolean doImport) {
			if (column.getMutator().getDragAccepter() == null)
				return false;
			if (children != null) {
				CollectionElement<? extends F> rowEl = children.getElement(childEl);
				C oldValue = column.getCategoryValue(rowEl.get());
				if (!column.getMutator().isEditable(rowEl.get(), oldValue))
					return false;
				boolean selected = theTable.isRowSelected(rowIndex);
				ModelCell<F, C> cell = new ModelCell.Default<>(rowEl::get, oldValue, rowIndex, theColumns.indexOf(column), selected,
					selected, theTable.isExpanded(rowIndex), theLeafTest.test(rowEl.get()));
				if (!column.getMutator().getDragAccepter().canAccept(cell, support, false))
					return false;
				BetterList<C> newColValue;
				try {
					newColValue = column.getMutator().getDragAccepter().accept(cell, support.getTransferable(), false, !doImport);
				} catch (IOException e) {
					return false;
				} catch (InvalidDnDOperationException e) {
					if ("No drop current".equals(e.getMessage())) {
						/* Found this from some dude on coderanch.com, specifically at
						 * https://coderanch.com/t/664525/java/Invalid-Drag-Drop-Exception
						 *
						 * Basically, under Windows, this call is invoked when the user releases the mouse button, but since the mouse
						 * button is not pressed, the underlying mechanism assumes there's no DnD operation going on,
						 * so when Transferable.getTransferData() is called, it throws an exception.
						 *
						 * This method is also called whenever the user moves their mouse, so the only way this could fail
						 * is if the underlying widgets or data have changed.  This is, of course, possible,
						 * so it makes sense to do the check.  However, if Windows throws this exception, meaning we can't do the check,
						 * it's most probable that things are ok and we should be ok to do the drop.
						 */
						return true;
					}
					throw e;
				}
				if (newColValue == null || ((CategoryRenderStrategy<F, C>) column).getMutator()//
					.isAcceptable(//
						(MutableCollectionElement<F>) children.mutableElement(rowEl.getElementId()), newColValue.getFirst()) != null)
					return false;
				if (doImport) {
					((CategoryRenderStrategy<F, C>) column).getMutator()//
					.mutate(//
						children.mutableElement(rowEl.getElementId()), newColValue.getFirst());
				}
				return true;
			} else {
				F root = theRoot.get();
				C oldValue = column.getCategoryValue(root);
				if (!column.getMutator().isEditable(root, oldValue))
					return false;
				boolean selected = theTable.isRowSelected(rowIndex);
				ModelCell<F, C> cell = new ModelCell.Default<>(theRoot, oldValue, rowIndex, theColumns.indexOf(column), selected, selected,
					theTable.isExpanded(rowIndex), theLeafTest.test(root));
				if (!column.getMutator().getDragAccepter().canAccept(cell, support, false))
					return false;
				BetterList<C> newColValue;
				try {
					newColValue = column.getMutator().getDragAccepter().accept(cell, support.getTransferable(), false, !doImport);
				} catch (IOException e) {
					return false;
				}
				MutableCollectionElement<F> syntheticEl = new MutableCollectionElement<F>() {
					@Override
					public ElementId getElementId() {
						throw new IllegalStateException();
					}

					@Override
					public F get() {
						return theRoot.get();
					}

					@Override
					public BetterCollection<F> getCollection() {
						throw new IllegalStateException();
					}

					@Override
					public String isEnabled() {
						if (theRoot instanceof SettableValue)
							return ((SettableValue<F>) theRoot).isEnabled().get();
						else
							return StdMsg.UNSUPPORTED_OPERATION;
					}

					@Override
					public String isAcceptable(F value) {
						if (theRoot instanceof SettableValue)
							return ((SettableValue<F>) theRoot).isAcceptable(value);
						else
							return StdMsg.UNSUPPORTED_OPERATION;
					}

					@Override
					public void set(F value) throws UnsupportedOperationException, IllegalArgumentException {
						if (theRoot instanceof SettableValue)
							((SettableValue<F>) theRoot).set(value, null);
						else
							throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
					}

					@Override
					public String canRemove() {
						return StdMsg.UNSUPPORTED_OPERATION;
					}

					@Override
					public void remove() throws UnsupportedOperationException {
						throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
					}
				};
				if (newColValue == null || ((CategoryRenderStrategy<F, C>) column).getMutator()//
					.isAcceptable(//
						syntheticEl, newColValue.getFirst()) != null)
					return false;
				if (doImport) {
					((CategoryRenderStrategy<F, C>) column).getMutator()//
					.mutate(//
						syntheticEl, newColValue.getFirst());
				}
				return true;
			}
		}

		@Override
		public boolean importData(TransferSupport support) {
			try (Transaction rowT = getLock().get().lock(true, support); Transaction colT = theColumns.lock(false, null)) {
				int rowIndex;
				if (support.isDrop()) {
					rowIndex = theTable.rowAtPoint(support.getDropLocation().getDropPoint());
					if (rowIndex < 0)
						return false;
				} else
					rowIndex = theTable.getSelectedRow();
				TreePath path = rowIndex < 0 ? new TreePath(new Object[] { theRoot.get() }) : theTable.getPathForRow(rowIndex);
				F target = (F) path.getLastPathComponent();
				F parent;
				ObservableCollection<? extends F> children;
				ElementId targetRow;
				if (path.getPathCount() == 1) {
					parent = null;
					children = null;
					targetRow = null;
				} else {
					parent = (F) path.getPathComponent(path.getPathCount() - 2);
					children = theChildren.apply(parent);
					int parentRow = theTable.getRowForPath(path.getParentPath());
					try {
						targetRow = children.getElement(rowIndex - parentRow - 1).getElementId();
					} catch (IndexOutOfBoundsException e) {
						return false; // Out-of-sync
					}
					if (children.getElement(targetRow).get() != target)
						return false; // Out-of-sync
				}
				if (rowIndex >= 0) {
					int columnIndex = support.isDrop() ? theTable.columnAtPoint(support.getDropLocation().getDropPoint())
						: theTable.getSelectedColumn();
					if (columnIndex >= 0)
						columnIndex = theTable.convertColumnIndexToModel(columnIndex);
					CategoryRenderStrategy<? super F, ?> column;
					if (columnIndex < 0)
						return false;
					else if (columnIndex == 0)
						column = theTreeColumn;
					else
						column = theColumns.get(columnIndex - 1);
					if (canImport(support, children, targetRow, rowIndex, column, true)) {
						return true;
					}
				}
			} catch (RuntimeException | Error e) {
				e.printStackTrace();
				throw e;
			}
			return false;
		}
	}
}
