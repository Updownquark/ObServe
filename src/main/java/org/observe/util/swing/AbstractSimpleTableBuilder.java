package org.observe.util.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.BoundedRangeModel;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.table.TableColumn;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.ObservableCollection;
import org.observe.util.ObservableCollectionSynchronization;
import org.observe.util.ObservableUtils;
import org.observe.util.swing.Dragging.GridFlavor;
import org.observe.util.swing.Dragging.GridTransferable;
import org.observe.util.swing.Dragging.SimpleTransferAccepter;
import org.observe.util.swing.Dragging.SimpleTransferSource;
import org.observe.util.swing.Dragging.TransferAccepter;
import org.observe.util.swing.Dragging.TransferSource;
import org.observe.util.swing.Dragging.TransferWrapper;
import org.observe.util.swing.ObservableCellRenderer.CellRenderContext;
import org.observe.util.swing.PanelPopulation.AbstractTableBuilder;
import org.observe.util.swing.PanelPopulation.CollectionWidgetBuilder;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.DataAction;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.SimpleComponentEditor;
import org.observe.util.swing.PanelPopulationImpl.SimpleDataAction;
import org.observe.util.swing.PanelPopulationImpl.SimpleHPanel;
import org.qommons.IntList;
import org.qommons.StringUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;

public abstract class AbstractSimpleTableBuilder<R, T extends JTable, P extends AbstractSimpleTableBuilder<R, T, P>>
extends SimpleComponentEditor<T, P> implements AbstractTableBuilder<R, T, P>, CollectionWidgetBuilder<R, T, P> {
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

	private String theItemName;
	private ObservableCollection<CategoryRenderStrategy<R, ?>> theSimpleColumns;
	private ObservableCollection<ObservableCollection<? extends CategoryRenderStrategy<R, ?>>> theComplexColumns;
	private ObservableCollection<? extends CategoryRenderStrategy<R, ?>> theFlatColumns;
	private SettableValue<R> theSelectionValue;
	private ObservableCollection<R> theSelectionValues;
	private SettableValue<Object> theColumnSelectionValue;
	private ObservableCollection<Object> theColumnSelectionValues;
	private List<Object> theActions;
	private boolean theActionsOnTop;
	private Dragging.SimpleTransferSource<R, ?> theDragSource;
	private Dragging.SimpleTransferAccepter<R, ?> theDragAccepter;
	private List<AbstractObservableTableModel.RowMouseListener<? super R>> theMouseListeners;
	private int theAdaptiveMinRowHeight;
	private int theAdaptivePrefRowHeight;
	private int theAdaptiveMaxRowHeight;
	private boolean withColumnHeader;
	private boolean isScrollable;

	protected AbstractSimpleTableBuilder(ComponentEditor<?, ?> parent, ObservableCollection<R> rows, Observable<?> until) {
		this(parent, rows, (T) new NoLayoutTable(), until);
	}

	protected AbstractSimpleTableBuilder(ComponentEditor<?, ?> parent, ObservableCollection<R> rows, T table, Observable<?> until) {
		super(parent, null, table, until);
		getEditor().setFillsViewportHeight(true);
		theActions = new LinkedList<>();
		theActionsOnTop = true;
		withColumnHeader = true;
		isScrollable = true;
	}

	@Override
	public String getItemName() {
		if (theItemName == null)
			return "item";
		else
			return theItemName;
	}

	@Override
	public P withItemName(String itemName) {
		theItemName = itemName;
		return (P) this;
	}

	protected ObservableCollection<? extends CategoryRenderStrategy<R, ?>> getColumns() {
		if (theFlatColumns != null)
			return theFlatColumns;
		else if (theSimpleColumns == null)
			theSimpleColumns = ObservableCollection.<CategoryRenderStrategy<R, ?>> build().onEdt().build();
		return theSimpleColumns;
	}

	protected SettableValue<R> getSelectionValue() {
		return theSelectionValue;
	}

	protected ObservableCollection<R> getSelectionValues() {
		return theSelectionValues;
	}

	@Override
	public P withColumns(ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns) {
		if (theComplexColumns == null) {
			theComplexColumns = ObservableCollection.<ObservableCollection<? extends CategoryRenderStrategy<R, ?>>> build().onEdt().build();
			theFlatColumns = theComplexColumns.flow()//
				.flatMap(c -> c.flow())//
				.collect();
			if (theSimpleColumns != null)
				theComplexColumns.add(theSimpleColumns);
		}
		theSimpleColumns = null;
		theComplexColumns.add(columns);
		return (P) this;
	}

	@Override
	public P withColumn(CategoryRenderStrategy<R, ?> column) {
		if (theSimpleColumns == null) {
			theSimpleColumns = ObservableCollection.<CategoryRenderStrategy<R, ?>> build().onEdt().build();
			if (theComplexColumns != null)
				theComplexColumns.add(theSimpleColumns);
		}
		theSimpleColumns.add(column);
		return (P) this;
	}

	@Override
	public P withSelection(SettableValue<R> selection, boolean enforceSingleSelection) {
		if (enforceSingleSelection)
			getEditor().getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		if (theSelectionValue == null) {
			theSelectionValue = selection;
		} else {
			Subscription linkSub = ObservableUtils.link(theSelectionValue, selection);
			getUntil().take(1).act0(linkSub::unsubscribe);
		}
		return (P) this;
	}

	@Override
	public P withSelection(ObservableCollection<R> selection) {
		if (theSelectionValues == null) {
			theSelectionValues = selection;
		} else {
			Subscription linkSub = ObservableCollectionSynchronization.synchronize(theSelectionValues, selection)//
				.synchronize();
			getUntil().take(1).act0(linkSub::unsubscribe);
		}
		return (P) this;
	}

	@Override
	public P withColumnSelection(SettableValue<Object> selection, boolean enforceSingleSelection) {
		theColumnSelectionValue = selection;
		if (enforceSingleSelection)
			getEditor().getColumnModel().getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		return (P) this;
	}

	@Override
	public P withColumnSelection(ObservableCollection<Object> selection) {
		theColumnSelectionValues = selection;
		return (P) this;
	}

	@Override
	public P withSelectionMode(int mode) {
		getEditor().getSelectionModel().setSelectionMode(mode);
		return (P) this;
	}

	@Override
	public P rowSelection(boolean rowSelection) {
		getEditor().setRowSelectionAllowed(rowSelection);
		return (P) this;
	}

	@Override
	public P columnSelection(boolean columnSelection) {
		getEditor().setColumnSelectionAllowed(columnSelection);
		return (P) this;
	}

	@Override
	public P withColumnSelectionMode(int mode) {
		getEditor().getColumnModel().getSelectionModel().setSelectionMode(mode);
		return (P) this;
	}

	@Override
	public P withColumnHeader(boolean columnHeader) {
		withColumnHeader = columnHeader;
		return (P) this;
	}

	@Override
	public P withAdaptiveHeight(int minRows, int prefRows, int maxRows) {
		boolean bad = false;
		if (minRows >= 0) {
			bad |= prefRows >= 0 && prefRows < minRows;
			bad |= maxRows >= 0 && maxRows < minRows;
		}
		bad |= prefRows >= 0 && maxRows >= 0 && maxRows < prefRows;
		if (bad)
			throw new IllegalArgumentException(
				"Required: min<=pref<=max: " + minRows + ", " + prefRows + ", " + maxRows + " except any that are negative (ignore)");
		theAdaptiveMinRowHeight = minRows;
		theAdaptivePrefRowHeight = prefRows;
		theAdaptiveMaxRowHeight = maxRows;
		return (P) this;
	}

	@Override
	public abstract List<R> getSelection();

	@Override
	public P withMouseListener(AbstractObservableTableModel.RowMouseListener<? super R> listener) {
		if (theMouseListeners == null)
			theMouseListeners = new ArrayList<>();
		theMouseListeners.add(listener);
		return (P) this;
	}

	@Override
	public P withRemove(Consumer<? super List<? extends R>> deletion, Consumer<DataAction<R, ?>> actionMod) {
		String single = getItemName();
		String plural = StringUtils.pluralize(single);
		if (deletion == null)
			deletion = defaultDeletion();
		return withMultiAction(null, deletion, action -> {
			action.allowForMultiple(true).withTooltip(items -> "Remove selected " + (items.size() == 1 ? single : plural))//
			.modifyButton(button -> button.withIcon(PanelPopulationImpl.getRemoveIcon(16)));
			if (actionMod != null)
				actionMod.accept(action);
		});
	}

	protected abstract Consumer<? super List<? extends R>> defaultDeletion();

	@Override
	public P withMultiAction(String actionName, Consumer<? super List<? extends R>> action, Consumer<DataAction<R, ?>> actionMod) {
		SimpleDataAction<R, ?> ta = new SimpleDataAction<>(actionName, this, action, true, getUntil());
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
	public P dragSourceRow(Consumer<? super TransferSource<R, ?>> source) {
		if (theDragSource == null)
			theDragSource = new SimpleTransferSource<>();
		// if (source == null)
		// throw new IllegalArgumentException("Drag sourcing must be configured");
		if (source != null)
			source.accept(theDragSource);
		return (P) this;
	}

	@Override
	public P dragAcceptRow(Consumer<? super TransferAccepter<R, ?>> accept) {
		if (theDragAccepter == null)
			theDragAccepter = new SimpleTransferAccepter<>();
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

	static void handleColumnHeaderClick(ObservableValue<? extends TableContentControl> filter, String columnName, Object cause) {
		TableContentControl filterV = filter.get();
		TableContentControl sorted = filterV == null ? new TableContentControl.RowSorter(Arrays.asList(columnName))
			: filterV.toggleSort(columnName, true);
		SettableValue<TableContentControl> settableFilter = (SettableValue<TableContentControl>) filter;
		if (settableFilter.isAcceptable(sorted) == null)
			settableFilter.set(sorted, cause);
	}

	protected ObservableCollection<? extends CategoryRenderStrategy<R, ?>> createColumnSet() {
		return getColumns();
	}

	protected abstract AbstractObservableTableModel<R> createTableModel(
		ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns);

	protected abstract AbstractObservableTableModel.TableRenderContext createTableRenderContext();

	protected abstract void syncSelection(T table, AbstractObservableTableModel<R> model, SettableValue<R> selection,
		boolean enforceSingle);

	protected abstract void syncMultiSelection(T table, AbstractObservableTableModel<R> model, ObservableCollection<R> selection);

	protected abstract TransferHandler setUpDnD(T table, SimpleTransferSource<R, ?> dragSource, SimpleTransferAccepter<R, ?> dragAccepter);

	protected abstract void onVisibleData(AbstractObservableTableModel<R> model, Consumer<CollectionChangeEvent<R>> onChange);

	protected abstract void forAllVisibleData(AbstractObservableTableModel<R> model, Consumer<ModelRow<R>> forEach);

	protected abstract ObservableCollection<? extends CategoryRenderStrategy<R, ?>> getDisplayedColumns();

	protected boolean isTransferConfigured() {
		return theDragSource != null || theDragAccepter != null;
	}

	protected boolean isDraggable() {
		return (theDragSource != null && theDragSource.isDraggable()) || (theDragAccepter != null && theDragAccepter.isDraggable());
	}

	@Override
	protected Component createComponent() {
		ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns = createColumnSet().safe(ThreadConstraint.EDT, getUntil());

		AbstractObservableTableModel<R> model = createTableModel(columns);

		T table = getEditor();
		// Tooltip control could mess up cell tooltips and stuff
		withTooltipControl(false);
		if (!withColumnHeader)
			table.setTableHeader(null);
		if (theMouseListeners != null) {
			for (ObservableTableModel.RowMouseListener<? super R> listener : theMouseListeners)
				model.addMouseListener(listener);
		}
		Subscription sub = model.hookUp(table, createTableRenderContext());
		getUntil().take(1).act(__ -> sub.unsubscribe());

		JScrollPane scroll = new JScrollPane(table);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		SizeListener sizeListener = new SizeListener(scroll, table, model);
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
		if (theSelectionValue != null)
			syncSelection(table, model, theSelectionValue, false);
		// Sync multi-selection so we can control the actions if nothing else
		ObservableCollection<R> multiSelection = ObservableCollection.create(b -> b.onEdt());
		syncMultiSelection(table, model, multiSelection);
		if (PanelPopulation.isDebugging(getEditor().getName(), "multiSelect"))
			multiSelection.simpleChanges().takeUntil(getUntil()).act(__ -> System.out.println("Selection=" + multiSelection));
		if (theSelectionValues != null) {
			// ObservableUtils.link(multiSelection, theSelectionValues);
			Subscription selSyncSub = ObservableCollectionSynchronization.synchronize(multiSelection, theSelectionValues)//
				.synchronize();
			getUntil().take(1).act(__ -> selSyncSub.unsubscribe());
		}
		if (theColumnSelectionValue != null || theColumnSelectionValues != null) {
			ObservableListModel<CategoryRenderStrategy<R, ?>> columnModel = (ObservableListModel<CategoryRenderStrategy<R, ?>>) model
				.getColumnModel();
			Function<Object, CategoryRenderStrategy<R, ?>> columnFinder = id -> {
				if (id == null)
					return null;
				for (int c = 0; c < model.getColumnCount(); c++) {
					if (id.equals(model.getColumn(c).getIdentifier()))
						return model.getColumn(c);
				}
				return null;
			};
			if (theColumnSelectionValues != null) {
				ObservableListSelectionModel<CategoryRenderStrategy<R, ?>> selectionModel = new ObservableListSelectionModel<>(columnModel,
					null, getUntil());
				table.getColumnModel().setSelectionModel(selectionModel);
				ObservableCollection<CategoryRenderStrategy<R, ?>> selectedColumnIds = theColumnSelectionValues.flow()//
					.<CategoryRenderStrategy<R, ?>> transform(
						tx -> tx.map(columnFinder).withReverse(c -> c == null ? null : c.getIdentifier()))//
					.collectActive(getUntil());
				Subscription syncSub = ObservableCollectionSynchronization.synchronize(selectedColumnIds, selectionModel).strictOrder()
					.synchronize();
				getUntil().take(1).act(__ -> syncSub.unsubscribe());
			}
			if (theColumnSelectionValue != null) {
				SettableValue<CategoryRenderStrategy<R, ?>> selectedColumn = theColumnSelectionValue.transformReversible(tx -> tx//
					.map(columnFinder).withReverse(c -> c == null ? null : c.getIdentifier()));
				ObservableSwingUtils.syncSelection(getEditor(), (ObservableListModel<CategoryRenderStrategy<R, ?>>) model.getColumnModel(),
					() -> getEditor().getColumnModel().getSelectionModel(), Equivalence.DEFAULT, selectedColumn, getUntil(), (idx, c) -> {
						if (idx > getDisplayedColumns().size())
							return;
						CollectionElement<?> cEl = getDisplayedColumns().getElement(idx);
						((MutableCollectionElement<CategoryRenderStrategy<R, Object>>) getDisplayedColumns()
							.mutableElement(cEl.getElementId())).set((CategoryRenderStrategy<R, Object>) cEl.get());
					});
			}
		}

		JComponent comp;
		if (!theActions.isEmpty()) {
			boolean hasPopups = false, hasButtons = false;
			for (Object action : theActions) {
				if (!(action instanceof SimpleDataAction))
					hasButtons = true;
				else {
					if (((SimpleDataAction<R, ?>) action).isPopup())
						hasPopups = true;
					if (((SimpleDataAction<R, ?>) action).isButton())
						hasButtons = true;
				}
			}
			for (Object action : theActions) {
				if (action instanceof SimpleDataAction)
					((SimpleDataAction<R, ?>) action).init(multiSelection);
			}
			if (hasPopups) {
				withPopupMenu(popupMenu -> {
					for (Object action : theActions) {
						if (action instanceof SimpleDataAction && ((SimpleDataAction<R, ?>) action).isPopup()) {
							SimpleDataAction<R, ?> dataAction = (SimpleDataAction<R, ?>) action;
							popupMenu.withAction("Action", dataAction.theObservableAction, dataAction::modifyButtonEditor);
						}
					}
				});
			}
			if (hasButtons) {
				SimpleHPanel<JPanel, ?> buttonPanel = new SimpleHPanel<>(this, null,
					new JPanel(new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING)), getUntil());
				for (Object action : theActions) {
					if (action instanceof SimpleDataAction) {
						if (((SimpleDataAction<?, ?>) action).isButton())
							((SimpleDataAction<R, ?>) action).addButton(buttonPanel);
					} else if (action instanceof Consumer)
						((Consumer<PanelPopulator<JPanel, ?>>) action).accept(buttonPanel);
				}
				JPanel tablePanel = new JPanel(new BorderLayout());
				tablePanel.add(buttonPanel.getComponent(), theActionsOnTop ? BorderLayout.NORTH : BorderLayout.SOUTH);
				tablePanel.add(scroll, BorderLayout.CENTER);
				comp = tablePanel;
			} else
				comp = scroll;
		} else
			comp = scroll;

		// Set up transfer handling (DnD, copy/paste)
		TransferHandler handler = setUpDnD(table, theDragSource, theDragAccepter);
		if (isTransferConfigured()) {
			if (isDraggable()) {
				table.setDragEnabled(isDraggable());
			} else {
				configureColumnsDraggable(columns);
			}
			table.setDropMode(DropMode.INSERT_ROWS);
			table.setTransferHandler(handler);
		} else {
			configureColumnsTransferable(columns, handler);
			configureColumnsDraggable(columns);
		}

		decorate(comp);
		return comp;
	}

	private void configureColumnsTransferable(ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns,
		TransferHandler handler) {
		ObservableValue<Boolean> columnsTransferable = columns.reduce(0, (count, column) -> {
			if (column.getDragSource() != null || column.getMutator().getDragAccepter() != null)
				return count + 1;
			else
				return count;
		}, (count, column) -> {
			if (column.getDragSource() != null || column.getMutator().getDragAccepter() != null)
				return count - 1;
			else
				return count;
		}).map(count -> count > 0);
		columnsTransferable.changes().takeUntil(getUntil()).filter(evt -> !evt.getNewValue().equals(evt.getOldValue())).act(evt -> {
			if (evt.getNewValue().booleanValue()) {
				getEditor().setDropMode(DropMode.INSERT_ROWS);
				getEditor().setTransferHandler(handler);
			} else {
				getEditor().setTransferHandler(null);
			}
		});
	}

	private void configureColumnsDraggable(ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns) {
		ObservableValue<Boolean> columnsDraggable = columns.reduce(0, (count, column) -> {
			if ((column.getDragSource() != null && column.getDragSource().isDraggable())//
				|| (column.getMutator().getDragAccepter() != null && column.getMutator().getDragAccepter().isDraggable()))
				return count + 1;
			else
				return count;
		}, (count, column) -> {
			if ((column.getDragSource() != null && column.getDragSource().isDraggable())//
				|| (column.getMutator().getDragAccepter() != null && column.getMutator().getDragAccepter().isDraggable()))
				return count - 1;
			else
				return count;
		}).map(count -> count > 0);
		columnsDraggable.changes().takeUntil(getUntil()).filter(evt -> !evt.getNewValue().equals(evt.getOldValue()))
		.act(evt -> getEditor().setDragEnabled(evt.getNewValue()));
	}

	class SizeListener implements ComponentListener, HierarchyListener, MouseListener, MouseMotionListener {
		private final JScrollPane scroll;
		private final JTable table;
		private final AbstractObservableTableModel<R> model;

		private final List<int[]> theColumnWidths;
		private int theResizingColumn;
		private int theResizingColumnOrigWidth;
		private int theResizingPreColumnWidth;
		private int theDragStart;
		private int theMaxSetAdaptiveHeight;

		/**
		 * This integer is how much the user has resized columns beyond the scroll pane's width.<br />
		 * This helps us with column layout.
		 */
		private int theTableExtraWidth;

		SizeListener(JScrollPane scroll, JTable table, AbstractObservableTableModel<R> model) {
			this.scroll = scroll;
			this.table = table;
			this.model = model;
			theMaxSetAdaptiveHeight = theAdaptiveMaxRowHeight;
			if (theMaxSetAdaptiveHeight < 0)
				theMaxSetAdaptiveHeight = theAdaptivePrefRowHeight;
			if (theMaxSetAdaptiveHeight < 0)
				theMaxSetAdaptiveHeight = theAdaptiveMinRowHeight;
			ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns = model.getColumns();
			theResizingColumn = -1;
			theColumnWidths = new ArrayList<>();
			for (int c = 0; c < columns.size(); c++) {
				int[] widths = new int[4]; // min, pref, max, and actual
				theColumnWidths.add(widths);
				getColumnWidths(model, columns.get(c), c, widths, columns.size(), null);
				TableColumn column = table.getColumnModel().getColumn(table.convertColumnIndexToView(c));
				column.setMinWidth(widths[0]);
				column.setMaxWidth(widths[2]);
				widths[3] = widths[1];
			}
			adjustScrollWidths();
			onVisibleData(model, evt -> EventQueue.invokeLater(() -> {
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
							getColumnWidths(model, column, c, newWidths, columns.size(), evt);
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
							getColumnWidths(model, column, c, newWidths, columns.size(), null);
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
				if (adjusted) {
					adjustScrollWidths();
				}
			}));
			columns.changes().act(evt -> {
				theResizingColumn = -1;
				boolean adjust = false;
				for (CollectionChangeEvent.ElementChange<? extends CategoryRenderStrategy<R, ?>> change : evt.getElements()) {
					switch (evt.type) {
					case add:
						int[] widths = new int[4];
						theColumnWidths.add(change.index, widths);
						getColumnWidths(model, change.newValue, change.index, widths, columns.size(), null);
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
				if (adjust) {
					adjustScrollWidths();
				}
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
			int tableSize = scroll.getViewport().getWidth() - (table.getColumnCount() - 1) * table.getColumnModel().getColumnMargin();
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
			init();
		}

		void init() {
			adjustHeight();
			adjustScrollWidths();
			theScrollWidth = scroll.getWidth();
			if (theScrollWidth <= 0)
				return;
			int tableSize = table.getWidth() + (getEditor().getColumnCount() - 1) * table.getColumnModel().getColumnMargin();
			if (tableSize > theScrollWidth)
				theTableExtraWidth = tableSize - theScrollWidth;
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
			long time = System.currentTimeMillis();
			if (time - theLastHE < 5)
				return;
			theLastHE = time;
			init();
		}

		void adjustScrollWidths() {
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

			if (table.getColumnModel().getColumnCount() == 0)
				maxW = Integer.MAX_VALUE;
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
			int tableSize = scroll.getWidth() - (getEditor().getColumnCount() - 1) * table.getColumnModel().getColumnMargin()//
				+ theTableExtraWidth;
			if (tableSize <= 0)
				return;
			isRecursive = true;
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
				// Now everything is equally squished. Distribute any extra space equally.
				BitSet expandableColumns = new BitSet(theColumnWidths.size());
				expandableColumns.set(0, theColumnWidths.size());
				for (boolean changed = true; size > 0 && !expandableColumns.isEmpty() && changed;) {
					changed = false;
					for (int c = 0; c < theColumnWidths.size(); c++) {
						if (!expandableColumns.get(c))
							continue;
						int[] cw = theColumnWidths.get(c);
						if (cw[2] <= cw[3]) {
							expandableColumns.clear(c);
							if (expandableColumns.isEmpty())
								break;
							else
								continue;
						}
						int extra = (int) Math.ceil(size * 1.0 / (expandableColumns.cardinality() - c));
						if (extra <= cw[2] - cw[3])
							cw[3] += extra;
						else {
							extra = cw[2] - cw[3];
							cw[3] = cw[2];
						}
						size -= extra;
						changed |= extra > 0;
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
				// Now everything is equally squished. Compress the rest equally.
				BitSet expandableColumns = new BitSet(theColumnWidths.size());
				expandableColumns.set(0, theColumnWidths.size());
				for (boolean changed = true; size > 0 && !expandableColumns.isEmpty() && changed;) {
					changed = false;
					for (int c = 0; c < theColumnWidths.size(); c++) {
						if (!expandableColumns.get(c))
							continue;
						int[] cw = theColumnWidths.get(c);
						if (cw[0] >= cw[3]) {
							expandableColumns.clear(c);
							if (expandableColumns.isEmpty())
								break;
							else
								continue;
						}
						int diff = -(int) Math.ceil(size * 1.0 / (expandableColumns.cardinality() - c));
						if (diff <= cw[3] - cw[0])
							cw[3] -= diff;
						else {
							diff = cw[3] - cw[0];
							cw[3] = cw[0];
						}
						size += diff;
						changed |= diff > 0;
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
			if (theMaxSetAdaptiveHeight <= 0)
				return; // Not adaptive
			int insets = table.getInsets().top + table.getInsets().bottom + scroll.getInsets().top + scroll.getInsets().bottom;
			int spacing = table.getIntercellSpacing().height;
			int minHeight = insets, prefHeight = insets, maxHeight = insets;
			boolean useSpacing = false;
			if (table.getTableHeader() != null && table.getTableHeader().isVisible()) {
				int headerHeight = table.getTableHeader().getPreferredSize().height;
				minHeight += headerHeight;
				prefHeight += headerHeight;
				maxHeight += headerHeight;
				useSpacing = true;
			}
			int rowCount = table.getRowCount();
			for (int i = 0; i < theMaxSetAdaptiveHeight && i < rowCount; i++) {
				int rowHeight = table.getRowHeight(i);
				if (useSpacing)
					rowHeight += spacing;
				else
					useSpacing = true;
				if (i > 0)
					rowHeight += spacing;
				if (theAdaptiveMinRowHeight >= 0 && i < theAdaptiveMinRowHeight)
					minHeight += rowHeight;
				if (theAdaptivePrefRowHeight >= 0 && i < theAdaptivePrefRowHeight)
					prefHeight += rowHeight;
				if (theAdaptiveMaxRowHeight >= 0 && i < theAdaptiveMaxRowHeight)
					maxHeight += rowHeight;
			}
			boolean hsb = scroll.getHorizontalScrollBarPolicy() != JScrollPane.HORIZONTAL_SCROLLBAR_NEVER;
			if (hsb) {
				int sbh = scroll.getHorizontalScrollBar().getHeight();
				minHeight += sbh;
				prefHeight += sbh;
				maxHeight += sbh;
			}
			minHeight = Math.max(0, minHeight);
			prefHeight = Math.max(minHeight, prefHeight);
			maxHeight = Math.max(minHeight, maxHeight);
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

	static class ColumnConstraints {
		int maxMin;
		int maxPref;
		int maxMax;

		void adjust(int min, int pref, int max) {
			if (min > maxMin)
				maxMin = min;
			if (pref > maxPref)
				maxPref = pref;
			if (max > maxMax)
				maxMax = max;
		}
	}

	void getColumnWidths(AbstractObservableTableModel<R> model, CategoryRenderStrategy<R, ?> column, int columnIndex, int[] widths,
		int columnCount, CollectionChangeEvent<R> rowEvent) {
		if (column.isUsingRenderingForSize()) {
			ObservableCellRenderer<R, ?> renderer = (ObservableCellRenderer<R, ?>) column.getRenderer();
			if (renderer == null) {
				renderer = new ObservableCellRenderer.DefaultObservableCellRenderer<>((row, cell) -> String.valueOf(cell));
				((CategoryRenderStrategy<R, Object>) column).withRenderer((ObservableCellRenderer<R, Object>) renderer);
			}
			ColumnConstraints cc = new ColumnConstraints();
			if (withColumnHeader && getEditor().getTableHeader().getComponentCount() == columnCount) {
				Component render = getEditor().getTableHeader().getComponent(getEditor().convertColumnIndexToView(columnIndex));
				int min = render.getMinimumSize().width;
				int pref = render.getPreferredSize().width;
				int max = render.getMaximumSize().width;
				cc.adjust(min, pref, max);
			}
			if (rowEvent != null) {
				for (CollectionChangeEvent.ElementChange<R> change : rowEvent.getElements()) {
					R row = change.newValue;
					boolean rowSelected = getEditor().isRowSelected(change.index);
					ModelRow<R> modelRow = new ModelRow.Default<>(() -> row, change.index, false, rowSelected, false, false, false);

					boolean cellSelected = getEditor().isCellSelected(change.index, columnIndex);
					Object cellValue = column.getCategoryValue(modelRow);
					ModelCell<R, Object> cell = new ModelCell.RowWrapper<>(modelRow, cellValue, columnIndex, false, cellSelected);
					Component render = ((CategoryRenderStrategy<R, Object>) column).getRenderer().getCellRendererComponent(getEditor(),
						cell, CellRenderContext.DEFAULT);
					int min = render.getMinimumSize().width;
					int pref = render.getPreferredSize().width;
					int max = render.getMaximumSize().width;
					cc.adjust(min, pref, max);
				}
			} else {
				forAllVisibleData(model, row -> {
					Object cellValue = column.getCategoryValue(row);
					boolean cellSelected = getEditor().isCellSelected(row.getRowIndex(), columnIndex);
					ModelCell<R, Object> cell = new ModelCell.RowWrapper<>(row, cellValue, columnIndex, false, cellSelected);
					Component render = ((CategoryRenderStrategy<R, Object>) column).getRenderer().getCellRendererComponent(getEditor(),
						cell, CellRenderContext.DEFAULT);
					int min = render.getMinimumSize().width;
					int pref = render.getPreferredSize().width;
					int max = render.getMaximumSize().width;
					cc.adjust(min, pref, max);
				});
			}
			// Not sure why, but these actually need just a pixel more padding
			cc.maxMin++;
			cc.maxMax++;
			widths[0] = cc.maxMin;
			widths[1] = Math.max(cc.maxPref, cc.maxMin);
			widths[2] = cc.maxMax;
		} else {
			widths[0] = column.getMinWidth();
			widths[1] = column.getPrefWidth();
			widths[2] = column.getMaxWidth();
		}
	}

	private static class TransferTarget {
		final int rowIndex;
		final int colIndex;
		final boolean beforeRow;
		final boolean beforeCol;
		final ElementId targetRow;
		final ElementId after;
		final ElementId before;

		TransferTarget(int rowIndex, int colIndex, boolean beforeRow, boolean beforeCol, ElementId targetRow, ElementId after,
			ElementId before) {
			this.rowIndex = rowIndex;
			this.colIndex = colIndex;
			this.beforeRow = beforeRow;
			this.beforeCol = beforeCol;
			this.targetRow = targetRow;
			this.after = after;
			this.before = before;
		}
	}

	public abstract class AbstractTableBuilderTransferHandler extends TransferHandler {
		private final JTable theTable;
		private final Dragging.SimpleTransferSource<R, Object> theRowSource;
		private final Dragging.SimpleTransferAccepter<R, Object> theRowAccepter;
		private Icon theAppearance;

		protected AbstractTableBuilderTransferHandler(JTable table, SimpleTransferSource<R, ?> rowSource,
			SimpleTransferAccepter<R, ?> rowAccepter) {
			theTable = table;
			theRowSource = (SimpleTransferSource<R, Object>) rowSource;
			theRowAccepter = (SimpleTransferAccepter<R, Object>) rowAccepter;
		}

		protected abstract Transaction lockRows(boolean forWrite);

		@Override
		protected Transferable createTransferable(JComponent c) {
			AbstractObservableTableModel<R> model = (AbstractObservableTableModel<R>) getEditor().getModel();
			try (Transaction rowT = lockRows(false); Transaction colT = getColumns().lock(false, null)) {
				if (getEditor().getSelectedRowCount() == 0)
					return null;
				List<R> selectedRows = new ArrayList<>(getEditor().getSelectedRowCount());
				for (int i = getEditor().getSelectionModel().getMinSelectionIndex(); i <= getEditor().getSelectionModel()
					.getMaxSelectionIndex(); i++) {
					if (getEditor().getSelectionModel().isSelectedIndex(i))
						selectedRows.add(model.getRow(i, getEditor()));
				}
				Transferable columnTransfer = null;
				Map<Integer, CategoryRenderStrategy<R, ?>> columns = new LinkedHashMap<>();
				for (int col = 0; col < getEditor().getColumnCount(); col++) {
					if (getEditor().getColumnModel().getSelectionModel().isSelectedIndex(col)) {
						int modelCol = getEditor().convertColumnIndexToModel(col);
						CategoryRenderStrategy<R, ?> column = model.getColumnModel().getElementAt(modelCol);
						if (column.getDragSource() != null)
							columns.put(modelCol, column);
					}
				}
				if (!columns.isEmpty()) {
					Transferable[][] grid = new Transferable[getEditor().getSelectedRowCount()][columns.size()];
					int row = 0;
					for (int r = getEditor().getSelectionModel().getMinSelectionIndex(); r <= getEditor().getSelectionModel()
						.getMaxSelectionIndex(); r++) {
						if (!getEditor().isRowSelected(r))
							continue;
						R rowValue = model.getRow(r, getEditor());
						ModelRow<R> modelRow = new ModelRow.Default<>(() -> rowValue, r, false, true, false,
							model.isExpanded(r, getEditor()), model.isLeaf(r, theTable));
						int cIdx = 0;
						for (Map.Entry<Integer, CategoryRenderStrategy<R, ?>> col : columns.entrySet()) {
							ModelCell<R, Object> cell = new ModelCell.RowWrapper<>(modelRow, col.getValue().getCategoryValue(modelRow),
								col.getKey(), false, getEditor().isCellSelected(row, getEditor().convertColumnIndexToView(col.getKey())));
							grid[row][cIdx] = ((Dragging.TransferSource<R, Object>) col.getValue().getDragSource())
								.createTransferable(cell);
							cIdx++;
						}
						row++;
					}
					if (columns.size() == 1) {
						Transferable[] multi = new Transferable[grid.length];
						for (int r = 0; r < grid.length; r++) {
							multi[r] = grid[r][0];
						}
						if (grid.length == 1) {
							columnTransfer = new Dragging.OrTransferable(new GridTransferable(grid), new Dragging.AndTransferable(multi),
								multi[0]);
						} else
							columnTransfer = new Dragging.OrTransferable(new GridTransferable(grid), new Dragging.AndTransferable(multi));
					} else
						columnTransfer = new GridTransferable(grid);
				}

				Transferable rowValueTransfer = null;
				if (theRowSource != null) {
					List<Transferable> rowTs = new ArrayList<>(selectedRows.size());
					int r = 0;
					for (int row = getEditor().getSelectionModel().getMinSelectionIndex(); row <= getEditor().getSelectionModel()
						.getMaxSelectionIndex(); row++) {
						if (getEditor().getSelectionModel().isSelectedIndex(row)) {
							R rowValue = selectedRows.get(r);
							ModelCell<R, R> cell = new ModelCell.Default<>(() -> rowValue, rowValue, row, 0, true, false, false, false,
								false, true);
							Transferable rowTr = theRowSource.createTransferable(cell);
							if (rowTr != null)
								rowTs.add(rowTr);
							r++;
						}
					}
					rowValueTransfer = new Dragging.AndTransferable(rowTs.toArray(new Transferable[rowTs.size()]));
				}
				Transferable result;
				if (columnTransfer != null) {
					if (rowValueTransfer != null)
						result = new Dragging.OrTransferable(columnTransfer, rowValueTransfer);
					else
						result = columnTransfer;
				} else if (rowValueTransfer != null)
					result = columnTransfer;
				else
					result = null;
				return result;
			}
		}

		@Override
		public int getSourceActions(JComponent c) {
			int actions = 0;
			try (Transaction rowT = lockRows(false); Transaction colT = getColumns().lock(false, null)) {
				if (getEditor().getSelectedRowCount() == 0)
					return actions;
				if (theRowSource != null)
					actions |= theRowSource.getSourceActions();
				int columnIndex = getEditor().getSelectedColumn();
				if (columnIndex >= 0)
					columnIndex = getEditor().convertColumnIndexToModel(columnIndex);
				CategoryRenderStrategy<R, ?> column = columnIndex >= 0 ? getColumns().get(columnIndex) : null;
				if (column != null && column.getDragSource() != null) {
					actions |= column.getDragSource().getSourceActions();
				}
			}
			return actions;
		}

		@Override
		public Icon getVisualRepresentation(Transferable t) {
			if (theAppearance != null)
				return theAppearance;
			return super.getVisualRepresentation(t);
		}

		@Override
		protected void exportDone(JComponent source, Transferable data, int action) {
			// TODO If removed, scroll
			super.exportDone(source, data, action);
		}

		protected abstract ElementId getRowElement(int rowIndex);

		protected abstract ElementId getAdjacentRowElement(ElementId rowElement, boolean next);

		protected abstract int getElementsAfter(ElementId rowElement);

		protected abstract R getRowValue(ElementId rowElement);

		protected abstract String canAddRow(R row, ElementId after, ElementId before);

		protected abstract String isRowAcceptable(ElementId rowElement, R newValue);

		protected abstract void setRow(ElementId rowElement, R newValue);

		protected abstract Object getRowCellValue(R rowValue);

		@Override
		public boolean canImport(TransferSupport support) {
			try (Transaction rowT = lockRows(false); Transaction colT = getColumns().lock(false, null)) {
				TransferTarget target = getTarget(support);
				return doImport(support, target.rowIndex, target.colIndex, target.beforeRow, target.beforeCol, target.targetRow,
					target.after, target.before, true);
			}
		}

		@Override
		public boolean importData(TransferSupport support) {
			try (Transaction rowT = lockRows(true); Transaction colT = getColumns().lock(false, null)) {
				TransferTarget target = getTarget(support);
				return doImport(support, target.rowIndex, target.colIndex, target.beforeRow, target.beforeCol, target.targetRow,
					target.after, target.before, false);
			} catch (RuntimeException | Error e) {
				e.printStackTrace();
				throw e;
			}
		}

		private TransferTarget getTarget(TransferSupport support) {
			int rowIndex, colIndex;
			boolean beforeRow, beforeCol;
			if (support.isDrop()) {
				rowIndex = getEditor().rowAtPoint(support.getDropLocation().getDropPoint());
				colIndex = getEditor().columnAtPoint(support.getDropLocation().getDropPoint());
				if (rowIndex < 0) {
					rowIndex = getEditor().getRowCount();
					beforeRow = beforeCol = false;
				} else {
					Rectangle bounds = getEditor().getCellRect(rowIndex, colIndex < 0 ? 0 : colIndex, false);
					beforeRow = (support.getDropLocation().getDropPoint().y - bounds.y) <= bounds.height / 2;
					beforeCol = (support.getDropLocation().getDropPoint().x - bounds.x) <= bounds.x / 2;
				}
			} else {
				rowIndex = getEditor().getSelectedRow();
				colIndex = -1;
				beforeRow = beforeCol = true;
			}
			ElementId targetRow = (rowIndex < 0 || rowIndex > getEditor().getRowCount()) ? null : getRowElement(rowIndex);
			ElementId after, before;
			if (targetRow != null) {
				after = beforeRow ? getAdjacentRowElement(targetRow, false) : targetRow;
				before = beforeRow ? targetRow : getAdjacentRowElement(targetRow, true);
			} else
				after = before = null;
			return new TransferTarget(rowIndex, colIndex, beforeRow, beforeCol, targetRow, after, before);
		}

		protected boolean doImport(TransferSupport support, int rowIndex, int colIndex, boolean beforeRow, boolean beforeCol,
			ElementId targetRow, ElementId after, ElementId before, boolean testOnly) {
			TransferWrapper wrapper = Dragging.wrap(support);
			if (theRowAccepter != null) {
				boolean selected = getEditor().isRowSelected(rowIndex);
				R rowValue = getRowValue(targetRow);
				ModelCell<R, ?> cell = targetRow == null ? null : new ModelCell.Default<>(() -> rowValue, getRowCellValue(rowValue),
					rowIndex, colIndex, selected, selected, false, false, false, true);
				if (theRowAccepter.canAccept(cell, beforeCol, beforeRow, wrapper, true)) {
					BetterList<? extends R> newRows;
					try {
						newRows = theRowAccepter.accept(cell, beforeCol, beforeRow, wrapper, true, testOnly);
					} catch (IOException e) {
						newRows = null;
						// Ignore
					}
					if (newRows == null) {//
					} else {
						boolean allImportable = true;
						if (rowIndex < 0) {
							for (R row : newRows) {
								if (canAddRow(row, null, null) != null) {
									allImportable = false;
									break;
								}
							}
						} else {
							for (R row : newRows) {
								if (canAddRow(row, after, before) != null) {
									allImportable = false;
									break;
								}
							}
						}
						if (allImportable)
							return true;
					}
				}
			}
			if (rowIndex >= 0) {
				int columnIndex = wrapper.isDrop() ? getEditor().columnAtPoint(wrapper.getDropLocation().getDropPoint())
					: getEditor().getSelectedColumn();
				if (columnIndex >= 0) {
					int modelColumn = getEditor().convertColumnIndexToModel(columnIndex);
					CategoryRenderStrategy<R, ?> column = getColumns().get(modelColumn);
					R rowValue = getRowValue(targetRow);
					if (canImport(wrapper, rowIndex, modelColumn, targetRow, rowValue, column, !testOnly))
						return true;
					for (DataFlavor flavor : wrapper.getDataFlavors()) {
						if (flavor instanceof GridFlavor) {
							GridFlavor gridFlavor = (GridFlavor) flavor;
							if (gridFlavor.rows - 1 >= getElementsAfter(targetRow)
								|| columnIndex + gridFlavor.columns > getEditor().getColumnCount())
								continue;
							boolean allImportable = true;
							for (int c = 0; c < gridFlavor.columns; c++) {
								modelColumn = getEditor().convertColumnIndexToModel(columnIndex + c);
								column = getColumns().get(modelColumn);
								ElementId rowEl = targetRow;
								for (int r = 0; r < gridFlavor.rows; r++) {
									rowValue = getRowValue(rowEl);
									allImportable = canImport(new Dragging.GridElementTransferWrapper(wrapper, r, c), rowIndex + r,
										columnIndex + c, rowEl, rowValue, column, false);
									if (!allImportable)
										break;
									rowEl = getAdjacentRowElement(rowEl, true);
								}
								if (!allImportable)
									break;
							}
							if (allImportable) {
								if (!testOnly) {
									for (int c = 0; c < gridFlavor.columns; c++) {
										modelColumn = getEditor().convertColumnIndexToModel(columnIndex + c);
										column = getColumns().get(modelColumn);
										ElementId rowEl = targetRow;
										for (int r = 0; r < gridFlavor.rows; r++) {
											rowValue = getRowValue(rowEl);
											canImport(new Dragging.GridElementTransferWrapper(wrapper, r, c), rowIndex + r, columnIndex + c,
												rowEl, rowValue, column, true);
											rowEl = getAdjacentRowElement(rowEl, true);
										}
									}
								}
								return true;
							}
						}
					}
				}
			}
			return false;
		}

		private <C> boolean canImport(TransferWrapper support, int rowIndex, int colIndex, ElementId rowElement, R rowValue,
			CategoryRenderStrategy<R, C> column, boolean doImport) {
			if (column.getMutator().getDragAccepter() == null)
				return false;
			boolean selected = getEditor().isRowSelected(rowIndex);
			AbstractObservableTableModel<R> model = (AbstractObservableTableModel<R>) getEditor().getModel();
			ModelRow<R> row = new ModelRow.Default<>(() -> rowValue, rowIndex, false, selected, false,
				model.isExpanded(rowIndex, getEditor()), model.isLeaf(rowIndex, theTable));
			C oldValue = column.getCategoryValue(row);
			ModelCell<R, C> cell = new ModelCell.RowWrapper<>(row, oldValue, colIndex, false,
				getEditor().isCellSelected(rowIndex, colIndex));
			if (!column.getMutator().getDragAccepter().canAccept(cell, false, false, support, false))
				return false;
			BetterList<? extends R> result;
			try {
				result = column.getMutator().getDragAccepter().accept(cell, false, false, support, false,
					!doImport);
			} catch (IOException e) {
				return false;
			}
			if (result == null)
				return false;
			else if (result.isEmpty())
				return true;
			else if (isRowAcceptable(rowElement, result.getFirst()) != null)
				return false;
			else if (doImport) {
				support.setDropAction(DnDConstants.ACTION_COPY_OR_MOVE);
				setRow(rowElement, result.getFirst());
			}
			return true;
		}
	}
}
