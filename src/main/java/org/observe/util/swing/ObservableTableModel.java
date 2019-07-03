package org.observe.util.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import javax.swing.BoxLayout;
import javax.swing.DefaultRowSorter;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListModel;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.SpinnerListModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.SimpleSettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryFilterStrategy.CategoryFilter;
import org.observe.util.swing.CategoryRenderStrategy.CategoryMouseListener;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
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

	/**
	 * @param <R> The row-type of the table model
	 * @param table The JTable to link with the supplementary (more than just model) functionality of the {@link ObservableTableModel}
	 * @param model The {@link ObservableTableModel} to control the table with
	 * @return A subscription which, when {@link Subscription#unsubscribe() unsubscribed}, will stop the non-model control of the model over
	 *         the table
	 */
	public static <R> Subscription hookUp(JTable table, ObservableTableModel<R> model) {
		LinkedList<Subscription> subs = new LinkedList<>();
		try (Transaction rowT = model.getRows().lock(false, null); Transaction colT = model.getColumns().lock(false, null)) {
			if (table.getModel() == model) {
				// The row filter must get model events before the table does
				// so it can do its row- and column-indexed filtering before the row asks for it
				table.setModel(new DefaultTableModel());
			}
			SimpleObservable<Void> until = new SimpleObservable<>(null, false, null, b -> b.unsafe());
			subs.add(() -> until.onNext(null));
			ObservableTableFiltering<R, ObservableTableModel<R>> rowFilter = new ObservableTableFiltering<>(model, until);
			TableRowSorter<ObservableTableModel<R>> rowSorter = new TableRowSorter<>(model);
			rowSorter.setRowFilter(rowFilter);
			table.setModel(model);
			table.setRowSorter(rowSorter);
			boolean[] rowFilterEnabled = new boolean[] { true };
			PropertyChangeListener rowSorterListener = new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					rowFilterEnabled[0] = false;
					System.err.println("Row sorter changed externally on a table with an " + ObservableTableModel.class.getSimpleName()
						+ ". This will disable the table sort and filtering mechanism provided by this API");
					table.removePropertyChangeListener("rowSorter", this);
					// Refresh the columns so they can adjust to not being able to filter anymore
					for (int c = 0; c < model.getColumnCount(); c++) {
						CategoryRenderStrategy<? super R, ?> column = model.getColumn(c);
						TableColumn tblColumn = table.getColumnModel().getColumn(c);
						hookUp(table, tblColumn, c, column, model);
					}
				}
			};
			table.addPropertyChangeListener("rowSorter", rowSorterListener);
			for (int c = 0; c < model.getColumnCount(); c++) {
				CategoryRenderStrategy<? super R, ?> column = model.getColumn(c);
				TableColumn tblColumn = table.getColumnModel().getColumn(c);
				hookUp(table, tblColumn, c, column, model);
			}
			TableColumnModelListener colListener = new TableColumnModelListener() {
				@Override
				public void columnAdded(TableColumnModelEvent e) {
					hookUp(table, table.getColumnModel().getColumn(e.getToIndex()), e.getToIndex(), model.getColumn(e.getToIndex()), model);
				}

				@Override
				public void columnRemoved(TableColumnModelEvent e) {}

				@Override
				public void columnMoved(TableColumnModelEvent e) {}

				@Override
				public void columnSelectionChanged(ListSelectionEvent e) {}

				@Override
				public void columnMarginChanged(ChangeEvent e) {}
			};
			PropertyChangeListener colModelListener = evt -> {
				((TableColumnModel) evt.getOldValue()).removeColumnModelListener(colListener);
				((TableColumnModel) evt.getNewValue()).addColumnModelListener(colListener);
			};
			table.getColumnModel().addColumnModelListener(colListener);
			table.addPropertyChangeListener("columnModel", colModelListener);
			ListDataListener columnListener = new ListDataListener() {
				@Override
				public void intervalRemoved(ListDataEvent e) {}

				@Override
				public void intervalAdded(ListDataEvent e) {}

				@Override
				public void contentsChanged(ListDataEvent e) {
					for (int i = e.getIndex0(); i <= e.getIndex1(); i++)
						hookUp(table, table.getColumnModel().getColumn(i), i, model.theColumnModel.getElementAt(i), model);
				}
			};
			model.getColumnModel().addListDataListener(columnListener);
			subs.add(() -> {
				table.removePropertyChangeListener("rowSorter", rowSorterListener);
				if (table.getRowSorter() instanceof TableRowSorter
					&& ((TableRowSorter<?>) table.getRowSorter()).getRowFilter() == rowFilter)
					((TableRowSorter<?>) table.getRowSorter()).setRowFilter(null);
				table.removePropertyChangeListener("columnModel", colModelListener);
				table.getColumnModel().removeColumnModelListener(colListener);
				model.getColumnModel().removeListDataListener(columnListener);
			});
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
			MouseAdapter headerML = new MouseAdapter() {
				private int theEditingColumn;
				private SettableValue<String> isFilterEnabled;
				private JPopupMenu theFilterPopup;
				private SettableValue<SortOrder> theSortOrder;
				private JPanel theSortPanel;
				private JCheckBox theFilterCheck;
				private JPanel theCustomFilterPanel;

				private final SimpleObservable<Void> theUnsub = new SimpleObservable<>();

				@Override
				public void mousePressed(MouseEvent evt) {
					int column = table.columnAtPoint(evt.getPoint());
					if (column < 0)
						return;
					int modelColumn = table.convertColumnIndexToModel(column);
					CategoryRenderStrategy<? super R, ?> c = model.getColumn(modelColumn);
					if (c.getFilterability() != null)
						showFilterPopup(modelColumn);
					else if (c.getSortability() != null) {
						if (table.getRowSorter() instanceof DefaultRowSorter)
							((DefaultRowSorter<?, ?>) table.getRowSorter()).toggleSortOrder(modelColumn);
					}
				}

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

				private <C> void showFilterPopup(int column) {
					if (!rowFilterEnabled[0])
						return;
					CategoryFilter<C> filter = (CategoryFilter<C>) rowFilter.getFilter(column).getFilter();
					if (filter == null)
						return;
					CategoryRenderStrategy<? super R, C> c = (CategoryRenderStrategy<? super R, C>) model.getColumn(column);
					theEditingColumn = column;
					if (theFilterPopup == null) {
						theFilterPopup = new JPopupMenu();
						theSortPanel = new JPanel();
						theSortOrder = new SimpleSettableValue<>(SortOrder.class, false);
						theSortOrder.set(SortOrder.UNSORTED, null);
						theSortOrder.noInitChanges().act(evt -> {
							setSortOrder(evt.getNewValue());
						});
						theSortPanel.setLayout(new BoxLayout(theSortPanel, BoxLayout.Y_AXIS));
						theSortPanel.add(new JLabel("Sort:"));
						String[] sortOrders = new String[3];
						sortOrders[0] = "Ascending";
						sortOrders[1] = "Unsorted";
						sortOrders[2] = "Descending";
						JSpinner sortSpinner = new JSpinner(new SpinnerListModel(sortOrders));
						((JSpinner.DefaultEditor) sortSpinner.getEditor()).getTextField().setEditable(false);
						ObservableSwingUtils.spinnerFor(sortSpinner, "Sort the table contents by this column's value", //
							theSortOrder.map(sort -> sortOrders[sort.ordinal()], text -> SortOrder.valueOf(text.toUpperCase())));
						sortSpinner.setEditor(table);
						theSortPanel.add(sortSpinner);
						theFilterPopup.add(theSortPanel);
						theFilterCheck = new JCheckBox("Filter Values");
						isFilterEnabled = new SimpleSettableValue<>(String.class, true);
						theFilterCheck.addActionListener(evt -> setFilterEnabled(theFilterCheck.isSelected(), evt));
						theFilterPopup.add(theFilterCheck);
						theCustomFilterPanel = new JPanel(new BorderLayout());
						theFilterPopup.add(theCustomFilterPanel);
						theFilterPopup.addComponentListener(new ComponentAdapter() {
							@Override
							public void componentHidden(ComponentEvent e) {
								theEditingColumn = -1;
								theUnsub.onNext(null);
							}
						});
					}
					theSortOrder.set(getSortOrder(column), null);
					theSortPanel.setVisible(c.getSortability() != null);
					theFilterCheck.setSelected(filter.isFiltered());
					ObservableSet<C> distinctValues = model.getRows().flow().map(c.getType(), row -> c.getCategoryValue(row))//
						.withEquivalence(c.getFilterability().getEquivalence()).distinct().collectActive(theUnsub);
					Component editor = filter.getEditor(distinctValues, isFilterEnabled, theUnsub, () -> {
						rowSorter.sort();
					});
					theCustomFilterPanel.add(editor, BorderLayout.CENTER);
					// TODO resize and relocate the popup
					theFilterPopup.setVisible(true);
				}

				private void setFilterEnabled(boolean enabled, Object cause) {
					int column = theEditingColumn;
					if (column < 0)
						return;
					isFilterEnabled.set(enabled ? null : "Filtering is disabled", cause);
					CategoryFilter<?> filter = rowFilter.getFilter(theEditingColumn).getFilter();
					if (filter != null)
						filter.clearFilters();
				}

				private SortOrder getSortOrder(int column) {
					for (SortKey key : rowSorter.getSortKeys()) {
						if (key.getColumn() == column)
							return key.getSortOrder();
					}
					return SortOrder.UNSORTED;
				}

				private void setSortOrder(SortOrder order) {
					int column = theEditingColumn;
					if (column < 0)
						return;
					LinkedList<SortKey> keys = new LinkedList<>(rowSorter.getSortKeys());
					Iterator<SortKey> keyIter = keys.iterator();
					while (keyIter.hasNext()) {
						SortKey key = keyIter.next();
						if (key.getColumn() == column) {
							if (key.getSortOrder() == order)
								return; // Already sorted as specified
							else {
								keyIter.remove();
								break;
							}
						}
					}
					keys.addFirst(new SortKey(column, order));
					rowSorter.setSortKeys(keys);
				}
			};
			table.getTableHeader().addMouseListener(headerML);
			table.getTableHeader().addMouseMotionListener(headerML);
			subs.add(() -> table.getTableHeader().removeMouseListener(headerML));
			subs.add(() -> table.getTableHeader().removeMouseMotionListener(headerML));
		}
		return Subscription.forAll(subs.toArray(new Subscription[subs.size()]));
	}

	private static <R, C> void hookUp(JTable table, TableColumn tblColumn, int columnIndex, CategoryRenderStrategy<? super R, C> column,
		ObservableTableModel<R> model) {
		if (column.getIdentifier() != null)
			tblColumn.setIdentifier(column.getIdentifier());
		else
			tblColumn.setIdentifier(column);
		if (column.getRenderer() != null)
			tblColumn.setCellRenderer(new ObservableTableCellRenderer<>(model, column.getRenderer()));
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
		if (column.getSortability() != null) {
			if (table.getRowSorter() == null)
				table.setRowSorter(new TableRowSorter<>(model));
			if (table.getRowSorter() instanceof DefaultRowSorter) {
				DefaultRowSorter<?, ?> sorter = (DefaultRowSorter<?, ?>) table.getRowSorter();
				sorter.setMaxSortKeys(model.getColumnCount());
				sorter.setSortsOnUpdates(true);
				sorter.setComparator(columnIndex, column.getSortability());
				sorter.setSortable(columnIndex, true);
			}
		} else if (table.getRowSorter() instanceof DefaultRowSorter) {
			((DefaultRowSorter<?, ?>) table.getRowSorter()).setComparator(columnIndex, null);
			((DefaultRowSorter<?, ?>) table.getRowSorter()).setSortable(columnIndex, false);
		}
		if (column.getSortability() != null && column.getFilterability() != null)
			tblColumn.setHeaderRenderer(new SortableFilterableHeaderRenderer<>(table, columnIndex, column));
		// TODO Add other column stuff
	}

	private static class ObservableTableCellRenderer<R, C> implements TableCellRenderer {
		private final ObservableTableModel<R> theModel;
		private final ObservableCellRenderer<? super R, C> theRenderer;

		ObservableTableCellRenderer(ObservableTableModel<R> model, ObservableCellRenderer<? super R, C> renderer) {
			theModel = model;
			theRenderer = renderer;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row,
			int column) {
			return theRenderer.getCellRendererComponent(table, //
				() -> theModel.getRow(row), (C) value, isSelected, false, true, hasFocus, row, column);
		}
	}
}
