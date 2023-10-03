package org.observe.util.swing;

import java.awt.Component;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.InvalidDnDOperationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.ObservableCollection;
import org.observe.swingx.JXTreeTable;
import org.observe.util.TypeTokens;
import org.observe.util.swing.AbstractObservableTableModel.TableRenderContext;
import org.observe.util.swing.Dragging.SimpleTransferAccepter;
import org.observe.util.swing.Dragging.SimpleTransferSource;
import org.observe.util.swing.PanelPopulation.TreeTableEditor;
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
class SimpleTreeTableBuilder<F, P extends SimpleTreeTableBuilder<F, P>> extends AbstractSimpleTableBuilder<BetterList<F>, JXTreeTable, P>
implements TreeTableEditor<F, P> {
	public static <F> SimpleTreeTableBuilder<F, ?> createTreeTable(ObservableValue<F> root,
		Function<? super F, ? extends ObservableCollection<? extends F>> children, Observable<?> until) {
		return new SimpleTreeTableBuilder<>(root, children, null, null, until);
	}

	public static <F> SimpleTreeTableBuilder<F, ?> createTreeTable2(ObservableValue<F> root,
		Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children, Observable<?> until) {
		return new SimpleTreeTableBuilder<>(root, null, children, null, until);
	}

	public static <F> SimpleTreeTableBuilder<F, ?> createTreeTable3(ObservableValue<F> root,
		BiFunction<? super BetterList<F>, ? super Observable<?>, ? extends ObservableCollection<? extends F>> children,
			Observable<?> until) {
		return new SimpleTreeTableBuilder<>(root, null, null, children, until);
	}

	private final ObservableValue<F> theRoot;
	private final Function<? super F, ? extends ObservableCollection<? extends F>> theChildren1;
	private final Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> theChildren2;
	private final BiFunction<? super BetterList<F>, ? super Observable<?>, ? extends ObservableCollection<? extends F>> theChildren3;
	private Predicate<? super F> theLeafTest;
	private Predicate<? super BetterList<F>> theLeafTest2;

	private SettableValue<F> theValueSingleSelection;
	private boolean isSingleSelection;
	private ObservableCollection<F> theValueMultiSelection;
	private boolean isRootVisible;

	private CategoryRenderStrategy<BetterList<F>, F> theTreeColumn;

	private SimpleTreeTableBuilder(ObservableValue<F> root, Function<? super F, ? extends ObservableCollection<? extends F>> children1,
		Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children2,
			BiFunction<? super BetterList<F>, ? super Observable<?>, ? extends ObservableCollection<? extends F>> children3,
				Observable<?> until) {
		super(null, new JXTreeTable(), until);
		theRoot = root;
		theChildren1 = children1;
		theChildren2 = children2;
		theChildren3 = children3;
		isRootVisible = true;
		theTreeColumn = new CategoryRenderStrategy<>("Tree", root.getType(),
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
			Predicate<? super F> leafTest = theLeafTest;
			if (leafTest != null)
				return leafTest.test((F) node);
			Predicate<? super BetterList<F>> leafTest2 = theLeafTest2;
			if (leafTest2 != null) {
				BetterList<F> path = getBetterPath((F) node, false);
				if (path != null)
					return leafTest2.test(path);
			}
			return false;
		}
	}

	@Override
	public List<BetterList<F>> getSelection() {
		TreePath[] selection = getEditor().getTreeSelectionModel().getSelectionPaths();
		return BetterList.of(Arrays.stream(selection)//
			.map(path -> (BetterList<F>) BetterList.of(path.getPath())));
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
	protected TypeToken<BetterList<F>> getRowType() {
		return TypeTokens.get().keyFor(BetterList.class).<BetterList<F>> parameterized(theRoot.getType());
	}

	@Override
	protected ObservableCollection<? extends CategoryRenderStrategy<BetterList<F>, ?>> createColumnSet() {
		TypeToken<CategoryRenderStrategy<BetterList<F>, ?>> columnType = TypeTokens.get().keyFor(CategoryRenderStrategy.class)
			.parameterized(//
				TypeTokens.get().keyFor(BetterList.class).parameterized(theRoot.getType()), //
				TypeTokens.get().WILDCARD);
		if (theTreeColumn == null)
			theTreeColumn = new CategoryRenderStrategy<>("Tree", theRoot.getType(), f -> f.getLast());
		ObservableCollection<? extends CategoryRenderStrategy<BetterList<F>, ?>> columns = getColumns();
		columns = columns.safe(ThreadConstraint.EDT, getUntil());
		columns = ObservableCollection.flattenCollections(columnType, //
			ObservableCollection.of(columnType, theTreeColumn), //
			columns).collect();
		return columns;
	}

	@Override
	protected AbstractObservableTableModel<BetterList<F>> createTableModel(
		ObservableCollection<? extends CategoryRenderStrategy<BetterList<F>, ?>> columns) {
		return new ObservableTreeTableModel<>(new PPTreeModel(), columns);
	}

	@Override
	protected TableRenderContext createTableRenderContext() {
		return null;
	}

	@Override
	protected void syncSelection(JXTreeTable table, AbstractObservableTableModel<BetterList<F>> model,
		SettableValue<BetterList<F>> selection, boolean enforceSingle) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void syncMultiSelection(JXTreeTable table, AbstractObservableTableModel<BetterList<F>> model,
		ObservableCollection<BetterList<F>> selection) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void watchSelection(AbstractObservableTableModel<BetterList<F>> model, JXTreeTable table, Consumer<Object> onSelect) {
		// TODO Auto-generated method stub

	}

	@Override
	protected TransferHandler setUpDnD(JXTreeTable table, SimpleTransferSource<BetterList<F>> dragSource,
		SimpleTransferAccepter<BetterList<F>, BetterList<F>, BetterList<F>> dragAccepter) {
		return new TreeTableBuilderTransferHandler(table);
	}

	@Override
	protected void onVisibleData(Consumer<CollectionChangeEvent<BetterList<F>>> onChange) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void forAllVisibleData(Consumer<ModelRow<BetterList<F>>> forEach) {
		// TODO Auto-generated method stub

	}

	@Override
	protected Component createComponent() {
		Component comp = super.createComponent();

		ObservableTreeTableModel<F> model = (ObservableTreeTableModel<F>) getEditor().getTreeTableModel();
		// Selection
		if (theValueMultiSelection != null)
			ObservableTreeTableModel.syncSelection(
				getEditor(), theValueSingleSelection.safe(ThreadConstraint.EDT, getUntil()).transformReversible(//
					TypeTokens.get().keyFor(BetterList.class).<BetterList<F>> parameterized(getRoot().getType()), tx -> tx
					.map(v -> model.getTreeModel().getBetterPath(v, true)).withReverse(path -> path == null ? null : path.getLast())),
				false, Equivalence.DEFAULT, getUntil());
		if (theValueSingleSelection != null)
			ObservableTreeTableModel.syncSelection(getEditor(), theValueSingleSelection.transformReversible(//
				TypeTokens.get().keyFor(BetterList.class).<BetterList<F>> parameterized(getRoot().getType()),
				tx -> tx.map(v -> model.getTreeModel().getBetterPath(v, true)).withReverse(path -> path == null ? null : path.getLast())),
				false, Equivalence.DEFAULT, getUntil());
		if (isSingleSelection)
			getEditor().getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

		getEditor().setRootVisible(isRootVisible);
		return comp;
	}

	class TreeTableBuilderTransferHandler extends TransferHandler {
		private final JXTreeTable theTable;

		TreeTableBuilderTransferHandler(JXTreeTable table) {
			theTable = table;
		}

		@Override
		protected Transferable createTransferable(JComponent c) {
			try (Transaction colT = getColumns().lock(false, null)) {
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
				CategoryRenderStrategy<BetterList<F>, ?> column = columnIndex >= 0 ? getColumns().get(columnIndex) : null;
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
			try (Transaction colT = getColumns().lock(false, null)) {
				if (theTable.getSelectedRowCount() == 0)
					return actions;
				int columnIndex = theTable.getSelectedColumn();
				if (columnIndex >= 0)
					columnIndex = theTable.convertColumnIndexToModel(columnIndex);
				CategoryRenderStrategy<BetterList<F>, ?> column = columnIndex >= 0 ? getColumns().get(columnIndex) : null;
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
			try (Transaction colT = getColumns().lock(false, null)) {
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
						column = getColumns().get(columnIndex - 1);
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
					rowIndex, getColumns().indexOf(column) + 1, selected, selected, false, false, theTable.isExpanded(rowIndex),
					theLeafTest.test(rowEl.get()), null);
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
				ModelCell<BetterList<F>, C> cell = new ModelCell.Default<>(() -> root, oldValue, rowIndex, getColumns().indexOf(column) + 1,
					selected, selected, false, false, theTable.isExpanded(rowIndex), theLeafTest.test(root.getLast()), null);
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
				Transaction colT = getColumns().lock(false, null)) {
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
						column = getColumns().get(columnIndex - 1);
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
