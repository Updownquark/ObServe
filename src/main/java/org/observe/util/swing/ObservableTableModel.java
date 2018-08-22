package org.observe.util.swing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

/**
 * A simple swing table model backed by an observable list
 *
 * @param <T> The type of the backing row data
 */
public abstract class ObservableTableModel<T> implements TableModel {
	private final ObservableCollection<T> theRows;
	private final String[] theColNames;
	private final Function<? super T, ?>[] theColumns;

	private final List<TableModelListener> theListeners;

	/**
	 * @param rows The backing row data
	 * @param colNames The names for the columns
	 * @param columns The functions that provide cell data for each row
	 */
	public ObservableTableModel(ObservableCollection<T> rows, String[] colNames, Function<? super T, ?>... columns) {
		if (colNames.length != columns.length) {
			throw new IllegalArgumentException("Column names and columns do not have the same lengths ("+colNames.length+" and "+columns.length+")");
		}
		theRows = rows;
		theColNames = colNames;
		theColumns = columns;
		theListeners = new ArrayList<>();

		theRows.changes().act(event -> {
			int[] indexes = event.getIndexes();
			switch (event.type) {
			case add:
				added(indexes);
				break;
			case remove:
				removed(indexes);
				break;
			case set:
				boolean justChanges = true;
				for (int i = 0; i < indexes.length && justChanges; i++) {
					justChanges &= event.elements.get(i).oldValue == event.elements.get(i).newValue;
				}
				if (justChanges) {
					changed(indexes);
				} else {
					for (int i = 0; i < indexes.length; i++) {
						removed(new int[] { indexes[i] });
						added(new int[] { indexes[i] });
					}
				}
				break;
			}
		});
	}

	@Override
	public int getRowCount() {
		return theRows.size();
	}

	@Override
	public int getColumnCount() {
		return theColumns.length;
	}

	@Override
	public String getColumnName(int columnIndex) {
		return theColNames[columnIndex];
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return TypeTokens.getRawType(TypeToken.of(theColumns[columnIndex].getClass()).resolveType(Function.class.getTypeParameters()[1]));
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		return theColumns[columnIndex].apply(theRows.get(rowIndex));
	}

	@Override
	public void addTableModelListener(TableModelListener l) {
		theListeners.add(l);
	}

	@Override
	public void removeTableModelListener(TableModelListener l) {
		theListeners.remove(l);
	}

	private void added(int[] indexes) {
		// Swing may expect indexes to be in ascending order
		for(int [] interval : ObservableSwingUtils.getContinuousIntervals(indexes, true)) {
			TableModelEvent evt = new TableModelEvent(this, interval[0], interval[1], TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT);
			for (TableModelListener listener : theListeners) {
				listener.tableChanged(evt);
			}
		}
	}

	private void removed(int[] indexes) {
		// Swing may expect indexes to be in ascending order
		for(int [] interval : ObservableSwingUtils.getContinuousIntervals(indexes, false)) {
			TableModelEvent evt = new TableModelEvent(this, interval[0], interval[1], TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE);
			for (TableModelListener listener : theListeners) {
				listener.tableChanged(evt);
			}
		}
	}

	private void changed(int[] indexes) {
		// Swing may expect indexes to be in ascending order
		for(int [] interval : ObservableSwingUtils.getContinuousIntervals(indexes, true)) {
			TableModelEvent evt = new TableModelEvent(this, interval[0], interval[1], TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE);
			for (TableModelListener listener : theListeners) {
				listener.tableChanged(evt);
			}
		}
	}

	/**
	 * Instructs all table model listeners to completely reload this table model. Useful if much of the table structure has changed in a way
	 * not communicated by the backing rows list
	 */
	protected void changed() {
		TableModelEvent evt = new TableModelEvent(this);
		for (TableModelListener listener : theListeners) {
			listener.tableChanged(evt);
		}
	}
}
