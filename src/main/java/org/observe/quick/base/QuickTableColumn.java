package org.observe.quick.base;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelSetInstanceBuilder;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.qonfig.*;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickValueWidget;
import org.observe.quick.QuickWidget;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickStyledElement;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * Represents a column in a {@link TabularWidget}
 *
 * @param <R> The type of rows in the table
 * @param <C> The type of this column's value
 */
public interface QuickTableColumn<R, C> {
	/**
	 * A set if table columns
	 *
	 * @param <R> The type of rows in the table
	 */
	public interface TableColumnSet<R> extends ValueTyped<R> {
		/**
		 * {@link TableColumnSet} definition
		 *
		 * @param <CC> The type of column set to create
		 */
		public interface Def<CC extends TableColumnSet<?>> extends ValueTyped.Def<CC> {
			/** @return The renderer to represent the column value to the user when they are not interacting with it */
			QuickWidget.Def<?> getRenderer();

			/** @return The strategy for editing values in this column */
			ColumnEditing.Def getEditing();

			/**
			 * @param <R> The type of rows in the table
			 * @param parent The parent for the column set
			 * @return The interpreted column set
			 */
			<R> Interpreted<R, ? extends CC> interpret(ExElement.Interpreted<?> parent);
		}

		/**
		 * {@link TableColumnSet} interpretation
		 *
		 * @param <R> The type of rows in the table
		 * @param <CC> The type of column set to create
		 */
		public interface Interpreted<R, CC extends TableColumnSet<R>> extends ValueTyped.Interpreted<R, CC> {
			@Override
			Def<? super CC> getDefinition();

			@Override
			ValueTyped.Interpreted<R, ?> getParentElement();

			/** @return The renderer to represent the column value to the user when they are not interacting with it */
			QuickWidget.Interpreted<?> getRenderer();

			/** @return The strategy for editing values in this column */
			ColumnEditing.Interpreted<R, ?> getEditing();

			/**
			 * Initializes or updates this column set
			 *
			 * @param env The expresso environment to use to interpret expressions
			 * @throws ExpressoInterpretationException If this column set could not be interpreted
			 */
			void updateColumns(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

			/** @return The column set */
			CC create();
		}

		@Override
		ValueTyped<R> getParentElement();

		/** @return The type of rows in the table */
		TypeToken<R> getRowType();

		/** @return The columns for the table */
		ObservableCollection<? extends QuickTableColumn<R, ?>> getColumns();

		@Override
		TableColumnSet<R> copy(ExElement parent);
	}

	/** @return The name of the column--the text for the column's header */
	SettableValue<String> getName();

	/** @return The column set that this column belongs to */
	QuickTableColumn.TableColumnSet<R> getColumnSet();

	/** @return The type of this column's value */
	TypeToken<C> getType();

	/** @return The current value of this column */
	SettableValue<C> getValue();

	/** @return The tooltip for this column's header */
	SettableValue<String> getHeaderTooltip();

	/** @return The minimum width of this column, in pixels */
	Integer getMinWidth();

	/** @return The preferred width of this column, in pixels */
	Integer getPrefWidth();

	/** @return The maximum width of this column, in pixels */
	Integer getMaxWidth();

	/**
	 * @return The width of this column, in pixels. Overrides {@link #getMinWidth() min}, {@link #getPrefWidth() preferred}, and
	 *         {@link #getMaxWidth() max} widths
	 */
	Integer getWidth();

	/** @return The renderer to represent the column value to the user when they are not interacting with it */
	QuickWidget getRenderer();

	/**
	 * Returns an observable that fires when OUTSIDE INFLUENCES to the render style change. This is an important distinction, because if the
	 * proper render style was listened to, that would change multiple times for every cell that is rendered (because row value, row index,
	 * etc. are set independently). That style should NEVER be listened to, because the act of listening to it is expensive given how often
	 * it may change. Use this observable instead.
	 *
	 * @return An observable that fires when OUTSIDE INFLUENCES to the render style change.
	 */
	Observable<? extends Causable> getRenderStyleChanges();

	/** @return The strategy for editing values in this column */
	ColumnEditing<R, C> getEditing();

	/** Updates or initializes this column */
	void update();

	/**
	 * Model context for {@link ColumnEditing}
	 *
	 * @param <R> The type of rows in the table
	 * @param <C> The value type of the column
	 */
	public interface ColumnEditContext<R, C> extends TabularWidget.TabularContext<R> {
		/** @return The value in the column that is currently being edited */
		SettableValue<C> getEditColumnValue();

		/**
		 * Default {@link ColumnEditContext} implementation
		 *
		 * @param <R> The type of rows in the table
		 * @param <C> The value type of the column
		 */
		public class Default<R, C> extends TabularWidget.TabularContext.Default<R> implements ColumnEditContext<R, C> {
			private final SettableValue<C> theEditColumnValue;

			/**
			 * @param renderValue The row value in the table that is currently being edited
			 * @param selected Whether the row is selected in the table
			 * @param rowIndex The index of the row of the editing element in the table
			 * @param columnIndex The index of the column of the editing element in the table
			 * @param editColumnValue The value in the column that is currently being edited
			 */
			public Default(SettableValue<R> renderValue, SettableValue<Boolean> selected, SettableValue<Integer> rowIndex,
				SettableValue<Integer> columnIndex, SettableValue<C> editColumnValue) {
				super(renderValue, selected, rowIndex, columnIndex);
				theEditColumnValue = editColumnValue;
			}

			/**
			 * @param rowType The type of rows in the table
			 * @param columnType The value type of the column
			 * @param descrip A description of the model context, for debugging
			 */
			public Default(TypeToken<R> rowType, TypeToken<C> columnType, String descrip) {
				super(rowType, descrip);
				theEditColumnValue = SettableValue.build(columnType).withValue(TypeTokens.get().getDefaultValue(columnType))
					.withDescription(descrip + ".columnEditValue").build();
			}

			@Override
			public SettableValue<C> getEditColumnValue() {
				return theEditColumnValue;
			}
		}
	}

	/**
	 * A strategy for editing values in a {@link QuickTableColumn column} of a {@link TabularWidget}
	 *
	 * @param <R> The type of rows in the table
	 * @param <C> The value type of the column
	 */
	public class ColumnEditing<R, C> extends ExElement.Abstract implements QuickValueWidget.WidgetValueSupplier<C> {
		/** The XML name of this element */
		public static final String COLUMN_EDITING = "column-edit";

