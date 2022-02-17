package org.observe.util.swing;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.jdesktop.swingx.JXTreeTable;
import org.jdesktop.swingx.treetable.TreeTableModel;
import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy.CategoryKeyListener;
import org.observe.util.swing.ObservableTableModel.TableRenderContext;
import org.qommons.ArrayUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;

/**
 * An observable model to supply a tree table with data
 *
 * @param <T> The type of values in the tree
 */
public class ObservableTreeTableModel<T> implements TreeTableModel {
	private final ObservableTreeModel<T> theTreeModel;
	private final ObservableCollection<? extends CategoryRenderStrategy<? super T, ?>> theColumns;
	// No aspect of this model may only change except on the EDT. We'll delegate to ObservableListModel's logic to handle this safely.
	private final ObservableListModel<? extends CategoryRenderStrategy<? super T, ?>> theColumnModel;

	private final ListenerList<ObservableTreeModel.PathMouseListener<? super T>> thePathMouseListeners;

	/**
	 * @param treeModel The tree model for this tree table models' hierarchy
	 * @param columns The columns for the table
	 */
	public ObservableTreeTableModel(ObservableTreeModel<T> treeModel,
		ObservableCollection<? extends CategoryRenderStrategy<? super T, ?>> columns) {
		theTreeModel = treeModel;
		theColumns = columns;

		theColumnModel = new ObservableListModel<>(columns);

		thePathMouseListeners = ListenerList.build().build();
	}

	/** @return The underlying tree model */
	public ObservableTreeModel<T> getTreeModel() {
		return theTreeModel;
	}

	/** @return The collection of additional table columns */
	public ObservableCollection<? extends CategoryRenderStrategy<? super T, ?>> getColumns() {
		return theColumns;
	}

	/** @return The additional table column, in an EDT-safe list model */
	public ObservableListModel<? extends CategoryRenderStrategy<? super T, ?>> getColumnModel() {
		return theColumnModel;
	}

