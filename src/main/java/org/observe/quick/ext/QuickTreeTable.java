package org.observe.quick.ext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
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
 * @param <C> The column type of the table
 */
public class QuickTreeTable<N, C> extends QuickTree<N> implements TabularWidget<BetterList<N>, C> {
	/** The XML name of this element */
	public static final String TREE_TABLE = "tree-table";

	/** {@link QuickTreeTable} definition */
	public static class Def extends QuickTree.Def<QuickTreeTable<?, ?>> implements TabularWidget.Def<QuickTreeTable<?, ?>> {
		private final List<QuickTableColumn.TableColumnSet.Def<?>> theColumns;
		private ModelComponentId theRowIndexVariable;
		private ModelComponentId theColumnIndexVariable;
		private TableSelectionType theSelectionType;
		private TableSelectionMode theRowSelectionMode;
		private TableSelectionMode theColumnSelectionMode;
		private CompiledExpression theColumnSelection;
		private CompiledExpression theColumnMultiSelection;

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
		public TableSelectionType getSelectionType() {
			return theSelectionType;
		}

		@Override
		public TableSelectionMode getRowSelectionMode() {
			return theRowSelectionMode;
		}

		@Override
		public TableSelectionMode getColumnSelectionMode() {
			return theColumnSelectionMode;
		}

		@Override
		public CompiledExpression getColumnSelection() {
			return theColumnSelection;
		}

		@Override
		public CompiledExpression getColumnMultiSelection() {
			return theColumnMultiSelection;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theRowIndexVariable = elModels.getElementValueModelId("rowIndex");
			theColumnIndexVariable = elModels.getElementValueModelId("columnIndex");
			theSelectionType = TableSelectionType.valueOf(session.getAttributeText("selection-type"));
			theRowSelectionMode = TableSelectionMode.valueOf(session.getAttributeText("row-selection-mode"));
			theColumnSelectionMode = TableSelectionMode.valueOf(session.getAttributeText("column-selection-mode"));
			theColumnSelection = getAttributeExpression("column-selection", session);
			theColumnMultiSelection = getAttributeExpression("column-multi-selection", session);
			switch (theSelectionType) {
			case row:
				if (theColumnSelection != null)
					reporting().at(theColumnSelection.getFilePosition()).warn("Column selection is not supported with selection-type=row");
				if (theColumnMultiSelection != null)
					reporting().at(theColumnMultiSelection.getFilePosition())
					.warn("Column selection is not supported with selection-type=row");
				break;
			case column:
				if (getSelection() != null)
					reporting().at(getSelection().getFilePosition()).warn("Path selection is not supported with selection-type=column");
				if (getMultiSelection() != null)
					reporting().at(getMultiSelection().getFilePosition())
					.warn("Path selection is not supported with selection-type=column");
				if (getNodeSelection() != null)
					reporting().at(getNodeSelection().getFilePosition()).warn("Node selection is not supported with selection-type=column");
				if (getNodeMultiSelection() != null)
					reporting().at(getNodeMultiSelection().getFilePosition())
					.warn("Node selection is not supported with selection-type=column");
				break;
			default:
				break;
			}
			syncChildren(QuickTableColumn.TableColumnSet.Def.class, theColumns, session.forChildren("columns"));
		}

		@Override
		public Interpreted<?, ?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickTreeTable} interpretation
	 *
	 * @param <N> The type of the values of each node
	 * @param <C> The column type of the table
	 */
	public static class Interpreted<N, C> extends QuickTree.Interpreted<N, QuickTreeTable<N, C>>
	implements TabularWidget.Interpreted<BetterList<N>, C, QuickTreeTable<N, C>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<C>> theColumnSelection;
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<C>> theColumnMultiSelection;
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
		public InterpretedValueSynth<SettableValue<?>, SettableValue<C>> getColumnSelection() {
			return theColumnSelection;
		}

		@Override
		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<C>> getColumnMultiSelection() {
			return theColumnMultiSelection;
		}

		@Override
		public ObservableCollection<QuickTableColumn.TableColumnSet.Interpreted<BetterList<N>, ?>> getColumns() {
			return theColumns;
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			if (theColumns == null)
				theColumns = ObservableCollection.<QuickTableColumn.TableColumnSet.Interpreted<BetterList<N>, ?>> build()//
				.build();
			syncChildren(getDefinition().getColumns(), theColumns, def -> def.interpret(this), TableColumnSet.Interpreted::updateColumns);
			if (getDefinition().getColumnSelection() != null || getDefinition().getColumnMultiSelection() != null) {
				Set<TypeToken<? extends C>> types = new LinkedHashSet<>();
				for (QuickTableColumn.TableColumnSet.Interpreted<BetterList<N>, ?> column : theColumns) {
					TypeToken<?> columnIdType = column.getIdType();
					if (columnIdType != null)
						types.add((TypeToken<? extends C>) columnIdType);
				}
				TypeToken<C> columnType;
				if (types.isEmpty())
					columnType = (TypeToken<C>) TypeTokens.get().OBJECT;
				else
					columnType = TypeTokens.get().getCommonType(types);
				if (columnType == null)
					columnType = (TypeToken<C>) TypeTokens.get().OBJECT;
				theColumnSelection = interpret(getDefinition().getColumnSelection(), ModelTypes.Value.forType(columnType));
				theColumnMultiSelection = interpret(getDefinition().getColumnMultiSelection(), ModelTypes.Collection.forType(columnType));
			} else {
				theColumnSelection = null;
				theColumnMultiSelection = null;
			}
		}

