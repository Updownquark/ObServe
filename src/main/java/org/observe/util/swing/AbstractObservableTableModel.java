package org.observe.util.swing;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.TreeCellRenderer;

import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.dbug.Dbug;
import org.observe.dbug.DbugAnchor;
import org.observe.dbug.DbugAnchorType;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy.CategoryKeyListener;
import org.observe.util.swing.CategoryRenderStrategy.CategoryMouseListener;
import org.qommons.IntList;
import org.qommons.Transaction;
import org.qommons.TriConsumer;
import org.qommons.collect.ListenerList;

import com.google.common.reflect.TypeToken;

/**
 * A tabular model backed by an observable collection {@link #getColumns() column definitions}
 *
 * @param <R> The type of the row data for the table
 */
public abstract class AbstractObservableTableModel<R> {
	/** {@link Dbug} anchor type for this class */
	@SuppressWarnings("rawtypes")
	public static final DbugAnchorType<AbstractObservableTableModel> DBUG=Dbug.common().anchor(AbstractObservableTableModel.class, null);

	private final ObservableCollection<? extends CategoryRenderStrategy<R, ?>> theColumns;
	private final ObservableListModel<? extends CategoryRenderStrategy<R, ?>> theColumnModel;

	private final ListenerList<RowMouseListener<? super R>> theRowMouseListeners;

	@SuppressWarnings("rawtypes")
	private final DbugAnchor<AbstractObservableTableModel> theAnchor = DBUG.instance(this);

	/**
	 * @param colNames The names for the columns
	 * @param columns The functions that provide cell data for each row
	 */
	protected AbstractObservableTableModel(String[] colNames, Function<? super R, ?>... columns) {
		this(makeColumns(colNames, columns));
	}

	private static <R> ObservableCollection<? extends CategoryRenderStrategy<R, ?>> makeColumns(String[] colNames,
		Function<? super R, ?>[] columnAccessors) {
		if (colNames.length != columnAccessors.length) {
			throw new IllegalArgumentException(
				"Column names and columns do not have the same lengths (" + colNames.length + " and " + columnAccessors.length + ")");
		}
		CategoryRenderStrategy<R, ?>[] columns = new CategoryRenderStrategy[colNames.length];
		for (int i = 0; i < columns.length; i++) {
			columns[i] = new CategoryRenderStrategy<>(colNames[i], (TypeToken<Object>) detectColumnClass(columnAccessors[i]),
				columnAccessors[i]);
		}
		return ObservableCollection.of(new TypeToken<CategoryRenderStrategy<R, ?>>() {
		}, columns);
	}

	private static TypeToken<?> detectColumnClass(Function<?, ?> accessor) {
		// Note that this doesn't work on lambdas, or classes that use a type argument for the function result type
		// So this will return Object a lot
		return TypeToken.of(accessor.getClass()).resolveType(Function.class.getTypeParameters()[1]);
	}

	/**
	 * @param columns The columns for the table
	 */
	protected AbstractObservableTableModel(ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns) {
		theColumns = columns;

		try (Transaction t = theAnchor.instantiating()//
			.watchFor(ObservableListModel.DBUG, "columnModel", tk -> tk.skip(1))) {
			theColumnModel = new ObservableListModel<>(columns);
		}

		theRowMouseListeners = ListenerList.build().build();
	}

	/** @return This table model's columns */
	public ObservableCollection<? extends CategoryRenderStrategy<R, ?>> getColumns() {
		return theColumns;
	}

	/** @return This table model's columns, in {@link ListModel} form */
	public ObservableListModel<? extends CategoryRenderStrategy<R, ?>> getColumnModel() {
		return theColumnModel;
	}

	/**
	 * @param index The index of the row to get
	 * @return The column definition at the given index
	 */
	public CategoryRenderStrategy<R, ?> getColumn(int index) {
		return theColumnModel.getElementAt(index);
	}

	public int getColumnCount() {
		return theColumnModel.getSize();
	}

