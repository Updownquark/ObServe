package org.observe.quick.base;

import java.util.ArrayList;
import java.util.List;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickWidget;
import org.observe.quick.base.QuickTableColumn.TableColumnSet;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * A widget that displays rows of values, and containing columns to represent attributes of each row
 *
 * @param <R> The row type of the tabular widget
 */
public interface TabularWidget<R> extends MultiValueWidget<R> {
	/** The XML name of this element */
	public static final String TABULAR_WIDGET = "tabular-widget";

	/**
	 * {@link TabularWidget} definition
	 *
	 * @param <W> The sub-type of tabular widget to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = TABULAR_WIDGET,
		interpretation = Interpreted.class,
		instance = TabularWidget.class)
	public interface Def<W extends TabularWidget<?>> extends MultiValueWidget.Def<W> {
		/** @return The columns to represent attributes of each row */
		@QonfigChildGetter("columns")
		List<QuickTableColumn.TableColumnSet.Def<?>> getColumns();

		/** @return The model ID of the variable containing the selected status of the current row */
		ModelComponentId getSelectedVariable();

		/** @return The model ID of the variable containing the row index of the current row */
		ModelComponentId getRowIndexVariable();

		/** @return The model ID of the variable containing the column index of the current cell */
		ModelComponentId getColumnIndexVariable();

		/**
		 * Abstract {@link TabularWidget} definition implementation
		 *
		 * @param <W> The sub-type of tabular widget to create
		 */
		public abstract class Abstract<W extends TabularWidget<?>> extends QuickWidget.Def.Abstract<W> implements Def<W> {
			private final List<QuickTableColumn.TableColumnSet.Def<?>> theColumns;
			private ModelComponentId theActiveValueVariable;
			private ModelComponentId theSelectedVariable;
			private ModelComponentId theRowIndexVariable;
			private ModelComponentId theColumnIndexVariable;

