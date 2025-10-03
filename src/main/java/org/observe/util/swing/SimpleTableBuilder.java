package org.observe.util.swing;

import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.CollectionSubscription;
import org.observe.collect.ObservableCollection;
import org.observe.util.ObservableCollectionSynchronization;
import org.observe.util.swing.CategoryRenderStrategy.CategoryClickAdapter;
import org.observe.util.swing.Dragging.SimpleTransferAccepter;
import org.observe.util.swing.Dragging.SimpleTransferSource;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.DataAction;
import org.observe.util.swing.PanelPopulation.TableBuilder;
import org.observe.util.swing.TableContentControl.FilteredValue;
import org.qommons.IntList;
import org.qommons.LambdaUtils;
import org.qommons.StringUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListElement;
import org.qommons.collect.MutableCollectionElement;

class SimpleTableBuilder<R, T extends JTable, P extends SimpleTableBuilder<R, T, P>> extends AbstractSimpleTableBuilder<R, T, P>
implements TableBuilder<R, T, P> {
	static class DynamicColumnSet<R, C> {
		final Function<? super R, ? extends Collection<? extends C>> columnValues;
		final Comparator<? super C> columnSort;
		final Function<? super C, CategoryRenderStrategy<R, ?>> columnCreator;

		public DynamicColumnSet(Function<? super R, ? extends Collection<? extends C>> columnValues, Comparator<? super C> columnSort,
			Function<? super C, CategoryRenderStrategy<R, ?>> columnCreator) {
			this.columnValues = columnValues;
			this.columnSort = columnSort;
			this.columnCreator = columnCreator;
		}
	}

	static class NoLayoutTable extends JTable {
		@Override
		public void doLayout() { // We do this ourselves
		}
	}

	private final ObservableCollection<R> theRows;
	private ObservableCollection<R> theFilteredRows;
	private Function<? super R, String> theNameFunction;
	private final List<DynamicColumnSet<R, ?>> theDynamicColumns;
	private Predicate<? super R> theInitialSelection;
	private ObservableValue<? extends TableContentControl> theFilter;
	private ObservableValue<String> theCountTitleDisplayedText;
	private ObservableCollection<FilteredValue<R>> theFilteredValueRows;
	private ObservableCollection<? extends CategoryRenderStrategy<R, ?>> theDisplayedColumns;
	private boolean isRowsDraggable;
	private Runnable thePostDrag;

	SimpleTableBuilder(ComponentEditor<?, ?> parent, ObservableCollection<R> rows, Observable<?> until) {
		this(parent, rows, (T) new NoLayoutTable(), until);
	}

	SimpleTableBuilder(ComponentEditor<?, ?> parent, ObservableCollection<R> rows, T table, Observable<?> until) {
		super(parent, null, table, until);
		theRows = rows;
		theDynamicColumns = new ArrayList<>();
	}

	protected ObservableCollection<R> getActualRows() {
		return ((ObservableTableModel<R>) getEditor().getModel()).getRows();
	}

	Function<? super R, String> getNameFunction() {
		return theNameFunction;
	}

	@Override
	public ObservableCollection<? extends R> getRows() {
		return theRows;
	}

	@Override
	public P withNameColumn(Function<? super R, String> getName, BiConsumer<? super R, String> setName, boolean unique,
		Consumer<CategoryRenderStrategy<R, String>> column) {
		TableBuilder.super.withNameColumn(getName, setName, unique, column);
		theNameFunction = getName;
		return (P) this;
	}

	@Override
	public P withIndexColumn(String columnName, Consumer<CategoryRenderStrategy<R, Integer>> column) {
		return withColumn(columnName, int.class, __ -> 0, col -> {
			col.withRenderer(ObservableCellRenderer.fromTableRenderer(new DefaultTableCellRenderer() {
				@Override
				public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
					int rowIndex, int columnIndex) {
					super.getTableCellRendererComponent(table, value, isSelected, hasFocus, rowIndex, columnIndex);
					Supplier<R> rowValue = () -> {
						CollectionElement<R> el = theFilteredRows.getElement(rowIndex);
						if (el == null)
							return null; // May have been removed
						ElementId realRow = theRows.getEquivalentElement(el.getElementId());
						if (realRow == null)
							return null; // May have been removed
						return theRows.getElement(realRow).get();
					};
					ModelRow<R> modelRow = new ModelRow.Default<>(rowValue, rowIndex, false, isSelected, hasFocus, false, true);
					setText(col.print(modelRow));
					return this;
				}
			}, (__, c) -> String.valueOf(c)));
			if (column != null)
				column.accept(col);
		});
	}

	@Override
	public <C> P withDynamicColumns(Function<? super R, ? extends Collection<? extends C>> columnValues, Comparator<? super C> columnSort,
		Function<? super C, CategoryRenderStrategy<R, ?>> columnCreator) {
		theDynamicColumns.add(new DynamicColumnSet<>(columnValues, columnSort, columnCreator));
		return (P) this;
	}

	@Override
	public P withInitialSelection(Predicate<? super R> initSelection) {
		theInitialSelection = initSelection;
		return (P) this;
	}

	@Override
	public P withFiltering(ObservableValue<? extends TableContentControl> filter) {
		theFilter = filter;
		return (P) this;
	}

	@Override
	public P withCountTitle(ObservableValue<String> displayedText) {
		theCountTitleDisplayedText = displayedText;
		return (P) this;
	}

	@Override
	public ObservableCollection<R> getFilteredRows() {
		return theFilteredRows;
	}

	@Override
	public List<R> getSelection() {
		if (getEditor().getModel() instanceof ObservableTableModel) {
			return ObservableSwingUtils.getSelection(((ObservableTableModel<R>) getEditor().getModel()).getRowModel(),
				getEditor().getSelectionModel(), null);
		} else { // Model must have not been installed yet
			return Collections.emptyList();
		}
	}

	@Override
	public P withAdd(Supplier<? extends R> creator, Consumer<DataAction<R, ?>> actionMod) {
		return withMultiAction(null, values -> {
			R value = creator.get();
			ListElement<R> el = findElement(value);
			if (el == null) {
				el = theRows.addElement(value, false);
				if (el == null) {
					return; // Maybe a product of inability to add the value
				}
			}
			// Assuming here that the action is only called on the EDT,
			// meaning the above add operation has now been propagated to the list model and the selection model
			// It also means that the row model is sync'd with the collection, so we can use the index from the collection here
			ObservableCollection<R> rows = getActualRows();
			ListElement<R> displayedRow = (ListElement<R>) rows.getElementsBySource(el.getElementId(), theRows).peekFirst();
			if (displayedRow != null) {
				int index = displayedRow.getElementsBefore();
				getEditor().getSelectionModel().setSelectionInterval(index, index);
			}
		}, action -> {
			action.allowForMultiple(true).allowForEmpty(true).allowForAnyEnabled(true)//
			.modifyButton(button -> button.withIcon(PanelPopulationImpl.getAddIcon(16)).withTooltip("Add new " + getItemName()));
			if (actionMod != null)
				actionMod.accept(action);
		});
	}

	private ListElement<R> findElement(R value) {
		ListElement<R> el = theRows.getElement(value, false);
		if (el != null && el.get() != value) {
			ListElement<R> lastMatch = theRows.getElement(value, true);
			if (!lastMatch.getElementId().equals(el.getElementId())) {
				if (lastMatch.get() == value)
					el = lastMatch;
				else {
					while (el.get() != value && !el.getElementId().equals(lastMatch.getElementId()))
						el = el.getAdjacent(true);
				}
				if (el.get() != value)
					el = null;
			}
		}
		return el;
	}

	@Override
	protected Consumer<? super List<? extends R>> defaultDeletion() {
		return values -> {
			for (R value : values)
				theRows.remove(value);
		};
	}

	@Override
	public P withCopy(Function<? super R, ? extends R> copier, Consumer<DataAction<R, ?>> actionMod) {
		return withMultiAction(null, values -> {
			try (Transaction t = theFilteredRows.lock(true, null)) {
				betterCopy(copier);
			}
		}, action -> {
			String single = getItemName();
			String plural = StringUtils.pluralize(single);
			action.allowForMultiple(true).withTooltip(items -> "Duplicate selected " + (items.size() == 1 ? single : plural))//
			.modifyButton(button -> button.withIcon(PanelPopulationImpl.getCopyIcon(16)));
			if (actionMod != null)
				actionMod.accept(action);
		});
	}

	private void betterCopy(Function<? super R, ? extends R> copier) {
		ListSelectionModel selModel = getEditor().getSelectionModel();
		IntList newSelection = new IntList();
		for (int i = selModel.getMinSelectionIndex(); i >= 0 && i <= selModel.getMaxSelectionIndex(); i++) {
			if (!selModel.isSelectedIndex(i))
				continue;
			ListElement<R> toCopy = theRows.getElement(i);
			R copy = copier.apply(toCopy.get());
			CollectionElement<R> copied = findElement(copy);
			if (copied != null) {//
			} else if (theRows.canAdd(copy, toCopy.getElementId(), null) == null)
				copied = theRows.addElement(copy, toCopy.getElementId(), null, true);
			else
				copied = theRows.addElement(copy, false);
			if (copied != null) {
				ObservableCollection<R> rows = getActualRows();
				ListElement<R> rowEl = (ListElement<R>) rows.getElementsBySource(copied.getElementId(), theRows).peekFirst();
				if (rowEl != null)
					newSelection.add(rowEl.getElementsBefore());
			}
		}
		selModel.setValueIsAdjusting(true);
		selModel.clearSelection();
		for (int[] interval : ObservableSwingUtils.getContinuousIntervals(newSelection.toArray(), true))
			selModel.addSelectionInterval(interval[0], interval[1]);
		selModel.setValueIsAdjusting(false);
	}

	@Override
	public P withMove(boolean up, Consumer<DataAction<R, ?>> actionMod) {
		CategoryRenderStrategy<R, Object> moveColumn = new CategoryRenderStrategy<R, Object>(up ? "\u2191" : "\u2193", Object.class,
			v -> null)//
			.withHeaderTooltip("Move row " + (up ? "up" : "down"))//
			.decorateAll(deco -> deco.withIcon(PanelPopulationImpl.getMoveIcon(up, 16)))//
			.withWidths(15, 20, 20)//
			.withMutation(m -> m.mutateAttribute2((r, c) -> c)
				.withEditor(ObservableCellEditor.<R, Object> createButtonCellEditor(__ -> null, cell -> {
					if (up && cell.getRowIndex() == 0)
						return cell.getCellValue();
					else if (!up && cell.getRowIndex() == theFilteredRows.size() - 1)
						return cell.getCellValue();
					try (Transaction t = theFilteredRows.lock(true, null)) {
						CollectionElement<R> row = theRows.getElement(cell.getRowIndex());
							CollectionElement<R> adj = row.getAdjacent(!up);
						if (adj != null) {
								CollectionElement<R> adj2 = adj.getAdjacent(!up);
							theRows.move(row.getElementId(), up ? CollectionElement.getElementId(adj2) : adj.getElementId(),
								up ? adj.getElementId() : CollectionElement.getElementId(adj2), up, null);
							ListSelectionModel selModel = getEditor().getSelectionModel();
							int newIdx = up ? cell.getRowIndex() - 1 : cell.getRowIndex() + 1;
							selModel.addSelectionInterval(newIdx, newIdx);
						}
					}
					return cell.getCellValue();
				}).decorate((cell, deco) -> {
					deco.withIcon(PanelPopulationImpl.getMoveIcon(up, 16));
					if (up)
						deco.enabled(cell.getRowIndex() > 0);
					else
						deco.enabled(cell.getRowIndex() < theFilteredRows.size() - 1);
				})));
		withColumn(moveColumn);
		return (P) this;
	}

	@Override
	public P withMoveToEnd(boolean up, Consumer<DataAction<R, ?>> actionMod) {
		return withMultiAction(null, values -> {
			try (Transaction t = theFilteredRows.lock(true, null)) {
				// Ignore the given values and use the selection model so we get the indexes right in the case of duplicates
				ListSelectionModel selModel = getEditor().getSelectionModel();
				int selectionCount = 0;
				ElementId varBound = null;
				ElementId fixedBound = CollectionElement.getElementId(theRows.getTerminalElement(up));
				int start = up ? selModel.getMinSelectionIndex() : selModel.getMaxSelectionIndex();
				int end = up ? selModel.getMaxSelectionIndex() : selModel.getMinSelectionIndex();
				int inc = up ? 1 : -1;
				for (int i = start; i >= 0 && i != end; i += inc) {
					if (!selModel.isSelectedIndex(i))
						continue;
					selectionCount++;
					CollectionElement<R> move = theRows.getElement(i);
					move = theRows.move(move.getElementId(), up ? varBound : fixedBound, up ? fixedBound : varBound, up, null);
					varBound = move.getElementId();
				}
				if (selectionCount != 0)
					selModel.setSelectionInterval(0, selectionCount - 1);
			}
		}, action -> {
			ObservableValue<String> enabled;
			Supplier<String> enabledGet = () -> {
				try (Transaction t = theFilteredRows.lock(false, null)) {
					ListSelectionModel selModel = getEditor().getSelectionModel();
					if (selModel.isSelectionEmpty())
						return "Nothing selected";
					int start = up ? selModel.getMinSelectionIndex() : selModel.getMaxSelectionIndex();
					int end = up ? selModel.getMaxSelectionIndex() : selModel.getMinSelectionIndex();
					int inc = up ? 1 : -1;
					ElementId varBound = null;
					ElementId fixedBound = CollectionElement.getElementId(theRows.getTerminalElement(up));
					for (int i = start; i >= 0 && i != end; i += inc) {
						if (!selModel.isSelectedIndex(i))
							continue;
						CollectionElement<R> move = theRows.getElement(i);
						String msg = theRows.canMove(move.getElementId(), up ? varBound : fixedBound, up ? fixedBound : varBound);
						if (msg != null)
							return msg;
						varBound = move.getElementId();
					}
					return null;
				}
			};
			if (getSelectionValues() != null)
				enabled = ObservableValue.of(enabledGet, getSelectionValues().simpleChanges());
			else
				enabled = getSelectionValue().map(__ -> enabledGet.get());
			action.allowForMultiple(true)
			.withTooltip(items -> "Move selected row" + (items.size() == 1 ? "" : "s") + " to the " + (up ? "top" : "bottom"))
			.modifyButton(button -> button.withIcon(PanelPopulationImpl.getMoveEndIcon(up, 16)))//
			.allowForEmpty(false).allowForAnyEnabled(false).modifyAction(a -> a.disableWith(enabled));
		});
	}

	@Override
	public P withDraggableRows(boolean draggable, Runnable postDrag) {
		isRowsDraggable = draggable;
		thePostDrag = postDrag;
		return (P) this;
	}

	@Override
	public ObservableValue<String> getTooltip() {
		return null;
	}

	@Override
	protected Component createFieldNameLabel(Observable<?> until) {
		return null;
	}

	@Override
	protected Component createPostLabel(Observable<?> until) {
		return null;
	}

	@Override
	protected ObservableCollection<? extends CategoryRenderStrategy<R, ?>> createColumnSet() {
		ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns;
		if (theDynamicColumns.isEmpty()) {
			columns = getColumns();
		} else {
			List<ObservableCollection<? extends CategoryRenderStrategy<R, ?>>> columnSets = new ArrayList<>();
			columnSets.add(getColumns());
			for (DynamicColumnSet<R, ?> dc : theDynamicColumns) {
				columnSets.add(evaluateDynamicColumns(dc));
			}
			columns = ObservableCollection.flattenCollections(columnSets.toArray(new ObservableCollection[columnSets.size()]))
				.collectActive(getUntil());
		}
		return columns;
	}

	private <D> ObservableCollection<CategoryRenderStrategy<R, ?>> evaluateDynamicColumns(DynamicColumnSet<R, D> dc) {
		ObservableCollection.CollectionDataFlow<R, ?, D> columnValueFlow = theRows.flow()//
			.flatMap(row -> ObservableCollection.of(dc.columnValues.apply(row)).flow());
		if (dc.columnSort != null)
			columnValueFlow = columnValueFlow.distinctSorted(dc.columnSort, false);
		else
			columnValueFlow = columnValueFlow.distinct();
		return columnValueFlow.<CategoryRenderStrategy<R, ?>> transform(tx -> tx//
			.cache(true).reEvalOnUpdate(false).fireIfUnchanged(false)//
			.map(columnValue -> dc.columnCreator.apply(columnValue))).collectActive(getUntil());
	}

	@Override
	protected AbstractObservableTableModel<R> createTableModel(ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns) {
		if (theFilter != null) {
			if (theFilter instanceof SettableValue) {
				Map<CategoryRenderStrategy<R, ?>, Runnable> headerListening = new HashMap<>();
				CollectionSubscription colClickSub = columns.subscribe(evt -> {
					switch (evt.getType()) {
					case add:
						headerListening.put(evt.getNewValue(), evt.getNewValue().addMouseListener(new CategoryClickAdapter<R, Object>() {
							@Override
							public void mouseClicked(ModelCell<? extends R, ? extends Object> cell, MouseEvent e) {
								// Looking for a mouse click on the column header
								if (cell == null && e.getComponent() instanceof JTableHeader)
									handleColumnHeaderClick(theFilter, evt.getNewValue().getName(), e);
							}
						}));
						break;
					case remove:
						headerListening.remove(evt.getOldValue()).run();
						break;
					case set:
						if (evt.getOldValue() != evt.getNewValue()) {
							headerListening.remove(evt.getOldValue()).run();
							headerListening.put(evt.getNewValue(),
								evt.getNewValue().addMouseListener(new CategoryClickAdapter<R, Object>() {
									@Override
									public void mouseClicked(ModelCell<? extends R, ? extends Object> cell, MouseEvent e) {
										// Looking for a mouse click on the column header
										if (cell == null && e.getComponent() instanceof JTableHeader)
											handleColumnHeaderClick(theFilter, evt.getNewValue().getName(), e);
									}
								}));
						}
					}
				}, true);
				getUntil().take(1).act(__ -> colClickSub.unsubscribe(true));
			}

			columns = TableContentControl.applyColumnControl(columns, theFilter, getUntil());
			theDisplayedColumns = columns;
			Observable<?> columnChanges = Observable.or(Observable.constant(null), columns.simpleChanges());
			ObservableCollection<TableContentControl.FilteredValue<R>> rawFiltered = TableContentControl.applyRowControl(theRows,
				() -> theDisplayedColumns, theFilter.refresh(columnChanges), getUntil());
			theFilteredValueRows = rawFiltered.safe(ThreadConstraint.EDT, getUntil());
			theFilteredRows = theFilteredValueRows.flow()
				.<R> transform(tx -> tx.map(LambdaUtils.printableFn(f -> f.value, "value", null)).modifySource(FilteredValue::setValue))
				.collectActive(getUntil());
		} else {
			theFilteredValueRows = null;
			theFilteredRows = theRows.safe(ThreadConstraint.EDT, getUntil());
			theDisplayedColumns = columns;
		}

		return new ObservableTableModel<>(theFilteredRows, columns);
	}

	@Override
	protected AbstractObservableTableModel.TableRenderContext createTableRenderContext() {
		if (theFilteredValueRows == null)
			return null;
		return new AbstractObservableTableModel.TableRenderContext() {
			@Override
			public SortedMatchSet getEmphaticRegions(int row, int column) {
				TableContentControl.FilteredValue<R> fv = theFilteredValueRows.get(row);
				if (column >= fv.getColumns())
					return null;
				return fv.getMatches(column);
			}
		};
	}

	@Override
	protected void syncSelection(T table, AbstractObservableTableModel<R> model, SettableValue<R> selection, boolean enforceSingle) {
		ObservableTableModel<R> tableModel = (ObservableTableModel<R>) model;
		ObservableSwingUtils.syncSelection(table, tableModel.getRowModel(), table::getSelectionModel, tableModel.getRows().equivalence(),
			selection, getUntil(), (index, cause) -> {
				if (index < 0 || index >= getRows().size())
					return;
				MutableCollectionElement<R> el = (MutableCollectionElement<R>) getRows()
					.mutableElement(getRows().getElement(index).getElementId());
				if (el.isAcceptable(el.get()) == null) {
					try (Transaction t = getRows().lock(true, cause)) {
						el.set(el.get());
					}
				}
			});
	}

	@Override
	protected void syncMultiSelection(T table, AbstractObservableTableModel<R> model, ObservableCollection<R> selection) {
		ObservableTableModel<R> tableModel = (ObservableTableModel<R>) model;
		ObservableListSelectionModel<R> selectionModel = new ObservableListSelectionModel<>(tableModel.getRowModel(), theInitialSelection,
			getUntil());
		table.setSelectionModel(selectionModel);
		if (selection != null) {
			Subscription syncSub = ObservableCollectionSynchronization.synchronize(selection, selectionModel).strictOrder().synchronize();
			getUntil().take(1).act0(syncSub::unsubscribe);
		}
		// selectionModel.synchronize(theSelectionValues, getUntil());
		// ObservableSwingUtils.syncSelection(table, model.getRowModel(), table::getSelectionModel, model.getRows().equivalence(),
		// theSelectionValues, getUntil());
	}

	@Override
	protected TransferHandler setUpDnD(T table, SimpleTransferSource<R, ?> dragSource, SimpleTransferAccepter<R, ?> dragAccepter) {
		return new TableBuilderTransferHandler(table, dragSource, dragAccepter, thePostDrag);
	}

	@Override
	protected void onVisibleData(AbstractObservableTableModel<R> model, Consumer<CollectionChangeEvent<R>> onChange) {
		theFilteredRows.changes().takeUntil(getUntil()).act(onChange::accept);
	}

	@Override
	protected ObservableCollection<? extends CategoryRenderStrategy<R, ?>> getDisplayedColumns() {
		return theDisplayedColumns;
	}

	private static class ModelRowImpl<R> implements ModelRow<R> {
		private final JTable theTable;
		private int theRowIndex = -1;
		private R theRowValue;
		private String isEnabled;

		ModelRowImpl(JTable table) {
			theTable = table;
		}

		ModelRowImpl<R> nextRow(R rowValue) {
			theRowValue = rowValue;
			theRowIndex++;
			return this;
		}

		@Override
		public R getModelValue() {
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
			return false;
		}

		@Override
		public boolean isLeaf() {
			return true;
		}

		@Override
		public String isEnabled() {
			return isEnabled;
		}

		@Override
		public ModelRow<R> setEnabled(String enabled) {
			isEnabled = enabled;
			return this;
		}
	}

	@Override
	protected void forAllVisibleData(AbstractObservableTableModel<R> model, Consumer<ModelRow<R>> forEach) {
		try (Transaction t = theFilteredRows.lock(false, null)) {
			ModelRowImpl<R> row = new ModelRowImpl<>(getEditor());
			for (R rowValue : theFilteredRows)
				forEach.accept(row.nextRow(rowValue));
		}
	}

	@Override
	protected boolean isTransferConfigured() {
		return isRowsDraggable || super.isTransferConfigured();
	}

	@Override
	protected boolean isDraggable() {
		return isRowsDraggable || super.isDraggable();
	}

	@Override
	protected Component createComponent() {
		Component comp = super.createComponent();

		if (theCountTitleDisplayedText != null && comp instanceof JComponent) {
			NumberFormat numberFormat = NumberFormat.getIntegerInstance();
			String singularItemName = getItemName() != null ? getItemName() : "item";
			String pluralItemName = StringUtils.pluralize(singularItemName);
			TitledBorder border = BorderFactory.createTitledBorder(singularItemName);
			if (theFilteredValueRows != null) {
				theRows.observeSize()
				.<String> transform(tx -> tx//
					.combineWith(theFilteredValueRows.observeSize())//
					.combineWith(theCountTitleDisplayedText)//
					.combine((sz, f, ttl) -> {
						String text;
						if (theFilter.get() != TableContentControl.DEFAULT) {// Filtering active
							if (f != sz)
								text = numberFormat.format(f) + " of ";
							else if (sz > 1)
								text = "All ";
							else
								text = "";
						} else
							text = "";
						text += numberFormat.format(sz) + " " + (sz == 1 ? singularItemName : pluralItemName);
						if (ttl != null && !ttl.isEmpty())
							text += " " + ttl;
						return text;
					}))//
				.changes().takeUntil(getUntil()).act(evt -> {
					border.setTitle(evt.getNewValue());
					comp.repaint();
				});
			} else {
				theRows.observeSize()//
				.<String> transform(tx -> tx//
					.combineWith(theCountTitleDisplayedText)//
					.combine((sz, ttl) -> {
						String text = numberFormat.format(sz) + " " + (sz == 1 ? singularItemName : pluralItemName);
						if (ttl != null && !ttl.isEmpty())
							text += " " + ttl;
						return text;
					}))
				.changes().takeUntil(getUntil()).act(evt -> {
					border.setTitle(evt.getNewValue());
					comp.repaint();
				});
			}
			((JComponent) comp).setBorder(border);
		}

		return comp;
	}

	class TableBuilderTransferHandler extends AbstractSimpleTableBuilder<R, T, P>.AbstractTableBuilderTransferHandler {
		private final Runnable thePostDrag;

		TableBuilderTransferHandler(JTable table, SimpleTransferSource<R, ?> rowSource, SimpleTransferAccepter<R, ?> rowAccepter,
			Runnable postDrag) {
			super(table, rowSource, rowAccepter);
			thePostDrag = postDrag;
		}

		@Override
		protected Transaction lockRows(boolean forWrite) {
			return theRows.lock(forWrite, null);
		}

		@Override
		protected ElementId getRowElement(int rowIndex) {
			if (rowIndex >= 0 && rowIndex < theFilteredRows.size())
				return theFilteredRows.getElement(rowIndex).getElementId();
			else
				return null;
		}

		@Override
		protected ElementId getAdjacentRowElement(ElementId rowElement, boolean next) {
			return CollectionElement.getElementId(theFilteredRows.getElement(rowElement).getAdjacent(next));
		}

		@Override
		protected int getElementsAfter(ElementId rowElement) {
			return theFilteredRows.getElement(rowElement).getElementsAfter();
		}

		@Override
		protected R getRowValue(ElementId rowElement) {
			return theFilteredRows.getElement(rowElement).get();
		}

		@Override
		protected String canAddRow(R row, ElementId after, ElementId before) {
			return theRows.canAdd(row, coreElement(after), coreElement(before));
		}

		@Override
		protected String isRowAcceptable(ElementId rowElement, R newValue) {
			return theRows.mutableElement(coreElement(rowElement)).isAcceptable(newValue);
		}

		@Override
		protected void setRow(ElementId rowElement, R newValue) {
			theRows.mutableElement(coreElement(rowElement)).set(newValue);
		}

		@Override
		protected Object getRowCellValue(R rowValue) {
			return rowValue;
		}

		private ElementId coreElement(ElementId filteredId) {
			return filteredId == null ? null : theFilteredRows.getSourceElements(filteredId, theRows).getFirst();
		}

		@Override
		protected Transferable createTransferable(JComponent c) {
			Transferable superT = super.createTransferable(c);
			Transferable rowTransfer = null;
			if (isRowsDraggable) {
				ElementId[] rowIds = new ElementId[getEditor().getSelectedRowCount()];
				int r = 0;
				for (int i = getEditor().getSelectionModel().getMinSelectionIndex(); i <= getEditor().getSelectionModel()
					.getMaxSelectionIndex(); i++) {
					if (getEditor().getSelectionModel().isSelectedIndex(i)) {
						rowIds[r] = theFilteredRows.getElement(i).getElementId();
						r++;
					}
				}
				rowTransfer = new RowTransferable(rowIds);
			}
			if (superT != null) {
				if (rowTransfer != null)
					return new Dragging.AndTransferable(superT, rowTransfer);
				else
					return superT;
			} else
				return rowTransfer;
		}

		@Override
		public int getSourceActions(JComponent c) {
			int actions = super.getSourceActions(c);
			if (isRowsDraggable)
				actions |= TransferHandler.MOVE;
			return actions;
		}

		@Override
		protected void exportDone(JComponent source, Transferable data, int action) {
			// TODO If removed, scroll
			super.exportDone(source, data, action);
		}

		@Override
		protected boolean doImport(TransferSupport support, int rowIndex, int colIndex, boolean beforeRow, boolean beforeCol,
			ElementId targetRow, ElementId after, ElementId before, boolean testOnly) {
			// Support row move before anything else
			if (isRowsDraggable && support.isDrop() && (support.getSourceDropActions() & MOVE) != 0
				&& support.isDataFlavorSupported(ROW_ELEMENT_FLAVOR)) {
				// Moving rows by drag
				ElementId[] rowElements;
				try {
					rowElements = (ElementId[]) support.getTransferable().getTransferData(ROW_ELEMENT_FLAVOR);
					if (rowElements.length == 0)
						return false;
					if (theFilteredRows.getEquivalentElement(rowElements[0]) != null) { // Drag within same table
						boolean canMoveAll = true;
						for (ElementId rowEl : rowElements) {
							if (!rowEl.isPresent())
								continue;
							if (rowEl.equals(after) || rowEl.equals(before))
								continue;
							if (theFilteredRows.canMove(rowEl, after, before) != null) {
								canMoveAll = false;
								break;
							}
						}
						if (canMoveAll) {
							if (testOnly)
								return true;
							for (ElementId rowEl : rowElements) {
								ElementId newRowEl = theFilteredRows.move(rowEl, after, before, true, null).getElementId();
								after = newRowEl;
							}
							if (thePostDrag != null)
								thePostDrag.run();
						}
					}
				} catch (IOException | UnsupportedFlavorException e) {
					e.printStackTrace();
				}
			}
			return super.doImport(support, rowIndex, colIndex, beforeRow, beforeCol, targetRow, after, before, testOnly);
		}
	}

	static final DataFlavor ROW_ELEMENT_FLAVOR = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType, "Row Elements");

	static class RowTransferable implements Transferable {
		private final ElementId[] theRowElements;

		public RowTransferable(ElementId[] rowElements) {
			theRowElements = rowElements;
		}

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			return new DataFlavor[] { ROW_ELEMENT_FLAVOR };
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			if (flavor == ROW_ELEMENT_FLAVOR)
				return true;
			return false;
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			if (flavor == ROW_ELEMENT_FLAVOR)
				return theRowElements;
			throw new UnsupportedFlavorException(flavor);
		}
	}
}