	public String getColumnName(int columnIndex) {
		return theColumnModel.getElementAt(columnIndex).getName();
	}

	public Class<?> getColumnClass(int columnIndex) {
		return TypeTokens.getRawType(theColumnModel.getElementAt(columnIndex).getType());
	}

	/**
	 * @param mouseListener The listener to receive mouse events for each row
	 * @return A Runnable to execute to cease mouse listening
	 */
	public Runnable addMouseListener(RowMouseListener<? super R> mouseListener) {
		return theRowMouseListeners.add(mouseListener, true);
	}

	protected abstract Transaction lockRows(boolean write, Object cause);

	/**
	 * @param index The index of the row to get
	 * @return The row value at the given index
	 */
	protected abstract R getRow(int rowIndex, JTable table);

	protected abstract boolean isExpanded(int rowIndex, JTable table);

	protected abstract boolean isLeaf(int rowIndex, Supplier<R> rowValue);

	/**
	 * @param table The JTable to link with the supplementary (more than just model) functionality of the {@link AbstractObservableTableModel}
	 * @return A subscription which, when {@link Subscription#unsubscribe() unsubscribed}, will stop the non-model control of the model over
	 *         the table
	 */
	public Subscription hookUp(JTable table) {
		return hookUp(table, null);
	}

	interface TableHookup extends Subscription {
		int getHoveredRow();

		int getHoveredColumn();

		static TableHookup of(Subscription sub, IntSupplier hoveredRow, IntSupplier hoveredColumn) {
			return new TableHookup() {
				@Override
				public void unsubscribe() {
					sub.unsubscribe();
				}

				@Override
				public int getHoveredRow() {
					return hoveredRow.getAsInt();
				}

				@Override
				public int getHoveredColumn() {
					return hoveredColumn.getAsInt();
				}
			};
		}
	}

