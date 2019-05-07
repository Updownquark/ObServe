package org.observe.util.swing;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import javax.swing.JTable;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy.CategoryMouseListener;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.MutableCollectionElement;

import com.google.common.reflect.TypeToken;

/**
 * A simple swing table model backed by an observable list
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
	private final ListDataListener theColumnModelListener;

	private final List<TableModelListener> theListeners;

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
		theRows = rows;
		theColumns = columns;

		theRowModel = new ObservableListModel<>(rows);
		theColumnModel = new ObservableListModel<>(columns);
		theListeners = new ArrayList<>();

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
		theColumnModelListener = new ListDataListener() {
			@Override
			public void intervalAdded(ListDataEvent e) {
				fireColumnChange();
			}

			@Override
			public void intervalRemoved(ListDataEvent e) {
				fireColumnChange();
			}

			@Override
			public void contentsChanged(ListDataEvent e) {
				fireColumnChange();
			}
		};
	}

	/** @return This table model's rows */
	public ObservableCollection<R> getRows() {
		return theRows;
	}

	/** @return This table model's columns */
	public ObservableCollection<? extends CategoryRenderStrategy<? super R, ?>> getColumns() {
		return theColumns;
	}

	@Override
	public int getRowCount() {
		return theRowModel.getSize();
	}

	public R getRow(int index) {
		return theRowModel.getElementAt(index);
	}

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
				theColumnModel.addListDataListener(theColumnModelListener);
			}
		});
	}

	@Override
	public void removeTableModelListener(TableModelListener l) {
		ObservableSwingUtils.onEQ(() -> {
			if (theListeners.remove(l) && theListeners.isEmpty()) {
				theRowModel.removeListDataListener(theRowModelListener);
				theColumnModel.removeListDataListener(theColumnModelListener);
			}
		});
	}

	void fireRowChange(int index0, int index1, int eventType) {
		TableModelEvent tableEvt = new TableModelEvent(this, index0, index1, TableModelEvent.ALL_COLUMNS, eventType);
		for (TableModelListener listener : theListeners) {
			listener.tableChanged(tableEvt);
		}
	}

	void fireColumnChange() {
		TableModelEvent tableEvt = new TableModelEvent(ObservableTableModel.this, TableModelEvent.HEADER_ROW);
		for (TableModelListener listener : theListeners) {
			listener.tableChanged(tableEvt);
		}
	}

	public static <R> Subscription hookUp(JTable table, ObservableTableModel<R> model) {
		LinkedList<Subscription> subs = new LinkedList<>();
		try (Transaction rowT = model.getRows().lock(false, null); Transaction colT = model.getColumns().lock(false, null)) {
			for (int c = 0; c < model.getColumnCount(); c++) {
				CategoryRenderStrategy<? super R, ?> column = model.getColumn(c);
				TableColumn tblColumn = table.getColumnModel().getColumn(c);
				tblColumn.setIdentifier(column);
				tblColumn.setCellEditor(column.getMutator().getEditor()//
					.withValueTooltip((row, col) -> ((CategoryRenderStrategy<R, Object>) column).getTooltip((R) row, col)));
				// TODO Add other column stuff, esp. renderer
			}
			MouseListener ml = new MouseListener() {
				class MouseClickStruct {
					final CollectionElement<R> row;
					final Object column;
					final CategoryMouseListener<? super R, Object> listener;

					MouseClickStruct(CollectionElement<R> row, Object column, CategoryMouseListener<? super R, Object> listener) {
						this.row = row;
						this.column = column;
						this.listener = listener;
					}
				}

				@Override
				public void mouseClicked(MouseEvent e) {
					MouseClickStruct value = getValue(e, false);
					if (value != null)
						value.listener.mouseClicked(value.row, value.column, e);
				}

				@Override
				public void mousePressed(MouseEvent e) {
					MouseClickStruct value = getValue(e, false);
					if (value != null)
						value.listener.mousePressed(value.row, value.column, e);
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					MouseClickStruct value = getValue(e, false);
					if (value != null)
						value.listener.mouseReleased(value.row, value.column, e);
				}

				@Override
				public void mouseEntered(MouseEvent e) {
					MouseClickStruct value = getValue(e, true);
					if (value != null)
						value.listener.mouseEntered(value.row, value.column, e);
				}

				@Override
				public void mouseExited(MouseEvent e) {
					MouseClickStruct value = getValue(e, true);
					if (value != null)
						value.listener.mouseExited(value.row, value.column, e);
				}

				private MouseClickStruct getValue(MouseEvent evt, boolean movement) {
					int row = table.rowAtPoint(evt.getPoint());
					if (row < 0) {
						return null;
					}
					int column = table.columnAtPoint(evt.getPoint());
					if (column < 0) {
						return null;
					}
					row = table.convertRowIndexToModel(row);
					column = table.convertColumnIndexToModel(column);
					CategoryRenderStrategy<? super R, Object> category = (CategoryRenderStrategy<? super R, Object>) model
						.getColumn(column);
					if (category.getMouseListener() == null) {
						return null;
					} else if (movement && category.getMouseListener() instanceof CategoryRenderStrategy.CategoryClickAdapter)
						return null;

					CollectionElement<R> rowElement = model.getRows().getElement(row);
					Object colValue = category.getCategoryValue(rowElement.get());
					boolean enabled;
					if (category.getMutator().getEditability() != null) {
						enabled = category.getMutator().isEditable(rowElement.get(), colValue);
					} else {
						enabled = true;
					}
					if (!enabled) {
						return null;
					}
					return new MouseClickStruct(rowElement, colValue, category.getMouseListener());
				}
			};
			table.addMouseListener(ml);
			subs.add(() -> table.removeMouseListener(ml));
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
			MouseMotionListener headerMML = new MouseAdapter() {
				@Override
				public void mouseMoved(MouseEvent evt) {
					int column = table.columnAtPoint(evt.getPoint());
					if (column < 0) {
						table.getTableHeader().setToolTipText(null);
						return;
					}
					CategoryRenderStrategy<? super R, Object> category = (CategoryRenderStrategy<? super R, Object>) model
						.getColumn(column);
					table.getTableHeader().setToolTipText(category.getHeaderTooltip());
				}
			};
			table.getTableHeader().addMouseMotionListener(headerMML);
			subs.add(() -> table.getTableHeader().removeMouseMotionListener(headerMML));
		}
		return Subscription.forAll(subs.toArray(new Subscription[subs.size()]));
	}
}