		/** {@link ColumnEditing} definition */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = COLUMN_EDITING,
			interpretation = Interpreted.class,
			instance = ColumnEditing.class)
		public static class Def extends ExElement.Def.Abstract<ColumnEditing<?, ?>>
		implements QuickValueWidget.WidgetValueSupplier.Def<ColumnEditing<?, ?>> {
			private QuickWidget.Def<?> theEditor;
			private ModelComponentId theColumnEditValueVariable;
			private CompiledExpression isEditable;
			private CompiledExpression isAcceptable;
			private Integer theClicks;

			/**
			 * @param parent The column set this editing is for
			 * @param type The Qonfig type of this element
			 */
			public Def(TableColumnSet.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public TableColumnSet.Def<?> getParentElement() {
				return (TableColumnSet.Def<?>) super.getParentElement();
			}

			/** @return The sub-strategy to edit with */
			@QonfigAttributeGetter("type")
			public ColumnEditType.Def<?> getType() {
				return getAddOn(ColumnEditType.Def.class);
			}

			/** @return The widget to modify the column value */
			@QonfigChildGetter("editor")
			public QuickWidget.Def<?> getEditor() {
				return theEditor;
			}

			/** @return The model ID of the variable by which the editing column value will be available to expressions */
			@QonfigAttributeGetter("column-edit-value-name")
			public ModelComponentId getColumnEditValueVariable() {
				return theColumnEditValueVariable;
			}

			/** @return Whether the column is editable for a cell */
			@QonfigAttributeGetter("editable-if")
			public CompiledExpression isEditable() {
				return isEditable;
			}

			/** @return Whether the input value is acceptable for the current cell */
			@QonfigAttributeGetter("accept")
			public CompiledExpression isAcceptable() {
				return isAcceptable;
			}

			/** @return How many clicks are required to activate editing on the column */
			@QonfigAttributeGetter("clicks")
			public Integer getClicks() {
				return theClicks;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theEditor = syncChild(QuickWidget.Def.class, theEditor, session, "editor");
				String columnEditValueName = session.getAttributeText("column-edit-value-name");
				isEditable = getAttributeExpression("editable-if", session);
				isAcceptable = getAttributeExpression("accept", session);
				theClicks = session.getAttribute("clicks", Integer.class);
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theColumnEditValueVariable = elModels.getElementValueModelId(columnEditValueName);
				elModels.satisfyElementValueType(theColumnEditValueVariable, ModelTypes.Value,
					(interp, env) -> ModelTypes.Value.forType(((Interpreted<?, ?>) interp).getColumnType()));
			}

			/**
			 * @param parent The parent element for the interpreted editing
			 * @return The interpreted editing
			 */
			public Interpreted<?, ?> interpret(TableColumnSet.Interpreted<?, ?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		/**
		 * {@link ColumnEditing} interpretation
		 *
		 * @param <R> The type of rows in the table
		 * @param <C> The value type of the column
		 */
		public static class Interpreted<R, C> extends ExElement.Interpreted.Abstract<ColumnEditing<R, C>>
		implements QuickValueWidget.WidgetValueSupplier.Interpreted<C, ColumnEditing<R, C>> {
			private TypeToken<C> theColumnType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isEditable;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isAcceptable;
			private QuickWidget.Interpreted<?> theEditor;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for the editing
			 */
			protected Interpreted(Def definition, TableColumnSet.Interpreted<R, ?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public TableColumnSet.Interpreted<R, ?> getParentElement() {
				return (TableColumnSet.Interpreted<R, ?>) super.getParentElement();
			}

			/** @return The sub-strategy to edit with */
			public ColumnEditType.Interpreted<R, C, ?> getType() {
				return getAddOn(ColumnEditType.Interpreted.class);
			}

			/** @return Whether the column is editable for a cell */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isEditable() {
				return isEditable;
			}

			/** @return Whether the input value is acceptable for the current cell */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isAcceptable() {
				return isAcceptable;
			}

			/** @return The widget to modify the column value */
			public QuickWidget.Interpreted<?> getEditor() {
				return theEditor;
			}

			/** @return the value type of the column */
			public TypeToken<C> getColumnType() {
				return theColumnType;
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<C>> getValue() throws ExpressoInterpretationException {
				try {
					return getModels().getValue(getDefinition().getColumnEditValueVariable(), ModelTypes.Value.forType(theColumnType),
						getExpressoEnv());
				} catch (ModelException e) {
					throw new ExpressoInterpretationException(
						"Could not get column value '" + getDefinition().getColumnEditValueVariable() + "'",
						getDefinition().reporting().getPosition(), 0, e);
				} catch (TypeConversionException e) {
					throw new ExpressoInterpretationException(
						"Could not convert column value '" + getDefinition().getColumnEditValueVariable() + "'",
						getDefinition().reporting().getPosition(), 0, e);
				}
			}

			/**
			 * Updates or initializes this editing strategy
			 *
			 * @param columnType The value type of the column
			 * @param env The expresso environment to use to interpret expressions
			 * @return This editing strategy
			 * @throws ExpressoInterpretationException If the editing strategy could not be interpreted
			 */
			public Interpreted<R, C> update(TypeToken<C> columnType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				theColumnType = columnType;

				super.update(env);
				isEditable = ExpressoTransformations.parseFilter(getDefinition().isEditable(), this, true);
				return this;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				super.doUpdate(expressoEnv);

				isAcceptable = ExpressoTransformations.parseFilter(getDefinition().isAcceptable(), this, true);
				theEditor = syncChild(getDefinition().getEditor(), theEditor, def -> def.interpret(this),
					(e, eEnv) -> e.updateElement(eEnv));
			}

			/** @return The editing strategy */
			public ColumnEditing<R, C> create() {
				return new ColumnEditing<>(getIdentity());
			}
		}

		private ModelValueInstantiator<SettableValue<String>> theEditableInstantiator;
		private ModelValueInstantiator<SettableValue<String>> theAcceptInstantiator;

		private TypeToken<R> theRowType;
		private SettableValue<SettableValue<R>> theEditRowValue;
		private SettableValue<SettableValue<C>> theEditColumnValue;
		private SettableValue<SettableValue<Boolean>> isSelected;
		private SettableValue<SettableValue<Integer>> theRowIndex;
		private SettableValue<SettableValue<Integer>> theColumnIndex;
		private ModelComponentId theColumnEditValueVariable;
		private Integer theClicks;
		private SettableValue<C> theRawColumnEditValue;
		private SettableValue<C> theFilteredColumnEditValue;
		private SettableValue<SettableValue<String>> isEditable;
		private SettableValue<SettableValue<String>> isAcceptable;
		private QuickWidget theEditor;

		/** @param id The element ID for the editing */
		protected ColumnEditing(Object id) {
			super(id);
			isEditable = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			isAcceptable = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			isSelected = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
			theRowIndex = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(int.class)).build();
			theColumnIndex = SettableValue.build(theRowIndex.getType()).build();
		}

		@Override
		public TableColumnSet<R> getParentElement() {
			return (TableColumnSet<R>) super.getParentElement();
		}

		/** @return The sub-strategy to edit with */
		public ColumnEditType<R, C> getType() {
			return getAddOn(ColumnEditType.class);
		}

		/** @return The model ID of the variable by which the editing column value will be available to expressions */
		public ModelComponentId getColumnEditValueName() {
			return theColumnEditValueVariable;
		}

		/** @return The widget to modify the column value */
		public QuickWidget getEditor() {
			return theEditor;
		}

		/** @return Whether the column is editable for a cell */
		public SettableValue<String> isEditable() {
			return SettableValue.flatten(isEditable);
		}

		/** @return Whether the input value is acceptable for the current cell */
		public SettableValue<String> isAcceptable() {
			return SettableValue.flatten(isAcceptable);
		}

		/** @return The editing column value, disabled and filter-accepted as configured */
		public SettableValue<C> getFilteredColumnEditValue() {
			return theFilteredColumnEditValue;
		}

		/** @return How many clicks are required to activate editing on the column */
		public Integer getClicks() {
			return theClicks;
		}

		/** @param ctx The model context for this editing */
		public void setEditorContext(ColumnEditContext<R, C> ctx) {
			theEditRowValue.set(ctx.getActiveValue(), null);
			theEditColumnValue.set(ctx.getEditColumnValue(), null);
			isSelected.set(ctx.isSelected(), null);
			theRowIndex.set(ctx.getRowIndex(), null);
			theColumnIndex.set(ctx.getColumnIndex(), null);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			ColumnEditing.Interpreted<R, C> myInterpreted = (ColumnEditing.Interpreted<R, C>) interpreted;

			TypeToken<R> rowType;
			try {
				rowType = myInterpreted.getParentElement().getParentElement().getValueType();
			} catch (ExpressoInterpretationException e) {
				throw new IllegalStateException("Not initialized?", e);
			}
			if (theEditRowValue == null || !theRowType.equals(rowType)) {
				theEditRowValue = SettableValue
					.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<R>> parameterized(rowType)).build();
			}
			if (theRawColumnEditValue == null || !theRawColumnEditValue.getType().equals(myInterpreted.getColumnType())) {
				TypeToken<C> columnType = myInterpreted.getColumnType();
				C defValue = TypeTokens.get().getDefaultValue(columnType);
				theRawColumnEditValue = SettableValue.build(columnType).withValue(defValue).build();
				theFilteredColumnEditValue = SettableValue.build(columnType).withValue(defValue).build()//
					// .disableWith(SettableValue.flatten(isEditable))//
					.filterAccept(v -> {
						SettableValue<String> accept = isAcceptable.get();
						if (accept == null)
							return null;
						theRawColumnEditValue.set(v, null);
						return accept == null ? null : accept.get();
					});
				theEditColumnValue = SettableValue
					.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<C>> parameterized(myInterpreted.getColumnType()))
					.build();
			}

			theColumnEditValueVariable = myInterpreted.getDefinition().getColumnEditValueVariable();
			theClicks = myInterpreted.getDefinition().getClicks();
			theEditableInstantiator = myInterpreted.isEditable() == null ? null : myInterpreted.isEditable().instantiate();
			theAcceptInstantiator = myInterpreted.isAcceptable() == null ? null : myInterpreted.isAcceptable().instantiate();

			if (myInterpreted.getEditor() == null)
				theEditor = null;
			else if (theEditor == null || theEditor.getIdentity() != myInterpreted.getEditor().getDefinition().getIdentity())
				theEditor = myInterpreted.getEditor().create();
			if (theEditor != null)
				theEditor.update(myInterpreted.getEditor(), this);
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();

			if (theEditableInstantiator != null)
				theEditableInstantiator.instantiate();
			if (theAcceptInstantiator != null)
				theAcceptInstantiator.instantiate();

			if (theEditor != null)
				theEditor.instantiated();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			// isEditable is called from rendering
			isEditable.set(theEditableInstantiator == null ? null : theEditableInstantiator.get(myModels), null);

			MultiValueRenderable<R> owner = getOwner(getParentElement());
			ModelSetInstance editorModels = copyTableModels(myModels.copy(), owner).build();
			ExFlexibleElementModelAddOn.satisfyElementValue(theColumnEditValueVariable, editorModels,
				SettableValue.flatten(theEditColumnValue));
			ColumnEditType<R, C> editing = getAddOn(ColumnEditType.class);
			if (owner != null)
				replaceTableValues(editorModels, owner, SettableValue.flatten(theEditRowValue), SettableValue.flatten(isSelected),
					SettableValue.flatten(theRowIndex), SettableValue.flatten(theColumnIndex));
			if (theEditor != null)
				theEditor.instantiate(editorModels);
			if (editing != null)
				editing.instantiateEditor(editorModels);
			isAcceptable.set(theAcceptInstantiator == null ? null : theAcceptInstantiator.get(editorModels), null);
		}

		@Override
		public ColumnEditing<R, C> copy(ExElement parent) {
			ColumnEditing<R, C> copy = (ColumnEditing<R, C>) super.copy(parent);

			C defValue = TypeTokens.get().getDefaultValue(theRawColumnEditValue.getType());
			copy.isEditable = SettableValue.build(isEditable.getType()).build();
			copy.isAcceptable = SettableValue.build(isAcceptable.getType()).build();
			copy.theRawColumnEditValue = SettableValue.build(theRawColumnEditValue.getType()).withValue(defValue).build();
			copy.theFilteredColumnEditValue = SettableValue.build(theRawColumnEditValue.getType()).withValue(defValue).build()//
				// .disableWith(SettableValue.flatten(copy.isEditable))//
				.filterAccept(v -> {
					SettableValue<String> accept = copy.isAcceptable.get();
					if (accept == null)
						return null;
					copy.theRawColumnEditValue.set(v, null);
					return accept == null ? null : accept.get();
				});
			copy.theEditRowValue = SettableValue.build(theEditRowValue.getType()).build();
			copy.theEditColumnValue = SettableValue.build(theEditColumnValue.getType()).build();
			copy.isSelected = SettableValue.build(isSelected.getType()).build();
			copy.theRowIndex = SettableValue.build(theRowIndex.getType()).build();
			copy.theColumnIndex = SettableValue.build(theRowIndex.getType()).build();

			copy.theEditor = theEditor.copy(copy);

			return copy;
		}
	}

	/**
	 * @param <R> The row type of the table
	 * @param columns The column set to get the widget for
	 * @return The widget that the column set is for
	 */
	static <R> MultiValueRenderable<R> getOwner(TableColumnSet<R> columns) {
		ExElement parent = columns.getParentElement();
		while (parent != null && !(parent instanceof MultiValueRenderable))
			parent = parent.getParentElement();
		return (MultiValueRenderable<R>) parent;
	}

	/**
	 * @param columnModels The models of the table column
	 * @param owner The models of the table or widget that the columns are for
	 * @return A model instance containing copies of the column and row values to be used independently of the model context of the widget
	 *         and the column
	 * @throws ModelInstantiationException If the models could not be copied
	 */
	static ModelSetInstanceBuilder copyTableModels(ModelSetInstanceBuilder columnModels, MultiValueRenderable<?> owner)
		throws ModelInstantiationException {
		if (owner == null)
			return columnModels;
		ExWithElementModel ownerElModels = owner.getAddOn(ExWithElementModel.class);
		ModelInstantiator ownerModels = ownerElModels.getElement().getModels();
		ModelComponentId ownerModelId;
		if (ownerModels.getLocalTagValue(ExModelAugmentation.ELEMENT_MODEL_TAG) == owner.getIdentity())
			ownerModelId = ownerModels.getIdentity();
		else {
			ownerModelId = null;
			for (ModelComponentId inh : ownerModels.getInheritance()) {
				if (ownerModels.getInheritance(inh).getLocalTagValue(ExModelAugmentation.ELEMENT_MODEL_TAG) == owner.getIdentity()) {
					ownerModelId = inh;
					break;
				}
			}
		}
		ModelSetInstanceBuilder columnModelBuilder = columnModels;
		if (ownerModelId != null)
			columnModelBuilder.withAll(columnModels.getInherited(ownerModelId).copy(columnModels.getUntil()).build());

		return columnModelBuilder;
	}

	/**
	 * @param <R> The row type of the tabular widget
	 * @param columnModels The models of the table column
	 * @param owner The widget that the column set is for
	 * @param rowValue The value to replace for the row value in the model
	 * @param selected The value to replace for the selected value in the model
	 * @param rowIndex The value to replace for the row index in the model
	 * @param columnIndex The value to replace for the column index in the model
	 * @throws ModelInstantiationException If the model values could not be replaced
	 */
	static <R> void replaceTableValues(ModelSetInstance columnModels, MultiValueRenderable<R> owner, SettableValue<R> rowValue,
		SettableValue<Boolean> selected, SettableValue<Integer> rowIndex, SettableValue<Integer> columnIndex)
			throws ModelInstantiationException {
		if (rowValue != null && owner.getActiveValueVariable() != null)
			ExFlexibleElementModelAddOn.satisfyElementValue(owner.getActiveValueVariable(), columnModels, rowValue);
		if (selected != null && owner.getSelectedVariable() != null)
			ExFlexibleElementModelAddOn.satisfyElementValue(owner.getSelectedVariable(), columnModels, selected);
		if (owner instanceof TabularWidget) {
			TabularWidget<R> table = (TabularWidget<R>) owner;
			if (rowIndex != null && table.getRowIndexVariable() != null)
				ExFlexibleElementModelAddOn.satisfyElementValue(table.getRowIndexVariable(), columnModels, rowIndex);
			if (columnIndex != null && table.getColumnIndexVariable() != null)
				ExFlexibleElementModelAddOn.satisfyElementValue(table.getColumnIndexVariable(), columnModels, columnIndex);
		}
	}

	/**
	 * A sub-strategy for editing column values. {@link ColumnEditing} contains all the information common to any column editing strategy,
	 * but lacks the actual means to effect an edit.
	 *
	 * @param <R> The row type of the tabular widget
	 * @param <C> The value type of the column
	 */
	public abstract class ColumnEditType<R, C> extends ExAddOn.Abstract<ColumnEditing<R, C>> {
		/**
		 * {@link ColumnEditType} definition
		 *
		 * @param <CET> The sub-type of editing to create
		 */
		public static abstract class Def<CET extends ColumnEditType<?, ?>> extends ExAddOn.Def.Abstract<ColumnEditing<?, ?>, CET> {
			/**
			 * @param type The Qonfig type of this add-on
			 * @param element The column editing to define
			 */
			protected Def(QonfigAddOn type, ColumnEditing.Def element) {
				super(type, element);
			}

			@Override
			public ColumnEditing.Def getElement() {
				return (ColumnEditing.Def) super.getElement();
			}

			@Override
			public abstract Interpreted<?, ?, ? extends CET> interpret(ExElement.Interpreted<?> element);
		}

		/**
		 * {@link ColumnEditType} interpretation
		 *
		 * @param <R> The row type of the tabular widget
		 * @param <C> The value type of the column
		 * @param <CET> The sub-type of editing to create
		 */
		public static abstract class Interpreted<R, C, CET extends ColumnEditType<R, C>>
		extends ExAddOn.Interpreted.Abstract<ColumnEditing<R, C>, CET> {
			/**
			 * @param definition The definition to interpret
			 * @param element The column editing to define
			 */
			protected Interpreted(Def<? super CET> definition, ColumnEditing.Interpreted<R, C> element) {
				super(definition, element);
			}

			@Override
			public Def<? super CET> getDefinition() {
				return (Def<? super CET>) super.getDefinition();
			}
		}

		@Override
		public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
			// Do nothing. We need to use the editor models, which are passed intentionally from the editing via the method below
		}

		/**
		 * @param editorModels The models for the editor
		 * @throws ModelInstantiationException If the editor could not be instantiated
		 */
		public abstract void instantiateEditor(ModelSetInstance editorModels) throws ModelInstantiationException;

		/** @param element The column editing to define */
		protected ColumnEditType(ColumnEditing<R, C> element) {
			super(element);
		}

		/**
		 * A column editing strategy that modifies the editing row of the table with the edited column value
		 *
		 * @param <R> The row type of the tabular widget
		 * @param <C> The value type of the column
		 */
		public static class RowModifyEditType<R, C> extends ColumnEditType<R, C> {
			/** The XML name of this add-on */
			public static final String MODIFY = "modify-row-value";

			/** {@link RowModifyEditType} definition */
			@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
				qonfigType = MODIFY,
				interpretation = Interpreted.class,
				instance = RowModifyEditType.class)
			public static class Def extends ColumnEditType.Def<RowModifyEditType<?, ?>> {
				private CompiledExpression theCommit;
				private boolean isRowUpdate;

				/**
				 * @param type The Qonfig type of this add-on
				 * @param element The column editing to define
				 */
				public Def(QonfigAddOn type, QuickTableColumn.ColumnEditing.Def element) {
					super(type, element);
				}

				/** @return The action that modifies the editing row with the edited column value */
				@QonfigAttributeGetter("commit")
				public CompiledExpression getCommit() {
					return theCommit;
				}

				/** @return Whether to fire an update event on the edited row after an edit */
				@QonfigAttributeGetter("row-update")
				public boolean isRowUpdate() {
					return isRowUpdate;
				}

				@Override
				public void update(ExpressoQIS session, ExElement.Def<? extends ColumnEditing<?, ?>> element)
					throws QonfigInterpretationException {
					super.update(session, element);
					theCommit = element.getAttributeExpression("commit", session);
					isRowUpdate = session.getAttribute("row-update", boolean.class);
				}

				@Override
				public Interpreted<?, ?> interpret(ExElement.Interpreted<?> element) {
					return new Interpreted<>(this, (ColumnEditing.Interpreted<?, ?>) element);
				}
			}

			/**
			 * {@link RowModifyEditType} interpretation
			 *
			 * @param <R> The row type of the tabular widget
			 * @param <C> The value type of the column
			 */
			public static class Interpreted<R, C> extends ColumnEditType.Interpreted<R, C, RowModifyEditType<R, C>> {
				private InterpretedValueSynth<ObservableAction, ObservableAction> theCommit;

				/**
				 * @param definition The definition to interpret
				 * @param element The column editing to define
				 */
				protected Interpreted(Def definition, ColumnEditing.Interpreted<R, C> element) {
					super(definition, element);
				}

				@Override
				public Def getDefinition() {
					return (Def) super.getDefinition();
				}

				/** @return The action that modifies the editing row with the edited column value */
				public InterpretedValueSynth<ObservableAction, ObservableAction> getCommit() {
					return theCommit;
				}

				@Override
				public void update(ExElement.Interpreted<? extends ColumnEditing<R, C>> element) throws ExpressoInterpretationException {
					super.update(element);
					theCommit = getElement().interpret(getDefinition().getCommit(), ModelTypes.Action.instance());
				}

				@Override
				public Class<RowModifyEditType<R, C>> getInstanceType() {
					return (Class<RowModifyEditType<R, C>>) (Class<?>) RowModifyEditType.class;
				}

				@Override
				public RowModifyEditType<R, C> create(ColumnEditing<R, C> element) {
					return new RowModifyEditType<>(element);
				}
			}

			private ModelValueInstantiator<ObservableAction> theCommitInstantiator;
			private SettableValue<ObservableAction> theCommit;
			private boolean isRowUpdate;

			/** @param element The column editing to define */
			protected RowModifyEditType(ColumnEditing<R, C> element) {
				super(element);
				theCommit = SettableValue.build(TypeTokens.get().of(ObservableAction.class)).build();
			}

			/** @return Whether to fire an update event on the edited row after an edit */
			public boolean isRowUpdate() {
				return isRowUpdate;
			}

			/** @return The action that modifies the editing row with the edited column value */
			public ObservableAction getCommit() {
				return ObservableAction.flatten(theCommit);
			}

			@Override
			public Class<Interpreted<R, C>> getInterpretationType() {
				return (Class<Interpreted<R, C>>) (Class<?>) Interpreted.class;
			}

			@Override
			public void update(ExAddOn.Interpreted<? extends ColumnEditing<R, C>, ?> interpreted, ColumnEditing<R, C> element)
				throws ModelInstantiationException {
				super.update(interpreted, element);
				RowModifyEditType.Interpreted<R, C> myInterpreted = (RowModifyEditType.Interpreted<R, C>) interpreted;
				isRowUpdate = myInterpreted.getDefinition().isRowUpdate();
				theCommitInstantiator = myInterpreted.getCommit() == null ? null : myInterpreted.getCommit().instantiate();
			}

			@Override
			public void instantiateEditor(ModelSetInstance editorModels) throws ModelInstantiationException {
				super.instantiate(editorModels);
				theCommit.set(theCommitInstantiator == null ? null : theCommitInstantiator.get(editorModels), null);
			}

			@Override
			public RowModifyEditType<R, C> copy(ColumnEditing<R, C> element) {
				RowModifyEditType<R, C> copy = (RowModifyEditType<R, C>) super.copy(element);

				copy.theCommit = SettableValue.build(theCommit.getType()).build();

				return copy;
			}
		}

