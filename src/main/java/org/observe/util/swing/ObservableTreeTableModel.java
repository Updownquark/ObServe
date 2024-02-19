package org.observe.util.swing;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.swing.JTable;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.TableColumn;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.jdesktop.swingx.treetable.TreeTableModel;
import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.swingx.JXTreeTable;
import org.qommons.ArrayUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;

/**
 * An observable model to supply a tree table with data
 *
 * @param <T> The type of values in the tree
 */
public class ObservableTreeTableModel<T> extends AbstractObservableTableModel<BetterList<T>> implements TreeTableModel {
	private final ObservableTreeModel<T> theTreeModel;

	/**
	 * @param treeModel The tree model for this tree table models' hierarchy
	 * @param columns The columns for the table
	 */
	public ObservableTreeTableModel(ObservableTreeModel<T> treeModel,
		ObservableCollection<? extends CategoryRenderStrategy<BetterList<T>, ?>> columns) {
		super(columns);
		theTreeModel = treeModel;
	}

	/** @return The underlying tree model */
	public ObservableTreeModel<T> getTreeModel() {
		return theTreeModel;
	}

	@Override
	public Object getRoot() {
		return theTreeModel.getRoot();
	}

	@Override
	public Object getChild(Object parent, int index) {
		return theTreeModel.getChild(parent, index);
	}

	@Override
	public int getChildCount(Object parent) {
		return theTreeModel.getChildCount(parent);
	}

	@Override
	public boolean isLeaf(Object node) {
		return theTreeModel.isLeaf(node);
	}

	@Override
	public void valueForPathChanged(TreePath path, Object newValue) {
		theTreeModel.valueForPathChanged(path, newValue);
	}

	@Override
	public int getIndexOfChild(Object parent, Object child) {
		return theTreeModel.getIndexOfChild(parent, child);
	}

	@Override
	public void addTreeModelListener(TreeModelListener l) {
		theTreeModel.addTreeModelListener(l);
	}

	@Override
	public void removeTreeModelListener(TreeModelListener l) {
		theTreeModel.removeTreeModelListener(l);
	}

	@Override
	public int getHierarchicalColumn() {
		return 0;
	}

	@Override
	public Object getValueAt(Object treeValue, int columnIndex) {
		return getColumnModel().getElementAt(columnIndex).getCategoryValue(theTreeModel.getBetterPath((T) treeValue, false));
	}

	@Override
	public boolean isCellEditable(Object treeValue, int columnIndex) {
		CategoryRenderStrategy<? super BetterList<T>, Object> column = (CategoryRenderStrategy<? super BetterList<T>, Object>) getColumnModel()
			.getElementAt(columnIndex);
		BetterList<T> path = theTreeModel.getBetterPath((T) treeValue, false);
		return column.getMutator().isEditable(path, column.getCategoryValue(path));
	}