			/**
			 * @param parent The parent element of the widget
			 * @param type The Qonfig type of the widget
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
				theColumns = new ArrayList<>();
			}

			@Override
			public List<QuickTableColumn.TableColumnSet.Def<?>> getColumns() {
				return theColumns;
			}

			@Override
			public ModelComponentId getActiveValueVariable() {
				return theActiveValueVariable;
			}

			@Override
			public ModelComponentId getSelectedVariable() {
				return theSelectedVariable;
			}

			@Override
			public ModelComponentId getRowIndexVariable() {
				return theRowIndexVariable;
			}

			@Override
			public ModelComponentId getColumnIndexVariable() {
				return theColumnIndexVariable;
			}

			/**
			 * @param session The session to inspect
			 * @return The name of the model variable in which the value of the active row will be available to expressions
			 */
			protected abstract String getActiveValueVariableName(ExpressoQIS session);

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement() // multi-value-widget
					.getSuperElement() // widget
					));
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theActiveValueVariable = elModels.getElementValueModelId(getActiveValueVariableName(session));
				theSelectedVariable = elModels.getElementValueModelId("selected");
				theRowIndexVariable = elModels.getElementValueModelId("rowIndex");
				theColumnIndexVariable = elModels.getElementValueModelId("columnIndex");
				elModels.satisfyElementValueType(getActiveValueVariable(), ModelTypes.Value,
					(interp, env) -> ModelTypes.Value.forType(getRowType((TabularWidget.Interpreted<?, ?>) interp, env)));
				syncChildren(QuickTableColumn.TableColumnSet.Def.class, theColumns, session.forChildren("columns"));
			}

			/**
			 * @param interpreted The interpreted widget
			 * @param env The expresso environment for interpreting expressions
			 * @return The row type of the tabular widget
			 * @throws ExpressoInterpretationException If the row type could not be interpreted
			 */
			protected abstract TypeToken<?> getRowType(TabularWidget.Interpreted<?, ?> interpreted, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException;

			@Override
			public abstract TabularWidget.Interpreted<?, ? extends W> interpret(ExElement.Interpreted<?> parent);
		}
	}

	/**
	 * {@link TabularWidget} interpretation
	 *
	 * @param <R> The row type of the tabular widget
	 * @param <W> The sub-type of tabular widget to create
	 */
	public interface Interpreted<R, W extends TabularWidget<R>> extends MultiValueWidget.Interpreted<R, W> {
		@Override
		Def<? super W> getDefinition();

		/** @return The columns to represent attributes of each row */
		List<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> getColumns();

		/**
		 * Abstract {@link TabularWidget} interpretation implementation
		 *
		 * @param <R> The row type of the tabular widget
		 * @param <W> The sub-type of tabular widget to create
		 */
		public abstract class Abstract<R, W extends TabularWidget<R>> extends QuickWidget.Interpreted.Abstract<W>
		implements Interpreted<R, W> {
			private ObservableCollection<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> theColumns;
			private TypeToken<R> theRowType;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for the widget
			 */
			protected Abstract(Def<? super W> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super W> getDefinition() {
				return (Def<? super W>) super.getDefinition();
			}

			@Override
			public TypeToken<R> getValueType() throws ExpressoInterpretationException {
				return theRowType;
			}

			@Override
			public ObservableCollection<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> getColumns() {
				return theColumns;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theRowType = (TypeToken<R>) getAddOn(ExWithElementModel.Interpreted.class).getElement().getExpressoEnv().getModels()
					.getComponent(getDefinition().getActiveValueVariable()).interpreted().getType().getType(0);
				if (theColumns == null)
					theColumns = ObservableCollection.<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> build().build();
				syncChildren(getDefinition().getColumns(), theColumns, def -> def.interpret(this),
					TableColumnSet.Interpreted::updateColumns);
			}

			@Override
			public void destroy() {
				super.destroy();
				for (QuickTableColumn.TableColumnSet.Interpreted<R, ?> columnSet : theColumns.reverse())
					columnSet.destroy();
				theColumns.clear();
			}

			@Override
			public abstract W create();
		}
	}

	/**
	 * Model context for a {@link TabularWidget}
	 *
	 * @param <R> The row type of the tabular widget
	 */
	public interface TabularContext<R> extends MultiValueRenderContext<R> {
		/** @return The row index of the current row */
		SettableValue<Integer> getRowIndex();

		/** @return The column index of the current cell */
		SettableValue<Integer> getColumnIndex();

		/**
		 * Default {@link TabularContext} implementation
		 *
		 * @param <R> The row type of the tabular widget
		 */
		public class Default<R> extends MultiValueRenderContext.Default<R> implements TabularContext<R> {
			private final SettableValue<Integer> theRowIndex;
			private final SettableValue<Integer> theColumnIndex;

			/**
			 * @param renderValue The value of the current row
			 * @param selected Whether the current row is selected
			 * @param rowIndex The row index of the current row
			 * @param columnIndex The column index of the current cell
			 */
			public Default(SettableValue<R> renderValue, SettableValue<Boolean> selected, SettableValue<Integer> rowIndex,
				SettableValue<Integer> columnIndex) {
				super(renderValue, selected);
				theRowIndex = rowIndex;
				theColumnIndex = columnIndex;
			}

			/** @param descrip A description of this context for debugging */
			public Default(String descrip) {
				this(
					SettableValue.<R> build().withDescription(descrip + ".rowValue").build(), //
					SettableValue.<Boolean> build().withValue(false).withDescription(descrip + ".selected").build(),
					SettableValue.<Integer> build().withValue(0).withDescription(descrip + ".rowIndex").build(), //
					SettableValue.<Integer> build().withValue(0).withDescription(descrip + ".columnIndex").build());
			}

			@Override
			public SettableValue<Integer> getRowIndex() {
				return theRowIndex;
			}

			@Override
			public SettableValue<Integer> getColumnIndex() {
				return theColumnIndex;
			}
		}
	}

	@Override
	ModelComponentId getActiveValueVariable();

	/** @return The model ID of the variable containing the row index of the current row */
	ModelComponentId getRowIndexVariable();

	/** @return The model ID of the variable containing the column index of the current cell */
	ModelComponentId getColumnIndexVariable();

	/**
	 * @param ctx The model context for this tabular widget
	 * @throws ModelInstantiationException If the model context could not be installed
	 */
	void setContext(TabularContext<R> ctx) throws ModelInstantiationException;

	/** @return The columns to represent attributes of each row */
	ObservableCollection<QuickTableColumn.TableColumnSet<R>> getColumns();

	/** @return All columns from all sources in this table */
	ObservableCollection<QuickTableColumn<R, ?>> getAllColumns();

	/**
	 * Abstract {@link TabularWidget} implementation
	 *
	 * @param <R> The row type of the tabular widget
	 */
	public abstract class Abstract<R> extends QuickWidget.Abstract implements TabularWidget<R> {
		private ObservableCollection<QuickTableColumn.TableColumnSet<R>> theColumnSets;
		private ObservableCollection<QuickTableColumn<R, ?>> theColumns;

		private ModelComponentId theSelectedVariable;
		private ModelComponentId theRowIndexVariable;
		private ModelComponentId theColumnIndexVariable;
		private ModelComponentId theActiveValueVariable;

		private SettableValue<SettableValue<R>> theActiveValue;
		private SettableValue<SettableValue<Boolean>> isSelected;
		private SettableValue<SettableValue<Integer>> theRowIndex;
		private SettableValue<SettableValue<Integer>> theColumnIndex;

		/** @param id The element ID for this widget */
		protected Abstract(Object id) {
			super(id);
			isSelected = SettableValue.<SettableValue<Boolean>> build().build();
			theRowIndex = SettableValue.<SettableValue<Integer>> build().build();
			theColumnIndex = SettableValue.<SettableValue<Integer>> build().build();
			theColumnSets = ObservableCollection.<TableColumnSet<R>> build().build();
			theColumns = theColumnSets.flow().<QuickTableColumn<R, ?>> flatMap(columnSet -> columnSet.getColumns().flow())//
				.collect();
			theActiveValue = SettableValue.<SettableValue<R>> build().build();
		}

		@Override
		public ObservableCollection<QuickTableColumn.TableColumnSet<R>> getColumns() {
			return theColumnSets.flow().unmodifiable(false).collect();
		}

		@Override
		public ObservableCollection<QuickTableColumn<R, ?>> getAllColumns() {
			return theColumns.flow().unmodifiable(false).collect();
		}

		@Override
		public ModelComponentId getActiveValueVariable() {
			return theActiveValueVariable;
		}

		@Override
		public ModelComponentId getSelectedVariable() {
			return theSelectedVariable;
		}

		@Override
		public ModelComponentId getRowIndexVariable() {
			return theRowIndexVariable;
		}

		@Override
		public ModelComponentId getColumnIndexVariable() {
			return theColumnIndexVariable;
		}

		@Override
		public void setContext(TabularContext<R> ctx) throws ModelInstantiationException {
			setContext((MultiValueRenderContext<R>) ctx);
			theRowIndex.set(ctx.getRowIndex(), null);
			theColumnIndex.set(ctx.getColumnIndex(), null);
		}

		@Override
		public void setContext(MultiValueRenderContext<R> ctx) throws ModelInstantiationException {
			theActiveValue.set(ctx.getActiveValue(), null);
			isSelected.set(ctx.isSelected(), null);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			TabularWidget.Interpreted<R, ?> myInterpreted = (TabularWidget.Interpreted<R, ?>) interpreted;
			theSelectedVariable = myInterpreted.getDefinition().getSelectedVariable();
			theRowIndexVariable = myInterpreted.getDefinition().getRowIndexVariable();
			theColumnIndexVariable = myInterpreted.getDefinition().getColumnIndexVariable();
			theActiveValueVariable = myInterpreted.getDefinition().getActiveValueVariable();
			CollectionUtils.synchronize(theColumnSets, myInterpreted.getColumns(), (v, i) -> v.getIdentity() == i.getIdentity())//
			.adjust(
				new CollectionUtils.CollectionSynchronizerX<QuickTableColumn.TableColumnSet<R>, QuickTableColumn.TableColumnSet.Interpreted<R, ?>, ModelInstantiationException>() {
					@Override
					public boolean getOrder(
						ElementSyncInput<QuickTableColumn.TableColumnSet<R>, QuickTableColumn.TableColumnSet.Interpreted<R, ?>> element) {
						return true;
					}

					@Override
					public ElementSyncAction leftOnly(
						ElementSyncInput<QuickTableColumn.TableColumnSet<R>, QuickTableColumn.TableColumnSet.Interpreted<R, ?>> element) {
						element.getLeftValue().destroy();
						return element.remove();
					}

					@Override
					public ElementSyncAction rightOnly(
						ElementSyncInput<QuickTableColumn.TableColumnSet<R>, QuickTableColumn.TableColumnSet.Interpreted<R, ?>> element)
							throws ModelInstantiationException {
						TableColumnSet<R> created;
						try {
							created = element.getRightValue().create();
							created.update(element.getRightValue(), TabularWidget.Abstract.this);
						} catch (RuntimeException | Error e) {
							element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
							return element.remove();
						}
						for (QuickTableColumn<R, ?> column : created.getColumns()) {
							try {
								column.update();
							} catch (RuntimeException | Error e) {
								column.getColumnSet().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
							}
						}

						return element.useValue(created);
					}

					@Override
					public ElementSyncAction common(
						ElementSyncInput<QuickTableColumn.TableColumnSet<R>, QuickTableColumn.TableColumnSet.Interpreted<R, ?>> element)
							throws ModelInstantiationException {
						try {
							element.getLeftValue().update(element.getRightValue(), TabularWidget.Abstract.this);
						} catch (RuntimeException | Error e) {
							element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
						}
						for (QuickTableColumn<R, ?> column : element.getLeftValue().getColumns()) {
							try {
								column.update();
							} catch (RuntimeException | Error e) {
								column.getColumnSet().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
							}
						}
						return element.useValue(element.getLeftValue());
					}
				}, CollectionUtils.AdjustmentOrder.RightOrder);
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();

			for (TableColumnSet<R> column : theColumnSets)
				column.instantiated();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			ExFlexibleElementModelAddOn.satisfyElementValue(theActiveValueVariable, myModels, SettableValue.flatten(theActiveValue));
			ExFlexibleElementModelAddOn.satisfyElementValue(theSelectedVariable, myModels, SettableValue.flatten(isSelected));
			ExFlexibleElementModelAddOn.satisfyElementValue(theRowIndexVariable, myModels, SettableValue.flatten(theRowIndex));
			ExFlexibleElementModelAddOn.satisfyElementValue(theColumnIndexVariable, myModels, SettableValue.flatten(theColumnIndex));

			for (TableColumnSet<R> column : theColumnSets)
				column.instantiate(myModels);
		}

		@Override
		public TabularWidget.Abstract<R> copy(ExElement parent) {
			TabularWidget.Abstract<R> copy = (TabularWidget.Abstract<R>) super.copy(parent);

			copy.theColumnSets = ObservableCollection.<TableColumnSet<R>> build().build();
			copy.theColumns = copy.theColumnSets.flow().<QuickTableColumn<R, ?>> flatMap(columnSet -> columnSet.getColumns().flow())//
				.collect();
			copy.theActiveValue = SettableValue.<SettableValue<R>> build().build();
			copy.isSelected = SettableValue.<SettableValue<Boolean>> build().build();
			copy.theRowIndex = SettableValue.<SettableValue<Integer>> build().build();
			copy.theColumnIndex = SettableValue.<SettableValue<Integer>> build().build();

			for (TableColumnSet<R> columnSet : theColumnSets)
				copy.theColumnSets.add(columnSet.copy(this));

			return copy;
		}
	}
}
