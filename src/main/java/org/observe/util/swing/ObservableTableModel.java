package org.observe.util.swing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.JTable;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import org.observe.collect.ObservableCollection;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement;

import com.google.common.reflect.TypeToken;

/**
 * A simple swing table model backed by an observable collection of {@link #getRows() row values} and an observable collection of
 * {@link #getColumns() column definitions}
 *
 * @param <R> The type of the backing row data
 */
public class ObservableTableModel<R> extends AbstractObservableTableModel<R> implements TableModel {
	private final ObservableCollection<R> theRows;

	// No aspect of a table model may only change except the EDT. We'll delegate to ObservableListModel's logic to handle this safely.
	private final ObservableListModel<R> theRowModel;
	private final ListDataListener theRowModelListener;

	private final List<TableModelListener> theListeners;

	/**
	 * @param rows The backing row data
	 * @param colNames The names for the columns
	 * @param columns The functions that provide cell data for each row
	 */
	public ObservableTableModel(ObservableCollection<R> rows, String[] colNames, Function<? super R, ?>... columns) {
		this(rows, makeColumns(colNames, columns));
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
	 * @param rows The row values for the table
	 * @param columns The columns for the table
	 */
	public ObservableTableModel(ObservableCollection<R> rows,
		ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns) {
		super(columns);
		theRows = rows;

		// try (Transaction t = theAnchor.instantiating()//
		// .watchFor(ObservableListModel.DBUG, "rowModel", tk -> tk.applyTo(1))//
		// .watchFor(ObservableListModel.DBUG, "columnModel", tk -> tk.skip(1))) {
		theRowModel = new ObservableListModel<>(rows);
		// }
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
	}

	/** @return This table model's rows */
	public ObservableCollection<R> getRows() {
		return theRows;
	}

	/** @return This table model's rows, in {@link ListModel} form */
	public ObservableListModel<R> getRowModel() {
		return theRowModel;
	}

	@Override
	public int getRowCount() {
		return theRowModel.getSize();
	}

	@Override
	public R getRow(int index, JTable table) {
		return theRowModel.getElementAt(index);
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		return getColumnModel().getElementAt(columnIndex).getCategoryValue(theRowModel.getElementAt(rowIndex));
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		CategoryRenderStrategy<? super R, Object> column = (CategoryRenderStrategy<? super R, Object>) getColumnModel()
			.getElementAt(columnIndex);
		R rowValue = theRowModel.getElementAt(rowIndex);
		return column.getMutator().isEditable(rowValue, column.getCategoryValue(rowValue));
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		// These index checks aren't normally needed, but I've seen this called with higher row indexes and I'm not sure why
		if (columnIndex >= getColumnModel().getSize())
			return;
		CategoryRenderStrategy<? super R, Object> column = (CategoryRenderStrategy<? super R, Object>) getColumnModel()
			.getElementAt(columnIndex);
		try (Transaction t = theRows.lock(true, null)) {
			if (rowIndex >= theRows.size())
				return;
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

	@Override
	protected Transaction lockRows(boolean write, Object cause) {
		return theRows.lock(write, cause);
	}

	@Override
	protected boolean isExpanded(int rowIndex, JTable table) {
		return false;
	}

	@Override
	protected boolean isLeaf(int rowIndex, Supplier<R> rowValue) {
		return true;
	}

	void fireRowChange(int index0, int index1, int eventType) {
		TableModelEvent tableEvt = new TableModelEvent(this, index0, index1, TableModelEvent.ALL_COLUMNS, eventType);
		for (TableModelListener listener : theListeners) {
			listener.tableChanged(tableEvt);
		}
	}

	@Override
	public TableHookup hookUp(JTable table, TableRenderContext ctx) {
		if (table.getModel() != this)
			table.setModel(this);
		return super.hookUp(table, ctx);
	}
}