		@Override
		public void destroy() {
			super.destroy();
			for (QuickTableColumn.TableColumnSet.Interpreted<BetterList<N>, ?> columnSet : theColumns.reverse())
				columnSet.destroy();
			theColumns.clear();
		}

		@Override
		public QuickTreeTable<N, C> create() {
			return new QuickTreeTable<>(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<C>> theColumnSelectionInstantiator;
	private ModelValueInstantiator<ObservableCollection<C>> theColumnMultiSelectionInstantiator;

	private TableSelectionType theSelectionType;
	private TableSelectionMode theRowSelectionMode;
	private TableSelectionMode theColumnSelectionMode;

	private ObservableCollection<QuickTableColumn.TableColumnSet<BetterList<N>>> theColumnSets;
	private ObservableCollection<QuickTableColumn<BetterList<N>, ?>> theColumns;
	private ModelComponentId theRowIndexVariable;
	private ModelComponentId theColumnIndexVariable;

	private SettableValue<SettableValue<C>> theColumnSelection;
	private SettableValue<ObservableCollection<C>> theColumnMultiSelection;
	private SettableValue<Integer> theRowIndex;
	private SettableValue<Integer> theColumnIndex;

	/** @param id The element ID for this widget */
	protected QuickTreeTable(Object id) {
		super(id);

		theColumnSets = ObservableCollection.<QuickTableColumn.TableColumnSet<BetterList<N>>> build().build();
		theColumns = theColumnSets.flow()//
			.<QuickTableColumn<BetterList<N>, ?>> flatMap(columnSet -> columnSet.getColumns().flow())//
			.collect();
		theRowIndex = SettableValue.create(b -> b.withValue(0));
		theColumnIndex = SettableValue.create(b -> b.withValue(0));

		theColumnSelection = SettableValue.create();
		theColumnMultiSelection = SettableValue.create();
	}

	@Override
	public TableSelectionType getSelectionType() {
		return theSelectionType;
	}

	@Override
	public TableSelectionMode getRowSelectionMode() {
		return theRowSelectionMode;
	}

	@Override
	public TableSelectionMode getColumnSelectionMode() {
		return theColumnSelectionMode;
	}

	@Override
	public SettableValue<C> getColumnSelection() {
		return SettableValue.flatten(theColumnSelection);
	}

	@Override
	public ObservableCollection<C> getColumnMultiSelection() {
		return ObservableCollection.flattenValue(theColumnMultiSelection);
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
	public SettableValue<Integer> getRowIndex() {
		return theRowIndex;
	}

	@Override
	public SettableValue<Integer> getColumnIndex() {
		return theColumnIndex;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted<N, C> myInterpreted = (Interpreted<N, C>) interpreted;
		theSelectionType = myInterpreted.getDefinition().getSelectionType();
		theRowSelectionMode = myInterpreted.getDefinition().getRowSelectionMode();
		theColumnSelectionMode = myInterpreted.getDefinition().getColumnSelectionMode();
		theRowIndexVariable = myInterpreted.getDefinition().getRowIndexVariable();
		theColumnIndexVariable = myInterpreted.getDefinition().getColumnIndexVariable();
		theColumnSelectionInstantiator = myInterpreted.getColumnSelection() == null ? null
			: myInterpreted.getColumnSelection().instantiate();
		theColumnMultiSelectionInstantiator = myInterpreted.getColumnMultiSelection() == null ? null
			: myInterpreted.getColumnMultiSelection().instantiate();
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

		if (theColumnSelectionInstantiator != null)
			theColumnSelectionInstantiator.instantiate();
		if (theColumnMultiSelectionInstantiator != null)
			theColumnMultiSelectionInstantiator.instantiate();
		for (TableColumnSet<BetterList<N>> column : theColumnSets)
			column.instantiated();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		ExFlexibleElementModelAddOn.satisfyElementValue(theRowIndexVariable, myModels, theRowIndex);
		ExFlexibleElementModelAddOn.satisfyElementValue(theColumnIndexVariable, myModels, theColumnIndex);

		theColumnSelection.set(theColumnSelectionInstantiator == null ? null : theColumnSelectionInstantiator.get(myModels));
		theColumnMultiSelection.set(theColumnMultiSelectionInstantiator == null ? null : theColumnMultiSelectionInstantiator.get(myModels));
		for (TableColumnSet<BetterList<N>> column : theColumnSets)
			column.instantiate(myModels);
		return myModels;
	}

	@Override
	public QuickTreeTable<N, C> copy(ExElement parent) {
		QuickTreeTable<N, C> copy = (QuickTreeTable<N, C>) super.copy(parent);

		copy.theColumnSelection = SettableValue.create();
		copy.theColumnMultiSelection = SettableValue.create();
		copy.theColumnSets = ObservableCollection.<QuickTableColumn.TableColumnSet<BetterList<N>>> build().build();
		copy.theColumns = copy.theColumnSets.flow()//
			.<QuickTableColumn<BetterList<N>, ?>> flatMap(columnSet -> columnSet.getColumns().flow())//
			.collect();
		copy.theRowIndex = SettableValue.create(b -> b.withValue(0));
		copy.theColumnIndex = SettableValue.create(b -> b.withValue(0));

		for (TableColumnSet<BetterList<N>> columnSet : theColumnSets)
			copy.theColumnSets.add(columnSet.copy(this));

		return copy;
	}
}
