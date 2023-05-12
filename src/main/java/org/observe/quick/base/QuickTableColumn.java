package org.observe.quick.base;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoRuntimeException;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.TypeConversionException;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickStyledElement;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface QuickTableColumn<R, C> {
	public interface TableColumnSet<R> extends QuickElement {
		public interface Def<CC extends TableColumnSet<?>> extends QuickElement.Def<CC> {
			<R> Interpreted<R, ? extends CC> interpret(QuickElement.Interpreted<?> parent);
		}

		public interface Interpreted<R, CC extends TableColumnSet<R>> extends QuickElement.Interpreted<CC> {
			void update(InterpretedModelSet models, QuickStyledElement.QuickInterpretationCache cache) throws ExpressoInterpretationException;

			CC create(QuickElement parent);
		}

		ObservableCollection<? extends QuickTableColumn<R, ?>> getColumns();

		TableColumnSet<R> update(ModelSetInstance models) throws ModelInstantiationException;
	}

	SettableValue<String> getName();

	QuickTableColumn.TableColumnSet<R> getColumnSet();

	TypeToken<C> getType();

	SettableValue<C> getValue();

	SettableValue<String> getHeaderTooltip();

	ObservableValue<QuickWidget> getRenderer();

	void update();

	public class SingleColumnSet<R, C> extends QuickElement.Abstract implements TableColumnSet<R> {
		public static class Def extends QuickElement.Def.Abstract<SingleColumnSet<?, ?>>
		implements TableColumnSet.Def<SingleColumnSet<?, ?>> {
			private CompiledExpression theName;
			private String theColumnValueName;
			private CompiledExpression theValue;
			private CompiledExpression theHeaderTooltip;
			private QuickWidget.Def<?> theRenderer;

			public Def(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
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

			@Override
			public Def update(AbstractQIS<?> session) throws QonfigInterpretationException {
				super.update(session);
				theName = getExpressoSession().getAttributeExpression("name");
				theColumnValueName = getExpressoSession().getAttributeText("column-value-name");
				theValue = getExpressoSession().getAttributeExpression("value");
				theHeaderTooltip = getExpressoSession().getAttributeExpression("header-tooltip");
				theRenderer = QuickElement.useOrReplace(QuickWidget.Def.class, theRenderer, session, "renderer");
				return this;
			}

			@Override
			public <R> Interpreted<R, ?> interpret(QuickElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<R, C> extends QuickElement.Interpreted.Abstract<SingleColumnSet<R, C>>
		implements TableColumnSet.Interpreted<R, SingleColumnSet<R, C>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theName;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<C>> theValue;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theHeaderTooltip;
			private QuickWidget.Interpreted<?> theRenderer;

			public Interpreted(SingleColumnSet.Def definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SingleColumnSet.Def getDefinition() {
				return (SingleColumnSet.Def) super.getDefinition();
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

			public TypeToken<C> getType() {
				return (TypeToken<C>) theValue.getType().getType(0);
			}

			@Override
			public void update(InterpretedModelSet models, QuickStyledElement.QuickInterpretationCache cache) throws ExpressoInterpretationException {
				theValue = getDefinition().getValue().evaluate(ModelTypes.Value.<C> anyAs()).interpret();
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
		public SingleColumnSet<R, C> update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);
			try {
				DynamicModelValue.satisfyDynamicValueIfUnsatisfied(getInterpreted().getDefinition().getColumnValueName(),
					getInterpreted().getValue().getType(), getModels(), getValue());
			} catch (ModelException | TypeConversionException e) {
				throw new ExpressoRuntimeException("Install of column value failed",
					getInterpreted().getDefinition().getElement().getPositionInFile());
			}
			theName.set(getInterpreted().getName().get(getModels()), null);
			try {
				theValue.set(getInterpreted().getValue().get(getModels()), null);
			} catch (ExpressoRuntimeException e) {
				throw e;
			} catch (RuntimeException | Error e) {
				throw new ExpressoRuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(),
					getInterpreted().getDefinition().getExpressoSession().getAttributeValuePosition("value", 0), e);
			}
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
						getInterpreted().getRenderer().getDefinition().getElement().getPositionInFile(), e);
				}
			}
			if (theRenderer.get() != null) {
				try {
					theRenderer.get().update(getModels());
				} catch (ExpressoRuntimeException e) {
					throw e;
				} catch (RuntimeException | Error e) {
					throw new ExpressoRuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(),
						getInterpreted().getRenderer().getDefinition().getElement().getPositionInFile(), e);
				}
			}
			return this;
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
			public void update() {
			}
		}
	}
}
