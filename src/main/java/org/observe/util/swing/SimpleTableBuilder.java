package org.observe.util.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.CollectionSubscription;
import org.observe.collect.ObservableCollection;
import org.observe.dbug.DbugAnchor;
import org.observe.dbug.DbugAnchor.InstantiationTransaction;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy.CategoryClickAdapter;
import org.observe.util.swing.Dragging.SimpleTransferAccepter;
import org.observe.util.swing.Dragging.SimpleTransferSource;
import org.observe.util.swing.Dragging.TransferAccepter;
import org.observe.util.swing.Dragging.TransferSource;
import org.observe.util.swing.ObservableCellRenderer.CellRenderContext;
import org.observe.util.swing.ObservableTableModel.RowMouseListener;
import org.observe.util.swing.PanelPopulation.DataAction;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.TableBuilder;
import org.observe.util.swing.PanelPopulationImpl.AbstractComponentEditor;
import org.observe.util.swing.PanelPopulationImpl.SimpleDataAction;
import org.observe.util.swing.PanelPopulationImpl.SimpleHPanel;
import org.observe.util.swing.TableContentControl.FilteredValue;
import org.qommons.ArrayUtils;
import org.qommons.IntList;
import org.qommons.LambdaUtils;
import org.qommons.StringUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

class SimpleTableBuilder<R, P extends SimpleTableBuilder<R, P>> extends AbstractComponentEditor<JTable, P>
implements TableBuilder<R, P> {
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

	@SuppressWarnings("rawtypes")
	private final DbugAnchor<TableBuilder> theAnchor;
	private final ObservableCollection<R> theRows;
	private ObservableCollection<R> theFilteredRows;
	private String theItemName;
	private Function<? super R, String> theNameFunction;
	private ObservableCollection<? extends CategoryRenderStrategy<R, ?>> theColumns;
	private final List<DynamicColumnSet<R, ?>> theDynamicColumns;
	private SettableValue<R> theSelectionValue;
	private ObservableCollection<R> theSelectionValues;
	private List<Object> theActions;
	private boolean theActionsOnTop;
	private ObservableValue<? extends TableContentControl> theFilter;
	private String theCountTitleDisplayedText;
	private Dragging.SimpleTransferSource<R> theDragSource;
	private Dragging.SimpleTransferAccepter<R, R, R> theDragAccepter;
	private List<ObservableTableModel.RowMouseListener<? super R>> theMouseListeners;
	private int theAdaptiveMinRowHeight;
	private int theAdaptivePrefRowHeight;
	private int theAdaptiveMaxRowHeight;
	private boolean withColumnHeader;
	private boolean isScrollable;

	SimpleTableBuilder(ObservableCollection<R> rows, Observable<?> until) {
		super(new JTable() {
			@Override
			public void doLayout() { // We do this ourselves
			}
		}, until);
		theAnchor = PanelPopulation.TableBuilder.DBUG.instance(this, a -> a//
			.setField("type", rows.getType(), null)//
			);
		theRows = rows;
		theDynamicColumns = new ArrayList<>();
		theActions = new LinkedList<>();
		theActionsOnTop = true;
		withColumnHeader = true;
		isScrollable = true;
	}

	protected ObservableCollection<R> getActualRows() {
		return ((ObservableTableModel<R>) getEditor().getModel()).getRows();
	}

	@Override
	public String getItemName() {
		if (theItemName == null)
			return "item";
		else
			return theItemName;
	}

	Function<? super R, String> getNameFunction() {
		return theNameFunction;
	}

	@Override
	public P withItemName(String itemName) {
		theItemName = itemName;
		return (P) this;
	}

	@Override
	public ObservableCollection<? extends R> getRows() {
		return theRows;
	}

	@Override
	public P withColumns(ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns) {
		theColumns = columns;
		return (P) this;
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
					setText(col.print(() -> {
						CollectionElement<R> el = theFilteredRows.getElement(rowIndex);
						if (el == null)
							return null; // May have been removed
						ElementId realRow = theRows.getEquivalentElement(el.getElementId());
						if (realRow == null)
							return null; // May have been removed
						return theRows.getElement(realRow).get();
					}, rowIndex));
					return this;
				}
			}, (__, c) -> String.valueOf(c)));
			if (column != null)
				column.accept(col);
		});
	}

	@Override
	public P withColumn(CategoryRenderStrategy<R, ?> column) {
		if (theColumns == null)
			theColumns = ObservableCollection.create(new TypeToken<CategoryRenderStrategy<R, ?>>() {
			}.where(new TypeParameter<R>() {
			}, TypeTokens.get().wrap(theRows.getType())));
		((ObservableCollection<CategoryRenderStrategy<R, ?>>) theColumns).add(column);
		return (P) this;
	}

	@Override
	public <C> P withDynamicColumns(Function<? super R, ? extends Collection<? extends C>> columnValues, Comparator<? super C> columnSort,
		Function<? super C, CategoryRenderStrategy<R, ?>> columnCreator) {
		theDynamicColumns.add(new DynamicColumnSet<>(columnValues, columnSort, columnCreator));
		return (P) this;
	}

	@Override
	public P withSelection(SettableValue<R> selection, boolean enforceSingleSelection) {
		theSelectionValue = selection;
		if (enforceSingleSelection)
			getEditor().getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		return (P) this;
	}

	@Override
	public P withSelection(ObservableCollection<R> selection) {
		theSelectionValues = selection;
		return (P) this;
	}

	@Override
	public P withFiltering(ObservableValue<? extends TableContentControl> filter) {
		theFilter = filter;
		return (P) this;
	}

	@Override
	public P withCountTitle(String displayedText) {
		theCountTitleDisplayedText = displayedText;
		return (P) this;
	}

	@Override
	public ObservableCollection<R> getFilteredRows() {
		return theFilteredRows;
	}

	@Override
	public P withColumnHeader(boolean columnHeader) {
		withColumnHeader = columnHeader;
		return (P) this;
	}

	@Override
	public P withAdaptiveHeight(int minRows, int prefRows, int maxRows) {
		if (minRows < 0 || minRows > prefRows || prefRows > maxRows)
			throw new IllegalArgumentException("Required: 0<=min<=pref<=max: " + minRows + ", " + prefRows + ", " + maxRows);
		theAdaptiveMinRowHeight = minRows;
		theAdaptivePrefRowHeight = prefRows;
		theAdaptiveMaxRowHeight = maxRows;
		return (P) this;
	}

	@Override
	public List<R> getSelection() {
		return ObservableSwingUtils.getSelection(((ObservableTableModel<R>) getEditor().getModel()).getRowModel(),
			getEditor().getSelectionModel(), null);
	}

	@Override
	public P withMouseListener(RowMouseListener<? super R> listener) {
		if(theMouseListeners==null)
			theMouseListeners=new ArrayList<>();
		theMouseListeners.add(listener);
		return (P) this;
	}

	@Override
	public P withAdd(Supplier<? extends R> creator, Consumer<DataAction<R, ?>> actionMod) {
		return withMultiAction(null, values -> {
			R value = creator.get();
			CollectionElement<R> el = findElement(value);
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
			CollectionElement<R> displayedRow = rows.getElementsBySource(el.getElementId(), theRows).peekFirst();
			if (displayedRow != null) {
				int index = rows.getElementsBefore(displayedRow.getElementId());
				getEditor().getSelectionModel().setSelectionInterval(index, index);
			}
		}, action -> {
			action.allowForMultiple(true).allowForEmpty(true).allowForAnyEnabled(true)//
			.modifyButton(button -> button.withIcon(PanelPopulationImpl.getAddIcon(16)).withTooltip("Add new " + getItemName()));
			if (actionMod != null)
				actionMod.accept(action);
		});
	}

	private CollectionElement<R> findElement(R value) {
		CollectionElement<R> el = theRows.getElement(value, false);
		if (el != null && el.get() != value) {
			CollectionElement<R> lastMatch = theRows.getElement(value, true);
			if (!lastMatch.getElementId().equals(el.getElementId())) {
				if (lastMatch.get() == value)
					el = lastMatch;
				else {
					while (el.get() != value && !el.getElementId().equals(lastMatch.getElementId()))
						el = theRows.getAdjacentElement(el.getElementId(), true);
				}
				if (el.get() != value)
					el = null;
			}
		}
		return el;
	}

	@Override
	public P withRemove(Consumer<? super List<? extends R>> deletion, Consumer<DataAction<R, ?>> actionMod) {
		String single = getItemName();
		String plural = StringUtils.pluralize(single);
		if(deletion==null){
			deletion=values->{
				for(R value : values)
					theRows.remove(value);
			};
		}
		return withMultiAction(null, deletion, action -> {
			action.allowForMultiple(true).withTooltip(items -> "Remove selected " + (items.size() == 1 ? single : plural))//
			.modifyButton(button -> button.withIcon(PanelPopulationImpl.getRemoveIcon(16)));
			if (actionMod != null)
				actionMod.accept(action);
		});
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
			CollectionElement<R> toCopy = theRows.getElement(i);
			R copy = copier.apply(toCopy.get());
			CollectionElement<R> copied = findElement(copy);
			if (copied != null) {//
			} else if (theRows.canAdd(copy, toCopy.getElementId(), null) == null)
				copied = theRows.addElement(copy, toCopy.getElementId(), null, true);
			else
				copied = theRows.addElement(copy, false);
			if (copied != null) {
				ObservableCollection<R> rows = getActualRows();
				CollectionElement<R> rowEl = rows.getElementsBySource(copied.getElementId(), theRows).peekFirst();
				if (rowEl != null)
					newSelection.add(rows.getElementsBefore(rowEl.getElementId()));
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
		CategoryRenderStrategy<R, Object> moveColumn = new CategoryRenderStrategy<R, Object>(up ? "\u2191" : "\u2193",
			TypeTokens.get().OBJECT, v -> null)//
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
						CollectionElement<R> adj = theRows.getAdjacentElement(row.getElementId(), !up);
						if (adj != null) {
							CollectionElement<R> adj2 = theRows.getAdjacentElement(adj.getElementId(), !up);
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
		((ObservableCollection<CategoryRenderStrategy<R, ?>>) theColumns).add(moveColumn);
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
			if (theSelectionValues != null)
				enabled = ObservableValue.of(TypeTokens.get().STRING, enabledGet, () -> theSelectionValues.getStamp(),
					theSelectionValues.simpleChanges());
			else
				enabled = theSelectionValue.map(__ -> enabledGet.get());
			action.allowForMultiple(true)
			.withTooltip(items -> "Move selected row" + (items.size() == 1 ? "" : "s") + " to the " + (up ? "top" : "bottom"))
			.modifyButton(button -> button.withIcon(PanelPopulationImpl.getMoveEndIcon(up, 16)))//
			.allowForEmpty(false).allowForAnyEnabled(false).modifyAction(a -> a.disableWith(enabled));
		});
	}

	@Override
	public P withMultiAction(String actionName, Consumer<? super List<? extends R>> action, Consumer<DataAction<R, ?>> actionMod) {
		SimpleDataAction<R, ?> ta = new SimpleDataAction<>(actionName, this, action, this::getSelection);
		actionMod.accept(ta);
		theActions.add(ta);
		return (P) this;
	}

	@Override
	public P withActionsOnTop(boolean actionsOnTop) {
		theActionsOnTop = actionsOnTop;
		return (P) this;
	}

	@Override
	public P withTableOption(Consumer<? super PanelPopulator<?, ?>> panel) {
		theActions.add(panel);
		return (P) this;
	}

	@Override
	public P dragSourceRow(Consumer<? super TransferSource<R>> source) {
		if (theDragSource == null)
			theDragSource = new SimpleTransferSource<>(theRows.getType());
		// if (source == null)
		// throw new IllegalArgumentException("Drag sourcing must be configured");
		if (source != null)
			source.accept(theDragSource);
		return (P) this;
	}

	@Override
	public P dragAcceptRow(Consumer<? super TransferAccepter<R, R, R>> accept) {
		if (theDragAccepter == null)
			theDragAccepter = new SimpleTransferAccepter<>(theRows.getType());
		// if (accept == null)
		// throw new IllegalArgumentException("Drag accepting must be configured");
		if (accept != null)
			accept.accept(theDragAccepter);
		return (P) this;
	}

	@Override
	public P scrollable(boolean scrollable) {
		isScrollable = scrollable;
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

	private static void handleColumnHeaderClick(ObservableValue<? extends TableContentControl> filter, String columnName, boolean checkType,
		Object cause) {
		TableContentControl filterV = filter.get();
		TableContentControl sorted = filterV == null ? new TableContentControl.RowSorter(Arrays.asList(columnName))
			: filterV.toggleSort(columnName, true);
		if (checkType && TypeTokens.get().isInstance(filter.getType(), sorted))
			return;
		SettableValue<TableContentControl> settableFilter = (SettableValue<TableContentControl>) filter;
		if (settableFilter.isAcceptable(sorted) == null)
			settableFilter.set(sorted, cause);
	}

	@Override
	protected Component createComponent() {
		if (theColumns == null)
			throw new IllegalStateException("No columns configured");
		theAnchor.event("create", null);
		InstantiationTransaction instantiating = theAnchor.instantiating();
		ObservableTableModel<R> model;
		ObservableCollection<TableContentControl.FilteredValue<R>> filtered;
		ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns;
		ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns2;
		if (theDynamicColumns.isEmpty()) {
			columns2 = theColumns;
		} else {
			List<ObservableCollection<? extends CategoryRenderStrategy<R, ?>>> columnSets = new ArrayList<>();
			columnSets.add(theColumns);
			for (DynamicColumnSet<R, ?> dc : theDynamicColumns) {
				ObservableCollection.CollectionDataFlow<R, ?, Object> columnValueFlow = theRows.flow()//
					.flatMap(Object.class, row -> ObservableCollection.of(Object.class, dc.columnValues.apply(row)).flow());
				if (dc.columnSort != null)
					columnValueFlow = columnValueFlow.distinctSorted((Comparator<Object>) dc.columnSort, false);
				else
					columnValueFlow = columnValueFlow.distinct();
				ObservableCollection<CategoryRenderStrategy<R, ?>> dcc = columnValueFlow
					.transform((Class<CategoryRenderStrategy<R, ?>>) (Class<?>) CategoryRenderStrategy.class, tx -> tx//
						.cache(true).reEvalOnUpdate(false).fireIfUnchanged(false)//
						.map(columnValue -> ((DynamicColumnSet<R, Object>) dc).columnCreator.apply(columnValue)))
					.collectActive(getUntil());
				columnSets.add(dcc);
			}
			columns2 = ObservableCollection.flattenCollections((TypeToken<CategoryRenderStrategy<R, ?>>) theColumns.getType(),
				columnSets.toArray(new ObservableCollection[columnSets.size()])).collectActive(getUntil());
		}
		columns2 = columns2.safe(ThreadConstraint.EDT, getUntil());
		if (theFilter != null) {
			if (theFilter instanceof SettableValue) {
				Map<CategoryRenderStrategy<R, ?>, Runnable> headerListening = new HashMap<>();
				boolean checkType = TypeTokens.getRawType(theFilter.getType()) != TableContentControl.class;
				CollectionSubscription colClickSub = columns2.subscribe(evt -> {
					switch (evt.getType()) {
					case add:
						headerListening.put(evt.getNewValue(), evt.getNewValue().addMouseListener(new CategoryClickAdapter<R, Object>() {
							@Override
							public void mouseClicked(ModelCell<? extends R, ? extends Object> cell, MouseEvent e) {
								if (cell == null) // Looking for a mouse click on the column header
									handleColumnHeaderClick(theFilter, evt.getNewValue().getName(), checkType, e);
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
										if (cell == null) // Looking for a mouse click on the column header
											handleColumnHeaderClick(theFilter, evt.getNewValue().getName(), checkType, e);
									}
								}));
						}
					}
				}, true);
				getUntil().take(1).act(__ -> colClickSub.unsubscribe(true));
			}

			columns = TableContentControl.applyColumnControl(columns2, theFilter, getUntil());
			Observable<?> columnChanges = Observable.or(Observable.constant(null), columns.simpleChanges());
			ObservableCollection<TableContentControl.FilteredValue<R>> rawFiltered = TableContentControl.applyRowControl(theRows,
				() -> columns, theFilter.refresh(columnChanges), getUntil());
			filtered = rawFiltered.safe(ThreadConstraint.EDT, getUntil());
			theFilteredRows = filtered.flow()
				.transform(theRows.getType(),
					tx -> tx.map(LambdaUtils.printableFn(f -> f.value, "value", null)).modifySource(FilteredValue::setValue))
				.collectActive(getUntil());
		} else {
			filtered = null;
			columns = columns2;
			theFilteredRows = theRows.safe(ThreadConstraint.EDT, getUntil());
		}
		instantiating.watchFor(ObservableTableModel.DBUG, "model", tk -> tk.applyTo(1));
		model = new ObservableTableModel<>(theFilteredRows, columns);
		JTable table = getEditor();
		if (!withColumnHeader)
			table.setTableHeader(null);
		table.setModel(model);
		if(theMouseListeners!=null) {
			for(ObservableTableModel.RowMouseListener<? super R> listener : theMouseListeners)
				model.addMouseListener(listener);
		}
		Subscription sub = ObservableTableModel.hookUp(table, model, //
			filtered == null ? null : new ObservableTableModel.TableRenderContext() {
			@Override
			public SortedMatchSet getEmphaticRegions(int row, int column) {
				TableContentControl.FilteredValue<R> fv = filtered.get(row);
				if (column >= fv.getColumns())
					return null;
				return fv.getMatches(column);
			}
		});
		getUntil().take(1).act(__ -> sub.unsubscribe());

		JScrollPane scroll = new JScrollPane(table);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		class SizeListener implements ComponentListener, HierarchyListener, MouseListener, MouseMotionListener {
			private final List<int[]> theColumnWidths;
			private int theResizingColumn;
			private int theResizingColumnOrigWidth;
			private int theResizingPreColumnWidth;
			private int theDragStart;

			/**
			 * This integer is how much the user has resized columns beyond the scroll pane's width.<br />
			 * This helps us with column layout.
			 */
			private int theTableExtraWidth;

			SizeListener() {
				theResizingColumn = -1;
				theColumnWidths = new ArrayList<>();
				for (int c = 0; c < columns.size(); c++) {
					int[] widths = new int[4]; // min, pref, max, and actual
					theColumnWidths.add(widths);
					getColumnWidths(columns.get(c), c, widths, null);
					TableColumn column = table.getColumnModel().getColumn(table.convertColumnIndexToView(c));
					column.setMinWidth(widths[0]);
					column.setMaxWidth(widths[2]);
					widths[3] = widths[1];
				}
				adjustScrollWidths();
				theFilteredRows.changes().act(evt -> {
					if (theAdaptivePrefRowHeight > 0)
						adjustHeight();
					boolean adjusted = false;
					int c = 0;
					for (CategoryRenderStrategy<R, ?> column : columns) {
						if (column.isUsingRenderingForSize()) {
							int[] cw = theColumnWidths.get(c);
							int[] newWidths = new int[3];
							boolean colAdjust = false;
							switch (evt.type) {
							case add:
								getColumnWidths(column, c, newWidths, evt);
								if (newWidths[0] > cw[0]) {
									colAdjust = true;
									cw[0] = newWidths[0];
								}
								if (newWidths[1] > cw[1]) {
									colAdjust = true;
									cw[1] = newWidths[1];
								}
								if (newWidths[2] > cw[2]) {
									colAdjust = true;
									cw[2] = newWidths[2];
								}
								break;
							case remove:
							case set:
								getColumnWidths(column, c, newWidths, null);
								if (newWidths[0] != cw[0]) {
									colAdjust = true;
									cw[0] = newWidths[0];
								}
								if (newWidths[1] != cw[1]) {
									colAdjust = true;
									cw[1] = newWidths[1];
								}
								if (newWidths[2] != cw[2]) {
									colAdjust = true;
									cw[2] = newWidths[2];
								}
								break;
							}
							if (colAdjust) {
								adjusted = true;
								TableColumn tc = table.getColumnModel().getColumn(table.convertColumnIndexToView(c));
								tc.setMinWidth(cw[0]);
								tc.setMaxWidth(cw[2]);
								if (cw[3] == 0)
									cw[3] = cw[1];
								else if (cw[3] < cw[0])
									cw[3] = cw[0];
								else if (cw[3] > cw[2])
									cw[3] = cw[2];
							}
							break;
						}
						c++;
					}
					if (adjusted)
						adjustScrollWidths();
				});
				columns.changes().act(evt -> {
					theResizingColumn = -1;
					boolean adjust = false;
					for (CollectionChangeEvent.ElementChange<? extends CategoryRenderStrategy<R, ?>> change : evt.getElements()) {
						switch (evt.type) {
						case add:
							int[] widths = new int[4];
							theColumnWidths.add(change.index, widths);
							getColumnWidths(change.newValue, change.index, widths, null);
							widths[3] = widths[1];
							adjust = true;
							break;
						case remove:
							theColumnWidths.remove(change.index);
							adjust = true;
							break;
						case set:
							if (!change.newValue.isUsingRenderingForSize()) {
								int[] cw = theColumnWidths.get(change.index);
								adjust |= cw[0] != change.newValue.getMinWidth();
								adjust |= cw[1] != change.newValue.getPrefWidth();
								adjust |= cw[2] != change.newValue.getMaxWidth();
								cw[0] = change.newValue.getMinWidth();
								cw[1] = change.newValue.getPrefWidth();
								cw[2] = change.newValue.getMaxWidth();
								TableColumn tc = table.getColumnModel().getColumn(table.convertColumnIndexToView(change.index));
								tc.setMinWidth(cw[0]);
								tc.setMaxWidth(cw[2]);
							}
							break;
						}
					}
					if (adjust)
						adjustScrollWidths();
				});
			}

			@Override
			public void mouseClicked(MouseEvent e) {
			}

			@Override
			public void mousePressed(MouseEvent e) {
				TableColumn resizing = table.getTableHeader().getResizingColumn();
				if (resizing == null) {
					theResizingColumn = -1;
					return;
				}
				theResizingColumn = resizing.getModelIndex();
				theResizingColumnOrigWidth = resizing.getWidth();
				theDragStart = e.getX();
				int viewC = table.convertColumnIndexToView(theResizingColumn);
				theResizingPreColumnWidth = viewC * table.getColumnModel().getColumnMargin();
				for (int c = 0; c < viewC; c++)
					theResizingPreColumnWidth += table.getColumnModel().getColumn(c).getWidth();
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				int resizeModelIndex = theResizingColumn;
				if (resizeModelIndex < 0)
					return;
				int resizeColumn = table.convertColumnIndexToView(resizeModelIndex);
				int tableSize = scroll.getViewport().getWidth()
					- (table.getColumnCount() - 1) * table.getColumnModel().getColumnMargin();
				if (tableSize <= 0)
					return;
				int newWidth = theResizingColumnOrigWidth + e.getX() - theDragStart;
				if (newWidth < theColumnWidths.get(resizeModelIndex)[0])
					newWidth = theColumnWidths.get(resizeModelIndex)[0];
				else if (newWidth > theColumnWidths.get(resizeModelIndex)[2])
					newWidth = theColumnWidths.get(resizeModelIndex)[2];
				if (newWidth == theColumnWidths.get(resizeModelIndex)[3])
					return;
				int remain = tableSize - theResizingPreColumnWidth - newWidth;
				int[] postTotalW = new int[4];
				if (!isScrollable) {
					// We need to determine if the new size is actually ok--if the columns to the right of the drag
					// can be resized down enough to accommodate the user's action.
					for (int c = resizeColumn + 1; c < table.getColumnModel().getColumnCount(); c++) {
						int[] widths = theColumnWidths.get(table.convertColumnIndexToModel(c));
						postTotalW[0] += widths[0];
						postTotalW[1] += widths[1];
						postTotalW[2] += widths[2];
						postTotalW[3] += widths[3];
						if (c > resizeColumn + 1) {
							postTotalW[0] += table.getColumnModel().getColumnMargin();
							postTotalW[1] += table.getColumnModel().getColumnMargin();
							postTotalW[2] += table.getColumnModel().getColumnMargin();
							postTotalW[3] += table.getColumnModel().getColumnMargin();
						}
					}
					// If not, cap the action so that the columns are at their min sizes
					if (remain < postTotalW[0]) {
						newWidth = tableSize - theResizingPreColumnWidth - postTotalW[0];
						if (newWidth <= theColumnWidths.get(resizeModelIndex)[0])
							return; // Already as small as it can be, ignore the drag
						remain = tableSize - theResizingPreColumnWidth - newWidth;
					}
					if (remain == postTotalW[3])
						return;
				}
				int wDiff = newWidth - theColumnWidths.get(resizeModelIndex)[3];
				theColumnWidths.get(resizeModelIndex)[3] = newWidth;
				if (!isScrollable) {
					// Then adjust all the actual sizes of columns to the right of the drag
					distributeSize(remain - postTotalW[3], resizeColumn + 1);
				} else {
					int newTableW = table.getColumnModel().getColumnMargin() * (table.getColumnCount() - 1);
					for (int[] cw : theColumnWidths)
						newTableW += cw[3];
					table.setSize(newTableW, table.getHeight());
					theTableExtraWidth += wDiff;
					boolean preHSB = scroll.getHorizontalScrollBarPolicy() != JScrollPane.HORIZONTAL_SCROLLBAR_NEVER;
					boolean hsb = newTableW > tableSize;
					if (preHSB != hsb) {
						scroll.setHorizontalScrollBarPolicy(
							hsb ? JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED : JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
						if (theAdaptivePrefRowHeight > 0)
							adjustHeight();
					}
				}
				isRecursive = true;
				applyColumnSizes();
				isRecursive = false;
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				theResizingColumn = -1;
			}

			@Override
			public void mouseEntered(MouseEvent e) {
			}

			@Override
			public void mouseExited(MouseEvent e) {
			}

			@Override
			public void mouseMoved(MouseEvent e) {
			}

			private int theScrollWidth;

			@Override
			public void componentResized(ComponentEvent e) {
				int newWidth = scroll.getWidth();
				int widthDiff = newWidth - theScrollWidth;
				// If we're resizing in a way that accommodates the cumulative growth or shrinkage of columns due to user resizing,
				// modify the extra table width such that we don't grow or shrink columns as a result of the table resize.
				// This has the effect of resetting the table so that the columns will fit in the scroll window if possible.
				if (widthDiff > 0 && theTableExtraWidth > 0)
					theTableExtraWidth = Math.max(0, theTableExtraWidth - widthDiff);
				else if (widthDiff < 0 && theTableExtraWidth < 0)
					theTableExtraWidth = Math.min(0, theTableExtraWidth - widthDiff);
				theScrollWidth = newWidth;
				adjustHeight();
				adjustScrollWidths();
			}

			@Override
			public void componentShown(ComponentEvent e) {
				adjustHeight();
				adjustScrollWidths();
			}

			@Override
			public void componentMoved(ComponentEvent e) {
			}

			@Override
			public void componentHidden(ComponentEvent e) {
			}

			private long theLastHE;
			@Override
			public void hierarchyChanged(HierarchyEvent e) {
				if (!scroll.isShowing())
					return;
				long time=System.currentTimeMillis();
				if (time - theLastHE < 5)
					return;
				theLastHE = time;
				adjustHeight();
				adjustScrollWidths();
			}

			void adjustScrollWidths() {
				theAnchor.event("adjustWidth", null);
				int spacing = table.getInsets().left + table.getInsets().right//
					+ table.getColumnModel().getColumnMargin() * (table.getColumnCount() - 1)//
					+ 2;
				int minW = spacing;
				int prefW = spacing, maxW = spacing;
				for (int[] width : theColumnWidths) {
					minW += width[0];
					prefW += width[1];
					maxW += width[2];
					if (maxW < 0)
						maxW = Integer.MAX_VALUE;
				}
				BoundedRangeModel vbm = scroll.getVerticalScrollBar().getModel();

				boolean vsbVisible = isScrollable && vbm.getExtent() < vbm.getMaximum();
				int sbw = scroll.getVerticalScrollBar().getWidth();
				if (vsbVisible) {
					minW += sbw;
					prefW += sbw;
					maxW += sbw;
					if (maxW < 0)
						maxW = Integer.MAX_VALUE;
				}
				// Dimension psvs = table.getPreferredScrollableViewportSize();
				Dimension min = scroll.getMinimumSize();
				Dimension pref = scroll.getPreferredSize();
				Dimension max = scroll.getMaximumSize();

				// if (psvs.width != prefW) {
				// if (vsbVisible)
				// table.setPreferredScrollableViewportSize(new Dimension(prefW, psvs.height));
				// else
				// table.setPreferredScrollableViewportSize(new Dimension(prefW - sbw, psvs.height));
				// }

				if (!isScrollable)
					scroll.setMinimumSize(new Dimension(minW, min.height));
				scroll.setPreferredSize(new Dimension(prefW, pref.height));
				scroll.setMaximumSize(new Dimension(maxW, max.height));
				layoutColumns();
			}

			private boolean isRecursive;

			void layoutColumns() {
				if (isRecursive)
					return;
				int tableSize = scroll.getViewport().getWidth()
					- (getEditor().getColumnCount() - 1) * table.getColumnModel().getColumnMargin()//
					+ theTableExtraWidth;
				if (tableSize <= 0)
					return;
				isRecursive = true;
				theAnchor.event("layoutColumns", null);
				int[] total = new int[4];
				// Figure out how things have changed--how much space the columns want compared to how much we have
				for (int c = 0; c < theColumnWidths.size(); c++) {
					int[] cw = theColumnWidths.get(c);
					total[0] += cw[0];
					total[1] += cw[1];
					total[2] += cw[2];
					if (total[2] < 0)
						total[2] = Integer.MAX_VALUE;
					total[3] += cw[3];
				}
				int diff = tableSize - total[3];
				diff = distributeSize(diff, 0);
				if (diff < 0) { // Table is too small for its columns
					if (isScrollable)
						total[3] = total[1];
					else
						total[3] = total[0];
				} else if (diff > 0) { // Columns can't take up all the space in the table
					total[3] = total[2];
				} else
					total[3] = tableSize;

				applyColumnSizes();
				if (table.getSize().width != total[3])
					table.setSize(new Dimension(total[3], table.getHeight()));
				boolean preHsb = scroll.getHorizontalScrollBarPolicy() != JScrollPane.HORIZONTAL_SCROLLBAR_NEVER;
				if (theTableExtraWidth > 0)
					tableSize -= theTableExtraWidth;
				boolean hsb = isScrollable && total[3] > tableSize;
				if (preHsb != hsb) {
					scroll.setHorizontalScrollBarPolicy(
						hsb ? JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED : JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
					adjustHeight();
				}
				isRecursive = false;
			}

			private int distributeSize(int size, int startColumn) {
				/* Logical flow:
				 * If the amount of space available increases, distribute the space first to columns that are more squished,
				 * i.e., those whose actual size is much less than their preference.  As these are relieved, distribute the space
				 * to columns evenly such that all approach their maximum size together.
				 * If the amount of space decreases, take the space from columns that are not squished, i.e. those whose size is much
				 * greater than their preference.  As these become more squished, take space from all columns evenly such that all approach
				 * their preferred size together.  Never auto-resize columns below their preference in a scrollable table.
				 * If the table is not scrollable, shrink columns together down to their minimum size.
				 */
				if (size == 0)
					return 0;
				// Figure out how squished each column is and
				IntList squish = new IntList(theColumnWidths.size()).setSorted(true);
				IntList columnsBySquish = new IntList(theColumnWidths.size());
				for (int c = startColumn; c < table.getColumnCount(); c++) {
					int modelC = table.convertColumnIndexToModel(c);
					int[] cw = theColumnWidths.get(modelC);
					if (size < 0) {
						if (isScrollable) {
							if (cw[3] <= cw[1])
								continue;
						} else if (cw[3] <= cw[0])
							continue;
					} else if (cw[3] >= cw[2])
						continue;
					int cSquish = cw[3] - cw[1];
					int index = squish.indexFor(cSquish);
					while (index < squish.size() && squish.get(index) == cSquish)
						index++;
					squish.add(index, cSquish);
					columnsBySquish.add(index, modelC);
				}
				squish.setSorted(false); // Turn off the ordered checking
				if (size > 0) { // Grow
					for (int c = 0; c < squish.size() && size > 0; c++) {
						int d = c == squish.size() - 1 ? size : Math.min((squish.get(c + 1) - squish.get(c)) * (c + 1), size);
						int d2 = d / (c + 1);
						int remain = d - d2 * (c + 1);
						for (int i = c; i >= 0 && (d2 > 0 || remain > 0); i--) {
							if (i == squish.size())
								continue;
							int[] cw = theColumnWidths.get(columnsBySquish.get(i));
							int squishMod = d2;
							if (remain > 0) {
								squishMod++;
								remain--;
							}
							if (squishMod >= cw[2] - cw[3]) {
								squishMod = cw[2] - cw[3];
								squish.remove(i);
								columnsBySquish.remove(i);
							} else
								squish.set(i, squish.get(i) + squishMod);
							cw[3] += squishMod;
							size -= squishMod;
						}
					}
				} else {
					for (int c = 0; c < squish.size() && size < 0; c++) {
						int ci = squish.size() - c - 1;
						int d = c == squish.size() - 1 ? -size : Math.min((squish.get(ci) - squish.get(ci - 1)) * (c + 1), -size);
						int d2 = d / (c + 1);
						int remain = d - d2 * (c + 1);
						for (int i = ci; i < squish.size() && (d2 > 0 || remain > 0); i++) {
							if (i < 0)
								continue;
							int[] cw = theColumnWidths.get(columnsBySquish.get(i));
							int squishMod = d2;
							if (remain > 0) {
								squishMod++;
								remain--;
							}
							int min = isScrollable ? cw[1] : cw[0];
							if (squishMod >= cw[3] - min) {
								squishMod = cw[3] - min;
								squish.remove(i);
								columnsBySquish.remove(i);
							} else
								squish.set(i, squish.get(i) - squishMod);
							cw[3] -= squishMod;
							size += squishMod;
						}
					}
				}
				return size;
			}

			void applyColumnSizes() {
				for (int c = 0; c < model.getColumnCount() && c < table.getColumnModel().getColumnCount(); c++) {
					int[] widths = theColumnWidths.get(table.convertColumnIndexToModel(c));
					TableColumn tc = table.getColumnModel().getColumn(c);
					if (tc.getWidth() != widths[3])
						tc.setWidth(widths[3]);
					if (tc.getPreferredWidth() != widths[3])
						tc.setPreferredWidth(widths[3]);
				}
			}

			void adjustHeight() {
				if (theAdaptivePrefRowHeight <= 0)
					return; // Not adaptive
				theAnchor.event("adjustHeight", null);
				int insets = table.getInsets().top + table.getInsets().bottom;
				int spacing = table.getIntercellSpacing().height;
				int minHeight = insets + spacing + 4, prefHeight = insets + spacing + 4, maxHeight = insets + spacing + 4;
				if (table.getTableHeader() != null && table.getTableHeader().isVisible()) {
					minHeight += table.getTableHeader().getPreferredSize().height;
					maxHeight += table.getTableHeader().getPreferredSize().height;
					minHeight += spacing;
					maxHeight += spacing;
				}
				int rowCount = model.getRowCount();
				for (int i = 0; i < theAdaptiveMaxRowHeight && i < rowCount; i++) {
					int rowHeight = table.getRowHeight(i);
					if (i < theAdaptiveMinRowHeight)
						minHeight += rowHeight;
					if (i < theAdaptivePrefRowHeight)
						prefHeight += rowHeight;
					if (i < theAdaptiveMaxRowHeight)
						maxHeight += rowHeight;
				}
				boolean hsb = scroll.getHorizontalScrollBarPolicy() != JScrollPane.HORIZONTAL_SCROLLBAR_NEVER;
				if (hsb) {
					int sbh = scroll.getHorizontalScrollBar().getHeight();
					minHeight += sbh;
					prefHeight += sbh;
					maxHeight += sbh;
				}
				minHeight = Math.max(0, minHeight - 4);
				prefHeight = Math.max(0, prefHeight - 4);
				maxHeight = Math.max(0, maxHeight - 4);
				// Dimension psvs = table.getPreferredScrollableViewportSize();
				// if (psvs.height != prefHeight) {
				// // int w = 0;
				// // for (int c = 0; c < table.getColumnModel().getColumnCount(); c++)
				// // w += table.getColumnModel().getColumn(c).getWidth();
				// table.setPreferredScrollableViewportSize(new Dimension(psvs.width, prefHeight));
				// }
				Dimension pref = scroll.getPreferredSize();
				scroll.setPreferredSize(new Dimension(pref.width, prefHeight));
				Dimension min = scroll.getMinimumSize();
				scroll.setMinimumSize(new Dimension(min.width, minHeight));
				Dimension max = scroll.getMaximumSize();
				scroll.setMaximumSize(new Dimension(max.width, maxHeight));
				if (scroll.getParent() != null)
					scroll.getParent().revalidate();
			}
		}
		SizeListener sizeListener = new SizeListener();
		if (table.getTableHeader() != null) {
			table.getTableHeader().addMouseListener(sizeListener);
			table.getTableHeader().addMouseMotionListener(sizeListener);
		}
		scroll.addComponentListener(sizeListener);
		scroll.addHierarchyListener(sizeListener);
		EventQueue.invokeLater(() -> {
			if (isScrollable)
				sizeListener.adjustHeight();
			sizeListener.adjustScrollWidths();
		});
		if (isScrollable) {
			// Default scroll increments are ridiculously small
			scroll.getVerticalScrollBar().setUnitIncrement(10);
			scroll.getHorizontalScrollBar().setUnitIncrement(10);
		} else {
			scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		}

		// Selection
		Supplier<List<R>> selectionGetter = () -> getSelection();
		if (theSelectionValue != null)
			ObservableSwingUtils.syncSelection(table, model.getRowModel(), table::getSelectionModel, model.getRows().equivalence(),
				theSelectionValue, getUntil(), (index, cause) -> {
					if (index >= getRows().size())
						return;
					MutableCollectionElement<R> el = (MutableCollectionElement<R>) getRows()
						.mutableElement(getRows().getElement(index).getElementId());
					if (el.isAcceptable(el.get()) == null) {
						try (Transaction t = getRows().lock(true, cause)) {
							el.set(el.get());
						}
					}
				}, false);
		if (theSelectionValues != null)
			ObservableSwingUtils.syncSelection(table, model.getRowModel(), table::getSelectionModel, model.getRows().equivalence(),
				theSelectionValues, getUntil());

		JComponent comp;
		if (!theActions.isEmpty()) {
			ListSelectionListener selListener = e -> {
				List<R> selection = selectionGetter.get();
				for (Object action : theActions) {
					if (action instanceof SimpleDataAction)
						((SimpleDataAction<R, ?>) action).updateSelection(selection, e);
				}
			};
			ListDataListener dataListener = new ListDataListener() {
				@Override
				public void intervalAdded(ListDataEvent e) {}

				@Override
				public void intervalRemoved(ListDataEvent e) {}

				@Override
				public void contentsChanged(ListDataEvent e) {
					ListSelectionModel selModel = table.getSelectionModel();
					if (selModel.getMinSelectionIndex() >= 0 && e.getIndex0() <= selModel.getMaxSelectionIndex()
						&& e.getIndex1() >= selModel.getMinSelectionIndex()) {
						List<R> selection = selectionGetter.get();
						for (Object action : theActions) {
							if (action instanceof SimpleDataAction)
								((SimpleDataAction<R, ?>) action).updateSelection(selection, e);
						}
					}
				}
			};
			List<R> selection = selectionGetter.get();
			for (Object action : theActions) {
				if (action instanceof SimpleDataAction)
					((SimpleDataAction<R, ?>) action).updateSelection(selection, null);
			}

			PropertyChangeListener selModelListener = evt -> {
				((ListSelectionModel) evt.getOldValue()).removeListSelectionListener(selListener);
				((ListSelectionModel) evt.getNewValue()).addListSelectionListener(selListener);
			};
			table.getSelectionModel().addListSelectionListener(selListener);
			table.addPropertyChangeListener("selectionModel", selModelListener);
			model.getRowModel().addListDataListener(dataListener);
			getUntil().take(1).act(__ -> {
				table.removePropertyChangeListener("selectionModel", selModelListener);
				table.getSelectionModel().removeListSelectionListener(selListener);
				model.getRowModel().removeListDataListener(dataListener);
			});
			SimpleHPanel<JPanel, ?> buttonPanel = new SimpleHPanel<>(null,
				new JPanel(new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING)), getUntil());
			for (Object action : theActions) {
				if (action instanceof SimpleDataAction)
					((SimpleDataAction<R, ?>) action).addButton(buttonPanel);
				else if (action instanceof Consumer)
					buttonPanel.addHPanel(null, "box", (Consumer<PanelPopulator<JPanel, ?>>) action);
			}
			JPanel tablePanel = new JPanel(new BorderLayout());
			tablePanel.add(buttonPanel.getComponent(), theActionsOnTop ? BorderLayout.NORTH : BorderLayout.SOUTH);
			tablePanel.add(scroll, BorderLayout.CENTER);
			comp = tablePanel;
		} else
			comp = scroll;

		if (theCountTitleDisplayedText != null) {
			NumberFormat numberFormat = NumberFormat.getIntegerInstance();
			String singularItemName = theItemName != null ? theItemName : "item";
			String pluralItemName = StringUtils.pluralize(singularItemName);
			TitledBorder border = BorderFactory.createTitledBorder(singularItemName);
			if (filtered != null) {
				theRows.observeSize()
				.transform(int[].class, tx -> tx.combineWith(filtered.observeSize()).combine((sz, f) -> new int[] { sz, f }))//
				.changes().takeUntil(getUntil()).act(evt -> {
					int sz = evt.getNewValue()[0];
					int f = evt.getNewValue()[1];
					String text;
					if (theFilter.get() != TableContentControl.DEFAULT) {// Filtering active
						if (f != sz)
							text = numberFormat.format(f) + " of ";
						else if(sz > 1)
							text = "All ";
						else
							text = "";
					} else
						text = "";
					text += numberFormat.format(sz) + " " + (sz == 1 ? singularItemName : pluralItemName);
					if (!theCountTitleDisplayedText.isEmpty())
						text += " " + theCountTitleDisplayedText;
					border.setTitle(text);
					comp.repaint();
				});
			} else {
				theRows.observeSize().changes().takeUntil(getUntil()).act(evt -> {
					String text = numberFormat.format(evt.getNewValue()) + " "
						+ (evt.getNewValue() == 1 ? singularItemName : pluralItemName);
					if (!theCountTitleDisplayedText.isEmpty())
						text += " " + theCountTitleDisplayedText;
					border.setTitle(text);
					comp.repaint();
				});
			}
			comp.setBorder(border);
		}

		// Set up transfer handling (DnD, copy/paste)
		boolean draggable = theDragSource != null || theDragAccepter != null;
		for (CategoryRenderStrategy<? super R, ?> column : theColumns) {
			// TODO check the draggable flag
			if (column.getDragSource() != null || column.getMutator().getDragAccepter() != null) {
				draggable = true;
				break;
			}
		}
		if (draggable) {
			table.setDragEnabled(true);
			table.setDropMode(DropMode.INSERT_ROWS);
			TableBuilderTransferHandler handler = new TableBuilderTransferHandler(table, theDragSource, theDragAccepter);
			table.setTransferHandler(handler);
		}

		instantiating.close();
		return comp;
	}

	private void getColumnWidths(CategoryRenderStrategy<R, ?> column, int columnIndex, int[] widths, CollectionChangeEvent<R> rowEvent) {
		if (column.isUsingRenderingForSize()) {
			ObservableCellRenderer<R, ?> renderer = (ObservableCellRenderer<R, ?>) column.getRenderer();
			if (renderer == null) {
				renderer = new ObservableCellRenderer.DefaultObservableCellRenderer<>((row, cell) -> String.valueOf(cell));
				((CategoryRenderStrategy<R, Object>) column).withRenderer((ObservableCellRenderer<R, Object>) renderer);
			}
			int maxMin = 0, maxPref = 0, maxMax = 0;
			if (withColumnHeader) {
				Component render = getEditor().getTableHeader().getComponent(getEditor().convertColumnIndexToView(columnIndex));
				int min = render.getMinimumSize().width;
				int pref = render.getPreferredSize().width;
				int max = render.getMaximumSize().width;
				if (min > maxMin)
					maxMin = min;
				if (pref > maxPref)
					maxPref = pref;
				if (max > maxMax)
					maxMax = max;
			}
			if (rowEvent != null) {
				for (CollectionChangeEvent.ElementChange<R> change : rowEvent.getElements()) {
					R row = change.newValue;
					Object cellValue = column.getCategoryValue(row);
					ModelCell<R, Object> cell = new ModelCell.Default<>(() -> row, cellValue, change.index, columnIndex,
						getEditor().isRowSelected(change.index), false, false, false, false, true);
					Component render = ((CategoryRenderStrategy<R, Object>) column).getRenderer().getCellRendererComponent(getEditor(),
						cell, CellRenderContext.DEFAULT);
					int min = render.getMinimumSize().width;
					int pref = render.getPreferredSize().width;
					int max = render.getMaximumSize().width;
					if (min > maxMin)
						maxMin = min;
					if (pref > maxPref)
						maxPref = pref;
					if (max > maxMax)
						maxMax = max;
				}
			} else {
				int r = 0;
				for (R row : theFilteredRows) {
					Object cellValue = column.getCategoryValue(row);
					ModelCell<R, Object> cell = new ModelCell.Default<>(() -> row, cellValue, r, columnIndex, getEditor().isRowSelected(r),
						false, false, false, false, true);
					Component render = ((CategoryRenderStrategy<R, Object>) column).getRenderer().getCellRendererComponent(getEditor(),
						cell, CellRenderContext.DEFAULT);
					int min = render.getMinimumSize().width;
					int pref = render.getPreferredSize().width;
					int max = render.getMaximumSize().width;
					if (min > maxMin)
						maxMin = min;
					if (pref > maxPref)
						maxPref = pref;
					if (max > maxMax)
						maxMax = max;
					r++;
				}
			}
			// Not sure why, but these actually need just a pixel more padding
			maxMin++;
			maxMax++;
			widths[0] = maxMin;
			widths[1] = Math.max(maxPref, maxMin);
			widths[2] = maxMax;
		} else {
			widths[0] = column.getMinWidth();
			widths[1] = column.getPrefWidth();
			widths[2] = column.getMaxWidth();
		}
	}

	class TableBuilderTransferHandler extends TransferHandler {
		private final JTable theTable;
		private final Dragging.SimpleTransferSource<R> theRowSource;
		private final Dragging.SimpleTransferAccepter<R, R, R> theRowAccepter;

		TableBuilderTransferHandler(JTable table, SimpleTransferSource<R> rowSource, SimpleTransferAccepter<R, R, R> rowAccepter) {
			theTable = table;
			theRowSource = rowSource;
			theRowAccepter = rowAccepter;
		}

		@Override
		protected Transferable createTransferable(JComponent c) {
			try (Transaction rowT = theRows.lock(false, null); Transaction colT = theColumns.lock(false, null)) {
				if (theTable.getSelectedRowCount() == 0)
					return null;
				List<R> selectedRows = new ArrayList<>(theTable.getSelectedRowCount());
				for (int i = theTable.getSelectionModel().getMinSelectionIndex(); i <= theTable.getSelectionModel()
					.getMaxSelectionIndex(); i++) {
					if (theTable.getSelectionModel().isSelectedIndex(i))
						selectedRows.add(theRows.get(i));
				}
				int columnIndex = theTable.getSelectedColumn();
				if (columnIndex >= 0)
					columnIndex = theTable.convertColumnIndexToModel(columnIndex);
				CategoryRenderStrategy<? super R, ?> column = columnIndex >= 0 ? theColumns.get(columnIndex) : null;
				Transferable columnTransfer = null;
				if (column != null && column.getDragSource() != null) {
					Transferable[] columnTs = new Transferable[selectedRows.size()];
					for (int r = 0; r < selectedRows.size(); r++)
						columnTs[r] = ((Dragging.TransferSource<Object>) column.getDragSource())
						.createTransferable(column.getCategoryValue(selectedRows.get(r)));
					columnTransfer = columnTs.length == 1 ? columnTs[0] : new Dragging.AndTransferable(columnTs);
				}
				Transferable rowTransfer = null;
				if (theRowSource != null) {
					List<Transferable> rowTs = new ArrayList<>(selectedRows.size());
					ElementId[] rowIds = new ElementId[selectedRows.size()];
					int r = 0;
					for (int i = theTable.getSelectionModel().getMinSelectionIndex(); i <= theTable.getSelectionModel()
						.getMaxSelectionIndex(); i++) {
						if (theTable.getSelectionModel().isSelectedIndex(i)) {
							Transferable rowTr = theRowSource.createTransferable(selectedRows.get(r));
							if (rowTr != null)
								rowTs.add(rowTr);
							rowIds[r] = theFilteredRows.getElement(i).getElementId();
							r++;
						}
					}
					if (rowTs.isEmpty())
						rowTransfer = new RowTransferable(null, rowIds);
					else
						rowTransfer = new RowTransferable(
							rowTs.size() == 1 ? rowTs.get(0) : new Dragging.AndTransferable(rowTs.toArray(new Transferable[rowTs.size()])),
								rowIds);
				}
				if (rowTransfer != null && columnTransfer != null)
					return new Dragging.OrTransferable(rowTransfer, columnTransfer);
				else if (rowTransfer != null)
					return rowTransfer;
				else
					return columnTransfer;
			}
		}

		@Override
		public int getSourceActions(JComponent c) {
			int actions = 0;
			try (Transaction rowT = theRows.lock(false, null); Transaction colT = theColumns.lock(false, null)) {
				if (theTable.getSelectedRowCount() == 0)
					return actions;
				if (theRowSource != null)
					actions |= theRowSource.getSourceActions();
				int columnIndex = theTable.getSelectedColumn();
				if (columnIndex >= 0)
					columnIndex = theTable.convertColumnIndexToModel(columnIndex);
				CategoryRenderStrategy<? super R, ?> column = columnIndex >= 0 ? theColumns.get(columnIndex) : null;
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
			TableContentControl filter = theFilter == null ? null : theFilter.get();
			if (filter != null && filter.getRowSorting() != null) {
				return false; // Can't drag into the table when row-sorted
			}
			try (Transaction rowT = theRows.lock(true, support); Transaction colT = theColumns.lock(false, null)) {
				int rowIndex;
				boolean beforeRow;
				if (support.isDrop()) {
					rowIndex = theTable.rowAtPoint(support.getDropLocation().getDropPoint());
					if (rowIndex < 0) {
						rowIndex = theFilteredRows.size() - 1;
						beforeRow = false;
					} else {
						Rectangle bounds = theTable.getCellRect(rowIndex, 0, false);
						beforeRow = (support.getDropLocation().getDropPoint().y - bounds.y) <= bounds.height / 2;
					}
				} else {
					rowIndex = theTable.getSelectedRow();
					beforeRow = true;
				}
				ElementId targetRow = theFilteredRows.getElement(rowIndex).getElementId();
				ElementId after = beforeRow ? CollectionElement.getElementId(theFilteredRows.getAdjacentElement(targetRow, false))
					: targetRow;
				ElementId before = beforeRow ? targetRow
					: CollectionElement.getElementId(theFilteredRows.getAdjacentElement(targetRow, true));
				// Support row move before anything else
				if (support.getComponent() == theTable && support.isDrop()//
					&& (support.getSourceDropActions() & MOVE) != 0 && support.isDataFlavorSupported(ROW_ELEMENT_FLAVOR)) {
					// Moving rows by drag
					ElementId[] rowElements;
					try {
						rowElements = (ElementId[]) support.getTransferable().getTransferData(ROW_ELEMENT_FLAVOR);
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
						if (canMoveAll)
							return true;
					} catch (IOException | UnsupportedFlavorException e) {
						e.printStackTrace();
					}
				}
				if (theRowAccepter != null && support.getComponent() != theTable && theRowAccepter.canAccept(null, support, true)) {
					BetterList<R> newRows;
					try {
						newRows = theRowAccepter.accept(null, support.getTransferable(), true, true);
					} catch (IOException e) {
						newRows = null;
						// Ignore
					}
					if (newRows == null) {//
					} else {
						boolean allImportable = true;
						if (rowIndex < 0) {
							for (R row : newRows) {
								if (theRows.canAdd(row) != null) {
									allImportable = false;
									break;
								}
							}
						} else {
							for (R row : newRows) {
								if (theRows.canAdd(row, after, before) != null) {
									allImportable = false;
									break;
								}
							}
						}
						if (allImportable)
							return true;
					}
				}
				if (rowIndex >= 0) {
					int columnIndex = support.isDrop() ? theTable.columnAtPoint(support.getDropLocation().getDropPoint())
						: theTable.getSelectedColumn();
					if (columnIndex >= 0)
						columnIndex = theTable.convertColumnIndexToModel(columnIndex);
					CategoryRenderStrategy<? super R, ?> column = columnIndex >= 0 ? theColumns.get(columnIndex) : null;
					if (column != null && canImport(support, rowIndex, column, false))
						return true;
				}
			} catch (RuntimeException | Error e) {
				e.printStackTrace();
				throw e;
			}
			return false;
		}

		private <C> boolean canImport(TransferSupport support, int rowIndex, CategoryRenderStrategy<? super R, C> column,
			boolean doImport) {
			if (column.getMutator().getDragAccepter() == null)
				return false;
			CollectionElement<R> rowEl = theRows.getElement(rowIndex);
			C oldValue = column.getCategoryValue(rowEl.get());
			if (!column.getMutator().isEditable(rowEl.get(), oldValue))
				return false;
			boolean selected = theTable.isRowSelected(rowIndex);
			ModelCell<R, C> cell = new ModelCell.Default<>(rowEl::get, oldValue, rowIndex, theColumns.indexOf(column), selected, selected,
				false, false, false, true);
			if (!column.getMutator().getDragAccepter().canAccept(cell, support, false))
				return false;
			BetterList<C> newColValue;
			try {
				newColValue = column.getMutator().getDragAccepter().accept(cell, support.getTransferable(), false, !doImport);
			} catch (IOException e) {
				return false;
			}
			if (newColValue == null || ((CategoryRenderStrategy<R, C>) column).getMutator()//
				.isAcceptable(//
					theRows.mutableElement(rowEl.getElementId()), newColValue.getFirst()) != null)
				return false;
			if (doImport) {
				support.setDropAction(DnDConstants.ACTION_COPY_OR_MOVE);
				((CategoryRenderStrategy<R, C>) column).getMutator()//
				.mutate(//
					theRows.mutableElement(rowEl.getElementId()), newColValue.getFirst());
			}
			return true;
		}

		@Override
		public boolean importData(TransferSupport support) {
			try (Transaction rowT = theRows.lock(true, support); Transaction colT = theColumns.lock(false, null)) {
				int rowIndex;
				boolean beforeRow;
				if (support.isDrop()) {
					rowIndex = theTable.rowAtPoint(support.getDropLocation().getDropPoint());
					if (rowIndex < 0) {
						rowIndex = theFilteredRows.size() - 1;
						beforeRow = false;
					} else {
						Rectangle bounds = theTable.getCellRect(rowIndex, 0, false);
						beforeRow = (support.getDropLocation().getDropPoint().y - bounds.y) <= bounds.height / 2;
					}
				} else {
					rowIndex = theTable.getSelectedRow();
					beforeRow = true;
				}
				ElementId targetRow = theFilteredRows.getElement(rowIndex).getElementId();
				ElementId after = beforeRow ? CollectionElement.getElementId(theFilteredRows.getAdjacentElement(targetRow, false))
					: targetRow;
				ElementId before = beforeRow ? targetRow
					: CollectionElement.getElementId(theFilteredRows.getAdjacentElement(targetRow, true));
				// Support row move before anything else
				if (support.getComponent() == theTable && support.isDrop()//
					&& (support.getSourceDropActions() & MOVE) != 0 && support.isDataFlavorSupported(ROW_ELEMENT_FLAVOR)) {
					// Moving rows by drag
					ElementId[] rowElements;
					boolean moved = false;
					try {
						rowElements = (ElementId[]) support.getTransferable().getTransferData(ROW_ELEMENT_FLAVOR);
						for (ElementId rowEl : rowElements) {
							// Let's not throw an exception if the collection changed or something else happened
							// to make an item unmovable
							if (!rowEl.isPresent())
								continue;
							if (rowEl.equals(after) || rowEl.equals(before))
								continue;
							moved = true;
							if (theFilteredRows.canMove(rowEl, after, before) != null)
								continue;
							ElementId newRowEl = theFilteredRows.move(rowEl, after, before, true, null).getElementId();
							after = newRowEl;
						}
						if (moved)
							return true;
					} catch (IOException | UnsupportedFlavorException e) {
						e.printStackTrace();
					}
				}
				if (theRowAccepter != null && support.getComponent() != theTable && theRowAccepter.canAccept(null, support, true)) {
					BetterList<R> newRows;
					try {
						newRows = theRowAccepter.accept(null, support.getTransferable(), true, false);
					} catch (IOException e) {
						newRows = null;
						// Ignore
					}
					if (newRows == null) {//
					} else {
						boolean allImportable = true;
						if (rowIndex < 0) {
							for (R row : newRows) {
								if (theRows.canAdd(row) != null) {
									allImportable = false;
									break;
								}
							}
							if (allImportable) {
								theRows.addAll(newRows);
								return true;
							}
						} else {
							for (R row : newRows) {
								if (theFilteredRows.canAdd(row, after, before) != null) {
									allImportable = false;
									break;
								}
							}
							if (allImportable) {
								theFilteredRows.addAll(beforeRow ? rowIndex : rowIndex + 1, newRows);
								return true;
							}
						}
					}
				}
				if (rowIndex >= 0) {
					int columnIndex = support.isDrop() ? theTable.columnAtPoint(support.getDropLocation().getDropPoint())
						: theTable.getSelectedColumn();
					if (columnIndex >= 0)
						columnIndex = theTable.convertColumnIndexToModel(columnIndex);
					CategoryRenderStrategy<? super R, ?> column = columnIndex >= 0 ? theColumns.get(columnIndex) : null;
					if (column != null && canImport(support, rowIndex, column, true)) {
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

	static final DataFlavor ROW_ELEMENT_FLAVOR = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType, "Row Elements");

	static class RowTransferable implements Transferable {
		private final Transferable theWrapped;
		private final ElementId[] theRowElements;

		public RowTransferable(Transferable wrapped, ElementId[] rowElements) {
			theWrapped = wrapped;
			theRowElements = rowElements;
		}

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			if (theWrapped == null)
				return new DataFlavor[] { ROW_ELEMENT_FLAVOR };
			return ArrayUtils.add(theWrapped.getTransferDataFlavors(), ROW_ELEMENT_FLAVOR);
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			if (flavor == ROW_ELEMENT_FLAVOR)
				return true;
			else if (theWrapped != null)
				return theWrapped.isDataFlavorSupported(flavor);
			return false;
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			if (flavor == ROW_ELEMENT_FLAVOR)
				return theRowElements;
			else if (theWrapped != null)
				return theWrapped.getTransferData(flavor);
			throw new UnsupportedFlavorException(flavor);
		}
	}
}