package org.observe.quick.base;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
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
	public interface TableColumnSet<R> extends RowTyped<R> {
		public interface Def<CC extends TableColumnSet<?>> extends RowTyped.Def<CC> {
			QuickWidget.Def<?> getRenderer();

			ColumnEditing.Def getEditing();

			<R> Interpreted<R, ? extends CC> interpret(ExElement.Interpreted<?> parent);
		}

		public interface Interpreted<R, CC extends TableColumnSet<R>> extends RowTyped.Interpreted<R, CC> {
			@Override
			Def<? super CC> getDefinition();

			@Override
			TabularWidget.Interpreted<R, ?> getParentElement();

			QuickWidget.Interpreted<?> getRenderer();

			ColumnEditing.Interpreted<R, ?> getEditing();

			void updateColumns(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

			CC create(ExElement parent);
		}

		@Override
		TabularWidget<R> getParentElement();

		TypeToken<R> getRowType();

		ObservableCollection<? extends QuickTableColumn<R, ?>> getColumns();
	}

	SettableValue<String> getName();

	QuickTableColumn.TableColumnSet<R> getColumnSet();

	TypeToken<C> getType();

	SettableValue<C> getValue();

	SettableValue<String> getHeaderTooltip();

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
		private static final SingleTypeTraceability<ColumnEditing<?, ?>, Interpreted<?, ?>, Def> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, COLUMN_EDITING, Def.class,
				Interpreted.class, ColumnEditing.class);

		public static class Def extends ExElement.Def.Abstract<ColumnEditing<?, ?>>
		implements QuickValueWidget.WidgetValueSupplier.Def<ColumnEditing<?, ?>> {
			private QuickWidget.Def<?> theEditor;
			private String theColumnEditValueName;
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
			public String getColumnEditValueName() {
				return theColumnEditValueName;
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
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session);
				theEditor = ExElement.useOrReplace(QuickWidget.Def.class, theEditor, session, "editor");
				theColumnEditValueName = session.getAttributeText("column-edit-value-name");
				isEditable = session.getAttributeExpression("editable-if");
				isAcceptable = session.getAttributeExpression("accept");
				theClicks = session.getAttribute("clicks", Integer.class);
				getAddOn(ExWithElementModel.Def.class).satisfyElementValueType(theColumnEditValueName, ModelTypes.Value,
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
					return getModels().getValue(getDefinition().getColumnEditValueName(), ModelTypes.Value.forType(theColumnType),
						getExpressoEnv());
				} catch (ModelException e) {
					throw new ExpressoInterpretationException(
						"Could not get column value '" + getDefinition().getColumnEditValueName() + "'",
						getDefinition().reporting().getPosition(), 0, e);
				} catch (TypeConversionException e) {
					throw new ExpressoInterpretationException(
						"Could not convert column value '" + getDefinition().getColumnEditValueName() + "'",
						getDefinition().reporting().getPosition(), 0, e);
				}
			}

			public Interpreted<R, C> update(TypeToken<C> columnType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				theColumnType = columnType;

				super.update(env);
				isEditable = getDefinition().isEditable() == null ? null
					: getDefinition().isEditable().interpret(ModelTypes.Value.STRING, getExpressoEnv());
				isAcceptable = getDefinition().isAcceptable() == null ? null
					: getDefinition().isAcceptable().interpret(ModelTypes.Value.STRING, getExpressoEnv());
				if (getDefinition().getEditor() == null)
					theEditor = null;
				else if (theEditor == null || !theEditor.getDefinition().equals(getDefinition().getEditor()))
					theEditor = getDefinition().getEditor().interpret(this);
				if (theEditor != null)
					theEditor.updateElement(env);
				return this;
			}

			public ColumnEditing<R, C> create(ExElement parent, QuickTableColumn<R, C> column) {
				return new ColumnEditing<>(this, (TableColumnSet<R>) parent, column);
			}
		}

		private final SettableValue<SettableValue<R>> theEditRowValue;
		private final SettableValue<SettableValue<C>> theEditColumnValue;
		private final SettableValue<SettableValue<Boolean>> isSelected;
		private final SettableValue<SettableValue<Integer>> theRowIndex;
		private final SettableValue<SettableValue<Integer>> theColumnIndex;
		private String theColumnEditValueName;
		private String theValueName;
		private Integer theClicks;
		private final SettableValue<C> theRawColumnEditValue;
		private final SettableValue<C> theFilteredColumnEditValue;
		private final SettableValue<SettableValue<String>> isEditable;
		private final SettableValue<SettableValue<String>> isAcceptable;
		private QuickWidget theEditor;

		public ColumnEditing(Interpreted<R, C> interpreted, TableColumnSet<R> parent, QuickTableColumn<R, C> column) {
			super(interpreted, parent);
			isEditable = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			isAcceptable = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			C defValue = TypeTokens.get().getDefaultValue(column.getType());
			theRawColumnEditValue = SettableValue.build(column.getType()).withValue(defValue).build();
			theFilteredColumnEditValue = SettableValue.build(column.getType()).withValue(defValue).build()//
				.disableWith(SettableValue.flatten(isEditable))//
				.filterAccept(v -> {
					SettableValue<String> accept = isAcceptable.get();
					if (accept == null)
						return null;
					theRawColumnEditValue.set(v, null);
					return accept == null ? null : accept.get();
				});
			theEditRowValue = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class)
				.<SettableValue<R>> parameterized(interpreted.getParentElement().getParentElement().getRowType())).build();
			theEditColumnValue = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<C>> parameterized(interpreted.getColumnType())).build();
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

		public String getColumnEditValueName() {
			return theColumnEditValueName;
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

		public void setEditorContext(ColumnEditContext<R, C> ctx) throws ModelInstantiationException {
			theEditRowValue.set(ctx.getRenderValue(), null);
			theEditColumnValue.set(ctx.getEditColumnValue(), null);
			isSelected.set(ctx.isSelected(), null);
			theRowIndex.set(ctx.getRowIndex(), null);
			theColumnIndex.set(ctx.getColumnIndex(), null);
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			ColumnEditing.Interpreted<R, C> myInterpreted = (ColumnEditing.Interpreted<R, C>) interpreted;
			theColumnEditValueName = myInterpreted.getDefinition().getColumnEditValueName();
			theClicks = myInterpreted.getDefinition().getClicks();
			isEditable.set(myInterpreted.isEditable() == null ? null : myInterpreted.isEditable().get(myModels), null);
			isAcceptable.set(myInterpreted.isAcceptable() == null ? null : myInterpreted.isAcceptable().get(myModels), null);

			if (myInterpreted.getEditor() == null)
				theEditor = null;
			else if (theEditor == null || theEditor.getIdentity() != myInterpreted.getEditor().getDefinition().getIdentity())
				theEditor = myInterpreted.getEditor().create(this);
			theValueName = getParentElement().getParentElement().getValueName();
			ExWithElementModel elModels = getAddOn(ExWithElementModel.class);
			elModels.satisfyElementValue(theColumnEditValueName, SettableValue.flatten(theEditColumnValue));
			if (theEditor != null) {
				Set<String> lookingForValues = new HashSet<>(Arrays.asList(theValueName, "rowIndex", "columnIndex", "selected"));
				ModelSetInstance editorModels = createEditorModels(myModels.copy(), lookingForValues);
				ExWithElementModel tableElModels = getParentElement().getParentElement().getAddOn(ExWithElementModel.class);
				if (theValueName != null)
					tableElModels.satisfyElementValue(theValueName, SettableValue.flatten(theEditRowValue), editorModels,
						ExFlexibleElementModelAddOn.ActionIfSatisfied.Replace);
				tableElModels.satisfyElementValue("selected", SettableValue.flatten(isSelected), editorModels,
					ExFlexibleElementModelAddOn.ActionIfSatisfied.Replace);
				tableElModels.satisfyElementValue("rowIndex", SettableValue.flatten(theRowIndex), editorModels,
					ExFlexibleElementModelAddOn.ActionIfSatisfied.Replace);
				tableElModels.satisfyElementValue("columnIndex", SettableValue.flatten(theColumnIndex), editorModels,
					ExFlexibleElementModelAddOn.ActionIfSatisfied.Replace);
				theEditor.update(myInterpreted.getEditor(), editorModels);

				// Find the name of the row value variable we need to spoof
				ExElement.Interpreted<?> parent = interpreted.getParentElement();
				while (parent != null && !(parent instanceof MultiValueWidget.Interpreted))
					parent = parent.getParentElement();
				if (parent != null)
					theValueName = ((MultiValueWidget.Interpreted<?, ?>) parent).getDefinition().getValueName();
			}
		}

		protected ModelSetInstance createEditorModels(ObservableModelSet.ModelSetInstanceBuilder editingModels,
			Set<String> lookingForValues) throws ModelInstantiationException {
			if (!lookingForValues.isEmpty()) {
				for (Map.Entry<ObservableModelSet.ModelComponentId, ? extends InterpretedModelSet> inh : editingModels.getModel()
					.getInheritance().entrySet()) {
					if (!lookingForValues.isEmpty() && hasAny(inh.getValue(), lookingForValues))
						editingModels.withAll(createEditorModels(editingModels.getInherited(inh.getKey()).copy(), lookingForValues));
				}
			}
			return editingModels.build();
		}

		private boolean hasAny(InterpretedModelSet model, Set<String> lookingForValues) {
			boolean anyFound = false;
			Iterator<String> lfvIter = lookingForValues.iterator();
			while (lfvIter.hasNext()) {
				String lfv = lfvIter.next();
				ObservableModelSet.ModelComponentNode<?> component = model.getComponentIfExists(lfv);
				if (component != null) {
					anyFound = true;
					if (component.getIdentity().getOwnerId().equals(model.getIdentity()))
						lfvIter.remove(); // No need to copy further for this value
				}
			}
			return anyFound;
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

		protected ColumnEditType(Interpreted<R, C, ?> interpreted, ColumnEditing<R, C> element) {
			super(interpreted, element);
		}

		public static class RowModifyEditType<R, C> extends ColumnEditType<R, C> {
			public static final String MODIFY = "modify-row-value";
			private static final SingleTypeTraceability<ColumnEditing<?, ?>, ?, ?> TRACEABILITY = ElementTypeTraceability
				.<ColumnEditing<?, ?>, RowModifyEditType<?, ?>, Interpreted<?, ?>, Def> getAddOnTraceability(QuickBaseInterpretation.NAME,
					QuickBaseInterpretation.VERSION, MODIFY, Def.class, Interpreted.class, RowModifyEditType.class);

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
					element.withTraceability(TRACEABILITY.validate(getType(), session.reporting()));
					super.update(session, element);
					theCommit = session.getAttributeExpression("commit");
					isRowUpdate = session.getAttribute("row-update", boolean.class);
				}

				@Override
				public Interpreted<?, ?> interpret(ExElement.Interpreted<? extends ColumnEditing<?, ?>> element) {
					return new Interpreted<>(this, (ColumnEditing.Interpreted<?, ?>) element);
				}
			}

			public static class Interpreted<R, C> extends ColumnEditType.Interpreted<R, C, RowModifyEditType<R, C>> {
				private InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> theCommit;

				public Interpreted(Def definition, ColumnEditing.Interpreted<R, C> element) {
					super(definition, element);
				}

				@Override
				public Def getDefinition() {
					return (Def) super.getDefinition();
				}

				public InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> getCommit() {
					return theCommit;
				}

				@Override
				public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
					super.update(env);
					theCommit = getDefinition().getCommit() == null ? null
						: getDefinition().getCommit().interpret(ModelTypes.Action.any(), env);
				}

				@Override
				public RowModifyEditType<R, C> create(ColumnEditing<R, C> element) {
					return new RowModifyEditType<>(this, element);
				}
			}

			private final SettableValue<ObservableAction<?>> theCommit;
			private boolean isRowUpdate;

			public RowModifyEditType(Interpreted<R, C> interpreted, ColumnEditing<R, C> element) {
				super(interpreted, element);
				theCommit = SettableValue.build(TypeTokens.get().keyFor(ObservableAction.class).<ObservableAction<?>> wildCard()).build();
			}

			public boolean isRowUpdate() {
				return isRowUpdate;
			}

			public ObservableAction<?> getCommit() {
				return ObservableAction.flatten(theCommit);
			}

			@Override
			public void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
				super.update(interpreted, models);
				RowModifyEditType.Interpreted<R, C> myInterpreted = (RowModifyEditType.Interpreted<R, C>) interpreted;
				isRowUpdate = myInterpreted.getDefinition().isRowUpdate();
				theCommit.set(myInterpreted.getCommit() == null ? null : myInterpreted.getCommit().get(models), null);
			}
		}

		public static class RowReplaceEditType<R, C> extends ColumnEditType<R, C> {
			public static final String REPLACE = "replace-row-value";
			private static final SingleTypeTraceability<ColumnEditing<?, ?>, ?, ?> TRACEABILITY = ElementTypeTraceability
				.<ColumnEditing<?, ?>, RowReplaceEditType<?, ?>, Interpreted<?, ?>, Def> getAddOnTraceability(QuickBaseInterpretation.NAME,
					QuickBaseInterpretation.VERSION, REPLACE, Def.class, Interpreted.class, RowReplaceEditType.class);

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
					element.withTraceability(TRACEABILITY.validate(getType(), session.reporting()));
					super.update(session, element);
					theReplacement = session.getAttributeExpression("replacement");
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
						.interpret(ModelTypes.Value.forType(getElement().getParentElement().getRowType()), env);
				}

				@Override
				public RowReplaceEditType<R, C> create(ColumnEditing<R, C> element) {
					return new RowReplaceEditType<>(this, element);
				}
			}

			private final SettableValue<SettableValue<R>> theReplacement;

			public RowReplaceEditType(Interpreted<R, C> interpreted, ColumnEditing<R, C> element) {
				super(interpreted, element);
				theReplacement = SettableValue.build(
					TypeTokens.get().keyFor(SettableValue.class).<SettableValue<R>> parameterized(element.getParentElement().getRowType()))
					.build();
			}

			public SettableValue<R> getReplacement() {
				return SettableValue.flatten(theReplacement);
			}

			@Override
			public void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
				super.update(interpreted, models);
				RowReplaceEditType.Interpreted<R, C> myInterpreted = (RowReplaceEditType.Interpreted<R, C>) interpreted;
				theReplacement.set(myInterpreted.getReplacement() == null ? null : myInterpreted.getReplacement().get(models), null);
			}
		}
	}

	public class SingleColumnSet<R, C> extends QuickStyledElement.Abstract implements TableColumnSet<R> {
		public static final String COLUMN = "column";
		private static final SingleTypeTraceability<SingleColumnSet<?, ?>, Interpreted<?, ?>, Def> COLUMN_TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, COLUMN, Def.class, Interpreted.class,
				SingleColumnSet.class);
		private static final SingleTypeTraceability<SingleColumnSet<?, ?>, Interpreted<?, ?>, Def> RENDERING_TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "rendering", Def.class,
				Interpreted.class,
				SingleColumnSet.class);

		public static class Def extends QuickStyledElement.Def.Abstract<SingleColumnSet<?, ?>>
		implements TableColumnSet.Def<SingleColumnSet<?, ?>> {
			private CompiledExpression theName;
			private String theColumnValueName;
			private CompiledExpression theValue;
			private CompiledExpression theHeaderTooltip;
			private QuickWidget.Def<?> theRenderer;
			private ColumnEditing.Def theEditing;

			public Def(RowTyped.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public RowTyped.Def<?> getParentElement() {
				return (RowTyped.Def<?>) super.getParentElement();
			}

			@QonfigAttributeGetter(asType = "column", value = "name")
			public CompiledExpression getName() {
				return theName;
			}

			@QonfigAttributeGetter(asType = "column", value = "column-value-name")
			public String getColumnValueName() {
				return theColumnValueName;
			}

			@QonfigAttributeGetter(asType = "column", value = "value")
			public CompiledExpression getValue() {
				return theValue;
			}

			@QonfigAttributeGetter(asType = "column", value = "header-tooltip")
			public CompiledExpression getHeaderTooltip() {
				return theHeaderTooltip;
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
				withTraceability(COLUMN_TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				withTraceability(RENDERING_TRACEABILITY.validate(session.asElement("rendering").getFocusType(), session.reporting()));
				super.doUpdate(session.asElement("styled"));
				theName = session.getAttributeExpression("name");
				theColumnValueName = session.getAttributeText("column-value-name");
				theValue = session.getAttributeExpression("value");
				theHeaderTooltip = session.getAttributeExpression("header-tooltip");
				ExpressoQIS renderer = session.forChildren("renderer").peekFirst();
				if (renderer == null)
					renderer = session.forMetadata("default-renderer").peekFirst();
				theRenderer = ExElement.useOrReplace(QuickWidget.Def.class, theRenderer, renderer, null);
				theEditing = ExElement.useOrReplace(ColumnEditing.Def.class, theEditing, session, "edit");
				getAddOn(ExWithElementModel.Def.class).satisfyElementValueType(theColumnValueName, ModelTypes.Value,
					(interp, env) -> ModelTypes.Value.forType(((Interpreted<?, ?>) interp).getType()));
			}

			@Override
			protected ColumnStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new ColumnStyle.Def(parentStyle, this, style);
			}

			@Override
			public <R> Interpreted<R, ?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, (RowTyped.Interpreted<R, ?>) parent);
			}
		}

		public static class Interpreted<R, C> extends QuickStyledElement.Interpreted.Abstract<SingleColumnSet<R, C>>
		implements TableColumnSet.Interpreted<R, SingleColumnSet<R, C>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theName;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<C>> theValue;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theHeaderTooltip;
			private QuickWidget.Interpreted<?> theRenderer;
			private ColumnEditing.Interpreted<R, C> theEditing;

			public Interpreted(SingleColumnSet.Def definition, RowTyped.Interpreted<R, ?> parent) {
				super(definition, parent);
			}

			@Override
			public SingleColumnSet.Def getDefinition() {
				return (SingleColumnSet.Def) super.getDefinition();
			}

			@Override
			public TabularWidget.Interpreted<R, ?> getParentElement() {
				return (TabularWidget.Interpreted<R, ?>) super.getParentElement();
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
			public TypeToken<R> getRowType() {
				return getParentElement().getRowType();
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
			public SingleColumnSet<R, C> create(ExElement parent) {
				return new SingleColumnSet<>(this, (TabularWidget<R>) parent);
			}
		}

		private final TypeToken<R> theRowType;
		private final TypeToken<C> theColumnType;
		private final ObservableCollection<SingleColumn> theColumn;

		private final SettableValue<SettableValue<String>> theName;
		private String theColumnValueName;
		private final SettableValue<SettableValue<C>> theValue;
		private final SettableValue<SettableValue<String>> theHeaderTooltip;
		private QuickWidget theRenderer;
		private ColumnEditing<R, C> theEditing;

		public SingleColumnSet(Interpreted<R, C> interpreted, TabularWidget<R> parent) {
			super(interpreted, parent);
			theName = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
				.build();
			theValue = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<C>> parameterized(interpreted.getType())).build();
			theRowType = interpreted.getRowType();
			theColumnType = interpreted.getType();
			theColumn = ObservableCollection.of((Class<SingleColumnSet<R, C>.SingleColumn>) (Class<?>) SingleColumn.class,
				new SingleColumn());
			theHeaderTooltip = SettableValue.build(theName.getType()).build();
		}

		@Override
		public TabularWidget<R> getParentElement() {
			return (TabularWidget<R>) super.getParentElement();
		}

		public SettableValue<String> getName() {
			return SettableValue.flatten(theName);
		}

		public String getColumnValueName() {
			return theColumnValueName;
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
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			SingleColumnSet.Interpreted<R, C> myInterpreted = (SingleColumnSet.Interpreted<R, C>) interpreted;
			getAddOn(ExWithElementModel.class).satisfyElementValue(myInterpreted.getDefinition().getColumnValueName(), getValue(),
				ExWithElementModel.ActionIfSatisfied.Ignore);
			theName.set(myInterpreted.getName().get(myModels), null);
			theColumnValueName = myInterpreted.getDefinition().getColumnValueName();
			theValue.set(myInterpreted.getValue().get(myModels), null);
			theHeaderTooltip.set(myInterpreted.getHeaderTooltip() == null ? null : myInterpreted.getHeaderTooltip().get(myModels), null);

			if (myInterpreted.getRenderer() == null)
				theRenderer = null;
			else if (theRenderer == null || theRenderer.getIdentity() != myInterpreted.getRenderer().getIdentity()) {
				try {
					theRenderer = myInterpreted.getRenderer().create(this);
				} catch (RuntimeException | Error e) {
					myInterpreted.getRenderer().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(),
						e);
				}
			}
			if (theRenderer != null) {
				try {
					theRenderer.update(myInterpreted.getRenderer(), myModels);
				} catch (RuntimeException | Error e) {
					myInterpreted.getRenderer().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(),
						e);
				}
			}

			if (myInterpreted.getEditing() == null)
				theEditing = null;
			else if (theEditing == null || theEditing.getIdentity() != myInterpreted.getEditing().getIdentity())
				theEditing = myInterpreted.getEditing().create(this, theColumn.getFirst());
			if (theEditing != null)
				theEditing.update(myInterpreted.getEditing(), myModels);
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
					return new ColumnStyle(this, (SingleColumnSet<?, ?>) styled);
				}
			}

			public ColumnStyle(Interpreted interpreted, SingleColumnSet<?, ?> styledElement) {
				super(interpreted, styledElement);
			}

			@Override
			public SingleColumnSet<?, ?> getStyledElement() {
				return (SingleColumnSet<?, ?>) super.getStyledElement();
			}
		}
	}
}
