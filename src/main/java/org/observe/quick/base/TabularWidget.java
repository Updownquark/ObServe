package org.observe.quick.base;

import java.util.ArrayList;
import java.util.List;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.TypeConversionException;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickWidget;
import org.observe.quick.base.QuickTableColumn.TableColumnSet;
import org.observe.util.TypeTokens;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface TabularWidget<R> extends MultiValueWidget<R> {
	public interface Def<W extends TabularWidget<?>> extends MultiValueWidget.Def<W> {
		List<QuickTableColumn.TableColumnSet.Def<?>> getColumns();

		public abstract class Abstract<W extends TabularWidget<?>> extends QuickWidget.Def.Abstract<W> implements Def<W> {
			private final List<QuickTableColumn.TableColumnSet.Def<?>> theColumns;

			public Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
				theColumns = new ArrayList<>();
			}

			@Override
			public List<QuickTableColumn.TableColumnSet.Def<?>> getColumns() {
				return theColumns;
			}

			@Override
			public Def.Abstract<W> update(AbstractQIS<?> session) throws QonfigInterpretationException {
				super.update(session);
				CollectionUtils
				.synchronize(theColumns, session.forChildren(), (c, s) -> QuickElement.typesEqual(c.getElement(), s.getElement()))//
				.adjust(
					new CollectionUtils.CollectionSynchronizerE<QuickTableColumn.TableColumnSet.Def<?>, AbstractQIS<?>, QonfigInterpretationException>() {
						@Override
						public boolean getOrder(ElementSyncInput<QuickTableColumn.TableColumnSet.Def<?>, AbstractQIS<?>> element)
							throws QonfigInterpretationException {
							return true;
						}

						@Override
						public ElementSyncAction leftOnly(ElementSyncInput<QuickTableColumn.TableColumnSet.Def<?>, AbstractQIS<?>> element)
							throws QonfigInterpretationException {
							// TODO dispose the column set?
							return element.remove();
						}

						@Override
						public ElementSyncAction rightOnly(ElementSyncInput<QuickTableColumn.TableColumnSet.Def<?>, AbstractQIS<?>> element)
							throws QonfigInterpretationException {
							if (element.getRightValue().supportsInterpretation(QuickTableColumn.TableColumnSet.Def.class)) {
								TableColumnSet.Def<?> column = element.getRightValue()//
									.interpret(QuickTableColumn.TableColumnSet.Def.class);
								column.update(element.getRightValue());
								return element.useValue(column);
							} else
								return element.preserve();
						}

						@Override
						public ElementSyncAction common(ElementSyncInput<QuickTableColumn.TableColumnSet.Def<?>, AbstractQIS<?>> element)
							throws QonfigInterpretationException {
							element.getLeftValue().update(element.getRightValue());
							return element.useValue(element.getLeftValue());
						}
					}, CollectionUtils.AdjustmentOrder.RightOrder);
				return this;
			}

			@Override
			public abstract TabularWidget.Interpreted<?, ? extends W> interpret(QuickElement.Interpreted<?> parent);
		}
	}

	public interface Interpreted<R, W extends TabularWidget<R>> extends MultiValueWidget.Interpreted<R, W> {
		@Override
		Def<? super W> getDefinition();

		TypeToken<R> getRowType();

		List<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> getColumns();

		public abstract class Abstract<R, W extends TabularWidget<R>> extends QuickWidget.Interpreted.Abstract<W>
		implements Interpreted<R, W> {
			private final List<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> theColumns;

			public Abstract(Def<? super W> definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
				theColumns = new ArrayList<>();
			}

			@Override
			public Def<? super W> getDefinition() {
				return (Def<? super W>) super.getDefinition();
			}

			@Override
			public List<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> getColumns() {
				return theColumns;
			}

			@Override
			public Interpreted.Abstract<R, W> update(QuickInterpretationCache cache)
				throws ExpressoInterpretationException {
				DynamicModelValue.satisfyDynamicValueType(getDefinition().getValueName(), getDefinition().getModels(),
					ModelTypes.Value.forType(getRowType()));
				super.update(cache);
				CollectionUtils.synchronize(theColumns, getDefinition().getColumns(), (i, d) -> i.getDefinition() == d)//
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
							interpreted.update(getModels(), cache);
							return element.useValue(interpreted);
						}

						@Override
						public ElementSyncAction common(
							ElementSyncInput<QuickTableColumn.TableColumnSet.Interpreted<R, ?>, QuickTableColumn.TableColumnSet.Def<?>> element)
								throws ExpressoInterpretationException {
							element.getLeftValue().update(getModels(), cache);
							return element.useValue(element.getLeftValue());
						}
					}, CollectionUtils.AdjustmentOrder.RightOrder);
				return this;
			}

			@Override
			public abstract W create(QuickElement parent);
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

	void setContext(TabularContext<R> ctx) throws ModelInstantiationException;

	ObservableCollection<QuickTableColumn<R, ?>> getColumns();

	public abstract class Abstract<R> extends QuickWidget.Abstract implements TabularWidget<R> {
		private final ObservableCollection<QuickTableColumn.TableColumnSet<R>> theColumnSets;
		private final ObservableCollection<QuickTableColumn<R, ?>> theColumns;
		private SettableValue<R> theRowValue;

		public Abstract(TabularWidget.Interpreted<R, ?> interpreted, QuickElement parent) {
			super(interpreted, parent);
			theColumnSets = ObservableCollection.build((Class<QuickTableColumn.TableColumnSet<R>>) (Class<?>) QuickTableColumn.TableColumnSet.class)
				.build();
			theColumns = theColumnSets.flow()//
				.<QuickTableColumn<R, ?>> flatMap(TypeTokens.get().keyFor(QuickTableColumn.class).<QuickTableColumn<R, ?>> parameterized(
					getInterpreted().getRowType(), TypeTokens.get().WILDCARD), columnSet -> columnSet.getColumns().flow())//
				.collect();
		}

		@Override
		public TabularWidget.Interpreted<R, ?> getInterpreted() {
			return (TabularWidget.Interpreted<R, ?>) super.getInterpreted();
		}

		@Override
		public ObservableCollection<QuickTableColumn<R, ?>> getColumns() {
			return theColumns;
		}

		@Override
		public void setContext(TabularContext<R> ctx) throws ModelInstantiationException {
			setContext((MultiValueRenderContext<R>) ctx);
			SettableValue<Integer> row = ctx.getRowIndex();
			if (row != null) {
				try {
					DynamicModelValue.satisfyDynamicValue("rowIndex", ModelTypes.Value.INT, getModels(), row);
				} catch (ModelException e) {
					throw new ModelInstantiationException("No rowIndex value?",
						getInterpreted().getDefinition().getExpressoSession().getElement().getPositionInFile(), 0, e);
				} catch (TypeConversionException e) {
					throw new IllegalStateException("rowIndex is not an integer?", e);
				}
			}
			SettableValue<Integer> col = ctx.getColumnIndex();
			if (col != null) {
				try {
					DynamicModelValue.satisfyDynamicValue("columnIndex", ModelTypes.Value.INT, getModels(), col);
				} catch (ModelException e) {
					throw new ModelInstantiationException("No columnIndex value?",
						getInterpreted().getDefinition().getExpressoSession().getElement().getPositionInFile(), 0, e);
				} catch (TypeConversionException e) {
					throw new IllegalStateException("columnIndex is not an integer?", e);
				}
			}
		}

		@Override
		public void setContext(MultiValueRenderContext<R> ctx) throws ModelInstantiationException {
			theRowValue = ctx.getRenderValue();
			if (theRowValue != null) {
				try {
					DynamicModelValue.satisfyDynamicValue(getInterpreted().getDefinition().getValueName(),
						ModelTypes.Value.forType(getInterpreted().getRowType()), getModels(), theRowValue);
				} catch (ModelException e) {
					throw new ModelInstantiationException("No " + getInterpreted().getDefinition().getValueName() + " value?",
						getInterpreted().getDefinition().getExpressoSession().getElement().getPositionInFile(), 0, e);
				} catch (TypeConversionException e) {
					throw new IllegalStateException(
						getInterpreted().getDefinition().getValueName() + " is not a " + getInterpreted().getRowType() + "?", e);
				}
			}
			SettableValue<Boolean> selected = ctx.isSelected();
			if (selected != null) {
				try {
					DynamicModelValue.satisfyDynamicValue("selected", ModelTypes.Value.BOOLEAN, getModels(), selected);
				} catch (ModelException e) {
					throw new ModelInstantiationException("No selected value?",
						getInterpreted().getDefinition().getExpressoSession().getElement().getPositionInFile(), 0, e);
				} catch (TypeConversionException e) {
					throw new IllegalStateException("selected is not a boolean?", e);
				}
			}
		}

		@Override
		public TabularWidget.Abstract<R> update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);
			CollectionUtils.synchronize(theColumnSets, getInterpreted().getColumns(), (v, i) -> v.getInterpreted() == v)//
			.adjust(
				new CollectionUtils.CollectionSynchronizerE<QuickTableColumn.TableColumnSet<R>, QuickTableColumn.TableColumnSet.Interpreted<R, ?>, ModelInstantiationException>() {
					@Override
					public boolean getOrder(
						ElementSyncInput<QuickTableColumn.TableColumnSet<R>, QuickTableColumn.TableColumnSet.Interpreted<R, ?>> element)
							throws ModelInstantiationException {
						return true;
					}

					@Override
					public ElementSyncAction leftOnly(
						ElementSyncInput<QuickTableColumn.TableColumnSet<R>, QuickTableColumn.TableColumnSet.Interpreted<R, ?>> element)
							throws ModelInstantiationException {
						// TODO Dispose of the column?
						return element.remove();
					}

					@Override
					public ElementSyncAction rightOnly(
						ElementSyncInput<QuickTableColumn.TableColumnSet<R>, QuickTableColumn.TableColumnSet.Interpreted<R, ?>> element)
							throws ModelInstantiationException {
						TableColumnSet<R> created = element.getRightValue()//
							.create(TabularWidget.Abstract.this);
						created.update(getModels());
						for (QuickTableColumn<R, ?> column : created.getColumns())
							column.update();
						return element.useValue(created);
					}

					@Override
					public ElementSyncAction common(
						ElementSyncInput<QuickTableColumn.TableColumnSet<R>, QuickTableColumn.TableColumnSet.Interpreted<R, ?>> element)
							throws ModelInstantiationException {
						element.getLeftValue().update(getModels());
						for (QuickTableColumn<R, ?> column : element.getLeftValue().getColumns())
							column.update();
						return element.useValue(element.getLeftValue());
					}
				}, CollectionUtils.AdjustmentOrder.RightOrder);
			return this;
		}
	}
}