		/**
		 * A column editing strategy that replaces the editing row of the table using the edited column value
		 *
		 * @param <R> The row type of the tabular widget
		 * @param <C> The value type of the column
		 */
		public static class RowReplaceEditType<R, C> extends ColumnEditType<R, C> {
			/** The XML name of this add-on */
			public static final String REPLACE = "replace-row-value";

			/** {@link RowReplaceEditType} definition */
			@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
				qonfigType = REPLACE,
				interpretation = Interpreted.class,
				instance = RowModifyEditType.class)
			public static class Def extends ColumnEditType.Def<RowReplaceEditType<?, ?>> {
				private CompiledExpression theReplacement;

				/**
				 * @param type The Qonfig type of this add-on
				 * @param element The column editing to define
				 */
				public Def(QonfigAddOn type, ColumnEditing.Def element) {
					super(type, element);
				}

				/** @return An expression that produces a new row value from the edited column value */
				@QonfigAttributeGetter("replacement")
				public CompiledExpression getReplacement() {
					return theReplacement;
				}

				@Override
				public void update(ExpressoQIS session, ExElement.Def<? extends ColumnEditing<?, ?>> element)
					throws QonfigInterpretationException {
					super.update(session, element);
					theReplacement = element.getAttributeExpression("replacement", session);
				}

