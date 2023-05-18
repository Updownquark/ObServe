package org.observe.quick.base;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ExpressoRuntimeException;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.TypeConversionException;
import org.observe.quick.QuickAddOn;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickStyledElement;
import org.observe.quick.QuickStyledElement.QuickInterpretationCache;
import org.observe.quick.QuickValueWidget;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface QuickTableColumn<R, C> {
	public interface TableColumnSet<R> extends QuickElement, RowTyped<R> {
		public interface Def<CC extends TableColumnSet<?>> extends QuickElement.Def<CC>, RowTyped.Def<CC> {
			<R> Interpreted<R, ? extends CC> interpret(QuickElement.Interpreted<?> parent);
		}

		public interface Interpreted<R, CC extends TableColumnSet<R>> extends QuickElement.Interpreted<CC>, RowTyped.Interpreted<R, CC> {
			void update(InterpretedModelSet models, QuickStyledElement.QuickInterpretationCache cache) throws ExpressoInterpretationException;

			CC create(QuickElement parent);
		}

		ObservableCollection<? extends QuickTableColumn<R, ?>> getColumns();

		void update(ModelSetInstance models) throws ModelInstantiationException;
	}

	SettableValue<String> getName();

	QuickTableColumn.TableColumnSet<R> getColumnSet();

	TypeToken<C> getType();

	SettableValue<C> getValue();

	SettableValue<String> getHeaderTooltip();

	ObservableValue<QuickWidget> getRenderer();

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
				theEditColumnValue = SettableValue.build(columnType).withDescription(descrip + ".columnEditValue").build();
			}

			@Override
			public SettableValue<C> getEditColumnValue() {
				return theEditColumnValue;
			}
		}
	}

	public class ColumnEditing<R, C> extends QuickElement.Abstract implements QuickValueWidget.WidgetValueSupplier<C> {
		public static class Def extends QuickElement.Def.Abstract<ColumnEditing<?, ?>>
		implements QuickValueWidget.WidgetValueSupplier.Def<ColumnEditing<?, ?>> {
			private QuickWidget.Def<?> theEditor;
			private String theColumnEditValueName;
			private CompiledExpression isEditable;
			private CompiledExpression isAcceptable;
			private Integer theClicks;

			public Def(TableColumnSet.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public TableColumnSet.Def<?> getParentElement() {
				return (TableColumnSet.Def<?>) super.getParentElement();
			}

			public QuickWidget.Def<?> getEditor() {
				return theEditor;
			}

			public String getColumnEditValueName() {
				return theColumnEditValueName;
			}

			public CompiledExpression isEditable() {
				return isEditable;
			}

			public CompiledExpression isAcceptable() {
				return isAcceptable;
			}

			public Integer getClicks() {
				return theClicks;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				super.update(session);
				theEditor = QuickElement.useOrReplace(QuickWidget.Def.class, theEditor, session, "editor");
				theColumnEditValueName = session.getAttributeText("column-edit-value-name");
				isEditable = session.getAttributeExpression("editable-if");
				isAcceptable = session.getAttributeExpression("accept");
				theClicks = session.getAttribute("clicks", Integer.class);
			}

			public Interpreted<?, ?> interpret(TableColumnSet.Interpreted<?, ?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<R, C> extends QuickElement.Interpreted.Abstract<ColumnEditing<R, C>>
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

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isEditable() {
				return isEditable;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> isAcceptable() {
				return isAcceptable;
			}

			public QuickWidget.Interpreted<?> getEditor() {
				return theEditor;
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<C>> getValue() throws ExpressoInterpretationException {
				try {
					return getModels().getValue(getDefinition().getColumnEditValueName(), ModelTypes.Value.forType(theColumnType))
						.interpret();
				} catch (ModelException e) {
					throw new ExpressoInterpretationException(
						"Could not get column value '" + getDefinition().getColumnEditValueName() + "'",
						getDefinition().reporting().getFileLocation().getPosition(0), 0, e);
				} catch (TypeConversionException e) {
					throw new ExpressoInterpretationException(
						"Could not convert column value '" + getDefinition().getColumnEditValueName() + "'",
						getDefinition().reporting().getFileLocation().getPosition(0), 0, e);
				}
			}

			public Interpreted<R, C> update(TypeToken<C> columnType, QuickInterpretationCache cache)
				throws ExpressoInterpretationException {
				theColumnType = columnType;
				DynamicModelValue.satisfyDynamicValueType(getDefinition().getColumnEditValueName(), getDefinition().getModels(),
					ModelTypes.Value.forType(columnType));

				super.update();
				isEditable = getDefinition().isEditable() == null ? null
					: getDefinition().isEditable().evaluate(ModelTypes.Value.STRING).interpret();
				isAcceptable = getDefinition().isAcceptable() == null ? null
					: getDefinition().isAcceptable().evaluate(ModelTypes.Value.STRING).interpret();
				if (getDefinition().getEditor() == null)
					theEditor = null;
				else if (theEditor == null || !theEditor.getDefinition().equals(getDefinition().getEditor()))
					theEditor = getDefinition().getEditor().interpret(this);
				if (theEditor != null)
					theEditor.update(cache);
				return this;
			}

			public ColumnEditing<R, C> create(QuickElement parent, QuickTableColumn<R, C> column) {
				return new ColumnEditing<>(this, (TableColumnSet<R>) parent, column);
			}
		}

		private final SettableValue<C> theRawColumnEditValue;
		private final SettableValue<C> theFilteredColumnEditValue;
		private final SettableValue<SettableValue<String>> isEditable;
		private final SettableValue<SettableValue<String>> isAcceptable;
		private QuickWidget theEditor;

		public ColumnEditing(Interpreted interpreted, TableColumnSet<R> parent, QuickTableColumn<R, C> column) {
			super(interpreted, parent);
			isEditable = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			isAcceptable = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			theRawColumnEditValue = SettableValue.build(column.getType()).build();
			theFilteredColumnEditValue = SettableValue.build(column.getType()).build()//
				.disableWith(SettableValue.flatten(isEditable))//
				.filterAccept(v -> {
					SettableValue<String> accept = isAcceptable.get();
					if (accept == null)
						return null;
					theRawColumnEditValue.set(v, null);
					return accept == null ? null : accept.get();
				});
		}

		@Override
		public Interpreted<R, C> getInterpreted() {
			return (Interpreted<R, C>) super.getInterpreted();
		}

		@Override
		public TableColumnSet<R> getParentElement() {
			return (TableColumnSet<R>) super.getParentElement();
		}

		public ColumnEditType<R, C> getType() {
			return getAddOn(ColumnEditType.class);
		}

		public QuickWidget getEditor() {
			return theEditor;
		}

		public SettableValue<C> getFilteredColumnEditValue() {
			return theFilteredColumnEditValue;
		}

		public void setEditorContext(ColumnEditContext<R, C> ctx) throws ModelInstantiationException {
			ModelInstanceType<SettableValue<?>, SettableValue<C>> rowType = ModelTypes.Value.forType(ctx.getEditColumnValue().getType());
			QuickElement.satisfyContextValue(getInterpreted().getDefinition().getColumnEditValueName(), rowType, ctx.getEditColumnValue(),
				this);
			if (theEditor != null) {
				QuickElement parent = getParentElement();
				while (parent != null && !(parent instanceof MultiValueWidget))
					parent = parent.getParentElement();
				if (parent != null)
					QuickElement.satisfyContextValue(((MultiValueWidget<?>) parent).getInterpreted().getDefinition().getValueName(),
						rowType, ctx.getEditColumnValue(), theEditor);
				QuickElement.satisfyContextValue("rowIndex", ModelTypes.Value.INT, ctx.getRowIndex(), theEditor);
				QuickElement.satisfyContextValue("columnIndex", ModelTypes.Value.INT, ctx.getColumnIndex(), theEditor);
				QuickElement.satisfyContextValue("selected", ModelTypes.Value.BOOLEAN, ctx.isSelected(), theEditor);
			}
		}

		@Override
		protected void update(ModelSetInstance models) throws ModelInstantiationException {
			// The context values in the editor's models need to be independent from the render models
			super.update(models.copy().build());

			isEditable.set(getInterpreted().isEditable() == null ? null : getInterpreted().isEditable().get(models), null);
			isAcceptable.set(getInterpreted().isAcceptable() == null ? null : getInterpreted().isAcceptable().get(models), null);

			if (getInterpreted().getEditor() == null)
				theEditor = null;
			else if (theEditor == null || !theEditor.getInterpreted().equals(getInterpreted().getEditor()))
				theEditor = getInterpreted().getEditor().create(this);
			if (theEditor != null)
				theEditor.update(getModels());
		}
	}

	public abstract class ColumnEditType<R, C> extends QuickAddOn.Abstract<ColumnEditing<R, C>> {
		public static abstract class Def<CET extends ColumnEditType<?, ?>> extends QuickAddOn.Def.Abstract<ColumnEditing<?, ?>, CET> {
			protected Def(QonfigAddOn type, ColumnEditing.Def element) {
				super(type, element);
			}

			@Override
			public ColumnEditing.Def getElement() {
				return (ColumnEditing.Def) super.getElement();
			}

			@Override
			public abstract Interpreted<?, ?, ? extends CET> interpret(QuickElement.Interpreted<? extends ColumnEditing<?, ?>> element);
		}

		public static abstract class Interpreted<R, C, CET extends ColumnEditType<R, C>>
		extends QuickAddOn.Interpreted.Abstract<ColumnEditing<R, C>, CET> {
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

		@Override
		public Interpreted<R, C, ?> getInterpreted() {
			return (Interpreted<R, C, ?>) super.getInterpreted();
		}

		public static class RowModifyEditType<R, C> extends ColumnEditType<R, C> {
			public static class Def extends ColumnEditType.Def<RowModifyEditType<?, ?>> {
				private CompiledExpression theCommit;
				private boolean isRowUpdate;

				public Def(QonfigAddOn type, QuickTableColumn.ColumnEditing.Def element) {
					super(type, element);
				}

				public CompiledExpression getCommit() {
					return theCommit;
				}

				public boolean isRowUpdate() {
					return isRowUpdate;
				}

				@Override
				public void update(ExpressoQIS session) throws QonfigInterpretationException {
					super.update(session);
					theCommit = session.getAttributeExpression("commit");
					isRowUpdate = session.getAttribute("row-update", boolean.class);
				}

				@Override
				public Interpreted<?, ?> interpret(QuickElement.Interpreted<? extends ColumnEditing<?, ?>> element) {
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
				public void update(InterpretedModelSet models) throws ExpressoInterpretationException {
					theCommit = getDefinition().getCommit() == null ? null
						: getDefinition().getCommit().evaluate(ModelTypes.Action.any()).interpret();
				}

				@Override
				public RowModifyEditType<R, C> create(ColumnEditing<R, C> element) {
					return new RowModifyEditType<>(this, element);
				}
			}

			private final SettableValue<ObservableAction<?>> theCommit;

			public RowModifyEditType(Interpreted<R, C> interpreted, ColumnEditing<R, C> element) {
				super(interpreted, element);
				theCommit = SettableValue.build(TypeTokens.get().keyFor(ObservableAction.class).<ObservableAction<?>> wildCard()).build();
			}

			@Override
			public Interpreted<R, C> getInterpreted() {
				return (Interpreted<R, C>) super.getInterpreted();
			}

			public ObservableAction<?> getCommit() {
				return ObservableAction.flatten(theCommit);
			}

			@Override
			public void update(ModelSetInstance models) throws ModelInstantiationException {
				theCommit.set(getInterpreted().getCommit() == null ? null : getInterpreted().getCommit().get(models), null);
			}
		}

		public static class RowReplaceEditType<R, C> extends ColumnEditType<R, C> {
			public static class Def extends ColumnEditType.Def<RowReplaceEditType<?, ?>> {
				private CompiledExpression theReplacement;

				public Def(QonfigAddOn type, ColumnEditing.Def element) {
					super(type, element);
				}

				public CompiledExpression getReplacement() {
					return theReplacement;
				}

				@Override
				public void update(ExpressoQIS session) throws QonfigInterpretationException {
					super.update(session);
					theReplacement = session.getAttributeExpression("replacement");
				}

				@Override
				public Interpreted<?, ?> interpret(QuickElement.Interpreted<? extends ColumnEditing<?, ?>> element) {
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
				public void update(InterpretedModelSet models) throws ExpressoInterpretationException {
					theReplacement = getDefinition().getReplacement() == null ? null
						: getDefinition().getReplacement().evaluate(ModelTypes.Value.forType(getElement().getParentElement().getRowType()))
						.interpret();
				}

				@Override
				public RowReplaceEditType<R, C> create(ColumnEditing<R, C> element) {
					return new RowReplaceEditType<>(this, element);
				}
			}

			private final SettableValue<SettableValue<R>> theReplacement;

			public RowReplaceEditType(Interpreted<R, C> interpreted, ColumnEditing<R, C> element) {
				super(interpreted, element);
				theReplacement = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class)
					.<SettableValue<R>> parameterized(element.getInterpreted().getParentElement().getRowType())).build();
			}

			@Override
			public Interpreted<R, C> getInterpreted() {
				return (Interpreted<R, C>) super.getInterpreted();
			}

			public SettableValue<R> getReplacement() {
				return SettableValue.flatten(theReplacement);
			}

			@Override
			public void update(ModelSetInstance models) throws ModelInstantiationException {
				theReplacement.set(getInterpreted().getReplacement() == null ? null : getInterpreted().getReplacement().get(models), null);
			}
		}
	}

	public class SingleColumnSet<R, C> extends QuickElement.Abstract implements TableColumnSet<R> {
		public static class Def extends QuickElement.Def.Abstract<SingleColumnSet<?, ?>>
		implements TableColumnSet.Def<SingleColumnSet<?, ?>> {
			private CompiledExpression theName;
			private String theColumnValueName;
			private CompiledExpression theValue;
			private CompiledExpression theHeaderTooltip;
			private QuickWidget.Def<?> theRenderer;
			private ColumnEditing.Def theEditing;

			public Def(RowTyped.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public RowTyped.Def<?> getParentElement() {
				return (RowTyped.Def<?>) super.getParentElement();
			}

			public CompiledExpression getName() {
				return theName;
			}

			public String getColumnValueName() {
				return theColumnValueName;
			}

			public CompiledExpression getValue() {
				return theValue;
			}

			public CompiledExpression getHeaderTooltip() {
				return theHeaderTooltip;
			}

			public QuickWidget.Def<?> getRenderer() {
				return theRenderer;
			}

			public ColumnEditing.Def getEditing() {
				return theEditing;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				super.update(session);
				theName = session.getAttributeExpression("name");
				theColumnValueName = session.getAttributeText("column-value-name");
				theValue = session.getAttributeExpression("value");
				theHeaderTooltip = session.getAttributeExpression("header-tooltip");
				theRenderer = QuickElement.useOrReplace(QuickWidget.Def.class, theRenderer, session, "renderer");
				theEditing = QuickElement.useOrReplace(ColumnEditing.Def.class, theEditing, session, "edit");
			}

			@Override
			public <R> Interpreted<R, ?> interpret(QuickElement.Interpreted<?> parent) {
				return new Interpreted<>(this, (RowTyped.Interpreted<R, ?>) parent);
			}
		}

		public static class Interpreted<R, C> extends QuickElement.Interpreted.Abstract<SingleColumnSet<R, C>>
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
			public RowTyped.Interpreted<R, ?> getParentElement() {
				return (RowTyped.Interpreted<R, ?>) super.getParentElement();
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

			public QuickWidget.Interpreted<?> getRenderer() {
				return theRenderer;
			}

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
			public void update(InterpretedModelSet models, QuickStyledElement.QuickInterpretationCache cache) throws ExpressoInterpretationException {
				theValue = getDefinition().getValue().evaluate(ModelTypes.Value.<C> anyAsV()).interpret();
				DynamicModelValue.satisfyDynamicValueType(getDefinition().getColumnValueName(), getDefinition().getModels(),
					theValue.getType());
				super.update();
				theName = getDefinition().getName().evaluate(ModelTypes.Value.STRING).interpret();
				theHeaderTooltip = getDefinition().getHeaderTooltip() == null ? null
					: getDefinition().getHeaderTooltip().evaluate(ModelTypes.Value.STRING).interpret();

				if (getDefinition().getRenderer() == null)
					theRenderer = null; // TODO Dispose?
				else if (theRenderer == null || theRenderer.getDefinition() != getDefinition().getRenderer()) {
					// TODO Dispose?
					theRenderer = getDefinition().getRenderer().interpret(this);
				}
				if (theRenderer != null)
					theRenderer.update(cache);

				if (getDefinition().getEditing() == null)
					theEditing = null; // TODO Dispose?
				else if (theEditing == null || theEditing.getDefinition() != getDefinition().getEditing())
					theEditing = (ColumnEditing.Interpreted<R, C>) getDefinition().getEditing().interpret(this);
				if (theEditing != null)
					theEditing.update(getType(), cache);
			}

			@Override
			public SingleColumnSet<R, C> create(QuickElement parent) {
				return new SingleColumnSet<>(this, parent);
			}
		}

		private final ObservableCollection<SingleColumn> theColumn;

		private final SettableValue<SettableValue<String>> theName;
		private final SettableValue<SettableValue<C>> theValue;
		private final SettableValue<SettableValue<String>> theHeaderTooltip;
		private final SettableValue<QuickWidget> theRenderer;
		private ColumnEditing<R, C> theEditing;

		public SingleColumnSet(Interpreted<R, C> interpreted, QuickElement parent) {
			super(interpreted, parent);
			theName = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
				.build();
			theValue = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<C>> parameterized(getInterpreted().getType())).build();
			theColumn = ObservableCollection.of((Class<SingleColumnSet<R, C>.SingleColumn>) (Class<?>) SingleColumn.class,
				new SingleColumn());
			theHeaderTooltip = SettableValue.build(theName.getType()).build();
			theRenderer = SettableValue.build(QuickWidget.class).build();
		}

		@Override
		public Interpreted<R, C> getInterpreted() {
			return (Interpreted<R, C>) super.getInterpreted();
		}

		@Override
		public ObservableCollection<? extends QuickTableColumn<R, ?>> getColumns() {
			return theColumn;
		}

		public SettableValue<C> getValue() {
			return SettableValue.flatten(theValue);
		}

		@Override
		public void update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);
			try {
				DynamicModelValue.satisfyDynamicValueIfUnsatisfied(getInterpreted().getDefinition().getColumnValueName(),
					getInterpreted().getValue().getType(), getModels(), getValue());
			} catch (ModelException | TypeConversionException e) {
				throw new ExpressoRuntimeException("Install of column value failed",
					getInterpreted().getDefinition().reporting().getFileLocation().getPosition(0));
			}
			theName.set(getInterpreted().getName().get(getModels()), null);
			theValue.set(getInterpreted().getValue().get(getModels()), null);
			theHeaderTooltip.set(getInterpreted().getHeaderTooltip() == null ? null : getInterpreted().getHeaderTooltip().get(getModels()),
				null);

			if (getInterpreted().getRenderer() == null)
				theRenderer.set(null, null); // TODO Dispose?
			else if (theRenderer.get() == null || theRenderer.get().getInterpreted() != getInterpreted().getRenderer()) {
				try {
					theRenderer.set(getInterpreted().getRenderer().create(this), null);
				} catch (ExpressoRuntimeException e) {
					throw e;
				} catch (RuntimeException | Error e) {
					throw new ExpressoRuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(),
						getInterpreted().getRenderer().getDefinition().reporting().getFileLocation().getPosition(0), e);
				}
			}
			if (theRenderer.get() != null) {
				try {
					theRenderer.get().update(getModels());
				} catch (ExpressoRuntimeException e) {
					throw e;
				} catch (RuntimeException | Error e) {
					throw new ExpressoRuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(),
						getInterpreted().getRenderer().getDefinition().reporting().getFileLocation().getPosition(0), e);
				}
			}

			if (getInterpreted().getEditing() == null)
				theEditing = null; // TODO Dispose?
			else if (theEditing == null || theEditing.getInterpreted() != getInterpreted().getEditing())
				theEditing = getInterpreted().getEditing().create(this, theColumn.getFirst());
			if (theEditing != null)
				theEditing.update(getModels());
		}

		public class SingleColumn implements QuickTableColumn<R, C> {
			@Override
			public TableColumnSet<R> getColumnSet() {
				return SingleColumnSet.this;
			}

			@Override
			public SettableValue<String> getName() {
				return SettableValue.flatten(theName);
			}

			@Override
			public TypeToken<C> getType() {
				return getInterpreted().getType();
			}

			@Override
			public SettableValue<C> getValue() {
				return SettableValue.flatten(theValue);
			}

			@Override
			public SettableValue<String> getHeaderTooltip() {
				return SettableValue.flatten(theHeaderTooltip);
			}

			@Override
			public ObservableValue<QuickWidget> getRenderer() {
				return theRenderer.unsettable();
			}

			@Override
			public ColumnEditing<R, C> getEditing() {
				return theEditing;
			}

			@Override
			public void update() {
			}
		}
	}
}
