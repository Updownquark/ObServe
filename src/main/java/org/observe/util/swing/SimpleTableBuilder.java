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
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.BoundedRangeModel;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.util.SafeObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.Dragging.SimpleTransferAccepter;
import org.observe.util.swing.Dragging.SimpleTransferSource;
import org.observe.util.swing.Dragging.TransferAccepter;
import org.observe.util.swing.Dragging.TransferSource;
import org.observe.util.swing.ObservableTableModel.RowMouseListener;
import org.observe.util.swing.PanelPopulation.AbstractComponentEditor;
import org.observe.util.swing.PanelPopulation.DataAction;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.SimpleDataAction;
import org.observe.util.swing.PanelPopulation.SimpleHPanel;
import org.observe.util.swing.PanelPopulation.TableBuilder;
import org.observe.util.swing.TableContentControl.FilteredValue;
import org.observe.util.swing.TableContentControl.ValueRenderer;
import org.qommons.ArrayUtils;
import org.qommons.IntList;
import org.qommons.LambdaUtils;
import org.qommons.StringUtils;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.debug.Debug;
import org.qommons.debug.Debug.DebugData;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

class SimpleTableBuilder<R, P extends SimpleTableBuilder<R, P>> extends AbstractComponentEditor<JTable, P>
implements TableBuilder<R, P> {
	private final ObservableCollection<R> theRows;
	private SafeObservableCollection<R> theSafeRows;
	private String theItemName;
	private Function<? super R, String> theNameFunction;
	private ObservableCollection<? extends CategoryRenderStrategy<? super R, ?>> theColumns;
	private SettableValue<R> theSelectionValue;
	private ObservableCollection<R> theSelectionValues;
	private List<Object> theActions;
	private ObservableValue<? extends TableContentControl> theFilter;
	private Dragging.SimpleTransferSource<R> theDragSource;
	private Dragging.SimpleTransferAccepter<R, R, R> theDragAccepter;
	private List<ObservableTableModel.RowMouseListener<? super R>> theMouseListeners;
	private int theAdaptiveMinRowHeight;
	private int theAdaptivePrefRowHeight;
	private int theAdaptiveMaxRowHeight;
	private boolean isScrollable;

	private Component theBuiltComponent;

	SimpleTableBuilder(ObservableCollection<R> rows, Supplier<Transactable> lock) {
		super(new JTable(), lock);
		theRows = rows;
		theActions = new LinkedList<>();
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
	public P withColumns(ObservableCollection<? extends CategoryRenderStrategy<? super R, ?>> columns) {
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
					setText(col.print(() -> theSafeRows.get(rowIndex), rowIndex));
					return this;
				}
			}, (__, c) -> String.valueOf(c)));
			if (column != null)
				column.accept(col);
		});
	}

	@Override
	public P withColumn(CategoryRenderStrategy<? super R, ?> column) {
		if (theColumns == null)
			theColumns = ObservableCollection.create(new TypeToken<CategoryRenderStrategy<? super R, ?>>() {
			}.where(new TypeParameter<R>() {
			}, TypeTokens.get().wrap(theRows.getType())));
		((ObservableCollection<CategoryRenderStrategy<? super R, ?>>) theColumns).add(column);
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
		return withMultiAction(values -> {
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
			.modifyButton(button -> button.withIcon(PanelPopulation.getAddIcon(16)).withTooltip("Add new " + getItemName()));
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
		return withMultiAction(deletion, action -> {
			action.allowForMultiple(true).withTooltip(items -> "Remove selected " + (items.size() == 1 ? single : plural))//
			.modifyButton(button -> button.withIcon(PanelPopulation.getRemoveIcon(16)));
			if (actionMod != null)
				actionMod.accept(action);
		});
	}

	@Override
	public P withCopy(Function<? super R, ? extends R> copier, Consumer<DataAction<R, ?>> actionMod) {
		return withMultiAction(values -> {
			try (Transaction t = theRows.lock(true, null)) {
				if (theSafeRows.hasQueuedEvents()) { // If there are queued changes, we can't rely on indexes we get back from the model
					simpleCopy(values, copier);
				} else {// Ignore the given values and use the selection model so we get the indexes right in the case of duplicates
					betterCopy(copier);
				}
			}
		}, action -> {
			String single = getItemName();
			String plural = StringUtils.pluralize(single);
			action.allowForMultiple(true).withTooltip(items -> "Duplicate selected " + (items.size() == 1 ? single : plural))//
			.modifyButton(button -> button.withIcon(PanelPopulation.getCopyIcon(16)));
			if (actionMod != null)
				actionMod.accept(action);
		});
	}

	private void simpleCopy(List<? extends R> selection, Function<? super R, ? extends R> copier) {
		for (R value : selection) {
			R copy = copier.apply(value);
			theRows.add(copy);
		}
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
		((ObservableCollection<CategoryRenderStrategy<? super R, ?>>) theColumns)
		.add(new CategoryRenderStrategy<>(up ? "\u2191" : "\u2193", TypeTokens.get().OBJECT, v -> null)
			.withHeaderTooltip("Move row " + (up ? "up" : "down")).decorateAll(deco -> deco.withIcon(PanelPopulation.getMoveIcon(up, 16)))//
			.withWidths(15, 20, 20)//
			.withMutation(
				m -> m.mutateAttribute2((r, c) -> c).withEditor(ObservableCellEditor.createButtonCellEditor(__ -> null, cell -> {
					if (up && cell.getRowIndex() == 0)
						return cell.getCellValue();
					else if (!up && cell.getRowIndex() == theSafeRows.size() - 1)
						return cell.getCellValue();
					try (Transaction t = theRows.lock(true, null)) {
						if (!theSafeRows.hasQueuedEvents()) {
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
					}
					return cell.getCellValue();
				}).decorate((cell, deco) -> {
					deco.withIcon(PanelPopulation.getMoveIcon(up, 16));
					if (up)
						deco.enabled(cell.getRowIndex() > 0);
					else
						deco.enabled(cell.getRowIndex() < theSafeRows.size() - 1);
				}))));
		return (P) this;
	}

	@Override
	public P withMoveToEnd(boolean up, Consumer<DataAction<R, ?>> actionMod) {
		return withMultiAction(values -> {
			try (Transaction t = theRows.lock(true, null)) {
				if (theSafeRows.hasQueuedEvents()) { // If there are queued changes, we can't rely on indexes we get back from the model
				} else {// Ignore the given values and use the selection model so we get the indexes right in the case of duplicates
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
			}
		}, action -> {
			ObservableValue<String> enabled;
			Supplier<String> enabledGet = () -> {
				try (Transaction t = theRows.lock(false, null)) {
					if (theSafeRows.hasQueuedEvents())
						return "Data set updating";
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
			.modifyButton(button -> button.withIcon(PanelPopulation.getMoveEndIcon(up, 16)))//
			.allowForEmpty(false).allowForAnyEnabled(false).modifyAction(a -> a.disableWith(enabled));
		});
	}

	@Override
	public P withMultiAction(Consumer<? super List<? extends R>> action, Consumer<DataAction<R, ?>> actionMod) {
		SimpleDataAction<R, ?> ta = new SimpleDataAction<>(this, action, this::getSelection);
		actionMod.accept(ta);
		theActions.add(ta);
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

	@Override
	public Component getOrCreateComponent(Observable<?> until) {
		if (theBuiltComponent != null)
			return theBuiltComponent;
		if (theColumns == null)
			throw new IllegalStateException("No columns configured");
		theSafeRows = new SafeObservableCollection<>(theRows, EventQueue::isDispatchThread, EventQueue::invokeLater, until);
		ObservableTableModel<R> model;
		ObservableCollection<TableContentControl.FilteredValue<R>> filtered;
		if (theFilter != null) {
			ObservableCollection<CategoryRenderStrategy<? super R, ?>> safeColumns = new SafeObservableCollection<>(//
				(ObservableCollection<CategoryRenderStrategy<? super R, ?>>) TableContentControl.applyColumnControl(theColumns,
					theFilter, until),
				EventQueue::isDispatchThread, EventQueue::invokeLater, until);
			Observable<?> columnChanges = Observable.or(Observable.constant(null), safeColumns.simpleChanges());
			List<ValueRenderer<R>> renderers = new ArrayList<>();
			columnChanges.act(__ -> {
				renderers.clear();
				for (CategoryRenderStrategy<? super R, ?> column : safeColumns) {
					renderers.add(new TableContentControl.ValueRenderer<R>() {
						@Override
						public String getName() {
							return column.getName();
						}

						@Override
						public boolean searchGeneral() {
							return column.isFilterable();
						}

						@Override
						public CharSequence render(R row) {
							return column.print(row);
						}

						@Override
						public int compare(R o1, R o2) {
							Object c1 = column.getCategoryValue(o1);
							Object c2 = column.getCategoryValue(o2);
							if (c1 instanceof String && c2 instanceof String)
								return StringUtils.compareNumberTolerant((String) c1, (String) c2, true, true);
							else if (c1 instanceof Comparable && c2 instanceof Comparable) {
								try {
									return ((Comparable<Object>) c1).compareTo(c2);
								} catch (ClassCastException e) {
									// Ignore
								}
							}
							return 0;
						}
					});
				}
			});
			filtered = TableContentControl.applyRowControl(theSafeRows, () -> renderers, theFilter.refresh(columnChanges), until);
			ObservableCollection<R> filteredValues = filtered.flow()
				.transform(theRows.getType(),
					tx -> tx.map(LambdaUtils.printableFn(f -> f.value, "value", null)).modifySource(FilteredValue::setValue))
				.collectActive(until);
			DebugData d = Debug.d().debug(theRows);
			if (d.isActive())
				Debug.d().debug(filteredValues, true).merge(d);
			model = new ObservableTableModel<>(filteredValues, true, safeColumns, true);
		} else {
			filtered = null;
			model = new ObservableTableModel<>(theSafeRows, true, theColumns, false);
		}
		JTable table = getEditor();
		table.setModel(model);
		if(theMouseListeners!=null) {
			for(ObservableTableModel.RowMouseListener<? super R> listener : theMouseListeners)
				model.addMouseListener(listener);
		}
		Subscription sub = ObservableTableModel.hookUp(table, model, //
			filtered == null ? null : new ObservableTableModel.TableRenderContext() {
			@Override
			public int[][] getEmphaticRegions(int row, int column) {
				TableContentControl.FilteredValue<R> fv = filtered.get(row);
				if (column >= fv.getColumns() || !fv.isFiltered())
					return null;
				return fv.getMatches(column);
			}
		});
		if (until != null)
			until.take(1).act(__ -> sub.unsubscribe());

		JScrollPane scroll = new JScrollPane(table);
		if (isScrollable) {
			// Default scroll increments are ridiculously small
			scroll.getVerticalScrollBar().setUnitIncrement(10);
			scroll.getHorizontalScrollBar().setUnitIncrement(10);
			if (theAdaptivePrefRowHeight > 0) {
				class HeightAdjustmentListener implements ListDataListener, ChangeListener {
					private boolean isHSBVisible;
					private boolean isVSBVisible;

					@Override
					public void intervalRemoved(ListDataEvent e) {
						if (e.getIndex0() < theAdaptiveMaxRowHeight)
							adjustHeight();
					}

					@Override
					public void intervalAdded(ListDataEvent e) {
						if (e.getIndex0() < theAdaptiveMaxRowHeight)
							adjustHeight();
					}

					@Override
					public void contentsChanged(ListDataEvent e) {
						if (e.getIndex0() < theAdaptiveMaxRowHeight)
							adjustHeight();
					}

					@Override
					public void stateChanged(ChangeEvent e) {
						BoundedRangeModel hbm = scroll.getHorizontalScrollBar().getModel();
						if (hbm.getValueIsAdjusting())
							return;
						if (isHSBVisible != (hbm.getExtent() > hbm.getMaximum())) {
							adjustHeight();
						} else {
							BoundedRangeModel vbm = scroll.getVerticalScrollBar().getModel();
							if (vbm.getValueIsAdjusting())
								return;
							if (isVSBVisible != (vbm.getExtent() > vbm.getMaximum()))
								adjustHeight();
						}
					}

					void adjustHeight() {
						int minHeight = 0, prefHeight = 0, maxHeight = 0;
						if (table.getTableHeader() != null && table.getTableHeader().isVisible()) {
							minHeight += table.getTableHeader().getPreferredSize().height;
							maxHeight += table.getTableHeader().getPreferredSize().height;
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
						BoundedRangeModel hbm = scroll.getHorizontalScrollBar().getModel();
						isHSBVisible = hbm.getExtent() > hbm.getMaximum();
						if (isHSBVisible) {
							int sbh = scroll.getHorizontalScrollBar().getHeight();
							minHeight += sbh;
							maxHeight += sbh;
						}
						BoundedRangeModel vbm = scroll.getVerticalScrollBar().getModel();
						isVSBVisible = vbm.getExtent() > vbm.getMaximum();
						Dimension psvs = table.getPreferredScrollableViewportSize();
						if (psvs.height != prefHeight) {
							int w = 0;
							for (int c = 0; c < table.getColumnModel().getColumnCount(); c++)
								w += table.getColumnModel().getColumn(c).getWidth();
							table.setPreferredScrollableViewportSize(new Dimension(psvs.width, prefHeight));
						}
						Dimension min = scroll.getMinimumSize();
						if (min.height != minHeight) {
							int w = 10;
							if (isVSBVisible)
								w += scroll.getVerticalScrollBar().getWidth();
							scroll.getViewport().setMinimumSize(new Dimension(w, minHeight));
						}
						Dimension max = scroll.getMaximumSize();
						if (max.height != maxHeight) {
							int w = 0;
							if (isVSBVisible)
								w += scroll.getVerticalScrollBar().getWidth();
							for (int c = 0; c < model.getColumnCount(); c++) {
								w += table.getColumnModel().getColumn(c).getMaxWidth();
								if (w < 0) {
									w = Integer.MAX_VALUE;
									break;
								}
							}
							scroll.getViewport().setMaximumSize(new Dimension(w, maxHeight));
						}
						if (scroll.getParent() != null)
							scroll.getParent().revalidate();
					}
				}
				HeightAdjustmentListener hal = new HeightAdjustmentListener();
				model.getRowModel().addListDataListener(hal);
				model.getColumnModel().addListDataListener(hal);
				scroll.getHorizontalScrollBar().getModel().addChangeListener(hal);
				scroll.getVerticalScrollBar().getModel().addChangeListener(hal);
				hal.adjustHeight();
			}
		} else {
			scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		}

		// Selection
		Supplier<List<R>> selectionGetter = () -> getSelection();
		if (theSelectionValue != null)
			ObservableSwingUtils.syncSelection(table, model.getRowModel(), table::getSelectionModel, model.getRows().equivalence(),
				theSelectionValue, until, index -> {
					if (index >= getRows().size())
						return;
					MutableCollectionElement<R> el = (MutableCollectionElement<R>) getRows()
						.mutableElement(getRows().getElement(index).getElementId());
					if (el.isAcceptable(el.get()) == null)
						el.set(el.get());
				}, false);
		if (theSelectionValues != null)
			ObservableSwingUtils.syncSelection(table, model.getRowModel(), table::getSelectionModel, model.getRows().equivalence(),
				theSelectionValues, until);
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
					if (selModel.getMinSelectionIndex() >= 0 && e.getIndex0() >= selModel.getMinSelectionIndex()
						&& e.getIndex1() <= selModel.getMaxSelectionIndex()) {
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
			until.take(1).act(__ -> {
				table.removePropertyChangeListener("selectionModel", selModelListener);
				table.getSelectionModel().removeListSelectionListener(selListener);
				model.getRowModel().removeListDataListener(dataListener);
			});
			SimpleHPanel<JPanel> buttonPanel = new SimpleHPanel<>(null,
				new JPanel(new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING)), theLock, until);
			for (Object action : theActions) {
				if (action instanceof SimpleDataAction)
					((SimpleDataAction<R, ?>) action).addButton(buttonPanel);
				else if (action instanceof Consumer)
					buttonPanel.addHPanel(null, "box", (Consumer<PanelPopulator<JPanel, ?>>) action);
			}
			JPanel tablePanel = new JPanel(new BorderLayout());
			tablePanel.add(buttonPanel.getOrCreateComponent(until), BorderLayout.NORTH);
			tablePanel.add(scroll, BorderLayout.CENTER);
			theBuiltComponent = tablePanel;
		} else
			theBuiltComponent = scroll;
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

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

		return decorate(theBuiltComponent);
	}

	@Override
	public Component getComponent() {
		return theBuiltComponent;
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
							rowIds[r] = theSafeRows.getElement(i).getElementId();
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
						rowIndex = theSafeRows.size() - 1;
						beforeRow = false;
					} else {
						Rectangle bounds = theTable.getCellRect(rowIndex, 0, false);
						beforeRow = (support.getDropLocation().getDropPoint().y - bounds.y) <= bounds.height / 2;
					}
				} else {
					rowIndex = theTable.getSelectedRow();
					beforeRow = true;
				}
				ElementId targetRow = theSafeRows.getElement(rowIndex).getElementId();
				ElementId after = beforeRow ? CollectionElement.getElementId(theSafeRows.getAdjacentElement(targetRow, false)) : targetRow;
				ElementId before = beforeRow ? targetRow : CollectionElement.getElementId(theSafeRows.getAdjacentElement(targetRow, true));
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
							if (theSafeRows.canMove(rowEl, after, before) != null) {
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
				false, true);
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
						rowIndex = theSafeRows.size() - 1;
						beforeRow = false;
					} else {
						Rectangle bounds = theTable.getCellRect(rowIndex, 0, false);
						beforeRow = (support.getDropLocation().getDropPoint().y - bounds.y) <= bounds.height / 2;
					}
				} else {
					rowIndex = theTable.getSelectedRow();
					beforeRow = true;
				}
				ElementId targetRow = theSafeRows.getElement(rowIndex).getElementId();
				ElementId after = beforeRow ? CollectionElement.getElementId(theSafeRows.getAdjacentElement(targetRow, false)) : targetRow;
				ElementId before = beforeRow ? targetRow : CollectionElement.getElementId(theSafeRows.getAdjacentElement(targetRow, true));
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
							if (theSafeRows.canMove(rowEl, after, before) != null)
								continue;
							ElementId newRowEl = theSafeRows.move(rowEl, after, before, true, null).getElementId();
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
								if (theSafeRows.canAdd(row, after, before) != null) {
									allImportable = false;
									break;
								}
							}
							if (allImportable) {
								theSafeRows.addAll(beforeRow ? rowIndex : rowIndex + 1, newRows);
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