	/**
	 * @param columnIndex The index of the column to get
	 * @return The column at the given index in the additional columns model
	 */
	public CategoryRenderStrategy<? super T, ?> getColumn(int columnIndex) {
		return theColumnModel.getElementAt(columnIndex);
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

	/**
	 * @param mouseListener The listener to receive mouse events for each path
	 * @return A Runnable to execute to cease mouse listening
	 */
	public Runnable addMouseListener(ObservableTreeModel.PathMouseListener<? super T> mouseListener) {
		return thePathMouseListeners.add(mouseListener, true);
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return TypeTokens.getRawType(theColumnModel.getElementAt(columnIndex).getType());
	}

	@Override
	public int getColumnCount() {
		return theColumnModel.getSize();
	}

	@Override
	public String getColumnName(int columnIndex) {
		return theColumnModel.getElementAt(columnIndex).getName();
	}

	@Override
	public int getHierarchicalColumn() {
		return 0;
	}

	@Override
	public Object getValueAt(Object treeValue, int columnIndex) {
		return theColumnModel.getElementAt(columnIndex).getCategoryValue((T) treeValue);
	}

	@Override
	public boolean isCellEditable(Object treeValue, int columnIndex) {
		CategoryRenderStrategy<? super T, Object> column = (CategoryRenderStrategy<? super T, Object>) theColumnModel
			.getElementAt(columnIndex);
		return column.getMutator().isEditable((T) treeValue, column.getCategoryValue((T) treeValue));
	}

	@Override
	public void setValueAt(Object newValue, Object treeValue, int columnIndex) {
		CategoryRenderStrategy<? super T, Object> column = (CategoryRenderStrategy<? super T, Object>) theColumnModel
			.getElementAt(columnIndex);
		MutableCollectionElement<T> element = new MutableCollectionElement<T>() {
			@Override
			public ElementId getElementId() {
				throw new UnsupportedOperationException();
			}

			@Override
			public T get() {
				return (T) treeValue;
			}

			@Override
			public BetterCollection<T> getCollection() {
				throw new UnsupportedOperationException();
			}

			@Override
			public String isEnabled() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public String isAcceptable(T value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
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

	/**
	 * @param <R> The row-type of the table model
	 * @param table The JTable to link with the supplementary (more than just model) functionality of the {@link ObservableTableModel}
	 * @param model The {@link ObservableTableModel} to control the table with
	 * @return A subscription which, when {@link Subscription#unsubscribe() unsubscribed}, will stop the non-model control of the model over
	 *         the table
	 */
	public static <R> Subscription hookUp(JXTreeTable table, ObservableTreeTableModel<R> model) {
		return hookUp(table, model, null);
	}

	/**
	 * @param <R> The row-type of the table model
	 * @param table The JTable to link with the supplementary (more than just model) functionality of the {@link ObservableTableModel}
	 * @param model The {@link ObservableTableModel} to control the table with
	 * @param ctx The table render context for highlighting
	 * @return A subscription which, when {@link Subscription#unsubscribe() unsubscribed}, will stop the non-model control of the model over
	 *         the table
	 */
	public static <R> Subscription hookUp(JXTreeTable table, ObservableTreeTableModel<R> model, TableRenderContext ctx) {
		LinkedList<Subscription> subs = new LinkedList<>();
		try (Transaction colT = model.getColumns().lock(false, null)) {
			for (int c = 0; c < model.getColumnCount(); c++) {
				CategoryRenderStrategy<? super R, ?> column = model.getColumn(c);
				TableColumn tblColumn = table.getColumnModel().getColumn(c);
				hookUp(table, tblColumn, column, model, ctx);
			}
			ListDataListener columnListener = new ListDataListener() {
				@Override
				public void intervalAdded(ListDataEvent e) {
					// TableColumnModel has no addColumn model that takes an index--it always appends to the end
					// Adding the column causes the table to immediately ask the model for data for that column,
					// and if the column is not actually the last one, the model will give the wrong data.
					// Moving the column in the model doesn't tell the table that the column's data is bad,
					// so this cannot be easily corrected.
					// At the moment, the best solution I can find is to remove all the columns after the ones to be added, then re-add
					// them.
					int afterColumnCount = table.getColumnModel().getColumnCount() - e.getIndex0();
					List<TableColumn> afterColumns = afterColumnCount == 0 ? Collections.emptyList() : new ArrayList<>(afterColumnCount);
					for (int i = table.getColumnModel().getColumnCount() - 1; i >= e.getIndex0(); i--) {
						TableColumn column = table.getColumnModel().getColumn(i);
						afterColumns.add(column);
						table.getColumnModel().removeColumn(column);
					}
					for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
						TableColumn column = new TableColumn(i);
						CategoryRenderStrategy<? super R, ?> category = model.getColumnModel().getElementAt(i);
						hookUp(table, column, category, model, ctx);
						table.getColumnModel().addColumn(column);
					}
					for (int i = afterColumns.size() - 1; i >= 0; i--) {
						afterColumns.get(i).setModelIndex(table.getColumnModel().getColumnCount());
						table.getColumnModel().addColumn(afterColumns.get(i));
					}
				}

				@Override
				public void intervalRemoved(ListDataEvent e) {
					for (int i = e.getIndex1(); i >= e.getIndex0(); i--)
						table.getColumnModel().removeColumn(table.getColumnModel().getColumn(i));
				}

				@Override
				public void contentsChanged(ListDataEvent e) {
					for (int i = e.getIndex0(); i <= e.getIndex1(); i++)
						hookUp(table, table.getColumnModel().getColumn(i), model.theColumnModel.getElementAt(i), model, ctx);
				}
			};
			model.getColumnModel().addListDataListener(columnListener);
			subs.add(() -> {
				model.getColumnModel().removeListDataListener(columnListener);
			});
			MouseAdapter ml = new MouseAdapter() {
				class MouseClickStruct<C> {
					private final ModelPath<R> thePath;
					private final ModelCell<R, C> cell;
					private final CategoryRenderStrategy<? super R, ? super C> theCategory;

					MouseClickStruct(ModelPath<R> path, ModelCell<R, C> cell, CategoryRenderStrategy<? super R, ? super C> category) {
						thePath = path;
						this.cell = cell;
						theCategory = category;
					}

					MouseClickStruct<C> clicked(MouseEvent e, MouseClickStruct<?> previous) {
						if (thePath != null)
							model.thePathMouseListeners.forEach(listener -> listener.mouseClicked(thePath, e));
						if (theCategory != null && theCategory.getMouseListener() != null)
							theCategory.getMouseListener().mouseClicked(cell, e);
						return this;
					}

					MouseClickStruct<C> pressed(MouseEvent e, MouseClickStruct<?> previous) {
						if (thePath != null)
							model.thePathMouseListeners.forEach(listener -> listener.mousePressed(thePath, e));
						if (theCategory != null && theCategory.getMouseListener() != null)
							theCategory.getMouseListener().mousePressed(cell, e);
						return this;
					}

					MouseClickStruct<C> released(MouseEvent e, MouseClickStruct<?> previous) {
						if (thePath != null)
							model.thePathMouseListeners.forEach(listener -> listener.mouseReleased(thePath, e));
						if (theCategory != null && theCategory.getMouseListener() != null)
							theCategory.getMouseListener().mouseReleased(cell, e);
						return this;
					}

					MouseClickStruct<C> entered(MouseEvent e, MouseClickStruct<?> previous) {
						if (thePath != null)
							model.thePathMouseListeners.forEach(listener -> listener.mouseEntered(thePath, e));
						if (theCategory != null && theCategory.getMouseListener() != null)
							theCategory.getMouseListener().mouseEntered(cell, e);
						return this;
					}

					MouseClickStruct<C> exited(MouseEvent e, MouseClickStruct<?> previous) {
						if (thePath != null)
							model.thePathMouseListeners.forEach(listener -> listener.mouseExited(thePath, e));
						if (theCategory != null && theCategory.getMouseListener() != null)
							theCategory.getMouseListener().mouseExited(cell, e);
						return this;
					}

					MouseClickStruct<C> moved(MouseEvent e, MouseClickStruct<?> previous) {
						if (previous != null && previous.thePath != null) {
							if (thePath != null && thePath.getModelValue() == previous.thePath.getModelValue()) {
								model.thePathMouseListeners.forEach(listener -> listener.mouseMoved(thePath, e));
								if (previous.theCategory != null && previous.theCategory == theCategory) {
									if (theCategory != null && theCategory.getMouseListener() != null)
										theCategory.getMouseListener().mouseMoved(cell, e);
								} else {
									previous.exitCell(e);
									if (theCategory != null && theCategory.getMouseListener() != null)
										theCategory.getMouseListener().mouseEntered(cell, e);
								}
							} else {
								previous.exitRow(e);
								if (theCategory != null && theCategory.getMouseListener() != null)
									theCategory.getMouseListener().mouseEntered(cell, e);
							}
						} else {
							if (theCategory != null && theCategory.getMouseListener() != null)
								theCategory.getMouseListener().mouseEntered(cell, e);
						}
						return this;
					}

					void exitCell(MouseEvent e) {
						if (theCategory != null && theCategory.getMouseListener() != null)
							theCategory.getMouseListener().mouseExited(cell, e);
					}

					void exitRow(MouseEvent e) {
						exitCell(e);
						model.thePathMouseListeners.forEach(listener -> listener.mouseExited(thePath, e));
					}
				}

				private MouseClickStruct<?> thePrevious;

				@Override
				public void mouseClicked(MouseEvent e) {
					thePrevious = getValue(e, false).clicked(e, thePrevious);
				}

				@Override
				public void mousePressed(MouseEvent e) {
					thePrevious = getValue(e, false).pressed(e, thePrevious);
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					thePrevious = getValue(e, false).released(e, thePrevious);
				}

				@Override
				public void mouseEntered(MouseEvent e) {
					thePrevious = getValue(e, false).entered(e, thePrevious);
				}

				@Override
				public void mouseExited(MouseEvent e) {
					thePrevious = getValue(e, false).exited(e, thePrevious);
				}

				@Override
				public void mouseMoved(MouseEvent e) {
					thePrevious = getValue(e, false).moved(e, thePrevious);
				}

				private <C> MouseClickStruct<C> getValue(MouseEvent evt, boolean movement) {
					int row = table.rowAtPoint(evt.getPoint());
					if (row < 0) {
						return new MouseClickStruct<>(null, null, null);
					}
					row = table.convertRowIndexToModel(row);
					TreePath path = table.getPathForRow(row);
					R rowValue = (R) path.getLastPathComponent();
					int column = table.columnAtPoint(evt.getPoint());
					if (column < 0) {
						if (model.thePathMouseListeners.isEmpty())
							return new MouseClickStruct<>(null, null, null);
						return new MouseClickStruct<>(new TreeTableModelPath<>(table, path), null, null);
					}
					column = table.convertColumnIndexToModel(column);
					CategoryRenderStrategy<? super R, C> category = (CategoryRenderStrategy<? super R, C>) model.getColumn(column);
					if (movement && category.getMouseListener() instanceof CategoryRenderStrategy.CategoryClickAdapter) {
						if (model.thePathMouseListeners.isEmpty())
							return new MouseClickStruct<>(null, null, null);
						return new MouseClickStruct<>(new TreeTableModelPath<>(table, path), null, null);
					}

					C colValue = category.getCategoryValue(rowValue);
					boolean enabled;
					if (category.getMutator().getEditability() != null) {
						enabled = category.getMutator().isEditable(rowValue, colValue);
					} else {
						enabled = true;
					}
					if (!enabled) {
						if (model.thePathMouseListeners.isEmpty())
							return new MouseClickStruct<>(null, null, null);
						return new MouseClickStruct<>(new TreeTableModelPath<>(table, path), null, null);
					}
					boolean selected = table.isCellSelected(row, column);
					ModelPath<R> modelPath = new TreeTableModelPath<>(table, path);
					ModelCell<R, C> cell = new ModelCell.Default<>(() -> rowValue, colValue, row, column, selected, selected, true, true);
					return new MouseClickStruct<>(modelPath, cell, category);
				}
			};
			table.addMouseListener(ml);
			table.addMouseMotionListener(ml);
			subs.add(() -> table.removeMouseListener(ml));
			subs.add(() -> table.removeMouseMotionListener(ml));
			MouseMotionListener tableMML = new MouseAdapter() {
				@Override
				public void mouseMoved(MouseEvent evt) {
					int row = table.rowAtPoint(evt.getPoint());
					if (row < 0) {
						table.setToolTipText(null);
						return;
					}
					int column = table.columnAtPoint(evt.getPoint());
					if (column < 0) {
						table.setToolTipText(null);
						return;
					}
					CategoryRenderStrategy<? super R, Object> category = (CategoryRenderStrategy<? super R, Object>) model
						.getColumn(column);
					row = table.convertRowIndexToModel(row);
					column = table.convertColumnIndexToModel(column);
					R rowValue = (R) table.getPathForRow(row).getLastPathComponent();
					table.setToolTipText(category.getTooltip(rowValue, category.getCategoryValue(rowValue)));
				}
			};
			table.addMouseMotionListener(tableMML);
			subs.add(() -> table.removeMouseMotionListener(tableMML));
			KeyListener tableKL = new KeyListener() {
				class KeyTypeStruct<C> {
					private final ModelCell<R, C> cell;
					private final CategoryKeyListener<? super R, ? super C> listener;

					KeyTypeStruct(ModelCell<R, C> cell, CategoryKeyListener<? super R, ? super C> listener) {
						this.cell = cell;
						this.listener = listener;
					}

					void typed(KeyEvent e) {
						listener.keyTyped(cell, e);
					}

					void pressed(KeyEvent e) {
						listener.keyPressed(cell, e);
					}

					void released(KeyEvent e) {
						listener.keyReleased(cell, e);
					}
				}

				@Override
				public void keyTyped(KeyEvent e) {
					KeyTypeStruct<?> value = getValue();
					if (value != null)
						value.typed(e);
				}

				@Override
				public void keyPressed(KeyEvent e) {
					KeyTypeStruct<?> value = getValue();
					if (value != null)
						value.pressed(e);
				}

				@Override
				public void keyReleased(KeyEvent e) {
					KeyTypeStruct<?> value = getValue();
					if (value != null)
						value.released(e);
				}

				<C> KeyTypeStruct<C> getValue() {
					int row = table.getSelectedRow();
					if (row < 0)
						return null;
					int column = table.getSelectedColumn();
					if (column < 0)
						return null;
					CategoryRenderStrategy<? super R, C> category = (CategoryRenderStrategy<? super R, C>) model.getColumn(column);
					if (category.getKeyListener() == null)
						return null;

					R rowValue = (R) table.getPathForRow(row).getLastPathComponent();
					C colValue = category.getCategoryValue(rowValue);
					boolean enabled;
					if (category.getMutator().getEditability() != null) {
						enabled = category.getMutator().isEditable(rowValue, colValue);
					} else {
						enabled = true;
					}
					if (!enabled) {
						return null;
					}
					boolean selected = table.isCellSelected(row, column);
					ModelCell<R, C> cell = new ModelCell.Default<>(() -> rowValue, colValue, row, column, selected, selected, true, true);
					return new KeyTypeStruct<>(cell, category.getKeyListener());
				}
			};
			table.addKeyListener(tableKL);
			subs.add(() -> table.removeKeyListener(tableKL));
			MouseAdapter headerML = new MouseAdapter() {
				@Override
				public void mouseMoved(MouseEvent evt) {
					int column = table.columnAtPoint(evt.getPoint());
					if (column < 0) {
						table.getTableHeader().setToolTipText(null);
						return;
					}
					int modelColumn = table.convertColumnIndexToModel(column);
					CategoryRenderStrategy<? super R, Object> category = (CategoryRenderStrategy<? super R, Object>) model
						.getColumn(modelColumn);
					table.getTableHeader().setToolTipText(category.getHeaderTooltip());
				}
			};
			table.getTableHeader().addMouseMotionListener(headerML);
			subs.add(() -> table.getTableHeader().removeMouseMotionListener(headerML));
		}
		return Subscription.forAll(subs.toArray(new Subscription[subs.size()]));
	}

	private static <R, C> void hookUp(JXTreeTable table, TableColumn tblColumn, CategoryRenderStrategy<? super R, ? extends C> column,
		ObservableTreeTableModel<R> model, TableRenderContext ctx) {
		tblColumn.setHeaderValue(column.getName());
		if (column.getIdentifier() != null)
			tblColumn.setIdentifier(column.getIdentifier());
		else
			tblColumn.setIdentifier(column);
		ObservableTreeTableCellRenderer<R, C> renderer = new ObservableTreeTableCellRenderer<>((CategoryRenderStrategy<R, C>) column, ctx);
		tblColumn.setCellRenderer(renderer);
		if (tblColumn.getModelIndex() == table.getTreeTableModel().getHierarchicalColumn())
			table.setTreeCellRenderer(renderer);
		if (column.getMutator().getEditor() != null)
			tblColumn.setCellEditor(column.getMutator().getEditor()//
				.withValueTooltip((row, col) -> ((CategoryRenderStrategy<R, Object>) column).getTooltip((R) row, col)));
		if (column.getMinWidth() >= 0)
			tblColumn.setMinWidth(column.getMinWidth());
		if (column.getPrefWidth() >= 0)
			tblColumn.setPreferredWidth(column.getPrefWidth());
		if (column.getMaxWidth() >= 0)
			tblColumn.setMaxWidth(column.getMaxWidth());
		tblColumn.setResizable(column.isResizable());
		// TODO Add other column stuff
	}

	private static class ObservableTreeTableCellRenderer<R, C> implements TableCellRenderer, TreeCellRenderer {
		private final CategoryRenderStrategy<R, C> theColumn;
		private final TableRenderContext theContext;
		private ComponentDecorator theDecorator;
		private Runnable theRevert;

		private Component theLastRender;

		ObservableTreeTableCellRenderer(CategoryRenderStrategy<R, C> column, TableRenderContext ctx) {
			theColumn = column;
			theContext = ctx;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row,
			int column) {
			int modelRow = table.convertRowIndexToModel(row);
			int modelColumn = table.convertColumnIndexToModel(column);
			return getCellRendererComponent(table, (R) ((JXTreeTable) table).getPathForRow(modelRow).getLastPathComponent(), //
				modelRow, modelColumn, value, isSelected, hasFocus, true, true, row, column);
		}

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row,
			boolean hasFocus) {
			return getCellRendererComponent(tree, (R) value, row, 0, value, selected, hasFocus, expanded, leaf, row, 0);
		}

		Component getCellRendererComponent(Component component, R modelValue, int modelRow, int modelColumn, Object value,
			boolean isSelected, boolean hasFocus, boolean expanded, boolean leaf, int row, int column) {
			if (theRevert != null) {
				theRevert.run();
				theRevert = null;
			}
			if (theLastRender != null) {
				theLastRender.setBackground(null);
				theLastRender.setForeground(null);
				theLastRender = null;
			}
			ObservableCellRenderer<R, C> renderer = theColumn.getRenderer() != null ? (ObservableCellRenderer<R, C>) theColumn.getRenderer()
				: new ObservableCellRenderer.DefaultObservableCellRenderer<>((r, c) -> String.valueOf(c));
			ModelCell<R, C> cell = new ModelCell.Default<>(() -> modelValue, (C) value, row, column, isSelected, hasFocus, expanded, leaf);
			Component c = renderer.getCellRendererComponent(component, cell,
				() -> theContext == null ? null : theContext.getEmphaticRegions(modelRow, modelColumn));
			theLastRender = c;

			if (theColumn.getDecorator() != null) {
				if (theDecorator == null)
					theDecorator = new ComponentDecorator();
				else
					theDecorator.reset();
				theColumn.getDecorator().decorate(cell, theDecorator);
				theRevert = theDecorator.decorate(c);
				theDecorator.reset();
			}
			return c;
		}
	}

	static class TreeTableModelPath<T> implements ModelPath<T> {
		private final JXTreeTable theTreeTable;
		private final TreePath thePath;
		private TreeTableModelPath<T> theParent;

		TreeTableModelPath(JXTreeTable treeTable, TreePath path) {
			theTreeTable = treeTable;
			thePath = path;
		}

		@Override
		public ModelPath<T> getParent() {
			if (theParent == null) {
				theParent = new TreeTableModelPath<>(theTreeTable, thePath.getParentPath());
			}
			return theParent;
		}

		@Override
		public T getModelValue() {
			return (T) thePath.getLastPathComponent();
		}

		@Override
		public int getRowIndex() {
			return theTreeTable.getRowForPath(thePath);
		}

		@Override
		public boolean isSelected() {
			return theTreeTable.getTreeSelectionModel().isPathSelected(thePath);
		}

		@Override
		public boolean hasFocus() {
			return isSelected();
		}

		@Override
		public boolean isExpanded() {
			return theTreeTable.isExpanded(thePath);
		}

		@Override
		public boolean isLeaf() {
			return theTreeTable.getTreeTableModel().isLeaf(thePath.getLastPathComponent());
		}
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
		boolean[] callbackLock = new boolean[1];
		TreeSelectionModel[] selectionModel = new TreeSelectionModel[] { treeTable.getTreeSelectionModel() };
		TreeSelectionListener selListener = e -> {
			if (callbackLock[0])
				return;
			TreeSelectionModel selModel = selectionModel[0];
			TreePath path;
			if (selModel.isSelectionEmpty())
				path = null;
			else if (singularOnly && selModel.getSelectionCount() > 1)
				path = null;
			else
				path = selModel.getLeadSelectionPath();
			callbackLock[0] = true;
			try {
				if (path != null) {
					List<T> list = (List<T>) (List<?>) Arrays.asList(path.getPath());
					selection.set(BetterList.of(list), e);
				} else if (selection.get() != null)
					selection.set(null, e);
			} finally {
				callbackLock[0] = false;
			}
		};
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
				TreeSelectionModel selModel = selectionModel[0];
				if (selModel.isSelectionEmpty())
					return;
				else if (singularOnly && selModel.getSelectionCount() > 1)
					return;
				int selIdx = selModel.getLeadSelectionRow();
				int found = ArrayUtils.binarySearch(0, e.getChildIndices().length, i -> Integer.compare(parentRow + i, selIdx));
				if (found < 0)
					return;
				callbackLock[0] = true;
				try {
					List<T> list = new ArrayList<>(e.getPath().length + 1);
					list.addAll((List<T>) (List<?>) Arrays.asList(e.getPath()));
					list.add((T) e.getChildren()[found]);
					selection.set(BetterList.of(list), e);
				} finally {
					callbackLock[0] = false;
				}
			}

			@Override
			public void treeStructureChanged(TreeModelEvent e) {
				if (callbackLock[0])
					return;
				TreeSelectionModel selModel = selectionModel[0];
				List<T> list;
				if (selModel.isSelectionEmpty())
					list = null;
				else if (singularOnly && selModel.getSelectionCount() > 1)
					list = null;
				else {
					TreePath path = treeTable.getPathForRow(selModel.getLeadSelectionRow());
					list = (List<T>) (List<?>) Arrays.asList(path.getPath());
				}
				if (!Objects.equals(list, selection.get())) {
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
				TreeSelectionModel selModel = selectionModel[0];
				if (evt.getNewValue() == null) {
					selModel.clearSelection();
				} else if (evt.getOldValue() == evt.getNewValue() && !selModel.isSelectionEmpty()//
					&& (selModel.getSelectionCount() == 1 || !singularOnly)//
					&& equivalence.elementEquals((T) selModel.getLeadSelectionPath().getLastPathComponent(), evt.getNewValue().getLast())) {
					ObservableTreeModel<T> model = (ObservableTreeModel<T>) treeTable.getModel();
					if (selModel.getLeadSelectionRow() == 0)
						model.rootChanged();
					else {
						TreePath parentPath = selModel.getLeadSelectionPath().getParentPath();
						int parentRow = treeTable.getRowForPath(parentPath);
						int childIdx = treeTable.getRowForPath(selModel.getLeadSelectionPath()) - parentRow - 1;
						ObservableCollection<? extends T> children = model.getNode((T) parentPath.getLastPathComponent()).getChildren();
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
					int row = treeTable.getRowForPath(path);
					if (row < 0) {
						treeTable.expandPath(path);
						selModel.setSelectionPath(path);
						treeTable.scrollPathToVisible(path);
					}
				}
			} finally {
				callbackLock[0] = false;
			}
		}));

		until.take(1).act(__ -> {
			selectionModel[0].removeTreeSelectionListener(selListener);
			treeTable.getTreeTableModel().removeTreeModelListener(modelListener);
		});
	}

	public static <T> void syncSelection(JXTreeTable tree, ObservableCollection<BetterList<T>> multiSelection, Observable<?> until) {
		// TODO Auto-generated method stub
		System.err.println("Not implemented yet");
	}
}
