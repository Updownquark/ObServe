package org.observe.quick.ext;

import java.util.ArrayList;
import java.util.List;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.quick.base.MultiValueRenderable;
import org.observe.quick.base.QuickTableColumn;
import org.observe.quick.base.QuickTableColumn.TableColumnSet;
import org.observe.quick.base.QuickTree;
import org.observe.quick.base.TabularWidget;
import org.observe.util.TypeTokens;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * A tree that can display extra information for each node in columns, like a table
 *
 * @param <N> The type of the values of each node
 */
public class QuickTreeTable<N> extends QuickTree<N> implements TabularWidget<BetterList<N>> {
	/** The XML name of this element */
	public static final String TREE_TABLE = "tree-table";

	/** {@link QuickTreeTable} definition */
	public static class Def extends QuickTree.Def<QuickTreeTable<?>> implements TabularWidget.Def<QuickTreeTable<?>> {
		private final List<QuickTableColumn.TableColumnSet.Def<?>> theColumns;
		private ModelComponentId theRowIndexVariable;
		private ModelComponentId theColumnIndexVariable;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
			theColumns = new ArrayList<>();
		}

		@Override
		public List<QuickTableColumn.TableColumnSet.Def<?>> getColumns() {
			return theColumns;
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
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theRowIndexVariable = elModels.getElementValueModelId("rowIndex");
			theColumnIndexVariable = elModels.getElementValueModelId("columnIndex");
			syncChildren(QuickTableColumn.TableColumnSet.Def.class, theColumns, session.forChildren("columns"));
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickTreeTable} interpretation
	 *
	 * @param <N> The type of the values of each node
	 */
	public static class Interpreted<N> extends QuickTree.Interpreted<N, QuickTreeTable<N>>
	implements TabularWidget.Interpreted<BetterList<N>, QuickTreeTable<N>> {
		private ObservableCollection<QuickTableColumn.TableColumnSet.Interpreted<BetterList<N>, ?>> theColumns;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public ObservableCollection<QuickTableColumn.TableColumnSet.Interpreted<BetterList<N>, ?>> getColumns() {
			return theColumns;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			if (theColumns == null)
				theColumns = ObservableCollection.build(TypeTokens.get().keyFor(
					QuickTableColumn.TableColumnSet.Interpreted.class).<QuickTableColumn.TableColumnSet.Interpreted<BetterList<N>, ?>> parameterized(
						getValueType(), TypeTokens.get().WILDCARD))//
				.build();
			syncChildren(getDefinition().getColumns(), theColumns, def -> def.interpret(this), TableColumnSet.Interpreted::updateColumns);
		}

		@Override
		public void destroy() {
			super.destroy();
			for (QuickTableColumn.TableColumnSet.Interpreted<BetterList<N>, ?> columnSet : theColumns.reverse())
				columnSet.destroy();
			theColumns.clear();
		}

		@Override
		public QuickTreeTable<N> create() {
			return new QuickTreeTable<>(getIdentity());
		}
	}

	private ObservableCollection<QuickTableColumn.TableColumnSet<BetterList<N>>> theColumnSets;
	private ObservableCollection<QuickTableColumn<BetterList<N>, ?>> theColumns;
	private ModelComponentId theRowIndexVariable;
	private ModelComponentId theColumnIndexVariable;

	private SettableValue<SettableValue<Integer>> theRowIndex;
	private SettableValue<SettableValue<Integer>> theColumnIndex;

