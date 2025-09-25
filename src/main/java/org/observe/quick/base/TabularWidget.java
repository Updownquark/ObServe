package org.observe.quick.base;

import java.util.ArrayList;
import java.util.Collections;
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
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
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

/**
 * A widget that displays rows of values, and containing columns to represent attributes of each row
 *
 * @param <R> The row type of the tabular widget
 * @param <C> The super-type of IDs for columns in the tabular widget
 */
public interface TabularWidget<R, C> extends MultiValueWidget<R> {
	/** The XML name of this element */
	public static final String TABULAR_WIDGET = "tabular-widget";

	/** The kinds of things that may be selected in a table */
	public enum TableSelectionType {
		/** Specifies that only rows may be selected in the table */
		row,
		/** Specifies that only columns may be selected in the table */
		column,
		/** Specifies that individual cells (or groups thereof) may be selected in the table */
		cell;
	}

	/** The type of intervals of rows/columns/cells that may be selected in a table */
	public enum TableSelectionMode {
		/** Specifies that only a single row/column/cell may be selected at a time */
		single,
		/** Specifies that multiple rows/columns/cells may be selected in a single contiguous range */
		contiguous,
		/** Specifies that multiple rows/columns/cells may be selected in discontiguous groups */
		general;
	}

	/**
	 * {@link TabularWidget} definition
	 *
	 * @param <W> The sub-type of tabular widget to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = TABULAR_WIDGET,
		interpretation = Interpreted.class,
		instance = TabularWidget.class)
	public interface Def<W extends TabularWidget<?, ?>> extends MultiValueWidget.Def<W> {
		/** @return The columns to represent attributes of each row */
		@QonfigChildGetter("columns")
		List<QuickTableColumn.TableColumnSet.Def<?>> getColumns();

		/** @return The model ID of the variable containing the selected status of the current row */
		ModelComponentId getSelectedVariable();

		/** @return The model ID of the variable containing the row index of the current row */
		ModelComponentId getRowIndexVariable();

		/** @return The model ID of the variable containing the column index of the current cell */
		ModelComponentId getColumnIndexVariable();

		/** @return The kinds of things that may be selected in the table */
		@QonfigAttributeGetter("selection-type")
		TableSelectionType getSelectionType();

		/** @return The type of intervals of rows that may be selected in the table (if the selection type allows) */
		@QonfigAttributeGetter("row-selection-mode")
		TableSelectionMode getRowSelectionMode();

		/** @return The type of intervals of columns that may be selected in the table (if the selection type allows) */
		@QonfigAttributeGetter("column-selection-mode")
		TableSelectionMode getColumnSelectionMode();

		/**
		 * @return The ID of the column the user has selected in the table, or null if no column or multiple columns are selected, or the
		 *         selected column's ID is not set
		 */
		@QonfigAttributeGetter("column-selection")
		CompiledExpression getColumnSelection();

		/** @return The IDs of the columns the user has selected in the table for which the ID is specified */
		@QonfigAttributeGetter("column-multi-selection")
		CompiledExpression getColumnMultiSelection();

		/**
		 * Abstract {@link TabularWidget} definition implementation
		 *
		 * @param <W> The sub-type of tabular widget to create
		 */
		public abstract class Abstract<W extends TabularWidget<?, ?>> extends QuickWidget.Def.Abstract<W> implements Def<W> {
			private final List<QuickTableColumn.TableColumnSet.Def<?>> theColumns;
			private ModelComponentId theActiveValueVariable;
			private ModelComponentId theSelectedVariable;
			private ModelComponentId theRowIndexVariable;
			private ModelComponentId theColumnIndexVariable;
			private TableSelectionType theSelectionType;
			private TableSelectionMode theRowSelectionMode;
			private TableSelectionMode theColumnSelectionMode;
			private CompiledExpression theRowSelection;
			private CompiledExpression theRowMultiSelection;
			private CompiledExpression theColumnSelection;
			private CompiledExpression theColumnMultiSelection;
			private final List<QuickTransfer.TransferSource.Def> theTransferSources;
			private final List<QuickTransfer.TransferAccept.Def> theTransferAccepters;

