package org.observe.quick.base;

import java.util.ArrayList;
import java.util.List;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
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
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
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
			private ObservableCollection<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> theColumns;

			protected Abstract(Def<? super W> definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super W> getDefinition() {
				return (Def<? super W>) super.getDefinition();
			}

			@Override
			public ObservableCollection<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> getColumns() {
				return theColumns;
			}

			@Override
			public void update(QuickStyledElement.QuickInterpretationCache cache) throws ExpressoInterpretationException {
				if (theColumns == null)
					theColumns = ObservableCollection.build(TypeTokens.get().keyFor(
						QuickTableColumn.TableColumnSet.Interpreted.class).<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> parameterized(
							getRowType(), TypeTokens.get().WILDCARD))//
					.build();
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
			}

			@Override
			public void destroy() {
				for (QuickTableColumn.TableColumnSet.Interpreted<R, ?> columnSet : theColumns.reverse())
					columnSet.destroy();
				theColumns.clear();
				super.destroy();
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

	TypeToken<R> getRowType();

	String getValueName();

	void setContext(TabularContext<R> ctx) throws ModelInstantiationException;

	ObservableCollection<QuickTableColumn.TableColumnSet<R>> getColumnSets();

	ObservableCollection<QuickTableColumn<R, ?>> getColumns();

	public abstract class Abstract<R> extends QuickWidget.Abstract implements TabularWidget<R> {
		private final TypeToken<R> theRowType;
		private final ObservableCollection<QuickTableColumn.TableColumnSet<R>> theColumnSets;
		private final ObservableCollection<QuickTableColumn<R, ?>> theColumns;

		private final SettableValue<SettableValue<R>> theRenderValue;
		private final SettableValue<SettableValue<Boolean>> isSelected;
		private final SettableValue<SettableValue<Integer>> theRowIndex;
		private final SettableValue<SettableValue<Integer>> theColumnIndex;
		private String theValueName;

		protected Abstract(TabularWidget.Interpreted<R, ?> interpreted, QuickElement parent) {
			super(interpreted, parent);
			theRowType = interpreted.getRowType();
			theColumnSets = ObservableCollection
				.build((Class<QuickTableColumn.TableColumnSet<R>>) (Class<?>) QuickTableColumn.TableColumnSet.class).build();
			theColumns = theColumnSets.flow()//
				.<QuickTableColumn<R, ?>> flatMap(TypeTokens.get().keyFor(QuickTableColumn.class).<QuickTableColumn<R, ?>> parameterized(
					theRowType, TypeTokens.get().WILDCARD), columnSet -> columnSet.getColumns().flow())//
				.collect();
			theRenderValue = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<R>> parameterized(interpreted.getRowType())).build();
			isSelected = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
			theRowIndex = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(int.class)).build();
			theColumnIndex = SettableValue.build(theRowIndex.getType()).build();
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
		public TypeToken<R> getRowType() {
			return theRowType;
		}

		@Override
		public String getValueName() {
			return theValueName;
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
		protected void updateModel(QuickElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			TabularWidget.Interpreted<R, ?> myInterpreted = (TabularWidget.Interpreted<R, ?>) interpreted;
			theValueName = myInterpreted.getDefinition().getValueName();
			satisfyContextValue(theValueName, ModelTypes.Value.forType(theRowType), SettableValue.flatten(theRenderValue), myModels);
			satisfyContextValue("selected", ModelTypes.Value.BOOLEAN, SettableValue.flatten(isSelected, () -> false), myModels);
			satisfyContextValue("rowIndex", ModelTypes.Value.INT, SettableValue.flatten(theRowIndex, () -> 0), myModels);
			satisfyContextValue("columnIndex", ModelTypes.Value.INT, SettableValue.flatten(theColumnIndex, () -> 0), myModels);
			CollectionUtils
			.synchronize(theColumnSets, myInterpreted.getColumns(), (v, i) -> v.getIdentity() == i.getDefinition().getIdentity())//
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
							created.update(element.getRightValue(), myModels);
						} catch (RuntimeException | Error e) {
							element.getRightValue().getDefinition().reporting()
							.error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
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
							element.getLeftValue().update(element.getRightValue(), myModels);
						} catch (RuntimeException | Error e) {
							element.getRightValue().getDefinition().reporting()
							.error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
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
	}
}
