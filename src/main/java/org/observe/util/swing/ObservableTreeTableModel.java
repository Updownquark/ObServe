package org.observe.util.swing;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

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
import org.qommons.ThreadConstraint;
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
		if (columnIndex == getHierarchicalColumn())
			return treeValue;
		return getColumnModel().getElementAt(columnIndex).getCategoryValue(((ObservableTreeModel<T>.TreeNode) treeValue).getModelRow());
	}

	@Override
	public boolean isCellEditable(Object treeValue, int columnIndex) {
		return isCellEditable(((ObservableTreeModel<T>.TreeNode) treeValue).getModelRow(), getColumnModel().getElementAt(columnIndex),
			columnIndex);
	}

	private <C> boolean isCellEditable(ModelRow<BetterList<T>> modelRow, CategoryRenderStrategy<BetterList<T>, C> column, int columnIndex) {
		ModelCell<BetterList<T>, C> cell = new ModelCell.RowWrapper<>(modelRow, column.getCategoryValue(modelRow), columnIndex, false,
			false);
		return column.getMutator().isEditable(cell);
	}

	@Override
	public void setValueAt(Object newValue, Object treeValue, int columnIndex) {
		BetterList<T> path=((ObservableTreeModel<T>.TreeNode) treeValue).getValuePath();
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
	public BetterList<T> getRow(int rowIndex, JTable table) {
		return ((ObservableTreeModel<T>.TreeNode) ((JXTreeTable) table).getPathForRow(rowIndex).getLastPathComponent()).getValuePath();
	}

	@Override
	protected boolean isExpanded(int rowIndex, JTable table) {
		return ((JXTreeTable) table).isExpanded(rowIndex);
	}

	@Override
	protected boolean isLeaf(int rowIndex, JTable table) {
		return isLeaf(((JXTreeTable) table).getPathForRow(rowIndex).getLastPathComponent());
	}

	@Override
	public TableHookup hookUp(JTable table, TableRenderContext ctx) {
		if (((JXTreeTable) table).getTreeTableModel() != this)
			((JXTreeTable) table).setTreeTableModel(this);
		TableHookup hookup = super.hookUp(table, ctx);
		// JXTreeTable has this horrible feature where all the columns are re-loaded when the root changes.
		// The column model listener installed by the hookUp call is somehow not notified of these reloaded columns.
		// So after such an event, the columns are rendered by a default cell renderer.
		// I don't understand what's happening, but this seems to fix it.
		TreeModelListener reloadListener = new TreeModelListener() {
			@Override
			public void treeStructureChanged(TreeModelEvent e) {
				EventQueue.invokeLater(() -> hookupCurrentColumns(table, ctx, hookup));
			}

			@Override
			public void treeNodesRemoved(TreeModelEvent e) {
			}

			@Override
			public void treeNodesInserted(TreeModelEvent e) {
			}

			@Override
			public void treeNodesChanged(TreeModelEvent e) {
			}
		};
		theTreeModel.addTreeModelListener(reloadListener);
		return new TableHookup() {
			@Override
			public void unsubscribe() {
				hookup.unsubscribe();
				theTreeModel.removeTreeModelListener(reloadListener);
			}

			@Override
			public int getHoveredRow() {
				return hookup.getHoveredRow();
			}

			@Override
			public int getHoveredColumn() {
				return hookup.getHoveredColumn();
			}
		};
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
					BetterList<T> list = ObservableTreeModel.betterPath(path);
					if (selection.isAcceptable(list) == null)
						selection.set(list, e);
				} else if (selection.get() != null) {
					if (selection.isAcceptable(null) == null)
						selection.set(null, e);
				}
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
				ObservableSwingUtils.flushEQCache();
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
				ObservableTreeModel<T>.TreeNode selNode = (ObservableTreeModel<T>.TreeNode) selPath
					.getPathComponent(e.getTreePath().getPathCount());
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
					for (Object node : e.getPath())
						list.add(((ObservableTreeModel<T>.TreeNode) node).get());
					list.add(((ObservableTreeModel<T>.TreeNode) e.getChildren()[found]).get());
					BetterList<T> betterList=BetterList.of(list);
					if (selection.isAcceptable(betterList) == null)
						selection.set(betterList, e);
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
					list = new ArrayList<>(path.getPathCount());
					for (Object node : path.getPath())
						list.add(((ObservableTreeModel<T>.TreeNode) node).get());
				}
				BetterList<T> betterList=list==null ? null : BetterList.of(list);
				if (!Objects.equals(list, selection.get()) && selection.isAcceptable(betterList) == null) {
					callbackLock[0] = true;
					try {
						selection.set(betterList, e);
					} finally {
						callbackLock[0] = false;
					}
				}
			}
		};
		treeTable.getTreeTableModel().addTreeModelListener(modelListener);
		selection.changes().takeUntil(until).safe(ThreadConstraint.EDT).act(evt -> {
			if (callbackLock[0])
				return;
			callbackLock[0] = true;
			try {
				if (evt.getNewValue() == null) {
					selectionModel.clearSelection();
				} else if (evt.getOldValue() == evt.getNewValue() && !selectionModel.isSelectionEmpty()//
					&& (selectionModel.getSelectionCount() == 1 || !singularOnly)//
					&& ObservableTreeModel.isSamePath(evt.getNewValue(), selectionModel.getLeadSelectionPath(), equivalence)) {
					if (selectionModel.getLeadSelectionRow() == 0)
						model.rootChanged();
					else {
						TreePath parentPath = selectionModel.getLeadSelectionPath().getParentPath();
						int parentRow = treeTable.getRowForPath(parentPath);
						int childIdx = treeTable.getRowForPath(selectionModel.getLeadSelectionPath()) - parentRow - 1;
						ObservableCollection<? extends T> children = ((ObservableTreeModel<T>.TreeNode) parentPath.getLastPathComponent())
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
					Runnable select = new Runnable() {
						TreePath path;
						int tries = 0;

						@Override
						public void run() {
							if (callbackLock[0])
								return;
							callbackLock[0] = true;
							try {
								if (path == null)
									path = model.getTreePath(evt.getNewValue(), equivalence);
								if (path == null)
									EventQueue.invokeLater(this);
								else if (treeTable.isExpanded(path.getParentPath()))
									selectionModel.setSelectionPath(path);
								else if (++tries < path.getPathCount() + 5) {
									for (TreePath p = path.getParentPath(); p != null; p = p.getParentPath()) {
										if (!treeTable.isExpanded(p)) {
											treeTable.expandPath(p);
											break;
										}
									}
									EventQueue.invokeLater(this);
								}
							} finally {
								callbackLock[0] = false;
							}
						}
					};
					EventQueue.invokeLater(select);
				}
			} finally {
				callbackLock[0] = false;
			}
		});

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
	 * @param equivalence The equivalence for the tree values
	 * @param until The observable to stop all listening
	 */
	public static <T> void syncSelection(JXTreeTable treeTable, ObservableCollection<BetterList<T>> multiSelection,
		Equivalence<? super T> equivalence, Observable<?> until) {
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
					(better, treePath) -> ObservableTreeModel.isSamePath(better, treePath, equivalence))//
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
					int idx = 0;
					for (CollectionElement<BetterList<T>> selected : multiSelection.elements()) {
						int fIdx = idx;
						if (ObservableTreeModel.eventApplies(e, selected.get(), equivalence, () -> fIdx))
							multiSelection.mutableElement(selected.getElementId()).set(selected.get());
						idx++;
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
		ObservableTreeModel<T> treeModel = ((ObservableTreeTableModel<T>) treeTable.getTreeTableModel()).getTreeModel();
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
					selection[i++] = treeModel.getTreePath(path, equivalence);
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
}