			/**
			 * @param parent The parent element of the widget
			 * @param type The Qonfig type of the widget
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
				theColumns = new ArrayList<>();
				theTransferSources = new ArrayList<>();
				theTransferAccepters = new ArrayList<>();
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
			public CompiledExpression getSelection() {
				return theRowSelection;
			}

			@Override
			public CompiledExpression getMultiSelection() {
				return theRowMultiSelection;
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
			public List<QuickTransfer.TransferSource.Def> getTransferSources() {
				return Collections.unmodifiableList(theTransferSources);
			}

			@Override
			public List<QuickTransfer.TransferAccept.Def> getTransferAccepters() {
				return Collections.unmodifiableList(theTransferAccepters);
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
				theSelectionType = TableSelectionType.valueOf(session.getAttributeText("selection-type"));
				theRowSelectionMode = TableSelectionMode.valueOf(session.getAttributeText("row-selection-mode"));
				theColumnSelectionMode = TableSelectionMode.valueOf(session.getAttributeText("column-selection-mode"));
				theRowSelection = getAttributeExpression("selection", session);
				theRowMultiSelection = getAttributeExpression("multi-selection", session);
				theColumnSelection = getAttributeExpression("column-selection", session);
				theColumnMultiSelection = getAttributeExpression("column-multi-selection", session);
				switch (theSelectionType) {
				case row:
					if (theColumnSelection != null)
						reporting().at(theColumnSelection.getFilePosition())
						.warn("Column selection is not supported with selection-type=row");
					if (theColumnMultiSelection != null)
						reporting().at(theColumnMultiSelection.getFilePosition())
						.warn("Column selection is not supported with selection-type=row");
					break;
				case column:
					if (theRowSelection != null)
						reporting().at(theRowSelection.getFilePosition()).warn("Row selection is not supported with selection-type=column");
					if (theRowMultiSelection != null)
						reporting().at(theRowMultiSelection.getFilePosition())
						.warn("Row selection is not supported with selection-type=column");
					break;
				default:
					break;
				}
				elModels.<Interpreted<?, ?, ?>, SettableValue<?>> satisfyElementSingleValueType(getActiveValueVariable(), ModelTypes.Value,
					this::getRowType);
				syncChildren(QuickTableColumn.TableColumnSet.Def.class, theColumns, session.forChildren("columns"));
				syncChildren(QuickTransfer.TransferSource.Def.class, theTransferSources, session.forChildren("transfer-source"));
				syncChildren(QuickTransfer.TransferAccept.Def.class, theTransferAccepters, session.forChildren("transfer-accept"));
			}

			/**
			 * @param interpreted The interpreted widget
			 * @return The row type of the tabular widget
			 * @throws ExpressoInterpretationException If the row type could not be interpreted
			 */
			protected abstract TypeToken<?> getRowType(TabularWidget.Interpreted<?, ?, ?> interpreted)
				throws ExpressoInterpretationException;

