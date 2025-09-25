package org.observe.quick.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.collect.CollectionSubscription;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExModelAugmentation;
import org.observe.expresso.qonfig.ExMultiElementTraceable;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ExpressoTransformations;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickValueWidget;
import org.observe.quick.QuickWidget;
import org.observe.quick.base.QuickTransfer.TransferAccept;
import org.observe.quick.base.QuickTransfer.TransferSource;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickStyled;
import org.observe.quick.style.QuickStyled.QuickInstanceStyle;
import org.observe.quick.style.QuickStyledElement;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.ElementId;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.ErrorReporting;

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
			/** @return The renderers to represent the column value to the user when they are not interacting with it */
			List<QuickWidget.Def<?>> getRenderers();

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

			/** @return The super-type of all IDs in this column set, or null if no columns in this set have their ID specified */
			TypeToken<?> getIdType();

			/** @return The renderers to represent the column value to the user when they are not interacting with it */
			List<QuickWidget.Interpreted<?>> getRenderers();

			/** @return The strategy for editing values in this column */
			ColumnEditing.Interpreted<R, ?> getEditing();

			/** @return Transfer sources configured for this column set */
			List<? extends QuickTransfer.TransferSource.Interpreted<?, ?>> getTransferSources();

			/** @return Transfer accepters configured for this column set */
			List<? extends QuickTransfer.TransferAccept.Interpreted<?, ?>> getTransferAccepters();

			/**
			 * Initializes or updates this column set
			 *
			 * @throws ExpressoInterpretationException If this column set could not be interpreted
			 */
			void updateColumns() throws ExpressoInterpretationException;

			/** @return The column set */
			CC create();
		}

		/** @return The columns for the table */
		ObservableCollection<? extends QuickTableColumn<R, ?>> getColumns();

		@Override
		TableColumnSet<R> copy(ExElement parent);
	}

	/** @return The ID of the column, if specified */
	SettableValue<?> getId();

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

	/** @return The renderers to represent the column value to the user when they are not interacting with it */
	List<QuickWidget> getRenderers();

	/** @return The strategy for editing values in this column */
	ColumnEditing<R, C> getEditing();

	/** @return Transfer sources for column values */
	List<QuickTransfer.TransferSource<C, ?>> getTransferSources();

	/** @return Transfer accepters for column values */
	List<QuickTransfer.TransferAccept<C, ?>> getTransferAccepters();

	/** Updates or initializes this column */
	void update();

	/** @return Error Reporting for this column */
	ErrorReporting reporting();

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
			private final List<QuickWidget.Def<?>> theEditors;
			private ModelComponentId theColumnEditValueVariable;
			private CompiledExpression isEditable;
			private CompiledExpression isAcceptable;
			private boolean isRenderingEnabled;
			private Integer theClicks;

			/**
			 * @param parent The column set this editing is for
			 * @param type The Qonfig type of this element
			 */
			public Def(ExElement.Def<? extends TableColumnSet<?>> parent, QonfigElementOrAddOn type) {
				super(parent, type);
				theEditors = new ArrayList<>();
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

			/** @return The widget or widgets to modify the column value (first visible will be used) */
			@QonfigChildGetter("editor")
			public List<QuickWidget.Def<?>> getEditors() {
				return theEditors;
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

			/** @return Whether the enabled state of this column editing should affect the rendering */
			@QonfigAttributeGetter("render-enabled")
			public boolean isRenderingEnabled() {
				return isRenderingEnabled;
			}

			/** @return How many clicks are required to activate editing on the column */
			@QonfigAttributeGetter("clicks")
			public Integer getClicks() {
				return theClicks;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				syncChildren(QuickWidget.Def.class, theEditors, session.forChildren("editor"));
				String columnEditValueName = session.getAttributeText("column-edit-value-name");
				isEditable = getAttributeExpression("editable-if", session);
				isAcceptable = getAttributeExpression("accept", session);
				isRenderingEnabled = session.getAttribute("render-enabled", boolean.class);
				theClicks = session.getAttribute("clicks", Integer.class);
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theColumnEditValueVariable = elModels.getElementValueModelId(columnEditValueName);
				elModels.<Interpreted<?, ?>, SettableValue<?>> satisfyElementSingleValueType(theColumnEditValueVariable, ModelTypes.Value,
					Interpreted::getColumnType);
			}

			/**
			 * @param parent The parent element for the interpreted editing
			 * @return The interpreted editing
			 */
			public Interpreted<?, ?> interpret(ExElement.Interpreted<? extends TableColumnSet<?>> parent) {
				return new Interpreted<>(this, (ExElement.Interpreted<TableColumnSet<Object>>) parent);
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
			private final List<QuickWidget.Interpreted<?>> theEditors;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for the editing
			 */
			protected Interpreted(Def definition, ExElement.Interpreted<? extends TableColumnSet<R>> parent) {
				super(definition, parent);
				theEditors = new ArrayList<>();
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

			/** @return The widget or widgets to modify the column value (first visible will be used) */
			public List<QuickWidget.Interpreted<?>> getEditors() {
				return theEditors;
			}

			/** @return the value type of the column */
			public TypeToken<C> getColumnType() {
				return theColumnType;
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<C>> getValue() throws ExpressoInterpretationException {
				try {
					return getDefaultEnv().getModels().getValue(getDefinition().getColumnEditValueVariable(),
						ModelTypes.Value.forType(theColumnType), getDefaultEnv());
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
			 * @return This editing strategy
			 * @throws ExpressoInterpretationException If the editing strategy could not be interpreted
			 */
			public Interpreted<R, C> update(TypeToken<C> columnType) throws ExpressoInterpretationException {
				theColumnType = columnType;

				super.update();
				isEditable = ExpressoTransformations.parseFilter(getDefinition().isEditable(), this, true);
				return this;
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();

				isAcceptable = ExpressoTransformations.parseFilter(getDefinition().isAcceptable(), this, true);
				syncChildren(getDefinition().getEditors(), theEditors, def -> def.interpret(this), e -> e.updateElement());
			}

			/** @return The editing strategy */
			public ColumnEditing<R, C> create() {
				return new ColumnEditing<>(getIdentity());
			}
		}

		private ModelValueInstantiator<SettableValue<String>> theEditableInstantiator;
		private ModelValueInstantiator<SettableValue<String>> theAcceptInstantiator;

		private SettableValue<R> theEditRowValue;
		private SettableValue<C> theEditColumnValue;
		private SettableValue<Boolean> isSelected;
		private SettableValue<Integer> theRowIndex;
		private SettableValue<Integer> theColumnIndex;
		private ModelComponentId theColumnEditValueVariable;
		private boolean isRenderingEnabled;
		private Integer theClicks;
		private SettableValue<C> theRawColumnEditValue;
		private SettableValue<C> theFilteredColumnEditValue;
		private SettableValue<SettableValue<String>> isEditable;
		private SettableValue<SettableValue<String>> isAcceptable;
		private List<QuickWidget> theEditors;
		private List<SettableValue<Boolean>> theEditorVisibilities;

		/** @param id The element ID for the editing */
		protected ColumnEditing(Object id) {
			super(id);
			isEditable = SettableValue.create();
			isAcceptable = SettableValue.create();
			isSelected = SettableValue.create(b -> b.withValue(false));
			theRowIndex = SettableValue.create(b -> b.withValue(0));
			theColumnIndex = SettableValue.create(b -> b.withValue(0));
			theEditRowValue = SettableValue.create();
			theRawColumnEditValue = SettableValue.create();
			theFilteredColumnEditValue = SettableValue.<C> create()//
				// .disableWith(SettableValue.flatten(isEditable))//
				.filterAccept(v -> {
					SettableValue<String> accept = isAcceptable.get();
					if (accept == null)
						return null;
					theRawColumnEditValue.set(v, null);
					return accept == null ? null : accept.get();
				});
			theEditColumnValue = SettableValue.create();
			theEditors = new ArrayList<>();
			theEditorVisibilities = new ArrayList<>();
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

		/** @return The widget or widgets to modify the column value (first visible will be used) */
		public List<QuickWidget> getEditors() {
			return Collections.unmodifiableList(theEditors);
		}

		/** @return The list of visible values for each of this column editing's editor widgets */
		public List<SettableValue<Boolean>> getEditorVisibilities() {
			return Collections.unmodifiableList(theEditorVisibilities);
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

		/** @return Whether the enabled state of this column editing should affect the rendering */
		public boolean isRenderingEnabled() {
			return isRenderingEnabled;
		}

		/** @return How many clicks are required to activate editing on the column */
		public Integer getClicks() {
			return theClicks;
		}

		/** @return The row value of the cell being edited */
		public SettableValue<R> getEditRowValue() {
			return theEditRowValue;
		}

		/** @return The cell value being edited */
		public SettableValue<C> getEditColumnValue() {
			return theEditColumnValue;
		}

		/** @return Whether the value being edited is in a selected row */
		public SettableValue<Boolean> isSelected() {
			return isSelected;
		}

		/** @return The row index of the cell being edited */
		public SettableValue<Integer> getRowIndex() {
			return theRowIndex;
		}

		/** @return The column index of the cell being edited */
		public SettableValue<Integer> getColumnIndex() {
			return theColumnIndex;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			ColumnEditing.Interpreted<R, C> myInterpreted = (ColumnEditing.Interpreted<R, C>) interpreted;

			theColumnEditValueVariable = myInterpreted.getDefinition().getColumnEditValueVariable();
			isRenderingEnabled = myInterpreted.getDefinition().isRenderingEnabled();
			theClicks = myInterpreted.getDefinition().getClicks();
			theEditableInstantiator = myInterpreted.isEditable() == null ? null : myInterpreted.isEditable().instantiate();
			theAcceptInstantiator = myInterpreted.isAcceptable() == null ? null : myInterpreted.isAcceptable().instantiate();

			syncChildren(myInterpreted.getEditors(), theEditors, QuickWidget.Interpreted::create, QuickWidget::update);
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();

			if (theEditableInstantiator != null)
				theEditableInstantiator.instantiate();
			if (theAcceptInstantiator != null)
				theAcceptInstantiator.instantiate();

			for (QuickWidget editor : theEditors)
				editor.instantiated();
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			myModels = super.doInstantiate(myModels);

			// isEditable is called from rendering
			isEditable.set(theEditableInstantiator == null ? null : theEditableInstantiator.get(myModels), null);

			MultiValueRenderable<R> owner = getOwner(getParentElement());
			if (owner == null) {
				reporting().error("This class needs to be contained by a <multi-value-renderable>");
				return myModels;
			}
			ModelSetInstance editorModels = QuickCoreInterpretation.copyModels(//
				myModels, owner.getActiveValueVariable(), Observable.or(myModels.getUntil(), onDestroy())).build();
			ExFlexibleElementModelAddOn.satisfyElementValue(theColumnEditValueVariable, editorModels, theEditColumnValue);
			ColumnEditType<R, C> editing = getAddOn(ColumnEditType.class);
			replaceTableValues(editorModels, owner, theEditRowValue, isSelected, theRowIndex, theColumnIndex);
			theEditorVisibilities.clear();
			for (QuickWidget editor : theEditors) {
				editor.instantiate(editorModels);
				// The visibility on the editor may use model values from the editor models.
				// We need a copy that only uses renderer models, since the editor visibility is the test for whether an editor
				// can edit a model value, and that test is made using the renderer models.
				ModelValueInstantiator<SettableValue<Boolean>> vizInstantiator = editor.getVisibleInstantiator();
				if (vizInstantiator == null)
					theEditorVisibilities.add(SettableValue.of(true, "Always Visible"));
				else {
					try {
						theEditorVisibilities.add(vizInstantiator.get(myModels));
					} catch (ModelInstantiationException e) {
						// This succeeded when instantiating in the QuickWidget, so this almost certainly means
						// that the visibility uses an editor-specific value, which is not ok
						editor.reporting().error("Editor visibility cannot use any editor-specific values", e);
					}
				}
			}
			if (editing != null)
				editing.instantiateEditor(editorModels);
			isAcceptable.set(theAcceptInstantiator == null ? null : theAcceptInstantiator.get(editorModels), null);
			return myModels;
		}

		@Override
		public ColumnEditing<R, C> copy(ExElement parent) {
			ColumnEditing<R, C> copy = (ColumnEditing<R, C>) super.copy(parent);

			copy.isEditable = SettableValue.<SettableValue<String>> build().build();
			copy.isAcceptable = SettableValue.<SettableValue<String>> build().build();
			copy.theRawColumnEditValue = SettableValue.<C> build().build();
			copy.theFilteredColumnEditValue = SettableValue.<C> build().build()//
				// .disableWith(SettableValue.flatten(copy.isEditable))//
				.filterAccept(v -> {
					SettableValue<String> accept = copy.isAcceptable.get();
					if (accept == null)
						return null;
					copy.theRawColumnEditValue.set(v, null);
					return accept == null ? null : accept.get();
				});
			copy.theEditRowValue = SettableValue.create();
			copy.theEditColumnValue = SettableValue.create();
			copy.isSelected = SettableValue.create(b -> b.withValue(false));
			copy.theRowIndex = SettableValue.create(b -> b.withValue(0));
			copy.theColumnIndex = SettableValue.create(b -> b.withValue(0));

			copy.theEditors = new ArrayList<>();
			// Editor visibilities are populated in doInstantiate(MSI)
			copy.theEditorVisibilities = new ArrayList<>();
			for (QuickWidget editor : theEditors)
				copy.theEditors.add(editor.copy(copy));

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
			TabularWidget<R, ?> table = (TabularWidget<R, ?>) owner;
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
			protected Def(QonfigAddOn type, ExElement.Def<? extends ColumnEditing<?, ?>> element) {
				super(type, element);
			}

			@Override
			public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
				return Collections.singleton((Class<ExAddOn.Def<?, ?>>) (Class<?>) ExModelAugmentation.Def.class);
			}

			@Override
			public ColumnEditing.Def getElement() {
				return (ColumnEditing.Def) super.getElement();
			}

			@Override
			public abstract <E2 extends ColumnEditing<?, ?>> Interpreted<?, ?, ? extends CET> interpret(ExElement.Interpreted<E2> element);
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
			protected Interpreted(Def<? super CET> definition, ExElement.Interpreted<? extends ColumnEditing<R, C>> element) {
				super(definition, element);
			}

			@Override
			public Def<? super CET> getDefinition() {
				return (Def<? super CET>) super.getDefinition();
			}
		}

		@Override
		public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
			// Do nothing. We need to use the editor models, which are passed intentionally from the editing via the method below
			return models;
		}

		/**
		 * @param editorModels The models for the editor
		 * @throws ModelInstantiationException If the editor could not be instantiated
		 */
		public abstract void instantiateEditor(ModelSetInstance editorModels) throws ModelInstantiationException;

		/** @param element The column editing to define */
		protected ColumnEditType(ExElement element) {
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
				public Def(QonfigAddOn type, ExElement.Def<? extends ColumnEditing<?, ?>> element) {
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
				public <E2 extends ColumnEditing<?, ?>> Interpreted<?, ?> interpret(ExElement.Interpreted<E2> element) {
					return new Interpreted<>(this, (ExElement.Interpreted<ColumnEditing<Object, Object>>) element);
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
				protected Interpreted(Def definition, ExElement.Interpreted<? extends ColumnEditing<R, C>> element) {
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
				public RowModifyEditType<R, C> create(ExElement element) {
					return new RowModifyEditType<>(element);
				}
			}

			private ModelValueInstantiator<ObservableAction> theCommitInstantiator;
			private SettableValue<ObservableAction> theCommit;
			private boolean isRowUpdate;

			/** @param element The column editing to define */
			protected RowModifyEditType(ExElement element) {
				super(element);
				theCommit = SettableValue.<ObservableAction> build().build();
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
			public void update(ExAddOn.Interpreted<? super ColumnEditing<R, C>, ?> interpreted, ExElement element)
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
			public RowModifyEditType<R, C> copy(ExElement element) {
				RowModifyEditType<R, C> copy = (RowModifyEditType<R, C>) super.copy(element);

				copy.theCommit = SettableValue.<ObservableAction> build().build();

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
				public Def(QonfigAddOn type, ExElement.Def<? extends ColumnEditing<?, ?>> element) {
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
				public <E2 extends ColumnEditing<?, ?>> Interpreted<?, ?> interpret(ExElement.Interpreted<E2> element) {
					return new Interpreted<>(this, (ExElement.Interpreted<? extends ColumnEditing<Object, Object>>) element);
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
				protected Interpreted(Def definition, ExElement.Interpreted<? extends ColumnEditing<R, C>> element) {
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
				public RowReplaceEditType<R, C> create(ExElement element) {
					return new RowReplaceEditType<>(element);
				}
			}

			private ModelValueInstantiator<SettableValue<R>> theReplacementInstantiator;
			private SettableValue<SettableValue<R>> theReplacement;

			/** @param element The column editing to define */
			protected RowReplaceEditType(ExElement element) {
				super(element);
				theReplacement = SettableValue.<SettableValue<R>> build().build();
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
			public void update(ExAddOn.Interpreted<? super ColumnEditing<R, C>, ?> interpreted, ExElement element)
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
			public RowReplaceEditType<R, C> copy(ExElement element) {
				RowReplaceEditType<R, C> copy = (RowReplaceEditType<R, C>) super.copy(element);

				copy.theReplacement = SettableValue.<SettableValue<R>> build().build();

				return copy;
			}
		}
	}

	/**
	 * Abstract class for a &lt;column> element
	 *
	 * @param <R> The row type of the tabular widget
	 * @param <C> The value type of the table
	 */
	public abstract class AbstractSingleColumn<R, C> extends QuickStyledElement.Abstract implements TableColumnSet<R> {
		/** The XML name of this element */
		public static final String COLUMN = "column";

		/**
		 * {@link AbstractSingleColumn} definition
		 *
		 * @param <SCS> The sub-type of {@link AbstractSingleColumn} this definition creates
		 */
		@ExMultiElementTraceable({
			@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
				qonfigType = COLUMN,
				interpretation = Interpreted.class,
				instance = AbstractSingleColumn.class),
			@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = "rendering",
			interpretation = Interpreted.class,
			instance = AbstractSingleColumn.class) })
		public static abstract class Def<SCS extends AbstractSingleColumn<?, ?>> extends QuickStyledElement.Def.Abstract<SCS>
		implements TableColumnSet.Def<SCS> {
			private CompiledExpression theId;
			private CompiledExpression theName;
			private ModelComponentId theColumnValueVariable;
			private CompiledExpression theValue;
			private CompiledExpression theHeaderTooltip;
			private Integer theMinWidth;
			private Integer thePrefWidth;
			private Integer theMaxWidth;
			private Integer theWidth;
			private final List<QuickWidget.Def<?>> theRenderers;
			private ColumnEditing.Def theEditing;
			private final List<QuickTransfer.TransferSource.Def> theTransferSources;
			private final List<QuickTransfer.TransferAccept.Def> theTransferAccepters;

			/**
			 * @param parent The parent of the column
			 * @param type The Qonfig type of the element
			 */
			protected Def(ExElement.Def<? extends ValueTyped<?>> parent, QonfigElementOrAddOn type) {
				super(parent, type);
				theRenderers = new ArrayList<>();
				theTransferSources = new ArrayList<>();
				theTransferAccepters = new ArrayList<>();
			}

			/** @return The ID of the column if it is specified */
			@QonfigAttributeGetter(asType = COLUMN, value = "id")
			public CompiledExpression getId() {
				return theId;
			}

			/** @return The name of the column--the text for the column's header */
			@QonfigAttributeGetter(asType = COLUMN, value = "name")
			public CompiledExpression getName() {
				return theName;
			}

			/** @return The model ID of the variable by which the active column value will be available to expressions */
			@QonfigAttributeGetter(asType = COLUMN, value = "column-value-name")
			public ModelComponentId getColumnValueVariable() {
				return theColumnValueVariable;
			}

			/** @return The current value of this column */
			@QonfigAttributeGetter(asType = COLUMN, value = "value")
			public CompiledExpression getValue() {
				return theValue;
			}

			/** @return The tooltip for this column's header */
			@QonfigAttributeGetter(asType = COLUMN, value = "header-tooltip")
			public CompiledExpression getHeaderTooltip() {
				return theHeaderTooltip;
			}

			/** @return The minimum width of this column, in pixels */
			@QonfigAttributeGetter(asType = COLUMN, value = "min-width")
			public Integer getMinWidth() {
				return theMinWidth;
			}

			/** @return The preferred width of this column, in pixels */
			@QonfigAttributeGetter(asType = COLUMN, value = "pref-width")
			public Integer getPrefWidth() {
				return thePrefWidth;
			}

			/** @return The maximum width of this column, in pixels */
			@QonfigAttributeGetter(asType = COLUMN, value = "max-width")
			public Integer getMaxWidth() {
				return theMaxWidth;
			}

			/**
			 * @return The width of this column, in pixels. Overrides {@link #getMinWidth() min}, {@link #getPrefWidth() preferred}, and
			 *         {@link #getMaxWidth() max} widths
			 */
			@QonfigAttributeGetter(asType = COLUMN, value = "width")
			public Integer getWidth() {
				return theWidth;
			}

			@QonfigChildGetter(asType = "rendering", value = "renderer")
			@Override
			public List<QuickWidget.Def<?>> getRenderers() {
				return Collections.unmodifiableList(theRenderers);
			}

			@QonfigChildGetter(asType = COLUMN, value = "edit")
			@Override
			public ColumnEditing.Def getEditing() {
				return theEditing;
			}

			/** @return Transfer sources for column values */
			@QonfigChildGetter(asType = COLUMN, value = "transfer-source")
			public List<QuickTransfer.TransferSource.Def> getTransferSources() {
				return Collections.unmodifiableList(theTransferSources);
			}

			/** @return Transfer accepters for column values */
			@QonfigChildGetter(asType = COLUMN, value = "transfer-accept")
			public List<QuickTransfer.TransferAccept.Def> getTransferAccepters() {
				return Collections.unmodifiableList(theTransferAccepters);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement("styled"));
				theId = getAttributeExpression("id", session);
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

				List<ExpressoQIS> renderers = session.forChildren("renderer");
				if (renderers.isEmpty())
					renderers = session.metadata().get("default-renderer").get();
				syncChildren(QuickWidget.Def.class, theRenderers, renderers);
				theEditing = syncChild(ColumnEditing.Def.class, theEditing, session, "edit");
				elModels.<Interpreted<?, ?, ?>, SettableValue<?>> satisfyElementSingleValueType(theColumnValueVariable, ModelTypes.Value,
					Interpreted::interpretType);
				syncChildren(QuickTransfer.TransferSource.Def.class, theTransferSources, session.forChildren("transfer-source"));
				syncChildren(QuickTransfer.TransferAccept.Def.class, theTransferAccepters, session.forChildren("transfer-accept"));
			}

			@Override
			public ColumnStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new ColumnStyle.Def(parentStyle, this, style);
			}

			@Override
			public abstract <R> Interpreted<R, ?, ? extends SCS> interpret(ExElement.Interpreted<?> parent);
		}

		/**
		 * {@link AbstractSingleColumn} interpretation
		 *
		 * @param <R> The row type of the tabular widget
		 * @param <C> The value type of the table
		 * @param <SCS> The sub-type of {@link AbstractSingleColumn} this definition creates
		 */
		public static abstract class Interpreted<R, C, SCS extends AbstractSingleColumn<R, C>>
		extends QuickStyledElement.Interpreted.Abstract<SCS> implements TableColumnSet.Interpreted<R, SCS> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<?>> theId;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theName;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<C>> theValue;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theHeaderTooltip;
			private final List<QuickWidget.Interpreted<?>> theRenderers;
			private ColumnEditing.Interpreted<R, C> theEditing;
			private final List<QuickTransfer.TransferSource.Interpreted<C, ?>> theTransferSources;
			private final List<QuickTransfer.TransferAccept.Interpreted<C, ?>> theTransferAccepters;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element
			 */
			protected Interpreted(AbstractSingleColumn.Def<? super SCS> definition, ExElement.Interpreted<? extends ValueTyped<R>> parent) {
				super(definition, parent);
				if (!(parent instanceof MultiValueWidget.Interpreted))
					throw new IllegalStateException("The parent of a column must be a multi-value-widget");
				theRenderers = new ArrayList<>();
				theTransferSources = new ArrayList<>();
				theTransferAccepters = new ArrayList<>();
			}

			@Override
			public AbstractSingleColumn.Def<? super SCS> getDefinition() {
				return (AbstractSingleColumn.Def<? super SCS>) super.getDefinition();
			}

			@Override
			public ExElement.Interpreted<? extends MultiValueWidget<R>> getParentElement() {
				return (ExElement.Interpreted<? extends MultiValueWidget<R>>) super.getParentElement();
			}

			/** @return The ID of the column, if specified */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<?>> getId() {
				return theId;
			}

			@Override
			public TypeToken<?> getIdType() {
				return theId == null ? null : theId.getType().getType(0);
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

			@Override
			public List<QuickWidget.Interpreted<?>> getRenderers() {
				return Collections.unmodifiableList(theRenderers);
			}

			/** @return The strategy for editing values in this column */
			@Override
			public ColumnEditing.Interpreted<R, C> getEditing() {
				return theEditing;
			}

			/** @return Transfer sources for column values */
			@Override
			public List<QuickTransfer.TransferSource.Interpreted<C, ?>> getTransferSources() {
				return Collections.unmodifiableList(theTransferSources);
			}

			/** @return Transfer accepters for column values */
			@Override
			public List<QuickTransfer.TransferAccept.Interpreted<C, ?>> getTransferAccepters() {
				return Collections.unmodifiableList(theTransferAccepters);
			}

			@Override
			public TypeToken<R> getValueType() throws ExpressoInterpretationException {
				return ((ValueTyped.Interpreted<R, ?>) getParentElement()).getValueType();
			}

			TypeToken<C> interpretType() throws ExpressoInterpretationException {
				if (theValue == null)
					theValue = interpret(getDefinition().getValue(), ModelTypes.Value.anyAsV());
				return getType();
			}

			/** @return The value type of the column */
			public TypeToken<C> getType() {
				return (TypeToken<C>) theValue.getType().getType(0);
			}

			@Override
			public void updateColumns() throws ExpressoInterpretationException {
				update();
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();
				interpretType(); // Ensure the value is interpreted
				theId = interpret(getDefinition().getId(), ModelTypes.Value.any());
				theName = interpret(getDefinition().getName(), ModelTypes.Value.STRING);
				theHeaderTooltip = interpret(getDefinition().getHeaderTooltip(), ModelTypes.Value.STRING);

				syncChildren(getDefinition().getRenderers(), theRenderers, def -> def.interpret(this), r -> r.updateElement());
				theEditing = syncChild(getDefinition().getEditing(), theEditing,
					def -> (ColumnEditing.Interpreted<R, C>) def.interpret(this), e -> e.update(getType()));
				syncChildren(getDefinition().getTransferSources(), theTransferSources,
					def -> (QuickTransfer.TransferSource.Interpreted<C, ?>) def.interpret(this),
					interp -> interp.updateTransferSource(getType()));
				syncChildren(getDefinition().getTransferAccepters(), theTransferAccepters,
					ta -> (QuickTransfer.TransferAccept.Interpreted<C, ?>) ta.interpret(this), ta -> ta.updateTransferAccepter(getType()));
			}

			@Override
			public void destroy() {
				for (QuickWidget.Interpreted<?> renderer : theRenderers)
					renderer.destroy();
				if (theEditing != null) {
					theEditing.destroy();
					theEditing = null;
				}
				super.destroy();
			}

			@Override
			public abstract SCS create();
		}

		private TypeToken<C> theColumnType;

		/** Model value instantiator for this column's ID */
		protected ModelValueInstantiator<SettableValue<?>> theIdInstantiator;
		/** Model value instantiator for this column's name */
		protected ModelValueInstantiator<SettableValue<String>> theNameInstantiator;
		/** Model value instantiator for this column's value */
		protected ModelValueInstantiator<SettableValue<C>> theValueInstantiator;
		/** Model value instantiator for this column's header tooltip. May be null */
		protected ModelValueInstantiator<SettableValue<String>> theHeaderTooltipInstantiator;

		private Integer theMinWidth;
		private Integer thePrefWidth;
		private Integer theMaxWidth;
		private Integer theWidth;

		private ModelComponentId theColumnValueVariable;
		private List<QuickWidget> theRenderers;
		private ColumnEditing<R, C> theEditing;
		private List<QuickTransfer.TransferSource<C, ?>> theTransferSources;
		private List<QuickTransfer.TransferAccept<C, ?>> theTransferAccepters;

		/** @param id The element ID for the column */
		protected AbstractSingleColumn(Object id) {
			super(id);
			theRenderers = new ArrayList<>();
			theTransferSources = new ArrayList<>();
			theTransferAccepters = new ArrayList<>();
		}

		@Override
		public ValueTyped<R> getParentElement() {
			return (ValueTyped<R>) super.getParentElement();
		}

		/** @return The model ID of the variable by which the active column value will be available to expressions */
		public ModelComponentId getColumnValueVariable() {
			return theColumnValueVariable;
		}

		/** @return The renderer to represent the column value to the user when they are not interacting with it */
		public List<QuickWidget> getRenderers() {
			return Collections.unmodifiableList(theRenderers);
		}

		/** @return The strategy for editing values in this column */
		public ColumnEditing<R, C> getEditing() {
			return theEditing;
		}

		/** @return The type of this column's value */
		public TypeToken<C> getColumnType() {
			return theColumnType;
		}

		/** @return The minimum width for the column, if configured */
		public Integer getMinWidth() {
			return theMinWidth;
		}

		/** @return The preferred width for the column, if configured */
		public Integer getPrefWidth() {
			return thePrefWidth;
		}

		/** @return The maximum width for the column, if configured */
		public Integer getMaxWidth() {
			return theMaxWidth;
		}

		/** @return The constant width for the column, if configured */
		public Integer getWidth() {
			return theWidth;
		}

		/** @return Transfer source configurations for tree nodes by path */
		public List<QuickTransfer.TransferSource<C, ?>> getTransferSources() {
			return Collections.unmodifiableList(theTransferSources);
		}

		/** @return Transfer accepters for column values */
		public List<QuickTransfer.TransferAccept<C, ?>> getTransferAccepters() {
			return Collections.unmodifiableList(theTransferAccepters);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			AbstractSingleColumn.Interpreted<R, C, ?> myInterpreted = (AbstractSingleColumn.Interpreted<R, C, ?>) interpreted;
			theColumnType = myInterpreted.getType();
			theIdInstantiator = myInterpreted.getId() == null ? null : myInterpreted.getId().instantiate();
			theNameInstantiator = myInterpreted.getName().instantiate();
			theColumnValueVariable = myInterpreted.getDefinition().getColumnValueVariable();
			theValueInstantiator = myInterpreted.getValue().instantiate();
			theHeaderTooltipInstantiator = myInterpreted.getHeaderTooltip() == null ? null : myInterpreted.getHeaderTooltip().instantiate();

			theMinWidth = myInterpreted.getDefinition().getMinWidth();
			thePrefWidth = myInterpreted.getDefinition().getPrefWidth();
			theMaxWidth = myInterpreted.getDefinition().getMaxWidth();
			theWidth = myInterpreted.getDefinition().getWidth();

			syncChildren(myInterpreted.getRenderers(), theRenderers, QuickWidget.Interpreted::create, QuickWidget::update);
			theEditing = syncChild(myInterpreted.getEditing(), theEditing, ColumnEditing.Interpreted::create, ColumnEditing::update);
			syncChildren(myInterpreted.getTransferSources(), theTransferSources, ts -> ts.create(), QuickTransfer.TransferSource::update);
			syncChildren(myInterpreted.getTransferAccepters(), theTransferAccepters, ta -> ta.create(),
				QuickTransfer.TransferAccept::update);
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();

			if (theIdInstantiator != null)
				theIdInstantiator.instantiate();
			theNameInstantiator.instantiate();
			theValueInstantiator.instantiate();
			if (theHeaderTooltipInstantiator != null)
				theHeaderTooltipInstantiator.instantiate();
			for (QuickWidget renderer : theRenderers)
				renderer.instantiated();
			if (theEditing != null)
				theEditing.instantiated();
			for (QuickTransfer.TransferSource<C, ?> ts : theTransferSources)
				ts.instantiated();
			for (QuickTransfer.TransferAccept<C, ?> ta : theTransferAccepters)
				ta.instantiated();
			for (QuickTransfer.TransferAccept<C, ?> ta : theTransferAccepters)
				ta.instantiated();
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			myModels = super.doInstantiate(myModels);

			for (QuickWidget renderer : theRenderers)
				renderer.instantiate(myModels);
			if (theEditing != null)
				theEditing.instantiate(myModels);
			for (QuickTransfer.TransferSource<C, ?> ts : theTransferSources)
				ts.instantiate(myModels);
			for (QuickTransfer.TransferAccept<C, ?> ta : theTransferAccepters)
				ta.instantiate(myModels);
			return myModels;
		}

		@Override
		public AbstractSingleColumn<R, C> copy(ExElement parent) {
			AbstractSingleColumn<R, C> copy = (AbstractSingleColumn<R, C>) super.copy(parent);

			copy.theRenderers = new ArrayList<>();
			for (QuickWidget renderer : theRenderers)
				copy.theRenderers.add(renderer.copy(copy));
			if (theEditing != null)
				copy.theEditing = theEditing.copy(copy);

			copy.theTransferSources = new ArrayList<>();
			for (QuickTransfer.TransferSource<C, ?> ts : theTransferSources)
				copy.theTransferSources.add(ts.copy(copy));
			copy.theTransferAccepters = new ArrayList<>();
			for (QuickTransfer.TransferAccept<C, ?> ta : theTransferAccepters)
				copy.theTransferAccepters.add(ta.copy(copy));

			return copy;
		}

		/** Style for a {@link AbstractSingleColumn &lt;column>} */
		public static class ColumnStyle extends QuickInstanceStyle.Abstract {
			/** {@link ColumnStyle} definition */
			public static class Def extends QuickInstanceStyle.Def.Abstract {
				/**
				 * @param parent The parent Quick style to inherit from
				 * @param styledElement The column element to style
				 * @param wrapped The compiled style to wrap
				 */
				public Def(QuickInstanceStyle.Def parent, AbstractSingleColumn.Def<?> styledElement, QuickCompiledStyle wrapped) {
					super(parent, styledElement.getAddOn(QuickStyled.Def.class), wrapped);
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent)
					throws ExpressoInterpretationException {
					return new Interpreted(this, (AbstractSingleColumn.Interpreted<?, ?, ?>) parentEl,
						(QuickInstanceStyle.Interpreted) parent, getWrapped().interpret(parentEl, parent));
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
				public Interpreted(Def compiled, AbstractSingleColumn.Interpreted<?, ?, ?> styledElement,
					QuickInstanceStyle.Interpreted parent, QuickInterpretedStyle wrapped) {
					super(compiled, styledElement.getAddOn(QuickStyled.Interpreted.class), parent, wrapped);
				}

				@Override
				public Def getDefinition() {
					return (Def) super.getDefinition();
				}

				@Override
				public QuickInstanceStyle create(QuickStyled styled) {
					return new ColumnStyle();
				}
			}
		}

		/** A {@link QuickTableColumn} for an {@link AbstractSingleColumn} */
		protected abstract class Column implements QuickTableColumn<R, C> {
			@Override
			public TableColumnSet<R> getColumnSet() {
				return AbstractSingleColumn.this;
			}

			@Override
			public TypeToken<C> getType() {
				return getColumnType();
			}

			@Override
			public Integer getMinWidth() {
				return AbstractSingleColumn.this.getMinWidth();
			}

			@Override
			public Integer getPrefWidth() {
				return AbstractSingleColumn.this.getPrefWidth();
			}

			@Override
			public Integer getMaxWidth() {
				return AbstractSingleColumn.this.getMaxWidth();
			}

			@Override
			public Integer getWidth() {
				return AbstractSingleColumn.this.getWidth();
			}

			@Override
			public void update() {
			}

			@Override
			public ErrorReporting reporting() {
				return AbstractSingleColumn.this.reporting();
			}

			@Override
			public String toString() {
				return AbstractSingleColumn.this.toString();
			}
		}
	}

	/**
	 * The &lt;column> element, a single table column
	 *
	 * @param <R> The row type of the tabular widget
	 * @param <C> The value type of the table
	 */
	public class SingleColumnSet<R, C> extends AbstractSingleColumn<R, C> {
		/** {@link SingleColumnSet} definition */
		public static class Def extends AbstractSingleColumn.Def<SingleColumnSet<?, ?>> {
			/**
			 * @param parent The parent of the column
			 * @param type The Qonfig type of the element
			 */
			public Def(ExElement.Def<? extends ValueTyped<?>> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public <R> Interpreted<R, ?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, (ExElement.Interpreted<? extends ValueTyped<R>>) parent);
			}
		}

		/**
		 * {@link SingleColumnSet} interpretation
		 *
		 * @param <R> The row type of the tabular widget
		 * @param <C> The value type of the table
		 */
		public static class Interpreted<R, C> extends AbstractSingleColumn.Interpreted<R, C, SingleColumnSet<R, C>> {
			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element
			 */
			protected Interpreted(Def definition, ExElement.Interpreted<? extends ValueTyped<R>> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public SingleColumnSet<R, C> create() {
				return new SingleColumnSet<>(getIdentity());
			}
		}

		private ObservableCollection<SingleColumn> theColumn;

		private SettableValue<?> theId;
		private SettableValue<SettableValue<String>> theName;
		private SettableValue<C> theValue;
		private SettableValue<SettableValue<String>> theHeaderTooltip;

		/** @param id The element ID for the column */
		protected SingleColumnSet(Object id) {
			super(id);
			theName = SettableValue.<SettableValue<String>> build().build();
			theHeaderTooltip = SettableValue.<SettableValue<String>> build().build();
			theColumn = ObservableCollection.of(new SingleColumn());
		}

		@Override
		public ValueTyped<R> getParentElement() {
			return super.getParentElement();
		}

		/** @return The ID of this column, if specified */
		public SettableValue<?> getId() {
			return theId;
		}

		/** @return The name of the column--the text for the column's header */
		public SettableValue<String> getName() {
			return SettableValue.flatten(theName);
		}

		@Override
		public ObservableCollection<? extends QuickTableColumn<R, ?>> getColumns() {
			return theColumn;
		}

		/** @return The current value of this column */
		public SettableValue<C> getValue() {
			return theValue;
		}

		/** @return The tooltip for this column's header */
		public SettableValue<String> getHeaderTooltip() {
			return SettableValue.flatten(theHeaderTooltip);
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			myModels = super.doInstantiate(myModels);
			theId = theIdInstantiator == null ? null : theIdInstantiator.get(myModels);
			theValue = theValueInstantiator.get(myModels);
			ExFlexibleElementModelAddOn.satisfyElementValue(getColumnValueVariable(), myModels, getValue(),
				ExFlexibleElementModelAddOn.ActionIfSatisfied.Ignore);
			theName.set(theNameInstantiator.get(myModels), null);
			theHeaderTooltip.set(theHeaderTooltipInstantiator == null ? null : theHeaderTooltipInstantiator.get(myModels), null);
			return myModels;
		}

		@Override
		public SingleColumnSet<R, C> copy(ExElement parent) {
			SingleColumnSet<R, C> copy = (SingleColumnSet<R, C>) super.copy(parent);

			copy.theName = SettableValue.<SettableValue<String>> build().build();
			copy.theHeaderTooltip = SettableValue.<SettableValue<String>> build().build();

			copy.theColumn = ObservableCollection.of(copy.new SingleColumn());

			return copy;
		}

		/** The {@link QuickTableColumn column} of a {@link SingleColumnSet} */
		public class SingleColumn extends AbstractSingleColumn<R, C>.Column {
			@Override
			public SettableValue<?> getId() {
				return SingleColumnSet.this.getId();
			}

			@Override
			public SettableValue<String> getName() {
				return SingleColumnSet.this.getName();
			}

			@Override
			public SettableValue<C> getValue() {
				return theValue;
			}

			@Override
			public SettableValue<String> getHeaderTooltip() {
				return SingleColumnSet.this.getHeaderTooltip();
			}

			@Override
			public List<QuickWidget> getRenderers() {
				return SingleColumnSet.this.getRenderers();
			}

			@Override
			public ColumnEditing<R, C> getEditing() {
				return SingleColumnSet.this.getEditing();
			}

			@Override
			public List<TransferSource<C, ?>> getTransferSources() {
				return SingleColumnSet.this.getTransferSources();
			}

			@Override
			public List<TransferAccept<C, ?>> getTransferAccepters() {
				return SingleColumnSet.this.getTransferAccepters();
			}
		}
	}

	/**
	 * The &lt;variable-columns> element, with the ability to create a variable number of table columns based on values in a collection
	 *
	 * @param <R> The type of rows in the table
	 * @param <E> The type of values in the collection
	 * @param <C> The type of the column values
	 */
	public class VariableColumns<R, E, C> extends AbstractSingleColumn<R, C> {
		/** The XML name of this element */
		public static final String VARIABLE_COLUMNS = "variable-columns";

		/** {@link VariableColumns} definition */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = VARIABLE_COLUMNS,
			interpretation = Interpreted.class,
			instance = VariableColumns.class)
		public static class Def extends AbstractSingleColumn.Def<VariableColumns<?, ?, ?>> {
			private ModelComponentId theColumnElementVariable;
			private CompiledExpression theValues;

			/**
			 * @param parent The parent of the column
			 * @param qonfigType The Qonfig type of the element
			 */
			public Def(ExElement.Def<? extends ValueTyped<?>> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			/** @return The expression for the collection, for each element of which a column will be added to the table */
			@QonfigAttributeGetter("for-each")
			public CompiledExpression getValues() {
				return theValues;
			}

			/**
			 * @return The ID of the variable for the name by which to refer to the active element in the {@link #getValues() values}
			 *         collection
			 */
			@QonfigAttributeGetter("column-element-as")
			public ModelComponentId getColumnElementVariable() {
				return theColumnElementVariable;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				theValues = getAttributeExpression("for-each", session);
				String columnValueName = session.getAttributeText("column-element-as");
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theColumnElementVariable = elModels.getElementValueModelId(columnValueName);
				elModels.<Interpreted<?, ?, ?>, SettableValue<?>> satisfyElementSingleValueType(theColumnElementVariable, ModelTypes.Value,
					Interpreted::getColumnElementType);
			}

			@Override
			public <R> Interpreted<R, ?, ?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, (ExElement.Interpreted<? extends ValueTyped<R>>) parent);
			}
		}

		/**
		 * {@link VariableColumns} interpretation
		 *
		 * @param <R> The type of rows in the table
		 * @param <E> The type of values in the collection
		 * @param <C> The type of the column values
		 */
		public static class Interpreted<R, E, C> extends AbstractSingleColumn.Interpreted<R, C, VariableColumns<R, E, C>> {
			private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<E>> theValues;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element
			 */
			protected Interpreted(Def definition, ExElement.Interpreted<? extends ValueTyped<R>> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			TypeToken<E> getColumnElementType() {
				return (TypeToken<E>) theValues.getType().getType(0);
			}

			/** @return The expression for the collection, for each element of which a column will be added to the table */
			public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<E>> getValues() {
				return theValues;
			}

			@Override
			public void updateColumns() throws ExpressoInterpretationException {
				update();
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				theValues = interpret(getDefinition().getValues(), ModelTypes.Collection.anyAs());
				super.doUpdate();
			}

			@Override
			public VariableColumns<R, E, C> create() {
				return new VariableColumns<>(getIdentity());
			}
		}

		private ModelValueInstantiator<ObservableCollection<E>> theValuesInstantiator;

		private ModelComponentId theColumnElementVariable;

		private SettableValue<ObservableCollection<E>> theValuesHolder;
		private SettableValue<ObservableCollection<VariableColumn>> theColumns;

		VariableColumns(Object id) {
			super(id);
			theValuesHolder = SettableValue.create();
			theColumns = SettableValue.create();
		}

		/** @return The ID of the variable for the name by which to refer to the active element in the values collection */
		public ModelComponentId getColumnElementVariable() {
			return theColumnElementVariable;
		}

		@Override
		public ObservableCollection<? extends QuickTableColumn<R, ?>> getColumns() {
			return ObservableCollection.flattenValue(theColumns);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);

			Interpreted<R, E, C> myInterpreted = (Interpreted<R, E, C>) interpreted;

			theValuesInstantiator = myInterpreted.getValues().instantiate();
			theColumnElementVariable = myInterpreted.getDefinition().getColumnElementVariable();
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();

			theValuesInstantiator.instantiate();
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			myModels = super.doInstantiate(myModels);

			theValuesHolder.set(theValuesInstantiator.get(myModels), null);

			ObservableCollection<E> values = ObservableCollection.flattenValue(theValuesHolder);
			ObservableCollection<VariableColumn> columns = ObservableCollection.<VariableColumn> build()//
				.withThreadConstraint(values.getThreadConstraint())//
				.build();
			ThreadConstraint threading = values.getThreadConstraint();
			ModelSetInstance fModels = myModels;
			CollectionSubscription valueSub = values.subscribe(evt -> {
				try (Transaction t = columns.lock(true, evt)) {
					switch (evt.getType()) {
					case add:
						SimpleObservable<Void> elementModelUntil = SimpleObservable.build()//
						.withThreadConstraint(threading)//
						.build();
						SettableValue.Builder<E> elementValueBuilder = SettableValue.<E> build()//
							.withValue(evt.getNewValue());
						if (threading != ThreadConstraint.ANY)
							elementValueBuilder.withThreadConstraint(threading);
						SettableValue<E> elementValue = elementValueBuilder.build();
						ModelSetInstance elementModelCopy;
						try {
							elementModelCopy = QuickCoreInterpretation.copyModels(fModels, theColumnElementVariable, elementModelUntil)
								.build();
							ExFlexibleElementModelAddOn.satisfyElementValue(theColumnElementVariable, elementModelCopy, elementValue);
							columns.add(evt.getIndex(),
								new VariableColumn(values, evt.getElementId(), elementValue, elementModelCopy, elementModelUntil));
						} catch (ModelInstantiationException e) {
							reporting().error("Could not add column for " + evt.getElementId() + ": " + evt.getNewValue(), e);
							elementModelUntil.onNext(null);
							// Put in a placeholder for bookkeeping and filter it out later
							columns.add(evt.getIndex(), null);
						}
						break;
					case remove:
						VariableColumn column = columns.remove(evt.getIndex());
						if (column != null)
							column.destroy();
						break;
					case set:
						column = columns.get(evt.getIndex());
						if (column != null)
							column.update(evt.getNewValue(), evt);
						break;
					}
				}
			}, true);
			Observable.or(myModels.getUntil(), onDestroy()).take(1).act(__ -> {
				valueSub.unsubscribe(true);
				threading.invoke(() -> {
					for (VariableColumn column : columns) {
						if (column != null)
							column.destroy();
					}
				});
			});
			theColumns.set(columns, null);
			return myModels;
		}

		@Override
		public VariableColumns<R, E, C> copy(ExElement parent) {
			VariableColumns<R, E, C> copy = (VariableColumns<R, E, C>) super.copy(parent);

			copy.theValuesHolder = SettableValue.create();
			copy.theColumns = SettableValue.create();

			return copy;
		}

		class VariableColumn extends AbstractSingleColumn<R, C>.Column {
			private final SettableValue<E> theColumnElement;
			private final SettableValue<C> theColumnValue;
			private final SettableValue<String> theName;
			private final SettableValue<String> theHeaderTooltip;
			private final List<QuickWidget> theRenderers;
			private final ColumnEditing<R, C> theEditing;
			private final List<QuickTransfer.TransferSource<C, ?>> theTransferSources;
			private final List<QuickTransfer.TransferAccept<C, ?>> theTransferAccepters;
			private final SimpleObservable<Void> theUntil;
			private boolean theCallbackLock;

			VariableColumn(ObservableCollection<E> columnValues, ElementId element, SettableValue<E> elementValue, ModelSetInstance models,
				SimpleObservable<Void> elementUntil) throws ModelInstantiationException {
				theColumnElement = elementValue;
				theUntil = elementUntil;
				theName = theNameInstantiator.get(models);
				theColumnValue = theValueInstantiator.get(models);
				theRenderers = new ArrayList<>();
				ExFlexibleElementModelAddOn.satisfyElementValue(getColumnValueVariable(), models, theColumnValue,
					ExFlexibleElementModelAddOn.ActionIfSatisfied.Replace);
				theHeaderTooltip = theHeaderTooltipInstantiator == null ? SettableValue.of(null, "Constant")
					: theHeaderTooltipInstantiator.get(models);
				for (QuickWidget renderer : VariableColumns.this.getRenderers()) {
					renderer = renderer.copy(VariableColumns.this);
					renderer.instantiate(models);
					theRenderers.add(renderer);
				}
				if (VariableColumns.this.getEditing() != null) {
					theEditing = VariableColumns.this.getEditing().copy(VariableColumns.this);
					theEditing.instantiate(models);
				} else
					theEditing = null;
				theTransferSources = new ArrayList<>(VariableColumns.this.getTransferSources().size());
				for (QuickTransfer.TransferSource<C, ?> ts : VariableColumns.this.getTransferSources()) {
					ts = ts.copy(VariableColumns.this);
					ts.instantiate(models);
					theTransferSources.add(ts);
				}
				theTransferAccepters = new ArrayList<>(VariableColumns.this.getTransferAccepters().size());
				for (QuickTransfer.TransferAccept<C, ?> ta : VariableColumns.this.getTransferAccepters()) {
					ta = ta.copy(VariableColumns.this);
					ta.instantiate(models);
					theTransferAccepters.add(ta);
				}

				theColumnElement.noInitChanges().act(evt -> {
					if (theCallbackLock)
						return;
					theCallbackLock = true;
					try (Transaction t = columnValues.lock(true, evt)) {
						columnValues.mutableElement(element).set(evt.getNewValue());
					} finally {
						theCallbackLock = false;
					}
				});
			}

			void update(E newValue, Object cause) {
				if (theCallbackLock)
					return;
				theCallbackLock = true;
				try {
					theColumnElement.set(newValue, cause);
				} finally {
					theCallbackLock = false;
				}
			}

			void destroy() {
				theUntil.onNext(null);
			}

			@Override
			public SettableValue<E> getId() {
				return theColumnElement;
			}

			@Override
			public SettableValue<String> getName() {
				return theName;
			}

			@Override
			public SettableValue<C> getValue() {
				return theColumnValue;
			}

			@Override
			public SettableValue<String> getHeaderTooltip() {
				return theHeaderTooltip;
			}

			@Override
			public List<QuickWidget> getRenderers() {
				return Collections.unmodifiableList(theRenderers);
			}

			@Override
			public ColumnEditing<R, C> getEditing() {
				return theEditing;
			}

			@Override
			public List<TransferSource<C, ?>> getTransferSources() {
				return Collections.unmodifiableList(theTransferSources);
			}

			@Override
			public List<TransferAccept<C, ?>> getTransferAccepters() {
				return Collections.unmodifiableList(theTransferAccepters);
			}

			@Override
			public String toString() {
				return super.toString() + " for " + theColumnElement.get();
			}
		}
	}
}
