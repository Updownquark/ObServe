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
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
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

public interface TabularWidget<R> extends MultiValueWidget<R>, RowTyped<R> {
	public static final SingleTypeTraceability<TabularWidget<?>, Interpreted<?, ?>, Def<?>> TABULAR_WIDGET_TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, "tabular-widget", Def.class,
			Interpreted.class, TabularWidget.class);

	public interface Def<W extends TabularWidget<?>> extends MultiValueWidget.Def<W>, RowTyped.Def<W> {
		@QonfigChildGetter("columns")
		List<QuickTableColumn.TableColumnSet.Def<?>> getColumns();

		ModelComponentId getSelectedVariable();

		ModelComponentId getRowIndexVariable();

		ModelComponentId getColumnIndexVariable();

		public abstract class Abstract<W extends TabularWidget<?>> extends QuickWidget.Def.Abstract<W> implements Def<W> {
			private final List<QuickTableColumn.TableColumnSet.Def<?>> theColumns;
			private ModelComponentId theValueVariable;
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
			public ModelComponentId getValueVariable() {
				return theValueVariable;
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

			protected abstract String getValueVariableName(ExpressoQIS session);

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TABULAR_WIDGET_TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				withTraceability(MV_WIDGET_TRACEABILITY.validate(session.getFocusType().getSuperElement(), session.reporting()));
				withTraceability(
					MV_RENDERABLE_TRACEABILITY.validate(session.asElement("multi-value-renderable").getFocusType(), session.reporting()));
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement() // multi-value-widget
					.getSuperElement() // widget
					));
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theValueVariable = elModels.getElementValueModelId(getValueVariableName(session));
				theSelectedVariable = elModels.getElementValueModelId("selected");
				theRowIndexVariable = elModels.getElementValueModelId("rowIndex");
				theColumnIndexVariable = elModels.getElementValueModelId("columnIndex");
				elModels.satisfyElementValueType(getValueVariable(), ModelTypes.Value,
					(interp, env) -> ModelTypes.Value.forType(getRowType((TabularWidget.Interpreted<?, ?>) interp, env)));
				CollectionUtils
				.synchronize(theColumns, session.forChildren("columns"), (c, s) -> ExElement.typesEqual(c.getElement(), s.getElement()))//
				.adjust(
					new CollectionUtils.CollectionSynchronizerE<QuickTableColumn.TableColumnSet.Def<?>, ExpressoQIS, QonfigInterpretationException>() {
						@Override
						public boolean getOrder(ElementSyncInput<QuickTableColumn.TableColumnSet.Def<?>, ExpressoQIS> element)
							throws QonfigInterpretationException {
							return true;
						}

						@Override
						public ElementSyncAction leftOnly(ElementSyncInput<QuickTableColumn.TableColumnSet.Def<?>, ExpressoQIS> element)
							throws QonfigInterpretationException {
							// TODO dispose the column set?
							return element.remove();
						}

						@Override
						public ElementSyncAction rightOnly(
							ElementSyncInput<QuickTableColumn.TableColumnSet.Def<?>, ExpressoQIS> element)
								throws QonfigInterpretationException {
							TableColumnSet.Def<?> column = element.getRightValue()//
								.interpret(QuickTableColumn.TableColumnSet.Def.class);
							column.update(element.getRightValue());
							return element.useValue(column);
						}

						@Override
						public ElementSyncAction common(ElementSyncInput<QuickTableColumn.TableColumnSet.Def<?>, ExpressoQIS> element)
							throws QonfigInterpretationException {
							element.getLeftValue().update(element.getRightValue());
							return element.useValue(element.getLeftValue());
						}
					}, CollectionUtils.AdjustmentOrder.RightOrder);
			}

			protected abstract TypeToken<?> getRowType(TabularWidget.Interpreted<?, ?> interpreted, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException;

			@Override
			public abstract TabularWidget.Interpreted<?, ? extends W> interpret(ExElement.Interpreted<?> parent);
		}
	}

	public interface Interpreted<R, W extends TabularWidget<R>> extends MultiValueWidget.Interpreted<R, W>, RowTyped.Interpreted<R, W> {
		@Override
		Def<? super W> getDefinition();

		@Override
		TypeToken<R> getRowType();

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
			public TypeToken<R> getRowType() {
				return theRowType;
			}

			@Override
			public ObservableCollection<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> getColumns() {
				return theColumns;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				theRowType = (TypeToken<R>) env.getModels().getComponent(getDefinition().getValueVariable()).interpreted().getType()
					.getType(0);
				if (theColumns == null)
					theColumns = ObservableCollection.build(TypeTokens.get().keyFor(
						QuickTableColumn.TableColumnSet.Interpreted.class).<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> parameterized(
							getRowType(), TypeTokens.get().WILDCARD))//
					.build();
				super.doUpdate(env);
				CollectionUtils.synchronize(theColumns, getDefinition().getColumns(), (i, d) -> i.getIdentity() == d.getIdentity())//
				.adjust(
					new CollectionUtils.CollectionSynchronizerE<QuickTableColumn.TableColumnSet.Interpreted<R, ?>, QuickTableColumn.TableColumnSet.Def<?>, ExpressoInterpretationException>() {
						@Override
						public boolean getOrder(
							ElementSyncInput<QuickTableColumn.TableColumnSet.Interpreted<R, ?>, QuickTableColumn.TableColumnSet.Def<?>> element)
								throws ExpressoInterpretationException {
							return true;
						}

						@Override
						public ElementSyncAction leftOnly(
							ElementSyncInput<QuickTableColumn.TableColumnSet.Interpreted<R, ?>, QuickTableColumn.TableColumnSet.Def<?>> element)
								throws ExpressoInterpretationException {
							// TODO Dispose of the column set?
							return element.remove();
						}

						@Override
						public ElementSyncAction rightOnly(
							ElementSyncInput<QuickTableColumn.TableColumnSet.Interpreted<R, ?>, QuickTableColumn.TableColumnSet.Def<?>> element)
								throws ExpressoInterpretationException {
							TableColumnSet.Interpreted<R, ?> interpreted = element.getRightValue()//
								.interpret(Interpreted.Abstract.this);
							interpreted.updateColumns(env);
							return element.useValue(interpreted);
						}

						@Override
						public ElementSyncAction common(
							ElementSyncInput<QuickTableColumn.TableColumnSet.Interpreted<R, ?>, QuickTableColumn.TableColumnSet.Def<?>> element)
								throws ExpressoInterpretationException {
							element.getLeftValue().updateColumns(env);
							return element.useValue(element.getLeftValue());
						}
					}, CollectionUtils.AdjustmentOrder.RightOrder);
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
	ModelComponentId getValueVariable();

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
		private ModelComponentId theValueVariable;

		private SettableValue<SettableValue<R>> theRenderValue;
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
		public ModelComponentId getValueVariable() {
			return theValueVariable;
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
			theRenderValue.set(ctx.getRenderValue(), null);
			isSelected.set(ctx.isSelected(), null);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);
			TabularWidget.Interpreted<R, ?> myInterpreted = (TabularWidget.Interpreted<R, ?>) interpreted;
			if (theRowType == null || !theRowType.equals(myInterpreted.getRowType())) {
				theRowType = myInterpreted.getRowType();
				theColumnSets = ObservableCollection.build((Class<TableColumnSet<R>>) (Class<?>) TableColumnSet.class).build();
				TypeToken<QuickTableColumn<R, ?>> columnType = TypeTokens.get().keyFor(QuickTableColumn.class)//
					.<QuickTableColumn<R, ?>> parameterized(theRowType, TypeTokens.get().WILDCARD);
				theColumns = theColumnSets.flow()//
					.<QuickTableColumn<R, ?>> flatMap(columnType, columnSet -> columnSet.getColumns().flow())//
					.collect();
				theRenderValue = SettableValue
					.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<R>> parameterized(theRowType)).build();
			}
			theSelectedVariable = myInterpreted.getDefinition().getSelectedVariable();
			theRowIndexVariable = myInterpreted.getDefinition().getRowIndexVariable();
			theColumnIndexVariable = myInterpreted.getDefinition().getColumnIndexVariable();
			theValueVariable = myInterpreted.getDefinition().getValueVariable();
			CollectionUtils.synchronize(theColumnSets, myInterpreted.getColumns(), (v, i) -> v.getIdentity() == i.getIdentity())//
			.adjust(
				new CollectionUtils.CollectionSynchronizer<QuickTableColumn.TableColumnSet<R>, QuickTableColumn.TableColumnSet.Interpreted<R, ?>>() {
					@Override
					public boolean getOrder(
						ElementSyncInput<QuickTableColumn.TableColumnSet<R>, QuickTableColumn.TableColumnSet.Interpreted<R, ?>> element) {
						return true;
					}

					@Override
					public ElementSyncAction leftOnly(
						ElementSyncInput<QuickTableColumn.TableColumnSet<R>, QuickTableColumn.TableColumnSet.Interpreted<R, ?>> element) {
						// TODO Dispose of the column?
						return element.remove();
					}

					@Override
					public ElementSyncAction rightOnly(
						ElementSyncInput<QuickTableColumn.TableColumnSet<R>, QuickTableColumn.TableColumnSet.Interpreted<R, ?>> element) {
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
						ElementSyncInput<QuickTableColumn.TableColumnSet<R>, QuickTableColumn.TableColumnSet.Interpreted<R, ?>> element) {
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
		public void instantiated() {
			super.instantiated();

			for (TableColumnSet<R> column : theColumnSets)
				column.instantiated();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			ExFlexibleElementModelAddOn.satisfyElementValue(theValueVariable, myModels, SettableValue.flatten(theRenderValue));
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
			copy.theRenderValue = SettableValue.build(theRenderValue.getType()).build();
			copy.isSelected = SettableValue.build(isSelected.getType()).build();
			copy.theRowIndex = SettableValue.build(theRowIndex.getType()).build();
			copy.theColumnIndex = SettableValue.build(theRowIndex.getType()).build();

			for (TableColumnSet<R> columnSet : theColumnSets)
				copy.theColumnSets.add(columnSet.copy(this));

			return copy;
		}
	}
}