				@Override
				public Interpreted<?, ?> interpret(ExElement.Interpreted<?> element) {
					return new Interpreted<>(this, (ColumnEditing.Interpreted<?, ?>) element);
				}
			}

			/**
			 * {@link RowReplaceEditType} interpretation
			 *
			 * @param <R> The row type of the tabular widget
			 * @param <C> The value type of the column
			 */
			public static class Interpreted<R, C> extends ColumnEditType.Interpreted<R, C, RowReplaceEditType<R, C>> {
				private InterpretedValueSynth<SettableValue<?>, SettableValue<R>> theReplacement;

				/**
				 * @param definition The definition to interpret
				 * @param element The column editing to define
				 */
				protected Interpreted(Def definition, ColumnEditing.Interpreted<R, C> element) {
					super(definition, element);
				}

				@Override
				public Def getDefinition() {
					return (Def) super.getDefinition();
				}

				/** @return An expression that produces a new row value from the edited column value */
				public InterpretedValueSynth<SettableValue<?>, SettableValue<R>> getReplacement() {
					return theReplacement;
				}

				@Override
				public void update(ExElement.Interpreted<? extends ColumnEditing<R, C>> element) throws ExpressoInterpretationException {
					super.update(element);
					theReplacement = getElement().interpret(getDefinition().getReplacement(),
						ModelTypes.Value.forType(((ColumnEditing.Interpreted<R, C>) element).getParentElement().getValueType()));
				}

