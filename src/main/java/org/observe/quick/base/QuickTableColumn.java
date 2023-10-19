package org.observe.quick.base;

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
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
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
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface QuickTableColumn<R, C> {
	public interface TableColumnSet<R> extends ValueTyped<R> {
		public interface Def<CC extends TableColumnSet<?>> extends ValueTyped.Def<CC> {
			QuickWidget.Def<?> getRenderer();

			ColumnEditing.Def getEditing();

			<R> Interpreted<R, ? extends CC> interpret(ExElement.Interpreted<?> parent);
		}

		public interface Interpreted<R, CC extends TableColumnSet<R>> extends ValueTyped.Interpreted<R, CC> {
			@Override
			Def<? super CC> getDefinition();

			@Override
			ValueTyped.Interpreted<R, ?> getParentElement();

			QuickWidget.Interpreted<?> getRenderer();

			ColumnEditing.Interpreted<R, ?> getEditing();

			void updateColumns(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

			CC create();
		}

		@Override
		ValueTyped<R> getParentElement();

		TypeToken<R> getRowType();

		ObservableCollection<? extends QuickTableColumn<R, ?>> getColumns();

		@Override
		TableColumnSet<R> copy(ExElement parent);
	}

	SettableValue<String> getName();

	QuickTableColumn.TableColumnSet<R> getColumnSet();

	TypeToken<C> getType();

	SettableValue<C> getValue();

	SettableValue<String> getHeaderTooltip();

	Integer getMinWidth();

	Integer getPrefWidth();

	Integer getMaxWidth();

	Integer getWidth();

	QuickWidget getRenderer();

	ColumnEditing<R, C> getEditing();

	void update();

	public interface ColumnEditContext<R, C> extends TabularWidget.TabularContext<R> {
		SettableValue<C> getEditColumnValue();

		public class Default<R, C> extends TabularWidget.TabularContext.Default<R> implements ColumnEditContext<R, C> {
			private final SettableValue<C> theEditColumnValue;

			public Default(SettableValue<R> renderValue, SettableValue<Boolean> selected, SettableValue<Integer> rowIndex,
				SettableValue<Integer> columnIndex, SettableValue<C> editColumnValue) {
				super(renderValue, selected, rowIndex, columnIndex);
				theEditColumnValue = editColumnValue;
			}

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

	public class ColumnEditing<R, C> extends ExElement.Abstract implements QuickValueWidget.WidgetValueSupplier<C> {
		public static final String COLUMN_EDITING = "column-edit";

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

			public Def(TableColumnSet.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public TableColumnSet.Def<?> getParentElement() {
				return (TableColumnSet.Def<?>) super.getParentElement();
			}

			@QonfigAttributeGetter("type")
			public ColumnEditType.Def<?> getType() {
				return getAddOn(ColumnEditType.Def.class);
			}

			@QonfigChildGetter("editor")
			public QuickWidget.Def<?> getEditor() {
				return theEditor;
			}

			@QonfigAttributeGetter("column-edit-value-name")
			public ModelComponentId getColumnEditValueVariable() {
				return theColumnEditValueVariable;
			}

			@QonfigAttributeGetter("editable-if")
			public CompiledExpression isEditable() {
				return isEditable;
			}

			@QonfigAttributeGetter("accept")
			public CompiledExpression isAcceptable() {
				return isAcceptable;
			}

			@QonfigAttributeGetter("clicks")
			public Integer getClicks() {
				return theClicks;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theEditor = ExElement.useOrReplace(QuickWidget.Def.class, theEditor, session, "editor");
				String columnEditValueName = session.getAttributeText("column-edit-value-name");
				isEditable = getAttributeExpression("editable-if", session);
				isAcceptable = getAttributeExpression("accept", session);
				theClicks = session.getAttribute("clicks", Integer.class);
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theColumnEditValueVariable = elModels.getElementValueModelId(columnEditValueName);
				elModels.satisfyElementValueType(theColumnEditValueVariable, ModelTypes.Value,
					(interp, env) -> ModelTypes.Value.forType(((Interpreted<?, ?>) interp).getColumnType()));
			}

			public Interpreted<?, ?> interpret(TableColumnSet.Interpreted<?, ?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<R, C> extends ExElement.Interpreted.Abstract<ColumnEditing<R, C>>
		implements QuickValueWidget.WidgetValueSupplier.Interpreted<C, ColumnEditing<R, C>> {
			private TypeToken<C> theColumnType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isEditable;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isAcceptable;
			private QuickWidget.Interpreted<?> theEditor;

			public Interpreted(Def definition, TableColumnSet.Interpreted<R, ?> parent) {
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

			public ColumnEditType.Interpreted<R, C, ?> getType() {
				return getAddOn(ColumnEditType.Interpreted.class);
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isEditable() {
				return isEditable;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isAcceptable() {
				return isAcceptable;
			}

			public QuickWidget.Interpreted<?> getEditor() {
				return theEditor;
			}

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

			public Interpreted<R, C> update(TypeToken<C> columnType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				theColumnType = columnType;

				super.update(env);
				isEditable = ExpressoTransformations.parseFilter(getDefinition().isEditable(), env, true);
				return this;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				super.doUpdate(expressoEnv);

				isAcceptable = ExpressoTransformations.parseFilter(getDefinition().isAcceptable(), expressoEnv, true);
				if (getDefinition().getEditor() == null)
					theEditor = null;
				else if (theEditor == null || !theEditor.getDefinition().equals(getDefinition().getEditor()))
					theEditor = getDefinition().getEditor().interpret(this);
				if (theEditor != null)
					theEditor.updateElement(expressoEnv);
			}

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

		public ColumnEditing(Object id) {
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

		public ColumnEditType<R, C> getType() {
			return getAddOn(ColumnEditType.class);
		}

		public ModelComponentId getColumnEditValueName() {
			return theColumnEditValueVariable;
		}

		public QuickWidget getEditor() {
			return theEditor;
		}

		public SettableValue<String> isEditable() {
			return SettableValue.flatten(isEditable);
		}

		public SettableValue<String> isAcceptable() {
			return SettableValue.flatten(isAcceptable);
		}

		public SettableValue<C> getFilteredColumnEditValue() {
			return theFilteredColumnEditValue;
		}

		public Integer getClicks() {
			return theClicks;
		}

		public void setEditorContext(ColumnEditContext<R, C> ctx) {
			theEditRowValue.set(ctx.getActiveValue(), null);
			theEditColumnValue.set(ctx.getEditColumnValue(), null);
			isSelected.set(ctx.isSelected(), null);
			theRowIndex.set(ctx.getRowIndex(), null);
			theColumnIndex.set(ctx.getColumnIndex(), null);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
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
					.disableWith(SettableValue.flatten(isEditable))//
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
		public void instantiated() {
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

			ExFlexibleElementModelAddOn.satisfyElementValue(theColumnEditValueVariable, myModels,
				SettableValue.flatten(theEditColumnValue));
			isEditable.set(theEditableInstantiator == null ? null : theEditableInstantiator.get(myModels), null);

			ExElement owner = getParentElement().getParentElement();
			ModelSetInstance editorModels = myModels.copy()//
				.withAll(myModels.getInherited(owner.getModels().getIdentity()).copy(myModels.getUntil()).build())//
				.build();
			ColumnEditType<R, C> editing = getAddOn(ColumnEditType.class);
			if (editing != null && owner instanceof MultiValueRenderable) {
				// Not enough to copy the editor models, because I also need to replace values table models
				MultiValueRenderable<R> mvr = (MultiValueRenderable<R>) owner;
				if (mvr.getActiveValueVariable() != null)
					ExFlexibleElementModelAddOn.satisfyElementValue(mvr.getActiveValueVariable(), editorModels,
						SettableValue.flatten(theEditRowValue), ExFlexibleElementModelAddOn.ActionIfSatisfied.Replace);
				if (mvr.getSelectedVariable() != null)
					ExFlexibleElementModelAddOn.satisfyElementValue(mvr.getSelectedVariable(), editorModels,
						SettableValue.flatten(isSelected), ExFlexibleElementModelAddOn.ActionIfSatisfied.Replace);
				if (owner instanceof TabularWidget) {
					TabularWidget<R> table = (TabularWidget<R>) owner;
					if (table.getRowIndexVariable() != null)
						ExFlexibleElementModelAddOn.satisfyElementValue(table.getRowIndexVariable(), editorModels,
							SettableValue.flatten(theRowIndex), ExFlexibleElementModelAddOn.ActionIfSatisfied.Replace);
					if (table.getColumnIndexVariable() != null)
						ExFlexibleElementModelAddOn.satisfyElementValue(table.getColumnIndexVariable(), editorModels,
							SettableValue.flatten(theColumnIndex), ExFlexibleElementModelAddOn.ActionIfSatisfied.Replace);
				}
				if (theEditor != null)
					theEditor.instantiate(editorModels);
				editing.instantiateEditor(editorModels);
			}
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
				.disableWith(SettableValue.flatten(isEditable))//
				.filterAccept(v -> {
					SettableValue<String> accept = isAcceptable.get();
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

			return copy;
		}
	}

	public abstract class ColumnEditType<R, C> extends ExAddOn.Abstract<ColumnEditing<R, C>> {
		public static abstract class Def<CET extends ColumnEditType<?, ?>> extends ExAddOn.Def.Abstract<ColumnEditing<?, ?>, CET> {
			protected Def(QonfigAddOn type, ColumnEditing.Def element) {
				super(type, element);
			}

			@Override
			public ColumnEditing.Def getElement() {
				return (ColumnEditing.Def) super.getElement();
			}

			@Override
			public abstract Interpreted<?, ?, ? extends CET> interpret(ExElement.Interpreted<? extends ColumnEditing<?, ?>> element);
		}

		public static abstract class Interpreted<R, C, CET extends ColumnEditType<R, C>>
		extends ExAddOn.Interpreted.Abstract<ColumnEditing<R, C>, CET> {
			protected Interpreted(Def<? super CET> definition, ColumnEditing.Interpreted<R, C> element) {
				super(definition, element);
			}

			@Override
			public Def<? super CET> getDefinition() {
				return (Def<? super CET>) super.getDefinition();
			}

			@Override
			public ColumnEditing.Interpreted<R, C> getElement() {
				return (ColumnEditing.Interpreted<R, C>) super.getElement();
			}
		}

		@Override
		public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
			// Do nothing. We need to use the editor models, which are passed intentionally from the editing via the method below
		}

		public abstract void instantiateEditor(ModelSetInstance editorModels) throws ModelInstantiationException;

		protected ColumnEditType(ColumnEditing<R, C> element) {
			super(element);
		}

		public static class RowModifyEditType<R, C> extends ColumnEditType<R, C> {
			public static final String MODIFY = "modify-row-value";

			@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
				qonfigType = MODIFY,
				interpretation = Interpreted.class,
				instance = RowModifyEditType.class)
			public static class Def extends ColumnEditType.Def<RowModifyEditType<?, ?>> {
				private CompiledExpression theCommit;
				private boolean isRowUpdate;

				public Def(QonfigAddOn type, QuickTableColumn.ColumnEditing.Def element) {
					super(type, element);
				}

				@QonfigAttributeGetter("commit")
				public CompiledExpression getCommit() {
					return theCommit;
				}

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
				public Interpreted<?, ?> interpret(ExElement.Interpreted<? extends ColumnEditing<?, ?>> element) {
					return new Interpreted<>(this, (ColumnEditing.Interpreted<?, ?>) element);
				}
			}

			public static class Interpreted<R, C> extends ColumnEditType.Interpreted<R, C, RowModifyEditType<R, C>> {
				private InterpretedValueSynth<ObservableAction, ObservableAction> theCommit;

				public Interpreted(Def definition, ColumnEditing.Interpreted<R, C> element) {
					super(definition, element);
				}

				@Override
				public Def getDefinition() {
					return (Def) super.getDefinition();
				}

				public InterpretedValueSynth<ObservableAction, ObservableAction> getCommit() {
					return theCommit;
				}

				@Override
				public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
					super.update(env);
					theCommit = getDefinition().getCommit() == null ? null
						: getDefinition().getCommit().interpret(ModelTypes.Action.instance(), env);
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

			public RowModifyEditType(ColumnEditing<R, C> element) {
				super(element);
				theCommit = SettableValue.build(TypeTokens.get().of(ObservableAction.class)).build();
			}

			public boolean isRowUpdate() {
				return isRowUpdate;
			}

			public ObservableAction getCommit() {
				return ObservableAction.flatten(theCommit);
			}

			@Override
			public Class<Interpreted<R, C>> getInterpretationType() {
				return (Class<Interpreted<R, C>>) (Class<?>) Interpreted.class;
			}

			@Override
			public void update(ExAddOn.Interpreted<?, ?> interpreted) {
				super.update(interpreted);
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

		public static class RowReplaceEditType<R, C> extends ColumnEditType<R, C> {
			public static final String REPLACE = "replace-row-value";

			@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
				qonfigType = REPLACE,
				interpretation = Interpreted.class,
				instance = RowModifyEditType.class)
			public static class Def extends ColumnEditType.Def<RowReplaceEditType<?, ?>> {
				private CompiledExpression theReplacement;

				public Def(QonfigAddOn type, ColumnEditing.Def element) {
					super(type, element);
				}

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
				public Interpreted<?, ?> interpret(ExElement.Interpreted<? extends ColumnEditing<?, ?>> element) {
					return new Interpreted<>(this, (ColumnEditing.Interpreted<?, ?>) element);
				}
			}

			public static class Interpreted<R, C> extends ColumnEditType.Interpreted<R, C, RowReplaceEditType<R, C>> {
				private InterpretedValueSynth<SettableValue<?>, SettableValue<R>> theReplacement;

				public Interpreted(Def definition, ColumnEditing.Interpreted<R, C> element) {
					super(definition, element);
				}

				@Override
				public Def getDefinition() {
					return (Def) super.getDefinition();
				}

				public InterpretedValueSynth<SettableValue<?>, SettableValue<R>> getReplacement() {
					return theReplacement;
				}

				@Override
				public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
					super.update(env);
					theReplacement = getDefinition().getReplacement() == null ? null : getDefinition().getReplacement()
						.interpret(ModelTypes.Value.forType(getElement().getParentElement().getValueType()), env);
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

			public RowReplaceEditType(ColumnEditing<R, C> element) {
				super(element);
				theReplacement = SettableValue.build(
					TypeTokens.get().keyFor(SettableValue.class).<SettableValue<R>> parameterized(element.getParentElement().getRowType()))
					.build();
			}

			@Override
			public Class<Interpreted<R, C>> getInterpretationType() {
				return (Class<Interpreted<R, C>>) (Class<?>) Interpreted.class;
			}

			public SettableValue<R> getReplacement() {
				return SettableValue.flatten(theReplacement);
			}

			@Override
			public void update(ExAddOn.Interpreted<?, ?> interpreted) {
				super.update(interpreted);
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

	public class SingleColumnSet<R, C> extends QuickStyledElement.Abstract implements TableColumnSet<R> {
		public static final String COLUMN = "column";

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

			public Def(ValueTyped.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public ValueTyped.Def<?> getParentElement() {
				return (ValueTyped.Def<?>) super.getParentElement();
			}

			@QonfigAttributeGetter(asType = "column", value = "name")
			public CompiledExpression getName() {
				return theName;
			}

			@QonfigAttributeGetter(asType = "column", value = "column-value-name")
			public ModelComponentId getColumnValueVariable() {
				return theColumnValueVariable;
			}

			@QonfigAttributeGetter(asType = "column", value = "value")
			public CompiledExpression getValue() {
				return theValue;
			}

			@QonfigAttributeGetter(asType = "column", value = "header-tooltip")
			public CompiledExpression getHeaderTooltip() {
				return theHeaderTooltip;
			}

			@QonfigAttributeGetter(asType = "column", value = "min-width")
			public Integer getMinWidth() {
				return theMinWidth;
			}

			@QonfigAttributeGetter(asType = "column", value = "pref-width")
			public Integer getPrefWidth() {
				return thePrefWidth;
			}

			@QonfigAttributeGetter(asType = "column", value = "max-width")
			public Integer getMaxWidth() {
				return theMaxWidth;
			}

			@QonfigAttributeGetter(asType = "column", value = "width")
			public Integer getWidth() {
				return theWidth;
			}

			@QonfigChildGetter(asType = "rendering", value = "renderer")
			@Override
			public QuickWidget.Def<?> getRenderer() {
				return theRenderer;
			}

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
					renderer = session.forMetadata("default-renderer").peekFirst();
				theRenderer = ExElement.useOrReplace(QuickWidget.Def.class, theRenderer, renderer, null);
				theEditing = ExElement.useOrReplace(ColumnEditing.Def.class, theEditing, session, "edit");
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

		public static class Interpreted<R, C> extends QuickStyledElement.Interpreted.Abstract<SingleColumnSet<R, C>>
		implements TableColumnSet.Interpreted<R, SingleColumnSet<R, C>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theName;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<C>> theValue;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theHeaderTooltip;
			private QuickWidget.Interpreted<?> theRenderer;
			private ColumnEditing.Interpreted<R, C> theEditing;

			public Interpreted(SingleColumnSet.Def definition, ValueTyped.Interpreted<R, ?> parent) {
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

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getName() {
				return theName;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<C>> getValue() {
				return theValue;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getHeaderTooltip() {
				return theHeaderTooltip;
			}

			@Override
			public QuickWidget.Interpreted<?> getRenderer() {
				return theRenderer;
			}

			@Override
			public ColumnEditing.Interpreted<R, C> getEditing() {
				return theEditing;
			}

			@Override
			public TypeToken<R> getValueType() throws ExpressoInterpretationException {
				return ((ValueTyped.Interpreted<R, ?>) getParentElement()).getValueType();
			}

			public TypeToken<C> getType() {
				return (TypeToken<C>) theValue.getType().getType(0);
			}

			@Override
			public void updateColumns(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				theValue = getDefinition().getValue().interpret(ModelTypes.Value.<C> anyAsV(), env);
				super.doUpdate(env);
				theName = getDefinition().getName().interpret(ModelTypes.Value.STRING, getExpressoEnv());
				theHeaderTooltip = getDefinition().getHeaderTooltip() == null ? null
					: getDefinition().getHeaderTooltip().interpret(ModelTypes.Value.STRING, getExpressoEnv());

				if (getDefinition().getRenderer() == null) {
					if (theRenderer != null)
						theRenderer.destroy();
					theRenderer = null;
				} else if (theRenderer == null || theRenderer.getIdentity() != getDefinition().getRenderer().getIdentity()) {
					if (theRenderer != null)
						theRenderer.destroy();
					theRenderer = getDefinition().getRenderer().interpret(this);
				}
				if (theRenderer != null)
					theRenderer.updateElement(env);

				if (getDefinition().getEditing() == null) {
					if (theEditing != null)
						theEditing.destroy();
					theEditing = null;
				} else if (theEditing == null || theEditing.getIdentity() != getDefinition().getEditing().getIdentity()) {
					if (theEditing != null)
						theEditing.destroy();
					theEditing = (ColumnEditing.Interpreted<R, C>) getDefinition().getEditing().interpret(this);
				}
				if (theEditing != null)
					theEditing.update(getType(), getExpressoEnv());
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

		public SingleColumnSet(Object id) {
			super(id);
			theName = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
				.build();
			theHeaderTooltip = SettableValue.build(theName.getType()).build();
		}

		@Override
		public ValueTyped<R> getParentElement() {
			return (ValueTyped<R>) super.getParentElement();
		}

		public SettableValue<String> getName() {
			return SettableValue.flatten(theName);
		}

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

		public SettableValue<C> getValue() {
			return SettableValue.flatten(theValue);
		}

		public SettableValue<String> getHeaderTooltip() {
			return SettableValue.flatten(theHeaderTooltip);
		}

		public QuickWidget getRenderer() {
			return theRenderer;
		}

		public ColumnEditing<R, C> getEditing() {
			return theEditing;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
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
		public void instantiated() {
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

			if (theRenderer != null)
				theRenderer.instantiate(myModels);
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

		public static class ColumnStyle extends QuickStyledElement.QuickInstanceStyle.Abstract {
			public static class Def extends QuickInstanceStyle.Def.Abstract {
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

			public static class Interpreted extends QuickInstanceStyle.Interpreted.Abstract {
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
