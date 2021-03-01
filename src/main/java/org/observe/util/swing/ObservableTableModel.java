package org.observe.util.swing;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import javax.swing.JTable;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy.CategoryKeyListener;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;

import com.google.common.reflect.TypeToken;

/**
 * A simple swing table model backed by an observable collection of {@link #getRows() row values} and an observable collection of
 * {@link #getColumns() column definitions}
 *
 * @param <R> The type of the backing row data
 */
public class ObservableTableModel<R> implements TableModel {
	private final ObservableCollection<R> theRows;
	private final ObservableCollection<? extends CategoryRenderStrategy<? super R, ?>> theColumns;

	// No aspect of a table model may only change except the EDT. We'll delegate to ObservableListModel's logic to handle this safely.
	private final ObservableListModel<R> theRowModel;
	private final ObservableListModel<? extends CategoryRenderStrategy<? super R, ?>> theColumnModel;
	private final ListDataListener theRowModelListener;

	private final List<TableModelListener> theListeners;

	private final ListenerList<RowMouseListener<? super R>> theRowMouseListeners;
	/**
	 * @param rows The backing row data
	 * @param colNames The names for the columns
	 * @param columns The functions that provide cell data for each row
	 */
	public ObservableTableModel(ObservableCollection<R> rows, String[] colNames, Function<? super R, ?>... columns) {
		this(rows, makeColumns(colNames, columns));
	}

	private static <R> ObservableCollection<? extends CategoryRenderStrategy<? super R, ?>> makeColumns(String[] colNames,
		Function<? super R, ?>[] columnAccessors) {
		if (colNames.length != columnAccessors.length) {
			throw new IllegalArgumentException(
				"Column names and columns do not have the same lengths (" + colNames.length + " and " + columnAccessors.length + ")");
		}
		CategoryRenderStrategy<? super R, ?>[] columns = new CategoryRenderStrategy[colNames.length];
		for (int i = 0; i < columns.length; i++) {
			columns[i] = new CategoryRenderStrategy<>(colNames[i], (TypeToken<Object>) detectColumnClass(columnAccessors[i]),
				columnAccessors[i]);
		}
		return ObservableCollection.of(new TypeToken<CategoryRenderStrategy<? super R, ?>>() {}, columns);
	}

	private static TypeToken<?> detectColumnClass(Function<?, ?> accessor) {
		// Note that this doesn't work on lambdas, or classes that use a type argument for the function result type
		// So this will return Object a lot
		return TypeToken.of(accessor.getClass()).resolveType(Function.class.getTypeParameters()[1]);
	}

	/**
	 * @param rows The row values for the table
	 * @param columns The columns for the table
	 */
	public ObservableTableModel(ObservableCollection<R> rows,
		ObservableCollection<? extends CategoryRenderStrategy<? super R, ?>> columns) {
		this(rows, false, columns, false);
	}

	/**
	 * @param rows The row values for the table
	 * @param rowsSafe Whether the row collection will fire only on the EDT
	 * @param columns The columns for the table
	 * @param columnsSafe Whether the column collection will fire only on the EDT
	 */
	public ObservableTableModel(ObservableCollection<R> rows, boolean rowsSafe, //
		ObservableCollection<? extends CategoryRenderStrategy<? super R, ?>> columns, boolean columnsSafe) {
		theRows = rows;
		theColumns = columns;

		theRowModel = new ObservableListModel<>(rows, rowsSafe);
		theColumnModel = new ObservableListModel<>(columns, columnsSafe);
		theListeners = new ArrayList<>();

		theRowMouseListeners = ListenerList.build().build();
		theRowModelListener = new ListDataListener() {
			@Override
			public void intervalAdded(ListDataEvent e) {
				fireRowChange(e.getIndex0(), e.getIndex1(), TableModelEvent.INSERT);
			}

			@Override
			public void intervalRemoved(ListDataEvent e) {
				fireRowChange(e.getIndex0(), e.getIndex1(), TableModelEvent.DELETE);
			}

			@Override
			public void contentsChanged(ListDataEvent e) {
				fireRowChange(e.getIndex0(), e.getIndex1(), TableModelEvent.UPDATE);
			}
		};
	}