	@Override
	public void setValueAt(Object newValue, Object treeValue, int columnIndex) {
		BetterList<T> path = getTreeModel().getBetterPath((T) treeValue, false);
		if (path == null) {
			System.err.println("Could not find tree node " + treeValue);
			return;
		}
		CategoryRenderStrategy<BetterList<T>, Object> column = (CategoryRenderStrategy<BetterList<T>, Object>) getColumnModel()
			.getElementAt(columnIndex);
		MutableCollectionElement<BetterList<T>> element = new MutableCollectionElement<BetterList<T>>() {
			@Override
			public ElementId getElementId() {
				throw new UnsupportedOperationException();
			}

			@Override
			public BetterList<T> get() {
				return path;
			}

			@Override
			public BetterCollection<BetterList<T>> getCollection() {
				throw new UnsupportedOperationException();
			}

			@Override
			public String isEnabled() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public String isAcceptable(BetterList<T> value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void set(BetterList<T> value) throws UnsupportedOperationException, IllegalArgumentException {
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
		column.getMutator().mutate(element, newValue);
	}

	@Override
	protected Transaction lockRows(boolean write, Object cause) {
		return Transaction.NONE;
	}

	@Override
	protected BetterList<T> getRow(int rowIndex, JTable table) {
		return ObservableTreeModel.betterPath(((JXTreeTable) table).getPathForRow(rowIndex));
	}

	@Override
	protected boolean isExpanded(int rowIndex, JTable table) {
		return ((JXTreeTable) table).isExpanded(rowIndex);
	}

	@Override
	protected boolean isLeaf(int rowIndex, Supplier<BetterList<T>> rowValue) {
		return isLeaf(rowValue.get());
	}

	@Override
	public TableHookup hookUp(JTable table, TableRenderContext ctx) {
		if (((JXTreeTable) table).getTreeTableModel() != this)
			((JXTreeTable) table).setTreeTableModel(this);
		return super.hookUp(table, ctx);
	}

	@Override
	protected <C> void hookUpColumn(JTable table, TableColumn tblColumn, CategoryRenderStrategy<BetterList<T>, C> column,
		TableRenderContext ctx, IntSupplier hoveredRow, IntSupplier hoveredColumn) {
		super.hookUpColumn(table, tblColumn, column, ctx, hoveredRow, hoveredColumn);
		JXTreeTable treeTable = (JXTreeTable) table;
		if (tblColumn.getModelIndex() == treeTable.getTreeTableModel().getHierarchicalColumn())
			treeTable.setTreeCellRenderer((TreeCellRenderer) tblColumn.getCellRenderer());
	}

	/**
	 * Synchronizes a tree's selection model with a SettableValue whose value is a tree path (BetterList) of items in the tree
	 *
	 * @param treeTable The tree whose selection to synchronize
	 * @param selection The selected path value
	 * @param singularOnly Whether, when multiple items are selected in the tree, the selected value should be set to null (as opposed to
	 *        the lead value)
	 * @param equivalence The equivalence to use for the tree
	 * @param until An observable that, when fired, will release all resources and undo all subscriptions made by this method
	 */
	public static <T> void syncSelection(JXTreeTable treeTable, SettableValue<BetterList<T>> selection, boolean singularOnly,
		Equivalence<? super T> equivalence, Observable<?> until) {
		// This is copied from ObservableTreeModel and only slightly modified to match the JXTreeTable class,
		// but it's not really possible to consolidate this code, because though the JTree and JXTreeTable classes
		// have similar tree APIs, they're not related by inheritance
		ObservableTreeModel<T> model = ((ObservableTreeTableModel<T>) treeTable.getTreeTableModel()).getTreeModel();
		boolean[] callbackLock = new boolean[1];
		TreeSelectionModel selectionModel = treeTable.getTreeSelectionModel();
		TreeSelectionListener selListener = e -> {
			if (callbackLock[0])
				return;
			ObservableSwingUtils.flushEQCache();
			TreePath path;
			if (selectionModel.isSelectionEmpty())
				path = null;
			else if (singularOnly && selectionModel.getSelectionCount() > 1)
				path = null;
			else
				path = selectionModel.getLeadSelectionPath();
			callbackLock[0] = true;
			try {
				if (path != null) {
					List<T> list = (List<T>) (List<?>) Arrays.asList(path.getPath());
					BetterList<T> newSel = BetterList.of(list);
					if (selection.isAcceptable(newSel) == null)
						selection.set(newSel, e);
				} else if (selection.get() != null)
					selection.set(null, e);
			} finally {
				callbackLock[0] = false;
			}
		};
		selectionModel.addTreeSelectionListener(selListener);
		TreeModelListener modelListener = new TreeModelListener() {
			@Override
			public void treeNodesInserted(TreeModelEvent e) {
			}

			@Override
			public void treeNodesRemoved(TreeModelEvent e) {
			}

			@Override
			public void treeNodesChanged(TreeModelEvent e) {
				if (callbackLock[0])
					return;
				int parentRow = treeTable.getRowForPath(e.getTreePath());
				if (parentRow < 0 || !treeTable.isExpanded(parentRow))
					return;
				if (selectionModel.isSelectionEmpty())
					return;
				else if (singularOnly && selectionModel.getSelectionCount() > 1)
					return;
				TreePath selPath = selectionModel.getSelectionPath();
				if (!e.getTreePath().isDescendant(selPath) || selPath.getPathCount() == e.getTreePath().getPathCount())
					return;
				Object selNode = selPath.getPathComponent(e.getTreePath().getPathCount());
				int found = -1;
				for (int c = 0; c < e.getChildren().length; c++) {
					if (e.getChildren()[c] == selNode) {
						found = c;
						break;
					}
				}
				if (found < 0)
					return;
				callbackLock[0] = true;
				try {
					List<T> list = new ArrayList<>(e.getPath().length + 1);
					list.addAll((List<T>) (List<?>) Arrays.asList(e.getPath()));
					list.add((T) e.getChildren()[found]);
					if (selection.isAcceptable(BetterList.of(list)) == null)
						selection.set(BetterList.of(list), e);
				} finally {
					callbackLock[0] = false;
				}
			}

			@Override
			public void treeStructureChanged(TreeModelEvent e) {
				if (callbackLock[0])
					return;
				List<T> list;
				if (selectionModel.isSelectionEmpty())
					list = null;
				else if (singularOnly && selectionModel.getSelectionCount() > 1)
					list = null;
				else {
					TreePath path = treeTable.getPathForRow(selectionModel.getLeadSelectionRow());
					list = (List<T>) (List<?>) Arrays.asList(path.getPath());
				}
				if (!Objects.equals(list, selection.get()) && selection.isAcceptable(BetterList.of(list)) == null) {
					callbackLock[0] = true;
					try {
						selection.set(BetterList.of(list), e);
					} finally {
						callbackLock[0] = false;
					}
				}
			}
		};
		treeTable.getTreeTableModel().addTreeModelListener(modelListener);
		selection.changes().takeUntil(until).act(evt -> ObservableSwingUtils.onEQ(() -> {
			if (callbackLock[0])
				return;
			callbackLock[0] = true;
			try {
				if (evt.getNewValue() == null) {
					selectionModel.clearSelection();
				} else if (evt.getOldValue() == evt.getNewValue() && !selectionModel.isSelectionEmpty()//
					&& (selectionModel.getSelectionCount() == 1 || !singularOnly)//
					&& equivalence.elementEquals((T) selectionModel.getLeadSelectionPath().getLastPathComponent(),
						evt.getNewValue().getLast())) {
					if (selectionModel.getLeadSelectionRow() == 0)
						model.rootChanged();
					else {
						TreePath parentPath = selectionModel.getLeadSelectionPath().getParentPath();
						int parentRow = treeTable.getRowForPath(parentPath);
						int childIdx = treeTable.getRowForPath(selectionModel.getLeadSelectionPath()) - parentRow - 1;
						ObservableCollection<? extends T> children = model.getNode((T) parentPath.getLastPathComponent(), false)
							.getChildren();
						MutableCollectionElement<T> el = (MutableCollectionElement<T>) children
							.mutableElement(children.getElement(childIdx).getElementId());
						if (el.isAcceptable(evt.getNewValue().getLast()) == null) {
							try (Transaction t = children.lock(true, evt)) {
								el.set(evt.getNewValue().getLast());
							}
						}
					}
				} else {
					TreePath path = new TreePath(evt.getNewValue().toArray());
					Runnable select = new Runnable() {
						int tries = 0;

						@Override
						public void run() {
							if (model.getNode((T) path.getLastPathComponent(), false) != null && treeTable.isExpanded(path.getParentPath()))
								selectionModel.setSelectionPath(path);
							else if (++tries < path.getPathCount() + 5) {
								for (TreePath p = path.getParentPath(); p != null; p = p.getParentPath()) {
									if (model.getNode((T) p.getLastPathComponent(), false) != null) {
										if (!treeTable.isExpanded(p))
											treeTable.expandPath(p);
										break;
									}
								}
								EventQueue.invokeLater(this);
							}
						}
					};
					EventQueue.invokeLater(select);
				}
			} finally {
				callbackLock[0] = false;
			}
		}));

		until.take(1).act(__ -> {
			selectionModel.removeTreeSelectionListener(selListener);
			treeTable.getTreeTableModel().removeTreeModelListener(modelListener);
		});
	}

	/**
	 * Synchronizes selection between nodes in a tree table and an observable collection of tree paths
	 *
	 * @param <T> The type of nodes in the tree
	 * @param treeTable The tree table to synchronize selection for
	 * @param multiSelection The tree paths to synchronize the tree selection with
	 * @param until The observable to stop all listening
	 */
	public static <T> void syncSelection(JXTreeTable treeTable, ObservableCollection<BetterList<T>> multiSelection, Observable<?> until) {
		// This method assumes multiSelection is already safe for the EDT

		// Tree selection->collection
		boolean[] callbackLock = new boolean[1];
		TreeSelectionModel selectionModel = treeTable.getTreeSelectionModel();
		TreeSelectionListener selListener = e -> {
			if (callbackLock[0])
				return;
			ObservableSwingUtils.flushEQCache();
			callbackLock[0] = true;
			try (Transaction t = multiSelection.lock(true, e)) {
				CollectionUtils
				.synchronize(multiSelection, Arrays.asList(selectionModel.getSelectionPaths()),
					(better, treePath) -> isSamePath(better, treePath))//
				.adjust(new CollectionUtils.CollectionSynchronizer<BetterList<T>, TreePath>() {
					@Override
					public boolean getOrder(ElementSyncInput<BetterList<T>, TreePath> element) {
						return true;
					}

					@Override
					public ElementSyncAction leftOnly(ElementSyncInput<BetterList<T>, TreePath> element) {
						return element.remove();
					}

					@Override
					public ElementSyncAction rightOnly(ElementSyncInput<BetterList<T>, TreePath> element) {
						return element.useValue(ObservableTreeModel.betterPath(element.getRightValue()));
					}

					@Override
					public ElementSyncAction common(ElementSyncInput<BetterList<T>, TreePath> element) {
						return element.preserve();
					}

				}, CollectionUtils.AdjustmentOrder.RightOrder);
			} finally {
				callbackLock[0] = false;
			}
		};
		selectionModel.addTreeSelectionListener(selListener);
		// Tree model->update collection
		TreeModelListener modelListener = new TreeModelListener() {
			@Override
			public void treeNodesInserted(TreeModelEvent e) {}

			@Override
			public void treeNodesRemoved(TreeModelEvent e) {}

			@Override
			public void treeNodesChanged(TreeModelEvent e) {
				if (callbackLock[0])
					return;
				int parentRow = treeTable.getRowForPath(e.getTreePath());
				if (parentRow < 0 || !treeTable.isExpanded(parentRow))
					return;

				Transaction t = multiSelection.tryLock(true, e);
				if (t == null)
					return;
				callbackLock[0] = true;
				try {
					for (CollectionElement<BetterList<T>> selected : multiSelection.elements()) {
						if (eventApplies(e, selected.get()))
							multiSelection.mutableElement(selected.getElementId()).set(selected.get());
					}
				} finally {
					t.close();
					callbackLock[0] = false;
				}
			}

			@Override
			public void treeStructureChanged(TreeModelEvent e) {
				if (callbackLock[0])
					return;
				// Update the entire selection
				Transaction t = multiSelection.tryLock(true, e);
				if (t == null)
					return;
				try {
					for (CollectionElement<BetterList<T>> selected : multiSelection.elements())
						multiSelection.mutableElement(selected.getElementId()).set(selected.get());
				} finally {
					t.close();
				}
			}
		};
		treeTable.getTreeTableModel().addTreeModelListener(modelListener);
		// collection->tree selection
		Subscription msSub = multiSelection.simpleChanges().act(evt -> {
			if (callbackLock[0])
				return;
			callbackLock[0] = true;
			try (Transaction t = multiSelection.lock(false, modelListener)) {
				TreePath[] selection = new TreePath[multiSelection.size()];
				int i = 0;
				for (BetterList<T> path : multiSelection)
					selection[i++] = new TreePath(path.toArray());
				selectionModel.setSelectionPaths(selection);
			} finally {
				callbackLock[0] = false;
			}
		});

		until.take(1).act(__ -> {
			selectionModel.removeTreeSelectionListener(selListener);
			treeTable.getTreeTableModel().removeTreeModelListener(modelListener);
			msSub.unsubscribe();
		});
	}

	private static boolean isSamePath(BetterList<?> better, TreePath treePath) {
		if (better.size() != treePath.getPathCount())
			return false;
		for (Object betterV : better.reverse()) {
			if (!Objects.equals(betterV, treePath.getLastPathComponent()))
				return false;
			treePath = treePath.getParentPath();
		}
		return true;
	}

	private static boolean eventApplies(TreeModelEvent e, BetterList<?> path) {
		if (path.size() <= e.getTreePath().getPathCount())
			return false;
		if (!isSamePath(path.subList(0, e.getTreePath().getPathCount()), e.getTreePath()))
			return false;
		return ArrayUtils.contains(e.getChildren(), path.get(e.getTreePath().getPathCount()));
	}
}
