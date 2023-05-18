package org.observe.quick.base;

import java.util.ArrayList;
import java.util.List;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ExpressoRuntimeException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickStyledElement;
import org.observe.quick.QuickWidget;
import org.observe.quick.base.QuickTableColumn.TableColumnSet;
import org.observe.util.TypeTokens;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface TabularWidget<R> extends MultiValueWidget<R>, RowTyped<R> {
	public interface Def<W extends TabularWidget<?>> extends MultiValueWidget.Def<W>, RowTyped.Def<W> {
		List<QuickTableColumn.TableColumnSet.Def<?>> getColumns();

		public abstract class Abstract<W extends TabularWidget<?>> extends QuickWidget.Def.Abstract<W> implements Def<W> {
			private final List<QuickTableColumn.TableColumnSet.Def<?>> theColumns;

			protected Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
				theColumns = new ArrayList<>();
			}

			@Override
			public List<QuickTableColumn.TableColumnSet.Def<?>> getColumns() {
				return theColumns;
			}

			@Override
			public Def.Abstract<W> update(ExpressoQIS session) throws QonfigInterpretationException {
				super.update(session);
				CollectionUtils
				.synchronize(theColumns, session.forChildren("columns"),
					(c, s) -> QuickElement.typesEqual(c.getElement(), s.getElement()))//
				.adjust(
						new CollectionUtils.CollectionSynchronizerE<QuickTableColumn.TableColumnSet.Def<?>, ExpressoQIS, QonfigInterpretationException>() {
						@Override
							public boolean getOrder(ElementSyncInput<QuickTableColumn.TableColumnSet.Def<?>, ExpressoQIS> element)
							throws QonfigInterpretationException {
							return true;
						}

						@Override
						public ElementSyncAction leftOnly(
								ElementSyncInput<QuickTableColumn.TableColumnSet.Def<?>, ExpressoQIS> element)
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
						public ElementSyncAction common(
								ElementSyncInput<QuickTableColumn.TableColumnSet.Def<?>, ExpressoQIS> element)
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

	public interface Interpreted<R, W extends TabularWidget<R>> extends MultiValueWidget.Interpreted<R, W>, RowTyped.Interpreted<R, W> {
		@Override
		Def<? super W> getDefinition();

		@Override
		TypeToken<R> getRowType();

		List<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> getColumns();

		public abstract class Abstract<R, W extends TabularWidget<R>> extends QuickWidget.Interpreted.Abstract<W>
		implements Interpreted<R, W> {
			private final List<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> theColumns;

			protected Abstract(Def<? super W> definition, QuickElement.Interpreted<?> parent) {
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
			public Interpreted.Abstract<R, W> update(QuickStyledElement.QuickInterpretationCache cache)
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

			public Default(TypeToken<T> rowType, String descrip) {
				this(SettableValue.build(rowType).withDescription(descrip + ".rowValue").build(), //
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

	@Override
	Interpreted<R, ?> getInterpreted();

	void setContext(TabularContext<R> ctx) throws ModelInstantiationException;

	ObservableCollection<QuickTableColumn.TableColumnSet<R>> getColumnSets();

	ObservableCollection<QuickTableColumn<R, ?>> getColumns();

	public abstract class Abstract<R> extends QuickWidget.Abstract implements TabularWidget<R> {
		private final ObservableCollection<QuickTableColumn.TableColumnSet<R>> theColumnSets;
		private final ObservableCollection<QuickTableColumn<R, ?>> theColumns;
		private SettableValue<R> theRowValue;

		protected Abstract(TabularWidget.Interpreted<R, ?> interpreted, QuickElement parent) {
			super(interpreted, parent);
			theColumnSets = ObservableCollection
				.build((Class<QuickTableColumn.TableColumnSet<R>>) (Class<?>) QuickTableColumn.TableColumnSet.class).build();
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
		public ObservableCollection<QuickTableColumn.TableColumnSet<R>> getColumnSets() {
			return theColumnSets.flow().unmodifiable(false).collect();
		}

		@Override
		public ObservableCollection<QuickTableColumn<R, ?>> getColumns() {
			return theColumns.flow().unmodifiable(false).collect();
		}

		@Override
		public void setContext(TabularContext<R> ctx) throws ModelInstantiationException {
			setContext((MultiValueRenderContext<R>) ctx);
			satisfyContextValue("rowIndex", ModelTypes.Value.INT, ctx.getRowIndex());
			satisfyContextValue("columnIndex", ModelTypes.Value.INT, ctx.getColumnIndex());
		}

		@Override
		public void setContext(MultiValueRenderContext<R> ctx) throws ModelInstantiationException {
			theRowValue = ctx.getRenderValue();
			satisfyContextValue(getInterpreted().getDefinition().getValueName(), ModelTypes.Value.forType(getInterpreted().getRowType()),
				theRowValue);
			satisfyContextValue("selected", ModelTypes.Value.BOOLEAN, ctx.isSelected());
		}

		@Override
		public TabularWidget.Abstract<R> update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);
			CollectionUtils.synchronize(theColumnSets, getInterpreted().getColumns(), (v, i) -> v.getInterpreted() == i)//
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
						TableColumnSet<R> created;
						try {
							created = element.getRightValue()//
								.create(TabularWidget.Abstract.this);
							created.update(getModels());
						} catch (ExpressoRuntimeException e) {
							throw e;
						} catch (RuntimeException | Error e) {
							throw new ExpressoRuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(),
								element.getRightValue().getDefinition().getElement().getPositionInFile(), e);
						}
						for (QuickTableColumn<R, ?> column : created.getColumns()) {
							try {
								column.update();
							} catch (ExpressoRuntimeException e) {
								throw e;
							} catch (RuntimeException | Error e) {
								throw new ExpressoRuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(),
									column.getColumnSet().getInterpreted().getDefinition().getElement().getPositionInFile(), e);
							}
						}

						return element.useValue(created);
					}

					@Override
					public ElementSyncAction common(
						ElementSyncInput<QuickTableColumn.TableColumnSet<R>, QuickTableColumn.TableColumnSet.Interpreted<R, ?>> element)
							throws ModelInstantiationException {
						try {
							element.getLeftValue().update(getModels());
						} catch (ExpressoRuntimeException e) {
							throw e;
						} catch (RuntimeException | Error e) {
							throw new ExpressoRuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(),
								element.getRightValue().getDefinition().getElement().getPositionInFile(), e);
						}
						for (QuickTableColumn<R, ?> column : element.getLeftValue().getColumns()) {
							try {
								column.update();
							} catch (ExpressoRuntimeException e) {
								throw e;
							} catch (RuntimeException | Error e) {
								throw new ExpressoRuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(),
									column.getColumnSet().getInterpreted().getDefinition().getElement().getPositionInFile(), e);
							}
						}
						return element.useValue(element.getLeftValue());
					}
				}, CollectionUtils.AdjustmentOrder.RightOrder);
			return this;
		}
	}
}
