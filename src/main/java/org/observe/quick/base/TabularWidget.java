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
import org.observe.util.TypeTokens;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface TabularWidget<R> extends MultiValueWidget<R> {
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = "tabular-widget",
		interpretation = Interpreted.class,
		instance = TabularWidget.class)
	public interface Def<W extends TabularWidget<?>> extends MultiValueWidget.Def<W> {
		@QonfigChildGetter("columns")
		List<QuickTableColumn.TableColumnSet.Def<?>> getColumns();

		ModelComponentId getSelectedVariable();

		ModelComponentId getRowIndexVariable();

		ModelComponentId getColumnIndexVariable();

		public abstract class Abstract<W extends TabularWidget<?>> extends QuickWidget.Def.Abstract<W> implements Def<W> {
			private final List<QuickTableColumn.TableColumnSet.Def<?>> theColumns;
			private ModelComponentId theActiveValueVariable;
			private ModelComponentId theSelectedVariable;
			private ModelComponentId theRowIndexVariable;
			private ModelComponentId theColumnIndexVariable;

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

			protected abstract TypeToken<?> getRowType(TabularWidget.Interpreted<?, ?> interpreted, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException;

			@Override
			public abstract TabularWidget.Interpreted<?, ? extends W> interpret(ExElement.Interpreted<?> parent);
		}
	}

	public interface Interpreted<R, W extends TabularWidget<R>> extends MultiValueWidget.Interpreted<R, W> {
		@Override
		Def<? super W> getDefinition();

		List<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> getColumns();

		public abstract class Abstract<R, W extends TabularWidget<R>> extends QuickWidget.Interpreted.Abstract<W>
		implements Interpreted<R, W> {
			private ObservableCollection<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> theColumns;
			private TypeToken<R> theRowType;

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
					theColumns = ObservableCollection.build(TypeTokens.get().keyFor(
						QuickTableColumn.TableColumnSet.Interpreted.class).<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> parameterized(
							getValueType(), TypeTokens.get().WILDCARD))//
					.build();
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

	public interface TabularContext<R> extends MultiValueRenderContext<R> {
		SettableValue<Integer> getRowIndex();

		SettableValue<Integer> getColumnIndex();

		public class Default<T> extends MultiValueRenderContext.Default<T> implements TabularContext<T> {
			private final SettableValue<Integer> theRowIndex;
			private final SettableValue<Integer> theColumnIndex;

			public Default(SettableValue<T> renderValue, SettableValue<Boolean> selected, SettableValue<Integer> rowIndex,
				SettableValue<Integer> columnIndex) {
				super(renderValue, selected);
				theRowIndex = rowIndex;
				theColumnIndex = columnIndex;
			}

			public Default(TypeToken<T> rowType, String descrip) {
				this(
					SettableValue.build(rowType).withDescription(descrip + ".rowValue").withValue(TypeTokens.get().getDefaultValue(rowType))
					.build(), //
					SettableValue.build(boolean.class).withValue(false).withDescription(descrip + ".selected").build(),
					SettableValue.build(int.class).withValue(0).withDescription(descrip + ".rowIndex").build(), //
					SettableValue.build(int.class).withValue(0).withDescription(descrip + ".columnIndex").build());
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

	TypeToken<R> getRowType();

	@Override
	ModelComponentId getActiveValueVariable();

	ModelComponentId getRowIndexVariable();

	ModelComponentId getColumnIndexVariable();

	void setContext(TabularContext<R> ctx) throws ModelInstantiationException;

	ObservableCollection<QuickTableColumn.TableColumnSet<R>> getColumns();

	ObservableCollection<QuickTableColumn<R, ?>> getAllColumns();

	public abstract class Abstract<R> extends QuickWidget.Abstract implements TabularWidget<R> {
		private TypeToken<R> theRowType;
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

		protected Abstract(Object id) {
			super(id);
			isSelected = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(TypeTokens.get().BOOLEAN))
				.build();
			theRowIndex = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(TypeTokens.get().INT)).build();
			theColumnIndex = SettableValue.build(theRowIndex.getType()).build();
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
		public TypeToken<R> getRowType() {
			return theRowType;
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
			TypeToken<R> rowType;
			try {
				rowType = myInterpreted.getValueType();
			} catch (ExpressoInterpretationException e) {
				throw new IllegalStateException("Not initialized?", e);
			}
			if (theRowType == null || !theRowType.equals(rowType)) {
				theRowType = rowType;
				theColumnSets = ObservableCollection.build((Class<TableColumnSet<R>>) (Class<?>) TableColumnSet.class).build();
				TypeToken<QuickTableColumn<R, ?>> columnType = TypeTokens.get().keyFor(QuickTableColumn.class)//
					.<QuickTableColumn<R, ?>> parameterized(theRowType, TypeTokens.get().WILDCARD);
				theColumns = theColumnSets.flow()//
					.<QuickTableColumn<R, ?>> flatMap(columnType, columnSet -> columnSet.getColumns().flow())//
					.collect();
				theActiveValue = SettableValue
					.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<R>> parameterized(theRowType)).build();
			}
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

			copy.theColumnSets = ObservableCollection.build(theColumnSets.getType()).build();
			copy.theColumns = copy.theColumnSets.flow()//
				.<QuickTableColumn<R, ?>> flatMap(theColumns.getType(), columnSet -> columnSet.getColumns().flow())//
				.collect();
			copy.theActiveValue = SettableValue.build(theActiveValue.getType()).build();
			copy.isSelected = SettableValue.build(isSelected.getType()).build();
			copy.theRowIndex = SettableValue.build(theRowIndex.getType()).build();
			copy.theColumnIndex = SettableValue.build(theRowIndex.getType()).build();

			for (TableColumnSet<R> columnSet : theColumnSets)
				copy.theColumnSets.add(columnSet.copy(this));

			return copy;
		}
	}
}
