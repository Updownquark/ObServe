package org.observe.quick.swing;

import java.util.HashMap;
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
		Subscription sub;
		try {
			sub = columns.subscribe(evt -> {
				boolean renderer = false;
				try {
					switch (evt.getType()) {
					case add:
						renderer = true;
						if (evt.getNewValue().getRenderer() != null)
							renderers.put(evt.getNewValue().getIdentity(),
								tx.transform(evt.getNewValue().getRenderer(), QuickSwingPopulator.class));
						renderer = false;
						if (evt.getNewValue().getEditing() != null && evt.getNewValue().getEditing().getEditor() != null)
							editors.put(evt.getNewValue().getIdentity(),
								tx.transform(evt.getNewValue().getEditing().getEditor(), QuickSwingPopulator.class));
						break;
					case remove:
						renderers.remove(evt.getOldValue().getIdentity());
						editors.remove(evt.getOldValue().getIdentity());
						break;
					case set:
						if (evt.getOldValue().getIdentity() != evt.getNewValue().getIdentity()) {
							renderers.remove(evt.getOldValue().getIdentity());
							editors.remove(evt.getOldValue().getIdentity());
						}
						renderer = true;
						if (evt.getNewValue().getRenderer() != null)
							renderers.put(evt.getNewValue().getIdentity(),
								tx.transform(evt.getNewValue().getRenderer(), QuickSwingPopulator.class));
						renderer = false;
						if (evt.getNewValue().getEditing() != null && evt.getNewValue().getEditing().getEditor() != null)
							editors.put(evt.getNewValue().getIdentity(),
								tx.transform(evt.getNewValue().getEditing().getEditor(), QuickSwingPopulator.class));
						break;
					}
				} catch (ExpressoInterpretationException e) {
					if (renderersInitialized)
						(renderer ? evt.getNewValue().getRenderer() : evt.getNewValue().getEditing().getEditor()).reporting()
						.at(e.getErrorOffset()).error(e.getMessage(), e);
					else
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
							() -> theParentComponent, renderers.get(column.getColumnSet().getIdentity()),
							editors.get(column.getColumnSet().getIdentity()));
					} catch (ModelInstantiationException e) {
						if (tableInitialized) {
							column.getColumnSet().reporting().error(e.getMessage(), e);
							return null;
						} else
							throw new CheckedExceptionWrapper(e);
					}
				})//
				.refreshEach(column -> {
					QuickWidget renderer = column.getColumn().getRenderer();
					return renderer == null ? null : renderer.getRepaint();
				})//
				.filter(column -> column == null ? "Column failed to create" : null)//
				.catchUpdates(ThreadConstraint.ANY)//
				.collectActive(until);
			Subscription columnsSub = theSwingTableColumns.subscribe(evt -> {
				if (evt.getNewValue() != null)
					evt.getNewValue().init(theSwingTableColumns, evt.getElementId());
			}, true);
			until.take(1).act(__ -> columnsSub.unsubscribe());
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