	/**
	 * @param table The JTable to link with the supplementary (more than just model) functionality of the {@link AbstractObservableTableModel}
	 * @param ctx The table render context for highlighting
	 * @return A subscription which, when {@link Subscription#unsubscribe() unsubscribed}, will stop the non-model control of the model over
	 *         the table
	 */
	public TableHookup hookUp(JTable table, TableRenderContext ctx) {
		LinkedList<Subscription> subs = new LinkedList<>();
		TableMouseListener<R> ml = new TableMouseListener<R>(table) {
			@Override
			protected ListenerList<RowMouseListener<? super R>> getRowListeners() {
				return theRowMouseListeners;
			}

			@Override
			protected int getModelRow(MouseEvent evt) {
				int row = table.rowAtPoint(evt.getPoint());
				row = row < 0 ? row : table.convertRowIndexToModel(row);
				return row;
			}

			@Override
			protected int getModelColumn(MouseEvent evt) {
				int col = table.columnAtPoint(evt.getPoint());
				col = col < 0 ? col : table.convertColumnIndexToModel(col);
				return col;
			}

			@Override
			protected Point getCellOffset(int row, int column) {
				Rectangle bounds = table.getCellRect(row, column, false);
				return bounds == null ? null : bounds.getLocation();
			}

			@Override
			protected R getRowValue(int rowIndex) {
				return AbstractObservableTableModel.this.getRow(rowIndex, table);
			}

			@Override
			protected CategoryRenderStrategy<R, ?> getColumn(int columnIndex) {
				return getColumnModel().getElementAt(columnIndex);
			}

			@Override
			protected boolean isRowSelected(int rowIndex) {
				return table.isRowSelected(rowIndex);
			}

			@Override
			protected boolean isCellSelected(int rowIndex, int columnIndex) {
				return table.isCellSelected(rowIndex, columnIndex);
			}

			@Override
			protected <C> void setToolTip(String tooltip, boolean header) {
				(header ? table.getTableHeader() : table).setToolTipText(tooltip);
			}

			@Override
			public void mousePressed(MouseEvent e) {
				super.mousePressed(e);
				// Re-render hovered cell for state change
				int r = getHoveredRow();
				int c = getHoveredColumn();
				if (r >= 0 && c >= 0)
					table.repaint(table.getCellRect(r, c, false));
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				super.mouseReleased(e);
				// Re-render hovered cell for state change
				int r = getHoveredRow();
				int c = getHoveredColumn();
				if (r >= 0 && c >= 0)
					table.repaint(table.getCellRect(r, c, false));
			}

			@Override
			protected void hoverChanged(int preHoveredRow, int preHoveredColumn, int newHoveredRow, int newHoveredColumn) {
				// Repaint the rows, in case they change due to hover
				if (preHoveredRow != newHoveredRow) {
					if (preHoveredRow >= 0) {
						// System.out.println("Row exited " + preHoveredRow);
						Rectangle bounds = table.getCellRect(preHoveredRow, 0, false);
						table.repaint(0, bounds.y, table.getWidth(), bounds.height);
					}
					if (newHoveredRow >= 0) {
						// System.out.println("Row entered " + newHoveredRow);
						Rectangle bounds = table.getCellRect(newHoveredRow, 0, false);
						table.repaint(0, bounds.y, table.getWidth(), bounds.height);
					}
				} else if (preHoveredColumn != newHoveredColumn) {
					if (preHoveredColumn >= 0) {
						// System.out.println("Cell exited " + preHoveredColumn);
						table.repaint(table.getCellRect(preHoveredRow, preHoveredColumn, false));
					}
					if (newHoveredColumn >= 0) {
						// System.out.println("Cell entered " + newHoveredColumn);
						table.repaint(table.getCellRect(preHoveredRow, newHoveredColumn, false));
					}
				}
			}
		};
		try (Transaction rowT = lockRows(false, null); Transaction colT = getColumns().lock(false, null)) {
			for (int c = 0; c < getColumnCount(); c++) {
				CategoryRenderStrategy<R, ?> column = getColumn(c);
				TableColumn tblColumn;
				if (c < table.getColumnModel().getColumnCount())
					tblColumn = table.getColumnModel().getColumn(c);
				else {
					tblColumn = new TableColumn(c);
					table.getColumnModel().addColumn(tblColumn);
				}
				hookUpColumn(table, tblColumn, column, ctx, ml::getHoveredRow, ml::getHoveredColumn);
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
						CategoryRenderStrategy<R, ?> category = getColumnModel().getElementAt(i);
						hookUpColumn(table, column, category, ctx, ml::getHoveredRow, ml::getHoveredColumn);
						table.getColumnModel().addColumn(column);
					}
					for (int i = afterColumns.size() - 1; i >= 0; i--) {
						afterColumns.get(i).setModelIndex(table.getColumnModel().getColumnCount());
						table.getColumnModel().addColumn(afterColumns.get(i));
					}
				}

				@Override
				public void intervalRemoved(ListDataEvent e) {
					IntList removedIndexes = new IntList(true, true);
					for (int i = e.getIndex1(); i >= e.getIndex0(); i--) {
						removedIndexes.add(table.getColumnModel().getColumn(i).getModelIndex());
						table.getColumnModel().removeColumn(table.getColumnModel().getColumn(i));
					}
					for (int i = 0; i < table.getColumnCount(); i++) {
						TableColumn col = table.getColumnModel().getColumn(i);
						int removed = removedIndexes.indexFor(col.getModelIndex());
						if (removed > 0)
							col.setModelIndex(col.getModelIndex() - removed);
					}
				}

				@Override
				public void contentsChanged(ListDataEvent e) {
					for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
						hookUpColumn(table, table.getColumnModel().getColumn(i),
							(CategoryRenderStrategy<R, Object>) theColumnModel.getElementAt(i), ctx, ml::getHoveredRow,
							ml::getHoveredColumn);
						if (table.getRowCount() > 0) {
							Rectangle bounds = table.getCellRect(0, table.convertColumnIndexToModel(i), false);
							table.repaint(bounds.x, 0, bounds.width, table.getHeight());
						}
					}
				}
			};
			getColumnModel().addListDataListener(columnListener);
			subs.add(() -> {
				getColumnModel().removeListDataListener(columnListener);
			});
			table.addMouseListener(ml);
			table.addMouseMotionListener(ml);
			if (table.getTableHeader() != null) {
				table.getTableHeader().addMouseListener(ml);
				table.getTableHeader().addMouseMotionListener(ml);
			}
			table.addHierarchyListener(ml);
			subs.add(() -> {
				table.removeMouseListener(ml);
				table.removeMouseMotionListener(ml);
				table.removeHierarchyListener(ml);
				if (table.getTableHeader() != null) {
					table.getTableHeader().removeMouseListener(ml);
					table.getTableHeader().removeMouseMotionListener(ml);
				}
			});
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
					CategoryRenderStrategy<? super R, C> category = (CategoryRenderStrategy<? super R, C>) getColumn(column);
					if (category.getKeyListener() == null)
						return null;

					R rowValue = getRow(row, table);
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
					boolean rowHovered = ml.getHoveredRow() == row;
					boolean cellHovered = rowHovered && ml.getHoveredColumn() == column;
					ModelCell<R, C> cell = new ModelCell.Default<>(() -> rowValue, colValue, row, column, selected, selected,
						rowHovered, cellHovered, isExpanded(row, table), isLeaf(row, () -> rowValue));
					return new KeyTypeStruct<>(cell, category.getKeyListener());
				}
			};
			table.addKeyListener(tableKL);
			subs.add(() -> table.removeKeyListener(tableKL));
		}
		return TableHookup.of(//
			Subscription.forAll(subs.toArray(new Subscription[subs.size()])), //
			ml::getHoveredRow, ml::getHoveredColumn);
	}

	protected <C> void hookUpColumn(JTable table, TableColumn tblColumn, CategoryRenderStrategy<R, C> column, TableRenderContext ctx,
		IntSupplier hoveredRow, IntSupplier hoveredColumn) {
		tblColumn.setHeaderValue(column.getName());
		if (column.getIdentifier() != null)
			tblColumn.setIdentifier(column.getIdentifier());
		else
			tblColumn.setIdentifier(column);
		tblColumn.setCellRenderer(new ObservableTableCellRenderer<>(this, table, column, ctx, hoveredRow, hoveredColumn));
		if (column.getMutator().getEditor() != null)
			tblColumn.setCellEditor(column.getMutator().getEditor()
				.withCellTooltip(column.getTooltipFn())//
				.withHovering(hoveredRow, hoveredColumn));
		// This is done by the advanced column layout ability in AbstractSimpleTableBuilder
		// if (column.getMinWidth() >= 0)
		// tblColumn.setMinWidth(column.getMinWidth());
		// if (column.getPrefWidth() >= 0)
		// tblColumn.setPreferredWidth(column.getPrefWidth());
		// if (column.getMaxWidth() >= 0)
		// tblColumn.setMaxWidth(column.getMaxWidth());
		tblColumn.setResizable(column.isResizable());
		// TODO Add other column stuff
	}

	private static class ObservableTableCellRenderer<R, C> implements TableCellRenderer, TreeCellRenderer {
		private final AbstractObservableTableModel<R> theModel;
		private final JTable theTable;
		private final CategoryRenderStrategy<R, C> theColumn;
		private final TableRenderContext theContext;
		private ComponentDecorator theDecorator;
		private Runnable theRevert;
		private IntSupplier theHoveredRow;
		private IntSupplier theHoveredColumn;

		ObservableTableCellRenderer(AbstractObservableTableModel<R> model, JTable table, CategoryRenderStrategy<R, C> column,
			TableRenderContext ctx,
			IntSupplier hoveredRow, IntSupplier hoveredColumn) {
			theModel = model;
			theTable = table;
			theColumn = column;
			theContext = ctx;
			theHoveredRow = hoveredRow;
			theHoveredColumn = hoveredColumn;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row,
			int column) {
			int modelRow = table.convertRowIndexToModel(row);
			int modelColumn = table.convertColumnIndexToModel(column);
			return getCellRendererComponent(table, theModel.getRow(row, table), //
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
			ObservableCellRenderer<R, C> renderer = theColumn.getRenderer() != null ? (ObservableCellRenderer<R, C>) theColumn.getRenderer()
				: new ObservableCellRenderer.DefaultObservableCellRenderer<>((r, c) -> String.valueOf(c));
			boolean rowHovered = theHoveredRow.getAsInt() == row;
			boolean cellHovered = rowHovered && theHoveredColumn.getAsInt() == column;
			Supplier<R> rowValue = new Supplier<R>() {
				private R theCachedValue;
				private boolean isCached;

				@Override
				public R get() {
					if (!isCached)
						theCachedValue = theModel.getRow(modelRow, theTable);
					return theCachedValue;
				}
			};
			ModelCell<R, C> cell = new ModelCell.Default<>(rowValue, (C) value, //
				row, column, isSelected, hasFocus, rowHovered, cellHovered, expanded, leaf);
			Component c = renderer.getCellRendererComponent(component, cell,
				() -> theContext == null ? null : theContext.getEmphaticRegions(modelRow, modelColumn));

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

	public interface RowMouseListener<R> {
		void mouseClicked(ModelRow<? extends R> row, MouseEvent e);

		void mousePressed(ModelRow<? extends R> row, MouseEvent e);

		void mouseReleased(ModelRow<? extends R> row, MouseEvent e);

		void mouseEntered(ModelRow<? extends R> row, MouseEvent e);

		void mouseExited(ModelRow<? extends R> row, MouseEvent e);

		void mouseMoved(ModelRow<? extends R> row, MouseEvent e);
	}

	public static abstract class RowMouseAdapter<R> implements RowMouseListener<R> {
		@Override
		public void mouseClicked(ModelRow<? extends R> row, MouseEvent e) {
		}

		@Override
		public void mousePressed(ModelRow<? extends R> row, MouseEvent e) {
		}

		@Override
		public void mouseReleased(ModelRow<? extends R> row, MouseEvent e) {
		}

		@Override
		public void mouseEntered(ModelRow<? extends R> row, MouseEvent e) {
		}

		@Override
		public void mouseExited(ModelRow<? extends R> row, MouseEvent e) {
		}

		@Override
		public void mouseMoved(ModelRow<? extends R> row, MouseEvent e) {
		}
	}

	/** Supports external suggestions to table cell renderers */
	public interface TableRenderContext {
		/**
		 * @param row The model row index being rendered
		 * @param column The model column index being rendered
		 * @return The start/end regions that should be emphasized in the rendered text
		 */
		SortedMatchSet getEmphaticRegions(int row, int column);
	}

	/**
	 * A mouse listener on a table that handles column- and row-specific listeners and tooltips
	 *
	 * @param <R> The row type of the table
	 */
	public static abstract class TableMouseListener<R> extends MouseAdapter implements HierarchyListener {
		class MouseClickStruct<C> {
			private final ModelRow<R> theRow;
			private final ModelCell<R, C> theCell;
			private final CategoryRenderStrategy<R, C> theCategory;

			MouseClickStruct(ModelRow<R> row, ModelCell<R, C> cell, CategoryRenderStrategy<R, C> category) {
				theRow = row;
				theCell = cell;
				theCategory = category;
			}

			MouseClickStruct<C> clicked(MouseEvent e, MouseClickStruct<?> previous) {
				fireRowEvent(e, RowMouseListener::mouseClicked);
				fireCellEvent(e, CategoryMouseListener::mouseClicked);
				return this;
			}

			MouseClickStruct<C> pressed(MouseEvent e, MouseClickStruct<?> previous) {
				fireRowEvent(e, RowMouseListener::mousePressed);
				fireCellEvent(e, CategoryMouseListener::mousePressed);
				return this;
			}

			MouseClickStruct<C> released(MouseEvent e, MouseClickStruct<?> previous) {
				fireRowEvent(e, RowMouseListener::mouseReleased);
				fireCellEvent(e, CategoryMouseListener::mouseReleased);
				return this;
			}

			MouseClickStruct<C> entered(MouseEvent e, MouseClickStruct<?> previous) {
				checkToolTip(theRow, theCell, theCategory);
				fireRowEvent(e, RowMouseListener::mouseEntered);
				fireCellEvent(e, CategoryMouseListener::mouseEntered);
				return this;
			}

			private void checkToolTip(ModelRow<R> row, ModelCell<R, C> cell, CategoryRenderStrategy<R, C> category) {
				if (row != null && category != null)
					setToolTip(category.getTooltip(cell), false);
				else if (category != null) // Column header
					setToolTip(category.getHeaderTooltip(), true);
			}

			MouseClickStruct<C> exited(MouseEvent e, MouseClickStruct<?> previous) {
				fireRowEvent(e, RowMouseListener::mouseExited);
				fireCellEvent(e, CategoryMouseListener::mouseExited);
				return this;
			}

			MouseClickStruct<C> moved(MouseEvent e, MouseClickStruct<?> previous) {
				boolean checkToolTip;
				if (previous != null && previous.theRow != null) {
					if (theRow != null && theRow.getModelValue() == previous.theRow.getModelValue()) {
						fireRowEvent(e, RowMouseListener::mouseMoved);
						if (previous.theCategory != null && previous.theCategory == theCategory) {
							checkToolTip = false;
							fireCellEvent(e, CategoryMouseListener::mouseMoved);
						} else {
							checkToolTip = true;
							previous.exitCell(e);
							fireCellEvent(e, CategoryMouseListener::mouseEntered);
						}
					} else {
						checkToolTip = true;
						previous.exitRow(e);
						fireRowEvent(e, RowMouseListener::mouseEntered);
						fireCellEvent(e, CategoryMouseListener::mouseEntered);
					}
				} else {
					checkToolTip = true;
					fireRowEvent(e, RowMouseListener::mouseEntered);
					fireCellEvent(e, CategoryMouseListener::mouseEntered);
				}
				if (checkToolTip)
					checkToolTip(theRow, theCell, theCategory);
				return this;
			}

			void exitCell(MouseEvent e) {
				fireCellEvent(e, CategoryMouseListener::mouseExited);
			}

			void exitRow(MouseEvent e) {
				exitCell(e);
				fireRowEvent(e, RowMouseListener::mouseExited);
			}

			private void fireRowEvent(MouseEvent e, TriConsumer<RowMouseListener<? super R>, ModelRow<R>, MouseEvent> call) {
				if (theRow == null || getRowListeners().isEmpty())
					return;
				Point offset = getCellOffset(theCell.getRowIndex(), theCell.getColumnIndex());
				if (offset == null)
					return;
				e.translatePoint(0, -offset.y);
				try {
					getRowListeners().forEach(listener -> call.accept(listener, theRow, e));
				} finally {
					e.translatePoint(0, offset.y);
				}
			}

			private void fireCellEvent(MouseEvent e,
				TriConsumer<CategoryMouseListener<? super R, ? super C>, ModelCell<R, C>, MouseEvent> call) {
				if (theCategory == null || theCategory.getMouseListener() == null)
					return;
				Point offset;
				if (theCell != null)
					offset = getCellOffset(theCell.getRowIndex(), theCell.getColumnIndex());
				else
					offset = e.getPoint();
				if (offset == null)
					return;
				e.translatePoint(-offset.x, -offset.y);
				try {
					call.accept(theCategory.getMouseListener(), theCell, e);
				} finally {
					e.translatePoint(offset.x, offset.y);
				}
			}
		}

		private final Component theComponent;
		private MouseClickStruct<?> thePrevious;
		private int theHoveredRow = -1;
		private int theHoveredColumn = -1;

		protected TableMouseListener(Component component) {
			theComponent = component;
		}

		protected abstract ListenerList<RowMouseListener<? super R>> getRowListeners();

		protected abstract int getModelRow(MouseEvent evt);

		protected abstract Point getCellOffset(int row, int column);

		protected abstract int getModelColumn(MouseEvent evt);

		protected abstract R getRowValue(int rowIndex);

		protected abstract CategoryRenderStrategy<R, ?> getColumn(int columnIndex);

		protected abstract boolean isRowSelected(int rowIndex);

		protected abstract boolean isCellSelected(int rowIndex, int columnIndex);

		protected abstract <C> void setToolTip(String tooltip, boolean header);

		protected abstract void hoverChanged(int preHoveredRow, int preHoveredColumn, int newHoveredRow, int newHoveredColumn);

		public int getHoveredRow() {
			return theHoveredRow;
		}

		public int getHoveredColumn() {
			return theHoveredColumn;
		}

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
			setHover(-1, -1);
		}

		@Override
		public void mouseMoved(MouseEvent e) {
			thePrevious = getValue(e, false).moved(e, thePrevious);
		}

		@Override
		public void hierarchyChanged(HierarchyEvent e) {
			if (!theComponent.isShowing())
				setHover(-1, -1);
		}

		private void setHover(int row, int column) {
			int preR = theHoveredRow, preC = theHoveredColumn;
			boolean changed = preR != row || preC != column;
			theHoveredRow = row;
			theHoveredColumn = column;
			if (changed)
				hoverChanged(preR, preC, row, column);
		}

		private <C> MouseClickStruct<C> getValue(MouseEvent evt, boolean movement) {
			int row;
			if (evt.getComponent() instanceof JTableHeader)
				row = -1;
			else
				row = getModelRow(evt);
			int column = getModelColumn(evt);
			if (row != theHoveredRow || column != theHoveredColumn)
				setHover(row, column);
			if (row < 0) {
				CategoryRenderStrategy<R, C> category = column < 0 ? null : (CategoryRenderStrategy<R, C>) getColumn(column);
				return new MouseClickStruct<>(null, null, category);
			}
			R rowValue = getRowValue(row);
			if (column < 0) {
				if (getRowListeners().isEmpty())
					return new MouseClickStruct<>(null, null, null);
				boolean selected = isRowSelected(row);
				return new MouseClickStruct<>(new ModelRow.Default<>(() -> rowValue, row, selected, selected, true, true, true), null,
					null);
			}
			CategoryRenderStrategy<R, C> category = (CategoryRenderStrategy<R, C>) getColumn(column);
			if (movement && !category.getMouseListener().isMovementListener()) {
				if (getRowListeners().isEmpty())
					return new MouseClickStruct<>(null, null, null);
				boolean selected = isRowSelected(row);
				return new MouseClickStruct<>(new ModelRow.Default<>(() -> rowValue, row, selected, selected, true, true, true), null,
					null);
			}

			C colValue = category.getCategoryValue(rowValue);
			boolean enabled;
			if (category.getMutator().getEditability() != null) {
				enabled = category.getMutator().isEditable(rowValue, colValue);
			} else {
				enabled = true;
			}
			if (!enabled) {
				if (getRowListeners().isEmpty())
					return new MouseClickStruct<>(null, null, null);
				boolean selected = isRowSelected(row);
				return new MouseClickStruct<>(new ModelRow.Default<>(() -> rowValue, row, selected, selected, true, true, true), null,
					null);
			}
			boolean selected = isCellSelected(row, column);
			ModelCell<R, C> cell = new ModelCell.Default<>(() -> rowValue, colValue, row, column, selected, selected, true, true, true,
				true);
			return new MouseClickStruct<>(cell, cell, category);
		}
	}
}