				@Override
				public Class<RowReplaceEditType<R, C>> getInstanceType() {
					return (Class<RowReplaceEditType<R, C>>) (Class<?>) RowReplaceEditType.class;
				}

				@Override
				public RowReplaceEditType<R, C> create(ColumnEditing<R, C> element) {
					return new RowReplaceEditType<>(element);
				}
			}

			private ModelValueInstantiator<SettableValue<R>> theReplacementInstantiator;
			private SettableValue<SettableValue<R>> theReplacement;

			/** @param element The column editing to define */
			protected RowReplaceEditType(ColumnEditing<R, C> element) {
				super(element);
				theReplacement = SettableValue.build(
					TypeTokens.get().keyFor(SettableValue.class).<SettableValue<R>> parameterized(element.getParentElement().getRowType()))
					.build();
			}

			@Override
			public Class<Interpreted<R, C>> getInterpretationType() {
				return (Class<Interpreted<R, C>>) (Class<?>) Interpreted.class;
			}

			/** @return An expression that produces a new row value from the edited column value */
			public SettableValue<R> getReplacement() {
				return SettableValue.flatten(theReplacement);
			}

			@Override
			public void update(ExAddOn.Interpreted<? extends ColumnEditing<R, C>, ?> interpreted, ColumnEditing<R, C> element)
				throws ModelInstantiationException {
				super.update(interpreted, element);
				RowReplaceEditType.Interpreted<R, C> myInterpreted = (RowReplaceEditType.Interpreted<R, C>) interpreted;
				theReplacementInstantiator = myInterpreted.getReplacement() == null ? null : myInterpreted.getReplacement().instantiate();
			}

