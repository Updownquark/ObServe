package org.observe.util.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.BoundedRangeModel;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.table.TableColumn;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.ObservableCollection;
import org.observe.dbug.DbugAnchor;
import org.observe.dbug.DbugAnchor.InstantiationTransaction;
import org.observe.util.TypeTokens;
import org.observe.util.swing.Dragging.SimpleTransferAccepter;
import org.observe.util.swing.Dragging.SimpleTransferSource;
import org.observe.util.swing.Dragging.TransferAccepter;
import org.observe.util.swing.Dragging.TransferSource;
import org.observe.util.swing.ObservableCellRenderer.CellRenderContext;
import org.observe.util.swing.PanelPopulation.AbstractComponentEditor;
import org.observe.util.swing.PanelPopulation.AbstractTableBuilder;
import org.observe.util.swing.PanelPopulation.CollectionWidgetBuilder;
import org.observe.util.swing.PanelPopulation.DataAction;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulationImpl.SimpleDataAction;
import org.observe.util.swing.PanelPopulationImpl.SimpleHPanel;
import org.qommons.IntList;
import org.qommons.StringUtils;
import org.qommons.ThreadConstraint;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

public abstract class AbstractSimpleTableBuilder<R, T extends JTable, P extends AbstractSimpleTableBuilder<R, T, P>>
extends AbstractComponentEditor<T, P>
implements AbstractTableBuilder<R, T, P>, CollectionWidgetBuilder<R, T, P> {
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

	@SuppressWarnings("rawtypes")
	private final DbugAnchor<AbstractTableBuilder> theAnchor;
	private String theItemName;
	private ObservableCollection<? extends CategoryRenderStrategy<R, ?>> theColumns;
	private SettableValue<R> theSelectionValue;
	private ObservableCollection<R> theSelectionValues;
	private ObservableValue<String> theDisablement;
	private List<Object> theActions;
	private boolean theActionsOnTop;
	private Dragging.SimpleTransferSource<R> theDragSource;
	private Dragging.SimpleTransferAccepter<R, R, R> theDragAccepter;
	private List<AbstractObservableTableModel.RowMouseListener<? super R>> theMouseListeners;
	private int theAdaptiveMinRowHeight;
	private int theAdaptivePrefRowHeight;
	private int theAdaptiveMaxRowHeight;
	private boolean withColumnHeader;
	private boolean isScrollable;

	protected AbstractSimpleTableBuilder(ObservableCollection<R> rows, Observable<?> until) {
		this(rows, (T) new NoLayoutTable(), until);
	}

	protected AbstractSimpleTableBuilder(ObservableCollection<R> rows, T table, Observable<?> until) {
		super(null, table, until);
		theAnchor = AbstractTableBuilder.DBUG.instance(this, a -> a//
			.setField("type", rows.getType(), null)//
			);
		theActions = new LinkedList<>();
		theActionsOnTop = true;
		withColumnHeader = true;
		isScrollable = true;
	}

	protected abstract TypeToken<R> getRowType();

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
		return theColumns;
	}

	protected SettableValue<R> getSelectionValue() {
		return theSelectionValue;
	}

	protected ObservableCollection<R> getSelectionValues() {
		return theSelectionValues;
	}

	@Override
	public P withColumns(ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns) {
		theColumns = columns;
		return (P) this;
	}

	@Override
	public P withColumn(CategoryRenderStrategy<R, ?> column) {
		if (theColumns == null)
			theColumns = ObservableCollection.create(new TypeToken<CategoryRenderStrategy<R, ?>>() {
			}.where(new TypeParameter<R>() {
			}, TypeTokens.get().wrap(getRowType())));
		((ObservableCollection<CategoryRenderStrategy<R, ?>>) theColumns).add(column);
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
	public P disableWith(ObservableValue<String> disabled) {
		if (theDisablement == null)
			theDisablement = disabled;
		else
			theDisablement = ObservableValue.firstValue(TypeTokens.get().STRING, msg -> msg != null, () -> null, theDisablement, disabled);
		return (P) this;
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
	public abstract List<R> getSelection();

	@Override
	public P withMouseListener(AbstractObservableTableModel.RowMouseListener<? super R> listener) {
		if(theMouseListeners==null)
			theMouseListeners=new ArrayList<>();
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
		SimpleDataAction<R, ?> ta = new SimpleDataAction<>(actionName, this, action, this::getSelection, true, getUntil());
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
			theDragSource = new SimpleTransferSource<>(getRowType());
		// if (source == null)
		// throw new IllegalArgumentException("Drag sourcing must be configured");
		if (source != null)
			source.accept(theDragSource);
		return (P) this;
	}

	@Override
	public P dragAcceptRow(Consumer<? super TransferAccepter<R, R, R>> accept) {
		if (theDragAccepter == null)
			theDragAccepter = new SimpleTransferAccepter<>(getRowType());
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

	protected ObservableCollection<? extends CategoryRenderStrategy<R, ?>> createColumnSet() {
		return theColumns;
	}

	protected abstract AbstractObservableTableModel<R> createTableModel(
		ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns);

	protected abstract AbstractObservableTableModel.TableRenderContext createTableRenderContext();

	protected abstract void syncSelection(T table, AbstractObservableTableModel<R> model, SettableValue<R> selection,
		boolean enforceSingle);

	protected abstract void syncMultiSelection(T table, AbstractObservableTableModel<R> model, ObservableCollection<R> selection);

	protected abstract void watchSelection(AbstractObservableTableModel<R> model, T table, Consumer<Object> onSelect);

	protected abstract TransferHandler setUpDnD(T table, SimpleTransferSource<R> dragSource, SimpleTransferAccepter<R, R, R> dragAccepter);

	protected abstract void onVisibleData(Consumer<CollectionChangeEvent<R>> onChange);

	protected abstract void forAllVisibleData(Consumer<ModelRow<R>> forEach);

	@Override
	protected Component createComponent() {
		theAnchor.event("create", null);
		InstantiationTransaction instantiating = theAnchor.instantiating();

		ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns = createColumnSet().safe(ThreadConstraint.EDT, getUntil());

		instantiating.watchFor(ObservableTableModel.DBUG, "model", tk -> tk.applyTo(1));
		AbstractObservableTableModel<R> model = createTableModel(columns);

		T table = getEditor();
		if (theDisablement != null) {
			theDisablement.changes().takeUntil(getUntil()).act(evt -> {
				// Let's not worry about tooltip here. We could mess up cell tooltips and stuff.
				table.setEnabled(evt.getNewValue() == null);
			});
		}
		if (!withColumnHeader)
			table.setTableHeader(null);
		if(theMouseListeners!=null) {
			for(ObservableTableModel.RowMouseListener<? super R> listener : theMouseListeners)
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
		Supplier<List<R>> selectionGetter = () -> getSelection();
		if (theSelectionValue != null)
			syncSelection(table, model, theSelectionValue, false);
		syncMultiSelection(table, model, theSelectionValues);

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
			watchSelection(model, table, e -> {
				List<R> selection = selectionGetter.get();
				for (Object action : theActions) {
					if (action instanceof SimpleDataAction)
						((SimpleDataAction<R, ?>) action).updateSelection(selection, e);
				}
			});
			List<R> selection = selectionGetter.get();
			for (Object action : theActions) {
				if (action instanceof SimpleDataAction)
					((SimpleDataAction<R, ?>) action).updateSelection(selection, null);
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
				SimpleHPanel<JPanel, ?> buttonPanel = new SimpleHPanel<>(null,
					new JPanel(new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING)), getUntil());
				for (Object action : theActions) {
					if (action instanceof SimpleDataAction) {
						if (((SimpleDataAction<?, ?>) action).isButton())
							((SimpleDataAction<R, ?>) action).addButton(buttonPanel);
					} else if (action instanceof Consumer)
						buttonPanel.addHPanel(null, "box", (Consumer<PanelPopulator<JPanel, ?>>) action);
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
			TransferHandler handler = setUpDnD(table, theDragSource, theDragAccepter);
			table.setTransferHandler(handler);
		}

		instantiating.close();
		decorate(comp);
		return comp;
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

		/**
		 * This integer is how much the user has resized columns beyond the scroll pane's width.<br />
		 * This helps us with column layout.
		 */
		private int theTableExtraWidth;

		SizeListener(JScrollPane scroll, JTable table, AbstractObservableTableModel<R> model) {
			this.scroll = scroll;
			this.table = table;
			this.model = model;
			ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns = model.getColumns();
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
			onVisibleData(evt -> {
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
				if (adjusted) {
					adjustScrollWidths();
				}
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
			int newWidth = scroll.getViewport().getWidth();
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
			theScrollWidth = scroll.getViewport().getWidth();
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
			int tableSize = scroll.getViewport().getWidth() - (getEditor().getColumnCount() - 1) * table.getColumnModel().getColumnMargin()//
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
				// Now everything is equally squished. Distribute any extra space equally.
				for (boolean changed = true; size > 0 && changed; changed = false) {
					for (int c = 0; c < theColumnWidths.size(); c++) {
						int[] cw = theColumnWidths.get(c);
						int extra = (int) Math.ceil(size * 1.0 / (theColumnWidths.size() - c));
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
				for (boolean changed = true; size < 0 && changed; changed = false) {
					for (int c = 0; c < theColumnWidths.size(); c++) {
						int[] cw = theColumnWidths.get(c);
						int diff = -(int) Math.ceil(size * 1.0 / (theColumnWidths.size() - c));
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
			if (theAdaptivePrefRowHeight <= 0)
				return; // Not adaptive
			theAnchor.event("adjustHeight", null);
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
			for (int i = 0; i < theAdaptiveMaxRowHeight && i < rowCount; i++) {
				int rowHeight = table.getRowHeight(i);
				if (useSpacing)
					rowHeight += spacing;
				else
					useSpacing = true;
				if (i > 0)
					rowHeight += spacing;
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

	void getColumnWidths(CategoryRenderStrategy<R, ?> column, int columnIndex, int[] widths, CollectionChangeEvent<R> rowEvent) {
		if (column.isUsingRenderingForSize()) {
			ObservableCellRenderer<R, ?> renderer = (ObservableCellRenderer<R, ?>) column.getRenderer();
			if (renderer == null) {
				renderer = new ObservableCellRenderer.DefaultObservableCellRenderer<>((row, cell) -> String.valueOf(cell));
				((CategoryRenderStrategy<R, Object>) column).withRenderer((ObservableCellRenderer<R, Object>) renderer);
			}
			ColumnConstraints cc = new ColumnConstraints();
			if (withColumnHeader) {
				Component render = getEditor().getTableHeader().getComponent(getEditor().convertColumnIndexToView(columnIndex));
				int min = render.getMinimumSize().width;
				int pref = render.getPreferredSize().width;
				int max = render.getMaximumSize().width;
				cc.adjust(min, pref, max);
			}
			if (rowEvent != null) {
				for (CollectionChangeEvent.ElementChange<R> change : rowEvent.getElements()) {
					R row = change.newValue;
					Object cellValue = column.getCategoryValue(row);
					ModelCell<R, Object> cell = new ModelCell.Default<>(() -> row, cellValue, change.index, columnIndex,
						getEditor().isRowSelected(change.index), false, false, false, false, true, null);
					Component render = ((CategoryRenderStrategy<R, Object>) column).getRenderer().getCellRendererComponent(getEditor(),
						cell, CellRenderContext.DEFAULT);
					int min = render.getMinimumSize().width;
					int pref = render.getPreferredSize().width;
					int max = render.getMaximumSize().width;
					cc.adjust(min, pref, max);
				}
			} else {
				forAllVisibleData(row -> {
					Object cellValue = column.getCategoryValue(row.getModelValue());
					ModelCell<R, Object> cell = new ModelCell.Default<>(row::getModelValue, cellValue, row.getRowIndex(), columnIndex,
						row.isSelected(), row.hasFocus(), false, false, row.isExpanded(), row.isLeaf(), null);
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
}
