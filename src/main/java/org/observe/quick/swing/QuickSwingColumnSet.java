package org.observe.quick.swing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.quick.QuickWidget;
import org.observe.quick.base.QuickTableColumn;
import org.observe.quick.base.TabularWidget;
import org.observe.quick.swing.QuickSwingTablePopulation.InterpretedSwingTableColumn;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.qommons.ThreadConstraint;
import org.qommons.Transformer;
import org.qommons.TriConsumer;
import org.qommons.ex.CheckedExceptionWrapper;

/**
 * Utility for interpreting a set of columns for a tabular widget in a swing UI
 *
 * @param <R> The type of the row collection in the Quick widget
 * @param <R2> The type of the row collection in the PanelPopulation widget
 */
public class QuickSwingColumnSet<R, R2> {
	private final TriConsumer<R2, R, QuickWidget> theUpdate;
	private final Function<R2, R> theReverse;
	private final Map<Object, QuickSwingPopulator<QuickWidget>> renderers = new HashMap<>();
	private final Map<Object, QuickSwingPopulator<QuickWidget>> editors = new HashMap<>();
	private final QuickSwingTransfer theDragging;
	private boolean renderersInitialized;

	/**
	 * @param widget The interpreted Quick widget
	 * @param columns The interpreted Quick columns
	 * @param tx Swing transformation
	 * @param update The function to update a PanelPopulation value when a row changes in the Quick widget's row collection
	 * @param reverse The function to produce a Quick widget's row value from a PanelPopulation row
	 * @throws ExpressoInterpretationException If the columns could not be interpreted
	 */
	public QuickSwingColumnSet(QuickWidget.Interpreted<?> widget,
		ObservableCollection<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> columns, Transformer<ExpressoInterpretationException> tx,
		TriConsumer<R2, R, QuickWidget> update, Function<R2, R> reverse) throws ExpressoInterpretationException {
		theUpdate = update;
		theReverse = reverse;
		theDragging = new QuickSwingTransfer();
		Subscription sub;
		try {
			sub = columns.subscribe(evt -> {
				int renderer = -1;
				try {
					switch (evt.getType()) {
					case add:
						renderer = 0;
						for (QuickWidget.Interpreted<?> r : evt.getNewValue().getRenderers()) {
							renderers.put(r.getIdentity(), tx.transform(r, QuickSwingPopulator.class));
							renderer++;
						}
						renderer = -1;
						theDragging.withSources(evt.getNewValue().getTransferSources(), tx);
						theDragging.withAccepters(evt.getNewValue().getTransferAccepters(), tx);
						if (evt.getNewValue().getEditing() != null) {
							for (QuickWidget.Interpreted<?> editor : evt.getNewValue().getEditing().getEditors())
								editors.put(editor.getIdentity(), tx.transform(editor, QuickSwingPopulator.class));
						}

						break;
					case remove:
						for (QuickWidget.Interpreted<?> r : evt.getNewValue().getRenderers())
							renderers.remove(r.getIdentity());
						renderers.remove(evt.getOldValue().getIdentity());
						editors.remove(evt.getOldValue().getIdentity());
						theDragging.removeSources(evt.getNewValue().getTransferSources());
						theDragging.removeAccepters(evt.getNewValue().getTransferAccepters());
						break;
					case set:
						if (evt.getOldValue().getIdentity() != evt.getNewValue().getIdentity()) {
							for (QuickWidget.Interpreted<?> r : evt.getNewValue().getRenderers())
								renderers.remove(r.getIdentity());
							renderers.remove(evt.getOldValue().getIdentity());
							editors.remove(evt.getOldValue().getIdentity());
							theDragging.removeSources(evt.getNewValue().getTransferSources());
							theDragging.removeAccepters(evt.getNewValue().getTransferAccepters());
						}
						renderer = 0;
						for (QuickWidget.Interpreted<?> r : evt.getNewValue().getRenderers()) {
							renderers.put(r.getIdentity(), tx.transform(r, QuickSwingPopulator.class));
							renderer++;
						}
						renderer = -1;
						theDragging.withSources(evt.getNewValue().getTransferSources(), tx);
						theDragging.withAccepters(evt.getNewValue().getTransferAccepters(), tx);
						if (evt.getNewValue().getEditing() != null) {
							for (QuickWidget.Interpreted<?> editor : evt.getNewValue().getEditing().getEditors())
								editors.put(editor.getIdentity(), tx.transform(editor, QuickSwingPopulator.class));
						}
						break;
					}
				} catch (ExpressoInterpretationException e) {
					if (renderersInitialized) {
						if (renderer >= 0)
							evt.getNewValue().getRenderers().get(renderer).reporting().at(e.getErrorOffset()).error(e.getMessage(), e);
						else {
							QuickWidget.Interpreted<?> editor = evt.getNewValue().getEditing().getEditors().get(0);
							editor.reporting().at(e.getErrorOffset()).error(e.getMessage(), e);
						}
					} else
						throw new CheckedExceptionWrapper(e);
				}
			}, true);
		} catch (CheckedExceptionWrapper e) {
			if (e.getCause() instanceof ExpressoInterpretationException)
				throw (ExpressoInterpretationException) e.getCause();
			else
				throw new ExpressoInterpretationException(e.getMessage(), widget.reporting().getPosition(), 0, e.getCause());
		}
		renderersInitialized = true;
		widget.destroyed().act(__ -> sub.unsubscribe());
	}