			@Override
			public abstract TabularWidget.Interpreted<?, ?, ? extends W> interpret(ExElement.Interpreted<?> parent);
		}
	}

	/**
	 * {@link TabularWidget} interpretation
	 *
	 * @param <R> The row type of the tabular widget
	 * @param <C> The super-type of IDs for columns in the tabular widget
	 * @param <W> The sub-type of tabular widget to create
	 */
	public interface Interpreted<R, C, W extends TabularWidget<R, C>> extends MultiValueWidget.Interpreted<R, W> {
		@Override
		Def<? super W> getDefinition();

		/** @return The columns to represent attributes of each row */
		List<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> getColumns();

		/**
		 * @return The ID of the column the user has selected in the table, or null if no column or multiple columns are selected, or the
		 *         selected column's ID is not set
		 */
		InterpretedValueSynth<SettableValue<?>, SettableValue<C>> getColumnSelection();

		/** @return The IDs of the columns the user has selected in the table for which the ID is specified */
		InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<C>> getColumnMultiSelection();

		/**
		 * Abstract {@link TabularWidget} interpretation implementation
		 *
		 * @param <R> The row type of the tabular widget
		 * @param <C> The super-type of IDs for columns in the tabular widget
		 * @param <W> The sub-type of tabular widget to create
		 */
		public abstract class Abstract<R, C, W extends TabularWidget<R, C>> extends QuickWidget.Interpreted.Abstract<W>
		implements Interpreted<R, C, W> {
			private ObservableCollection<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> theColumns;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<R>> theRowSelection;
			private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<R>> theRowMultiSelection;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<C>> theColumnSelection;
			private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<C>> theColumnMultiSelection;
			private final List<QuickTransfer.TransferSource.Interpreted<R, ?>> theTransferSources;
			private final List<QuickTransfer.TransferAccept.Interpreted<R, ?>> theTransferAccepters;
			private TypeToken<R> theRowType;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for the widget
			 */
			protected Abstract(Def<? super W> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theTransferSources = new ArrayList<>();
				theTransferAccepters = new ArrayList<>();
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
			public InterpretedValueSynth<SettableValue<?>, SettableValue<R>> getSelection() {
				return theRowSelection;
			}

			@Override
			public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<R>> getMultiSelection() {
				return theRowMultiSelection;
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
			public ObservableCollection<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> getColumns() {
				return theColumns;
			}

			@Override
			public List<QuickTransfer.TransferSource.Interpreted<R, ?>> getTransferSources() {
				return Collections.unmodifiableList(theTransferSources);
			}

			@Override
			public List<QuickTransfer.TransferAccept.Interpreted<R, ?>> getTransferAccepters() {
				return Collections.unmodifiableList(theTransferAccepters);
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();
				theRowType = (TypeToken<R>) getAddOn(ExWithElementModel.Interpreted.class).getElement().getDefaultEnv().getModels()
					.getComponent(getDefinition().getActiveValueVariable()).interpreted().getType().getType(0);
				theRowSelection = interpret(getDefinition().getSelection(), ModelTypes.Value.forType(getValueType()));
				theRowMultiSelection = interpret(getDefinition().getMultiSelection(), ModelTypes.Collection.forType(getValueType()));
				if (theColumns == null)
					theColumns = ObservableCollection.<QuickTableColumn.TableColumnSet.Interpreted<R, ?>> build().build();
				syncChildren(getDefinition().getColumns(), theColumns, def -> def.interpret(this),
					TableColumnSet.Interpreted::updateColumns);
				if (getDefinition().getColumnSelection() != null || getDefinition().getColumnMultiSelection() != null) {
					Set<TypeToken<? extends C>> types = new LinkedHashSet<>();
					for (QuickTableColumn.TableColumnSet.Interpreted<R, ?> column : theColumns) {
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
					theColumnMultiSelection = interpret(getDefinition().getColumnMultiSelection(),
						ModelTypes.Collection.forType(columnType));
				} else {
					theColumnSelection = null;
					theColumnMultiSelection = null;
				}
				syncChildren(getDefinition().getTransferSources(), theTransferSources,
					def -> (QuickTransfer.TransferSource.Interpreted<R, ?>) def.interpret(this),
					ts -> ts.updateTransferSource(theRowType));
				syncChildren(getDefinition().getTransferAccepters(), theTransferAccepters,
					def -> (QuickTransfer.TransferAccept.Interpreted<R, ?>) def.interpret(this),
					ts -> ts.updateTransferAccepter(theRowType));
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

	@Override
	ModelComponentId getActiveValueVariable();

	/** @return The model ID of the variable containing the row index of the current row */
	ModelComponentId getRowIndexVariable();

	/** @return The model ID of the variable containing the column index of the current cell */
	ModelComponentId getColumnIndexVariable();

	/** @return The kinds of things that may be selected in the table */
	TableSelectionType getSelectionType();

	/** @return The type of intervals of rows that may be selected in the table (if the selection type allows) */
	TableSelectionMode getRowSelectionMode();

	/** @return The type of intervals of columns that may be selected in the table (if the selection type allows) */
	TableSelectionMode getColumnSelectionMode();

	/**
	 * @return The ID of the column the user has selected in the table, or null if no column or multiple columns are selected, or the
	 *         selected column's ID is not set
	 */
	SettableValue<C> getColumnSelection();

	/** @return The IDs of the columns the user has selected in the table for which the ID is specified */
	ObservableCollection<C> getColumnMultiSelection();

	/** @return The columns to represent attributes of each row */
	ObservableCollection<QuickTableColumn.TableColumnSet<R>> getColumns();

	/** @return All columns from all sources in this table */
	ObservableCollection<QuickTableColumn<R, ?>> getAllColumns();

	/** @return The row index of the current row */
	SettableValue<Integer> getRowIndex();

	/** @return The column index of the current cell */
	SettableValue<Integer> getColumnIndex();

	/**
	 * Abstract {@link TabularWidget} implementation
	 *
	 * @param <R> The row type of the tabular widget
	 * @param <C> The column type of the tabular widget
	 */
	public abstract class Abstract<R, C> extends QuickWidget.Abstract implements TabularWidget<R, C> {
		private ModelValueInstantiator<SettableValue<R>> theSelectionInstantiator;
		private ModelValueInstantiator<ObservableCollection<R>> theMultiSelectionInstantiator;
		private ModelValueInstantiator<SettableValue<C>> theColumnSelectionInstantiator;
		private ModelValueInstantiator<ObservableCollection<C>> theColumnMultiSelectionInstantiator;

		private TableSelectionType theSelectionType;
		private TableSelectionMode theRowSelectionMode;
		private TableSelectionMode theColumnSelectionMode;

		private ObservableCollection<QuickTableColumn.TableColumnSet<R>> theColumnSets;
		private ObservableCollection<QuickTableColumn<R, ?>> theColumns;
		private List<QuickTransfer.TransferSource<R, ?>> theTransferSources;
		private List<QuickTransfer.TransferAccept<R, ?>> theTransferAccepters;

		private ModelComponentId theSelectedVariable;
		private ModelComponentId theRowIndexVariable;
		private ModelComponentId theColumnIndexVariable;
		private ModelComponentId theActiveValueVariable;

		private SettableValue<SettableValue<R>> theSelection;
		private SettableValue<ObservableCollection<R>> theMultiSelection;
		private SettableValue<SettableValue<C>> theColumnSelection;
		private SettableValue<ObservableCollection<C>> theColumnMultiSelection;
		private SettableValue<R> theActiveValue;
		private SettableValue<Boolean> isSelected;
		private SettableValue<Integer> theRowIndex;
		private SettableValue<Integer> theColumnIndex;

		/** @param id The element ID for this widget */
		protected Abstract(Object id) {
			super(id);
			theActiveValue = SettableValue.create();
			isSelected = SettableValue.create(b -> b.withValue(false));
			theRowIndex = SettableValue.create(b -> b.withValue(0));
			theColumnIndex = SettableValue.create(b -> b.withValue(0));
			theColumnSets = ObservableCollection.create();
			theColumns = theColumnSets.flow().<QuickTableColumn<R, ?>> flatMap(columnSet -> columnSet.getColumns().flow())//
				.collect();

			theSelection = SettableValue.create();
			theMultiSelection = SettableValue.create();
			theColumnSelection = SettableValue.create();
			theColumnMultiSelection = SettableValue.create();

			theTransferSources = new ArrayList<>();
			theTransferAccepters = new ArrayList<>();
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
		public SettableValue<R> getSelection() {
			return SettableValue.flatten(theSelection);
		}

		@Override
		public ObservableCollection<R> getMultiSelection() {
			return ObservableCollection.flattenValue(theMultiSelection);
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
		public ObservableCollection<QuickTableColumn.TableColumnSet<R>> getColumns() {
			return theColumnSets.flow().unmodifiable(false).collect();
		}

		@Override
		public ObservableCollection<QuickTableColumn<R, ?>> getAllColumns() {
			return theColumns.flow().unmodifiable(false).collect();
		}

		@Override
		public List<QuickTransfer.TransferSource<R, ?>> getTransferSources() {
			return Collections.unmodifiableList(theTransferSources);
		}

		@Override
		public List<QuickTransfer.TransferAccept<R, ?>> getTransferAccepters() {
			return Collections.unmodifiableList(theTransferAccepters);
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
		public SettableValue<R> getActiveValue() {
			return theActiveValue;
		}

		@Override
		public SettableValue<Boolean> isSelected() {
			return isSelected;
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
			TabularWidget.Interpreted<R, C, ?> myInterpreted = (TabularWidget.Interpreted<R, C, ?>) interpreted;
			theSelectionType = myInterpreted.getDefinition().getSelectionType();
			theRowSelectionMode = myInterpreted.getDefinition().getRowSelectionMode();
			theColumnSelectionMode = myInterpreted.getDefinition().getColumnSelectionMode();
			theSelectedVariable = myInterpreted.getDefinition().getSelectedVariable();
			theRowIndexVariable = myInterpreted.getDefinition().getRowIndexVariable();
			theColumnIndexVariable = myInterpreted.getDefinition().getColumnIndexVariable();
			theActiveValueVariable = myInterpreted.getDefinition().getActiveValueVariable();
			theSelectionInstantiator = myInterpreted.getSelection() == null ? null : myInterpreted.getSelection().instantiate();
			theMultiSelectionInstantiator = myInterpreted.getMultiSelection() == null ? null
				: myInterpreted.getMultiSelection().instantiate();
			theColumnSelectionInstantiator = myInterpreted.getColumnSelection() == null ? null
				: myInterpreted.getColumnSelection().instantiate();
			theColumnMultiSelectionInstantiator = myInterpreted.getColumnMultiSelection() == null ? null
				: myInterpreted.getColumnMultiSelection().instantiate();
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
			syncChildren(myInterpreted.getTransferSources(), theTransferSources, interp -> interp.create(),
				QuickTransfer.TransferSource::update);
			syncChildren(myInterpreted.getTransferAccepters(), theTransferAccepters, interp -> interp.create(),
				QuickTransfer.TransferAccept::update);
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();

			if (theSelectionInstantiator != null)
				theSelectionInstantiator.instantiate();
			if (theMultiSelectionInstantiator != null)
				theMultiSelectionInstantiator.instantiate();
			if (theColumnSelectionInstantiator != null)
				theColumnSelectionInstantiator.instantiate();
			if (theColumnMultiSelectionInstantiator != null)
				theColumnMultiSelectionInstantiator.instantiate();
			for (TableColumnSet<R> column : theColumnSets)
				column.instantiated();
			for (QuickTransfer.TransferSource<R, ?> ts : theTransferSources)
				ts.instantiated();
			for (QuickTransfer.TransferAccept<R, ?> ta : theTransferAccepters)
				ta.instantiated();
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			myModels = super.doInstantiate(myModels);

			ExFlexibleElementModelAddOn.satisfyElementValue(theActiveValueVariable, myModels, theActiveValue);
			ExFlexibleElementModelAddOn.satisfyElementValue(theSelectedVariable, myModels, isSelected);
			ExFlexibleElementModelAddOn.satisfyElementValue(theRowIndexVariable, myModels, theRowIndex);
			ExFlexibleElementModelAddOn.satisfyElementValue(theColumnIndexVariable, myModels, theColumnIndex);

			theSelection.set(theSelectionInstantiator == null ? null : theSelectionInstantiator.get(myModels));
			theMultiSelection.set(theMultiSelectionInstantiator == null ? null : theMultiSelectionInstantiator.get(myModels));
			theColumnSelection.set(theColumnSelectionInstantiator == null ? null : theColumnSelectionInstantiator.get(myModels));
			theColumnMultiSelection
			.set(theColumnMultiSelectionInstantiator == null ? null : theColumnMultiSelectionInstantiator.get(myModels));
			for (TableColumnSet<R> column : theColumnSets)
				column.instantiate(myModels);

			for (QuickTransfer.TransferSource<R, ?> ts : theTransferSources)
				ts.instantiate(myModels);
			for (QuickTransfer.TransferAccept<R, ?> ta : theTransferAccepters)
				ta.instantiate(myModels);
			return myModels;
		}

		@Override
		public TabularWidget.Abstract<R, C> copy(ExElement parent) {
			TabularWidget.Abstract<R, C> copy = (TabularWidget.Abstract<R, C>) super.copy(parent);

			copy.theColumnSets = ObservableCollection.<TableColumnSet<R>> build().build();
			copy.theColumns = copy.theColumnSets.flow().<QuickTableColumn<R, ?>> flatMap(columnSet -> columnSet.getColumns().flow())//
				.collect();

			copy.theActiveValue = SettableValue.create();
			copy.isSelected = SettableValue.create(b -> b.withValue(false));
			copy.theRowIndex = SettableValue.create(b -> b.withValue(0));
			copy.theColumnIndex = SettableValue.create(b -> b.withValue(0));

			copy.theSelection = SettableValue.create();
			copy.theMultiSelection = SettableValue.create();
			copy.theColumnSelection = SettableValue.create();
			copy.theColumnMultiSelection = SettableValue.create();

			for (TableColumnSet<R> columnSet : theColumnSets)
				copy.theColumnSets.add(columnSet.copy(this));

			copy.theTransferSources = new ArrayList<>();
			for (QuickTransfer.TransferSource<R, ?> ts : theTransferSources)
				copy.theTransferSources.add(ts.copy(copy));
			for (QuickTransfer.TransferAccept<R, ?> ta : theTransferAccepters)
				copy.theTransferAccepters.add(ta.copy(copy));

			return copy;
		}
	}
}
