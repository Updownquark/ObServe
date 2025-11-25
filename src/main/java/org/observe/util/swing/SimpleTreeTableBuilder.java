package org.observe.util.swing;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.ObservableCollection;
import org.observe.swingx.JXTreeTable;
import org.observe.util.ObservableCollectionSynchronization;
import org.observe.util.swing.AbstractObservableTableModel.TableRenderContext;
import org.observe.util.swing.Dragging.SimpleTransferAccepter;
import org.observe.util.swing.Dragging.SimpleTransferSource;
import org.observe.util.swing.Dragging.TransferAccepter;
import org.observe.util.swing.Dragging.TransferSource;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.TreeTableEditor;
import org.qommons.LambdaUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.ElementId;

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
class SimpleTreeTableBuilder<F, P extends SimpleTreeTableBuilder<F, P>> extends AbstractSimpleTableBuilder<BetterList<F>, JXTreeTable, P>
implements TreeTableEditor<F, P> {
	public static <F> SimpleTreeTableBuilder<F, ?> createTreeTable(ComponentEditor<?, ?> parent, ObservableValue<? extends F> root,
		Function<? super F, ? extends ObservableCollection<? extends F>> children, Observable<?> until) {
		return new SimpleTreeTableBuilder<>(parent, root, children, null, null, until);
	}

	public static <F> SimpleTreeTableBuilder<F, ?> createTreeTable2(ComponentEditor<?, ?> parent, ObservableValue<? extends F> root,
		Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children, Observable<?> until) {
		return new SimpleTreeTableBuilder<>(parent, root, null, children, null, until);
	}

	public static <F> SimpleTreeTableBuilder<F, ?> createTreeTable3(ComponentEditor<?, ?> parent, ObservableValue<? extends F> root,
		BiFunction<? super BetterList<F>, ? super Observable<?>, ? extends ObservableCollection<? extends F>> children,
			Observable<?> until) {
		return new SimpleTreeTableBuilder<>(parent, root, null, null, children, until);
	}

	private final ObservableValue<? extends F> theRoot;
	private final Function<? super F, ? extends ObservableCollection<? extends F>> theChildren1;
	private final Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> theChildren2;
	private final BiFunction<? super BetterList<F>, ? super Observable<?>, ? extends ObservableCollection<? extends F>> theChildren3;
	private Predicate<? super F> theLeafTest;
	private Predicate<? super BetterList<F>> theLeafTest2;

	private SettableValue<F> theValueSingleSelection;
	private boolean isSingleSelection;
	private ObservableCollection<F> theValueMultiSelection;
	private boolean isRootVisible;
	private Observable<?> theExpandAll;
	private Observable<?> theCollapseAll;

	private CategoryRenderStrategy<BetterList<F>, F> theTreeColumn;
	private ObservableCollection<? extends CategoryRenderStrategy<BetterList<F>, ?>> theDisplayedColumns;

	private SimpleTreeTableBuilder(ComponentEditor<?, ?> parent, ObservableValue<? extends F> root,
		Function<? super F, ? extends ObservableCollection<? extends F>> children1,
			Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children2,
				BiFunction<? super BetterList<F>, ? super Observable<?>, ? extends ObservableCollection<? extends F>> children3,
					Observable<?> until) {
		super(parent, null, new JXTreeTable(), until);
		theRoot = root;
		theChildren1 = children1;
		theChildren2 = children2;
		theChildren3 = children3;
		isRootVisible = true;
		theTreeColumn = new CategoryRenderStrategy<>("Tree", (Class<F>) Object.class,
			LambdaUtils.printableFn(BetterList::getLast, "BetterList::getLast", null));
	}

	class PPTreeModel extends ObservableTreeModel<F> {
		PPTreeModel() {
			super(theRoot);
		}

		@Override
		protected ObservableCollection<? extends F> getChildren(BetterList<F> parentPath, Observable<?> nodeUntil) {
			if (theChildren1 != null)
				return theChildren1.apply(parentPath.getLast());
			else if (theChildren2 != null)
				return theChildren2.apply(parentPath);
			else
				return theChildren3.apply(parentPath, nodeUntil);
		}

		@Override
		public void valueForPathChanged(TreePath path, Object newValue) {
		}

		@Override
		public boolean isLeaf(Object node) {
			ObservableTreeModel<F>.TreeNode treeNode = (ObservableTreeModel<F>.TreeNode) node;
			Predicate<? super F> leafTest = theLeafTest;
			if (leafTest != null)
				return leafTest.test(treeNode.get());
			Predicate<? super BetterList<F>> leafTest2 = theLeafTest2;
			if (leafTest2 != null) {
				return leafTest2.test(treeNode.getValuePath());
			}
			return false;
		}
	}

	@Override
	public List<BetterList<F>> getSelection() {
		TreePath[] selection = getEditor().getTreeSelectionModel().getSelectionPaths();
		if (selection == null || selection.length == 0)
			return BetterList.empty();
		return BetterList.of(Arrays.stream(selection)//
			.map(ObservableTreeModel::betterPath));
	}

	@Override
	protected Consumer<? super List<? extends BetterList<F>>> defaultDeletion() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public ObservableValue<? extends F> getRoot() {
		return theRoot;
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
	public P withLeafTest2(Predicate<? super BetterList<F>> leafTest) {
		theLeafTest2 = leafTest;
		return (P) this;
	}

	@Override
	public P withRootVisible(boolean rootVisible) {
		isRootVisible = rootVisible;
		return (P) this;
	}

	@Override
	public P withExpandAll(Observable<?> expandAll) {
		if (theExpandAll == null)
			theExpandAll = expandAll;
		else
			theExpandAll = Observable.or(theExpandAll, expandAll);
		return (P) this;
	}

	@Override
	public P withCollapseAll(Observable<?> expandAll) {
		if (theCollapseAll == null)
			theCollapseAll = expandAll;
		else
			theCollapseAll = Observable.or(theCollapseAll, expandAll);
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
	public CategoryRenderStrategy<BetterList<F>, F> getRender() {
		return theTreeColumn;
	}

	@Override
	public P withRender(CategoryRenderStrategy<BetterList<F>, F> render) {
		theTreeColumn = render;
		return (P) this;
	}

	@Override
	protected ObservableCollection<? extends CategoryRenderStrategy<BetterList<F>, ?>> createColumnSet() {
		if (theTreeColumn == null)
			theTreeColumn = new CategoryRenderStrategy<>("Tree", (Class<F>) Object.class, f -> f.getLast());
		ObservableCollection<? extends CategoryRenderStrategy<BetterList<F>, ?>> columns = getColumns();
		columns = columns.safe(ThreadConstraint.EDT, getUntil());
		columns = ObservableCollection.flattenCollections(ObservableCollection.of(theTreeColumn), columns).collect();
		return columns;
	}

	@Override
	protected AbstractObservableTableModel<BetterList<F>> createTableModel(
		ObservableCollection<? extends CategoryRenderStrategy<BetterList<F>, ?>> columns) {
		theDisplayedColumns = columns;
		ObservableTreeTableModel<F> model= new ObservableTreeTableModel<>(new PPTreeModel(), columns);
		JXTreeTable tree = getEditor();
		model.getTreeModel().withRenderer(theTreeColumn).withStatus(tree::getRowForPath, tree::isExpanded, tree.getTreeSelectionModel()::isPathSelected);
		return model;
	}

	@Override
	protected ObservableCollection<? extends CategoryRenderStrategy<BetterList<F>, ?>> getDisplayedColumns() {
		return theDisplayedColumns;
	}

	@Override
	protected TableRenderContext createTableRenderContext() {
		return null;
	}

	@Override
	protected void syncSelection(JXTreeTable table, AbstractObservableTableModel<BetterList<F>> model,
		SettableValue<BetterList<F>> selection, boolean enforceSingle) {
		ObservableTreeTableModel.syncSelection(table, selection, false, Equivalence.DEFAULT, getUntil());
		if (theValueSingleSelection != null) {
			ObservableTreeModel<F> treeModel = ((ObservableTreeTableModel<F>) model).getTreeModel();
			ObservableTreeTableModel.syncSelection(getEditor(), theValueSingleSelection.<BetterList<F>> transformReversible(//
				tx -> tx.map(v -> treeModel.getValuePath(v, true)).withReverse(path -> path == null ? null : path.getLast())), false,
				Equivalence.DEFAULT, getUntil());
		}
	}

	@Override
	protected void syncMultiSelection(JXTreeTable table, AbstractObservableTableModel<BetterList<F>> model,
		ObservableCollection<BetterList<F>> selection) {
		if (selection != null)
			ObservableTreeTableModel.syncSelection(table, selection, Equivalence.DEFAULT, getUntil());
		if (theValueMultiSelection != null) {
			ObservableTreeModel<F> treeModel = ((ObservableTreeTableModel<F>) model).getTreeModel();
			// Subscription sub = ObservableUtils.link(selection, theValueMultiSelection, //
			// path -> path == null ? null : path.getLast(), //
			// value -> treeModel.getBetterPath(value, true), false, false);
			ObservableCollection<F> modelValueSel = selection.flow()//
				.<F> transform(tx -> tx//
					.cache(false).reEvalOnUpdate(false).fireIfUnchanged(true)//
					.map(path -> path == null ? null : path.getLast())//
					.replaceSource(value -> treeModel.getValuePath(value, true), null))//
				.collect();
			Subscription sub = ObservableCollectionSynchronization.synchronize(modelValueSel, theValueMultiSelection)//
				.synchronize();
			getUntil().take(1).act(__ -> sub.unsubscribe());
		}
	}

	@Override
	protected boolean isDraggable() {
		Dragging.TransferSource<BetterList<F>, F> valueDragSource = null;
		Dragging.TransferAccepter<BetterList<F>, F> valueDragAccept = null;
		if (theTreeColumn != null) {
			valueDragSource = theTreeColumn.getDragSource();
			if (theTreeColumn.getMutator() != null)
				valueDragAccept = theTreeColumn.getMutator().getDragAccepter();
		}

		return super.isDraggable() || valueDragSource != null || valueDragAccept != null;
	}

	@Override
	protected TransferHandler setUpDnD(JXTreeTable table, SimpleTransferSource<BetterList<F>, ?> dragSource,
		SimpleTransferAccepter<BetterList<F>, ?> dragAccepter) {
		Dragging.TransferSource<BetterList<F>, F> valueDragSource = null;
		Dragging.TransferAccepter<BetterList<F>, F> valueDragAccept = null;
		if (theTreeColumn != null) {
			valueDragSource = theTreeColumn.getDragSource();
			if (theTreeColumn.getMutator() != null)
				valueDragAccept = theTreeColumn.getMutator().getDragAccepter();
		}

		return new TreeTableBuilderTransferHandler((JTree) table.getCellRenderer(0, table.getHierarchicalColumn()),
			(TransferSource<BetterList<F>, F>) dragSource, (TransferAccepter<BetterList<F>, F>) dragAccepter, valueDragSource,
			valueDragAccept);
	}

	@Override
	protected void onVisibleData(AbstractObservableTableModel<BetterList<F>> model,
		Consumer<CollectionChangeEvent<BetterList<F>>> onChange) {
		/* I've had so many issues here where the tree table doesn't report model updates to me correctly.
		 * E.g. when this call happens, the tree model is empty, but at some point it acquires the root row without reporting it.
		 * I also get duplicate add and remove calls, and sometimes update events for rows that haven't been reported.
		 * I don't understand why this is happening, but for reliability I'm just going to refresh any time things aren't as I expect.
		 */
		ObservableCollection<BetterList<F>> rows = ObservableCollection.create();
		JXTreeTable editor = getEditor();
		TableModelListener listener = new TableModelListener() {
			@Override
			public void tableChanged(TableModelEvent e) {
				if (e.getColumn() == TableModelEvent.ALL_COLUMNS && e.getFirstRow() != TableModelEvent.HEADER_ROW) {
					int eventRows = e.getLastRow() - e.getFirstRow() + 1;
					switch (e.getType()) {
					case TableModelEvent.INSERT:
						if (e.getFirstRow() > rows.size() || (rows.size() + eventRows != editor.getRowCount())) {
							// Not in-sync
							refresh(false);
							return;
						}
						BetterList<F>[] newRows = new BetterList[eventRows];
						int newIndex = 0;
						for (int r = e.getFirstRow(); r <= e.getLastRow(); r++)
							newRows[newIndex++] = model.getRow(r, editor);
						rows.addAll(e.getFirstRow(), Arrays.asList(newRows));
						break;
					case TableModelEvent.DELETE:
						if (e.getLastRow() >= rows.size() || (rows.size() - eventRows != editor.getRowCount()))
							refresh(false);
						else
							rows.subList(e.getFirstRow(), e.getLastRow() + 1).clear();
						break;
					case TableModelEvent.UPDATE:
						int lastRow = e.getLastRow();
						if (lastRow == Integer.MAX_VALUE) {// Stupid code for anything may have happened.
							refresh(true);
							return;
						}
						if (lastRow >= rows.size()) { // Not in-sync
							refresh(false);
						}
						for (int r = e.getFirstRow(); r <= lastRow; r++)
							rows.set(r, model.getRow(r, editor));
						break;
					}
				}
			}

			void refresh(boolean updateAll) {
				BetterList<F>[] currentRows = new BetterList[editor.getRowCount()];
				for (int r = 0; r < currentRows.length; r++)
					currentRows[r] = model.getRow(r, editor);
				try (Transaction t = rows.lock(true, null)) {
					CollectionUtils.synchronize(rows, Arrays.asList(currentRows), SimpleTreeTableBuilder::rowsIdentical)//
						.simple(LambdaUtils.identity())//
						.commonUses(true, updateAll)//
						.rightOrder()//
						.adjust();
				}
			}
		};
		editor.getModel().addTableModelListener(listener);
		rows.changes().takeUntil(getUntil()).act(onChange::accept);
		getUntil().act(__ -> editor.getModel().removeTableModelListener(listener));
	}

	static boolean rowsIdentical(BetterList<?> row1, BetterList<?> row2) {
		if (row1.size() != row2.size())
			return false;
		Iterator<?> iter1 = row1.iterator();
		Iterator<?> iter2 = row2.iterator();
		while (iter1.hasNext()) {
			if (iter1.next() != iter2.next())
				return false;
		}
		return true;
	}

	private static class ModelRowImpl<F> implements ModelRow<BetterList<F>> {
		private final JXTreeTable theTable;
		private final ObservableTreeTableModel<F> theModel;
		private int theRowIndex = -1;
		private BetterList<F> theRowValue;
		private String isEnabled;

		ModelRowImpl(JXTreeTable table, ObservableTreeTableModel<F> model) {
			theTable = table;
			theModel = model;
		}

		ModelRowImpl<F> nextRow(BetterList<F> rowValue) {
			theRowValue = rowValue;
			theRowIndex++;
			return this;
		}

		@Override
		public BetterList<F> getModelValue() {
			return theRowValue;
		}

		@Override
		public int getRowIndex() {
			return theRowIndex;
		}

		@Override
		public boolean isSelected() {
			return theTable.isRowSelected(theRowIndex);
		}

		@Override
		public boolean hasFocus() {
			return false;
		}

		@Override
		public boolean isRowHovered() {
			return false;
		}

		@Override
		public boolean isExpanded() {
			return theTable.isExpanded(theRowIndex);
		}

		@Override
		public boolean isLeaf() {
			return theModel.isLeaf(theRowValue.getLast());
		}

		@Override
		public String isEnabled() {
			return isEnabled;
		}

		@Override
		public ModelRow<BetterList<F>> setEnabled(String enabled) {
			isEnabled = enabled;
			return this;
		}
	}

	@Override
	protected void forAllVisibleData(AbstractObservableTableModel<BetterList<F>> model, Consumer<ModelRow<BetterList<F>>> forEach) {
		JXTreeTable table = getEditor();
		ModelRowImpl<F> row = new ModelRowImpl<>(table, (ObservableTreeTableModel<F>) model);
		for (int i = 0; i < table.getRowCount(); i++)
			forEach.accept(row.nextRow(model.getRow(i, table)));
	}

	public static JTree getTree(JXTreeTable treeTable) {
		return (JTree) treeTable.getCellRenderer(0, treeTable.getHierarchicalColumn());
	}

	@Override
	protected Component createComponent() {
		Component comp = super.createComponent();

		if (isSingleSelection)
			getEditor().getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

		if (theTreeColumn != null && theTreeColumn.getRenderer() != null)
			theTreeColumn.getRenderer().associate((Component) getEditor().getCellRenderer(0, 0)); // Hacky, but it works
		getEditor().setRootVisible(isRootVisible);
		JTree tree = getTree(getEditor());
		if (theExpandAll != null) {
			theExpandAll.takeUntil(getUntil()).act(__ -> {
				for (int r = 0; r < tree.getRowCount(); r++)
					tree.expandRow(r);
			});
		}
		if (theCollapseAll != null) {
			theCollapseAll.takeUntil(getUntil()).act(__ -> {
				int rowCount = tree.getRowCount();
				boolean collapsed = false;
				do {
					for (int r = rowCount - 1; r >= 0 && rowCount == tree.getRowCount(); r--)
						tree.collapseRow(rowCount - 1);
					collapsed = rowCount != tree.getRowCount();
				} while (collapsed);
			});
		}
		return comp;
	}

	class PathElement<N> implements ElementId {
		final ObservableTreeModel<F>.TreeNode parentNode;
		final ElementId childElement;

		PathElement(ObservableTreeModel<F>.TreeNode parentNode, ElementId childElement) {
			this.parentNode = parentNode;
			this.childElement = childElement;
		}

		@Override
		public int compareTo(ElementId o) {
			ElementId otherChild = ((PathElement<?>) o).childElement;
			if (childElement == null) {
				if (otherChild == null)
					return 0;
				else
					return -1;
			} else if (otherChild == null)
				return 1;
			return childElement.compareTo(otherChild);
		}

		@Override
		public boolean isPresent() {
			return childElement == null || childElement.isPresent();
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(childElement);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof PathElement && childElement.equals(((PathElement<?>) obj).childElement);
		}

		@Override
		public String toString() {
			String str = parentNode.toString();
			if (childElement != null)
				str += "." + childElement;
			return str;
		}
	}

	class TreeTableBuilderTransferHandler extends TransferHandler {
		private final JTree theTree;
		private final Dragging.TransferSource<BetterList<F>, F> thePathSource;
		private final Dragging.TransferAccepter<BetterList<F>, F> thePathAccepter;
		private final Dragging.TransferSource<BetterList<F>, F> theNodeSource;
		private final Dragging.TransferAccepter<BetterList<F>, F> theNodeAccepter;
		private Icon theDragAppearance;

		TreeTableBuilderTransferHandler(JTree tree, TransferSource<BetterList<F>, F> pathSource,
			TransferAccepter<BetterList<F>, F> pathAccepter, Dragging.TransferSource<BetterList<F>, F> nodeSource,
			Dragging.TransferAccepter<BetterList<F>, F> nodeAccepter) {
			theTree = tree;
			thePathSource = pathSource;
			thePathAccepter = pathAccepter;
			theNodeSource = nodeSource;
			theNodeAccepter = nodeAccepter;
		}

		@Override
		public int getSourceActions(JComponent c) {
			int actions = 0;
			if (thePathSource != null)
				actions |= thePathSource.getSourceActions();
			if (theNodeSource != null)
				actions |= theNodeSource.getSourceActions();
			return actions;
		}

		@Override
		protected Transferable createTransferable(JComponent c) {
			if ((thePathSource == null && theNodeSource == null) || theTree.getSelectionCount() == 0)
				return null;

			List<Transferable> transferables = new ArrayList<>(theTree.getSelectionCount());
			for (TreePath path : theTree.getSelectionPaths()) {
				BetterList<F> betterPath = ObservableTreeModel.betterPath(path);
				ModelCell<BetterList<F>, F> cell = new ModelCell.Default<>(() -> betterPath, betterPath.getLast(),
					theTree.getRowForPath(path), 0, theTree.isPathSelected(path), false, false, false, theTree.isExpanded(path),
					theTree.getModel().isLeaf(path.getLastPathComponent()));
				if (thePathSource != null) {
					Transferable pathT = thePathSource.createTransferable(cell);
					if (pathT != null)
						transferables.add(pathT);
				}
				if (theNodeSource != null) {
					Transferable nodeT = theNodeSource.createTransferable(cell);
					if (nodeT != null)
						transferables.add(nodeT);
				}
			}
			if (transferables.isEmpty())
				return null;
			else if (transferables.size() == 1)
				return transferables.get(0);
			else
				return new Dragging.AndTransferable(transferables.toArray(new Transferable[transferables.size()]));
		}

		@Override
		public Icon getVisualRepresentation(Transferable t) {
			if (theDragAppearance != null)
				return theDragAppearance;
			return super.getVisualRepresentation(t);
		}

		@Override
		protected void exportDone(JComponent source, Transferable data, int action) {
			// TODO If removed, scroll
			super.exportDone(source, data, action);
		}

		@Override
		public boolean canImport(TransferSupport support) {
			if ((thePathAccepter == null && theNodeAccepter == null) || theTree.getRowCount() == 0)
				return false;
			int rowIndex;
			boolean beforeRow;
			if (support.isDrop()) {
				Point dropPoint = support.getDropLocation().getDropPoint();
				rowIndex = theTree.getRowForLocation(dropPoint.x, dropPoint.y);
				if (rowIndex < 0) {
					rowIndex = theTree.getRowCount() - 1;
					beforeRow = false;
				} else {
					Rectangle bounds = theTree.getRowBounds(rowIndex);
					beforeRow = (support.getDropLocation().getDropPoint().y - bounds.y) <= bounds.height / 2;
				}
			} else {
				rowIndex = theTree.getLeadSelectionRow();
				if (rowIndex < 0)
					return false;
				beforeRow = false;
			}

			Dragging.TransferWrapper wrapper = Dragging.wrap(support);
			TreePath treePath = theTree.getPathForRow(rowIndex);
			BetterList<F> targetPath = ObservableTreeModel.betterPath(treePath);
			boolean selected = theTree.isRowSelected(rowIndex);
			ModelCell<BetterList<F>, F> cell = new ModelCell.Default<>(() -> targetPath, targetPath.getLast(), rowIndex, 0, selected,
				selected, false, false, theTree.isExpanded(rowIndex), theTree.getModel().isLeaf(treePath.getLastPathComponent()));
			if (thePathAccepter != null && thePathAccepter.canAccept(cell, false, beforeRow, wrapper, true)) {
				theDragAppearance = thePathAccepter.getDragAppearance();
				return true;
			}
			if (theNodeAccepter != null && theNodeAccepter.canAccept(cell, false, beforeRow, wrapper, true)) {
				theDragAppearance = theNodeAccepter.getDragAppearance();
				return true;
			}
			return false;
		}

		@Override
		public boolean importData(TransferSupport support) {
			if ((thePathAccepter == null && theNodeAccepter == null) || theTree.getRowCount() == 0)
				return false;
			int rowIndex;
			boolean beforeRow;
			if (support.isDrop()) {
				Point dropPoint = support.getDropLocation().getDropPoint();
				rowIndex = theTree.getRowForLocation(dropPoint.x, dropPoint.y);
				if (rowIndex < 0) {
					rowIndex = theTree.getRowCount() - 1;
					beforeRow = false;
				} else {
					Rectangle bounds = theTree.getRowBounds(rowIndex);
					beforeRow = (support.getDropLocation().getDropPoint().y - bounds.y) <= bounds.height / 2;
				}
			} else {
				rowIndex = theTree.getLeadSelectionRow();
				if (rowIndex < 0)
					return false;
				beforeRow = false;
			}

			Dragging.TransferWrapper wrapper = Dragging.wrap(support);
			TreePath treePath = theTree.getPathForRow(rowIndex);
			BetterList<F> targetPath = ObservableTreeModel.betterPath(treePath);
			boolean selected = theTree.isRowSelected(rowIndex);
			ModelCell<BetterList<F>, F> cell = new ModelCell.Default<>(() -> targetPath, targetPath.getLast(), rowIndex, 0, selected,
				selected, false, false, theTree.isExpanded(rowIndex), theTree.getModel().isLeaf(treePath.getLastPathComponent()));
			if (thePathAccepter != null && thePathAccepter.canAccept(cell, selected, beforeRow, wrapper, true)) {
				try {
					thePathAccepter.accept(cell, false, beforeRow, wrapper, true, false);
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else if (theNodeAccepter != null && theNodeAccepter.canAccept(cell, false, beforeRow, wrapper, true)) {
				try {
					theNodeAccepter.accept(cell, false, beforeRow, wrapper, true, false);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return false;
		}
	}
}