	/** @return This table model's rows */
	public ObservableCollection<R> getRows() {
		return theRows;
	}

	/** @return This table model's rows, in {@link ListModel} form */
	public ObservableListModel<R> getRowModel() {
		return theRowModel;
	}

	/** @return This table model's columns */
	public ObservableCollection<? extends CategoryRenderStrategy<? super R, ?>> getColumns() {
		return theColumns;
	}

	/** @return This table model's columns, in {@link ListModel} form */
	public ObservableListModel<? extends CategoryRenderStrategy<? super R, ?>> getColumnModel() {
		return theColumnModel;
	}

	@Override
	public int getRowCount() {
		return theRowModel.getSize();
	}

	/**
	 * @param index The index of the row to get
	 * @return The row value at the given index
	 */
	public R getRow(int index) {
		return theRowModel.getElementAt(index);
	}

	/**
	 * @param index The index of the row to get
	 * @return The column definition at the given index
	 */
	public CategoryRenderStrategy<? super R, ?> getColumn(int index) {
		return theColumnModel.getElementAt(index);
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
	public Class<?> getColumnClass(int columnIndex) {
		return TypeTokens.getRawType(theColumnModel.getElementAt(columnIndex).getType());
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		return theColumnModel.getElementAt(columnIndex).getCategoryValue(theRowModel.getElementAt(rowIndex));
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		CategoryRenderStrategy<? super R, Object> column = (CategoryRenderStrategy<? super R, Object>) theColumnModel
			.getElementAt(columnIndex);
		R rowValue = theRowModel.getElementAt(rowIndex);
		return column.getMutator().isEditable(rowValue, column.getCategoryValue(rowValue));
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		CategoryRenderStrategy<? super R, Object> column = (CategoryRenderStrategy<? super R, Object>) theColumnModel
			.getElementAt(columnIndex);
		try (Transaction t = theRows.lock(true, null)) {
			if (theRowModel.getPendingUpdates() > 0) {
				// The editing must happen on an element
				// If row update events are pending, then the row at the given rowIndex may not be the element intended
				// Therefore, we need to prevent this update
				// We could just ignore this, but that would be confusing to the user
				// Electing here to throw an exception instead
				throw new IllegalStateException("Update events are pending--cannot edit value");
			}
			MutableCollectionElement<R> rowElement = theRows.mutableElement(theRows.getElement(rowIndex).getElementId());
			column.getMutator().mutate(rowElement, aValue);
		}
	}

	@Override
	public void addTableModelListener(TableModelListener l) {
		ObservableSwingUtils.onEQ(() -> {
			boolean wasEmpty = theListeners.isEmpty();
			theListeners.add(l);
			if (wasEmpty) {
				theRowModel.addListDataListener(theRowModelListener);
			}
		});
	}

	@Override
	public void removeTableModelListener(TableModelListener l) {
		ObservableSwingUtils.onEQ(() -> {
			if (theListeners.remove(l) && theListeners.isEmpty()) {
				theRowModel.removeListDataListener(theRowModelListener);
			}
		});
	}

	/**
	 * @param mouseListener The listener to receive mouse events for each row
	 * @return A Runnable to execute to cease mouse listening
	 */
	public Runnable addMouseListener(RowMouseListener<? super R> mouseListener) {
		return theRowMouseListeners.add(mouseListener, true);
	}

	void fireRowChange(int index0, int index1, int eventType) {
		TableModelEvent tableEvt = new TableModelEvent(this, index0, index1, TableModelEvent.ALL_COLUMNS, eventType);
		for (TableModelListener listener : theListeners) {
			listener.tableChanged(tableEvt);
		}
	}

	/**
	 * @param <R> The row-type of the table model
	 * @param table The JTable to link with the supplementary (more than just model) functionality of the {@link ObservableTableModel}
	 * @param model The {@link ObservableTableModel} to control the table with
	 * @return A subscription which, when {@link Subscription#unsubscribe() unsubscribed}, will stop the non-model control of the model over
	 *         the table
	 */
	public static <R> Subscription hookUp(JTable table, ObservableTableModel<R> model) {
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
	public static <R> Subscription hookUp(JTable table, ObservableTableModel<R> model, TableRenderContext ctx) {
		LinkedList<Subscription> subs = new LinkedList<>();
		try (Transaction rowT = model.getRows().lock(false, null); Transaction colT = model.getColumns().lock(false, null)) {
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
					private final ModelRow<R> theRow;
					private final ModelCell<R, C> cell;
					private final CategoryRenderStrategy<? super R, ? super C> theCategory;

					MouseClickStruct(ModelRow<R> row, ModelCell<R, C> cell, CategoryRenderStrategy<? super R, ? super C> category) {
						theRow=row;
						this.cell = cell;
						theCategory=category;
					}

					MouseClickStruct<C> clicked(MouseEvent e, MouseClickStruct<?> previous) {
						if(theRow!=null)
							model.theRowMouseListeners.forEach(listener -> listener.mouseClicked(theRow, e));
						if(theCategory!=null && theCategory.getMouseListener()!=null)
							theCategory.getMouseListener().mouseClicked(cell, e);
						return this;
					}

					MouseClickStruct<C> pressed(MouseEvent e, MouseClickStruct<?> previous) {
						if(theRow!=null)
							model.theRowMouseListeners.forEach(listener -> listener.mousePressed(theRow, e));
						if(theCategory!=null && theCategory.getMouseListener()!=null)
							theCategory.getMouseListener().mousePressed(cell, e);
						return this;
					}

					MouseClickStruct<C> released(MouseEvent e, MouseClickStruct<?> previous) {
						if(theRow!=null)
							model.theRowMouseListeners.forEach(listener -> listener.mouseReleased(theRow, e));
						if(theCategory!=null && theCategory.getMouseListener()!=null)
							theCategory.getMouseListener().mouseReleased(cell, e);
						return this;
					}

					MouseClickStruct<C> entered(MouseEvent e, MouseClickStruct<?> previous) {
						if(theRow!=null)
							model.theRowMouseListeners.forEach(listener -> listener.mouseEntered(theRow, e));
						if(theCategory!=null && theCategory.getMouseListener()!=null)
							theCategory.getMouseListener().mouseEntered(cell, e);
						return this;
					}

					MouseClickStruct<C> exited(MouseEvent e, MouseClickStruct<?> previous) {
						if(theRow!=null)
							model.theRowMouseListeners.forEach(listener -> listener.mouseExited(theRow, e));
						if(theCategory!=null && theCategory.getMouseListener()!=null)
							theCategory.getMouseListener().mouseExited(cell, e);
						return this;
					}

					MouseClickStruct<C> moved(MouseEvent e, MouseClickStruct<?> previous) {
						if(previous!=null && previous.theRow!=null) {
							if(theRow!=null && theRow.getModelValue()==previous.theRow.getModelValue()) {
								model.theRowMouseListeners.forEach(listener -> listener.mouseMoved(theRow, e));
								if(previous.theCategory!=null && previous.theCategory==theCategory) {
									if(theCategory!=null && theCategory.getMouseListener()!=null)
										theCategory.getMouseListener().mouseMoved(cell, e);
								} else {
									previous.exitCell(e);
									if(theCategory!=null && theCategory.getMouseListener()!=null)
										theCategory.getMouseListener().mouseEntered(cell, e);
								}
							} else {
								previous.exitRow(e);
								if(theCategory!=null && theCategory.getMouseListener()!=null)
									theCategory.getMouseListener().mouseEntered(cell, e);
							}
						} else {
							if(theCategory!=null && theCategory.getMouseListener()!=null)
								theCategory.getMouseListener().mouseEntered(cell, e);
						}
						return this;
					}

					void exitCell(MouseEvent e) {
						if(theCategory!=null && theCategory.getMouseListener()!=null)
							theCategory.getMouseListener().mouseExited(cell, e);
					}

					void exitRow(MouseEvent e) {
						exitCell(e);
						model.theRowMouseListeners.forEach(listener -> listener.mouseExited(theRow, e));
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
					R rowValue = model.getRow(row);
					int column = table.columnAtPoint(evt.getPoint());
					if (column < 0) {
						if (model.theRowMouseListeners.isEmpty())
							return new MouseClickStruct<>(null, null, null);
						boolean selected = table.isRowSelected(row);
						return new MouseClickStruct<>(new ModelRow.Default<>(() -> rowValue, row, selected, selected, true, true), null,
							null);
					}
					column = table.convertColumnIndexToModel(column);
					CategoryRenderStrategy<? super R, C> category = (CategoryRenderStrategy<? super R, C>) model
						.getColumn(column);
					if (movement && category.getMouseListener() instanceof CategoryRenderStrategy.CategoryClickAdapter) {
						if (model.theRowMouseListeners.isEmpty())
							return new MouseClickStruct<>(null, null, null);
						boolean selected = table.isRowSelected(row);
						return new MouseClickStruct<>(new ModelRow.Default<>(() -> rowValue, row, selected, selected, true, true), null,
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
						if (model.theRowMouseListeners.isEmpty())
							return new MouseClickStruct<>(null, null, null);
						boolean selected = table.isRowSelected(row);
						return new MouseClickStruct<>(new ModelRow.Default<>(() -> rowValue, row, selected, selected, true, true), null,
							null);
					}
					boolean selected = table.isCellSelected(row, column);
					ModelCell<R, C> cell = new ModelCell.Default<>(() -> rowValue, colValue, row, column, selected, selected, true, true);
					return new MouseClickStruct<>(cell, cell, category);
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
					R rowValue = model.getRow(row);
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

					R rowValue = model.getRow(row);
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

	private static <R, C> void hookUp(JTable table, TableColumn tblColumn, CategoryRenderStrategy<? super R, ? extends C> column,
		ObservableTableModel<R> model, TableRenderContext ctx) {
		tblColumn.setHeaderValue(column.getName());
		if (column.getIdentifier() != null)
			tblColumn.setIdentifier(column.getIdentifier());
		else
			tblColumn.setIdentifier(column);
		tblColumn.setCellRenderer(new ObservableTableCellRenderer<>(model, (CategoryRenderStrategy<R, C>) column, ctx));
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

	private static class ObservableTableCellRenderer<R, C> implements TableCellRenderer {
		private final ObservableTableModel<R> theModel;
		private final CategoryRenderStrategy<R, C> theColumn;
		private final TableRenderContext theContext;
		private ComponentDecorator theDecorator;

		private Component theLastRender;

		ObservableTableCellRenderer(ObservableTableModel<R> model, CategoryRenderStrategy<R, C> column, TableRenderContext ctx) {
			theModel = model;
			theColumn = column;
			theContext = ctx;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row,
			int column) {
			if (theLastRender != null) {
				theLastRender.setBackground(null);
				theLastRender.setForeground(null);
				theLastRender = null;
			}
			ObservableCellRenderer<R, C> renderer = theColumn.getRenderer() != null ? (ObservableCellRenderer<R, C>) theColumn.getRenderer()
				: new ObservableCellRenderer.DefaultObservableCellRenderer<>((r, c) -> String.valueOf(c));
			int modelRow = table.convertRowIndexToModel(row);
			int modelColumn = table.convertColumnIndexToModel(column);
			ModelCell<R, C> cell = new ModelCell.Default<>(() -> theModel.getRow(modelRow), (C) value, //
				row, column, isSelected, hasFocus, true, true);
			Component c = renderer.getCellRendererComponent(table, cell,
				() -> theContext == null ? null : theContext.getEmphaticRegions(modelRow, modelColumn));
			theLastRender = c;

			if (theColumn.getDecorator() != null) {
				if (theDecorator == null)
					theDecorator = new ComponentDecorator();
				else
					theDecorator.reset();
				theColumn.getDecorator().decorate(cell, theDecorator);
				theDecorator.decorate(c);
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
		int[][] getEmphaticRegions(int row, int column);
	}
}