	/**
	 * @param widget The instantiated Quick widget
	 * @param columns The instantiated Quick columns
	 * @param ctx The tabular context for the widget
	 * @param until The observable to release resources
	 * @return The populator for the PanelPopulation table
	 * @throws ModelInstantiationException If the columns could not be prepared for population
	 */
	public Populator createPopulator(QuickWidget widget, ObservableCollection<QuickTableColumn<R, ?>> columns,
		TabularWidget.TabularContext<R> ctx, Observable<?> until) throws ModelInstantiationException {
		return new Populator(widget, columns, ctx, until);
	}

	/**
	 * Handles Quick-based table columns for a {@link org.observe.util.swing.PanelPopulation.AbstractTableBuilder
	 * PanelPopulation.AbstractTableBuilder}
	 */
	public class Populator {
		private final ObservableCollection<InterpretedSwingTableColumn<R, R2, ?>> theSwingTableColumns;
		private final ObservableCollection<CategoryRenderStrategy<R2, ?>> theRenderStrategies;
		private ComponentEditor<?, ?> theParentComponent;
		private boolean tableInitialized;

		Populator(QuickWidget parent, ObservableCollection<QuickTableColumn<R, ?>> columns, TabularWidget.TabularContext<R> ctx,
			Observable<?> until) {
			theSwingTableColumns = columns.flow()//
				.<InterpretedSwingTableColumn<R, R2, ?>> map(column -> {
					try {
						return new InterpretedSwingTableColumn<>(parent, column, theUpdate, theReverse, ctx, until,
							() -> theParentComponent, renderers, editors, theDragging);
					} catch (ModelInstantiationException e) {
						if (tableInitialized) {
							column.getColumnSet().reporting().error(e.getMessage(), e);
							return null;
						} else
							throw new CheckedExceptionWrapper(e);
					}
				})//
				.refreshEach(column -> {
					if (column == null)
						return null;
					List<Observable<?>> refresh = new ArrayList<>();
					refresh.add(column.getColumn().getName().noInitChanges());
					for (QuickWidget renderer : column.getColumn().getRenderers()) {
						if (renderer.getRepaint() != null)
							refresh.add(renderer.getRepaint());
					}
					if (column.getColumn().getHeaderTooltip() != null)
						refresh.add(column.getColumn().getHeaderTooltip().noInitChanges());

					return Observable.or(refresh.toArray(new Observable[refresh.size()]));
				})//
				.filter(column -> column == null ? "Column failed to create" : null)//
				.catchUpdates(ThreadConstraint.ANY)//
				.collectActive(until);
			theRenderStrategies = theSwingTableColumns.flow()//
				.<CategoryRenderStrategy<R2, ?>> map(column -> column.getCRS())//
				.collect();
			tableInitialized = true;
		}

		/** @param table The table to populate with the Quick-sourced columns */
		public void populate(PanelPopulation.AbstractTableBuilder<R2, ?, ?> table) {
			theParentComponent = table;
			table.withColumns(theRenderStrategies);
		}
	}
}
