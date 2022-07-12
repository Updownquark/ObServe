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
import org.observe.util.swing.PanelPopulation.DataAction;
import org.observe.util.swing.PanelPopulation.TreeTableEditor;
import org.observe.util.swing.PanelPopulationImpl.AbstractComponentEditor;
import org.observe.util.swing.PanelPopulationImpl.SimpleButtonEditor;
import org.observe.util.swing.PanelPopulationImpl.SimpleDataAction;
import org.qommons.LambdaUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.tree.BetterTreeList;

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
	public static <F> SimpleTreeTableBuilder<F, ?> createTreeTable(ObservableValue<F> root,
		Function<? super F, ? extends ObservableCollection<? extends F>> children, Observable<?> until) {
		return new SimpleTreeTableBuilder<>(root, children, null, until);
	}

	public static <F> SimpleTreeTableBuilder<F, ?> createTreeTable2(ObservableValue<F> root,
		Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children, Observable<?> until) {
		return new SimpleTreeTableBuilder<>(root, null, children, until);
	}

	private final ObservableValue<F> theRoot;
	private final Function<? super F, ? extends ObservableCollection<? extends F>> theChildren1;
	private final Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> theChildren2;
	private Predicate<? super F> theLeafTest;

	private String theItemName;

	private SettableValue<F> theValueSingleSelection;
	private SettableValue<BetterList<F>> thePathSingleSelection;
	private boolean isSingleSelection;
	private ObservableCollection<F> theValueMultiSelection;
	private ObservableCollection<BetterList<F>> thePathMultiSelection;
	private List<SimpleDataAction<BetterList<F>, ?>> theActions;

	private Function<? super F, String> theNameFunction;
	private CategoryRenderStrategy<BetterList<F>, F> theTreeColumn;
	private ObservableCollection<? extends CategoryRenderStrategy<BetterList<F>, ?>> theColumns;
	private List<ObservableTableModel.RowMouseListener<? super BetterList<F>>> theMouseListeners;
	private int theAdaptiveMinRowHeight;
	private int theAdaptivePrefRowHeight;
	private int theAdaptiveMaxRowHeight;
	private boolean isScrollable;

	private SimpleTreeTableBuilder(ObservableValue<F> root,
		Function<? super F, ? extends ObservableCollection<? extends F>> children1,
			Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children2, Observable<?> until) {
		super(new JXTreeTable(), until);
		theRoot = root;
		theChildren1 = children1;
		theChildren2 = children2;
		theLeafTest = __ -> false;
		theActions = new ArrayList<>();
		theTreeColumn = new CategoryRenderStrategy<>("Tree", root.getType(),
			LambdaUtils.printableFn(BetterList::getLast, "BetterList::getLast", null));
	}

	class PPTreeModel extends ObservableTreeModel<F> {
		PPTreeModel() {
			super(theRoot);
		}

		@Override
		protected ObservableCollection<? extends F> getChildren(F parent) {
			if (theChildren1 != null)
				return theChildren1.apply(parent);
			return theChildren2.apply(getBetterPath(parent, false));
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
	public P withMultiAction(String actionName, Consumer<? super List<? extends BetterList<F>>> action,
		Consumer<DataAction<BetterList<F>, ?>> actionMod) {
		SimpleDataAction<BetterList<F>, ?> ta = new SimpleDataAction<>(actionName, this, action, this::getSelection);
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
		thePathSingleSelection = selection;
		isSingleSelection = enforceSingleSelection;
		return (P) this;
	}

	@Override
	public P withSelection(ObservableCollection<BetterList<F>> selection) {
		thePathMultiSelection = selection;
		return (P) this;
	}

	@Override
	public P withValueSelection(SettableValue<F> selection, boolean enforceSingleSelection) {
		theValueSingleSelection = selection;
		isSingleSelection = enforceSingleSelection;
		return (P) this;
	}

	@Override
	public P withValueSelection(ObservableCollection<F> selection) {
		theValueMultiSelection = selection;
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
	public P withColumns(ObservableCollection<? extends CategoryRenderStrategy<BetterList<F>, ?>> columns) {
		theColumns = columns;
		return (P) this;
	}

	@Override
	public P withColumn(CategoryRenderStrategy<BetterList<F>, ?> column) {
		if (theColumns == null)
			theColumns = ObservableCollection.create(new TypeToken<CategoryRenderStrategy<BetterList<F>, ?>>() {
			}.where(new TypeParameter<F>() {
			}, theRoot.getType()));
		((ObservableCollection<CategoryRenderStrategy<BetterList<F>, ?>>) theColumns).add(column);
		return (P) this;
	}

	@Override
	public CategoryRenderStrategy<BetterList<F>, F> getRender() {
		return theTreeColumn;
	}

	@Override
	public P withRender(CategoryRenderStrategy<BetterList<F>, F> render) {
		theTreeColumn = render;
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
	public P scrollable(boolean scrollable) {
		isScrollable = scrollable;
		return (P) this;
	}

	@Override
	protected Component createComponent() {
		if (theColumns == null)
			throw new IllegalStateException("No columns configured");
		TypeToken<CategoryRenderStrategy<BetterList<F>, ?>> columnType = TypeTokens.get().keyFor(CategoryRenderStrategy.class)
			.parameterized(//
				TypeTokens.get().keyFor(BetterList.class).parameterized(theRoot.getType()), //
				TypeTokens.get().WILDCARD);
		if (theTreeColumn == null)
			theTreeColumn = new CategoryRenderStrategy<>("Tree", theRoot.getType(), f -> f.getLast());
		ObservableCollection<? extends CategoryRenderStrategy<BetterList<F>, ?>> columns = theColumns;
		columns = columns.safe(ThreadConstraint.EDT, getUntil());
		columns = ObservableCollection.flattenCollections(columnType, //
			ObservableCollection.of(columnType, theTreeColumn), //
			columns).collect();
		ObservableTreeTableModel<F> model = new ObservableTreeTableModel<>(new PPTreeModel(), columns);
		JXTreeTable table = getEditor();
		table.setTreeTableModel(model);
		if (theMouseListeners != null) {
			for (ObservableTableModel.RowMouseListener<? super BetterList<F>> listener : theMouseListeners)
				model.addMouseListener(listener);
		}
		Subscription sub = ObservableTreeTableModel.hookUp(table, model);
		getUntil().take(1).act(__ -> sub.unsubscribe());

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
		if (thePathMultiSelection != null)
			ObservableTreeTableModel.syncSelection(getEditor(), thePathMultiSelection, getUntil());
		if (theValueMultiSelection != null)
			ObservableTreeTableModel.syncSelection(
				getEditor(), theValueSingleSelection.safe(ThreadConstraint.EDT, getUntil()).transformReversible(//
					TypeTokens.get().keyFor(BetterList.class).<BetterList<F>> parameterized(getRoot().getType()), tx -> tx
					.map(v -> model.getTreeModel().getBetterPath(v, true)).withReverse(path -> path == null ? null : path.getLast())),
				false, Equivalence.DEFAULT, getUntil());
		if (thePathSingleSelection != null)
			ObservableTreeTableModel.syncSelection(getEditor(), thePathSingleSelection, false, Equivalence.DEFAULT, getUntil());
		if (theValueSingleSelection != null)
			ObservableTreeTableModel.syncSelection(getEditor(), theValueSingleSelection.transformReversible(//
				TypeTokens.get().keyFor(BetterList.class).<BetterList<F>> parameterized(getRoot().getType()),
				tx -> tx.map(v -> model.getTreeModel().getBetterPath(v, true)).withReverse(path -> path == null ? null : path.getLast())),
				false, Equivalence.DEFAULT, getUntil());
		if (isSingleSelection)
			getEditor().getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

		if (!theActions.isEmpty()) {
			JPopupMenu popup = new JPopupMenu();
			SimpleDataAction<BetterList<F>, ?>[] actions = theActions.toArray(new SimpleDataAction[theActions.size()]);
			JMenuItem[] actionMenuItems = new JMenuItem[actions.length];
			for (int a = 0; a < actions.length; a++) {
				actionMenuItems[a] = new JMenuItem();
				SimpleDataAction<BetterList<F>, ?> action = actions[a];
				SimpleButtonEditor<?, ?> buttonEditor = new SimpleButtonEditor<>(null, actionMenuItems[a], null, action.theObservableAction,
					false, getUntil());
				if (action.theButtonMod != null)
					action.theButtonMod.accept(buttonEditor);
				buttonEditor.getComponent();
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
		for (CategoryRenderStrategy<BetterList<F>, ?> column : theColumns) {
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

		JScrollPane scroll2 = new JScrollPane(getEditor());
		return scroll2;
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
				List<BetterList<F>> selectedRows = new ArrayList<>(theTable.getSelectedRowCount());
				for (int i = theTable.getSelectionModel().getMinSelectionIndex(); i <= theTable.getSelectionModel()
					.getMaxSelectionIndex(); i++) {
					if (theTable.getSelectionModel().isSelectedIndex(i))
						selectedRows.add(ObservableTreeModel.betterPath(theTable.getPathForRow(i)));
				}
				int columnIndex = theTable.getSelectedColumn();
				if (columnIndex >= 0)
					columnIndex = theTable.convertColumnIndexToModel(columnIndex);
				CategoryRenderStrategy<BetterList<F>, ?> column = columnIndex >= 0 ? theColumns.get(columnIndex) : null;
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
				CategoryRenderStrategy<BetterList<F>, ?> column = columnIndex >= 0 ? theColumns.get(columnIndex) : null;
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
				BetterList<F> path = rowIndex < 0 ? BetterList.of(theRoot.get())
					: ObservableTreeModel.betterPath(theTable.getPathForRow(rowIndex));
				F parent;
				ObservableCollection<F> children;
				ElementId targetRow;
				if (path.size() == 1) {
					parent = null;
					children = null;
					targetRow = null;
				} else {
					parent = path.get(path.size() - 2);
					if (theChildren1 != null)
						children = (ObservableCollection<F>) theChildren1.apply(parent);
					else
						children = (ObservableCollection<F>) theChildren2.apply(path.subList(0, path.size() - 1));
					try {
						targetRow = children.getElement(path.getLast(), true).getElementId();
					} catch (IndexOutOfBoundsException e) {
						return false; // Out-of-sync
					}
					if (children.getElement(targetRow).get() != path.getLast())
						return false; // Out-of-sync
				}
				if (rowIndex >= 0) {
					int columnIndex = support.isDrop() ? theTable.columnAtPoint(support.getDropLocation().getDropPoint())
						: theTable.getSelectedColumn();
					if (columnIndex >= 0)
						columnIndex = theTable.convertColumnIndexToModel(columnIndex);
					CategoryRenderStrategy<BetterList<F>, ?> column;
					if (columnIndex < 0)
						return false;
					else if (columnIndex == 0)
						column = theTreeColumn;
					else
						column = theColumns.get(columnIndex - 1);
					BetterList<F> parentPath = BetterTreeList.<F> build().build();
					parentPath.addAll(path.subList(0, path.size() - 1));
					if (canImport(support, parentPath, children, targetRow, rowIndex, column, false))
						return true;
				}
			} catch (RuntimeException | Error e) {
				e.printStackTrace();
				throw e;
			}
			return false;
		}

		class MutableTreeTableRow implements MutableCollectionElement<BetterList<F>> {
			private final MutableCollectionElement<F> terminal;
			private final BetterList<F> path;

			public MutableTreeTableRow(MutableCollectionElement<F> terminal, BetterList<F> path) {
				this.terminal = terminal;
				this.path = path;
			}

			@Override
			public ElementId getElementId() {
				return terminal.getElementId();
			}

			@Override
			public BetterList<F> get() {
				return path;
			}

			@Override
			public BetterCollection<BetterList<F>> getCollection() {
				throw new IllegalStateException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String isEnabled() {
				return terminal.isEnabled();
			}

			@Override
			public String isAcceptable(BetterList<F> value) {
				if (value.isEmpty())
					return StdMsg.ILLEGAL_ELEMENT;
				else
					return terminal.isAcceptable(value.getLast());
			}

			@Override
			public void set(BetterList<F> value) throws UnsupportedOperationException, IllegalArgumentException {
				// Just ignore the rest of the path
				if (value.isEmpty())
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				else
					terminal.set(value.getLast());
			}

			@Override
			public String canRemove() {
				return terminal.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				terminal.remove();
			}

			@Override
			public String toString() {
				return terminal.toString();
			}
		}

		private <C> boolean canImport(TransferSupport support, BetterList<F> parentPath, ObservableCollection<? extends F> children,
			ElementId childEl, int rowIndex, CategoryRenderStrategy<BetterList<F>, C> column, boolean doImport) {
			if (column.getMutator().getDragAccepter() == null)
				return false;
			if (children != null) {
				CollectionElement<? extends F> rowEl = children.getElement(childEl);
				parentPath.add(rowEl.get());
				C oldValue = column.getCategoryValue(parentPath);
				if (!column.getMutator().isEditable(parentPath, oldValue))
					return false;
				boolean selected = theTable.isRowSelected(rowIndex);
				ModelCell<BetterList<F>, C> cell = new ModelCell.Default<>(() -> BetterCollections.unmodifiableList(parentPath), oldValue,
					rowIndex, theColumns.indexOf(column), selected, selected, false, false, theTable.isExpanded(rowIndex),
					theLeafTest.test(rowEl.get()));
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
				if (newColValue == null || column.getMutator().isAcceptable(//
					new MutableTreeTableRow((MutableCollectionElement<F>) children.mutableElement(rowEl.getElementId()),
						cell.getModelValue()),
					newColValue.getFirst()) != null)
					return false;
				if (doImport) {
					column.getMutator().mutate(//
						new MutableTreeTableRow((MutableCollectionElement<F>) children.mutableElement(rowEl.getElementId()),
							cell.getModelValue()),
						newColValue.getFirst());
				}
				return true;
			} else {
				BetterList<F> root = BetterList.of(theRoot.get());
				C oldValue = column.getCategoryValue(root);
				if (!column.getMutator().isEditable(root, oldValue))
					return false;
				boolean selected = theTable.isRowSelected(rowIndex);
				ModelCell<BetterList<F>, C> cell = new ModelCell.Default<>(() -> root, oldValue, rowIndex, theColumns.indexOf(column),
					selected, selected, false, false, theTable.isExpanded(rowIndex), theLeafTest.test(root.getLast()));
				if (!column.getMutator().getDragAccepter().canAccept(cell, support, false))
					return false;
				BetterList<C> newColValue;
				try {
					newColValue = column.getMutator().getDragAccepter().accept(cell, support.getTransferable(), false, !doImport);
				} catch (IOException e) {
					return false;
				}
				MutableCollectionElement<BetterList<F>> syntheticRootEl = new MutableCollectionElement<BetterList<F>>() {
					@Override
					public ElementId getElementId() {
						throw new IllegalStateException();
					}

					@Override
					public BetterList<F> get() {
						return BetterList.of(theRoot.get());
					}

					@Override
					public BetterCollection<BetterList<F>> getCollection() {
						throw new IllegalStateException(StdMsg.UNSUPPORTED_OPERATION);
					}

					@Override
					public String isEnabled() {
						if (theRoot instanceof SettableValue)
							return ((SettableValue<F>) theRoot).isEnabled().get();
						else
							return StdMsg.UNSUPPORTED_OPERATION;
					}

					@Override
					public String isAcceptable(BetterList<F> value) {
						if (value.size() != 1)
							return StdMsg.ILLEGAL_ELEMENT;
						else if (theRoot instanceof SettableValue)
							return ((SettableValue<F>) theRoot).isAcceptable(value.getLast());
						else
							return StdMsg.UNSUPPORTED_OPERATION;
					}

					@Override
					public void set(BetterList<F> value) throws UnsupportedOperationException, IllegalArgumentException {
						if (value.size() != 1)
							throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
						else if (theRoot instanceof SettableValue)
							((SettableValue<F>) theRoot).set(value.getLast(), null);
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

					@Override
					public String toString() {
						return theRoot.toString();
					}
				};
				if (newColValue == null || column.getMutator().isAcceptable(//
					syntheticRootEl, newColValue.getFirst()) != null)
					return false;
				if (doImport) {
					column.getMutator().mutate(//
						syntheticRootEl, newColValue.getFirst());
				}
				return true;
			}
		}

		@Override
		public boolean importData(TransferSupport support) {
			try (// Transaction rowT = getRoot().lock(true, support); // Don't know what to lock here
				Transaction colT = theColumns.lock(false, null)) {
				int rowIndex;
				if (support.isDrop()) {
					rowIndex = theTable.rowAtPoint(support.getDropLocation().getDropPoint());
					if (rowIndex < 0)
						return false;
				} else
					rowIndex = theTable.getSelectedRow();
				BetterList<F> path = rowIndex < 0 ? BetterList.of(theRoot.get())
					: ObservableTreeModel.betterPath(theTable.getPathForRow(rowIndex));
				F parent;
				ObservableCollection<F> children;
				ElementId targetRow;
				if (path.size() == 1) {
					parent = null;
					children = null;
					targetRow = null;
				} else {
					parent = path.get(path.size() - 2);
					if (theChildren1 != null)
						children = (ObservableCollection<F>) theChildren1.apply(parent);
					else
						children = (ObservableCollection<F>) theChildren2.apply(path.subList(0, path.size() - 1));
					try {
						targetRow = children.getElement(path.getLast(), true).getElementId();
					} catch (IndexOutOfBoundsException e) {
						return false; // Out-of-sync
					}
					if (children.getElement(targetRow).get() != path.getLast())
						return false; // Out-of-sync
				}
				if (rowIndex >= 0) {
					int columnIndex = support.isDrop() ? theTable.columnAtPoint(support.getDropLocation().getDropPoint())
						: theTable.getSelectedColumn();
					if (columnIndex >= 0)
						columnIndex = theTable.convertColumnIndexToModel(columnIndex);
					CategoryRenderStrategy<BetterList<F>, ?> column;
					if (columnIndex < 0)
						return false;
					else if (columnIndex == 0)
						column = theTreeColumn;
					else
						column = theColumns.get(columnIndex - 1);
					BetterList<F> parentPath = BetterTreeList.<F> build().build();
					parentPath.addAll(path.subList(0, path.size() - 1));
					if (canImport(support, parentPath, children, targetRow, rowIndex, column, true)) {
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