	/** @param id The element ID for this widget */
	protected QuickTreeTable(Object id) {
		super(id);

		theRowIndex = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(TypeTokens.get().INT)).build();
		theColumnIndex = SettableValue.build(theRowIndex.getType()).build();
	}

	@Override
	public TypeToken<BetterList<N>> getRowType() {
		return TypeTokens.get().keyFor(BetterList.class).<BetterList<N>> parameterized(getNodeType());
	}

	@Override
	public ObservableCollection<QuickTableColumn.TableColumnSet<BetterList<N>>> getColumns() {
		return theColumnSets.flow().unmodifiable(false).collect();
	}

	@Override
	public ObservableCollection<QuickTableColumn<BetterList<N>, ?>> getAllColumns() {
		return theColumns.flow().unmodifiable(false).collect();
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
	public void setContext(TabularContext<BetterList<N>> ctx) throws ModelInstantiationException {
		setContext((MultiValueRenderable.MultiValueRenderContext<BetterList<N>>) ctx);
		theRowIndex.set(ctx.getRowIndex(), null);
		theColumnIndex.set(ctx.getColumnIndex(), null);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		Interpreted<N> myInterpreted = (Interpreted<N>) interpreted;
		TypeToken<BetterList<N>> rowType;
		boolean newType;
		try {
			newType = getNodeType() == null || !getNodeType().equals(myInterpreted.getNodeType());
			rowType = myInterpreted.getValueType();
		} catch (ExpressoInterpretationException e) {
			throw new IllegalStateException("Not initialized?", e);
		}
		super.doUpdate(interpreted);
		if (newType) {
			theColumnSets = ObservableCollection.build((Class<TableColumnSet<BetterList<N>>>) (Class<?>) TableColumnSet.class).build();
			TypeToken<QuickTableColumn<BetterList<N>, ?>> columnType = TypeTokens.get().keyFor(QuickTableColumn.class)//
				.<QuickTableColumn<BetterList<N>, ?>> parameterized(rowType, TypeTokens.get().WILDCARD);
			theColumns = theColumnSets.flow()//
				.<QuickTableColumn<BetterList<N>, ?>> flatMap(columnType, columnSet -> columnSet.getColumns().flow())//
				.collect();
		}
		theRowIndexVariable = myInterpreted.getDefinition().getRowIndexVariable();
		theColumnIndexVariable = myInterpreted.getDefinition().getColumnIndexVariable();
		CollectionUtils.synchronize(theColumnSets, myInterpreted.getColumns(), (v, i) -> v.getIdentity() == i.getIdentity())//
		.adjust(
			new CollectionUtils.CollectionSynchronizerX<QuickTableColumn.TableColumnSet<BetterList<N>>, QuickTableColumn.TableColumnSet.Interpreted<BetterList<N>, ?>, ModelInstantiationException>() {
				@Override
				public boolean getOrder(
					ElementSyncInput<QuickTableColumn.TableColumnSet<BetterList<N>>, QuickTableColumn.TableColumnSet.Interpreted<BetterList<N>, ?>> element) {
					return true;
				}

				@Override
				public ElementSyncAction leftOnly(
					ElementSyncInput<QuickTableColumn.TableColumnSet<BetterList<N>>, QuickTableColumn.TableColumnSet.Interpreted<BetterList<N>, ?>> element) {
					element.getLeftValue().destroy();
					return element.remove();
				}

				@Override
				public ElementSyncAction rightOnly(
					ElementSyncInput<QuickTableColumn.TableColumnSet<BetterList<N>>, QuickTableColumn.TableColumnSet.Interpreted<BetterList<N>, ?>> element)
						throws ModelInstantiationException {
					TableColumnSet<BetterList<N>> created;
					try {
						created = element.getRightValue().create();
						created.update(element.getRightValue(), QuickTreeTable.this);
					} catch (RuntimeException | Error e) {
						element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
						return element.remove();
					}
					for (QuickTableColumn<BetterList<N>, ?> column : created.getColumns()) {
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
					ElementSyncInput<QuickTableColumn.TableColumnSet<BetterList<N>>, QuickTableColumn.TableColumnSet.Interpreted<BetterList<N>, ?>> element)
						throws ModelInstantiationException {
					try {
						element.getLeftValue().update(element.getRightValue(), QuickTreeTable.this);
					} catch (RuntimeException | Error e) {
						element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
					}
					for (QuickTableColumn<BetterList<N>, ?> column : element.getLeftValue().getColumns()) {
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

		for (TableColumnSet<BetterList<N>> column : theColumnSets)
			column.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		ExFlexibleElementModelAddOn.satisfyElementValue(theRowIndexVariable, myModels, SettableValue.flatten(theRowIndex));
		ExFlexibleElementModelAddOn.satisfyElementValue(theColumnIndexVariable, myModels, SettableValue.flatten(theColumnIndex));

		for (TableColumnSet<BetterList<N>> column : theColumnSets)
			column.instantiate(myModels);
	}

	@Override
	public QuickTreeTable<N> copy(ExElement parent) {
		QuickTreeTable<N> copy = (QuickTreeTable<N>) super.copy(parent);

		copy.theColumnSets = ObservableCollection.build(theColumnSets.getType()).build();
		copy.theColumns = copy.theColumnSets.flow()//
			.<QuickTableColumn<BetterList<N>, ?>> flatMap(theColumns.getType(), columnSet -> columnSet.getColumns().flow())//
			.collect();
		copy.theRowIndex = SettableValue.build(theRowIndex.getType()).build();
		copy.theColumnIndex = SettableValue.build(theRowIndex.getType()).build();

		for (TableColumnSet<BetterList<N>> columnSet : theColumnSets)
			copy.theColumnSets.add(columnSet.copy(this));

		return copy;
	}
}