			@Override
			public void instantiateEditor(ModelSetInstance editorModels) throws ModelInstantiationException {
				super.instantiate(editorModels);
				theReplacement.set(theReplacementInstantiator == null ? null : theReplacementInstantiator.get(editorModels), null);
			}

			@Override
			public RowReplaceEditType<R, C> copy(ColumnEditing<R, C> element) {
				RowReplaceEditType<R, C> copy = (RowReplaceEditType<R, C>) super.copy(element);

				copy.theReplacement = SettableValue.build(theReplacement.getType()).build();

				return copy;
			}
		}
	}

	/**
	 * The &lt;column> element, a single table column
	 *
	 * @param <R> The row type of the tabular widget
	 * @param <C> The value type of the table
	 */
	public class SingleColumnSet<R, C> extends QuickStyledElement.Abstract implements TableColumnSet<R> {
		/** The XML name of this element */
		public static final String COLUMN = "column";

		/** {@link SingleColumnSet} definition */
		@ExMultiElementTraceable({
			@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
				qonfigType = COLUMN,
				interpretation = Interpreted.class,
				instance = SingleColumnSet.class),
			@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = "rendering",
			interpretation = Interpreted.class,
			instance = SingleColumnSet.class) })
		public static class Def extends QuickStyledElement.Def.Abstract<SingleColumnSet<?, ?>>
		implements TableColumnSet.Def<SingleColumnSet<?, ?>> {
			private CompiledExpression theName;
			private ModelComponentId theColumnValueVariable;
			private CompiledExpression theValue;
			private CompiledExpression theHeaderTooltip;
			private Integer theMinWidth;
			private Integer thePrefWidth;
			private Integer theMaxWidth;
			private Integer theWidth;
			private QuickWidget.Def<?> theRenderer;
			private ColumnEditing.Def theEditing;

			/**
			 * @param parent The parent of the column
			 * @param type The Qonfig type of the element
			 */
			public Def(ValueTyped.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public ValueTyped.Def<?> getParentElement() {
				return (ValueTyped.Def<?>) super.getParentElement();
			}

			/** @return The name of the column--the text for the column's header */
			@QonfigAttributeGetter(asType = "column", value = "name")
			public CompiledExpression getName() {
				return theName;
			}

			/** @return The model ID of the variable by which the active column value will be available to expressions */
			@QonfigAttributeGetter(asType = "column", value = "column-value-name")
			public ModelComponentId getColumnValueVariable() {
				return theColumnValueVariable;
			}

			/** @return The current value of this column */
			@QonfigAttributeGetter(asType = "column", value = "value")
			public CompiledExpression getValue() {
				return theValue;
			}

			/** @return The tooltip for this column's header */
			@QonfigAttributeGetter(asType = "column", value = "header-tooltip")
			public CompiledExpression getHeaderTooltip() {
				return theHeaderTooltip;
			}

			/** @return The minimum width of this column, in pixels */
			@QonfigAttributeGetter(asType = "column", value = "min-width")
			public Integer getMinWidth() {
				return theMinWidth;
			}

			/** @return The preferred width of this column, in pixels */
			@QonfigAttributeGetter(asType = "column", value = "pref-width")
			public Integer getPrefWidth() {
				return thePrefWidth;
			}

			/** @return The maximum width of this column, in pixels */
			@QonfigAttributeGetter(asType = "column", value = "max-width")
			public Integer getMaxWidth() {
				return theMaxWidth;
			}

			/**
			 * @return The width of this column, in pixels. Overrides {@link #getMinWidth() min}, {@link #getPrefWidth() preferred}, and
			 *         {@link #getMaxWidth() max} widths
			 */
			@QonfigAttributeGetter(asType = "column", value = "width")
			public Integer getWidth() {
				return theWidth;
			}

			/** @return The renderer to represent the column value to the user when they are not interacting with it */
			@QonfigChildGetter(asType = "rendering", value = "renderer")
			@Override
			public QuickWidget.Def<?> getRenderer() {
				return theRenderer;
			}

			/** @return The strategy for editing values in this column */
			@QonfigChildGetter(asType = "column", value = "edit")
			@Override
			public ColumnEditing.Def getEditing() {
				return theEditing;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement("styled"));
				theName = getAttributeExpression("name", session);
				String columnValueName = session.getAttributeText("column-value-name");
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theColumnValueVariable = elModels.getElementValueModelId(columnValueName);
				theValue = getAttributeExpression("value", session);
				theHeaderTooltip = getAttributeExpression("header-tooltip", session);
				String w = session.getAttributeText("min-width");
				theMinWidth = w == null ? null : Integer.parseInt(w);
				w = session.getAttributeText("pref-width");
				thePrefWidth = w == null ? null : Integer.parseInt(w);
				w = session.getAttributeText("max-width");
				theMaxWidth = w == null ? null : Integer.parseInt(w);
				w = session.getAttributeText("width");
				theWidth = w == null ? null : Integer.parseInt(w);

				ExpressoQIS renderer = session.forChildren("renderer").peekFirst();
				if (renderer == null)
					renderer = session.metadata().get("default-renderer").get().peekFirst();
				theRenderer = syncChild(QuickWidget.Def.class, theRenderer, renderer, null);
				theEditing = syncChild(ColumnEditing.Def.class, theEditing, session, "edit");
				elModels.satisfyElementValueType(theColumnValueVariable, ModelTypes.Value,
					(interp, env) -> ModelTypes.Value.forType(((Interpreted<?, ?>) interp).getType()));
			}

			@Override
			protected ColumnStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new ColumnStyle.Def(parentStyle, this, style);
			}

			@Override
			public <R> Interpreted<R, ?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, (ValueTyped.Interpreted<R, ?>) parent);
			}
		}

		/**
		 * {@link SingleColumnSet} interpretation
		 *
		 * @param <R> The row type of the tabular widget
		 * @param <C> The value type of the table
		 */
		public static class Interpreted<R, C> extends QuickStyledElement.Interpreted.Abstract<SingleColumnSet<R, C>>
		implements TableColumnSet.Interpreted<R, SingleColumnSet<R, C>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theName;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<C>> theValue;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theHeaderTooltip;
			private QuickWidget.Interpreted<?> theRenderer;
			private ColumnEditing.Interpreted<R, C> theEditing;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element
			 */
			protected Interpreted(SingleColumnSet.Def definition, ValueTyped.Interpreted<R, ?> parent) {
				super(definition, parent);
				if (!(parent instanceof MultiValueWidget.Interpreted))
					throw new IllegalStateException("The parent of a column must be a multi-value-widget");
			}

			@Override
			public SingleColumnSet.Def getDefinition() {
				return (SingleColumnSet.Def) super.getDefinition();
			}

			@Override
			public MultiValueWidget.Interpreted<R, ?> getParentElement() {
				return (MultiValueWidget.Interpreted<R, ?>) super.getParentElement();
			}

			/** @return The name of the column--the text for the column's header */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getName() {
				return theName;
			}

			/** @return The current value of this column */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<C>> getValue() {
				return theValue;
			}

			/** @return The tooltip for this column's header */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getHeaderTooltip() {
				return theHeaderTooltip;
			}

			/** @return The renderer to represent the column value to the user when they are not interacting with it */
			@Override
			public QuickWidget.Interpreted<?> getRenderer() {
				return theRenderer;
			}

			/** @return The strategy for editing values in this column */
			@Override
			public ColumnEditing.Interpreted<R, C> getEditing() {
				return theEditing;
			}

			@Override
			public TypeToken<R> getValueType() throws ExpressoInterpretationException {
				return ((ValueTyped.Interpreted<R, ?>) getParentElement()).getValueType();
			}

			/** @return The value type of the column */
			public TypeToken<C> getType() {
				return (TypeToken<C>) theValue.getType().getType(0);
			}

			@Override
			public void updateColumns(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				theValue = interpret(getDefinition().getValue(), ModelTypes.Value.<C> anyAsV());
				super.doUpdate(env);
				theName = interpret(getDefinition().getName(), ModelTypes.Value.STRING);
				theHeaderTooltip = interpret(getDefinition().getHeaderTooltip(), ModelTypes.Value.STRING);

				theRenderer = syncChild(getDefinition().getRenderer(), theRenderer, def -> def.interpret(this),
					(r, rEnv) -> r.updateElement(rEnv));
				theEditing = syncChild(getDefinition().getEditing(), theEditing,
					def -> (ColumnEditing.Interpreted<R, C>) def.interpret(this), (e, eEnv) -> e.update(getType(), eEnv));
			}

			@Override
			public void destroy() {
				if (theRenderer != null) {
					theRenderer.destroy();
					theRenderer = null;
				}
				if (theEditing != null) {
					theEditing.destroy();
					theEditing = null;
				}
				super.destroy();
			}

			@Override
			public SingleColumnSet<R, C> create() {
				return new SingleColumnSet<>(getIdentity());
			}
		}

		private TypeToken<R> theRowType;
		private TypeToken<C> theColumnType;
		private ObservableCollection<SingleColumn> theColumn;

		private ModelValueInstantiator<SettableValue<String>> theNameInstantiator;
		private ModelValueInstantiator<SettableValue<C>> theValueInstantiator;
		private ModelValueInstantiator<SettableValue<String>> theHeaderTooltipInstantiator;

		private Integer theMinWidth;
		private Integer thePrefWidth;
		private Integer theMaxWidth;
		private Integer theWidth;

		private SettableValue<SettableValue<String>> theName;
		private ModelComponentId theColumnValueVariable;
		private SettableValue<SettableValue<C>> theValue;
		private SettableValue<SettableValue<String>> theHeaderTooltip;
		private QuickWidget theRenderer;
		private ColumnEditing<R, C> theEditing;

		private Observable<? extends Causable> theRenderStyleChanges;

		/** @param id The element ID for the column */
		protected SingleColumnSet(Object id) {
			super(id);
			theName = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
				.build();
			theHeaderTooltip = SettableValue.build(theName.getType()).build();
		}

		@Override
		public ValueTyped<R> getParentElement() {
			return (ValueTyped<R>) super.getParentElement();
		}

		/** @return The name of the column--the text for the column's header */
		public SettableValue<String> getName() {
			return SettableValue.flatten(theName);
		}

		/** @return The model ID of the variable by which the active column value will be available to expressions */
		public ModelComponentId getColumnValueVariable() {
			return theColumnValueVariable;
		}

		@Override
		public TypeToken<R> getRowType() {
			return theRowType;
		}

		@Override
		public ObservableCollection<? extends QuickTableColumn<R, ?>> getColumns() {
			return theColumn;
		}

		/** @return The current value of this column */
		public SettableValue<C> getValue() {
			return SettableValue.flatten(theValue);
		}

		/** @return The tooltip for this column's header */
		public SettableValue<String> getHeaderTooltip() {
			return SettableValue.flatten(theHeaderTooltip);
		}

		/** @return The renderer to represent the column value to the user when they are not interacting with it */
		public QuickWidget getRenderer() {
			return theRenderer;
		}

		/** @return The strategy for editing values in this column */
		public ColumnEditing<R, C> getEditing() {
			return theEditing;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			SingleColumnSet.Interpreted<R, C> myInterpreted = (SingleColumnSet.Interpreted<R, C>) interpreted;
			TypeToken<R> rowType;
			try {
				rowType = myInterpreted.getValueType();
			} catch (ExpressoInterpretationException e) {
				throw new IllegalStateException("Not initialized?", e);
			}
			if (theRowType == null || !theRowType.equals(rowType) || !theColumnType.equals(myInterpreted.getType())) {
				theRowType = rowType;
				theColumnType = myInterpreted.getType();
				theValue = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<C>> parameterized(theColumnType))
					.build();
				theColumn = ObservableCollection.of((Class<SingleColumnSet<R, C>.SingleColumn>) (Class<?>) SingleColumn.class,
					new SingleColumn());
			}
			theNameInstantiator = myInterpreted.getName().instantiate();
			theColumnValueVariable = myInterpreted.getDefinition().getColumnValueVariable();
			theValueInstantiator = myInterpreted.getValue().instantiate();
			theHeaderTooltipInstantiator = myInterpreted.getHeaderTooltip() == null ? null : myInterpreted.getHeaderTooltip().instantiate();

			theMinWidth = myInterpreted.getDefinition().getMinWidth();
			thePrefWidth = myInterpreted.getDefinition().getPrefWidth();
			theMaxWidth = myInterpreted.getDefinition().getMaxWidth();
			theWidth = myInterpreted.getDefinition().getWidth();

			if (myInterpreted.getRenderer() == null)
				theRenderer = null;
			else if (theRenderer == null || theRenderer.getIdentity() != myInterpreted.getRenderer().getIdentity()) {
				try {
					theRenderer = myInterpreted.getRenderer().create();
				} catch (RuntimeException | Error e) {
					myInterpreted.getRenderer().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(),
						e);
				}
			}
			if (theRenderer != null) {
				try {
					theRenderer.update(myInterpreted.getRenderer(), this);
				} catch (RuntimeException | Error e) {
					myInterpreted.getRenderer().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(),
						e);
				}
			}

			if (myInterpreted.getEditing() == null)
				theEditing = null;
			else if (theEditing == null || theEditing.getIdentity() != myInterpreted.getEditing().getIdentity())
				theEditing = myInterpreted.getEditing().create();
			if (theEditing != null)
				theEditing.update(myInterpreted.getEditing(), this);
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();
			if (theRenderer != null)
				theRenderer.instantiated();
			if (theEditing != null)
				theEditing.instantiated();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);
			ExFlexibleElementModelAddOn.satisfyElementValue(theColumnValueVariable, myModels, getValue(),
				ExFlexibleElementModelAddOn.ActionIfSatisfied.Ignore);
			theName.set(theNameInstantiator.get(myModels), null);
			theValue.set(theValueInstantiator.get(myModels), null);
			theHeaderTooltip.set(theHeaderTooltipInstantiator == null ? null : theHeaderTooltipInstantiator.get(myModels), null);

			if (theRenderer != null) {
				// TODO
				// ModelSetInstance rendererModels = theRenderer.instantiate(myModels);
				/* Create a copy of the renderer with table context values that don't change.
				 * This will enable the table to be rendered when outside influences on the style change
				 * without incurring the performance hit of all the style update eventing for every table cell rendered.
				 */
				// This still doesn't work. The rendererCopy.instantiate(MSI) method throws an exception satisfying widget element values
				// because these have already been satisfied.
				// MultiValueRenderable<R> owner = getOwner(this);
				// ModelSetInstance rendererModelsCopy = copyTableModels(rendererModels.copy(), owner)//
				// .with(myModels.getInherited(getModels().getIdentity()).copy().build())//
				// .build();
				// ExFlexibleElementModelAddOn.satisfyElementValue(theColumnValueVariable, rendererModelsCopy, //
				// SettableValue.of(theColumnType, TypeTokens.get().getDefaultValue(theColumnType), "Immutable"),
				// ExFlexibleElementModelAddOn.ActionIfSatisfied.Replace);
				// replaceTableValues(rendererModelsCopy, owner, //
				// SettableValue.of(theRowType, TypeTokens.get().getDefaultValue(theRowType), "Immutable"), //
				// SettableValue.of(boolean.class, false, "Immutable"), //
				// SettableValue.of(int.class, 0, "Immutable"), //
				// SettableValue.of(int.class, 0, "Immutable"));
				// QuickWidget rendererCopy = theRenderer.copy(this);
				// rendererCopy.instantiate(rendererModelsCopy);
				// theRenderStyleChanges = rendererCopy.getStyle().changes();
				theRenderStyleChanges = Observable.empty();
			} else
				theRenderStyleChanges = Observable.empty();
			if (theEditing != null)
				theEditing.instantiate(myModels);
		}

		@Override
		public SingleColumnSet<R, C> copy(ExElement parent) {
			SingleColumnSet<R, C> copy = (SingleColumnSet<R, C>) super.copy(parent);

			copy.theName = SettableValue.build(theName.getType()).build();
			copy.theValue = SettableValue.build(theValue.getType()).build();
			copy.theHeaderTooltip = SettableValue.build(theHeaderTooltip.getType()).build();

			copy.theColumn.clear();
			copy.theColumn.add(copy.new SingleColumn());
			if (theRenderer != null)
				copy.theRenderer = theRenderer.copy(copy);
			if (theEditing != null)
				copy.theEditing = theEditing.copy(copy);

			return copy;
		}

		/** The {@link QuickTableColumn column} of a {@link SingleColumnSet} */
		public class SingleColumn implements QuickTableColumn<R, C> {
			@Override
			public TableColumnSet<R> getColumnSet() {
				return SingleColumnSet.this;
			}

			@Override
			public SettableValue<String> getName() {
				return SingleColumnSet.this.getName();
			}

			@Override
			public TypeToken<C> getType() {
				return theColumnType;
			}

			@Override
			public SettableValue<C> getValue() {
				return SettableValue.flatten(theValue);
			}

			@Override
			public SettableValue<String> getHeaderTooltip() {
				return SingleColumnSet.this.getHeaderTooltip();
			}

			@Override
			public Integer getMinWidth() {
				return theMinWidth;
			}

			@Override
			public Integer getPrefWidth() {
				return thePrefWidth;
			}

			@Override
			public Integer getMaxWidth() {
				return theMaxWidth;
			}

			@Override
			public Integer getWidth() {
				return theWidth;
			}

			@Override
			public QuickWidget getRenderer() {
				return theRenderer;
			}

			@Override
			public Observable<? extends Causable> getRenderStyleChanges() {
				return theRenderStyleChanges;
			}

			@Override
			public ColumnEditing<R, C> getEditing() {
				return theEditing;
			}

			@Override
			public void update() {
			}

			@Override
			public String toString() {
				return SingleColumnSet.this.toString();
			}
		}

		/** Style for a {@link SingleColumnSet &lt;column>} */
		public static class ColumnStyle extends QuickStyledElement.QuickInstanceStyle.Abstract {
			/** {@link ColumnStyle} definition */
			public static class Def extends QuickInstanceStyle.Def.Abstract {
				/**
				 * @param parent The parent Quick style to inherit from
				 * @param styledElement The column element to style
				 * @param wrapped The compiled style to wrap
				 */
				public Def(QuickInstanceStyle.Def parent, SingleColumnSet.Def styledElement, QuickCompiledStyle wrapped) {
					super(parent, styledElement, wrapped);
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
					throws ExpressoInterpretationException {
					return new Interpreted(this, (SingleColumnSet.Interpreted<?, ?>) parentEl, (QuickInstanceStyle.Interpreted) parent,
						getWrapped().interpret(parentEl, parent, env));
				}
			}

			/** {@link ColumnStyle} interpretation */
			public static class Interpreted extends QuickInstanceStyle.Interpreted.Abstract {
				/**
				 * @param compiled The definition to interpret
				 * @param styledElement The column element to style
				 * @param parent The parent style to inherit from
				 * @param wrapped The interpreted style to wrap
				 */
				public Interpreted(Def compiled, SingleColumnSet.Interpreted<?, ?> styledElement, QuickInstanceStyle.Interpreted parent,
					QuickInterpretedStyle wrapped) {
					super(compiled, styledElement, parent, wrapped);
				}

				@Override
				public Def getDefinition() {
					return (Def) super.getDefinition();
				}

				@Override
				public QuickInstanceStyle create(QuickStyledElement styled) {
					return new ColumnStyle();
				}
			}

			@Override
			public SingleColumnSet<?, ?> getStyledElement() {
				return (SingleColumnSet<?, ?>) super.getStyledElement();
			}
		}
	}
}
